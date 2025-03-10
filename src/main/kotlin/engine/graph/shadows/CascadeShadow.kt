package com.maorbarak.engine.graph.shadows

import com.maorbarak.engine.graph.vk.GraphConstants
import com.maorbarak.engine.scene.Scene
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

class CascadeShadow {

    var projViewMatrix: Matrix4f
    var splitDistance: Float = 0.0f
        private set


    init {
        projViewMatrix = Matrix4f()
    }

    companion object {
        fun updateCascadeShadows(cascadeShadows: List<CascadeShadow>, scene: Scene) {
            val viewMatrix = scene.camera.viewMatrix
            val projMatrix = scene.projection.projectionMatrix
            val lightPos = scene.directionalLight!!.position

            val cascadeSplitLambda = .95f

            val nearClip = projMatrix.perspectiveNear()
            val farClip = projMatrix.perspectiveFar()
            val clipRange = farClip - nearClip

            val minZ = nearClip
            val maxZ = nearClip + clipRange // why don't take the farClip??

            val range = maxZ - minZ // lol same as clipRange
            val ratio = maxZ / minZ

            // Calculate split depths based on view camera frustum
            // Based on method presented in https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch10.html
            val cascadeSplits = Array(GraphConstants.SHADOW_MAP_CASCADE_COUNT) { i ->
                val p = (i + 1) / GraphConstants.SHADOW_MAP_CASCADE_COUNT.toDouble()
                val log = minZ * Math.pow(range.toDouble(), p).toFloat()
                val uniform = minZ + range * p
                val d = cascadeSplitLambda * (log - uniform) + uniform
                ((d - nearClip) / clipRange).toFloat()
            }

            // Calculate orthographic projection matrix for each cascade
            var lastSplitDist = .0f
            cascadeSplits.forEachIndexed { i, splitDist ->

                // corners in NDC (normalized device coordinates) space
                val frustumCorners = arrayOf(
                    Vector3f(-1.0f, 1.0f, 0.0f),
                    Vector3f(1.0f, 1.0f, 0.0f),
                    Vector3f(1.0f, -1.0f, 0.0f),
                    Vector3f(-1.0f, -1.0f, 0.0f),
                    Vector3f(-1.0f, 1.0f, 1.0f),
                    Vector3f(1.0f, 1.0f, 1.0f),
                    Vector3f(1.0f, -1.0f, 1.0f),
                    Vector3f(-1.0f, -1.0f, 1.0f),
                )

                // Project frustum corners into world space
                val invCam = (Matrix4f(projMatrix).mul(viewMatrix)).invert()
                frustumCorners.forEach {
                    val invCorner = Vector4f(it, 1f).mul(invCam)
                    // perspective division or homogeneous division
                    // [not in this case it does transforms from [-w,w] to [-1, 1]]
                    // but here the projection matrix introduces a W-component that scales the coordinates; dividing by W normalizes them back into 3D world space.
                    it.set(invCorner.x / invCorner.w, invCorner.y / invCorner.w, invCorner.z / invCorner.w)
                }

                (0..<4).forEach { j ->
                    val dist = Vector3f(frustumCorners[j + 4]).sub(frustumCorners[j])
                    frustumCorners[j + 4].set(Vector3f(frustumCorners[j]).add(Vector3f(dist).mul(splitDist)))
                    frustumCorners[j].set(Vector3f(frustumCorners[j]).add(Vector3f(dist).mul(lastSplitDist)))
                }

                // Get frustum center
                val frustumCenter = Vector3f(.0f)
                frustumCorners.forEach {
                    frustumCenter.add(it)
                }
                frustumCenter.div(8f)

                var radius = 0f
                frustumCorners.forEach {
                    val distance = (Vector3f(it).sub(frustumCenter)).length()
                    radius = max(radius, distance)
                }
                radius = Math.ceil(radius * 16.0).toFloat() / 16f

                val maxExtents = Vector3f(radius) // r
                val minExtents = Vector3f(maxExtents).mul(-1f) // -r

                val lightDir = Vector3f(lightPos.x, lightPos.y, lightPos.z).mul(-1f).normalize()
                // eye = moving r from the center to the point of the light
                val eye = Vector3f(frustumCenter).sub(Vector3f(lightDir).mul(-minExtents.z))
                val up = Vector3f(0f, 1f, 0f)
                // eye is me, center is where I want to look at, up is up
                val lightViewMatrix = Matrix4f().lookAt(eye, frustumCenter, up)
                val lightOrthoMatrix = Matrix4f().ortho(
                    minExtents.x, maxExtents.x, minExtents.y, maxExtents.y, 0f, maxExtents.z - minExtents.z, true
                )

                // store split distance and matrix in cascade
                cascadeShadows[i].apply {
                    splitDistance = (nearClip + splitDist * clipRange) * -1f
                    projViewMatrix.set(lightOrthoMatrix.mul(lightViewMatrix))
                    lastSplitDist = splitDist
                }
            }
        }
    }
}