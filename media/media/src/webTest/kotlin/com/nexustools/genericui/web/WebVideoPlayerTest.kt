package com.nexustools.genericui.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebVideoPlayerTest {

    @Test
    fun testTranscodeStreamSimulatedSeekingIgnoresSpuriousPauseAndSkipsZeroSeekOnLoadedMetadata() {
        val player = WebVideoPlayerImpl()
        val playingStates = mutableListOf<Boolean>()
        val timeUpdates = mutableListOf<Double>()
        player.setOnPlayingStateChanged { playingStates.add(it) }
        player.setOnTimeUpdate { timeUpdates.add(it) }

        // 1. Load initial transcode stream with ?tv=...&ta=...&tc=... at 0.0s (without mode=transcode)
        val initialTranscodeUrl = "http://localhost:7070/v1/stream/ep1?tv=av1,vp9,hevc,h264&ta=opus,aac&tc=webm,mkv,mp4"
        player.setSource(initialTranscodeUrl, seekOffsetSeconds = 0.0, durationOverrideSeconds = 1420.0)
        assertEquals(1420.0, player.getDurationSeconds())
        assertEquals(0.0, player.getCurrentTimeSeconds())

        // On loadedmetadata of a paused transcode stream at 0s, it must NOT seek to 0.001s
        player.nativeWidget.dispatchFromBrowser("loadedmetadata", null)
        assertNull(
            player.nativeWidget.getAttribute("data-last-seek-sec"),
            "Transcode stream must not issue a 0.001s seek on loadedmetadata"
        )

        // Start playback
        player.play()
        assertTrue(player.isPlaying())
        assertEquals(listOf(true), playingStates)

        // 2. Simulate a +15s seek in transcode mode (?start=15) while playing
        val seekTranscodeUrl = "$initialTranscodeUrl&start=15"
        player.setSource(seekTranscodeUrl, seekOffsetSeconds = 15.0, durationOverrideSeconds = 1420.0)
        player.play()
        assertTrue(player.isPlaying())
        assertEquals(15.0, player.getCurrentTimeSeconds())

        // Browser's peer.load() queues an automatic "pause" event before the new stream's metadata arrives;
        // WebVideoPlayerImpl must ignore this spurious pause while isChangingSource && wantsPlayback is true!
        player.nativeWidget.dispatchFromBrowser("pause", null)
        assertTrue(
            player.isPlaying(),
            "Spurious HTML5 pause event during transcode source switch must not reset playing to false"
        )
        assertFalse(
            playingStates.contains(false),
            "Spurious HTML5 pause event during transcode source switch must not notify playing=false"
        )

        // Now metadata and timeupdate arrive from the new stream starting at relative 0.0s -> 2.5s
        player.nativeWidget.dispatchFromBrowser("loadedmetadata", null)
        assertNull(
            player.nativeWidget.getAttribute("data-last-seek-sec"),
            "Simulated seek transcode stream must not issue a 0.001s seek on loadedmetadata"
        )
        player.nativeWidget.dispatchFromBrowser("timeupdate", 2.5)
        assertEquals(17.5, player.getCurrentTimeSeconds(), 0.001)
        assertEquals(17.5, timeUpdates.last(), 0.001)

        // Explicit user pause() after stream is playing must transition to false normally
        player.pause()
        assertFalse(player.isPlaying())
        assertEquals(false, playingStates.last())
    }
}
