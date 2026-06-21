# Rainnya

Android AI 聊天客户端，通过 WebSocket 连接 AstrBot 服务端，支持流式回复、会话管理、本地持久化。

## 特性

- **WebSocket 实时通信** — 全双工流式传输，逐字显示 AI 回复
- **会话管理** — 多会话切换、重命名、删除
- **本地持久化** — Room 数据库，App 杀死重启后自动恢复
- **Material3 动态取色** — Android 12+ 自动适配主题色
- **Markdown 渲染** — AI 回复中的粗体、代码块、链接等
- **连接状态指示** — 实时显示 WebSocket 连接状态
- **消息复制、时间戳、长文本折叠** — 便捷的消息交互

## 技术栈

- **语言**: Kotlin 2.2
- **UI**: Jetpack Compose + Material3
- **WebSocket**: OkHttp 4.12
- **数据库**: Room 2.7 (KSP)
- **构建**: Gradle 9.4 + AGP 9.2
- **最低支持**: API 28 (Android 9)

## 架构

```
app/
├── data/
│   ├── local/          # Room 数据库 (AppDatabase, ChatDao)
│   ├── model/          # 数据模型 (ChatMessage, ChatSession)
│   ├── repository/     # ChatRepository — 状态管理 + 业务逻辑
│   ├── settings/       # AppSettings — SharedPreferences
│   └── websocket/      # AstrBotWsClient — OkHttp WebSocket
├── ui/
│   ├── chat/           # 聊天页面 (ChatScreen, ChatViewModel)
│   ├── components/     # 可复用组件 (MessageBubble, ChatInputBar, MarkdownText)
│   ├── sessions/       # 会话列表
│   ├── settings/       # 设置页面
│   ├── navigation/     # 底部导航
│   ├── theme/          # 主题
│   └── util/           # 工具 (KeyboardHeightUtil)
└── MainActivity.kt
```

## 配置

在「设置」页面填写：

| 配置项 | 说明 |
|--------|------|
| 服务器地址 | AstrBot HTTP 地址 (如 `http://192.168.1.100:6185`) |
| API Key | AstrBot 的 API 密钥 |
| 用户名 | 显示名称，自动添加 `app_` 前缀 |

## 构建

```bash
git clone https://github.com/yourname/rainnya.git
cd rainnya
./gradlew assembleDebug
```

## 协议

MIT
