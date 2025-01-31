package com.maorbarak.engine.graph

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.scene.Scene
import com.maorbarak.engine.Window
import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.scene.ModelData
import org.tinylog.kotlin.Logger

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
    private val pipelineCache: PipelineCache
    private val vulkanModels: MutableList<VulkanModel>


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
        pipelineCache = PipelineCache(device)
        fwdRenderActivity = ForwardRenderActivity(swapChain, commandPool, pipelineCache)
        vulkanModels = mutableListOf()
    }

    fun cleanup() {
        presentQueue.waitIdle()
        graphicsQueue.waitIdle()
        device.waitIdle()

        vulkanModels.forEach(VulkanModel::cleanup)
        fwdRenderActivity.cleanup()
        commandPool.cleanup()
        swapChain.cleanup()
        surface.cleanup()
        device.cleanup()
        physicalDevice.cleanup()
        instance.cleanup()
    }

    fun loadModels(modelDataList: List<ModelData>) {
        Logger.debug("Loading ${modelDataList.size} model(s)")
        vulkanModels.addAll(VulkanModel.transformModels(modelDataList, commandPool, graphicsQueue))
        Logger.debug("Loaded ${modelDataList.size} model(s)")
    }

    fun render(scene: Scene) {
        fwdRenderActivity.waitForFence()

        val imageIndex = swapChain.acquireNextImage()
        if (imageIndex < 0) {
            return
        }

        fwdRenderActivity.recordCommandBuffer(vulkanModels)
        fwdRenderActivity.submit(graphicsQueue)

        swapChain.presentImage(presentQueue, imageIndex)
    }
}