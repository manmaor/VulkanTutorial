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

    private val surface: Surface

    private val swapChain: SwapChain

    init {
        instance = Instance(EngineProperties.validate)

        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, EngineProperties.physDeviceName)
        device = Device(physicalDevice)
        graphicsQueue = Queue.GraphicsQueue(device, 0)

        surface = Surface(physicalDevice, window.handle)

        swapChain = SwapChain(device, surface, window, EngineProperties.requestedImages, EngineProperties.vSync)

    }

    fun cleanup() {
        swapChain.cleanup()
        surface.cleanup()
        device.cleanup()
        physicalDevice.cleanup()
        instance.cleanup()
    }

    fun render(scene: Scene) {
        // To be implemented
    }
}