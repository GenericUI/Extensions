package com.nexustools.genericui.ext.schema

/**
 * Variant row template inside a [PolymorphicRowListFieldSpec].
 */
data class SchemaRowVariantSpec(
    val id: String,
    val label: String,
    val slotFields: List<SchemaFieldSpec>
)
