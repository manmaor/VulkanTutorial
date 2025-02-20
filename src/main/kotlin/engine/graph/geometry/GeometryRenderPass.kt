package com.maorbarak.engine.graph.geometry

import com.maorbarak.engine.graph.vk.Attachment
import com.maorbarak.engine.graph.vk.Device
import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*

// describes the attachment and the dependencies for the following render pass
class GeometryRenderPass(
    val device: Device,
    attachment: List<Attachment>
) {

    val vkRenderPass: Long

    init {
        MemoryStack.stackPush().use { stack ->
            // Attachments operations
            var depthAttachmentPos = 0
            val attachmentDesc = VkAttachmentDescription.calloc(attachment.size, stack)
            attachment.forEachIndexed { i, attachment ->
                attachmentDesc[i]
                    .format(attachment.image.format)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR) // what will happen when the subpass starts
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE) // what will happen when the subpass ends
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .samples(MAX_SAMPLES) // ??
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)

                if (attachment.isDepthAttachment) {
                    depthAttachmentPos = i
                    attachmentDesc[i].finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
                } else {
                    attachmentDesc[i].finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                }
            }

            // Attachment references
            val colorReferences = VkAttachmentReference.calloc(GeometryAttachments.NUMBER_COLOR_ATTACHMENTS, stack)
            (0..<GeometryAttachments.NUMBER_COLOR_ATTACHMENTS).forEach { i ->
                colorReferences[i]
                    .attachment(i)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            }

            val depthReference = VkAttachmentReference.calloc(stack)
                .attachment(depthAttachmentPos)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)

            // The subpass description
            val subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .pColorAttachments(colorReferences)
                .colorAttachmentCount(colorReferences.capacity())
                .pDepthStencilAttachment(depthReference)

            // dependencies
            // it separates the execution of two blocks, the conditions defined by the combination of the srcXX parameters must be met before the part controlled by the dstXX conditions can execute.
            val subpassDependencies = VkSubpassDependency.calloc(2, stack)
            subpassDependencies[0]
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT or VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT or
                        VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)

            subpassDependencies[1]
                .srcSubpass(0)
                .dstSubpass(VK_SUBPASS_EXTERNAL)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT or VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT or
                        VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)

            // Creating the render pass
            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachmentDesc)
                .pSubpasses(subpass)
                .pDependencies(subpassDependencies)

            val lp = stack.mallocLong(1)
            vkCheck(vkCreateRenderPass(device.vkDevice, renderPassInfo, null, lp),
                "Failed to create render pass")
            vkRenderPass = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyRenderPass(device.vkDevice, vkRenderPass, null)
    }

    companion object {
        const val MAX_SAMPLES = 1
    }
}