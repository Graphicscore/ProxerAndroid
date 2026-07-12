# Stream Controls Flicker Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the anime stream player's controls from popping in and immediately closing again on tap.

**Architecture:** `StreamActivity` currently has a bidirectional coupling: controller visibility drives system-bar visibility (`toggleFullscreen`), and system-bar visibility changes drive controller visibility back (`handleUIChange`, invoked from the `OnApplyWindowInsetsListener`). Self-triggered insets dispatches echo back and can force the controller to the wrong state moments after a tap opened it. Fix: track the system-bar state we expect from our own `toggleFullscreen()` calls and have the insets listener ignore dispatches until one matches that expectation, only falling through to `handleUIChange()` for genuinely external system-bar changes.

**Tech Stack:** Kotlin, AndroidX `WindowInsetsControllerCompat`/`WindowInsetsCompat`, Media3 `PlayerView`. No new dependencies.

## Global Constraints

- Change confined to `src/main/kotlin/me/proxer/app/anime/stream/StreamActivity.kt` — no other files touched.
- No unit test infra exists for this UI-thread/insets logic (would need Robolectric + a real `Window`/`WindowInsetsController`, not present in this codebase's JVM test setup) — verification is `./gradlew compileDebugKotlin` plus manual on-device testing, per the spec (`docs/superpowers/specs/2026-07-12-stream-controls-flicker-fix-design.md`).
- Every `toggleFullscreen()` call site (tap via `ControllerVisibilityListener`, `onMultiWindowModeChanged`, ad-end handler) must go through the same guard — no special-casing per call site.

---

### Task 1: Add system-bars echo guard to StreamActivity

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamActivity.kt:103-241`

**Interfaces:**
- Consumes: nothing new — uses existing `WindowInsetsControllerCompat`, `WindowInsetsCompat`, `ViewCompat.setOnApplyWindowInsetsListener`, `playerView.showController()`/`hideController()`, `isInFullscreenMode`, `handleUIChange()`, `playerManager.isPlayingAd`.
- Produces: new private field `pendingSystemBarsVisible: Boolean?` on `StreamActivity`. No public/internal API changes — `toggleFullscreen(wantFullscreen: Boolean)`, `handleUIChange(systemBarsVisible: Boolean = !isInFullscreenMode)` keep their existing signatures, so `StreamScreen.kt` call sites (`activity.toggleFullscreen(...)`) are untouched.

- [ ] **Step 1: Add the guard field**

In `StreamActivity.kt`, next to the existing `isInFullscreenMode` field (around line 109-110):

```kotlin
    internal var isInFullscreenMode = false
        private set

    private var pendingSystemBarsVisible: Boolean? = null

```

- [ ] **Step 2: Set the expectation inside `toggleFullscreen()`**

Replace the current `toggleFullscreen` (lines 215-227):

```kotlin
    internal fun toggleFullscreen(wantFullscreen: Boolean) {
        val isInMultiWindowMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && this.isInMultiWindowMode
        val controller = WindowInsetsControllerCompat(window, window.decorView)

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

with:

```kotlin
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

- [ ] **Step 3: Gate the insets listener on the expectation**

Replace the current listener (lines 127-131):

```kotlin
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            val systemBarsVisible = insets.isVisible(WindowInsetsCompat.Type.systemBars())
            handleUIChange(systemBarsVisible)
            ViewCompat.onApplyWindowInsets(v, insets)
        }
```

with:

```kotlin
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            val systemBarsVisible = insets.isVisible(WindowInsetsCompat.Type.systemBars())
            val expected = pendingSystemBarsVisible

            when {
                expected == null -> handleUIChange(systemBarsVisible)
                systemBarsVisible == expected -> pendingSystemBarsVisible = null
                else -> Unit
            }

            ViewCompat.onApplyWindowInsets(v, insets)
        }
```

- [ ] **Step 4: Compile check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/anime/stream/StreamActivity.kt
git commit -m "$(cat <<'EOF'
fix(anime): stop stream controls popping in and closing immediately

toggleFullscreen() and handleUIChange() formed a feedback loop: showing
the controller toggled the system bars, whose insets callback toggled
the controller again, sometimes with a stale/transient value from the
system-bar animation, forcing the controls shut right after a tap
opened them. Track the system-bars state we expect from our own
toggleFullscreen() calls and ignore insets dispatches until one matches
it, so only genuinely external system-bar changes reach handleUIChange().
EOF
)"
```

- [ ] **Step 6: Manual verification on device**

Run: `./gradlew installDebug`

On the device:
1. Open any anime stream.
2. Tap the player repeatedly in portrait — controls open and stay open until the auto-hide timeout or a deliberate re-tap; no flash-close.
3. Rotate to landscape (fullscreen), repeat the tap test — same result.
4. Enter and exit multi-window/split-screen mode while the stream plays — system bars and controls still toggle correctly.
5. If ad playback is reachable in test conditions, let an ad play through to confirm the fullscreen re-entry after ad end (`adFullscreenHandler` path) still works.

Expected: no case where tapping the player shows and then immediately hides the controls.

---

## Self-Review

**Spec coverage:** The spec's single required change — the `pendingSystemBarsVisible` guard around `toggleFullscreen()` and the insets listener — is implemented in Task 1, Steps 1-3, matching the spec's code blocks verbatim. The spec's "no automated test infra" note and manual verification checklist are carried into Steps 4 and 6. No other spec sections (Error Handling: none needed) require a task.

**Placeholder scan:** No TBD/TODO, no "add appropriate handling" language, no unfilled code blocks — all steps show complete before/after code.

**Type consistency:** `pendingSystemBarsVisible: Boolean?` is declared once (Step 1) and referenced identically in Steps 2 and 3. `toggleFullscreen(wantFullscreen: Boolean)` and `handleUIChange(systemBarsVisible: Boolean = !isInFullscreenMode)` signatures are unchanged from the existing code, so no downstream call sites (`StreamScreen.kt`) need updates.
