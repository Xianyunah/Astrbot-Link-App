package com.rainnya.chat.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rainnya.chat.data.settings.AppSettings
import com.rainnya.chat.ui.chat.ChatScreen
import com.rainnya.chat.ui.chat.ChatViewModel
import com.rainnya.chat.ui.sessions.SessionsScreen
import com.rainnya.chat.ui.settings.SettingsScreen

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val navItems = listOf(
    BottomNavItem("聊天", Icons.Filled.Chat, Icons.Outlined.Chat),
    BottomNavItem("会话", Icons.Filled.Forum, Icons.Outlined.Forum),
    BottomNavItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun AppNavigation(settings: AppSettings) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val chatViewModel: ChatViewModel = viewModel()
    val repository = chatViewModel.repository

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedIndex == index) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedIndex) {
            0 -> ChatScreen(
                scaffoldPadding = padding,
                viewModel = chatViewModel,
            )
            1 -> SessionsScreen(
                repository = repository,
                onSessionClick = { sessionId ->
                    chatViewModel.switchSession(sessionId)
                    selectedIndex = 0
                },
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            )
            2 -> SettingsScreen(
                settings = settings,
                repository = repository,
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            )
        }
    }
}
