package com.maorbarak.engine

object EngineProperties {
    private const val defaultUPS = 30
    private const val defaultValidate = false
    private const val filename = "eng.properties"

    val ups: Int
    val validate: Boolean

    init {
        // Reading properties file
        EngineProperties.javaClass.getResourceAsStream("/$filename").use { stream ->
            val props: Map<String, String> = stream?.reader()
                ?.readLines()
                ?.filter { it.contains("=") }
                ?.associate { line ->
                    line
                        .replace(" ", "")
                        .split("=")
                        .let { it[0] to it[1] }
                } ?: mapOf()
            ups = props.getOrDefault("ups", defaultUPS).toString().toInt()
            validate = props.getOrDefault("validate", defaultValidate).toString().toBoolean()
        }
    }
}
