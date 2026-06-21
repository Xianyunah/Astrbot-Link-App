package com.rainnya.chat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rainnya.chat.data.model.ChatMessage
import com.rainnya.chat.data.model.MessageRole
import com.rainnya.chat.ui.theme.RainnyaTheme

@Composable
fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    if (isSystem) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val bgColor = if (isUser)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerHighest

        val textColor = if (isUser)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurface

        val shape = if (isUser)
            RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
        else
            RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

        val cursorAlpha = if (message.streaming) {
            val transition = rememberInfiniteTransition(label = "cursor")
            val alpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "cursorAlpha",
            )
            alpha
        } else {
            0f
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = if (message.streaming) {
                    buildAnnotatedString {
                        append(message.content)
                        withStyle(SpanStyle(color = textColor.copy(alpha = cursorAlpha))) {
                            append("▌")
                        }
                    }
                } else {
                    AnnotatedString(message.content)
                },
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MessageBubbleUserPreview() {
    RainnyaTheme {
        MessageBubble(
            message = ChatMessage(
                id = "1",
                content = "你好，今天天气怎么样？",
                role = MessageRole.USER,
            )
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
                content = "今天天气很好，适合出门散步。",
                role = MessageRole.ASSISTANT,
            )
        )
    }
}
