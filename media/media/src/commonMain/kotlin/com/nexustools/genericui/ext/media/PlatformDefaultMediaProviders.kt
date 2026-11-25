package com.nexustools.genericui.ext.media

/**
 * Lazily installs the active backend's default media providers when a genuine `NativeFactory` is
 * active in [com.nexustools.genericui.core.GenericUI] and providers were not already installed
 * (e.g., standalone components constructed inside `JestElectricRunner` tests).
 */
internal expect fun installPlatformDefaultMediaProvidersIfNeeded()
