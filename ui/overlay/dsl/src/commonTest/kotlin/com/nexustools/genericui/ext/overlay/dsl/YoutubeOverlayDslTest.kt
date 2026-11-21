package com.nexustools.genericui.ext.overlay.dsl

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.ext.overlay.HybridOverlayLayoutMode
import com.nexustools.genericui.ext.overlay.OverlayActions
import com.nexustools.genericui.ext.overlay.OverlayButtonVariant
import com.nexustools.genericui.ext.overlay.OverlayHostBox
import com.nexustools.genericui.ext.overlay.OverlayIconType
import com.nexustools.genericui.ext.overlay.OverlayState
import com.nexustools.genericui.ext.overlay.YoutubeTimelinePlacement
import com.nexustools.genericui.ext.overlay.res.R
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class YoutubeOverlayDslTest {

    @Test
    fun testYoutubePlayerOverlayAndButtonDslBuilders() {
        val root = Container("root")
        var toggledFs: Boolean? = null
        var playPauseCount = 0

        val playBtn = root.overlayPlayPauseButton(
            isPlaying = false,
            variant = OverlayButtonVariant.CENTER_PRIMARY,
            id = "dsl-play"
        ) {
            onPlayPauseToggle = { playPauseCount++ }
        }
        val fsBtn = root.overlayFullscreenButton(
            isFullscreen = false,
            variant = OverlayButtonVariant.CIRCLE_PILL,
            id = "dsl-fs",
            onFullscreenToggle = { toggledFs = it }
        )
        val nextBtn = root.overlayControlButton(
            iconType = OverlayIconType.NEXT,
            variant = OverlayButtonVariant.INNER_PILL,
            id = "dsl-next"
        )

        assertNull(playBtn.tooltipText)
        assertNull(fsBtn.tooltipText)
        assertNull(nextBtn.tooltipText)
        assertEquals(R.drawable.ic_play, playBtn.iconRes)
        assertEquals(R.drawable.ic_fullscreen_enter, fsBtn.iconRes)
        assertEquals(R.drawable.ic_next, nextBtn.iconRes)

        playBtn.triggerClick()
        assertEquals(1, playPauseCount)

        fsBtn.triggerClick()
        assertEquals(true, toggledFs)
        assertEquals(R.drawable.ic_fullscreen_exit, fsBtn.iconRes)

        val standalone = buildYoutubePlayerOverlay(
            state = OverlayState(title = "DSL Video", durationSec = 180.0),
            id = "standalone-yt"
        ) {
            val controller = hybridOverlayDifference(
                initialMode = HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT
            )
            assertTrue(controller.isCompactMode)
        }
        assertEquals("DSL Video", standalone.titleLabel.text)
        assertEquals(YoutubeTimelinePlacement.BELOW_CONTROLS, standalone.timelinePlacement)
        assertEquals(
            CssFilter(blur = CssLength.px(16f), saturate = 1.2f),
            standalone.centerPlayPauseButton.backdropFilter
        )
        assertFalse(standalone.playPauseButton.isVisible)

        val mounted = root.youtubePlayerOverlay(
            actions = OverlayActions(onPlayPause = { playPauseCount++ }),
            id = "mounted-yt"
        )
        assertEquals(4, root.children.size)

        val host = OverlayHostBox(content = Container("video-surface"))
        val attached = host.attachYoutubePlayerOverlay(id = "host-yt")
        assertSame(attached, host.overlay)
        assertEquals("0:00 / --:--", attached.timeReadoutLabel.text)

        standalone.dispose()
        mounted.dispose()
        host.dispose()
    }
}
