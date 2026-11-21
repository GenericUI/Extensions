package com.nexustools.genericui.ext.media

/**
 * Selects which player configuration subset [MediaExtension.getPlayerSettingsSchema] and
 * [MediaExtension.hasConfigurablePlayerSettings] describe.
 */
enum class MediaPlayerSettingsTarget {
    VIDEO,
    AUDIO,
    BOTH
}
