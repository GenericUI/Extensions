package com.nexustools.genericui.ext.threejs.animation

/**
 * Timed keyframe value curve (`THREE.KeyframeTrack`) targeting an object property path
 * (e.g., `".position"`, `".quaternion"`, `".scale"`).
 */
class KeyframeTrack(
    val name: String,
    val times: FloatArray,
    val values: FloatArray,
    val valueSize: Int = if (times.isNotEmpty()) values.size / times.size else 3
) {
    fun evaluateAt(time: Float, outBuffer: FloatArray = FloatArray(valueSize)): FloatArray {
        if (times.isEmpty() || valueSize <= 0) return outBuffer
        if (time <= times.first() || times.size == 1) {
            for (k in 0 until valueSize) {
                outBuffer[k] = values[k]
            }
            return outBuffer
        }
        if (time >= times.last()) {
            val base = (times.size - 1) * valueSize
            for (k in 0 until valueSize) {
                outBuffer[k] = values[base + k]
            }
            return outBuffer
        }

        var idx = 0
        while (idx + 1 < times.size && times[idx + 1] < time) {
            idx++
        }
        val t0 = times[idx]
        val t1 = times[idx + 1]
        val alpha = if (t1 > t0) ((time - t0) / (t1 - t0)).coerceIn(0f, 1f) else 0f
        val base0 = idx * valueSize
        val base1 = (idx + 1) * valueSize
        for (k in 0 until valueSize) {
            val v0 = values[base0 + k]
            val v1 = values[base1 + k]
            outBuffer[k] = v0 + (v1 - v0) * alpha
        }
        return outBuffer
    }
}
