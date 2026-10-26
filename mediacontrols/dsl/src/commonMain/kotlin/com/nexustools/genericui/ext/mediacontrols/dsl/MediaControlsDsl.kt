package com.nexustools.genericui.ext.mediacontrols.dsl

import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow
import kotlin.jvm.JvmName

/**
 * Creates and optionally activates a [MediaControlsSession] configured via the `GenericUI` DSL.
 */
fun mediaControlsSession(
    window: IWindow? = null,
    metadata: MediaControlMetadata = MediaControlMetadata(),
    buttonConfig: MediaControlButtonConfig = MediaControlButtonConfig(),
    keyboardMediaKeysEnabled: Boolean = true,
    taskbarPreviewControlsEnabled: Boolean = true,
    autoActivate: Boolean = true,
    nativeImpl: MediaControlsNativeImpl = MediaControlsRegistry.createNativeImpl(),
    onEvent: ((MediaControlEvent) -> Unit)? = null,
    block: (@GenericUiDslMarker MediaControlsSession.() -> Unit)? = null
): MediaControlsSession {
    val session = MediaControlsSession(window = window, nativeImpl = nativeImpl)
    session.keyboardMediaKeysEnabled = keyboardMediaKeysEnabled
    session.taskbarPreviewControlsEnabled = taskbarPreviewControlsEnabled
    session.updateButtonConfig(buttonConfig)
    session.updateMetadata(metadata)
    if (onEvent != null) {
        session.addListener { event -> onEvent(event) }
    }
    if (block != null) {
        session.block()
    }
    if (autoActivate) {
        session.activate()
    }
    return session
}

/**
 * Binds and configures a [MediaControlsSession] directly on this [IWindow].
 */
@JvmName("bindWindowMediaControlsSession")
fun IWindow.mediaControlsSession(
    metadata: MediaControlMetadata = MediaControlMetadata(),
    buttonConfig: MediaControlButtonConfig = MediaControlButtonConfig(),
    keyboardMediaKeysEnabled: Boolean = true,
    taskbarPreviewControlsEnabled: Boolean = true,
    autoActivate: Boolean = true,
    nativeImpl: MediaControlsNativeImpl = MediaControlsRegistry.createNativeImpl(),
    onEvent: ((MediaControlEvent) -> Unit)? = null,
    block: (@GenericUiDslMarker MediaControlsSession.() -> Unit)? = null
): MediaControlsSession = com.nexustools.genericui.ext.mediacontrols.dsl.mediaControlsSession(
    window = this,
    metadata = metadata,
    buttonConfig = buttonConfig,
    keyboardMediaKeysEnabled = keyboardMediaKeysEnabled,
    taskbarPreviewControlsEnabled = taskbarPreviewControlsEnabled,
    autoActivate = autoActivate,
    nativeImpl = nativeImpl,
    onEvent = onEvent,
    block = block
)
