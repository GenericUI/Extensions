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
import com.nexustools.genericui.windows.WindowsAnimationNativeImpl
import com.nexustools.genericui.windows.WindowsCacheDirectoryImpl
import com.nexustools.genericui.windows.WindowsConfigDirectoryImpl
import com.nexustools.genericui.windows.WindowsDatabaseNativeImpl
import com.nexustools.genericui.windows.WindowsFileSystemNativeImpl
import com.nexustools.genericui.windows.WindowsHttpClientNativeImpl
import com.nexustools.genericui.windows.WindowsPreferenceStoreImpl
import com.nexustools.genericui.windows.WindowsResourceResolverImpl
import com.nexustools.genericui.windows.WindowsTcpSocketNativeImpl
import com.nexustools.genericui.windows.WindowsUdpSocketNativeImpl
import com.nexustools.genericui.windows.WindowsUiThreadDispatcherNativeImpl
import com.nexustools.genericui.windows.WindowsUiTimerNativeImpl
import com.nexustools.genericui.windows.WindowsWebSocketNativeImpl
import com.nexustools.genericui.windows.WindowsWicImageCodecNativeImpl

actual object WorkerPlatformServices {
    actual val backendTarget: BackendTarget = BackendTarget.Windows
    actual val backendName: String = "webview-worker-windows"
    actual val layoutFactory: LayoutFactory = DesktopLayoutFactory

    actual fun createImageCodec(): ImageCodecNativeImpl? = WindowsWicImageCodecNativeImpl()
    actual fun createFileSystem(): FileSystemNativeImpl = WindowsFileSystemNativeImpl()
    actual fun createPreferenceStore(appName: String): PreferenceStoreNativeImpl = WindowsPreferenceStoreImpl(appName)
    actual fun createConfigDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        WindowsConfigDirectoryImpl(appName, subdirectory)
    actual fun createCacheDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        WindowsCacheDirectoryImpl(appName, subdirectory)
    actual fun createDatabase(databaseName: String, filePath: String?, version: Int): DatabaseNativeImpl =
        WindowsDatabaseNativeImpl(databaseName = databaseName, filePath = filePath ?: "$databaseName.db", version = version)
    actual fun createResourceResolver(): ResourceResolverNativeImpl = WindowsResourceResolverImpl()
    actual fun createTcpSocket(): TcpSocketNativeImpl = WindowsTcpSocketNativeImpl()
    actual fun createUdpSocket(): UdpSocketNativeImpl = WindowsUdpSocketNativeImpl()
    actual fun createHttpClient(): HttpClientNativeImpl = WindowsHttpClientNativeImpl()
    actual fun createWebSocket(): WebSocketNativeImpl = WindowsWebSocketNativeImpl()
    actual fun createUiThreadDispatcher(): UiThreadDispatcherNativeImpl = WindowsUiThreadDispatcherNativeImpl()
    actual fun createUiTimerScheduler(): UiTimerNativeImpl = WindowsUiTimerNativeImpl()
    actual fun createAnimationScheduler(): AnimationNativeImpl = WindowsAnimationNativeImpl()
}
