package com.nexustools.genericui.ext.schema

/**
 * Homogeneous list of nested [ObjectSchema] records (`List<Map<String, Any?>>`) with add,
 * remove, and optional reordering controls.
 */
data class ObjectListFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    val itemSchema: ObjectSchema,
    override val defaultValue: List<Map<String, Any?>> = emptyList(),
    val addButtonText: String = "Add Item",
    val allowReordering: Boolean = true,
    val allowDeletion: Boolean = true,
    val maxItems: Int? = null,
    val serializeAsJsonString: Boolean = false,
    override val placeholder: String? = null,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = true,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
    override val colSpan: Int? = null
) : SchemaFieldSpec
