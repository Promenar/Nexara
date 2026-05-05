# Nexara 功能迁移修复方案 & OpenCode 指令模板

> **日期**: 2026-05-04
> **基准**: `.agent/docs/audits/functional-migration-alignment-audit-2026-05-04.md`
> **设计参考**: `.stitch/` 目录（49 个 HTML 设计稿 + 设计系统 Token + 功能参考）
> **修复策略**: 6 个 Session，按依赖关系排序，P0 → P1 → P2 渐进修复
> **核心原则**: 视觉样式**完全以 Stitch MD3 设计稿为准**，绝不参考原 RN UI 样式

---

## 依赖关系与执行顺序

```
Session G0 (全局组件基座)
    │
    ├── Session G1 (导航参数修复 + Tab结构)
    │       │
    │       ├── Session G2 (Agent 管理流程: B1→B2→B3)
    │       │       │
    │       │       └── Session G3 (会话设置 & 聊天增强)
    │       │
    │       └── Session G4 (设置 Tab 全面补齐)
    │
    └── Session G5 (知识库 & 高级功能)
```

---

## Session G0: 全局组件基座

### 目标
构建所有页面共用的全局可复用组件。后续所有 Session 依赖这些组件。

### 需创建的文件

| # | 文件 | 对应 Stitch 设计 | 功能 |
|---|------|-----------------|------|
| 1 | `ui/common/ModelPicker.kt` | `.stitch/screens/f7e063d9f3714701adbaa3b3cad56538.html` + `stitch-ui-functional-reference.md` H3 | GlassBottomSheet + 搜索 + 模型列表 + 能力标签(Reasoning/Vision/Web/Rerank/Embedding/Chat 彩色胶囊) + 选中态品牌色勾号 + 空状态 |
| 2 | `ui/common/FloatingTextEditor.kt` | `.stitch/screens/ffa06f4ce51b43079a5623e723eaef04.html` | 全屏 Modal + 顶部栏(标题+保存+关闭) + 多行文本编辑区 |
| 3 | `ui/common/FloatingCodeEditor.kt` | 同上 | 全屏 Modal + 等宽字体 + 行号 + 语法高亮(基础) + 保存 |
| 4 | `ui/common/ColorPickerPanel.kt` | `stitch-ui-functional-reference.md` E9 / B3 | 一行预设色圆点(8-10个) + RainbowSlider 自定义色选择 |
| 5 | `ui/common/InferencePresets.kt` | `stitch-ui-functional-reference.md` B3 | 3 卡片: 精确(Purple Code图标) / 均衡(Cyan Zap图标) / 创意(Amber BookOpen图标)，选中品牌色边框+着色背景 |
| 6 | `ui/common/ExecutionModeSelector.kt` | `stitch-ui-functional-reference.md` C3 | auto/semi/manual 三段分段控制 |
| 7 | `ui/common/SwipeableItem.kt` | `stitch-ui-functional-reference.md` B1/B2 | 左滑置顶 + 右滑删除 + 弹性回弹动画 |
| 8 | `ui/common/ConfirmDialog.kt` | 通用 | 品牌风格确认对话框(标题+描述+确认/取消) |
| 9 | `ui/common/SettingsSectionHeader.kt` | `stitch-ui-functional-reference.md` E1 | Manrope 10sp 大写 tertiary 色 + 可选右侧行动链接 |
| 10 | `ui/common/SettingsInput.kt` | E1/B3 | Glass-panel 背景 + border-radius 12dp + 焦点边框色过渡 |
| 11 | `ui/common/SettingsToggle.kt` | 通用 | 带标签和描述的 MD3 Switch 组件 |
| 12 | `ui/common/CollapsibleSection.kt` | B3 | 可折叠区域 + Chevron 旋转动画 |
| 13 | `ui/common/AgentAvatar.kt` | B3 | 80dp 头像预览 + 品牌色背景 + 图标/自定义图片 |

### 设计 Token 参考
- 所有组件必须使用 `NexaraColors`, `NexaraTypography`, `NexaraShapes`, `NexaraCustomShapes`, `NexaraGlassCard`
- 毛玻璃: `NexaraGlassCard` + `border: 0.5dp GlassBorder`
- 底部弹窗: 使用 `ModalBottomSheet` (Material3) + 毛玻璃 containerColor

---

## Session G1: 导航参数修复 + Tab 结构对齐

### 目标
修复所有路由的参数传递链路，修正 Tab 结构对齐 Stitch A2 规范。

### 需修改的文件

| # | 文件 | 修改内容 |
|---|------|---------|
| 1 | `navigation/NavGraph.kt` | 全面重构路由: (1) 所有路由添加路径参数 (`{agentId}`, `{sessionId}`, `{providerId}` 等) (2) 新增缺失路由常量和 composable 块 (3) 传递 `backStackEntry` 参数到各 Screen |
| 2 | `ui/MainTabScaffold.kt` | Tab 结构改为 3 Tab: Chat / Library / Settings (对齐 Stitch A2)，移除 Insights 占位 Tab，Artifacts 内容合并到 Library Tab |
| 3 | `ui/hub/AgentHubScreen.kt` | 修改 `onNavigateToChat` 为 `onNavigateToSessionList: (String) -> Unit`，点击 Agent 传递 `agentId` |
| 4 | `ui/hub/AgentSessionsScreen.kt` | 接收 `agentId` 参数，加载指定 Agent 的会话；标题动态显示 Agent 名称；添加 FAB 创建新会话按钮；添加右上角设置图标跳转 Agent 编辑器 |
| 5 | `ui/chat/ChatScreen.kt` | 接收 `sessionId` 参数，加载指定会话；标题动态显示会话标题；输入栏 placeholder 动态化 |
| 6 | `ui/chat/SessionSettingsScreen.kt` | 接收 `sessionId` 参数，加载关联 Agent 信息 |

### 新增路由定义

```kotlin
// NavDestinations 新增
const val AGENT_EDIT = "agent_edit/{agentId}"
const val AGENT_RAG_CONFIG = "agent_rag_config/{agentId}"
const val AGENT_ADVANCED_RETRIEVAL = "agent_advanced_retrieval/{agentId}"
const val SPA_SETTINGS = "spa_settings"
const val SPA_RAG_CONFIG = "spa_rag_config"
const val SPA_ADVANCED_RETRIEVAL = "spa_advanced_retrieval"
const val SESSION_SETTINGS_SHEET = "session_settings_sheet/{sessionId}"
const val WORKSPACE_SHEET = "workspace_sheet/{sessionId}"
const val DOC_EDITOR = "doc_editor/{docId}"
const val KNOWLEDGE_GRAPH = "knowledge_graph"
const val RAG_ADVANCED_KG = "rag_advanced_kg"
const val RAG_DEBUG = "rag_debug"
const val BACKUP_SETTINGS = "backup_settings"
const val WORKBENCH = "workbench"
const val LOCAL_MODELS = "local_models"

// 修改为带参数
const val SESSION_LIST = "session_list/{agentId}"
const val CHAT_HERO = "chat_hero/{sessionId}"
const val SESSION_SETTINGS = "session_settings/{sessionId}"
const val PROVIDER_FORM = "provider_form/{providerId?}"
const val PROVIDER_MODELS = "provider_models/{providerId}"
```

### Stitch 参考设计
- A2 Tab 导航: `.stitch/screens/4614c183122b46ecad69d60d4a61cb96.html`
- B2 会话列表: `.stitch/screens/c5317715dae64d70b44569342ece58cb.html`

---

## Session G2: Agent 管理流程 (B1→B2→B3)

### 目标
完整实现 Agent 创建、编辑、配置的完整用户流程。

### 需创建/修改的文件

| # | 文件 | 对应 Stitch 设计 | 功能 |
|---|------|-----------------|------|
| 1 | `ui/hub/AgentEditScreen.kt` (新建) | `.stitch/screens/8237789a111d41daa4f5957f549d75d6.html` | **Agent 编辑器核心页面**: GlassHeader "编辑助手" + 基本信息(名称+描述) + 外观区(CollapsibleSection: AgentAvatar + 10预设图标网格 + 自定义上传 + ColorPickerPanel) + 性格(SystemPrompt预览卡片+状态徽章+点击打开FloatingTextEditor) + 模型配置(ModelPicker + InferencePresets) + RAG配置入口链接 + 高级检索入口链接 + 危险区删除按钮(ConfirmDialog) |
| 2 | `ui/hub/AgentRagConfigScreen.kt` (新建) | `.stitch/screens/fcb498712c44441485cedd879ed11e7e.html` | Agent 级 RAG 配置: GlassHeader + 配置状态卡片("继承全局"/"自定义"+重置) + RAG参数面板(同 E10 结构) |
| 3 | `ui/hub/AgentAdvancedRetrievalScreen.kt` (新建) | `.stitch/screens/b24c64539c6a4ab8a7680dbcd5386f24.html` | Agent 级高级检索: 同 E11 结构 |
| 4 | `ui/hub/AgentHubScreen.kt` (修改) | `.stitch/screens/51903d366b024784b472f7eca445d22b.html` | B1 增强: 大标题"对话"+副标题 + 搜索栏(固定不随列表滚动) + SwipeableItem(左滑置顶/右滑删除) + "+"创建按钮 + SuperAssistantFAB(右下角圆形品牌色+Sparkles图标) + 空状态引导 + 置顶优先排序 |
| 5 | `ui/hub/AgentSessionsScreen.kt` (修改) | `.stitch/screens/c5317715dae64d70b44569342ece58cb.html` | B2 增强: 动态Agent名+会话数标题 + 搜索栏 + SwipeableItem(置顶/删除) + FAB(品牌色+图标)创建新会话 + 右上角Settings图标跳转B3 + 空状态 |
| 6 | `ui/hub/AgentHubViewModel.kt` (修改) | — | 添加 `deleteAgent()`, `togglePin()`, 排序逻辑(置顶优先) |
| 7 | `ui/hub/AgentEditViewModel.kt` (新建) | — | Agent 加载/保存/删除，debounce 自动保存(1s)，变更检测 |

### Stitch 参考设计
- B1 助手列表: `.stitch/screens/51903d366b024784b472f7eca445d22b.html`
- B2 会话列表: `.stitch/screens/c5317715dae64d70b44569342ece58cb.html`
- B3 Agent编辑器: `.stitch/screens/8237789a111d41daa4f5957f549d75d6.html`
- C6/C8 RAG配置: `.stitch/screens/fcb498712c44441485cedd879ed11e7e.html`
- C7/C9 高级检索: `.stitch/screens/b24c64539c6a4ab8a7680dbcd5386f24.html`

---

## Session G3: 会话设置 & 聊天增强

### 目标
完善聊天页面的功能密度，实现会话设置底部弹窗和完整会话设置页。

### 需创建/修改的文件

| # | 文件 | 对应 Stitch 设计 | 功能 |
|---|------|-----------------|------|
| 1 | `ui/chat/SessionSettingsSheet.kt` (新建) | `.stitch/screens/b1605ae432d84b00a87bd4ef99210822.html` | **会话设置底部弹窗**: ModalBottomSheet(70%高度) + 4 Tab(模型/思考级别/统计/工具) + Tab切换动画滑动指示器 + 模型面板(ModelPicker嵌入) + 思考级别面板(4卡片: Minimal/Low/Medium/High) + 统计面板(Token环形图+分类条) + 工具面板(时间注入开关+Agent技能开关+严格模式+ExecutionModeSelector+MCP列表+用户技能列表) |
| 2 | `ui/chat/SessionSettingsScreen.kt` (重写) | `.stitch/screens/d05753ebc7dd44d8b9a52985d9787aae.html` | **会话设置完整页**: GlassHeader + 会话标题 + Agent引用卡片(名+图标+编辑链接) + 导出按钮 + 标题编辑(AI生成按钮) + InferenceSettings(温度/TopP/MaxTokens滑块+InferencePresets) + RAG设置区(长期记忆开关+知识图谱开关+知识库开关+文档选择器) + 已选文档标签(可移除) + 上下文管理 + 自定义Prompt(FloatingTextEditor) + 危险区删除 |
| 3 | `ui/chat/ChatScreen.kt` (修改) | `.stitch/screens/1cf134b1b77548b68f3fc719ea16de1c.html` + `328d89dc846346c0b05ff979c2bc87ab.html` | 聊天增强: (1) 动态标题 (2) 流式输出UI(逐字显示+光标动画) (3) "回到底部"FAB (4) 生成中屏幕常亮 (5) ChatInputTopBar(模型选择+工具开关) (6) TokenStatsModal入口 (7) KGExtractionIndicator |
| 4 | `ui/chat/SpaSettingsScreen.kt` (新建) | `.stitch/screens/2e480fb741c5466081e59c738ee950bf.html` | 超级助手设置: GlassHeader + 标题编辑 + FAB外观区(CollapsibleSection: 图标网格+ColorPicker+旋转动画开关+发光开关) + 模型配置(InferenceSettings) + 知识图谱区(KG开关+查看链接) + RAG配置入口 + 高级检索入口 + 上下文管理 + 全局知识统计(3 MetricCard) + 清理幽灵数据 + 导出历史 + 危险区删除 |
| 5 | `ui/chat/WorkspaceSheet.kt` (新建) | `.stitch/screens/9a048db17bb54996a40c62ec5e74f4f5.html` | 工作区底部弹窗: ModalBottomSheet(85%高度) + 3 Tab(任务/产物/文件) + 任务列表 + 产物缩略图 + 文件浏览器 + 文件预览Modal |
| 6 | `ui/chat/ChatViewModel.kt` (修改) | — | 添加流式输出支持、模型切换、标题编辑等 |

### Stitch 参考设计
- C2 会话设置: `.stitch/screens/d05753ebc7dd44d8b9a52985d9787aae.html`
- C3 设置弹窗: `.stitch/screens/b1605ae432d84b00a87bd4ef99210822.html`
- C4 工作区: `.stitch/screens/9a048db17bb54996a40c62ec5e74f4f5.html`
- C5 SPA设置: `.stitch/screens/2e480fb741c5466081e59c738ee950bf.html`
- 聊天主会话: `.stitch/screens/1cf134b1b77548b68f3fc719ea16de1c.html`
- 内联组件: `.stitch/screens/9a32a3fb90b446ae9afb8dbd48d4feef.html`
- 高级组件: `.stitch/screens/ffa06f4ce51b43079a5623e723eaef04.html`

---

## Session G4: 设置 Tab 全面补齐

### 目标
完善设置首页的动态数据绑定，实现提供商管理，补齐缺失的设置子页面。

### 需修改/创建的文件

| # | 文件 | 对应 Stitch 设计 | 功能 |
|---|------|-----------------|------|
| 1 | `ui/hub/UserSettingsHomeScreen.kt` (重写) | `.stitch/screens/e29e5edff1034b3e8e1099b572466ec8.html` | **设置首页完全重写**: 大标题"设置" + 动画分段Tab(应用/提供商) + 应用Tab: 用户头像(动态) + 名称(可编辑) + 语言选择器 + 外观切换 + 主题色链接 + 触觉反馈开关 + Web搜索链接 + 模型预设区(摘要/图像/嵌入/重排各带chevron到ModelPicker) + RAG配置链接 + 高级检索链接 + Token用量链接 + 工作台链接(实验性) + 技能链接 + 本地模型链接(实验性) + 备份设置组件 + 日志开关 + 导出日志 + 关于(含彩蛋) + 提供商Tab: 添加提供商按钮 + ProviderList(卡片:图标+名称+URL+编辑/删除/管理模型按钮) + 底部品牌信息 |
| 2 | `ui/settings/ProviderFormScreen.kt` (修改) | `.stitch/screens/eb34c022baa045dbafab586d774e766e.html` | 增强: 18+预设提供商网格(2列) + 选中品牌色边框 + 自动填充 + Vertex AI JSON解析 + 焦点边框色过渡 |
| 3 | `ui/settings/ProviderModelsScreen.kt` (修改) | `.stitch/screens/87c9584895e14a59a492bad0ff398f04.html` | 增强: 搜索 + 自动获取 + 手动添加 + 批量禁用/删除 + 测试连接(延迟/错误) + 类型选择器(chat/reasoning/image/embedding/rerank胶囊) + 能力标签(Vision/Internet/Reasoning) + 上下文长度输入 |
| 4 | `ui/settings/SettingsViewModel.kt` (修改) | — | 增强: 动态统计数据、提供商列表管理、用户配置读写 |
| 5 | `ui/settings/BackupSettingsScreen.kt` (新建) | `.stitch/screens/acadb66f584643f69abc45d7b469e0a0.html` | 备份设置: 内容选择区(可折叠: 会话/知识库/文件/设置/密钥开关) + 本地存储(导出/导入) + WebDAV云端(启用开关+自动备份+上传/下载+配置服务器Modal) |
| 6 | `ui/settings/WorkbenchScreen.kt` (新建) | `.stitch/screens/9a90f1c02c3f4eb4b012438eb8059140.html` | 便携工作台: 状态卡片(监视器图标+启停开关) + 稳定性引导(通知权限/电池优化) + 连接详情(URL+访问码+客户端数) |
| 7 | `ui/settings/LocalModelsScreen.kt` (新建) | `.stitch/screens/5127ef1d652e4f0398ccd345758514c5.html` | 本地模型管理: 启用开关 + 导入按钮 + 模型列表(加载/卸载/删除) + 插槽状态(Main/Emb/Rerank) + HardwareBadge |

### Stitch 参考设计
- E1 设置首页: `.stitch/screens/e29e5edff1034b3e8e1099b572466ec8.html`
- E2 提供商表单: `.stitch/screens/eb34c022baa045dbafab586d774e766e.html`
- E3 提供商模型: `.stitch/screens/87c9584895e14a59a492bad0ff398f04.html`
- E14 备份设置: `.stitch/screens/acadb66f584643f69abc45d7b469e0a0.html`
- E5 工作台: `.stitch/screens/9a90f1c02c3f4eb4b012438eb8059140.html`
- E7 本地模型: `.stitch/screens/5127ef1d652e4f0398ccd345758514c5.html`

---

## Session G5: 知识库 & 高级功能

### 目标
补齐知识库缺失页面，实现知识图谱、RAG 高级设置、调试面板和技能系统。

### 需创建/修改的文件

| # | 文件 | 对应 Stitch 设计 | 功能 |
|---|------|-----------------|------|
| 1 | `ui/rag/RagHomeScreen.kt` (修改) | `.stitch/screens/8a862a89c580418f9229e886a84b951e.html` | D1 增强: Portal视图切换(文档/记忆) + 记忆视图(卡片列表) + 搜索栏 + 拖放上传区(虚线边框) + 向量化状态指示器(浮动) + 多选模式(底部工具栏) + 移动弹窗(文件夹选择器) |
| 2 | `ui/rag/DocEditorScreen.kt` (新建) | `.stitch/screens/a2f86f720c2c418f91494a522b92f84b.html` | 文档编辑器: GlassHeader(标题+大小+保存+预览/编辑切换) + 大文件警告 + 编辑区/预览区(基础语法高亮) |
| 3 | `ui/rag/KnowledgeGraphScreen.kt` (新建) | `.stitch/screens/55da3fa3f6ef4d33b59e80b4c5eba448.html` | 知识图谱查看器: 全屏Canvas + 节点(圆形+品牌色边框) + 边连线 + 拖拽平移/缩放 + 节点详情弹窗 |
| 4 | `ui/rag/RagAdvancedScreen.kt` (新建) | `.stitch/screens/97b9dbefd2b942cc99ae08d1e2eb9530.html` | RAG高级设置(知识图谱): KG启用开关 + 提取模型选择 + JIT微图区(开关+最大块数+免费模式+域名) + 成本策略(单选) + 本地优化(增量哈希+规则预过滤) + 提取提示词(FloatingTextEditor) + 查看图谱链接 |
| 5 | `ui/rag/RagDebugScreen.kt` (新建) | `.stitch/screens/9c902d909f90416b8febc13b4fcc8dc5.html` | RAG调试面板: 总向量数 + 存储大小 + 类型分布 + 冗余率 + 清理按钮 + Top会话列表 |
| 6 | `ui/rag/RagFolderScreen.kt` (修改) | `.stitch/screens/3b7b162796a24e178f04e3306f7fe13e.html` | 增强: 多选模式 + 批量操作 + 文档项完善 |
| 7 | `ui/settings/SkillsScreen.kt` (修改) | `.stitch/screens/1c3473ffc56346d4bd36c490a6a64aa8.html` | E8 增强: 循环限制步进器 + 3 Tab(预设技能/用户技能/MCP服务器) + MCP添加表单 + 服务器列表(状态图标+同步/删除) + 工具列表 |
| 8 | `ui/settings/GlobalRagConfigScreen.kt` (修改) | `.stitch/screens/2d213175f11948ed9366df51baf9d4d8.html` | E10 增强: 3 预设卡片 + 向量统计仪表盘(3 MetricCard) + 摘要模板编辑器 + "高级"链接(E12) + "更多详情"链接(E13) + 清除/清理按钮 |
| 9 | `ui/settings/AdvancedRetrievalScreen.kt` (修改) | `.stitch/screens/b24c64539c6a4ab8a7680dbcd5386f24.html` | E11 增强: 混合搜索区 + 可观测性区 + 重排序启用后相关滑块禁用逻辑 |

### Stitch 参考设计
- D1 知识库首页: `.stitch/screens/8a862a89c580418f9229e886a84b951e.html`
- D2 文件夹详情: `.stitch/screens/3b7b162796a24e178f04e3306f7fe13e.html`
- D3 文档编辑器: `.stitch/screens/a2f86f720c2c418f91494a522b92f84b.html`
- D4 知识图谱: `.stitch/screens/55da3fa3f6ef4d33b59e80b4c5eba448.html`
- E10 全局RAG: `.stitch/screens/2d213175f11948ed9366df51baf9d4d8.html`
- E12 RAG高级: `.stitch/screens/97b9dbefd2b942cc99ae08d1e2eb9530.html`
- E13 RAG调试: `.stitch/screens/9c902d909f90416b8febc13b4fcc8dc5.html`
- E8 技能: `.stitch/screens/1c3473ffc56346d4bd36c490a6a64aa8.html`

---

## 总工作量估算

| Session | 新建文件 | 修改文件 | 预估代码量 | 优先级 |
|---------|---------|---------|-----------|--------|
| G0 全局组件 | 13 | 0 | ~2500 行 | **P0** |
| G1 导航修复 | 0 | 6 | ~800 行修改 | **P0** |
| G2 Agent管理 | 5 | 2 | ~2000 行 | **P0** |
| G3 会话聊天 | 4 | 2 | ~2500 行 | **P1** |
| G4 设置Tab | 3 | 4 | ~2500 行 | **P1** |
| G5 知识库高级 | 4 | 5 | ~2000 行 | **P2** |
| **合计** | **29** | **19** | **~12300 行** | — |

---

*文档结束。以下为 OpenCode 指令模板。*
