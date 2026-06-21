package com.rainnya.chat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rainnya.chat.data.model.ChatMessage
import com.rainnya.chat.data.model.MessageRole
import com.rainnya.chat.ui.theme.RainnyaTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CHAR_DELAY_MS = 8L
private const val COLLAPSE_THRESHOLD = 300

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onCopy: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == MessageRole.USER

    if (message.role == MessageRole.SYSTEM) {
        SystemMessageBubble(message = message, modifier = modifier)
        return
    }

    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Aavatar(text = "AI", modifier = Modifier.padding(end = 6.dp, bottom = 2.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            val bubbleShape = if (isUser) {
                RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)
            } else {
                RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
            }

            Column(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = { onCopy(message.content) },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                BubbleContent(
                    message = message,
                    textColor = textColor,
                )

                val timeStr = timestampString(message.timestamp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.4f),
                    )
                }
            }
        }

        if (isUser) {
            Aavatar(text = "我", modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
        }
    }
}

@Composable
private fun BubbleContent(
    message: ChatMessage,
    textColor: Color,
) {
    val isStreaming = message.streaming

    if (isStreaming && message.content.isEmpty()) {
        PulseLoadingBar(textColor = textColor)
        return
    }

    var expanded by remember(message.id) { mutableStateOf(false) }
    val shouldCollapse = !isStreaming && message.content.length > COLLAPSE_THRESHOLD
    val displayText = if (shouldCollapse && !expanded) {
        message.content.take(COLLAPSE_THRESHOLD)
    } else {
        message.content
    }

    if (isStreaming) {
        StreamingText(
            fullContent = displayText,
            textColor = textColor,
            messageId = message.id,
        )
    } else {
        MarkdownText(
            text = displayText,
            color = textColor,
        )
    }

    if (shouldCollapse) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = if (expanded) "收起" else "显示全部 (${message.content.length}字)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StreamingText(
    fullContent: String,
    textColor: Color,
    messageId: String,
) {
    var visibleLength by remember(messageId) { mutableIntStateOf(0) }

    LaunchedEffect(fullContent, messageId) {
        while (visibleLength < fullContent.length) {
            visibleLength++
            delay(CHAR_DELAY_MS)
        }
    }

    val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha",
    )

    val end = visibleLength.coerceAtMost(fullContent.length)
    Text(
        text = buildAnnotatedString {
            append(fullContent.substring(0, end))
            withStyle(SpanStyle(color = textColor.copy(alpha = cursorAlpha))) {
                append("▎")
            }
        },
        color = textColor,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun PulseLoadingBar(textColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            textColor.copy(alpha = alpha * 0.3f),
                            textColor.copy(alpha = alpha * 0.8f),
                            textColor.copy(alpha = alpha * 0.3f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun Aavatar(text: String, modifier: Modifier = Modifier) {
    val bg = if (text == "我") {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val fg = if (text == "我") {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
            color = fg,
        )
    }
}

@Composable
private fun SystemMessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

private fun timestampString(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val sdf = if (now - timestamp < 86400000L) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    } else {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    }
    return sdf.format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
fun MessageBubbleUserPreview() {
    RainnyaTheme {
        MessageBubble(
            message = ChatMessage(
                id = "1", content = "你好，今天天气怎么样？", role = MessageRole.USER,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MessageBubbleAssistantPreview() {
    RainnyaTheme {
        MessageBubble(
            message = ChatMessage(
                id = "2",
                content = "今天天气很好，适合出门散步。可以试试去公园走走。",
                role = MessageRole.ASSISTANT,
            ),
        )
    }
}
