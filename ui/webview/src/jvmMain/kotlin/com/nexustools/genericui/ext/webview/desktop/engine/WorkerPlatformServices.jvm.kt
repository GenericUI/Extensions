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
import com.nexustools.genericui.swing.SwingAnimationNativeImpl
import com.nexustools.genericui.swing.SwingDatabaseNativeImpl
import com.nexustools.genericui.swing.SwingFileSystemNativeImpl
import com.nexustools.genericui.swing.SwingHttpClientNativeImpl
import com.nexustools.genericui.swing.SwingImageIoCodecImpl
import com.nexustools.genericui.swing.SwingPreferenceStoreImpl
import com.nexustools.genericui.swing.SwingResourceResolverImpl
import com.nexustools.genericui.swing.SwingTcpSocketNativeImpl
import com.nexustools.genericui.swing.SwingUdpSocketNativeImpl
import com.nexustools.genericui.swing.SwingUiThreadDispatcherNativeImpl
import com.nexustools.genericui.swing.SwingUiTimerImpl
import com.nexustools.genericui.swing.SwingUserDirectoryNativeImpl
import com.nexustools.genericui.swing.SwingWebSocketNativeImpl
import com.nexustools.genericui.threading.UiThreadDispatcherNativeImpl

actual object WorkerPlatformServices {
    actual val backendTarget: BackendTarget = BackendTarget.Swing
    actual val backendName: String = "webview-worker-jvm"
    actual val layoutFactory: LayoutFactory = DesktopLayoutFactory

    actual fun createImageCodec(): ImageCodecNativeImpl? = SwingImageIoCodecImpl()
    actual fun createFileSystem(): FileSystemNativeImpl = SwingFileSystemNativeImpl()
    actual fun createPreferenceStore(appName: String): PreferenceStoreNativeImpl = SwingPreferenceStoreImpl(appName)
    actual fun createConfigDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        SwingUserDirectoryNativeImpl(appName = appName, subdirectory = subdirectory, isCache = false)
    actual fun createCacheDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        SwingUserDirectoryNativeImpl(appName = appName, subdirectory = subdirectory, isCache = true)
    actual fun createDatabase(databaseName: String, filePath: String?, version: Int): DatabaseNativeImpl =
        SwingDatabaseNativeImpl(databaseName = databaseName, filePath = filePath ?: "$databaseName.db", version = version)
    actual fun createResourceResolver(): ResourceResolverNativeImpl = SwingResourceResolverImpl()
    actual fun createTcpSocket(): TcpSocketNativeImpl = SwingTcpSocketNativeImpl()
    actual fun createUdpSocket(): UdpSocketNativeImpl = SwingUdpSocketNativeImpl()
    actual fun createHttpClient(): HttpClientNativeImpl = SwingHttpClientNativeImpl()
    actual fun createWebSocket(): WebSocketNativeImpl = SwingWebSocketNativeImpl()
    actual fun createUiThreadDispatcher(): UiThreadDispatcherNativeImpl = SwingUiThreadDispatcherNativeImpl()
    actual fun createUiTimerScheduler(): UiTimerNativeImpl = SwingUiTimerImpl()
    actual fun createAnimationScheduler(): AnimationNativeImpl = SwingAnimationNativeImpl()
}
