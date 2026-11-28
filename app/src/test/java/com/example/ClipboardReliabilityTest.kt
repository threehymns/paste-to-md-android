package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettingsRepository
import com.example.session.AndroidDocumentExporter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClipboardReliabilityTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun clearPrefs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppSettingsRepository(context).resetToDefaults()
    }

    @Test
    fun androidDocumentExporterWritesUtf8AndReturnsFalseForMissingProvider() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = AndroidDocumentExporter(context.contentResolver)
        val tempFile = File(context.cacheDir, "saved_test.md")
        val fileUri = Uri.fromFile(tempFile)

        val validSuccess = exporter.write(
            targetId = fileUri.toString(),
            markdown = "# Café — Markdown"
        )
        assertTrue(validSuccess)
        assertEquals("# Café — Markdown", tempFile.readText(Charsets.UTF_8))

        val missingUri = Uri.parse("content://com.example.nonexistent.provider/missing.md")
        val nullStreamSuccess = exporter.write(
            targetId = missingUri.toString(),
            markdown = "should fail"
        )
        assertFalse(nullStreamSuccess)
    }

    @Test
    fun autoPasteOnLaunchReadsClipboardOnceWindowGainsFocus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppSettingsRepository(context).setAutoPasteOnLaunch(true)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("sample", "Focused Paste"))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitForIdle()
            scenario.onActivity { activity ->
                activity.onWindowFocusChanged(true)
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("markdown_text_field").assertIsDisplayed()
            composeTestRule.onNodeWithTag("markdown_text_field").assertTextContains("Focused Paste")
        }
    }

    @Test
    fun manualCopyWritesClipboardWithInlineConfirmationAndNoSnackbar() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newHtmlText("doc", "Hello", "<h1>Hello</h1>"))

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("paste_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("markdown_text_field").assertTextContains("# Hello", substring = true)

            composeTestRule.mainClock.autoAdvance = false
            try {
                composeTestRule.onNodeWithTag("copy_button").performClick()
                composeTestRule.mainClock.advanceTimeBy(220L)
                composeTestRule.waitForIdle()

                val copiedClip = clipboard.primaryClip
                assertEquals("# Hello", copiedClip?.getItemAt(0)?.text?.toString()?.trim())
                composeTestRule.onNodeWithText("Copied").assertIsDisplayed()
                composeTestRule.onNodeWithText("Markdown copied to clipboard").assertDoesNotExist()
            } finally {
                composeTestRule.mainClock.autoAdvance = true
            }
        }
    }
}
