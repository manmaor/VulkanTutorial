package com.maorbarak.engine.scene

import org.joml.Vector4f
import org.lwjgl.assimp.*
import org.lwjgl.assimp.Assimp.*
import org.lwjgl.system.MemoryStack
import org.tinylog.kotlin.Logger
import java.io.File
import java.nio.IntBuffer


object ModelLoader {

    fun loadModel(modelId: String, modelPath: String, texturesDir: String): ModelData = loadModel(
        modelId, modelPath, texturesDir,
        flags = aiProcess_GenSmoothNormals or
                aiProcess_JoinIdenticalVertices or
                aiProcess_Triangulate or
                aiProcess_FixInfacingNormals or
                // tangent space is an image with the z component is the value and points up,
                // e.g. normal maps
                aiProcess_CalcTangentSpace or
                // This removes the node graph and pre-transforms all vertices with the local
                //   transformation matrices of their nodes.
                // Keep in mind that this flag cannot be used with animations
                aiProcess_PreTransformVertices
    )

    fun loadModel(modelId: String, modelPath: String, texturesDir: String, flags: Int): ModelData {
        Logger.debug("Loading model data $modelPath");
         if (!File(modelPath).exists()) {
            throw RuntimeException("Model path does not exist [$modelPath]")
         }

        if (!File(texturesDir).exists()) {
            throw RuntimeException("Model path does not exist [$modelPath]")
        }

        val aiScene = aiImportFile(modelPath, flags)
            ?: throw RuntimeException("Error loading model [modelPath: $modelPath, texturesDir: $texturesDir]")

        val materialList  = (0..<aiScene.mNumMaterials()).map { i ->
            val aiMaterial = AIMaterial.create(aiScene.mMaterials()!!.get(i))
            processMaterial(aiMaterial, texturesDir)
        }

        val meshDataList = (0..<aiScene.mNumMeshes()).map { i ->
            val aiMesh = AIMesh.create(aiScene.mMeshes()!!.get(i))
            processMesh(aiMesh)
        }

        val modelData = ModelData(modelId, meshDataList, materialList)
        aiReleaseImport(aiScene)
        Logger.debug("Loaded Model [$modelPath]")

        return modelData
    }

    private fun processMaterial(aiMaterial: AIMaterial, texturesDir: String): ModelData.Material {
        MemoryStack.stackPush().use { stack ->
            val color = AIColor4D.create()

            // Diffuse / texture
            var diffuse: Vector4f = ModelData.Material.DEFAULT_COLOR
            var result = aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE,
                aiTextureType_NONE, 0, color)
            if (result == aiReturn_SUCCESS) {
                diffuse = Vector4f(color.r(), color.g(), color.b(), color.a())
            }
                val aiTexturePath = AIString.calloc(stack)
            aiGetMaterialTexture(aiMaterial, aiTextureType_DIFFUSE, 0, aiTexturePath,
                null as IntBuffer?, null, null, null, null, null)
            var texturePath = aiTexturePath.dataString()
            if (texturePath.isNotEmpty()) {
                texturePath = texturesDir + File.separator + File(texturePath).name
                diffuse = Vector4f(0f, 0f, 0f, 0f)
            }

            // Normal
            val aiNormalMapPath = AIString.calloc(stack)
            aiGetMaterialTexture(aiMaterial, aiTextureType_NORMALS, 0, aiNormalMapPath,
                null as IntBuffer?, null, null, null, null, null)
            var normalMapPath = aiNormalMapPath.dataString()
            if (normalMapPath.isNotEmpty()) {
                normalMapPath = texturesDir + File.separator + File(normalMapPath).name
            }

            // Metallic Roughness
            val aiMetallicRoughnessPath = AIString.calloc(stack)
            aiGetMaterialTexture(aiMaterial, AI_MATKEY_GLTF_PBRMETALLICROUGHNESS_METALLICROUGHNESS_TEXTURE, 0, aiMetallicRoughnessPath,
                null as IntBuffer?, null, null, null, null, null)
            var metallicRoughnessPath = aiMetallicRoughnessPath.dataString()
            if (metallicRoughnessPath.isNotEmpty()) {
                metallicRoughnessPath = texturesDir + File.separator + File(metallicRoughnessPath).name
            }

            val metallicArr = floatArrayOf(0f)
            val pMax = intArrayOf(1)
            result = aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_METALLIC_FACTOR, aiTextureType_NONE, 0, metallicArr, pMax)
            if (result != aiReturn_SUCCESS) {
                metallicArr[0] = 1f
            }

            val roughnessArr = floatArrayOf(0f)
            result = aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_ROUGHNESS_FACTOR, aiTextureType_NONE, 0, roughnessArr, pMax)
            if (result != aiReturn_SUCCESS) {
                roughnessArr[0] = 1f
            }

            return ModelData.Material(texturePath, normalMapPath, metallicRoughnessPath, diffuse, roughnessArr[0], metallicArr[0])
        }
    }

    private fun processMesh(aiMesh: AIMesh): ModelData.MeshData {
        val vertices: List<Float> = processVertices(aiMesh)
        val normals  = processNormals(aiMesh)
        val tangents = processTangents(aiMesh, normals)
        val biTangents = processBitangents(aiMesh, normals)
        var textCoords: List<Float> = processTextCoords(aiMesh)
        val indices: List<Int> = processIndices(aiMesh)

        if (textCoords.isEmpty()) {
            val numElements = vertices.size / 3 * 2
            textCoords = List(numElements)  { 0f }
        }

        val materialIdx = aiMesh.mMaterialIndex()
        return ModelData.MeshData(
            vertices.toFloatArray(),
            normals.toFloatArray(),
            tangents.toFloatArray(),
            biTangents.toFloatArray(),
            textCoords.toFloatArray(),
            indices.toIntArray(),
            materialIdx)
    }

    private fun processVertices(aiMesh: AIMesh): List<Float> {
        val vertices = mutableListOf<Float>()
        aiMesh.mVertices().forEach {
            vertices.add(it.x())
            vertices.add(it.y())
            vertices.add(it.z())
        }
        return vertices
    }

    private fun processNormals(aiMesh: AIMesh): List<Float> {
        val normals = mutableListOf<Float>()

        aiMesh.mNormals()?.forEach {
            normals.add(it.x())
            normals.add(it.y())
            normals.add(it.z())
        }

        return normals
    }

    private fun processTangents(aiMesh: AIMesh, normals: List<Float>): List<Float> {
        val tangents = mutableListOf<Float>()

        aiMesh.mTangents()?.forEach {
            tangents.add(it.x())
            tangents.add(it.y())
            tangents.add(it.z())
        }

        // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
        if (tangents.isEmpty()) {
            return List(normals.size) { 0f }
        }

        return tangents
    }

    private fun processBitangents(aiMesh: AIMesh, normals: List<Float>): List<Float> {
        val biTangents = mutableListOf<Float>()

        aiMesh.mBitangents()?.forEach {
            biTangents.add(it.x())
            biTangents.add(it.y())
            biTangents.add(it.z())
        }

        // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
        if (biTangents.isEmpty()) {
            return List(normals.size) { 0f }
        }

        return biTangents
    }

    private fun processTextCoords(aiMesh: AIMesh): List<Float> {
        val textCoords = mutableListOf<Float>()
        aiMesh.mTextureCoords(0)?.forEach {
            textCoords.add(it.x())
            textCoords.add(1 - it.y())
        }
        return textCoords
    }

    private fun processIndices(aiMesh: AIMesh): List<Int> {
        val indices = mutableListOf<Int>()
        aiMesh.mFaces().forEach { face ->
            val buffer = face.mIndices()
            while (buffer.remaining() > 0) {
                indices.add(buffer.get())
            }
        }
        return indices
    }
}