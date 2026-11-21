package com.nexustools.genericui.ext.schema

/**
 * Base sealed contract for all 12 typed field specifications inside an [ObjectSchema].
 */
sealed interface SchemaFieldSpec : SchemaNode {
    val defaultValue: Any?
    val placeholder: String?
    val required: Boolean
    val validators: List<SchemaValidator>
    val legacyKeys: List<String>
    val widgetHint: SchemaWidgetHint
    val colSpan: Int?
}
