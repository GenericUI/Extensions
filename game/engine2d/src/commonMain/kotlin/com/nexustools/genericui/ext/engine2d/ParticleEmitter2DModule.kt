package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.engine.math.Vec2f
import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Configurable 2D particle emitter module simulating velocity, gravity, size, and RGBA color fade.
 */
class ParticleEmitter2DModule(
    var emissionRatePerSecond: Float = 20f,
    var maxParticles: Int = 256,
    var particleLifetimeSeconds: Float = 1.2f,
    var initialSpeedMin: Float = 40f,
    var initialSpeedMax: Float = 120f,
    var spreadAngleRad: Float = (PI * 2.0).toFloat(),
    var directionAngleRad: Float = -(PI * 0.5).toFloat(),
    var gravity: Vec2f = Vec2f(0f, 180f),
    var startSize: Float = 8f,
    var endSize: Float = 1f,
    var startColor: RgbaColor = RgbaColor(255, 200, 80, 255),
    var endColor: RgbaColor = RgbaColor(255, 80, 20, 0),
    var zOrder: Int = 5,
    var isEmitting: Boolean = true
) : EntityBehavior() {

    private val particles = mutableListOf<Particle2D>()
    private var emissionAccumulator: Float = 0f

    val activeParticleCount: Int
        get() = particles.size

    fun emitBurst(count: Int) {
        val origin = entity?.transform?.position2D ?: Vec2f.ZERO
        for (i in 0 until count) {
            if (particles.size >= maxParticles) break
            val angle = directionAngleRad + (Random.nextFloat() - 0.5f) * spreadAngleRad
            val speed = initialSpeedMin + Random.nextFloat() * (initialSpeedMax - initialSpeedMin).coerceAtLeast(0f)
            particles.add(
                Particle2D(
                    x = origin.x,
                    y = origin.y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    ageSeconds = 0f,
                    lifetimeSeconds = particleLifetimeSeconds.coerceAtLeast(0.05f),
                    startSize = startSize,
                    endSize = endSize,
                    startColor = startColor,
                    endColor = endColor
                )
            )
        }
    }

    override fun onTick(deltaSeconds: Float) {
        if (isEmitting && emissionRatePerSecond > 0f) {
            emissionAccumulator += deltaSeconds * emissionRatePerSecond
            val toSpawn = emissionAccumulator.toInt()
            if (toSpawn > 0) {
                emissionAccumulator -= toSpawn.toFloat()
                emitBurst(toSpawn)
            }
        }

        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.ageSeconds += deltaSeconds
            if (p.ageSeconds >= p.lifetimeSeconds) {
                iter.remove()
                continue
            }
            p.vx += gravity.x * deltaSeconds
            p.vy += gravity.y * deltaSeconds
            p.x += p.vx * deltaSeconds
            p.y += p.vy * deltaSeconds
        }
    }

    fun render(ctx: ImageBuffer2DContext) {
        if (!isEnabled || particles.isEmpty()) return
        for (p in particles) {
            val t = (p.ageSeconds / p.lifetimeSeconds).coerceIn(0f, 1f)
            val size = p.startSize + (p.endSize - p.startSize) * t
            val r = (p.startColor.r + (p.endColor.r - p.startColor.r) * t).toInt().coerceIn(0, 255)
            val g = (p.startColor.g + (p.endColor.g - p.startColor.g) * t).toInt().coerceIn(0, 255)
            val b = (p.startColor.b + (p.endColor.b - p.startColor.b) * t).toInt().coerceIn(0, 255)
            val a = (p.startColor.a + (p.endColor.a - p.startColor.a) * t).toInt().coerceIn(0, 255)
            if (a <= 0 || size <= 0f) continue
            ctx.setFillColor(RgbaColor(r, g, b, a))
            ctx.beginPath()
            ctx.arc(p.x, p.y, size * 0.5f, 0f, (PI * 2.0).toFloat(), false)
            ctx.fill()
        }
    }
}
