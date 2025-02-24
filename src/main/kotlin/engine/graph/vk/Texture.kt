package com.maorbarak.engine.graph.vk

import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import org.tinylog.kotlin.Logger
import java.nio.ByteBuffer
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.min

class Texture(
    device: Device,
    val fileName: String,
    imageFormat: Int
) {

    val height: Int
    val width: Int
    val image: Image
    val imageView: ImageView
    val mipLevels: Int
    var stgBuffer: VulkanBuffer? = null
        private set

    var recordedTransition: Boolean
        private set

    val hasTransparencies: Boolean

    init {
        Logger.debug("Creating texture [$fileName]")
        recordedTransition = false

        var buf: ByteBuffer? = null
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            val channels = stack.mallocInt(1)

            buf = stbi_load(fileName, w, h, channels, 4) ?:
                throw RuntimeException("Image file [$fileName] not loaded: ${stbi_failure_reason()}")
            hasTransparencies = setHasTransparencies(buf!!)

            width = w.get()
            height = h.get()
            mipLevels = floor(log2(min(width, height).toFloat())).toInt() + 1

            createStgBuffer(device, buf!!)
            val imageData = Image.ImageData(width = width, height = height,
                // VK_IMAGE_USAGE_TRANSFER_SRC_BIT because we the image as a source to generate the mipmaps
                usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT or VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT,
                format = imageFormat, mipLevels = mipLevels)
            image = Image(device, imageData)
            val imageViewData = ImageView.ImageViewData(
                format = image.format, aspectMask = VK_IMAGE_ASPECT_COLOR_BIT, mipLevels = mipLevels)
            imageView = ImageView(device, image.vkImage, imageViewData)
        }

        buf?.let { stbi_image_free(it) }
    }

    fun cleanup() {
        cleanupStgBuffer()
        imageView.cleanup()
        image.cleanup()
    }

    fun cleanupStgBuffer() {
        stgBuffer?.let {
            it.cleanup()
            stgBuffer = null
        }
    }

    private fun setHasTransparencies(buf: ByteBuffer): Boolean {
        val numPixels = buf.capacity() / 4
        var offset = 0
        (0..<numPixels).forEach { i ->
            if ((0xff and buf[offset + 3].toInt()) < 255) {
                return true
            }
            offset += 4
        }

        return false
    }

    private fun createStgBuffer(device: Device, data: ByteBuffer) {
        val size = data.remaining()
        stgBuffer = VulkanBuffer(device, size.toLong(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
        val mappedMemory = stgBuffer!!.map()
        val buffer = MemoryUtil.memByteBuffer(mappedMemory, stgBuffer!!.requestedSize.toInt())
        buffer.put(data)
        data.flip()
        stgBuffer!!.unMap()
    }

    private fun recordCopyBuffer(stack: MemoryStack, cmd: CommandBuffer, bufferData: VulkanBuffer) {
        val region = VkBufferImageCopy.calloc(1, stack)
            .bufferOffset(0)
            .bufferRowLength(0)
            .bufferImageHeight(0)
            .imageSubresource {
                it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }
            .imageOffset { it.x(0).y(0).z(0) }
            .imageExtent { it.width(width).height(height).depth(1) }

        vkCmdCopyBufferToImage(cmd.vkCommandBuffer, bufferData.buffer, image.vkImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region)
    }

    private fun recordImageTransition (stack: MemoryStack, cmd: CommandBuffer, oldLayout: Int, newLayout: Int) {
        // Memory barriers in Vulkan are used to specify availability and
        //   visibility dependencies over a memory region
        // set of operations -> availability operation -> the visibility operation.
        val barrier = VkImageMemoryBarrier.calloc(1, stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            // layout transtions
            .oldLayout(oldLayout)
            .newLayout(newLayout)
            // transfer the ownership from one queue family to another
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(image.vkImage)
            .subresourceRange {
                it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(mipLevels)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }

        var srcStage: Int
        var srcAccessMask: Int
        var dstAccessMask: Int
        var dstStage: Int

        when {
            oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> {
                srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT // this is the earliest possible pipeline stage
                srcAccessMask = 0
                dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT
                dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT
            }
            oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> {
                srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT
                srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT
                dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                dstAccessMask = VK_ACCESS_SHADER_READ_BIT
            }
            else -> throw RuntimeException("Unsupported layout transition")
        }

        // limits the access to the memory to the first scope of the barrier with that access mask
        barrier.srcAccessMask(srcAccessMask)
        barrier.dstAccessMask(dstAccessMask)

        vkCmdPipelineBarrier(cmd.vkCommandBuffer, srcStage, dstStage, 0,
            null, null, barrier)
    }

    private fun recordGenerateMipMaps(stack: MemoryStack, cmd: CommandBuffer) {
        val subResourceRange = VkImageSubresourceRange.calloc(stack)
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseArrayLayer(0)
            .levelCount(1)
            .layerCount(1)

        val barrier = VkImageMemoryBarrier.calloc(1, stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .image(image.vkImage)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .subresourceRange(subResourceRange)

        var mipWidth = width
        var mipHeight = height

        (1..<mipLevels).forEach { i ->
            subResourceRange.baseMipLevel(i - 1)
            barrier.subresourceRange(subResourceRange)
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)

            vkCmdPipelineBarrier(cmd.vkCommandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                0, null, null, barrier)

            val auxi = i
            val srcOffset0 = VkOffset3D.calloc(stack).x(0).y(0).z(0)
            val srcOffset1 = VkOffset3D.calloc(stack).x(mipWidth).y(mipHeight).z(1)
            val dstOffset0 = VkOffset3D.calloc(stack).x(0).y(0).z(0)
            val dstOffset1 = VkOffset3D.calloc(stack)
                .x(if (mipWidth > 1) mipWidth / 2 else 1)
                .y(if (mipHeight > 1) mipHeight / 2 else 1)
                .z(1)

            val blit = VkImageBlit.calloc(1, stack)
                .srcOffsets(0, srcOffset0)
                .srcOffsets(1, srcOffset1)
                .srcSubresource {
                    it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(auxi - 1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
                .dstOffsets(0, dstOffset0)
                .dstOffsets(1, dstOffset1)
                .dstSubresource {
                    it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(auxi)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }

            vkCmdBlitImage(cmd.vkCommandBuffer,
                image.vkImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                image.vkImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                blit,
                VK_FILTER_LINEAR // filter to apply if the blit operation requires scaling.
            )

            // Make the mip layer visible to the shader
            barrier
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)

            vkCmdPipelineBarrier(cmd.vkCommandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                0, null, null, barrier)

            if (mipWidth > 1) mipWidth /= 2
            if (mipHeight > 1) mipHeight /= 2
        }

        // Make the last mip level visible to the shader
        subResourceRange.baseMipLevel(mipLevels - 1)
        barrier
            .subresourceRange(subResourceRange)
            .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
            .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
            .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)

        vkCmdPipelineBarrier(cmd.vkCommandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0, null, null, barrier)

    }

    fun recordTextureTransition(cmd: CommandBuffer) {
        if (stgBuffer != null && !recordedTransition) {
            Logger.debug("Recording transition for texture [$fileName]")
            recordedTransition = true
            MemoryStack.stackPush().use { stack ->
                // transition from VK_IMAGE_LAYOUT_UNDEFINED to VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                // meaning From undefined layout to a layout where we can transfer the image contents
                recordImageTransition(stack, cmd, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                // copy staging buffer contents to the image
                recordCopyBuffer(stack, cmd, stgBuffer!!)

                recordGenerateMipMaps(stack, cmd)

                // transition from VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL to VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                // meaning From transferable image content(already transferred) to visible to fragment shader
//                recordImageTransition(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            }
        } else {
            Logger.debug("Texture [$fileName] has already been transitioned")
        }
    }
}