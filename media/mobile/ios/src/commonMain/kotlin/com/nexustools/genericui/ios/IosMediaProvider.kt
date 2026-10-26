package com.nexustools.genericui.ios

import com.nexustools.genericui.ext.media.mobile.MobileMediaExtensionProvider

/**
 * Wires iOS media playback and session implementations ([IosVideoPlayerImpl],
 * [IosAudioPlayerImpl], [IosAudioAnalyzerImpl], and [IosMediaSessionManagerImpl])
 * into [MobileMediaExtensionProvider].
 */
object IosMediaProvider {
    fun install() {
        MobileMediaExtensionProvider.install(
            videoPlayerFactory = { IosVideoPlayerImpl() },
            audioPlayerFactory = { IosAudioPlayerImpl() },
            audioAnalyzerFactory = { IosAudioAnalyzerImpl() },
            mediaSessionManagerFactory = { IosMediaSessionManagerImpl() }
        )
    }
}
