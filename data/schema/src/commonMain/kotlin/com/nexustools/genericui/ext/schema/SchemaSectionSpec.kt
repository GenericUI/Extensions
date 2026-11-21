package com.nexustools.genericui.ext.schema

/**
 * Logical section or tab descriptor inside an [ObjectSchema].
 */
data class SchemaSectionSpec(
    val id: String,
    val title: String,
    val description: String? = null,
    val icon: String? = null
)
