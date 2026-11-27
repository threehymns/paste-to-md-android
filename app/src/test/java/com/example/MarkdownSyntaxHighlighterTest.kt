package com.example

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSyntaxHighlighterTest {

    private val lightColors = lightColorScheme()
    private val highlighter = MarkdownSyntaxHighlighter(lightColors)

    @Test
    fun `highlighter uses structural equality for ColorScheme`() {
        val first = MarkdownSyntaxHighlighter(lightColors)
        val second = MarkdownSyntaxHighlighter(lightColors)
        val dark = MarkdownSyntaxHighlighter(darkColorScheme())

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, dark)
    }

    @Test
    fun `highlights both backtick and tilde fenced code blocks and skips inner formatting`() {
        val markdown = """
            # Title

            ```kotlin
            # not a heading
            val x = **not_bold**
            ```

            ~~~python
            *not_italic*
            [not_a_link](https://example.com)
            ~~~
        """.trimIndent()

        val transformed = highlighter.filter(AnnotatedString(markdown))
        assertEquals(OffsetMapping.Identity, transformed.offsetMapping)
        assertEquals(markdown, transformed.text.text)

        val styles = transformed.text.spanStyles

        val backtickStart = markdown.indexOf("```kotlin")
        val backtickEnd = markdown.indexOf("```\n\n~~~") + 3
        assertTrue(
            "Expected monospace style on backtick code block",
            styles.any {
                it.start == backtickStart &&
                    it.end == backtickEnd &&
                    it.item.fontFamily == FontFamily.Monospace &&
                    it.item.background == lightColors.surfaceVariant
            }
        )

        val tildeStart = markdown.indexOf("~~~python")
        val tildeEnd = markdown.length
        assertTrue(
            "Expected monospace style on tilde code block",
            styles.any {
                it.start == tildeStart &&
                    it.end == tildeEnd &&
                    it.item.fontFamily == FontFamily.Monospace &&
                    it.item.background == lightColors.surfaceVariant
            }
        )

        val innerHeadingStart = markdown.indexOf("# not a heading")
        assertFalse(
            "Heading inside code block should not receive bold header style",
            styles.any { it.start == innerHeadingStart && it.item.fontWeight == FontWeight.Bold }
        )

        val innerLinkStart = markdown.indexOf("[not_a_link]")
        assertFalse(
            "Link inside tilde code block should not receive link color",
            styles.any { it.start == innerLinkStart && it.item.color == lightColors.tertiary }
        )
    }

    @Test
    fun `does not italicize snake_case identifiers or URLs while highlighting real underscore italics`() {
        val markdown = "Use my_snake_case_var and https://example.com/a_b_c with _real_ and _x_."
        val transformed = highlighter.filter(AnnotatedString(markdown))
        val italicSpans = transformed.text.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }

        val highlightedSubstrings = italicSpans.map { markdown.substring(it.start, it.end) }
        assertEquals(listOf("_real_", "_x_"), highlightedSubstrings)
    }

    @Test
    fun `does not apply inline bold or italic styles inside inline code spans`() {
        val markdown = "Run `**not_bold** and _not_italic_` then **bold**."
        val transformed = highlighter.filter(AnnotatedString(markdown))
        val styles = transformed.text.spanStyles

        val boldSubstrings = styles
            .filter { it.item.fontWeight == FontWeight.Bold }
            .map { markdown.substring(it.start, it.end) }
        assertEquals(listOf("**bold**"), boldSubstrings)

        val italicSubstrings = styles
            .filter { it.item.fontStyle == FontStyle.Italic }
            .map { markdown.substring(it.start, it.end) }
        assertTrue("No italic spans expected, found $italicSubstrings", italicSubstrings.isEmpty())

        val inlineCodeStart = markdown.indexOf('`')
        val inlineCodeEnd = markdown.lastIndexOf('`') + 1
        assertTrue(
            styles.any {
                it.start == inlineCodeStart &&
                    it.end == inlineCodeEnd &&
                    it.item.fontFamily == FontFamily.Monospace
            }
        )
    }
}
