# Nexara native-ui 全栈逻辑审计报告

> 审计日期: 2026-05-11
> 审计范围: `native-ui/app/src/main/java/com/promenar/nexara/` 全部 Kotlin 源码
> 方法: 链路追踪 + 状态幽灵检测 + 工具链可见性审计 + 代码完整性校验

---

## 一、僵尸功能清单 (Zombie Feature List)

### 等级定义
- **P0 (CRITICAL)**: 有界面、零逻辑 — 模型完全感知不到功能
- **P1 (HIGH)**: 部分断裂 — 配置持久化但未生效
- **P2 (LOW)**: 死控件 — 空 onClick / 硬编码 / 纯装饰

---

### 🔴 P0 — 致命僵尸

| # | 功能 | UI 位置 | 断裂点 | 代码行 |
|---|------|---------|--------|--------|
| P0-1 | **Web Search 完整功能** | `SessionSettingsSheet:ToolsPanel` + `SearchConfigScreen` | `ContextBuilder` 初始化时未传入 `webSearchProvider`；`nexara_search` 全 8 个 SharedPreferences 键写入但从未被 `ChatViewModel`/`ContextBuilder` 读取 | `ChatViewModel.kt:78-81`, `ContextBuilder.kt:48-51` |
| P0-2 | **Loop Limit (自动循环次数)** | `SkillsScreen.kt:149-177` | `SettingsViewModel.updateLoopLimit()` 写入 `nexara_settings.loop_limit`，但 `ChatViewModel.generateMessage():194` 使用 `session.autoLoopLimit` (默认5，`ChatModels.kt:293`)，从未读取 SharedPreferences | `ChatViewModel.kt:194`, `SkillsScreen.kt:149` |
| P0-3 | **UserSkillRegistry 全部方法** | `SkillsScreen.kt` custom tab | `getSkill()` 返回 `null`，`getAllSkills()` 返回 `emptyList()`，`getAllTools()` 返回 `emptyList()`，注释写 `// Implementation for...` | `UserSkillRegistry.kt:13-25` |
| P0-4 | **CustomDatabaseSkill.execute()** | `SkillsScreen.kt` 用户自定义工具 | 永远返回 `"Logic not yet implemented"` | `UserSkillRegistry.kt:37-38` |
| P0-5 | **SpaSettingsScreen 全部控件** | `SpaSettingsScreen.kt` 整页 | 所有状态为局部 `remember { mutableStateOf() }` (行 71-78)，无 ViewModel、无持久化、无输出，TopAppBar 引用不存在 | `SpaSettingsScreen.kt:66-381` |
| P0-6 | **Search Engine Configuration** | `SkillsScreen.kt:814-833` 搜索配置 Sheet | 引擎选择、深度、结果数写入 `nexara_search` prefs，但 `WebSearchSkill` 和 `ContextBuilder` 均未读取 | `SearchConfigViewModel.kt`, `ContextBuilder.kt` |
| P0-7 | **SessionSettingsSheet webSearch toggle 后端** | `SessionSettingsSheet.kt:788` | toggle 写入 `session.options.webSearch`，`PromptRequest.webSearch` 已设值，但无搜索 Provider 执行实际搜索并注入结果 | `ChatViewModel.kt:254`, `ContextBuilder.kt:72-80` |

### 🟡 P1 — 部分断裂

| # | 功能 | 断裂点 | 代码行 |
|---|------|--------|--------|
| P1-1 | **Knowledge Graph toggle** | `enableKnowledgeGraph` 写入 `session.ragOptions`，但 `ContextBuilder.buildContext():55-59` 调用 `kgProvider.extractContext()` 时未检查该开关 | `SessionSettingsScreen.kt:299`, `ContextBuilder.kt:55` |
| P1-2 | **Preset Models (image/embedding/rerank)** | `SettingsViewModel.setPresetModel()` 写入 SharedPreferences，但 LLM pipeline 仅消费 `preset_summary_model` | `UserSettingsHomeScreen.kt:425-449`, `NexaraApplication.kt:165-170` |
| P1-3 | **Haptic Feedback** | `SettingsViewModel.hapticEnabled` 写入 `nexara_settings.haptic_enabled`，但应用中无任何 `performHapticFeedback()` 调用 | `UserSettingsHomeScreen.kt:114` |
| P1-4 | **Rerank toggle** | `SessionSettingsSheet.kt:793` 写入 `session.ragOptions.enableRerank`，`ContextBuilder` 传递该值但 `MemoryManagerRagAdapter` 消费路径待验证 | `ContextBuilder.kt:94` |

### 🟢 P2 — 死控件

| # | 位置 | 代码 |
|---|------|------|
| P2-1 | `SessionSettingsScreen.kt:189` | Swap Agent 按钮 `onClick = {}` |
| P2-2 | `SessionSettingsScreen.kt:208` | Export 按钮 `onClick = {}` |
| P2-3 | `SessionSettingsScreen.kt:338` | Add Docs 按钮 `onClick = {}` |
| P2-4 | `SpaSettingsScreen.kt:334` | Clean Ghost 按钮 `onClick = {}` |
| P2-5 | `SpaSettingsScreen.kt:346` | Export History 按钮 `onClick = {}` |
| P2-6 | `SpaSettingsScreen.kt:324-327` | StatCard 三组数据硬编码 `"1,204"`, `"8,432"`, `"2.4M"` |
| P2-7 | `SkillsScreen.kt:733` | MCP Default 开关 `onCheckedChange = {}` |
| P2-8 | `UserSettingsHomeScreen.kt:510` | About 卡片 `onClick = {}` |

---

## 二、TODO/FIXME/Placeholder 清单

| 文件 | 行 | 内容 | 严重度 |
|------|----|------|--------|
| `ChatViewModel.kt` | 422 | `val maxTokens = 128000 // TODO: Get from model spec` | P1 |
| `ChatViewModel.kt` | 836 | `val max = 128000 // TODO: Get from model spec` | P1 |
| `ContextBuilder.kt` | 131 | `// 2. Tools Instructions (Placeholder...)` | P1 |
| `BackupSettingsScreen.kt` | 321 | `// TODO: Add file picker for cloud files` | P2 |
| `BackupSettingsScreen.kt` | 397 | `// TODO: Implement test connection` | P2 |
| `UserSkillRegistry.kt` | 15,19,24 | `// Implementation for...` + return null/emptyList | P0 |
| `CustomDatabaseSkill` | 38 | `"Logic not yet implemented"` | P0 |

---

## 三、配置一致性报告

### `nexara_settings` SharedPreferences (12 个键)

| 键 | UI 写入 | 消费端 | 状态 |
|----|---------|--------|------|
| `loop_limit` | `SettingsVM.updateLoopLimit()` | ❌ 永不读取 | **断裂** |
| `haptic_enabled` | `SettingsVM.setHaptic()` | ❌ 永不读取 | **断裂** |
| `enabled_skills` | `SettingsVM.toggleSkill()` | `ChatVM.buildToolList():687` | ✅ 一致 |
| `preset_summary_model` | `SettingsVM.setPresetModel()` | `ChatVM:426,592` | ✅ 一致 |
| `preset_image_model` | `SettingsVM.setPresetModel()` | ❌ 永不读取 | **断裂** |
| `preset_embedding_model` | `SettingsVM.setPresetModel()` | ❌ 永不读取 | **断裂** |
| `preset_rerank_model` | `SettingsVM.setPresetModel()` | ❌ 永不读取 | **断裂** |
| `user_name` | `SettingsVM.updateUserName()` | 仅 UI | — |
| `user_avatar` | `SettingsVM.updateUserAvatar()` | 仅 UI | — |
| `theme_mode` | `SettingsVM.setThemeMode()` | Theme.kt | ✅ 一致 |
| `language` | `SettingsVM.setLanguage()` | LocaleHelper | ✅ 一致 |
| `enabled_models` | `SettingsVM.toggleModel()` | `SessionSettingsSheet:ModelPanel` | ✅ 一致 |

### `nexara_search` SharedPreferences (8 个键 — **全幽灵存储**)

| 键 | UI 写入 | 消费端 | 状态 |
|----|---------|--------|------|
| `web_search_enabled` | `SearchConfigVM` | ❌ | **断裂** |
| `search_engine` | `SearchConfigVM` | ❌ | **断裂** |
| `searxng_url` | `SearchConfigVM` | ❌ | **断裂** |
| `tavily_api_key` | `SearchConfigVM` | ❌ | **断裂** |
| `search_depth` | `SearchConfigVM` | ❌ | **断裂** |
| `result_count` | `SearchConfigVM` | ❌ | **断裂** |
| `include_domains` | `SearchConfigVM` | ❌ | **断裂** |
| `exclude_domains` | `SearchConfigVM` | ❌ | **断裂** |

---

## 四、已连通链路 (Verified Connected)

```
AgentEditScreen → AgentEditViewModel → Room(AgentEntity) → ChatViewModel → ContextBuilder.buildSystemPrompt()
  → agentSystemPrompt 注入 ✅

SessionSettingsSheet::temperature/topP/maxTokens → ChatViewModel.updateInferenceParams()
  → PromptRequest → LlmProtocol.sendPrompt() ✅

SessionSettingsSheet::timeInjection toggle → SessionOptions.enableTimeInjection
  → ContextBuilder.buildSystemPrompt() [System Time: ...] 注入 ✅

SessionSettingsSheet::toolsEnabled toggle → SessionOptions.toolsEnabled
  → ChatViewModel.buildToolList() → tools 数组控空 ✅

SkillsScreen skill toggles → SharedPreferences enabled_skills
  → ChatViewModel.buildToolList() → skillRegistry.getAllTools() → ProtocolTool 定义 ✅

SessionSettingsScreen::customPrompt → ChatViewModel.updateCustomPrompt()
  → ContextBuilder.buildSystemPrompt() session.customPrompt 注入 ✅

SessionSettingsSheet::streamTimeout → updateInferenceParams() → PromptRequest.streamTimeout ✅
SessionSettingsSheet::autoSummaryThreshold → updateInferenceParams() → generateMessage() 摘要触发 ✅
SessionSettingsSheet::activeContextWindow → updateInferenceParams() → getSafeActiveWindow() ✅
```

---

## 五、断裂链路总览图

```
                          UI Layer                    |         Data Layer          |     AI Engine Layer
                          =========                   |         ==========          |     ================

SearchConfigScreen ───→ SearchConfigVM ───→ nexara_search prefs ───→ [断点 ❌] ───→ ContextBuilder (无 WebSearchProvider)
SessionSettingsSheet ──→ toggle("webSearch") ──→ session.options.webSearch ──→ PromptRequest.webSearch ──→ [协议层?]
                                                                                   [断点 ❌] ──→ 无实际搜索结果

SkillsScreen ───→ loop_limit ──→ nexara_settings prefs ──→ [断点 ❌] ──→ ChatVM 用硬编码 session.autoLoopLimit=5

SkillsScreen(User) ──→ CustomSkillEntity(Room) ──→ UserSkillRegistry ──→ [断点 ❌] ──→ getSkill()=null

Settings(Image) ───→ presets ──→ SharedPrefs ──→ [断点 ❌] ──→ ImageService 不读取
Settings(Embedding) ──→ presets ──→ SharedPrefs ──→ [断点 ❌] ──→ EmbeddingClient 不读取
Settings(Rerank) ───→ presets ──→ SharedPrefs ──→ [断点 ❌] ──→ Reranker 不读取

SpaSettingsScreen ──→ [无连接 ❌] ──→ 全部 remember{} 局部状态
```

---

## 六、受影响的文件地图

| 文件 | 当前行数 | 涉及问题 | 变更风险 |
|------|---------|----------|----------|
| `ChatViewModel.kt` | 849 | P0-2, P1-2, P0-1, TODO×2 | **HIGH** — 核心文件，需谨慎增量变更 |
| `ContextBuilder.kt` | 193 | P0-1, P1-1, TODO | **HIGH** — 构造参数变更影响 ChatViewModel |
| `NexaraApplication.kt` | 339 | P0-1 (需创建 WebSearchProvider) | MEDIUM |
| `UserSkillRegistry.kt` | 40 | P0-3 | LOW — 独立文件 |
| `SettingsViewModel.kt` | 737 | P1-3 | LOW — 增量添加 |
| `SpaSettingsScreen.kt` | 407 | P0-5 (全页面重写) | LOW — 现有代码零依赖 |
| `SessionSettingsScreen.kt` | 562 | P2-1~P2-3 | LOW — UI 级修改 |
| `SearchConfigViewModel.kt` | 105 | P0-6 (需添加被消费逻辑) | MEDIUM |
| `SkillsScreen.kt` | 947 | P2-7 | LOW — 单个 onClick |
| `ChatModels.kt` | 296 | 新增数据类可能 | LOW |
| `SkillRepository.kt` | 需查看 | P0-3 | MEDIUM |
| `LocalModelsScreen.kt` | 需查看 | P0-5 (占位符) | — |
