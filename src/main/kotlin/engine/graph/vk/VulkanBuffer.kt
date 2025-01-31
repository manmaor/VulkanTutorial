package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.vkMapMemory
import org.lwjgl.vulkan.VK11.*


class VulkanBuffer(
    val device: Device,
    val requestedSize: Long,
    usage: Int,
    reqMask: Int
) {
    val allocationSize: Long
    val buffer: Long
    val memory: Long
    val pb: PointerBuffer

    private var mappedMemory: Long?

    init {
        MemoryStack.stackPush().use { stack ->
            mappedMemory = null

            val bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(requestedSize)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            val lp = stack.mallocLong(1)
            vkCheck(vkCreateBuffer(device.vkDevice, bufferCreateInfo, null, lp),
                "Failed to create buffer")
            buffer = lp[0]

            val memReq = VkMemoryRequirements.malloc(stack)
            vkGetBufferMemoryRequirements(device.vkDevice, buffer, memReq)

            val memAlloc = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReq.size())
                .memoryTypeIndex(VulkanUtils.memoryTypeFromProperties(
                    device.physicalDevice, memReq.memoryTypeBits(), reqMask))

            vkCheck(vkAllocateMemory(device.vkDevice, memAlloc, null, lp),
                "Failed to allocate memory")

            allocationSize = memAlloc.allocationSize()
            memory = lp[0]
            pb = MemoryUtil.memAllocPointer(1)

            vkCheck(vkBindBufferMemory(device.vkDevice, buffer, memory, 0),
                "Failed to bind buffer memory")
        }
    }

    fun cleanup() {
        MemoryUtil.memFree(pb)
        vkDestroyBuffer(device.vkDevice, buffer, null)
        vkFreeMemory(device.vkDevice, memory, null)
    }

    fun map(): Long {
        return mappedMemory ?: run {
            vkCheck(vkMapMemory(device.vkDevice, memory, 0, allocationSize, 0, pb),
                "Failed to map Buffer")
            pb[0].also {
                mappedMemory = it
            }
        }
    }

    fun unMap() {
        mappedMemory?.let {
            vkUnmapMemory(device.vkDevice, memory)
            mappedMemory = null
        }
    }
}