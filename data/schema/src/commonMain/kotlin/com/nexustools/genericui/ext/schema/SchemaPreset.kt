package com.nexustools.genericui.ext.schema

/**
 * Named value preset bundle inside an [ObjectSchema] (e.g., `Subtle` / `Default` / `Intense`).
 */
data class SchemaPreset(
    val id: String,
    val label: String,
    val description: String? = null,
    val values: Map<String, Any?> = emptyMap()
)
