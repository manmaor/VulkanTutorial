package com.maorbarak.engine.scene

import java.nio.IntBuffer

data class ModelData(
    val modelId: String,
    val meshDataList: List<MeshData>
) {
    data class MeshData(val position: FloatArray, val indices: IntArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MeshData

            if (!position.contentEquals(other.position)) return false
            if (!indices.contentEquals(other.indices)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = position.contentHashCode()
            result = 31 * result + indices.contentHashCode()
            return result
        }
    }
}