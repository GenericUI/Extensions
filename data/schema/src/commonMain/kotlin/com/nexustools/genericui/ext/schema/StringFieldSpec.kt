package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.dialog.FileTypeFilter

/**
 * Text field specification supporting single-line, multiline, masked password, autocomplete
 * suggestions, and file/folder path picker presentations.
 */
data class StringFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    override val defaultValue: String = "",
    override val placeholder: String? = null,
    val maxLength: Int? = null,
    val multiline: Boolean = false,
    val rows: Int = 4,
    val secret: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val suggestionsProvider: ((SchemaEvalContext) -> List<String>)? = null,
    val fileFilters: List<FileTypeFilter> = emptyList(),
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = false,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val colSpan: Int? = null
) : SchemaFieldSpec
