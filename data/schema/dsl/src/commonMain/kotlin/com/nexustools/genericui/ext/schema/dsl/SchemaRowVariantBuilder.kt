package com.nexustools.genericui.ext.schema.dsl

import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.schema.SchemaFieldSpec

/**
 * DSL scope for declaring slot fields within a single [com.nexustools.genericui.ext.schema.SchemaRowVariantSpec].
 */
@GenericUiDslMarker
class SchemaRowVariantBuilder : SchemaBuilderNodeScope() {
    internal fun buildSlotFields(): List<SchemaFieldSpec> =
        buildNodes().map { node ->
            node as? SchemaFieldSpec
                ?: error("Polymorphic row variants may only contain SchemaFieldSpec slot fields, got $node.")
        }
}
