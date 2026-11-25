@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.component.media.AudioFftSize
import com.nexustools.genericui.component.media.AudioPcmFftAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.res.GeneratedResourceRegistry
import com.nexustools.genericui.component.media.res.R
import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.ApplicationLifecycleExtensionHook
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.sass.SassCompiler
import com.nexustools.genericui.style.theme.ThemeStyleSheetRegistry
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
 * - Native desktop targets (`:windows`, `:macos`, `:gtk`, `:kde`) use native `libvlc`.
 * - JVM `:swing` uses `libvlcj` (`uk.co.caprica.vlcj`).
 * - Mobile targets (`:android`, `:ios`) use native OS media components (`MediaPlayer` / `ExoPlayer` / `AudioRecord` and `AVPlayer`).
 * - `:web` uses native HTML5 `<video>`, `<audio>`, and WebAudio `AnalyserNode` components.
 */
object MediaExtension {
    const val DEFAULT_STYLE_MODULE_ID: String = "ext:media:style"

    private var cachedRawStyleScss: String? = null
    private var cachedDefaultStyleSheet: CssStyleSheet? = null
    private var styleLifecycleHookInstalled: Boolean = false
    private var videoPlayerProvider: (() -> VideoPlayerNativeImpl<*>)? = null
    private var audioPlayerProvider: (() -> AudioPlayerNativeImpl)? = null
    private var audioAnalyzerProvider: ((Int) -> AudioAnalyzerNativeImpl)? = null
    private var mediaSessionManagerProvider: (() -> MediaSessionManagerNativeImpl)? = null
    private var screenWakeLockProvider: (() -> ScreenWakeLockNativeImpl)? = null
    var mediaNotificationFactory: (() -> MediaNotificationNativeImpl)? = null

    private val codecSupportProviderRef = AtomicReference<MediaCodecSupportProvider?>(null)
    private val playerSettingsProviderRef = AtomicReference<MediaPlayerSettingsProvider?>(null)
    private val vlcConfigHandlerRef = AtomicReference<VlcBackendConfigHandler?>(null)
    private val vlcConfigurationRef = AtomicReference(VlcConfiguration())
    private val autoInstalledFactoryRef = AtomicReference<NativeFactory?>(null)
    private var isInstallingPlatformDefaults: Boolean = false

    init {
        SchemaRegistry.register(VlcConfigurationSchemaDescriptor, VlcConfiguration::class)
        rawDefaultStyleScss()
    }

    /**
     * Captures and returns the raw generic `:extensions:media:media` `style.scss` source (`R.style.style`)
     * before another module with the same generated style resource ID (`0x6f0c0001`) can overwrite
     * the shared [Resources.activeTable] slot.
     */
    fun rawDefaultStyleScss(): String {
        cachedRawStyleScss?.let { return it }
        GeneratedResourceRegistry.ensureInitialized()
        return Resources.getRawText(R.style.style).also { raw ->
            if (raw.isNotBlank()) {
                cachedRawStyleScss = raw
            }
        }
    }

    /**
     * Lazily compiles and caches `:extensions:media:media`'s generic, theme-independent `style.scss`
     * stylesheet (`R.style.style`). Never compiles consumer `theme.scss`.
     */
    fun defaultStyleSheet(): CssStyleSheet {
        cachedDefaultStyleSheet?.let { return it }
        val rawScss = rawDefaultStyleScss()
        return SassCompiler.compileToStyleSheet(rawScss).also { sheet ->
            cachedDefaultStyleSheet = sheet
        }
    }

    /**
     * Installs an [ApplicationLifecycleExtensionHook] so consumer apps register [DEFAULT_STYLE_MODULE_ID]
     * in [ThemeStyleSheetRegistry] without forcing eager CSS compilation during headless construction.
     */
    fun installStyleSheetHook() {
        rawDefaultStyleScss()
        if (styleLifecycleHookInstalled) return
        styleLifecycleHookInstalled = true
        GenericUiExtensionRegistry.registerLifecycleHook(
            object : ApplicationLifecycleExtensionHook {
                override fun onAppPrepared(app: IApplication, descriptor: GenericUiAppDescriptor) {
                    if (ThemeStyleSheetRegistry.getCompiledThemeEntries().none { it.first == DEFAULT_STYLE_MODULE_ID }) {
                        ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(DEFAULT_STYLE_MODULE_ID, defaultStyleSheet())
                    }
                }
            }
        )
    }

    /**
     * Synchronously ensures [defaultStyleSheet] is registered in [ThemeStyleSheetRegistry]
     * (`"ext:media:style"`) and installs the application lifecycle hook.
     */
    fun ensureStyleSheetRegistered(): CssStyleSheet {
        val sheet = defaultStyleSheet()
        if (ThemeStyleSheetRegistry.getCompiledThemeEntries().none { it.first == DEFAULT_STYLE_MODULE_ID }) {
            ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(DEFAULT_STYLE_MODULE_ID, sheet)
        }
        installStyleSheetHook()
        return sheet
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
        mediaNotification: (() -> MediaNotificationNativeImpl)? = null,
        screenWakeLock: (() -> ScreenWakeLockNativeImpl)? = null,
        vlcConfigHandler: VlcBackendConfigHandler? = null,
        codecSupportProvider: MediaCodecSupportProvider? = null,
        playerSettingsProvider: MediaPlayerSettingsProvider? = null
    ) {
        autoInstalledFactoryRef.store(
            if (isInstallingPlatformDefaults) GenericUI.activeFactory else null
        )
        if (videoPlayer != null) videoPlayerProvider = videoPlayer
        if (audioPlayer != null) audioPlayerProvider = audioPlayer
        if (audioAnalyzer != null) audioAnalyzerProvider = audioAnalyzer
        if (mediaSessionManager != null) mediaSessionManagerProvider = mediaSessionManager
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

    private fun invalidateAutoInstalledProvidersIfFactoryChanged() {
        val installedFor = autoInstalledFactoryRef.load() ?: return
        if (GenericUI.activeFactory !== installedFor) {
            autoInstalledFactoryRef.store(null)
            resetProvidersForTest()
        }
    }

    private inline fun <T> createOrInstallDefaultProvider(
        providerGetter: () -> T?,
        missingMessage: String
    ): T {
        invalidateAutoInstalledProvidersIfFactoryChanged()
        providerGetter()?.let { return it }
        if (GenericUI.activeFactory != null) {
            isInstallingPlatformDefaults = true
            try {
                installPlatformDefaultMediaProvidersIfNeeded()
            } finally {
                isInstallingPlatformDefaults = false
            }
            providerGetter()?.let { return it }
        }
        throw IllegalStateException(missingMessage)
    }

    fun createVideoPlayerNative(): VideoPlayerNativeImpl<*>? {
        invalidateAutoInstalledProvidersIfFactoryChanged()
        return videoPlayerProvider?.invoke()
    }

    fun createVideoPlayerNativeOrFallback(): VideoPlayerNativeImpl<*> =
        createOrInstallDefaultProvider(
            providerGetter = { videoPlayerProvider?.invoke() },
            missingMessage = "No VideoPlayerNativeImpl provider registered in MediaExtension"
        )

    fun createAudioPlayerNative(): AudioPlayerNativeImpl? {
        invalidateAutoInstalledProvidersIfFactoryChanged()
        return audioPlayerProvider?.invoke()
    }

    fun createAudioPlayerNativeOrFallback(): AudioPlayerNativeImpl =
        createOrInstallDefaultProvider(
            providerGetter = { audioPlayerProvider?.invoke() },
            missingMessage = "No AudioPlayerNativeImpl provider registered in MediaExtension"
        )

    fun createAudioAnalyzerNative(fftSize: Int = 2048): AudioAnalyzerNativeImpl {
        invalidateAutoInstalledProvidersIfFactoryChanged()
        audioAnalyzerProvider?.invoke(fftSize)?.let { return it }
        if (GenericUI.activeFactory != null) {
            isInstallingPlatformDefaults = true
            try {
                installPlatformDefaultMediaProvidersIfNeeded()
            } finally {
                isInstallingPlatformDefaults = false
            }
            audioAnalyzerProvider?.invoke(fftSize)?.let { return it }
        }
        return HeadlessDspAudioAnalyzerNativeImpl(fftSize)
    }

    fun createMediaSessionManagerNative(): MediaSessionManagerNativeImpl? {
        invalidateAutoInstalledProvidersIfFactoryChanged()
        return mediaSessionManagerProvider?.invoke()
    }

    fun createMediaNotificationNative(): MediaNotificationNativeImpl? {
        invalidateAutoInstalledProvidersIfFactoryChanged()
        return mediaNotificationFactory?.invoke()
    }

    fun createScreenWakeLockNative(): ScreenWakeLockNativeImpl? {
        invalidateAutoInstalledProvidersIfFactoryChanged()
        return screenWakeLockProvider?.invoke()
    }

    internal fun resetProvidersForTest() {
        autoInstalledFactoryRef.store(null)
        videoPlayerProvider = null
        audioPlayerProvider = null
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

    private class HeadlessDspAudioAnalyzerNativeImpl(
        initialFftSize: Int = 2048
    ) : AudioAnalyzerNativeImpl {
        private val dsp = AudioPcmFftAnalyzer(
            AudioAnalyzerConfig(fftSize = AudioFftSize.fromSampleCount(initialFftSize.coerceIn(32, 32768)))
        )

        override var fftSize: Int
            get() = dsp.fftSize
            set(value) {
                dsp.fftSize = value.coerceIn(32, 32768)
            }

        override val frequencyBinCount: Int
            get() = dsp.frequencyBinCount

        override fun getByteTimeDomainData(destination: ByteArray) = dsp.getByteTimeDomainData(destination)

        override fun getFloatTimeDomainData(destination: FloatArray) = dsp.getFloatTimeDomainData(destination)

        override fun getByteFrequencyData(destination: ByteArray) = dsp.getByteFrequencyData(destination)

        override fun getFloatFrequencyData(destination: FloatArray) = dsp.getFloatFrequencyData(destination)

        override fun computeEnergyBands(): AudioEnergyBands = dsp.computeEnergyBands()

        override fun pushPcmSamples(samples: FloatArray, channels: Int) = dsp.pushPcmFloatMonoOrStereo(samples, channels = channels)

        override fun pushPcmS16Le(bytes: ByteArray, byteCount: Int, channels: Int, volumeScale: Float) =
            dsp.pushPcmS16Le(bytes, byteCount, channels, volumeScale)

        override fun dispose() = Unit
    }

    private val EMPTY_CAPABILITIES_REPORT = MediaCapabilitiesReport(
        videoCodecs = emptyMap(),
        audioCodecs = emptyMap(),
        containers = emptySet()
    )
}
