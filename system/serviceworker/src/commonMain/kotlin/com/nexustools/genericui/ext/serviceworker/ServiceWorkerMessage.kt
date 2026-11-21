package com.nexustools.genericui.ext.serviceworker

/**
 * Marks a Kotlin `data class` as an IPC message / request / reply payload exchanged between
 * UI clients and a `@MainServiceWorker`.
 *
 * The GenericUI Gradle codegen task generates reflection-free [ServiceWorkerMessageCodec]
 * implementations (`<Msg>ServiceWorkerCodec.kt`) and `GeneratedServiceWorkerCodecRegistrar.kt`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ServiceWorkerMessage
