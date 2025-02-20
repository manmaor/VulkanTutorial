package com.maorbarak.engine.graph.lighting

import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.*

class AttachmentsDescriptorSet(descriptorPool: DescriptorPool, descriptorSetLayout: AttachmentsLayout, attachments: List<Attachment>, val binding: Int): DescriptorSet() {

    val device: Device
    override val vkDescriptorSet: Long
    val textureSampler: TextureSampler

    init {
        MemoryStack.stackPush().use { stack ->
           device = descriptorPool.device
            val pDescriptorSetLayout = stack.mallocLong(1)
            pDescriptorSetLayout.put(0, descriptorSetLayout.vkDescriptorLayout)
            val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool.vkDescriptorPool)
                .pSetLayouts(pDescriptorSetLayout)

            val pDescriptorSet = stack.mallocLong(1)
            vkCheck(
                vkAllocateDescriptorSets(device.vkDevice, allocInfo, pDescriptorSet),
                "Failed to create descriptor set")
            vkDescriptorSet = pDescriptorSet[0]

            textureSampler = TextureSampler(device, 1, false)

            update(attachments)
        }
    }

    fun cleanup() {
        textureSampler.cleanup()
    }

    fun update(attachments: List<Attachment>) {
        MemoryStack.stackPush().use { stack ->
            val descrBuffer = VkWriteDescriptorSet.calloc(attachments.size, stack)
            attachments.forEachIndexed { i, attachment ->
                val imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .sampler(textureSampler.vkSampler)
                    .imageView(attachment.imageView.vkImageView)

                if (attachment.isDepthAttachment) {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
                } else {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                }

                descrBuffer[i]
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding + i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo)
            }

            vkUpdateDescriptorSets(device.vkDevice, descrBuffer, null)
        }
    }

}