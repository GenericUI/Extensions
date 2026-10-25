package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl

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
