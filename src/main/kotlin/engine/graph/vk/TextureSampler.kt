package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*

class TextureSampler(
    val device: Device,
    mipLevels: Int,
    anisotropyEnabled: Boolean
) {

    val vkSampler: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val sampleInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                // magnification filter in texture lookup
                .magFilter(VK_FILTER_LINEAR)
                .minFilter(VK_FILTER_LINEAR)
                // what will be returned for a texture lookup, uvw = xyz
                // VK_SAMPLER_ADDRESS_MODE_REPEAT = the texture repeats endlessly
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                // the color for the border that will be used for texture lookups beyond bounds
                // when VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER is used in the addressMode[X]
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                // if false the range is [0, 1]
                // if false then [0, width][0,height]
                .unnormalizedCoordinates(false)
                // enables a comparison when performing texture lookups
                .compareEnable(false)
                // specifies the comparison operation ????
                .compareOp(VK_COMPARE_OP_ALWAYS)
                // specify the mipmap filter to apply in lookups
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                // mip mapping properties
                .minLod(0.0f)
                .maxLod(mipLevels.toFloat())
                .mipLodBias(0.0f)

            if (anisotropyEnabled && device.isSamplerAnisotropy) {
                sampleInfo
                    .anisotropyEnable(true)
                    .maxAnisotropy(MAX_ANISOTROPY.toFloat())
            }

            val pSampler = stack.mallocLong(1)
            vkCheck(vkCreateSampler(device.vkDevice, sampleInfo, null, pSampler),
                "Failed to create sampler")
            vkSampler = pSampler[0]
        }
    }

    fun cleanup() {
        vkDestroySampler(device.vkDevice, vkSampler, null)
    }

    companion object {
        const val MAX_ANISOTROPY = 16
    }
}