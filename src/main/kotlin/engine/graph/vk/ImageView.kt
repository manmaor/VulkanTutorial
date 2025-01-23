package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkImageViewCreateInfo
import org.lwjgl.vulkan.*

class ImageView(
    private val device: Device,
    vkImage: Long,
    imageViewData: ImageViewData
) {

    val aspectMask: Int
    val mipLevels: Int
    val vkImageView: Long

    init {
        aspectMask = imageViewData.aspectMask
        mipLevels = imageViewData.mipLevels

        MemoryStack.stackPush().use { stack ->
            val viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(vkImage)
                .viewType(imageViewData.viewType)
                .format(imageViewData.format)
                .subresourceRange {
                    it
                        .aspectMask(aspectMask)
                        .baseMipLevel(0)
                        .levelCount(mipLevels)
                        .baseArrayLayer(imageViewData.baseArrayLayer)
                        .layerCount(imageViewData.layerCount)
                }

            val lp = stack.mallocLong(1)
            vkCheck(vkCreateImageView(device.vkDevice, viewCreateInfo, null, lp),
                "Failed to create image view")
            vkImageView = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyImageView(device.vkDevice, vkImageView, null)
    }

    data class ImageViewData(
        val aspectMask: Int,
        val baseArrayLayer: Int = 0,
        val format: Int,
        val layerCount: Int = 1,
        val mipLevels: Int = 1,
        val viewType: Int = VK11.VK_IMAGE_VIEW_TYPE_2D,
    ) {



    }
}