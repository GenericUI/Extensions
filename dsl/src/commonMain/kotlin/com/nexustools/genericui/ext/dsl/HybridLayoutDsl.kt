package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.layout.AdaptiveNavigationScaffold
import com.nexustools.genericui.component.layout.BentoGridItemSpec
import com.nexustools.genericui.component.layout.CollapsibleBottomDockLayout
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HybridColumnLayout
import com.nexustools.genericui.component.layout.HybridColumnSpec
import com.nexustools.genericui.component.layout.HybridFoldMode
import com.nexustools.genericui.component.layout.HybridFullscreenPolicy
import com.nexustools.genericui.component.layout.HybridSplitNavigationLayout
import com.nexustools.genericui.component.layout.HybridTabPlacement
import com.nexustools.genericui.component.layout.ResponsiveBentoGridLayout
import com.nexustools.genericui.component.layout.ResponsiveMasterDetailLayout
import com.nexustools.genericui.core.AbstractContainer

/**
 * In-place [HybridColumnLayout] builder inside any [AbstractContainer].
 */
inline fun AbstractContainer.hybridColumnLayout(
    foldMode: HybridFoldMode = HybridFoldMode.PIN_FIRST_COLUMN_TOP,
    tabPlacement: HybridTabPlacement = HybridTabPlacement.TOP_TABS,
    fullscreenPolicy: HybridFullscreenPolicy = HybridFullscreenPolicy.DISABLED,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker HybridColumnLayout).() -> Unit = {}
): HybridColumnLayout = mountDslComponent(
    this,
    HybridColumnLayout(
        foldMode = foldMode,
        tabPlacement = tabPlacement,
        fullscreenPolicy = fullscreenPolicy
    ),
    classes,
    id,
    block
)

/**
 * Adds a column [Container] inside this [HybridColumnLayout] using declarative DSL syntax.
 */
inline fun HybridColumnLayout.column(
    id: String,
    title: String = id,
    weight: Float = 1.0f,
    colSm: Int? = null,
    colMd: Int? = null,
    colLg: Int? = null,
    colXl: Int? = null,
    icon: String? = null,
    compactHeightPx: Int? = null,
    compactAspectRatio: Float? = null,
    scrollableInColumnMode: Boolean = false,
    scrollableInSingleMode: Boolean = false,
    classes: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container {
    val colContainer = Container("div", "gui-hybrid-column")
    if (!classes.isNullOrBlank()) {
        colContainer.addClass(classes)
    }
    colContainer.block()
    addColumn(
        spec = HybridColumnSpec(
            id = id,
            title = title,
            icon = icon,
            weight = weight,
            colSm = colSm,
            colMd = colMd,
            colLg = colLg,
            colXl = colXl,
            compactHeightPx = compactHeightPx,
            compactAspectRatio = compactAspectRatio,
            scrollableInColumnMode = scrollableInColumnMode,
            scrollableInSingleMode = scrollableInSingleMode
        ),
        component = colContainer
    )
    return colContainer
}

/**
 * In-place [HybridSplitNavigationLayout] builder inside any [AbstractContainer].
 */
inline fun AbstractContainer.hybridSplitNavigationLayout(
    inColumnMode: Boolean = false,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker HybridSplitNavigationLayout).() -> Unit = {}
): HybridSplitNavigationLayout = mountDslComponent(
    this,
    HybridSplitNavigationLayout(inColumnMode = inColumnMode),
    classes,
    id,
    block
)

/**
 * Registers a navigable section [Container] inside this [HybridSplitNavigationLayout].
 */
inline fun HybridSplitNavigationLayout.section(
    id: String,
    title: String,
    icon: String? = null,
    badge: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container {
    val sectionContainer = Container("div", "gui-split-nav-section")
    if (!classes.isNullOrBlank()) {
        sectionContainer.addClass(classes)
    }
    sectionContainer.block()
    addSection(id = id, title = title, icon = icon, badge = badge, content = sectionContainer)
    return sectionContainer
}

/**
 * In-place [CollapsibleBottomDockLayout] builder inside any [AbstractContainer].
 */
inline fun AbstractContainer.collapsibleBottomDockLayout(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker CollapsibleBottomDockLayout).() -> Unit = {}
): CollapsibleBottomDockLayout = mountDslComponent(
    this,
    CollapsibleBottomDockLayout(),
    classes,
    id,
    block
)

/**
 * Configures and mounts the primary content [Container] inside this [CollapsibleBottomDockLayout].
 */
inline fun CollapsibleBottomDockLayout.mainContent(
    classes: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container {
    val contentContainer = Container("div", "gui-bottom-dock-main")
    if (!classes.isNullOrBlank()) {
        contentContainer.addClass(classes)
    }
    contentContainer.block()
    setMainContent(contentContainer)
    return contentContainer
}

/**
 * Configures and mounts the persistent bottom dock bar [Container] inside this [CollapsibleBottomDockLayout].
 */
inline fun CollapsibleBottomDockLayout.dockContent(
    classes: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container {
    val dockContainer = Container("div", "gui-bottom-dock-content")
    if (!classes.isNullOrBlank()) {
        dockContainer.addClass(classes)
    }
    dockContainer.block()
    setDockContent(dockContainer, getExpandedSheetContent())
    return dockContainer
}

/**
 * In-place [ResponsiveMasterDetailLayout] builder inside any [AbstractContainer].
 */
inline fun AbstractContainer.responsiveMasterDetailLayout(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker ResponsiveMasterDetailLayout).() -> Unit = {}
): ResponsiveMasterDetailLayout = mountDslComponent(
    this,
    ResponsiveMasterDetailLayout(),
    classes,
    id,
    block
)

/**
 * Configures and mounts the Master pane [Container] inside this [ResponsiveMasterDetailLayout].
 */
inline fun ResponsiveMasterDetailLayout.masterPane(
    widthPx: Int = 320,
    colMd: Int = 4,
    classes: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container {
    val pane = Container("div", "gui-master-pane-content")
    if (!classes.isNullOrBlank()) {
        pane.addClass(classes)
    }
    pane.block()
    setMasterPane(pane, widthPx = widthPx, colMd = colMd)
    return pane
}

/**
 * Configures and mounts the Detail pane [Container] inside this [ResponsiveMasterDetailLayout].
 */
inline fun ResponsiveMasterDetailLayout.detailPane(
    colMd: Int = 8,
    classes: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container {
    val pane = Container("div", "gui-detail-pane-content")
    if (!classes.isNullOrBlank()) {
        pane.addClass(classes)
    }
    pane.block()
    setDetailPane(pane, colMd = colMd)
    return pane
}

/**
 * Configures and mounts the optional 3rd Inspector pane [Container] inside this [ResponsiveMasterDetailLayout].
 */
inline fun ResponsiveMasterDetailLayout.inspectorPane(
    widthPx: Int = 300,
    colXl: Int = 3,
    classes: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container {
    val pane = Container("div", "gui-inspector-pane-content")
    if (!classes.isNullOrBlank()) {
        pane.addClass(classes)
    }
    pane.block()
    setInspectorPane(pane, widthPx = widthPx, colXl = colXl)
    return pane
}

/**
 * In-place [AdaptiveNavigationScaffold] builder inside any [AbstractContainer].
 */
inline fun AbstractContainer.adaptiveNavigationScaffold(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker AdaptiveNavigationScaffold).() -> Unit = {}
): AdaptiveNavigationScaffold = mountDslComponent(
    this,
    AdaptiveNavigationScaffold(),
    classes,
    id,
    block
)

/**
 * Registers a destination [Container] inside this [AdaptiveNavigationScaffold].
 */
inline fun AdaptiveNavigationScaffold.destination(
    id: String,
    label: String,
    icon: String? = null,
    badge: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container {
    val destContainer = Container("div", "gui-scaffold-destination")
    if (!classes.isNullOrBlank()) {
        destContainer.addClass(classes)
    }
    destContainer.block()
    addDestination(id = id, label = label, icon = icon, badge = badge, content = destContainer)
    return destContainer
}

/**
 * In-place [ResponsiveBentoGridLayout] builder inside any [AbstractContainer].
 */
inline fun AbstractContainer.responsiveBentoGridLayout(
    gapPx: Int = 16,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker ResponsiveBentoGridLayout).() -> Unit = {}
): ResponsiveBentoGridLayout = mountDslComponent(
    this,
    ResponsiveBentoGridLayout(gapPx = gapPx),
    classes,
    id,
    block
)

/**
 * Adds a card [Container] to this [ResponsiveBentoGridLayout] using [spec].
 */
inline fun ResponsiveBentoGridLayout.bentoCard(
    spec: BentoGridItemSpec,
    classes: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container {
    val cardContainer = Container("div", "gui-bento-card-content")
    if (!classes.isNullOrBlank()) {
        cardContainer.addClass(classes)
    }
    cardContainer.block()
    addCard(spec, cardContainer)
    return cardContainer
}

/**
 * Adds a card [Container] to this [ResponsiveBentoGridLayout] with responsive Bootstrap 12-grid column spans.
 */
inline fun ResponsiveBentoGridLayout.bentoCard(
    id: String,
    title: String = "",
    colXs: Int = 12,
    colSm: Int = 12,
    colMd: Int = 6,
    colLg: Int = 4,
    colXl: Int = 3,
    rowSpan: Int = 1,
    minHeightPx: Int? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Container).() -> Unit = {}
): Container = bentoCard(
    spec = BentoGridItemSpec(
        id = id,
        title = title,
        colXs = colXs,
        colSm = colSm,
        colMd = colMd,
        colLg = colLg,
        colXl = colXl,
        rowSpan = rowSpan,
        minHeightPx = minHeightPx
    ),
    classes = classes,
    block = block
)
