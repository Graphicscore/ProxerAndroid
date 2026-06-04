# Fix Gradle Build Warnings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate all five Gradle/Kotlin compiler warning categories from `./gradlew compileDebugKotlin`.

**Architecture:** Two-phase: Phase 1 migrates from external `org.jetbrains.kotlin.android` plugin to AGP's built-in Kotlin (2.2.10), removing the root cause of warnings 4–5. Phase 2 fixes the remaining three warnings via a one-line `gradle.properties` change and two-line Room DAO fix.

**Tech Stack:** AGP 9.2.1, Kotlin 2.2.10 (built-in), KSP 2.2.10-2.0.2, Room 2.8.4, Gradle 9.5.1, Groovy DSL.

**Spec:** `docs/superpowers/specs/2026-06-04-fix-gradle-warnings-design.md`

---

### Task 1: Phase 1 — Migrate to built-in Kotlin

**Goal:** Remove the external `org.jetbrains.kotlin.android` / `kotlin.plugin.compose` plugins and switch to AGP 9.2.1's bundled Kotlin 2.2.10, eliminating the `applicationVariants` obsolete API warnings and the plugin deprecation warning.

**Files:**
- Modify: `gradle/versions.gradle`
- Modify: `gradle.properties`
- Modify: `settings.gradle`
- Modify: `build.gradle`
- Modify: `CLAUDE.md`

**Acceptance Criteria:**
- [ ] `./gradlew compileDebugKotlin --warning-mode all` produces zero `w:` lines mentioning `kotlin.android`, `applicationVariants`, `testVariants`, or `unitTestVariants`
- [ ] `./gradlew assembleDebug` completes without error
- [ ] `CLAUDE.md` toolchain table shows Kotlin 2.2.10 and KSP 2.2.10-2.0.2

**Verify:**
```
./gradlew compileDebugKotlin --no-daemon --max-workers 2 --warning-mode all 2>&1 | grep "^w:"
```
Expected: no output (zero `w:` lines).

**Steps:**

- [ ] **Step 1: Update versions.gradle**

  Change `kotlinVersion` and `kspVersion`:

  ```groovy
  // gradle/versions.gradle — change these two lines
  kspVersion = "2.2.10-2.0.2"   // was "2.3.9"
  kotlinVersion = "2.2.10"       // was "2.4.0"
  ```

  Full resulting block (only the two changed lines shown; everything else stays):
  ```groovy
  // Core plugins
  androidPluginVersion = "9.2.1"

  kspVersion = "2.2.10-2.0.2"
  kotlinVersion = "2.2.10"
  ```

- [ ] **Step 2: Update gradle.properties**

  Remove the `android.builtInKotlin=false`, `android.newDsl=false` lines and their comment block. Also remove both keys from `android.suppressUnsupportedOptionWarnings`.

  **Before (lines 13–18):**
  ```properties
  # Both properties required to use org.jetbrains.kotlin.android plugin (Kotlin 2.4.0) with AGP 9.
  # Remove when AGP bundles the same Kotlin version, allowing migration to built-in Kotlin.
  android.builtInKotlin=false
  android.newDsl=false
  android.suppressUnsupportedOptionWarnings=android.suppressUnsupportedOptionWarnings,android.enableR8.fullMode,\
    android.namespacedRClass,android.enableJetifier,android.builtInKotlin,android.newDsl
  ```

  **After:**
  ```properties
  android.suppressUnsupportedOptionWarnings=android.suppressUnsupportedOptionWarnings,android.enableR8.fullMode,\
    android.namespacedRClass,android.enableJetifier
  ```

  Full resulting `gradle.properties`:
  ```properties
  org.gradle.jvmargs=-Xmx2048M -XX:MaxHeapSize=1024M -Dfile.encoding=UTF-8
  org.gradle.vfs.watch=true
  org.gradle.parallel=true
  org.gradle.caching=true

  kotlin.code.style=official

  android.useAndroidX=true
  android.enableJetifier=true
  android.namespacedRClass=true
  android.nonTransitiveRClass=false
  android.enableR8.fullMode=true
  android.suppressUnsupportedOptionWarnings=android.suppressUnsupportedOptionWarnings,android.enableR8.fullMode,\
    android.namespacedRClass,android.enableJetifier
  ```

- [ ] **Step 3: Update settings.gradle — remove external Kotlin plugin declarations**

  Remove both Kotlin plugin lines from `pluginManagement.plugins`:

  **Before:**
  ```groovy
  plugins {
      id "org.jetbrains.kotlin.android" version "${kotlinVersion}"
      id "org.jetbrains.kotlin.plugin.compose" version "${kotlinVersion}"
      id "com.google.devtools.ksp" version "${kspVersion}"
      ...
  }
  ```

  **After:**
  ```groovy
  plugins {
      id "com.google.devtools.ksp" version "${kspVersion}"
      id "com.mikepenz.aboutlibraries.plugin" version "${aboutLibrariesVersion}"
      id "com.github.ben-manes.versions" version "${versionsPluginVersion}"
      id "io.gitlab.arturbosch.detekt" version "${detektPluginVersion}"
      id "org.jlleitschuh.gradle.ktlint" version "${ktlintPluginVersion}"
  }
  ```

- [ ] **Step 4: Update build.gradle — remove external Kotlin plugin applications**

  Remove both Kotlin plugin lines from the `plugins` block:

  **Before:**
  ```groovy
  plugins {
      id "com.android.application"
      id "org.jetbrains.kotlin.android"
      id "org.jetbrains.kotlin.plugin.compose"
      id "com.google.devtools.ksp"
      ...
  }
  ```

  **After:**
  ```groovy
  plugins {
      id "com.android.application"
      id "com.google.devtools.ksp"
      id "com.mikepenz.aboutlibraries.plugin"
      id "com.github.ben-manes.versions"
      id "io.gitlab.arturbosch.detekt"
      id "org.jlleitschuh.gradle.ktlint"
  }
  ```

  Keep the `tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach { kotlinOptions { ... } }` block unchanged — AGP bundles Kotlin internally and still exposes `KotlinCompile` tasks.

  > **If the build fails** with `ClassNotFoundException` or `unresolved reference: KotlinCompile`, replace the `tasks.withType(...)` block with this equivalent inside the `android` block:
  > ```groovy
  > android {
  >     // ... existing config ...
  >     kotlinOptions {
  >         allWarningsAsErrors = false
  >         jvmTarget = javaVersion.toString()
  >         freeCompilerArgs += ["-Xjsr305=strict", "-progressive"]
  >     }
  > }
  > ```

- [ ] **Step 5: Run the verification command**

  ```bash
  ./gradlew compileDebugKotlin --no-daemon --max-workers 2 --warning-mode all 2>&1 | grep "^w:"
  ```

  Expected: **no output**. If you see `w:` lines for Kotlin plugin deprecation or `applicationVariants`, check that both plugin lines are removed from `build.gradle` AND `settings.gradle`.

  Also confirm the full build still works:
  ```bash
  ./gradlew assembleDebug --no-daemon --max-workers 2
  ```
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Update CLAUDE.md toolchain table**

  In `CLAUDE.md`, the Toolchain table section:

  **Before:**
  ```markdown
  | Kotlin | 2.4.0                                                                                     |
  | KSP | 2.3.9 (Room, Moshi, Glide — uses `com.github.bumptech.glide:ksp` artifact)               |
  ```

  **After:**
  ```markdown
  | Kotlin | 2.2.10 (AGP 9.2.1 built-in; no external `org.jetbrains.kotlin.android` plugin)     |
  | KSP | 2.2.10-2.0.2 (Room, Moshi, Glide — uses `com.github.bumptech.glide:ksp` artifact)        |
  ```

  Also remove the Key Gotchas entry about `android.builtInKotlin=false` + `android.newDsl=false` (the bullet starting with `` `android.builtInKotlin=false` + `android.newDsl=false` ``).

- [ ] **Step 7: Commit**

  ```bash
  git add gradle/versions.gradle gradle.properties settings.gradle build.gradle CLAUDE.md
  git commit -m "build: migrate to AGP built-in Kotlin 2.2.10, remove external kotlin-android plugin"
  ```

---

### Task 2: Phase 2 — Fix remaining code warnings

**Goal:** Eliminate the three remaining warnings: JVM native access restriction from `gradle.properties`, and two Room KSP warnings in `MessengerDao.kt`.

**Files:**
- Modify: `gradle.properties`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/sync/MessengerDao.kt`

**Acceptance Criteria:**
- [ ] `./gradlew clean kspDebugKotlin compileDebugKotlin --rerun-tasks --warning-mode all` produces zero `w:` lines
- [ ] No `WARNING: A restricted method in java.lang.System has been called` lines in build output
- [ ] Room KSP emits no warnings for `MessengerDao.getConferencesLiveData`

**Verify:**
```
./gradlew clean kspDebugKotlin compileDebugKotlin --no-daemon --max-workers 2 --rerun-tasks --warning-mode all 2>&1 | grep -E "^w:|^WARNING:"
```
Expected: **no output**.

**Steps:**

- [ ] **Step 1: Fix JVM native access warning in gradle.properties**

  Append `--enable-native-access=ALL-UNNAMED` to `org.gradle.jvmargs`:

  **Before:**
  ```properties
  org.gradle.jvmargs=-Xmx2048M -XX:MaxHeapSize=1024M -Dfile.encoding=UTF-8
  ```

  **After:**
  ```properties
  org.gradle.jvmargs=-Xmx2048M -XX:MaxHeapSize=1024M -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED
  ```

- [ ] **Step 2: Fix Room DAO in MessengerDao.kt**

  File: `src/main/kotlin/me/proxer/app/chat/prv/sync/MessengerDao.kt`

  Add `import androidx.room.RewriteQueriesToDropUnusedColumns` to the imports block (after the existing Room imports, line ~10):

  **Before (imports block):**
  ```kotlin
  import androidx.room.Dao
  import androidx.room.Insert
  import androidx.room.OnConflictStrategy
  import androidx.room.Query
  import androidx.room.RoomWarnings
  import androidx.room.Transaction
  ```

  **After:**
  ```kotlin
  import androidx.room.Dao
  import androidx.room.Insert
  import androidx.room.OnConflictStrategy
  import androidx.room.Query
  import androidx.room.RewriteQueriesToDropUnusedColumns
  import androidx.room.RoomWarnings
  import androidx.room.Transaction
  ```

  Then at line 101, apply two changes to `getConferencesLiveData`:
  1. Add `@RewriteQueriesToDropUnusedColumns` before `@Query`
  2. Remove `?` from return type

  **Before (around line 86–101):**
  ```kotlin
  @Query(
      """
          SELECT * FROM conferences
          LEFT JOIN (
              SELECT id AS messageId, conferenceId, userId, message AS messageText, username,
                     `action` as messageAction from messages
              GROUP BY conferenceId
              HAVING MAX(date)
              ORDER BY date DESC, id
          ) AS messages
          ON conferences.id = messages.conferenceId
          WHERE topic LIKE '%' || :searchQuery || '%'
          ORDER BY date DESC
          """
  )
  abstract fun getConferencesLiveData(searchQuery: String): LiveData<List<ConferenceWithMessage>?>
  ```

  **After:**
  ```kotlin
  @RewriteQueriesToDropUnusedColumns
  @Query(
      """
          SELECT * FROM conferences
          LEFT JOIN (
              SELECT id AS messageId, conferenceId, userId, message AS messageText, username,
                     `action` as messageAction from messages
              GROUP BY conferenceId
              HAVING MAX(date)
              ORDER BY date DESC, id
          ) AS messages
          ON conferences.id = messages.conferenceId
          WHERE topic LIKE '%' || :searchQuery || '%'
          ORDER BY date DESC
          """
  )
  abstract fun getConferencesLiveData(searchQuery: String): LiveData<List<ConferenceWithMessage>>
  ```

  `@RewriteQueriesToDropUnusedColumns` tells Room's KSP processor to silently drop the `conferenceId` column returned by the JOIN that `ConferenceWithMessage` doesn't use, rather than warning about the mismatch.

- [ ] **Step 3: Run the verification command**

  This forces KSP to re-run (necessary to see Room warnings or their absence):
  ```bash
  ./gradlew clean kspDebugKotlin compileDebugKotlin --no-daemon --max-workers 2 --rerun-tasks --warning-mode all 2>&1 | grep -E "^w:|^WARNING:"
  ```
  Expected: **no output at all**.

- [ ] **Step 4: Commit**

  ```bash
  git add gradle.properties src/main/kotlin/me/proxer/app/chat/prv/sync/MessengerDao.kt
  git commit -m "fix(build): suppress JVM native access warning, fix Room DAO nullable type and column mismatch"
  ```
