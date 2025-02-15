package com.maorbarak.engine.graph

import com.maorbarak.engine.graph.vk.*
import com.maorbarak.engine.scene.ModelData
import org.joml.Vector4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkBufferCopy


class VulkanModel(
    val modelId: String
) {

    val vulkanMaterialList: MutableList<VulkanMaterial> = mutableListOf()

    fun cleanup() {
        vulkanMaterialList.forEach(VulkanMaterial::cleanup)
    }

    companion object {

        private fun createIndicesBuffer(device: Device, meshData: ModelData.MeshData): TransferBuffers {
            val bufferSize = meshData.indices.size * GraphConstants.INT_LENGTH

            val srcBuffer = VulkanBuffer(device, bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
            val dstBuffer = VulkanBuffer(device, bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)

            val mappedMemory = srcBuffer.map()
            val data = MemoryUtil.memIntBuffer(mappedMemory, srcBuffer.requestedSize.toInt())
            data.put(meshData.indices)
            srcBuffer.unMap()

            return TransferBuffers(srcBuffer, dstBuffer)
        }

        private fun createVerticesBuffers(device: Device, meshData: ModelData.MeshData): TransferBuffers {
            val positions = meshData.position
            val textCoords = if (meshData.textCoords == null || meshData.textCoords.isEmpty()) FloatArray(positions.size/3*2) else meshData.textCoords
            val numElements = positions.size + textCoords.size
            val bufferSize = numElements * GraphConstants.FLOAT_LENGTH

            val srcBuffer = VulkanBuffer(device, bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
            val dstBuffer = VulkanBuffer(device, bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)

            val mappedMemory = srcBuffer.map()
            val data = MemoryUtil.memFloatBuffer(mappedMemory, srcBuffer.requestedSize.toInt())

            (0..<(positions.size / 3)).forEach { row ->
                val startPos = row * 3
                val startTextCoords = row * 2
                data.put(positions[startPos])
                data.put(positions[startPos + 1])
                data.put(positions[startPos + 2])
                data.put(textCoords[startTextCoords])
                data.put(textCoords[startTextCoords + 1])
            }

            srcBuffer.unMap()

            return TransferBuffers(srcBuffer, dstBuffer)
        }

        private fun recordTransferCommand(cmd: CommandBuffer, transferBuffers: TransferBuffers) {
            MemoryStack.stackPush().use { stack ->
                val copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0)
                    .dstOffset(0)
                    .size(transferBuffers.srcBuffer.requestedSize)

                vkCmdCopyBuffer(cmd.vkCommandBuffer, transferBuffers.srcBuffer.buffer, transferBuffers.dstBuffer.buffer, copyRegion)
            }
        }

        private fun transformMaterial(material: ModelData.Material,
                                      device: Device,
                                      textureCache: TextureCache,
                                      cmd: CommandBuffer,
                                      textureList: MutableList<Texture>): VulkanMaterial {
            val texture = textureCache.createTexture(device, material.texturePath, VK_FORMAT_R8G8B8A8_SRGB)
            val hasTexture = material.texturePath != null && material.texturePath.trim().isNotEmpty()

            texture.recordTextureTransition(cmd)
            textureList.add(texture)

            return VulkanMaterial(material.diffuseColor, texture, hasTexture, mutableListOf())
        }

        fun transformModels(modelDataList: List<ModelData>, textureCache: TextureCache, commandPool: CommandPool, queue: Queue): List<VulkanModel> {
            val vulkanModelList: MutableList<VulkanModel> = mutableListOf()
            val device = commandPool.device
            val cmd = CommandBuffer(commandPool, oneTimeSubmit = true, primary = true)
            val stagingBufferList: MutableList<VulkanBuffer> = mutableListOf()
            val textureList: MutableList<Texture> = mutableListOf()

            cmd.beginRecording()

            modelDataList.forEach { modelData ->
                val vulkanModel = VulkanModel(modelData.modelId)
                vulkanModelList.add(vulkanModel)

                val defaultVulkanMaterial: VulkanMaterial by lazy {
                    transformMaterial(ModelData.Material(), device, textureCache, cmd, textureList)
                }

                // Create textures defined for the materials
                modelData.materialList.forEach { material ->
                    val vulkanMaterial = transformMaterial(material, device, textureCache, cmd, textureList)
                    vulkanModel.vulkanMaterialList.add(vulkanMaterial)
                }

                // Transform meshes loading their data into GPU buffers
                modelData.meshDataList.forEach { meshData ->
                    val verticesBuffer = createVerticesBuffers(device, meshData)
                    val indicesBuffer = createIndicesBuffer(device, meshData)
                    stagingBufferList.add(verticesBuffer.srcBuffer)
                    stagingBufferList.add(indicesBuffer.srcBuffer)
                    recordTransferCommand(cmd, verticesBuffer)
                    recordTransferCommand(cmd, indicesBuffer)

                    val vulkanMesh = VulkanMesh(verticesBuffer.dstBuffer, indicesBuffer.dstBuffer, meshData.indices.size)

                    val vulkanMaterial: VulkanMaterial = if (meshData.materialIdx >= 0 && meshData.materialIdx < vulkanModel.vulkanMaterialList.size) {
                        vulkanModel.vulkanMaterialList[meshData.materialIdx]
                    } else {
                        defaultVulkanMaterial
                    }
                    vulkanMaterial.vulkanMeshList.add(vulkanMesh)
                }
            }

            cmd.endRecording()
            val fence = Fence(device, true)
            fence.reset()
            MemoryStack.stackPush().use { stack ->
                queue.submit(stack.pointers(cmd.vkCommandBuffer), null, null, null, fence)
            }
            fence.fenceWait()
            fence.cleanup()
            cmd.cleanup()

            stagingBufferList.forEach(VulkanBuffer::cleanup)
            textureList.forEach(Texture::cleanupStgBuffer)

            return vulkanModelList.toList()
        }

    }

    private data class TransferBuffers(val srcBuffer: VulkanBuffer, val dstBuffer: VulkanBuffer)

    data class VulkanMesh(
        val verticesBuffer: VulkanBuffer,
        val indicesBuffer: VulkanBuffer,
        val numIndices: Int
    ) {
        fun cleanup() {
            verticesBuffer.cleanup()
            indicesBuffer.cleanup()
        }
    }

    data class VulkanMaterial(
        val diffuseColor: Vector4f,
        val texture: Texture,
        val hasTexture: Boolean,
        val vulkanMeshList: MutableList<VulkanMesh>
    ) {
        val isTransparent = texture.hasTransparencies

        fun cleanup() {
            vulkanMeshList.forEach(VulkanMesh::cleanup)
        }

    }
}