package com.rainnya.chat.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rainnya.chat.data.repository.ConnectionState
import com.rainnya.chat.ui.theme.RainnyaTheme

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    pendingImageUri: Uri? = null,
    onRemoveImage: () -> Unit = {},
    uploading: Boolean = false,
    uploadProgress: Float = 0f,
    connectionState: ConnectionState = ConnectionState.CONNECTED,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val inputEnabled = connectionState == ConnectionState.CONNECTED
    val canSend = inputEnabled && (text.isNotBlank() || pendingImageUri != null) && !isStreaming && !uploading

    val placeholderText = when {
        connectionState == ConnectionState.DISCONNECTED -> "未连接，请在设置中配置"
        connectionState == ConnectionState.CONNECTING -> "连接中…"
        connectionState == ConnectionState.ERROR -> "连接失败，点击重连"
        else -> "发消息给 Rainnya…"
    }

    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (pendingImageUri != null) {
            ImagePreviewThumbnail(
                uri = pendingImageUri,
                onRemove = onRemoveImage,
            )
        }

        Row(
            modifier = Modifier
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
                .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPickImage,
                enabled = inputEnabled && !uploading,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "选择图片",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

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

            if (uploading) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            } else {
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
    }
}

@Composable
private fun ImagePreviewThumbnail(
    uri: Uri,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "待发送图片",
                modifier = Modifier.fillMaxWidth().height(64.dp),
                contentScale = ContentScale.Crop,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "移除图片",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatInputBarConnectedPreview() {
    RainnyaTheme {
        ChatInputBar(
            text = "hello", onTextChange = {}, onSend = {}, onPickImage = {},
            connectionState = ConnectionState.CONNECTED,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatInputBarStreamingPreview() {
    RainnyaTheme {
        ChatInputBar(
            text = "", onTextChange = {}, onSend = {}, onPickImage = {},
            connectionState = ConnectionState.CONNECTED, isStreaming = true,
        )
    }
}
