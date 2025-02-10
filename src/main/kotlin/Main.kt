package com.maorbarak

import com.maorbarak.engine.Engine
import com.maorbarak.engine.IAppLogic
import com.maorbarak.engine.Window
import com.maorbarak.engine.graph.Render
import com.maorbarak.engine.scene.Entity
import com.maorbarak.engine.scene.ModelLoader
import com.maorbarak.engine.scene.Scene
import org.joml.Math
import org.joml.Vector3f

class Main: IAppLogic {

    private val rotatingAngle = Vector3f(1f, 1f, 1f)
    private var angle = 0f
    private lateinit var cubeEntity: Entity

    override fun cleanup() {
    }

    override fun init(window: Window, scene: Scene, render: Render) {



        val modelId = "CubeModel"
        val modelData = ModelLoader.loadModel(modelId, "resources/models/cube/cube.obj", "resources/models/cube")
        val modelDataList = listOf(modelData)

        cubeEntity = Entity("CubeEntity", modelId, Vector3f(0f, 0f, -2f))
        scene.addEntity(cubeEntity)

        render.loadModels(modelDataList)
    }

    override fun input(window: Window, scene: Scene, diffTimeMillis: Long) {
    }

    override fun update(window: Window, scene: Scene, diffTimeMillis: Long) {
        // update the application state
        angle += 1f
        if (angle >= 360) {
            angle -= 360
        }
        cubeEntity.rotation.identity().rotateAxis(Math.toRadians(angle), rotatingAngle)
        cubeEntity.updateModelMatrix()
    }

}

fun main() {
    println("Starting application")
    val engine = Engine("Vulkan Book", Main())
    engine.start()
}