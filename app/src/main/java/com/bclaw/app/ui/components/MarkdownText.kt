package com.bclaw.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography

/**
 * Renders markdown text as native Compose elements.
 *
 * Supported:
 * - Fenced code blocks (``` ... ```)
 * - Inline code (`code`)
 * - Bold (**text**)
 * - Italic (*text*)
 * - Headings (#, ##, ###)
 * - Bullet lists (- item, * item)
 * - Numbered lists (1. item)
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current

    val blocks = parseMarkdownBlocks(text)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (block in blocks) {
            when (block) {
                is MdBlock.CodeFence -> {
                    Text(
                        text = block.code,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceNear)
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                        style = typography.code,
                        color = colors.textPrimary,
                    )
                }
                is MdBlock.Paragraph -> {
                    if (block.lines.isNotEmpty()) {
                        val annotated = buildMarkdownAnnotated(
                            block.lines,
                            typography.body,
                            colors.textPrimary,
                            colors.surfaceNear,
                        )
                        Text(
                            text = annotated,
                            style = typography.body,
                            color = colors.textPrimary,
                        )
                    }
                }
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> typography.hero
                        2 -> typography.title
                        else -> typography.body.copy(fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = buildInlineAnnotated(
                            block.text,
                            style,
                            colors.textPrimary,
                            colors.surfaceNear,
                        ),
                        style = style,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                is MdBlock.ListItem -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = block.bullet,
                            style = typography.body,
                            color = colors.textDim,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = buildInlineAnnotated(
                                block.text,
                                typography.body,
                                colors.textPrimary,
                                colors.surfaceNear,
                            ),
                            style = typography.body,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// -- Block-level parsing --

private sealed interface MdBlock {
    data class CodeFence(val lang: String, val code: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class ListItem(val bullet: String, val text: String) : MdBlock
    data class Paragraph(val lines: List<String>) : MdBlock
}

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    var i = 0
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paragraphBuffer.toList()))
            paragraphBuffer.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block
        if (line.trimStart().startsWith("```")) {
            flushParagraph()
            val lang = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeFence(lang, codeLines.joinToString("\n")))
            i++ // skip closing ```
            continue
        }

        // Heading
        val headingMatch = headingRegex.matchEntire(line)
        if (headingMatch != null) {
            flushParagraph()
            val level = headingMatch.groupValues[1].length
            val headingText = headingMatch.groupValues[2]
            blocks.add(MdBlock.Heading(level.coerceAtMost(3), headingText))
            i++
            continue
        }

        // Bullet list (- item, * item)
        val bulletMatch = bulletListRegex.matchEntire(line)
        if (bulletMatch != null) {
            flushParagraph()
            blocks.add(MdBlock.ListItem("•", bulletMatch.groupValues[1]))
            i++
            continue
        }

        // Numbered list (1. item)
        val numberedMatch = numberedListRegex.matchEntire(line)
        if (numberedMatch != null) {
            flushParagraph()
            blocks.add(MdBlock.ListItem("${numberedMatch.groupValues[1]}.", numberedMatch.groupValues[2]))
            i++
            continue
        }

        // Empty line = paragraph break
        if (line.isBlank()) {
            flushParagraph()
            i++
            continue
        }

        // Regular text line
        paragraphBuffer.add(line)
        i++
    }
    flushParagraph()
    return blocks
}

private val headingRegex = Regex("""^(#{1,3})\s+(.+)$""")
private val bulletListRegex = Regex("""^\s*[-*]\s+(.+)$""")
private val numberedListRegex = Regex("""^\s*(\d+)\.\s+(.+)$""")

// -- Inline parsing --

private fun buildMarkdownAnnotated(
    lines: List<String>,
    bodyStyle: TextStyle,
    textColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    return buildInlineAnnotated(lines.joinToString("\n"), bodyStyle, textColor, codeBackground)
}

private fun buildInlineAnnotated(
    text: String,
    bodyStyle: TextStyle,
    textColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Inline code
                text[i] == '`' && i + 1 < text.length -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            fontSize = bodyStyle.fontSize * 0.9,
                        )) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Bold **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic *text* (single asterisk, not preceded by another *)
                text[i] == '*' && (i == 0 || text[i - 1] != '*') && i + 1 < text.length && text[i + 1] != '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i && (end + 1 >= text.length || text[end + 1] != '*')) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
