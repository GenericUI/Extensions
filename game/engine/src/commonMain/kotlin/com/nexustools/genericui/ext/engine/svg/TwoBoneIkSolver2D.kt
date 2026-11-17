package com.nexustools.genericui.ext.engine.svg

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Analytic law-of-cosines 2-bone inverse kinematics solver for arms, legs, and mechanical limbs.
 */
object TwoBoneIkSolver2D {
    fun solve(
        rootX: Float,
        rootY: Float,
        upperLength: Float,
        lowerLength: Float,
        targetX: Float,
        targetY: Float,
        bendDirection: Float = 1f
    ): TwoBoneIkResult {
        val u = upperLength.coerceAtLeast(1e-4f)
        val l = lowerLength.coerceAtLeast(1e-4f)
        val dx = targetX - rootX
        val dy = targetY - rootY
        val rawDist = hypot(dx, dy)
        val baseAngle = if (rawDist > 1e-6f) atan2(dy, dx) else 0f

        val minReach = abs(u - l) + 1e-4f
        val maxReach = (u + l) - 1e-4f
        val clampedDist = rawDist.coerceIn(minReach, maxReach)
        val reachable = rawDist in abs(u - l)..(u + l)

        val cosAlpha = ((u * u + clampedDist * clampedDist - l * l) / (2f * u * clampedDist)).coerceIn(-1f, 1f)
        val alpha = acos(cosAlpha)

        val cosBeta = ((u * u + l * l - clampedDist * clampedDist) / (2f * u * l)).coerceIn(-1f, 1f)
        val beta = acos(cosBeta)

        val bendSign = if (bendDirection >= 0f) 1f else -1f
        val rootAngle = normalizeAngle(baseAngle - bendSign * alpha)
        val jointAngle = normalizeAngle(bendSign * (PI.toFloat() - beta))

        val midX = rootX + cos(rootAngle) * u
        val midY = rootY + sin(rootAngle) * u
        val endX = midX + cos(rootAngle + jointAngle) * l
        val endY = midY + sin(rootAngle + jointAngle) * l

        return TwoBoneIkResult(
            rootAngleRad = rootAngle,
            jointAngleRad = jointAngle,
            endEffectorX = endX,
            endEffectorY = endY,
            reachedTarget = reachable
        )
    }

    /**
     * Solves 2-bone IK directly onto [upperBoneName] and [lowerBoneName] in [skeleton],
     * preserving parent world orientation and blending by [weight] (`0f..1f`).
     */
    fun applyToBones(
        skeleton: Skeleton2D,
        upperBoneName: String,
        lowerBoneName: String,
        targetX: Float,
        targetY: Float,
        bendDirection: Float = 1f,
        weight: Float = 1f
    ): TwoBoneIkResult? {
        skeleton.updateWorldTransforms()
        val upper = skeleton.getBone(upperBoneName) ?: return null
        val lower = skeleton.getBone(lowerBoneName) ?: return null

        val result = solve(
            rootX = upper.worldX,
            rootY = upper.worldY,
            upperLength = upper.lengthPx,
            lowerLength = lower.lengthPx,
            targetX = targetX,
            targetY = targetY,
            bendDirection = bendDirection
        )

        val parentWorldAngle = upper.parentName?.let { skeleton.getBone(it)?.worldAngleRad } ?: 0f
        val targetUpperLocal = normalizeAngle(result.rootAngleRad - parentWorldAngle)
        val targetLowerLocal = result.jointAngleRad

        val w = weight.coerceIn(0f, 1f)
        if (w >= 0.9999f) {
            upper.localAngleRad = targetUpperLocal
            lower.localAngleRad = targetLowerLocal
        } else if (w > 0f) {
            upper.localAngleRad = lerpAngle(upper.localAngleRad, targetUpperLocal, w)
            lower.localAngleRad = lerpAngle(lower.localAngleRad, targetLowerLocal, w)
        }
        skeleton.updateWorldTransforms()
        return result
    }

    private fun normalizeAngle(rad: Float): Float {
        var a = rad
        val twoPi = (2.0 * PI).toFloat()
        val pi = PI.toFloat()
        while (a > pi) a -= twoPi
        while (a < -pi) a += twoPi
        return a
    }

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        val diff = normalizeAngle(b - a)
        return normalizeAngle(a + diff * t)
    }
}
