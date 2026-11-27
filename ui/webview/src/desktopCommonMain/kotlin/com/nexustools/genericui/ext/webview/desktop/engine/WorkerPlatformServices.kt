package com.nexustools.genericui.ext.webview.desktop.engine

import com.nexustools.genericui.component.layout.LayoutFactory
import com.nexustools.genericui.desktop.layout.DesktopLayoutFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.nativeimpl.image.ImageCodecNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl
import com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl
import com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.UdpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.WebSocketNativeImpl
import com.nexustools.genericui.nativeimpl.system.AnimationNativeImpl
import com.nexustools.genericui.nativeimpl.system.DatabaseNativeImpl
import com.nexustools.genericui.nativeimpl.system.PreferenceStoreNativeImpl
import com.nexustools.genericui.nativeimpl.system.UiTimerNativeImpl
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl
import com.nexustools.genericui.resources.ResourceResolverNativeImpl
import com.nexustools.genericui.threading.UiThreadDispatcherNativeImpl

/**
 * Provides headless platform-native I/O, image codecs, networking, preferences, and timers
 * inside an isolated desktop WebView worker process without initializing any host GUI toolkit window.
 */
expect object WorkerPlatformServices {
    val backendTarget: BackendTarget
    val backendName: String
    val layoutFactory: LayoutFactory
    fun createImageCodec(): ImageCodecNativeImpl?
    fun createFileSystem(): FileSystemNativeImpl
    fun createPreferenceStore(appName: String): PreferenceStoreNativeImpl
    fun createConfigDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl
    fun createCacheDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl
    fun createDatabase(databaseName: String, filePath: String?, version: Int): DatabaseNativeImpl
    fun createResourceResolver(): ResourceResolverNativeImpl
    fun createTcpSocket(): TcpSocketNativeImpl
    fun createUdpSocket(): UdpSocketNativeImpl
    fun createHttpClient(): HttpClientNativeImpl
    fun createWebSocket(): WebSocketNativeImpl
    fun createUiThreadDispatcher(): UiThreadDispatcherNativeImpl
    fun createUiTimerScheduler(): UiTimerNativeImpl
    fun createAnimationScheduler(): AnimationNativeImpl
}
