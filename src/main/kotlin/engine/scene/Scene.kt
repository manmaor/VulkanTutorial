package com.maorbarak.engine.scene

import com.maorbarak.engine.Window

class Scene (
    val window: Window
) {

    val entitiesMap: HashMap<String, MutableList<Entity>>
    val projection: Projection

    init {
        entitiesMap = hashMapOf()
        projection = Projection()
        projection.resize(window.width, window.height)
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
}