package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClipboardReliabilityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPrefs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppSettingsRepository(context).resetToDefaults()
    }

    @Test
    fun autoPasteWaitsForWindowFocusBeforeReadingClipboard() {
        val activity = composeTestRule.activity
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("sample", "Focused Paste"))

        // Ensure window focus is currently false before enabling auto-paste.
        composeTestRule.runOnIdle {
            activity.onWindowFocusChanged(false)
            activity.settingsRepository.setAutoPasteOnLaunch(true)
        }
        composeTestRule.waitForIdle()

        // Should still be on Paste screen until window gains focus.
        composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()

        composeTestRule.runOnIdle {
            activity.onWindowFocusChanged(true)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("markdown_text_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("markdown_text_field").assertTextContains("Focused Paste")
    }

    @Test
    fun htmlConversionErrorDoesNotOverwriteMarkdownOrAutoCopyClipboard() {
        val activity = composeTestRule.activity
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // First navigate to Preview with valid text.
        clipboard.setPrimaryClip(ClipData.newPlainText("initial", "Existing Markdown"))
        composeTestRule.onNodeWithTag("paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("markdown_text_field").assertTextContains("Existing Markdown")

        // Enable auto-copy and put HTML on clipboard, with a failing converter.
        composeTestRule.runOnIdle {
            activity.settingsRepository.setAutoCopyOnConvert(true)
            activity.htmlConverter = { _, _ -> throw IllegalStateException("Malformed HTML") }
            clipboard.setPrimaryClip(ClipData.newHtmlText("bad", "fallback", "<p>bad</p>"))
        }

        composeTestRule.onNodeWithTag("paste_again_button").performClick()
        composeTestRule.waitForIdle()

        // Existing markdown text and clipboard HTML must remain intact.
        composeTestRule.onNodeWithTag("markdown_text_field").assertTextContains("Existing Markdown")
        val currentClip = clipboard.primaryClip
        assertEquals("<p>bad</p>", currentClip?.getItemAt(0)?.htmlText)
        assertTrue(
            "Expected error toast, got: ${ShadowToast.getTextOfLatestToast()}",
            ShadowToast.getTextOfLatestToast()?.contains("Error converting HTML") == true
        )
    }

    @Test
    fun cancellingInFlightConversionOnBackDoesNotMutateOrReopenPreview() {
        val activity = composeTestRule.activity
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // Start in Preview with initial text.
        clipboard.setPrimaryClip(ClipData.newPlainText("initial", "First Doc"))
        composeTestRule.onNodeWithTag("paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("markdown_text_field").assertIsDisplayed()

        // Start a slow HTML conversion on Paste Again, then press Clear/Back before it finishes.
        val gate = CompletableDeferred<String>()
        composeTestRule.runOnIdle {
            activity.htmlConverter = { _, _ -> gate.await() }
            clipboard.setPrimaryClip(ClipData.newHtmlText("slow", "slow", "<p>Slow</p>"))
        }

        composeTestRule.onNodeWithTag("paste_again_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("clear_button").performClick()
        composeTestRule.waitForIdle()

        // Complete the stale deferred after navigating back.
        gate.complete("Stale Result")
        composeTestRule.waitForIdle()

        // Must remain on Paste screen, not overwritten or jumped back to Preview.
        composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()
    }

    @Test
    fun writeMarkdownToUriReturnsFalseWhenOutputStreamIsNullAndWritesUtf8WhenValid() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File(context.cacheDir, "saved_test.md")
        val fileUri = Uri.fromFile(tempFile)

        val validSuccess = writeMarkdownToUri(
            contentResolver = context.contentResolver,
            uri = fileUri,
            markdown = "# Café — Markdown"
        )
        assertTrue(validSuccess)
        assertEquals("# Café — Markdown", tempFile.readText(Charsets.UTF_8))

        // Unregistered content URI returns null / throws FileNotFoundException -> returns false.
        val missingUri = Uri.parse("content://com.example.nonexistent.provider/missing.md")
        val nullStreamSuccess = writeMarkdownToUri(
            contentResolver = context.contentResolver,
            uri = missingUri,
            markdown = "should fail"
        )
        assertFalse(nullStreamSuccess)
    }
}
