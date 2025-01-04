package com.maorbarak.engine

import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.*
import org.lwjgl.system.MemoryUtil

class Window(
    val title: String
) {

    var resized = false
        private set
    var width: Int
        private set
    var height: Int
        private set

    val handle: Long
    val mouseInput: MouseInput

    init {
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }

        if (!glfwVulkanSupported()) {
            throw IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD)")
        }

        val vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor())
        width = vidMode.width()
        height = vidMode.height()

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API) // What does this means
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE)

        handle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL)
        if (handle == MemoryUtil.NULL) {
            throw RuntimeException("Failed to create the GLFW window");
        }

        glfwSetFramebufferSizeCallback(handle) { window, w, h -> resize(w, h) }

        glfwSetKeyCallback(handle) { window, key, scancode, action, mods ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true)
            }
//            keyCallback?.let {
//                it.invoke(window, key, scancode, action, mods)
//            }
        }

        mouseInput = MouseInput(handle)
    }

    fun cleanup() {
        glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
    }

    fun isKeyPressed(keyCode: Int): Boolean {
        return glfwGetKey(handle, keyCode) == GLFW_PRESS;
    }

    fun pollEvents() {
        glfwPollEvents()
        mouseInput.input()
    }

    fun resetResized() {
        resized = false
    }

    fun resize(width: Int, height: Int) {
        resized = true
        this.width = width
        this.height = height
    }

    fun setShouldClose() {
        glfwSetWindowShouldClose(handle, true);
    }

    fun shouldClose(): Boolean {
        return glfwWindowShouldClose(handle);
    }
}