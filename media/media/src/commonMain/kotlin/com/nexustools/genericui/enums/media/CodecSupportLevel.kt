package com.nexustools.genericui.enums.media

/**
 * Support level for a given [VideoCodec], [AudioCodec], or [MediaContainer].
 */
enum class CodecSupportLevel {
    UNSUPPORTED,
    SOFTWARE_ONLY,
    SUPPORTED,
    HARDWARE_ACCELERATED;

    val isPlayable: Boolean
        get() = this != UNSUPPORTED
}
