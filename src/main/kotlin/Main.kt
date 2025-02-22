package com.maorbarak

import com.maorbarak.engine.Engine
import com.maorbarak.engine.IAppLogic
import com.maorbarak.engine.Window
import com.maorbarak.engine.graph.Render
import com.maorbarak.engine.scene.Entity
import com.maorbarak.engine.scene.Light
import com.maorbarak.engine.scene.ModelLoader
import com.maorbarak.engine.scene.Scene
import org.joml.Math
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW


class Main: IAppLogic {

    private val MOUSE_SENSITIVITY: Float = 0.1f
    private val MOVEMENT_SPEED: Float = 0.01f

    private val rotatingAngle = Vector3f(1f, 1f, 1f)
//    private lateinit var cubeEntity: Entity
    private lateinit var sponzaEntry: Entity

    private lateinit var directionalLight: Light
    private var angleInc = 0f
    private var lightAngle = 0f


    override fun cleanup() {
    }

    override fun init(window: Window, scene: Scene, render: Render) {

//        val modelId = "CubeModel"
//        val modelData = ModelLoader.loadModel(modelId, "resources/models/cube/cube.obj", "resources/models/cube")

        val sponzaModelId = "sponza-model"
        val sponzaModelData = ModelLoader.loadModel(sponzaModelId, "resources/models/sponza/Sponza.gltf", "resources/models/sponza")

        val modelDataList = listOf(/*modelData,*/ sponzaModelData)

//        cubeEntity = Entity("CubeEntity", modelId, Vector3f(0f, 0f, -2f))
        sponzaEntry = Entity("SponzaEntiry", sponzaModelId, Vector3f())

//        scene.addEntity(cubeEntity)
        scene.addEntity(sponzaEntry)

        render.loadModels(modelDataList)


        // Lights
        scene.ambientLight.set(.2, .2, .2, 1.0)
        val lights = mutableListOf<Light>()
        directionalLight = Light()
        directionalLight.position.set(.0, 1.0, .0, .0)
        directionalLight.color.set(1.0, 1.0, 1.0, 1.0)
        lights.add(directionalLight)

        val light = Light()
        light.position.set(.0, 1.0, .0, 1.0)
        light.color.set(.0, 1.0, .0, 1.0)
        lights.add(light)
        scene.setLights(lights.toTypedArray())
    }

    override fun input(window: Window, scene: Scene, diffTimeMillis: Long) {
        val move = diffTimeMillis * MOVEMENT_SPEED
        val camera = scene.camera

        if (window.isKeyPressed(GLFW.GLFW_KEY_W)) {
            camera.moveForward(move)
        } else if (window.isKeyPressed(GLFW.GLFW_KEY_S)) {
            camera.moveBackwards(move)
        }

        if (window.isKeyPressed(GLFW.GLFW_KEY_A)) {
            camera.moveLeft(move)
        } else if (window.isKeyPressed(GLFW.GLFW_KEY_D)) {
            camera.moveRight(move)
        }

        if (window.isKeyPressed(GLFW.GLFW_KEY_UP)) {
            camera.moveUp(move)
        } else if (window.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
            camera.moveDown(move)
        }

        if (window.isKeyPressed(GLFW.GLFW_KEY_LEFT)) {
            angleInc -= 0.05f
        } else if (window.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) {
            angleInc += 0.05f
        } else {
            angleInc = 0f
        }
        lightAngle += angleInc
        if (lightAngle < 0) {
            lightAngle = 0f
        } else if (lightAngle > 180) {
            lightAngle = 180f
        }
        updateDirectionalLight()

        val mouseInput = window.mouseInput
        if (mouseInput.rightButtonPressed) {
            val displVec = mouseInput.displVec
            camera.addRotation(
                Math.toRadians(-displVec.x * MOUSE_SENSITIVITY),
                Math.toRadians(-displVec.y * MOUSE_SENSITIVITY))
        }
    }

    fun updateDirectionalLight() {
        val lightDirection = directionalLight.position
        lightDirection.x = 0f
        lightDirection.y = Math.sin(Math.toRadians(lightAngle))
        lightDirection.z = Math.cos(Math.toRadians(lightAngle))
        lightDirection.normalize()
        lightDirection.w = 0.0f
    }

    override fun update(window: Window, scene: Scene, diffTimeMillis: Long) {
        // update the application state
//        angle += 1f
//        if (angle >= 360) {
//            angle -= 360
//        }
//        cubeEntity.rotation.identity().rotateAxis(Math.toRadians(angle), rotatingAngle)
//        cubeEntity.updateModelMatrix()
    }

}

fun main() {
    println("Starting application")
    val engine = Engine("Vulkan Book", Main())
    engine.start()
}