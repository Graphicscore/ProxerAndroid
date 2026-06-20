# Detekt Formatting Migration Design

**Date:** 2026-06-19
**Branch:** detekt-fix2

## Goal

Replace the standalone `org.jlleitschuh.gradle.ktlint` plugin with detekt-formatting rules.
Single linting pipeline: `./gradlew detekt` covers both code quality and formatting.
Android lint (`./gradlew lint` + `lint.xml`) is unaffected — it runs independently and is out of scope.

## Approach

Two-commit phased migration to isolate upgrade risk from behavior change.

---

## Commit 1 — Upgrade detekt to 2.0.0-alpha.5

### Files

| File | Change |
|---|---|
| `gradle/versions.gradle` | `detektPluginVersion = "2.0.0-alpha.5"` |
| `gradle/dependencies.gradle` | `detekt-formatting` artifact version → `2.0.0-alpha.5` |
| `config/detekt/detekt.yml` | Fix any rule IDs renamed or restructured in detekt 2.x |

### Success criteria

`./gradlew detekt` passes cleanly. No ktlint changes in this commit.

---

## Commit 2 — Replace ktlint with detekt-formatting

### Files

**`build.gradle`**
- Remove `id "org.jlleitschuh.gradle.ktlint"` from `plugins {}`
- Remove entire `ktlint { ... }` block
- Add vendor file exclusions to the `detekt { }` block:
  ```groovy
  detekt {
      excludes = ["**/me/zhanghai/**", "**/com/gojuno/**", "**/androidx/recyclerview/widget/BindAwareViewHolder.kt"]
  }
  ```
- `autoCorrect` stays `false`

**`gradle/versions.gradle`**
- Remove `ktlintPluginVersion` and `ktlintVersion`

**`config/detekt/detekt.yml`**
- Add `Formatting:` section enabling all detekt-formatting rules
- Set `max-line-length: 120` explicitly (mirrors `.editorconfig`)
- Add path-scoped disable for `FunctionNaming` on TV files:
  ```yaml
  Formatting:
    active: true
    FunctionNaming:
      excludes: ["**/tv/**"]
  ```

**`.editorconfig`**
- Remove the TV path glob and its `ktlint_standard_function-naming = disabled` entry — superseded by the detekt.yml exclusion above
- All other `.editorconfig` entries stay (detekt-formatting reads them natively; editors also need them)

### What is NOT changed

- `config/lint/lint.xml` — Android lint rules, unrelated to this migration
- `android { lint { } }` block in `build.gradle` — unchanged
- `.editorconfig` formatting parameters (indent, line-length, charset, etc.) — stay as canonical source

### Success criteria

- `./gradlew detekt` enforces formatting and fails on violations
- `./gradlew ktlint` task no longer exists
- `./gradlew lint` still runs Android lint unaffected
- TV Compose `@Preview` functions (Composable naming convention) do not trigger FunctionNaming violations
