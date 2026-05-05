# Nexara 多语言 (i18n) 审计报告

> **日期**: 2026-05-04  
> **目标语言**: 中文（简体）+ English  
> **审计范围**: native-ui 全部 69 个 UI .kt 文件  
> **审计结论**: ❌ **完全不通过** — 零 i18n 基础设施，语言使用严重不一致

---

## 一、i18n 基础设施现状

| 检查项 | 状态 |
|--------|------|
| `res/values/strings.xml` | ❌ **不存在** — `app/src/main/res/` 整个目录缺失 |
| `res/values-zh-rCN/strings.xml` | ❌ **不存在** |
| `R.string` 引用 | ❌ **0 处** |
| `stringResource()` 调用 | ❌ **0 处** |
| 语言切换机制 | ❌ 无 — SettingsViewModel 有 `language` 字段但**仅存储不使用** |

**结论**: 项目 i18n 基础设施为零。所有用户可见文本硬编码在 Kotlin 源码中。

---

## 二、语言一致性分析 — 严重混合

### 问题概述

OpenCode 在不同 Session 中使用了**不同的默认语言**，导致 12 个文件写中文、其余写英文，形成严重的语言混乱：

| 语言模式 | 文件数 | 典型文件 |
|----------|--------|---------|
| **全中文硬编码** | 8 | BackupSettingsScreen, LocalModelsScreen, WorkbenchScreen, ProviderFormScreen, ProviderModelsScreen, AgentEditScreen, AgentRagConfigScreen, AgentAdvancedRetrievalScreen |
| **中英混合** | 4 | UserSettingsHomeScreen (主体中文), AgentHubScreen, AgentSessionsScreen, WelcomeScreen |
| **全英文硬编码** | 49+ | ChatScreen, SessionSettingsScreen, SessionSettingsSheet, SpaSettingsScreen, WorkspaceSheet, RagHomeScreen, 全局组件等 |

### 语言分布详情

```
ui/
├── common/          → 全部英文 (23 文件)
├── hub/
│   ├── AgentHubScreen.kt           → 中英混合 (搜索"搜索助手...", FAB "超级助手")
│   ├── AgentSessionsScreen.kt      → 中文 ("搜索会话...", "还没有对话", "新建会话")
│   ├── AgentEditScreen.kt          → 全中文 (25处)
│   ├── AgentRagConfigScreen.kt     → 全中文 (17处)
│   ├── AgentAdvancedRetrieval.kt   → 全中文 (20处)
│   └── UserSettingsHomeScreen.kt   → 全中文 (51处)
├── chat/
│   ├── ChatScreen.kt               → 全英文
│   ├── SessionSettingsScreen.kt    → 全英文
│   ├── SessionSettingsSheet.kt     → 全英文
│   ├── SpaSettingsScreen.kt        → 全英文
│   └── WorkspaceSheet.kt           → 全英文
├── settings/
│   ├── ProviderFormScreen.kt       → 全中文 (13处)
│   ├── ProviderModelsScreen.kt     → 全中文 (14处)
│   ├── BackupSettingsScreen.kt     → 全中文 (24处)
│   ├── WorkbenchScreen.kt          → 全中文 (15处)
│   ├── LocalModelsScreen.kt        → 全中文 (12处)
│   ├── SearchConfigScreen.kt       → 英文
│   ├── SkillsScreen.kt             → 英文
│   ├── ThemeScreen.kt              → 英文
│   └── TokenUsageScreen.kt         → 英文
├── rag/
│   ├── RagHomeScreen.kt            → 英文
│   ├── RagFolderScreen.kt          → 英文
│   ├── DocEditorScreen.kt          → 英文
│   ├── KnowledgeGraphScreen.kt     → 英文
│   ├── RagAdvancedScreen.kt        → 英文
│   ├── RagDebugScreen.kt           → 英文
│   ├── GlobalRagConfigScreen.kt    → 英文
│   └── AdvancedRetrievalScreen.kt  → 英文
└── welcome/WelcomeScreen.kt        → 中英混合 ("NEXARA" + "中文 (简体)")
```

---

## 三、全量硬编码文本清单

### 3.1 ui/common/ (全局组件) — 全英文，共约 55 条

| 文件 | 硬编码数 | 典型文本 |
|------|---------|---------|
| ModelPicker.kt | 5 | "Select Model", "Search models...", "No models available", "K context", "Selected" |
| FloatingTextEditor.kt | 3 | "Enter text...", "Back", "Save" |
| FloatingCodeEditor.kt | 2 | "Back", "Save" |
| InferencePresets.kt | 4 | "Precise", "Balanced", "Creative", "T:" |
| ColorPickerPanel.kt | 1 | "Custom" |
| ExecutionModeSelector.kt | 3 | AUTO/SEMI/MANUAL (枚举名) |
| ConfirmDialog.kt | 2 | "Confirm", "Cancel" |
| NexaraConfirmDialog.kt | 1 | "Cancel" |
| NexaraSearchBar.kt | 2 | "Search...", "Clear" |
| CollapsibleSection.kt | 2 | "Collapse", "Expand" |
| NexaraCollapsibleSection.kt | 2 | "Collapse", "Expand" |
| NexaraPageLayout.kt | 1 | "Back" |
| NexaraSettingsItem.kt | 1 | "Navigate" |
| SwipeableItem.kt | 1 | "Pin" |
| AgentAvatar.kt | 0 | 无用户可见文本 |
| SettingsInput.kt | 0 | label/placeholder 由调用者传入 |
| SettingsToggle.kt | 0 | 同上 |
| SettingsSectionHeader.kt | 0 | 同上 |
| NexaraGlassCard.kt | 0 | 无 |
| NexaraBottomSheet.kt | 0 | 无 |
| NexaraLoadingIndicator.kt | 0 | 无 |
| MarkdownText.kt | 0 | 无 |
| NexaraSnackbar.kt | 0 | 无 |

### 3.2 ui/hub/ — 混合中英文，共约 130 条

#### AgentHubScreen.kt — 中英混合 (13处中文)

| 行号 | 当前文本 | 语言 |
|------|---------|------|
| - | "搜索助手..." | 中文 |
| - | "超级助手" | 中文 |
| - | "创建新助手" | 中文 |
| - | "名称"/"描述"/"模型 ID"/"系统提示词" | 中文 |
| - | "添加"/"取消" | 中文 |
| - | "没有助手" / "点击 + 创建你的第一个 AI 助手" | 中文 |
| - | "固定"/"删除" | 中文 |
| - | "对话" / "你的智能助手团队" | 中文 |

#### AgentSessionsScreen.kt — 中文 (4处)

| 文本 | |
|------|-|
| "搜索会话..." | |
| "还没有对话" / "开始第一个吧" | |
| "新建会话" | |

#### AgentEditScreen.kt — 全中文 (25处)

| 文本 | |
|------|-|
| "编辑助手" | |
| "基本信息" / "名称" / "描述" | |
| "外观" / "性格" / "系统提示词" | |
| "已配置" / "未设置" | |
| "模型配置" / "知识" / "RAG 配置" / "高级检索" | |
| "危险区" / "删除助手" | |
| "确定要删除此助手吗？" / "此操作无法撤销" | |
| "删除" / "取消" | |

#### AgentRagConfigScreen.kt — 全中文 (17处)

#### AgentAdvancedRetrievalScreen.kt — 全中文 (20处)

#### UserSettingsHomeScreen.kt — 全中文 (51处)

包含: "设置"/"应用"/"提供商"/"通用"/"语言"/"外观"/"主题色"/"触觉反馈"/"模型预设"/"知识管理"/"工具"/"数据"/"关于"/"删除提供商"/"暂无提供商配置"/"添加提供商"/"管理模型"/"编辑名称"/"保存"/"取消" 等

### 3.3 ui/chat/ — 全英文，共约 120 条

#### ChatScreen.kt (12处)

| 文本 | 建议中文 |
|------|---------|
| "New Chat" | 新对话 |
| "Session Settings" | 会话设置 |
| "Token Stats" | Token 统计 |
| "Super Assistant" | 超级助手 |
| "Export Chat" | 导出对话 |
| "Message..." | 发消息... |
| "Stop" / "Send" | 停止 / 发送 |

#### SessionSettingsScreen.kt (35+处)

包含: "Settings"/"Active Agent"/"Export Session"/"Session Info"/"Session Title"/"AI Generated Title"/"Inference Parameters"/"Temperature"/"Precise"/"Creative"/"Top P"/"Max Tokens"/"RAG Settings"/"Long-term Memory"/"Knowledge Graph Extraction"/"Knowledge Base"/"Custom Prompt"/"Delete Session"/"Are you sure..."

#### SessionSettingsSheet.kt (30+处)

包含: "Model"/"Thinking Level"/"Stats"/"Tools"/"Minimal"/"Low"/"Medium"/"High"/"Prompt"/"Completion"/"RAG System"/"Reset"/"Time Injection"/"Strict Mode"/各种技能名

#### SpaSettingsScreen.kt (25+处)

包含: "Super Assistant"/"FAB Appearance"/"Icon Style"/"Rotation Animation"/"Glow Effect"/"Model Configuration"/"Knowledge Graph"/"Global Knowledge"/"Documents"/"Sessions"/"Vectors"/"Clean Ghost Data"/"Export History"

#### WorkspaceSheet.kt (20+处)

包含: "Workspace"/"Tasks"/"Artifacts"/"Files"/"Running"/"Pending"/"Completed"/"Preview"/"Edit"/"Save"

### 3.4 ui/settings/ — 混合，共约 140 条

#### 英文文件 (5个)

| 文件 | 硬编码数 |
|------|---------|
| SearchConfigScreen.kt | ~20 |
| SkillsScreen.kt | ~30 |
| ThemeScreen.kt | ~10 |
| TokenUsageScreen.kt | ~15 |
| SettingsViewModel.kt | ~5 |

#### 中文文件 (5个)

| 文件 | 硬编码数 | 典型文本 |
|------|---------|---------|
| BackupSettingsScreen.kt | 24 | "备份与恢复"/"备份内容"/"本地存储"/"WebDAV 云端"/"导出备份"/"导入备份" |
| LocalModelsScreen.kt | 12 | "本地模型"/"启用本地引擎"/"导入 GGUF 文件"/"活跃插槽"/"已导入模型"/"引擎状态" |
| WorkbenchScreen.kt | 15 | "工作台"/"服务器状态"/"连接详情"/"通知权限"/"电池优化" |
| ProviderFormScreen.kt | 13 | "提供商"/"名称"/"API Key"/"保存"/"取消" |
| ProviderModelsScreen.kt | 14 | "模型管理"/"自动获取"/"添加模型"/"全部禁用"/"测试" |

### 3.5 ui/rag/ — 全英文，共约 130 条

| 文件 | 硬编码数 | 典型文本 |
|------|---------|---------|
| RagHomeScreen.kt | ~30 | "Knowledge Base"/"Documents"/"Memory"/"Graph"/"Collections"/"Upload"/"Indexing" |
| RagFolderScreen.kt | ~15 | "Documents"/"Select All"/"Move"/"Delete"/"Vectorize" |
| DocEditorScreen.kt | ~20 | "Document Editor"/"Edit"/"Preview"/"Save"/"Large File Warning" |
| KnowledgeGraphScreen.kt | ~15 | "Knowledge Graph"/"Documents"/"Folders"/"Concepts"/"Details" |
| RagAdvancedScreen.kt | ~20 | "Knowledge Graph"/"JIT Micro-Graph"/"Cost Strategy"/"Incremental Hash" |
| RagDebugScreen.kt | ~15 | "Vector Stats"/"Total Vectors"/"Storage"/"Redundancy"/"Top Sessions" |
| GlobalRagConfigScreen.kt | ~20 | "RAG Settings"/"Chunk Size"/"Overlap"/"Memory"/"Context Window" |
| AdvancedRetrievalScreen.kt | ~15 | "Memory Retrieval"/"Document Retrieval"/"Reranking"/"Hybrid Search" |

### 3.6 ui/ 其他 — 约 10 条

| 文件 | 文本 |
|------|------|
| MainTabScaffold.kt | "CHAT"/"LIBRARY"/"SETTINGS" |
| WelcomeScreen.kt | "NEXARA"/"INTELLIGENCE REIMAGINED"/"English"/"中文 (简体)" |

---

## 四、总计

| 目录 | 文件数 | 硬编码文本总数 | 语言 |
|------|--------|---------------|------|
| ui/common/ | 23 | ~55 | 英文 |
| ui/hub/ | 9 | ~130 | **中英混合** |
| ui/chat/ | 13 | ~120 | 英文 |
| ui/settings/ | 10 | ~140 | **中英混合** |
| ui/rag/ | 14 | ~130 | 英文 |
| ui/ 其他 | 2 | ~10 | 中英混合 |
| **总计** | **71** | **~585** | — |

---

## 五、核心问题汇总

### 问题 1: 零 i18n 基础设施 (P0)

- `app/src/main/res/` 目录**完全不存在**
- 无 `strings.xml`、无 `values-zh-rCN/`
- 无 `R.string` 引用、无 `stringResource()` 调用
- SettingsViewModel 的 `language` 字段仅存储偏好，**从未被任何 UI 读取使用**

### 问题 2: 语言严重不一致 (P0)

- 8 个文件全中文、49+ 个文件全英文、4 个文件中英混合
- 同一功能在不同页面语言不同 (如 AgentHub 用中文 "搜索助手..."，ChatScreen 用英文 "Message...")
- 用户在设置中切换语言后**无任何效果**，因为没有任何 UI 文本响应语言变更

### 问题 3: 无语言切换运行时机制 (P1)

- 即使创建了 `strings.xml`，Android 默认只跟随系统语言
- 需要实现**应用内语言切换** (不依赖系统设置)，这需要：
  1. 自定义 `ContextWrapper` 覆盖 `attachBaseContext`
  2. `AppCompatDelegate.setApplicationLocales()` (AndroidX AppCompat 1.6+)
  3. 或手动维护 `Configuration` override

### 问题 4: 枚举/常量类语言硬编码 (P1)

- `AppTab` 枚举: `title = "CHAT"` — 不能直接调用 `stringResource()`
- `ExecutionMode` 枚举: `.name` 直接用作显示文本
- `ModelCapability` 枚举标签在多处重复定义
- 解决方案: 枚举持有 `@StringRes Int` 资源 ID

---

## 六、修复方案建议

### Phase 1: 基础设施搭建 (P0)

1. **创建资源目录结构**:
   ```
   app/src/main/res/
   ├── values/
   │   └── strings.xml          ← 默认 (英文)
   └── values-zh-rCN/
       └── strings.xml          ← 简体中文
   ```

2. **在 `build.gradle.kts` 中添加**:
   ```kotlin
   android {
       resourceConfiguration += listOf("en", "zh-rCN")
   }
   ```

### Phase 2: 全量字符串抽取 (~585 条)

将所有硬编码文本替换为 `stringResource(R.string.xxx)` 调用。

#### string key 命名规范建议:
```
{模块}_{组件}_{用途}

示例:
  chat_title_new          → "New Chat" / "新对话"
  settings_title          → "Settings" / "设置"
  hub_search_placeholder  → "Search agents..." / "搜索助手..."
  btn_confirm             → "Confirm" / "确认"
  btn_cancel              → "Cancel" / "取消"
  cd_back                 → "Back" / "返回"
  label_temperature       → "Temperature" / "温度"
```

#### 特殊处理:
- **枚举类**: 改为持有 `@StringRes Int`，在 Composable 中用 `stringResource()` 转换
- **ViewModel 中的文本**: 错误消息等通过 `StateFlow<Int>` (resourceId) 或 `StateFlow<String>` (已解析文本) 传递
- **动态拼接文本**: 使用 `getString(R.string.xxx, arg1, arg2)` 格式化

### Phase 3: 应用内语言切换 (P1)

1. 在 `NexaraApplication` 中实现 `ContextWrapper`:
   ```kotlin
   override fun attachBaseContext(base: Context) {
       val lang = PreferenceManager.getDefaultSharedPreferences(base)
           .getString("language", "zh") ?: "zh"
       val locale = Locale(if (lang == "zh") "zh-CN" else "en")
       val config = Configuration(base.resources.configuration)
       config.setLocale(locale)
       super.attachBaseContext(base.createConfigurationContext(config))
   }
   ```

2. 使用 AndroidX `AppCompatDelegate.setApplicationLocales()` (推荐)

3. 语言切换后 `recreate()` 所有 Activity

### Phase 4: 验证

- 中文模式下逐页验证所有文本为中文
- 英文模式下逐页验证所有文本为英文
- 语言切换即时生效（无需重启应用）

---

## 七、工作量估算

| 阶段 | 任务 | 预估 |
|------|------|------|
| Phase 1 | 基础设施搭建 | 0.5h |
| Phase 2 | 全量 ~585 条字符串抽取 + 双语翻译 | 6-8h (OpenCode) |
| Phase 3 | 应用内语言切换机制 | 2h |
| Phase 4 | 验证 + 修复 | 2h |
| **总计** | — | **~12h** |

---

> **结论**: 项目当前 i18n 状态为**零基础设施 + 语言严重混乱**，需要全量 ~585 条字符串的外部化和双语翻译，以及应用内语言切换机制的实现。建议作为独立 OpenCode Session 执行。
