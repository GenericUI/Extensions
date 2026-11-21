package com.nexustools.genericui.ext.schema

/**
 * Declares a compile-time visibility condition on a `@SchemaData` property.
 * When both [equalsValue] and [inValues] are empty, the condition checks truthiness of [field].
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class SchemaVisibleWhen(
    val field: String,
    val equalsValue: String = "",
    val inValues: Array<String> = [],
    val negate: Boolean = false
)
