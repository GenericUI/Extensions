package com.nexustools.genericui.ext.schema

/**
 * Full-width layout row break and optional horizontal divider/section label inside an [ObjectSchema].
 */
data class SchemaSeparatorSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    val showLine: Boolean = true,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = true,
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null
) : SchemaNode
