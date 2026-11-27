package com.nexustools.genericui.ext.webview.desktop.engine

import com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl
import com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl
import com.nexustools.genericui.net.http.HttpMethod
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.res.DataUrlCodec
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * Resolves and fetches URLs (`data:`, `blob:`, `file://`, `http://`, `https://`, and relative
 * asset paths resolved against `baseUrl`) using [FileSystemNativeImpl] and [HttpClientNativeImpl]
 * with explicit timeouts, subresource deduplication/budgets, and `allowFileAccess` / origin policy enforcement.
 */
class WebResourceLoader(
    private val fileSystem: FileSystemNativeImpl,
    private val httpClient: HttpClientNativeImpl,
    var allowFileAccess: Boolean = false
) {
    companion object {
        const val DEFAULT_MAIN_TIMEOUT_MS: Long = 15_000L
        const val DEFAULT_SUBRESOURCE_TIMEOUT_MS: Long = 4_000L
        const val DEFAULT_PAGE_SUBRESOURCE_BUDGET_MS: Long = 10_000L
    }

    private val blobRegistry = LinkedHashMap<String, Pair<String, ByteArray>>()
    private val subresourceCache = LinkedHashMap<String, WebResourceResult>()
    private var nextBlobId: Long = 1L
    private var subresourceBudgetStart: TimeSource.Monotonic.ValueTimeMark? = null
    private var subresourceBudgetMs: Long = DEFAULT_PAGE_SUBRESOURCE_BUDGET_MS
    private var subresourceSessionActive: Boolean = false

    var isAbortedProvider: (() -> Boolean)? = null

    fun beginSubresourceSession(budgetMs: Long = DEFAULT_PAGE_SUBRESOURCE_BUDGET_MS) {
        subresourceCache.clear()
        subresourceBudgetMs = budgetMs.coerceAtLeast(1L)
        subresourceBudgetStart = TimeSource.Monotonic.markNow()
        subresourceSessionActive = true
    }

    fun endSubresourceSession() {
        subresourceSessionActive = false
        subresourceBudgetStart = null
        subresourceCache.clear()
    }

    fun registerBlob(mimeType: String, bytes: ByteArray): String {
        val id = nextBlobId++
        val url = "blob:null/$id"
        blobRegistry[url] = (mimeType.ifBlank { "application/octet-stream" } to bytes.copyOf())
        return url
    }

    fun revokeBlob(url: String) {
        blobRegistry.remove(url)
    }

    fun resolveUrl(rawUrl: String, baseUrl: String?): String {
        val target = rawUrl.trim()
        if (target.isEmpty()) return baseUrl ?: "about:blank"
        if (target.startsWith("data:", ignoreCase = true) ||
            target.startsWith("blob:", ignoreCase = true) ||
            target.startsWith("http://", ignoreCase = true) ||
            target.startsWith("https://", ignoreCase = true) ||
            target.startsWith("file://", ignoreCase = true) ||
            target.startsWith("about:", ignoreCase = true)
        ) {
            return target
        }
        if (hasExplicitNonFileScheme(target)) {
            return target
        }

        val cleanBase = baseUrl?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("about:", ignoreCase = true) }
        if (cleanBase != null) {
            if (cleanBase.startsWith("http://", ignoreCase = true) ||
                cleanBase.startsWith("https://", ignoreCase = true) ||
                cleanBase.startsWith("file://", ignoreCase = true)
            ) {
                return resolveRelativeUri(target, cleanBase)
            }
            // Local filesystem directory or file path base
            val normalizedBase = cleanBase.replace('\\', '/')
            val baseDir = if (normalizedBase.endsWith("/")) normalizedBase.dropLast(1)
            else if (normalizedBase.substringAfterLast('/').contains('.')) normalizedBase.substringBeforeLast('/', "")
            else normalizedBase
            val cleanTarget = target.replace('\\', '/').trimStart('/')
            return if (baseDir.isEmpty()) cleanTarget else normalizePosixSegments("$baseDir/$cleanTarget")
        }
        return target
    }

    /**
     * Fetches a page subresource (`<script src>`, `<link rel="stylesheet">`, `@import`, `<img>`,
     * `<track>`, `<iframe>`) with URL deduplication, per-resource timeout, total page network budget,
     * cooperative abort checks, and safe error results without throwing exceptions.
     */
    fun loadSubresourceSync(
        rawUrl: String,
        baseUrl: String? = null,
        timeoutMs: Long = DEFAULT_SUBRESOURCE_TIMEOUT_MS
    ): WebResourceResult {
        val resolved = resolveUrl(rawUrl, baseUrl)
        if (isAbortedProvider?.invoke() == true) {
            return WebResourceResult(resolved, 499, "Loading Aborted", "text/plain")
        }
        if (subresourceSessionActive) {
            subresourceCache[resolved]?.let { return it }
        }
        val isNetwork = resolved.startsWith("http://", ignoreCase = true) ||
            resolved.startsWith("https://", ignoreCase = true)
        val effectiveTimeout = if (isNetwork && subresourceSessionActive) {
            val elapsed = subresourceBudgetStart?.elapsedNow()?.inWholeMilliseconds ?: 0L
            val remaining = subresourceBudgetMs - elapsed
            if (remaining <= 0L) {
                val exhausted = WebResourceResult(
                    url = resolved,
                    status = 408,
                    statusText = "Page Subresource Budget Exhausted",
                    mimeType = "text/plain"
                )
                subresourceCache[resolved] = exhausted
                return exhausted
            }
            minOf(timeoutMs.coerceAtLeast(1L), remaining).coerceAtLeast(1L)
        } else {
            timeoutMs.coerceAtLeast(1L)
        }

        val result = try {
            loadSync(
                rawUrl = resolved,
                baseUrl = baseUrl,
                timeoutMs = effectiveTimeout
            )
        } catch (t: Throwable) {
            WebResourceResult(
                url = resolved,
                status = 504,
                statusText = t.message ?: t::class.simpleName ?: "Subresource Fetch Error",
                mimeType = "text/plain"
            )
        }
        if (subresourceSessionActive) {
            subresourceCache[resolved] = result
        }
        return result
    }

    fun loadSync(
        rawUrl: String,
        baseUrl: String? = null,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        bodyBytes: ByteArray? = null,
        timeoutMs: Long = DEFAULT_MAIN_TIMEOUT_MS
    ): WebResourceResult {
        val resolved = resolveUrl(rawUrl, baseUrl)

        if (isAbortedProvider?.invoke() == true) {
            return WebResourceResult(resolved, 499, "Loading Aborted", "text/plain")
        }

        if (DataUrlCodec.isDataUrl(resolved)) {
            val bytes = DataUrlCodec.decodeBytes(resolved)
                ?: return WebResourceResult(resolved, 400, "Bad Data URL", "text/plain")
            val mime = DataUrlCodec.extractMimeType(resolved) ?: "text/plain"
            return WebResourceResult(
                url = resolved,
                status = 200,
                statusText = "OK",
                mimeType = mime,
                headers = mapOf("content-type" to mime),
                bytes = bytes
            )
        }

        if (resolved.startsWith("blob:", ignoreCase = true)) {
            val entry = blobRegistry[resolved]
                ?: return WebResourceResult(resolved, 404, "Not Found", "text/plain")
            return WebResourceResult(
                url = resolved,
                status = 200,
                statusText = "OK",
                mimeType = entry.first,
                headers = mapOf("content-type" to entry.first),
                bytes = entry.second.copyOf()
            )
        }

        if (resolved.startsWith("about:", ignoreCase = true)) {
            return WebResourceResult(resolved, 200, "OK", "text/html", bytes = ByteArray(0))
        }

        if (hasExplicitNonFileScheme(resolved) &&
            !resolved.startsWith("http://", ignoreCase = true) &&
            !resolved.startsWith("https://", ignoreCase = true) &&
            !resolved.startsWith("file://", ignoreCase = true)
        ) {
            return WebResourceResult(resolved, 400, "Unsupported URI Scheme", "text/plain")
        }

        if (resolved.startsWith("http://", ignoreCase = true) || resolved.startsWith("https://", ignoreCase = true)) {
            return runBlocking {
                val httpMethod = when (method.trim().uppercase()) {
                    "POST" -> HttpMethod.POST
                    "PUT" -> HttpMethod.PUT
                    "PATCH" -> HttpMethod.PATCH
                    "DELETE" -> HttpMethod.DELETE
                    "HEAD" -> HttpMethod.HEAD
                    "OPTIONS" -> HttpMethod.OPTIONS
                    else -> HttpMethod.GET
                }
                val req = HttpRequest(
                    url = resolved,
                    method = httpMethod,
                    headers = headers,
                    bodyBytes = bodyBytes,
                    timeoutMs = timeoutMs
                )
                val resp = try {
                    withTimeoutOrNull(timeoutMs) {
                        httpClient.execute(req)
                    }
                } catch (t: Throwable) {
                    throw IllegalStateException("HTTP request failed for $resolved: ${t.message ?: t::class.simpleName}", t)
                } ?: throw IllegalStateException("HTTP request timed out after ${timeoutMs}ms: $resolved")
                val ct = resp.header("content-type") ?: DataUrlCodec.guessMimeType(resolved)
                WebResourceResult(
                    url = resp.url.ifBlank { resolved },
                    status = resp.statusCode,
                    statusText = resp.statusText.ifBlank { if (resp.isSuccessful) "OK" else "HTTP ${resp.statusCode}" },
                    mimeType = ct,
                    headers = resp.headers,
                    bytes = resp.bodyBytes
                )
            }
        }

        // Local file resolution (file:// URI or relative/local asset path)
        val localPath = fileUrlToLocalPath(resolved)
        val sameBaseLocal = baseUrl != null && !baseUrl.startsWith("http://", ignoreCase = true) && !baseUrl.startsWith("https://", ignoreCase = true)
        if (!allowFileAccess && !sameBaseLocal) {
            return WebResourceResult(resolved, 403, "File Access Disallowed", "text/plain")
        }

        val bytes = runBlocking {
            withTimeoutOrNull(timeoutMs) {
                if (fileSystem.exists(localPath)) fileSystem.readFile(localPath) else null
            }
        } ?: return WebResourceResult(resolved, 404, "Not Found", "text/plain")

        val mime = DataUrlCodec.guessMimeType(localPath)
        return WebResourceResult(
            url = resolved,
            status = 200,
            statusText = "OK",
            mimeType = mime,
            headers = mapOf("content-type" to mime),
            bytes = bytes
        )
    }

    fun resolveLocalFilePathIfAccessible(rawUrl: String, baseUrl: String?): String? {
        val resolved = resolveUrl(rawUrl, baseUrl)
        if (DataUrlCodec.isDataUrl(resolved) ||
            resolved.startsWith("http://", ignoreCase = true) ||
            resolved.startsWith("https://", ignoreCase = true) ||
            resolved.startsWith("blob:", ignoreCase = true)
        ) {
            return null
        }
        val path = fileUrlToLocalPath(resolved)
        val exists = runBlocking {
            withTimeoutOrNull(3_000L) { fileSystem.exists(path) } ?: false
        }
        return if (exists) path else null
    }

    private fun fileUrlToLocalPath(urlOrPath: String): String {
        if (!urlOrPath.startsWith("file://", ignoreCase = true)) {
            return urlOrPath.substringBefore('?').substringBefore('#')
        }
        var rest = urlOrPath.substring(7).substringBefore('?').substringBefore('#')
        if (rest.startsWith("localhost/", ignoreCase = true)) {
            rest = rest.substring("localhost".length)
        }
        // Handle Windows file:///C:/... -> C:/...
        if (rest.length >= 3 && rest[0] == '/' && rest[2] == ':') {
            rest = rest.substring(1)
        }
        return decodePercentSimple(rest)
    }

    private fun resolveRelativeUri(target: String, base: String): String {
        val schemeEnd = base.indexOf("://")
        val scheme = base.substring(0, schemeEnd)
        val afterScheme = base.substring(schemeEnd + 3)
        val slashIdx = afterScheme.indexOf('/')
        val queryIdx = afterScheme.indexOf('?')
        val fragIdx = afterScheme.indexOf('#')
        val authorityEnd = listOf(slashIdx, queryIdx, fragIdx).filter { it >= 0 }.minOrNull() ?: afterScheme.length
        val authority = afterScheme.substring(0, authorityEnd)
        val basePath = if (slashIdx in 0 until authorityEnd + 1) {
            afterScheme.substring(slashIdx).substringBefore('?').substringBefore('#')
        } else {
            "/"
        }

        if (target.startsWith("//")) return "$scheme:$target"
        if (target.startsWith("/")) return "$scheme://$authority${normalizePosixSegments(target)}"
        if (target.startsWith("?") || target.startsWith("#")) {
            return "${base.substringBefore('#').substringBefore('?')}$target"
        }

        val dir = basePath.substringBeforeLast('/', "")
        val combined = if (dir.isEmpty()) "/$target" else "$dir/$target"
        return "$scheme://$authority${normalizePosixSegments(combined)}"
    }

    private fun hasExplicitNonFileScheme(uri: String): Boolean {
        val colon = uri.indexOf(':')
        if (colon <= 0) return false
        // Ignore Windows drive letters like C:\foo or C:/foo
        if (colon == 1 && uri[0].isLetter()) return false
        val first = uri[0]
        if (!first.isLetter()) return false
        for (i in 1 until colon) {
            val ch = uri[i]
            if (!ch.isLetterOrDigit() && ch != '+' && ch != '-' && ch != '.') {
                return false
            }
        }
        return true
    }

    private fun normalizePosixSegments(rawPath: String): String {
        val qIdx = rawPath.indexOf('?')
        val query = if (qIdx >= 0) rawPath.substring(qIdx) else ""
        val clean = if (qIdx >= 0) rawPath.substring(0, qIdx) else rawPath
        val leadingSlash = clean.startsWith('/')
        val drivePrefix = if (!leadingSlash && clean.length >= 2 && clean[1] == ':') clean.substring(0, 2) else ""
        val pathBody = if (drivePrefix.isNotEmpty()) clean.substring(2) else clean

        val stack = ArrayList<String>()
        for (seg in pathBody.split('/')) {
            when {
                seg.isEmpty() || seg == "." -> Unit
                seg == ".." -> if (stack.isNotEmpty() && stack.last() != "..") stack.removeAt(stack.lastIndex)
                else -> stack.add(seg)
            }
        }
        val joined = stack.joinToString("/")
        return when {
            drivePrefix.isNotEmpty() -> "$drivePrefix/$joined$query"
            leadingSlash -> "/$joined$query"
            else -> "$joined$query"
        }
    }

    private fun decodePercentSimple(s: String): String {
        if ('%' !in s) return s
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.add(hex.toByte())
                    i += 3
                    continue
                }
            }
            for (b in s[i].toString().encodeToByteArray()) {
                bytes.add(b)
            }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }
}
