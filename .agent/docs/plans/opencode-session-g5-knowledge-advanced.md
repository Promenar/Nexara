# OpenCode 指令模板 — Session G5: 知识库 & 高级功能

> **项目**: Nexara Native UI  
> **工作目录**: `k:/Nexara/native-ui/`  
> **Session**: G5 — 知识库 & 高级功能  
> **前置依赖**: G0 (全局组件) + G1 (导航参数)  
> **设计参考根目录**: `k:/Nexara/.stitch/`

---

## 你的任务

补齐知识库模块缺失页面（文档编辑器、知识图谱、RAG 高级设置、RAG 调试面板），增强现有知识库首页和技能设置页面，增强全局 RAG 配置和高级检索配置。

**核心原则**:
- 视觉样式**完全以 `.stitch/` 中的 Stitch MD3 设计稿为准**
- 绝不参考原 RN UI 样式

---

## 设计参考

**必须先打开以下 HTML 设计稿**：

| 页面 | Stitch 设计稿 |
|------|-------------|
| D1 知识库首页 | `.stitch/screens/8a862a89c580418f9229e886a84b951e.html` |
| D2 文件夹详情 | `.stitch/screens/3b7b162796a24e178f04e3306f7fe13e.html` |
| D3 文档编辑器 | `.stitch/screens/a2f86f720c2c418f91494a522b92f84b.html` |
| D4 知识图谱 | `.stitch/screens/55da3fa3f6ef4d33b59e80b4c5eba448.html` |
| E8 技能设置 | `.stitch/screens/1c3473ffc56346d4bd36c490a6a64aa8.html` |
| E10 全局 RAG | `.stitch/screens/2d213175f11948ed9366df51baf9d4d8.html` |
| E12 RAG 高级 | `.stitch/screens/97b9dbefd2b942cc99ae08d1e2eb9530.html` |
| E13 RAG 调试 | `.stitch/screens/9c902d909f90416b8febc13b4fcc8dc5.html` |
| E11 高级检索 | `.stitch/screens/b24c64539c6a4ab8a7680dbcd5386f24.html` |

**功能参考**: `.stitch/design_system/stitch-ui-functional-reference.md` → Group D + E10~E13

---

## 任务 1: 增强 `RagHomeScreen.kt` (D1)

### 现有文件: `ui/rag/RagHomeScreen.kt` (245行)

### 增强内容

1. **Portal 视图切换**: 点击 3 个 Portal 卡片 → 切换显示内容
   - **文档视图** (默认): 面包屑导航 + 控制栏 + 文件夹列表 + 文档列表
   - **记忆视图**: 记忆卡片列表 (内容 + 时间)
   - **图谱视图**: 跳转 KnowledgeGraphScreen

2. **搜索栏**: 固定在顶部，毛玻璃背景

3. **拖放上传区**: 虚线边框区域 + "拖放文件到此处上传" (移动端为文件选择器按钮)

4. **向量化状态指示器**: 浮动进度条 + 旋转图标 + "正在向量化..."

5. **多选模式**: 长按文档 → 进入多选模式
   - 底部工具栏: 重新向量化 + 删除
   - 标题显示 "已选择 N 项"

6. **移动弹窗**: ModalBottomSheet 文件夹选择器 (选择目标文件夹 → 移动)

### Stitch 参考

`.stitch/screens/8a862a89c580418f9229e886a84b951e.html`

---

## 任务 2: 新建 `DocEditorScreen.kt` (D3)

### 创建文件: `ui/rag/DocEditorScreen.kt`

**Stitch 参考**: `.stitch/screens/a2f86f720c2c418f91494a522b92f84b.html`

**组件签名**:
```kotlin
@Composable
fun DocEditorScreen(
    docId: String,
    onNavigateBack: () -> Unit
)
```

### UI 结构

```
Scaffold
├── TopAppBar (GlassHeader: 文档标题 + 文件大小 + 保存按钮 ✓ + 预览/编辑切换 👁/✏️)
└── Box
    ├── 【大文件警告横幅】(可关闭) "文件较大，编辑可能影响性能"
    ├── [编辑模式] BasicTextField (全屏, 等宽字体)
    └── [预览模式] 文本展示 (基础语法高亮)
```

### ViewModel: `DocEditorViewModel.kt`

```kotlin
class DocEditorViewModel(application: Application) : AndroidViewModel(application) {
    val document: StateFlow<Document?>
    val content: MutableStateFlow<String>
    val isLargeFile: StateFlow<Boolean>
    val isEditing: MutableStateFlow<Boolean>
    val isDirty: StateFlow<Boolean>
    
    fun loadDocument(docId: String)
    fun saveDocument()
    fun toggleEditMode()
}
```

---

## 任务 3: 新建 `KnowledgeGraphScreen.kt` (D4)

### 创建文件: `ui/rag/KnowledgeGraphScreen.kt`

**Stitch 参考**: `.stitch/screens/55da3fa3f6ef4d33b59e80b4c5eba448.html`

**组件签名**:
```kotlin
@Composable
fun KnowledgeGraphScreen(
    onNavigateBack: () -> Unit
)
```

### UI 结构

```
Scaffold
├── TopAppBar (GlassHeader + 动态标题)
└── Box (全屏 Canvas)
    ├── 【图谱区域】
    │   ├── 节点: 圆形 + 品牌色边框 + 标签
    │   └── 边: 连线 (灰色)
    │
    ├── 手势: 拖拽平移 + 双指缩放
    │
    └── 节点详情弹窗 (GlassBottomSheet)
        ├── 节点名称 + 类型
        ├── 属性列表
        └── 编辑/删除按钮
```

### 实现方式

使用 Jetpack Compose `Canvas` + `transformable` + `draggable` 手势:
- `rememberTransformableState` 管理缩放/平移
- 节点用 `drawCircle` + `drawText`
- 边用 `drawLine`
- 点击检测用 `hitTest` 距离判断

**注意**: 实际图谱数据可暂用模拟数据（50个节点 + 80条边），后续接入 VectorStore 的实际实体数据。

---

## 任务 4: 新建 `RagAdvancedScreen.kt` (E12)

### 创建文件: `ui/rag/RagAdvancedScreen.kt`

**Stitch 参考**: `.stitch/screens/97b9dbefd2b942cc99ae08d1e2eb9530.html`

**组件签名**:
```kotlin
@Composable
fun RagAdvancedScreen(
    onNavigateBack: () -> Unit
)
```

### UI 结构

```
Scaffold
├── TopAppBar (GlassHeader "知识图谱")
└── LazyColumn
    ├── SettingsToggle "启用知识图谱 (KG)"
    │
    ├── SettingsSectionHeader "提取配置"
    ├── SettingsItem "提取模型" → ModelPicker
    │
    ├── SettingsSectionHeader "JIT 微图"
    ├── SettingsToggle "启用 JIT"
    ├── SettingsInput "最大块数" (数字输入)
    ├── SettingsToggle "免费模式"
    ├── SettingsToggle "域名自动检测"
    │
    ├── SettingsSectionHeader "成本策略"
    ├── 【单选按钮组】
    │   ├── ○ 摘要优先 (推荐)
    │   ├── ○ 按需
    │   └── ○ 全扫描
    │
    ├── SettingsSectionHeader "本地优化"
    ├── SettingsToggle "增量哈希"
    ├── SettingsToggle "规则预过滤"
    │
    ├── SettingsSectionHeader "提取提示词"
    ├── 【提示词预览卡片】
    │   ├── 预览文本 + 状态徽章
    │   └── 点击 → FloatingTextEditor
    │   └── "重置默认" 按钮 + 警告横幅
    │
    ├── SettingsItem "查看完整图谱" → knowledge_graph
    └──
```

---

## 任务 5: 新建 `RagDebugScreen.kt` (E13)

### 创建文件: `ui/rag/RagDebugScreen.kt`

**Stitch 参考**: `.stitch/screens/9c902d909f90416b8febc13b4fcc8dc5.html`

**组件签名**:
```kotlin
@Composable
fun RagDebugScreen(
    onNavigateBack: () -> Unit
)
```

### UI 结构

```
Scaffold
├── TopAppBar (GlassHeader "向量统计")
└── LazyColumn
    ├── 【标题行】Database 图标 + "Vector Stats" + 刷新按钮
    │
    ├── 【概览卡片】NexaraGlassCard
    │   ├── 总向量数 (大号粗体)
    │   └── 存储大小 (MB)
    │
    ├── SettingsSectionHeader "类型分布"
    ├── 【分布卡片】
    │   ├── 文档向量数 + 占比条
    │   └── 记忆/摘要向量数 + 占比条
    │
    ├── SettingsSectionHeader "存储健康"
    ├── 【健康卡片】
    │   ├── 冗余率 (>20% 红色, ≤20% 绿色)
    │   └── 清理按钮 (仅冗余>1%时显示)
    │
    ├── SettingsSectionHeader "Top 会话"
    └── 【会话列表】
        └── 每行: 会话 ID + 向量数药丸徽章
```

---

## 任务 6: 增强 `RagFolderScreen.kt` (D2)

### 现有文件: `ui/rag/RagFolderScreen.kt` (9.5KB)

### 增强内容

1. **多选模式**: 长按文档 → 进入多选
2. **批量操作**: 底部工具栏 (移动到文件夹 + 重新向量化 + 删除)
3. **文档项完善**: 图标 + 标题 + 向量化状态标签 + 标签
4. **移动弹窗**: ModalBottomSheet 文件夹选择器

### Stitch 参考

`.stitch/screens/3b7b162796a24e178f04e3306f7fe13e.html`

---

## 任务 7: 增强 `SkillsScreen.kt` (E8)

### 现有文件: `ui/settings/SkillsScreen.kt` (6.5KB)

### 增强内容

1. **循环限制区**: +/- 步进器 (1-100, 100 显示为 "无限") + 无限模式警告横幅
2. **3 Tab 动画切换**: 预设技能 / 用户技能 / MCP 服务器
3. **预设技能 Tab**: 开关列表 + 技能 ID 徽章 (等宽字体) + 描述
4. **用户技能 Tab**: 开关 + 编辑/配置/删除操作 + FloatingCodeEditor
5. **MCP 服务器 Tab**:
   - 添加表单: 名称 + URL + 类型选择
   - 服务器列表: 状态图标(绿=连接/红=断开) + 名称 + URL + 同步/删除
   - 工具列表 (展开)
   - 调用间隔步进器 + 启用/默认开关

### Stitch 参考

`.stitch/screens/1c3473ffc56346d4bd36c490a6a64aa8.html`

---

## 任务 8: 增强 `GlobalRagConfigScreen.kt` (E10)

### 现有文件: `ui/rag/GlobalRagConfigScreen.kt` (12KB)

### 增强内容

1. **3 预设卡片**: 均衡(Zap图标) / 写作(Edit图标) / 编程(Code图标)，选中品牌色边框+着色背景
2. **向量统计仪表盘**: 3 列 MetricCard (文档数/向量数/存储 MB)
3. **摘要模板编辑器**: 预览卡片 + FloatingTextEditor
4. **"高级" 链接**: → rag_advanced_kg (E12)
5. **"更多详情" 链接**: → rag_debug (E13)
6. **"清除向量数据" 按钮**: 红色危险按钮 + ConfirmDialog (含"同时清除关联图谱"/"仅清除向量"选项)
7. **"清理孤立数据" 按钮**: 琥珀色按钮

### Stitch 参考

`.stitch/screens/2d213175f11948ed9366df51baf9d4d8.html`

---

## 任务 9: 增强 `AdvancedRetrievalScreen.kt` (E11)

### 现有文件: `ui/rag/AdvancedRetrievalScreen.kt` (13.2KB)

### 增强内容

1. **混合搜索区**: 启用开关 + 向量权重 Slider (0-100%) + BM25 增益 Slider (0.5x-2.0x)
2. **可观测性区**: 显示检索进度 Switch + 显示检索详情 Switch + 跟踪检索指标 Switch
3. **重排序联动**: 启用重排序后，记忆/文档限制 Slider 置灰并显示 "Rerank" 徽章
4. **查询改写策略选择器**: 3 个水平按钮 (hyde/multi-query/expansion) + 选中态动画

### Stitch 参考

`.stitch/screens/b24c64539c6a4ab8a7680dbcd5386f24.html`

---

## 导航集成

确保 `NavGraph.kt` 中所有新路由正确连接：

```kotlin
composable(
    route = NavDestinations.DOC_EDITOR,
    arguments = listOf(navArgument("docId") { type = NavType.StringType })
) { ... }

composable(NavDestinations.KNOWLEDGE_GRAPH) {
    KnowledgeGraphScreen(onNavigateBack = { navController.popBackStack() })
}

composable(NavDestinations.RAG_ADVANCED_KG) {
    RagAdvancedScreen(onNavigateBack = { navController.popBackStack() })
}

composable(NavDestinations.RAG_DEBUG) {
    RagDebugScreen(onNavigateBack = { navController.popBackStack() })
}
```

---

## 完成标准

- [ ] RagHomeScreen Portal 视图切换、搜索、拖放上传、多选模式
- [ ] DocEditorScreen 编辑/预览模式、大文件警告、保存
- [ ] KnowledgeGraphScreen Canvas 图谱 + 缩放平移 + 节点详情弹窗
- [ ] RagAdvancedScreen 知识图谱配置 + JIT + 成本策略 + 提示词编辑
- [ ] RagDebugScreen 向量统计 + 冗余率 + Top 会话
- [ ] RagFolderScreen 多选 + 批量操作 + 移动弹窗
- [ ] SkillsScreen 循环限制 + 3 Tab + MCP 服务器管理
- [ ] GlobalRagConfigScreen 3 预设 + 统计仪表盘 + 清除/清理
- [ ] AdvancedRetrievalScreen 混合搜索 + 可观测性 + 联动逻辑
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
