package com.maorbarak.engine.graph.vk

import org.lwjgl.vulkan.VK10.VK_MAX_MEMORY_TYPES
import org.lwjgl.vulkan.VK10.VK_SUCCESS
import java.util.*

object VulkanUtils {

    val os = System
        .getProperty("os.name", "generic")
        .lowercase(Locale.ENGLISH)
        .let {
            when {
                it.contains("mac") || it.contains("darwin") -> OSType.MACOS
                it.contains("win") -> OSType.WINDOWS
                it.contains("nux") -> OSType.LINUX
                else -> OSType.OTHER
            }
        }

    fun vkCheck(err: Int, errMsg: String) {
        if (err != VK_SUCCESS) {
            throw RuntimeException("$errMsg: $err")
        }
    }

    fun memoryTypeFromProperties(physicalDevice: PhysicalDevice, typeBits: Int, reqsMask: Int): Int {
        var currentTypeBits = typeBits

        val memoryTypes = physicalDevice.vkMemoryProperties.memoryTypes()
        (0..<VK_MAX_MEMORY_TYPES).forEach {
            if ((currentTypeBits and 1) == 1 && (memoryTypes[it].propertyFlags() and reqsMask) == reqsMask) {
                return it
            }

            currentTypeBits = currentTypeBits shr 1
        }

        throw RuntimeException("Failed to find memoryType")
    }

    enum class OSType {
        WINDOWS, MACOS, LINUX, OTHER
    }
}

