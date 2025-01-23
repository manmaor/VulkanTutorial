package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.VK10.VK_NULL_HANDLE
import org.lwjgl.vulkan.VK10.vkQueueSubmit
import org.lwjgl.vulkan.VK11
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkSubmitInfo
import org.tinylog.kotlin.Logger
import java.nio.IntBuffer
import java.nio.LongBuffer

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

    fun submit(commandBuffers: PointerBuffer,
               waitSemaphores: LongBuffer?,
               dstStageMasks: IntBuffer,
               signalSemaphores: LongBuffer,
               fence: Fence?) {
        MemoryStack.stackPush().use { stack ->
            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK11.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(commandBuffers)
                .pSignalSemaphores(signalSemaphores)

            waitSemaphores?.let {
                submitInfo
                    .waitSemaphoreCount(waitSemaphores.capacity())
                    .pWaitSemaphores(waitSemaphores)
                    .pWaitDstStageMask(dstStageMasks) // states where in the pipeline execution we should wait
            } ?: run {
                submitInfo
                    .waitSemaphoreCount(0)
            }

            val fenceHandle = fence?.vkFence ?: VK_NULL_HANDLE

            vkCheck(vkQueueSubmit(vkQueue, submitInfo, fenceHandle),
                "Failed to submit command to queue")
        }
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

    class PresentQueue(
        device: Device,
        surface: Surface,
        indexQueue: Int
    ): Queue(
        device,
        getPresentQueueFamilyIndex(device, surface),
        indexQueue
    ) {
        companion object {
            private fun getPresentQueueFamilyIndex(device: Device, surface: Surface): Int {
                MemoryStack.stackPush().use { stack ->
                    val intBuff = stack.mallocInt(1)
                    device.physicalDevice.vkQueueFamilyProps.forEachIndexed { index, vkQueueFamilyProperties ->
                        KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(device.physicalDevice.vkPhysicalDevice, index, surface.vkSurface, intBuff)
                        if (intBuff[0] == VK11.VK_TRUE) return index
                    }
                }

                throw RuntimeException("Failed to get Presentation Queue family index")
            }
        }
    }
}