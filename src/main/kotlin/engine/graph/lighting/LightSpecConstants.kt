package com.maorbarak.engine.graph.lighting

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.graph.vk.GraphConstants
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkSpecializationInfo
import org.lwjgl.vulkan.VkSpecializationMapEntry
import java.nio.ByteBuffer

class LightSpecConstants {

    private val data: ByteBuffer
    private val specEntryMap: VkSpecializationMapEntry.Buffer
    val specInfo: VkSpecializationInfo

    init {
        data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH * 3 + GraphConstants.FLOAT_LENGTH)
        data.putInt(GraphConstants.SHADOW_MAP_CASCADE_COUNT)
        data.putInt( if (EngineProperties.isShadowPcf) 1 else 0)
        data.putFloat(EngineProperties.shadowBias)
        data.putInt(if (EngineProperties.isShadowDebug) 1 else 0)
        data.flip()

        specEntryMap = VkSpecializationMapEntry.calloc(4)
        specEntryMap[0]
            .constantID(0)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(0)
        specEntryMap[1]
            .constantID(1)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(GraphConstants.INT_LENGTH)
        specEntryMap[2]
            .constantID(2)
            .size(GraphConstants.FLOAT_LENGTH.toLong())
            .offset(GraphConstants.INT_LENGTH * 2)
        specEntryMap[3]
            .constantID(3)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(GraphConstants.INT_LENGTH * 2 + GraphConstants.FLOAT_LENGTH)

        specInfo = VkSpecializationInfo.calloc()
            .pData(data)
            .pMapEntries(specEntryMap)
    }

    fun cleanup() {
        MemoryUtil.memFree(specEntryMap)
        specInfo.free()
        MemoryUtil.memFree(data)
    }
}