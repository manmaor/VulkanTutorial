package com.maorbarak.engine.graph.vk

import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*

class VertexBufferStructure: VertexInputStateInfo() {

    val viAttrs: VkVertexInputAttributeDescription.Buffer
    val viBindings: VkVertexInputBindingDescription.Buffer

    init {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES)
        viBindings = VkVertexInputBindingDescription.calloc(1)
        vi = VkPipelineVertexInputStateCreateInfo.calloc()

        var i = 0
        // Position
        viAttrs[i]
            .binding(0) // This will be used later on the shaders
            .location(i)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(0)

        i += 1
        // Normals
        viAttrs[i]
            .binding(0)
            .location(i)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH)

        i += 1
        // Tangents
        viAttrs[i]
            .binding(0)
            .location(i)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(NORMAL_COMPONENTS * GraphConstants.FLOAT_LENGTH + POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH)

        i += 1
        // BiTangents
        viAttrs[i]
            .binding(0)
            .location(i)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(NORMAL_COMPONENTS  * GraphConstants.FLOAT_LENGTH * 2 + POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH)

        i += 1
        // Texture coordinates
        viAttrs[i]
            .binding(0)
            .location(i)
            .format(VK_FORMAT_R32G32_SFLOAT)
            .offset(NORMAL_COMPONENTS  * GraphConstants.FLOAT_LENGTH * 3 + POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH)


        viBindings[0]
            .binding(0)
            .stride(
                POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH +
                        NORMAL_COMPONENTS * GraphConstants.FLOAT_LENGTH * 3 +
                        TEXT_COORD_COMPONENTS * GraphConstants.FLOAT_LENGTH
            )
            .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

        vi!!
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            .pVertexBindingDescriptions(viBindings)
            .pVertexAttributeDescriptions(viAttrs)
    }

    override fun cleanup() {
        super.cleanup()
        viBindings.free()
        viAttrs.free()
    }

    companion object {
        const val NUMBER_OF_ATTRIBUTES = 5
        const val POSITION_COMPONENTS = 3
        const val NORMAL_COMPONENTS = 3
        const val TEXT_COORD_COMPONENTS = 2
    }
}