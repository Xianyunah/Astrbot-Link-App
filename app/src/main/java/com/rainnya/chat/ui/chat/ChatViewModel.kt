package com.rainnya.chat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rainnya.chat.data.repository.ChatRepository
import com.rainnya.chat.data.repository.ConnectionState
import com.rainnya.chat.data.settings.AppSettings
import com.rainnya.chat.data.websocket.WsEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<com.rainnya.chat.data.model.ChatMessage> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val inputText: String = "",
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    val repository = ChatRepository(settings)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.messages.collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
        viewModelScope.launch {
            repository.wsEvents.collect { event ->
                repository.handleWsEvent(event)
            }
        }
        if (settings.isConfigured) {
            repository.connect()
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        repository.sendMessage(text.trim())
        _uiState.value = _uiState.value.copy(inputText = "")
    }

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun newSession() {
        repository.newSession()
    }

    fun connect() {
        repository.connect()
    }

    fun disconnect() {
        repository.disconnect()
    }

    override fun onCleared() {
        repository.disconnect()
        super.onCleared()
    }
}
