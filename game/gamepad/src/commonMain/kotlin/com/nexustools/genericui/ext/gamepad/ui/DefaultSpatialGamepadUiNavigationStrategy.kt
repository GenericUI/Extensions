package com.nexustools.genericui.ext.gamepad.ui

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.DropdownButton
import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.RadioButton
import com.nexustools.genericui.component.form.RadioGroup
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.shell.MenuItem
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.tabs.Tab
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.event.InputFocusManager
import com.nexustools.genericui.ext.gamepad.GamepadAxis2D
import com.nexustools.genericui.ext.gamepad.GamepadFrameSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Default zero-configuration spatial & tree-order [GamepadUiNavigationStrategy] working directly
 * on any GenericUI component hierarchy (`Window`, `DialogWindow`, `AbstractContainer`).
 */
open class DefaultSpatialGamepadUiNavigationStrategy(
    var scrollSpeedPxPerSecond: Float = 520f,
    var onUnhandledCancel: (() -> Unit)? = null
) : GamepadUiNavigationStrategy {
    private val trackedScrollOffsets = mutableMapOf<ScrollView, FloatArray>()

    fun trackedScrollOffset(scrollView: ScrollView): GamepadAxis2D {
        val arr = trackedScrollOffsets[scrollView] ?: return GamepadAxis2D.ZERO
        return GamepadAxis2D(arr[0], arr[1])
    }

    open fun resolveActiveModalScope(root: AbstractContainer): AbstractContainer {
        if (root is Window) {
            val topModal = root.attachedDialogWindows.lastOrNull {
                it.isVisible && it.isWindowShown && it.isModal
            }
            if (topModal != null) return topModal
        }
        return root
    }

    protected open fun isFocusableCandidate(component: AbstractComponent): Boolean {
        if (!component.isVisible || !component.isEnabled || component.isDisposed) return false
        val optAttr = component.getAttribute("data-gamepad-focusable")
        if (optAttr == "false") return false
        if (optAttr == "true") return true
        if (component is Button) return true
        if (component is CheckBox) return true
        if (component is TextField) return true
        if (component is Select || component is DropdownButton) return true
        if (component is Slider) return true
        if (component is Tab || component is MenuItem) return true
        if (component.onClick != null) return true
        return false
    }

    override fun discoverFocusableComponents(root: AbstractContainer): List<AbstractComponent> {
        val activeRoot = resolveActiveModalScope(root)
        val discovered = mutableListOf<AbstractComponent>()
        collectRecursive(activeRoot, discovered)
        return discovered
    }

    private fun collectRecursive(
        node: AbstractComponent,
        out: MutableList<AbstractComponent>
    ) {
        if (!node.isVisible || !node.isEnabled || node.isDisposed) return
        val focusable = isFocusableCandidate(node)
        if (focusable) {
            out.add(node)
        }
        if (node is DropdownButton || node is Tab || node is Select || node is Slider ||
            node is CheckBox || node is TextField || node is Button
        ) {
            return
        }
        if (node is AbstractContainer) {
            for (child in node.children) {
                collectRecursive(child, out)
            }
        }
    }

    override fun resolveInitialFocus(
        root: AbstractContainer,
        candidates: List<AbstractComponent>
    ): AbstractComponent? {
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.isFocused } ?: candidates.first()
    }

    override fun resolveNextFocus(
        current: AbstractComponent?,
        direction: GamepadNavDirection,
        candidates: List<AbstractComponent>
    ): AbstractComponent? {
        if (candidates.isEmpty()) return null
        if (current == null || current !in candidates) {
            return candidates.first()
        }

        val currentBounds = current.getBounds()
        val hasMeasuredGeometry = currentBounds.width > 0 && currentBounds.height > 0 &&
            candidates.count { cand ->
                cand !== current && cand.getBounds().let { b ->
                    b.width > 0 && b.height > 0 && (b.x != currentBounds.x || b.y != currentBounds.y)
                }
            } > 0

        if (hasMeasuredGeometry) {
            val curCx = currentBounds.x + currentBounds.width * 0.5f
            val curCy = currentBounds.y + currentBounds.height * 0.5f
            var bestConeCandidate: AbstractComponent? = null
            var bestConeScore = Float.MAX_VALUE
            var bestHalfPlaneCandidate: AbstractComponent? = null
            var bestHalfPlaneScore = Float.MAX_VALUE

            for (cand in candidates) {
                if (cand === current) continue
                val b = cand.getBounds()
                if (b.width <= 0 || b.height <= 0) continue
                val candCx = b.x + b.width * 0.5f
                val candCy = b.y + b.height * 0.5f
                val dx = candCx - curCx
                val dy = candCy - curCy

                val primary: Float
                val perp: Float
                when (direction) {
                    GamepadNavDirection.RIGHT -> {
                        if (dx <= 1f) continue
                        primary = dx
                        perp = abs(dy)
                    }
                    GamepadNavDirection.LEFT -> {
                        if (dx >= -1f) continue
                        primary = -dx
                        perp = abs(dy)
                    }
                    GamepadNavDirection.DOWN -> {
                        if (dy <= 1f) continue
                        primary = dy
                        perp = abs(dx)
                    }
                    GamepadNavDirection.UP -> {
                        if (dy >= -1f) continue
                        primary = -dy
                        perp = abs(dx)
                    }
                }

                if (perp <= primary * 1.4f + 16f) {
                    val score = primary + perp * 2.2f
                    if (score < bestConeScore) {
                        bestConeScore = score
                        bestConeCandidate = cand
                    }
                } else {
                    val score = primary + perp * 3.5f
                    if (score < bestHalfPlaneScore) {
                        bestHalfPlaneScore = score
                        bestHalfPlaneCandidate = cand
                    }
                }
            }
            return bestConeCandidate ?: bestHalfPlaneCandidate
        }

        val currentIdx = candidates.indexOf(current)
        if (currentIdx < 0) return candidates.first()
        return when (direction) {
            GamepadNavDirection.DOWN, GamepadNavDirection.RIGHT ->
                candidates[(currentIdx + 1) % candidates.size]
            GamepadNavDirection.UP, GamepadNavDirection.LEFT ->
                candidates[(currentIdx - 1 + candidates.size) % candidates.size]
        }
    }

    override fun onFocusChanged(
        previous: AbstractComponent?,
        current: AbstractComponent?,
        root: AbstractContainer
    ) {
        if (previous != null && previous !== current) {
            previous.isFocused = false
            previous.isHovered = false
            InputFocusManager.onComponentFocusChanged(previous, false)
            if (previous.isTextInputComponent) {
                InputFocusManager.setEditableInputFocused(false)
            }
        }
        if (current != null) {
            current.isFocused = true
            current.isHovered = true
            InputFocusManager.onComponentFocusChanged(current, true)
            if (current.isTextInputComponent) {
                InputFocusManager.setEditableInputFocused(true)
            }
        }
    }

    override fun onActivate(
        focused: AbstractComponent,
        root: AbstractContainer,
        frame: GamepadFrameSnapshot
    ): Boolean {
        if (!focused.isEnabled || !focused.isVisible) return false
        when (focused) {
            is RadioButton -> {
                val group = findAncestorOfType<RadioGroup>(focused)
                if (group != null) {
                    group.selectedValue = focused.optionValue
                } else {
                    focused.isChecked = true
                    focused.onCheckedChanged?.invoke(true)
                }
                return true
            }
            is CheckBox -> {
                val updated = !focused.isChecked
                focused.isChecked = updated
                focused.onCheckedChanged?.invoke(updated)
                return true
            }
            is Select -> {
                val selectable = focused.options.filter { !it.disabled }
                if (selectable.isEmpty()) return false
                val curIdx = selectable.indexOfFirst { it.value == focused.selectedValue }
                val next = selectable[(curIdx + 1) % selectable.size]
                focused.selectedValue = next.value
                focused.onSelectionChanged?.invoke(next.value)
                return true
            }
            is DropdownButton -> {
                val entries = focused.items
                if (entries.isNotEmpty()) {
                    val prevIdx = focused.getAttribute("data-gamepad-dropdown-index")?.toIntOrNull() ?: -1
                    val nextIdx = (prevIdx + 1) % entries.size
                    val nextItem = entries[nextIdx]
                    focused.setAttribute("data-gamepad-dropdown-index", nextIdx.toString())
                    focused.onItemSelected?.invoke(nextItem.first)
                }
                if (focused.onClick != null) {
                    focused.triggerClick()
                }
                return entries.isNotEmpty() || focused.onClick != null
            }
            is Tab -> {
                val tabContainer = findAncestorOfType<TabContainer>(focused)
                if (tabContainer != null && focused.isSelectable) {
                    val idx = tabContainer.entries.indexOfFirst { it.tab === focused }
                    if (idx >= 0) {
                        tabContainer.selectedIndex = idx
                    }
                }
                focused.triggerClick()
                return true
            }
            else -> {
                focused.triggerClick()
                return focused.onClick != null
            }
        }
    }

    override fun onCancel(
        focused: AbstractComponent?,
        root: AbstractContainer,
        frame: GamepadFrameSnapshot
    ): Boolean {
        if (root is Window) {
            val topDialog = root.attachedDialogWindows.lastOrNull { it.isVisible && it.isWindowShown }
            if (topDialog != null) {
                topDialog.close()
                return true
            }
        } else if (root is DialogWindow && root.isWindowShown) {
            root.close()
            return true
        }
        if (focused?.isTextInputComponent == true || InputFocusManager.isTypingInTextInput) {
            focused?.isFocused = false
            InputFocusManager.setEditableInputFocused(false)
            return true
        }
        val handler = onUnhandledCancel
        if (handler != null) {
            handler.invoke()
            return true
        }
        return false
    }

    override fun onTabCycle(
        root: AbstractContainer,
        delta: Int,
        frame: GamepadFrameSnapshot
    ): Boolean {
        val activeScope = resolveActiveModalScope(root)
        val tabContainer = findFirstVisibleTabContainer(activeScope) ?: return false
        val selectableIndices = tabContainer.entries.indices.filter {
            val tab = tabContainer.entries[it].tab
            tab.isSelectable && tab.isEnabled && tab.isVisible
        }
        if (selectableIndices.isEmpty()) return false
        val curPos = selectableIndices.indexOf(tabContainer.selectedIndex).coerceAtLeast(0)
        val step = if (delta >= 0) 1 else -1
        val nextPos = (curPos + step + selectableIndices.size) % selectableIndices.size
        tabContainer.selectedIndex = selectableIndices[nextPos]
        return true
    }

    override fun onContinuousInput(
        focused: AbstractComponent?,
        root: AbstractContainer,
        frame: GamepadFrameSnapshot,
        deltaSeconds: Float
    ): Boolean {
        val rx = frame.rightStick.x
        val ry = frame.rightStick.y
        if (abs(rx) <= 0.08f && abs(ry) <= 0.08f) return false

        val activeScope = resolveActiveModalScope(root)
        val scrollView = (if (focused != null) findAncestorOfType<ScrollView>(focused) else null)
            ?: findFirstVisibleScrollView(activeScope)
            ?: return false

        val dt = deltaSeconds.coerceIn(0f, 0.25f)
        val offset = trackedScrollOffsets.getOrPut(scrollView) { floatArrayOf(0f, 0f) }
        offset[0] = (offset[0] + rx * scrollSpeedPxPerSecond * dt).coerceAtLeast(0f)
        offset[1] = (offset[1] - ry * scrollSpeedPxPerSecond * dt).coerceAtLeast(0f)
        val sx = offset[0].roundToInt()
        val sy = offset[1].roundToInt()
        scrollView.setAttribute("data-gamepad-scroll-x", sx.toString())
        scrollView.setAttribute("data-gamepad-scroll-y", sy.toString())
        scrollView.scrollTo(sx, sy)
        return true
    }

    private inline fun <reified T : AbstractComponent> findAncestorOfType(node: AbstractComponent): T? {
        var cur: AbstractComponent? = node.parent
        while (cur != null) {
            if (cur is T) return cur
            cur = cur.parent
        }
        return null
    }

    private fun findFirstVisibleTabContainer(node: AbstractComponent): TabContainer? {
        if (!node.isVisible || !node.isEnabled || node.isDisposed) return null
        if (node is TabContainer) return node
        if (node is AbstractContainer) {
            for (child in node.children) {
                val found = findFirstVisibleTabContainer(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findFirstVisibleScrollView(node: AbstractComponent): ScrollView? {
        if (!node.isVisible || !node.isEnabled || node.isDisposed) return null
        if (node is ScrollView) return node
        if (node is AbstractContainer) {
            for (child in node.children) {
                val found = findFirstVisibleScrollView(child)
                if (found != null) return found
            }
        }
        return null
    }
}
