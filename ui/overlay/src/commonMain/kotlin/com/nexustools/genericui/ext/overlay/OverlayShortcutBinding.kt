package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.event.KeyEvent

/**
 * Represents a keyboard shortcut binding handled by [OverlayInputRouter].
 */
data class OverlayShortcutBinding(
    val id: String,
    val key: String,
    val ctrlKey: Boolean = false,
    val shiftKey: Boolean = false,
    val altKey: Boolean = false,
    val metaKey: Boolean = false,
    val allowRepeat: Boolean = true,
    val action: (OverlayState, OverlayActions) -> Unit
) {
    fun matches(event: KeyEvent): Boolean {
        if (!allowRepeat && event.isRepeat) return false
        if (event.ctrlKey != ctrlKey ||
            event.shiftKey != shiftKey ||
            event.altKey != altKey ||
            event.metaKey != metaKey
        ) {
            return false
        }
        val normalizedTarget = if (key == " ") "space" else key.trim().lowercase()
        val eventKey = if (event.key == " ") " " else event.key.trim().lowercase()
        val eventCode = event.code.trim().lowercase()
        return eventKey == normalizedTarget ||
            eventCode == normalizedTarget ||
            (normalizedTarget == "space" && (eventKey == " " || eventKey == "spacebar" || eventCode == "space"))
    }
}
