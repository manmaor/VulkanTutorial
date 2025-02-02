package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements

class Image(
    val device: Device,
    imageData: ImageData
) {

    val format: Int
    val mipLevels: Int
    val vkImage: Long
    val vkMemory: Long

    init {
        MemoryStack.stackPush().use { stack ->
            format = imageData.format
            mipLevels = imageData.mipLevels

            val imageCreateInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(format)
                .extent { it.width(imageData.width).height(imageData.height).depth(1) }
                .mipLevels(mipLevels)
                .arrayLayers(imageData.arrayLayers)
                .samples(imageData.sampleCount)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE) // between family queues
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(imageData.usage)

            val lp = stack.mallocLong(1)
            vkCheck(vkCreateImage(device.vkDevice, imageCreateInfo, null, lp),
                "Failed to create image")
            vkImage = lp[0]

            // Ger memory requirements fpr this object
            val memReqs = VkMemoryRequirements.calloc(stack)
            vkGetImageMemoryRequirements(device.vkDevice, vkImage, memReqs)

            // Select memory size and type
            val memAlloc = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(VulkanUtils.memoryTypeFromProperties(device.physicalDevice, memReqs.memoryTypeBits(), 0))

            // Allocate the memory
            vkCheck(vkAllocateMemory(device.vkDevice, memAlloc, null, lp),
                "Failed to allocate memory")
            vkMemory = lp[0]

            // Bind memory
            vkCheck(vkBindImageMemory(device.vkDevice, vkImage, vkMemory, 0),
                "Failed to bind image memory")
        }
    }

    fun cleanup() {
        vkDestroyImage(device.vkDevice, vkImage, null)
        vkFreeMemory(device.vkDevice, vkMemory, null)
    }

    data class ImageData(
        val height: Int,
        val width: Int,
        val usage: Int,
        val arrayLayers: Int = 1,
        val format: Int = VK_FORMAT_R8G8B8A8_SRGB,
        val mipLevels: Int = 1,
        val sampleCount: Int = 1,
    )
}