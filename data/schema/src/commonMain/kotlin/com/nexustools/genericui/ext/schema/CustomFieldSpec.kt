package com.nexustools.genericui.ext.schema

/**
 * Pluggable custom field descriptor mounted via a registered [SchemaCustomFieldRenderer].
 */
data class CustomFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    val rendererId: String,
    override val defaultValue: Any? = null,
    val config: Map<String, Any?> = emptyMap(),
    val inlineRenderer: SchemaCustomFieldRenderer? = null,
    override val placeholder: String? = null,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = false,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
    override val colSpan: Int? = null
) : SchemaFieldSpec
