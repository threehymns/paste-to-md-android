package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Paste to Markdown", appName)
    }

    @Test
    fun `settings repository persists preferences and resets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AppSettingsRepository(context)

        // Initial default checks
        assertEquals(ThemeMode.SYSTEM, repo.settings.value.themeMode)
        assertTrue(repo.settings.value.dynamicColor)
        assertFalse(repo.settings.value.autoPasteOnLaunch)

        // Modify preferences
        repo.setThemeMode(ThemeMode.DARK)
        repo.setAutoPasteOnLaunch(true)
        repo.setBulletMarker(BulletMarker.ASTERISK)
        repo.setHeadingStyle(HeadingStyle.SETEXT)

        assertEquals(ThemeMode.DARK, repo.settings.value.themeMode)
        assertTrue(repo.settings.value.autoPasteOnLaunch)
        assertEquals(BulletMarker.ASTERISK, repo.settings.value.bulletMarker)
        assertEquals(HeadingStyle.SETEXT, repo.settings.value.headingStyle)

        // Test reset to defaults
        repo.resetToDefaults()
        assertEquals(ThemeMode.SYSTEM, repo.settings.value.themeMode)
        assertFalse(repo.settings.value.autoPasteOnLaunch)
        assertEquals(BulletMarker.HYPHEN, repo.settings.value.bulletMarker)
        assertEquals(HeadingStyle.ATX, repo.settings.value.headingStyle)
    }

    @Test
    fun `markdown converter converts HTML according to settings`() {
        val html = "<h1>Title</h1><p>A paragraph with <strong>bold</strong> text.</p><ul><li>Item 1</li><li>Item 2</li></ul>"

        // ATX style with hyphen bullets
        val atxSettings = AppSettings(
            headingStyle = HeadingStyle.ATX,
            bulletMarker = BulletMarker.HYPHEN
        )
        val atxOutput = MarkdownConverter.convert(html, atxSettings)
        assertTrue(atxOutput.contains("# Title"))
        assertTrue(atxOutput.contains("- Item 1"))

        // Setext style with asterisk bullets
        val setextSettings = AppSettings(
            headingStyle = HeadingStyle.SETEXT,
            bulletMarker = BulletMarker.ASTERISK
        )
        val setextOutput = MarkdownConverter.convert(html, setextSettings)
        assertTrue(setextOutput.contains("Title\n====="))
        assertTrue(setextOutput.contains("* Item 1"))
    }
}
