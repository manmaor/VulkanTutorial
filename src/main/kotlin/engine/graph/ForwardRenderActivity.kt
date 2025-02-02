package com.maorbarak.engine.graph

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.graph.vk.Queue
import com.maorbarak.engine.scene.Scene
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import java.nio.ByteBuffer

class ForwardRenderActivity(
    private var swapChain: SwapChain,
    commandPool: CommandPool,
    pipelineCache: PipelineCache,
    val scene: Scene
) {

    val commandBuffers: List<CommandBuffer>
    val device: Device
    val fences: List<Fence>

    val fwdShaderProgram: ShaderProgram
    val pipeline: Pipeline
    val renderPass: SwapChainRenderPass

    // some tutorials are using incorrectly only one depth buffer, this is wrong because image creation can overlap
    // origin: chapter 7, Putting it all up together
    private lateinit var depthAttachments: Array<Attachment>
    private lateinit var frameBuffers: List<FrameBuffer>

    init {
        device = swapChain.device

        MemoryStack.stackPush().use { stack ->
            val device = swapChain.device
            val imageViews = swapChain.imageViews

            createDepthImages()
            renderPass = SwapChainRenderPass(swapChain, depthAttachments[0].image.format)
            createFrameBuffers()

            if (EngineProperties.isShaderRecompilation) {
                ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
                ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader)
            }
            fwdShaderProgram = ShaderProgram(device, arrayOf(
                ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, VERTEX_SHADER_FILE_SPV),
                ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, FRAGMENT_SHADER_FILE_SPV),
            ))
            val pipelineCreationInfo = Pipeline.PipelineCreationInfo(
                renderPass.vkRenderPass, fwdShaderProgram, 1, true, GraphConstants.MAT4X4_SIZE*2,  VertexBufferStructure())
            pipeline = Pipeline(pipelineCache, pipelineCreationInfo)
            pipelineCreationInfo.cleanup()

            this.commandBuffers = List(imageViews.size) { CommandBuffer(commandPool, true, false) }
            this.fences = List(imageViews.size) { Fence(device, true) }
            // We are no longer pre-recording command
//            imageViews.indices.forEach { index ->
//                recordCommandBuffer(commandBuffers[index], frameBuffers[index], swapChainExtent.width(), swapChainExtent.height())
//            }
        }
    }

    fun cleanup() {
        pipeline.cleanup()
        depthAttachments.forEach(Attachment::cleanup)
        fwdShaderProgram.cleanup()
        frameBuffers.forEach(FrameBuffer::cleanup)
        renderPass.cleanup()
        commandBuffers.forEach(CommandBuffer::cleanup)
        fences.forEach(Fence::cleanup)
    }

    private fun createDepthImages() {
        val (width, height) = swapChain.swapChainExtent.run { width() to height() }
        depthAttachments = (0..<swapChain.numImages)
            .map { Attachment(device, width, height, VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) }
            .toTypedArray()
    }

    private fun createFrameBuffers() {
        MemoryStack.stackPush().use { stack ->
            val (width, height) = swapChain.swapChainExtent.run { width() to height() }
            val imageViews = swapChain.imageViews

            val pAttachments = stack.mallocLong(2)
            frameBuffers = imageViews.indices.map { i ->
                pAttachments.put(0, imageViews[i].vkImageView)
                pAttachments.put(1, depthAttachments[i].imageView.vkImageView)
                FrameBuffer(device, width, height, pAttachments, renderPass.vkRenderPass)
            }
        }
    }

    fun recordCommandBuffer(vulkanModelList: List<VulkanModel>) {
        MemoryStack.stackPush().use { stack ->
            val (width, height) = swapChain.swapChainExtent.run { width() to height() }
            val idx = swapChain.currentFrame

            val commandBuffer = commandBuffers[idx]
            val frameBuffer = frameBuffers[idx]

            commandBuffer.reset()
            val clearValues = VkClearValue.calloc(2, stack)
            clearValues.apply(0) { v -> v.color().float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1f) }
            clearValues.apply(1) { v -> v.depthStencil().depth(1.0f)}


            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { a -> a.extent().set(width, height) }
                .framebuffer(frameBuffer.vkFrameBuffer)

            commandBuffer.beginRecording()
            val cmdHandle= commandBuffer.vkCommandBuffer
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline)

            val viewport = VkViewport.calloc(1, stack)
                .x(0f)
                .y(height.toFloat())
                .height(-height.toFloat())
                .width(width.toFloat())
                .minDepth(0.0f) // should always be from 0 to 1
                .maxDepth(1.0f) // should always be from 0 to 1
            vkCmdSetViewport(cmdHandle, 0, viewport)

            val scissor = VkRect2D.calloc(1, stack)
                .extent { it.width(width).height(height) }
                .offset { it.x(0).y(0) }
            vkCmdSetScissor(cmdHandle, 0, scissor)

            val offsets = stack.mallocLong(1)
            offsets.put(0, 0.toLong())
            val vertexBuffer = stack.mallocLong(1)
            val pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE * 2)
            vulkanModelList.forEach { vulkanModel ->
                val modelId = vulkanModel.modelId
                val entities = scene.entitiesMap[modelId]
                if (entities?.isNotEmpty() != true) {
                    return@forEach
                }
                vulkanModel.vulkanMeshList.forEach { mesh ->
                    vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)

                    entities.forEach { entity ->
                        setPushConstantBuffer(cmdHandle, scene.projection.projectionMatrix, entity.modelMatrix, pushConstantBuffer)
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
                    }
                }
            }

            vkCmdEndRenderPass(cmdHandle)
            commandBuffer.endRecording()
        }
    }

    private fun setPushConstantBuffer(cmdHandle: VkCommandBuffer, projMatrix: Matrix4f, modelMatrix: Matrix4f, pushConstantBuffer: ByteBuffer) {
        projMatrix.get(pushConstantBuffer)
        modelMatrix.get(GraphConstants.MAT4X4_SIZE, pushConstantBuffer)
        vkCmdPushConstants(cmdHandle, pipeline.vkPipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer)
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

    fun resize(swapChain: SwapChain) {
        this.swapChain = swapChain
        frameBuffers.forEach(FrameBuffer::cleanup)
        depthAttachments.forEach(Attachment::cleanup)
        createDepthImages()
        createFrameBuffers()
    }

    fun waitForFence() {
        val idx = swapChain.currentFrame
        val fence = fences[idx]
        fence.fenceWait()
    }

    companion object {
        private const val FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/fwd_fragment.glsl"
        private const val FRAGMENT_SHADER_FILE_SPV = "$FRAGMENT_SHADER_FILE_GLSL.spv"
        private const val VERTEX_SHADER_FILE_GLSL = "resources/shaders/fwd_vertex.glsl"
        private const val VERTEX_SHADER_FILE_SPV = "$VERTEX_SHADER_FILE_GLSL.spv"
    }
}