package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.content.HorizontalCarouselSelector
import com.nexustools.genericui.component.content.ImageView
import com.nexustools.genericui.component.content.MasterDetailStepSelect
import com.nexustools.genericui.component.content.Table
import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.feedback.ProgressBar
import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.htmlcompat.Li
import com.nexustools.genericui.component.htmlcompat.Ol
import com.nexustools.genericui.component.htmlcompat.Ul
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.surface.CollapsiblePanel
import com.nexustools.genericui.component.surface.ListGroup
import com.nexustools.genericui.component.surface.ListGroupItem
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.ui.ObjectFit

/**
 * In-place Bootstrap [Card] builder.
 */
inline fun AbstractContainer.card(
    title: String? = null,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Card).() -> Unit = {}
): Card = mountDslComponent(this, Card(title = title), classes, id, block)

/**
 * In-place [CollapsiblePanel] builder.
 */
inline fun AbstractContainer.collapsiblePanel(
    title: String = "",
    expanded: Boolean = true,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker CollapsiblePanel).() -> Unit = {}
): CollapsiblePanel = mountDslComponent(
    this,
    CollapsiblePanel(title = title, expanded = expanded),
    classes,
    id,
    block
)

/**
 * In-place `<ul>` [Ul] builder matching `kotlinx.html`'s `ul`.
 */
inline fun AbstractContainer.ul(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Ul).() -> Unit = {}
): Ul = mountDslComponent(this, Ul(), classes, id, block)

/**
 * In-place `<ol>` [Ol] builder matching `kotlinx.html`'s `ol`.
 */
inline fun AbstractContainer.ol(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Ol).() -> Unit = {}
): Ol = mountDslComponent(this, Ol(), classes, id, block)

/**
 * In-place Bootstrap [ListGroup] builder (`listGroup`).
 */
inline fun AbstractContainer.listGroup(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker ListGroup).() -> Unit = {}
): ListGroup = mountDslComponent(this, ListGroup(), classes, id, block)

/**
 * In-place `<li>` [Li] builder matching `kotlinx.html`'s `li`.
 */
inline fun AbstractContainer.li(
    text: String = "",
    active: Boolean = false,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Li).() -> Unit = {}
): Li = mountDslComponent(this, Li(id = id, isActive = active), classes, id) {
    if (text.isNotEmpty()) {
        +text
    }
    block()
}

/**
 * In-place Bootstrap [ListGroupItem] builder (`listGroupItem`).
 */
inline fun AbstractContainer.listGroupItem(
    text: String = "",
    active: Boolean = false,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker ListGroupItem).() -> Unit = {}
): ListGroupItem = mountDslComponent(this, ListGroupItem(id = id, isActive = active), classes, id) {
    if (text.isNotEmpty()) {
        +text
    }
    block()
}

/**
 * In-place `<img>` / [ImageView] builder matching `kotlinx.html`'s `img`.
 */
inline fun AbstractContainer.img(
    src: String? = null,
    alt: String? = null,
    objectFit: ObjectFit = ObjectFit.CONTAIN,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker ImageView).() -> Unit = {}
): ImageView = mountDslComponent(this, ImageView(sourceUrl = src, objectFit = objectFit), classes, id) {
    if (alt != null) {
        fallbackText = alt
    }
    block()
}

/**
 * Alias for [img] (`imageView`).
 */
inline fun AbstractContainer.imageView(
    src: String? = null,
    alt: String? = null,
    objectFit: ObjectFit = ObjectFit.CONTAIN,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker ImageView).() -> Unit = {}
): ImageView = img(src = src, alt = alt, objectFit = objectFit, classes = classes, id = id, block = block)

/**
 * In-place structured [Table] component builder (`dataTable`).
 */
inline fun AbstractContainer.dataTable(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Table).() -> Unit = {}
): Table = mountDslComponent(this, Table(), classes, id, block)

/**
 * In-place [MasterDetailStepSelect] (`vhselect`) builder.
 */
inline fun AbstractContainer.masterDetailStepSelect(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker MasterDetailStepSelect).() -> Unit = {}
): MasterDetailStepSelect = mountDslComponent(this, MasterDetailStepSelect(), classes, id, block)

/**
 * In-place [HorizontalCarouselSelector] (`entries`) builder.
 */
inline fun AbstractContainer.horizontalCarouselSelector(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker HorizontalCarouselSelector).() -> Unit = {}
): HorizontalCarouselSelector = mountDslComponent(this, HorizontalCarouselSelector(), classes, id, block)

/**
 * In-place [Navbar] builder.
 */
inline fun AbstractContainer.navbar(
    brandTitle: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Navbar).() -> Unit = {}
): Navbar = mountDslComponent(this, Navbar(brandTitle = brandTitle), classes, id, block)

/**
 * In-place Bootstrap [Alert] builder.
 */
inline fun AbstractContainer.alert(
    message: String = "",
    type: BootstrapType = BootstrapType.INFO,
    dismissible: Boolean = false,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Alert).() -> Unit = {}
): Alert = mountDslComponent(
    this,
    Alert(message = message, type = type, dismissible = dismissible),
    classes,
    id,
    block
)

/**
 * In-place [ProgressBar] builder.
 */
inline fun AbstractContainer.progressBar(
    progress: Float = 0f,
    indeterminate: Boolean = false,
    type: BootstrapType = BootstrapType.PRIMARY,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker ProgressBar).() -> Unit = {}
): ProgressBar = mountDslComponent(
    this,
    ProgressBar(progress = progress, indeterminate = indeterminate, type = type),
    classes,
    id,
    block
)

/**
 * In-place [Spinner] builder.
 */
inline fun AbstractContainer.spinner(
    growStyle: Boolean = false,
    label: String? = "Loading...",
    type: BootstrapType = BootstrapType.PRIMARY,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker Spinner).() -> Unit = {}
): Spinner = mountDslComponent(
    this,
    Spinner(growStyle = growStyle, label = label, type = type),
    classes,
    id,
    block
)
