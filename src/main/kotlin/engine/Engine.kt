package com.maorbarak.engine

import com.maorbarak.engine.graph.Render
import com.maorbarak.engine.scene.Scene

class Engine(
    windowTitle: String,
    val appLogic: IAppLogic
) {
    val window: Window
    val scene: Scene
    val render: Render

    var running: Boolean = false

    init {
        window = Window(windowTitle)
        scene = Scene(window)
        render = Render(window, scene)
        appLogic.init(window, scene, render)
    }

    fun cleanup() {
        appLogic.cleanup()
        render.cleanup()
        window.cleanup()
    }

    fun start() {
        running = true
        run()
    }

    fun stop() {
        running = false
    }

    fun run() {
        var initialTime = System.currentTimeMillis()
        val timeU = 1000.0 / EngineProperties.ups
        var deltaUpdate = 0.0

        var updateTime = initialTime
        while (running && !window.shouldClose()) {
            scene.camera.hasMoved = false
            window.pollEvents()

            val now = System.currentTimeMillis()
            deltaUpdate += (now - initialTime) / timeU

            appLogic.input(window, scene, now - initialTime)

            if (deltaUpdate >= 1) {
                val diffTimeMillis = now - updateTime
                appLogic.update(window, scene, diffTimeMillis)
                updateTime = now
                deltaUpdate--
            }

            render.render(scene)

            initialTime = now
        }

        cleanup()
    }
}