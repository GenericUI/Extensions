package com.nexustools.genericui.ext.engine.animation

/**
 * Single control point on a cubic [HermiteSplineCurve] with normalized time (`0f..1f`), value,
 * and incoming/outgoing derivative tangents.
 */
data class HermiteKeyframe(
    val time: Float,
    val value: Float,
    val inTangent: Float = 0f,
    val outTangent: Float = 0f
)
