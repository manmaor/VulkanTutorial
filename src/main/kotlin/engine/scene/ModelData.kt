package com.maorbarak.engine.scene

import org.joml.Vector4f


data class ModelData(
    val modelId: String,
    val meshDataList: List<MeshData>,
    val materialList: List<Material>
) {
    data class MeshData(val position: FloatArray, val textCoords: FloatArray?, val indices: IntArray, val materialIdx: Int)
    data class Material(val texturePath: String? = null, val diffuseColor: Vector4f = DEFAULT_COLOR) {
        companion object {
            val DEFAULT_COLOR = Vector4f(1f, 1f, 1f, 1f)
        }
    }
}