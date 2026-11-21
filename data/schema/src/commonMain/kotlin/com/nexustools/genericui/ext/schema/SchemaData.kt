package com.nexustools.genericui.ext.schema

/**
 * Marks a Kotlin data/model class for compile-time schema descriptor generation
 * (`<ClassName>SchemaDescriptor`) by the GenericUI Gradle plugin.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class SchemaData(
    val id: String = "",
    val title: String = "",
    val description: String = ""
)
