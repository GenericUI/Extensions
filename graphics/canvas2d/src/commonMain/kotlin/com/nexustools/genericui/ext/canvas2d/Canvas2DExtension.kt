package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.ApplicationLifecycleExtensionHook
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.canvas2d.res.GeneratedResourceRegistry
import com.nexustools.genericui.ext.canvas2d.res.R
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl
import com.nexustools.genericui.nativeimpl.media.ScrollCanvas2DNativeImpl
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.sass.SassCompiler
import com.nexustools.genericui.style.theme.ThemeStyleSheetRegistry
import kotlin.math.max

/**
 * Extension registry and [ImageBuffer] / [ImageBuffer2DContext] bridge for `:extensions:graphics:canvas2d`,
 * relying strictly on `:extensions:graphics:imageprocessing`'s universal [ImageBuffer] and
 * [ImageBuffer.getContext] (`SystemGpuImageBuffer` on System targets, and
 * `WebImageBuffer` on Web).
 */
object Canvas2DExtension {
    const val DEFAULT_STYLE_MODULE_ID: String = "ext:canvas2d:style"

    private var cachedDefaultStyleSheet: CssStyleSheet? = null
    private var lifecycleHookInstalled: Boolean = false
    private var contextFactory: ((Int, Int) -> ImageBuffer2DContext)? = null
    private var canvas2dNativeFactory: ((Int, Int) -> Canvas2DNativeImpl<*>)? = null
    private var scrollCanvas2dNativeFactory: ((Int, Int, Int, Int) -> ScrollCanvas2DNativeImpl<*>)? = null
    private var autoInstalledFactoryRef: NativeFactory? = null
    private var isInstallingPlatformDefaults: Boolean = false

    fun defaultStyleSheet(): CssStyleSheet {
        cachedDefaultStyleSheet?.let { return it }
        GeneratedResourceRegistry.ensureInitialized()
        val rawScss = Resources.getRawText(R.style.style).orEmpty()
        return SassCompiler.compileToStyleSheet(rawScss).also { sheet ->
            cachedDefaultStyleSheet = sheet
        }
    }

    fun ensureStyleSheetRegistered(): CssStyleSheet {
        val sheet = defaultStyleSheet()
        if (ThemeStyleSheetRegistry.getCompiledThemeEntries().none { it.first == DEFAULT_STYLE_MODULE_ID }) {
            ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(DEFAULT_STYLE_MODULE_ID, sheet)
        }
        if (!lifecycleHookInstalled) {
            lifecycleHookInstalled = true
            GenericUiExtensionRegistry.registerLifecycleHook(
                object : ApplicationLifecycleExtensionHook {
                    override fun onAppPrepared(app: IApplication, descriptor: GenericUiAppDescriptor) {
                        if (ThemeStyleSheetRegistry.getCompiledThemeEntries().none { it.first == DEFAULT_STYLE_MODULE_ID }) {
                            ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(DEFAULT_STYLE_MODULE_ID, defaultStyleSheet())
                        }
                    }
                }
            )
        }
        return sheet
    }

    fun registerContextFactory(factory: (width: Int, height: Int) -> ImageBuffer2DContext) {
        if (!isInstallingPlatformDefaults) {
            autoInstalledFactoryRef = null
        }
        contextFactory = factory
    }

    fun registerCanvas2DFactory(factory: (width: Int, height: Int) -> Canvas2DNativeImpl<*>) {
        if (!isInstallingPlatformDefaults) {
            autoInstalledFactoryRef = null
        }
        canvas2dNativeFactory = factory
    }

    fun registerScrollCanvas2DFactory(
        factory: (viewportWidth: Int, viewportHeight: Int, contentWidth: Int, contentHeight: Int) -> ScrollCanvas2DNativeImpl<*>
    ) {
        if (!isInstallingPlatformDefaults) {
            autoInstalledFactoryRef = null
        }
        scrollCanvas2dNativeFactory = factory
    }

    private fun ensureDefaultProvidersInstalled() {
        if (isInstallingPlatformDefaults) return
        val currentFactory = GenericUI.activeFactory
        val needsInstall = canvas2dNativeFactory == null ||
            (autoInstalledFactoryRef != null && currentFactory !== autoInstalledFactoryRef)
        if (!needsInstall) return
        isInstallingPlatformDefaults = true
        try {
            installPlatformDefaultCanvas2DProviders()
            autoInstalledFactoryRef = currentFactory
        } finally {
            isInstallingPlatformDefaults = false
        }
    }

    fun createCanvas2DNative(width: Int = 800, height: Int = 600): Canvas2DNativeImpl<*> {
        ensureDefaultProvidersInstalled()
        return canvas2dNativeFactory?.invoke(width, height)
            ?: throw IllegalStateException("No Canvas2DNativeImpl provider registered in Canvas2DExtension")
    }

    fun createScrollCanvas2DNativeOrNull(
        viewportWidth: Int = 800,
        viewportHeight: Int = 600,
        contentWidth: Int = viewportWidth,
        contentHeight: Int = viewportHeight
    ): ScrollCanvas2DNativeImpl<*>? {
        ensureDefaultProvidersInstalled()
        return scrollCanvas2dNativeFactory?.invoke(viewportWidth, viewportHeight, contentWidth, contentHeight)
    }

    fun createScrollCanvas2DNative(
        viewportWidth: Int = 800,
        viewportHeight: Int = 600,
        contentWidth: Int = viewportWidth,
        contentHeight: Int = viewportHeight
    ): ScrollCanvas2DNativeImpl<*> {
        ensureDefaultProvidersInstalled()
        return scrollCanvas2dNativeFactory?.invoke(viewportWidth, viewportHeight, contentWidth, contentHeight)
            ?: SystemScrollCanvas2DNativeImpl(
                initialViewportWidth = viewportWidth,
                initialViewportHeight = viewportHeight,
                initialContentWidth = contentWidth,
                initialContentHeight = contentHeight
            )
    }

    /**
     * Creates an `:extensions:graphics:imageprocessing` [ImageBuffer] for offscreen 2D rendering or
     * as a `Canvas2D` backend. Call `imageBuffer.getContext()` to fetch its [ImageBuffer2DContext].
     */
    fun createImageBuffer(
        width: Int = 800,
        height: Int = 600,
        doubleBuffer: Boolean = false,
        antialias: Boolean = true
    ): ImageBuffer = ImageBuffer.create(
        width = width,
        height = height,
        doubleBuffer = doubleBuffer,
        antialias = antialias
    )

    fun wrapImageBufferContext(imageBuffer: ImageBuffer): ImageBuffer2DContext =
        imageBuffer.getContext()

    fun createContext(width: Int = 800, height: Int = 600, doubleBuffer: Boolean = false): ImageBuffer2DContext {
        ensureDefaultProvidersInstalled()
        contextFactory?.let { return it(width, height) }
        return createImageBuffer(
            width = width,
            height = height,
            doubleBuffer = doubleBuffer,
            antialias = true
        ).getContext()
    }

    /**
     * Draws an [com.nexustools.genericui.image.ImageBuffer] onto [context] at `(dx, dy)` using
     * `:extensions:graphics:imageprocessing`'s accelerated [ImageBuffer2DContext].
     */
    fun drawImage(
        context: ImageBuffer2DContext,
        buffer: com.nexustools.genericui.image.ImageBuffer,
        dx: Float = 0f,
        dy: Float = 0f
    ) {
        context.drawImage(buffer, dx, dy)
    }

    /**
     * Fetches a rectangular region of [context] into a `:common` [com.nexustools.genericui.image.ImageBuffer]
     * via `:extensions:graphics:imageprocessing`.
     */
    fun extractImageBuffer(
        context: ImageBuffer2DContext,
        x: Int = 0,
        y: Int = 0,
        width: Int = context.width,
        height: Int = context.height
    ): com.nexustools.genericui.image.ImageBuffer {
        val safeW = max(1, width)
        val safeH = max(1, height)
        val surface = context.imageBuffer
        if (x == 0 && y == 0 && safeW == surface.width && safeH == surface.height) {
            return surface.toImageBuffer()
        }
        val rgbaBytes = surface.readPixelsRgba(x, y, safeW, safeH)
        return com.nexustools.genericui.image.ImageBuffer(
            width = safeW,
            height = safeH,
            pixelFormat = PixelFormat.RGBA_8888,
            bytes = if (rgbaBytes.size == safeW * safeH * 4) rgbaBytes else ByteArray(safeW * safeH * 4)
        )
    }
}
