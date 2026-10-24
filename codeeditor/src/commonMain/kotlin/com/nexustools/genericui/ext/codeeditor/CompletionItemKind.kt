package com.nexustools.genericui.ext.codeeditor

/**
 * Category of an IntelliSense autocomplete [CompletionItem].
 */
enum class CompletionItemKind(val badgeChar: String) {
    KEYWORD("K"),
    FUNCTION("ƒ"),
    METHOD("m"),
    CLASS("C"),
    INTERFACE("I"),
    ENUM("E"),
    VARIABLE("v"),
    PROPERTY("p"),
    SNIPPET("S")
}
