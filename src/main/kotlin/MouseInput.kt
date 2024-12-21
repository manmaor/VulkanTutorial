package com.maorbarak

import org.joml.Vector2f
import org.lwjgl.glfw.GLFW.*

class MouseInput(windowHandle: Long) {
    val previousPos: Vector2f = Vector2f(-1f, -1f)
    val currentPos: Vector2f = Vector2f()
    val displVec: Vector2f = Vector2f()
    var inWindow: Boolean = false
        private set
    var leftButtonPressed: Boolean = false
        private set
    var rightButtonPressed: Boolean = false
        private set

    init {
        glfwSetCursorPosCallback(windowHandle) { window, x, y ->
            currentPos.x = x.toFloat()
            currentPos.y = y.toFloat()
        }

        glfwSetCursorEnterCallback(windowHandle) { window, entered ->
            inWindow = entered
        }

        glfwSetMouseButtonCallback(windowHandle) { window, button, action, mode ->
            leftButtonPressed = button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS
            rightButtonPressed = button == GLFW_MOUSE_BUTTON_2 && action == GLFW_PRESS
        }
    }

    fun input() {
        displVec.x = 0f
        displVec.y = 0f

        if (previousPos.x  >= 0 && previousPos.y >= 0 && inWindow) {
            displVec.x = currentPos.y - previousPos.y
            displVec.y = currentPos.x - previousPos.x
        }

        previousPos.x = previousPos.x
        previousPos.y = previousPos.y
    }
}