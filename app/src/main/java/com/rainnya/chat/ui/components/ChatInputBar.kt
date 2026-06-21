package com.rainnya.chat.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier,
) {
    val enabled = connectionState == ConnectionState.CONNECTED
    val placeholderText = when (connectionState) {
        ConnectionState.DISCONNECTED -> "未连接，请在设置中配置"
        ConnectionState.CONNECTING -> "连接中…"
        ConnectionState.ERROR -> "连接失败"
        ConnectionState.CONNECTED -> "输入消息…"
    }

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        enabled = enabled,
        placeholder = { Text(placeholderText) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        ),
        trailingIcon = {
            IconButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "发送",
                    tint = if (enabled && text.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        singleLine = true,
    )
}

@Preview(showBackground = true)
@Composable
fun ChatInputBarConnectedPreview() {
    RainnyaTheme {
        ChatInputBar(text = "hello", onTextChange = {}, onSend = {}, connectionState = ConnectionState.CONNECTED)
    }
}

@Preview(showBackground = true)
@Composable
fun ChatInputBarDisconnectedPreview() {
    RainnyaTheme {
        ChatInputBar(text = "", onTextChange = {}, onSend = {}, connectionState = ConnectionState.DISCONNECTED)
    }
}
