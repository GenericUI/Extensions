package com.nexustools.genericui.ext.ipc

/**
 * Platform-specific endpoint resolution defaults (OS detection, user scope tag, and short socket root).
 */
internal expect object IpcEndpointPlatformSupport {
    fun defaultTransportKind(): String
    fun currentUserScopeTag(): String
    fun shortSocketBaseDir(userHash8: String, osHint: String): String
}
