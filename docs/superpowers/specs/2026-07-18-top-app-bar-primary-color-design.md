# Top App Bar Primary Color — Design

**Date:** 2026-07-18
**Status:** Approved

## Problem

The top app bar on every screen renders in a surface color (white in light mode, dark grey in
night mode) instead of the app's red-ish primary accent (`#8A0E0E` in the Classic theme). The
navigation drawer header is already correct, which makes the mismatch obvious: opening the drawer
shows a red header sliding over a white bar.

## Root Cause

Material3's `TopAppBar` defaults `containerColor` to `colorScheme.surface`. Nothing in the app
overrides it, so all 30 call sites inherit the surface color.

The drawer header looks right because it explicitly sets `color = MaterialTheme.colorScheme.primary`
(`MainScreen.kt:104`). Nothing is wrong with the drawer — the bars simply never opted in.

A second, related defect lives in `ProxerTheme.kt:26`: the color scheme is built with
`lightColorScheme(...)` unconditionally, even in night mode. Colors passed explicitly (primary,
surface, background, …) resolve correctly from theme attrs in both modes, so this is invisible for
those roles. But every *derived* role — `surfaceVariant`, `surfaceContainer*`, `outline`,
`onSurfaceVariant`, `scrim`, `inverseSurface` — silently gets light-mode values in dark themes.

## Scope

Both defects are fixed together. Part 2 changes surface-derived colors app-wide, which is exactly
what Part 1's bars were accidentally picking up; shipping them together means one visual review
pass instead of two.

## Part 1 — Shared `ProxerTopAppBar`

Add `src/main/kotlin/me/proxer/app/ui/compose/ProxerTopAppBar.kt`: a thin pass-through over M3
`TopAppBar` that presets the color block.

```kotlin
colors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)
```

The wrapper mirrors `TopAppBar`'s signature — `title`, `modifier`, `navigationIcon`, `actions`,
`windowInsets`, `scrollBehavior` — so migrating a call site is an import swap plus a rename.

### Call sites

30 total across 26 files. Four files hold two each: `ConferenceScreen.kt`, `ChatScreen.kt`,
`MessengerScreen.kt`, `AboutScreen.kt`.

**29 migrate to `ProxerTopAppBar`.**

**One stays as raw `TopAppBar`:** `StreamScreen.kt:416`, the video-player overlay bar. It uses a
deliberate 50%-black container (`0x80000000`) with white icon tints so it reads over arbitrary video
frames. A red bar there would be a regression. The exclusion gets a short comment explaining why, so
a later reader sees an intentional deviation rather than a missed file.

### Why a wrapper, not 29 inline color blocks

One place to change if the accent ever moves, and the single exclusion becomes legible as a decision
instead of an oversight.

## Part 2 — `ProxerTheme` dark scheme

In `ProxerTheme.kt`:

1. Select `darkColorScheme(...)` or `lightColorScheme(...)` based on `isSystemInDarkTheme()`, keeping
   the identical explicit color assignments in both branches. `AppCompatDelegate.setDefaultNightMode`
   (driven by the app's theme-variant preference in `MainApplication.kt:221`) updates the
   Configuration uiMode, which is what `isSystemInDarkTheme()` reads — so the app's own
   light/dark/auto/battery setting flows through correctly, not just the system setting.
2. Add the one missing explicit role: `onBackground`, resolved from `R.attr.colorOnBackground`. That
   attr is already defined DayNight-aware in `values/styles.xml` and `values-night/styles.xml` but
   was never passed to the scheme, so text on background currently inherits a near-black default in
   dark mode.

All three theme variants (Classic, BlueGreen, Gloomy) already define DayNight-aware attrs, so attr
resolution needs no change.

## Verification

- `./gradlew compileDebugKotlin`
- `./gradlew testDebugUnitTest`
- `./gradlew detekt`
- Grep gate: no bare `TopAppBar(` outside `StreamScreen.kt` and `ProxerTopAppBar.kt`

## Risk

Part 2 has app-wide blast radius by design — any Compose component reading a derived role changes
appearance in dark mode. That is the intended correction, but it is the part worth eyeballing
on-device across the three theme variants and both light and dark.

## Out of Scope

- Status bar and navigation bar colors. These come from the XML theme (`android:navigationBarColor`
  is already `?attr/colorPrimary`) and are unaffected.
- The remaining View/XML surfaces. `Widget.App.AppBarLayout` already sets
  `android:background="?attr/colorPrimary"`, so legacy bars were never part of this bug.
