package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.document.BookContainer
import com.nexustools.genericui.component.document.BookPage
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize

/**
 * In-place [BookContainer] builder for composing multi-page printable documents (defaulting to
 * [PrintPageSize.A4] portrait).
 */
inline fun AbstractContainer.bookContainer(
    pageSize: PrintPageSize = PrintPageSize.A4,
    orientation: PrintPageOrientation = PrintPageOrientation.PORTRAIT,
    marginMm: Float = 12f,
    documentTitle: String = "Document",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker BookContainer).() -> Unit = {}
): BookContainer = mountDslComponent(
    this,
    BookContainer(
        pageSize = pageSize,
        orientation = orientation,
        marginMm = marginMm,
        documentTitle = documentTitle
    ),
    classes,
    id,
    block
)

/**
 * Adds a [BookPage] to this [BookContainer] and configures its child GenericUI components in-place.
 */
fun BookContainer.page(
    title: String = "",
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker BookPage).() -> Unit = {}
): BookPage {
    val bookPage = addPage(title = title)
    if (!classes.isNullOrBlank()) {
        bookPage.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        bookPage.id = id
    }
    bookPage.block()
    return bookPage
}
