package com.maorbarak.engine.graph.lighting

import com.maorbarak.engine.graph.vk.FrameBuffer
import com.maorbarak.engine.graph.vk.SwapChain
import org.lwjgl.system.MemoryStack
import org.tinylog.kotlin.Logger

class LightingFrameBuffer(
    swapChain: SwapChain
) {

    var frameBuffers: Array<FrameBuffer>
        private set
    val lightingRenderPass: LightingRenderPass

    init {
        Logger.debug("Creating Lighting FrameBuffer")
        lightingRenderPass = LightingRenderPass(swapChain)
        frameBuffers = createFrameBuffers(swapChain)
    }

    fun cleanup() {
        Logger.debug("Destroying Lighting FrameBuffer")
        frameBuffers.forEach(FrameBuffer::cleanup)
        lightingRenderPass.cleanup()
    }

    private fun createFrameBuffers(swapChain: SwapChain): Array<FrameBuffer> {
        val (width, height) = swapChain.swapChainExtent.run { width() to height() }
        MemoryStack.stackPush().use { stack ->

            val attachmentsBuff = stack.mallocLong(1)
            return Array(swapChain.numImages) { i ->
                attachmentsBuff.put(0, swapChain.imageViews[i].vkImageView)
                FrameBuffer(swapChain.device, width, height, attachmentsBuff, lightingRenderPass.vkRenderPass )
            }
        }
    }

    fun resize(swapChain: SwapChain) {
        frameBuffers.forEach(FrameBuffer::cleanup)
        frameBuffers = createFrameBuffers(swapChain)
    }

}