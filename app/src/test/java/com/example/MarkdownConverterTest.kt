package com.example

import com.example.data.AppSettings
import com.example.data.BulletMarker
import com.example.data.CodeFenceStyle
import com.example.data.EmphasisMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownConverterTest {

    @Test
    fun `underscore emphasis preserves operators and globs inside fenced code blocks`() {
        val html = """
            <p>Compute <strong>power</strong> and <em>x</em>:</p>
            <pre><code class="language-python">val = 2 ** 3
glob = "**/*.kt"
ptr = *value*
</code></pre>
        """.trimIndent()

        val output = MarkdownConverter.convert(
            html,
            AppSettings(emphasisMarker = EmphasisMarker.UNDERSCORE)
        )

        assertTrue("Expected prose bold converted to __power__, got:\n$output", output.contains("__power__"))
        assertTrue("Expected single-char italic converted to _x_, got:\n$output", output.contains("_x_"))
        assertTrue("Expected Python exponentiation preserved in code block, got:\n$output", output.contains("val = 2 ** 3"))
        assertTrue("Expected glob preserved in code block, got:\n$output", output.contains("glob = \"**/*.kt\""))
        assertTrue("Expected *value* preserved in code block, got:\n$output", output.contains("ptr = *value*"))
    }

    @Test
    fun `underscore emphasis preserves inline code spans including when wrapped in bold`() {
        val html = "<p>Run <code>2 ** 3</code> and <strong>use <code>*raw*</code> safely</strong>.</p>"

        val output = MarkdownConverter.convert(
            html,
            AppSettings(emphasisMarker = EmphasisMarker.UNDERSCORE)
        )

        assertTrue("Expected inline code preserved, got:\n$output", output.contains("`2 ** 3`"))
        assertTrue("Expected bold wrapping inline code preserved, got:\n$output", output.contains("__use `*raw*` safely__"))
    }

    @Test
    fun `underscore emphasis handles single-char and start-of-line italics while preserving asterisk list bullets`() {
        val html = """
            <p><em>Start</em> of line and <em>a</em> single letter and <strong><em>both</em></strong>.</p>
            <ul>
              <li><em>First</em> item</li>
              <li><strong>Second</strong> item</li>
            </ul>
        """.trimIndent()

        val output = MarkdownConverter.convert(
            html,
            AppSettings(
                bulletMarker = BulletMarker.ASTERISK,
                emphasisMarker = EmphasisMarker.UNDERSCORE
            )
        )

        assertTrue("Expected start-of-line italic _Start_, got:\n$output", output.contains("_Start_"))
        assertTrue("Expected single-char italic _a_, got:\n$output", output.contains("_a_"))
        assertTrue("Expected bold-italic ___both___, got:\n$output", output.contains("___both___"))
        assertTrue("Expected asterisk list bullet preserved with italic item, got:\n$output", output.contains("* _First_ item"))
        assertTrue("Expected asterisk list bullet preserved with bold item, got:\n$output", output.contains("* __Second__ item"))
    }

    @Test
    fun `tilde code fence converts outer fences without altering inner backticks`() {
        val input = """
            Intro text
              ```kotlin
              val ticks = "```"
              val math = 2 ** 3
              ```
            Outro *text*
        """.trimIndent()

        val output = MarkdownConverter.postProcessMarkdown(
            markdown = input,
            useTildeFences = true,
            useUnderscoreEmphasis = true
        )

        val expected = """
            Intro text
              ~~~kotlin
              val ticks = "```"
              val math = 2 ** 3
              ~~~
            Outro _text_
        """.trimIndent()

        assertEquals(expected, output)
    }

    @Test
    fun `underscore emphasis preserves thematic breaks and multi-backtick code spans`() {
        val input = """
            ***
            * * *
            Use `` `**keep**` `` and *convert*
        """.trimIndent()

        val output = MarkdownConverter.postProcessMarkdown(
            markdown = input,
            useTildeFences = false,
            useUnderscoreEmphasis = true
        )

        assertTrue(output.contains("***"))
        assertTrue(output.contains("* * *"))
        assertTrue(output.contains("`` `**keep**` ``"))
        assertTrue(output.contains("_convert_"))
        assertFalse(output.contains("__keep__"))
    }

    @Test
    fun `tilde fence setting converts HTML pre code block to tildes`() {
        val html = "<pre><code class=\"language-js\">const x = 1;</code></pre>"
        val output = MarkdownConverter.convert(
            html,
            AppSettings(codeFenceStyle = CodeFenceStyle.TILDE)
        )

        assertTrue("Expected tilde opening fence, got:\n$output", output.contains("~~~"))
        assertFalse("Did not expect backtick fence in output:\n$output", output.contains("```"))
    }
}
