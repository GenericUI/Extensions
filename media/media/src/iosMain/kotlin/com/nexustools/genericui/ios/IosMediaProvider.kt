package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.media.mobile.MobileMediaExtensionProvider

/**
 * Wires Apple iOS & iPadOS ([BackendTarget.IOS] and [BackendTarget.IPadOS]) media playback and
 * session implementations ([IosVideoPlayerImpl], [IosAudioPlayerImpl], [IosAudioAnalyzerImpl],
 * [IosMediaSessionManagerImpl], [IosSeekBarImpl], and [IosScreenWakeLockImpl]) into
 * [MobileMediaExtensionProvider].
 */
object IosMediaProvider {
    fun supportsTarget(target: BackendTarget): Boolean = target.isIosFamily

    fun install(target: BackendTarget) {
        require(supportsTarget(target)) {
            "IosMediaProvider only supports iOS-family targets (IOS, IPadOS); got $target."
        }
        install()
    }

    fun install() {
        MobileMediaExtensionProvider.install(
            videoPlayerFactory = { IosVideoPlayerImpl() },
            audioPlayerFactory = { IosAudioPlayerImpl() },
            audioAnalyzerFactory = { IosAudioAnalyzerImpl() },
            mediaSessionManagerFactory = { IosMediaSessionManagerImpl() },
            seekBarFactory = { IosSeekBarImpl() },
            screenWakeLockFactory = { IosScreenWakeLockImpl() }
        )
    }
}
