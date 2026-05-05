# Nexara 功能迁移对齐审计报告

> **日期**: 2026-05-04  
> **审计范围**: RN (TypeScript) → Kotlin (Jetpack Compose) 前后端功能对齐度  
> **参照标准**: RN 原版 34 个 TSX 页面 + Stitch UI 功能参考  
> **审计方法**: 逐页对比 RN 原版功能与 Kotlin 实现的覆盖度

---

## 一、全局架构对齐

| 维度 | RN 原版 | Kotlin 实现 | 差距 |
|------|---------|-------------|------|
| **路由系统** | Expo Router (文件路由) 34 页面 | Navigation Compose 14 路由 | **缺 20 个路由** |
| **Tab 结构** | 3 Tab: Chat / Library / Settings | 4 Tab: Chat / Insights / Artifacts / Settings | **Tab 结构不对齐**，Insights 为占位 |
| **数据层** | MMKV + SQLite + VectorStore | Room 16 Entity + 15 DAO + 4 Repository | 数据层基本完整 |
| **状态管理** | Zustand Store (5个) | 5 ViewModel + 6 Manager | 架构对齐 |
| **LLM 协议** | OpenAI / Anthropic / VertexAI | LlmProvider + LlmProtocol | 协议层对齐 |
| **RAG 管线** | EmbeddingClient → TextSplitter → VectorStore | 同架构已实现 | 对齐 |

---

## 二、逐页对齐审计

### Group A — 应用外壳与全局导航

| Stitch ID | 页面 | RN 原版 | Kotlin 实现 | 对齐度 | 备注 |
|-----------|------|---------|-------------|--------|------|
| A1 | 欢迎页 | `app/welcome.tsx` SVG笔画动画+语言选择 | `WelcomeScreen.kt` (235行) 径向渐变+语言按钮 | **90%** | 缺SVG笔画描边动画 |
| A2 | 底部Tab导航 | 3 Tab (Chat/Library/Settings) | `MainTabScaffold.kt` 4 Tab | **60%** | 多了Insights(占位)和Artifacts，Tab结构不符Stitch规范 |
| A3 | 全局加载屏幕 | `app/index.tsx` | 无独立实现 | **0%** | 直接跳转，无加载画面 |

---

### Group B — 聊天 Tab 会话列表与 Agent 管理

| Stitch ID | 页面 | RN 原版 | Kotlin 实现 | 对齐度 | 备注 |
|-----------|------|---------|-------------|--------|------|
| B1 | 助手列表首页 | `app/(tabs)/chat.tsx` AgentExplorerScreen: 大标题+搜索+滑动置顶/删除+创建Agent+FAB超级助手 | `AgentHubScreen.kt` (282行) 基本列表+搜索+创建对话框 | **50%** | **缺**: 滑动操作(置顶/删除)、FAB超级助手按钮、大标题样式、空状态引导、Agent置顶排序 |
| B2 | Agent会话列表 | `app/chat/agent/[agentId].tsx`: 接收agentId参数+Agent名+会话数+搜索+滑动置顶/删除+FAB新建+设置入口 | `AgentSessionsScreen.kt` (207行) 标题**硬编码**"Super Assistant" | **25%** | **严重缺陷**: (1) 不接收agentId参数 (2) 标题硬编码 (3) 无FAB新建会话按钮 (4) 无Agent设置入口 (5) 无滑动操作 (6) 不传递Agent上下文 |
| B3 | Agent编辑器 | `app/chat/agent/edit/[agentId].tsx` 完整编辑页: 名称/描述/头像/颜色/SystemPrompt/模型/温度/RAG入口/高级检索入口/删除 | **不存在** | **0%** | **完全缺失**。无AgentEditScreen，无路由，无任何Agent编辑功能 |
| B3-sub | Agent RAG配置 | `app/chat/agent/edit/rag-config/[agentId].tsx` | **不存在** | **0%** | 完全缺失 |
| B3-sub | Agent高级检索 | `app/chat/agent/edit/advanced-retrieval/[agentId].tsx` | **不存在** | **0%** | 完全缺失 |

---

### Group C — 聊天对话界面与二级页面

| Stitch ID | 页面 | RN 原版 | Kotlin 实现 | 对齐度 | 备注 |
|-----------|------|---------|-------------|--------|------|
| C1 | 聊天主页 | `app/chat/[id].tsx` (~800行): 反转列表+流式输出+消息编辑/重发+模型切换+知识图谱提取+手动向量化+会话摘要+标题编辑+工具执行时间线 | `ChatScreen.kt` (222行): 基本消息列表+输入栏 | **30%** | **严重不足**: (1) 标题硬编码"Super Assistant" (2) 无流式输出UI (3) 无消息编辑/重发 (4) 无模型切换 (5) 无工具执行时间线 (6) 无标题编辑 (7) 无"回到底部"按钮 (8) 无Token统计弹窗 (9) 无知识图谱提取指示器 |
| C2 | 会话设置页 | `app/chat/[id]/settings.tsx`: 标题编辑+AI生成标题+推理参数+RAG开关(记忆/图谱/知识库)+文档选择+上下文管理+自定义Prompt+导出+删除 | `SessionSettingsScreen.kt` (107行): 仅"Active Agent: Super Assistant"硬编码标签 | **5%** | **几乎为空壳**: (1) Agent名硬编码 (2) 无标题编辑 (3) 无推理参数 (4) 无RAG开关 (5) 无文档选择 (6) 无自定义Prompt (7) 无导出/删除 |
| C3 | 会话设置底部弹窗 | `SessionSettingsSheet`: 4 Tab(模型/思考级别/统计/工具)+模型列表+思考级别+Token统计+工具开关+执行模式+MCP服务器 | **不存在** | **0%** | 完全缺失 |
| C4 | 工作区底部弹窗 | `WorkspaceSheet`: 3 Tab(任务/产物/文件)+文件预览+编辑 | **不存在** | **0%** | 完全缺失 |
| C5 | 超级助手设置 | `app/chat/spa-settings.tsx`: FAB外观/模型/知识图谱/RAG/上下文/全局统计/清理 | **不存在** | **0%** | 完全缺失 |
| C6 | SPA RAG配置 | `spa-rag-config`: 作用域RAG参数 | **不存在** | **0%** | 完全缺失 |
| C7 | SPA高级检索 | `spa-advanced-retrieval`: 作用域检索参数 | **不存在** | **0%** | 完全缺失 |
| C8 | Agent RAG配置 | `agent-rag-config`: 作用域RAG参数 | **不存在** | **0%** | 完全缺失 |
| C9 | Agent高级检索 | `agent-advanced-retrieval`: 作用域检索参数 | **不存在** | **0%** | 完全缺失 |

---

### Group D — 知识库 Tab

| Stitch ID | 页面 | RN 原版 | Kotlin 实现 | 对齐度 | 备注 |
|-----------|------|---------|-------------|--------|------|
| D1 | 知识库首页 | `RagHome`: 3入口Portal(文档/记忆/图谱)+文档视图+记忆视图+搜索+拖放上传+向量化状态+多选+移动弹窗 | `RagHomeScreen.kt` (245行): 3 Portal卡片+文件夹列表+空状态 | **40%** | **缺**: (1) 无Portal视图切换(文档/记忆) (2) 无拖放上传 (3) 无向量化状态指示 (4) 无多选模式 (5) 无移动弹窗 (6) 无搜索功能 |
| D2 | 文件夹详情 | `RagFolderDetail`: 文档列表+多选+移动/删除 | `RagFolderScreen.kt` (9.51KB) | **~60%** | 基本框架存在，细节未完全对齐 |
| D3 | 文档编辑器 | `DocEditor`: 编辑/预览+语法高亮+大文件警告 | **不存在** | **0%** | 完全缺失 |
| D4 | 知识图谱查看器 | `KnowledgeGraph`: 全屏可视化图谱 | **不存在** | **0%** | 完全缺失 |

---

### Group E — 设置 Tab

| Stitch ID | 页面 | RN 原版 | Kotlin 实现 | 对齐度 | 备注 |
|-----------|------|---------|-------------|--------|------|
| E1 | 设置首页 | `SettingsHome`: 用户头像+名称+语言+外观+主题色+触觉+Web搜索+模型预设+RAG+高级检索+Token+工作台+技能+本地模型+备份+日志+关于+提供商列表 | `UserSettingsHomeScreen.kt` (178行): 用户头像(硬编码)+6个静态设置项 | **20%** | **严重不足**: (1) 用户名硬编码"Alex Nova" (2) 无语言选择 (3) 无外观切换 (4) 无提供商Tab (5) 无提供商管理 (6) 无RAG配置入口(指向rag_advanced) (7) 无备份设置 (8) 无日志开关 (9) 无关于页面 (10) subtitle全硬编码 |
| E2 | 提供商表单 | `ProviderFormScreen`: 18+预设网格+名称/URL/Key/Project/Region/SA字段 | `ProviderFormScreen.kt` (14.17KB) | **~70%** | 基本框架存在，需验证预设覆盖度 |
| E3 | 提供商模型管理 | `ProviderModelsScreen`: 搜索+自动获取+添加+批量操作+模型项(名称/ID/测试/开关/类型/能力/上下文长度) | `ProviderModelsScreen.kt` (6.71KB) | **~40%** | 功能大幅精简，缺批量操作、测试连接、能力标签、类型选择器 |
| E4 | 搜索配置 | `SearchConfig`: 5引擎选择+结果数+API Key+保存 | `SearchConfigScreen.kt` (10.78KB) | **~60%** | 基本框架存在 |
| E5 | 便携工作台 | `Workbench`: 本地Web服务器+状态+稳定性引导+连接详情+访问码 | **不存在** | **0%** | 完全缺失 |
| E6 | Token用量统计 | `TokenUsage`: 环形图+3 MetricCard+按提供商/按模型视图+重置 | `TokenUsageScreen.kt` (7.56KB) | **~30%** | 基本列表展示，缺环形图和提供商/模型视图切换 |
| E7 | 本地模型管理 | `LocalModels`: GGUF导入+加载/卸载+插槽+硬件标识 | **不存在** | **0%** | 完全缺失 |
| E8 | 技能设置 | `SkillsSettings`: 循环限制+3Tab(预设/用户/MCP)+代码编辑+服务器管理 | `SkillsScreen.kt` (6.49KB) | **~25%** | 简化版，缺循环限制、MCP服务器管理、代码编辑器 |
| E9 | 主题设置 | `ThemeSettings`: ColorPicker+实时预览 | `ThemeScreen.kt` (7.5KB) | **~50%** | 基本颜色选择，预览可能简化 |
| E10 | 全局RAG配置 | `RagConfig`: 3预设+分块/重叠/记忆/上下文/摘要+模板+向量统计+清除 | `GlobalRagConfigScreen.kt` (12.02KB) | **~50%** | 基本滑块存在，需验证完整度 |
| E11 | 高级检索配置 | `AdvancedRetrieval`: 记忆/文档检索+重排序+查询改写+混合搜索+可观测性 | `AdvancedRetrievalScreen.kt` (13.23KB) | **~60%** | 最完整的页面之一 |
| E12 | RAG高级设置 | `RagAdvanced`: 知识图谱配置+JIT+成本策略+优化+提示词 | **不存在** (E10中可能部分集成) | **~10%** | 知识图谱高级配置几乎不存在 |
| E13 | RAG调试面板 | `RagDebug`: 向量统计+冗余率+Top会话+清理 | **不存在** | **0%** | 完全缺失 |
| E14 | 备份设置 | `BackupSettings`: 本地+WebDAV+加密+内容选择 | **不存在** | **0%** | 完全缺失 |

---

### Group G — 全局覆盖层与通用组件

| Stitch ID | 组件 | RN 原版 | Kotlin 实现 | 对齐度 | 备注 |
|-----------|------|---------|-------------|--------|------|
| G7 | Token统计弹窗 | GlassBottomSheet+环形指示器+3 MetricRow+重置 | **不存在** | **0%** | 完全缺失 |
| G8 | 图片查看器 | 全屏Modal+缩放+分享 | **不存在** | **0%** | 完全缺失 |
| G9 | 文本选择模态 | GlassBottomSheet+选择+复制 | **不存在** | **0%** | 完全缺失 |
| G10 | 浮动代码编辑器 | 全屏Modal+语法高亮+行号+保存 | **不存在** | **0%** | 完全缺失 |
| G11 | 浮动文本编辑器 | 全屏Modal+多行编辑+保存 | **不存在** | **0%** | 完全缺失 |
| G12 | Artifact渲染器 | 产物容器+类型徽章+ECharts/Mermaid | **不存在** | **0%** | 完全缺失 |

---

### Group H — 技能与执行系统

| Stitch ID | 组件 | RN 原版 | Kotlin 实现 | 对齐度 | 备注 |
|-----------|------|---------|-------------|--------|------|
| H1 | 核心记忆列表 | 记忆卡片+分类标签+删除 | **不存在** | **0%** | 完全缺失 |
| H2 | 工具执行时间线 | 可折叠容器+时间线步骤+干预UI+循环活跃干预 | **不存在** | **0%** | 完全缺失 |
| H3 | ModelPicker | GlassBottomSheet+搜索+模型列表+能力标签+选中态 | **不存在** (作为独立可复用组件) | **0%** | 无统一ModelPicker组件 |

---

## 三、导航参数传递缺陷（根因分析）

### 问题链路

```
AgentHubScreen.selectAgent(agent.id)
    → onNavigateToChat()           // ❌ 无参数传递
    → NavGraph: CHAT_HERO          // ❌ 直接跳聊天页，跳过SESSION_LIST
    
正确链路应为：
AgentHubScreen.selectAgent(agent.id)
    → onNavigateToSessionList(agentId)  // ✅ 传递agentId
    → NavGraph: SESSION_LIST/{agentId}  // ✅ 带参数路由
    → AgentSessionsScreen(agentId)      // ✅ 加载该Agent的会话
```

### 所有涉及参数传递的路由缺陷

| 路由 | 应有参数 | 实际参数 | 影响 |
|------|----------|----------|------|
| `SESSION_LIST` | `agentId` | 无 | 始终显示同一列表 |
| `CHAT_HERO` | `sessionId` | 无 | 无法加载指定会话 |
| `SESSION_SETTINGS` | `sessionId` | 无 | 无法编辑指定会话设置 |
| `PROVIDER_FORM` | `providerId?` | 无 | 无法编辑现有提供商 |
| `PROVIDER_MODELS` | `providerId` | 无 | 无法管理指定提供商模型 |

---

## 四、缺失路由清单

以下是 RN 原版存在但 Kotlin 中完全没有路由的页面：

| # | 缺失路由 | 对应 RN 页面 | 优先级 |
|---|----------|-------------|--------|
| 1 | `agent_edit/{agentId}` | `app/chat/agent/edit/[agentId].tsx` | **P0** |
| 2 | `agent_rag_config/{agentId}` | `app/chat/agent/edit/rag-config/[agentId].tsx` | **P1** |
| 3 | `agent_advanced_retrieval/{agentId}` | `app/chat/agent/edit/advanced-retrieval/[agentId].tsx` | **P1** |
| 4 | `spa_settings` | `app/chat/spa-settings.tsx` | **P1** |
| 5 | `spa_rag_config` | SPA RAG配置 | **P2** |
| 6 | `spa_advanced_retrieval` | SPA高级检索 | **P2** |
| 7 | `session_settings_sheet` | 会话设置底部弹窗 | **P0** |
| 8 | `workspace_sheet` | 工作区底部弹窗 | **P2** |
| 9 | `doc_editor` | 文档编辑器 | **P1** |
| 10 | `knowledge_graph` | 知识图谱查看器 | **P2** |
| 11 | `rag_advanced` (E12) | 知识图谱高级配置 | **P2** |
| 12 | `rag_debug` | 向量统计调试 | **P2** |
| 13 | `backup_settings` | 备份与恢复 | **P2** |
| 14 | `workbench` | 便携工作台 | **P3** |
| 15 | `local_models` | 本地GGUF模型 | **P3** |

---

## 五、全局组件缺失清单

| # | 缺失组件 | RN 原版位置 | 优先级 |
|---|----------|-------------|--------|
| 1 | ModelPicker (BottomSheet) | 全局复用 | **P0** |
| 2 | FloatingTextEditor (Modal) | System Prompt / 摘要模板编辑 | **P0** |
| 3 | FloatingCodeEditor (Modal) | 技能代码 / JSON 编辑 | **P1** |
| 4 | ExecutionModeSelector | auto/semi/manual 分段控制 | **P1** |
| 5 | InferencePresets | 精确/均衡/创意 三卡片 | **P1** |
| 6 | ColorPickerPanel | 颜色网格+RainbowSlider | **P1** |
| 7 | TokenStatsModal (BottomSheet) | 聊天中Token统计 | **P2** |
| 8 | ArtifactRenderer | 产物渲染(ECharts/Mermaid) | **P2** |
| 9 | ImageViewerModal | 图片全屏查看+缩放+分享 | **P2** |
| 10 | SwipeableItem | 滑动操作(置顶/删除) | **P1** |
| 11 | ConfirmDialog | 通用确认对话框 | **P1** |

---

## 六、功能完整度评分

| 功能域 | RN 页面数 | Kotlin 已实现 | 完整度 | 评分 |
|--------|-----------|---------------|--------|------|
| **A. 应用外壳** | 3 | 2 (A1不完整) | 67% | C |
| **B. Agent管理** | 5 | 2 (B1不完整, B2严重缺陷) | 25% | **F** |
| **C. 聊天界面** | 9 | 2 (C1不完整, C2空壳) | 15% | **F** |
| **D. 知识库** | 4 | 2 (D1不完整) | 35% | **D** |
| **E. 设置** | 14 | 9 (多数简化) | 30% | **D** |
| **G. 覆盖层/组件** | 6 | 0 | 0% | **F** |
| **H. 技能系统** | 3 | 0 | 0% | **F** |
| **综合** | **44** | **17 (含简化)** | **~25%** | **F** |

---

## 七、关键硬编码问题

| 位置 | 硬编码内容 | 应该 |
|------|-----------|------|
| `AgentSessionsScreen.kt:50` | `"Super Assistant"` | 接收 `agentId` → 加载 Agent 名称 |
| `ChatScreen.kt:58` | `"Super Assistant"` | 接收 `sessionId` → 加载会话标题 |
| `ChatScreen.kt:196` | `"Message Super Assistant..."` | 动态 Agent 名 |
| `SessionSettingsScreen.kt:89` | `"Super Assistant"` | 接收会话关联的 Agent 名 |
| `UserSettingsHomeScreen.kt:147` | `"AN"` 头像 | 用户配置的头像 |
| `UserSettingsHomeScreen.kt:152` | `"Alex Nova"` | 用户名 |
| `UserSettingsHomeScreen.kt:157` | `"Pro Plan • Active"` | 用户状态 |
| `UserSettingsHomeScreen.kt:62` | `"GPT-4 Turbo, Temp 0.7"` | 当前模型配置 |
| `UserSettingsHomeScreen.kt:69` | `"3 Active Sources"` | 知识库统计 |
| `UserSettingsHomeScreen.kt:78` | `"Tavily Deep Search Enabled"` | 搜索配置 |
| `UserSettingsHomeScreen.kt:86` | `"14 Functions Ready"` | 技能统计 |
| `UserSettingsHomeScreen.kt:94` | `"$12.40 this month"` | Token费用 |
| `UserSettingsHomeScreen.kt:101` | `"Premium Dark Mode"` | 当前主题 |

---

## 八、总结与优先级建议

### P0 — 核心流程断裂（必须修复）

1. **导航参数传递**: 所有路由需传递 `agentId`/`sessionId` 等参数
2. **Agent编辑器 (B3)**: 完全缺失，用户无法编辑/配置 Agent
3. **会话设置底部弹窗 (C3)**: 缺失，聊天中无法切换模型/调整参数
4. **ModelPicker 全局组件**: 缺失，多处依赖

### P1 — 重要功能缺失

5. **Agent RAG/高级检索配置 (B3-sub)**: Agent 级 RAG 定制
6. **会话设置页 (C2)**: 当前为空壳，需完整实现
7. **超级助手设置 (C5)**: 全局配置入口
8. **文档编辑器 (D3)**: 知识库文档管理
9. **全局组件**: SwipeableItem, InferencePresets, ColorPickerPanel, ConfirmDialog
10. **设置首页 (E1)**: 需动态数据绑定 + 提供商管理 Tab

### P2 — 次要功能缺失

11. 知识图谱查看器 (D4)
12. RAG高级设置 (E12)
13. 备份设置 (E14)
14. Token统计弹窗 (G7)
15. Artifact渲染器 (G12)
16. 工作区弹窗 (C4)
17. 浮动代码编辑器 (G10)

### P3 — 低优先级

18. 便携工作台 (E5)
19. 本地GGUF模型 (E7)
20. RAG调试面板 (E13)
21. 图片查看器 (G8)

---

> **结论**: 当前 Kotlin 迁移的**后端架构（数据层、协议层、RAG管线）**完成度较高（~85%），但**前端功能对齐度仅约 25%**。核心问题集中在：(1) 导航参数传递链路断裂导致所有 Agent 指向同一页面；(2) 大量二级页面和全局组件未迁移；(3) 设置首页数据全部硬编码。建议按 P0 → P1 → P2 优先级分阶段补齐。
