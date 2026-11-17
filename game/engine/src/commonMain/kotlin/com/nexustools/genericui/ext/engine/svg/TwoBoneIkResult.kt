package com.nexustools.genericui.ext.engine.svg

/**
 * Analytic 2-bone inverse kinematics solution containing the world-space [rootAngleRad] for
 * the upper bone, relative [jointAngleRad] for the lower bone, and resolved tip coordinates.
 */
data class TwoBoneIkResult(
    val rootAngleRad: Float,
    val jointAngleRad: Float,
    val endEffectorX: Float,
    val endEffectorY: Float,
    val reachedTarget: Boolean
)
