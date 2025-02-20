package com.maorbarak.engine.graph

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.scene.Scene
import com.maorbarak.engine.Window
import com.maorbarak.engine.graph.geometry.GeometryRenderActivity
import com.maorbarak.engine.graph.lighting.LightingRenderActivity
import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.graph.vk.Queue
import com.maorbarak.engine.scene.ModelData
import org.tinylog.kotlin.Logger
import java.util.*

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
    private var swapChain: SwapChain
    private val commandPool: CommandPool
//    private val fwdRenderActivity: ForwardRenderActivity
    private val geometryRenderActivity: GeometryRenderActivity
    private val lightingRenderActivity: LightingRenderActivity
    private val pipelineCache: PipelineCache
    private val textureCache: TextureCache
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
        geometryRenderActivity = GeometryRenderActivity(swapChain, commandPool, pipelineCache, scene)
        lightingRenderActivity = LightingRenderActivity(swapChain, commandPool, pipelineCache,
            geometryRenderActivity.geometryFrameBuffer.geometryAttachments.attachments)
//        fwdRenderActivity = ForwardRenderActivity(swapChain, commandPool, pipelineCache, scene)
        vulkanModels = mutableListOf()
        textureCache = TextureCache()
    }

    fun cleanup() {
        presentQueue.waitIdle()
        graphicsQueue.waitIdle()
        device.waitIdle()

        textureCache.cleanup()
        vulkanModels.forEach(VulkanModel::cleanup)
        pipelineCache.cleanup()
//        fwdRenderActivity.cleanup()
        lightingRenderActivity.cleanup()
        geometryRenderActivity.cleanup()
        commandPool.cleanup()
        swapChain.cleanup()
        surface.cleanup()
        device.cleanup()
        physicalDevice.cleanup()
        instance.cleanup()
    }

    fun loadModels(modelDataList: List<ModelData>) {
        Logger.debug("Loading ${modelDataList.size} model(s)")
        vulkanModels.addAll(VulkanModel.transformModels(modelDataList, textureCache, commandPool, graphicsQueue))
        Logger.debug("Loaded ${modelDataList.size} model(s)")

        // Reorder materials inside models
        vulkanModels.forEach { model ->
            model.vulkanMaterialList.sortBy { it.isTransparent }
        }

        // Reorder models
        vulkanModels.sortBy { model -> model.vulkanMaterialList.any { it.isTransparent } }

        geometryRenderActivity.registerModels(vulkanModels)
    }

    fun render(scene: Scene) {
        if (window.width <= 0 && window.height <= 0) {
            // minimized
            return
        }
        geometryRenderActivity.waitForFence()

        val imageIndex = swapChain.acquireNextImage().takeUnless { window.resized || it < 0 } ?: run {
            window.resetResized()
            resize()
            scene.projection.resize(window.width, window.height)
            swapChain.acquireNextImage()
        }

        geometryRenderActivity.recordCommandBuffer(vulkanModels)
        geometryRenderActivity.submit(graphicsQueue)
        lightingRenderActivity.prepareCommandBuffer()
        lightingRenderActivity.submit(graphicsQueue)

        if (swapChain.presentImage(presentQueue, imageIndex)) {
            window.resized = true
        }
    }

    private fun resize() {
        device.waitIdle()
        graphicsQueue.waitIdle()

        swapChain.cleanup()

        swapChain = SwapChain(device, surface, window, EngineProperties.requestedImages, EngineProperties.vSync,
            presentQueue, listOf<Queue>(graphicsQueue))
        geometryRenderActivity.resize(swapChain)
        lightingRenderActivity.resize(swapChain, geometryRenderActivity.geometryFrameBuffer.geometryAttachments.attachments)
    }
}