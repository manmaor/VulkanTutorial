package com.maorbarak.engine.graph

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.scene.Scene
import com.maorbarak.engine.Window
import com.maorbarak.engine.graph.vk.Instance

class Render(
    val window: Window,
    scene: Scene
) {
    private val instance: Instance

    init {
        instance = Instance(EngineProperties.validate)
    }

    fun render(scene: Scene) {
        // To be implemented
    }

    fun cleanup() {
        instance.cleanup()
    }
}