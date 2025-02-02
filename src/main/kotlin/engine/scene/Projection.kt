package com.maorbarak.engine.scene

import com.maorbarak.engine.EngineProperties
import org.joml.Matrix4f

class Projection {
    val projectionMatrix = Matrix4f()

    fun resize(width: Int, height: Int) {
        projectionMatrix.identity()
        projectionMatrix.perspective(
            EngineProperties.fov, // in radians
            width.toFloat()/height.toFloat(),
            EngineProperties.zNear,
            EngineProperties.zFar,
            true // vulkan (also Direct3D) uses Normalized Device Coordinates (NDC), so we need to normalize the z coords to [0, 1]
        )
    }
}