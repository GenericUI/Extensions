package com.nexustools.genericui.android

import com.nexustools.genericui.ext.media.mobile.MobileMediaExtensionProvider

/**
 * Wires Android media playback and session implementations ([AndroidVideoPlayerImpl],
 * [AndroidAudioPlayerImpl], [AndroidAudioAnalyzerImpl], and [AndroidMediaSessionManagerImpl])
 * into [MobileMediaExtensionProvider].
 */
object AndroidMediaProvider {
    fun install() {
        MobileMediaExtensionProvider.install(
            videoPlayerFactory = { AndroidVideoPlayerImpl() },
            audioPlayerFactory = { AndroidAudioPlayerImpl() },
            audioAnalyzerFactory = { AndroidAudioAnalyzerImpl() },
            mediaSessionManagerFactory = { AndroidMediaSessionManagerImpl() }
        )
    }
}
