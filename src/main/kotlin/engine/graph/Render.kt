package com.maorbarak.engine.graph

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.scene.Scene
import com.maorbarak.engine.Window
import com.maorbarak.engine.graph.vk.*

class Render(
    val window: Window,
    scene: Scene
) {
    private val instance: Instance

    private val physicalDevice: PhysicalDevice
    private val device: Device
    private val graphicsQueue: Queue.GraphicsQueue
    private val presentQueue: Queue.PresentQueue
    private val surface: Surface
    private val swapChain: SwapChain
    private val commandPool: CommandPool
    private val fwdRenderActivity: ForwardRenderActivity


    init {
        instance = Instance(EngineProperties.validate)

        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, EngineProperties.physDeviceName)
        device = Device(physicalDevice)

        surface = Surface(physicalDevice, window.handle)

        graphicsQueue = Queue.GraphicsQueue(device, 0)
        presentQueue = Queue.PresentQueue(device, surface, 0)

        swapChain = SwapChain(device, surface, window, EngineProperties.requestedImages, EngineProperties.vSync,
            presentQueue, listOf<Queue>(graphicsQueue))

        commandPool = CommandPool(device, graphicsQueue.queueFamilyIndex)
        fwdRenderActivity = ForwardRenderActivity(swapChain, commandPool)
    }

    fun cleanup() {
        presentQueue.waitIdle()
        graphicsQueue.waitIdle()
        device.waitIdle()

        fwdRenderActivity.cleanup()
        commandPool.cleanup()
        swapChain.cleanup()
        surface.cleanup()
        device.cleanup()
        physicalDevice.cleanup()
        instance.cleanup()
    }

    fun render(scene: Scene) {
        fwdRenderActivity.waitForFence()

        val imageIndex = swapChain.acquireNextImage()
        if (imageIndex < 0) {
            return
        }

        fwdRenderActivity.submit(graphicsQueue)

        swapChain.presentImage(presentQueue, imageIndex)
    }
}