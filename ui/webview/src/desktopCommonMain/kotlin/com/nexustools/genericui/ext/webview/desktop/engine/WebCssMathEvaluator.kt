package com.nexustools.genericui.ext.webview.desktop.engine

import kotlin.math.max
import kotlin.math.min

/**
 * Evaluates W3C CSS mathematical functions (`calc(...)`, `min(...)`, `max(...)`, `clamp(...)`)
 * with nested parentheses, operator precedence, and unit conversion (`px`, `em`, `rem`, `%`,
 * `vw`, `vh`, `vmin`, `vmax`, `pt`).
 */
object WebCssMathEvaluator {

    fun evaluateLengthPx(
        rawValue: String,
        fontSizePx: Float = 16f,
        rootFontSizePx: Float = 16f,
        percentBasePx: Float = 0f,
        viewportWidthPx: Float = 1024f,
        viewportHeightPx: Float = 768f
    ): Float? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (lower == "0") return 0f
        if (lower == "auto" || lower == "none" || lower == "normal" || lower == "inherit" || lower == "initial") {
            return null
        }

        if (lower.startsWith("calc(") && lower.endsWith(")")) {
            return evalExpr(
                trimmed.substring(5, trimmed.length - 1),
                fontSizePx, rootFontSizePx, percentBasePx, viewportWidthPx, viewportHeightPx
            )
        }
        if (lower.startsWith("min(") && lower.endsWith(")")) {
            val args = splitTopLevelArgs(trimmed.substring(4, trimmed.length - 1))
            val vals = args.mapNotNull {
                evaluateLengthPx(it, fontSizePx, rootFontSizePx, percentBasePx, viewportWidthPx, viewportHeightPx)
            }
            return vals.minOrNull()
        }
        if (lower.startsWith("max(") && lower.endsWith(")")) {
            val args = splitTopLevelArgs(trimmed.substring(4, trimmed.length - 1))
            val vals = args.mapNotNull {
                evaluateLengthPx(it, fontSizePx, rootFontSizePx, percentBasePx, viewportWidthPx, viewportHeightPx)
            }
            return vals.maxOrNull()
        }
        if (lower.startsWith("clamp(") && lower.endsWith(")")) {
            val args = splitTopLevelArgs(trimmed.substring(6, trimmed.length - 1))
            if (args.size == 3) {
                val minV = evaluateLengthPx(args[0], fontSizePx, rootFontSizePx, percentBasePx, viewportWidthPx, viewportHeightPx) ?: return null
                val prefV = evaluateLengthPx(args[1], fontSizePx, rootFontSizePx, percentBasePx, viewportWidthPx, viewportHeightPx) ?: return null
                val maxV = evaluateLengthPx(args[2], fontSizePx, rootFontSizePx, percentBasePx, viewportWidthPx, viewportHeightPx) ?: return null
                return max(minV, min(prefV, maxV))
            }
        }
        return parseSingleUnitPx(trimmed, fontSizePx, rootFontSizePx, percentBasePx, viewportWidthPx, viewportHeightPx)
    }

    private fun evalExpr(
        expr: String,
        fontSizePx: Float,
        rootFontSizePx: Float,
        percentBasePx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Float? {
        val tokens = tokenize(expr)
        if (tokens.isEmpty()) return null
        val parser = ExprParser(tokens, fontSizePx, rootFontSizePx, percentBasePx, viewportWidthPx, viewportHeightPx)
        return parser.parseExpression()?.takeIf { it.isFinite() }
    }

    private fun tokenize(expr: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c == '(' || c == ')' || c == '+' || c == '*' || c == '/' || c == ',' -> {
                    out.add(c.toString())
                    i++
                }
                c == '-' -> {
                    val prev = out.lastOrNull()
                    val isUnary = prev == null || prev == "(" || prev == "+" || prev == "-" || prev == "*" || prev == "/" || prev == ","
                    val nextChar = if (i + 1 < expr.length) expr[i + 1] else '\u0000'
                    if (isUnary && (nextChar.isDigit() || nextChar == '.')) {
                        val start = i
                        i++
                        while (i < expr.length && (expr[i].isLetterOrDigit() || expr[i] == '.' || expr[i] == '%')) i++
                        out.add(expr.substring(start, i))
                    } else {
                        out.add("-")
                        i++
                    }
                }
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isLetterOrDigit() || expr[i] == '.' || expr[i] == '%')) i++
                    out.add(expr.substring(start, i))
                }
                c.isLetter() -> {
                    val start = i
                    while (i < expr.length && (expr[i].isLetterOrDigit() || expr[i] == '-')) i++
                    if (i < expr.length && expr[i] == '(') {
                        var depth = 1
                        i++
                        while (i < expr.length && depth > 0) {
                            if (expr[i] == '(') depth++
                            else if (expr[i] == ')') depth--
                            i++
                        }
                        out.add(expr.substring(start, i))
                    } else {
                        out.add(expr.substring(start, i))
                    }
                }
                else -> i++
            }
        }
        return out
    }

    private class ExprParser(
        private val tokens: List<String>,
        private val fontSizePx: Float,
        private val rootFontSizePx: Float,
        private val percentBasePx: Float,
        private val viewportWidthPx: Float,
        private val viewportHeightPx: Float
    ) {
        private var pos = 0

        fun parseExpression(): Float? {
            var left = parseTerm() ?: return null
            while (pos < tokens.size && (tokens[pos] == "+" || tokens[pos] == "-")) {
                val op = tokens[pos++]
                val right = parseTerm() ?: return null
                left = if (op == "+") left + right else left - right
            }
            return left
        }

        private fun parseTerm(): Float? {
            var left = parseFactor() ?: return null
            while (pos < tokens.size && (tokens[pos] == "*" || tokens[pos] == "/")) {
                val op = tokens[pos++]
                val right = parseFactor() ?: return null
                left = if (op == "*") left * right else if (right == 0f) return null else left / right
            }
            return left
        }

        private fun parseFactor(): Float? {
            if (pos >= tokens.size) return null
            val tok = tokens[pos]
            if (tok == "(") {
                pos++
                val v = parseExpression()
                if (pos < tokens.size && tokens[pos] == ")") pos++
                return v
            }
            if (tok == "-") {
                pos++
                return parseFactor()?.let { -it }
            }
            if (tok == "+") {
                pos++
                return parseFactor()
            }
            pos++
            return evaluateLengthPx(tok, fontSizePx, rootFontSizePx, percentBasePx, viewportWidthPx, viewportHeightPx)
                ?: tok.toFloatOrNull()
        }
    }

    private fun parseSingleUnitPx(
        raw: String,
        fontSizePx: Float,
        rootFontSizePx: Float,
        percentBasePx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Float? {
        val lower = raw.trim().lowercase()
        return when {
            lower.endsWith("px") -> lower.dropLast(2).toFloatOrNull()
            lower.endsWith("rem") -> lower.dropLast(3).toFloatOrNull()?.let { it * rootFontSizePx }
            lower.endsWith("em") -> lower.dropLast(2).toFloatOrNull()?.let { it * fontSizePx }
            lower.endsWith("vw") -> lower.dropLast(2).toFloatOrNull()?.let { it * viewportWidthPx / 100f }
            lower.endsWith("vh") -> lower.dropLast(2).toFloatOrNull()?.let { it * viewportHeightPx / 100f }
            lower.endsWith("vmin") -> lower.dropLast(4).toFloatOrNull()?.let { it * min(viewportWidthPx, viewportHeightPx) / 100f }
            lower.endsWith("vmax") -> lower.dropLast(4).toFloatOrNull()?.let { it * max(viewportWidthPx, viewportHeightPx) / 100f }
            lower.endsWith("pt") -> lower.dropLast(2).toFloatOrNull()?.let { it * (96f / 72f) }
            lower.endsWith("%") -> lower.dropLast(1).toFloatOrNull()?.let { it * percentBasePx / 100f }
            else -> lower.toFloatOrNull()
        }
    }

    fun splitTopLevelArgs(input: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var depth = 0
        var quote: Char? = null
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (quote != null) {
                if (c == quote) quote = null
            } else {
                when (c) {
                    '\'', '"' -> quote = c
                    '(' -> depth++
                    ')' -> if (depth > 0) depth--
                    ',' -> if (depth == 0) {
                        out.add(input.substring(start, i).trim())
                        start = i + 1
                    }
                }
            }
            i++
        }
        val tail = input.substring(start).trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }
}
