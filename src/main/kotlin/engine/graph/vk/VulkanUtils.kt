package com.maorbarak.engine.graph.vk

import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.VK_MAX_MEMORY_TYPES
import org.lwjgl.vulkan.VK10.VK_SUCCESS
import org.lwjgl.vulkan.VK11.VK_SHADER_STAGE_VERTEX_BIT
import org.lwjgl.vulkan.VK11.vkCmdPushConstants
import org.lwjgl.vulkan.VkCommandBuffer
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

    fun copyMatrixToBuffer(vulkanBuffer: VulkanBuffer, matrix: Matrix4f, offset: Int = 0) {
        val mappedMemory = vulkanBuffer.map()
        val matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, vulkanBuffer.requestedSize.toInt())
        matrix.get(offset, matrixBuffer)
        vulkanBuffer.unMap()
    }

    fun setMatrixAsPushConstant(pipeline: Pipeline, cmdHandle: VkCommandBuffer, matrix: Matrix4f) {
        MemoryStack.stackPush().use { stack ->
            val pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE)
            matrix.get(0, pushConstantBuffer)
            vkCmdPushConstants(cmdHandle, pipeline.vkPipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer)
        }
    }

    enum class OSType {
        WINDOWS, MACOS, LINUX, OTHER
    }
}

