package com.maorbarak.engine.scene


data class ModelData(
    val modelId: String,
    val meshDataList: List<MeshData>
) {
    data class MeshData(val position: FloatArray, val textCoords: FloatArray?, val indices: IntArray) {
    }
}