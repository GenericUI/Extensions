package com.nexustools.genericui.ext.webview.desktop.njs

/**
 * Lexer-aware ES6+ source preprocessor transforming modern JavaScript syntax into NJS-native AST/JIT
 * compatible JavaScript without modifying `lib/NJS`:
 * - Backtick template literals (including arbitrarily nested `${expr}`)
 * - `class Name [extends Base]` declarations & expressions with `constructor`, `super(...)`, getters/setters, and `static` methods
 * - `async function` / `async (...) =>` / `await` via Promise CPS transformation
 * - Default function parameters (`function(a, b = 1)`)
 * - Array & object destructuring with rest elements (`const [a, ...rest] = arr`, `const { x, y = 1 } = obj`)
 */
object NjsEs6Preprocessor {

    private const val MAX_PREPROCESS_DEPTH: Int = 48

    private var tempCounter: Int = 0

    private fun nextTemp(prefix: String = "__njsTmp"): String {
        tempCounter++
        return "$prefix$tempCounter"
    }

    fun preprocess(source: String): String = preprocessInternal(source, 0)

    private fun preprocessInternal(source: String, depth: Int): String {
        if (depth >= MAX_PREPROCESS_DEPTH || !needsTransform(source)) return source
        return transformLexicalPass(source, depth)
    }

    private fun needsTransform(src: String): Boolean =
        src.contains('`') ||
            src.contains("class ") ||
            src.contains("class{") ||
            src.contains("async ") ||
            src.contains("async(") ||
            src.contains("await ") ||
            src.contains("...")

    private fun transformLexicalPass(src: String, depth: Int): String {
        val sb = StringBuilder(src.length + 64)
        var i = 0
        val len = src.length
        var lastSigChar: Char = ';'
        var lastIdent = ""

        while (i < len) {
            val c = src[i]
            // Line comment
            if (c == '/' && i + 1 < len && src[i + 1] == '/') {
                val end = src.indexOf('\n', i + 2).let { if (it < 0) len else it }
                sb.append(src, i, end)
                i = end
                continue
            }
            // Block comment
            if (c == '/' && i + 1 < len && src[i + 1] == '*') {
                val end = src.indexOf("*/", i + 2).let { if (it < 0) len else it + 2 }
                sb.append(src, i, end)
                i = end
                continue
            }
            // Single / Double quoted string
            if (c == '\'' || c == '"') {
                val end = scanQuotedString(src, i, c)
                sb.append(src, i, end)
                i = end
                lastSigChar = c
                lastIdent = ""
                continue
            }
            // Template literal `...${expr}...`
            if (c == '`') {
                val end = scanTemplateLiteralEnd(src, i)
                val inner = src.substring(i + 1, (end - 1).coerceAtLeast(i + 1))
                sb.append(desugarTemplateLiteral(inner, depth))
                i = end
                lastSigChar = '"'
                lastIdent = ""
                continue
            }
            // Regex literal /pattern/flags when `/` starts an expression
            if (c == '/' && canStartRegexLiteral(lastSigChar, lastIdent)) {
                val end = scanRegexLiteralEnd(src, i)
                sb.append(src, i, end)
                i = end
                lastSigChar = '/'
                lastIdent = ""
                continue
            }

            // Check keywords: class, async, function
            if ((c == 'c' || c == 'a' || c == 'f') && (i == 0 || !isIdentPart(src[i - 1]))) {
                // class declaration / expression
                if (src.startsWith("class", i) && i + 5 < len && (src[i + 5].isWhitespace() || src[i + 5] == '{')) {
                    val consumed = tryTransformClass(src, i, depth)
                    if (consumed != null && consumed.second > i) {
                        sb.append(consumed.first)
                        i = consumed.second
                        lastSigChar = ')'
                        lastIdent = ""
                        continue
                    }
                }
                // async function / async arrow
                if (src.startsWith("async", i) && i + 5 < len && (src[i + 5].isWhitespace() || src[i + 5] == '(')) {
                    val consumed = tryTransformAsync(src, i, depth)
                    if (consumed != null && consumed.second > i) {
                        sb.append(consumed.first)
                        i = consumed.second
                        lastSigChar = '}'
                        lastIdent = ""
                        continue
                    }
                }
                // function default parameters e.g. function foo(a, b = 10)
                if (src.startsWith("function", i) && i + 8 < len && (src[i + 8].isWhitespace() || src[i + 8] == '(' || src[i + 8] == '*')) {
                    val consumed = tryTransformFunctionDefaults(src, i, depth)
                    if (consumed != null && consumed.second > i) {
                        sb.append(consumed.first)
                        i = consumed.second
                        lastSigChar = '}'
                        lastIdent = ""
                        continue
                    }
                }
            }

            // Array/Object destructuring declarations: const/let/var [a, ...rest] = expr;
            if ((c == 'c' || c == 'l' || c == 'v') && (i == 0 || !isIdentPart(src[i - 1]))) {
                val kw = when {
                    src.startsWith("const", i) && i + 5 < len && src[i + 5].isWhitespace() -> "const"
                    src.startsWith("let", i) && i + 3 < len && src[i + 3].isWhitespace() -> "let"
                    src.startsWith("var", i) && i + 3 < len && src[i + 3].isWhitespace() -> "var"
                    else -> null
                }
                if (kw != null) {
                    val consumed = tryTransformDestructuringDecl(src, i, kw, depth)
                    if (consumed != null && consumed.second > i) {
                        sb.append(consumed.first)
                        i = consumed.second
                        lastSigChar = ';'
                        lastIdent = ""
                        continue
                    }
                }
            }

            if (isIdentPart(c)) {
                var endId = i + 1
                while (endId < len && isIdentPart(src[endId])) endId++
                sb.append(src, i, endId)
                lastIdent = src.substring(i, endId)
                lastSigChar = src[endId - 1]
                i = endId
                continue
            }

            sb.append(c)
            if (!c.isWhitespace()) {
                lastSigChar = c
                lastIdent = ""
            }
            i++
        }
        return sb.toString()
    }

    private fun desugarTemplateLiteral(inner: String, depth: Int): String {
        val parts = ArrayList<String>()
        val literal = StringBuilder()
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c == '\\' && i + 1 < inner.length) {
                val next = inner[i + 1]
                when (next) {
                    '`' -> literal.append('`')
                    '$' -> literal.append('$')
                    else -> literal.append('\\').append(next)
                }
                i += 2
                continue
            }
            if (c == '$' && i + 1 < inner.length && inner[i + 1] == '{') {
                parts.add(quoteJsDoubleString(literal.toString()))
                literal.setLength(0)
                val exprEnd = findMatchingBraceInJs(inner, i + 1)
                val exprRaw = inner.substring(i + 2, exprEnd)
                parts.add("String(" + preprocessInternal(exprRaw, depth + 1) + ")")
                i = (exprEnd + 1).coerceAtMost(inner.length)
                continue
            }
            when (c) {
                '"' -> literal.append("\\\"")
                '\n' -> literal.append("\\n")
                '\r' -> literal.append("\\r")
                '\t' -> literal.append("\\t")
                '\\' -> literal.append("\\\\")
                else -> literal.append(c)
            }
            i++
        }
        parts.add(quoteJsDoubleString(literal.toString()))
        if (parts.size == 1) return parts[0]
        return "(" + parts.joinToString(" + ") + ")"
    }

    private fun quoteJsDoubleString(alreadyEscapedInner: String): String = "\"$alreadyEscapedInner\""

    private fun tryTransformClass(src: String, startIdx: Int, depth: Int): Pair<String, Int>? {
        var i = startIdx + 5
        i = skipWs(src, i)
        val nameStart = i
        while (i < src.length && isIdentPart(src[i])) i++
        val className = src.substring(nameStart, i)
        i = skipWs(src, i)

        var superName: String? = null
        if (src.startsWith("extends", i) && i + 7 < src.length && src[i + 7].isWhitespace()) {
            i = skipWs(src, i + 7)
            val supStart = i
            while (i < src.length && src[i] != '{') i++
            superName = src.substring(supStart, i).trim()
        }
        i = skipWs(src, i)
        if (i >= src.length || src[i] != '{') return null
        val bodyClose = findMatchingBraceInJs(src, i)
        val classBody = src.substring(i + 1, bodyClose)

        val members = parseClassMembers(classBody)
        val effectiveName = className.ifEmpty { nextTemp("__Class") }
        val superParam = nextTemp("__super")
        val sb = StringBuilder()
        val isStatement = startIdx == 0 || src[startIdx - 1].isWhitespace() || src[startIdx - 1] == ';' || src[startIdx - 1] == '}' || src[startIdx - 1] == '{'
        if (className.isNotEmpty() && isStatement) {
            sb.append("var ").append(className).append(" = ")
        }
        sb.append("(function(").append(superParam).append(") {\n")

        val ctor = members.firstOrNull { !it.isStatic && it.name == "constructor" }
        val ctorParams = ctor?.params.orEmpty()
        val ctorBodyRaw = ctor?.body ?: if (superName != null) "super.apply(this, arguments);" else ""
        val ctorBodyTransformed = replaceSuperCalls(preprocessInternal(ctorBodyRaw, depth + 1), superParam, isConstructor = true)
        sb.append("  function ").append(effectiveName).append("(").append(ctorParams).append(") {\n")
        if (!ctorBodyTransformed.contains("$superParam.call(this") && !ctorBodyTransformed.contains("$superParam.apply(this") && superName != null) {
            sb.append("    ").append(superParam).append(".apply(this, arguments);\n")
        }
        sb.append("    ").append(ctorBodyTransformed).append("\n")
        sb.append("  }\n")
        if (superName != null) {
            sb.append("  ").append(effectiveName).append(".prototype = Object.create(").append(superParam).append(".prototype);\n")
            sb.append("  ").append(effectiveName).append(".prototype.constructor = ").append(effectiveName).append(";\n")
        }
        for (m in members) {
            if (!m.isStatic && m.name == "constructor") continue
            val mBody = replaceSuperCalls(preprocessInternal(m.body, depth + 1), superParam, isConstructor = false)
            val targetObj = if (m.isStatic) effectiveName else "$effectiveName.prototype"
            when (m.kind) {
                "method" -> {
                    if (m.computedKey != null) {
                        sb.append("  ").append(targetObj).append("[").append(preprocessInternal(m.computedKey, depth + 1)).append("] = function(").append(m.params).append(") { ").append(mBody).append(" };\n")
                    } else {
                        sb.append("  ").append(targetObj).append(".").append(m.name).append(" = function(").append(m.params).append(") { ").append(mBody).append(" };\n")
                    }
                }
                "get" -> {
                    sb.append("  Object.defineProperty(").append(targetObj).append(", \"").append(m.name).append("\", { get: function() { ").append(mBody).append(" }, configurable: true });\n")
                }
                "set" -> {
                    sb.append("  Object.defineProperty(").append(targetObj).append(", \"").append(m.name).append("\", { set: function(").append(m.params).append(") { ").append(mBody).append(" }, configurable: true });\n")
                }
            }
        }
        sb.append("  return ").append(effectiveName).append(";\n")
        sb.append("})(").append(superName ?: "null").append(")")
        if (className.isNotEmpty() && isStatement) sb.append(";")
        return sb.toString() to (bodyClose + 1).coerceAtMost(src.length)
    }

    private data class ClassMemberSpec(
        val name: String,
        val computedKey: String?,
        val kind: String,
        val isStatic: Boolean,
        val params: String,
        val body: String
    )

    private fun parseClassMembers(body: String): List<ClassMemberSpec> {
        val list = ArrayList<ClassMemberSpec>()
        var i = 0
        while (i < body.length) {
            i = skipWs(body, i)
            if (i >= body.length) break
            if (body[i] == ';') { i++; continue }
            var isStatic = false
            if (body.startsWith("static", i) && i + 6 < body.length && body[i + 6].isWhitespace()) {
                isStatic = true
                i = skipWs(body, i + 6)
            }
            var kind = "method"
            if (body.startsWith("get", i) && i + 3 < body.length && body[i + 3].isWhitespace()) {
                kind = "get"
                i = skipWs(body, i + 3)
            } else if (body.startsWith("set", i) && i + 3 < body.length && body[i + 3].isWhitespace()) {
                kind = "set"
                i = skipWs(body, i + 3)
            }
            var computedKey: String? = null
            var name = ""
            if (i < body.length && body[i] == '[') {
                val closeB = findMatchingBracketInJs(body, i)
                computedKey = body.substring(i + 1, closeB)
                i = skipWs(body, (closeB + 1).coerceAtMost(body.length))
            } else {
                val nStart = i
                while (i < body.length && isIdentPart(body[i])) i++
                name = body.substring(nStart, i)
                i = skipWs(body, i)
            }
            if (i >= body.length || body[i] != '(') {
                // Class field or stray token: advance cleanly to `;` or newline
                val nextSemi = body.indexOf(';', i).let { if (it < 0) body.length else it + 1 }
                i = nextSemi
                continue
            }
            val closeP = findMatchingParenInJs(body, i)
            val params = body.substring(i + 1, closeP)
            i = skipWs(body, (closeP + 1).coerceAtMost(body.length))
            if (i >= body.length || body[i] != '{') break
            val closeBody = findMatchingBraceInJs(body, i)
            val mBody = body.substring(i + 1, closeBody)
            list.add(ClassMemberSpec(name, computedKey, kind, isStatic, params, mBody))
            i = (closeBody + 1).coerceAtMost(body.length)
        }
        return list
    }

    private fun replaceSuperCalls(body: String, superVar: String, isConstructor: Boolean): String {
        val sb = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\'' || c == '"') {
                val end = scanQuotedString(body, i, c)
                sb.append(body, i, end)
                i = end
                continue
            }
            if (body.startsWith("super", i) && (i == 0 || !isIdentPart(body[i - 1])) &&
                (i + 5 >= body.length || !isIdentPart(body[i + 5]))
            ) {
                var j = skipWs(body, i + 5)
                if (isConstructor && j < body.length && body[j] == '(') {
                    val closeP = findMatchingParenInJs(body, j)
                    val args = body.substring(j + 1, closeP).trim()
                    sb.append(superVar).append(".call(this").append(if (args.isNotEmpty()) ", $args" else "").append(")")
                    i = (closeP + 1).coerceAtMost(body.length)
                    continue
                } else if (j < body.length && body[j] == '.') {
                    j++
                    val mStart = j
                    while (j < body.length && isIdentPart(body[j])) j++
                    val mName = body.substring(mStart, j)
                    j = skipWs(body, j)
                    if (j < body.length && body[j] == '(') {
                        val closeP = findMatchingParenInJs(body, j)
                        val args = body.substring(j + 1, closeP).trim()
                        sb.append(superVar).append(".prototype.").append(mName).append(".call(this")
                            .append(if (args.isNotEmpty()) ", $args" else "").append(")")
                        i = (closeP + 1).coerceAtMost(body.length)
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun tryTransformFunctionDefaults(src: String, startIdx: Int, depth: Int): Pair<String, Int>? {
        var i = startIdx + 8
        i = skipWs(src, i)
        val nStart = i
        while (i < src.length && isIdentPart(src[i])) i++
        val fnName = src.substring(nStart, i)
        i = skipWs(src, i)
        if (i >= src.length || src[i] != '(') return null
        val closeP = findMatchingParenInJs(src, i)
        val rawParams = src.substring(i + 1, closeP)
        if (!rawParams.contains('=') && !rawParams.contains("...")) return null
        val next = skipWs(src, (closeP + 1).coerceAtMost(src.length))
        if (next >= src.length || src[next] != '{') return null
        val bodyClose = findMatchingBraceInJs(src, next)
        val rawBody = src.substring(next + 1, bodyClose)

        val (cleanParams, prologue) = desugarParamsAndDefaults(rawParams, depth)
        val transformed = buildString {
            append("function")
            if (fnName.isNotEmpty()) append(' ').append(fnName)
            append('(').append(cleanParams).append(") {\n")
            if (prologue.isNotEmpty()) append(prologue).append('\n')
            append(preprocessInternal(rawBody, depth + 1)).append('\n')
            append('}')
        }
        return transformed to (bodyClose + 1).coerceAtMost(src.length)
    }

    private fun desugarParamsAndDefaults(rawParams: String, depth: Int): Pair<String, String> {
        if (rawParams.isBlank()) return "" to ""
        val parts = splitTopLevelArgs(rawParams)
        val cleanNames = ArrayList<String>()
        val prologue = StringBuilder()
        parts.forEachIndexed { idx, part ->
            val p = part.trim()
            if (p.startsWith("...")) {
                val restName = p.substring(3).trim()
                prologue.append("  var ").append(restName)
                    .append(" = Array.prototype.slice.call(arguments, ").append(idx).append(");\n")
            } else if ('=' in p) {
                val eq = p.indexOf('=')
                val pName = p.substring(0, eq).trim()
                val defVal = preprocessInternal(p.substring(eq + 1).trim(), depth + 1)
                cleanNames.add(pName)
                prologue.append("  if (").append(pName).append(" === undefined) { ")
                    .append(pName).append(" = ").append(defVal).append("; }\n")
            } else {
                cleanNames.add(p)
            }
        }
        return cleanNames.joinToString(", ") to prologue.toString()
    }

    private fun tryTransformAsync(src: String, startIdx: Int, depth: Int): Pair<String, Int>? {
        var i = skipWs(src, startIdx + 5)
        var fnName = ""
        val rawParams: String
        val bodyText: String
        val endIdx: Int

        if (src.startsWith("function", i) && (i + 8 < src.length && (src[i + 8].isWhitespace() || src[i + 8] == '('))) {
            i = skipWs(src, i + 8)
            val nStart = i
            while (i < src.length && isIdentPart(src[i])) i++
            fnName = src.substring(nStart, i)
            i = skipWs(src, i)
            if (i >= src.length || src[i] != '(') return null
            val closeP = findMatchingParenInJs(src, i)
            rawParams = src.substring(i + 1, closeP)
            val bodyStart = skipWs(src, (closeP + 1).coerceAtMost(src.length))
            if (bodyStart >= src.length || src[bodyStart] != '{') return null
            val bodyClose = findMatchingBraceInJs(src, bodyStart)
            bodyText = src.substring(bodyStart + 1, bodyClose)
            endIdx = (bodyClose + 1).coerceAtMost(src.length)
        } else if (i < src.length && src[i] == '(') {
            val closeP = findMatchingParenInJs(src, i)
            rawParams = src.substring(i + 1, closeP)
            var j = skipWs(src, (closeP + 1).coerceAtMost(src.length))
            if (!src.startsWith("=>", j)) return null
            j = skipWs(src, j + 2)
            if (j < src.length && src[j] == '{') {
                val bodyClose = findMatchingBraceInJs(src, j)
                bodyText = src.substring(j + 1, bodyClose)
                endIdx = (bodyClose + 1).coerceAtMost(src.length)
            } else {
                val exprEnd = findArrowExprEnd(src, j)
                bodyText = "return " + src.substring(j, exprEnd).trim() + ";"
                endIdx = exprEnd
            }
        } else if (i < src.length && isIdentPart(src[i])) {
            var pEnd = i
            while (pEnd < src.length && isIdentPart(src[pEnd])) pEnd++
            rawParams = src.substring(i, pEnd)
            var j = skipWs(src, pEnd)
            if (!src.startsWith("=>", j)) return null
            j = skipWs(src, j + 2)
            if (j < src.length && src[j] == '{') {
                val bodyClose = findMatchingBraceInJs(src, j)
                bodyText = src.substring(j + 1, bodyClose)
                endIdx = (bodyClose + 1).coerceAtMost(src.length)
            } else {
                val exprEnd = findArrowExprEnd(src, j)
                bodyText = "return " + src.substring(j, exprEnd).trim() + ";"
                endIdx = exprEnd
            }
        } else {
            return null
        }

        val (cleanParams, prologue) = desugarParamsAndDefaults(rawParams, depth)
        val cpsBody = desugarAsyncBodyToPromiseChain(prologue + preprocessInternal(bodyText, depth + 1), depth + 1)
        val out = buildString {
            append("function")
            if (fnName.isNotEmpty()) append(' ').append(fnName)
            append('(').append(cleanParams).append(") {\n")
            append("  return (function() {\n")
            append(cpsBody).append('\n')
            append("  }).call(this);\n")
            append('}')
        }
        return out to endIdx
    }

    private fun desugarAsyncBodyToPromiseChain(body: String, depth: Int = 0): String {
        val stmts = splitTopLevelStatements(body)
        return compileStatementsCps(stmts, 0, depth)
    }

    private fun compileStatementsCps(stmts: List<String>, index: Int, depth: Int = 0): String {
        if (index >= stmts.size) {
            return "return Promise.resolve(undefined);"
        }
        if (depth >= MAX_PREPROCESS_DEPTH) {
            val remaining = stmts.subList(index, stmts.size).joinToString(" ").trim()
            return if (remaining.isEmpty()) "return Promise.resolve(undefined);"
            else "try { $remaining; return Promise.resolve(undefined); } catch (__syncErr) { return Promise.reject(__syncErr); }"
        }
        val rawStmt = stmts[index].trim()
        if (rawStmt.isEmpty()) return compileStatementsCps(stmts, index + 1, depth + 1)

        // Extract try { ... } catch (err) { ... } if it contains `await`
        if (rawStmt.startsWith("try") && rawStmt.length > 3 && (rawStmt[3].isWhitespace() || rawStmt[3] == '{') && containsAwaitKeyword(rawStmt)) {
            val parsedTry = parseTryCatchFinally(rawStmt)
            if (parsedTry != null) {
                val (tryBody, catchVar, catchBody, finallyBody) = parsedTry
                val tryPromise = desugarAsyncBodyToPromiseChain(tryBody, depth + 1)
                val catchCps = if (catchBody != null) {
                    val v = catchVar?.takeIf { it.isNotEmpty() } ?: nextTemp("__err")
                    "function($v) { try { ${desugarAsyncBodyToPromiseChain(catchBody, depth + 1)} } catch (__ce) { return Promise.reject(__ce); } }"
                } else {
                    "undefined"
                }
                val afterFinally = compileStatementsCps(stmts, index + 1, depth + 1)
                val baseChain = "(function() { try { $tryPromise } catch (__te) { return Promise.reject(__te); } })().then(undefined, $catchCps)"
                val withFinally = if (finallyBody != null) {
                    "$baseChain.then(function(__v) { ${preprocessInternal(finallyBody, depth + 1)} return __v; }, function(__e) { ${preprocessInternal(finallyBody, depth + 1)} return Promise.reject(__e); })"
                } else {
                    baseChain
                }
                return "return $withFinally.then(function() { $afterFinally });"
            }
        }

        // Direct `return await expr`
        if (rawStmt.startsWith("return") && (rawStmt.length == 6 || rawStmt[6].isWhitespace())) {
            val expr = rawStmt.substring(6).trim().removeSuffix(";").trim()
            if (expr.startsWith("await") && expr.length > 5 && expr[5].isWhitespace()) {
                val inner = expr.substring(5).trim()
                return "return Promise.resolve($inner);"
            }
            if (!containsAwaitKeyword(expr)) {
                return "return Promise.resolve(${expr.ifEmpty { "undefined" }});"
            }
        }

        // Variable declaration with `await`: `const/let/var x = await expr;`
        val declAwait = Regex("^(var|let|const)\\s+([a-zA-Z_\$][a-zA-Z0-9_\$]*)\\s*=\\s*await\\s+(.+)$", RegexOption.DOT_MATCHES_ALL)
            .find(rawStmt.removeSuffix(";").trim())
        if (declAwait != null) {
            val varName = declAwait.groupValues[2]
            val rhsExpr = declAwait.groupValues[3].trim()
            val rest = compileStatementsCps(stmts, index + 1, depth + 1)
            return "return Promise.resolve($rhsExpr).then(function($varName) { $rest });"
        }

        // Assignment with `await`: `lhs = await expr;`
        val assignAwait = Regex("^([a-zA-Z_\$][a-zA-Z0-9_.\$\\[\\]]*)\\s*=\\s*await\\s+(.+)$", RegexOption.DOT_MATCHES_ALL)
            .find(rawStmt.removeSuffix(";").trim())
        if (assignAwait != null) {
            val lhs = assignAwait.groupValues[1]
            val rhsExpr = assignAwait.groupValues[2].trim()
            val tmp = nextTemp("__aw")
            val rest = compileStatementsCps(stmts, index + 1, depth + 1)
            return "return Promise.resolve($rhsExpr).then(function($tmp) { $lhs = $tmp; $rest });"
        }

        // Bare `await expr;`
        val cleanSemi = rawStmt.removeSuffix(";").trim()
        if (cleanSemi.startsWith("await") && cleanSemi.length > 5 && cleanSemi[5].isWhitespace()) {
            val inner = cleanSemi.substring(5).trim()
            val rest = compileStatementsCps(stmts, index + 1, depth + 1)
            return "return Promise.resolve($inner).then(function() { $rest });"
        }

        // Embedded await inside statement expression: extract first `await <expr>` cleanly
        if (containsAwaitKeyword(rawStmt)) {
            val extracted = extractFirstInlineAwait(rawStmt)
            if (extracted != null) {
                val (awaitedExpr, replacedStmt) = extracted
                if (awaitedExpr.isNotEmpty() && replacedStmt != rawStmt) {
                    val tmp = nextTemp("__aw")
                    val expandedStmts = ArrayList<String>()
                    expandedStmts.add("var $tmp = await $awaitedExpr;")
                    expandedStmts.add(replacedStmt.replace("__AWAIT_PLACEHOLDER__", tmp))
                    for (k in index + 1 until stmts.size) expandedStmts.add(stmts[k])
                    return compileStatementsCps(expandedStmts, 0, depth + 1)
                }
            }
        }

        // Normal synchronous statements grouped until next await-containing statement (always advance past index)
        val syncSb = StringBuilder()
        var j = index
        while (j < stmts.size) {
            val s = stmts[j].trim()
            if (s.isEmpty()) { j++; continue }
            if (j > index && (containsAwaitKeyword(s) || (s.startsWith("return") && (s.length == 6 || s[6].isWhitespace())))) {
                break
            }
            syncSb.append(s)
            if (!s.endsWith(";") && !s.endsWith("}")) syncSb.append(';')
            syncSb.append(' ')
            j++
        }
        val nextCps = compileStatementsCps(stmts, j.coerceAtLeast(index + 1), depth + 1)
        return if (syncSb.isEmpty()) {
            nextCps
        } else {
            "try { $syncSb $nextCps } catch (__syncErr) { return Promise.reject(__syncErr); }"
        }
    }

    private fun tryTransformDestructuringDecl(src: String, startIdx: Int, kw: String, depth: Int): Pair<String, Int>? {
        var i = skipWs(src, startIdx + kw.length)
        if (i >= src.length || (src[i] != '[' && src[i] != '{')) return null
        val isArray = src[i] == '['
        val closeIdx = if (isArray) findMatchingBracketInJs(src, i) else findMatchingBraceInJs(src, i)
        val pattern = src.substring(i + 1, closeIdx)
        var j = skipWs(src, (closeIdx + 1).coerceAtMost(src.length))
        if (j >= src.length || src[j] != '=') return null
        j = skipWs(src, j + 1)
        val exprEnd = findStatementExprEnd(src, j)
        val rhsRaw = preprocessInternal(src.substring(j, exprEnd).trim(), depth + 1)
        val tmp = nextTemp("__destr")
        val sb = StringBuilder()
        if (isArray) {
            sb.append("var ").append(tmp).append(" = Array.from(").append(rhsRaw).append("); ")
            val items = splitTopLevelArgs(pattern)
            items.forEachIndexed { idx, rawItem ->
                val item = rawItem.trim()
                if (item.startsWith("...")) {
                    val restName = item.substring(3).trim()
                    sb.append("var ").append(restName).append(" = ").append(tmp).append(".slice(").append(idx).append("); ")
                } else if (item.isNotEmpty()) {
                    val eqIdx = item.indexOf('=')
                    if (eqIdx > 0) {
                        val vName = item.substring(0, eqIdx).trim()
                        val defVal = preprocessInternal(item.substring(eqIdx + 1).trim(), depth + 1)
                        sb.append("var ").append(vName).append(" = (").append(tmp).append("[").append(idx)
                            .append("] !== undefined ? ").append(tmp).append("[").append(idx).append("] : ").append(defVal).append("); ")
                    } else {
                        sb.append("var ").append(item).append(" = ").append(tmp).append("[").append(idx).append("]; ")
                    }
                }
            }
        } else {
            sb.append("var ").append(tmp).append(" = (").append(rhsRaw).append(") || {}; ")
            val items = splitTopLevelArgs(pattern)
            for (rawItem in items) {
                val item = rawItem.trim()
                if (item.isEmpty()) continue
                val eqIdx = findTopLevelChar(item, '=')
                val beforeDefault = (if (eqIdx >= 0) item.substring(0, eqIdx) else item).trim()
                val defVal = if (eqIdx >= 0) preprocessInternal(item.substring(eqIdx + 1).trim(), depth + 1) else null
                val colonIdx = beforeDefault.indexOf(':')
                val propKey = (if (colonIdx >= 0) beforeDefault.substring(0, colonIdx) else beforeDefault).trim()
                val varName = (if (colonIdx >= 0) beforeDefault.substring(colonIdx + 1) else propKey).trim()
                if (defVal != null) {
                    sb.append("var ").append(varName).append(" = (").append(tmp).append(".").append(propKey)
                        .append(" !== undefined ? ").append(tmp).append(".").append(propKey).append(" : ").append(defVal).append("); ")
                } else {
                    sb.append("var ").append(varName).append(" = ").append(tmp).append(".").append(propKey).append("; ")
                }
            }
        }
        val consumedEnd = (if (exprEnd < src.length && src[exprEnd] == ';') exprEnd + 1 else exprEnd).coerceAtMost(src.length)
        return sb.toString() to consumedEnd
    }

    private fun parseTryCatchFinally(rawStmt: String): Tuple4<String, String?, String?, String?>? {
        var i = skipWs(rawStmt, 3)
        if (i >= rawStmt.length || rawStmt[i] != '{') return null
        val tryClose = findMatchingBraceInJs(rawStmt, i)
        val tryBody = rawStmt.substring(i + 1, tryClose)
        i = skipWs(rawStmt, tryClose + 1)
        var catchVar: String? = null
        var catchBody: String? = null
        var finallyBody: String? = null
        if (rawStmt.startsWith("catch", i)) {
            i = skipWs(rawStmt, i + 5)
            if (i < rawStmt.length && rawStmt[i] == '(') {
                val closeP = findMatchingParenInJs(rawStmt, i)
                catchVar = rawStmt.substring(i + 1, closeP).trim()
                i = skipWs(rawStmt, closeP + 1)
            }
            if (i < rawStmt.length && rawStmt[i] == '{') {
                val cClose = findMatchingBraceInJs(rawStmt, i)
                catchBody = rawStmt.substring(i + 1, cClose)
                i = skipWs(rawStmt, cClose + 1)
            }
        }
        if (rawStmt.startsWith("finally", i)) {
            i = skipWs(rawStmt, i + 7)
            if (i < rawStmt.length && rawStmt[i] == '{') {
                val fClose = findMatchingBraceInJs(rawStmt, i)
                finallyBody = rawStmt.substring(i + 1, fClose)
            }
        }
        return Tuple4(tryBody, catchVar, catchBody, finallyBody)
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun containsAwaitKeyword(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\'' || c == '"') {
                i = scanQuotedString(text, i, c)
                continue
            }
            if (text.startsWith("await", i) &&
                (i == 0 || !isIdentPart(text[i - 1])) &&
                i + 5 < text.length && text[i + 5].isWhitespace()
            ) {
                return true
            }
            i++
        }
        return false
    }

    private fun extractFirstInlineAwait(stmt: String): Pair<String, String>? {
        var i = 0
        while (i < stmt.length) {
            val c = stmt[i]
            if (c == '\'' || c == '"') {
                i = scanQuotedString(stmt, i, c)
                continue
            }
            if (stmt.startsWith("await", i) &&
                (i == 0 || !isIdentPart(stmt[i - 1])) &&
                i + 5 < stmt.length && stmt[i + 5].isWhitespace()
            ) {
                val exprStart = skipWs(stmt, i + 5)
                var j = exprStart
                var paren = 0
                var bracket = 0
                var brace = 0
                while (j < stmt.length) {
                    val ch = stmt[j]
                    if (ch == '\'' || ch == '"') {
                        j = scanQuotedString(stmt, j, ch)
                        continue
                    }
                    when (ch) {
                        '(' -> paren++
                        ')' -> { if (paren == 0) break; paren-- }
                        '[' -> bracket++
                        ']' -> { if (bracket == 0) break; bracket-- }
                        '{' -> brace++
                        '}' -> { if (brace == 0) break; brace-- }
                        ',', ';' -> if (paren == 0 && bracket == 0 && brace == 0) break
                    }
                    j++
                }
                val awaitedExpr = stmt.substring(exprStart, j).trim()
                val replaced = stmt.substring(0, i) + "__AWAIT_PLACEHOLDER__" + stmt.substring(j)
                return awaitedExpr to replaced
            }
            i++
        }
        return null
    }

    private fun splitTopLevelStatements(body: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var i = 0
        var paren = 0
        var bracket = 0
        var brace = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '/' && i + 1 < body.length && body[i + 1] == '/') {
                val end = body.indexOf('\n', i + 2).let { if (it < 0) body.length else it }
                i = end
                continue
            }
            if (c == '/' && i + 1 < body.length && body[i + 1] == '*') {
                val end = body.indexOf("*/", i + 2).let { if (it < 0) body.length else it + 2 }
                i = end
                continue
            }
            if (c == '\'' || c == '"') {
                i = scanQuotedString(body, i, c)
                continue
            }
            when (c) {
                '(' -> paren++
                ')' -> if (paren > 0) paren--
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
                '{' -> brace++
                '}' -> {
                    if (brace > 0) brace--
                    if (brace == 0 && paren == 0 && bracket == 0) {
                        val after = skipWs(body, i + 1)
                        val isContinuation = body.startsWith("catch", after) ||
                            body.startsWith("finally", after) ||
                            body.startsWith("else", after) ||
                            (after < body.length && (body[after] == ';' || body[after] == ')' || body[after] == ',' || body[after] == '.'))
                        if (!isContinuation) {
                            val stmt = body.substring(start, i + 1).trim()
                            if (stmt.isNotEmpty()) out.add(stmt)
                            start = i + 1
                        }
                    }
                }
                ';' -> if (brace == 0 && paren == 0 && bracket == 0) {
                    val stmt = body.substring(start, i + 1).trim()
                    if (stmt.isNotEmpty()) out.add(stmt)
                    start = i + 1
                }
            }
            i++
        }
        val tail = body.substring(start).trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    private fun findStatementExprEnd(src: String, start: Int): Int {
        var i = start
        var paren = 0
        var bracket = 0
        var brace = 0
        while (i < src.length) {
            val c = src[i]
            if (c == '\'' || c == '"') { i = scanQuotedString(src, i, c); continue }
            if (c == '`') { i = scanTemplateLiteralEnd(src, i); continue }
            when (c) {
                '(' -> paren++
                ')' -> if (paren > 0) paren--
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
                '{' -> brace++
                '}' -> {
                    if (brace == 0 && paren == 0 && bracket == 0) return i
                    if (brace > 0) brace--
                }
                ';', '\n' -> if (paren == 0 && bracket == 0 && brace == 0) return i
            }
            i++
        }
        return src.length
    }

    private fun findArrowExprEnd(src: String, start: Int): Int {
        var i = start
        var paren = 0
        var bracket = 0
        var brace = 0
        while (i < src.length) {
            val c = src[i]
            if (c == '\'' || c == '"') { i = scanQuotedString(src, i, c); continue }
            when (c) {
                '(' -> paren++
                ')' -> if (paren == 0) return i else paren--
                '[' -> bracket++
                ']' -> if (bracket == 0) return i else bracket--
                '{' -> brace++
                '}' -> if (brace == 0) return i else brace--
                ',', ';' -> if (paren == 0 && bracket == 0 && brace == 0) return i
            }
            i++
        }
        return src.length
    }

    private fun scanQuotedString(src: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < src.length) {
            val c = src[i]
            if (c == '\\') { i += 2; continue }
            if (c == quote) return i + 1
            i++
        }
        return src.length
    }

    private fun scanTemplateLiteralEnd(src: String, start: Int): Int {
        var i = start + 1
        while (i < src.length) {
            val c = src[i]
            if (c == '\\') { i += 2; continue }
            if (c == '$' && i + 1 < src.length && src[i + 1] == '{') {
                val close = findMatchingBraceInJs(src, i + 1)
                i = (close + 1).coerceAtMost(src.length)
                continue
            }
            if (c == '`') return i + 1
            i++
        }
        return src.length
    }

    private fun scanRegexLiteralEnd(src: String, start: Int): Int {
        var i = start + 1
        var inCharClass = false
        while (i < src.length) {
            val c = src[i]
            if (c == '\\') { i += 2; continue }
            if (c == '[') inCharClass = true
            else if (c == ']' && inCharClass) inCharClass = false
            else if (c == '/' && !inCharClass) {
                i++
                while (i < src.length && src[i].isLetter()) i++
                return i
            }
            if (c == '\n') return start + 1
            i++
        }
        return src.length
    }

    private fun canStartRegexLiteral(prevSigChar: Char, prevIdent: String): Boolean {
        if (prevIdent in setOf("return", "typeof", "throw", "case", "in", "instanceof", "delete", "void", "else")) {
            return true
        }
        return prevSigChar in charArrayOf('(', '[', '{', ',', ';', '=', ':', '?', '&', '|', '!', '~', '+', '-', '*', '/', '%', '<', '>', '^')
    }

    private fun findMatchingBraceInJs(src: String, openIdx: Int): Int =
        findMatchingPairInJs(src, openIdx, '{', '}')

    private fun findMatchingParenInJs(src: String, openIdx: Int): Int =
        findMatchingPairInJs(src, openIdx, '(', ')')

    private fun findMatchingBracketInJs(src: String, openIdx: Int): Int =
        findMatchingPairInJs(src, openIdx, '[', ']')

    private fun findMatchingPairInJs(src: String, openIdx: Int, open: Char, close: Char): Int {
        var depth = 0
        var i = openIdx
        while (i < src.length) {
            val c = src[i]
            if (c == '\'' || c == '"') { i = scanQuotedString(src, i, c); continue }
            if (c == '`') { i = scanTemplateLiteralEnd(src, i); continue }
            if (c == '/' && i + 1 < src.length && src[i + 1] == '/') {
                i = src.indexOf('\n', i + 2).let { if (it < 0) src.length else it }
                continue
            }
            if (c == '/' && i + 1 < src.length && src[i + 1] == '*') {
                i = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                continue
            }
            if (c == open) depth++
            else if (c == close) {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return src.length
    }

    private fun splitTopLevelArgs(raw: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var i = 0
        var paren = 0
        var bracket = 0
        var brace = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\'' || c == '"') { i = scanQuotedString(raw, i, c); continue }
            when (c) {
                '(' -> paren++
                ')' -> if (paren > 0) paren--
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
                '{' -> brace++
                '}' -> if (brace > 0) brace--
                ',' -> if (paren == 0 && bracket == 0 && brace == 0) {
                    out.add(raw.substring(start, i))
                    start = i + 1
                }
            }
            i++
        }
        out.add(raw.substring(start))
        return out
    }

    private fun findTopLevelChar(raw: String, ch: Char): Int {
        var i = 0
        var paren = 0
        var bracket = 0
        var brace = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\'' || c == '"') { i = scanQuotedString(raw, i, c); continue }
            if (c == ch && paren == 0 && bracket == 0 && brace == 0) return i
            when (c) {
                '(' -> paren++
                ')' -> if (paren > 0) paren--
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
                '{' -> brace++
                '}' -> if (brace > 0) brace--
            }
            i++
        }
        return -1
    }

    private fun skipWs(src: String, start: Int): Int {
        var i = start
        while (i < src.length && src[i].isWhitespace()) i++
        return i
    }

    private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'
}
