package com.nexustools.genericui.ext.box2d

/**
 * Construction descriptor for a [Body2D] (`b2BodyDef`).
 */
data class BodyDef2D(
    var type: BodyType2D = BodyType2D.STATIC,
    val position: Vec2 = Vec2(),
    var angle: Float = 0f,
    val linearVelocity: Vec2 = Vec2(),
    var angularVelocity: Float = 0f,
    var linearDamping: Float = 0f,
    var angularDamping: Float = 0f,
    var allowSleep: Boolean = true,
    var awake: Boolean = true,
    var fixedRotation: Boolean = false,
    var bullet: Boolean = false,
    var enabled: Boolean = true,
    var gravityScale: Float = 1f,
    var userData: Any? = null
)
