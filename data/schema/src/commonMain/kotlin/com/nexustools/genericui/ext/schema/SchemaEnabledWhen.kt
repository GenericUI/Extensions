package com.nexustools.genericui.ext.schema

/**
 * Declares a compile-time enablement condition on a `@SchemaData` property.
 * When both [equalsValue] and [inValues] are empty, the condition checks truthiness of [field].
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class SchemaEnabledWhen(
    val field: String,
    val equalsValue: String = "",
    val inValues: Array<String> = [],
    val negate: Boolean = false
)
