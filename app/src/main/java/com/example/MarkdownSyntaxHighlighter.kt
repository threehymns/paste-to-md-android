package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

data class MarkdownSyntaxHighlighter(private val colorScheme: ColorScheme) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val plainText = text.text
        if (plainText.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val fencedCodeRanges = findFencedCodeRanges(plainText)
        val inlineCodeRanges = findInlineCodeRanges(plainText, fencedCodeRanges)
        val protectedRanges = mergeSortedRanges(fencedCodeRanges, inlineCodeRanges)

        val annotatedString = buildAnnotatedString {
            append(plainText)

            val codeSpanStyle = SpanStyle(
                background = colorScheme.surfaceVariant,
                color = colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )

            // Blockquotes
            BLOCKQUOTE_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, protectedRanges)) {
                    addStyle(SpanStyle(color = colorScheme.secondary), match.range.first, match.range.last + 1)
                }
            }

            // Headers
            HEADER_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, protectedRanges)) {
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, color = colorScheme.primary),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // Lists (Unordered & Ordered)
            LIST_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, protectedRanges)) {
                    addStyle(SpanStyle(color = colorScheme.primary), match.range.first, match.range.last + 1)
                }
            }

            // Tables (simple markdown tables)
            TABLE_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, protectedRanges)) {
                    addStyle(
                        SpanStyle(background = colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // Horizontal Rules
            HR_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, protectedRanges)) {
                    addStyle(
                        SpanStyle(color = colorScheme.outline, fontWeight = FontWeight.Bold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // Bold (**text** and boundary-safe __text__)
            BOLD_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, protectedRanges)) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }
            }

            // Italic (*text* and boundary-safe _text_, avoiding snake_case and URLs)
            ITALIC_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, protectedRanges)) {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
                }
            }

            // Strikethrough
            STRIKETHROUGH_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, protectedRanges)) {
                    addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), match.range.first, match.range.last + 1)
                }
            }

            // Links
            LINK_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, protectedRanges)) {
                    addStyle(
                        SpanStyle(color = colorScheme.tertiary, textDecoration = TextDecoration.Underline),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // Inline code spans
            inlineCodeRanges.forEach { range ->
                addStyle(codeSpanStyle, range.first, range.last + 1)
            }

            // Fenced code blocks (both ``` and ~~~)
            fencedCodeRanges.forEach { range ->
                addStyle(codeSpanStyle, range.first, range.last + 1)
            }
        }

        return TransformedText(annotatedString, OffsetMapping.Identity)
    }

    companion object {
        private val OPENING_FENCE_REGEX = Regex("""^\s*([`~]{3,})(.*)$""")
        private val BLOCKQUOTE_REGEX = Regex("""(?m)^>\s+.*$""")
        private val HEADER_REGEX = Regex("""(?m)^#{1,6}\s+.*$""")
        private val BOLD_REGEX = Regex(
            """(?<!\*)\*\*(?!\s)[^*\n]+?(?<!\s)\*\*(?!\*)|(?<![_\p{L}\p{N}])__(?!\s)[^_\n]+?(?<!\s)__(?![_\p{L}\p{N}])"""
        )
        private val ITALIC_REGEX = Regex(
            """(?<![*\p{L}\p{N}])\*(?!\s|\*)[^*\n]+?(?<!\s|\*)\*(?![*\p{L}\p{N}])|(?<![_\p{L}\p{N}])_(?!\s|_)[^_\n]+?(?<!\s|_)_(?![_\p{L}\p{N}])"""
        )
        private val STRIKETHROUGH_REGEX = Regex("""~~(?!\s)[^~\n]+?(?<!\s)~~""")
        private val LINK_REGEX = Regex("""\[[^\]\n]+\]\([^)\n]+\)""")
        private val LIST_REGEX = Regex("""(?m)^\s*(?:[-+*]|\d+\.)\s+.*$""")
        private val TABLE_REGEX = Regex("""(?m)^\|.*\|$""")
        private val HR_REGEX = Regex("""(?m)^\s*(?:-{3,}|\*{3,}|_{3,})\s*$""")

        private fun findFencedCodeRanges(text: String): List<IntRange> {
            val ranges = mutableListOf<IntRange>()
            var inFence = false
            var fenceChar = '\u0000'
            var fenceLen = 0
            var blockStart = 0

            var i = 0
            val len = text.length
            while (i < len) {
                val lineStart = i
                while (i < len && text[i] != '\n' && text[i] != '\r') {
                    i++
                }
                val lineEnd = i
                val nextIndex = when {
                    i < len && text[i] == '\r' && i + 1 < len && text[i + 1] == '\n' -> i + 2
                    i < len -> i + 1
                    else -> i
                }

                val line = text.substring(lineStart, lineEnd)
                if (!inFence) {
                    val match = OPENING_FENCE_REGEX.matchEntire(line)
                    if (match != null) {
                        val fence = match.groupValues[1]
                        val info = match.groupValues[2]
                        val ch = fence[0]
                        if (fence.all { it == ch } && !(ch == '`' && info.contains('`'))) {
                            inFence = true
                            fenceChar = ch
                            fenceLen = fence.length
                            blockStart = lineStart
                        }
                    }
                } else if (isClosingFence(line, fenceChar, fenceLen)) {
                    if (lineEnd > blockStart) {
                        ranges.add(blockStart until lineEnd)
                    }
                    inFence = false
                }

                i = nextIndex
            }

            if (inFence && len > blockStart) {
                ranges.add(blockStart until len)
            }

            return ranges
        }

        private fun isClosingFence(line: String, expectedChar: Char, minLength: Int): Boolean {
            var i = 0
            while (i < line.length && (line[i] == ' ' || line[i] == '\t')) {
                i++
            }
            val start = i
            while (i < line.length && line[i] == expectedChar) {
                i++
            }
            if (i - start < minLength) return false
            while (i < line.length) {
                if (line[i] != ' ' && line[i] != '\t') return false
                i++
            }
            return true
        }

        private fun findInlineCodeRanges(text: String, fencedRanges: List<IntRange>): List<IntRange> {
            val ranges = mutableListOf<IntRange>()
            var fenceIdx = 0
            var i = 0
            val len = text.length

            while (i < len) {
                while (fenceIdx < fencedRanges.size && fencedRanges[fenceIdx].last < i) {
                    fenceIdx++
                }
                if (fenceIdx < fencedRanges.size && i in fencedRanges[fenceIdx]) {
                    i = fencedRanges[fenceIdx].last + 1
                    continue
                }

                if (text[i] == '\\' && i + 1 < len) {
                    i += 2
                    continue
                }

                if (text[i] == '`') {
                    val openStart = i
                    var tickCount = 0
                    while (i < len && text[i] == '`') {
                        tickCount++
                        i++
                    }
                    val limit = if (fenceIdx < fencedRanges.size) fencedRanges[fenceIdx].first else len
                    val closeStart = findMatchingBackticksInSegment(text, i, limit, tickCount)
                    if (closeStart != -1) {
                        val spanEnd = closeStart + tickCount
                        ranges.add(openStart until spanEnd)
                        i = spanEnd
                    }
                } else {
                    i++
                }
            }

            return ranges
        }

        private fun findMatchingBackticksInSegment(
            text: String,
            startIndex: Int,
            endLimit: Int,
            tickCount: Int
        ): Int {
            var i = startIndex
            while (i < endLimit) {
                if (text[i] == '\n' || text[i] == '\r') {
                    return -1
                }
                if (text[i] == '`') {
                    val runStart = i
                    while (i < endLimit && text[i] == '`') {
                        i++
                    }
                    if (i - runStart == tickCount) {
                        return runStart
                    }
                } else {
                    i++
                }
            }
            return -1
        }

        private fun mergeSortedRanges(a: List<IntRange>, b: List<IntRange>): List<IntRange> {
            if (a.isEmpty()) return b
            if (b.isEmpty()) return a
            val merged = ArrayList<IntRange>(a.size + b.size)
            var ia = 0
            var ib = 0
            while (ia < a.size && ib < b.size) {
                if (a[ia].first <= b[ib].first) {
                    merged.add(a[ia++])
                } else {
                    merged.add(b[ib++])
                }
            }
            while (ia < a.size) merged.add(a[ia++])
            while (ib < b.size) merged.add(b[ib++])
            return merged
        }

        private fun overlapsProtected(range: IntRange, protectedRanges: List<IntRange>): Boolean {
            if (protectedRanges.isEmpty()) return false
            // Binary search for the first range whose last >= range.first
            var low = 0
            var high = protectedRanges.lastIndex
            while (low <= high) {
                val mid = (low + high) ushr 1
                val candidate = protectedRanges[mid]
                when {
                    candidate.last < range.first -> low = mid + 1
                    candidate.first > range.last -> high = mid - 1
                    else -> return true
                }
            }
            return false
        }
    }
}
