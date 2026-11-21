package com.nexustools.genericui.ext.schema

/**
 * Option entry for [SelectFieldSpec] and [OrderedMultiSelectFieldSpec].
 */
data class SchemaOption(
    val value: String,
    val label: String = value,
    val disabled: Boolean = false,
    val group: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
)
