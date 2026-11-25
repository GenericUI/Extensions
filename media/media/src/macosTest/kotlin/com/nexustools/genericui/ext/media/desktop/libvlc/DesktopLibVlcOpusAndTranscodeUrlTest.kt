@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioFftSize
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.media.desktop.DesktopVlcMediaExtensionProvider
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.remove
import platform.posix.usleep
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies [DesktopLibVlcVideoPlayerImpl.isServerTranscodeUrl] parameter detection and
 * offline Matroska/Opus 21 ms BlockDuration playback through [DesktopLibVlcAudioPlayerImpl].
 */
class DesktopLibVlcOpusAndTranscodeUrlTest {

    @Test
    fun testIsServerTranscodeUrlRecognizesQueryCodecOverridesAndSeekParams() {
        DesktopVlcMediaExtensionProvider.install("macOS")
        val widget = DesktopNativeWidget("macOS", "libvlc-video")
        val player = DesktopLibVlcVideoPlayerImpl(nativeWidget = widget, platformName = "macOS")
        try {
            assertFalse(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1"))
            assertFalse(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1?apiKey=secret"))
            assertTrue(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1?tv=h264&ta=aac&tc=mp4"))
            assertTrue(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1?apiKey=1&tv=hevc,h265"))
            assertTrue(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1?ta=aac"))
            assertTrue(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1?apiKey=1&ta=opus"))
            assertTrue(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1?tc=mp4"))
            assertTrue(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1?apiKey=1&tc=mp4"))
            assertTrue(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1?mode=transcode"))
            assertTrue(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1/transcode/playlist.m3u8"))
            assertTrue(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1?start=30"))
            assertTrue(player.isServerTranscodeUrl("http://10.0.0.10:55522/v1/stream/abc/1/1?apiKey=1&start_seconds=45.5"))
        } finally {
            player.dispose()
        }
    }

    private fun ebmlId(id: Long): ByteArray {
        var bytesNeeded = 1
        while (bytesNeeded < 4 && (id ushr (8 * bytesNeeded)) != 0L) {
            bytesNeeded++
        }
        return ByteArray(bytesNeeded) { idx ->
            ((id ushr (8 * (bytesNeeded - 1 - idx))) and 0xFFL).toByte()
        }
    }

    private fun ebmlVint(value: Int): ByteArray {
        for (width in 1..4) {
            val max = (1 shl (7 * width)) - 2
            if (value <= max) {
                val encoded = value.toLong() or (1L shl (7 * width))
                return ByteArray(width) { idx ->
                    ((encoded ushr (8 * (width - 1 - idx))) and 0xFFL).toByte()
                }
            }
        }
        error("EBML size too large: $value")
    }

    private fun ebmlElement(id: Long, payload: ByteArray): ByteArray =
        ebmlId(id) + ebmlVint(payload.size) + payload

    private fun ebmlUInt(id: Long, value: Long): ByteArray {
        val bytes = if (value == 0L) {
            byteArrayOf(0)
        } else {
            val list = mutableListOf<Byte>()
            var rem = value
            while (rem > 0L) {
                list.add(0, (rem and 0xFFL).toByte())
                rem = rem ushr 8
            }
            list.toByteArray()
        }
        return ebmlElement(id, bytes)
    }

    private fun ebmlFloat64(id: Long, value: Double): ByteArray {
        val bits = value.toRawBits()
        return ebmlElement(id, ByteArray(8) { idx -> ((bits ushr (8 * (7 - idx))) and 0xFFL).toByte() })
    }

    private fun buildSynthetic21MsOpusMkvBytes(): ByteArray {
        val ebmlHeader = ebmlElement(
            0x1A45DFA3L,
            ebmlUInt(0x4286L, 1L) +
                ebmlUInt(0x42F7L, 1L) +
                ebmlUInt(0x42F2L, 4L) +
                ebmlUInt(0x42F3L, 8L) +
                ebmlElement(0x4282L, "matroska".encodeToByteArray()) +
                ebmlUInt(0x4287L, 4L) +
                ebmlUInt(0x4285L, 2L)
        )
        val segmentInfo = ebmlElement(
            0x1549A966L,
            ebmlUInt(0x2AD7B1L, 1_000_000L) +
                ebmlFloat64(0x4489L, 1200.0)
        )
        val opusHead = "OpusHead".encodeToByteArray() + byteArrayOf(
            0x01, 0x02,
            0x38, 0x01, // pre-skip 312 LE
            0x80.toByte(), 0xBB.toByte(), 0x00, 0x00, // 48000 Hz LE
            0x00, 0x00, // gain 0 LE
            0x00 // mapping 0
        )
        val audioSettings = ebmlElement(
            0xE1L,
            ebmlFloat64(0xB5L, 48_000.0) + ebmlUInt(0x9FL, 2L)
        )
        val trackEntry = ebmlElement(
            0xAEL,
            ebmlUInt(0xD7L, 1L) +
                ebmlUInt(0x73C5L, 1L) +
                ebmlUInt(0x83L, 2L) +
                ebmlElement(0x86L, "A_OPUS".encodeToByteArray()) +
                ebmlElement(0x63A2L, opusHead) +
                audioSettings
        )
        val tracks = ebmlElement(0x1654AE6BL, trackEntry)

        val opusSilencePacket = byteArrayOf(0xF8.toByte(), 0xFF.toByte(), 0xFE.toByte())
        var clusterPayload = ebmlUInt(0xE7L, 0L)
        var ptsMs = 0
        for (idx in 0 until 30) {
            val durMs = if (idx % 3 == 2) 21 else 20
            val blockHeader = byteArrayOf(
                0x81.toByte(),
                ((ptsMs shr 8) and 0xFF).toByte(),
                (ptsMs and 0xFF).toByte(),
                0x00
            )
            clusterPayload += ebmlElement(
                0xA0L,
                ebmlElement(0xA1L, blockHeader + opusSilencePacket) +
                    ebmlUInt(0x9BL, durMs.toLong())
            )
            ptsMs += durMs
        }
        val cluster = ebmlElement(0x1F43B675L, clusterPayload)
        val body = segmentInfo + tracks + cluster
        return ebmlHeader + ebmlId(0x18538067L) + ebmlVint(body.size) + body
    }

    @Test
    fun testDesktopLibVlcAudioPlayerDecodesSynthetic21MsOpusMkvWithAnalyzer() {
        DesktopVlcMediaExtensionProvider.install("macOS")
        val tmpDir = getenv("TMPDIR")?.toKString()?.trimEnd('/') ?: "/tmp"
        val mkvPath = "$tmpDir/genericui_opus_21ms_test.mkv"
        val data = buildSynthetic21MsOpusMkvBytes()
        val fp = fopen(mkvPath, "wb")
        assertTrue(fp != null, "Failed to open $mkvPath for writing")
        try {
            memScoped {
                val pinned = allocArray<ByteVar>(data.size)
                for (i in data.indices) {
                    pinned[i] = data[i]
                }
                fwrite(pinned, 1u, data.size.toULong(), fp)
            }
        } finally {
            fclose(fp)
        }

        val audioImpl = DesktopLibVlcAudioPlayerImpl()
        val analyzer = AudioAnalyzer(AudioAnalyzerConfig(fftSize = AudioFftSize.FFT_1024))
        var errorMessage: String? = null
        try {
            audioImpl.setOnError { msg -> errorMessage = msg }
            audioImpl.attachAudioAnalyzer(analyzer)
            audioImpl.setVolume(0.0f)
            audioImpl.setSource(mkvPath, seekOffsetSeconds = 0.0)
            audioImpl.play()
            var ticks = 0
            while (ticks < 40) {
                usleep(25_000u)
                ticks++
            }
            assertTrue(errorMessage == null, "Expected no playback error on 21ms MKV Opus: $errorMessage")
        } finally {
            audioImpl.stop()
            audioImpl.dispose()
            analyzer.dispose()
            remove(mkvPath)
        }
    }
}
