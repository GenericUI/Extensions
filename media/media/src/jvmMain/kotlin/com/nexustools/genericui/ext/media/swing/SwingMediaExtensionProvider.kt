package com.nexustools.genericui.ext.media.swing

import com.nexustools.genericui.ext.media.LibVlcCodecSupportProvider
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.vlc.VlcAudioOutputDevice
import com.nexustools.genericui.ext.media.vlc.VlcAudioOutputModule
import com.nexustools.genericui.ext.media.vlc.VlcBackendConfigHandler
import com.nexustools.genericui.ext.media.vlc.VlcConfiguration
import com.nexustools.genericui.ext.media.vlc.VlcDesktopOsKind
import com.nexustools.genericui.ext.media.vlc.VlcHardwareDecodeMode
import com.nexustools.genericui.ext.media.vlc.VlcPlatformCapabilities
import com.nexustools.genericui.ext.media.vlc.VlcVideoRendererMode
import com.nexustools.genericui.swing.SwingAudioAnalyzerImpl
import com.nexustools.genericui.swing.SwingAudioPlayerImpl
import com.nexustools.genericui.swing.SwingMediaSessionManagerImpl
import com.nexustools.genericui.swing.SwingScreenWakeLockImpl
import com.nexustools.genericui.swing.SwingSeekBarImpl
import com.nexustools.genericui.swing.SwingVideoPlayerImpl
import com.nexustools.genericui.util.WeakReference
import com.sun.jna.Platform
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.AudioDevice
import uk.co.caprica.vlcj.player.base.MediaPlayer

/**
 * Wires JVM/Swing `libvlcj` (`uk.co.caprica.vlcj`) media playback into [MediaExtension].
 */
object SwingMediaExtensionProvider {
    private val registryLock = Any()
    private val videoPlayers = mutableListOf<WeakReference<SwingVideoPlayerImpl>>()
    private val audioPlayers = mutableListOf<WeakReference<SwingAudioPlayerImpl>>()

    private val queryFactoryLock = Any()
    private var queryFactory: MediaPlayerFactory? = null

    private val vlcConfigHandler = object : VlcBackendConfigHandler {
        override fun onVlcConfigurationChanged(configuration: VlcConfiguration) {
            for (player in snapshotActiveVideoPlayers()) {
                player.applyVlcConfiguration(configuration)
            }
            for (player in snapshotActiveAudioPlayers()) {
                player.applyVlcConfiguration(configuration)
            }
        }

        override fun listAudioOutputModules(): List<VlcAudioOutputModule> {
            val factory = obtainQueryFactory() ?: return emptyList()
            return runCatching {
                factory.audio().audioOutputs().mapNotNull { out ->
                    val name = out.name?.trim().orEmpty()
                    if (name.isEmpty()) {
                        null
                    } else {
                        VlcAudioOutputModule(
                            name = name,
                            description = out.description?.trim()?.takeIf { it.isNotEmpty() } ?: name
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        override fun listAudioOutputDevices(audioOutputModule: String): List<VlcAudioOutputDevice> {
            val requested = audioOutputModule.trim()
            val factory = obtainQueryFactory() ?: return emptyList()
            return runCatching {
                val outputs = factory.audio().audioOutputs()
                if (requested.isNotEmpty()) {
                    val matched = outputs.firstOrNull { it.name.equals(requested, ignoreCase = true) }
                        ?: return@runCatching emptyList()
                    return@runCatching mapVlcjDevices(matched.devices)
                }
                VlcPlatformCapabilities.resolveDefaultAudioOutputDevices(
                    os = currentDesktopOs(),
                    moduleNames = outputs.mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotEmpty() } },
                    fetchDevices = { moduleName ->
                        val matched = outputs.firstOrNull { it.name.equals(moduleName, ignoreCase = true) }
                        mapVlcjDevices(matched?.devices)
                    }
                )
            }.getOrDefault(emptyList())
        }

        override fun listSupportedHardwareDecodeModes(): List<VlcHardwareDecodeMode> =
            VlcPlatformCapabilities.supportedHardwareDecodeModes(currentDesktopOs())

        override fun listSupportedVideoRenderers(): List<VlcVideoRendererMode> =
            VlcPlatformCapabilities.supportedVideoRenderers(currentDesktopOs())

        override fun onDetached() {
            releaseQueryFactory()
        }
    }

    private fun currentDesktopOs(): VlcDesktopOsKind = when {
        Platform.isWindows() -> VlcDesktopOsKind.WINDOWS
        Platform.isMac() -> VlcDesktopOsKind.MACOS
        Platform.isLinux() -> VlcDesktopOsKind.LINUX
        else -> VlcDesktopOsKind.OTHER
    }

    private fun mapVlcjDevices(devices: List<AudioDevice>?): List<VlcAudioOutputDevice> {
        if (devices.isNullOrEmpty()) return emptyList()
        return devices.mapNotNull { dev ->
            val id = dev.deviceId?.trim().orEmpty()
            if (id.isEmpty()) {
                null
            } else {
                VlcAudioOutputDevice(
                    deviceId = id,
                    description = dev.longName?.trim()?.takeIf { it.isNotEmpty() } ?: id
                )
            }
        }
    }

    private fun obtainQueryFactory(): MediaPlayerFactory? = synchronized(queryFactoryLock) {
        queryFactory ?: createMediaPlayerFactorySafely(
            listOf("--no-video", "--quiet", "--intf=dummy")
        )?.also { queryFactory = it }
    }

    private fun releaseQueryFactory() {
        synchronized(queryFactoryLock) {
            val factory = queryFactory
            queryFactory = null
            runCatching { factory?.release() }
        }
    }

    internal fun createMediaPlayerFactorySafely(args: List<String>): MediaPlayerFactory? {
        return try {
            MediaPlayerFactory(*args.toTypedArray())
        } catch (_: LinkageError) {
            null
        } catch (t: Throwable) {
            if (args.isNotEmpty() && t.message == "Failed to get a new native library instance") {
                runCatching { MediaPlayerFactory() }.getOrNull()
            } else {
                null
            }
        }
    }

    internal fun applyAudioConfiguration(
        mediaPlayer: MediaPlayer?,
        config: VlcConfiguration,
        previousModule: String,
        previousDeviceId: String
    ): Pair<String, String> {
        val player = mediaPlayer ?: return previousModule to previousDeviceId
        val nextModule = if (config.audioOutputModule.isNotBlank()) {
            runCatching { player.audio().setOutput(config.audioOutputModule) }
            config.audioOutputModule
        } else if (previousModule.isNotBlank()) {
            runCatching { player.audio().setOutput("any") }
            ""
        } else {
            ""
        }
        val nextDeviceId = if (config.audioOutputDeviceId.isNotBlank()) {
            runCatching {
                player.audio().setOutputDevice(
                    config.audioOutputModule.ifBlank { null },
                    config.audioOutputDeviceId
                )
            }
            config.audioOutputDeviceId
        } else if (previousDeviceId.isNotBlank()) {
            runCatching {
                player.audio().setOutputDevice(
                    config.audioOutputModule.ifBlank { null },
                    ""
                )
            }
            ""
        } else {
            ""
        }
        return nextModule to nextDeviceId
    }

    internal fun registerVideoPlayer(player: SwingVideoPlayerImpl) {
        synchronized(registryLock) {
            videoPlayers.removeAll { ref ->
                val item = ref.get()
                item == null || item === player || item.isDisposed
            }
            videoPlayers.add(WeakReference(player))
        }
    }

    internal fun unregisterVideoPlayer(player: SwingVideoPlayerImpl) {
        synchronized(registryLock) {
            videoPlayers.removeAll { ref ->
                val item = ref.get()
                item == null || item === player || item.isDisposed
            }
        }
    }

    internal fun registerAudioPlayer(player: SwingAudioPlayerImpl) {
        synchronized(registryLock) {
            audioPlayers.removeAll { ref ->
                val item = ref.get()
                item == null || item === player || item.isDisposed
            }
            audioPlayers.add(WeakReference(player))
        }
    }

    internal fun unregisterAudioPlayer(player: SwingAudioPlayerImpl) {
        synchronized(registryLock) {
            audioPlayers.removeAll { ref ->
                val item = ref.get()
                item == null || item === player || item.isDisposed
            }
        }
    }

    private fun snapshotActiveVideoPlayers(): List<SwingVideoPlayerImpl> =
        synchronized(registryLock) {
            val active = mutableListOf<SwingVideoPlayerImpl>()
            val iterator = videoPlayers.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next().get()
                if (item == null || item.isDisposed) {
                    iterator.remove()
                } else {
                    active.add(item)
                }
            }
            active
        }

    private fun snapshotActiveAudioPlayers(): List<SwingAudioPlayerImpl> =
        synchronized(registryLock) {
            val active = mutableListOf<SwingAudioPlayerImpl>()
            val iterator = audioPlayers.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next().get()
                if (item == null || item.isDisposed) {
                    iterator.remove()
                } else {
                    active.add(item)
                }
            }
            active
        }

    fun install() {
        MediaExtension.registerProviders(
            videoPlayer = { SwingVideoPlayerImpl() },
            audioPlayer = { SwingAudioPlayerImpl() },
            audioAnalyzer = { fftSize ->
                SwingAudioAnalyzerImpl().also { it.fftSize = fftSize }
            },
            mediaSessionManager = { SwingMediaSessionManagerImpl() },
            seekBar = { SwingSeekBarImpl() },
            screenWakeLock = { SwingScreenWakeLockImpl() },
            vlcConfigHandler = vlcConfigHandler,
            codecSupportProvider = LibVlcCodecSupportProvider(
                hardwareAccelerationBackend = "libvlcj (${MediaPlayerFactory::class.java.packageName})"
            )
        )
    }
}
