package com.nexustools.genericui.ext.schema

/**
 * Declares field-level metadata, numeric constraints, presentation hints, and legacy migration keys
 * on properties of a `@SchemaData` class.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class SchemaField(
    val key: String = "",
    val label: String = "",
    val description: String = "",
    val placeholder: String = "",
    val group: String = "",
    val unit: String = "",
    val min: Double = Double.NaN,
    val max: Double = Double.NaN,
    val step: Double = Double.NaN,
    val precision: Int = -1,
    val maxLength: Int = -1,
    val required: Boolean = false,
    val secret: Boolean = false,
    val multiline: Boolean = false,
    val fullWidth: Boolean = false,
    val widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
    val legacyKeys: Array<String> = []
)
