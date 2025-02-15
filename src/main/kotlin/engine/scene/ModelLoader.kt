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

            var diffuse: Vector4f = ModelData.Material.DEFAULT_COLOR
            val result = aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE,
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

            return ModelData.Material(texturePath, diffuse)
        }
    }

    private fun processMesh(aiMesh: AIMesh): ModelData.MeshData {
        val vertices: List<Float> = processVertices(aiMesh)
        var textCoords: List<Float> = processTextCoords(aiMesh)
        val indices: List<Int> = processIndices(aiMesh)

        if (textCoords.isEmpty()) {
            val numElements = vertices.size / 3 * 2
            textCoords = List(numElements)  { 0f }
        }

        val materialIdx = aiMesh.mMaterialIndex()
        return ModelData.MeshData(
            vertices.toFloatArray(),
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