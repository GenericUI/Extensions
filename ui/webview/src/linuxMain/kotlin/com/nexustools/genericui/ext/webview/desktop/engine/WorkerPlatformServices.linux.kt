package com.nexustools.genericui.ext.webview.desktop.engine

import com.nexustools.genericui.component.layout.LayoutFactory
import com.nexustools.genericui.desktop.layout.DesktopLayoutFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.gtk.GtkAnimationNativeImpl
import com.nexustools.genericui.gtk.GtkFileBrowserDialogNativeImpl
import com.nexustools.genericui.gtk.GtkUiThreadDispatcherNativeImpl
import com.nexustools.genericui.gtk.GtkUiTimerNativeImpl
import com.nexustools.genericui.linux.LinuxCurlHttpClientNativeImpl
import com.nexustools.genericui.linux.LinuxGdkPixbufImageCodecNativeImpl
import com.nexustools.genericui.linux.LinuxPosixFileSystemNativeImpl
import com.nexustools.genericui.linux.LinuxPosixTcpSocketNativeImpl
import com.nexustools.genericui.linux.LinuxPosixUdpSocketNativeImpl
import com.nexustools.genericui.linux.LinuxResourceResolverImpl
import com.nexustools.genericui.linux.LinuxRfc6455WebSocketNativeImpl
import com.nexustools.genericui.linux.LinuxSqliteDatabaseNativeImpl
import com.nexustools.genericui.linux.LinuxXdgCacheDirectoryImpl
import com.nexustools.genericui.linux.LinuxXdgConfigDirectoryImpl
import com.nexustools.genericui.linux.LinuxXdgPreferenceStoreImpl
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
    actual val backendTarget: BackendTarget = BackendTarget.Gtk
    actual val backendName: String = "webview-worker-linux"
    actual val layoutFactory: LayoutFactory = DesktopLayoutFactory

    actual fun createImageCodec(): ImageCodecNativeImpl? = LinuxGdkPixbufImageCodecNativeImpl()
    actual fun createFileSystem(): FileSystemNativeImpl =
        LinuxPosixFileSystemNativeImpl { GtkFileBrowserDialogNativeImpl() }
    actual fun createPreferenceStore(appName: String): PreferenceStoreNativeImpl = LinuxXdgPreferenceStoreImpl(appName)
    actual fun createConfigDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        LinuxXdgConfigDirectoryImpl(appName, subdirectory)
    actual fun createCacheDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        LinuxXdgCacheDirectoryImpl(appName, subdirectory)
    actual fun createDatabase(databaseName: String, filePath: String?, version: Int): DatabaseNativeImpl =
        LinuxSqliteDatabaseNativeImpl(databaseName = databaseName, filePath = filePath ?: "$databaseName.db", version = version)
    actual fun createResourceResolver(): ResourceResolverNativeImpl = LinuxResourceResolverImpl()
    actual fun createTcpSocket(): TcpSocketNativeImpl = LinuxPosixTcpSocketNativeImpl()
    actual fun createUdpSocket(): UdpSocketNativeImpl = LinuxPosixUdpSocketNativeImpl()
    actual fun createHttpClient(): HttpClientNativeImpl = LinuxCurlHttpClientNativeImpl()
    actual fun createWebSocket(): WebSocketNativeImpl = LinuxRfc6455WebSocketNativeImpl()
    actual fun createUiThreadDispatcher(): UiThreadDispatcherNativeImpl = GtkUiThreadDispatcherNativeImpl()
    actual fun createUiTimerScheduler(): UiTimerNativeImpl = GtkUiTimerNativeImpl()
    actual fun createAnimationScheduler(): AnimationNativeImpl = GtkAnimationNativeImpl()
}
