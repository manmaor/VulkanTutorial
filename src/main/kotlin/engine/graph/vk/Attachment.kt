package com.maorbarak.engine.graph.vk

import org.lwjgl.vulkan.VK11.*

class Attachment(
    device: Device,
    width: Int,
    height: Int,
    format: Int,
    usage: Int
) {

    val image: Image
    val imageView: ImageView
    val isDepthAttachment: Boolean

    init {
        image = Image(
            device,
            Image.ImageData(
                height,
                width,
                usage = usage or VK_IMAGE_USAGE_SAMPLED_BIT,
                format = format
            )
        )

        val aspectMask = when {
            (usage and VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) > 0 -> {
                isDepthAttachment = false
                VK_IMAGE_ASPECT_COLOR_BIT
            }
            (usage and VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) > 0 -> {
                isDepthAttachment = true
                VK_IMAGE_ASPECT_DEPTH_BIT
            }
            else -> {
                isDepthAttachment = false
                0
            }
        }

        imageView = ImageView(
            device,
            image.vkImage,
            ImageView.ImageViewData(
                aspectMask,
                format = image.format
            )
        )
    }

    fun cleanup() {
        imageView.cleanup()
        image.cleanup()
    }
}