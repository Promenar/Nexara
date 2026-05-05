# OpenCode 指令模板 — Session G1: 导航参数修复 + Tab 结构对齐

> **项目**: Nexara Native UI  
> **工作目录**: `k:/Nexara/native-ui/`  
> **Session**: G1 — 导航参数修复 + Tab 结构对齐  
> **前置依赖**: G0 (全局组件基座已完成)  
> **设计参考根目录**: `k:/Nexara/.stitch/`

---

## 你的任务

修复所有路由的参数传递链路（当前所有页面不传参数导致功能断裂），并将 Tab 结构对齐 Stitch A2 规范。

**核心原则**:
- 视觉样式**完全以 `.stitch/` 中的 Stitch MD3 设计稿为准**
- 绝不参考原 RN UI 样式
- 参数传递是**最高优先级**，修复后 AgentHub→SessionList→Chat 链路必须通畅

---

## 设计参考

先读取并理解设计规范：
- `.stitch/design_system/global_theme_specs.md` — 设计 Token
- `.stitch/screens/4614c183122b46ecad69d60d4a61cb96.html` — A2 Tab 导航视觉
- `.stitch/screens/c5317715dae64d70b44569342ece58cb.html` — B2 会话列表视觉
- `.stitch/screens/51903d366b024784b472f7eca445d22b.html` — B1 助手列表视觉
- `.stitch/design_system/stitch-ui-functional-reference.md` — A2/B1/B2 功能需求

同时阅读已有代码：
- `navigation/NavGraph.kt` — 当前路由结构
- `ui/MainTabScaffold.kt` — 当前 Tab 容器
- `ui/hub/AgentHubScreen.kt` — 当前 Agent 列表
- `ui/hub/AgentHubViewModel.kt` — Agent ViewModel
- `ui/hub/AgentSessionsScreen.kt` — 当前会话列表
- `ui/chat/ChatScreen.kt` — 当前聊天页
- `ui/chat/SessionSettingsScreen.kt` — 当前会话设置

---

## 修改 1: `navigation/NavGraph.kt` — 路由全面重构

### 新增路由常量 (在 `NavDestinations` object 中)

```kotlin
// 修改为带参数
const val SESSION_LIST = "session_list/{agentId}"
const val CHAT_HERO = "chat_hero/{sessionId}"
const val SESSION_SETTINGS = "session_settings/{sessionId}"
const val PROVIDER_FORM = "provider_form/{providerId?}"
const val PROVIDER_MODELS = "provider_models/{providerId}"

// 新增路由
const val AGENT_EDIT = "agent_edit/{agentId}"
const val AGENT_RAG_CONFIG = "agent_rag_config/{agentId}"
const val AGENT_ADVANCED_RETRIEVAL = "agent_advanced_retrieval/{agentId}"
const val SPA_SETTINGS = "spa_settings"
const val SESSION_SETTINGS_SHEET = "session_settings_sheet/{sessionId}"
const val WORKSPACE_SHEET = "workspace_sheet/{sessionId}"
const val DOC_EDITOR = "doc_editor/{docId}"
const val KNOWLEDGE_GRAPH = "knowledge_graph"
const val RAG_ADVANCED_KG = "rag_advanced_kg"
const val RAG_DEBUG = "rag_debug"
const val BACKUP_SETTINGS = "backup_settings"
const val WORKBENCH = "workbench"
const val LOCAL_MODELS = "local_models"

// 辅助函数
fun sessionList(agentId: String) = "session_list/$agentId"
fun chatHero(sessionId: String) = "chat_hero/$sessionId"
fun sessionSettings(sessionId: String) = "session_settings/$sessionId"
fun agentEdit(agentId: String) = "agent_edit/$agentId"
fun agentRagConfig(agentId: String) = "agent_rag_config/$agentId"
fun agentAdvancedRetrieval(agentId: String) = "agent_advanced_retrieval/$agentId"
fun providerForm(providerId: String? = null) = if (providerId != null) "provider_form/$providerId" else "provider_form"
fun providerModels(providerId: String) = "provider_models/$providerId"
fun docEditor(docId: String) = "doc_editor/$docId"
```

### 修改 composable 块

每个带参数的路由必须使用 `navArgument` + `NavType.StringType` 定义参数，并从 `backStackEntry.arguments` 提取传递给 Screen。

**示例**:
```kotlin
composable(
    route = NavDestinations.SESSION_LIST,
    arguments = listOf(navArgument("agentId") { type = NavType.StringType })
) { backStackEntry ->
    val agentId = backStackEntry.arguments?.getString("agentId") ?: ""
    AgentSessionsScreen(
        agentId = agentId,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToChat = { sessionId ->
            navController.navigate(NavDestinations.chatHero(sessionId))
        },
        onNavigateToAgentEdit = {
            navController.navigate(NavDestinations.agentEdit(agentId))
        }
    )
}
```

**注意**: 新增路由的 composable 块暂时用占位 Screen（仅 GlassHeader + "Coming Soon" 文字），后续 Session 会实现。但 `SESSION_LIST`、`CHAT_HERO`、`SESSION_SETTINGS` 必须在本次完整修改。

---

## 修改 2: `ui/MainTabScaffold.kt` — Tab 结构对齐

### 对齐 Stitch A2 规范

- **3 个 Tab**: Chat (MessageSquare 图标) / Library (Library 图标) / Settings (Settings 图标)
- **移除** Insights 占位 Tab
- **Artifacts Tab 内容合并到 Library Tab**: Library Tab 内部展示 `RagHomeScreen`
- Tab 标签支持 i18n (暂用英文)
- Tab 容器保留毛玻璃效果和发光选中态

### 修改回调签名

```kotlin
@Composable
fun MainTabScaffold(
    onNavigateToSecondary: (String) -> Unit,
    onNavigateToSessionList: (String) -> Unit  // 新增: 传递 agentId
)
```

Chat Tab 中的 `AgentHubScreen` 现在接收 `onNavigateToSessionList` 而非 `onNavigateToChat`。

---

## 修改 3: `ui/hub/AgentHubScreen.kt` — Agent 列表

### 修改签名

```kotlin
@Composable
fun AgentHubScreen(
    onNavigateToSessionList: (String) -> Unit  // 改: 传递 agentId
)
```

### 修改点击逻辑

```kotlin
// 原来:
onClick = {
    viewModel.selectAgent(agent.id)
    onNavigateToChat()  // ❌ 无参数
}

// 改为:
onClick = {
    viewModel.selectAgent(agent.id)
    onNavigateToSessionList(agent.id)  // ✅ 传递 agentId
}
```

---

## 修改 4: `ui/hub/AgentSessionsScreen.kt` — 会话列表

### 修改签名

```kotlin
@Composable
fun AgentSessionsScreen(
    agentId: String,                              // 新增
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,           // 改: 传递 sessionId
    onNavigateToAgentEdit: () -> Unit             // 新增: 跳转 Agent 编辑器
)
```

### 关键修改

1. **标题动态化**: 通过 `agentId` 从 ViewModel 加载 Agent 名称，替换硬编码 "Super Assistant"
2. **FAB 按钮**: 右下角添加品牌色 FAB (+图标)，点击创建新会话并跳转聊天
3. **右上角设置图标**: TopAppBar actions 添加 Settings 图标，点击 `onNavigateToAgentEdit`
4. **会话点击传递 sessionId**:
   ```kotlin
   onClick = {
       viewModel.selectSession(session.id)
       onNavigateToChat(session.id)  // 传递 sessionId
   }
   ```

### ViewModel 修改 (`SessionListViewModel.kt`)

- `loadSessions(agentId: String)` — 按 agentId 过滤
- `createSession(agentId: String)` — 创建新会话并返回 sessionId
- 暴露 `agentName: StateFlow<String>` 用于标题显示

---

## 修改 5: `ui/chat/ChatScreen.kt` — 聊天页

### 修改签名

```kotlin
@Composable
fun ChatScreen(
    sessionId: String,                            // 新增
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
)
```

### 关键修改

1. **标题动态化**: 通过 `sessionId` 加载会话标题
2. **placeholder 动态化**: `"Message ${agentName}..."` 替代硬编码
3. **ChatViewModel 修改**: `loadSession(sessionId: String)` 加载指定会话的消息

---

## 修改 6: `ui/chat/SessionSettingsScreen.kt` — 会话设置

### 修改签名

```kotlin
@Composable
fun SessionSettingsScreen(
    sessionId: String,                            // 新增
    onNavigateBack: () -> Unit
)
```

### 关键修改

1. **通过 sessionId 加载会话关联的 Agent 信息**（而非硬编码 "Super Assistant"）
2. Swap 按钮: 点击打开 ModelPicker (来自 G0)

---

## 完成标准

- [ ] 所有路由带参数定义 (`agentId`, `sessionId`, `providerId` 等)
- [ ] AgentHub → SessionList → Chat 完整链路通畅（点击 Agent 显示其会话，点击会话进入聊天）
- [ ] Tab 结构 3 Tab (Chat/Library/Settings)
- [ ] 所有硬编码 "Super Assistant" 替换为动态加载
- [ ] FAB 创建新会话功能正常
- [ ] Agent 设置入口（右上角图标）可点击跳转
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
- [ ] NavGraph 中新增路由有占位 composable 块（后续 Session 实现）
