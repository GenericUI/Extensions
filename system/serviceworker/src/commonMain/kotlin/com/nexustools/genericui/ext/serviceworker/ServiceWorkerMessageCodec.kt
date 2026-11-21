package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.system.PreferenceElement
import kotlin.reflect.KClass

/**
 * Reflection-free message serializer/deserializer contract used by generated `@ServiceWorkerMessage`
 * codecs across all 10 GenericUI KMP targets.
 */
interface ServiceWorkerMessageCodec<T : Any> {
    val typeId: String
    val messageClass: KClass<T>

    fun encode(value: T): PreferenceElement

    fun decode(element: PreferenceElement): T
}
