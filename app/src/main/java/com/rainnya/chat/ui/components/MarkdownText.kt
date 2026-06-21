package com.rainnya.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

private val CODE_BLOCK_REGEX = Pattern.compile(
    "```(\\w*)\\s*\\r?\\n(.*?)```", Pattern.DOTALL
)
private val BOLD_REGEX = Pattern.compile("\\*\\*(.+?)\\*\\*")
private val ITALIC_REGEX = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
private val INLINE_CODE_REGEX = Pattern.compile("`(.+?)`")
private val LINK_REGEX = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)")

private sealed class MdSegment {
    data class Code(val language: String, val code: String) : MdSegment()
    data class Text(val content: String) : MdSegment()
}

@Composable
fun MarkdownText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    val segments = remember(text) { parseMarkdownSegments(text) }

    Column(modifier = modifier) {
        for (segment in segments) {
            when (segment) {
                is MdSegment.Code -> CodeBlock(segment.code)
                is MdSegment.Text -> InlineMarkdown(
                    text = segment.content,
                    style = style,
                    color = color,
                    linkColor = linkColor,
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh

    Text(
        text = code,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(12.dp),
        lineHeight = 20.sp,
    )
}

@Composable
private fun InlineMarkdown(
    text: String,
    style: TextStyle,
    color: Color,
    linkColor: Color,
) {
    val codeColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, style, color, linkColor, codeColor) {
        parseInlineMarkdown(text, style, color, linkColor, codeColor)
    }
    Text(text = annotated, style = style)
}

private fun parseMarkdownSegments(text: String): List<MdSegment> {
    val segments = mutableListOf<MdSegment>()
    val matcher = CODE_BLOCK_REGEX.matcher(text)
    var lastEnd = 0

    while (matcher.find()) {
        if (matcher.start() > lastEnd) {
            segments.add(MdSegment.Text(text.substring(lastEnd, matcher.start())))
        }
        val lang = matcher.group(1).trim()
        val code = matcher.group(2).trimEnd()
        segments.add(MdSegment.Code(lang, code))
        lastEnd = matcher.end()
    }

    if (lastEnd < text.length) {
        segments.add(MdSegment.Text(text.substring(lastEnd)))
    }

    return segments
}

private fun parseInlineMarkdown(
    text: String,
    baseStyle: TextStyle,
    baseColor: Color,
    linkColor: Color,
    codeColor: Color,
): AnnotatedString = buildAnnotatedString {
    var remaining = text
    val fontSizeSp = baseStyle.fontSize.value
    val monospaceSize = if (fontSizeSp > 0f) (fontSizeSp * 0.9f).sp else 14.sp

    while (remaining.isNotEmpty()) {
        val boldMatcher = BOLD_REGEX.matcher(remaining)
        val italicMatcher = ITALIC_REGEX.matcher(remaining)
        val codeMatcher = INLINE_CODE_REGEX.matcher(remaining)
        val linkMatcher = LINK_REGEX.matcher(remaining)

        val boldStart = if (boldMatcher.find()) boldMatcher.start() else Int.MAX_VALUE
        val italicStart = if (italicMatcher.find()) italicMatcher.start() else Int.MAX_VALUE
        val codeStart = if (codeMatcher.find()) codeMatcher.start() else Int.MAX_VALUE
        val linkStart = if (linkMatcher.find()) linkMatcher.start() else Int.MAX_VALUE

        val earliest = minOf(boldStart, italicStart, codeStart, linkStart)

        if (earliest == Int.MAX_VALUE) {
            append(remaining)
            break
        }

        if (earliest > 0) {
            append(remaining.substring(0, earliest))
            remaining = remaining.substring(earliest)
            continue
        }

        when (earliest) {
            boldStart -> {
                val m = BOLD_REGEX.matcher(remaining)
                m.find()
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                    append(m.group(1))
                }
                remaining = remaining.substring(m.end())
            }
            italicStart -> {
                val m = ITALIC_REGEX.matcher(remaining)
                m.find()
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                    append(m.group(1))
                }
                remaining = remaining.substring(m.end())
            }
            codeStart -> {
                val m = INLINE_CODE_REGEX.matcher(remaining)
                m.find()
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = monospaceSize,
                    color = codeColor,
                )) {
                    append(m.group(1))
                }
                remaining = remaining.substring(m.end())
            }
            linkStart -> {
                val m = LINK_REGEX.matcher(remaining)
                m.find()
                val linkText = m.group(1)
                withStyle(SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                )) {
                    append(linkText ?: m.group(2))
                }
                remaining = remaining.substring(m.end())
            }
        }
    }

    if (length > 0) {
        addStyle(SpanStyle(color = baseColor), 0, length)
    }
}
