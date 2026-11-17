package com.nexustools.genericui.ext.box2d

/**
 * Construction descriptor for attaching a [Shape2D] to a [Body2D] (`b2FixtureDef`).
 */
data class FixtureDef2D(
    var shape: Shape2D = CircleShape2D(0.5f),
    var density: Float = 1f,
    var friction: Float = 0.2f,
    var restitution: Float = 0f,
    var isSensor: Boolean = false,
    var categoryBits: Int = 0x0001,
    var maskBits: Int = 0xFFFF,
    var groupIndex: Int = 0,
    var userData: Any? = null
)
