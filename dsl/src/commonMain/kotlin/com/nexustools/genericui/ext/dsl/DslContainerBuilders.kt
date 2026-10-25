package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.htmlcompat.Article
import com.nexustools.genericui.component.htmlcompat.Aside
import com.nexustools.genericui.component.htmlcompat.Br
import com.nexustools.genericui.component.htmlcompat.Details
import com.nexustools.genericui.component.htmlcompat.Div
import com.nexustools.genericui.component.htmlcompat.Fieldset
import com.nexustools.genericui.component.htmlcompat.Figure
import com.nexustools.genericui.component.htmlcompat.Footer
import com.nexustools.genericui.component.htmlcompat.Form
import com.nexustools.genericui.component.htmlcompat.Header
import com.nexustools.genericui.component.htmlcompat.Hr
import com.nexustools.genericui.component.htmlcompat.Main
import com.nexustools.genericui.component.htmlcompat.Section
import com.nexustools.genericui.component.htmlcompat.Tbody
import com.nexustools.genericui.component.htmlcompat.Td
import com.nexustools.genericui.component.htmlcompat.Tfoot
import com.nexustools.genericui.component.htmlcompat.Th
import com.nexustools.genericui.component.htmlcompat.Thead
import com.nexustools.genericui.component.htmlcompat.Tr
import com.nexustools.genericui.component.layout.Box
import com.nexustools.genericui.component.layout.Col
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.Divider
import com.nexustools.genericui.component.layout.FlexBox
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.Row
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.navigation.StepScrollView
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent

/**
 * Internal helper to apply optional CSS classes and `id` to a newly constructed DSL component,
 * mount it into [parent], and invoke its in-place configuration [block].
 */
@PublishedApi
internal inline fun <T : AbstractComponent> mountDslComponent(
    parent: AbstractContainer,
    component: T,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker T).() -> Unit = {}
): T {
    if (!classes.isNullOrBlank()) {
        component.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        component.id = id
    }
    component.block()
    parent.addChild(component)
    return component
}

/**
 * Top-level `kotlinx.html`-style entry point that creates and configures a root [Div] (`div`).
 */
inline fun buildDiv(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Div).() -> Unit = {}
): Div {
    val root = Div(classes ?: "")
    if (!id.isNullOrBlank()) {
        root.id = id
    }
    root.block()
    return root
}

/**
 * Top-level `kotlinx.html`-style entry point that creates and configures a Bootstrap [Container].
 */
inline fun buildContainer(
    classes: String? = "container",
    id: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container {
    val root = Container(classes ?: "container")
    if (!id.isNullOrBlank()) {
        root.id = id
    }
    root.block()
    return root
}

/**
 * Configures this [AbstractContainer] in-place using `GenericUI`'s `kotlinx.html`-style DSL.
 * Optionally clears existing children first when [clearFirst] is `true`.
 */
inline fun <T : AbstractContainer> T.build(
    clearFirst: Boolean = false,
    block: (@GenericUiDslMarker T).() -> Unit
): T {
    if (clearFirst) {
        clearChildren()
    }
    this.block()
    return this
}

/**
 * Mounts any custom [AbstractComponent] instance in-place inside this [AbstractContainer].
 */
inline fun <T : AbstractComponent> AbstractContainer.component(
    child: T,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker T).() -> Unit = {}
): T = mountDslComponent(this, child, classes, id, block)

/**
 * In-place `<div>` [Div] builder matching `kotlinx.html`'s `div(classes = "...") { ... }`.
 */
inline fun AbstractContainer.div(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Div).() -> Unit = {}
): Div = mountDslComponent(this, Div(), classes, id, block)

/**
 * In-place Bootstrap `.container` builder.
 */
inline fun AbstractContainer.container(
    classes: String? = "container",
    id: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container = mountDslComponent(this, Container("container"), classes, id, block)

/**
 * In-place `<section>` [Section] builder.
 */
inline fun AbstractContainer.section(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Section).() -> Unit = {}
): Section = mountDslComponent(this, Section(), classes, id, block)

/**
 * In-place `<header>` [Header] builder.
 */
inline fun AbstractContainer.header(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Header).() -> Unit = {}
): Header = mountDslComponent(this, Header(), classes, id, block)

/**
 * In-place `<footer>` [Footer] builder.
 */
inline fun AbstractContainer.footer(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Footer).() -> Unit = {}
): Footer = mountDslComponent(this, Footer(), classes, id, block)

/**
 * In-place `<main>` [Main] builder.
 */
inline fun AbstractContainer.main(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Main).() -> Unit = {}
): Main = mountDslComponent(this, Main(), classes, id, block)

/**
 * In-place `<nav>` [Container] builder.
 */
inline fun AbstractContainer.nav(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container = mountDslComponent(this, Container("nav", ""), classes, id, block)

/**
 * In-place `<article>` [Article] builder.
 */
inline fun AbstractContainer.article(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Article).() -> Unit = {}
): Article = mountDslComponent(this, Article(), classes, id, block)

/**
 * In-place `<aside>` [Aside] builder.
 */
inline fun AbstractContainer.aside(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Aside).() -> Unit = {}
): Aside = mountDslComponent(this, Aside(), classes, id, block)

/**
 * In-place `<form>` [Form] builder.
 */
inline fun AbstractContainer.form(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Form).() -> Unit = {}
): Form = mountDslComponent(this, Form(), classes, id, block)

/**
 * In-place `<fieldset>` [Fieldset] builder.
 */
inline fun AbstractContainer.fieldset(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Fieldset).() -> Unit = {}
): Fieldset = mountDslComponent(this, Fieldset(), classes, id, block)

/**
 * In-place `<figure>` [Figure] builder.
 */
inline fun AbstractContainer.figure(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Figure).() -> Unit = {}
): Figure = mountDslComponent(this, Figure(), classes, id, block)

/**
 * In-place `<details>` [Details] builder.
 */
inline fun AbstractContainer.details(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Details).() -> Unit = {}
): Details = mountDslComponent(this, Details(), classes, id, block)

/**
 * In-place stacked overlay [Box] builder (`position-relative`).
 */
inline fun AbstractContainer.box(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Box).() -> Unit = {}
): Box = mountDslComponent(this, Box(), classes, id, block)

/**
 * In-place Bootstrap [Row] builder (`row`).
 */
inline fun AbstractContainer.row(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Row).() -> Unit = {}
): Row = mountDslComponent(this, Row(), classes, id, block)

/**
 * In-place Bootstrap [Col] builder (`col` / `col-$span`).
 */
inline fun AbstractContainer.col(
    classes: String? = null,
    span: Int? = null,
    weight: Float = 1.0f,
    id: String? = null,
    block: (@GenericUiDslMarker Col).() -> Unit = {}
): Col = mountDslComponent(this, Col(span = span, weight = weight), classes, id, block)

/**
 * Alias for [col].
 */
inline fun AbstractContainer.column(
    classes: String? = null,
    span: Int? = null,
    weight: Float = 1.0f,
    id: String? = null,
    block: (@GenericUiDslMarker Col).() -> Unit = {}
): Col = col(classes = classes, span = span, weight = weight, id = id, block = block)

/**
 * In-place horizontal stack [HStack] builder (`hstack`).
 */
inline fun AbstractContainer.hstack(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker HStack).() -> Unit = {}
): HStack = mountDslComponent(this, HStack(), classes, id, block)

/**
 * In-place vertical stack [VStack] builder (`vstack`).
 */
inline fun AbstractContainer.vstack(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker VStack).() -> Unit = {}
): VStack = mountDslComponent(this, VStack(), classes, id, block)

/**
 * In-place [FlexBox] builder (`d-flex`).
 */
inline fun AbstractContainer.flexBox(
    direction: FlexDirection = FlexDirection.ROW,
    justify: JustifyContent? = null,
    align: AlignItems? = null,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker FlexBox).() -> Unit = {}
): FlexBox = mountDslComponent(this, FlexBox(), classes, id) {
    setFlexDirection(direction)
    if (justify != null) setJustifyContent(justify)
    if (align != null) setAlignItems(align)
    block()
}

/**
 * In-place [ScrollView] builder.
 */
inline fun AbstractContainer.scrollView(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker ScrollView).() -> Unit = {}
): ScrollView = mountDslComponent(this, ScrollView(), classes, id, block)

/**
 * In-place [StepScrollView] builder (`scrollview` with up/down stepper buttons and viewport).
 */
inline fun AbstractContainer.stepScrollView(
    wrapAround: Boolean = true,
    stepHeightPx: Int? = null,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker StepScrollView).() -> Unit = {}
): StepScrollView = mountDslComponent(
    this,
    StepScrollView(wrapAround = wrapAround, stepHeightPx = stepHeightPx),
    classes,
    id,
    block
)

/**
 * In-place `<hr>` [Hr] builder.
 */
inline fun AbstractContainer.hr(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Hr).() -> Unit = {}
): Hr = mountDslComponent(this, Hr(), classes, id, block)

/**
 * Alias for [hr].
 */
inline fun AbstractContainer.divider(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Divider).() -> Unit = {}
): Divider = hr(classes = classes, id = id, block = block)

/**
 * In-place [Spacer] / `<br>` builder.
 */
inline fun AbstractContainer.spacer(
    widthPx: Int = 0,
    heightPx: Int = 8,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Spacer).() -> Unit = {}
): Spacer = mountDslComponent(this, Spacer(widthPx = widthPx, heightPx = heightPx), classes, id, block)

/**
 * In-place `<br>` [Br] builder.
 */
fun AbstractContainer.br(
    heightPx: Int = 8,
    classes: String? = null
): Br = mountDslComponent(this, Br(heightPx = heightPx), classes, null)

/**
 * In-place HTML/layout `<table>` [Container] builder (supports nested `thead`, `tbody`, `tfoot`, `tr`, `th`, `td`).
 */
inline fun AbstractContainer.table(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container = mountDslComponent(this, Container("table", "table"), classes, id, block)

/**
 * In-place `<thead>` [Thead] builder.
 */
inline fun AbstractContainer.thead(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Thead).() -> Unit = {}
): Thead = mountDslComponent(this, Thead(), classes, id, block)

/**
 * In-place `<tbody>` [Tbody] builder.
 */
inline fun AbstractContainer.tbody(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Tbody).() -> Unit = {}
): Tbody = mountDslComponent(this, Tbody(), classes, id, block)

/**
 * In-place `<tfoot>` [Tfoot] builder.
 */
inline fun AbstractContainer.tfoot(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Tfoot).() -> Unit = {}
): Tfoot = mountDslComponent(this, Tfoot(), classes, id, block)

/**
 * In-place `<tr>` [Tr] builder.
 */
inline fun AbstractContainer.tr(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Tr).() -> Unit = {}
): Tr = mountDslComponent(this, Tr(), classes, id, block)

/**
 * In-place `<th>` [Th] builder (accepts either a [text] string or nested components / `+"text"`).
 */
inline fun AbstractContainer.th(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Th).() -> Unit = {}
): Th = mountDslComponent(this, Th(), classes, id) {
    if (text.isNotEmpty()) {
        +text
    }
    block()
}

/**
 * In-place `<td>` [Td] builder (accepts either a [text] string or nested components / `+"text"`).
 */
inline fun AbstractContainer.td(
    text: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Td).() -> Unit = {}
): Td = mountDslComponent(this, Td(), classes, id) {
    if (text.isNotEmpty()) {
        +text
    }
    block()
}
