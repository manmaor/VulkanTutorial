package com.maorbarak.engine.graph.vk

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

    enum class OSType {
        WINDOWS, MACOS, LINUX, OTHER
    }
}

