package com.nexustools.genericui.ext.webview.desktop.engine

import com.nexustools.genericui.apple.AppleDatabaseNativeImpl
import com.nexustools.genericui.apple.AppleHttpClientNativeImpl
import com.nexustools.genericui.apple.AppleImageIoCodecNativeImpl
import com.nexustools.genericui.apple.ApplePreferenceStoreImpl
import com.nexustools.genericui.apple.AppleTcpSocketNativeImpl
import com.nexustools.genericui.apple.AppleUdpSocketNativeImpl
import com.nexustools.genericui.apple.AppleUiThreadDispatcherNativeImpl
import com.nexustools.genericui.apple.AppleUiTimerNativeImpl
import com.nexustools.genericui.apple.AppleUserDirectoryNativeImpl
import com.nexustools.genericui.apple.AppleWebSocketNativeImpl
import com.nexustools.genericui.component.layout.LayoutFactory
import com.nexustools.genericui.desktop.layout.DesktopLayoutFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.macos.MacosAnimationNativeImpl
import com.nexustools.genericui.macos.MacosFileSystemNativeImpl
import com.nexustools.genericui.macos.MacosResourceResolverImpl
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

actual object WorkerPlatformServices {
    actual val backendTarget: BackendTarget = BackendTarget.MacOS
    actual val backendName: String = "webview-worker-macos"
    actual val layoutFactory: LayoutFactory = DesktopLayoutFactory

    actual fun createImageCodec(): ImageCodecNativeImpl? = AppleImageIoCodecNativeImpl()
    actual fun createFileSystem(): FileSystemNativeImpl = MacosFileSystemNativeImpl()
    actual fun createPreferenceStore(appName: String): PreferenceStoreNativeImpl =
        ApplePreferenceStoreImpl(
            appName = appName,
            userDirectory = AppleUserDirectoryNativeImpl(
                appName = appName,
                subdirectory = "",
                useCachesDirectory = false,
                useXdgStyleConfigDirectory = true
            )
        )
    actual fun createConfigDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        AppleUserDirectoryNativeImpl(appName = appName, subdirectory = subdirectory, useCachesDirectory = false, useXdgStyleConfigDirectory = true)
    actual fun createCacheDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        AppleUserDirectoryNativeImpl(appName = appName, subdirectory = subdirectory, useCachesDirectory = true, useXdgStyleConfigDirectory = false)
    actual fun createDatabase(databaseName: String, filePath: String?, version: Int): DatabaseNativeImpl =
        AppleDatabaseNativeImpl(databaseName = databaseName, filePath = filePath ?: "$databaseName.db", version = version)
    actual fun createResourceResolver(): ResourceResolverNativeImpl = MacosResourceResolverImpl()
    actual fun createTcpSocket(): TcpSocketNativeImpl = AppleTcpSocketNativeImpl()
    actual fun createUdpSocket(): UdpSocketNativeImpl = AppleUdpSocketNativeImpl()
    actual fun createHttpClient(): HttpClientNativeImpl = AppleHttpClientNativeImpl()
    actual fun createWebSocket(): WebSocketNativeImpl = AppleWebSocketNativeImpl()
    actual fun createUiThreadDispatcher(): UiThreadDispatcherNativeImpl = AppleUiThreadDispatcherNativeImpl()
    actual fun createUiTimerScheduler(): UiTimerNativeImpl = AppleUiTimerNativeImpl()
    actual fun createAnimationScheduler(): AnimationNativeImpl = MacosAnimationNativeImpl()
}
