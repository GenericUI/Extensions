package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.engine.math.Vec2f
import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D camera module providing zoom, viewport centering, smooth entity following, and trauma-based
 * screen shake over [ImageBuffer2DContext].
 */
class Camera2DModule(
    var viewportWidth: Float = 800f,
    var viewportHeight: Float = 600f,
    var zoom: Float = 1f,
    var pixelsPerUnit: Float = 1f,
    var followTarget: SceneEntity? = null,
    var followSmoothing: Float = 8f
) : EntityBehavior() {

    var shakeIntensity: Float = 0f
    var shakeDecayPerSecond: Float = 25f
    private var shakePhase: Float = 0f
    private var currentShakeOffset: Vec2f = Vec2f.ZERO

    override fun onTick(deltaSeconds: Float) {
        val owner = entity ?: return
        val target = followTarget
        if (target != null) {
            val alpha = (followSmoothing * deltaSeconds).coerceIn(0f, 1f)
            val cur = owner.transform.position
            val dst = target.transform.position
            owner.transform.setPosition2D(
                cur.x + (dst.x - cur.x) * alpha,
                cur.y + (dst.y - cur.y) * alpha
            )
        }
        if (shakeIntensity > 0f) {
            shakePhase += deltaSeconds * 47f
            currentShakeOffset = Vec2f(
                cos(shakePhase) * shakeIntensity,
                sin(shakePhase * 1.37f) * shakeIntensity
            )
            shakeIntensity = (shakeIntensity - shakeDecayPerSecond * deltaSeconds).coerceAtLeast(0f)
        } else {
            currentShakeOffset = Vec2f.ZERO
        }
    }

    fun triggerShake(intensityPx: Float) {
        shakeIntensity = maxOf(shakeIntensity, intensityPx.coerceAtLeast(0f))
    }

    fun applyTransform(ctx: ImageBuffer2DContext) {
        val camPos = entity?.transform?.position ?: return
        val camRot = entity?.transform?.rotation2DRad ?: 0f
        val safeZoom = (zoom * pixelsPerUnit).coerceAtLeast(0.01f)

        ctx.translate(viewportWidth * 0.5f + currentShakeOffset.x, viewportHeight * 0.5f + currentShakeOffset.y)
        ctx.scale(safeZoom, safeZoom)
        if (camRot != 0f) {
            ctx.rotate(-camRot)
        }
        ctx.translate(-camPos.x, -camPos.y)
    }

    fun worldToScreen(worldPoint: Vec2f): Vec2f {
        val camPos = entity?.transform?.position2D ?: Vec2f.ZERO
        val safeZoom = (zoom * pixelsPerUnit).coerceAtLeast(0.01f)
        val dx = (worldPoint.x - camPos.x) * safeZoom
        val dy = (worldPoint.y - camPos.y) * safeZoom
        return Vec2f(viewportWidth * 0.5f + dx, viewportHeight * 0.5f + dy)
    }

    fun screenToWorld(screenPoint: Vec2f): Vec2f {
        val camPos = entity?.transform?.position2D ?: Vec2f.ZERO
        val safeZoom = (zoom * pixelsPerUnit).coerceAtLeast(0.01f)
        val wx = (screenPoint.x - viewportWidth * 0.5f) / safeZoom + camPos.x
        val wy = (screenPoint.y - viewportHeight * 0.5f) / safeZoom + camPos.y
        return Vec2f(wx, wy)
    }
}
