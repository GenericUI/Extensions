package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.htmlcompat.A
import com.nexustools.genericui.component.htmlcompat.B
import com.nexustools.genericui.component.htmlcompat.Code
import com.nexustools.genericui.component.htmlcompat.Em
import com.nexustools.genericui.component.htmlcompat.Figcaption
import com.nexustools.genericui.component.htmlcompat.H1
import com.nexustools.genericui.component.htmlcompat.H2
import com.nexustools.genericui.component.htmlcompat.H3
import com.nexustools.genericui.component.htmlcompat.H4
import com.nexustools.genericui.component.htmlcompat.H5
import com.nexustools.genericui.component.htmlcompat.H6
import com.nexustools.genericui.component.htmlcompat.I
import com.nexustools.genericui.component.htmlcompat.P
import com.nexustools.genericui.component.htmlcompat.Pre
import com.nexustools.genericui.component.htmlcompat.Small
import com.nexustools.genericui.component.htmlcompat.Span
import com.nexustools.genericui.component.htmlcompat.Strong
import com.nexustools.genericui.component.htmlcompat.Sub
import com.nexustools.genericui.component.htmlcompat.Summary
import com.nexustools.genericui.component.htmlcompat.Sup
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.Blockquote
import com.nexustools.genericui.component.typography.CodeBlock
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.MarkdownView
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
 * In-place `<h1>` [H1] builder matching `kotlinx.html`'s `h1`.
 */
inline fun AbstractContainer.h1(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker H1).() -> Unit = {}
): H1 = mountDslComponent(this, H1(text = text), classes, id, block)

/**
 * In-place `<h2>` [H2] builder matching `kotlinx.html`'s `h2`.
 */
inline fun AbstractContainer.h2(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker H2).() -> Unit = {}
): H2 = mountDslComponent(this, H2(text = text), classes, id, block)

/**
 * In-place `<h3>` [H3] builder matching `kotlinx.html`'s `h3`.
 */
inline fun AbstractContainer.h3(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker H3).() -> Unit = {}
): H3 = mountDslComponent(this, H3(text = text), classes, id, block)

/**
 * In-place `<h4>` [H4] builder matching `kotlinx.html`'s `h4`.
 */
inline fun AbstractContainer.h4(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker H4).() -> Unit = {}
): H4 = mountDslComponent(this, H4(text = text), classes, id, block)

/**
 * In-place `<h5>` [H5] builder matching `kotlinx.html`'s `h5`.
 */
inline fun AbstractContainer.h5(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker H5).() -> Unit = {}
): H5 = mountDslComponent(this, H5(text = text), classes, id, block)

/**
 * In-place `<h6>` [H6] builder matching `kotlinx.html`'s `h6`.
 */
inline fun AbstractContainer.h6(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker H6).() -> Unit = {}
): H6 = mountDslComponent(this, H6(text = text), classes, id, block)

/**
 * In-place `<p>` [P] paragraph builder matching `kotlinx.html`'s `p`.
 */
inline fun AbstractContainer.p(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker P).() -> Unit = {}
): P = mountDslComponent(this, P(text = text), classes, id, block)

/**
 * Alias for [p].
 */
inline fun AbstractContainer.paragraph(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker P).() -> Unit = {}
): P = p(text = text, classes = classes, id = id, block = block)

/**
 * In-place `<span>` [Span] inline builder matching `kotlinx.html`'s `span`.
 */
inline fun AbstractContainer.span(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Span).() -> Unit = {}
): Span = mountDslComponent(this, Span(text = text), classes, id, block)

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
 * In-place `<sup>` superscript [Sup] builder matching `kotlinx.html`'s `sup`.
 */
inline fun AbstractContainer.sup(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Sup).() -> Unit = {}
): Sup = mountDslComponent(this, Sup(text = text), classes, id, block)

/**
 * In-place `<sub>` subscript [Sub] builder matching `kotlinx.html`'s `sub`.
 */
inline fun AbstractContainer.sub(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Sub).() -> Unit = {}
): Sub = mountDslComponent(this, Sub(text = text), classes, id, block)

/**
 * In-place `<small>` [Small] builder matching `kotlinx.html`'s `small`.
 */
inline fun AbstractContainer.small(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Small).() -> Unit = {}
): Small = mountDslComponent(this, Small(text = text), classes, id, block)

/**
 * In-place `<strong>` bold [Strong] builder matching `kotlinx.html`'s `strong`.
 */
inline fun AbstractContainer.strong(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Strong).() -> Unit = {}
): Strong = mountDslComponent(this, Strong(text = text), classes, id, block)

/**
 * In-place `<b>` bold [B] builder matching `kotlinx.html`'s `b`.
 */
inline fun AbstractContainer.b(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker B).() -> Unit = {}
): B = mountDslComponent(this, B(text = text), classes, id, block)

/**
 * In-place `<em>` italic [Em] builder matching `kotlinx.html`'s `em`.
 */
inline fun AbstractContainer.em(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Em).() -> Unit = {}
): Em = mountDslComponent(this, Em(text = text), classes, id, block)

/**
 * In-place `<i>` italic/icon [I] builder matching `kotlinx.html`'s `i`.
 */
inline fun AbstractContainer.i(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker I).() -> Unit = {}
): I = mountDslComponent(this, I(text = text), classes, id, block)

/**
 * In-place `<a>` link [A] builder matching `kotlinx.html`'s `a`.
 */
inline fun AbstractContainer.a(
    text: String = "",
    href: String? = null,
    classes: String? = null,
    id: String? = null,
    noinline onClick: (() -> Unit)? = null,
    block: (@GenericUiDslMarker A).() -> Unit = {}
): A = mountDslComponent(this, A(text = text, href = href), classes, id) {
    if (onClick != null) {
        this.onClick = onClick
    }
    block()
}

/**
 * In-place `<figcaption>` [Figcaption] builder.
 */
inline fun AbstractContainer.figcaption(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Figcaption).() -> Unit = {}
): Figcaption = mountDslComponent(this, Figcaption(text = text), classes, id, block)

/**
 * In-place `<summary>` [Summary] builder.
 */
inline fun AbstractContainer.summary(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Summary).() -> Unit = {}
): Summary = mountDslComponent(this, Summary(text = text), classes, id, block)

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
 * In-place `<pre>` [Pre] builder.
 */
inline fun AbstractContainer.pre(
    code: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Pre).() -> Unit = {}
): Pre = mountDslComponent(this, Pre(code = code), classes, id, block)

/**
 * In-place `<code>` [Code] builder.
 */
inline fun AbstractContainer.code(
    code: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Code).() -> Unit = {}
): Code = mountDslComponent(this, Code(code = code), classes, id, block)

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
