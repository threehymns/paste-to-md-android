package com.example

import com.example.data.AppSettings
import com.example.data.CodeFenceStyle
import com.example.data.EmphasisMarker
import com.example.data.HeadingStyle
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownConverter {

    private val OPENING_FENCE_REGEX = Regex("""^(\s*)([`~]{3,})(.*)$""")
    private val THEMATIC_BREAK_REGEX = Regex("""^\s*(?:>\s*)*\*(?:\s*\*){2,}\s*$""")
    private val LIST_BULLET_PREFIX_REGEX = Regex("""^(\s*(?:>\s*)*\*(?=\s))(.*)$""")

    private val BOLD_ITALIC_REGEX = Regex("""(?<!\*)\*\*\*(?!\s)(.+?)(?<!\s)\*\*\*(?!\*)""")
    private val BOLD_REGEX = Regex("""(?<!\*)\*\*(?!\s)(.+?)(?<!\s)\*\*(?!\*)""")
    private val ITALIC_REGEX = Regex("""(?<![*\p{L}\p{N}])\*(?!\s|\*)(.+?)(?<!\s|\*)\*(?![*\p{L}\p{N}])""")

    fun convert(html: String, settings: AppSettings): String {
        val options = MutableDataSet()

        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, settings.headingStyle == HeadingStyle.SETEXT)
        options.set(FlexmarkHtmlConverter.UNORDERED_LIST_DELIMITER, settings.bulletMarker.char)

        if (settings.preserveLineBreaks) {
            options.set(FlexmarkHtmlConverter.BR_AS_EXTRA_BLANK_LINES, false)
        }

        val converter = FlexmarkHtmlConverter.builder(options).build()
        val markdown = converter.convert(html)

        val useTildeFences = settings.codeFenceStyle == CodeFenceStyle.TILDE
        val useUnderscoreEmphasis = settings.emphasisMarker == EmphasisMarker.UNDERSCORE
        if (!useTildeFences && !useUnderscoreEmphasis) {
            return markdown
        }

        return postProcessMarkdown(
            markdown = markdown,
            useTildeFences = useTildeFences,
            useUnderscoreEmphasis = useUnderscoreEmphasis
        )
    }

    internal fun postProcessMarkdown(
        markdown: String,
        useTildeFences: Boolean,
        useUnderscoreEmphasis: Boolean
    ): String {
        if (markdown.isEmpty()) return markdown

        val result = StringBuilder(markdown.length)
        var inFencedCode = false
        var activeFenceChar = '\u0000'
        var activeFenceLen = 0

        var index = 0
        val length = markdown.length
        while (index < length) {
            val lineStart = index
            while (index < length && markdown[index] != '\n' && markdown[index] != '\r') {
                index++
            }
            val lineEnd = index
            val lineBreakEnd = when {
                index < length && markdown[index] == '\r' && index + 1 < length && markdown[index + 1] == '\n' -> index + 2
                index < length -> index + 1
                else -> index
            }

            val line = markdown.substring(lineStart, lineEnd)
            val lineBreak = markdown.substring(lineEnd, lineBreakEnd)
            index = lineBreakEnd

            if (!inFencedCode) {
                val fenceMatch = parseOpeningFence(line)
                if (fenceMatch != null) {
                    inFencedCode = true
                    activeFenceChar = fenceMatch.fenceChar
                    activeFenceLen = fenceMatch.fenceLength
                    if (useTildeFences && activeFenceChar == '`') {
                        result.append(fenceMatch.indent)
                        repeat(activeFenceLen) { result.append('~') }
                        result.append(fenceMatch.infoString)
                    } else {
                        result.append(line)
                    }
                    result.append(lineBreak)
                    continue
                }

                if (useUnderscoreEmphasis) {
                    result.append(rewriteEmphasisOutsideCode(line))
                } else {
                    result.append(line)
                }
                result.append(lineBreak)
            } else {
                val closingFence = parseClosingFence(line, activeFenceChar, activeFenceLen)
                if (closingFence != null) {
                    inFencedCode = false
                    if (useTildeFences && activeFenceChar == '`') {
                        result.append(closingFence.indent)
                        repeat(closingFence.fenceLength) { result.append('~') }
                        result.append(closingFence.trailing)
                    } else {
                        result.append(line)
                    }
                } else {
                    // Inside a fenced code block: preserve content verbatim.
                    result.append(line)
                }
                result.append(lineBreak)
            }
        }

        return result.toString()
    }

    private data class OpeningFence(
        val indent: String,
        val fenceChar: Char,
        val fenceLength: Int,
        val infoString: String
    )

    private data class ClosingFence(
        val indent: String,
        val fenceLength: Int,
        val trailing: String
    )

    private fun parseOpeningFence(line: String): OpeningFence? {
        val match = OPENING_FENCE_REGEX.matchEntire(line) ?: return null
        val indent = match.groupValues[1]
        val fence = match.groupValues[2]
        val infoString = match.groupValues[3]
        val firstChar = fence[0]
        if (fence.any { it != firstChar }) return null
        // CommonMark: backtick fence info strings cannot contain backtick characters.
        if (firstChar == '`' && infoString.contains('`')) return null
        return OpeningFence(indent, firstChar, fence.length, infoString)
    }

    private fun parseClosingFence(line: String, expectedChar: Char, minLength: Int): ClosingFence? {
        var i = 0
        while (i < line.length && (line[i] == ' ' || line[i] == '\t')) {
            i++
        }
        val indent = line.substring(0, i)
        val fenceStart = i
        while (i < line.length && line[i] == expectedChar) {
            i++
        }
        val fenceLen = i - fenceStart
        if (fenceLen < minLength) return null
        val trailing = line.substring(i)
        if (trailing.any { it != ' ' && it != '\t' }) return null
        return ClosingFence(indent, fenceLen, trailing)
    }

    private fun rewriteEmphasisOutsideCode(line: String): String {
        if (!line.contains('*')) return line
        if (THEMATIC_BREAK_REGEX.matches(line)) return line

        val listMatch = LIST_BULLET_PREFIX_REGEX.matchEntire(line)
        val prefix = listMatch?.groupValues?.get(1) ?: ""
        val body = listMatch?.groupValues?.get(2) ?: line
        if (!body.contains('*')) return line

        // Pick two private-use sentinel chars guaranteed not to appear in the line.
        var openSentinel = '\uE000'
        while (body.indexOf(openSentinel) >= 0 && openSentinel < '\uF8FE') {
            openSentinel++
        }
        var closeSentinel = (openSentinel.code + 1).toChar()
        while (body.indexOf(closeSentinel) >= 0 && closeSentinel < '\uF8FF') {
            closeSentinel++
        }

        val protectedTokens = mutableListOf<String>()
        val masked = StringBuilder(body.length)

        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                val tokenIndex = protectedTokens.size
                protectedTokens.add(body.substring(i, i + 2))
                masked.append(openSentinel).append(tokenIndex).append(closeSentinel)
                i += 2
            } else if (c == '`') {
                var tickCount = 0
                while (i + tickCount < body.length && body[i + tickCount] == '`') {
                    tickCount++
                }
                val closeStart = findMatchingBackticks(body, i + tickCount, tickCount)
                if (closeStart != -1) {
                    val spanEnd = closeStart + tickCount
                    val tokenIndex = protectedTokens.size
                    protectedTokens.add(body.substring(i, spanEnd))
                    masked.append(openSentinel).append(tokenIndex).append(closeSentinel)
                    i = spanEnd
                } else {
                    // Unclosed backtick run: keep literal backticks.
                    masked.append(body, i, i + tickCount)
                    i += tickCount
                }
            } else {
                masked.append(c)
                i++
            }
        }

        var rewritten = masked.toString()
        rewritten = BOLD_ITALIC_REGEX.replace(rewritten, "___$1___")
        rewritten = BOLD_REGEX.replace(rewritten, "__$1__")
        rewritten = ITALIC_REGEX.replace(rewritten, "_$1_")

        if (protectedTokens.isEmpty()) {
            return prefix + rewritten
        }

        val restored = StringBuilder(prefix.length + body.length)
        restored.append(prefix)
        var r = 0
        while (r < rewritten.length) {
            if (rewritten[r] == openSentinel) {
                val closeIdx = rewritten.indexOf(closeSentinel, r + 1)
                if (closeIdx > r + 1) {
                    val tokenIndex = rewritten.substring(r + 1, closeIdx).toIntOrNull()
                    if (tokenIndex != null && tokenIndex in protectedTokens.indices) {
                        restored.append(protectedTokens[tokenIndex])
                        r = closeIdx + 1
                        continue
                    }
                }
            }
            restored.append(rewritten[r])
            r++
        }
        return restored.toString()
    }

    private fun findMatchingBackticks(text: String, startIndex: Int, tickCount: Int): Int {
        var i = startIndex
        while (i < text.length) {
            if (text[i] == '`') {
                val runStart = i
                while (i < text.length && text[i] == '`') {
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
}
