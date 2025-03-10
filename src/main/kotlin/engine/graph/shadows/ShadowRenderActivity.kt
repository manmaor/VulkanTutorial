package com.maorbarak.engine.graph.shadows

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.graph.VulkanModel
import com.maorbarak.engine.graph.geometry.GeometryAttachments
import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.scene.Scene
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import java.nio.LongBuffer

class ShadowRenderActivity(
    private var swapChain: SwapChain,
    pipelineCache: PipelineCache,
    val scene: Scene
) {

    private var firstRun: Boolean

    val device: Device
    val shadowsFrameBuffer: ShadowsFrameBuffer

    private lateinit var pipeline: Pipeline
    private lateinit var shaderProgram: ShaderProgram
    lateinit var cascadeShadows: List<CascadeShadow>
        private set
    private lateinit var descriptorPool: DescriptorPool
    private lateinit var descriptorSetLayouts: Array<DescriptorSetLayout>
    private lateinit var textureDescriptorSetLayout: DescriptorSetLayout.SamplerDescriptorSetLayout
    private lateinit var uniformDescriptorSetLayout: DescriptorSetLayout.UniformDescriptorSetLayout
    private lateinit var textureSampler: TextureSampler
    private lateinit var descriptorSetMap: MutableMap<String, TextureDescriptorSet>
    private lateinit var projMatrixDescriptorSet: Array<DescriptorSet.UniformDescriptorSet>
    private lateinit var shadowsUniforms: Array<VulkanBuffer>

    val depthAttachment
        get() = shadowsFrameBuffer.depthAttachment


    init {
        firstRun = true
        device = swapChain.device
        shadowsFrameBuffer = ShadowsFrameBuffer(device)

        createShaders()
        createDescriptorPool(swapChain.numImages)
        createDescriptorSets(swapChain.numImages)
        createPipeline(pipelineCache)
        createShadowCascades()
    }

    fun cleanup() {
        pipeline.cleanup()
        shadowsUniforms.forEach(VulkanBuffer::cleanup)
        uniformDescriptorSetLayout.cleanup();
        textureDescriptorSetLayout.cleanup();
        textureSampler.cleanup();
        descriptorPool.cleanup();
        shaderProgram.cleanup();
        shadowsFrameBuffer.cleanup();
    }

    private fun createShaders() {
        if (EngineProperties.isShaderRecompilation) {
            ShaderCompiler.compileShaderIfChanged(SHADOW_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
            ShaderCompiler.compileShaderIfChanged(SHADOW_GEOMETRY_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_geometry_shader)
            ShaderCompiler.compileShaderIfChanged(SHADOW_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader)
        }
        shaderProgram = ShaderProgram(device, arrayOf(
            ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, SHADOW_VERTEX_SHADER_FILE_SPV),
            ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_GEOMETRY_BIT, SHADOW_GEOMETRY_SHADER_FILE_SPV),
            ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, SHADOW_FRAGMENT_SHADER_FILE_SPV)
        ))
    }

    private fun createDescriptorPool(numImages: Int) {
        descriptorPool = DescriptorPool(device, listOf(
            DescriptorPool.DescriptorTypeCount(numImages, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER),
            DescriptorPool.DescriptorTypeCount(EngineProperties.maxMaterials, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER), // we need to check transparent fragments so they do not cast shadows)
        ))
    }

    private fun createDescriptorSets(numImages: Int) {
        uniformDescriptorSetLayout = DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_GEOMETRY_BIT)
        textureDescriptorSetLayout = DescriptorSetLayout.SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
        descriptorSetLayouts = arrayOf(
            uniformDescriptorSetLayout,
            textureDescriptorSetLayout,
        )

        descriptorSetMap = mutableMapOf()
        textureSampler = TextureSampler(device, 1, false)

        shadowsUniforms = Array(numImages) { VulkanBuffer(device, GraphConstants.MAT4X4_SIZE.toLong() * GraphConstants.SHADOW_MAP_CASCADE_COUNT, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0) }
        projMatrixDescriptorSet  = Array(numImages) { i -> DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, shadowsUniforms[i], 0) }
    }

    private fun createPipeline(pipelineCache: PipelineCache) {
        val pipelineCreationInfo = Pipeline.PipelineCreationInfo(
            shadowsFrameBuffer.shadowsRenderPass.vkRenderPass,
            shaderProgram,
            GeometryAttachments.NUMBER_COLOR_ATTACHMENTS,
            true,
            true,
            GraphConstants.MAT4X4_SIZE,
            VertexBufferStructure(),
            descriptorSetLayouts
        )
        pipeline = Pipeline(pipelineCache, pipelineCreationInfo)
        pipelineCreationInfo.cleanup()
    }

    private fun createShadowCascades() {
        cascadeShadows = List(GraphConstants.SHADOW_MAP_CASCADE_COUNT) { CascadeShadow() }
    }

    fun resize(swapChain: SwapChain) {
        this.swapChain = swapChain
        CascadeShadow.updateCascadeShadows(cascadeShadows, scene)
    }

    fun registerModels(vulkanModelList: List<VulkanModel>) {
        device.waitIdle()
        vulkanModelList.forEach { vulkanModel ->
            vulkanModel.vulkanMaterialList.forEach materialScope@ { vulkanMaterial ->
                updateTextureDescriptorSet(vulkanMaterial.texture)
            }
        }
    }

    private fun updateTextureDescriptorSet(texture: Texture) {
        val textureFileName  = texture.fileName
        descriptorSetMap.getOrPut(textureFileName) { TextureDescriptorSet(descriptorPool, textureDescriptorSetLayout, texture, textureSampler, 0) }
    }

    // We will use the same command buffer used while doing the geometry pass to render the depth maps.
    // Doing this way there is no need to add additionally synchronization code so the lighting phase starts when the scene and the depth maps have been properly render.
    // We can do this, because rendering the geometry information and the depth maps are independent.
    fun recordCommandBuffer(commandBuffer: CommandBuffer, vulkanModelList: List<VulkanModel>) {
        MemoryStack.stackPush().use { stack ->
            if (firstRun || scene.lightChanged || scene.camera.hasMoved) {
                CascadeShadow.updateCascadeShadows(cascadeShadows, scene)
                if (firstRun) {
                    firstRun = false
                }
            }

            val idx = swapChain.currentFrame

            updateProjViewBuffers(idx)

            val clearValues = VkClearValue.calloc(1, stack)
            clearValues.apply(0) { v -> v.depthStencil().depth(1.0f)}

            val width: Int = EngineProperties.shadowMapSize
            val height: Int = EngineProperties.shadowMapSize

            val cmdHandle = commandBuffer.vkCommandBuffer


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

            val frameBuffer = shadowsFrameBuffer.frameBuffer

            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(shadowsFrameBuffer.shadowsRenderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { a -> a.extent().set(width, height) }
                .framebuffer(frameBuffer.vkFrameBuffer)

            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline)


            val descriptorSets = stack.mallocLong(2)
                .put(0, projMatrixDescriptorSet[idx].vkDescriptorSet)

            recordEntities(stack, cmdHandle, descriptorSets, vulkanModelList)

            vkCmdEndRenderPass(cmdHandle)
        }
    }

    private fun recordEntities(stack: MemoryStack, cmdHandle: VkCommandBuffer, descriptorSets: LongBuffer, vulkanModelList: List<VulkanModel>) {
        val offsets = stack.mallocLong(1)
        offsets.put(0, 0.toLong())
        val vertexBuffer = stack.mallocLong(1)

        vulkanModelList.forEach modelScope@ { vulkanModel ->
            val modelId = vulkanModel.modelId
            val entities = scene.entitiesMap[modelId]
            if (entities?.isNotEmpty() != true) {
                return@modelScope
            }
            vulkanModel.vulkanMaterialList.forEach materialScope@ { material ->
                val textureDescriptorSet = descriptorSetMap[material.texture.fileName]!!

                // In chap11 they moved this to the entity loop bellow
                descriptorSets.put(1, textureDescriptorSet.vkDescriptorSet)

                material.vulkanMeshList.forEach meshScope@ { mesh ->
                    vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)

                    entities.forEach { entity ->
                        vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeline.vkPipelineLayout, 0, descriptorSets, null)

                        setPushConstant(pipeline, cmdHandle, entity.modelMatrix)
//                        VulkanUtils.setMatrixAsPushConstant(pipeLine, cmdHandle, entity.modelMatrix)
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
                    }
                }
            }
        }
    }

    fun updateProjViewBuffers(idx: Int) {
        var offset = 0
        cascadeShadows.forEach { cascadeShadow ->
            VulkanUtils.copyMatrixToBuffer(shadowsUniforms[idx], cascadeShadow.projViewMatrix, offset)
            offset += GraphConstants.MAT4X4_SIZE
        }
    }

    fun setPushConstant(pipeline: Pipeline, cmdHandle: VkCommandBuffer, matrix: Matrix4f) {
        VulkanUtils.setMatrixAsPushConstant(pipeline, cmdHandle, matrix)
    }

    companion object {
        const val SHADOW_FRAGMENT_SHADER_FILE_GLSL: String = "resources/shaders/shadow_fragment.glsl"
        const val SHADOW_FRAGMENT_SHADER_FILE_SPV: String = "$SHADOW_FRAGMENT_SHADER_FILE_GLSL.spv"
        const val SHADOW_GEOMETRY_SHADER_FILE_GLSL: String = "resources/shaders/shadow_geometry.glsl"
        const val SHADOW_GEOMETRY_SHADER_FILE_SPV: String = "$SHADOW_GEOMETRY_SHADER_FILE_GLSL.spv"
        const val SHADOW_VERTEX_SHADER_FILE_GLSL: String = "resources/shaders/shadow_vertex.glsl"
        const val SHADOW_VERTEX_SHADER_FILE_SPV: String = "$SHADOW_VERTEX_SHADER_FILE_GLSL.spv"
    }
}