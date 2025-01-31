package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkShaderModuleCreateInfo

import java.io.*
import java.nio.file.Files

import org.lwjgl.vulkan.VK11.*
import org.tinylog.kotlin.Logger

class ShaderProgram(
    val device: Device,
    shaderModuleData: Array<ShaderModuleData>
) {

    val shaderModules: List<ShaderModule>

    init {
        try {
            shaderModules = shaderModuleData.map { data ->
                val bytes = Files.readAllBytes(File(data.shaderSpvFile).toPath())
                val moduleHandle = createShaderModule(bytes)
                ShaderModule(data.shaderStage, moduleHandle)
            }
        } catch (e: IOException) {
            Logger.error("Error reading shader files", e)
            throw RuntimeException(e)
        }
    }

    fun cleanup() {
        shaderModules.forEach {
            vkDestroyShaderModule(device.vkDevice, it.handle, null)
        }
    }

    private fun createShaderModule(code: ByteArray): Long {
        MemoryStack.stackPush().use { stack ->
            val pCode = stack.malloc(code.size).put(0, code)

            val moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(pCode)

            val lp = stack.mallocLong(1)
            vkCheck(VK10.vkCreateShaderModule(device.vkDevice, moduleCreateInfo, null, lp),
                "Failed to create shader module")

            return lp[0]
        }
    }


    data class ShaderModule (val shaderStage: Int, val handle: Long)

    data class ShaderModuleData(val shaderStage: Int, val shaderSpvFile: String)
}