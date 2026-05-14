# Phase 3 — Super Assistant 清理

> **基准**: PRD ADR-001（去繁就简，取消 Super Assistant 概念）
> **创建日期**: 2026-05-13
> **原则**: 删除而非迁移——SpaSettingsScreen 和 SpaViewModel 直接删除，相关引用移除

---

## 1. 命中文件清单

| 文件 | 操作 |
|------|------|
| `PostProcessor.kt` | 修改 — 移除 isSuperAssistant 检查 |
| `PostProcessorTest.kt` | 修改 — 移除 super_assistant 测试 |
| `NexaraApplication.kt` | 修改 — 移除 "super" Agent |
| `strings.xml` (en) | 修改 — 删除 35 个 spa_* 字符串 |
| `strings.xml` (zh) | 修改 — 删除 35 个 spa_* 字符串 |
| `SpaViewModel.kt` | **删除** |
| `SpaSettingsScreen.kt` | **删除** |
| `NavGraph.kt` | 修改 — 移除 SPA_SETTINGS 路由 |
| `ChatScreen.kt` | 修改 — 移除 onNavigateToSpaSettings |
| `AgentHubScreen.kt` | 修改 — 移除 onNavigateToSuperChat |
| `MainTabScaffold.kt` | 修改 — 移除 onNavigateToSuperChat 实现 |

---

## 2. 并行会话拆分

```
Session K: 业务层清理（PostProcessor + NexaraApplication + strings.xml）
Session L: UI/导航层清理（NavGraph + Screen + 删除 SpaSettings）
```

| 文件 | K | L |
|------|---|---|
| `PostProcessor.kt` | ✅ | — |
| `PostProcessorTest.kt` | ✅ | — |
| `NexaraApplication.kt` | ✅ | — |
| `strings.xml` (en + zh) | ✅ | — |
| `SpaViewModel.kt` | — | ✅ 删除 |
| `SpaSettingsScreen.kt` | — | ✅ 删除 |
| `NavGraph.kt` | — | ✅ |
| `ChatScreen.kt` | — | ✅ |
| `AgentHubScreen.kt` | — | ✅ |
| `MainTabScaffold.kt` | — | ✅ |

**零冲突。**

---

## 3. Session K — 业务层清理

### Session K 提示词

```
你需要清理 Nexara 项目中所有 Super Assistant 的业务层残余。

项目根目录: /Users/promenar/Codex/Nexara/native-ui

## 任务

### 1. PostProcessor.kt — 移除 isSuperAssistant 检查

路径: app/src/main/java/com/promenar/nexara/ui/chat/manager/PostProcessor.kt

删除第 69-70 行:
```kotlin
        val isSuperAssistant = params.sessionId == "super_assistant" ||
                params.session.agentId == "super_assistant"
```

将第 78 行:
```kotlin
        if (!isSuperAssistant && (params.session.messages.size <= 2 || isDefaultTitle)) {
```
改为:
```kotlin
        if (params.session.messages.size <= 2 || isDefaultTitle) {
```
即所有会话统一逻辑，不再特殊跳过 "super" 会话的标题生成。

### 2. PostProcessorTest.kt — 移除相关测试

路径: app/src/test/java/com/promenar/nexara/ui/chat/manager/PostProcessorTest.kt

- 删除 `updateStatsSkipsTitleForSuperAssistant()` 测试方法（约第 112-145 行）
- 如果测试类中还有其他引用 `super_assistant` 的地方，一并改为普通 session ID

### 3. NexaraApplication.kt — 移除 "super" Agent

路径: app/src/main/java/com/promenar/nexara/NexaraApplication.kt

在 `defaultAgents` 列表中，将:
```kotlin
com.promenar.nexara.domain.model.Agent(id = "super", name = "Nexara 超级助手", description = "原生加速版，支持实时流式响应", icon = "✨", color = "#C0C1FF", isPinned = true),
```
改为:
```kotlin
com.promenar.nexara.domain.model.Agent(id = "default", name = "Nexara 助手", description = "通用 AI 助手，支持流式对话与知识检索", icon = "✨", color = "#C0C1FF", isPinned = true),
```
将 id 从 "super" 改为 "default"，名称从 "Nexara 超级助手" 改为 "Nexara 助手"。

### 4. strings.xml (en) — 删除 spa_* 和 super 相关字符串

路径: app/src/main/res/values/strings.xml

删除:
- 第 80 行: `<string name="hub_fab_super">Super Assistant</string>`
- 第 245 行: `<string name="chat_menu_super_assistant">Super Assistant</string>`
- 第 368-400 行: 整个 `<!-- SpaSettingsScreen -->` 块（约 35 个 spa_* 字符串）

### 5. strings.xml (zh) — 同上

路径: app/src/main/res/values-zh-rCN/strings.xml

删除:
- 第 80 行: `<string name="hub_fab_super">超级助手</string>`
- 第 251 行: `<string name="chat_menu_super_assistant">超级助手</string>`
- 第 371-403 行: 整个 `<!-- SpaSettingsScreen -->` 块（约 35 个 spa_* 字符串）

## 执行要求

1. 编译验证: `./gradlew :app:compileDebugKotlin`
2. 测试: `./gradlew :app:testDebugUnitTest --tests "com.promenar.nexara.ui.chat.manager.PostProcessorTest"`
3. 确认 PostProcessor 中不再有 `isSuperAssistant` / `super_assistant` 字符串
4. 确认 NexaraApplication 中不再有 `"super"` 作为 agent id

## 禁止事项
- 不修改 UI 层文件（NavGraph / Screen / Scaffold 等由 Session L 处理）
```

---

## 4. Session L — UI/导航层清理

### Session L 提示词

```
你需要清理 Nexara 项目中所有 Super Assistant 的 UI 和导航层残余。

项目根目录: /Users/promenar/Codex/Nexara/native-ui

## 任务

### 1. 删除 SpaViewModel.kt

路径: app/src/main/java/com/promenar/nexara/ui/chat/SpaViewModel.kt
操作: **直接删除整个文件**

### 2. 删除 SpaSettingsScreen.kt

路径: app/src/main/java/com/promenar/nexara/ui/chat/SpaSettingsScreen.kt
操作: **直接删除整个文件**

### 3. NavGraph.kt — 移除 SPA_SETTINGS 路由

路径: app/src/main/java/com/promenar/nexara/navigation/NavGraph.kt

- 第 24 行: 删除 `import com.promenar.nexara.ui.chat.SpaSettingsScreen`
- 第 61 行: 删除 `const val SPA_SETTINGS = "spa_settings"`
- 第 204-206 行: 将:
```kotlin
                onNavigateToSpaSettings = {
                    navController.navigate(NavDestinations.SPA_SETTINGS)
                }
```
  改为: 删除 onNavigateToSpaSettings 行（或保持空 lambda）
- 第 258-264 行: 删除整个 `composable(NavDestinations.SPA_SETTINGS)` 块

### 4. ChatScreen.kt — 移除 onNavigateToSpaSettings

路径: app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt

- 第 124 行: 删除 `onNavigateToSpaSettings: () -> Unit = {}` 参数
- 查找并删除所有对 onNavigateToSpaSettings 的调用（可能是聊天菜单中的 "Super Assistant Settings" 按钮）

### 5. AgentHubScreen.kt — 移除 onNavigateToSuperChat

路径: app/src/main/java/com/promenar/nexara/ui/hub/AgentHubScreen.kt

- 第 37 行: 删除 `@Suppress("UNUSED_PARAMETER") onNavigateToSuperChat: () -> Unit` 参数
- 同时删除函数签名中对应的参数定义

### 6. MainTabScaffold.kt — 移除 onNavigateToSuperChat 实现

路径: app/src/main/java/com/promenar/nexara/ui/MainTabScaffold.kt

- 第 72-74 行: 删除:
```kotlin
                    onNavigateToSuperChat = {
                        onNavigateToSessionList("super")
                    }
```

## 执行要求

1. 每步编译验证: `./gradlew :app:compileDebugKotlin`
   - **特别注意**: 删除 SpaSettingsScreen 后，检查是否有其他文件 import 了它（用 `grep -r "SpaSettings" app/src` 检查）
2. 全部测试: `./gradlew :app:testDebugUnitTest`
3. 确认以下文件不再存在: `SpaViewModel.kt`、`SpaSettingsScreen.kt`
4. 确认以下不再出现: `SPA_SETTINGS`、`SpaSettings`、`onNavigateToSuperChat`、`onNavigateToSpaSettings`

## 排查步骤（编译失败时）

如果编译失败，可能是某些文件仍引用了被删除的类：
```bash
cd native-ui
grep -r "SpaSettings\|SpaViewModel\|SPA_SETTINGS\|onNavigateToSpaSettings\|onNavigateToSuperChat" app/src/main --include="*.kt"
```
逐个修复引用后重新编译。

## 禁止事项
- 不修改业务层文件（PostProcessor / NexaraApplication / strings.xml 由 Session K 处理）
```

---

## 5. 验证清单

- [ ] `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- [ ] `./gradlew :app:testDebugUnitTest` 无新增失败
- [ ] `SpaViewModel.kt` 和 `SpaSettingsScreen.kt` 文件已删除
- [ ] 代码库中全搜索 `"super"` + `spa_settings` + `isSuperAssistant` + `onNavigateToSuperChat` 零命中（Kotlin 源文件）
- [ ] `strings.xml` 中零 `spa_` 字符串

---

**文档维护者**: AI Assistant
**最后更新**: 2026-05-13
