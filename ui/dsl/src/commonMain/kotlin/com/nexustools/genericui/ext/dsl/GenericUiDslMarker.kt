package com.nexustools.genericui.ext.dsl

/**
 * DSL scope marker annotation for `GenericUI`'s `kotlinx.html`-inspired component builder DSL.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class GenericUiDslMarker
