# 交接文档 (2026-05-14 会话完成)

## ✅ 已完成 — AGP 构建警告消除 (2026-05-14)
- `build.gradle.kts`: 删除冗余 `sourceSets { jniLibs.srcDir(...) }` 块，`src/main/jniLibs` 是 AGP 默认目录
- `gradle.properties`: `disallowKotlinSourceSets=false` 保留（KSP Room compiler 必需），注释说明原因

## ✅ 已完成 — 知识库导入 Bug 修复 (2026-05-14)
- **🔴 P0**: `RagHomeScreen.kt:407` — `shownDocs.isEmpty()` 逻辑反转 → 改为 `isNotEmpty()`，修复文档列表渲染
- **🟡**: `RagViewModel.kt` — 新增 `lastQueueError` StateFlow，向量化失败后保留错误提示 UI
- **🟡**: `VectorizationQueue.kt` — `notifyStateChange()` 在完成/失败后补充调用
- **🔵**: `EmbeddingClient.kt` — 空配置前置检查，避免无意义重试
- **🔵**: `ChatScreen.kt` — 补充 `delay`/`clickable`/`FontWeight` 缺失导入

## ✅ 已完成 — 嵌入模型全链路审计 + 致命 Bug 修复 (2026-05-14)
- **🔴 P0 致命 Bug**: `embedding_base_url`/`embedding_api_key` 永为空
  - 原因: ProviderManager 写入 `nexara_provider` 的键是 `base_url`/`api_key`，但 NexaraApplication 的 `embeddingClient` 读取的是 `embedding_base_url`/`embedding_api_key`（不同键名）
  - 修复: `NexaraApplication.kt` — 专用键为空时回退到主 LLM 提供商的 `base_url`/`api_key`
  - 同样修复了 `rerankClient`
- **全链路审计**: Provider 配置 → ProviderManager → NexaraApplication → EmbeddingClient → VectorizationQueue/VectorRepository/MemoryManager
- **VectorizationQueue** 新增 `dispatcher` 参数（默认 `Dispatchers.Default`），提升可测试性

## ✅ 已完成 — 重排模型调用管线修复 (2026-05-14)
- **🔴 P0 致命 Bug**: `RerankClient.rerank()` 从未被调用
  - 原因: `MemoryManager` 构造函数不包含 `rerankClient` 参数；`retrieveContext()` 缺失重排步骤
  - 修复: 注入 `rerankClient: RerankClient?` → 去重后、类型过滤前插入 rerank 调用
- **🟡**: `Reranker.kt` — 新增空配置前置检查

## ✅ 已完成 — PipelineBubble 气泡合并 + 容器重构 (2026-05-14)
- **新增 `PipelineBubble.kt`**: 将 Agent 多步 ASSISTANT+TOOL 消息合并为单一线性气泡，内部以思考→工具→正文的流水线排列，步骤间以竖线连接器串联
- **`buildPipelineGroups()`**: 相邻 ASSISTANT/TOOL 消息合并为一组，USER 消息独立成组
- **`InlineThinkingRow`**: 替代旧版 `ThinkingBlock`，紧凑内联布局（Primary 色系），进行中脉冲圆点 + "正在思考"，完成后对勾 + "思考完成"，默认折叠
- **`InlineToolRow`**: 替代旧版 `ToolExecutionTimeline`，紧凑内联布局（Tertiary 色系），显示工具名 + 状态（脉冲/对勾/红叉），展开后显示参数和结果摘要，默认折叠
- **`PipelineConnector`**: 竖线连接器（灰色圆点 + 细线），串联各步骤
- **锚定修复** (`ChatScreen.kt`): `LaunchedEffect(latestUserMsgId)` 替代 `isGenerating + streamingContent.isEmpty()` 竞态条件
- **IME 键盘联动** (`ChatScreen.kt`): `WindowInsets.isImeVisible` 检测 + 分组索引滚动
- **Agent Fallback 解析器** (`ChatViewModel.kt`): `extractToolCallsFromText()` 支持 `name/function/tool/tool_name` 多字段约定 + OpenAI `function.arguments` 嵌套 + 代码块/裸JSON 双模式
- **JSON 剥离增强** (`ChatViewModel.kt`): `stripToolCallJsonBlocks()` 双重匹配 — Markdown 代码块 + 裸 JSON 对象行
- **流式速度**: `StreamSpeed.BALANCED` 38→120 CPS, FAST 800 CPS
- **表格深色模式**: `NexaraTableWidget` 新增行间分隔线

## ✅ 已完成 — 图像生成工具 (2026-05-14)
- **新增文件**:
  - `ImageGenClient.kt` — OpenAI-compatible 图像生成客户端
  - `ImageGenerationSkill.kt` — `generate_image` 工具实现
  - `GeneratedImageData` — 图片本地存储元信息
- **修改文件**:
  - `NexaraApplication.kt` — 注册 ImageGenerationSkill
  - `ChatScreen.kt` — ChatBubble 新增 AsyncImage 图片渲染
  - `ToolExecutor.kt` — `images = result.data` 传递图片数据到 Message
- **设计**: LLM 聊天与图像生成可调用不同端点（独立读取 `preset_image_model`）
- **ADR**: 见 `docs/ADR/image-generation-tool.md`

## ✅ 已完成 — 单元测试 (2026-05-14)
- 新增 3 个测试类: `EmbeddingClientTest` (21), `VectorizationQueueTest` (23), `RagViewModelTest` 扩展 (6)
- 总计 50 个新测试用例，101 tests 98% 通过率 (2 预存失败)

## ✅ 已完成 — 工具管理与聊天交互 UI 优化 (2026-05-14)
- **工具管理**:
    - `SkillsScreen.kt`: `ScrollableTabRow` -> `TabRow` 居中对齐；美化 Tab 指示器
    - 统一标题为 "工具管理" (zh-CN) / "Tool Management" (en)
    - `UserSettingsHomeScreen.kt`: 移除未实装的"外观设置"条目
- **聊天界面布局**:
    - `ChatScreen.kt`: 输入框底部间距 `20.dp` -> `8.dp`
    - `TokenIndicator`: 气泡样式美化（圆角 24dp + NexaraGlassCard），通过 `DpOffset` 修正偏右问题，实现正上方对齐
    - **模型名称转换**: 将输入栏及消息底部的模型 ID (如 `gemini-3-flash`) 替换为易读名称 (如 `Gemini 3 Flash`)

## ✅ 已完成 — 思考容器自动展开修复 (2026-05-14)
- **时空竞态修复**: `PipelineBubble.kt:123` — `isThinkingStreaming` 判定从 `status == THINKING` 改为 `streamingContent.isEmpty()`
- **原理**: 思考步骤首次渲染时机总是晚于 THINKING 窗口，正文开始后 `streamingContent` 非空自动折叠显示"思考完成"
- **副作用**: 无（正文开始瞬间折叠，不会持续"正在思考"）

## ✅ 已完成 — 输入栏草稿持久化 (2026-05-14)
- `ChatViewModel.loadSession()`: 缓存 + DB 两条路径均恢复 `Session.draft` → `_inputText`
- `ChatViewModel.saveCurrentDraft()`: 新增方法，写入 DB 草稿
- `ChatScreen.kt`: `DisposableEffect(sessionId) { onDispose { saveCurrentDraft() } }`
- `ChatViewModel.sendMessage()`: 发送后异步清空 DB `draft = null`

## ✅ 已完成 — 思考容器文本颜色修复 (2026-05-14)
- **根因**: `nexaraMarkdownColors().text` 硬编码 `OnBackground`，第三方库不读取 CompositionLocal
- **修复**: `nexaraMarkdownColors(textColor=)` 参数化，`MarkdownSafe(textColor=)` 透传 `effectiveColor`
- **影响**: `NexaraMarkdownTheme.kt`, `MarkdownText.kt`，同步修复 InlineThinkingRow + ThinkingBlock

## ✅ 已完成 — DIA 深度审计与文档体系刷新 (2026-05-14)
- **registry.md**: 18 个过期计划归档，保留 1 个活跃计划；更新项目补充说明
- **ARCHITECTURE.md**: 更新依赖图（新增 Domain 层 + GenerationService 计划）、Repository 覆盖率、ADR 状态
- **IMPLEMENTATION_ANALYSIS.md**: 版本 2.0.0-alpha → 2.0.0-beta；§1 代码规模 235→300；§2 Domain 层缺失→已实现；Repository 3/8→9/9 (100%)；§3 总体进度 63%→74%；§4 AD-1~AD-4 全部消除；§10 完全重写开发路线图
- **handover.md**: 本会话变更

## ✅ 已完成 — 三会话并行：提示词系统 + 编辑器 + 视觉 (2026-05-14)
- **S-A 双层系统提示词**: ChatViewModel 分离 agentSystemPrompt/sessionCustomPrompt；ContextBuilderParams 新增字段；ContextBuilder §5 改写为独立 session 层（"## Session Instructions" 标注）
- **S-B Markdown 编辑器**: 新建 `UnifiedPromptEditor.kt` — Editor/Preview/Split 三模式 Tab，行号列+字数统计；替换 FloatingTextEditor 共 4 处（AgentEdit + SessionSettings + AgentRagConfig + ChatScreen 三点菜单）
- **S-C 视觉 MD3 美化**: AgentEditScreen 447 行重构 — NexaraGlassCard→M3 Card、头像 100dp→48dp、推理预设 Card→FilterChip、Section 间 HorizontalDivider
- **ChatScreen 菜单补丁**: 三点菜单新增 "Session Prompt"（Description icon）+ 分隔线，点击打开 UnifiedPromptEditor
- 同步完成 RAG Phase 7 知识库修复（Reranker 增强、PDF 提取、KG 可视化重构等）

## 🚀 下一步

| 优先级 | 任务 | 工时 | 说明 |
|--------|------|------|------|
| **P0** | 后台生成能力（GenerationService） | 2d | Foreground Service 承载 SSE 流式，方案已规划 |
| P0 | 思考容器 `userToggled` flag | 0.3h | 手动折叠后不被流式更新覆盖 |
| P1 | PDF/Word/HTML 文档导入 | 2d | 扩展 PdfExtractor/HtmlExtractor |
| P1 | 混合检索（向量+FTS5） | 1.5d | VectorStore + KeywordSearcher 融合 |
| P1 | Embedding 本地降级 | 1d | 无远程 API 时回退本地方案 |
| P1 | 知识图谱可视化（D3.js） | 2d | WebView 力导向图 |
| P2 | MCP 协议客户端 | 3d | McpServerEntity 完整实现 |
| P2 | Token 仪表盘完善 | 1.5d | TokenUsageScreen UI |

## ⚠️ 风险
- 后台生成：Android Foreground Service + POST_NOTIFICATIONS 权限（API 34+）
- DB 竞态：GenerationService / ChatViewModel 互斥（`currentGeneratingSessionId`）

## ✅ 已完成 — Phase 9 发布冲刺 + 测试补全 (2026-05-15)
- **5 会话并行执行**，23 项变更全部检查通过，零失败
- **多模态**: 图片选择/预览/发送 + OpenAI Vision + Anthropic 双协议适配
- **Token 仪表盘**: GlobalStatsCard + SessionRanking + Canvas 趋势图 + 费用计算
- **HTML Artifacts**: HtmlArtifactCard WebView 预览 + 全屏分屏 + PNG 导出
- **测试**: 10 个新测试文件（22 用例），52 个测试文件全覆盖
- 计划文件: `.agent/plans/20260515-phase9-polish-and-tests.md`
- 总体进度: 84% → 92%

## ✅ 已完成 — Phase 8 Agent 工具系统重构与增强 (2026-05-15)
- **3 会话并行执行**，19 项变更全部检查通过，零失败
- **工具分类**: CurrentTimeSkill 退役为被动注入，主动/注入/MCP 三轨并行
- **生图暴露**: ImageGenerationSkill 出现在设置界面，用户可控
- **MCP 同步**: 修复 McpSkillRegistry→SettingsViewModel 闭链，动态工具可被 LLM 调用
- **文件工具**: 4 个新增（read/write/list/search），工作区绑定 + 逃逸检查
- **JS 沙箱**: exec_js 基于 WebView，5s 超时，供 LLM 执行确定性计算
- **审批增强**: 工具级审批跳过 + 通过后执行
- 计划文件: `.agent/plans/20260514-phase8-agent-tools-enhancement.md`

## ✅ 已完成 — Phase 7 知识库系统修复与增强 (2026-05-14)
- **5 会话并行执行**，27 项变更全部检查通过，零失败
- **PDF/Word**: Apache PDFBox + POI 集成，真实文本提取
- **编辑器**: DocEditorViewModel 移除 Mock 内容，标题持久化
- **文件夹**: 级联删除 + 重命名
- **检索增强**: 混合检索/Rerank/查询重写默认开启
- **UI 补全**: Memory 视图、KG ECharts 可视化、FTS5 全文搜索
- 计划文件: `.agent/plans/20260514-phase7-knowledge-base-repair.md`

## DIA Status
- `CHANGELOG.md` ✅ 已更新 (2026-05-14)
- `docs/MARKDOWN_RENDERING_AUDIT.md` ✅ 已更新
- `docs/ARCHITECTURE.md` ✅ 已更新 (ADR-004, Domain层, Repository覆盖率)
- `docs/IMPLEMENTATION_ANALYSIS.md` ✅ 已更新 (v2.0.0-beta, 进度74%)
- `.agent/registry.md` ✅ 已更新 (18文件归档)
- 详见 `.agent/registry.md`
