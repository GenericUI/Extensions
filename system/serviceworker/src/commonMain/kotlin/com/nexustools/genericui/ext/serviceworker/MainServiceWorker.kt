package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.enums.system.AppTarget

/**
 * Marks a [ServiceWorker] subclass as the background Service Worker entry point for [targets].
 *
 * At most one `@MainServiceWorker` class may apply to any enabled backend target in a project;
 * the GenericUI Gradle plugin validates this constraint and constructor requirements at compile time.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MainServiceWorker(
    vararg val targets: AppTarget = [AppTarget.COMMON]
)
