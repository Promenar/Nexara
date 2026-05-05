# OpenCode 指令模板 — Session G3: 会话设置 & 聊天增强

> **项目**: Nexara Native UI  
> **工作目录**: `k:/Nexara/native-ui/`  
> **Session**: G3 — 会话设置 & 聊天增强  
> **前置依赖**: G0 (全局组件) + G1 (导航参数) + G2 (Agent管理)  
> **设计参考根目录**: `k:/Nexara/.stitch/`

---

## 你的任务

完善聊天页面的功能密度，实现会话设置底部弹窗(C3)和完整会话设置页(C2)，新建超级助手设置页(C5)和工作区弹窗(C4)。

**核心原则**:
- 视觉样式**完全以 `.stitch/` 中的 Stitch MD3 设计稿为准**
- 绝不参考原 RN UI 样式

---

## 设计参考

**必须先打开以下 HTML 设计稿**：

| 页面 | Stitch 设计稿 |
|------|-------------|
| C2 会话设置 | `.stitch/screens/d05753ebc7dd44d8b9a52985d9787aae.html` |
| C3 设置弹窗 | `.stitch/screens/b1605ae432d84b00a87bd4ef99210822.html` |
| C4 工作区 | `.stitch/screens/9a048db17bb54996a40c62ec5e74f4f5.html` |
| C5 SPA设置 | `.stitch/screens/2e480fb741c5466081e59c738ee950bf.html` |
| 聊天主会话 | `.stitch/screens/1cf134b1b77548b68f3fc719ea16de1c.html` |
| 内联组件 | `.stitch/screens/9a32a3fb90b446ae9afb8dbd48d4feef.html` |
| 高级组件 | `.stitch/screens/ffa06f4ce51b43079a5623e723eaef04.html` |

**功能参考**: `.stitch/design_system/stitch-ui-functional-reference.md` → Group C

---

## 任务 1: 重写 `ChatScreen.kt` — 聊天页面增强

### 现有文件: `ui/chat/ChatScreen.kt`

### 增强内容

1. **动态标题**: 从 ViewModel 加载会话标题/Agent 名（G1 已传递 sessionId）
2. **流式输出 UI**: 消息逐字显示 + 光标闪烁动画
   ```kotlin
   // ChatBubble 增加生成中态
   if (message.isGenerating) {
       // 显示光标动画 (品牌色闪烁竖线)
   }
   ```
3. **"回到底部" FAB**: 列表不在底部时显示，点击滚动到最新消息
4. **ChatInputTopBar**: 输入栏上方添加模型选择按钮 + 工具开关
5. **生成中**: 屏幕常亮 (`FLAG_KEEP_SCREEN_ON`)
6. **TokenStatsModal 入口**: 右上角菜单中添加

### ViewModel 增强 (`ChatViewModel.kt`)

```kotlin
// 新增
val sessionTitle: StateFlow<String>
val agentName: StateFlow<String>
val isUserScrolledAway: StateFlow<Boolean>

fun loadSession(sessionId: String)
fun regenerateLastMessage()
fun editAndResend(messageId: String, newContent: String)
fun toggleTool(toolName: String, enabled: Boolean)
```

---

## 任务 2: 新建 `SessionSettingsSheet.kt` (C3)

### 创建文件: `ui/chat/SessionSettingsSheet.kt`

**Stitch 参考**: `.stitch/screens/b1605ae432d84b00a87bd4ef99210822.html`

**组件签名**:
```kotlin
@Composable
fun SessionSettingsSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    sessionId: String
)
```

### UI 结构

```
ModalBottomSheet (70% 高度, 毛玻璃背景)
├── Handle + 标题 "会话设置"
├── 【4 Tab 切换栏】(滑动指示器动画)
│   ├── 模型
│   ├── 思考级别
│   ├── 统计
│   └── 工具
│
├── [模型面板] (默认显示)
│   ├── 搜索栏
│   └── 已启用模型列表
│       └── 每项: 模型图标 + 模型名(粗体) + 提供商名 + 能力标签 + 选中 ✓
│       └── 点击 → 关闭弹窗并切换模型
│
├── [思考级别面板]
│   └── 4 卡片 (2x2 网格):
│       ├── Minimal (Zap 图标, 紫色) — "快速响应，最低计算"
│       ├── Low (Brain 图标, 青色) — "基础推理，平衡速度"
│       ├── Medium (Sparkles 图标, 琥珀色) — "深度推理，推荐"
│       └── High (GraduationCap 图标, 翠绿色) — "最强推理，最高质量"
│       └── 选中: 品牌色边框 + 着色背景
│
├── [统计面板]
│   ├── 大号 Token 总数 (环形指示器)
│   ├── 3 MetricRow:
│   │   ├── Prompt (紫色 MessageSquare) + 进度条
│   │   ├── Completion (琥珀色 Zap) + 进度条
│   │   └── RAG System (绿色 Database) + 进度条
│   └── 重置按钮 (红色)
│
└── [工具面板]
    ├── 时间注入 Switch
    ├── Agent 技能开关 (列表)
    ├── 严格模式 Switch
    ├── ExecutionModeSelector (auto/semi/manual)
    ├── MCP 服务器列表 (开关)
    └── 用户技能列表 (开关)
```

### 使用 HorizontalPager + TabRow 实现 Tab 切换

```kotlin
val pagerState = rememberPagerState(pageCount = { 4 })
ScrollableTabRow(
    selectedTabIndex = pagerState.currentPage,
    containerColor = Color.Transparent,
    indicator = { /* 自定义滑动指示器 */ }
) { ... }
HorizontalPager(state = pagerState) { page ->
    when (page) {
        0 -> ModelPanel(...)
        1 -> ThinkingLevelPanel(...)
        2 -> StatsPanel(...)
        3 -> ToolsPanel(...)
    }
}
```

---

## 任务 3: 重写 `SessionSettingsScreen.kt` (C2)

### 现有文件: `ui/chat/SessionSettingsScreen.kt`

**Stitch 参考**: `.stitch/screens/d05753ebc7dd44d8b9a52985d9787aae.html`

### 完整 UI 结构

```
Scaffold
├── TopAppBar (GlassHeader "设置" + 返回按钮 + 会话标题)
└── LazyColumn
    ├── 【Agent 引用卡片】NexaraGlassCard
    │   ├── Agent 图标(48dp 圆形) + Agent 名 + "编辑" 链接
    │   └── 点击 → 跳转 AgentEditScreen
    │
    ├── 【导出按钮】NexaraGlassCard
    │   └── Icon(Download) + "导出会话"
    │
    ├── SettingsSectionHeader "会话信息"
    ├── 【标题编辑】SettingsInput + AI 生成按钮(Sparkles 图标)
    │
    ├── SettingsSectionHeader "推理参数"
    ├── 【InferenceSettings】NexaraGlassCard
    │   ├── 温度 Slider (0.0-2.0, 步长 0.1)
    │   ├── TopP Slider (0.0-1.0, 步长 0.05)
    │   ├── MaxTokens Slider (100-4096, 步长 100)
    │   └── InferencePresets
    │
    ├── SettingsSectionHeader "RAG 设置"
    ├── 【RAG 开关区】
    │   ├── SettingsToggle "长期记忆"
    │   ├── SettingsToggle "知识图谱提取"
    │   ├── SettingsToggle "知识库"
    │   └── 【文档选择器】
    │       ├── 已选文档标签 (FlowRow, 可移除 ×)
    │       └── "+" 添加文档按钮
    │
    ├── SettingsSectionHeader "提示词"
    ├── 【自定义 Prompt】NexaraGlassCard
    │   ├── 预览文本 + 状态徽章 ("已配置"/"未设置")
    │   └── 点击 → FloatingTextEditor
    │
    ├── Spacer (32dp)
    ├── 【危险区】
    │   └── 删除会话按钮 (红色, ConfirmDialog)
    └──
```

---

## 任务 4: 新建 `SpaSettingsScreen.kt` (C5)

### 创建文件: `ui/chat/SpaSettingsScreen.kt`

**Stitch 参考**: `.stitch/screens/2e480fb741c5466081e59c738ee950bf.html`

**组件签名**:
```kotlin
@Composable
fun SpaSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRagConfig: () -> Unit,
    onNavigateToAdvancedRetrieval: () -> Unit
)
```

### UI 结构

```
Scaffold
├── TopAppBar (GlassHeader "超级助手设置")
└── LazyColumn
    ├── 【标题编辑】SettingsInput
    │
    ├── SettingsSectionHeader "FAB 外观"
    ├── CollapsibleSection "外观配置"
    │   ├── 图标样式网格 (10 预设图标, 选中品牌色边框)
    │   ├── ColorPickerPanel (FAB 颜色)
    │   ├── SettingsToggle "旋转动画"
    │   └── SettingsToggle "发光效果"
    │
    ├── SettingsSectionHeader "模型配置"
    ├── 【模型选择器】NexaraGlassCard + ModelPicker
    ├── InferencePresets
    │
    ├── SettingsSectionHeader "知识图谱"
    ├── SettingsToggle "启用知识图谱"
    ├── NexaraGlassCard "查看完整图谱" (链接)
    │
    ├── SettingsSectionHeader "知识管理"
    ├── SettingsItem → RAG 配置入口
    ├── SettingsItem → 高级检索入口
    │
    ├── SettingsSectionHeader "上下文"
    ├── 【上下文管理面板】Slider
    │
    ├── SettingsSectionHeader "全局知识统计"
    ├── Row (3 MetricCard: 文档数/会话数/向量数)
    │
    ├── 【清理按钮】"清理幽灵数据" (琥珀色)
    ├── 【导出历史】
    │
    ├── Spacer (32dp)
    ├── 【危险区】删除按钮
    └──
```

---

## 任务 5: 新建 `WorkspaceSheet.kt` (C4)

### 创建文件: `ui/chat/WorkspaceSheet.kt`

**Stitch 参考**: `.stitch/screens/9a048db17bb54996a40c62ec5e74f4f5.html`

**组件签名**:
```kotlin
@Composable
fun WorkspaceSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    sessionId: String
)
```

### UI 结构

```
ModalBottomSheet (85% 高度)
├── Handle + "工作区" + 路径指示器
├── 【3 Tab】(任务 / 产物 / 文件)
│
├── [任务面板]
│   └── 任务卡片列表: 图标 + 标题 + 状态徽章
│
├── [产物的面板]
│   └── 产物缩略图卡片网格 (2列)
│
└── [文件面板]
    ├── 文件/文件夹树形列表
    └── 文件预览 Modal (文件名 + 路径 + 编辑/保存 + 内容)
```

---

## 导航集成

在 `NavGraph.kt` 中确保以下路由正确连接：

```kotlin
// C2 - 重写后的会话设置
composable(
    route = NavDestinations.SESSION_SETTINGS,
    arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
) { ... }

// C3 - 会话设置弹窗 (从 ChatScreen 内部调用，不需要独立路由)
// SessionSettingsSheet 在 ChatScreen 内部通过 show 状态控制

// C4 - 工作区弹窗 (同上，从 ChatScreen 内部调用)

// C5 - SPA 设置
composable(NavDestinations.SPA_SETTINGS) {
    SpaSettingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToRagConfig = { navController.navigate(NavDestinations.SPA_RAG_CONFIG) },
        onNavigateToAdvancedRetrieval = { navController.navigate(NavDestinations.SPA_ADVANCED_RETRIEVAL) }
    )
}
```

---

## 完成标准

- [ ] ChatScreen 动态标题、流式输出 UI、"回到底部" FAB、ChatInputTopBar
- [ ] SessionSettingsSheet 4 Tab (模型/思考级别/统计/工具) 完整实现
- [ ] SessionSettingsScreen 完整重写: Agent 引用、推理参数、RAG 开关、文档选择、Prompt 编辑、导出/删除
- [ ] SpaSettingsScreen 完整实现: FAB 外观、模型配置、知识图谱、RAG、统计
- [ ] WorkspaceSheet 3 Tab (任务/产物/文件) 基本实现
- [ ] 从聊天页面可打开设置弹窗和会话设置页
- [ ] 所有硬编码文字已替换为动态数据
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
