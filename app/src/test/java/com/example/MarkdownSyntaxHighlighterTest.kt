package com.example

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
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
    fun `applies larger descending font sizes for ATX markdown headings than body text`() {
        val markdown = """
            # Heading 1
            ## Heading 2
            ### Heading 3
            #### Heading 4
            ##### Heading 5
            ###### Heading 6
            Regular body paragraph
        """.trimIndent()

        val transformed = highlighter.filter(AnnotatedString(markdown))
        val styles = transformed.text.spanStyles

        fun fontSizeFor(line: String): TextUnit {
            val start = markdown.indexOf(line)
            val end = start + line.length
            return styles.first { it.start == start && it.end == end && it.item.fontSize.isSp }.item.fontSize
        }

        val h1 = fontSizeFor("# Heading 1")
        val h2 = fontSizeFor("## Heading 2")
        val h3 = fontSizeFor("### Heading 3")
        val h4 = fontSizeFor("#### Heading 4")
        val h5 = fontSizeFor("##### Heading 5")
        val h6 = fontSizeFor("###### Heading 6")

        assertTrue("Expected H1 ($h1) > H2 ($h2)", h1.value > h2.value)
        assertTrue("Expected H2 ($h2) > H3 ($h3)", h2.value > h3.value)
        assertTrue("Expected H3 ($h3) > H4 ($h4)", h3.value > h4.value)
        assertTrue("Expected H4 ($h4) > H5 ($h5)", h4.value > h5.value)
        assertTrue("Expected H5 ($h5) > H6 ($h6)", h5.value > h6.value)
        assertTrue("Expected H6 ($h6) > body (16.sp)", h6.value > 16f)

        val bodyStart = markdown.indexOf("Regular body paragraph")
        assertFalse(
            "Regular body text should not receive a heading fontSize override",
            styles.any { it.start <= bodyStart && it.end > bodyStart && it.item.fontSize != TextUnit.Unspecified }
        )
    }

    @Test
    fun `applies larger font sizes to Setext headings and preserves standalone horizontal rules`() {
        val markdown = """
            Main Title
            ==========

            Sub Title
            ---------

            Body text

            ---
        """.trimIndent()

        val transformed = highlighter.filter(AnnotatedString(markdown))
        val styles = transformed.text.spanStyles

        val h1Start = markdown.indexOf("Main Title")
        val h1End = h1Start + "Main Title".length
        val h1Size = styles.first { it.start == h1Start && it.end == h1End && it.item.fontSize.isSp }.item.fontSize

        val h2Start = markdown.indexOf("Sub Title")
        val h2End = h2Start + "Sub Title".length
        val h2Size = styles.first { it.start == h2Start && it.end == h2End && it.item.fontSize.isSp }.item.fontSize

        assertTrue("Expected Setext H1 ($h1Size) > Setext H2 ($h2Size)", h1Size.value > h2Size.value)
        assertTrue("Expected Setext H2 ($h2Size) > body (16.sp)", h2Size.value > 16f)

        val hrStart = markdown.lastIndexOf("---")
        val hrEnd = hrStart + 3
        assertTrue(
            "Standalone horizontal rule should keep outline color",
            styles.any { it.start == hrStart && it.end == hrEnd && it.item.color == lightColors.outline }
        )
    }

    @Test
    fun `keeps heading font size when heading contains inline code`() {
        val markdown = "# Using `Markdown` Titles"
        val transformed = highlighter.filter(AnnotatedString(markdown))
        val styles = transformed.text.spanStyles

        assertTrue(
            "Heading with inline code should still receive larger heading font size",
            styles.any {
                it.start == 0 &&
                    it.end == markdown.length &&
                    it.item.fontWeight == FontWeight.Bold &&
                    it.item.fontSize == 26.sp
            }
        )

        val codeStart = markdown.indexOf("`Markdown`")
        val codeEnd = codeStart + "`Markdown`".length
        assertTrue(
            "Inline code inside heading should still receive monospace style",
            styles.any {
                it.start == codeStart &&
                    it.end == codeEnd &&
                    it.item.fontFamily == FontFamily.Monospace
            }
        )
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
