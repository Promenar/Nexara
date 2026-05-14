# 会话级提示词 + Markdown 编辑器 + 助手设置视觉美化

> **日期**: 2026-05-14  
> **状态**: ✅ 已完成（含 ChatScreen 菜单补丁）  
> **并行策略**: 3 个独立会话，修改文件互不重叠，可完全并行

---

## 并行执行总览

```
Session A: 双层系统提示词后端        修改 ChatViewModel + ContextBuilder + ChatModels
              └─ 纯后端逻辑，3 文件

Session B: Markdown 编辑器 + 全场景 UI 接入  新建组件 + 替换旧编辑器 + ChatScreen 菜单入口
              └─ 1 新文件 + 3 旧替换 + 1 UI 新增

Session C: 助手设置视觉 MD3 美化    重做 AgentEditScreen 布局
              └─ 1 文件重构
```

**关键约束**：三个会话修改的文件**完全无重叠**，可同时启动，合并不冲突。

**⚠️ 逻辑依赖**：Session B 的 ChatScreen 菜单入口调用 `UnifiedPromptEditor`（Session B 自己创建）+ `SessionManager.updateSessionPrompt()`（已有）。Session A 纯后端，不依赖 B/C。

---

## 文件冲突矩阵

| 文件 | S-A | S-B | S-C |
|------|:---:|:---:|:---:|
| `ChatViewModel.kt` | ✏️ | | |
| `ContextBuilder.kt` | ✏️ | | |
| `ContextBuilderParams` (ChatModels.kt) | ✏️ | | |
| `ChatScreen.kt` | | ✏️ | |
| `SessionSettingsScreen.kt` / Sheet | | ✏️ | |
| `UnifiedPromptEditor.kt` (新) | | ✏️ | |
| `AgentEditScreen.kt` | | ✏️ | ✏️ |
| `AgentEditViewModel.kt` | | | ✏️ |
| `AgentRagConfigScreen.kt` | | ✏️ | |

---

## Session A：双层系统提示词（纯后端）

> **优先级**: P0  
> **工时**: 1h  
> **依赖**: 无  
> **并行组**: 可与 B/C 同时执行  
> **UI 入口**: 由 Session B 在 ChatScreen 三点菜单中实现

### 背景

当前 `ChatViewModel.generateMessage()` 第 279 行存在降级逻辑：

```kotlin
agentSystemPrompt = agentConfig.systemPrompt.ifBlank { sessionForCtx.customPrompt }
```

导致当 Agent 有 systemPrompt 时，Session customPrompt 被挤入 `agentSystemPrompt` 参数，ContextBuilder §4/§5 双重输出。真正的"双层提示词"应该是 Agent 和 Session 各自独立注入，互不覆盖。

**注意**：Session 级提示词的编辑 UI 由 Session B 在 ChatScreen 三点菜单中实现。Session A 只负责后端逻辑——确保 `Session.customPrompt` 字段被正确写入 DB 后，ContextBuilder 能将其作为第二层独立注入。

### 实施方案

#### 1. 分离 ChatViewModel 的 prompt 收集

**文件**: `ChatViewModel.kt` (第 265-279 行附近)

将 ContextBuilderParams 的构建改为：

```kotlin
val contextParams = ContextBuilderParams(
    ...
    agentSystemPrompt = agentConfig.systemPrompt,  // 始终传 Agent 的，不做 fallback
    sessionCustomPrompt = sessionForCtx.customPrompt,  // 新增：始终传 Session 的
    ...
)
```

#### 2. 更新 ContextBuilderParams

**文件**: `ChatModels.kt` (ContextBuilderParams 定义处)

新增字段：

```kotlin
data class ContextBuilderParams(
    ...
    val agentSystemPrompt: String?,     // Agent 级系统提示词
    val sessionCustomPrompt: String?,   // 新增：Session 级自定义提示词
    ...
)
```

#### 3. 更新 ContextBuilder.buildSystemPrompt()

**文件**: `ContextBuilder.kt` (第 152-163 行)

```kotlin
// 4. Agent System Prompt
params.agentSystemPrompt?.let { prompt ->
    if (prompt.isNotBlank()) {
        sb.appendLine(prompt)
        sb.appendLine()
    }
}

// 5. Session Custom Prompt
params.sessionCustomPrompt?.let { prompt ->
    if (prompt.isNotBlank()) {
        sb.appendLine("## Session Instructions")
        sb.appendLine(prompt)
        sb.appendLine()
    }
}
```

`session.customPrompt` 的直接读取改为使用 `params.sessionCustomPrompt`。

### 验证标准
- Agent 有 prompt + Session 有 prompt → 两者都出现在最终 system prompt 中（双层）
- Agent 无 prompt + Session 有 prompt → 仅 Session prompt 出现
- Agent 有 prompt + Session 无 prompt → 仅 Agent prompt 出现

---

## Session B：Markdown 编辑器原子组件 + 统一替换 + ChatScreen 菜单入口

> **优先级**: P1  
> **工时**: 3.5h  
> **依赖**: 无  
> **并行组**: 可与 A/C 同时执行  
> **职责**: 新建 UnifiedPromptEditor → 替换 3 处旧编辑器 → 在 ChatScreen 三点菜单新增"编辑会话提示词"入口

### 背景

当前 3 处提示词编辑使用 `FloatingTextEditor` — 全屏纯文本，无 Markdown 支持，无预览模式。项目已有成熟的 `MarkdownText` 渲染器，但未用于编辑器。

### 新建组件：`UnifiedPromptEditor`

**文件**: `native-ui/app/src/main/java/com/promenar/nexara/ui/common/UnifiedPromptEditor.kt`

设计为可复用的原子组件，支持三种模式：

| 模式 | 布局 | 适用场景 |
|------|------|---------|
| **Dialog** | 全屏 Dialog，与现有 FloatingTextEditor 一致 | Agent 系统提示词编辑 |
| **Sheet** | ModalBottomSheet，3/4 屏高 | Session 临时提示词编辑 |
| **Inline** | 内联 Composable，无弹窗 | 嵌入设置页面直接编辑（后续扩展） |

核心功能：
- **编辑面板**：`BasicTextField` + 等宽字体 + 行号列 + 字数统计
- **实时预览面板**：复用 `MarkdownText（markdown=..., isStreaming=false）`
- **顶部 Tab 切换**：编辑 | 预览 | 分屏（50/50 横向）
- **底部状态栏**：字数 / 行数 / 字符数
- **保存/撤销**：工具栏按钮

关键架构决策：
- 使用 `remember` 本地状态，不依赖外部 ViewModel
- `onSave` 回调传入最终文本
- 尺寸自适应模式参数（`Dialog` vs `Sheet`）

### 替换清单 + 新增入口

| 原组件 | 文件 | 操作 |
|--------|------|------|
| Agent 系统提示词 | `AgentEditScreen.kt` (~第 395-432 行) | 替换为 `UnifiedPromptEditor(mode=Dialog)` |
| Session 自定义提示词 | `SessionSettingsScreen.kt` (~第 132-142 行) | 替换为 `UnifiedPromptEditor(mode=Dialog)` |
| Summary Template | `AgentRagConfigScreen.kt` | 替换为 `UnifiedPromptEditor(mode=Dialog)` |
| **新增** | `ChatScreen.kt` `ChatTopBar` 三点菜单 | 新增 "编辑会话提示词" 菜单项 → `UnifiedPromptEditor(mode=Dialog)` |

### ChatScreen 菜单入口（新增）

**文件**: `ChatScreen.kt` 的 `ChatTopBar` Composable（约第 736-777 行）

当前三点菜单结构：
```
DropdownMenu:
  - Session Settings (Tune)
  - Rename (Edit)
  - Delete Session (Delete, red)
```

改造为：
```
DropdownMenu:
  - Session Settings (Tune)
  - Session Prompt (Description/Writing icon)  ← 新增
  ───────────────── (Divider)
  - Rename (Edit)
  - Delete Session (Delete, red)
```

实现：
1. `ChatTopBar` 新增回调参数 `onSessionPrompt: () -> Unit`
2. `ChatScreen` 新增状态 `var showSessionPromptEditor by remember { mutableStateOf(false) }`
3. 菜单中插入 `DropdownMenuItem`：
   ```kotlin
   DropdownMenuItem(
       text = { Text("Session Prompt") },
       leadingIcon = { Icon(Icons.Rounded.Description, null, Modifier.size(18.dp)) },
       onClick = {
           showMenu = false
           showSessionPromptEditor = true  // 实际通过回调触发
       }
   )
   HorizontalDivider()  // 分隔设置类和危险操作
   ```
4. 在 `ChatScreen` 中渲染编辑器：
   ```kotlin
   if (showSessionPromptEditor) {
       UnifiedPromptEditor(
           show = true,
           onDismiss = { showSessionPromptEditor = false },
           onSave = { text ->
               chatViewModel.updateSessionPrompt(text)
               showSessionPromptEditor = false
           },
           initialText = uiState.session?.customPrompt ?: "",
           title = "Session Prompt",
           mode = EditorMode.DIALOG
       )
   }
   ```
5. `ChatViewModel` 新增方法（或复用已有）：
   ```kotlin
   fun updateSessionPrompt(prompt: String) {
       val sessionId = _currentSessionId.value ?: return
       viewModelScope.launch {
           sessionManager.updateSessionPrompt(sessionId, prompt.ifBlank { null })
       }
   }
   ```
   > `SessionManager.updateSessionPrompt()` 已存在（第 77 行），直接调用即可。

### 验证标准
- 编辑模式输入 Markdown 文本 → 切换预览 → 正确渲染标题/列表/代码块
- 分屏模式下左侧编辑、右侧实时预览
- Dialog 模式下保存后文本正确回传

---

## Session C：助手设置页面视觉 MD3 美化

> **优先级**: P1  
> **工时**: 3h  
> **依赖**: 无  
> **并行组**: 可与 A/B 同时执行

### 背景

`AgentEditScreen` 当前使用完全自定义的 Glass 设计语言：
- 大量 `NexaraGlassCard` 包裹（rgba(255,255,255,0.03) 半透明背景）
- 弹窗使用全屏 `Dialog` 而非 `ModalBottomSheet`
- Section 间靠间距区分，缺少视觉分隔和层次
- 信息密度低（每个配置项占满一个卡片）
- 颜色图标选择器占据过多空间

### 美化方案

#### 重构布局结构

```
Before (竖向堆叠卡片):          After (M3 紧凑布局):
┌──────────────────────┐       ┌──────────────────────┐
│ Name                  │       │ Agent Name           │
│ [_______________]     │       │ [_______________]     │
└──────────────────────┘       ├──────────────────────┤
┌──────────────────────┐       │ Description          │
│ Description           │       │ [_______________]     │
│ [_______________]     │       ├──────────────────────┤
└──────────────────────┘       │                      │
        12.dp gap               │     头像 + 图标 + 色   │
┌──────────────────────┐       │    (横向排列)         │
│ 头像/图标/颜色         │       ├──────────────────────┤
│ (大段展开)            │       │ System Prompt        │
└──────────────────────┘       │ [2行预览...]    编辑 > │
                               ├──────────────────────┤
                               │ Model: xxx      选择 > │
                               │ Inference: [●●○]      │
                               ├──────────────────────┤
                               │ Knowledge      配置 >  │
                               │ Advanced RAG   配置 >  │
                               └──────────────────────┘
```

#### 关键改动

1. **改用 `ListDetailPaneScaffold` 或标准卡片布局**：
   - Section 用 `Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest))`
   - 顶部头像/图标/颜色合并为一行（头像 48dp + 图标选择折叠为 TextButton + 色相 Slider 折叠）
   - Section 之间用 1dp `HorizontalDivider` 分隔

2. **提示词卡片增强**：
   - 从 2 行预览 → 5 行预览 + "编辑" 文字按钮
   - 显示字数统计 badge

3. **Model 选择改为 ModalBottomSheet**：
   - 替换全屏 Dialog
   - 仅在未选模型时显示 "Not Selected" + Primary 按钮

4. **推理预设改为 Chip 模式**：
   - 当前 3 个等宽卡片 → 3 个 `FilterChip`（`assistChipColors`）
   - 节省约 60% 垂直空间

5. **删除危险区域独立 Section**：
   - 将"Delete Agent"降级为底部 `TextButton` (红色)，不单独成 Card

6. **增加 FAB**：
   - 右下角 56dp FAB + Save 图标（`Icons.Rounded.Check`）
   - 仅在 `isDirty` 时显示并启用（需要 ViewModel 新增 `_isDirty` StateFlow）

### 验证标准
- 信息密度提升 40%+（更多配置项在一屏内可见）
- 提示词预览行数从 2 行 → 5 行
- 视觉风格与 MD3 标准对齐（不破坏 Nexara 品牌色）
- FAB 保存按钮提升操作效率

**注意**：保持 NexaraColors 品牌色（Primary = 紫蓝），仅调整组件结构和布局，不改变配色方案。

---

## 各会话完整提示词

### Session A 提示词（双层系统提示词）

```
## 任务：Nexara 双层系统提示词实现

### 背景
当前 `ChatViewModel.generateMessage()` 的 Agent/Session prompt 是"降级覆盖"逻辑：
当 Agent 有 systemPrompt 时，Session customPrompt 被覆盖。需求是改为真正的"双层"——Agent
和 Session 的提示词同时注入到最终 system prompt 中，形成层级结构。

### 项目路径
k:/Nexara/native-ui

### 需要修改的文件

#### 1. ChatViewModel.kt — 分离 prompt 收集

文件: app/src/main/java/com/promenar/nexara/ui/chat/ChatViewModel.kt

在 generateMessage() 方法中（约第 265-279 行），找到 ContextBuilderParams 构建处。

当前代码:
```kotlin
val contextParams = ContextBuilderParams(
    ...
    agentSystemPrompt = agentConfig.systemPrompt.ifBlank { sessionForCtx.customPrompt }
    ...
)
```

修改为:
```kotlin
val contextParams = ContextBuilderParams(
    ...
    agentSystemPrompt = agentConfig.systemPrompt,
    sessionCustomPrompt = sessionForCtx.customPrompt,
    ...
)
```

#### 2. ChatModels.kt — 新增 sessionCustomPrompt 字段

文件: app/src/main/java/com/promenar/nexara/data/model/ChatModels.kt

找到 `ContextBuilderParams` data class 定义（约在文件后半部分），在 `agentSystemPrompt` 字段附近新增:

```kotlin
data class ContextBuilderParams(
    ...
    val agentSystemPrompt: String?,
    val sessionCustomPrompt: String?,  // 新增：Session 级自定义提示词
    ...
)
```

#### 3. ContextBuilder.kt — 使用新参数

文件: app/src/main/java/com/promenar/nexara/ui/chat/manager/ContextBuilder.kt

在 buildSystemPrompt() 方法中（约第 152-163 行），找到处理 Session Custom Prompt 的部分。

当前代码:
```kotlin
// 5. Session Custom Prompt
if (session.customPrompt != null) {
    sb.appendLine()
    sb.appendLine(session.customPrompt)
}
```

修改为使用 params 而不是 session 对象:
```kotlin
// 5. Session Custom Prompt
params.sessionCustomPrompt?.let { prompt ->
    if (prompt.isNotBlank()) {
        sb.appendLine()
        sb.appendLine("## Session Instructions")
        sb.appendLine(prompt)
        sb.appendLine()
    }
}
```

### 改动量统计
- ChatViewModel.kt: 1 行修改
- ChatModels.kt: +1 行
- ContextBuilder.kt: ~8 行重构
- 总计: ~10 行

### 验证
- Agent 有 prompt + Session 有 prompt → 两者都出现在最终 system prompt 中
- Agent 无 prompt + Session 有 prompt → 仅 Session prompt 出现
- Agent 有 prompt + Session 无 prompt → 仅 Agent prompt 出现
```

---

### Session B 提示词（Markdown 编辑器）

```
## 任务：Nexara 统一 Markdown 提示词编辑器组件

### 背景
当前项目有 3 处提示词编辑入口使用 `FloatingTextEditor`（全屏纯文本 BasicTextField），
无 Markdown 预览，无行号，无字数统计。需要创建统一的 Markdown 编辑/预览双模式组件，
替换所有 3 处编辑器。

### 项目路径
k:/Nexara/native-ui

### 第一阶段：新建 UnifiedPromptEditor 组件

#### 新建文件: app/src/main/java/com/promenar/nexara/ui/common/UnifiedPromptEditor.kt

创建可复用的原子编辑器组件，参数签名:

```kotlin
enum class EditorMode { DIALOG, SHEET }

@Composable
fun UnifiedPromptEditor(
    show: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    initialText: String = "",
    title: String = "Edit Prompt",
    placeholder: String = "Enter your system prompt...",
    mode: EditorMode = EditorMode.DIALOG,
    fontSize: Int = 13
)
```

##### 行为要求:

1. **TopBar**: 标题 + 关闭按钮 + 保存按钮（Primary 圆角方块）
2. **Tab Row**: 三个 Tab — "Editor" | "Preview" | "Split"（分屏 50/50）
3. **Editor Tab**:
   - 左侧 36dp 行号列（深色背景，12sp 等宽数字，右边对齐）
   - 右侧 BasicTextField (Monospace, 14sp)
   - 支持垂直滚动
4. **Preview Tab**:
   - 复用 `MarkdownText(markdown=text, isStreaming=false, fontSize=fontSize, showCursor=false)`
   - 仅在用户切换到 Preview 时渲染
5. **Split Tab**:
   - `Row` 横向布局，左侧 Editor (0.5f), 右侧 Preview (0.5f)
   - 中间 1dp 分隔线
6. **Bottom Bar**: 字数 / 行数 / 字符数统计
7. **Mode 适配**:
   - `DIALOG`: 全屏 `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`
   - `SHEET`: `ModalBottomSheet`（如果当前时间允许，暂以 Dialog 实现即可，Sheet 留 TODO）

##### 组件依赖:
```kotlin
import com.promenar.nexara.ui.common.MarkdownText  // 现有组件
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraTypography
```

### 第二阶段：替换 3 处旧编辑器

#### 2.1 AgentEditScreen.kt

文件: app/src/main/java/com/promenar/nexara/ui/hub/AgentEditScreen.kt

找到系统提示词编辑区域（约第 385-440 行）:
- 当前: 点击 NexaraGlassCard 设置 `showSystemPromptEditor = true`，弹出 FloatingTextEditor
- 替换: 改为弹出 `UnifiedPromptEditor(show=showSystemPromptEditor, mode=DIALOG, title="System Prompt")`
- `onSave` 回调调用 `viewModel.setSystemPrompt(it)`
- 如果 ViewModel 没有 setSystemPrompt 方法，则调用 `viewModel.updateField("systemPrompt", it)` 或现有的等效方法

删除 `var showSystemPromptEditor by remember { mutableStateOf(false) }` 状态变量（由 UnifiedPromptEditor 内部管理）。

#### 2.2 SessionSettingsScreen.kt（或 SessionSettingsSheet.kt）

文件: app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsScreen.kt

找到 "Custom Prompt" 或 "Session Prompt" 设置项（约第 120-145 行）:
- 当前: 点击打开 FloatingTextEditor
- 替换: `UnifiedPromptEditor(show=showSessionPromptEditor, mode=DIALOG, title="Session Prompt")`

#### 2.3 AgentRagConfigScreen.kt

文件: app/src/main/java/com/promenar/nexara/ui/hub/AgentRagConfigScreen.kt

查找 Summary Template 编辑入口，替换 FloatingTextEditor → UnifiedPromptEditor。

### 改动量统计
- 新文件: ~250 行
- AgentEditScreen.kt: ~15 行修改
- SessionSettingsScreen.kt: ~10 行修改
- AgentRagConfigScreen.kt: ~10 行修改
- 总计: ~285 行

### 验证
- 打开编辑器 → 输入 "# Hello\n\n- item 1\n- item 2"
- 切换到 Preview Tab → 正确渲染标题和列表
- 切换到 Split Tab → 左侧编辑源码，右侧实时预览
- 点击 Save → onSave 回调触发，文本正确传递
```

---

### Session C 提示词（助手设置视觉美化）

```
## 任务：Nexara 助手设置页面 MD3 视觉美化

### 背景
AgentEditScreen 当前使用大量 NexaraGlassCard 包裹每个配置项，信息密度低，
弹窗使用全屏 Dialog 而非 BottomSheet，Section 间无视觉分隔。需要重做布局，
提升信息密度至 MD3 标准，但保留 NexaraColors 品牌色。

### 项目路径
k:/Nexara/native-ui

### 需要重做的文件

#### 文件: app/src/main/java/com/promenar/nexara/ui/hub/AgentEditScreen.kt

##### 1. 布局框架重构

当前: `Scaffold` + `LazyColumn` + `NexaraGlassCard` 每个配置项
改为: `Scaffold` + `LazyColumn` + M3 `Card`（`CardDefaults.cardColors(containerColor = SurfaceContainerHighest)`）

全局替换 `NexaraGlassCard` → `Card`（保留圆角 12dp 设置）适用于配置卡片。

##### 2. 头像/图标/颜色合并为一行

当前: 头像（100dp 居中）、图标选择（可折叠网格 4 列）、颜色选择（10 色块 + 滑条）分别占用大量垂直空间。

改为:
```kotlin
Row(verticalAlignment = CenterVertically) {
    AgentAvatar(modifier = Modifier.size(48.dp))  // 从 100dp 缩小
    
    Spacer(Modifier.width(16.dp))
    
    Column(Modifier.weight(1f)) {
        Text("Icon & Color", style = NexaraTypography.labelSmall)
        // 图标选择：横向滚动的 FilterChip 列表（当前选中的 icon 高亮）
        // 颜色选择：点击打开 ColorPicker 的 BottomSheet
    }
}
```

##### 3. 提示词卡片增强

- 预览行数从 `maxLines = 2` → `maxLines = 5`
- 标题行添加字数 badge: `"${systemPrompt.length} chars"`
- 右侧添加 `SettingsToggle` 或 `TextButton("Edit")` 替代纯 clickable 卡片

##### 4. Model 选择改为 ModalBottomSheet

当前: 全屏 Dialog + ModelPicker。
改为: 点击卡片 → 打开 `ModalBottomSheet(onDismissRequest = ...) { ModelPicker(...) }`

```kotlin
var showModelSheet by remember { mutableStateOf(false) }
// 卡片点击 → showModelSheet = true
if (showModelSheet) {
    ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
        ModelPicker(
            selectedModel = currentModel,
            onModelSelect = { model ->
                viewModel.setModel(model)
                showModelSheet = false
            },
            // ... 其他参数
        )
    }
}
```

##### 5. 推理预设改为 Chip

当前: `InferencePresets` 使用 3 个等宽 `NexaraGlassCard` 横向排列。
改为: 3 个 `FilterChip`（`AssistChip`）`Row(horizontalArrangement = spacedBy(8.dp))`。

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    InferPreset.entries.forEach { preset ->
        FilterChip(
            selected = currentPreset == preset,
            onClick = { viewModel.setPreset(preset) },
            label = { Text(preset.label) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = NexaraColors.Primary.copy(alpha = 0.15f),
                selectedLabelColor = NexaraColors.Primary
            )
        )
    }
}
```

##### 6. 节段分隔

当前: Section 间仅靠 `Arrangement.spacedBy(12.dp)` 区分。
改为: Section 间增加 1dp `HorizontalDivider` + Section 标题（`Text(labelSmall, uppercase, Bold, Outline color)`）。

##### 7. 保留的现有组件（不做修改）

- `SettingsInput` — 名称和描述输入
- `NexaraSettingsItem` — Knowledge/Advanced RAG 导航
- `ColorPickerPanel` — 颜色拾取（仅缩小尺寸）

##### 8. 删除的组件

- `InferencePresets` 组件 → 改为内联 FilterChip Row
- "固定在侧边栏" `SettingsToggle` → 已删除（上一轮修复）
- 独立的"危险区域"卡片 → 降级为底部 `TextButton("Delete Agent", color=Error)`

### 改动量统计
- AgentEditScreen.kt: ~100 行重构（改布局，不改逻辑）
- 无新增文件
- 无修改 ViewModel

### 验证
- 一屏内至少可见 4 个配置 Section（现为 2-3 个）
- 头像缩小至 48dp，与图标/颜色同行
- 模型选择用 BottomSheet 而非全屏 Dialog
- 推理预设使用 Chip 而非等宽卡片
- Section 间有清晰的视觉分隔
- 品牌色 NexaraColors.Primary 保持不变
```

---

## 总计

| 会话 | 工时 | 新增文件 | 修改文件 | 风险 |
|------|------|------|------|------|
| **S-A**: 双层提示词后端 | 1h | 0 | 3 | 低 |
| **S-B**: Markdown 编辑器 + 全场景接入 | 3.5h | 1 | 4（含 ChatScreen 菜单） | 中 |
| **S-C**: 视觉美化 | 3h | 0 | 1 | 中 |
| **合计** | **7.5h ≈ 1 天** | 1 | 8 文件 | |

**并行建议**: 三会话完全独立，文件无冲突，建议全部同时启动。

**UI 架构**: 会话提示词的编辑入口位于 ChatScreen 右上角三点菜单 → `Session Prompt`（Session B 实现），与已有的 SessionSettings BottomSheet 中的模型/参数配置分离，各自独立。
