package com.theveloper.aura.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.NotesConfig

@Composable
fun InterpretedNotesComponent(
    config: NotesConfig,
    modifier: Modifier = Modifier
) {
    val blocks = remember(config.text) { parseMarkdownBlocks(config.text) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                ComponentPill(if (config.isMarkdown) "Markdown render" else "Plain text")
            }

            if (config.text.isBlank()) {
                Text(
                    text = "No notes yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (config.isMarkdown) {
                MarkdownContent(
                    blocks = blocks,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = config.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MarkdownContent(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Text(
                        text = buildMarkdownAnnotatedString(
                            text = block.text,
                            linkColor = scheme.primary,
                            codeBackground = scheme.surfaceContainerHighest,
                            codeColor = scheme.onSurface
                        ),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildMarkdownAnnotatedString(
                            text = block.text,
                            linkColor = scheme.primary,
                            codeBackground = scheme.surfaceContainerHighest,
                            codeColor = scheme.onSurface
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        block.items.forEach { item ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = buildMarkdownAnnotatedString(
                                        text = item,
                                        linkColor = scheme.primary,
                                        codeBackground = scheme.surfaceContainerHighest,
                                        codeColor = scheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.Quote -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    ) {
                        Text(
                            text = buildMarkdownAnnotatedString(
                                text = block.text,
                                linkColor = scheme.primary,
                                codeBackground = scheme.surfaceContainerHighest,
                                codeColor = scheme.onSurface
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    if (text.isBlank()) return emptyList()

    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index].trimEnd()

        if (line.isBlank()) {
            index++
            continue
        }

        when {
            line.startsWith("# ") -> {
                blocks += MarkdownBlock.Heading(level = 1, text = line.removePrefix("# ").trim())
                index++
            }
            line.startsWith("## ") -> {
                blocks += MarkdownBlock.Heading(level = 2, text = line.removePrefix("## ").trim())
                index++
            }
            line.startsWith("### ") -> {
                blocks += MarkdownBlock.Heading(level = 3, text = line.removePrefix("### ").trim())
                index++
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index].trim()
                    if (!(current.startsWith("- ") || current.startsWith("* "))) break
                    items += current.drop(2).trim()
                    index++
                }
                blocks += MarkdownBlock.BulletList(items)
            }
            line.startsWith("> ") -> {
                val quoteLines = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index].trim()
                    if (!current.startsWith("> ")) break
                    quoteLines += current.removePrefix("> ").trim()
                    index++
                }
                blocks += MarkdownBlock.Quote(quoteLines.joinToString(separator = "\n"))
            }
            else -> {
                val paragraphLines = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index].trim()
                    if (
                        current.isBlank() ||
                        current.startsWith("# ") ||
                        current.startsWith("## ") ||
                        current.startsWith("### ") ||
                        current.startsWith("- ") ||
                        current.startsWith("* ") ||
                        current.startsWith("> ")
                    ) {
                        break
                    }
                    paragraphLines += current
                    index++
                }
                blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString(separator = " "))
            }
        }
    }

    return blocks
}

private fun buildMarkdownAnnotatedString(
    text: String,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) -> {
                    val end = text.indexOf("**", startIndex = index + 2)
                    if (end > index) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(text.substring(index + 2, end))
                        pop()
                        index = end + 2
                    } else {
                        append(text[index])
                        index++
                    }
                }
                text.startsWith("*", index) -> {
                    val end = text.indexOf('*', startIndex = index + 1)
                    if (end > index) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(index + 1, end))
                        pop()
                        index = end + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }
                text.startsWith("`", index) -> {
                    val end = text.indexOf('`', startIndex = index + 1)
                    if (end > index) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                                color = codeColor
                            )
                        )
                        append(text.substring(index + 1, end))
                        pop()
                        index = end + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }
                text.startsWith("[", index) -> {
                    val labelEnd = text.indexOf(']', startIndex = index + 1)
                    val urlStart = if (labelEnd != -1 && text.getOrNull(labelEnd + 1) == '(') labelEnd + 2 else -1
                    val urlEnd = if (urlStart != -1) text.indexOf(')', startIndex = urlStart) else -1

                    if (labelEnd != -1 && urlStart != -1 && urlEnd != -1) {
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        append(text.substring(index + 1, labelEnd))
                        pop()
                        index = urlEnd + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }
                else -> {
                    append(text[index])
                    index++
                }
            }
        }
    }
}
