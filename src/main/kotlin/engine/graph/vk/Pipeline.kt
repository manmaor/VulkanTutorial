package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.*
import org.tinylog.kotlin.Logger

class Pipeline(
    pipelineCache: PipelineCache,
    pipelineCreateInfo: PipelineCreationInfo
) {

    val device: Device
    val vkPipeline: Long
    val vkPipelineLayout: Long

    init {
        Logger.debug("Creating pipeline");

        device = pipelineCache.device
        MemoryStack.stackPush().use { stack ->
            val lp = stack.mallocLong(1)

            val main = stack.UTF8("main")

            val shaderModules = pipelineCreateInfo.shaderProgram.shaderModules
            val shaderStages = VkPipelineShaderStageCreateInfo.calloc(shaderModules.size, stack)
            shaderModules.indices.forEach { i ->
                shaderStages[i]
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(shaderModules[i].shaderStage)
                    .module(shaderModules[i].handle)
                    .pName(main)
            }

            val vkPipelineInputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)

            val vkPipelineViewportStateCreateInfo = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .scissorCount(1) // discards any vert the is outside the viewport

            val vkPipelineRasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .polygonMode(VK_POLYGON_MODE_FILL) // VK_POLYGON_MODE_LINE for wireframe
                .cullMode(VK_CULL_MODE_NONE) // should it be front bit?
                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                .lineWidth(1.0f)

            val vkPipelineMultisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

            val vkPipelineDepthStencilStateCreateInfo =
                if (!pipelineCreateInfo.hasDepthAttachment) null
                else VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false)

            // blendAttState + colorBlendState defines the blending of the new image with the previous image (helps with transparencies)
            val blendAttState = VkPipelineColorBlendAttachmentState.calloc(pipelineCreateInfo.numColorAttachments, stack)
            (0..<pipelineCreateInfo.numColorAttachments).forEach { i ->
                blendAttState[i]
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(pipelineCreateInfo.useBlend)

                if (pipelineCreateInfo.useBlend) {
                    blendAttState[i]
                        .colorBlendOp(VK_BLEND_OP_ADD) // ADD = [R = Rs0 × Sr + Rd × Dr, G = Gs0 × Sg + Gd × Dg and B = Bs0 × Sb + Bd × Db.]
                        .alphaBlendOp(VK_BLEND_OP_ADD) // ADD = As0 × Sa + Ad × Da (Sa for source and Da for destination)
                        .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA) // the factor for the rgb component (Sr, Sg, Sb)
                        .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA) // controls the blend factor to be used for the RGB destination factors (Dr, Dg and Db).
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE) //  controls the blend factor to be used for the alpha source component (Sa). In our case it will be 1
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)  // it will have a zero, ignoring the alpha value of the destination color.
                }
            }
            val colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .pAttachments(blendAttState)

            // pipeline are almost immutable, here we define what could be dynamic. otherwise we will need to recreate the pipeline.
            // e.g. we don't want to recreate the pipeline when the screen resized
            val vkPipelineDynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                .pDynamicStates(stack.ints(
                    VK_DYNAMIC_STATE_VIEWPORT,
                    VK_DYNAMIC_STATE_SCISSOR
                ))

            val vkPushConstantsRange = takeIf { pipelineCreateInfo.pushConstantsSize > 0 }?.let {
                VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(pipelineCreateInfo.pushConstantsSize)
            }

            val ppLayout = stack.mallocLong(pipelineCreateInfo.descriptorSetLayouts?.size ?: 0)
            pipelineCreateInfo.descriptorSetLayouts?.forEachIndexed { i, it ->
                ppLayout.put(i, it.vkDescriptorLayout)
            }

            // pass additional parameters to the shaders (for example by using uniforms)
            val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(ppLayout)
                .pPushConstantRanges(vkPushConstantsRange)

            vkCheck(vkCreatePipelineLayout(device.vkDevice, pPipelineLayoutCreateInfo, null, lp),
                "Failed to create pipeline layout")
            vkPipelineLayout = lp[0]

            val pipeline = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pStages(shaderStages)
                .pVertexInputState(pipelineCreateInfo.vertexInputStateInfo.vi)
                .pInputAssemblyState(vkPipelineInputAssemblyStateCreateInfo)
                .pViewportState(vkPipelineViewportStateCreateInfo)
                .pRasterizationState(vkPipelineRasterizationStateCreateInfo)
                .pMultisampleState(vkPipelineMultisampleStateCreateInfo)
                .pColorBlendState(colorBlendState)
                .pDynamicState(vkPipelineDynamicStateCreateInfo)
                .layout(vkPipelineLayout)
                .renderPass(pipelineCreateInfo.vkRenderPass)

            vkPipelineDepthStencilStateCreateInfo?.let {
                pipeline.pDepthStencilState(it)
            }

            vkCheck(vkCreateGraphicsPipelines(device.vkDevice, pipelineCache.vkPipelineCache, pipeline, null, lp),
                "Error creating graphics pipeline")
            vkPipeline = lp[0]
        }
    }

    fun cleanup() {
        Logger.debug("Destroying pipeline")
        vkDestroyPipelineLayout(device.vkDevice, vkPipelineLayout, null)
        vkDestroyPipeline(device.vkDevice, vkPipeline, null)
    }



    data class PipelineCreationInfo(
        val vkRenderPass: Long,
        val shaderProgram: ShaderProgram,
        val numColorAttachments: Int,
        val hasDepthAttachment: Boolean,
        val useBlend: Boolean,
        val pushConstantsSize: Int,
        val vertexInputStateInfo: VertexInputStateInfo,
        val descriptorSetLayouts: Array<DescriptorSetLayout>?
    ) {
        fun cleanup() {
            vertexInputStateInfo.cleanup()
        }
    }
}