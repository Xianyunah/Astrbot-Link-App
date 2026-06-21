package com.rainnya.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rainnya.chat.data.repository.ConnectionState
import com.rainnya.chat.ui.theme.RainnyaTheme

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    connectionState: ConnectionState = ConnectionState.CONNECTED,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val inputEnabled = connectionState == ConnectionState.CONNECTED
    val canSend = inputEnabled && text.isNotBlank() && !isStreaming

    val placeholderText = when {
        connectionState == ConnectionState.DISCONNECTED -> "未连接，请在设置中配置"
        connectionState == ConnectionState.CONNECTING -> "连接中…"
        connectionState == ConnectionState.ERROR -> "连接失败，点击重连"
        else -> "发消息给 Rainnya…"
    }

    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (inputEnabled || isFocused) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                },
            )
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { if (inputEnabled) onTextChange(it) },
            enabled = inputEnabled,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { isFocused = it.isFocused },
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.15f,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = false,
            maxLines = 5,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.heightIn(min = 28.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholderText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.15f,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (inputEnabled) 0.5f else 0.35f,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )

        AnimatedVisibility(
            visible = canSend,
            enter = scaleIn(animationSpec = tween(200)) + fadeIn(tween(150)),
            exit = scaleOut(animationSpec = tween(150)) + fadeOut(tween(100)),
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .then(
                        if (canSend) {
                            Modifier.clickable { onSend() }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "发送",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatInputBarConnectedPreview() {
    RainnyaTheme {
        ChatInputBar(
            text = "hello",
            onTextChange = {},
            onSend = {},
            connectionState = ConnectionState.CONNECTED,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatInputBarStreamingPreview() {
    RainnyaTheme {
        ChatInputBar(
            text = "",
            onTextChange = {},
            onSend = {},
            connectionState = ConnectionState.CONNECTED,
            isStreaming = true,
        )
    }
}
