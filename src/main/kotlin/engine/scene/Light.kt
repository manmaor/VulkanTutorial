package com.maorbarak.engine.scene

import org.joml.Vector4f

class Light {
    val color = Vector4f(0f, 0f, 0f, 0f)

    /**
     * For directional lights, the "w" coordinate will be 0. For point lights it will be "1". For directional lights
     * this attribute should be read as a direction.
     */
    val position = Vector4f(0f, 0f, 0f, 0f)
}