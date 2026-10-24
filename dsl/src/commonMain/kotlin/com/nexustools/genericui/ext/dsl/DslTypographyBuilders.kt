package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.Blockquote
import com.nexustools.genericui.component.typography.CodeBlock
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.MarkdownView
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * In-place `<h1..h6>` [Heading] builder.
 */
inline fun AbstractContainer.heading(
    level: Int = 1,
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Heading).() -> Unit = {}
): Heading = mountDslComponent(this, Heading(text = text, level = level), classes, id, block)

/**
 * In-place `<h1>` [Heading] builder matching `kotlinx.html`'s `h1`.
 */
inline fun AbstractContainer.h1(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Heading).() -> Unit = {}
): Heading = heading(level = 1, text = text, classes = classes, id = id, block = block)

/**
 * In-place `<h2>` [Heading] builder matching `kotlinx.html`'s `h2`.
 */
inline fun AbstractContainer.h2(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Heading).() -> Unit = {}
): Heading = heading(level = 2, text = text, classes = classes, id = id, block = block)

/**
 * In-place `<h3>` [Heading] builder matching `kotlinx.html`'s `h3`.
 */
inline fun AbstractContainer.h3(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Heading).() -> Unit = {}
): Heading = heading(level = 3, text = text, classes = classes, id = id, block = block)

/**
 * In-place `<h4>` [Heading] builder matching `kotlinx.html`'s `h4`.
 */
inline fun AbstractContainer.h4(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Heading).() -> Unit = {}
): Heading = heading(level = 4, text = text, classes = classes, id = id, block = block)

/**
 * In-place `<h5>` [Heading] builder matching `kotlinx.html`'s `h5`.
 */
inline fun AbstractContainer.h5(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Heading).() -> Unit = {}
): Heading = heading(level = 5, text = text, classes = classes, id = id, block = block)

/**
 * In-place `<h6>` [Heading] builder matching `kotlinx.html`'s `h6`.
 */
inline fun AbstractContainer.h6(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Heading).() -> Unit = {}
): Heading = heading(level = 6, text = text, classes = classes, id = id, block = block)

/**
 * In-place `<p>` [Text] paragraph builder matching `kotlinx.html`'s `p`.
 */
inline fun AbstractContainer.p(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Text).() -> Unit = {}
): Text = mountDslComponent(this, Text(text = text, paragraph = true), classes, id, block)

/**
 * Alias for [p].
 */
inline fun AbstractContainer.paragraph(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Text).() -> Unit = {}
): Text = p(text = text, classes = classes, id = id, block = block)

/**
 * In-place `<span>` [Text] inline builder matching `kotlinx.html`'s `span`.
 */
inline fun AbstractContainer.span(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Text).() -> Unit = {}
): Text = mountDslComponent(this, Text(text = text, paragraph = false), classes, id, block)

/**
 * In-place `<label>` [Label] builder matching `kotlinx.html`'s `label`.
 */
inline fun AbstractContainer.label(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Label).() -> Unit = {}
): Label = mountDslComponent(this, Label(text = text, tagName = "label", classPrefix = "form-label"), classes, id, block)

/**
 * In-place `<sup>` superscript [Label] builder matching `kotlinx.html`'s `sup`.
 */
inline fun AbstractContainer.sup(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Label).() -> Unit = {}
): Label = mountDslComponent(this, Label(text = text, tagName = "sup", classPrefix = ""), classes, id, block)

/**
 * In-place `<sub>` subscript [Label] builder matching `kotlinx.html`'s `sub`.
 */
inline fun AbstractContainer.sub(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Label).() -> Unit = {}
): Label = mountDslComponent(this, Label(text = text, tagName = "sub", classPrefix = ""), classes, id, block)

/**
 * In-place `<small>` [Label] builder matching `kotlinx.html`'s `small`.
 */
inline fun AbstractContainer.small(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Label).() -> Unit = {}
): Label = mountDslComponent(this, Label(text = text, tagName = "small", classPrefix = "small"), classes, id, block)

/**
 * In-place `<strong>` / `<b>` bold [Label] builder matching `kotlinx.html`'s `strong`.
 */
inline fun AbstractContainer.strong(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Label).() -> Unit = {}
): Label = mountDslComponent(this, Label(text = text, tagName = "strong", classPrefix = "fw-bold"), classes, id, block)

/**
 * Alias for [strong] (`<b>`).
 */
inline fun AbstractContainer.b(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Label).() -> Unit = {}
): Label = strong(text = text, classes = classes, id = id, block = block)

/**
 * In-place `<em>` / `<i>` italic [Label] builder matching `kotlinx.html`'s `em`.
 */
inline fun AbstractContainer.em(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Label).() -> Unit = {}
): Label = mountDslComponent(this, Label(text = text, tagName = "em", classPrefix = "fst-italic"), classes, id, block)

/**
 * Alias for [em] (`<i>`).
 */
inline fun AbstractContainer.i(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Label).() -> Unit = {}
): Label = em(text = text, classes = classes, id = id, block = block)

/**
 * In-place `<a>` link [Label] builder matching `kotlinx.html`'s `a`.
 */
inline fun AbstractContainer.a(
    text: String = "",
    href: String? = null,
    classes: String? = null,
    id: String? = null,
    noinline onClick: (() -> Unit)? = null,
    block: (@GenericUiDslMarker Label).() -> Unit = {}
): Label = mountDslComponent(this, Label(text = text, tagName = "a", classPrefix = "link-primary"), classes, id) {
    if (href != null) {
        tooltipText = href
    }
    if (onClick != null) {
        this.onClick = onClick
    }
    block()
}

/**
 * In-place Bootstrap [Badge] builder.
 */
inline fun AbstractContainer.badge(
    text: String = "",
    type: BootstrapType = BootstrapType.PRIMARY,
    pill: Boolean = false,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Badge).() -> Unit = {}
): Badge = mountDslComponent(this, Badge(text = text, type = type, pill = pill), classes, id, block)

/**
 * In-place `<pre><code>` [CodeBlock] builder.
 */
inline fun AbstractContainer.codeBlock(
    code: String = "",
    languageHint: String = "text",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker CodeBlock).() -> Unit = {}
): CodeBlock = mountDslComponent(this, CodeBlock(code = code, languageHint = languageHint), classes, id, block)

/**
 * Alias for [codeBlock] (`<pre>`).
 */
inline fun AbstractContainer.pre(
    code: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker CodeBlock).() -> Unit = {}
): CodeBlock = codeBlock(code = code, classes = classes, id = id, block = block)

/**
 * Alias for [codeBlock] (`<code>`).
 */
inline fun AbstractContainer.code(
    code: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker CodeBlock).() -> Unit = {}
): CodeBlock = codeBlock(code = code, classes = classes, id = id, block = block)

/**
 * In-place `<blockquote>` [Blockquote] builder.
 */
inline fun AbstractContainer.blockquote(
    quote: String = "",
    footerCitation: String? = null,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Blockquote).() -> Unit = {}
): Blockquote = mountDslComponent(
    this,
    Blockquote(quote = quote, footerCitation = footerCitation),
    classes,
    id,
    block
)

/**
 * In-place [MarkdownView] builder.
 */
inline fun AbstractContainer.markdownView(
    markdown: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker MarkdownView).() -> Unit = {}
): MarkdownView = mountDslComponent(this, MarkdownView(markdown = markdown), classes, id, block)
