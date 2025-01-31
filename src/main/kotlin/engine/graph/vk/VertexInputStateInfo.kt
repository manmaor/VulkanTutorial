package com.maorbarak.engine.graph.vk

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;


abstract class VertexInputStateInfo {
    var vi: VkPipelineVertexInputStateCreateInfo? = null
        protected set

    open fun cleanup() {
        vi?.free()
    }
}