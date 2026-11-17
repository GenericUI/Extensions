package com.nexustools.genericui.ext.gamepad

/**
 * Normalized 6-axis IMU telemetry (3-axis gyroscope + 3-axis accelerometer) plus AHRS-fused
 * gravity vector, unit orientation quaternion, and Euler angles (`pitch`, `yaw`, `roll`).
 */
data class SixAxisMotionState(
    val gyroRadPerSecX: Float = 0f,
    val gyroRadPerSecY: Float = 0f,
    val gyroRadPerSecZ: Float = 0f,
    val accelGX: Float = 0f,
    val accelGY: Float = 1f,
    val accelGZ: Float = 0f,
    val gravityX: Float = 0f,
    val gravityY: Float = -1f,
    val gravityZ: Float = 0f,
    val orientationQuatX: Float = 0f,
    val orientationQuatY: Float = 0f,
    val orientationQuatZ: Float = 0f,
    val orientationQuatW: Float = 1f,
    val pitchRad: Float = 0f,
    val yawRad: Float = 0f,
    val rollRad: Float = 0f,
    val timestampMs: Long = 0L,
    val isAvailable: Boolean = false
) {
    companion object {
        val UNAVAILABLE: SixAxisMotionState = SixAxisMotionState(isAvailable = false)
    }
}
