package com.maorbarak.engine.graph.shadows

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.graph.vk.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.tinylog.kotlin.Logger

class ShadowsFrameBuffer(
    val device: Device
) {

    val depthAttachment: Attachment
    val shadowsRenderPass: ShadowsRenderPass
    val frameBuffer: FrameBuffer

    init {
        Logger.debug("Creating ShadowsFrameBuffer")
        MemoryStack.stackPush().use { stack ->
            val shadowMapSize: Int = EngineProperties.shadowMapSize
            depthAttachment = Attachment(device, shadowMapSize, shadowMapSize, VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, GraphConstants.SHADOW_MAP_CASCADE_COUNT, VK_IMAGE_VIEW_TYPE_2D_ARRAY)
            shadowsRenderPass = ShadowsRenderPass(device, depthAttachment)

            val attachmentsBuff = stack.mallocLong(1)
            attachmentsBuff.put(0, depthAttachment.imageView.vkImageView)
            frameBuffer = FrameBuffer(device, shadowMapSize, shadowMapSize, attachmentsBuff, shadowsRenderPass.vkRenderPass, GraphConstants.SHADOW_MAP_CASCADE_COUNT)
        }
    }

    fun cleanup() {
        Logger.debug("Destroying ShadowsFrameBuffer")
        shadowsRenderPass.cleanup()
        depthAttachment.cleanup()
        frameBuffer.cleanup()
    }
}