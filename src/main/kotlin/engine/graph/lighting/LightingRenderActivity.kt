package com.maorbarak.engine.graph.lighting

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.scene.Light
import com.maorbarak.engine.scene.Scene
import org.joml.Matrix4f
import org.joml.Vector4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*


class LightingRenderActivity(
    private var swapChain: SwapChain,
    commandPool: CommandPool,
    val pipelineCache: PipelineCache,
    val attachments: List<Attachment>,
    val scene: Scene
) {
    val device: Device
    val lightingFrameBuffer: LightingFrameBuffer
    val auxVec: Vector4f

    private lateinit var commandBuffers: Array<CommandBuffer>
    private lateinit var fences: Array<Fence>
    private lateinit var pipeline: Pipeline
    private lateinit var shaderProgram: ShaderProgram

    // Descriptors
    private lateinit var descriptorPool: DescriptorPool
    private lateinit var descriptorSetLayouts: Array<DescriptorSetLayout>

    private lateinit var attachmentsLayout: AttachmentsLayout
    private lateinit var uniformDescriptorSetLayout: DescriptorSetLayout.UniformDescriptorSetLayout
    private lateinit var storageDescriptorSetLayout: DescriptorSetLayout.StorageDescriptorSetLayout

    private lateinit var attachmentsDescriptorSet: AttachmentsDescriptorSet

    private lateinit var invProjBuffer: VulkanBuffer
    private lateinit var invProjMatrixDescriptorSet: DescriptorSet.UniformDescriptorSet

    private lateinit var sceneBuffers: Array<VulkanBuffer>
    private lateinit var sceneDescriptorSets: Array<DescriptorSet.UniformDescriptorSet>

    private lateinit var lightsBuffers: Array<VulkanBuffer>
    private lateinit var lightsDescriptorSets: Array<DescriptorSet.StorageDescriptorSet>


    init {
        device = swapChain.device
        auxVec = Vector4f()
        lightingFrameBuffer = LightingFrameBuffer(swapChain)

        createShaders()
        createDescriptorPool(attachments)
        createUniforms(swapChain.numImages)
        createDescriptorSets(attachments, swapChain.numImages)
        createPipeline(pipelineCache)
        createCommandBuffers(commandPool, swapChain.numImages)
        updateInvProjMatrix()

        (0..<swapChain.numImages).forEach { i ->
            preRecordCommandBuffer(i)
        }
    }

    fun cleanup() {
        storageDescriptorSetLayout.cleanup()
        uniformDescriptorSetLayout.cleanup()
        attachmentsDescriptorSet.cleanup();
        attachmentsLayout.cleanup();
        descriptorPool.cleanup();
        sceneBuffers.forEach(VulkanBuffer::cleanup)
        lightsBuffers.forEach(VulkanBuffer::cleanup)
        pipeline.cleanup();
        invProjBuffer.cleanup()
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
            DescriptorPool.DescriptorTypeCount(swapChain.numImages + 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER),
            DescriptorPool.DescriptorTypeCount(swapChain.numImages, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
        ))
    }

    private fun createUniforms(numImages: Int) {
        invProjBuffer = VulkanBuffer(device, GraphConstants.MAT4X4_SIZE.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0)

        sceneBuffers = Array(numImages) {
            VulkanBuffer(device,
                GraphConstants.VEC4_SIZE * 2.toLong(), // ambient, lights count
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                0
            )

//            GraphConstants.INT_LENGTH.toLong() * 4 + // number of light (1 int), not sure that the rest are
//                    GraphConstants.VEC4_SIZE * 2 * GraphConstants.MAX_LIGHTS + // each light has position and color (2 vec4)
//                    GraphConstants.VEC4_SIZE // ambient light color
        }

        lightsBuffers = Array(numImages) {
            VulkanBuffer(
                device,
                // I think they forgot to shrink this
                GraphConstants.INT_LENGTH.toLong() * 4 +
                        GraphConstants.VEC4_SIZE * 2 * GraphConstants.MAX_LIGHTS +
                        GraphConstants.VEC4_SIZE,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT  ,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                0
            )
        }
    }

    private fun createDescriptorSets(attachments: List<Attachment>, numImages: Int) {
        attachmentsLayout = AttachmentsLayout(device, attachments.size)
        uniformDescriptorSetLayout = DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
        storageDescriptorSetLayout = DescriptorSetLayout.StorageDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
        descriptorSetLayouts = arrayOf(
            attachmentsLayout,
            storageDescriptorSetLayout,
            uniformDescriptorSetLayout,
            uniformDescriptorSetLayout
        )

        attachmentsDescriptorSet = AttachmentsDescriptorSet(descriptorPool, attachmentsLayout, attachments, 0)
        invProjMatrixDescriptorSet = DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, invProjBuffer, 0)

        sceneDescriptorSets = Array(numImages) { i ->
            DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, sceneBuffers[i], 0)
        }

        lightsDescriptorSets = Array(numImages) { i ->
            DescriptorSet.StorageDescriptorSet(descriptorPool, storageDescriptorSetLayout, lightsBuffers[i], 0)
        }
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

            val descriptorSets = stack.mallocLong(4)
                .put(0, attachmentsDescriptorSet.vkDescriptorSet)
                .put(1, lightsDescriptorSets[idx].vkDescriptorSet)
                .put(2, sceneDescriptorSets[idx].vkDescriptorSet)
                .put(3, invProjMatrixDescriptorSet.vkDescriptorSet)
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

        updateLights(scene.ambientLight, scene.lights, scene.camera.viewMatrix, lightsBuffers[idx], sceneBuffers[idx])
    }

    private fun updateLights(ambientLight: Vector4f, lights: Array<Light>, viewMatrix: Matrix4f, lightsBuffer: VulkanBuffer, sceneBuffer: VulkanBuffer) {
        // Lights
        var mappedMemory = lightsBuffer.map()
        var uniformBuffer = MemoryUtil.memByteBuffer(mappedMemory, lightsBuffer.requestedSize.toInt())

        var offset = 0
        lights.forEach { light ->
            auxVec.set(light.position)
            auxVec.mul(viewMatrix)
            auxVec.w = light.position.w
            auxVec.get(offset, uniformBuffer)
            offset += GraphConstants.VEC4_SIZE
            light.color.get(offset, uniformBuffer)
            offset += GraphConstants.VEC4_SIZE
        }

        lightsBuffer.unMap()


        // Scene Uniform

        mappedMemory = sceneBuffer.map()
        uniformBuffer = MemoryUtil.memByteBuffer(mappedMemory, sceneBuffer.requestedSize.toInt())

        ambientLight.get(0, uniformBuffer)
        offset = GraphConstants.VEC4_SIZE
        uniformBuffer.putInt(offset, lights.size)

        sceneBuffer.unMap()
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

        updateInvProjMatrix()

        (0..<swapChain.numImages).forEach { i ->
            preRecordCommandBuffer(i)
        }
    }

    private fun updateInvProjMatrix() {
        val invProj = Matrix4f(scene.projection.projectionMatrix).invert()
        VulkanUtils.copyMatrixToBuffer(invProjBuffer, invProj)
    }


    companion object {
        const val LIGHTING_FRAGMENT_SHADER_FILE_GLSL: String = "resources/shaders/lighting_fragment.glsl"
        const val LIGHTING_FRAGMENT_SHADER_FILE_SPV: String = "$LIGHTING_FRAGMENT_SHADER_FILE_GLSL.spv"
        const val LIGHTING_VERTEX_SHADER_FILE_GLSL: String = "resources/shaders/lighting_vertex.glsl"
        const val LIGHTING_VERTEX_SHADER_FILE_SPV: String = "$LIGHTING_VERTEX_SHADER_FILE_GLSL.spv"
    }
}