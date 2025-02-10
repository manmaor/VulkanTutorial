package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import org.tinylog.kotlin.Logger

// Pool -> Layout -> Set?
class DescriptorPool(
    val device: Device,
    descriptorTypeCounts: List<DescriptorTypeCount>
) {
    val vkDescriptorPool: Long

    init {
        Logger.debug("Creating descriptor pool")
        MemoryStack.stackPush().use { stack ->
            val maxSets = descriptorTypeCounts.fold(0) {acc, it -> acc + it.count}
            val typeCounts = VkDescriptorPoolSize.calloc(descriptorTypeCounts.size, stack)
            descriptorTypeCounts.forEachIndexed { i, it ->
                typeCounts[i]
                    .type(it.descriptorType)
                    .descriptorCount(it.count)
            }

            val descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT) // this will enable up to return unused sets to the pool (by calling freeDescriptorSet)
                .pPoolSizes(typeCounts)
                .maxSets(maxSets)

            val pDescriptorPool = stack.mallocLong(1)
            vkCheck(vkCreateDescriptorPool(device.vkDevice, descriptorPoolInfo, null, pDescriptorPool),
                "Failed to create descriptor pool")
            vkDescriptorPool = pDescriptorPool[0]
        }
    }

    fun cleanup() {
        Logger.debug("Destroying descriptor pool")
        vkDestroyDescriptorPool(device.vkDevice, vkDescriptorPool, null)
    }

    fun freeDescriptorSet(vkDescriptorSet: Long) {
        MemoryStack.stackPush().use { stack ->
            val longBuffer = stack.mallocLong(1)
            longBuffer.put(0, vkDescriptorSet)

            vkCheck(vkFreeDescriptorSets(device.vkDevice, vkDescriptorPool, longBuffer),
                "Failed to free descriptor set")
        }
    }

    data class DescriptorTypeCount(val count: Int, val descriptorType: Int)
}