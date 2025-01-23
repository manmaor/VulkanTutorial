package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkCommandBufferInheritanceInfo
import org.tinylog.kotlin.Logger

class CommandBuffer(
    private val commandPool: CommandPool,
    private val primary: Boolean,
    private val oneTimeSubmit: Boolean
) {

    val vkCommandBuffer: VkCommandBuffer

    init {
        Logger.trace("Creating command buffer")

        MemoryStack.stackPush().use { stack ->
            val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool.vkCommandPool)
                .level(if (primary) VK_COMMAND_BUFFER_LEVEL_PRIMARY else VK_COMMAND_BUFFER_LEVEL_SECONDARY)
                .commandBufferCount(1)

            val pb = stack.mallocPointer(1)
            vkCheck(vkAllocateCommandBuffers(commandPool.device.vkDevice, cmdBufAllocateInfo, pb),
                "Failed to allocate render command buffer")
            vkCommandBuffer = VkCommandBuffer(pb[0], commandPool.device.vkDevice)
        }
    }

    fun cleanup() {
        Logger.trace("Destroying command buffer")
        vkFreeCommandBuffers(commandPool.device.vkDevice, commandPool.vkCommandPool, vkCommandBuffer)
    }

    fun beginRecording(inheritanceInfo: InheritanceInfo? = null) {
        MemoryStack.stackPush().use { stack ->
            val cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)

            if (oneTimeSubmit) {
                cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
            }

            if (!primary) {
                inheritanceInfo?.let {
                    val vkInheritanceInfo = VkCommandBufferInheritanceInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO)
                        .renderPass(it.vkRenderPass)
                        .subpass(it.subPass)
                        .framebuffer(it.vkFrameBuffer)

                    cmdBufInfo
                        .pInheritanceInfo(vkInheritanceInfo)
                        .flags(VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT)

                } ?: throw RuntimeException("Secondary buffers must declare inheritance info")
            }

            vkCheck(vkBeginCommandBuffer(vkCommandBuffer, cmdBufInfo),
                "Failed to begin command buffer")
        }
    }

    fun endRecording() {
        vkCheck(vkEndCommandBuffer(vkCommandBuffer),
            "Failed to end command buffer")
    }

    fun reset() {
        vkResetCommandBuffer(vkCommandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT)
    }

    data class InheritanceInfo(
        val vkRenderPass: Long,
        val vkFrameBuffer: Long,
        val subPass: Int
    )
}