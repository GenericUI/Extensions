package com.nexustools.genericui.ext.threejs.loaders

/**
 * Lightweight pure-Kotlin RFC 8259 JSON parser used by [GltfLoader] across all KMP targets.
 */
internal class GltfJsonParser(private val source: String) {
    private var pos: Int = 0

    sealed interface Value {
        data object Null : Value
        data class Bool(val value: Boolean) : Value
        data class Num(val value: Double) : Value
        data class Str(val value: String) : Value
        data class Arr(val items: List<Value>) : Value
        data class Obj(val entries: Map<String, Value>) : Value {
            fun obj(key: String): Obj? = entries[key] as? Obj
            fun arr(key: String): Arr? = entries[key] as? Arr
            fun str(key: String): String? = (entries[key] as? Str)?.value
            fun int(key: String): Int? = (entries[key] as? Num)?.value?.toInt()
            fun float(key: String): Float? = (entries[key] as? Num)?.value?.toFloat()
            fun double(key: String): Double? = (entries[key] as? Num)?.value
            fun bool(key: String): Boolean? = (entries[key] as? Bool)?.value
            fun intList(key: String): List<Int>? = arr(key)?.items?.mapNotNull { (it as? Num)?.value?.toInt() }
            fun floatList(key: String): List<Float>? = arr(key)?.items?.mapNotNull { (it as? Num)?.value?.toFloat() }
        }
    }

    fun parseRoot(): Value.Obj {
        skipWhitespace()
        if (pos >= source.length) return Value.Obj(emptyMap())
        val parsed = parseValue()
        return parsed as? Value.Obj ?: Value.Obj(emptyMap())
    }

    private fun parseValue(): Value {
        skipWhitespace()
        if (pos >= source.length) throw IllegalArgumentException("Unexpected end of glTF JSON")
        return when (val ch = source[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> Value.Str(parseString())
            't' -> expectLiteral("true", Value.Bool(true))
            'f' -> expectLiteral("false", Value.Bool(false))
            'n' -> expectLiteral("null", Value.Null)
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("Unexpected token '$ch' at JSON offset $pos")
        }
    }

    private fun parseObject(): Value.Obj {
        pos++ // '{'
        skipWhitespace()
        val map = LinkedHashMap<String, Value>()
        if (pos < source.length && source[pos] == '}') {
            pos++
            return Value.Obj(map)
        }
        while (pos < source.length) {
            skipWhitespace()
            if (pos >= source.length || source[pos] != '"') {
                throw IllegalArgumentException("Expected string key at JSON offset $pos")
            }
            val key = parseString()
            skipWhitespace()
            if (pos >= source.length || source[pos] != ':') {
                throw IllegalArgumentException("Expected ':' after key '$key' at JSON offset $pos")
            }
            pos++
            map[key] = parseValue()
            skipWhitespace()
            if (pos < source.length && source[pos] == ',') {
                pos++
            } else if (pos < source.length && source[pos] == '}') {
                pos++
                return Value.Obj(map)
            } else {
                throw IllegalArgumentException("Expected ',' or '}' in object at JSON offset $pos")
            }
        }
        throw IllegalArgumentException("Unterminated JSON object")
    }

    private fun parseArray(): Value.Arr {
        pos++ // '['
        skipWhitespace()
        val list = ArrayList<Value>()
        if (pos < source.length && source[pos] == ']') {
            pos++
            return Value.Arr(list)
        }
        while (pos < source.length) {
            list.add(parseValue())
            skipWhitespace()
            if (pos < source.length && source[pos] == ',') {
                pos++
            } else if (pos < source.length && source[pos] == ']') {
                pos++
                return Value.Arr(list)
            } else {
                throw IllegalArgumentException("Expected ',' or ']' in array at JSON offset $pos")
            }
        }
        throw IllegalArgumentException("Unterminated JSON array")
    }

    private fun parseString(): String {
        pos++ // opening '"'
        val sb = StringBuilder()
        while (pos < source.length) {
            val ch = source[pos++]
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= source.length) throw IllegalArgumentException("Unterminated string escape")
                    when (val esc = source[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > source.length) throw IllegalArgumentException("Invalid \\u escape at $pos")
                            val code = source.substring(pos, pos + 4).toInt(16)
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> sb.append(esc)
                    }
                }
                else -> sb.append(ch)
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun parseNumber(): Value.Num {
        val start = pos
        if (source[pos] == '-') pos++
        while (pos < source.length && source[pos] in '0'..'9') pos++
        if (pos < source.length && source[pos] == '.') {
            pos++
            while (pos < source.length && source[pos] in '0'..'9') pos++
        }
        if (pos < source.length && (source[pos] == 'e' || source[pos] == 'E')) {
            pos++
            if (pos < source.length && (source[pos] == '+' || source[pos] == '-')) pos++
            while (pos < source.length && source[pos] in '0'..'9') pos++
        }
        val numStr = source.substring(start, pos)
        val parsed = numStr.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid JSON number '$numStr' at offset $start")
        return Value.Num(parsed)
    }

    private fun expectLiteral(literal: String, value: Value): Value {
        if (!source.startsWith(literal, pos)) {
            throw IllegalArgumentException("Invalid JSON token at offset $pos (expected '$literal')")
        }
        pos += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (pos < source.length) {
            when (source[pos]) {
                ' ', '\t', '\r', '\n' -> pos++
                else -> return
            }
        }
    }
}
