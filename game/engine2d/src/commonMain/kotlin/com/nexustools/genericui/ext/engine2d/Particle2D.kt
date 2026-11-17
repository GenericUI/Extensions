package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.style.color.RgbaColor

/**
 * Single live particle simulated inside a [ParticleEmitter2DModule].
 */
data class Particle2D(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var ageSeconds: Float,
    var lifetimeSeconds: Float,
    var startSize: Float,
    var endSize: Float,
    var startColor: RgbaColor,
    var endColor: RgbaColor
)
