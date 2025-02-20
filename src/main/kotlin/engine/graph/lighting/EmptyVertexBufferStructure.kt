package com.maorbarak.engine.graph.lighting

import com.maorbarak.engine.graph.vk.VertexInputStateInfo
import org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo

class EmptyVertexBufferStructure: VertexInputStateInfo() {


    init {
        vi = VkPipelineVertexInputStateCreateInfo.calloc()

        vi!!
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            .pVertexBindingDescriptions(null)
            .pVertexAttributeDescriptions(null)
    }
}