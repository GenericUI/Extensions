package com.nexustools.genericui.ext.schema.dsl

import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.schema.ObjectSchema
import com.nexustools.genericui.ext.schema.SchemaPreset
import com.nexustools.genericui.ext.schema.SchemaSectionSpec

/**
 * Top-level DSL builder for constructing an [ObjectSchema] with sections, presets,
 * reactive cross-field rules, and nested structural nodes.
 */
@GenericUiDslMarker
open class ObjectSchemaBuilder(
    private val id: String,
    private val title: String = "",
    private val description: String? = null,
    private val version: Int = 1
) : SchemaBuilderNodeScope() {

    private val sections = mutableListOf<SchemaSectionSpec>()
    private val presets = mutableListOf<SchemaPreset>()

    fun section(
        id: String,
        title: String = id,
        description: String? = null,
        icon: String? = null
    ) {
        sections.add(
            SchemaSectionSpec(
                id = id,
                title = title,
                description = description,
                icon = icon
            )
        )
    }

    fun preset(
        id: String,
        label: String = id,
        description: String? = null,
        values: Map<String, Any?> = emptyMap()
    ) {
        presets.add(
            SchemaPreset(
                id = id,
                label = label,
                description = description,
                values = values
            )
        )
    }

    fun build(): ObjectSchema = ObjectSchema(
        id = id,
        title = title,
        description = description,
        nodes = collectedNodes.toList(),
        sections = sections.toList(),
        presets = presets.toList(),
        reactiveRules = collectedRules.toList(),
        version = version
    )
}
