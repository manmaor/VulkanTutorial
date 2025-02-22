package com.maorbarak.engine.graph.geometry


import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.graph.VulkanModel
import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.graph.vk.DescriptorSetLayout
import com.maorbarak.engine.scene.Scene
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*

import org.lwjgl.vulkan.VK11.*
import java.nio.LongBuffer


class GeometryRenderActivity(
    private var swapChain: SwapChain,
    commandPool: CommandPool,
    val pipelineCache: PipelineCache,
    val scene: Scene
) {
    val device: Device
    val geometryFrameBuffer: GeometryFrameBuffer
    val materialSize: Int


    private lateinit var commandBuffers: Array<CommandBuffer>
    private lateinit var fences: Array<Fence>
    private lateinit var pipeLine: Pipeline
    private lateinit var shaderProgram: ShaderProgram

    // Descriptors
    private lateinit var descriptorPool: DescriptorPool

    private lateinit var geometryDescriptorSetLayouts: Array<DescriptorSetLayout>
    private lateinit var descriptorSetMap: MutableMap<String, TextureDescriptorSet>

    private lateinit var uniformDescriptorSetLayout: DescriptorSetLayout.UniformDescriptorSetLayout
    private lateinit var textureDescriptorSetLayout: DescriptorSetLayout.SamplerDescriptorSetLayout
    private lateinit var materialDescriptorSetLayout: DescriptorSetLayout.DynUniformDescriptorSetLayout

    private lateinit var textureSampler: TextureSampler
    private lateinit var materialsBuffer: VulkanBuffer
    private lateinit var materialsDescriptorSet: DescriptorSet.DynUniformDescriptorSet

    private lateinit var projMatrixUniform: VulkanBuffer
    private lateinit var projMatrixDescriptorSet: DescriptorSet.UniformDescriptorSet

    private lateinit var viewMatricesBuffer: Array<VulkanBuffer>
    private lateinit var viewMatricesDescriptorSets: Array<DescriptorSet.UniformDescriptorSet>

    init {
        device = swapChain.device
        geometryFrameBuffer = GeometryFrameBuffer(swapChain)
        materialSize = calcMaterialsUniformSize()
        createShaders()
        createDescriptorPool()
        createDescriptorSets(swapChain.numImages)
        createPipeline()
        createCommandBuffers(commandPool, swapChain.numImages)
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.projection.projectionMatrix)
    }

    fun calcMaterialsUniformSize(): Int {
        val minUboAlignment = device.physicalDevice.vkPhysicalDeviceProperties.limits().minUniformBufferOffsetAlignment()
        val mult = (GraphConstants.VEC4_SIZE * 9) / minUboAlignment + 1
        return (mult * minUboAlignment).toInt()
    }

    fun cleanup() {
        pipeLine.cleanup()
        materialsBuffer.cleanup()
        viewMatricesBuffer.forEach(VulkanBuffer::cleanup)
        projMatrixUniform.cleanup()
        textureSampler.cleanup()
        materialDescriptorSetLayout.cleanup()
        textureDescriptorSetLayout.cleanup()
        uniformDescriptorSetLayout.cleanup()
        descriptorPool.cleanup()
        shaderProgram.cleanup()
        geometryFrameBuffer.cleanup()
        commandBuffers.forEach(CommandBuffer::cleanup)
        fences.forEach(Fence::cleanup)
    }

    private fun createShaders() {
        if (EngineProperties.isShaderRecompilation) {
            ShaderCompiler.compileShaderIfChanged(GEOMETRY_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
            ShaderCompiler.compileShaderIfChanged(GEOMETRY_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader)
        }
        shaderProgram = ShaderProgram(device, arrayOf(
            ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, GEOMETRY_VERTEX_SHADER_FILE_SPV),
            ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, GEOMETRY_FRAGMENT_SHADER_FILE_SPV)
        ))
    }

    private fun createDescriptorPool() {
        descriptorPool = DescriptorPool(device, listOf(
            DescriptorPool.DescriptorTypeCount(swapChain.numImages + 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER),
            DescriptorPool.DescriptorTypeCount(EngineProperties.maxMaterials * 3, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER), // I think it's because in one render call we can send up maxMaterials materials to a single draw call
            DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
        ))
    }

    private fun createDescriptorSets(numImages: Int) {
        uniformDescriptorSetLayout = DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT)
        textureDescriptorSetLayout = DescriptorSetLayout.SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
        materialDescriptorSetLayout = DescriptorSetLayout.DynUniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
        geometryDescriptorSetLayouts = arrayOf(
            uniformDescriptorSetLayout,
            uniformDescriptorSetLayout,
            textureDescriptorSetLayout,
            textureDescriptorSetLayout,
            textureDescriptorSetLayout,
            materialDescriptorSetLayout
        )

        descriptorSetMap = mutableMapOf()
        textureSampler = TextureSampler(device, 1, true)
        projMatrixUniform = VulkanBuffer(device, GraphConstants.MAT4X4_SIZE.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
        projMatrixDescriptorSet = DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, projMatrixUniform, 0)

        viewMatricesBuffer = Array(numImages) { VulkanBuffer(device, GraphConstants.MAT4X4_SIZE.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) }
        viewMatricesDescriptorSets = Array(numImages) { i -> DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, viewMatricesBuffer[i], 0) }

        materialsBuffer = VulkanBuffer(device, materialSize * EngineProperties.maxMaterials.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
        materialsDescriptorSet = DescriptorSet.DynUniformDescriptorSet(descriptorPool, materialDescriptorSetLayout, materialsBuffer, 0, materialSize.toLong())
    }

    private fun createPipeline() {
        val pipelineCreationInfo = Pipeline.PipelineCreationInfo(
            geometryFrameBuffer.geometryRenderPass.vkRenderPass,
            shaderProgram,
            GeometryAttachments.NUMBER_COLOR_ATTACHMENTS,
            true,
            true,
            GraphConstants.MAT4X4_SIZE,
            VertexBufferStructure(),
            geometryDescriptorSetLayouts
        )
        pipeLine = Pipeline(pipelineCache, pipelineCreationInfo)
        pipelineCreationInfo.cleanup()
    }

    private fun createCommandBuffers(commandPool: CommandPool, numImages: Int) {
        commandBuffers = Array(numImages) { CommandBuffer(commandPool, true, false) }
        fences = Array(numImages) { Fence(device, true) }
    }

    fun registerModels(vulkanModelList: List<VulkanModel>) {
        device.waitIdle()
        var materialCount = 0
        vulkanModelList.forEach { vulkanModel ->
            vulkanModel.vulkanMaterialList.forEach materialScope@ { vulkanMaterial ->
                val materialOffset = materialCount * materialSize
                updateTextureDescriptorSet(vulkanMaterial.texture)
                updateTextureDescriptorSet(vulkanMaterial.normalMapTexture)
                updateTextureDescriptorSet(vulkanMaterial.metalRoughTexture)
                updateMaterialsBuffer(materialsBuffer, vulkanMaterial, materialOffset)
                materialCount += 1
            }
        }
    }

    private fun updateTextureDescriptorSet(texture: Texture) {
        val textureFileName  = texture.fileName
        descriptorSetMap.getOrPut(textureFileName) { TextureDescriptorSet(descriptorPool, textureDescriptorSetLayout, texture, textureSampler, 0) }
    }

    private fun updateMaterialsBuffer(vulkanBuffer: VulkanBuffer, material: VulkanModel.VulkanMaterial, offset: Int) {
        val mappedMemory = vulkanBuffer.map()
        val materialBuffer = MemoryUtil.memByteBuffer(mappedMemory, vulkanBuffer.requestedSize.toInt())
        material.diffuseColor.get(offset, materialBuffer)
        materialBuffer.apply {
            putFloat(offset + GraphConstants.FLOAT_LENGTH * 4, if (material.hasTexture) 1.0f else 0.0f)
            putFloat(offset + GraphConstants.FLOAT_LENGTH * 5, if (material.hasNormalMapTexture) 1.0f else 0.0f)
            putFloat(offset + GraphConstants.FLOAT_LENGTH * 6, if (material.hasMetalRoughTexture) 1.0f else 0.0f)
            putFloat(offset + GraphConstants.FLOAT_LENGTH * 7, material.roughnessFactor)
            putFloat(offset + GraphConstants.FLOAT_LENGTH * 8, material.metallicFactor)
        }
        vulkanBuffer.unMap()
    }

    fun recordCommandBuffer(vulkanModelList: List<VulkanModel>) {
        MemoryStack.stackPush().use { stack ->
            val (width, height) = swapChain.swapChainExtent.run { width() to height() }
            val idx = swapChain.currentFrame

            val frameBuffer = geometryFrameBuffer.frameBuffer
            val commandBuffer = commandBuffers[idx]

            commandBuffer.reset()
            val attachments = geometryFrameBuffer.geometryAttachments.attachments
            val clearValues = VkClearValue.calloc(attachments.size, stack)
            attachments.forEach {
                when {
                    it.isDepthAttachment -> clearValues.apply { v -> v.depthStencil().depth(1.0f)}
                    else -> clearValues.apply {  v -> v.color().float32(0, 0f).float32(1, 0f).float32(2, 0f).float32(3, 1f) }
                }
            }
            clearValues.flip()

            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(geometryFrameBuffer.geometryRenderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { a -> a.extent().set(width, height) }
                .framebuffer(frameBuffer.vkFrameBuffer)

            commandBuffer.beginRecording()
            val cmdHandle = commandBuffer.vkCommandBuffer
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLine.vkPipeline)

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


            val descriptorSets = stack.mallocLong(6)
                .put(0, projMatrixDescriptorSet.vkDescriptorSet)
                .put(1, viewMatricesDescriptorSets[idx].vkDescriptorSet)
                .put(5, materialsDescriptorSet.vkDescriptorSet)
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
                val normalMapDescriptorSet = descriptorSetMap[material.normalMapTexture.fileName]!!
                val metalRoughDescriptorSet = descriptorSetMap[material.metalRoughTexture.fileName]!!

                // In chap11 they moved this to the entity loop bellow
                descriptorSets.put(2, textureDescriptorSet.vkDescriptorSet)
                descriptorSets.put(3, normalMapDescriptorSet.vkDescriptorSet)
                descriptorSets.put(4, metalRoughDescriptorSet.vkDescriptorSet)

                material.vulkanMeshList.forEach meshScope@ { mesh ->
                    vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)

                    entities.forEach { entity ->
                        vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeLine.vkPipelineLayout, 0, descriptorSets, dynDescrSetOffset)

                        VulkanUtils.setMatrixAsPushConstant(pipeLine, cmdHandle, entity.modelMatrix)
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
            val commandBuffer = commandBuffers[idx]
            val currentFence = fences[idx]
            currentFence.reset()

            val syncSemaphores  = swapChain.syncSemaphoresList[idx]
            queue.submit(
                stack.pointers(commandBuffer.vkCommandBuffer),
                stack.longs(syncSemaphores.imgAcquisitionSemaphore.vkSemaphore),
                stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT), // states where in the pipeline execution we should wait
                stack.longs(syncSemaphores.geometryCompleteSemaphore.vkSemaphore),
                currentFence
            )
        }
    }

    fun waitForFence() {
        val idx = swapChain.currentFrame
        val fence = fences[idx]
        fence.fenceWait()
    }

    fun resize(swapChain: SwapChain) {
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.projection.projectionMatrix)
        this.swapChain = swapChain
        geometryFrameBuffer.resize(swapChain)
    }


    companion object {
        const val GEOMETRY_FRAGMENT_SHADER_FILE_GLSL: String = "resources/shaders/geometry_fragment.glsl"
        const val GEOMETRY_FRAGMENT_SHADER_FILE_SPV: String = "$GEOMETRY_FRAGMENT_SHADER_FILE_GLSL.spv"
        const val GEOMETRY_VERTEX_SHADER_FILE_GLSL: String = "resources/shaders/geometry_vertex.glsl"
        const val GEOMETRY_VERTEX_SHADER_FILE_SPV: String = "$GEOMETRY_VERTEX_SHADER_FILE_GLSL.spv"
    }
}