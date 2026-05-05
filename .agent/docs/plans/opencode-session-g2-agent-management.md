# OpenCode 指令模板 — Session G2: Agent 管理流程 (B1→B2→B3)

> **项目**: Nexara Native UI  
> **工作目录**: `k:/Nexara/native-ui/`  
> **Session**: G2 — Agent 管理完整流程  
> **前置依赖**: G0 (全局组件) + G1 (导航参数修复)  
> **设计参考根目录**: `k:/Nexara/.stitch/`

---

## 你的任务

完整实现 Agent 的创建、编辑、配置用户流程。包括 B1 助手列表增强、B2 会话列表增强、B3 Agent 编辑器（全新）。

**核心原则**:
- 视觉样式**完全以 `.stitch/` 中的 Stitch MD3 设计稿为准**
- 绝不参考原 RN UI 样式
- Stitch HTML 设计稿是唯一视觉权威

---

## 设计参考

**必须先打开以下 HTML 设计稿理解视觉设计**（用浏览器打开）：

| 页面 | Stitch 设计稿 |
|------|-------------|
| B1 助手列表 | `.stitch/screens/51903d366b024784b472f7eca445d22b.html` |
| B2 会话列表 | `.stitch/screens/c5317715dae64d70b44569342ece58cb.html` |
| B3 Agent 编辑器 | `.stitch/screens/8237789a111d41daa4f5957f549d75d6.html` |
| C6/C8 RAG 配置 | `.stitch/screens/fcb498712c44441485cedd879ed11e7e.html` |
| C7/C9 高级检索 | `.stitch/screens/b24c64539c6a4ab8a7680dbcd5386f24.html` |

**功能参考**: `.stitch/design_system/stitch-ui-functional-reference.md` → Group B + C6~C9

**已有代码参考**:
- `ui/hub/AgentHubScreen.kt` — 当前 Agent 列表
- `ui/hub/AgentHubViewModel.kt` — Agent ViewModel
- `ui/hub/AgentSessionsScreen.kt` — 当前会话列表（G1 已修改接收 agentId）
- `ui/common/` — G0 创建的全局组件

---

## 任务 1: 增强 `AgentHubScreen.kt` (B1)

### 对齐 Stitch B1 设计稿

在现有基础上增强：

1. **大标题区**: "对话" (headlineLarge) + 副标题 "你的智能助手团队" (bodyMedium, secondary 色)
2. **搜索栏**: 固定在顶部（不随列表滚动），毛玻璃背景，border-radius 12dp，Search 图标 + placeholder
3. **SwipeableItem** (来自 G0): 左滑置顶 + 右滑删除
4. **右上角 "+" 按钮**: 48x48dp, glass-panel, border-radius 16dp，点击弹出创建 Agent 对话框
5. **SuperAssistantFAB**: 右下角 56x56dp 圆形，品牌色背景 + Sparkles 图标，悬浮阴影
6. **空状态**: 无 Agent 时显示引导文案 + "创建第一个助手" 按钮
7. **排序**: 置顶优先 + 创建时间降序
8. **列表项**: 按 compression 0.97 spring 动画

### ViewModel 增强 (`AgentHubViewModel.kt`)

```kotlin
// 新增方法
fun deleteAgent(agentId: String)
fun togglePin(agentId: String)
```

---

## 任务 2: 增强 `AgentSessionsScreen.kt` (B2)

### 对齐 Stitch B2 设计稿

在 G1 修改基础上进一步增强：

1. **GlassHeader**: 返回按钮 (40dp 圆形 glass 背景) + Agent 名称 (headlineLarge) + 会话数量 (labelMedium secondary)
2. **右上角设置图标**: 点击 `onNavigateToAgentEdit()`
3. **搜索栏**: 毛玻璃背景
4. **会话卡片**: 标题 + 最后消息预览 + 时间 + SwipeableItem(左滑置顶/右滑删除)
5. **FAB**: 右下角 "+" (Agent 品牌色背景)，点击创建新会话并跳转聊天
6. **空状态**: 图标 + "还没有对话，开始第一个吧"

### ViewModel 增强 (`SessionListViewModel.kt`)

```kotlin
fun deleteSession(sessionId: String)
fun togglePinSession(sessionId: String)
fun createAndNavigate(agentId: String): String  // 返回新 sessionId
```

---

## 任务 3: 新建 `AgentEditScreen.kt` (B3) — 核心！

### 创建文件: `ui/hub/AgentEditScreen.kt`

**组件签名**:
```kotlin
@Composable
fun AgentEditScreen(
    agentId: String,
    onNavigateBack: () -> Unit,
    onNavigateToRagConfig: (String) -> Unit,
    onNavigateToAdvancedRetrieval: (String) -> Unit
)
```

### 完整 UI 结构（对照 Stitch B3 设计稿）

```
Scaffold
├── TopAppBar (GlassHeader "编辑助手" + 返回按钮)
└── LazyColumn
    ├── 【基本信息区】
    │   ├── SettingsInput "名称"
    │   └── SettingsInput "描述" (多行)
    │
    ├── SettingsSectionHeader "外观"
    ├── 【外观区】CollapsibleSection
    │   ├── AgentAvatar (80x80 预览)
    │   ├── 预设图标网格 (10个, 3列, 选中品牌色边框)
    │   ├── 自定义图片上传按钮 (虚线边框 + Upload 图标)
    │   └── ColorPickerPanel
    │
    ├── SettingsSectionHeader "性格"
    ├── 【系统提示词区】NexaraGlassCard
    │   ├── 预览文本 (2行截断) + 状态徽章 ("已配置"/"未设置")
    │   └── 点击 → 打开 FloatingTextEditor (来自 G0)
    │
    ├── SettingsSectionHeader "模型配置"
    ├── 【模型选择器】NexaraGlassCard (点击打开 ModelPicker)
    │   └── 当前模型名 + Chevron 右箭头
    ├── 【推理预设】InferencePresets (来自 G0)
    │
    ├── SettingsSectionHeader "知识"
    ├── 【RAG 配置入口】SettingsItem → 点击 onNavigateToRagConfig(agentId)
    ├── 【高级检索入口】SettingsItem → 点击 onNavigateToAdvancedRetrieval(agentId)
    │
    ├── Spacer (32dp)
    ├── 【危险区】
    │   └── 删除助手按钮 (红色文字, 确认弹窗 ConfirmDialog)
    └──
```

### 新建 ViewModel: `ui/hub/AgentEditViewModel.kt`

```kotlin
class AgentEditViewModel(application: Application) : AndroidViewModel(application) {
    val agent: StateFlow<Agent?>
    val name: MutableStateFlow<String>
    val description: MutableStateFlow<String>
    val systemPrompt: MutableStateFlow<String>
    val selectedModel: MutableStateFlow<String>
    val selectedColor: MutableStateFlow<String>
    val selectedIcon: MutableStateFlow<String>
    val temperature: MutableStateFlow<Float>
    val topP: MutableStateFlow<Float>
    val hasChanges: StateFlow<Boolean>
    
    fun loadAgent(agentId: String)
    fun saveAgent()
    fun deleteAgent()
    
    companion object {
        fun factory(application: Application): ViewModelProvider.Factory
    }
}
```

- 自动保存: debounce 1s (使用 `snapshotFlow` + `delay(1000)`)
- 变更检测: 比较当前值与初始值

---

## 任务 4: 新建 `AgentRagConfigScreen.kt` (C6/C8)

### 创建文件: `ui/hub/AgentRagConfigScreen.kt`

**Stitch 参考**: `.stitch/screens/fcb498712c44441485cedd879ed11e7e.html`

**组件签名**:
```kotlin
@Composable
fun AgentRagConfigScreen(
    agentId: String,
    scopeLabel: String,  // "Agent" 或 "Super Assistant"
    onNavigateBack: () -> Unit
)
```

**UI 结构**:
- GlassHeader + scopeLabel 名称
- 配置状态卡片: "继承全局" / "自定义" 标签 + 重置按钮(ConfirmDialog)
- RAG 参数面板 (复用 E10 的结构):
  - 文档分块大小 Slider (200-2000, 步长 100)
  - 分块重叠 Slider (0-500, 步长 50)
  - 记忆分块大小 Slider (500-2000, 步长 100)
  - 活跃上下文窗口 Slider (10-50, 步长 5)
  - 摘要触发阈值 Slider (5-30, 步长 5)
  - 摘要模板预览 (FloatingTextEditor)

---

## 任务 5: 新建 `AgentAdvancedRetrievalScreen.kt` (C7/C9)

### 创建文件: `ui/hub/AgentAdvancedRetrievalScreen.kt`

**Stitch 参考**: `.stitch/screens/b24c64539c6a4ab8a7680dbcd5386f24.html`

**组件签名**:
```kotlin
@Composable
fun AgentAdvancedRetrievalScreen(
    agentId: String,
    scopeLabel: String,
    onNavigateBack: () -> Unit
)
```

**UI 结构** (同 E11 高级检索):
- 配置状态卡片 (继承全局/自定义)
- 记忆检索区: 数量限制 Slider + 相似度阈值 Slider
- 文档检索区: 数量限制 Slider + 相似度阈值 Slider
- 重排序区: 启用开关 + 召回数量 + 最终结果数量
- 查询改写区: 启用开关 + 策略选择器(hyde/multi-query/expansion) + 变体数量
- 混合搜索区: 启用开关 + 向量权重 + BM25 增益

---

## 导航集成

确保 `NavGraph.kt` 中以下路由的 composable 块正确指向新建的 Screen:

```kotlin
composable(
    route = NavDestinations.AGENT_EDIT,
    arguments = listOf(navArgument("agentId") { type = NavType.StringType })
) { backStackEntry ->
    val agentId = backStackEntry.arguments?.getString("agentId") ?: ""
    AgentEditScreen(
        agentId = agentId,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToRagConfig = { navController.navigate(NavDestinations.agentRagConfig(agentId)) },
        onNavigateToAdvancedRetrieval = { navController.navigate(NavDestinations.agentAdvancedRetrieval(agentId)) }
    )
}
```

---

## 完成标准

- [ ] B1 AgentHubScreen 有滑动操作、FAB、空状态、排序
- [ ] B2 AgentSessionsScreen 动态标题、FAB创建会话、设置入口
- [ ] B3 AgentEditScreen 完整编辑器: 名称/描述/外观/提示词/模型/预设/RAG/检索/删除
- [ ] C6/C8 AgentRagConfigScreen 带参数的 RAG 配置
- [ ] C7/C9 AgentAdvancedRetrievalScreen 带参数的高级检索
- [ ] AgentEditViewModel 实现 debounce 自动保存
- [ ] 从 AgentHub → SessionList → AgentEdit 完整流程通畅
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
