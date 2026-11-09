package com.nexustools.genericui.android

import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.mobile.MobileMediaExtensionProvider

/**
 * Wires Android media playback and session implementations ([AndroidVideoPlayerImpl],
 * [AndroidAudioPlayerImpl], [AndroidAudioAnalyzerImpl], [AndroidMediaSessionManagerImpl],
 * and [AndroidMediaNotificationImpl]) into [MobileMediaExtensionProvider] and [MediaExtension].
 */
object AndroidMediaProvider {
    fun install() {
        MediaExtension.mediaNotificationFactory = { AndroidMediaNotificationImpl() }
        MobileMediaExtensionProvider.install(
            videoPlayerFactory = { AndroidVideoPlayerImpl() },
            audioPlayerFactory = { AndroidAudioPlayerImpl() },
            audioAnalyzerFactory = { AndroidAudioAnalyzerImpl() },
            mediaSessionManagerFactory = { AndroidMediaSessionManagerImpl() },
            seekBarFactory = { AndroidSeekBarImpl() }
        )
    }
}
