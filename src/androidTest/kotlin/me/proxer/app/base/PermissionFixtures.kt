package me.proxer.app.base

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Grants WRITE_EXTERNAL_STORAGE to the app under test.
 *
 * Call this from `@Before` in any test that launches [me.proxer.app.MainActivity] (i.e. anything going through
 * `MainActivity.getSectionIntent`). MainActivity.onCreate runtime-requests WRITE_EXTERNAL_STORAGE whenever
 * BuildConfig.LOG is set -- which it is in the debug build instrumentation runs against -- and
 * src/debug/AndroidManifest.xml declares that permission with no maxSdkVersion, so it is not silently dropped
 * on API levels where it is still requestable at runtime. Left ungranted there, the system permission dialog
 * launches on top of MainActivity and pauses it before the test can read the compose tree, failing every
 * assertion with "No compose hierarchies found in the app". Granting it up front makes checkSelfPermission
 * return PERMISSION_GRANTED so the dialog never opens. Granting is harmless where it does not apply.
 *
 * Screens hosted by their own activity (see NotificationScreenTest, and the TV tests) never request the
 * permission and so don't need this -- it is deliberately opt-in rather than an unconditional `@Before` on
 * [InstrumentedTestBase], to keep each call site explicit about why MainActivity in particular needs it.
 *
 * Uses raw [android.app.UiAutomation] rather than the declarative `GrantPermissionRule` because
 * `androidx.test:rules` is not on the androidTest classpath (gradle/dependencies.gradle pulls in only
 * `androidx.test.ext:junit`, `espresso-core` and `mockk-android`).
 */
fun grantStoragePermission() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()

    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        WRITE_EXTERNAL_STORAGE,
    )
}
