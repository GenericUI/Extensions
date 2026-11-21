package com.nexustools.genericui.ext.schema

/**
 * Excludes a transient or computed property on a `@SchemaData` class from schema generation
 * and serialization.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class SchemaIgnore
