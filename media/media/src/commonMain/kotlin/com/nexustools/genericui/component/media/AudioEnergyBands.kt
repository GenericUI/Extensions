package com.nexustools.genericui.component.media

/**
 * Real-time decomposed audio frequency energy bands (`[0.0 .. 1.0]`) computed by [AudioAnalyzer]
 * for driving audio-reactive visualizer shaders and spectrum displays.
 */
data class AudioEnergyBands(
    val bass: Float = 0f,
    val lowMid: Float = 0f,
    val mid: Float = 0f,
    val highMid: Float = 0f,
    val treble: Float = 0f,
    val overallRms: Float = 0f,
    val peakBeatDetected: Boolean = false
)
