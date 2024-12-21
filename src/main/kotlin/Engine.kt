package com.maorbarak

object EngineProperties {
    private const val defaultUPS = 30
    private const val filename = "eng.properties"

    val ups: Int

    init {
        // Reading properties file
        ups = defaultUPS
    }
}

class Engine(
    windowTitle: String,
    val appLogic: IAppLogic
) {
    val window: Window = Window(windowTitle)
    val scene: Scene = Scene(window)
    val render: Render = Render(window, scene)

    var running: Boolean = false

    init {
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
            window.pollEvents()

            val now = System.currentTimeMillis()
            deltaUpdate += (now + initialTime) / timeU

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