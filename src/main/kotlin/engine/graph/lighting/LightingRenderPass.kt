package com.maorbarak.engine.graph.lighting

import com.maorbarak.engine.graph.geometry.GeometryAttachments
import com.maorbarak.engine.graph.geometry.GeometryRenderPass.Companion.MAX_SAMPLES
import com.maorbarak.engine.graph.vk.Device
import com.maorbarak.engine.graph.vk.SwapChain
import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*

class LightingRenderPass(
    swapChain: SwapChain
) {

    val device: Device
    val vkRenderPass: Long

    init {
        device = swapChain.device

        MemoryStack.stackPush().use { stack ->
            val attachmentDesc = VkAttachmentDescription.calloc(1, stack)

            // Color attachment
            attachmentDesc[0]
                .format(swapChain.surfaceFormat.imageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT) // geometry didn't have this
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR) // what will happen when the subpass starts
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE) // what will happen when the subpass ends
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)

            val colorReferences = VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(colorReferences.remaining())
                .pColorAttachments(colorReferences)

            val subpassDependencies = VkSubpassDependency.calloc(1, stack)
            subpassDependencies[0]
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(0)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)

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
}