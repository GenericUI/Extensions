package com.nexustools.genericui.android

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.mobile.MobileMediaExtensionProvider

/**
 * Wires Android media playback and session implementations ([AndroidVideoPlayerImpl],
 * [AndroidAudioPlayerImpl], [AndroidAudioAnalyzerImpl], [AndroidMediaSessionManagerImpl],
 * and [AndroidMediaNotificationImpl]) into [MobileMediaExtensionProvider] and [MediaExtension].
 */
object AndroidMediaProvider {
    fun supportsTarget(target: BackendTarget): Boolean = target == BackendTarget.Android

    fun install(target: BackendTarget) {
        require(supportsTarget(target)) {
            "AndroidMediaProvider only supports BackendTarget.Android; got $target."
        }
        install()
    }

    fun install() {
        MediaExtension.mediaNotificationFactory = { AndroidMediaNotificationImpl() }
        MobileMediaExtensionProvider.install(
            videoPlayerFactory = { AndroidVideoPlayerImpl() },
            audioPlayerFactory = { AndroidAudioPlayerImpl() },
            audioAnalyzerFactory = { AndroidAudioAnalyzerImpl() },
            mediaSessionManagerFactory = { AndroidMediaSessionManagerImpl() },
            seekBarFactory = { AndroidSeekBarImpl() },
            screenWakeLockFactory = { AndroidScreenWakeLockImpl() },
            codecSupportProvider = AndroidMediaCodecSupportProvider()
        )
    }
}
