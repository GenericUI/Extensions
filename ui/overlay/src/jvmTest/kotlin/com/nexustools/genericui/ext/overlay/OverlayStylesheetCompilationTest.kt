package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.ext.overlay.res.R
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.sass.SassCompiler
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OverlayStylesheetCompilationTest {

    @Test
    fun testOverlayStyleScssAndRawThemeScssCompileCleanlyAndIsolateThemeTokens() {
        // 1. Generic style.scss compiles cleanly with zero $theme variables and exposes @keyframes
        val defaultSheet = OverlayExtension.defaultStyleSheet()
        assertTrue(defaultSheet.rules.isNotEmpty())
        assertTrue(defaultSheet.keyframes.containsKey("overlayFadeIn"))
        assertTrue(defaultSheet.keyframes.containsKey("overlayFadeOut"))

        val styleScss = Resources.getRawText(R.style.style)
        assertNotNull(styleScss)
        assertFalse(styleScss.contains("\$primary"))
        assertFalse(styleScss.contains("\$accent"))
        val compiledStyleCss = SassCompiler.compileToCss(styleScss)
        assertTrue(compiledStyleCss.contains(".gui-overlay-host.gui-overlay-cursor-hidden"))
        assertTrue(compiledStyleCss.contains(".gui-overlay-host-target-cursor-hidden"))
        assertTrue(compiledStyleCss.contains(".gui-overlay-titlebar-native-controls"))
        assertTrue(compiledStyleCss.contains("blur(22px) saturate(1.4)"))
        assertTrue(compiledStyleCss.contains("cursor: none"))

        // 2. Raw consumer theme.scss template exists uncompiled and compiles against consumer $primary/$accent
        val themeFile = sequenceOf(
            File("src/commonMain/resources/styles/theme.scss"),
            File("extensions/ui/overlay/src/commonMain/resources/styles/theme.scss")
        ).firstOrNull { it.isFile }
        assertNotNull(themeFile, "Expected raw overlay theme.scss template to exist")
        val rawThemeScss = themeFile.readText(Charsets.UTF_8)
        assertTrue(rawThemeScss.contains("\$primary"))
        assertTrue(rawThemeScss.contains("\$accent"))
        assertTrue(rawThemeScss.contains("!default"))

        val consumerScss = """
            ${'$'}primary: #ff3366;
            ${'$'}accent: #22d3ee;
            $rawThemeScss
        """.trimIndent()
        val compiledCss = SassCompiler.compileToCss(consumerScss)
        assertFalse(compiledCss.contains("\$accent"), "Expected \$accent to be substituted during compilation")
        assertFalse(compiledCss.contains("\$primary"), "Expected \$primary to be substituted during compilation")

        val compiledThemeSheet = SassCompiler.compileToStyleSheet(consumerScss)
        assertTrue(compiledThemeSheet.rules.isNotEmpty())

        val testBtn = OverlayControlButton(variant = OverlayButtonVariant.CIRCLE_PILL)
        testBtn.applyStyleSheet(compiledThemeSheet)
        assertTrue(compiledCss.contains("rgba(#22d3ee, 0.27)"))
        assertTrue(compiledCss.contains("rgba(#ff3366, 0.40)"))
        assertEquals(
            RgbaColor(34, 211, 238, 255),
            testBtn.computedStyle?.activeStyle?.borderColor
        )
        assertEquals(
            RgbaColor(255, 255, 255, 255),
            testBtn.computedStyle?.hoverStyle?.textColor
        )

        val badge = OverlayControlButtons.createOutlinedBadge("4K").apply {
            addClass("active")
            applyStyleSheet(compiledThemeSheet)
        }
        assertEquals(RgbaColor(34, 211, 238, 255), badge.computedStyle?.borderColor)
        assertEquals(RgbaColor(34, 211, 238, 255), badge.computedStyle?.textColor)
    }

    @Test
    fun testAtomicHiddenFadeBetweenEmbeddedTitleBarControlsAndPersistentRoot() {
        val host = OverlayHostBox(
            content = Container("cinema-stage").apply {
                widthPx = 1280
                heightPx = 720
            }
        )
        val overlay = YoutubePlayerOverlay().apply {
            windowTitleBarEnabled = true
        }
        host.attachOverlay(overlay)
        overlay.setSubtitleCueText("Dialogue cue")
        overlay.setLoadingState(true)
        host.applyStyleSheet(OverlayExtension.defaultStyleSheet())

        assertSame(overlay, overlay.autoHideController.targetComponent)
        assertSame(overlay, overlay.windowTitleBar.parent)
        assertSame(overlay, overlay.hudControlsContainer.parent)
        assertSame(host.persistentOverlayRoot, overlay.subtitleBanner.parent)
        assertSame(host.persistentOverlayRoot, overlay.loadingSpinner.parent)

        overlay.autoHideController.hideOverlay(instant = false)
        assertTrue(overlay.hasClass("hidden"))
        assertFalse(overlay.windowTitleBar.hasClass("hidden"))
        assertFalse(overlay.hudControlsContainer.hasClass("hidden"))
        assertFalse(host.persistentOverlayRoot.hasClass("hidden"))
        assertNull(overlay.windowTitleBar.style.opacity)
        assertNull(overlay.hudControlsContainer.style.opacity)
        assertEquals(0.0f, overlay.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(false, overlay.computedStyle?.pointerEventsEnabled)
        assertEquals("none", overlay.computedStyle?.cursor)
        assertEquals(1.0f, host.persistentOverlayRoot.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(1.0f, overlay.hudControlsContainer.opacity, 0.001f)
        assertTrue(overlay.subtitleBanner.isVisible)
        assertTrue(overlay.loadingSpinner.isVisible)

        overlay.autoHideController.showOverlay(scheduleHide = false)
        assertFalse(overlay.hasClass("hidden"))
        assertEquals(1.0f, overlay.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(true, overlay.computedStyle?.pointerEventsEnabled)
        assertSame(host.persistentOverlayRoot, overlay.subtitleBanner.parent)

        host.dispose()
    }

    @Test
    fun testOverlayHostPropagatesAndRestoresCursorHiddenClasses() {
        val initialTarget = Container("cinema-stage").apply {
            widthPx = 1280
            heightPx = 720
        }
        val host = OverlayHostBox(content = initialTarget)
        val overlay = YoutubePlayerOverlay()
        host.attachOverlay(overlay)
        host.applyStyleSheet(OverlayExtension.defaultStyleSheet())

        assertFalse(host.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(host.overlayRoot.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(initialTarget.hasClass("gui-overlay-host-target-cursor-hidden"))
        assertNull(initialTarget.computedStyle?.cursor)

        // Hiding with hideCursorWhenOverlayHidden = true hides cursor across host, root, and target
        // even though .overlay-fading-root.hidden sets pointer-events: none.
        overlay.autoHideController.hideOverlay(instant = true)
        assertTrue(host.hasClass("gui-overlay-cursor-hidden"))
        assertTrue(host.overlayRoot.hasClass("gui-overlay-cursor-hidden"))
        assertTrue(initialTarget.hasClass("gui-overlay-host-target-cursor-hidden"))
        assertEquals(false, overlay.computedStyle?.pointerEventsEnabled)
        assertEquals("none", host.computedStyle?.cursor)
        assertEquals("none", host.overlayRoot.computedStyle?.cursor)
        assertEquals("none", initialTarget.computedStyle?.cursor)

        // Swapping target while hidden clears old target and applies class to new target.
        val swappedTarget = Container("cinema-stage-next").apply {
            widthPx = 1280
            heightPx = 720
        }
        host.setContent(swappedTarget)
        assertFalse(initialTarget.hasClass("gui-overlay-host-target-cursor-hidden"))
        assertTrue(swappedTarget.hasClass("gui-overlay-host-target-cursor-hidden"))
        assertEquals("none", swappedTarget.computedStyle?.cursor)

        // Showing overlay removes cursor-hidden classes and restores cursor.
        overlay.autoHideController.showOverlay(scheduleHide = false)
        assertFalse(host.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(host.overlayRoot.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(swappedTarget.hasClass("gui-overlay-host-target-cursor-hidden"))
        assertNull(swappedTarget.computedStyle?.cursor)

        // When hideCursorWhenOverlayHidden = false, hiding does NOT add cursor-hidden classes.
        overlay.autoHideController.spec = AutoHideSpec(hideCursorWhenOverlayHidden = false)
        overlay.autoHideController.hideOverlay(instant = true)
        assertTrue(overlay.hasClass(AutoHideController.KEEP_CURSOR_CLASS))
        assertFalse(host.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(host.overlayRoot.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(swappedTarget.hasClass("gui-overlay-host-target-cursor-hidden"))
        assertNull(swappedTarget.computedStyle?.cursor)

        // When KEEP_CURSOR_CLASS is present on an attached hidden overlay, cursor-hidden classes stay absent.
        val keepCursorOverlay = YoutubePlayerOverlay().apply {
            autoHideController.hideOverlay(instant = true)
            addClass(AutoHideController.KEEP_CURSOR_CLASS)
        }
        host.attachOverlay(keepCursorOverlay)
        assertFalse(host.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(host.overlayRoot.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(swappedTarget.hasClass("gui-overlay-host-target-cursor-hidden"))
        assertNull(swappedTarget.computedStyle?.cursor)

        // Detaching or entering PiP clears cursor-hidden classes.
        val hideCursorOverlay = YoutubePlayerOverlay()
        host.attachOverlay(hideCursorOverlay)
        hideCursorOverlay.autoHideController.hideOverlay(instant = true)
        assertTrue(host.hasClass("gui-overlay-cursor-hidden"))
        host.setPictureInPictureMode(true)
        assertFalse(host.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(host.overlayRoot.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(swappedTarget.hasClass("gui-overlay-host-target-cursor-hidden"))

        host.setPictureInPictureMode(false)
        hideCursorOverlay.autoHideController.hideOverlay(instant = true)
        assertTrue(host.hasClass("gui-overlay-cursor-hidden"))
        host.detachOverlay()
        assertFalse(host.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(host.overlayRoot.hasClass("gui-overlay-cursor-hidden"))
        assertFalse(swappedTarget.hasClass("gui-overlay-host-target-cursor-hidden"))

        host.dispose()
    }
}
