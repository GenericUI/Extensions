package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.form.SelectOption

internal object SchemaOptionResolver {

    fun resolveQueryFilters(
        queryFilterRefs: Map<String, SchemaFilterValue>,
        dynamicOverride: Map<String, String>?,
        context: SchemaEvalContext
    ): Map<String, String>? {
        val resolved = LinkedHashMap<String, String>()
        for ((paramKey, filterRef) in queryFilterRefs) {
            when (filterRef) {
                is SchemaFilterValue.Literal -> {
                    resolved[paramKey] = dynamicOverride?.get(paramKey) ?: filterRef.value
                }
                is SchemaFilterValue.FieldRef -> {
                    val overrideVal = dynamicOverride?.get(paramKey)
                    if (!overrideVal.isNullOrBlank()) {
                        resolved[paramKey] = overrideVal
                    } else {
                        val rawVal = context.getValue(filterRef.fieldPath)?.toString()?.trim()
                        if (!rawVal.isNullOrEmpty()) {
                            resolved[paramKey] = rawVal
                        } else if (filterRef.fallbackValue != null) {
                            resolved[paramKey] = filterRef.fallbackValue
                        } else if (filterRef.required) {
                            return null
                        }
                    }
                }
            }
        }
        if (dynamicOverride != null) {
            for ((k, v) in dynamicOverride) {
                if (k !in resolved) {
                    resolved[k] = v
                }
            }
        }
        return resolved
    }

    fun transformAndPrepend(
        rawOptions: List<SchemaOption>,
        prependNullOption: SchemaOption?,
        transformer: ((List<SchemaOption>, SchemaEvalContext) -> List<SchemaOption>)?,
        context: SchemaEvalContext
    ): List<SchemaOption> {
        val transformed = transformer?.invoke(rawOptions, context) ?: rawOptions
        if (prependNullOption == null) return transformed
        return if (transformed.any { it.value == prependNullOption.value }) {
            transformed
        } else {
            listOf(prependNullOption) + transformed
        }
    }

    fun coerceOptionsList(constraintValue: Any?): List<SchemaOption>? {
        if (constraintValue == null) return null
        val items = when (constraintValue) {
            is List<*> -> constraintValue
            is Collection<*> -> constraintValue.toList()
            is Array<*> -> constraintValue.toList()
            else -> return null
        }
        return items.mapNotNull { item ->
            when (item) {
                is SchemaOption -> item
                is SelectOption -> SchemaOption(value = item.value, label = item.label)
                is Pair<*, *> -> {
                    val v = item.first?.toString() ?: return@mapNotNull null
                    val l = item.second?.toString() ?: v
                    SchemaOption(value = v, label = l)
                }
                is Map<*, *> -> {
                    val v = item["value"]?.toString() ?: return@mapNotNull null
                    val l = item["label"]?.toString() ?: v
                    val disabled = SchemaValueCodec.coerceBoolean(item["disabled"], false)
                    val group = item["group"]?.toString()
                    SchemaOption(value = v, label = l, disabled = disabled, group = group)
                }
                is String -> SchemaOption(value = item, label = item)
                else -> SchemaOption(value = item.toString(), label = item.toString())
            }
        }
    }

    fun coerceFilterMap(constraintValue: Any?): Map<String, String>? {
        val rawMap = constraintValue as? Map<*, *> ?: return null
        val out = LinkedHashMap<String, String>(rawMap.size)
        for ((k, v) in rawMap) {
            if (k != null && v != null) {
                out[k.toString()] = v.toString()
            }
        }
        return out
    }
}
