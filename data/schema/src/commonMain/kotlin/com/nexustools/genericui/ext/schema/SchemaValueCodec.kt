package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.abs

/**
 * Coerces, migrates (`legacyKeys`), clamps, compares, and serializes schema field values
 * across Kotlin objects, wire string maps, and JSON strings.
 */
object SchemaValueCodec {

    fun resolveRawValue(values: Map<String, Any?>, node: SchemaNode): Pair<Boolean, Any?> {
        if (node.key in values) {
            return true to values[node.key]
        }
        if (node is SchemaFieldSpec) {
            for (legacyKey in node.legacyKeys) {
                if (legacyKey in values) {
                    return true to values[legacyKey]
                }
            }
        }
        return false to null
    }

    fun coerceObjectValues(
        schema: ObjectSchema,
        values: Map<String, Any?>,
        preserveUnknownKeys: Boolean = false
    ): LinkedHashMap<String, Any?> {
        val result = coerceNodesValues(schema.nodes, values)
        if (preserveUnknownKeys) {
            for ((k, v) in values) {
                if (k !in result) {
                    result[k] = v
                }
            }
        }
        return result
    }

    internal fun coerceNodesValues(
        nodes: List<SchemaNode>,
        values: Map<String, Any?>
    ): LinkedHashMap<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (node in nodes) {
            when (node) {
                is SchemaSeparatorSpec -> Unit
                is SchemaGroupSpec -> {
                    val (found, raw) = resolveRawValue(values, node)
                    val childRawMap = if (found) coerceToMap(raw) else emptyMap()
                    out[node.key] = coerceNodesValues(node.children, childRawMap)
                }
                is SchemaAsyncGroupSpec -> {
                    val (found, raw) = resolveRawValue(values, node)
                    out[node.key] = if (found) coerceToMap(raw) else linkedMapOf<String, Any?>()
                }
                is SchemaIndexedGroupSpec -> {
                    val (found, raw) = resolveRawValue(values, node)
                    val rawList = if (found) coerceIndexedGroupValue(node, raw) else List(node.count) { emptyMap<String, Any?>() }
                    val normalized = ArrayList<Map<String, Any?>>(node.count)
                    for (i in 0 until node.count) {
                        val slotRaw = rawList.getOrNull(i) ?: emptyMap()
                        normalized.add(coerceNodesValues(node.children, slotRaw))
                    }
                    out[node.key] = if (node.serializeAsJsonString) {
                        SchemaJsonCodec.toJson(normalized)
                    } else {
                        normalized
                    }
                }
                is SchemaFieldSpec -> {
                    val (found, raw) = resolveRawValue(values, node)
                    out[node.key] = if (found) {
                        coerceValue(node, raw)
                    } else {
                        defaultFieldValue(node)
                    }
                }
            }
        }
        return out
    }

    fun defaultFieldValue(field: SchemaFieldSpec): Any? = when (field) {
        is BooleanFieldSpec -> field.defaultValue
        is IntFieldSpec -> if (field.isLong) field.defaultLongValue else field.defaultValue
        is DoubleFieldSpec -> if (field.isFloat) field.defaultValue.toFloat() else field.defaultValue
        is CompoundNumberFieldSpec -> coerceValue(field, field.defaultValue)
        is StringFieldSpec -> field.defaultValue
        is ColorFieldSpec -> coerceValue(field, field.defaultValue)
        is SelectFieldSpec -> field.defaultValue
        is OrderedMultiSelectFieldSpec -> if (field.serializeAsJsonString) {
            SchemaJsonCodec.toJson(field.defaultValue)
        } else {
            field.defaultValue.toList()
        }
        is PolymorphicRowListFieldSpec -> if (field.serializeAsJsonString) {
            SchemaJsonCodec.toJson(field.defaultValue)
        } else {
            field.defaultValue.map { it.toList() }
        }
        is CubicBezierFieldSpec -> coerceCubicBezierValue(field, field.defaultValue)
        is ObjectListFieldSpec -> if (field.serializeAsJsonString) {
            SchemaJsonCodec.toJson(field.defaultValue)
        } else {
            field.defaultValue.map { coerceNodesValues(field.itemSchema.nodes, it) }
        }
        is CustomFieldSpec -> field.defaultValue
    }

    fun coerceValue(field: SchemaFieldSpec, rawValue: Any?): Any? = when (field) {
        is BooleanFieldSpec -> coerceBoolean(rawValue, field.defaultValue)
        is IntFieldSpec -> coerceIntOrLong(field, rawValue)
        is DoubleFieldSpec -> coerceDoubleOrFloat(field, rawValue)
        is CompoundNumberFieldSpec -> coerceCompoundValue(field, rawValue)
        is StringFieldSpec -> rawValue?.toString() ?: ""
        is ColorFieldSpec -> coerceColorValue(field, rawValue)
        is SelectFieldSpec -> rawValue?.toString() ?: field.defaultValue
        is OrderedMultiSelectFieldSpec -> {
            val list = coerceOrderedMultiSelectValue(field, rawValue)
            if (field.serializeAsJsonString) SchemaJsonCodec.toJson(list) else list
        }
        is PolymorphicRowListFieldSpec -> {
            val matrix = coercePolymorphicRowsValue(field, rawValue)
            if (field.serializeAsJsonString) SchemaJsonCodec.toJson(matrix) else matrix
        }
        is CubicBezierFieldSpec -> coerceCubicBezierValue(field, rawValue)
        is ObjectListFieldSpec -> {
            val items = coerceObjectListValue(field, rawValue)
            if (field.serializeAsJsonString) SchemaJsonCodec.toJson(items) else items
        }
        is CustomFieldSpec -> rawValue
    }

    fun coerceBoolean(rawValue: Any?, defaultValue: Boolean = false): Boolean = when (rawValue) {
        null -> defaultValue
        is Boolean -> rawValue
        is Number -> rawValue.toDouble() != 0.0
        is String -> when (rawValue.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off", "" -> false
            else -> defaultValue
        }
        else -> defaultValue
    }

    internal fun parseLongUnclamped(field: IntFieldSpec, rawValue: Any?): Long = when (rawValue) {
        null -> if (field.isLong) field.defaultLongValue else field.defaultValue.toLong()
        is Long -> rawValue
        is Int -> rawValue.toLong()
        is Number -> rawValue.toLong()
        is Boolean -> if (rawValue) 1L else 0L
        is String -> rawValue.trim().toLongOrNull()
            ?: rawValue.trim().toDoubleOrNull()?.toLong()
            ?: (if (field.isLong) field.defaultLongValue else field.defaultValue.toLong())
        else -> if (field.isLong) field.defaultLongValue else field.defaultValue.toLong()
    }

    fun coerceIntOrLong(field: IntFieldSpec, rawValue: Any?): Number {
        val parsedLong = parseLongUnclamped(field, rawValue)
        val minL = field.min?.toLong()
        val maxL = field.max?.toLong()
        val clamped = when {
            minL != null && maxL != null -> parsedLong.coerceIn(minL, maxL)
            minL != null -> parsedLong.coerceAtLeast(minL)
            maxL != null -> parsedLong.coerceAtMost(maxL)
            else -> parsedLong
        }
        return if (field.isLong) clamped else clamped.toInt()
    }

    internal fun parseDoubleUnclamped(field: DoubleFieldSpec, rawValue: Any?): Double = when (rawValue) {
        null -> field.defaultValue
        is Double -> rawValue
        is Float -> rawValue.toDouble()
        is Number -> rawValue.toDouble()
        is Boolean -> if (rawValue) 1.0 else 0.0
        is String -> rawValue.trim().toDoubleOrNull() ?: field.defaultValue
        else -> field.defaultValue
    }

    fun coerceDoubleOrFloat(field: DoubleFieldSpec, rawValue: Any?): Number {
        val parsed = parseDoubleUnclamped(field, rawValue)
        val minD = field.min
        val maxD = field.max
        val clamped = when {
            minD != null && maxD != null -> parsed.coerceIn(minD, maxD)
            minD != null -> parsed.coerceAtLeast(minD)
            maxD != null -> parsed.coerceAtMost(maxD)
            else -> parsed
        }
        return if (field.isFloat) clamped.toFloat() else clamped
    }

    fun coerceCompoundValue(field: CompoundNumberFieldSpec, rawValue: Any?): Any {
        if (field.tupleMode) {
            val count = field.unitLabels.size
            val rawList = parseListOrJson(rawValue)
            val out = MutableList(count) { idx ->
                val item = rawList.getOrNull(idx)
                when (item) {
                    is Number -> item.toDouble()
                    is String -> item.trim().toDoubleOrNull() ?: 0.0
                    else -> ((field.defaultValue as? List<*>)?.getOrNull(idx) as? Number)?.toDouble() ?: 0.0
                }
            }
            return out
        }
        val total = when (rawValue) {
            null -> (field.defaultValue as? Number)?.toDouble() ?: 0.0
            is Number -> rawValue.toDouble()
            is List<*> -> field.compose(rawValue.map { (it as? Number)?.toDouble() ?: it?.toString()?.toDoubleOrNull() ?: 0.0 })
            is String -> {
                val trimmed = rawValue.trim()
                if (trimmed.startsWith("[")) {
                    val parsed = parseListOrJson(trimmed)
                    field.compose(parsed.map { (it as? Number)?.toDouble() ?: it?.toString()?.toDoubleOrNull() ?: 0.0 })
                } else {
                    trimmed.toDoubleOrNull() ?: (field.defaultValue as? Number)?.toDouble() ?: 0.0
                }
            }
            else -> (field.defaultValue as? Number)?.toDouble() ?: 0.0
        }
        val minB = field.min
        val maxB = field.max
        return when {
            minB != null && maxB != null -> total.coerceIn(minB, maxB)
            minB != null -> total.coerceAtLeast(minB)
            maxB != null -> total.coerceAtMost(maxB)
            else -> total
        }
    }

    fun coerceColorValue(field: ColorFieldSpec, rawValue: Any?): Any {
        val rgba = when (rawValue) {
            is RgbaColor -> rawValue
            is String -> RgbaColor.fromCss(rawValue.trim()) ?: RgbaColor.fromHex(field.defaultValue)
            null -> RgbaColor.fromHex(field.defaultValue)
            else -> RgbaColor.fromCss(rawValue.toString()) ?: RgbaColor.fromHex(field.defaultValue)
        }
        val effective = if (field.allowAlpha) rgba else rgba.copy(a = 255)
        return if (field.returnRgbaColorValue) {
            effective
        } else {
            effective.toHex(includeAlpha = field.allowAlpha && effective.a < 255)
        }
    }

    fun coerceOrderedMultiSelectValue(field: OrderedMultiSelectFieldSpec, rawValue: Any?): List<String> {
        val rawList = parseListOrJson(rawValue)
        val strings = rawList.mapNotNull { it?.toString() }
        val deduped = if (field.allowDuplicates) strings else strings.distinct()
        val cap = field.maxItems
        return if (cap != null && cap >= 0 && deduped.size > cap) {
            deduped.take(cap)
        } else {
            deduped
        }
    }

    fun coercePolymorphicRowsValue(field: PolymorphicRowListFieldSpec, rawValue: Any?): List<List<String?>> {
        val rawOuter = parseListOrJson(rawValue)
        val out = mutableListOf<List<String?>>()
        for (rowItem in rawOuter) {
            val row = when (rowItem) {
                is List<*> -> rowItem.map { it?.toString() }
                is Array<*> -> rowItem.map { it?.toString() }
                is String -> parseListOrJson(rowItem).map { it?.toString() }
                else -> continue
            }
            out.add(row)
        }
        val cap = field.maxRows
        return if (cap != null && cap >= 0 && out.size > cap) out.take(cap) else out
    }

    fun coerceCubicBezierValue(field: CubicBezierFieldSpec, rawValue: Any?): List<Float> {
        val rawList = parseListOrJson(rawValue)
        val def = field.defaultValue
        fun readAt(idx: Int): Float {
            val item = rawList.getOrNull(idx)
            val f = when (item) {
                is Number -> item.toFloat()
                is String -> item.trim().toFloatOrNull()
                else -> null
            } ?: def.getOrElse(idx) { 0f }
            return if (idx == 0 || idx == 2) f.coerceIn(0f, 1f) else f
        }
        return listOf(readAt(0), readAt(1), readAt(2), readAt(3))
    }

    fun coerceObjectListValue(field: ObjectListFieldSpec, rawValue: Any?): List<Map<String, Any?>> {
        val rawOuter = parseListOrJson(rawValue)
        val out = mutableListOf<Map<String, Any?>>()
        for (item in rawOuter) {
            val map = coerceToMap(item)
            out.add(coerceNodesValues(field.itemSchema.nodes, map))
        }
        val cap = field.maxItems
        return if (cap != null && cap >= 0 && out.size > cap) out.take(cap) else out
    }

    fun coerceIndexedGroupValue(node: SchemaIndexedGroupSpec, rawValue: Any?): List<Map<String, Any?>> {
        val rawOuter = parseListOrJson(rawValue)
        return List(node.count) { idx ->
            val rawItem = rawOuter.getOrNull(idx)
            val map = coerceToMap(rawItem)
            coerceNodesValues(node.children, map)
        }
    }

    fun coerceToMap(rawValue: Any?): Map<String, Any?> = when (rawValue) {
        null -> emptyMap()
        is Map<*, *> -> {
            val out = LinkedHashMap<String, Any?>(rawValue.size)
            for ((k, v) in rawValue) {
                if (k != null) out[k.toString()] = v
            }
            out
        }
        is String -> {
            val trimmed = rawValue.trim()
            if (trimmed.startsWith("{")) {
                (SchemaJsonCodec.parseJson(trimmed) as? Map<*, *>)?.let { parsed ->
                    val out = LinkedHashMap<String, Any?>(parsed.size)
                    for ((k, v) in parsed) {
                        if (k != null) out[k.toString()] = v
                    }
                    out
                } ?: emptyMap()
            } else {
                emptyMap()
            }
        }
        else -> emptyMap()
    }

    fun toWireString(node: SchemaNode?, value: Any?): String {
        if (value == null) return ""
        return when (node) {
            is BooleanFieldSpec -> {
                val b = coerceBoolean(value, node.defaultValue)
                if (node.wireZeroOneStrings || node.widgetHint == SchemaWidgetHint.SELECT_ZERO_ONE) {
                    if (b) "1" else "0"
                } else {
                    b.toString()
                }
            }
            is IntFieldSpec -> {
                val n = value as? Number
                n?.toLong()?.toString() ?: value.toString()
            }
            is DoubleFieldSpec -> {
                val d = (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull()
                if (d != null) DoubleFieldSpec.formatDouble(d, node.precision) else value.toString()
            }
            is CompoundNumberFieldSpec -> {
                if (node.tupleMode) {
                    SchemaJsonCodec.toJson(value)
                } else {
                    val d = (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull()
                    if (d != null) DoubleFieldSpec.formatDouble(d, node.precision) else value.toString()
                }
            }
            is ColorFieldSpec -> when (value) {
                is RgbaColor -> value.toHex(includeAlpha = node.allowAlpha && value.a < 255)
                else -> value.toString()
            }
            is OrderedMultiSelectFieldSpec,
            is PolymorphicRowListFieldSpec,
            is CubicBezierFieldSpec,
            is ObjectListFieldSpec,
            is SchemaGroupSpec,
            is SchemaAsyncGroupSpec,
            is SchemaIndexedGroupSpec -> if (value is String) value else SchemaJsonCodec.toJson(value)
            else -> when (value) {
                is Map<*, *>, is List<*> -> SchemaJsonCodec.toJson(value)
                is RgbaColor -> value.toHex()
                else -> value.toString()
            }
        }
    }

    fun valuesEqual(node: SchemaNode?, a: Any?, b: Any?): Boolean {
        if (a === b) return true
        if (a == null && b == null) return true
        if (a == null || b == null) {
            if (node is StringFieldSpec) {
                return (a?.toString().orEmpty()) == (b?.toString().orEmpty())
            }
            return false
        }
        return when {
            node is BooleanFieldSpec -> coerceBoolean(a) == coerceBoolean(b)
            node is DoubleFieldSpec || (a is Number && b is Number) -> {
                val da = (a as? Number)?.toDouble() ?: a.toString().toDoubleOrNull()
                val db = (b as? Number)?.toDouble() ?: b.toString().toDoubleOrNull()
                if (da != null && db != null) {
                    val tol = if ((node as? DoubleFieldSpec)?.isFloat == true || a is Float || b is Float) 1e-6 else 1e-9
                    if (da == db) true else abs(da - db) <= tol
                } else {
                    a == b
                }
            }
            node is ColorFieldSpec -> {
                val ca = (a as? RgbaColor) ?: RgbaColor.fromCss(a.toString())
                val cb = (b as? RgbaColor) ?: RgbaColor.fromCss(b.toString())
                if (ca != null && cb != null) ca == cb else a.toString().equals(b.toString(), ignoreCase = true)
            }
            a is Map<*, *> && b is Map<*, *> -> {
                if (a.size != b.size) return false
                a.all { (k, va) -> b.containsKey(k) && valuesEqual(null, va, b[k]) }
            }
            a is List<*> && b is List<*> -> {
                if (a.size != b.size) return false
                a.indices.all { idx -> valuesEqual(null, a[idx], b[idx]) }
            }
            node is OrderedMultiSelectFieldSpec -> {
                val la = coerceOrderedMultiSelectValue(node, a)
                val lb = coerceOrderedMultiSelectValue(node, b)
                la == lb
            }
            node is PolymorphicRowListFieldSpec -> {
                val ma = coercePolymorphicRowsValue(node, a)
                val mb = coercePolymorphicRowsValue(node, b)
                ma == mb
            }
            node is CubicBezierFieldSpec -> {
                val ca = coerceCubicBezierValue(node, a)
                val cb = coerceCubicBezierValue(node, b)
                ca.indices.all { i -> abs(ca[i] - cb[i]) <= 1e-6f }
            }
            node is SchemaIndexedGroupSpec -> {
                val la = coerceIndexedGroupValue(node, a)
                val lb = coerceIndexedGroupValue(node, b)
                valuesEqual(null, la, lb)
            }
            else -> a == b
        }
    }

    private fun parseListOrJson(rawValue: Any?): List<*> = when (rawValue) {
        null -> emptyList<Any?>()
        is List<*> -> rawValue
        is Collection<*> -> rawValue.toList()
        is Array<*> -> rawValue.toList()
        is FloatArray -> rawValue.toList()
        is DoubleArray -> rawValue.toList()
        is IntArray -> rawValue.toList()
        is String -> {
            val trimmed = rawValue.trim()
            if (trimmed.isEmpty()) {
                emptyList<Any?>()
            } else if (trimmed.startsWith("[")) {
                (SchemaJsonCodec.parseJson(trimmed) as? List<*>) ?: emptyList<Any?>()
            } else {
                trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        else -> emptyList<Any?>()
    }
}
