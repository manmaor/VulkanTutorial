package com.maorbarak.engine.scene

import com.maorbarak.engine.Window
import com.maorbarak.engine.graph.vk.GraphConstants
import org.joml.Vector4f

class Scene (
    val window: Window
) {

    val entitiesMap: HashMap<String, MutableList<Entity>>
    val projection: Projection
    val camera: Camera
    val ambientLight: Vector4f
    var lights: Array<Light>
        private set

    init {
        entitiesMap = hashMapOf()
        projection = Projection()
        projection.resize(window.width, window.height)
        camera = Camera()
        ambientLight = Vector4f()
        lights = emptyArray()
    }

    fun addEntity(entity: Entity) {
        val entities = entitiesMap.getOrPut(entity.modelId)  { mutableListOf() }
        entities.add(entity)
    }

    fun removeAllEntities() {
        entitiesMap.clear()
    }

    fun removeEntity(entity: Entity) {
        entitiesMap[entity.modelId]?.remove(entity)
    }

    fun setLights(lights: Array<Light>) {
        if (lights.size > GraphConstants.MAX_LIGHTS) {
            throw RuntimeException("Maximum number of lights set to:  ${GraphConstants.MAX_LIGHTS}")
        }

        this.lights = lights
    }
}