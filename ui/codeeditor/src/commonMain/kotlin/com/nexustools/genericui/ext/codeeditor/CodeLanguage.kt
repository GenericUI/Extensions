package com.nexustools.genericui.ext.codeeditor

/**
 * Programming and markup languages supported by [SyntaxTokenizer] and [IdeCodeEditor].
 */
enum class CodeLanguage(
    val displayName: String,
    val extensions: List<String>,
    val lineCommentPrefix: String? = "//",
    val blockCommentStart: String? = "/*",
    val blockCommentEnd: String? = "*/"
) {
    KOTLIN("Kotlin", listOf("kt", "kts")),
    JAVA("Java", listOf("java")),
    TYPESCRIPT("TypeScript", listOf("ts", "tsx")),
    JAVASCRIPT("JavaScript", listOf("js", "jsx", "mjs", "cjs")),
    PYTHON("Python", listOf("py", "pyw"), lineCommentPrefix = "#", blockCommentStart = "\"\"\"", blockCommentEnd = "\"\"\""),
    C("C", listOf("c", "h")),
    CPP("C++", listOf("cpp", "hpp", "cc", "cxx", "hxx")),
    CSHARP("C#", listOf("cs")),
    RUST("Rust", listOf("rs")),
    GO("Go", listOf("go")),
    GLSL("GLSL", listOf("glsl", "vert", "frag", "comp")),
    WGSL("WGSL", listOf("wgsl")),
    XML("XML", listOf("xml", "plist", "csproj"), lineCommentPrefix = null, blockCommentStart = "<!--", blockCommentEnd = "-->"),
    SVG_XML("SVG XML", listOf("svg"), lineCommentPrefix = null, blockCommentStart = "<!--", blockCommentEnd = "-->"),
    HTML("HTML", listOf("html", "htm"), lineCommentPrefix = null, blockCommentStart = "<!--", blockCommentEnd = "-->"),
    CSS("CSS", listOf("css"), lineCommentPrefix = null),
    SCSS("SCSS", listOf("scss", "sass")),
    JSON("JSON", listOf("json", "jsonl"), lineCommentPrefix = null, blockCommentStart = null, blockCommentEnd = null),
    YAML("YAML", listOf("yaml", "yml"), lineCommentPrefix = "#", blockCommentStart = null, blockCommentEnd = null),
    TOML("TOML", listOf("toml"), lineCommentPrefix = "#", blockCommentStart = null, blockCommentEnd = null),
    MARKDOWN("Markdown", listOf("md", "markdown"), lineCommentPrefix = null, blockCommentStart = "<!--", blockCommentEnd = "-->"),
    SQL("SQL", listOf("sql"), lineCommentPrefix = "--"),
    SHELL("Shell", listOf("sh", "bash", "zsh", "ps1"), lineCommentPrefix = "#", blockCommentStart = null, blockCommentEnd = null),
    PLAIN_TEXT("Plain Text", listOf("txt", "log"), lineCommentPrefix = null, blockCommentStart = null, blockCommentEnd = null);

    companion object {
        fun fromFileName(fileName: String): CodeLanguage {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return PLAIN_TEXT
            return entries.firstOrNull { ext in it.extensions } ?: PLAIN_TEXT
        }

        fun fromFenceTag(tag: String): CodeLanguage {
            val clean = tag.trim().lowercase()
            if (clean.isEmpty()) return PLAIN_TEXT
            return entries.firstOrNull {
                it.name.lowercase() == clean ||
                    it.displayName.lowercase() == clean ||
                    clean in it.extensions
            } ?: PLAIN_TEXT
        }
    }
}
