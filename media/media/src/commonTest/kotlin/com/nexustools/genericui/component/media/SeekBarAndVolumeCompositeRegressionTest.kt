package com.nexustools.genericui.component.media

import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.form.StyledSlider
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.surface.StyledComponent
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.event.PointerDragManager
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.style.css.CssProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Regression tests for Wave 2.4 (`SeekBar : StyledSlider`, `ExpandingVolumeSlider : Composite`,
 * `media/style.scss`, and zero inline theme/state Kotlin style setters).
 */
class SeekBarAndVolumeCompositeRegressionTest {

    @AfterTest
    fun tearDown() {
        PointerDragManager.cancelActiveDrag()
    }

    @Test
    fun testSeekBarExtendsStyledSliderWithPreloadSegmentLastCssZIndexAndPointerClickThrough() {
        val rawStyleScss = MediaExtension.rawDefaultStyleScss()
        assertTrue(rawStyleScss.isNotBlank())
        assertFalse(rawStyleScss.contains("\$accent"), "media/style.scss must not reference \$accent")
        assertFalse(rawStyleScss.contains("\$primary"), "media/style.scss must not reference \$primary")
        val mediaCss = MediaExtension.defaultStyleSheet()

        val seekBar = SeekBar(value = 25.0, min = 0.0, max = 100.0, step = 1.0)
        assertIs<StyledSlider>(seekBar)
        assertIs<Composite>(seekBar)
        assertFalse((seekBar as Any) is AbstractContainer, "SeekBar must NOT be an AbstractContainer")
        assertTrue(seekBar.hasClass(SeekBar.SEEK_BAR_CLASS))
        assertTrue(seekBar.hasClass(StyledSlider.DEFAULT_CLASS_PREFIX))

        val children = seekBar.mediaCompositeChildren()
        assertEquals(4, children.size)
        val track = seekBar.mediaTrackButton()
        val progress = seekBar.mediaProgressSegment()
        val knob = seekBar.mediaKnobButton()
        val preload = seekBar.internalPreloadSegment()
        assertSame(track, children[0])
        assertSame(progress, children[1])
        assertSame(knob, children[2])
        assertSame(preload, children[3])
        assertSame(preload, children.last())
        assertTrue(preload.hasClass(SeekBar.PRELOAD_CLASS))

        // Zero static/state/theme inline style setters; only dynamic percentage width/left.
        assertTrue(seekBar.style.isEmpty())
        assertTrue(track.style.isEmpty())
        assertFalse(preload.style.hasProperty(CssProperty.Z_INDEX))
        assertFalse(preload.style.hasProperty(CssProperty.POINTER_EVENTS))
        assertFalse(preload.style.hasProperty(CssProperty.BACKGROUND_COLOR))
        assertEquals("0%", preload.style.getWidth()?.toCssString())
        assertEquals("25%", progress.style.getWidth()?.toCssString())
        assertEquals("25%", knob.style.getLeft()?.toCssString())

        seekBar.bufferedValue = 80.0
        assertEquals("80%", preload.style.getWidth()?.toCssString())

        seekBar.applyStyleSheet(mediaCss)

        val trackZ = assertNotNull(track.computedStyle?.zIndex)
        val preloadZ = assertNotNull(preload.computedStyle?.zIndex)
        val progressZ = assertNotNull(progress.computedStyle?.zIndex)
        val knobZ = assertNotNull(knob.computedStyle?.zIndex)
        assertEquals(1, trackZ)
        assertEquals(2, preloadZ)
        assertEquals(3, progressZ)
        assertEquals(4, knobZ)
        assertTrue(trackZ < preloadZ && preloadZ < progressZ && progressZ < knobZ)

        assertEquals(false, preload.computedStyle?.pointerEventsEnabled)
        assertEquals(false, preload.pointerEventsEnabled)
        assertEquals(false, progress.computedStyle?.pointerEventsEnabled)
        assertEquals(false, progress.pointerEventsEnabled)
        assertEquals(true, track.pointerEventsEnabled)
        assertEquals(true, knob.pointerEventsEnabled)

        // Simulate track geometry (200x24): progress covers 0..50px (25%), preload covers 0..160px (80%),
        // knob sits around 50px (44..56px). Clicking at x = 120px lies over preloadSegment (and right of progressSegment);
        // clicking at x = 20px lies inside BOTH progressSegment and preloadSegment. Pointer-events: none
        // pass-through resolves both clicks cleanly to trackButton.
        seekBar.setBounds(Rect(0, 0, 200, 24))
        track.setBounds(Rect(0, 10, 200, 4))
        preload.setBounds(Rect(0, 10, 160, 4))
        progress.setBounds(Rect(0, 10, 50, 4))
        knob.setBounds(Rect(44, 6, 12, 12))

        fun hitTestSeekBarComposite(x: Float, y: Float): AbstractComponent {
            val hit = children
                .filter { child ->
                    val b = child.getBounds()
                    x >= b.x && x <= (b.x + b.width) && y >= b.y && y <= (b.y + b.height)
                }
                .filter { it.pointerEventsEnabled }
                .maxByOrNull { it.computedStyle?.zIndex ?: 0 }
            return hit ?: seekBar
        }

        // Click over preloadSegment region (x = 120, y = 12) -> hits trackButton -> seeks to 60.0
        val hitOverPreload = hitTestSeekBarComposite(120f, 12f)
        assertSame(track, hitOverPreload)
        var lastCommit: Double? = null
        seekBar.onSeekCommit = { lastCommit = it }
        hitOverPreload.onPointerDown?.invoke(PointerEvent(x = 120f, y = 12f, screenX = 120f, screenY = 12f))
        assertTrue(seekBar.isScrubbing)
        assertEquals(60.0, seekBar.value, 0.001)
        hitOverPreload.onPointerUp?.invoke(PointerEvent(x = 120f, y = 12f, screenX = 120f, screenY = 12f))
        assertFalse(seekBar.isScrubbing)
        assertEquals(60.0, lastCommit)

        // Click over both progressSegment & preloadSegment region (x = 20, y = 12) -> hits trackButton -> seeks to 10.0
        val hitOverProgressAndPreload = hitTestSeekBarComposite(20f, 12f)
        assertSame(track, hitOverProgressAndPreload)
        hitOverProgressAndPreload.onPointerDown?.invoke(PointerEvent(x = 20f, y = 12f, screenX = 20f, screenY = 12f))
        assertTrue(seekBar.isScrubbing)
        assertEquals(10.0, seekBar.value, 0.001)
        hitOverProgressAndPreload.onPointerUp?.invoke(PointerEvent(x = 20f, y = 12f, screenX = 20f, screenY = 12f))
        assertFalse(seekBar.isScrubbing)
        assertEquals(10.0, lastCommit)

        seekBar.dispose()
    }

    @Test
    fun testExpandingVolumeSliderCompositeContainsStyledSliderTogglesMuteClassesAndTracksScrub() {
        val rawStyleScss = MediaExtension.rawDefaultStyleScss()
        assertTrue(rawStyleScss.isNotBlank())
        val mediaCss = MediaExtension.defaultStyleSheet()

        val vol = ExpandingVolumeSlider(value = 80.0, muted = false)
        assertIs<Composite>(vol)
        assertFalse((vol as Any) is AbstractContainer, "ExpandingVolumeSlider must NOT extend AbstractContainer")
        assertTrue(vol.hasClass(ExpandingVolumeSlider.DEFAULT_CLASS_PREFIX))

        val internalChildren = vol.mediaCompositeChildren()
        val muteBtn = internalChildren.filterIsInstance<StyledButton>().single()
        val innerSlider = internalChildren.filterIsInstance<StyledSlider>().single()
        val iconStack = internalChildren.filterIsInstance<StyledContainer>().single()
        assertFalse((innerSlider as Any) is SeekBar, "ExpandingVolumeSlider must compose StyledSlider, not SeekBar")
        assertTrue(muteBtn.hasClass(ExpandingVolumeSlider.MUTE_BUTTON_CLASS))
        assertTrue(innerSlider.hasClass(ExpandingVolumeSlider.SLIDER_CONTROL_CLASS))
        assertTrue(iconStack.hasClass(ExpandingVolumeSlider.ICON_STACK_CLASS))

        // Zero static/state/theme inline style setters on Composite, mute button, icon stack, and icon layers
        assertTrue(vol.style.isEmpty())
        assertTrue(muteBtn.style.isEmpty())
        assertTrue(iconStack.style.isEmpty())
        assertTrue(vol.internalIconLayers().all { it.style.isEmpty() })
        assertTrue(innerSlider.style.isEmpty())

        vol.applyStyleSheet(mediaCss)

        val mutedCallbacks = mutableListOf<Boolean>()
        val volumeCallbacks = mutableListOf<Float>()
        vol.onMutedChanged = { mutedCallbacks.add(it) }
        vol.onVolumeChanged = { volumeCallbacks.add(it) }

        assertTrue(vol.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_HIGH))
        assertFalse(vol.hasClass(ExpandingVolumeSlider.CLASS_MUTED))

        vol.toggleMute()
        assertTrue(vol.isMuted)
        assertTrue(vol.hasClass(ExpandingVolumeSlider.CLASS_MUTED))
        assertFalse(vol.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_HIGH))
        assertEquals(listOf(true), mutedCallbacks)
        assertEquals(0f, vol.volume, 0.001f)

        vol.toggleMute()
        assertFalse(vol.isMuted)
        assertTrue(vol.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_HIGH))
        assertEquals(listOf(true, false), mutedCallbacks)
        assertEquals(0.8f, vol.volume, 0.001f)

        // Volume tiers toggle semantic classes deterministically
        vol.updateVolumeState(45.0, muted = false)
        assertTrue(vol.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_MEDIUM))
        vol.updateVolumeState(15.0, muted = false)
        assertTrue(vol.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_LOW))
        vol.updateVolumeState(0.0, muted = false)
        assertTrue(vol.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_ZERO))

        // Pure CSS :hover & scrubbing state management via StyledSlider (zero Kotlin pointer enter/leave watchers)
        assertNull(vol.onPointerEnter)
        assertNull(vol.onPointerLeave)
        assertFalse(vol.isScrubbing)
        assertFalse(vol.isExpanded)
        assertEquals("0px", innerSlider.computedStyle?.width)

        vol.dispatchPointerEnter(PointerEvent())
        assertTrue(vol.isExpanded)
        assertFalse(vol.hasClass(ExpandingVolumeSlider.EXPANDED_CLASS))
        assertEquals("92px", innerSlider.computedStyle?.width)
        vol.dispatchPointerLeave(PointerEvent())
        assertFalse(vol.isExpanded)
        assertEquals("0px", innerSlider.computedStyle?.width)

        innerSlider.internalTrackButton().setBounds(Rect(0, 0, 92, 24))
        vol.beginScrub()
        assertTrue(vol.isScrubbing)
        assertTrue(vol.isExpanded)
        assertTrue(vol.hasClass(ExpandingVolumeSlider.SCRUBBING_CLASS))
        assertEquals("92px", innerSlider.computedStyle?.width)

        innerSlider.internalTrackButton().onPointerDown?.invoke(PointerEvent(x = 46f, screenX = 46f))
        vol.endScrub()
        assertFalse(vol.isScrubbing)
        assertFalse(vol.hasClass(ExpandingVolumeSlider.SCRUBBING_CLASS))
        assertTrue(volumeCallbacks.isNotEmpty())

        vol.dispose()
    }
}
