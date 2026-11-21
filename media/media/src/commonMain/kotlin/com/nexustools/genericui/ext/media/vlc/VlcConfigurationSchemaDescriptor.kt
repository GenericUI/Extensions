package com.nexustools.genericui.ext.media.vlc

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.ext.media.MediaPlayerSettingsSchemaFactory
import com.nexustools.genericui.ext.media.MediaPlayerSettingsTarget
import com.nexustools.genericui.ext.schema.ObjectSchema
import com.nexustools.genericui.ext.schema.SchemaDescriptor
import com.nexustools.genericui.ext.schema.SchemaJsonCodec
import com.nexustools.genericui.ext.schema.SchemaPreferenceCodec

/**
 * [SchemaDescriptor] binding [VlcConfiguration] to `:extensions:data:schema` persistence and forms.
 */
object VlcConfigurationSchemaDescriptor : SchemaDescriptor<VlcConfiguration> {
    override val schema: ObjectSchema
        get() = MediaPlayerSettingsSchemaFactory.buildVlcSettingsSchema(MediaPlayerSettingsTarget.BOTH)

    override fun defaultInstance(): VlcConfiguration = VlcConfiguration()

    override fun toValueMap(instance: VlcConfiguration): Map<String, Any?> =
        MediaPlayerSettingsSchemaFactory.toValueMap(instance)

    override fun fromValueMap(values: Map<String, Any?>): VlcConfiguration =
        MediaPlayerSettingsSchemaFactory.fromValueMap(values)

    override fun toPreferenceElement(instance: VlcConfiguration): PreferenceElement =
        SchemaPreferenceCodec.toPreferenceElement(schema, toValueMap(instance))

    override fun fromPreferenceElement(element: PreferenceElement): VlcConfiguration =
        fromValueMap(SchemaPreferenceCodec.fromPreferenceElement(schema, element))

    override fun toJson(instance: VlcConfiguration): String =
        SchemaJsonCodec.encodeValues(schema, toValueMap(instance))

    override fun fromJson(json: String): VlcConfiguration =
        fromValueMap(SchemaJsonCodec.decodeValues(schema, json))

    override fun saveTo(store: PreferenceStore, keyOrPrefix: String, instance: VlcConfiguration) {
        SchemaPreferenceCodec.saveTo(store, keyOrPrefix, schema, toValueMap(instance))
    }

    override fun loadFrom(store: PreferenceStore, keyOrPrefix: String): VlcConfiguration =
        fromValueMap(SchemaPreferenceCodec.loadFrom(store, keyOrPrefix, schema))
}
