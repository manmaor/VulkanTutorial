package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.tinylog.kotlin.Logger

class PhysicalDevice(
    val vkPhysicalDevice: VkPhysicalDevice
) {

    val vkDeviceExtensions: VkExtensionProperties.Buffer // containing a list of supported extensions (name and version)
    val vkMemoryProperties: VkPhysicalDeviceMemoryProperties // contains information related to the different memory heaps the this device supports
    val vkPhysicalDeviceFeatures: VkPhysicalDeviceFeatures // contains fine-grained features supported by this device, such as if it supports depth clamping, certain types of shaders, etc
    private val vkPhysicalDeviceProperties: VkPhysicalDeviceProperties //  contains the physical device properties, such as the device name, the vendor, its limits, etc
    val vkQueueFamilyProps: VkQueueFamilyProperties.Buffer // holds the queue families supported by the device

    init {
        MemoryStack.stackPush().use { stack ->
            val intBuffer = stack.mallocInt(1)

            // Get Device Properties
            vkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc()
            vkGetPhysicalDeviceProperties(vkPhysicalDevice, vkPhysicalDeviceProperties)

            // Get Device Extension
            vkCheck(vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, null as String?, intBuffer, null),
                "Failed to get number of device extension properties")
            vkDeviceExtensions = VkExtensionProperties.calloc(intBuffer[0])
            vkCheck(vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, null as String?, intBuffer, vkDeviceExtensions),
                "Failed to get extension properties")

            // Get Queue family properties
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, null)
            vkQueueFamilyProps = VkQueueFamilyProperties.calloc(intBuffer[0])
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, vkQueueFamilyProps)

            vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc()
            vkGetPhysicalDeviceFeatures(vkPhysicalDevice, vkPhysicalDeviceFeatures)

            // Get Memory information and properties
            vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
            vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, vkMemoryProperties)
        }
    }

    val deviceName: String = vkPhysicalDeviceProperties.deviceNameString()

    val hasGraphicsQueueFamily: Boolean =
        (0 until vkQueueFamilyProps.capacity()).any { index ->
            (vkQueueFamilyProps[index].queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0
        }

    val hasKHRSwapChainExtension: Boolean =
        (0 until vkDeviceExtensions.capacity()).any { index ->
            KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME.contains(vkDeviceExtensions[index].extensionNameString())
        }

    fun cleanup() {
        Logger.debug("Destroying physical device $deviceName")

        vkMemoryProperties.free();
        vkPhysicalDeviceFeatures.free();
        vkQueueFamilyProps.free();
        vkDeviceExtensions.free();
        vkPhysicalDeviceProperties.free();
    }



    // TODO: cleanup

    companion object {
        fun createPhysicalDevice(instance: Instance, preferredDeviceName: String?): PhysicalDevice {
            Logger.debug("Selecting physical devices")
            MemoryStack.stackPush().use { stack ->
                val pPhysicalDevices = getPhysicalDevices(instance, stack)
                val numDevices = pPhysicalDevices.capacity()
                if (numDevices <= 0) {
                    throw RuntimeException("No physical devices found")
                }

                // Populate devices
               val devices = (0 until numDevices).map { index ->
                   PhysicalDevice(
                       VkPhysicalDevice(pPhysicalDevices[index], instance.vkInstance)
                   )
               }.groupBy { it.hasGraphicsQueueFamily && it.hasKHRSwapChainExtension }

                devices[false]?.forEach {
                    Logger.info("Device ${it.deviceName} does not support required extensions")
                    it.cleanup()
                }

                val preferredDevice = devices[true]
                    ?.onEach { device ->
                        Logger.debug("Device ${device.deviceName} supports required extensions")
                    }?.firstOrNull { device ->
                        preferredDeviceName?.let { it == device.deviceName } ?: false
                    }

                val selectedDevice = preferredDevice ?: devices[true]?.first()

                // cleanup
                devices[true]?.filter { it != selectedDevice }?.forEach { it.cleanup() }

                return selectedDevice?.also {
                    Logger.debug("Selected device: ${selectedDevice.deviceName}")
                } ?: throw RuntimeException("No suitable physical devices found");
            }
        }

        private fun getPhysicalDevices(instance: Instance, stack: MemoryStack): PointerBuffer {
            // Get number of physical devices
            val intBuffer = stack.mallocInt(1)
            vkCheck(vkEnumeratePhysicalDevices(instance.vkInstance, intBuffer, null),
                "Failed to get number of physical devices")
            val numDevices = intBuffer[0]
            Logger.debug("Detected {} physical device(s)", numDevices)

            // Populate physical devices list pointer
            val pPhysicalDevices = stack.mallocPointer(numDevices)
            vkCheck(vkEnumeratePhysicalDevices(instance.vkInstance, intBuffer, pPhysicalDevices),
                "Failed to get physical devices")
            return pPhysicalDevices
        }
    }
}