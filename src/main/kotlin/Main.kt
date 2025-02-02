package com.maorbarak

import com.maorbarak.engine.Engine
import com.maorbarak.engine.IAppLogic
import com.maorbarak.engine.Window
import com.maorbarak.engine.graph.Render
import com.maorbarak.engine.scene.Entity
import com.maorbarak.engine.scene.ModelData
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
        val positions = floatArrayOf(
            -0.5f, 0.5f, 0.5f,
            -0.5f, -0.5f, 0.5f,
            0.5f, -0.5f, 0.5f,
            0.5f, 0.5f, 0.5f,
            -0.5f, 0.5f, -0.5f,
            0.5f, 0.5f, -0.5f,
            -0.5f, -0.5f, -0.5f,
            0.5f, -0.5f, -0.5f,
        )
        val textCoords = floatArrayOf(
            0.0f, 0.0f,
            0.5f, 0.0f,
            1.0f, 0.0f,
            1.0f, 0.5f,
            1.0f, 1.0f,
            0.5f, 1.0f,
            0.0f, 1.0f,
            0.0f, 0.5f,
        )
        val indices = intArrayOf(
            // Front face
            0, 1, 3, 3, 1, 2,
            // Top Face
            4, 0, 3, 5, 4, 3,
            // Right face
            3, 2, 7, 5, 3, 7,
            // Left face
            6, 1, 0, 6, 0, 4,
            // Bottom face
            2, 1, 6, 2, 6, 7,
            // Back face
            7, 6, 4, 7, 4, 5,
        )


        val modelId = "CubeModel"
        val meshData = ModelData.MeshData(positions, textCoords, indices)

        val meshDataList = listOf(meshData)
        val modelData = ModelData(modelId, meshDataList)
        val modelDataList = listOf(modelData)
        render.loadModels(modelDataList)

        cubeEntity = Entity("CubeEntity", modelId, Vector3f(0f, 0f, -2f))
        scene.addEntity(cubeEntity)
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