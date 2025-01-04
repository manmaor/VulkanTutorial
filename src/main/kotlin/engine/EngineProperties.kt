package com.maorbarak.engine

object EngineProperties {
    private const val DEFAULT_UPS = 30
    private const val DEFAULT_VALIDATE = false
    private const val FILENAME = "eng.properties"

    val ups: Int
    val validate: Boolean
    val physDeviceName: String?

    init {
        // Reading properties file
        EngineProperties.javaClass.getResourceAsStream("/$FILENAME").use { stream ->
            val props: Map<String, String> = stream?.reader()
                ?.readLines()
                ?.filter { it.contains("=") }
                ?.associate { line ->
                    line
                        .replace(" ", "")
                        .split("=")
                        .let { it[0] to it[1] }
                } ?: mapOf()
            ups = props.getOrDefault("ups", DEFAULT_UPS).toString().toInt()
            validate = props.getOrDefault("validate", DEFAULT_VALIDATE).toString().toBoolean()
            physDeviceName = props["physDeviceName"]
        }
    }
}
