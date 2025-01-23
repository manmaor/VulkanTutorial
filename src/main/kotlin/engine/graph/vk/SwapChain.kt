package com.maorbarak.engine.graph.vk

import com.maorbarak.engine.Window
import com.maorbarak.engine.graph.vk.VulkanUtils.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import org.tinylog.kotlin.Logger

class SwapChain(
    val device: Device,
    surface: Surface,
    window: Window,
    requestedImages: Int,
    vsync: Boolean,
    presentationQueue: Queue.PresentQueue,
    concurrentQueues: List<Queue>
) {

   val imageViews: List<ImageView>
   val surfaceFormat: SurfaceFormat
   val swapChainExtent: VkExtent2D
   val syncSemaphoresList: List<SyncSemaphores>
   val vkSwapChain: Long

   val numImages: Int

   var currentFrame = 0
     private set

   init {
       Logger.debug("Creating Vulkan SwapChain")

       MemoryStack.stackPush().use { stack ->

           // Get surface capabilities
           val surfCapabilities = VkSurfaceCapabilitiesKHR.calloc(stack)
           vkCheck(
               KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.physicalDevice.vkPhysicalDevice, surface.vkSurface, surfCapabilities),
               "Failed to get surface capabilities"
           )

           val requestNumImages = calcNumImages(surfCapabilities, requestedImages)

           surfaceFormat = calcSurfaceFormat(device.physicalDevice, surface)

           swapChainExtent = calcSwapChainExtent(window, surfCapabilities)

           val presentMode = if(vsync) KHRSurface.VK_PRESENT_MODE_FIFO_KHR else KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR

           val vkSwapchainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
               .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
               .surface(surface.vkSurface)
               .minImageCount(requestNumImages)
               .imageFormat(surfaceFormat.imageFormat)
               .imageColorSpace(surfaceFormat.colorSpace)
               .imageExtent(swapChainExtent)
               .imageArrayLayers(1)
               .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
               .preTransform(surfCapabilities.currentTransform())
               .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
               .clipped(true)
               .presentMode(presentMode)

           val indices = concurrentQueues
               .filter { it.queueFamilyIndex != presentationQueue.queueFamilyIndex }
               .map { it.queueFamilyIndex }

           if (indices.isNotEmpty()) {
               val intBuffer = stack.mallocInt(indices.size + 1)
               indices.forEach { intBuffer.put(it) }
               intBuffer
                   .put(presentationQueue.queueFamilyIndex)
                   .flip()

               vkSwapchainCreateInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                   .queueFamilyIndexCount(intBuffer.capacity())
                   .pQueueFamilyIndices(intBuffer)
           } else {
               vkSwapchainCreateInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
           }


           val swapchainPointer = stack.mallocLong(1)
           vkCheck(KHRSwapchain.vkCreateSwapchainKHR(device.vkDevice, vkSwapchainCreateInfo, null, swapchainPointer),
               "Failed to create swap chain")
           vkSwapChain = swapchainPointer[0]

           imageViews = createImageViews(stack, device, vkSwapChain, surfaceFormat.imageFormat)
           numImages = imageViews.size
           syncSemaphoresList = List(numImages) { SyncSemaphores(device) }
       }
   }


    fun acquireNextImage(): Int {
        MemoryStack.stackPush().use { stack ->
            val ip = stack.mallocInt(1)
            val err = KHRSwapchain.vkAcquireNextImageKHR(device.vkDevice, vkSwapChain, Long.MAX_VALUE,
                syncSemaphoresList[currentFrame].imgAcquisitionSemaphore.vkSemaphore, MemoryUtil.NULL, ip)

            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                return -1
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                //  // Not optimal but swapchain can still be used
            } else if (err != VK_SUCCESS) {
                throw RuntimeException("Failed to acquire image: $err")
            }

            return ip[0]
        }
    }

    fun presentImage(queue: Queue, imageIndex: Int): Boolean {
        var resize = false
        MemoryStack.stackPush().use { stack ->
            val present = VkPresentInfoKHR.calloc(stack)
                .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                .pWaitSemaphores(stack.longs(syncSemaphoresList[currentFrame].renderCompleteSemaphore.vkSemaphore))
                .swapchainCount(1)
                .pSwapchains(stack.longs(vkSwapChain))
                .pImageIndices(stack.ints(imageIndex))

            val err = KHRSwapchain.vkQueuePresentKHR(queue.vkQueue, present)
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                resize = true
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // Not optimal but swap chain can still be used
            } else if (err != VK_SUCCESS) {
                throw RuntimeException("Failed to present KHR: $err")
            }
        }

        currentFrame = (currentFrame + 1) % imageViews.size
        return resize
    }

    private fun calcNumImages(surfCapabilities: VkSurfaceCapabilitiesKHR, requestedImages: Int): Int {
        val (minImageCount, maxImageCount) = surfCapabilities.run { minImageCount() to maxImageCount() }
        return requestedImages.coerceIn(minImageCount, maxImageCount.takeIf { it != 0 } ?: minImageCount)
            .also {
                Logger.debug("Requested $requestedImages images, got $it images. Surface capabilities, maxImages: $maxImageCount, minImages $minImageCount")
            }
    }

    private fun calcSurfaceFormat(physicalDevice: PhysicalDevice, surface: Surface): SurfaceFormat {
        MemoryStack.stackPush().use { stack ->
            val intBuffer = stack.mallocInt(1)
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.vkPhysicalDevice, surface.vkSurface, intBuffer, null),
                "Failed to get the number surface formats")
            val numFormats = intBuffer[0].takeIf { it > 0 }
                ?: throw RuntimeException("No surface formats retrieved")

            val surfaceFormats = VkSurfaceFormatKHR.calloc(numFormats, stack)
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.vkPhysicalDevice, surface.vkSurface, intBuffer, surfaceFormats),
                "Failed to get the number surface formats")

            return surfaceFormats
                .firstOrNull { it.format() == VK_FORMAT_B8G8R8A8_SRGB && it.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR }
                ?.let {
                    SurfaceFormat(it.format(), it.colorSpace())
                } ?: SurfaceFormat(VK_FORMAT_B8G8R8A8_SRGB, surfaceFormats[0].colorSpace())
        }
    }

    private fun calcSwapChainExtent(window: Window, surfCapabilities: VkSurfaceCapabilitiesKHR): VkExtent2D {
        val result = VkExtent2D.calloc()
        if (surfCapabilities.currentExtent().width().toLong() == 0xFFFFFFFF) {
            // Surface size undefined. Set to the window size if within bounds
            val width = window.width.coerceIn(surfCapabilities.minImageExtent().width(), surfCapabilities.maxImageExtent().width())
            val height = window.height.coerceIn(surfCapabilities.minImageExtent().height(), surfCapabilities.maxImageExtent().height())

            result.set(width, height)
        } else {
            // Surface already defined, just use that for the swap chain
            result.set(surfCapabilities.currentExtent())
        }
        return result
    }

    fun cleanup() {
        Logger.debug("Destroying Vulkan SwapChain")
        swapChainExtent.free()
        imageViews.forEach(ImageView::cleanup)
        syncSemaphoresList.forEach(SyncSemaphores::cleanup)
        KHRSwapchain.vkDestroySwapchainKHR(device.vkDevice, vkSwapChain, null)
    }

    private fun createImageViews(stack: MemoryStack, device: Device, swapChain: Long, format: Int): List<ImageView> {
        val ip = stack.mallocInt(1)
        vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.vkDevice, swapChain, ip, null),
            "Failed to get number of surface images")
        val numImages = ip[0]

        val swapChainImages = stack.mallocLong(numImages)
        vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.vkDevice, swapChain, ip, swapChainImages),
            "Failed to get surface images")

        val imageData = ImageView.ImageViewData(format = format, aspectMask = VK_IMAGE_ASPECT_COLOR_BIT)
        return (0 until numImages).map { i ->
            ImageView(device, swapChainImages[i], imageData)
        }
    }

    data class SurfaceFormat(
        val imageFormat: Int,
        val colorSpace: Int
    )

    data class SyncSemaphores(
        val imgAcquisitionSemaphore: Semaphore,
        val renderCompleteSemaphore: Semaphore
    ) {
        constructor(device: Device): this(
            imgAcquisitionSemaphore = Semaphore(device = device),
            renderCompleteSemaphore = Semaphore(device = device)
        )

        fun cleanup() {
            imgAcquisitionSemaphore.cleanup()
            renderCompleteSemaphore.cleanup()
        }
    }
}