# Nexara native-ui 修复路线图：分阶段多会话并行施工方案

> 创建: 2026-05-11 | 上游审计: `.agent/audits/full-stack-audit-20260511.md`
> 工作目录: `native-ui/` (Android/Kotlin/Compose)
> 每个 Session 为独立 Flash 模型会话的可直接粘贴 Prompt

---

## 总览：文件冲突图与并行策略

```
Phase A (Foundation) ── 必须最先执行
  Session A: ContextBuilder.kt + ChatViewModel.kt + NexaraApplication.kt + 新建 WebSearchProvider
  ↓ (A 完成后 B/C 可并行)
  
Phase B (3 会话并行 — 无文件冲突)
  Session B1: ChatViewModel.kt (loop limit + token indicator TODOs)
  Session B2: UserSkillRegistry.kt + SkillDao.kt + SkillRepository.kt + ISkillRepository.kt
  Session B3: SkillsScreen.kt + SessionSettingsScreen.kt + SpaSettingsScreen.kt + UserSettingsHomeScreen.kt

Phase C (3 会话并行 — 无文件冲突)
  Session C1: 新建 SpaViewModel.kt + 重写 SpaSettingsScreen.kt
  Session C2: SettingsViewModel.kt + NexaraApplication.kt (preset models in DI section)
  Session C3: ContextBuilder.kt + WebSearchSkill.kt (KG toggle + search config)
```

**文件冲突矩阵**:
| 文件 | Session | 冲突 |
|------|---------|------|
| `ContextBuilder.kt` | A, C3 | 需顺序 (A→C3) |
| `ChatViewModel.kt` | A, B1 | 需顺序 (A→B1) |
| `NexaraApplication.kt` | A, C2 | 需顺序 (A→C2) |
| 所有其他文件 | — | 无冲突，可并行 |

---

## Phase A — Foundation (必须最先执行)

### Session A: Web Search 系统提示词注入全链路修复

**目标**: 修复 P0-1 + P0-6 + P0-7，让 Web Search 配置能够注入搜索结果到 System Prompt

**涉及文件**:
- 新建 `app/src/main/java/com/promenar/nexara/ui/chat/manager/WebSearchContextProvider.kt`
- 修改 `app/src/main/java/com/promenar/nexara/ui/chat/manager/ContextBuilder.kt`
- 修改 `app/src/main/java/com/promenar/nexara/ui/chat/ChatViewModel.kt` (构造器新增参数)
- 修改 `app/src/main/java/com/promenar/nexara/NexaraApplication.kt` (创建 WebSearchProvider 实例)

**完成后 B/C Phase 可并行启动。**

---

### Session A 的 Flash 模型 Prompt:

```
# Task: 修复 Nexara Web Search 系统提示词注入全链路

## 背景
当前 `SessionSettingsSheet` 的 webSearch toggle 能写入 `session.options.webSearch`，
`SearchConfigScreen` 的所有配置能写入 `nexara_search` SharedPreferences，
但 `ContextBuilder` 从未读取这些配置或执行搜索，导致搜索结果永远无法注入到发给 LLM 的 system prompt 中。

**关键事实**: `WebSearchSkill.kt` 已能从 `nexara_search` prefs 读取引擎配置并在 tool_call 时搜索成功，
但 `ContextBuilder` 在构建 system prompt 时需要提前执行搜索以便 LLM 在第一轮就能看到搜索结果。

## 要修改的文件 (按顺序)

### 1. 新建: app/src/main/java/com/promenar/nexara/ui/chat/manager/WebSearchContextProvider.kt

此文件实现 `WebSearchProvider` 接口，从 `nexara_search` SharedPreferences 读取配置并委托给对应的搜索引擎 Provider。

参考 `WebSearchSkill.kt:60-76` 的 getActiveProvider() 逻辑。
需要引入 Context 以读取 SharedPreferences。
需要读取: search_engine, searxng_url, tavily_api_key, search_depth, result_count, include_domains, exclude_domains。

```kotlin
package com.promenar.nexara.ui.chat.manager

import android.content.Context
import com.promenar.nexara.data.remote.search.DuckDuckGoProvider
import com.promenar.nexara.data.remote.search.SearXNGProvider
import com.promenar.nexara.data.remote.search.TavilyProvider
import io.ktor.client.HttpClient

class WebSearchContextProvider(
    private val context: Context,
    private val httpClient: HttpClient
) : WebSearchProvider {
    
    private val prefs get() = context.getSharedPreferences("nexara_search", android.content.Context.MODE_PRIVATE)
    
    override suspend fun search(query: String): Pair<String, List<com.promenar.nexara.data.model.Citation>> {
        if (prefs.getBoolean("web_search_enabled", true) == false) {
            return Pair("", emptyList())
        }
        val provider = getActiveProvider()
        return provider.search(query)
    }
    
    private fun getActiveProvider(): WebSearchProvider {
        val engine = prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo"
        return when (engine) {
            "searxng" -> {
                val url = prefs.getString("searxng_url", "https://searx.be") ?: "https://searx.be"
                SearXNGProvider(httpClient, url)
            }
            "tavily" -> {
                val key = prefs.getString("tavily_api_key", "") ?: ""
                TavilyProvider(httpClient, key)
            }
            else -> DuckDuckGoProvider(httpClient)
        }
    }
}
```

### 2. 修改: NexaraApplication.kt

在 lazy 初始化块中添加 webSearchProvider:

```kotlin
// 在 kgProvider 附近添加:
val webSearchContextProvider: WebSearchProvider by lazy {
    WebSearchContextProvider(this, httpClient)
}
```

需要新增 import: `import com.promenar.nexara.ui.chat.manager.WebSearchContextProvider`

### 3. 修改: ChatViewModel.kt

修改 ContextBuilder 构造调用 (约第 78-81 行)：

将:
```kotlin
private val contextBuilder = ContextBuilder(
    ragProvider = memoryManager?.let { MemoryManagerRagAdapter(it) },
    kgProvider = kgProvider
)
```

改为:
```kotlin
private val webSearchContextProvider = (application as NexaraApplication).webSearchContextProvider

private val contextBuilder = ContextBuilder(
    webSearchProvider = webSearchContextProvider,
    ragProvider = memoryManager?.let { MemoryManagerRagAdapter(it) },
    kgProvider = kgProvider
)
```

### 4. 修改: ContextBuilder.kt

在 `buildSystemPrompt` 方法中 (约第 110 行开始), 确认 `searchContext` 已经在第 53 行通过 `performClientSideSearch` 获得，
并在第 174-178 行注入到 prompt。此逻辑已存在，无需修改，只需修复上游注入。

但需要确保 `webSearchProvider` 在 `generateMessage()` 中也能被触发。检查 `buildContext()` 中 `performClientSideSearch` 调用 (约第 53 行)：
确认其调用逻辑为: 当 `session.options?.webSearch == true` 时才执行搜索。
当前 `performClientSideSearch` 不支持此条件检查。需要传入 session 的 webSearch 选项。

修改 `buildContext` 方法 (约第 52 行):
将:
```kotlin
val searchContext = performClientSideSearch(params.content)
```
改为:
```kotlin
val searchContext = if (params.session.options?.webSearch == true) {
    performClientSideSearch(params.content)
} else ""
```

## 验收标准
1. 编译通过 (`./gradlew compileDebugKotlin`)
2. `ContextBuilder.buildSystemPrompt()` 的输出中应包含 "## Web Search Results" 段（当搜索开启且有结果时）
3. `SessionSettingsSheet` 的 webSearch toggle 开关应直接影响搜索行为
</系统提示>
```

---

## Phase B — 并行 (3 个独立会话)

### Session B1: ChatViewModel 核心修复 (Loop Limit + Token Indicator + 模型规格读取)

**无 Phase A 文件冲突**（ChatViewModel.kt 在 A 中的改动仅为构造器，B1 改动在方法体内）

**涉及文件**: 仅 `app/src/main/java/com/promenar/nexara/ui/chat/ChatViewModel.kt`

---

### Session B1 的 Flash 模型 Prompt:

```
# Task: 修复 ChatViewModel 中的两项 P0 断裂和两项 TODO

## 待修复项

### Fix 1: Loop Limit 读取 SharedPreferences (P0-2)
位置: 约第 194 行 `generateMessage()` 方法内
当前: `if (loopCount >= session.autoLoopLimit) return` — session.autoLoopLimit 永远为默认值 5
目标: 从 `nexara_settings` SharedPreferences 读取 `loop_limit` 首选项

改动: 在第 194 行上方添加
```kotlin
val prefs = application.getSharedPreferences("nexara_settings", 0)
val effectiveLoopLimit = prefs.getInt("loop_limit", 15)
if (loopCount >= effectiveLoopLimit) return
```
并删除原来的 `if (loopCount >= session.autoLoopLimit) return`

### Fix 2: Token Indicator 从模型规格读取最大值 (P1 TODO×2)
位置: 第 422 行和第 836 行
当前: `val maxTokens = 128000 // TODO: Get from model spec` (硬编码)
目标: 使用 `com.promenar.nexara.data.model.findModelSpec(session.modelId ?: "")?.contextLength` 动态获取

改动第 422 行:
```kotlin
val maxTokens = com.promenar.nexara.data.model.findModelSpec(
    finalSession.modelId ?: ""
)?.contextLength ?: 128000
```

改动第 836 行:
```kotlin
val max = com.promenar.nexara.data.model.findModelSpec(
    session.modelId ?: ""
)?.contextLength ?: 128000
```

### Fix 3: Tool Instructions 占位符 (P1)
位置: `ContextBuilder.kt` 第 131 行
当前: `// 2. Tools Instructions (Placeholder for now)` — 注释占位，无实际逻辑
目标: 在 session.options.toolsEnabled 为 true 时注入工具使用指令

在 `buildSystemPrompt()` 中第 131 行替换注释为:
```kotlin
// 2. Tools Instructions
if (session.options?.toolsEnabled == true) {
    sb.appendLine("[You have access to function calling tools. Use them when needed to provide accurate and up-to-date responses.]")
    sb.appendLine()
}
```

## 验收标准
1. 编译通过
2. `SkillsScreen` 中修改 loop_limit 值后，ChatViewModel 中 `effectiveLoopLimit` 随之变化
3. token indicator max 值根据当前模型 spec 的 contextLength 变化
</系统提示>
```

---

### Session B2: UserSkillRegistry 与 CustomDatabaseSkill 完整实现

**无文件冲突** — 仅涉及 UserSkillRegistry.kt, SkillDao.kt, SkillRepository.kt, ISkillRepository.kt

**涉及文件**:
- `app/src/main/java/com/promenar/nexara/ui/chat/manager/registry/UserSkillRegistry.kt`
- `app/src/main/java/com/promenar/nexara/data/local/db/dao/SkillDao.kt`
- `app/src/main/java/com/promenar/nexara/data/repository/SkillRepository.kt`
- `app/src/main/java/com/promenar/nexara/data/repository/ISkillRepository.kt`

---

### Session B2 的 Flash 模型 Prompt:

```
# Task: 完整实现 UserSkillRegistry — 让用户自定义工具可被发现和执行

## 背景
当前 `UserSkillRegistry` 的三个核心方法返回 null/empty (P0-3)，`CustomDatabaseSkill.execute()` 返回
"Logic not yet implemented" (P0-4)。用户通过 SkillsScreen 创建的自定义工具永远无法被 LLM 发现或调用。

## 要修改的文件

### 1. SkillDao.kt — 添加查询方法
文件: app/src/main/java/com/promenar/nexara/data/local/db/dao/SkillDao.kt

在接口中添加两个新方法:
```kotlin
@Query("SELECT * FROM custom_skills WHERE enabled = 1 ORDER BY createdAt DESC")
suspend fun getAllEnabledCustomSkills(): List<CustomSkillEntity>

@Query("SELECT * FROM custom_skills WHERE name = :name AND enabled = 1 LIMIT 1")
suspend fun getEnabledCustomSkillByName(name: String): CustomSkillEntity?
```

需要新增 import: `import com.promenar.nexara.data.local.db.entity.CustomSkillEntity` (已存在)

### 2. ISkillRepository.kt — 扩展接口
文件: app/src/main/java/com/promenar/nexara/data/repository/ISkillRepository.kt

添加两个新方法:
```kotlin
suspend fun getAllEnabledCustomSkills(): List<CustomSkillEntity>
suspend fun getEnabledCustomSkillByName(name: String): CustomSkillEntity?
```

### 3. SkillRepository.kt — 实现新方法
文件: app/src/main/java/com/promenar/nexara/data/repository/SkillRepository.kt

添加:
```kotlin
override suspend fun getAllEnabledCustomSkills() = skillDao.getAllEnabledCustomSkills()
override suspend fun getEnabledCustomSkillByName(name: String) = skillDao.getEnabledCustomSkillByName(name)
```

### 4. UserSkillRegistry.kt — 完成核心实现
文件: app/src/main/java/com/promenar/nexara/ui/chat/manager/registry/UserSkillRegistry.kt

```kotlin
package com.promenar.nexara.ui.chat.manager.registry

import com.promenar.nexara.data.repository.SkillRepository
import com.promenar.nexara.data.model.ToolResult
import com.promenar.nexara.data.remote.protocol.ProtocolTool
import com.promenar.nexara.data.remote.protocol.ProtocolToolFunction
import kotlinx.coroutines.runBlocking

class UserSkillRegistry(
    private val repository: SkillRepository
) : SkillRegistry {
    
    private var skillCache: List<SkillDefinition> = emptyList()
    
    override fun getSkill(name: String): SkillDefinition? {
        val entity = runBlocking { repository.getEnabledCustomSkillByName(name) }
        return entity?.let {
            CustomDatabaseSkill(it.id, it.name, it.description, it.parametersSchema, it.code)
        }
    }
    
    override fun getAllSkills(): List<SkillDefinition> {
        val entities = runBlocking { repository.getAllEnabledCustomSkills() }
        return entities.map {
            CustomDatabaseSkill(it.id, it.name, it.description, it.parametersSchema, it.code)
        }
    }
    
    override fun getAllTools(allowedIds: List<String>?): List<ProtocolTool> {
        val skills = getAllSkills()
        val filtered = if (allowedIds == null) skills else skills.filter { it.id in allowedIds }
        return filtered.map { skill ->
            ProtocolTool(
                type = "function",
                function = ProtocolToolFunction(
                    name = skill.name,
                    description = skill.description,
                    parameters = skill.parametersSchema.ifEmpty { """{"type":"object","properties":{}}""" }
                )
            )
        }
    }
}
```

### 5. CustomDatabaseSkill — 添加基本执行逻辑
同一文件 UserSkillRegistry.kt 中的 CustomDatabaseSkill 类，将 execute() 方法从:
```kotlin
return ToolResult("user_${System.currentTimeMillis()}", "Custom skill '$name' executed. (Logic not yet implemented)", "success")
```
改为:
```kotlin
return ToolResult(
    id = "user_${System.currentTimeMillis()}", 
    content = "Custom skill '$name' was called with args: ${args.entries.joinToString { "${it.key}=${it.value}" }}. However, sandbox execution is not yet implemented. Code to execute: ${code.take(200)}",
    status = "success"
)
```

## 验收标准
1. 编译通过
2. 用户在 SkillsScreen 创建自定义工具后，工具定义应出现在发送给 LLM 的 tools 数组中
3. LLM 调用自定义工具时，应返回包含参数信息的反馈（而非空错误）
</系统提示>
```

---

### Session B3: 死控件修复 — 所有 P2 空 onClick 与硬编码

**无文件冲突** — 仅涉及 UI 层文件

**涉及文件**:
- `app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsScreen.kt`
- `app/src/main/java/com/promenar/nexara/ui/chat/SpaSettingsScreen.kt`
- `app/src/main/java/com/promenar/nexara/ui/settings/SkillsScreen.kt`
- `app/src/main/java/com/promenar/nexara/ui/hub/UserSettingsHomeScreen.kt`

---

### Session B3 的 Flash 模型 Prompt:

```
# Task: 修复所有 UI 死控件 — 空 onClick 和硬编码值

## 待修复项

### Fix 1: SessionSettingsScreen.kt — Swap Agent 按钮 (当前约第 189 行)
当前: `IconButton(onClick = { })` — 空实现
目标: 隐藏该按钮或导航到 Agent 切换界面
建议: 因为 Swap Agent 在当前上下文无实际意义（Session 已绑定 Agent），将 `onClick = {}` 替换为 `onClick = onNavigateToAgentEdit` 并在 Button 上方添加条件判断 `if (session?.agentId != null)`

实际修改: 找到 `Row { IconButton(onClick = { }) { Icon(Icons.Rounded.SwapHoriz...` 区块，
将 `onClick = { }` 改为 `onClick = { onNavigateToAgentEdit(session?.agentId ?: return@IconButton) }`

### Fix 2: SessionSettingsScreen.kt — Export 按钮 (当前约第 204-211 行)
当前: `Row(modifier = ... .clickable { }` — 空实现
目标: 点击时创建一个简单的文本导出 (将 session 消息导出为文本并分享)
```kotlin
.clickable {
    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, 
            session?.messages?.joinToString("\n\n") { "${it.role.name}: ${it.content}" } ?: "")
    }
    context.startActivity(android.content.Intent.createChooser(shareIntent, "Export Chat"))
}
```

### Fix 3: SessionSettingsScreen.kt — Add Docs 按钮 (当前约第 338 行)
当前: `Surface(onClick = { }` — 空实现
目标: 启动文件选择器让用户选择文档
```kotlin
val docPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
) { uris -> /* add to attachedDocs */ }

Surface(onClick = { docPickerLauncher.launch("*/*") }, ...)
```
注: 需要在 Screen 顶层添加 `rememberLauncherForActivityResult`，并添加 import:
`import androidx.activity.compose.rememberLauncherForActivityResult`
`import androidx.activity.result.contract.ActivityResultContracts`

### Fix 4: SpaSettingsScreen.kt — Clean Ghost 按钮 (约第 333 行)
当前: `onClick = { }`
目标: 调用清理过期 session 或脏数据的逻辑。如果无法实现后端，改为显示 Toast 提示 "功能开发中"
```kotlin
onClick = {
    android.widget.Toast.makeText(context, "Ghost data cleanup is coming soon", android.widget.Toast.LENGTH_SHORT).show()
}
```

### Fix 5: SpaSettingsScreen.kt — Export History 按钮 (约第 346 行)
当前: `onClick = { }`
目标: 同 Fix 4，显示 Toast
```kotlin
onClick = {
    android.widget.Toast.makeText(context, "History export is coming soon", android.widget.Toast.LENGTH_SHORT).show()
}
```

### Fix 6: SpaSettingsScreen.kt — StatCard 硬编码值 (约第 324-327 行)
当前: `"1,204"`, `"8,432"`, `"2.4M"` 硬编码
目标: 暂不可接入真实数据（需后端支持）。改为通过 SharedPreferences 存储和读取模拟值，至少让取值路径存在：
保持硬编码但添加 TODO 注释标注数据源：
```kotlin
// TODO: Replace with real data from DocRepository.countDocuments(), SessionRepository.countSessions(), VectorDao.count()
StatCard("1,204", ...) // 硬编码占位，待接入 DocumentDao
StatCard("8,432", ...) // 硬编码占位，待接入 SessionDao  
StatCard("2.4M", ...)  // 硬编码占位，待接入 VectorDao
```

### Fix 7: SkillsScreen.kt — MCP Default 开关 (约第 733 行)  
当前: `onCheckedChange = {}`
目标: 实现 isDefault 更新。需要先在 SettingsViewModel 中添加 `updateMcpServerDefault()` 方法。

在 SettingsViewModel 中添加:
```kotlin
fun updateMcpServerDefault(id: String, isDefault: Boolean) {
    viewModelScope.launch {
        app.skillRepository.updateMcpServerDefault(id, isDefault)
        // Refresh the list
        app.skillRepository.getAllMcpServers().collectLatest { entities ->
            _mcpServers.value = entities.map { entity ->
                McpServerUiModel(
                    id = entity.id, name = entity.name, url = entity.url,
                    type = entity.type, isConnected = false,
                    isEnabled = entity.enabled, isDefault = entity.isDefault,
                    callIntervalMs = entity.callIntervalMs, tools = emptyList()
                )
            }
        }
    }
}
```

需要在 ISkillRepository/SkillRepository/SkillDao 中添加:
```kotlin
// SkillDao:
@Query("UPDATE mcp_servers SET isDefault = :isDefault WHERE id = :id")
suspend fun updateMcpServerDefault(id: String, isDefault: Boolean)

// ISkillRepository:
suspend fun updateMcpServerDefault(id: String, isDefault: Boolean)

// SkillRepository:
override suspend fun updateMcpServerDefault(id: String, isDefault: Boolean) = 
    skillDao.updateMcpServerDefault(id, isDefault)
```

然后修改 SkillsScreen.kt 的 MCP Default 开关:
将 `onCheckedChange = {}` 改为 `onCheckedChange = { viewModel.updateMcpServerDefault(server.id, it) }`

注意: SkillsScreen 中使用的是 `SettingsViewModel`。

### Fix 8: UserSettingsHomeScreen.kt — About 卡片 (约第 510 行)
当前: `onClick = { }`
目标: 打开浏览器到 GitHub 项目页
```kotlin
onClick = {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, 
        android.net.Uri.parse("https://github.com/promenar/nexara"))
    context.startActivity(intent)
}
```

## 验收标准
1. 编译通过
2. Export 按钮点击后分享对话框弹出
3. Add Docs 按钮点击后文件选择器打开
4. MCP Default 开关切换后状态持久化
5. Swap Agent 不 crash (改为导航或无操作)
</系统提示>
```

---

## Phase C — 并行 (3 个独立会话，依赖 Phase A 已完成)

### Session C1: SpaSettingsScreen 全页面重写

**涉及文件**:
- 新建 `app/src/main/java/com/promenar/nexara/ui/chat/SpaViewModel.kt`
- 重写 `app/src/main/java/com/promenar/nexara/ui/chat/SpaSettingsScreen.kt`

---

### Session C1 的 Flash 模型 Prompt:

```
# Task: 重写 SpaSettingsScreen — 从纯装饰页面升级为真实功能页面

## 背景
当前 `SpaSettingsScreen.kt` (407 行) 依赖全部局部 `remember { mutableStateOf() }` 状态，
无 ViewModel、无持久化、无后端连接，是一个完整的 P0-5 僵尸页面。

## 目标
创建 `SpaViewModel`，将当前 UI 中的所有硬编码状态连接到真实的 SharedPreferences 持久化，
并接入已有的 `SettingsViewModel` 提供给 LLM pipeline 的预设模型选择器。

## 要创建的文件

### 1. 新建: app/src/main/java/com/promenar/nexara/ui/chat/SpaViewModel.kt

```kotlin
package com.promenar.nexara.ui.chat

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpaViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = 
        application.getSharedPreferences("nexara_spa", android.content.Context.MODE_PRIVATE)
    
    private val _assistantTitle = MutableStateFlow(
        prefs.getString("assistant_title", "Nexara Prime") ?: "Nexara Prime"
    )
    val assistantTitle: StateFlow<String> = _assistantTitle.asStateFlow()
    
    private val _fabColor = MutableStateFlow(
        prefs.getString("fab_color", "#6366F1") ?: "#6366F1"
    )
    val fabColor: StateFlow<String> = _fabColor.asStateFlow()
    
    private val _fabIconIndex = MutableStateFlow(
        prefs.getInt("fab_icon_index", 0)
    )
    val fabIconIndex: StateFlow<Int> = _fabIconIndex.asStateFlow()
    
    private val _rotateAnimation = MutableStateFlow(
        prefs.getBoolean("rotate_animation", true)
    )
    val rotateAnimation: StateFlow<Boolean> = _rotateAnimation.asStateFlow()
    
    private val _glowEffect = MutableStateFlow(
        prefs.getBoolean("glow_effect", true)
    )
    val glowEffect: StateFlow<Boolean> = _glowEffect.asStateFlow()
    
    private val _enableKG = MutableStateFlow(
        prefs.getBoolean("enable_kg_spa", true)
    )
    val enableKG: StateFlow<Boolean> = _enableKG.asStateFlow()
    
    private val _contextWindow = MutableStateFlow(
        prefs.getFloat("context_window", 0.7f)
    )
    val contextWindow: StateFlow<Float> = _contextWindow.asStateFlow()
    
    fun updateAssistantTitle(title: String) {
        _assistantTitle.value = title
        prefs.edit().putString("assistant_title", title).apply()
    }
    
    fun updateFabColor(color: String) {
        _fabColor.value = color
        prefs.edit().putString("fab_color", color).apply()
    }
    
    fun updateFabIcon(index: Int) {
        _fabIconIndex.value = index
        prefs.edit().putInt("fab_icon_index", index).apply()
    }
    
    fun updateRotateAnimation(enabled: Boolean) {
        _rotateAnimation.value = enabled
        prefs.edit().putBoolean("rotate_animation", enabled).apply()
    }
    
    fun updateGlowEffect(enabled: Boolean) {
        _glowEffect.value = enabled
        prefs.edit().putBoolean("glow_effect", enabled).apply()
    }
    
    fun updateEnableKG(enabled: Boolean) {
        _enableKG.value = enabled
        prefs.edit().putBoolean("enable_kg_spa", enabled).apply()
    }
    
    fun updateContextWindow(value: Float) {
        _contextWindow.value = value
        prefs.edit().putFloat("context_window", value).apply()
    }
}
```

### 2. 重写: app/src/main/java/com/promenar/nexara/ui/chat/SpaSettingsScreen.kt

目标改动:
- 删除所有 `remember { mutableStateOf() }` 局部状态
- 使用 `viewModel<SpaViewModel>()` 从新建的 ViewModel 获取状态
- 将 StatCard 硬编码值保留但添加 TODO 注释
- 将 Clean Ghost / Export History 按钮的 onClick 改为显示 Toast (同 Phase B 的方案)
- 将 Model 选择器连接到 `SettingsViewModel.providerModels`

具体实现: 替换文件顶部 45 行的 import 和 407 行的函数体。
当前所有 `remember { mutableStateOf() }` 变量改为 `val xxx by viewModel.xxx.collectAsState()`
将 `val settingsViewModel: SettingsViewModel = viewModel(factory = ...)` 添加到函数开头以读取预设模型。

## 验收标准
1. 编译通过
2. Assistant Title 修改后重启应用仍保留
3. FAB Icon/Color/Rotate/Glow 设置持久化
4. 页面不再依赖纯局部状态
</系统提示>
```

---

### Session C2: 预设模型完整接线

**涉及文件**:
- `app/src/main/java/com/promenar/nexara/data/rag/EmbeddingClient.kt` (只读检查)
- `app/src/main/java/com/promenar/nexara/data/rag/ImageService.kt` (只读检查)
- `app/src/main/java/com/promenar/nexara/data/rag/Reranker.kt` (只读检查)
- `app/src/main/java/com/promenar/nexara/ui/settings/SettingsViewModel.kt` (添加预设模型的消费端连接)
- `app/src/main/java/com/promenar/nexara/NexaraApplication.kt` (让 EmbeddingClient/Reranker 从 SharedPreferences 读取预设模型)

---

### Session C2 的 Flash 模型 Prompt:

```
# Task: 将预设模型 (Image/Embedding/Rerank) 接线到实际服务

## 背景
`SettingsViewModel.setPresetModel()` 能写入 `preset_image_model`, `preset_embedding_model`, 
`preset_rerank_model` 到 SharedPreferences，但 `ChatViewModel` 只消费了 `preset_summary_model`。
ImageService / EmbeddingClient / Reranker 均未读取各自预设模型。

## 要修改的文件

### 1. NexaraApplication.kt — EmbeddingClient 读取预设模型
当前 embeddingClient lazy 初始化 (约第 165-170 行):
```kotlin
val embeddingClient: EmbeddingClient by lazy {
    val baseUrl = prefs.getString("embedding_base_url", "") ?: ""
    val apiKey = prefs.getString("embedding_api_key", "") ?: ""
    val model = prefs.getString("embedding_model", "") ?: ""
    EmbeddingClient(baseUrl = baseUrl, apiKey = apiKey, model = model)
}
```

修改为从 `nexara_settings` 读取 `preset_embedding_model` 作为回退:
```kotlin
val embeddingClient: EmbeddingClient by lazy {
    val settingsPrefs = getSharedPreferences("nexara_settings", MODE_PRIVATE)
    val baseUrl = prefs.getString("embedding_base_url", "") ?: ""
    val apiKey = prefs.getString("embedding_api_key", "") ?: ""
    val presetModel = settingsPrefs.getString("preset_embedding_model", "")
    val model = prefs.getString("embedding_model", "")?.ifBlank { presetModel } ?: presetModel ?: ""
    EmbeddingClient(baseUrl = baseUrl, apiKey = apiKey, model = model)
}
```

### 2. NexaraApplication.kt — ImageService 读取预设模型
查找 ImageService 的初始化位置 (如存在) 或添加对 `preset_image_model` 的读取。
如果 ImageService 是惰性初始化的，在构造函数或 init 中读取 SharedPreferences。
如果 ImageService 不在 Application 中管理，则在 SettingsViewModel 的 `setPresetModel()` 方法中添加通知机制。

### 3. NexaraApplication.kt — Reranker 读取预设模型
同 ImageService，确保 Reranker 读取 `preset_rerank_model`。

### 4. NexaraApplication.kt — Haptic Feedback 消费
在 `onCreate()` 或设置变更回调中，消费 `haptic_enabled` 首选项。
由于 Compose 中没有全局 haptic 开关的简单机制，建议：
- 在 Application 中保存 `hapticEnabled` 状态
- 为 ChatScreen 中的按钮交互添加 `if (hapticEnabled) performHapticFeedback()` 条件
- 或者直接删除该设置项（当前无 UI 暴露）

## 验收标准
1. 编译通过
2. `SettingsViewModel.setPresetModel("embedding", "some-model")` 后，`NexaraApplication.embeddingClient` 使用该模型
3. 同逻辑对 image/rerank 预设生效
</系统提示>
```

---

### Session C3: KG Toggle 修复 + WebSearchSkill 读取搜索配置

**涉及文件**:
- `app/src/main/java/com/promenar/nexara/ui/chat/manager/ContextBuilder.kt` (添加 KG 开关检查)
- `app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/WebSearchSkill.kt` (读取 search_depth/result_count/domains)
- `app/src/main/java/com/promenar/nexara/data/remote/search/DuckDuckGoProvider.kt` (只读)
- `app/src/main/java/com/promenar/nexara/data/remote/search/SearXNGProvider.kt` (只读)
- `app/src/main/java/com/promenar/nexara/data/remote/search/TavilyProvider.kt` (只读)

---

### Session C3 的 Flash 模型 Prompt:

```
# Task: 修复 KG Toggle 与 WebSearch 配置深度集成

## 背景
1. P1-1: 即使用户关闭 Knowledge Graph，ContextBuilder 仍会调用 kgProvider.extractContext()
2. P0-6: WebSearchSkill 已正确读取 search_engine，但未读取 search_depth, result_count, include_domains, exclude_domains 等高级配置

## 待修改文件

### 1. ContextBuilder.kt — 添加 KG 开关检查
当前 `buildContext()` 约第 55-59 行:
```kotlin
val kgContext = if (kgProvider != null && ragResult.second.isNotEmpty()) {
    try { kgProvider.extractContext(params.content, params.sessionId, ragResult.second) ?: "" }
    catch (_: Exception) { "" }
} else ""
```

改为加入 enableKnowledgeGraph 检查:
```kotlin
val kgEnabled = params.session.ragOptions?.enableKnowledgeGraph ?: false
val kgContext = if (kgProvider != null && ragResult.second.isNotEmpty() && kgEnabled) {
    try { kgProvider.extractContext(params.content, params.sessionId, ragResult.second) ?: "" }
    catch (_: Exception) { "" }
} else ""
```

### 2. WebSearchSkill.kt — 读取高级搜索配置
当前 `getActiveProvider()` 方法约第 60-76 行，只读取 search_engine。
修改为也读取并配置 search_depth, result_count, include_domains, exclude_domains。

扩展 WebSearchSkill 的方法签名，向各 Provider 传递配置参数。

先检查各 Provider 的构造函数是否支持这些参数:
- DuckDuckGoProvider: 检查是否接受 depth/resultCount 参数
- SearXNGProvider: 检查是否接受 domains 参数
- TavilyProvider: 检查是否接受 depth 参数

根据各 Provider 的实际接口，在 getActiveProvider() 中:
```kotlin
private fun getActiveProvider(): WebSearchProvider {
    val prefs = context.getSharedPreferences("nexara_search", Context.MODE_PRIVATE)
    val engine = prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo"
    val depth = prefs.getString("search_depth", "advanced") ?: "advanced"
    val resultCount = prefs.getInt("result_count", 5)
    
    return when (engine) {
        "duckduckgo" -> DuckDuckGoProvider(httpClient)
        "searxng" -> {
            val url = prefs.getString("searxng_url", "https://searx.be") ?: "https://searx.be"
            SearXNGProvider(httpClient, url)
        }
        "tavily" -> {
            val key = prefs.getString("tavily_api_key", "") ?: ""
            TavilyProvider(httpClient, key)
        }
        else -> DuckDuckGoProvider(httpClient)
    }
}
```

如果 Provider 不支持高级参数，至少在 search() 调用前添加过滤逻辑处理 include/exclude domains。

### 3. 检查并修复 PromptRequest.webSearch 与协议层的连接
文件: `app/src/main/java/com/promenar/nexara/data/remote/protocol/OpenAIProtocol.kt`
检查 `webSearch` 字段是否被正确转换为 API 请求参数。
如果协议层未处理，则添加基础转换逻辑。

## 验收标准
1. 编译通过
2. KG toggle 关闭后，ContextBuilder 不再调用 kgProvider.extractContext()
3. KG toggle 开启后，KG 上下文正常注入
4. WebSearchSkill 使用结果数配置值过滤搜索结果
</系统提示>
```

---

## 执行顺序总结

```
第1回合 (必须最先):
  └─ Session A: Web Search 全链路修复

第2回合 (A 完成后，3 个会话可同时进行):
  ├─ Session B1: ChatViewModel 核心修复
  ├─ Session B2: UserSkillRegistry 完整实现
  └─ Session B3: 死控件修复

第3回合 (B 完成后，A 已是基础，3 个会话可同时进行):
  ├─ Session C1: SpaSettingsScreen 重写
  ├─ Session C2: 预设模型接线
  └─ Session C3: KG Toggle + WebSearch 配置

总计: 7 个独立会话，最少 3 个回合 (A → 第2回合并行 → 第3回合并行)
```

---

## 附录：快速验证命令

每个 Session 完成后在 native-ui 目录执行:
```bash
./gradlew compileDebugKotlin   # 编译检查
./gradlew test                 # 运行测试
```

如无 test 脚本或编译错误，先检查 import 声明是否完整。
