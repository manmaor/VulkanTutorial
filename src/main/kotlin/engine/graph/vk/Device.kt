package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME
import org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkDeviceCreateInfo
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures
import org.tinylog.kotlin.Logger

class Device(
    val physicalDevice: PhysicalDevice
) {
    val vkDevice: VkDevice

    init {
        Logger.debug("Creating device")

        MemoryStack.stackPush().use { stack ->

            // Define required extensions
            val usePortability = getDeviceExtensions()
                .contains(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME) && VulkanUtils.os == VulkanUtils.OSType.MACOS
            val numExtensions = if (usePortability) 2 else 1
            val requiredExtensions = stack.mallocPointer(numExtensions)
            requiredExtensions.put(stack.ASCII(VK_KHR_SWAPCHAIN_EXTENSION_NAME))
            if (usePortability) {
                requiredExtensions.put(stack.ASCII(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME))
            }
            requiredExtensions.flip()

            // Set up required features
            val features = VkPhysicalDeviceFeatures.calloc(stack)

            // Enable all the queue families
            val queuePropsBuff = physicalDevice.vkQueueFamilyProps
            val numQueueFamilies = queuePropsBuff.capacity()
            val queueCreationInfoBuf = VkDeviceQueueCreateInfo.calloc(numQueueFamilies, stack)
            queuePropsBuff.forEachIndexed { index, props ->
                queueCreationInfoBuf[index]
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(index)
                    .pQueuePriorities(stack.callocFloat(props.queueCount()))
            }

            val deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .ppEnabledExtensionNames(requiredExtensions)
                .pEnabledFeatures(features)
                .pQueueCreateInfos(queueCreationInfoBuf)

            val devicePointer = stack.mallocPointer(1)
            vkCheck(vkCreateDevice(physicalDevice.vkPhysicalDevice, deviceCreateInfo, null, devicePointer),
                "Failed to create device")

            vkDevice = VkDevice(devicePointer[0], physicalDevice.vkPhysicalDevice, deviceCreateInfo)
        }
    }

    fun cleanup() {
        Logger.debug("Destroying Vulkan device")
        vkDestroyDevice(vkDevice, null)
    }

    private fun getDeviceExtensions(): Set<String> = physicalDevice.vkDeviceExtensions
            .map {
                it.extensionNameString()
            }
            .toSet()
            .also {
                Logger.debug("Supported device extensions $it");
            }

    /**
     * Waits for all operations for complete
     */
    fun waitIdle() {
        vkDeviceWaitIdle(vkDevice)
    }
}