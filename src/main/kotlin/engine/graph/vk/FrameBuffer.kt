package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.vkCreateFramebuffer
import org.lwjgl.vulkan.VK10.vkDestroyFramebuffer
import org.lwjgl.vulkan.VkFramebufferCreateInfo
import java.nio.LongBuffer

/**
 * This is the glue between the attachment and the image
 * A Framebuffer is nothing more than the collection of the specific attachments that a render pass can use
 */
class FrameBuffer(
    private val device: Device,
    width: Int,
    height: Int,
    pAttachment: LongBuffer,
    renderPass: Long
) {

    val vkFrameBuffer: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val fci = VkFramebufferCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                .pAttachments(pAttachment)
                .width(width)
                .height(height)
                .layers(1)
                .renderPass(renderPass)

            val lp = stack.mallocLong(1)
            vkCheck(vkCreateFramebuffer(device.vkDevice, fci, null, lp),
                "Failed to create FrameBuffer")
            vkFrameBuffer = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyFramebuffer(device.vkDevice, vkFrameBuffer, null)
    }

}