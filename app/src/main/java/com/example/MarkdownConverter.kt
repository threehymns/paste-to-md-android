package com.example

import com.example.data.AppSettings
import com.example.data.CodeFenceStyle
import com.example.data.EmphasisMarker
import com.example.data.HeadingStyle
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownConverter {

    fun convert(html: String, settings: AppSettings): String {
        val options = MutableDataSet()

        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, settings.headingStyle == HeadingStyle.SETEXT)
        options.set(FlexmarkHtmlConverter.UNORDERED_LIST_DELIMITER, settings.bulletMarker.char)

        if (settings.preserveLineBreaks) {
            options.set(FlexmarkHtmlConverter.BR_AS_EXTRA_BLANK_LINES, false)
        }

        val converter = FlexmarkHtmlConverter.builder(options).build()
        var markdown = converter.convert(html)

        // Code block style
        if (settings.codeFenceStyle == CodeFenceStyle.TILDE) {
            markdown = markdown.replace(Regex("(?m)^```"), "~~~")
        }

        // Emphasis delimiter
        if (settings.emphasisMarker == EmphasisMarker.UNDERSCORE) {
            // Convert bold **text** to __text__
            markdown = markdown.replace(Regex("""\*\*(.+?)\*\*"""), "__$1__")
            // Convert italic *text* to _text_ (preserving list bullets which are followed by whitespace at start of line)
            markdown = markdown.replace(Regex("""(?<!\*|\w|^)\*([^\s\*].*?[^\s\*])\*(?!\*|\w)"""), "_$1_")
        }

        return markdown
    }
}
