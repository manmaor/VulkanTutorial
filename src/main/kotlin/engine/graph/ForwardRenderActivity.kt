package com.maorbarak.engine.graph

import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.graph.vk.Queue
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*

class ForwardRenderActivity(
    val swapChain: SwapChain,
    commandPool: CommandPool
) {

    val commandBuffers: List<CommandBuffer>
    val fences: List<Fence>
    val frameBuffers: List<FrameBuffer>
    val renderPass: SwapChainRenderPass

    init {
        MemoryStack.stackPush().use { stack ->
            val device = swapChain.device
            val swapChainExtent = swapChain.swapChainExtent
            val imageViews = swapChain.imageViews

            renderPass = SwapChainRenderPass(swapChain)

            val pAttachments = stack.mallocLong(1)
            frameBuffers = imageViews.map {
                pAttachments.put(0, it.vkImageView)
                FrameBuffer(device, swapChainExtent.width(), swapChainExtent.height(), pAttachments, renderPass.vkRenderPass)
            }

            this.commandBuffers = List(imageViews.size) { CommandBuffer(commandPool, true, false) }
            this.fences = List(imageViews.size) { Fence(device, true) }
            imageViews.indices.forEach { index ->
                recordCommandBuffer(commandBuffers[index], frameBuffers[index], swapChainExtent.width(), swapChainExtent.height())
            }
        }
    }

    fun cleanup() {
        frameBuffers.forEach(FrameBuffer::cleanup)
        renderPass.cleanup()
        commandBuffers.forEach(CommandBuffer::cleanup)
        fences.forEach(Fence::cleanup)
    }

    private fun recordCommandBuffer(commandBuffer: CommandBuffer, frameBuffer: FrameBuffer, width: Int, height: Int) {
        MemoryStack.stackPush().use { stack ->
            val clearValues = VkClearValue.calloc(1, stack)
            clearValues.apply(0) { v -> v.color().float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1f) }
            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { a -> a.extent().set(width, height) }
                .framebuffer(frameBuffer.vkFrameBuffer)

            commandBuffer.beginRecording()
            vkCmdBeginRenderPass(commandBuffer.vkCommandBuffer, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)
            vkCmdEndRenderPass(commandBuffer.vkCommandBuffer)
            commandBuffer.endRecording()
        }
    }

    fun submit(queue: Queue) {
        MemoryStack.stackPush().use { stack ->
            val idx = swapChain.currentFrame
            val currentFence = fences[idx]
            currentFence.reset()
            val commandBuffer = commandBuffers[idx]
            val syncSemaphores  = swapChain.syncSemaphoresList[idx]
            queue.submit(
                stack.pointers(commandBuffer.vkCommandBuffer),
                stack.longs(syncSemaphores.imgAcquisitionSemaphore.vkSemaphore),
                stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT), // states where in the pipeline execution we should wait
                stack.longs(syncSemaphores.renderCompleteSemaphore.vkSemaphore),
                currentFence
            )
        }
    }

    fun waitForFence() {
        val idx = swapChain.currentFrame
        val fence = fences[idx]
        fence.fenceWait()
    }
}