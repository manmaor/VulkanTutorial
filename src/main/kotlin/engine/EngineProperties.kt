package com.maorbarak.engine

import java.io.InputStream

object EngineProperties {
    private const val DEFAULT_UPS = 30
    private const val DEFAULT_VALIDATE = false
    private const val FILENAME = "eng.properties"
    private const val DEFAULT_REQUESTED_IMAGES = 3

    val ups: Int
    val validate: Boolean
    val physDeviceName: String?
    val requestedImages: Int
    val vSync: Boolean

    init {
        // Reading properties file
        EngineProperties.javaClass.getResourceAsStream("/$FILENAME").use { stream ->
            val props: Map<String, String> = readProps(stream)

            ups = props.getOrDefault("ups", DEFAULT_UPS).toString().toInt()
            validate = props.getOrDefault("validate", DEFAULT_VALIDATE).toString().toBoolean()
            physDeviceName = props["physDeviceName"]
            requestedImages = props.getOrDefault("requestedImages", DEFAULT_REQUESTED_IMAGES).toString().toInt()
            vSync = props.getOrDefault("vsync", true).toString().toBoolean()
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
