package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceObjectCodec

/**
 * Cross-platform descriptor used by a [ServiceWorker] and platform hosts to start or focus
 * a UI fragment window with optional arguments and launch semantics.
 */
data class ServiceWorkerFragmentLaunchRequest(
    val fragmentId: String,
    val route: String? = null,
    val arguments: FragmentBundle? = null,
    val openAdditionalWindow: Boolean = false,
    val clearTask: Boolean = false,
    val bringToFront: Boolean = true
) {
    internal fun toControlPayloadJson(): String {
        val map = linkedMapOf<String, PreferenceElement>(
            "fragmentId" to PreferenceElement.ofString(fragmentId),
            "openAdditionalWindow" to PreferenceElement.ofBoolean(openAdditionalWindow),
            "clearTask" to PreferenceElement.ofBoolean(clearTask),
            "bringToFront" to PreferenceElement.ofBoolean(bringToFront)
        )
        if (!route.isNullOrBlank()) {
            map["route"] = PreferenceElement.ofString(route)
        }
        if (arguments != null && arguments.keys().isNotEmpty()) {
            map["argumentsB64"] = PreferenceElement.ofString(arguments.toBase64Url())
        }
        return PreferenceObjectCodec.elementToJson(PreferenceElement.ofObject(map))
    }

    internal companion object {
        private fun parseJsonOrThrow(json: String, context: String): PreferenceElement =
            try {
                PreferenceObjectCodec.fromJsonElement(json)
            } catch (e: IllegalArgumentException) {
                throw ServiceWorkerIpcException("Malformed $context payload: ${e.message}", e)
            }

        fun fromControlPayloadJson(json: String): ServiceWorkerFragmentLaunchRequest? {
            if (json.isBlank() || json == "null") return null
            val elem = parseJsonOrThrow(json, "fragment launch control")
            if (!elem.isObject && !elem.isMap) return null
            val fragmentId = elem["fragmentId"]?.asString()?.trim().orEmpty()
            val route = elem["route"]?.asString()?.trim()?.takeIf { it.isNotEmpty() }
            if (fragmentId.isEmpty() && route == null) return null
            val openAdditionalWindow = elem["openAdditionalWindow"]?.asBoolean(false) ?: false
            val clearTask = elem["clearTask"]?.asBoolean(false) ?: false
            val bringToFront = elem["bringToFront"]?.asBoolean(true) ?: true
            val argumentsB64 = elem["argumentsB64"]?.asString()?.trim()?.takeIf { it.isNotEmpty() }
            val arguments = argumentsB64?.let { encoded ->
                try {
                    FragmentBundle.fromBase64Url(encoded)
                } catch (e: IllegalArgumentException) {
                    throw ServiceWorkerIpcException("Malformed fragment launch arguments: ${e.message}", e)
                }
            }
            return ServiceWorkerFragmentLaunchRequest(
                fragmentId = fragmentId.ifEmpty { route.orEmpty() },
                route = route,
                arguments = arguments,
                openAdditionalWindow = openAdditionalWindow,
                clearTask = clearTask,
                bringToFront = bringToFront
            )
        }

        fun encodeCloseControlPayload(fragmentId: String, instanceId: String? = null): String {
            val map = linkedMapOf<String, PreferenceElement>(
                "fragmentId" to PreferenceElement.ofString(fragmentId)
            )
            if (!instanceId.isNullOrBlank()) {
                map["instanceId"] = PreferenceElement.ofString(instanceId)
            }
            return PreferenceObjectCodec.elementToJson(PreferenceElement.ofObject(map))
        }

        fun decodeCloseControlPayload(json: String): Pair<String, String?>? {
            if (json.isBlank() || json == "null") return null
            val elem = parseJsonOrThrow(json, "fragment close control")
            if (elem.isString) {
                val fid = elem.asString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                return fid to null
            }
            if (!elem.isObject && !elem.isMap) return null
            val fragmentId = elem["fragmentId"]?.asString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val instanceId = elem["instanceId"]?.asString()?.trim()?.takeIf { it.isNotEmpty() }
            return fragmentId to instanceId
        }

        fun encodeMountControlPayload(
            fragmentId: String,
            instanceId: String,
            route: String? = null
        ): String {
            val map = linkedMapOf<String, PreferenceElement>(
                "fragmentId" to PreferenceElement.ofString(fragmentId),
                "instanceId" to PreferenceElement.ofString(instanceId)
            )
            if (!route.isNullOrBlank()) {
                map["route"] = PreferenceElement.ofString(route)
            }
            return PreferenceObjectCodec.elementToJson(PreferenceElement.ofObject(map))
        }

        fun decodeMountControlPayload(json: String): Triple<String, String, String?>? {
            if (json.isBlank() || json == "null") return null
            val elem = parseJsonOrThrow(json, "fragment mount control")
            if (!elem.isObject && !elem.isMap) return null
            val fragmentId = elem["fragmentId"]?.asString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val instanceId = elem["instanceId"]?.asString()?.trim().orEmpty()
            val route = elem["route"]?.asString()?.trim()?.takeIf { it.isNotEmpty() }
            return Triple(fragmentId, instanceId, route)
        }
    }
}
