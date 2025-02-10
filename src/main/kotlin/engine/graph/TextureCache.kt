package com.maorbarak.engine.graph

import com.maorbarak.engine.EngineProperties
import com.maorbarak.engine.graph.vk.Device
import com.maorbarak.engine.graph.vk.Texture

class TextureCache {

    // We are using the LinkedHashMap because we want to keep the order of insertion
    private val textureMap: LinkedHashMap<String, Texture> = LinkedHashMap()

    fun cleanup() {
        textureMap.values.forEach(Texture::cleanup)
        textureMap.clear()
    }

    fun createTexture(device: Device, texturePath: String?, format: Int): Texture {
        val path = texturePath?.takeIf { it.trim().isNotEmpty() } ?: EngineProperties.defaultTexturePath
        val texture = textureMap.getOrPut(path) { Texture(device, path, format) }
        return texture
    }

    fun getAsList() = textureMap.values.toList()

    fun getPosition(texturePath: String) = textureMap.keys.indexOf(texturePath)

    fun getTexture(texturePath: String) = textureMap[texturePath]
}