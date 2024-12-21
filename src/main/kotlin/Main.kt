package com.maorbarak

class Main: IAppLogic {
    override fun cleanup() {
    }

    override fun init(window: Window, scene: Scene, render: Render) {
    }

    override fun input(window: Window, scene: Scene, diffTimeMillis: Long) {
        // Get user input
    }

    override fun update(window: Window, scene: Scene, diffTimeMillis: Long) {
        // update the application state

    }

}

fun main() {
    println("Starting application")
    val engine = Engine("Vulkan Book", Main())
    engine.start()
}