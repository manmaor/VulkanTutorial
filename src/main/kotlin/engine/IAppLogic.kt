package com.maorbarak.engine

import com.maorbarak.engine.graph.Render
import com.maorbarak.engine.scene.Scene

interface IAppLogic {
    fun cleanup()

    fun init(window: Window, scene: Scene, render: Render)

    fun input(window: Window, scene: Scene, diffTimeMillis: Long)

    fun update(window: Window, scene: Scene, diffTimeMillis: Long)
}