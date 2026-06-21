package com.rainnya.chat.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rainnya.chat.data.repository.ConnectionState
import com.rainnya.chat.ui.components.ChatInputBar
import com.rainnya.chat.ui.components.MessageBubble
import com.rainnya.chat.ui.theme.RainnyaTheme
import com.rainnya.chat.ui.util.rememberImeHeightPx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_VISIBLE = 200
private const val LOAD_MORE_STEP = 100

private val suggestions = listOf(
    "介绍一下你自己",
    "今天有什么新闻？",
    "帮我写一首诗",
    "什么是机器学习？",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    scaffoldPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val navBarDp = scaffoldPadding.calculateBottomPadding()
    val density = LocalDensity.current
    val imeHeightPx = rememberImeHeightPx()
    val bottomDp = if (imeHeightPx > 0) {
        with(density) { imeHeightPx.toDp() }
    } else {
        navBarDp
    }

    val currentSession = viewModel.repository.currentSession()
    val totalMessages = state.messages.size
    var visibleCount by remember(totalMessages) { mutableIntStateOf(MAX_VISIBLE) }
    val canLoadMore = totalMessages > MAX_VISIBLE && visibleCount < totalMessages
    val displayMessages = if (canLoadMore || totalMessages > MAX_VISIBLE) {
        state.messages.takeLast(visibleCount.coerceAtMost(totalMessages))
    } else {
        state.messages
    }

    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisible.index >= info.totalItemsCount - 2 &&
                lastVisible.offset + lastVisible.size <= info.viewportEndOffset + 1
        }
    }

    LaunchedEffect(totalMessages, currentSession?.sessionId) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    LaunchedEffect(isAtBottom) {
        if (!isAtBottom) return@LaunchedEffect
        snapshotFlow {
            state.messages.lastOrNull()?.let { if (it.streaming) it.content.length else null }
        }.collect { len ->
            if (len != null && displayMessages.isNotEmpty()) {
                listState.scrollToItem(displayMessages.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = bottomDp),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = currentSession?.displayName ?: "Rainnya",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.isStreaming) {
                        Text(
                            text = "AI 正在回复…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            actions = {
                ConnectionDot(
                    state = state.connectionState,
                    onReconnect = { viewModel.reconnect() },
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { viewModel.newSession() }) {
                    Icon(Icons.Rounded.Add, contentDescription = "新会话")
                }
            },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                EmptyChat(onSuggestion = { viewModel.sendMessage(it) })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    if (canLoadMore) {
                        item(key = "__load_more__") {
                            LoadMoreHeader(
                                totalCount = totalMessages,
                                onClick = {
                                    visibleCount =
                                        (visibleCount + LOAD_MORE_STEP).coerceAtMost(totalMessages)
                                },
                            )
                        }
                    }

                    items(displayMessages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onCopy = { text ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        message = "已复制",
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                        )
                    }

                    item(key = "__bottom_spacer__") {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
        }

        ChatInputBar(
            text = state.inputText,
            onTextChange = viewModel::updateInputText,
            onSend = { viewModel.sendMessage(state.inputText) },
            connectionState = state.connectionState,
            isStreaming = state.isStreaming,
        )
    }
}

@Composable
private fun LoadMoreHeader(totalCount: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                text = "查看更多历史消息（共${totalCount}条）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun EmptyChat(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Stars,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "有什么可以帮助你的？",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            suggestions.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { text ->
                        SuggestionCard(
                            text = text,
                            onClick = { onSuggestion(text) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ConnectionDot(
    state: ConnectionState,
    onReconnect: () -> Unit,
) {
    val dotColor = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    val alpha by if (state == ConnectionState.CONNECTING) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    if (state == ConnectionState.ERROR || state == ConnectionState.DISCONNECTED) {
        IconButton(onClick = onReconnect, modifier = Modifier.size(32.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .alpha(alpha)
                    .background(dotColor),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .alpha(alpha)
                .background(dotColor),
        )
    }
}

@Composable
fun ChatScreenPreview() {
    RainnyaTheme {
        ChatScreen()
    }
}
