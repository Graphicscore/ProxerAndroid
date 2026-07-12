# Stream Player Controls Flicker Fix

## Problem

Tapping the video player in `StreamActivity` pops the controller (playback controls + toolbar) in and then immediately closes it again — controls are effectively unusable.

## Root Cause

`StreamActivity` and `StreamScreen` maintain a bidirectional coupling between the Media3 controller's visibility and the system bars' visibility:

1. `PlayerView.ControllerVisibilityListener` (`StreamScreen.kt`) fires on controller visibility change and calls `activity.toggleFullscreen(visibility == View.GONE)`.
2. `toggleFullscreen()` (`StreamActivity.kt`) shows/hides the system bars via `WindowInsetsControllerCompat`.
3. The system bars change dispatches through `ViewCompat.setOnApplyWindowInsetsListener(window.decorView)`, which calls `handleUIChange(systemBarsVisible)`.
4. `handleUIChange()` calls `playerView.showController()` / `hideController()` again — a second, activity-driven visibility change triggered as a side effect of the controller's own visibility-change callback.

Because the system bar show/hide is animated, the insets callback can dispatch one or more times with a transient/intermediate state before settling. When a dispatch lands with a value that contradicts what was just requested (e.g. bars still animating in when the controller was just shown), step 4 forces the controller back to the wrong state immediately after the tap opened it — producing the pop-in/close-immediately symptom.

This is not a Compose-migration regression: the same bidirectional coupling exists in the pre-Compose `StreamActivity`/`StreamFragment` implementation (verified at commit `3fb0480a~1`). It's a latent bug in the existing architecture, not new code.

## Fix

Confined to `StreamActivity.kt`. Add a guard that lets the insets listener distinguish "echo of our own `toggleFullscreen()` call" from "genuine external system-bar change" (e.g. user manually swiping to reveal the system bar while fullscreen).

```kotlin
private var pendingSystemBarsVisible: Boolean? = null

internal fun toggleFullscreen(wantFullscreen: Boolean) {
    val isInMultiWindowMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && this.isInMultiWindowMode
    val controller = WindowInsetsControllerCompat(window, window.decorView)

    pendingSystemBarsVisible = !wantFullscreen || isInMultiWindowMode

    if (wantFullscreen && !isInMultiWindowMode) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        isInFullscreenMode = true
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        isInFullscreenMode = false
    }
}
```

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
    val systemBarsVisible = insets.isVisible(WindowInsetsCompat.Type.systemBars())
    val expected = pendingSystemBarsVisible

    when {
        expected == null -> handleUIChange(systemBarsVisible)
        systemBarsVisible == expected -> pendingSystemBarsVisible = null
        else -> Unit // still settling toward the expected state, ignore this dispatch
    }

    ViewCompat.onApplyWindowInsets(v, insets)
}
```

Behavior:
- Every `toggleFullscreen()` caller (tap via `ControllerVisibilityListener`, `onMultiWindowModeChanged`, ad-end handler) sets the expectation before touching the system bars, so all callers get the guard uniformly — no special-casing per call site.
- Insets dispatches are ignored until one matches the expected final state, absorbing however many transient dispatches the animation produces, then the guard clears and normal external-change handling resumes.
- A genuine external change (`pendingSystemBarsVisible == null`, e.g. user swipes down the system bar manually while fullscreen) still calls `handleUIChange` and shows the controller, preserving current behavior.
- Rapid re-taps just overwrite `pendingSystemBarsVisible` to the newer target; self-correcting, no stuck state.

## Error Handling

None needed — pure state tracking, no I/O, no new failure modes.

## Testing

No existing unit test infra covers `StreamActivity`'s UI-thread/insets logic (would need Robolectric plus a real `Window`/`WindowInsetsController`, not present in this codebase's JVM test setup). Verification is manual:

- Install debug build, open a stream.
- Tap repeatedly in portrait and landscape — controls stay visible until timeout or a deliberate re-tap, no flash-close.
- Verify multi-window mode entry/exit still toggles system bars and controls correctly.
- Verify ad-playback fullscreen re-entry (`adFullscreenHandler` path) still works.
