package com.nexustools.genericui.component.media

data class AudioAnalyzerConfig(
    val fftSize: AudioFftSize = AudioFftSize.FFT_2048,
    val smoothingTimeConstant: Float = 0.8f,
    val minDecibels: Float = -100f,
    val maxDecibels: Float = -30f
)
