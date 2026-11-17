package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry

/**
 * Self-rendered printable page preview container (`BookContainerAlienImpl`) implementing [BookContainerNativeImpl].
 */
class BookContainerAlienImpl(
    private val hostFactory: NativeFactory? = null,
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "book-container",
    tagName = "section",
    session = session
), BookContainerNativeImpl<Any?> {

    var currentPageSize: PrintPageSize = PrintPageSize.A4
        private set

    var currentOrientation: PrintPageOrientation = PrintPageOrientation.PORTRAIT
        private set

    var currentMarginMm: Float = 12f
        private set

    var documentTitleValue: String = "Document"
        private set

    private val bookStyleSheets = mutableListOf<PrecompiledStyleSheet>()

    override fun setPageSize(pageSize: PrintPageSize, orientation: PrintPageOrientation, marginMm: Float) {
        currentPageSize = pageSize
        currentOrientation = orientation
        currentMarginMm = marginMm
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setDocumentTitle(title: String) {
        documentTitleValue = title
    }

    override fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {
        val normSource = StyleSheetRegistry.normalizePath(styleSheet.sourcePath)
        val existingIdx = bookStyleSheets.indexOfFirst {
            it === styleSheet ||
                (normSource.isNotEmpty() && StyleSheetRegistry.normalizePath(it.sourcePath) == normSource)
        }
        if (existingIdx >= 0) {
            bookStyleSheets[existingIdx] = styleSheet
        } else {
            bookStyleSheets.add(styleSheet)
        }
        setAttribute("data-book-stylesheets", bookStyleSheets.joinToString("\n") { it.sourcePath })
        node.markLayoutDirty()
    }

    override fun removeStyleSheet(styleSheet: PrecompiledStyleSheet) {
        val normSource = StyleSheetRegistry.normalizePath(styleSheet.sourcePath)
        val removed = bookStyleSheets.remove(styleSheet) ||
            bookStyleSheets.removeAll {
                normSource.isNotEmpty() && StyleSheetRegistry.normalizePath(it.sourcePath) == normSource
            }
        if (removed) {
            setAttribute("data-book-stylesheets", bookStyleSheets.joinToString("\n") { it.sourcePath })
            node.markLayoutDirty()
        }
    }

    override fun clearStyleSheets() {
        bookStyleSheets.clear()
        setAttribute("data-book-stylesheets", "")
        node.markLayoutDirty()
    }

    fun getBookStyleSheets(): List<PrecompiledStyleSheet> = bookStyleSheets.toList()

    override fun printBook(
        documentTitle: String,
        pageSize: PrintPageSize,
        orientation: PrintPageOrientation,
        marginMm: Float
    ): Boolean {
        setDocumentTitle(documentTitle)
        setPageSize(pageSize, orientation, marginMm)
        val hostBook = hostFactory?.createBookContainer()
        return hostBook?.printBook(documentTitle, pageSize, orientation, marginMm) ?: true
    }
}
