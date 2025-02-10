package com.maorbarak.engine

import java.io.InputStream

object EngineProperties {
    private const val DEFAULT_UPS = 30
    private const val DEFAULT_VALIDATE = false
    private const val FILENAME = "eng.properties"
    private const val DEFAULT_REQUESTED_IMAGES = 3
    private const val DEFAULT_FOV = 60.0f
    private const val DEFAULT_Z_NEAR = 1.0f
    private const val DEFAULT_Z_FAR = 100f
    private const val DEFAULT_DEFAULT_TEXTURE_PATH = "resources/models/default/default.png"

    val ups: Int
    val validate: Boolean
    val physDeviceName: String?
    val requestedImages: Int
    val vSync: Boolean
    val isShaderRecompilation: Boolean
    val fov: Float
    val zNear: Float
    val zFar: Float
    val defaultTexturePath: String

    init {
        // Reading properties file
        EngineProperties.javaClass.getResourceAsStream("/$FILENAME").use { stream ->
            val props: Map<String, String> = readProps(stream)

            ups = props.getOrDefault("ups", DEFAULT_UPS).toString().toInt()
            validate = props.getOrDefault("validate", DEFAULT_VALIDATE).toString().toBoolean()
            physDeviceName = props["physDeviceName"]
            requestedImages = props.getOrDefault("requestedImages", DEFAULT_REQUESTED_IMAGES).toString().toInt()
            vSync = props.getOrDefault("vsync", true).toString().toBoolean()
            isShaderRecompilation = props.getOrDefault("shaderRecompilation", false).toString().toBoolean()
            fov = Math.toRadians(props.getOrDefault("fov", DEFAULT_FOV).toString().toDouble()).toFloat()
            zNear = props.getOrDefault("zNear", DEFAULT_Z_NEAR).toString().toFloat()
            zFar = props.getOrDefault("zFar", DEFAULT_Z_FAR).toString().toFloat()
            defaultTexturePath = props.getOrDefault("defaultTexturePath", DEFAULT_DEFAULT_TEXTURE_PATH)
        }
    }

    private fun readProps(stream: InputStream?) = stream?.reader()
        ?.readLines()
        ?.filter { it.contains("=") }
        ?.associate { line ->
            line
                .replace(" ", "")
                .split("=")
                .let { it[0] to it[1] }
        } ?: mapOf()
}
