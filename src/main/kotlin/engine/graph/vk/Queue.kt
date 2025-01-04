package com.maorbarak.engine.graph.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11
import org.lwjgl.vulkan.VkQueue
import org.tinylog.kotlin.Logger

open class Queue(
    val device: Device,
    val queueFamilyIndex: Int,
    val queueIndex: Int
) {

    private val queueHandle: Long
    val vkQueue: VkQueue

    init {
        Logger.debug("Creating queue")

        MemoryStack.stackPush().use { stack ->
            val pQueue = stack.mallocPointer(1)
            VK11.vkGetDeviceQueue(device.vkDevice, queueFamilyIndex, queueIndex, pQueue)
            queueHandle = pQueue[0]
            vkQueue = VkQueue(queueHandle, device.vkDevice)
        }
    }

    fun waitIdle() {
        VK11.vkQueueWaitIdle(vkQueue)
    }


    class GraphicsQueue(
        device: Device,
        indexQueue: Int
    ) : Queue(
        device,
        getGraphicsQueueFamilyIndex(device),
        indexQueue
    ) {

        companion object {
            private fun getGraphicsQueueFamilyIndex(device: Device): Int {
                return device.physicalDevice.vkQueueFamilyProps.indexOfFirst {
                    (it.queueFlags() and VK11.VK_QUEUE_GRAPHICS_BIT) != 0
                }.let { index ->
                    if (index == -1) {
                        throw RuntimeException("Failed to get graphics Queue family index")
                    }
                    index
                }
            }
        }

    }
}