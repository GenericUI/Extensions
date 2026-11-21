package com.nexustools.genericui.ext.media

import com.nexustools.genericui.ext.media.vlc.VlcConfiguration
import com.nexustools.genericui.ext.media.vlc.VlcDeinterlaceMode
import com.nexustools.genericui.ext.media.vlc.VlcHardwareDecodeMode
import com.nexustools.genericui.ext.media.vlc.VlcVideoRendererMode
import com.nexustools.genericui.ext.schema.BooleanFieldSpec
import com.nexustools.genericui.ext.schema.IntFieldSpec
import com.nexustools.genericui.ext.schema.ObjectSchema
import com.nexustools.genericui.ext.schema.SchemaConstraintTarget
import com.nexustools.genericui.ext.schema.SchemaFieldSpec
import com.nexustools.genericui.ext.schema.SchemaOption
import com.nexustools.genericui.ext.schema.SchemaReactiveRule
import com.nexustools.genericui.ext.schema.SchemaValueCodec
import com.nexustools.genericui.ext.schema.SchemaWidgetHint
import com.nexustools.genericui.ext.schema.SelectFieldSpec

/**
 * Builds dynamic [ObjectSchema] forms and value mappers for [MediaExtension] player settings,
 * including VLC audio output/device reactive option lookup and video decoder/renderer controls.
 */
object MediaPlayerSettingsSchemaFactory {

    const val VLC_SETTINGS_SCHEMA_ID: String = "vlc_configuration"

    fun buildVlcSettingsSchema(
        target: MediaPlayerSettingsTarget = MediaPlayerSettingsTarget.BOTH
    ): ObjectSchema {
        val nodes = mutableListOf<SchemaFieldSpec>()
        nodes.add(
            SelectFieldSpec(
                key = "audioOutputModule",
                label = "Audio Output Module",
                description = "VLC audio output module (aout) or Automatic.",
                defaultValue = "",
                optionsProvider = { audioOutputModuleOptions() },
                allowUnlistedSavedValue = true
            )
        )
        nodes.add(
            SelectFieldSpec(
                key = "audioOutputDeviceId",
                label = "Audio Output Device",
                description = "Target audio output device or System Default.",
                defaultValue = "",
                optionsProvider = { ctx ->
                    val selectedModule = ctx.getValue("audioOutputModule")?.toString()
                        ?: MediaExtension.vlcConfiguration.audioOutputModule
                    audioOutputDeviceOptions(selectedModule)
                },
                allowUnlistedSavedValue = true
            )
        )
        if (target == MediaPlayerSettingsTarget.VIDEO || target == MediaPlayerSettingsTarget.BOTH) {
            nodes.add(
                SelectFieldSpec(
                    key = "videoRenderer",
                    label = "Video Renderer",
                    description = "LibVLC video output renderer (--vout).",
                    defaultValue = VlcVideoRendererMode.AUTO.id,
                    staticOptions = VlcVideoRendererMode.entries.map { SchemaOption(it.id, it.displayName) },
                    optionsProvider = { videoRendererOptions() },
                    allowUnlistedSavedValue = true
                )
            )
            nodes.add(
                SelectFieldSpec(
                    key = "hardwareDecode",
                    label = "Hardware Decoding",
                    description = "LibVLC --avcodec-hw hardware acceleration mode.",
                    defaultValue = VlcHardwareDecodeMode.ANY.cliValue,
                    staticOptions = VlcHardwareDecodeMode.entries.map { SchemaOption(it.cliValue, it.displayName) },
                    optionsProvider = { hardwareDecodeOptions() },
                    allowUnlistedSavedValue = true
                )
            )
            nodes.add(
                SelectFieldSpec(
                    key = "deinterlaceMode",
                    label = "Deinterlace Mode",
                    description = "LibVLC video deinterlacing filter mode.",
                    defaultValue = VlcDeinterlaceMode.OFF.id,
                    staticOptions = VlcDeinterlaceMode.entries.map { SchemaOption(it.id, it.displayName) }
                )
            )
        }
        nodes.add(
            BooleanFieldSpec(
                key = "spdifPassthrough",
                label = "S/PDIF Passthrough",
                description = "Pass compressed AC3/DTS bitstreams directly to digital outputs.",
                defaultValue = false,
                widgetHint = SchemaWidgetHint.SWITCH
            )
        )
        nodes.add(
            IntFieldSpec(
                key = "networkCachingMs",
                label = "Network Caching",
                description = "Read-ahead buffer size for network media streams.",
                defaultValue = 1000,
                min = 0,
                max = 60000,
                step = 50,
                unit = "ms"
            )
        )
        nodes.add(
            IntFieldSpec(
                key = "fileCachingMs",
                label = "File Caching",
                description = "Read-ahead buffer size for local media files.",
                defaultValue = 300,
                min = 0,
                max = 60000,
                step = 50,
                unit = "ms"
            )
        )

        return ObjectSchema(
            id = VLC_SETTINGS_SCHEMA_ID,
            title = "VLC Player Settings",
            description = "Runtime audio, video renderer, hardware decoding, deinterlacing, and caching configuration.",
            nodes = nodes,
            reactiveRules = listOf(
                SchemaReactiveRule(
                    targetPath = "audioOutputDeviceId",
                    targetConstraint = SchemaConstraintTarget.OPTIONS,
                    sourcePaths = listOf("audioOutputModule"),
                    syncEvaluator = { ctx ->
                        val selectedModule = ctx.getString("audioOutputModule")
                        audioOutputDeviceOptions(selectedModule)
                    }
                )
            )
        )
    }

    fun emptySettingsSchema(target: MediaPlayerSettingsTarget): ObjectSchema = ObjectSchema(
        id = "media_player_settings_${target.name.lowercase()}",
        title = "Player Settings",
        nodes = emptyList()
    )

    fun toValueMap(config: VlcConfiguration): LinkedHashMap<String, Any?> = linkedMapOf(
        "audioOutputModule" to config.audioOutputModule,
        "audioOutputDeviceId" to config.audioOutputDeviceId,
        "videoRenderer" to config.videoRenderer.id,
        "hardwareDecode" to config.hardwareDecode.cliValue,
        "deinterlaceMode" to config.deinterlaceMode.id,
        "spdifPassthrough" to config.spdifPassthrough,
        "networkCachingMs" to config.networkCachingMs,
        "fileCachingMs" to config.fileCachingMs
    )

    fun fromValueMap(
        values: Map<String, Any?>,
        base: VlcConfiguration = VlcConfiguration()
    ): VlcConfiguration {
        val rawModule = if ("audioOutputModule" in values) values["audioOutputModule"]?.toString()?.trim().orEmpty() else base.audioOutputModule
        val rawDevice = if ("audioOutputDeviceId" in values) values["audioOutputDeviceId"]?.toString()?.trim().orEmpty() else base.audioOutputDeviceId
        val renderer = when (val raw = values["videoRenderer"]) {
            is VlcVideoRendererMode -> raw
            null -> base.videoRenderer
            else -> VlcVideoRendererMode.fromId(raw.toString())
        }
        val hwDecode = when (val raw = values["hardwareDecode"]) {
            is VlcHardwareDecodeMode -> raw
            null -> base.hardwareDecode
            else -> VlcHardwareDecodeMode.fromCliValue(raw.toString())
        }
        val deinterlace = when (val raw = values["deinterlaceMode"]) {
            is VlcDeinterlaceMode -> raw
            null -> base.deinterlaceMode
            else -> VlcDeinterlaceMode.fromId(raw.toString())
        }
        val spdif = if ("spdifPassthrough" in values) {
            SchemaValueCodec.coerceBoolean(values["spdifPassthrough"], base.spdifPassthrough)
        } else {
            base.spdifPassthrough
        }
        val netCache = if ("networkCachingMs" in values) {
            coerceIntSetting(values["networkCachingMs"], base.networkCachingMs).coerceIn(0, 60000)
        } else {
            base.networkCachingMs.coerceIn(0, 60000)
        }
        val fileCache = if ("fileCachingMs" in values) {
            coerceIntSetting(values["fileCachingMs"], base.fileCachingMs).coerceIn(0, 60000)
        } else {
            base.fileCachingMs.coerceIn(0, 60000)
        }
        return VlcConfiguration(
            audioOutputModule = rawModule,
            audioOutputDeviceId = rawDevice,
            videoRenderer = renderer,
            hardwareDecode = hwDecode,
            deinterlaceMode = deinterlace,
            spdifPassthrough = spdif,
            networkCachingMs = netCache,
            fileCachingMs = fileCache
        )
    }

    private fun coerceIntSetting(rawValue: Any?, fallback: Int): Int = when (rawValue) {
        is Number -> rawValue.toInt()
        is String -> rawValue.trim().toIntOrNull() ?: fallback
        else -> fallback
    }

    fun audioOutputModuleOptions(): List<SchemaOption> {
        val options = linkedMapOf("" to SchemaOption("", "Automatic"))
        for (module in MediaExtension.listVlcAudioOutputModules()) {
            val name = module.name.trim()
            if (name.isNotEmpty() && name !in options) {
                val label = module.description.trim().ifEmpty { name }
                options[name] = SchemaOption(name, label)
            }
        }
        return options.values.toList()
    }

    fun audioOutputDeviceOptions(selectedModule: String): List<SchemaOption> {
        val options = linkedMapOf("" to SchemaOption("", "System Default"))
        for (device in MediaExtension.listVlcAudioOutputDevices(selectedModule)) {
            val id = device.deviceId.trim()
            if (id.isNotEmpty() && id !in options) {
                val label = device.description.trim().ifEmpty { id }
                options[id] = SchemaOption(id, label)
            }
        }
        return options.values.toList()
    }

    private fun videoRendererOptions(): List<SchemaOption> {
        val supported = MediaExtension.listSupportedVlcVideoRenderers().ifEmpty { VlcVideoRendererMode.entries }
        return supported.map { SchemaOption(it.id, it.displayName) }
    }

    private fun hardwareDecodeOptions(): List<SchemaOption> {
        val supported = MediaExtension.listSupportedVlcHardwareDecodeModes().ifEmpty { VlcHardwareDecodeMode.entries }
        return supported.map { SchemaOption(it.cliValue, it.displayName) }
    }
}
