package com.nexustools.genericui.ext.media

import com.nexustools.genericui.ext.schema.ObjectSchema

/**
 * Optional SPI allowing non-VLC media backends to expose backend-specific player settings
 * schemas and runtime values through [MediaExtension].
 */
interface MediaPlayerSettingsProvider {
    fun hasConfigurableSettings(target: MediaPlayerSettingsTarget = MediaPlayerSettingsTarget.BOTH): Boolean = true
    fun buildSettingsSchema(target: MediaPlayerSettingsTarget = MediaPlayerSettingsTarget.BOTH): ObjectSchema
    fun getSettingsValues(): Map<String, Any?>
    fun applySettingsValues(values: Map<String, Any?>)
}
