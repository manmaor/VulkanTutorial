package com.maorbarak.engine.graph.vk

import org.lwjgl.util.shaderc.Shaderc
import org.tinylog.kotlin.Logger
import java.io.File

object ShaderCompiler {

    fun compileShader(shaderCode: String, shaderType: Int): ByteArray {
        var compiler = 0L
        var options = 0L
        var compiledShader: ByteArray

        try {

            compiler = Shaderc.shaderc_compiler_initialize()
            options = Shaderc.shaderc_compile_options_initialize()

            val result = Shaderc.shaderc_compile_into_spv(
                compiler,
                shaderCode,
                shaderType,
                "shader.glsl",
                "main",
                options
            )

            if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                throw RuntimeException("Shader compilation failed: ${Shaderc.shaderc_result_get_error_message(result)}")
            }

            val buffer = Shaderc.shaderc_result_get_bytes(result)!!
            compiledShader = ByteArray(buffer.remaining())
            buffer.get(compiledShader)

        } finally {
            Shaderc.shaderc_compiler_release(compiler)
            Shaderc.shaderc_compile_options_release(options)
        }

        return compiledShader
    }

    fun compileShaderIfChanged(glslShaderFile: String, shaderType: Int) {
        var compiledShader: ByteArray

        try {
            val glslFile = File(glslShaderFile)
            val spvFile = File(glslShaderFile + ".spv")
            if (!spvFile.exists() || glslFile.lastModified() > spvFile.lastModified()) {
                Logger.debug("Compiling ${glslFile.path} to ${spvFile.path}")
                val shaderCode = glslFile.readText()

                compiledShader = compileShader(shaderCode, shaderType)
                spvFile.writeBytes(compiledShader)
            } else {
                Logger.debug("Shader ${glslFile.path} already compiled. Loading compiled version: ${spvFile.path}")
            }

        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}