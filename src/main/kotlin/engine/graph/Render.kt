package com.maorbarak.engine.graph

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.scene.Scene
import com.maorbarak.engine.Window
import com.maorbarak.engine.graph.geometry.GeometryRenderActivity
import com.maorbarak.engine.graph.lighting.LightingRenderActivity
import com.maorbarak.engine.graph.shadows.ShadowRenderActivity
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
    private val geometryRenderActivity: GeometryRenderActivity
    private val shadowRenderActivity: ShadowRenderActivity
    private val lightingRenderActivity: LightingRenderActivity
    private val pipelineCache: PipelineCache
    private val textureCache: TextureCache
    private val vulkanModels: MutableList<VulkanModel>


    init {
        instance = Instance(EngineProperties.validate)

        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, EngineProperties.physDeviceName)
        device = Device(instance, physicalDevice)

        surface = Surface(physicalDevice, window.handle)

        graphicsQueue = Queue.GraphicsQueue(device, 0)
        presentQueue = Queue.PresentQueue(device, surface, 0)

        swapChain = SwapChain(device, surface, window, EngineProperties.requestedImages, EngineProperties.vSync,
            presentQueue, listOf<Queue>(graphicsQueue))

        vulkanModels = mutableListOf()
        textureCache = TextureCache()

        commandPool = CommandPool(device, graphicsQueue.queueFamilyIndex)
        pipelineCache = PipelineCache(device)
        geometryRenderActivity = GeometryRenderActivity(swapChain, commandPool, pipelineCache, scene)
        shadowRenderActivity = ShadowRenderActivity(swapChain, pipelineCache, scene)
        val attachments = listOf(
            *geometryRenderActivity.geometryFrameBuffer.geometryAttachments.attachments.toTypedArray(),
            shadowRenderActivity.depthAttachment
        )
        lightingRenderActivity = LightingRenderActivity(swapChain, commandPool, pipelineCache, attachments, scene)

    }

    fun cleanup() {
        presentQueue.waitIdle()
        graphicsQueue.waitIdle()
        device.waitIdle()

        textureCache.cleanup()
        vulkanModels.forEach(VulkanModel::cleanup)
        pipelineCache.cleanup()
        lightingRenderActivity.cleanup()
        shadowRenderActivity.cleanup()
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
//        vulkanModels.forEach { model ->
//            model.vulkanMaterialList.sortBy { it.isTransparent }
//        }

        // Reorder models
//        vulkanModels.sortBy { model -> model.vulkanMaterialList.any { it.isTransparent } }

        geometryRenderActivity.registerModels(vulkanModels)
        shadowRenderActivity.registerModels(vulkanModels)
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

        val commandBuffer = geometryRenderActivity.beginRecording()
        geometryRenderActivity.recordCommandBuffer(commandBuffer, vulkanModels)
        shadowRenderActivity.recordCommandBuffer(commandBuffer, vulkanModels)
        geometryRenderActivity.endRecording(commandBuffer)
        geometryRenderActivity.submit(graphicsQueue)

        lightingRenderActivity.prepareCommandBuffer(shadowRenderActivity.cascadeShadows)
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
        shadowRenderActivity.resize(swapChain)
        val attachments = listOf(
            *geometryRenderActivity.geometryFrameBuffer.geometryAttachments.attachments.toTypedArray(),
            shadowRenderActivity.depthAttachment
        )
        lightingRenderActivity.resize(swapChain, attachments)
    }
}