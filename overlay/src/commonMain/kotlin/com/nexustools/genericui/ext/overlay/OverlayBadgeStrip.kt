package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.SpacingScale

/**
 * Horizontal badge bar rendering a list of [OverlayBadgeSpec]s (for example `state.topBadges` in
 * [OverlaySlot.TOP_BAR] or `state.belowHostBadges` in [OverlaySlot.BELOW_HOST]) and routing clicks
 * to [OverlayActions.onBadgeClick] and [OverlayBadgeSpec.onClick].
 */
open class OverlayBadgeStrip(
    override val elementId: String = "badge-strip",
    override val slot: OverlaySlot = OverlaySlot.TOP_BAR,
    outlinedStyle: Boolean = (slot == OverlaySlot.TOP_BAR),
    themeColorHex: String = OverlayExtension.effectiveDefaultThemeColorHex(),
    var autoSyncFromState: Boolean = true
) : HStack(gap = SpacingScale.S1), OverlayElement {

    private var disposedState: Boolean = false
    private val renderedBadges = mutableListOf<OverlayBadge>()
    private var currentSpecs: List<OverlayBadgeSpec> = emptyList()
    private var currentActions: OverlayActions = OverlayActions()

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    var outlinedStyle: Boolean = outlinedStyle
        set(value) {
            field = value
            rebuildBadges()
        }

    var themeColorHex: String = themeColorHex
        set(value) {
            field = value
            for (badge in renderedBadges) {
                badge.themeColorHex = value
            }
        }

    val badges: List<OverlayBadge>
        get() = renderedBadges.toList()

    val specs: List<OverlayBadgeSpec>
        get() = currentSpecs.toList()

    init {
        htmlTag = "genericui:overlay-badge-strip"
        addClass("genericui-overlay-badge-strip")
        setAlignItems(AlignItems.CENTER)
        isVisible = false
    }

    fun setBadges(specs: List<OverlayBadgeSpec>, actions: OverlayActions = currentActions) {
        currentSpecs = specs
        currentActions = actions
        rebuildBadges()
    }

    private fun rebuildBadges() {
        for (existing in renderedBadges) {
            removeChild(existing)
            existing.dispose()
        }
        renderedBadges.clear()

        val visibleSpecs = currentSpecs.filter { it.visible }
        isVisible = visibleSpecs.isNotEmpty()
        for (spec in currentSpecs) {
            val badge = OverlayBadge(
                spec = spec,
                outlinedStyle = outlinedStyle || spec.variant == OverlayBadgeVariant.OUTLINED,
                themeColorHex = themeColorHex,
                elementId = "$elementId-badge-${spec.id}",
                slot = slot
            )
            badge.onStateChanged(OverlayState(), currentActions)
            renderedBadges.add(badge)
            addChild(badge)
        }
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        currentActions = actions
        if (autoSyncFromState) {
            val targetSpecs = when (slot) {
                OverlaySlot.BELOW_HOST -> state.belowHostBadges
                else -> state.topBadges
            }
            if (targetSpecs != currentSpecs) {
                currentSpecs = targetSpecs
                rebuildBadges()
            } else {
                for (badge in renderedBadges) {
                    badge.onStateChanged(state, actions)
                }
            }
        } else {
            for (badge in renderedBadges) {
                badge.onStateChanged(state, actions)
            }
        }
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        renderedBadges.clear()
        super<HStack>.dispose()
    }
}
