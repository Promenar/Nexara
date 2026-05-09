# Handover Log

## MD-S4 — Mermaid 流程图 + ECharts 图表渲染 (2026-05-10)

### 已完成

- **Mermaid 离线资源**: 下载 mermaid@11 的 mermaid.min.js (3.2MB) 到 `assets/mermaid/`
- **ECharts 离线资源**: 下载 echarts@5 的 echarts.min.js (1.0MB) 到 `assets/echarts/`
- **MermaidBlock**: 新建 `ui/renderer/MermaidRenderer.kt`，Mermaid 流程图渲染器
  - 构建完整 HTML（引用本地 mermaid.min.js），暗色主题配置
  - CSS 覆盖适配 Nexara 暗色配色（节点/边/标签/聚类背景色）
  - JS 字符串转义处理（反斜杠、反引号、美元符号）
- **EChartsBlock**: 新建 `ui/renderer/EChartsRenderer.kt`，ECharts 图表渲染器
  - 构建完整 HTML（引用本地 echarts.min.js），暗色主题 `'dark'`
  - 强制透明背景，resize 监听自适应宽度
  - try-catch 包裹渲染错误，显示红色错误信息
- **MarkdownText 集成**:
  - ContentSegment 新增 Mermaid/ECharts 子类型
  - `splitLatexSegments` 重命名为 `splitRichSegments`，增加 ` ```mermaid ``` ` 和 ` ```echarts ``` ` 代码块检测
  - 使用 RichSpan 区间冲突检测，LaTeX 块不与已占用的 mermaid/echarts 区间重叠
  - 渲染分支新增 MermaidBlock / EChartsBlock 调用

### 新增文件

| 文件 | 说明 |
|------|------|
| `assets/mermaid/mermaid.min.js` | Mermaid v11 离线 JS |
| `assets/echarts/echarts.min.js` | ECharts v5 离线 JS |
| `ui/renderer/MermaidRenderer.kt` | Mermaid 流程图渲染器（MermaidBlock + buildMermaidHtml） |
| `ui/renderer/EChartsRenderer.kt` | ECharts 图表渲染器（EChartsBlock + buildEChartsHtml） |

### 修改文件

| 文件 | 变更 |
|------|------|
| `ui/common/MarkdownText.kt` | ContentSegment 增加 Mermaid/ECharts，splitLatexSegments→splitRichSegments，渲染分支扩展 |

### 验收

- `compileDebugKotlin` BUILD SUCCESSFUL
- ` ```mermaid\ngraph LR\nA-->B\n``` ` 渲染为暗色主题流程图
- ` ```echarts\n{"xAxis":{"data":["A","B"]},...}\n``` ` 渲染为暗色主题图表
- ECharts 渲染错误时显示红色错误信息
- Mermaid/ECharts 与 LaTeX、普通 Markdown 混排正常

---

## MD-S3 — WebView 沙箱基座 + LaTeX 数学公式渲染 (2026-05-10)

### 已完成

- **KaTeX 离线资源**: 下载 katex@0.16.21 的 katex.min.js、katex.min.css、62 个字体文件到 `assets/katex/`
- **RichContentWebView**: 新建 `ui/renderer/RichContentWebView.kt`，通用 WebView 沙箱组件
  - 透明背景、JS 启用、自动内容高度测量（`onPageFinished` → `scrollHeight`）
  - `loadDataWithBaseURL("file:///android_asset/")` 确保 CSS 字体相对路径正确解析
- **LatexBlock**: 新建 `ui/renderer/LatexRenderer.kt`，LaTeX 公式渲染器
  - 构建完整 HTML（引用本地 KaTeX 资源），`katex.render()` 渲染 displayMode 公式
  - JS 字符串转义处理（反斜杠、引号、换行符）
- **MarkdownText LaTeX 集成**: `splitLatexSegments()` 将 markdown 按 `$$...$$` 拆分为 Markdown/Latex 段
  - 每段独立渲染：Markdown 段走 mikepenz Markdown，LaTeX 段走 LatexBlock WebView
  - 保留原有的代码围栏自定义渲染、流式光标、sanitizeStreamingMarkdown 逻辑

### 新增文件

| 文件 | 说明 |
|------|------|
| `assets/katex/katex.min.js` | KaTeX 0.16.21 核心 JS |
| `assets/katex/katex.min.css` | KaTeX 0.16.21 样式表 |
| `assets/katex/fonts/` | 62 个 KaTeX 字体文件（woff2/woff/ttf） |
| `ui/renderer/RichContentWebView.kt` | 通用 WebView 沙箱组件 |
| `ui/renderer/LatexRenderer.kt` | LaTeX 公式渲染器（LatexBlock + buildLatexHtml） |

### 修改文件

| 文件 | 变更 |
|------|------|
| `ui/common/MarkdownText.kt` | 新增 ContentSegment sealed class、splitLatexSegments()、MarkdownText 拆分渲染逻辑 |

### 验收

- `compileDebugKotlin` BUILD SUCCESSFUL
- `$$ E = mc^2 $$` 将通过 KaTeX 渲染为数学公式
- 复杂公式（矩阵、分式、积分）由 KaTeX 引擎完整支持
- LaTeX 块前后的普通 Markdown 内容正常渲染
- WebView 高度通过 scrollHeight 自适应

---

## MD-S5 — 流式渲染优化 + ThinkingBlock 接入 (2026-05-10)

### 已完成

- **sanitizeStreamingMarkdown()**: 新增流式输出预处理函数，自动修补未闭合代码围栏（补 ```` ``` ````）和截断未闭合 LaTeX（`$$`）块
- **MarkdownText 流式处理**: `MarkdownText` 在 `isStreaming=true` 时通过 `remember` 缓存经 `sanitizeStreamingMarkdown` 处理后的文本再传入 mikepenz Markdown
- **ThinkingBlock Markdown 渲染**: ThinkingBlock 中 reasoning 从纯 `Text()` 替换为 `MarkdownText(isStreaming = isGenerating)`，支持粗体、列表、代码等格式
- **清理**: 移除未使用的 `FontWeight` import，确认无残留 `FontFamily.Serif` 引用

### 修改文件

| 文件 | 变更 |
|------|------|
| `ui/common/MarkdownText.kt` | 新增 `sanitizeStreamingMarkdown()` 函数，`MarkdownText` 增加 `remember` 缓存 + 流式预处理 |
| `ui/chat/ChatScreen.kt` | ThinkingBlock reasoning 用 MarkdownText 替换 Text，移除未使用 FontWeight import |

### 验收

- `compileDebugKotlin` BUILD SUCCESSFUL
- Markdown 渲染大修（MD-S1 ~ MD-S5）全部完成

---

## MD-S1 — 依赖集成 + MarkdownText 重写 + ChatBubble 接入 (2026-05-10)

### 已完成

- **Gradle 依赖**: 添加 `mikepenz/multiplatform-markdown-renderer` v0.40.2（m3 + coil3）
- **NexaraMarkdownTheme**: 新建 `ui/renderer/NexaraMarkdownTheme.kt`，映射 NexaraColors/Typography 到 mikepenz API
- **MarkdownText 重写**: 用 mikepenz `Markdown` composable 替换手写解析器，保留 `StreamingCursor` 呼吸光标
- **ChatBubble 接入**: 助手消息从 `Text` 切换为 `MarkdownText`，移除重复的 cursor Box 和 infiniteTransition 变量

### 新增文件

| 文件 | 说明 |
|------|------|
| `ui/renderer/NexaraMarkdownTheme.kt` | Nexara 主题映射（markdownColor / markdownTypography） |

### 修改文件

| 文件 | 变更 |
|------|------|
| `app/build.gradle.kts` | 添加 markdown-renderer-m3/coil3 依赖 |
| `ui/common/MarkdownText.kt` | 完全重写，使用 mikepenz Markdown composable |
| `ui/chat/ChatScreen.kt` | ChatBubble 助手分支用 MarkdownText 替换 Text，移除重复光标代码 |

### 验收

- `compileDebugKotlin` BUILD SUCCESSFUL
- mikepenz 0.40.2 API：`markdownColor(text, codeBackground, inlineCodeBackground, dividerColor, tableBackground)`
- mikepenz 0.40.2 API：`markdownTypography(h1-h6, text, code, inlineCode, quote, paragraph, ordered, bullet, list, textLink, table)`

---

## S2 — archiveToRag 断链 + KG 上下文注入 (2026-05-10)

### 已完成

- **问题一**: archiveToRag 从未被调用
  - `ChatViewModel.generateMessage()` 在 `postProcessor.updateStats()` 之后添加 `postProcessor.archiveToRag()` 调用
  - `ragEnabled` 从硬编码 `false` 改为读取 `session.ragOptions` 配置 (`enableMemory || enableDocs`)
  - `ragUsage` 从 contextResult 传入 updateStats
  - archiveToRag 仅在 `enableMemory == true` 且 `accumulatedContent.isNotBlank()` 时执行，异常被静默捕获

- **问题二**: 知识图谱检索结果未注入 LLM 上下文
  - `ContextBuilder` 新增 `KgProvider` 接口和 `kgProvider` 构造参数
  - `buildContext()` 在 RAG 检索之后、system prompt 构建之前调用 `kgProvider.extractContext()`
  - `buildSystemPrompt()` 在 `## Retrieved Context` 之后追加 `## Knowledge Graph Relations` 段落
  - KG 提取失败不影响正常流程（try-catch 包裹，kgProvider=null 时跳过）

- **新建文件**: `data/rag/MicroGraphKgAdapter.kt`
  - 适配 `MicroGraphExtractor` → `KgProvider` 接口
  - `RagReference` → `SearchResult` 字段映射：id, content, score→similarity

- **NexaraApplication**: 新增 `microGraphExtractor` 和 `kgProvider` lazy 实例
  - 使用 `llmProvider.protocol`（LlmProvider.protocol 改为 internal 可见性）
  - 依赖 `graphStore` 和 `database.kgJitCacheDao()`

- **附修 S4 遗留编译错误**: 补全 DefaultSkillRegistry/CurrentTimeSkill/CalculatorSkill 的 import 和 skillRegistry 参数传递

### 修改文件

| 文件 | 变更 |
|------|------|
| `ui/chat/ChatViewModel.kt` | archiveToRag 调用、ragEnabled 配置化、kgProvider/skillRegistry 参数、ragUsage 传递 |
| `ui/chat/manager/ContextBuilder.kt` | KgProvider 接口、kgProvider 参数、KG 上下文注入 |
| `data/rag/MicroGraphKgAdapter.kt` | **新建** MicroGraphExtractor → KgProvider 适配器 |
| `data/remote/provider/LlmProvider.kt` | `protocol` 改为 `internal val` 暴露给同模块 |
| `NexaraApplication.kt` | microGraphExtractor/kgProvider lazy 实例、S4 import 补全 |

### 验证

- `compileDebugKotlin` BUILD SUCCESSFUL

---

## S3 — Tools 调用断链修复 (2026-05-10)

### 已完成

- **问题一**: PromptRequest 未传入 tools / frequencyPenalty / presencePenalty / webSearch
  - `ChatViewModel.generateMessage()` 现在传入全部 `InferenceParams` 字段
  - 添加 `buildToolList()` 骨架方法（当前返回空列表，S4 实现 SkillRegistry 后填充）
  - `tools` 字段为空列表时传 null，不影响不支持 tools 的协议

- **问题二**: Tool 执行后 Agent Loop 断裂
  - LLM 返回 tool_calls → 执行工具 → 结果作为 TOOL 消息追加 → 创建新 assistant 消息 → 递归调用 `generateMessage`
  - 添加 `loopCount` 参数防止无限递归，上限为 `session.autoLoopLimit`（默认 5）
  - 每次递归检查 `currentCoroutineContext().isActive`

- **问题三**: ContextBuilder 拼接 agentId 而非 systemPrompt
  - `ContextBuilderParams` 新增 `agentSystemPrompt` 字段
  - `buildSystemPrompt()` 使用 `params.agentSystemPrompt` 替代 `session.agentId`
  - `ChatViewModel` 从 `AgentDao` 查询 agent 的 `systemPrompt`，fallback 到 `session.customPrompt`

### 修改文件

| 文件 | 变更 |
|------|------|
| `ui/chat/ChatViewModel.kt` | 新增 `loopCount` 参数、`buildToolList()`、Agent Loop 续跑逻辑、agent prompt 查询 |
| `ui/chat/manager/ContextBuilder.kt` | `ContextBuilderParams` 新增 `agentSystemPrompt`，`buildSystemPrompt` 使用正确 prompt |

### 验证

- `compileDebugKotlin` BUILD SUCCESSFUL

## S4 — SkillRegistry + 内置 Skills (2026-05-10)

### 已完成

- **DefaultSkillRegistry**: `DefaultSkillRegistry` 实现 `SkillRegistry`，支持 `register()` / `getSkill()` / `getAllTools()`
- **ParameterizedSkill 接口**: Skill 可选实现此接口提供 `parametersSchema` JSON Schema
- **CurrentTimeSkill**: 返回 ISO 8601 格式当前时间，无参数
- **CalculatorSkill**: 递归下降解析四则运算表达式，支持 `+-*/()` 和一元负号
- **NexaraApplication**: 暴露 `skillRegistry` 懒加载属性，注册 2 个内置 Skill
- **ChatViewModel**:
  - 构造函数新增 `skillRegistry: DefaultSkillRegistry?` 参数
  - `toolExecutor` 接收 `skillRegistry`
  - `buildToolList()` 从 `skillRegistry.getAllTools()` 获取工具列表
  - Factory 传入 `app.skillRegistry`

### 新增文件

| 文件 | 说明 |
|------|------|
| `ui/chat/manager/DefaultSkillRegistry.kt` | SkillRegistry 默认实现 + ParameterizedSkill 接口 |
| `ui/chat/manager/skills/CurrentTimeSkill.kt` | current_time 内置 Skill |
| `ui/chat/manager/skills/CalculatorSkill.kt` | calculator 内置 Skill（递归下降解析器） |

### 修改文件

| 文件 | 变更 |
|------|------|
| `NexaraApplication.kt` | 新增 `skillRegistry` 属性，注册内置 Skills |
| `ui/chat/ChatViewModel.kt` | 构造函数新增 `skillRegistry` 参数，`buildToolList()` 填充，Factory 传入 |

### 验证

- `compileDebugKotlin` BUILD SUCCESSFUL

---

## MD-S2 — 代码块增强（语法高亮 + 复制按钮 + 语言标签） (2026-05-10)

### 已完成

- **Gradle 依赖**: 添加 `multiplatform-markdown-renderer-code` v0.40.2（基于 `dev.snipme:highlights` 的语法高亮）
- **CodeBlockWithHeader**: 新建 `ui/renderer/CodeBlockHeader.kt`，自定义代码块容器
  - 顶部 Row：左侧语言名称大写标签，右侧复制按钮（AnimatedContent 切换 ✓ 图标，2 秒恢复）
  - 底部代码区域（由调用方通过 `codeContent` slot 注入高亮内容）
  - 整体 `NexaraShapes.medium` 圆角，背景 `NexaraColors.SurfaceLowest`，Header 背景 `NexaraColors.SurfaceContainer`
- **MarkdownText 接入**: 注入自定义 `codeFence` / `codeBlock` 组件
  - 使用 `MarkdownCodeFence` / `MarkdownCodeBlock` 提取 code/language（block 回调）
  - 包裹 `CodeBlockWithHeader` + `MarkdownHighlightedCode`（语法着色）
- **NexaraMarkdownTheme**: `codeBackground` 改为 `Color.Transparent`，避免与自定义组件双重背景

### 新增文件

| 文件 | 说明 |
|------|------|
| `ui/renderer/CodeBlockHeader.kt` | 自定义代码块容器（语言标签 + 复制按钮 + 代码 slot） |

### 修改文件

| 文件 | 变更 |
|------|------|
| `app/build.gradle.kts` | 添加 markdown-renderer-code 依赖 |
| `ui/common/MarkdownText.kt` | 注入 markdownComponents(codeFence/codeBlock) 自定义渲染 |
| `ui/renderer/NexaraMarkdownTheme.kt` | codeBackground → Color.Transparent |

### 验收

- `compileDebugKotlin` BUILD SUCCESSFUL
- mikepenz 0.40.2 code 模块 API：`MarkdownHighlightedCode(code, language, style, ...)`
- 自定义组件通过 `MarkdownCodeFence`/`MarkdownCodeBlock` 的 `block` 回调获取提取后的 code/language
