package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
import org.lwjgl.vulkan.VK10.*
import org.tinylog.kotlin.Logger


class Instance(
    validate: Boolean
) {
    private val debugUtils: VkDebugUtilsMessengerCreateInfoEXT?
    private val vkDebugHandle: Long

    val vkInstance: VkInstance

    init {
        // Create application information
        Logger.debug("Creating Vulkan instance")
        MemoryStack.stackPush().use { stack ->
            val appShortName = stack.UTF8("VulkanBook")
            val applicationInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(appShortName)
                .applicationVersion(1)
                .pEngineName(appShortName)
                .engineVersion(0)
                .apiVersion(VK_API_VERSION_1_0)

            // Validation layers
            val validationLayers = getSupportedValidationLayers()
            var supportsValidation = validate
            if (validate && validationLayers.isEmpty()) {
                Logger.warn("Request validation but no supported validation layers found. Falling back to no validation")
                supportsValidation = false
            }
            Logger.debug("Validation: $supportsValidation")

            // Set required  layers
            var requiredLayers: PointerBuffer? = null
            if (supportsValidation) {
                requiredLayers = stack.mallocPointer(validationLayers.size)
                validationLayers.forEachIndexed { index, layer ->
                    requiredLayers.put(index, stack.ASCII(layer))
                }
            }

            val instanceExtensions = getInstanceExtensions()

            // GLFW Extension
            val glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
                ?: throw RuntimeException("Failed to find the GLFW platform surface extensions")


            // GLFW, Debug layers
            val usePortability = instanceExtensions.contains(PORTABILITY_EXTENSION)
                    && VulkanUtils.os == VulkanUtils.OSType.MACOS
            val numExtensions = glfwExtensions.remaining() + (if (usePortability) 1 else 0) + (if (supportsValidation) 1 else 0)
            val requiredExtensions = stack.mallocPointer(numExtensions)
            requiredExtensions.put(glfwExtensions)
            if (supportsValidation) { requiredExtensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)) }
            if (usePortability) { requiredExtensions.put(stack.UTF8(PORTABILITY_EXTENSION)) }
            requiredExtensions.flip()

            val extension = if (supportsValidation) {
                debugUtils = createDebugCallback()
                debugUtils.address()
            } else {
                debugUtils = null
                MemoryUtil.NULL
            }

            // Create instance info
            val instanceInfo = VkInstanceCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pNext(extension)
                .pApplicationInfo(applicationInfo)
                .ppEnabledLayerNames(requiredLayers)
                .ppEnabledExtensionNames(requiredExtensions)

            if (supportsValidation) {
                instanceInfo.flags(VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR)
            }

            val pInstance = stack.mallocPointer(1)
            vkCheck(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance")
            vkInstance = VkInstance(pInstance[0], instanceInfo)

            vkDebugHandle = if (supportsValidation) {
                val longBuff = stack.mallocLong(1)
                vkCheck(vkCreateDebugUtilsMessengerEXT(vkInstance, debugUtils!!, null, longBuff), "Error creating debug utils")
                longBuff[0]
            } else {
                VK_NULL_HANDLE
            }
        }
    }

    private fun getSupportedValidationLayers(): List<String> {
        MemoryStack.stackPush().use { stack ->
            val numLayersArr = stack.callocInt(1)
            vkEnumerateInstanceLayerProperties(numLayersArr, null)
            val numLayers = numLayersArr.get(0)
            Logger.debug("Instance supports $numLayers layers")

            val propsBuf = VkLayerProperties.calloc(numLayers, stack)
            vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf)
            val supportedLayers = propsBuf.map {
                Logger.debug("Supported layer $it")
                it.layerNameString()
            }

            // Main validation layer
            if (supportedLayers.contains("VK_LAYER_KHRONOS_validation")) {
                return listOf("VK_LAYER_KHRONOS_validation")
            }

            // Fallback 1
            if (supportedLayers.contains("VK_LAYER_LUNARG_standard_validation")) {
                return listOf("VK_LAYER_LUNARG_standard_validation")
            }

            // Fallback 2 (set)
            return listOf(
                "VK_LAYER_GOOGLE_threading",
                "VK_LAYER_LUNARG_parameter_validation",
                "VK_LAYER_LUNARG_object_tracker",
                "VK_LAYER_LUNARG_core_validation",
                "VK_LAYER_GOOGLE_unique_objects"
            ).filter(supportedLayers::contains)
        }
    }

    private fun getInstanceExtensions(): Set<String> {
        MemoryStack.stackPush().use { stack ->
            val numExtensionsBuf = stack.callocInt(1)
            vkEnumerateInstanceExtensionProperties(null as String?, numExtensionsBuf, null)
            val numExtensions = numExtensionsBuf[0]
            Logger.debug("Instance supports $numExtensions extensions")

            val instanceExtensionsProps = VkExtensionProperties.calloc(numExtensions, stack)
            vkEnumerateInstanceExtensionProperties(null as String?, numExtensionsBuf, instanceExtensionsProps)
            return instanceExtensionsProps
                .map {
                    it.extensionNameString()
                        .also { name -> Logger.debug("Supported instance extension $name") }
                }
                .toSet()
        }
    }

    private fun createDebugCallback() = VkDebugUtilsMessengerCreateInfoEXT
        .calloc()
        .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
        .messageSeverity(MESSAGE_SEVERITY_BITMASK)
        .messageType(MESSAGE_TYPE_BITMASK)
        .pfnUserCallback { messageSeverity, messageTypes, pCallbackData, pUserData ->
            val callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
            val logFunc: (String) -> Unit = when {
                (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0 -> Logger::info
                (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0 -> Logger::warn
                (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0 -> Logger::error
                else -> Logger::debug
            }
            logFunc("VkDebugUtilsCallback, ${callbackData.pMessageString()}")

            return@pfnUserCallback VK_FALSE
        }

    fun cleanup() {
        Logger.debug("Destroying Vulkan instance")
        if(vkDebugHandle != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(vkInstance, vkDebugHandle, null)
        }
        debugUtils?.run {
            pfnUserCallback().free()
            free()
        }
        vkDestroyInstance(vkInstance, null)
    }

    companion object {
        private const val PORTABILITY_EXTENSION = KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME
        const val MESSAGE_SEVERITY_BITMASK = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
        const val MESSAGE_TYPE_BITMASK = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
    }
}