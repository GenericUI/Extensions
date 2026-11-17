package com.nexustools.genericui.ext.box2d

/**
 * Descriptor for constructing a [Joint2D] constraint between two [Body2D]s (`b2JointDef`).
 */
data class JointDef2D(
    var type: JointType2D = JointType2D.DISTANCE,
    var bodyA: Body2D,
    var bodyB: Body2D,
    val localAnchorA: Vec2 = Vec2(),
    val localAnchorB: Vec2 = Vec2(),
    val localAxisA: Vec2 = Vec2(1f, 0f),
    var referenceAngle: Float = 0f,
    var collideConnected: Boolean = false,
    var length: Float = 1f,
    var minLength: Float = 0f,
    var maxLength: Float = Float.MAX_VALUE,
    var stiffness: Float = 0f,
    var damping: Float = 0f,
    var enableLimit: Boolean = false,
    var lowerLimit: Float = 0f,
    var upperLimit: Float = 0f,
    var enableMotor: Boolean = false,
    var motorSpeed: Float = 0f,
    var maxMotorTorqueOrForce: Float = 0f,
    var userData: Any? = null
)
