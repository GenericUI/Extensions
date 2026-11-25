@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.media.desktop

import com.nexustools.genericui.desktop.core.DesktopCanvasWidgetHooks
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.desktop.system.DesktopMediaSessionManagerNativeImpl
import com.nexustools.genericui.desktop.system.DesktopScreenWakeLockNativeImpl
import com.nexustools.genericui.ext.media.LibVlcCodecSupportProvider
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcAudioAnalyzerImpl
import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcAudioPlayerImpl
import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcVideoPlayerImpl
import com.nexustools.genericui.ext.media.desktop.libvlc.LibVlcBindings
import com.nexustools.genericui.ext.media.vlc.VlcAudioOutputDevice
import com.nexustools.genericui.ext.media.vlc.VlcAudioOutputModule
import com.nexustools.genericui.ext.media.vlc.VlcBackendConfigHandler
import com.nexustools.genericui.ext.media.vlc.VlcConfiguration
import com.nexustools.genericui.ext.media.vlc.VlcDesktopOsKind
import com.nexustools.genericui.ext.media.vlc.VlcHardwareDecodeMode
import com.nexustools.genericui.ext.media.vlc.VlcPlatformCapabilities
import com.nexustools.genericui.ext.media.vlc.VlcVideoRendererMode
import com.nexustools.genericui.util.WeakReference
import kotlinx.cinterop.COpaquePointer
import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.OsFamily
import kotlin.native.Platform

/**
 * Shared desktop media extension provider that wires native `libvlc` ([DesktopLibVlcVideoPlayerImpl],
 * [DesktopLibVlcAudioPlayerImpl], [DesktopLibVlcAudioAnalyzerImpl]) into [MediaExtension] for
 * `:kde`, `:gtk`, `:windows`, and `:macos`.
 */
object DesktopVlcMediaExtensionProvider {
    private val registryLock = AtomicInt(0)
    private val videoPlayers = mutableListOf<WeakReference<DesktopLibVlcVideoPlayerImpl<*>>>()
    private val audioPlayers = mutableListOf<WeakReference<DesktopLibVlcAudioPlayerImpl>>()

    private val queryInstanceLock = AtomicInt(0)
    private var queryVlcInstance: COpaquePointer? = null

    private val vlcConfigHandler = object : VlcBackendConfigHandler {
        override fun onVlcConfigurationChanged(configuration: VlcConfiguration) {
            for (player in snapshotActiveVideoPlayers()) {
                player.applyVlcConfiguration(configuration)
            }
            for (player in snapshotActiveAudioPlayers()) {
                player.applyVlcConfiguration(configuration)
            }
        }

        override fun listAudioOutputModules(): List<VlcAudioOutputModule> =
            withQueryVlcInstance { inst ->
                LibVlcBindings.readAudioOutputModules(inst)
            }

        override fun listAudioOutputDevices(audioOutputModule: String): List<VlcAudioOutputDevice> {
            val requested = audioOutputModule.trim()
            return withQueryVlcInstance { inst ->
                if (inst == null) return@withQueryVlcInstance emptyList()
                if (requested.isNotEmpty()) {
                    return@withQueryVlcInstance LibVlcBindings.readAudioOutputDevices(inst, requested)
                }
                val modules = LibVlcBindings.readAudioOutputModules(inst)
                VlcPlatformCapabilities.resolveDefaultAudioOutputDevices(
                    os = currentDesktopOs(),
                    moduleNames = modules.map { it.name },
                    fetchDevices = { moduleName -> LibVlcBindings.readAudioOutputDevices(inst, moduleName) }
                )
            }
        }

        override fun listSupportedHardwareDecodeModes(): List<VlcHardwareDecodeMode> =
            VlcPlatformCapabilities.supportedHardwareDecodeModes(currentDesktopOs())

        override fun listSupportedVideoRenderers(): List<VlcVideoRendererMode> =
            VlcPlatformCapabilities.supportedVideoRenderers(currentDesktopOs())

        override fun onDetached() {
            releaseQueryVlcInstance()
        }
    }

    private fun currentDesktopOs(): VlcDesktopOsKind = when (Platform.osFamily) {
        OsFamily.WINDOWS -> VlcDesktopOsKind.WINDOWS
        OsFamily.MACOSX -> VlcDesktopOsKind.MACOS
        OsFamily.LINUX -> VlcDesktopOsKind.LINUX
        else -> VlcDesktopOsKind.OTHER
    }

    private inline fun <T> withRegistryLock(block: () -> T): T {
        while (!registryLock.compareAndSet(0, 1)) {
            // brief spin
        }
        return try {
            block()
        } finally {
            registryLock.store(0)
        }
    }

    private inline fun <T> withQueryVlcInstance(block: (COpaquePointer?) -> T): T {
        while (!queryInstanceLock.compareAndSet(0, 1)) {
            // brief spin
        }
        return try {
            if (!LibVlcBindings.isAvailable) {
                block(null)
            } else {
                val inst = queryVlcInstance ?: LibVlcBindings.libvlc_new(
                    listOf("--no-video", "--quiet")
                )?.also { queryVlcInstance = it }
                block(inst)
            }
        } finally {
            queryInstanceLock.store(0)
        }
    }

    private fun releaseQueryVlcInstance() {
        while (!queryInstanceLock.compareAndSet(0, 1)) {
            // brief spin
        }
        try {
            val inst = queryVlcInstance
            queryVlcInstance = null
            if (inst != null) {
                LibVlcBindings.libvlc_release(inst)
            }
        } finally {
            queryInstanceLock.store(0)
        }
    }

    internal fun registerVideoPlayer(player: DesktopLibVlcVideoPlayerImpl<*>) {
        withRegistryLock {
            videoPlayers.removeAll { ref ->
                val item = ref.get()
                item == null || item === player || item.isDisposed
            }
            videoPlayers.add(WeakReference(player))
        }
    }

    internal fun unregisterVideoPlayer(player: DesktopLibVlcVideoPlayerImpl<*>) {
        withRegistryLock {
            videoPlayers.removeAll { ref ->
                val item = ref.get()
                item == null || item === player || item.isDisposed
            }
        }
    }

    internal fun registerAudioPlayer(player: DesktopLibVlcAudioPlayerImpl) {
        withRegistryLock {
            audioPlayers.removeAll { ref ->
                val item = ref.get()
                item == null || item === player || item.isDisposed
            }
            audioPlayers.add(WeakReference(player))
        }
    }

    internal fun unregisterAudioPlayer(player: DesktopLibVlcAudioPlayerImpl) {
        withRegistryLock {
            audioPlayers.removeAll { ref ->
                val item = ref.get()
                item == null || item === player || item.isDisposed
            }
        }
    }

    private fun snapshotActiveVideoPlayers(): List<DesktopLibVlcVideoPlayerImpl<*>> =
        withRegistryLock {
            val active = mutableListOf<DesktopLibVlcVideoPlayerImpl<*>>()
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

    private fun snapshotActiveAudioPlayers(): List<DesktopLibVlcAudioPlayerImpl> =
        withRegistryLock {
            val active = mutableListOf<DesktopLibVlcAudioPlayerImpl>()
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

    fun install(
        platformName: String = "Desktop"
    ) {
        MediaExtension.registerProviders(
            videoPlayer = {
                val widget = DesktopCanvasWidgetHooks.createInteractiveSurfaceWidget?.invoke("libvlc-video")
                    ?: DesktopNativeWidget(platformName, "libvlc-video")
                DesktopLibVlcVideoPlayerImpl(
                    nativeWidget = widget,
                    platformName = platformName
                )
            },
            audioPlayer = {
                DesktopLibVlcAudioPlayerImpl()
            },
            audioAnalyzer = { fftSize ->
                val widget = DesktopCanvasWidgetHooks.createInteractiveSurfaceWidget?.invoke("libvlc-audio-analyzer")
                    ?: DesktopNativeWidget(platformName, "libvlc-audio-analyzer")
                DesktopLibVlcAudioAnalyzerImpl(
                    initialFftSize = fftSize,
                    nativeWidget = widget
                )
            },
            mediaSessionManager = {
                DesktopMediaSessionManagerNativeImpl(backendToolkit = platformName)
            },
            screenWakeLock = {
                DesktopScreenWakeLockNativeImpl()
            },
            vlcConfigHandler = vlcConfigHandler,
            codecSupportProvider = LibVlcCodecSupportProvider("libvlc [$platformName]")
        )
    }
}
