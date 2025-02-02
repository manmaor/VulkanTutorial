package com.maorbarak.engine.scene

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f


class Entity(
    val id: String,
    val modelId: String,
    val position: Vector3f,
    val rotation: Quaternionf = Quaternionf(),
    val modelMatrix: Matrix4f = Matrix4f()
) {

    var scale: Float = 1f
        set(value) {
            field = value
            updateModelMatrix()
        }

    init {
        updateModelMatrix()
    }

    fun resetRotation() {
        rotation.apply {
            x = 0f; y = 0f; z = 0f; w = 0f
        }
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        position.apply {
            this.x = x
            this.y = y
            this.z = z
        }
        updateModelMatrix()
    }

    fun updateModelMatrix() {
        modelMatrix.translationRotateScale(position, rotation, scale)
    }

    override fun equals(other: Any?): Boolean {
        return other is Entity && other.id == this.id
    }
}
