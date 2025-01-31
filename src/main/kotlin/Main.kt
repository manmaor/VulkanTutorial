package com.maorbarak

import com.maorbarak.engine.Engine
import com.maorbarak.engine.IAppLogic
import com.maorbarak.engine.graph.Render
import com.maorbarak.engine.scene.Scene
import com.maorbarak.engine.Window
import com.maorbarak.engine.scene.ModelData

class Main: IAppLogic {
    override fun cleanup() {
    }

    override fun init(window: Window, scene: Scene, render: Render) {
        val modelId = "TriangleModel"
        val meshData = ModelData.MeshData(floatArrayOf(
            -0.5f, -0.5f, 0.0f,
            0.0f, 0.5f, 0.0f,
            0.5f, -0.5f, 0.0f
        ), intArrayOf(
            0, 1, 2
        ))

        val meshDataList = listOf(meshData)
        val modelData = ModelData(modelId, meshDataList)
        val modelDataList = listOf(modelData)
        render.loadModels(modelDataList)
    }

    override fun input(window: Window, scene: Scene, diffTimeMillis: Long) {
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