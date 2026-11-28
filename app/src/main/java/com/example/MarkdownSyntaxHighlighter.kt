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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

data class MarkdownSyntaxHighlighter(
    private val colorScheme: ColorScheme,
    private val baseFontSize: TextUnit = DEFAULT_BASE_FONT_SIZE_SP.sp,
    private val codeFontFamily: FontFamily = FontFamily.Monospace
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val plainText = text.text
        if (plainText.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val fencedCodeRanges = findFencedCodeRanges(plainText)
        val inlineCodeRanges = findInlineCodeRanges(plainText, fencedCodeRanges)
        val protectedRanges = mergeSortedRanges(fencedCodeRanges, inlineCodeRanges)
        val setextHeadings = findSetextHeadings(plainText, fencedCodeRanges)
        val setextUnderlineRanges = setextHeadings.map { it.underlineRange }

        val annotatedString = buildAnnotatedString {
            append(plainText)

            val codeSpanStyle = SpanStyle(
                background = colorScheme.surfaceVariant,
                color = colorScheme.onSurfaceVariant,
                fontFamily = codeFontFamily
            )

            // Blockquotes
            BLOCKQUOTE_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, protectedRanges)) {
                    addStyle(SpanStyle(color = colorScheme.secondary), match.range.first, match.range.last + 1)
                }
            }

            // ATX Headers (# through ######)
            HEADER_REGEX.findAll(plainText).forEach { match ->
                if (!overlapsProtected(match.range, fencedCodeRanges)) {
                    val level = match.groupValues[1].length
                    addStyle(
                        headingSpanStyle(level),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // Setext Headers (Title\n=== and Subtitle\n---)
            setextHeadings.forEach { heading ->
                addStyle(
                    headingSpanStyle(heading.level),
                    heading.titleRange.first,
                    heading.titleRange.last + 1
                )
                addStyle(
                    SpanStyle(
                        color = colorScheme.primary.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Bold
                    ),
                    heading.underlineRange.first,
                    heading.underlineRange.last + 1
                )
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
                if (
                    !overlapsProtected(match.range, protectedRanges) &&
                    !overlapsProtected(match.range, setextUnderlineRanges)
                ) {
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

    private fun headingSpanStyle(level: Int): SpanStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
        color = colorScheme.primary,
        fontSize = headingFontSize(level),
        letterSpacing = 0.sp
    )

    private fun headingFontSize(level: Int): TextUnit {
        val base = if (baseFontSize.isSp) baseFontSize.value else DEFAULT_BASE_FONT_SIZE_SP
        val scale = when (level) {
            1 -> 1.625f
            2 -> 1.4375f
            3 -> 1.25f
            4 -> 1.1875f
            5 -> 1.125f
            else -> 1.0625f
        }
        return (base * scale).sp
    }

    private data class SetextHeading(
        val titleRange: IntRange,
        val underlineRange: IntRange,
        val level: Int
    )

    companion object {
        private const val DEFAULT_BASE_FONT_SIZE_SP = 16f
        private val OPENING_FENCE_REGEX = Regex("""^\s*([`~]{3,})(.*)$""")
        private val BLOCKQUOTE_REGEX = Regex("""(?m)^>\s+.*$""")
        private val HEADER_REGEX = Regex("""(?m)^[ ]{0,3}(#{1,6})[ \t]+.*$""")
        private val SETEXT_UNDERLINE_REGEX = Regex("""^[ ]{0,3}(={2,}|-{2,})[ \t]*$""")
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
        private val HR_REGEX = Regex("""(?m)^[ \t]*(?:-{3,}|\*{3,}|_{3,})[ \t]*$""")

        private fun findSetextHeadings(
            text: String,
            fencedCodeRanges: List<IntRange>
        ): List<SetextHeading> {
            val headings = mutableListOf<SetextHeading>()
            var prevRange: IntRange? = null
            var prevLine: String? = null

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
                val lineRange = if (lineEnd > lineStart) lineStart until lineEnd else null
                val underlineMatch = SETEXT_UNDERLINE_REGEX.matchEntire(line)

                val candidateTitle = prevLine
                val candidateRange = prevRange
                if (
                    underlineMatch != null &&
                    lineRange != null &&
                    candidateTitle != null &&
                    candidateRange != null &&
                    !overlapsProtected(candidateRange, fencedCodeRanges) &&
                    !overlapsProtected(lineRange, fencedCodeRanges) &&
                    isSetextTitleCandidate(candidateTitle)
                ) {
                    val level = if (underlineMatch.groupValues[1][0] == '=') 1 else 2
                    headings.add(SetextHeading(candidateRange, lineRange, level))
                    prevLine = null
                    prevRange = null
                } else {
                    prevLine = line
                    prevRange = lineRange
                }

                i = nextIndex
            }

            return headings
        }

        private fun isSetextTitleCandidate(line: String): Boolean {
            if (line.isBlank()) return false
            if (HEADER_REGEX.matches(line)) return false
            if (BLOCKQUOTE_REGEX.matches(line)) return false
            if (LIST_REGEX.matches(line)) return false
            if (TABLE_REGEX.matches(line)) return false
            if (HR_REGEX.matches(line)) return false
            if (SETEXT_UNDERLINE_REGEX.matches(line)) return false
            if (OPENING_FENCE_REGEX.matches(line)) return false
            return true
        }

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
