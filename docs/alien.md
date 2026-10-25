# `:extensions:alien` — Self-Rendering Bootstrap 5.3 Canvas2D UI & Hybrid Native Compositor

> **Related Documentation**:
> - [Extensions Master Index](../README.md)
> - [`:extensions:canvas2d` & `:extensions:canvas2d:dsl`](canvas2d.md)
> - [`:extensions:imageprocessing`](imageprocessing.md)
> - [`:extensions:dsl`](dsl.md)
> - [Root GenericUI Overview](../../README.md)

---

## 1. Overview

`:extensions:alien` provides a self-rendering Bootstrap 5.3 component engine (`AlienFactory`, `AlienContainer`, `AlienFragment`, `UnalienContainer`, and `*AlienImpl` implementations for every component in `:common`) powered by `:extensions:canvas2d` (`Canvas2D` backed by `:extensions:imageprocessing`'s `ImageBuffer`), an `AlienRepaintManager` with dirty update region tracking (`AlienUpdateRegionTracker`), and a full CSS/SASS style & transition engine (`AlienCssStyleResolver` + `AlienTransitionAnimator`).

- **Browser-Identical Bootstrap 5.3 Default Rendering + Full CSS Theming & Transitions**:
  - By default, Alien components render identically to how Bootstrap 5.3 renders in a browser (`#0d6efd` primary, `#6c757d` secondary, `.375rem` radii, `.25rem` `rgba(..., 0.25)` focus rings, `.15s ease-in-out` hover/focus/active transitions).
  - Supports full CSS/SASS theming (`--bs-*` CSS custom variables, `data-bs-theme="dark"`, `CssStyleSheet`, `PrecompiledStyleSheet`, `ComputedStyle` gradients, multi-layer `box-shadow`, 2D `transform`, and cubic-Bezier transitions).
- **Web Target Pass-Through (`:extensions:alien:web`)**:
  - On the Web target (`js` and `wasmJs`), `AlienContainer`, `AlienFragment`, and `UnalienContainer` do nothing extra and forward directly to the parent container / `WebNativeFactory`, using standard browser DOM `Web*Impl` native implementations with zero `Canvas2D` overhead.
- **Mixing Native & Extension Components (`UnalienContainer` + Underlay Hole Cutting & Mouse Passthrough)**:
  - **`UnalienContainer`** acts as a native container inside an `AlienContainer` or `AlienFragment`, restoring the host OS `NativeFactory` so native OS `:common` components can be mixed into an Alien interface.
  - **Extension-provided components** (`GLCanvas`, `VKCanvas`, `VideoPlayer`, `AudioAnalyzer`, `WebView`, etc.) and **`UnalienContainer`** are attached to the `AlienContainer` / `AlienFragment` **below** the `Canvas2D`, and the `Canvas2D` either cuts a transparent hole (`AlienExtensionLayerMode.CUT_TRANSPARENT_HOLE`) with mouse/pointer/wheel passthrough or draws higher-Z Alien components on top of them.

---

## 2. Gradle Configuration

Enable `alien()` inside the `genericui { extensions { ... } }` block in `build.gradle.kts` (which automatically enables `canvas2d`, `imageProcessing`, and `gpu`, plus `:extensions:alien:dsl` when `dsl()` is enabled):

```kotlin
genericui {
    backends {
        allExceptSwing()
    }
    extensions {
        dsl()
        alien()
    }
}
```

---

## 3. Core Classes & `*AlienImpl` Component Set

| Class | Package | Description |
| :--- | :--- | :--- |
| [`AlienFactory`](../alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/AlienFactory.kt) | `com.nexustools.genericui.ext.alien` | Implements `NativeFactory`, returning `*AlienImpl` instances for all `:common` components while delegating OS windows, file dialogs, and system services to `hostFactory`. |
| [`AlienContainer`](../alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/AlienContainer.kt) | `com.nexustools.genericui.ext.alien` | Self-rendering `Container` overriding `resolveChildFactory()` to provide `AlienFactory` on System targets and forwarding to the parent DOM container on `:web`. |
| [`AlienFragment`](../alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/AlienFragment.kt) | `com.nexustools.genericui.ext.alien` | Self-rendering `Fragment` overriding `resolveChildFactory()` to provide `AlienFactory` on System targets and forwarding to the parent DOM container on `:web`. |
| [`UnalienContainer`](../alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/UnalienContainer.kt) | `com.nexustools.genericui.ext.alien` | Native container inside an `AlienContainer` / `AlienFragment` that unwraps `AlienFactory.hostFactory` so native OS `:common` components can be mixed in. |
| [`AlienRepaintManager`](../alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/AlienRepaintManager.kt) | `com.nexustools.genericui.ext.alien` | Coordinates layout passes, CSS transition ticks, transparent hole cutting for underlay slots, and partial dirty-region repaints on `Canvas2D`. |
| [`AlienUpdateRegionTracker`](../alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/AlienUpdateRegionTracker.kt) | `com.nexustools.genericui.ext.alien` | Coalesces overlapping/adjacent dirty `Rect` regions and clips them to the viewport. |
| [`AlienImpl`](../alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/impl/AlienImpl.kt) & `*AlienImpl` | `com.nexustools.genericui.ext.alien.impl` | Self-rendered implementations of all `:common` components (`ButtonAlienImpl`, `ContainerAlienImpl`, `DropdownAlienImpl`, `TextInputAlienImpl`, `SelectAlienImpl`, `CheckboxAlienImpl`, `RangeSliderAlienImpl`, `TextAlienImpl`, `MarkdownViewAlienImpl`, `ImageAlienImpl`, `TableAlienImpl`, `ScrollViewAlienImpl`, `DividerAlienImpl`, `BookContainerAlienImpl`, `CollapseAlienImpl`, `TabAlienImpl`, `TabContainerAlienImpl`, `NavbarAlienImpl`, `BottomNavigationBarAlienImpl`, `ProgressBarAlienImpl`, `SpinnerAlienImpl`, `PopoverAlienImpl`, `SplitPaneAlienImpl`, `MenuBarAlienImpl`, `MenuAlienImpl`, `MenuItemAlienImpl`, `ContextMenuAlienImpl`, `DialogWindowAlienImpl`, `DockWorkspaceAlienImpl`, `DockAreaAlienImpl`, `DockGroupAlienImpl`, `DockPanelAlienImpl`, `DockWindowAlienImpl`, `ToolPaletteAlienImpl`, `LazyListAlienImpl`, `LazyGridAlienImpl`, `TreeViewAlienImpl`). |

---

## 4. Usage Example (Declarative DSL + `UnalienContainer`)

```kotlin
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.ext.alien.AlienFragment
import com.nexustools.genericui.ext.alien.dsl.alienContainer
import com.nexustools.genericui.ext.alien.dsl.unalienContainer
import com.nexustools.genericui.ext.dsl.*
import com.nexustools.genericui.window.FragmentBundle

class StudioAlienFragment : AlienFragment() {
    override fun onCreate(savedState: FragmentBundle?) {
        alienContainer(classes = "p-4") {
            h2("Custom Alien Interface")
            button("Bootstrap 5.3 Alien Button", type = BootstrapType.PRIMARY)

            // Mix in genuine OS-native components via unalienContainer
            unalienContainer(classes = "mt-3 p-2 border rounded") {
                button("Host OS Native Button", type = BootstrapType.SECONDARY)
            }
        }
    }
}
```
