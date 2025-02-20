package com.maorbarak.engine.graph.fwd

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.graph.VulkanModel
import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.graph.vk.Queue
import com.maorbarak.engine.scene.Scene
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import java.nio.LongBuffer

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

    private lateinit var textureDescriptorSetLayout: DescriptorSetLayout.SamplerDescriptorSetLayout
    private lateinit var textureSampler: TextureSampler
    private lateinit var descriptorSetMap: MutableMap<String, TextureDescriptorSet>

    private lateinit var uniformDescriptorSetLayout: DescriptorSetLayout.UniformDescriptorSetLayout
    private lateinit var projMatrixUniform: VulkanBuffer
    private lateinit var projMatrixDescriptorSet: DescriptorSet.UniformDescriptorSet
    private lateinit var viewMatricesBuffer: Array<VulkanBuffer>
    private lateinit var viewMatricesDescriptorSets: Array<DescriptorSet.UniformDescriptorSet>

    private lateinit var materialDescriptorSetLayout: DescriptorSetLayout.DynUniformDescriptorSetLayout
    private var materialSize: Int
    private lateinit var materialsBuffer: VulkanBuffer
    private lateinit var materialDescriptorSet: DescriptorSet.DynUniformDescriptorSet


    init {
        device = swapChain.device

        MemoryStack.stackPush().use { stack ->
            val device = swapChain.device
            val imageViews = swapChain.imageViews

            materialSize = calcMaterialsUniformSize()

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
            createDescriptorSets(swapChain.imageViews.size)

            val pipelineCreationInfo = Pipeline.PipelineCreationInfo(
                renderPass.vkRenderPass, fwdShaderProgram, 1, true, true, GraphConstants.MAT4X4_SIZE,  VertexBufferStructure(), descriptorSetLayouts)
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
        materialsBuffer.cleanup()
        viewMatricesBuffer.forEach(VulkanBuffer::cleanup)
        projMatrixUniform.cleanup()
        textureSampler.cleanup()
        descriptorPool.cleanup()
        pipeline.cleanup()
        uniformDescriptorSetLayout.cleanup()
        textureDescriptorSetLayout.cleanup()
        materialDescriptorSetLayout.cleanup()
        depthAttachments.forEach(Attachment::cleanup)
        fwdShaderProgram.cleanup()
        frameBuffers.forEach(FrameBuffer::cleanup)
        renderPass.cleanup()
        commandBuffers.forEach(CommandBuffer::cleanup)
        fences.forEach(Fence::cleanup)
    }

    private fun calcMaterialsUniformSize(): Int {
        val minUboAlignment = device.physicalDevice.vkPhysicalDeviceProperties.limits().minUniformBufferOffsetAlignment()
        // As dor my understanding we need to know how many grid size to use for each batch
        // 1 batch = 9 Vec4
        // 1 batch / gridSize(minUboAlignment) = number of grids
        // +1 for buffer
        val mult = (GraphConstants.VEC4_SIZE * 9) / minUboAlignment + 1

        // translate the batch size (number of grid lines) to the actual size
        return (mult * minUboAlignment).toInt()
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

    private fun createDescriptorSets(numImages: Int) {
        uniformDescriptorSetLayout = DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT)
        textureDescriptorSetLayout = DescriptorSetLayout.SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
        materialDescriptorSetLayout = DescriptorSetLayout.DynUniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
        descriptorSetLayouts = arrayOf(
            uniformDescriptorSetLayout,
            uniformDescriptorSetLayout,
            textureDescriptorSetLayout,
            materialDescriptorSetLayout
        )

        val descriptorTypeCounts = listOf(
            DescriptorPool.DescriptorTypeCount(numImages + 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER),
            DescriptorPool.DescriptorTypeCount(EngineProperties.maxMaterials, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER),
            DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC),
        )
        descriptorPool = DescriptorPool(device, descriptorTypeCounts)

        descriptorSetMap = mutableMapOf()
        textureSampler = TextureSampler(device, 1, true)
        projMatrixUniform = VulkanBuffer(device, GraphConstants.MAT4X4_SIZE.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
        projMatrixDescriptorSet = DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, projMatrixUniform, 0)

        viewMatricesBuffer = Array(numImages) { VulkanBuffer(device, GraphConstants.MAT4X4_SIZE.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) }
        viewMatricesDescriptorSets = Array(numImages) { i -> DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, viewMatricesBuffer[i], 0) }

        materialsBuffer = VulkanBuffer(device, (materialSize * EngineProperties.maxMaterials).toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
        materialDescriptorSet = DescriptorSet.DynUniformDescriptorSet(descriptorPool, materialDescriptorSetLayout, materialsBuffer, 0, materialSize.toLong())
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


            val descriptorSets = stack.mallocLong(4)
                .put(0, projMatrixDescriptorSet.vkDescriptorSet)
                .put(1, viewMatricesDescriptorSets[idx].vkDescriptorSet)
                .put(3, materialDescriptorSet.vkDescriptorSet)
            VulkanUtils.copyMatrixToBuffer(viewMatricesBuffer[idx], scene.camera.viewMatrix)

           recordEntities(stack, cmdHandle, descriptorSets, vulkanModelList)

            vkCmdEndRenderPass(cmdHandle)
            commandBuffer.endRecording()
        }
    }

    private fun recordEntities(stack: MemoryStack, cmdHandle: VkCommandBuffer, descriptorSets: LongBuffer, vulkanModelList: List<VulkanModel>) {
        val offsets = stack.mallocLong(1)
        offsets.put(0, 0.toLong())
        val vertexBuffer = stack.mallocLong(1)
        val dynDescrSetOffset = stack.callocInt(1)
        var materialCount = 0

        vulkanModelList.forEach modelScope@ { vulkanModel ->
            val modelId = vulkanModel.modelId
            val entities = scene.entitiesMap[modelId]
            if (entities?.isNotEmpty() != true) {
                materialCount += vulkanModel.vulkanMaterialList.size
                return@modelScope
            }
            vulkanModel.vulkanMaterialList.forEach materialScope@ { material ->
                if (material.vulkanMeshList.isEmpty()) {
                    materialCount += 1
                    return@materialScope
                }
                val materialOffset = materialCount * materialSize
                dynDescrSetOffset.put(0, materialOffset)
                val textureDescriptorSet = descriptorSetMap[material.texture.fileName]!!
                descriptorSets.put(2, textureDescriptorSet.vkDescriptorSet)

                material.vulkanMeshList.forEach meshScope@ { mesh ->
                    vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)

                    entities.forEach { entity ->
                        vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeline.vkPipelineLayout, 0, descriptorSets, dynDescrSetOffset)

                        VulkanUtils.setMatrixAsPushConstant(pipeline, cmdHandle, entity.modelMatrix)
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
                    }
                }
                materialCount += 1
            }
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
        var materialCount = 0
        vulkanModelList.forEach { vulkanModel ->
            vulkanModel.vulkanMaterialList.forEach materialScope@ { vulkanMaterial ->
                val materialOffset = materialCount * materialSize
                updateTextureDescriptorSet(vulkanMaterial.texture)
                updateMaterialsBuffer(materialsBuffer, vulkanMaterial, materialOffset)
                materialCount += 1
//                if (vulkanMaterial.vulkanMeshList.isEmpty()) {
//                    return@materialScope
//                }
//                updateTextureDescriptorSet(vulkanMaterial.texture)
            }
        }
    }

    private fun updateTextureDescriptorSet(texture: Texture) {
        val fileName = texture.fileName
        descriptorSetMap.getOrPut(fileName) { TextureDescriptorSet(descriptorPool, textureDescriptorSetLayout, texture, textureSampler, 0) }
    }

    private fun updateMaterialsBuffer(vulkanBuffer: VulkanBuffer, material: VulkanModel.VulkanMaterial, offset: Int) {
        val mappedMemory = vulkanBuffer.map()
        val materialBuffer = MemoryUtil.memByteBuffer(mappedMemory, vulkanBuffer.requestedSize.toInt())
        material.diffuseColor.get(offset, materialBuffer)
        vulkanBuffer.unMap()
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