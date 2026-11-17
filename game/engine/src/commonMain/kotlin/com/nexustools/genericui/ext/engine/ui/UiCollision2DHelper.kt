package com.nexustools.genericui.ext.engine.ui

import com.nexustools.genericui.ext.engine.math.Vec2f
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import kotlin.math.abs

/**
 * Pure 2D AABB and circle collision / overlap utilities for UI-driven and lightweight 2D games.
 */
object UiCollision2DHelper {
    fun aabbOverlap(
        ax: Float,
        ay: Float,
        aw: Float,
        ah: Float,
        bx: Float,
        by: Float,
        bw: Float,
        bh: Float
    ): Boolean =
        ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by

    fun aabbOverlapEntities(
        a: SceneEntity,
        aSize: Vec2f,
        b: SceneEntity,
        bSize: Vec2f
    ): Boolean = aabbOverlap(
        a.transform.position.x,
        a.transform.position.y,
        aSize.x,
        aSize.y,
        b.transform.position.x,
        b.transform.position.y,
        bSize.x,
        bSize.y
    )

    fun circleOverlap(
        ax: Float,
        ay: Float,
        aRadius: Float,
        bx: Float,
        by: Float,
        bRadius: Float
    ): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val rSum = (aRadius + bRadius).coerceAtLeast(0f)
        return dx * dx + dy * dy <= rSum * rSum
    }

    fun circleOverlapEntities(
        a: SceneEntity,
        aRadius: Float,
        b: SceneEntity,
        bRadius: Float
    ): Boolean = circleOverlap(
        a.transform.position.x,
        a.transform.position.y,
        aRadius,
        b.transform.position.x,
        b.transform.position.y,
        bRadius
    )

    fun pointInAabb(
        px: Float,
        py: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): Boolean = px >= x && px <= x + width && py >= y && py <= y + height

    /**
     * Returns the minimum translation vector pushing rectangle A out of rectangle B, or `null` if disjoint.
     */
    fun computeAabbPenetration(
        ax: Float,
        ay: Float,
        aw: Float,
        ah: Float,
        bx: Float,
        by: Float,
        bw: Float,
        bh: Float
    ): Vec2f? {
        if (!aabbOverlap(ax, ay, aw, ah, bx, by, bw, bh)) return null
        val aCenterX = ax + aw * 0.5f
        val aCenterY = ay + ah * 0.5f
        val bCenterX = bx + bw * 0.5f
        val bCenterY = by + bh * 0.5f

        val dx = aCenterX - bCenterX
        val dy = aCenterY - bCenterY
        val overlapX = (aw + bw) * 0.5f - abs(dx)
        val overlapY = (ah + bh) * 0.5f - abs(dy)
        if (overlapX <= 0f || overlapY <= 0f) return null

        return if (overlapX < overlapY) {
            Vec2f(if (dx >= 0f) overlapX else -overlapX, 0f)
        } else {
            Vec2f(0f, if (dy >= 0f) overlapY else -overlapY)
        }
    }
}
