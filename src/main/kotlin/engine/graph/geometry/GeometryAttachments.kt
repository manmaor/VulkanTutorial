package com.maorbarak.engine.graph.geometry

import com.maorbarak.engine.graph.vk.Attachment
import com.maorbarak.engine.graph.vk.Device
import org.lwjgl.vulkan.VK11.*

class GeometryAttachments(
    device: Device,
    val width: Int,
    val height: Int
) {

    val attachments: List<Attachment>
    val depthAttachment: Attachment

    init {
        attachments = listOf(
            Attachment(device, width, height, VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT),
            Attachment(device, width, height, VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                .also { depthAttachment = it },
        )
    }

    fun cleanup() {
        attachments.forEach(Attachment::cleanup)
    }

    companion object {
        const val NUMBER_ATTACHMENTS = 2
        const val NUMBER_COLOR_ATTACHMENTS = NUMBER_ATTACHMENTS - 1 // minus the depth
    }
}