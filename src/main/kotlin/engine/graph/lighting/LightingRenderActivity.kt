package com.maorbarak.engine.graph.lighting

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.graph.VulkanModel
import com.maorbarak.engine.graph.vk.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import java.nio.LongBuffer


class LightingRenderActivity(
    private var swapChain: SwapChain,
    commandPool: CommandPool,
    val pipelineCache: PipelineCache,
    val attachments: List<Attachment>
) {
    val device: Device
    val lightingFrameBuffer: LightingFrameBuffer

    private lateinit var commandBuffers: Array<CommandBuffer>
    private lateinit var fences: Array<Fence>
    private lateinit var pipeline: Pipeline
    private lateinit var shaderProgram: ShaderProgram

    // Descriptors
    private lateinit var descriptorPool: DescriptorPool
    private lateinit var descriptorSetLayouts: Array<DescriptorSetLayout>
    private lateinit var attachmentsLayout: AttachmentsLayout
    private lateinit var attachmentsDescriptorSet: AttachmentsDescriptorSet

    init {
        device = swapChain.device
        lightingFrameBuffer = LightingFrameBuffer(swapChain)

        createShaders()
        createDescriptorPool(attachments)
        createDescriptorSets(attachments)
        createPipeline(pipelineCache)
        createCommandBuffers(commandPool, swapChain.numImages)

        (0..<swapChain.numImages).forEach { i ->
            preRecordCommandBuffer(i)
        }
    }

    fun cleanup() {
        attachmentsDescriptorSet.cleanup();
        attachmentsLayout.cleanup();
        descriptorPool.cleanup();
        pipeline.cleanup();
        lightingFrameBuffer.cleanup();
        shaderProgram.cleanup();
        commandBuffers.forEach(CommandBuffer::cleanup);
        fences.forEach(Fence::cleanup);
    }

    private fun createShaders() {
        if (EngineProperties.isShaderRecompilation) {
            ShaderCompiler.compileShaderIfChanged(LIGHTING_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
            ShaderCompiler.compileShaderIfChanged(LIGHTING_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader)
        }
        shaderProgram = ShaderProgram(device, arrayOf(
            ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, LIGHTING_VERTEX_SHADER_FILE_SPV),
            ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, LIGHTING_FRAGMENT_SHADER_FILE_SPV)
        ))
    }

    private fun createDescriptorPool(attachments: List<Attachment>) {
        descriptorPool = DescriptorPool(device, listOf(
            DescriptorPool.DescriptorTypeCount(attachments.size, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER),
        ))
    }

    private fun createDescriptorSets(attachments: List<Attachment>) {
        attachmentsLayout = AttachmentsLayout(device, attachments.size)
        descriptorSetLayouts = arrayOf(
            attachmentsLayout
        )

        attachmentsDescriptorSet = AttachmentsDescriptorSet(descriptorPool, attachmentsLayout, attachments, 0)
    }

    private fun createPipeline(pipelineCache: PipelineCache) {
        val pipelineCreationInfo = Pipeline.PipelineCreationInfo(
            lightingFrameBuffer.lightingRenderPass.vkRenderPass,
            shaderProgram,
            1,
            false,
            false,
            0,
            EmptyVertexBufferStructure(),
            descriptorSetLayouts
        )
        pipeline = Pipeline(pipelineCache, pipelineCreationInfo)
        pipelineCreationInfo.cleanup()
    }

    private fun createCommandBuffers(commandPool: CommandPool, numImages: Int) {
        commandBuffers = Array(numImages) { CommandBuffer(commandPool, true, false) }
        fences = Array(numImages) { Fence(device, true) }
    }

    private fun preRecordCommandBuffer(idx: Int) {
        MemoryStack.stackPush().use { stack ->
            val (width, height) = swapChain.swapChainExtent.run { width() to height() }

            val frameBuffer = lightingFrameBuffer.frameBuffers[idx]
            val commandBuffer = commandBuffers[idx]

            commandBuffer.reset()
            val clearValues = VkClearValue.calloc(attachments.size, stack)
            clearValues.apply {  v -> v.color().float32(0, 0f).float32(1, 0f).float32(2, 0f).float32(3, 1f) }

            val renderArea = VkRect2D.calloc(stack)
            renderArea.offset().set(0, 0)
            renderArea.extent().set(width, height)

            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(lightingFrameBuffer.lightingRenderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea(renderArea)
                .framebuffer(frameBuffer.vkFrameBuffer)

            commandBuffer.beginRecording()
            val cmdHandle = commandBuffer.vkCommandBuffer
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

            val descriptorSets = stack.mallocLong(1)
                .put(0, attachmentsDescriptorSet.vkDescriptorSet)
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline.vkPipelineLayout, 0, descriptorSets, null)

            vkCmdDraw(cmdHandle, 3, 1, 0, 0)

            vkCmdEndRenderPass(cmdHandle)
            commandBuffer.endRecording()

        }
    }

    // equivalent to recordCommandBuffer, but we are prerecorded
    fun prepareCommandBuffer() {
        val idx = swapChain.currentFrame
        val fence = fences[idx]

        fence.fenceWait()
        fence.reset()
    }

    fun submit(queue: Queue) {
        MemoryStack.stackPush().use { stack ->
            val idx = swapChain.currentFrame
            val commandBuffer = commandBuffers[idx]
            val currentFence = fences[idx]

            val syncSemaphores = swapChain.syncSemaphoresList[idx]
            queue.submit(
                stack.pointers(commandBuffer.vkCommandBuffer),
                stack.longs(syncSemaphores.geometryCompleteSemaphore.vkSemaphore),
                stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT), // states where in the pipeline execution we should wait
                stack.longs(syncSemaphores.renderCompleteSemaphore.vkSemaphore),
                currentFence
            )
        }
    }

    fun resize(swapChain: SwapChain, attachments: List<Attachment>) {
        this.swapChain = swapChain
        attachmentsDescriptorSet.update(attachments)
        lightingFrameBuffer.resize(swapChain)
        (0..<swapChain.numImages).forEach { i ->
            preRecordCommandBuffer(i)
        }
    }

//
//    fun waitForFence() {
//        val idx = swapChain.currentFrame
//        val fence = fences[idx]
//        fence.fenceWait()
//    }


    companion object {
        const val LIGHTING_FRAGMENT_SHADER_FILE_GLSL: String = "resources/shaders/lighting_fragment.glsl"
        const val LIGHTING_FRAGMENT_SHADER_FILE_SPV: String = "$LIGHTING_FRAGMENT_SHADER_FILE_GLSL.spv"
        const val LIGHTING_VERTEX_SHADER_FILE_GLSL: String = "resources/shaders/lighting_vertex.glsl"
        const val LIGHTING_VERTEX_SHADER_FILE_SPV: String = "$LIGHTING_VERTEX_SHADER_FILE_GLSL.spv"
    }
}