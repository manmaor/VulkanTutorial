package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.tinylog.kotlin.Logger

abstract class DescriptorSetLayout(
    val device: Device
) {
    abstract val vkDescriptorLayout: Long

    fun cleanup() {
        Logger.debug("Destroying descriptor set layout")
        vkDestroyDescriptorSetLayout(device.vkDevice, vkDescriptorLayout, null)
    }

    open class SimpleDescriptorSetLayout(
        device: Device, descriptorType: Int, binding: Int, stage: Int
    ): DescriptorSetLayout(device) {

        final override val vkDescriptorLayout: Long

        init {
            MemoryStack.stackPush().use { stack ->
                val layoutBinding = VkDescriptorSetLayoutBinding.calloc(1, stack)
                layoutBinding[0]
                    .binding(binding) // Shader number of layout
                    .descriptorType(descriptorType)
                    .descriptorCount(1)
                    .stageFlags(stage)

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

    class SamplerDescriptorSetLayout(device: Device, binding: Int, stage: Int):
        SimpleDescriptorSetLayout(device, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, binding, stage)

    class UniformDescriptorSetLayout(device: Device, binding: Int, stage: Int):
        SimpleDescriptorSetLayout(device, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, binding, stage)

    class DynUniformDescriptorSetLayout(device: Device, binding: Int, stage: Int):
        SimpleDescriptorSetLayout(device, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, binding, stage)

    class StorageDescriptorSetLayout(device: Device, binding: Int, stage: Int):
        SimpleDescriptorSetLayout(device, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, binding, stage)

}