package com.nexustools.genericui.ext.gamepad

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stateful six-axis IMU calibrator and complementary-filter AHRS orientation estimator.
 *
 * Uses right-handed controller body coordinates (`+X` right, `+Y` up out of face buttons,
 * `+Z` toward player) where a flat stationary controller measures `accelG = (0, +1, 0)`.
 * Integrates bias-corrected angular velocity `(gyroRadX, gyroRadY, gyroRadZ)` and fuses
 * gravity tilt from `(accelGX, accelGY, accelGZ)` whenever accelerometer magnitude stays near `1g`.
 */
class SixAxisSensorFusion(
    alpha: Float = 0.96f
) {
    val alpha: Float = alpha.coerceIn(0f, 1f)

    var gyroBiasX: Float = 0f
        private set
    var gyroBiasY: Float = 0f
        private set
    var gyroBiasZ: Float = 0f
        private set

    private var pitchRad: Float = 0f
    private var yawRad: Float = 0f
    private var rollRad: Float = 0f
    private var hasInitializedOrientation: Boolean = false

    fun calibrateGyroBias(biasX: Float, biasY: Float, biasZ: Float) {
        gyroBiasX = biasX
        gyroBiasY = biasY
        gyroBiasZ = biasZ
    }

    fun calibrateFromStationarySamples(
        samplesGyroX: FloatArray,
        samplesGyroY: FloatArray,
        samplesGyroZ: FloatArray
    ) {
        val count = minOf(samplesGyroX.size, samplesGyroY.size, samplesGyroZ.size)
        if (count <= 0) return
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0
        for (i in 0 until count) {
            sx += samplesGyroX[i]
            sy += samplesGyroY[i]
            sz += samplesGyroZ[i]
        }
        gyroBiasX = (sx / count).toFloat()
        gyroBiasY = (sy / count).toFloat()
        gyroBiasZ = (sz / count).toFloat()
    }

    fun resetOrientation() {
        pitchRad = 0f
        yawRad = 0f
        rollRad = 0f
        hasInitializedOrientation = false
    }

    fun update(
        gyroRadX: Float,
        gyroRadY: Float,
        gyroRadZ: Float,
        accelGX: Float,
        accelGY: Float,
        accelGZ: Float,
        dtSeconds: Float,
        timestampMs: Long = 0L
    ): SixAxisMotionState {
        val gx = gyroRadX - gyroBiasX
        val gy = gyroRadY - gyroBiasY
        val gz = gyroRadZ - gyroBiasZ

        val accelMag = sqrt(accelGX * accelGX + accelGY * accelGY + accelGZ * accelGZ)
        val accelTrustworthy = accelMag in 0.5f..1.5f

        val accelPitch = atan2(-accelGZ, hypot(accelGX, accelGY))
        val accelRoll = atan2(accelGX, accelGY)

        if (!hasInitializedOrientation && accelTrustworthy) {
            pitchRad = accelPitch
            rollRad = accelRoll
            hasInitializedOrientation = true
        }

        val dt = dtSeconds.coerceIn(0f, 0.25f)
        val predPitch = wrapAngle(pitchRad + gx * dt)
        val predYaw = wrapAngle(yawRad + gy * dt)
        val predRoll = wrapAngle(rollRad + gz * dt)

        if (accelTrustworthy) {
            pitchRad = blendAngle(predPitch, accelPitch, 1f - alpha)
            rollRad = blendAngle(predRoll, accelRoll, 1f - alpha)
        } else {
            pitchRad = predPitch
            rollRad = predRoll
        }
        yawRad = predYaw

        // Convert YXZ Euler (yaw around Y, pitch around X, roll around Z) into unit quaternion
        val cy = cos(yawRad * 0.5f)
        val sy = sin(yawRad * 0.5f)
        val cp = cos(pitchRad * 0.5f)
        val sp = sin(pitchRad * 0.5f)
        val cr = cos(rollRad * 0.5f)
        val sr = sin(rollRad * 0.5f)

        val qw = cy * cp * cr + sy * sp * sr
        val qx = cy * sp * cr + sy * cp * sr
        val qy = sy * cp * cr - cy * sp * sr
        val qz = cy * cp * sr - sy * sp * cr
        val qLen = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).coerceAtLeast(1e-6f)
        val nqx = qx / qLen
        val nqy = qy / qLen
        val nqz = qz / qLen
        val nqw = qw / qLen

        // Express downward world gravity (0, -1, 0) in controller body coordinates: q^-1 * (0,-1,0) * q
        val gravityX = -(2f * (nqx * nqy + nqw * nqz))
        val gravityY = -(1f - 2f * (nqx * nqx + nqz * nqz))
        val gravityZ = -(2f * (nqy * nqz - nqw * nqx))

        return SixAxisMotionState(
            gyroRadPerSecX = gx,
            gyroRadPerSecY = gy,
            gyroRadPerSecZ = gz,
            accelGX = accelGX,
            accelGY = accelGY,
            accelGZ = accelGZ,
            gravityX = gravityX,
            gravityY = gravityY,
            gravityZ = gravityZ,
            orientationQuatX = nqx,
            orientationQuatY = nqy,
            orientationQuatZ = nqz,
            orientationQuatW = nqw,
            pitchRad = pitchRad,
            yawRad = yawRad,
            rollRad = rollRad,
            timestampMs = timestampMs,
            isAvailable = true
        )
    }

    private fun wrapAngle(angle: Float): Float {
        val twoPi = (PI * 2.0).toFloat()
        var res = angle
        while (res > PI) res -= twoPi
        while (res < -PI) res += twoPi
        return res
    }

    private fun blendAngle(from: Float, target: Float, weightToTarget: Float): Float {
        val diff = wrapAngle(target - from)
        return wrapAngle(from + diff * weightToTarget)
    }
}
