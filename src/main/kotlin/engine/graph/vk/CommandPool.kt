package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkCommandPoolCreateInfo
import org.tinylog.kotlin.Logger

class CommandPool(
    val device: Device,
    queueFamilyIndex: Int
){

    val vkCommandPool: Long

    init {
        Logger.debug("Creating Vulkan CommandPool")

        MemoryStack.stackPush().use { stack ->
            val cmdCreateInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(queueFamilyIndex)

            val lp = stack.mallocLong(1)
            vkCheck(vkCreateCommandPool(device.vkDevice, cmdCreateInfo, null, lp),
                "Failed to create command pool")
            vkCommandPool = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyCommandPool(device.vkDevice, vkCommandPool, null)
    }
}