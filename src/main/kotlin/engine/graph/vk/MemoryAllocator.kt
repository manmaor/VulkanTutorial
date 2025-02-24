package com.maorbarak.engine.graph.vk


import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.*
import org.lwjgl.util.vma.Vma.*
import org.lwjgl.vulkan.VkDevice

class MemoryAllocator(
    instance: Instance,
    physicalDevice: PhysicalDevice,
    vkDevice: VkDevice
) {

    val vmaAllocator: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
                .set(instance.vkInstance, vkDevice)

            val createInfo = VmaAllocatorCreateInfo.calloc(stack)
                .instance(instance.vkInstance)
                .device(vkDevice)
                .physicalDevice(physicalDevice.vkPhysicalDevice)
                .pVulkanFunctions(vmaVulkanFunctions)

            val pAllocator  = stack.mallocPointer(1)
            vkCheck(vmaCreateAllocator(createInfo, pAllocator),
                "Failed to create VMA allocator")
            vmaAllocator = pAllocator[0]
        }
    }

    fun cleanup() {
        vmaDestroyAllocator(vmaAllocator)
    }
}