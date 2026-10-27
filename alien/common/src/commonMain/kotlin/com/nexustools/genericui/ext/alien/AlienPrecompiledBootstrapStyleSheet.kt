package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry
import com.nexustools.genericui.style.mapper.BootstrapClassMapper
import com.nexustools.genericui.style.sass.SassCompiler

/**
 * Precompiled Bootstrap 5.3 stylesheet ([PrecompiledStyleSheet]) for `:extensions:alien`.
 *
 * Synchronized with `src/commonMain/resources/styles/bootstrap.scss` via the `:extensions:alien:common`
 * `syncPrecompileAlienBootstrapScss` Gradle task so updates to `bootstrap.scss` automatically propagate
 * to Alien's precompiled Bootstrap rule set.
 */
object AlienPrecompiledBootstrapStyleSheet : PrecompiledStyleSheet {

    val RESOURCE_ID: ResourceID = IAlien.BOOTSTRAP_STYLE_SHEET_ID

    val BOOTSTRAP_STYLE_SHEET_ID: ResourceID = IAlien.BOOTSTRAP_STYLE_SHEET_ID

    override val name: String = "alien-bootstrap"

    override val cssResourcePath: String = "styles/bootstrap.scss"

    // BEGIN_PRECOMPILED_BOOTSTRAP_SCSS
    const val BOOTSTRAP_SCSS_SOURCE: String = """
// Bootstrap 5.3 Core Stylesheet for :extensions:alien
// Precompiled into AlienPrecompiledBootstrapStyleSheet for zero-overhead Alien rendering.

${'$'}primary: #0d6efd;
${'$'}primary-hover: #0b5ed7;
${'$'}primary-active: #0a58ca;

${'$'}secondary: #6c757d;
${'$'}secondary-hover: #5c636a;
${'$'}secondary-active: #565e64;

${'$'}success: #198754;
${'$'}success-hover: #157347;
${'$'}success-active: #146c43;

${'$'}danger: #dc3545;
${'$'}danger-hover: #bb2d3b;
${'$'}danger-active: #b02a37;

${'$'}warning: #ffc107;
${'$'}warning-hover: #ffca2c;
${'$'}warning-active: #ffcd39;

${'$'}info: #0dcaf0;
${'$'}info-hover: #31d2f2;
${'$'}info-active: #3dd5f3;

${'$'}light: #f8f9fa;
${'$'}light-hover: #d3d4d5;
${'$'}light-active: #c6c7c8;

${'$'}dark: #212529;
${'$'}dark-hover: #424649;
${'$'}dark-active: #4d5154;

${'$'}white: #ffffff;
${'$'}body-bg-light: #ffffff;
${'$'}body-color-light: #212529;
${'$'}secondary-bg-light: #e9ecef;
${'$'}tertiary-bg-light: #f8f9fa;
${'$'}border-color-light: #dee2e6;

${'$'}body-bg-dark: #212529;
${'$'}body-color-dark: #dee2e6;
${'$'}secondary-bg-dark: #343a40;
${'$'}tertiary-bg-dark: #2b3035;
${'$'}border-color-dark: #495057;

${'$'}border-radius: 6px;
${'$'}border-radius-sm: 4px;
${'$'}border-radius-lg: 8px;
${'$'}border-radius-xl: 16px;
${'$'}border-radius-xxl: 32px;
${'$'}border-radius-pill: 9999px;

.bs-theme-light {
  --bs-primary: ${'$'}primary;
  --bs-secondary: ${'$'}secondary;
  --bs-success: ${'$'}success;
  --bs-info: ${'$'}info;
  --bs-warning: ${'$'}warning;
  --bs-danger: ${'$'}danger;
  --bs-light: ${'$'}light;
  --bs-dark: ${'$'}dark;
  --bs-body-bg: ${'$'}body-bg-light;
  --bs-body-color: ${'$'}body-color-light;
  --bs-secondary-bg: ${'$'}secondary-bg-light;
  --bs-tertiary-bg: ${'$'}tertiary-bg-light;
  --bs-border-color: ${'$'}border-color-light;
  --bs-border-radius: ${'$'}border-radius;
  --bs-border-radius-sm: ${'$'}border-radius-sm;
  --bs-border-radius-lg: ${'$'}border-radius-lg;
}

.bs-theme-dark {
  --bs-primary: ${'$'}primary;
  --bs-secondary: ${'$'}secondary;
  --bs-success: ${'$'}success;
  --bs-info: ${'$'}info;
  --bs-warning: ${'$'}warning;
  --bs-danger: ${'$'}danger;
  --bs-light: ${'$'}light;
  --bs-dark: ${'$'}dark;
  --bs-body-bg: ${'$'}body-bg-dark;
  --bs-body-color: ${'$'}body-color-dark;
  --bs-secondary-bg: ${'$'}secondary-bg-dark;
  --bs-tertiary-bg: ${'$'}tertiary-bg-dark;
  --bs-border-color: ${'$'}border-color-dark;
  --bs-border-radius: ${'$'}border-radius;
  --bs-border-radius-sm: ${'$'}border-radius-sm;
  --bs-border-radius-lg: ${'$'}border-radius-lg;
}

// ---------------------------------------------------------------------------
// Buttons (.btn, .btn-*, .btn-outline-*, .btn-sm, .btn-lg)
// ---------------------------------------------------------------------------
.btn {
  display: inline-block;
  padding: 6px 12px;
  font-size: 16px;
  font-weight: 400;
  text-align: center;
  border-width: 1px;
  border-style: solid;
  border-radius: ${'$'}border-radius;
  cursor: pointer;
  user-select: none;
}

.btn-sm {
  padding: 4px 8px;
  font-size: 14px;
  border-radius: ${'$'}border-radius-sm;
}

.btn-lg {
  padding: 8px 16px;
  font-size: 20px;
  border-radius: ${'$'}border-radius-lg;
}

.btn-primary {
  background-color: ${'$'}primary;
  border-color: ${'$'}primary;
  color: ${'$'}white;
  &:hover {
    background-color: ${'$'}primary-hover;
    border-color: ${'$'}primary-hover;
    color: ${'$'}white;
  }
  &:active {
    background-color: ${'$'}primary-active;
    border-color: ${'$'}primary-active;
    color: ${'$'}white;
  }
}

.btn-secondary {
  background-color: ${'$'}secondary;
  border-color: ${'$'}secondary;
  color: ${'$'}white;
  &:hover {
    background-color: ${'$'}secondary-hover;
    border-color: ${'$'}secondary-hover;
    color: ${'$'}white;
  }
  &:active {
    background-color: ${'$'}secondary-active;
    border-color: ${'$'}secondary-active;
    color: ${'$'}white;
  }
}

.btn-success {
  background-color: ${'$'}success;
  border-color: ${'$'}success;
  color: ${'$'}white;
  &:hover {
    background-color: ${'$'}success-hover;
    border-color: ${'$'}success-hover;
    color: ${'$'}white;
  }
  &:active {
    background-color: ${'$'}success-active;
    border-color: ${'$'}success-active;
    color: ${'$'}white;
  }
}

.btn-danger {
  background-color: ${'$'}danger;
  border-color: ${'$'}danger;
  color: ${'$'}white;
  &:hover {
    background-color: ${'$'}danger-hover;
    border-color: ${'$'}danger-hover;
    color: ${'$'}white;
  }
  &:active {
    background-color: ${'$'}danger-active;
    border-color: ${'$'}danger-active;
    color: ${'$'}white;
  }
}

.btn-warning {
  background-color: ${'$'}warning;
  border-color: ${'$'}warning;
  color: ${'$'}body-color-light;
  &:hover {
    background-color: ${'$'}warning-hover;
    border-color: ${'$'}warning-hover;
    color: ${'$'}body-color-light;
  }
  &:active {
    background-color: ${'$'}warning-active;
    border-color: ${'$'}warning-active;
    color: ${'$'}body-color-light;
  }
}

.btn-info {
  background-color: ${'$'}info;
  border-color: ${'$'}info;
  color: ${'$'}body-color-light;
  &:hover {
    background-color: ${'$'}info-hover;
    border-color: ${'$'}info-hover;
    color: ${'$'}body-color-light;
  }
  &:active {
    background-color: ${'$'}info-active;
    border-color: ${'$'}info-active;
    color: ${'$'}body-color-light;
  }
}

.btn-light {
  background-color: ${'$'}light;
  border-color: ${'$'}light;
  color: ${'$'}body-color-light;
  &:hover {
    background-color: ${'$'}light-hover;
    border-color: ${'$'}light-hover;
    color: ${'$'}body-color-light;
  }
  &:active {
    background-color: ${'$'}light-active;
    border-color: ${'$'}light-active;
    color: ${'$'}body-color-light;
  }
}

.btn-dark {
  background-color: ${'$'}dark;
  border-color: ${'$'}dark;
  color: ${'$'}white;
  &:hover {
    background-color: ${'$'}dark-hover;
    border-color: ${'$'}dark-hover;
    color: ${'$'}white;
  }
  &:active {
    background-color: ${'$'}dark-active;
    border-color: ${'$'}dark-active;
    color: ${'$'}white;
  }
}

.btn-link {
  background-color: transparent;
  border-color: transparent;
  color: ${'$'}primary;
  text-decoration: underline;
  &:hover {
    background-color: transparent;
    border-color: transparent;
    color: ${'$'}primary-hover;
  }
  &:active {
    background-color: transparent;
    border-color: transparent;
    color: ${'$'}primary-active;
  }
}

.btn-outline-primary {
  background-color: transparent;
  border-color: ${'$'}primary;
  color: ${'$'}primary;
  &:hover {
    background-color: ${'$'}primary;
    border-color: ${'$'}primary;
    color: ${'$'}white;
  }
  &:active {
    background-color: ${'$'}primary-active;
    border-color: ${'$'}primary-active;
    color: ${'$'}white;
  }
}

.btn-outline-secondary {
  background-color: transparent;
  border-color: ${'$'}secondary;
  color: ${'$'}secondary;
  &:hover {
    background-color: ${'$'}secondary;
    border-color: ${'$'}secondary;
    color: ${'$'}white;
  }
  &:active {
    background-color: ${'$'}secondary-active;
    border-color: ${'$'}secondary-active;
    color: ${'$'}white;
  }
}

.btn-outline-success {
  background-color: transparent;
  border-color: ${'$'}success;
  color: ${'$'}success;
  &:hover {
    background-color: ${'$'}success;
    border-color: ${'$'}success;
    color: ${'$'}white;
  }
  &:active {
    background-color: ${'$'}success-active;
    border-color: ${'$'}success-active;
    color: ${'$'}white;
  }
}

.btn-outline-danger {
  background-color: transparent;
  border-color: ${'$'}danger;
  color: ${'$'}danger;
  &:hover {
    background-color: ${'$'}danger;
    border-color: ${'$'}danger;
    color: ${'$'}white;
  }
  &:active {
    background-color: ${'$'}danger-active;
    border-color: ${'$'}danger-active;
    color: ${'$'}white;
  }
}

.btn-outline-warning {
  background-color: transparent;
  border-color: ${'$'}warning;
  color: ${'$'}warning;
  &:hover {
    background-color: ${'$'}warning;
    border-color: ${'$'}warning;
    color: ${'$'}body-color-light;
  }
  &:active {
    background-color: ${'$'}warning-active;
    border-color: ${'$'}warning-active;
    color: ${'$'}body-color-light;
  }
}

.btn-outline-info {
  background-color: transparent;
  border-color: ${'$'}info;
  color: ${'$'}info;
  &:hover {
    background-color: ${'$'}info;
    border-color: ${'$'}info;
    color: ${'$'}body-color-light;
  }
  &:active {
    background-color: ${'$'}info-active;
    border-color: ${'$'}info-active;
    color: ${'$'}body-color-light;
  }
}

.btn-outline-light {
  background-color: transparent;
  border-color: ${'$'}light;
  color: ${'$'}light;
  &:hover {
    background-color: ${'$'}light;
    border-color: ${'$'}light;
    color: ${'$'}body-color-light;
  }
  &:active {
    background-color: ${'$'}light-active;
    border-color: ${'$'}light-active;
    color: ${'$'}body-color-light;
  }
}

.btn-outline-dark {
  background-color: transparent;
  border-color: ${'$'}dark;
  color: ${'$'}dark;
  &:hover {
    background-color: ${'$'}dark;
    border-color: ${'$'}dark;
    color: ${'$'}white;
  }
  &:active {
    background-color: ${'$'}dark-active;
    border-color: ${'$'}dark-active;
    color: ${'$'}white;
  }
}

// ---------------------------------------------------------------------------
// Alerts, Badges, Cards, Forms, Navbar, Modals, Popovers
// ---------------------------------------------------------------------------
.alert {
  padding: 16px;
  border-width: 1px;
  border-style: solid;
  border-radius: ${'$'}border-radius;
}

.alert-primary {
  background-color: #cfe2ff;
  border-color: #9ec5fe;
  color: #052c65;
}

.alert-secondary {
  background-color: #e2e3e5;
  border-color: #c4c8cb;
  color: #2b2f32;
}

.alert-success {
  background-color: #d1e7dd;
  border-color: #a3cfbb;
  color: #0a3622;
}

.alert-danger {
  background-color: #f8d7da;
  border-color: #f1aeb5;
  color: #58151c;
}

.alert-warning {
  background-color: #fff3cd;
  border-color: #ffe69c;
  color: #664d03;
}

.alert-info {
  background-color: #cff4fc;
  border-color: #9eeaf9;
  color: #055160;
}

.alert-light {
  background-color: #fcfcfd;
  border-color: #e9ecef;
  color: #495057;
}

.alert-dark {
  background-color: #ced4da;
  border-color: #adb5bd;
  color: #495057;
}

.badge {
  display: inline-block;
  padding: 4px 7px;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
  border-radius: ${'$'}border-radius-sm;
}

.card {
  padding: 16px;
  background-color: ${'$'}body-bg-light;
  border-width: 1px;
  border-style: solid;
  border-color: ${'$'}border-color-light;
  border-radius: ${'$'}border-radius;
}

.form-control {
  padding: 6px 12px;
  font-size: 16px;
  background-color: ${'$'}body-bg-light;
  color: ${'$'}body-color-light;
  border-width: 1px;
  border-style: solid;
  border-color: ${'$'}border-color-light;
  border-radius: ${'$'}border-radius;
  &:focus {
    border-color: #86b7fe;
  }
}

.form-select {
  padding: 6px 12px;
  font-size: 16px;
  background-color: ${'$'}body-bg-light;
  color: ${'$'}body-color-light;
  border-width: 1px;
  border-style: solid;
  border-color: ${'$'}border-color-light;
  border-radius: ${'$'}border-radius;
  &:focus {
    border-color: #86b7fe;
  }
}

.form-check-input {
  border-width: 1px;
  border-style: solid;
  border-color: ${'$'}border-color-light;
  border-radius: ${'$'}border-radius-sm;
  &:checked {
    background-color: ${'$'}primary;
    border-color: ${'$'}primary;
  }
}

.navbar {
  background-color: ${'$'}tertiary-bg-light;
  border-width: 1px;
  border-style: solid;
  border-color: ${'$'}border-color-light;
}

.modal-content {
  background-color: ${'$'}body-bg-light;
  border-width: 1px;
  border-style: solid;
  border-color: ${'$'}border-color-light;
  border-radius: ${'$'}border-radius-lg;
}

.popover {
  background-color: ${'$'}body-bg-light;
  border-width: 1px;
  border-style: solid;
  border-color: ${'$'}border-color-light;
  border-radius: ${'$'}border-radius-lg;
}

.bs-theme-dark {
  .card {
    background-color: ${'$'}tertiary-bg-dark;
    border-color: ${'$'}border-color-dark;
    color: ${'$'}body-color-dark;
  }
  .form-control {
    background-color: ${'$'}body-bg-dark;
    border-color: ${'$'}border-color-dark;
    color: ${'$'}body-color-dark;
    &:focus {
      border-color: #86b7fe;
    }
  }
  .form-select {
    background-color: ${'$'}body-bg-dark;
    border-color: ${'$'}border-color-dark;
    color: ${'$'}body-color-dark;
    &:focus {
      border-color: #86b7fe;
    }
  }
  .navbar {
    background-color: ${'$'}tertiary-bg-dark;
    border-color: ${'$'}border-color-dark;
    color: ${'$'}body-color-dark;
  }
  .modal-content {
    background-color: ${'$'}tertiary-bg-dark;
    border-color: ${'$'}border-color-dark;
    color: ${'$'}body-color-dark;
  }
  .popover {
    background-color: ${'$'}tertiary-bg-dark;
    border-color: ${'$'}border-color-dark;
    color: ${'$'}body-color-dark;
  }
}

// ---------------------------------------------------------------------------
// Background, Text & Border Color Utilities
// ---------------------------------------------------------------------------
.bg-primary { background-color: ${'$'}primary; }
.bg-secondary { background-color: ${'$'}secondary; }
.bg-success { background-color: ${'$'}success; }
.bg-danger { background-color: ${'$'}danger; }
.bg-warning { background-color: ${'$'}warning; }
.bg-info { background-color: ${'$'}info; }
.bg-light { background-color: ${'$'}light; }
.bg-dark { background-color: ${'$'}dark; }

.text-primary { color: ${'$'}primary; }
.text-secondary { color: ${'$'}secondary; }
.text-success { color: ${'$'}success; }
.text-danger { color: ${'$'}danger; }
.text-warning { color: ${'$'}warning; }
.text-info { color: ${'$'}info; }
.text-light { color: ${'$'}light; }
.text-dark { color: ${'$'}dark; }

.border {
  border-width: 1px;
  border-style: solid;
  border-color: ${'$'}border-color-light;
}
.border-0 { border-width: 0px; }
.border-1 { border-width: 1px; }
.border-2 { border-width: 2px; }
.border-3 { border-width: 3px; }
.border-4 { border-width: 4px; }
.border-5 { border-width: 5px; }

.border-primary { border-color: ${'$'}primary; }
.border-secondary { border-color: ${'$'}secondary; }
.border-success { border-color: ${'$'}success; }
.border-danger { border-color: ${'$'}danger; }
.border-warning { border-color: ${'$'}warning; }
.border-info { border-color: ${'$'}info; }
.border-light { border-color: ${'$'}light; }
.border-dark { border-color: ${'$'}dark; }

// ---------------------------------------------------------------------------
// Radius, Shadow & Opacity Utilities
// ---------------------------------------------------------------------------
.rounded-0 { border-radius: 0px; }
.rounded-1 { border-radius: ${'$'}border-radius-sm; }
.rounded-2 { border-radius: ${'$'}border-radius; }
.rounded-3 { border-radius: ${'$'}border-radius-lg; }
.rounded-4 { border-radius: ${'$'}border-radius-xl; }
.rounded-5 { border-radius: ${'$'}border-radius-xxl; }
.rounded-pill { border-radius: ${'$'}border-radius-pill; }
.rounded-circle { border-radius: 50%; }

.shadow-none { box-shadow: none; }
.shadow-sm { box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08); }
.shadow { box-shadow: 0 8px 16px rgba(0, 0, 0, 0.15); }
.shadow-lg { box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18); }

.opacity-0 { opacity: 0; }
.opacity-25 { opacity: 0.25; }
.opacity-50 { opacity: 0.5; }
.opacity-75 { opacity: 0.75; }
.opacity-100 { opacity: 1; }

// ---------------------------------------------------------------------------
// Typography Utilities
// ---------------------------------------------------------------------------
.fs-1 { font-size: 40px; }
.fs-2 { font-size: 32px; }
.fs-3 { font-size: 28px; }
.fs-4 { font-size: 24px; }
.fs-5 { font-size: 20px; }
.fs-6 { font-size: 16px; }

.fw-lighter { font-weight: 200; }
.fw-light { font-weight: 300; }
.fw-normal { font-weight: 400; }
.fw-medium { font-weight: 500; }
.fw-semibold { font-weight: 600; }
.fw-bold { font-weight: 700; }
.fw-bolder { font-weight: 800; }
.fst-italic { font-style: italic; }
.font-monospace { font-family: monospace; }

.lh-1 { line-height: 1; }
.lh-sm { line-height: 1.25; }
.lh-base { line-height: 1.5; }
.lh-lg { line-height: 2; }

.text-start { text-align: left; }
.text-center { text-align: center; }
.text-end { text-align: right; }
.text-justify { text-align: justify; }
.text-lowercase { text-transform: lowercase; }
.text-uppercase { text-transform: uppercase; }
.text-capitalize { text-transform: capitalize; }
.text-nowrap { white-space: nowrap; }
.text-wrap { white-space: normal; }
.text-truncate { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.text-break { word-break: break-word; }

// ---------------------------------------------------------------------------
// Sizing, Display, Flex & Spacing Utilities
// ---------------------------------------------------------------------------
.w-25 { width: 25%; }
.w-50 { width: 50%; }
.w-75 { width: 75%; }
.w-100 { width: 100%; }
.w-auto { width: auto; }
.mw-100 { max-width: 100%; }
.vw-100 { width: 100%; }
.min-vw-100 { min-width: 100%; }

.h-25 { height: 25%; }
.h-50 { height: 50%; }
.h-75 { height: 75%; }
.h-100 { height: 100%; }
.h-auto { height: auto; }
.mh-100 { max-height: 100%; }
.vh-100 { height: 100%; }
.min-vh-100 { min-height: 100%; }

.d-none { display: none; }
.d-inline { display: inline; }
.d-inline-block { display: inline-block; }
.d-block { display: block; }
.d-grid { display: grid; }
.d-flex { display: flex; }
.d-inline-flex { display: inline-flex; }

.p-0 { padding: 0px; }
.p-1 { padding: 4px; }
.p-2 { padding: 8px; }
.p-3 { padding: 16px; }
.p-4 { padding: 24px; }
.p-5 { padding: 48px; }
.px-0 { padding-left: 0px; padding-right: 0px; }
.px-1 { padding-left: 4px; padding-right: 4px; }
.px-2 { padding-left: 8px; padding-right: 8px; }
.px-3 { padding-left: 16px; padding-right: 16px; }
.px-4 { padding-left: 24px; padding-right: 24px; }
.px-5 { padding-left: 48px; padding-right: 48px; }
.py-0 { padding-top: 0px; padding-bottom: 0px; }
.py-1 { padding-top: 4px; padding-bottom: 4px; }
.py-2 { padding-top: 8px; padding-bottom: 8px; }
.py-3 { padding-top: 16px; padding-bottom: 16px; }
.py-4 { padding-top: 24px; padding-bottom: 24px; }
.py-5 { padding-top: 48px; padding-bottom: 48px; }
.pt-0 { padding-top: 0px; }
.pt-1 { padding-top: 4px; }
.pt-2 { padding-top: 8px; }
.pt-3 { padding-top: 16px; }
.pt-4 { padding-top: 24px; }
.pt-5 { padding-top: 48px; }
.pb-0 { padding-bottom: 0px; }
.pb-1 { padding-bottom: 4px; }
.pb-2 { padding-bottom: 8px; }
.pb-3 { padding-bottom: 16px; }
.pb-4 { padding-bottom: 24px; }
.pb-5 { padding-bottom: 48px; }
.ps-0 { padding-left: 0px; }
.ps-1 { padding-left: 4px; }
.ps-2 { padding-left: 8px; }
.ps-3 { padding-left: 16px; }
.ps-4 { padding-left: 24px; }
.ps-5 { padding-left: 48px; }
.pe-0 { padding-right: 0px; }
.pe-1 { padding-right: 4px; }
.pe-2 { padding-right: 8px; }
.pe-3 { padding-right: 16px; }
.pe-4 { padding-right: 24px; }
.pe-5 { padding-right: 48px; }

.m-0 { margin: 0px; }
.m-1 { margin: 4px; }
.m-2 { margin: 8px; }
.m-3 { margin: 16px; }
.m-4 { margin: 24px; }
.m-5 { margin: 48px; }
.mx-0 { margin-left: 0px; margin-right: 0px; }
.mx-1 { margin-left: 4px; margin-right: 4px; }
.mx-2 { margin-left: 8px; margin-right: 8px; }
.mx-3 { margin-left: 16px; margin-right: 16px; }
.mx-4 { margin-left: 24px; margin-right: 24px; }
.mx-5 { margin-left: 48px; margin-right: 48px; }
.my-0 { margin-top: 0px; margin-bottom: 0px; }
.my-1 { margin-top: 4px; margin-bottom: 4px; }
.my-2 { margin-top: 8px; margin-bottom: 8px; }
.my-3 { margin-top: 16px; margin-bottom: 16px; }
.my-4 { margin-top: 24px; margin-bottom: 24px; }
.my-5 { margin-top: 48px; margin-bottom: 48px; }
.mt-0 { margin-top: 0px; }
.mt-1 { margin-top: 4px; }
.mt-2 { margin-top: 8px; }
.mt-3 { margin-top: 16px; }
.mt-4 { margin-top: 24px; }
.mt-5 { margin-top: 48px; }
.mb-0 { margin-bottom: 0px; }
.mb-1 { margin-bottom: 4px; }
.mb-2 { margin-bottom: 8px; }
.mb-3 { margin-bottom: 16px; }
.mb-4 { margin-bottom: 24px; }
.mb-5 { margin-bottom: 48px; }
.ms-0 { margin-left: 0px; }
.ms-1 { margin-left: 4px; }
.ms-2 { margin-left: 8px; }
.ms-3 { margin-left: 16px; }
.ms-4 { margin-left: 24px; }
.ms-5 { margin-left: 48px; }
.me-0 { margin-right: 0px; }
.me-1 { margin-right: 4px; }
.me-2 { margin-right: 8px; }
.me-3 { margin-right: 16px; }
.me-4 { margin-right: 24px; }
.me-5 { margin-right: 48px; }
"""
    // END_PRECOMPILED_BOOTSTRAP_SCSS

    override val rawCss: String by lazy {
        SassCompiler.compileToCss(BOOTSTRAP_SCSS_SOURCE)
    }

    override val compiledRules: List<CompiledStyleRule> by lazy {
        compileFromScssSource(BOOTSTRAP_SCSS_SOURCE)
    }

    override val styleSheet: CssStyleSheet by lazy {
        CssStyleSheet.precompiledFromCompiledRules(
            compiledRules = compiledRules,
            rawCss = rawCss
        )
    }

    private val KNOWN_COMPONENT_PREFIXES = setOf(
        "btn",
        "alert",
        "badge",
        "card",
        "form-control",
        "form-select",
        "form-check-input",
        "navbar",
        "modal-content",
        "popover"
    )

    init {
        ensureRegistered()
    }

    fun ensureRegistered() {
        StyleSheetRegistry.register(
            name = name,
            path = cssResourcePath,
            resourceId = RESOURCE_ID,
            sheet = this
        )
    }

    fun resolveSheetByResourceId(resourceId: ResourceID): PrecompiledStyleSheet? {
        if (resourceId.id == RESOURCE_ID.id || resourceId.name.equals("bootstrap", ignoreCase = true)) {
            ensureRegistered()
            return this
        }
        return StyleSheetRegistry.resolveByResourceId(resourceId)
    }

    fun resolveSheetByPath(path: String): PrecompiledStyleSheet? {
        if (matchesPath(this, path)) {
            ensureRegistered()
            return this
        }
        return StyleSheetRegistry.get(path)
    }

    fun matchesPath(sheet: PrecompiledStyleSheet, path: String): Boolean {
        val clean = path.trim().replace('\\', '/').removePrefix("/").lowercase()
        if (clean.isEmpty()) return false
        val sheetPath = sheet.cssResourcePath.trim().replace('\\', '/').removePrefix("/").lowercase()
        val sheetName = sheet.name.trim().lowercase()
        return clean == sheetPath ||
            clean == sheetName ||
            clean == "$sheetName.scss" ||
            clean == "$sheetName.css" ||
            (sheet === this && (clean == "bootstrap" || clean == "bootstrap.scss" || clean == "bootstrap.css" || clean == "styles/bootstrap.css"))
    }

    fun matchesResourceId(sheet: PrecompiledStyleSheet, resourceId: ResourceID): Boolean {
        if (sheet === this && (resourceId.id == RESOURCE_ID.id || resourceId.name.equals("bootstrap", ignoreCase = true))) {
            return true
        }
        val resolved = StyleSheetRegistry.resolveByResourceId(resourceId)
        if (resolved != null && (resolved === sheet || resolved.name == sheet.name)) {
            return true
        }
        return resourceId.relativePath.isNotBlank() && matchesPath(sheet, resourceId.relativePath)
    }

    fun wrapCssStyleSheet(
        cssSheet: CssStyleSheet,
        sheetName: String = "alien-inline-stylesheet",
        resourcePath: String = "styles/alien-inline.css"
    ): PrecompiledStyleSheet = object : PrecompiledStyleSheet {
        override val name: String = sheetName
        override val styleSheet: CssStyleSheet = cssSheet
        override val rawCss: String = cssSheet.sourceCssText
        override val cssResourcePath: String = resourcePath
        override val compiledRules: List<CompiledStyleRule> = cssSheet.rules.map { rule ->
            CompiledStyleRule(
                selector = rule.selector,
                declarations = rule.declarations,
                specificity = rule.specificity,
                pseudoClass = rule.pseudoClass,
                mediaMinWidthPx = rule.mediaMinWidthPx,
                mediaMaxWidthPx = rule.mediaMaxWidthPx
            )
        }
    }

    fun resolveStyleSheetForNode(
        sheet: CssStyleSheet,
        node: AlienWidgetNode,
        darkTheme: Boolean,
        viewportWidthPx: Int = 1280
    ): ComputedStyle {
        val classes = collectBootstrapClassesForNode(node)
        val ancestorChain = buildAncestorChain(node, darkTheme)
        return sheet.resolveStyleForClasses(
            classes = classes,
            tagName = node.tagName.ifEmpty { "div" },
            id = node.attributes["id"],
            ancestorChain = ancestorChain,
            viewportWidthPx = viewportWidthPx
        )
    }

    /**
     * Compiles a Bootstrap `.scss` source string into specificity-ordered [CompiledStyleRule] entries.
     */
    fun compileFromScssSource(scssSource: String): List<CompiledStyleRule> {
        val parsed = SassCompiler.compileToStyleSheet(scssSource)
        return parsed.rules.map { rule ->
            CompiledStyleRule(
                selector = rule.selector,
                declarations = rule.declarations,
                specificity = rule.specificity,
                pseudoClass = rule.pseudoClass,
                mediaMinWidthPx = rule.mediaMinWidthPx,
                mediaMaxWidthPx = rule.mediaMaxWidthPx
            )
        }
    }

    /**
     * Collects the complete set of Bootstrap CSS classes (both enum-mapped classes and custom classes)
     * for [node] without mutating [AlienWidgetNode.styleSnapshot].
     */
    fun collectBootstrapClassesForNode(node: AlienWidgetNode): Set<String> {
        val snapshot = node.styleSnapshot
        val classes = linkedSetOf<String>()
        classes.addAll(BootstrapClassMapper.toEnumClassSet(snapshot))
        classes.addAll(snapshot.customClasses)

        val prefix = node.componentClassPrefix.trim().lowercase()
        if (prefix in KNOWN_COMPONENT_PREFIXES) {
            classes.add(prefix)
        }

        val semanticType = snapshot.type?.takeIf { it.isSemantic }
        if (prefix == "alert" && semanticType == null && classes.none { it.startsWith("alert-") }) {
            classes.add("alert-${BootstrapType.PRIMARY.cssSuffix}")
        } else if (prefix == "badge" && semanticType != null) {
            classes.add("bg-${semanticType.cssSuffix}")
        } else if (prefix == "badge" && semanticType == null && classes.none { it.startsWith("bg-") }) {
            classes.add("bg-${BootstrapType.SECONDARY.cssSuffix}")
        }
        return classes
    }

    /**
     * Resolves the precompiled Bootstrap 5.3 [ComputedStyle] (including `:hover`, `:focus`, `:active`,
     * `:disabled`, `:checked` pseudo-class states) for [node] under [darkTheme].
     */
    fun resolveForNode(
        node: AlienWidgetNode,
        darkTheme: Boolean,
        viewportWidthPx: Int = 1280
    ): ComputedStyle {
        val classes = collectBootstrapClassesForNode(node)
        val ancestorChain = buildAncestorChain(node, darkTheme)
        return styleSheet.resolveStyleForClasses(
            classes = classes,
            tagName = node.tagName.ifEmpty { "div" },
            id = node.attributes["id"],
            ancestorChain = ancestorChain,
            viewportWidthPx = viewportWidthPx
        )
    }

    private fun buildAncestorChain(node: AlienWidgetNode, darkTheme: Boolean): List<Set<String>> {
        val chain = mutableListOf<Set<String>>()
        var p = node.parent
        while (p != null) {
            val tokens = linkedSetOf<String>()
            tokens.addAll(collectBootstrapClassesForNode(p))
            val tag = p.tagName.ifEmpty { "div" }.lowercase()
            tokens.add(tag)
            tokens.add("tag:$tag")
            p.attributes["id"]?.takeIf { it.isNotEmpty() }?.let { tokens.add("#$it") }
            chain.add(tokens)
            p = p.parent
        }
        val ordered = chain.reversed().toMutableList()
        ordered.add(
            0,
            setOf(
                if (darkTheme) "bs-theme-dark" else "bs-theme-light",
                "root",
                "genericui:root",
                "tag:root",
                "tag:genericui:root"
            )
        )
        return ordered
    }
}
