package com.nexustools.genericui.ext.schema.dsl

import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.schema.SchemaFieldSpec
import com.nexustools.genericui.ext.schema.SchemaRowVariantSpec

/**
 * DSL scope for collecting variant templates inside a `polymorphicRowListField` block.
 */
@GenericUiDslMarker
class SchemaRowVariantsScope {
    private val collected = mutableListOf<SchemaRowVariantSpec>()

    fun variant(
        id: String,
        label: String = id,
        block: SchemaRowVariantBuilder.() -> Unit
    ) {
        val builder = SchemaRowVariantBuilder().apply(block)
        collected.add(
            SchemaRowVariantSpec(
                id = id,
                label = label,
                slotFields = builder.buildSlotFields()
            )
        )
    }

    fun variant(
        id: String,
        label: String,
        slotFields: List<SchemaFieldSpec>
    ) {
        collected.add(SchemaRowVariantSpec(id = id, label = label, slotFields = slotFields))
    }

    internal fun buildVariants(): List<SchemaRowVariantSpec> = collected.toList()
}
