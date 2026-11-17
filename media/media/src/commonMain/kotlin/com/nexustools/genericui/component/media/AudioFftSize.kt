package com.nexustools.genericui.component.media

enum class AudioFftSize(val sampleCount: Int) {
    FFT_32(32),
    FFT_64(64),
    FFT_128(128),
    FFT_256(256),
    FFT_512(512),
    FFT_1024(1024),
    FFT_2048(2048),
    FFT_4096(4096),
    FFT_8192(8192),
    FFT_16384(16384),
    FFT_32768(32768);

    companion object {
        fun fromSampleCount(count: Int): AudioFftSize =
            entries.minByOrNull { kotlin.math.abs(it.sampleCount - count) } ?: FFT_2048
    }
}
