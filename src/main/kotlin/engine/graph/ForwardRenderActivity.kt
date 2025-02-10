package com.maorbarak.engine.graph

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.graph.vk.Queue
import com.maorbarak.engine.scene.Scene
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*

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

    private lateinit var descriptorPool: DescriptorPool
    private lateinit var descriptorSetLayouts: Array<DescriptorSetLayout>
    private lateinit var uniformDescriptorSetLayout: DescriptorSetLayout.UniformDescriptorSetLayout
    private lateinit var textureDescriptorSetLayout: DescriptorSetLayout.SamplerDescriptorSetLayout
    private lateinit var descriptorSetMap: MutableMap<String, TextureDescriptorSet>
    private lateinit var projMatrixUniform: VulkanBuffer
    private lateinit var projMatrixDescriptorSet: DescriptorSet.UniformDescriptorSet
    private lateinit var textureSampler: TextureSampler

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
            createDescriptorSets()

            val pipelineCreationInfo = Pipeline.PipelineCreationInfo(
                renderPass.vkRenderPass, fwdShaderProgram, 1, true, GraphConstants.MAT4X4_SIZE,  VertexBufferStructure(), descriptorSetLayouts)
            pipeline = Pipeline(pipelineCache, pipelineCreationInfo)
            pipelineCreationInfo.cleanup()

            this.commandBuffers = List(imageViews.size) { CommandBuffer(commandPool, true, false) }
            this.fences = List(imageViews.size) { Fence(device, true) }
            VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.projection.projectionMatrix)


            // We are no longer pre-recording command
//            imageViews.indices.forEach { index ->
//                recordCommandBuffer(commandBuffers[index], frameBuffers[index], swapChainExtent.width(), swapChainExtent.height())
//            }

        }
    }

    fun cleanup() {
        projMatrixUniform.cleanup()
        textureSampler.cleanup()
        descriptorPool.cleanup()
        pipeline.cleanup()
        descriptorSetLayouts.forEach(DescriptorSetLayout::cleanup)
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

    private fun createDescriptorSets() {
        uniformDescriptorSetLayout = DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT)
        textureDescriptorSetLayout = DescriptorSetLayout.SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
        descriptorSetLayouts = arrayOf(
            uniformDescriptorSetLayout,
            textureDescriptorSetLayout
        )

        val descriptorTypeCounts = listOf(
            DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER),
            DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
        )
        descriptorPool = DescriptorPool(device, descriptorTypeCounts)

        descriptorSetMap = mutableMapOf()
        textureSampler = TextureSampler(device, 1, true)
        projMatrixUniform = VulkanBuffer(device, GraphConstants.MAT4X4_SIZE.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
        projMatrixDescriptorSet = DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, projMatrixUniform, 0)
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
            val descriptorSets = stack.mallocLong(2)
                .put(0, projMatrixDescriptorSet.vkDescriptorSet)
            vulkanModelList.forEach modelScope@ { vulkanModel ->
                val modelId = vulkanModel.modelId
                val entities = scene.entitiesMap[modelId]
                if (entities?.isNotEmpty() != true) {
                    return@modelScope
                }
                vulkanModel.vulkanMaterialList.forEach materialScope@ { material ->
                    if (material.vulkanMeshList.isEmpty()) {
                        return@materialScope
                    }
                    val textureDescriptorSet = descriptorSetMap[material.texture.fileName]!!
                    descriptorSets.put(1, textureDescriptorSet.vkDescriptorSet)

                    material.vulkanMeshList.forEach meshScope@ { mesh ->
                        vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                        vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                        vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)

                        entities.forEach { entity ->
                            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipelineLayout, 0, descriptorSets, null)

                            VulkanUtils.setMatrixAsPushConstant(pipeline, cmdHandle, entity.modelMatrix)
                            vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
                        }
                    }
                }
            }

            vkCmdEndRenderPass(cmdHandle)
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

    fun registerModels(vulkanModelList: List<VulkanModel>) {
        device.waitIdle()
        vulkanModelList.forEach { vulkanModel ->
            vulkanModel.vulkanMaterialList.forEach materialScope@ { vulkanMaterial ->
                if (vulkanMaterial.vulkanMeshList.isEmpty()) {
                    return@materialScope
                }
                updateTextureDescriptorSet(vulkanMaterial.texture)
            }
        }
    }

    fun updateTextureDescriptorSet(texture: Texture) {
        val fileName = texture.fileName
        descriptorSetMap.getOrPut(fileName) { TextureDescriptorSet(descriptorPool, textureDescriptorSetLayout, texture, textureSampler, 0) }
    }

    fun resize(swapChain: SwapChain) {
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.projection.projectionMatrix)
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