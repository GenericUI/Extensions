package com.nexustools.genericui.component.system

/**
 * Represents a transport or custom action button displayed in a [MediaNotification].
 */
data class MediaNotificationAction(
    val id: String,
    val title: String,
    val iconResId: Int = 0,
    val showInCompactView: Boolean = true
)
