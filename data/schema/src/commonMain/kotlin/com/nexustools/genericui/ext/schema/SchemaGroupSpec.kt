package com.nexustools.genericui.ext.schema

/**
 * Nested sub-schema container grouping [children] under [key], optionally bordered or collapsible.
 */
data class SchemaGroupSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    val bordered: Boolean = true,
    val collapsible: Boolean = false,
    val defaultExpanded: Boolean = true,
    val children: List<SchemaNode> = emptyList(),
    override val sectionId: String? = null,
    override val fullWidth: Boolean = true,
    val onMounted: ((SchemaForm) -> Unit)? = null,
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null
) : SchemaNode
