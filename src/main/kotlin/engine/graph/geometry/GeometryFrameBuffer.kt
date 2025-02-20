package com.maorbarak.engine.graph.geometry

import com.maorbarak.engine.graph.vk.FrameBuffer
import com.maorbarak.engine.graph.vk.SwapChain
import org.lwjgl.system.MemoryStack
import org.tinylog.kotlin.Logger

class GeometryFrameBuffer(
    swapChain: SwapChain
) {

    var frameBuffer: FrameBuffer
        private set
    var geometryAttachments: GeometryAttachments
        private set
    val geometryRenderPass: GeometryRenderPass

    init {
        Logger.debug("Creating GeometryFrameBuffer")
        geometryAttachments = createAttachments(swapChain)
        geometryRenderPass = GeometryRenderPass(swapChain.device, geometryAttachments.attachments)
        frameBuffer = createFrameBuffer(swapChain)
    }

    fun cleanup() {
        Logger.debug("Destroying Geometry FrameBuffer")
        geometryRenderPass.cleanup()
        geometryAttachments.cleanup()
        frameBuffer.cleanup()
    }

    private fun createAttachments(swapChain: SwapChain): GeometryAttachments {
        val (width, height) = swapChain.swapChainExtent.run { width() to height() }
        return GeometryAttachments(swapChain.device, width, height)
    }

    private fun createFrameBuffer(swapChain: SwapChain): FrameBuffer {
        MemoryStack.stackPush().use { stack ->

            val attachmentsBuff = stack.mallocLong(geometryAttachments.attachments.size)
            geometryAttachments.attachments.forEach {
                attachmentsBuff.put(it.imageView.vkImageView)
            }
            attachmentsBuff.flip()

            return FrameBuffer(swapChain.device, geometryAttachments.width, geometryAttachments.height, attachmentsBuff, geometryRenderPass.vkRenderPass )
        }
    }

    fun resize(swapChain: SwapChain) {
        frameBuffer.cleanup()
        geometryAttachments.cleanup()
        geometryAttachments = createAttachments(swapChain)
        frameBuffer = createFrameBuffer(swapChain)
    }

}