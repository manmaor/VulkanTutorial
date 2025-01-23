package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkSemaphoreCreateInfo
import org.lwjgl.vulkan.VK11.*

class Semaphore(
    val device: Device
) {

    val vkSemaphore: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)

            val lp = stack.mallocLong(1)
            vkCheck(VK10.vkCreateSemaphore(device.vkDevice, semaphoreCreateInfo, null, lp),
                "Failed to create semaphore")
            vkSemaphore = lp[0]
        }
    }

    fun cleanup() {
        vkDestroySemaphore(device.vkDevice, vkSemaphore, null)
    }
}