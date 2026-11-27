package com.nexustools.genericui.ext.ipc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IpcChannelAddressTest {

    @Test
    fun resolveDesktopEndpointProducesDeterministicNamedPipesAndShortUnixSockets() {
        val winA1 = IpcChannelAddress.resolveDesktopEndpoint(
            channelName = "ServiceWorker",
            runtimeDir = "C:\\Users\\Alice\\AppData\\Local\\App\\",
            osHint = "windows"
        )
        val winA2 = IpcChannelAddress.resolveDesktopEndpoint(
            channelName = "  serviceworker ",
            runtimeDir = "c:/users/alice/appdata/local/app",
            osHint = "win32"
        )
        val winOtherChannel = IpcChannelAddress.resolveDesktopEndpoint(
            channelName = "webview-gpu",
            runtimeDir = "C:\\Users\\Alice\\AppData\\Local\\App",
            osHint = "windows"
        )
        val winOtherDir = IpcChannelAddress.resolveDesktopEndpoint(
            channelName = "ServiceWorker",
            runtimeDir = "C:\\Users\\Alice\\AppData\\Local\\Other",
            osHint = "windows"
        )

        assertEquals(IpcChannelAddress.TRANSPORT_WINDOWS_NAMED_PIPE, winA1.transportKind)
        assertTrue(winA1.endpointAddress.startsWith("\\\\.\\pipe\\genericui."))
        assertEquals(winA1, winA2)
        assertNotEquals(winA1, winOtherChannel)
        assertNotEquals(winA1, winOtherDir)

        val linuxShort = IpcChannelAddress.resolveDesktopEndpoint(
            channelName = "renderer-1",
            runtimeDir = "/tmp/ipc-short",
            osHint = "linux"
        )
        assertEquals(IpcChannelAddress.TRANSPORT_UNIX_SOCKET, linuxShort.transportKind)
        assertTrue(linuxShort.endpointAddress.startsWith("/tmp/ipc-short/"))
        assertTrue(linuxShort.endpointAddress.endsWith(".sock"))

        val longDir = "/tmp/" + "very-long-runtime-segment-".repeat(6) + "dir"
        assertTrue(longDir.encodeToByteArray().size > 120)
        val macosLong = IpcChannelAddress.resolveDesktopEndpoint(
            channelName = "renderer-1",
            runtimeDir = longDir,
            osHint = "macos"
        )
        assertEquals(IpcChannelAddress.TRANSPORT_UNIX_SOCKET, macosLong.transportKind)
        assertTrue(macosLong.endpointAddress.encodeToByteArray().size <= 96)
        assertTrue(macosLong.endpointAddress.endsWith(".sock"))
    }

    @Test
    fun ipcConfigRejectsInvalidBounds() {
        val defaults = IpcConfig()
        assertEquals(5_000, defaults.connectTimeoutMs)
        assertEquals(16 * 1024 * 1024, defaults.maxPacketBytes)
        assertEquals(65_536, defaults.pipeBufferSizeBytes)

        assertFailsWith<IllegalArgumentException> { IpcConfig(connectTimeoutMs = -1) }
        assertFailsWith<IllegalArgumentException> { IpcConfig(maxPacketBytes = 0) }
        assertFailsWith<IllegalArgumentException> { IpcConfig(pipeBufferSizeBytes = 0) }
    }
}
