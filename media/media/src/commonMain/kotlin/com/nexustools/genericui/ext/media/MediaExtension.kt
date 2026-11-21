@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.ext.media.vlc.VlcAudioOutputDevice
import com.nexustools.genericui.ext.media.vlc.VlcAudioOutputModule
import com.nexustools.genericui.ext.media.vlc.VlcBackendConfigHandler
import com.nexustools.genericui.ext.media.vlc.VlcConfiguration
import com.nexustools.genericui.ext.media.vlc.VlcConfigurationSchemaDescriptor
import com.nexustools.genericui.ext.media.vlc.VlcHardwareDecodeMode
import com.nexustools.genericui.ext.media.vlc.VlcVideoRendererMode
import com.nexustools.genericui.ext.schema.ObjectSchema
import com.nexustools.genericui.ext.schema.SchemaRegistry
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.media.SeekBarNativeImpl
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.system.MediaNotificationNativeImpl
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.ScreenWakeLockNativeImpl
import kotlin.concurrent.atomics.AtomicReference

/**
 * Extension coordinator for `:extensions:media`.
 *
 * `:extensions:media` is completely self-contained and does not rely on any other extensions
 * outside optional schema/overlay contracts:
 * - Native desktop targets (`:windows`, `:macos`, `:gtk`, `:qt`) use native `libvlc`.
 * - JVM `:swing` uses `libvlcj` (`uk.co.caprica.vlcj`).
 * - Mobile targets (`:android`, `:ios`) use native OS media components (`MediaPlayer` / `ExoPlayer` / `AudioRecord` and `AVPlayer`).
 * - `:web` uses native HTML5 `<video>`, `<audio>`, and WebAudio `AnalyserNode` components.
 */
object MediaExtension {
    private var videoPlayerProvider: (() -> VideoPlayerNativeImpl<*>)? = null
    private var audioPlayerProvider: (() -> AudioPlayerNativeImpl)? = null
    private var seekBarProvider: (() -> SeekBarNativeImpl<*>)? = null
    private var audioAnalyzerProvider: ((Int) -> AudioAnalyzerNativeImpl)? = null
    private var mediaSessionManagerProvider: (() -> MediaSessionManagerNativeImpl)? = null
    private var screenWakeLockProvider: (() -> ScreenWakeLockNativeImpl)? = null
    var mediaNotificationFactory: (() -> MediaNotificationNativeImpl)? = null

    private val codecSupportProviderRef = AtomicReference<MediaCodecSupportProvider?>(null)
    private val playerSettingsProviderRef = AtomicReference<MediaPlayerSettingsProvider?>(null)
    private val vlcConfigHandlerRef = AtomicReference<VlcBackendConfigHandler?>(null)
    private val vlcConfigurationRef = AtomicReference(VlcConfiguration())

    init {
        SchemaRegistry.register(VlcConfigurationSchemaDescriptor, VlcConfiguration::class)
    }

    val supportsVlcConfiguration: Boolean
        get() = vlcConfigHandlerRef.load() != null

    var vlcConfiguration: VlcConfiguration
        get() = vlcConfigurationRef.load()
        set(value) {
            val normalized = value.copy(
                networkCachingMs = value.networkCachingMs.coerceAtLeast(0),
                fileCachingMs = value.fileCachingMs.coerceAtLeast(0)
            )
            vlcConfigurationRef.store(normalized)
            vlcConfigHandlerRef.load()?.onVlcConfigurationChanged(normalized)
        }

    fun registerVlcConfigHandler(handler: VlcBackendConfigHandler?) {
        val previous = vlcConfigHandlerRef.load()
        vlcConfigHandlerRef.store(handler)
        if (previous !== handler) {
            previous?.onDetached()
        }
        if (handler != null) {
            handler.onVlcConfigurationChanged(vlcConfigurationRef.load())
        }
    }

    fun registerCodecSupportProvider(provider: MediaCodecSupportProvider?) {
        codecSupportProviderRef.store(provider)
    }

    fun registerPlayerSettingsProvider(provider: MediaPlayerSettingsProvider?) {
        playerSettingsProviderRef.store(provider)
    }

    fun registerProviders(
        videoPlayer: (() -> VideoPlayerNativeImpl<*>)? = null,
        audioPlayer: (() -> AudioPlayerNativeImpl)? = null,
        audioAnalyzer: ((Int) -> AudioAnalyzerNativeImpl)? = null,
        mediaSessionManager: (() -> MediaSessionManagerNativeImpl)? = null,
        seekBar: (() -> SeekBarNativeImpl<*>)? = null,
        mediaNotification: (() -> MediaNotificationNativeImpl)? = null,
        screenWakeLock: (() -> ScreenWakeLockNativeImpl)? = null,
        vlcConfigHandler: VlcBackendConfigHandler? = null,
        codecSupportProvider: MediaCodecSupportProvider? = null,
        playerSettingsProvider: MediaPlayerSettingsProvider? = null
    ) {
        if (videoPlayer != null) videoPlayerProvider = videoPlayer
        if (audioPlayer != null) audioPlayerProvider = audioPlayer
        if (audioAnalyzer != null) audioAnalyzerProvider = audioAnalyzer
        if (mediaSessionManager != null) mediaSessionManagerProvider = mediaSessionManager
        if (seekBar != null) seekBarProvider = seekBar
        if (mediaNotification != null) mediaNotificationFactory = mediaNotification
        if (screenWakeLock != null) {
            screenWakeLockProvider = screenWakeLock
            MediaScreenWakeController.onWakeLockProviderUpdated()
        }
        if (vlcConfigHandler != null) {
            registerVlcConfigHandler(vlcConfigHandler)
        }
        if (codecSupportProvider != null) {
            codecSupportProviderRef.store(codecSupportProvider)
        }
        if (playerSettingsProvider != null) {
            playerSettingsProviderRef.store(playerSettingsProvider)
        }
    }

    fun listVlcAudioOutputModules(): List<VlcAudioOutputModule> =
        vlcConfigHandlerRef.load()?.listAudioOutputModules() ?: emptyList()

    fun listVlcAudioOutputDevices(
        audioOutputModule: String = vlcConfiguration.audioOutputModule
    ): List<VlcAudioOutputDevice> =
        vlcConfigHandlerRef.load()?.listAudioOutputDevices(audioOutputModule) ?: emptyList()

    fun listSupportedVlcHardwareDecodeModes(): List<VlcHardwareDecodeMode> =
        vlcConfigHandlerRef.load()?.listSupportedHardwareDecodeModes() ?: emptyList()

    fun listSupportedVlcVideoRenderers(): List<VlcVideoRendererMode> =
        vlcConfigHandlerRef.load()?.listSupportedVideoRenderers() ?: emptyList()

    fun createVideoPlayerNative(): VideoPlayerNativeImpl<*>? = videoPlayerProvider?.invoke()

    fun createVideoPlayerNativeOrFallback(): VideoPlayerNativeImpl<*> =
        videoPlayerProvider?.invoke()
            ?: throw IllegalStateException("No VideoPlayerNativeImpl provider registered in MediaExtension")

    fun createAudioPlayerNative(): AudioPlayerNativeImpl? = audioPlayerProvider?.invoke()

    fun createAudioPlayerNativeOrFallback(): AudioPlayerNativeImpl =
        audioPlayerProvider?.invoke()
            ?: throw IllegalStateException("No AudioPlayerNativeImpl provider registered in MediaExtension")

    fun createSeekBarNative(): SeekBarNativeImpl<*>? = seekBarProvider?.invoke()

    fun createSeekBarNativeOrFallback(): SeekBarNativeImpl<*> =
        seekBarProvider?.invoke()
            ?: throw IllegalStateException("No SeekBarNativeImpl provider registered in MediaExtension")

    fun createAudioAnalyzerNative(fftSize: Int = 2048): AudioAnalyzerNativeImpl =
        audioAnalyzerProvider?.invoke(fftSize)
            ?: throw IllegalStateException("No AudioAnalyzerNativeImpl provider registered in MediaExtension")

    fun createMediaSessionManagerNative(): MediaSessionManagerNativeImpl? =
        mediaSessionManagerProvider?.invoke()

    fun createMediaNotificationNative(): MediaNotificationNativeImpl? =
        mediaNotificationFactory?.invoke()

    fun createScreenWakeLockNative(): ScreenWakeLockNativeImpl? =
        screenWakeLockProvider?.invoke()

    internal fun resetProvidersForTest() {
        videoPlayerProvider = null
        audioPlayerProvider = null
        seekBarProvider = null
        audioAnalyzerProvider = null
        mediaSessionManagerProvider = null
        screenWakeLockProvider = null
        mediaNotificationFactory = null
        codecSupportProviderRef.store(null)
        playerSettingsProviderRef.store(null)
        val previousHandler = vlcConfigHandlerRef.load()
        vlcConfigHandlerRef.store(null)
        previousHandler?.onDetached()
        vlcConfigurationRef.store(VlcConfiguration())
        MediaScreenWakeController.onWakeLockProviderUpdated()
    }

    /**
     * Queries the uniform codec, container, 4K, HDR10/HLG/Dolby Vision, and surround audio capabilities
     * of the active media backend via [MediaCodecSupportProvider] without instantiating any player.
     */
    fun getMediaCapabilities(): MediaCapabilitiesReport =
        codecSupportProviderRef.load()?.queryMediaCapabilities() ?: EMPTY_CAPABILITIES_REPORT

    /**
     * Checks whether the given [VideoCodec] is supported on the active media backend via [MediaCodecSupportProvider].
     */
    fun isVideoCodecSupported(codec: VideoCodec): Boolean =
        codecSupportProviderRef.load()?.isVideoCodecSupported(codec) ?: false

    /**
     * Checks whether the given [AudioCodec] is supported on the active media backend via [MediaCodecSupportProvider].
     */
    fun isAudioCodecSupported(codec: AudioCodec): Boolean =
        codecSupportProviderRef.load()?.isAudioCodecSupported(codec) ?: false

    /**
     * Returns the set of all [VideoCodec]s supported on the active media backend via [MediaCodecSupportProvider].
     */
    fun getSupportedVideoCodecs(): Set<VideoCodec> =
        codecSupportProviderRef.load()?.getSupportedVideoCodecs() ?: emptySet()

    /**
     * Returns the set of all [AudioCodec]s supported on the active media backend via [MediaCodecSupportProvider].
     */
    fun getSupportedAudioCodecs(): Set<AudioCodec> =
        codecSupportProviderRef.load()?.getSupportedAudioCodecs() ?: emptySet()

    /**
     * Returns whether the active media backend supports runtime audio track switching.
     */
    fun supportsAudioTrackSelection(): Boolean =
        codecSupportProviderRef.load()?.supportsAudioTrackSelection() ?: false

    /**
     * Returns whether the active media backend supports runtime subtitle track switching.
     */
    fun supportsSubtitleTrackSelection(): Boolean =
        codecSupportProviderRef.load()?.supportsSubtitleTrackSelection() ?: false

    /**
     * Returns `true` when the active backend exposes configurable runtime player settings
     * (e.g. LibVLC audio output/device, video renderer, hardware decoding, caching) for [target].
     */
    fun hasConfigurablePlayerSettings(
        target: MediaPlayerSettingsTarget = MediaPlayerSettingsTarget.BOTH
    ): Boolean =
        supportsVlcConfiguration || (playerSettingsProviderRef.load()?.hasConfigurableSettings(target) == true)

    /**
     * Builds the dynamic `:extensions:data:schema` [ObjectSchema] for configurable player settings
     * on the active backend, or an empty schema when no backend-level settings exist.
     */
    fun getPlayerSettingsSchema(
        target: MediaPlayerSettingsTarget = MediaPlayerSettingsTarget.BOTH
    ): ObjectSchema =
        if (supportsVlcConfiguration) {
            MediaPlayerSettingsSchemaFactory.buildVlcSettingsSchema(target)
        } else {
            playerSettingsProviderRef.load()?.buildSettingsSchema(target)
                ?: MediaPlayerSettingsSchemaFactory.emptySettingsSchema(target)
        }

    /**
     * Returns the current backend player setting values keyed by schema field key.
     */
    fun getPlayerSettingsValues(): Map<String, Any?> =
        if (supportsVlcConfiguration) {
            MediaPlayerSettingsSchemaFactory.toValueMap(vlcConfiguration)
        } else {
            playerSettingsProviderRef.load()?.getSettingsValues() ?: emptyMap()
        }

    /**
     * Applies schema-validated [values] onto the active backend player configuration.
     */
    fun applyPlayerSettingsValues(values: Map<String, Any?>) {
        if (supportsVlcConfiguration) {
            vlcConfiguration = MediaPlayerSettingsSchemaFactory.fromValueMap(values, base = vlcConfiguration)
        } else {
            playerSettingsProviderRef.load()?.applySettingsValues(values)
        }
    }

    private val EMPTY_CAPABILITIES_REPORT = MediaCapabilitiesReport(
        videoCodecs = emptyMap(),
        audioCodecs = emptyMap(),
        containers = emptySet()
    )
}
