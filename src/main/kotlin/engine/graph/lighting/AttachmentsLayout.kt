package com.maorbarak.engine.graph.lighting

import com.maorbarak.engine.graph.vk.DescriptorSetLayout
import com.maorbarak.engine.graph.vk.Device
import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.tinylog.kotlin.Logger

class AttachmentsLayout(
    device: Device,
    numAttachments: Int
): DescriptorSetLayout(device) {

    override val vkDescriptorLayout: Long

    init {
        Logger.debug("Creating Attachments Layout")

        MemoryStack.stackPush().use { stack ->
            val layoutBinding = VkDescriptorSetLayoutBinding.calloc(numAttachments, stack)
            (0..<numAttachments).forEach { i ->
                layoutBinding[i]
                    .binding(i) // Shader number of layout
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
            }

            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(layoutBinding)

            val pSetLayout = stack.mallocLong(1)
            vkCheck(vkCreateDescriptorSetLayout(device.vkDevice, layoutInfo, null, pSetLayout),
                "Failed to create descriptor set layout")
            vkDescriptorLayout = pSetLayout[0]
        }

    }


}