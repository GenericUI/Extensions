package com.nexustools.genericui.ext.media.swing

import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.swing.SwingAudioAnalyzerImpl
import com.nexustools.genericui.swing.SwingAudioPlayerImpl
import com.nexustools.genericui.swing.SwingMediaSessionManagerImpl
import com.nexustools.genericui.swing.SwingVideoPlayerImpl

/**
 * Wires JVM/Swing `libvlcj` (`uk.co.caprica.vlcj`) media playback into [MediaExtension].
 */
object SwingMediaExtensionProvider {
    fun install() {
        MediaExtension.registerProviders(
            videoPlayer = { SwingVideoPlayerImpl() },
            audioPlayer = { SwingAudioPlayerImpl() },
            audioAnalyzer = { fftSize ->
                SwingAudioAnalyzerImpl().also { it.fftSize = fftSize }
            },
            mediaSessionManager = { SwingMediaSessionManagerImpl() }
        )
    }
}
