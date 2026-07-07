# Detekt Formatting Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace standalone ktlint with detekt-formatting so `./gradlew detekt` is the single Kotlin linting command.

**Architecture:** Two-commit phased migration — Commit 1 upgrades detekt to 2.0.0-alpha.5 in isolation; Commit 2 enables detekt-formatting rules and removes ktlint. Android lint (`./gradlew lint` + `lint.xml`) is out of scope and untouched.

**Tech Stack:** Gradle (Groovy DSL), detekt 2.0.0-alpha.5, detekt-formatting 2.0.0-alpha.5 (bundles ktlint internally), `.editorconfig`

---

## File Map

| File | Task | Change |
|---|---|---|
| `gradle/versions.gradle` | 1 + 2 | Bump `detektPluginVersion`; remove `ktlintPluginVersion` + `ktlintVersion` |
| `gradle/dependencies.gradle` | 1 | Bump `detekt-formatting` artifact version |
| `settings.gradle` | 2 | Remove ktlint plugin declaration |
| `build.gradle` | 2 | Remove ktlint `import`, `plugins` entry, and `ktlint {}` block |
| `config/detekt/detekt.yml` | 1 + 2 | Fix any renamed rules (Task 1); add `Formatting:` section (Task 2) |
| `.editorconfig` | 2 | Remove TV function-naming glob section |

---

## Task 1: Upgrade detekt to 2.0.0-alpha.5

**Files:**
- Modify: `gradle/versions.gradle:26-27`
- Modify: `gradle/dependencies.gradle:117`
- Modify: `config/detekt/detekt.yml` (if rules broke)

### Build commands for this project

Always use:
```bash
./gradlew --no-daemon --max-workers 2
```
Never use the system `gradle` binary — it is 7.6.3 and incompatible with Java 21 JBR.

- [ ] **Step 1: Bump detekt plugin version in versions.gradle**

In `gradle/versions.gradle`, change lines 26–27:
```groovy
// before
detektPluginVersion = "1.23.8"

// after
detektPluginVersion = "2.0.0-alpha.5"
```

- [ ] **Step 2: Bump detekt-formatting artifact version in dependencies.gradle**

In `gradle/dependencies.gradle`, change line 117:
```groovy
// before
detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")

// after
detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:2.0.0-alpha.5")
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers 2
```

Expected: `BUILD SUCCESSFUL`. If you see Gradle plugin resolution errors about `io.gitlab.arturbosch.detekt`, check that `settings.gradle` line 12 picks up `detektPluginVersion` correctly (it reads from `versions.gradle` via `apply from`).

- [ ] **Step 4: Run detekt and capture output**

```bash
./gradlew detekt --no-daemon --max-workers 2 2>&1 | tee /tmp/detekt-upgrade.txt
```

Expected: `BUILD SUCCESSFUL`. If it fails, go to Step 5. If it passes, skip to Step 6.

- [ ] **Step 5: Fix broken rule names in detekt.yml (only if Step 4 failed)**

If detekt reports errors like `Rule 'X' not found` or `Unknown property 'Y'`, those are detekt 2.x renames. Current `config/detekt/detekt.yml`:

```yaml
complexity:
  active: false

exceptions:
  TooGenericExceptionCaught:
    active: false

performance:
  SpreadOperator:
    active: false

style:
  MagicNumber:
    active: false
  ForbiddenComment:
    active: false
```

Check the error output for each failing rule. Detekt 2.x moved some rules between rule sets. Cross-reference against the detekt 2.x changelog at https://detekt.dev/docs/introduction/changelog. Common renames to watch for:
- `performance > SpreadOperator` → may have moved to `potential-bugs`
- Rule set names lowercased → `complexity`, `style`, etc. are unchanged

Apply only the renames that the error output demands. Keep the structure identical otherwise. Then re-run Step 4.

- [ ] **Step 6: Commit**

```bash
git add gradle/versions.gradle gradle/dependencies.gradle config/detekt/detekt.yml
git commit -m "build: upgrade detekt to 2.0.0-alpha.5"
```

---

## Task 2: Enable detekt-formatting, remove ktlint

**Files:**
- Modify: `config/detekt/detekt.yml`
- Modify: `build.gradle:1-2,17-19,175-185`
- Modify: `settings.gradle:13`
- Modify: `gradle/versions.gradle:27-28`
- Modify: `.editorconfig:22-23`

### What detekt-formatting does

`detekt-formatting` wraps ktlint rules and exposes them in a `Formatting` rule set. It reads `.editorconfig` natively — settings like `indent_size`, `max_line_length`, and `end_of_line` in `.editorconfig` are honored automatically. The `Formatting:` section in `detekt.yml` only needs to override what `.editorconfig` cannot express (path-scoped rule disables).

- [ ] **Step 1: Add Formatting section to detekt.yml**

Append to `config/detekt/detekt.yml`:

```yaml
Formatting:
  active: true
  excludes:
    - "**/me/zhanghai/**"
    - "**/com/gojuno/**"
    - "**/androidx/recyclerview/widget/BindAwareViewHolder.kt"
  MaximumLineLength:
    active: true
    maxLineLength: 120
  FunctionNaming:
    active: true
    excludes:
      - "**/tv/**"
```

Explanation:
- `Formatting: active: true` — enables the entire rule set (it ships disabled by default)
- Top-level `excludes` under `Formatting:` — skips the three vendor-copied files from all formatting rules (these were in ktlint's `filter { exclude(...) }` block)
- `MaximumLineLength: maxLineLength: 120` — matches `.editorconfig`'s `max_line_length = 120`; explicit here to avoid detekt using its own default (120 is also the detekt default, but being explicit removes ambiguity)
- `FunctionNaming: excludes: ["**/tv/**"]` — replaces the `.editorconfig` glob `[src/main/kotlin/me/proxer/app/tv/**]` / `ktlint_standard_function-naming = disabled`; TV Compose screens use PascalCase function names which violate the ktlint naming rule

- [ ] **Step 2: Remove ktlint import and plugin from build.gradle**

In `build.gradle`, remove line 2:
```groovy
// DELETE this line:
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
```

In the `plugins {}` block (lines 9–19), remove line 18:
```groovy
// DELETE this line:
id "org.jlleitschuh.gradle.ktlint"
```

- [ ] **Step 3: Remove ktlint block from build.gradle**

Delete the entire `ktlint { ... }` block (lines 175–185):
```groovy
// DELETE this entire block:
ktlint {
    version = ktlintVersion
    reporters {
        reporter ReporterType.CHECKSTYLE
    }
    filter {
        exclude("**/me/zhanghai/**")
        exclude("**/com/gojuno/**")
        exclude("**/androidx/recyclerview/widget/BindAwareViewHolder.kt")
    }
}
```

- [ ] **Step 4: Remove ktlint plugin declaration from settings.gradle**

In `settings.gradle`, remove line 13:
```groovy
// DELETE this line:
id "org.jlleitschuh.gradle.ktlint" version "${ktlintPluginVersion}"
```

- [ ] **Step 5: Remove ktlint versions from versions.gradle**

In `gradle/versions.gradle`, remove lines 27–28:
```groovy
// DELETE these two lines:
ktlintPluginVersion = "14.2.0"
ktlintVersion = "1.8.0"
```

- [ ] **Step 6: Remove TV function-naming entry from .editorconfig**

In `.editorconfig`, remove the entire TV glob block (lines 22–24):
```ini
# DELETE these lines:
[src/main/kotlin/me/proxer/app/tv/**]
ktlint_standard_function-naming = disabled

```

The remaining `.editorconfig` content (charset, indent, line length, etc.) stays unchanged — detekt-formatting reads it.

- [ ] **Step 7: Run detekt and verify formatting rules are active**

```bash
./gradlew detekt --no-daemon --max-workers 2 2>&1 | tee /tmp/detekt-formatting.txt
```

Expected: `BUILD SUCCESSFUL`. If detekt reports unknown rule `FunctionNaming` under `Formatting`, the rule name differs in detekt-formatting 2.x. Check the available rule names by looking at the detekt-formatting jar or the changelog; the ktlint rule `standard:function-naming` maps to a detekt rule name — find it and update `detekt.yml`.

If detekt reports that `Formatting: excludes` is not a valid property at the ruleset level (depends on detekt 2.x YAML schema), move the vendor exclusions to each individual rule or use the Gradle `detekt { source }` approach instead:

```groovy
// Fallback in build.gradle if YAML-level excludes don't work:
detekt {
    buildUponDefaultConfig = true
    config.from(files("$projectDir/config/detekt/detekt.yml"))
    autoCorrect = false
    source.setFrom("src/main/kotlin", "src/debug/kotlin", "src/release/kotlin")
    reports {
        md { required = true }
    }
}
```

Remove the vendor exclusions from `Formatting:` in `detekt.yml` (they are now excluded by source scope) and also remove the per-file `exclude(...)` patterns from `Formatting: FunctionNaming: excludes` (TV files under `src/main/kotlin/me/proxer/app/tv/` are included in the source set, so the `**/tv/**` exclude in YAML still applies).

And add a `baseline.xml` or per-file suppression for vendor files if `source.setFrom` still picks them up via transitive dependencies.

- [ ] **Step 8: Confirm ktlint task is gone**

```bash
./gradlew tasks --no-daemon | grep -i ktlint
```

Expected: no output (empty). If any ktlint tasks appear, the plugin was not fully removed — recheck `settings.gradle` and `build.gradle`.

- [ ] **Step 9: Confirm Android lint is unaffected**

```bash
./gradlew lintDebug --no-daemon --max-workers 2 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` (or same result as before this change). Android lint reads `config/lint/lint.xml` independently of detekt and ktlint.

- [ ] **Step 10: Commit**

```bash
git add config/detekt/detekt.yml build.gradle settings.gradle gradle/versions.gradle .editorconfig
git commit -m "build: replace ktlint with detekt-formatting rules"
```
