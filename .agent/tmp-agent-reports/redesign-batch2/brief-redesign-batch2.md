# 任务书：会话界面视觉重设计（顶栏、消息流、步骤行、审批卡、流式放出与输入区）

## 1. 背景与目标

Nexara 是 Android 原生 AI 客户端，使用 Kotlin、Jetpack Compose 和 Material 3，模块为 `native-ui/app`。当前目录是一个独立的 git worktree。设置页已按同一规格完成重设计，字体与间距令牌（`ui/theme/Type.kt`、`ui/theme/Spacing.kt`）已经就位。

本任务把会话界面改为商业 AI 客户端级别的阅读体验，具体要求是：

- 助手正文通栏无气泡，块间距和行高按阅读排版设定，不出现连续空行造成的大空白；
- 思考、工具、检索统一为无底色的单行步骤行，折叠态只显示自然语言摘要，不暴露工具内部 ID；
- 流式输出按到达速率匀速放出，慢速模型不再整段突现；
- 输入区合并为一个容器：输入框在上，附件、模型、上下文用量与发送按钮在同一工具行。

**权威规格**：`docs/superpowers/specs/2026-10-17-nexara-settings-chat-redesign.md`（下称“规格”）。

- 本任务实现规格第 5 节全部内容、第 6 节中与会话界面相关的条目、第 7 节“批次二”一行、第 8 节中与会话相关的测试，以及第 9.3 节的验收条件。
- 规格中的尺寸、字号、颜色角色和文案都是硬性要求。规格与本任务书冲突时以本任务书为准。

## 2. 已验证的现状

所有路径相对于 `native-ui/app/src/main/java/com/promenar/nexara/`。行号可能已轻微漂移，以 grep 结果为准。

### 2.1 `ui/chat/ChatScreen.kt`

| 位置 | 现状 |
| --- | --- |
| 约 :489 | 消息 `LazyColumn`；约 :504 `contentPadding` 左右 16dp，`verticalArrangement = spacedBy(NexaraSpacing.Medium)`（12dp） |
| 约 :640 | 输入区外层 `Surface`，内边距 16/8 |
| 约 :898 | `ContextCircularIndicator`（上下文圆环） |
| 约 :925 | `ChatInputTopBar`：一个 `FlowRow`，含模型 `AssistChip`（`onModelClick = actions.onOpenSettings`）、`TokenIndicator`、任务胶囊、`PostProcessChips` |
| 约 :1047 | `TokenIndicator`：`AssistChip` + `DropdownMenu` 承载 `ContextUsagePanel` |
| 约 :1277 | `ChatTopBar`：`TopAppBar`，标题为可点击重命名的 `Column`，内含会话名、`Edit` 铅笔图标和智能体名副标题；右侧 `Folder` 与 `MoreVert` 菜单（会话设置、会话提示词、清空历史、重命名、删除） |
| 约 :1560 | `ChatInputBar`：`Box`，`shape = extraLarge`，底色 `surfaceContainerHighest` 45% 透明度；“+” `IconButton`；`BasicTextField` 使用 `bodyMedium`，`enabled = !isGenerating` |
| 约 :1698 | `GenerationStatusButton`（发送 / 停止 / 错误态） |

### 2.2 `ui/chat/PipelineBubble.kt`

| 位置 | 现状 |
| --- | --- |
| 约 :136 | `PipelineBubble`：透明 `Surface`，步骤间距常量 `ChatStepSpacing` 6dp |
| 约 :528 | `ThinkingTrace`（思考行与展开内容） |
| 约 :593、:779 | 两处 `MarkdownText(` 调用（思考展开内容与正文片段） |
| 约 :609 | `AssistantMetadataRow`（模型名与时间元信息） |
| 约 :653 | `InlineToolRow`（工具组行） |
| 约 :750 | `ContentSegment`：正文片段，长按打开 `MessageContextMenu` |
| 约 :845 | `UserMessageBubble` |
| 约 :1079 | `StreamingCursor` |
| 约 :1111 | `MessageContextMenu`（时间、复制、编辑、分支、删除等） |

### 2.3 其它会话文件

- **`ui/chat/CompactStepRow.kt`** 约 :69：步骤行组件。背景 `surfaceContainerLow` 38% 透明度，可见高 32dp、触控高 48dp；进行中的思考行使用 `fadingReasoningTail` 渐隐推理预览。
- **`ui/chat/ChatInlineComponents.kt`**（约 1147 行）：
  - 约 :346 检索指示行，调用 `CompactStepRow`，标签 `UiTags.RAG_PROGRESS_CARD`，摘要由 `ragCardSummary`、`kgSummarySegments` 等拼成，点击打开 `RagDetailsSheet`；
  - 约 :835 `localizeApprovalPreviewDetail`、:851 `ApprovalTextBlock`、:873 `ApprovalCard`（参数：`toolName`、`description`、`calls`、`isExecuted`、`executionTime`、`enabled`、`onApprove`、`onDecline`、`onApproveAll`、`onAlwaysAllow`；当前用 `accentColor` 画左侧强调色）。
- **`ui/chat/ApprovalPreview.kt`**（101 行）：审批预览的数据整理。
- **`ui/chat/ToolInvocationPresentation.kt`**：现有 `inferredToolName`、`toolInvocations`、`toolGroupDurationMs`、`toolDurationText`。
- **`ui/chat/manager/MessageManager.kt`**：流式写入 UI 的节流 `UI_THROTTLE_MS = 100`，数据库防抖 `DB_DEBOUNCE_MS = 500`。即正文以约 100ms 一块的粒度到达界面。

### 2.4 Markdown 渲染

- **`ui/common/SmoothStreamContent.kt`**：`enum class StreamSpeed`（FAST、BALANCED 均为 6000 cps，SMOOTH 60 cps）；`reduceSmoothStreamContent`；`rememberSmoothStreamContent(…, cps = StreamSpeed.BALANCED.cps)` 以 16ms `delay` 循环推进。6000 cps 实际等于立即显示，所以 100ms 一块的到达粒度直接表现为整段突现。全项目只有 `MarkdownText` 调用它。
- **`ui/common/MarkdownText.kt`**：
  - 约 :128 `STREAM_TAIL_FADE_DURATION_MS = 220`；约 :153 `streamingTailFade`，在末段底部叠加 36dp 渐变遮罩（`BlendMode.DstIn`），约 :512 应用；
  - 约 :347 `MarkdownText(…, smoothingCps = StreamSpeed.BALANCED.cps)`，约 :367 调用 `rememberSmoothStreamContent`；
  - 外层 `Column` 使用 `spacedBy(8dp)`（紧凑模式 4dp）；
  - `markdownPadding`：block 12、list 8、listItem 4、listIndent 12；
  - 约 :850 `markdownAnnotator(config = markdownAnnotatorConfig(eolAsNewLine = true))`。
- **`ui/renderer/NexaraMarkdownTheme.kt`**：基础字号默认 13；段落与列表行高 ×1.6，引用 ×1.5，代码 ×1.4；标题 h1 1.2、h2 1.15、h3/h4 1.1，行高 ×1.4。
- 渲染库为 `com.mikepenz:multiplatform-markdown-renderer`（含 `-m3`、`-code`、`-coil3`）0.41.0。Gradle 缓存 `~/.gradle/caches/modules-2/files-2.1/com.mikepenz/` 下只有二进制 AAR/JAR、没有 sources jar；核实 API 时用 `javap` 读取缓存中的类签名，或从 Maven Central 获取同版本 sources jar 仅用于阅读。

### 2.5 字符串与数据

- 现有中文字符串：`chat_thought_duration`“已思考 %1$d 秒”、`chat_thinking_elapsed`“%1$d 秒”、`chat_status_thinking`“思考中...”、`chat_tools_count`“调用了 %1$d 个工具”、`chat_tool_running` / `chat_tool_completed` / `chat_tool_failed` / `chat_tool_pending`、`chat_tool_arguments`“调用参数：%1$s”、`chat_tool_default_name`“工具”。
- 会话字号设置默认 15（`data/model/ChatModels.kt` 约 :368）。

### 2.6 既有测试

- **JVM 测试**（`app/src/test/java/com/promenar/nexara/`）：`ui/chat/` 下 `CompactStepPresentationTest`、`PipelineBubbleTest`、`ToolInvocationPresentationTest`、`ChatComposerInsetsTest`、`ChatRenderStateContractTest`、`ApprovalPreviewTest`、`ApprovalPreviewLocalizationTest`、`RagDetailsDesignContractTest` 等；`ui/common/` 下 `SmoothStreamContentTest`、`MarkdownTextTest`、`MarkdownWideContentContractTest`、`InlineLatexPipelineBreakTest` 等。其中部分是读取源码字符串的契约测试。
- **设备测试**（`app/src/androidTest/java/com/promenar/nexara/ui/chat/`）：`ApprovalCardTagsTest`、`ThinkingTraceTest`、`ChatScreenContentStateTest`、`ChatImeInteractionTest`、`AttachmentActionMenuTest`、`ChatProviderSwitchSurfaceTest`、`RagDetailsAccessibilityTest` 等。
- **截图测试**（`app/src/screenshotTest/kotlin/com/promenar/nexara/ui/chat/`）：`CompactStepsScreenshotTest`、`ContextCompactionScreenshotTest`、`SessionSettingsBatch1ScreenshotTest`；另有 `ReleasePreviewScreenshotTest`。基线位于 `app/src/screenshotTestDebug/reference/`。
- Robolectric 在本项目中无法运行 Compose UI 测试（资源与 PackageParser 问题），Compose 布局断言写在 `androidTest`。

## 3. 需求

逐条实现规格第 5.1–5.8 节。以下是落实时的硬性补充。

### 3.1 顶栏（规格 5.1）

- 用 `TopAppBar`（或等价自定义 Row）实现 56dp、透明背景。标题只保留会话名一行，`titleMedium`，`maxLines = 1`、截断；点击标题仍触发重命名。
- 删除标题旁的 `Edit` 图标和智能体名副标题，并删除因此失效的字符串与参数。
- 文件图标 `Icons.Outlined.Folder` 22dp，更多菜单项与顺序不变。
- 分隔线根据 `LazyListState` 是否离开顶部（`firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0`）用 150ms 透明度动画淡入淡出，0.5dp `outlineVariant`。

### 3.2 消息流（规格 5.2）

- `LazyColumn` 删除 `spacedBy`；左右 20dp；内容宽度 `widthIn(max = 720.dp)` 并水平居中（横屏和平板宽度下生效）。
- 垂直节奏由各消息项自身的 padding 实现：用户消息之后到助手内容 20dp；助手内容（含操作行）到下一条用户消息 28dp；首条消息距顶栏 12dp；末条内容距输入区 16dp。
- 新增一个纯函数（例如 `chatItemSpacing(previous, current)`）计算相邻项间距，并为它写 JVM 单测，覆盖用户→助手、助手→用户、连续助手片段、首项、末项。

### 3.3 用户消息（规格 5.3）

- 气泡 `surfaceContainerHigh`、圆角 20dp、内边距左右 16dp / 上下 10dp，最大宽度为消息流可用宽度的 82%，右对齐。
- 文字 `bodyLarge` 基础上字号 = 会话字号，行高 = 字号 × 1.5，阅读换行（`NexaraReadingLineBreak`）。
- 气泡下方不显示时间；长按菜单保持现有项（时间、复制、编辑、分支、删除）。
- 附件缩略图位于气泡上方、右对齐、间距 6dp。

### 3.4 助手正文（规格 5.4）

- 正文无气泡、无底色、无描边。
- 修改 `NexaraMarkdownTheme` 与 `MarkdownText`，使：
  - 正文字号 = 会话字号（默认 15sp），行高 ×1.6，`onSurface`；
  - 标题 h1 1.35×、h2 1.2×、h3 1.1×（h4 及以下 1.0×），SemiBold，行高 ×1.35；
  - 块间距按规格 5.4 节：段落 10dp；标题上 20dp、下 8dp；列表与相邻段落 8dp、列表项 4dp、嵌套缩进 16dp；代码块、表格、公式、图表上下 12dp；引用左侧 3dp `outlineVariant` 竖线、左内边距 12dp。这些数值是**截图实测**的文字间距：行高带来的上下留白要计入，`MarkdownText` 外层 `spacedBy` 与 `markdownPadding` 不得叠加出更大的间距。
  - 代码块圆角 12dp、`surfaceContainerLow`；头部 36dp，左侧语言名 `labelSmall`，右侧复制、编辑图标 18dp；正文等宽 13sp/20sp。
  - 行内代码 `surfaceContainerHigh`，圆角 4dp，左右内边距 4dp。
- **空行折叠**：新增纯函数，在代码围栏外把连续 3 个及以上换行折叠为 2 个。必须保护围栏代码块、缩进代码块、行内代码和链接中的内容；表格内行不受影响。只处理“文本已有多余换行”这一类问题，不改动换行缺失的修复逻辑。
- **操作行**：生成完成后在正文下方 8dp 显示，高 36dp。
  - 左侧三个图标按钮：复制、重新生成、更多（菜单含分支、删除），图标 18dp、触控 48dp、`onSurfaceVariant`，都有 `contentDescription`；
  - 右侧“模型名 · HH:mm”，`labelSmall`、60% 透明度，模型名超长截断；
  - 生成中不显示操作行；
  - 保留 `CHAT_ASSISTANT_MODEL_METADATA`、`CHAT_ASSISTANT_TIME_METADATA` 标签（分别挂在模型名与时间文字上）；
  - 复制、重新生成、分支、删除调用现有回调，不新增业务逻辑。若某消息在当前状态下原本不能重新生成或分支，对应按钮隐藏或禁用，与现有长按菜单的可用性一致。
- 长按正文仍打开现有 `MessageContextMenu`。

### 3.5 步骤行（规格 5.5）

- 重写 `CompactStepRow`：无底色、无边框、无圆角容器；单行；布局高 44dp、整行宽度可点击；文字 13sp/18sp、Normal、`onSurfaceVariant` 85%；尾部 16dp `ChevronRight`，展开时旋转 90°（150ms）。
- 成功完成不显示前导图标；进行中显示 12dp、1.5dp 线宽的进度圈；失败在摘要后追加“· N 个失败”，该段 `error` 色。
- 相邻步骤行之间 0dp；步骤行与正文之间 8dp。`ChatStepSpacing` 改为符合这一节奏的取值或删除。
- 进行中的思考行尾部保留 `fadingReasoningTail` 单行推理预览，去掉底色。
- **文案**（中英文全部写入资源，数量相关的用 `plurals`）：
  - 思考进行中“正在思考 · 6 秒” / “Thinking · 6s”；完成“思考了 6 秒” / “Thought for 6s”。
  - 工具按规格 5.5 节的类别表生成，例如“运行了 2 条命令 · 2.3 秒”、“正在搜索网页…”、“运行了 2 条命令 · 1 个失败”。
- **`toolGroupSummary(invocations)`**：在 `ToolInvocationPresentation.kt` 中新增纯函数，返回与 Compose 解耦的结构化结果（类别、数量、状态、失败数、耗时），再由 Composable 映射为资源字符串。规则：
  - 工具 ID 到类别的映射严格按规格 5.5 节的表格；MCP 工具和未知 ID 归入“其它”；
  - 一组含 1 个类别时用该类别文案；2 个类别时按首次出现顺序用“，”连接；3 个及以上类别用“调用了 N 个工具”；
  - 进行中时按最后一个未完成调用的类别使用进行时文案；
  - 检索类别中 `knowledge_search` 与 `memory_search` 分别对应“检索了知识库”与“检索了记忆”；
  - 折叠态任何位置不得出现工具内部 ID 或参数。
- **展开内容**：左缩进 0（与正文左缘对齐），调用之间 12dp。
  - 每个调用的标题行：友好名称（`bodySmall`、`onSurface`）+ 内部 ID（等宽 11sp、`onSurfaceVariant` 60%）+ 耗时；友好名称可复用现有映射，没有映射时用类别名；
  - 参数块：等宽 12sp/18sp，`surfaceContainerLow`、圆角 8dp、内边距 10dp，默认最多 6 行，超出时显示“展开全部”文字按钮；
  - 结果：`bodySmall`，默认最多 12 行，超出时“展开全部”；错误结果 `error` 色；图片结果缩略图保持现状；
  - 思考展开：推理正文 `bodySmall` 阅读换行、`onSurfaceVariant`，左侧 2dp `outlineVariant` 竖线、左内边距 12dp。
- **检索行**（`ChatInlineComponents.kt` 约 :346）：改用新步骤行样式，摘要改为自然语言（“检索了知识库 · 5 条结果”“未能检索知识库”“正在检索知识库…”），保留 `UiTags.RAG_PROGRESS_CARD` 与点击打开 `RagDetailsSheet` 的行为。

### 3.6 审批卡片（规格 5.6）

- 容器 `surfaceContainerLow`、圆角 16dp、内边距 16dp；删除左侧强调竖条、描边和 `accentColor` 的强调用法。
- 标题行：16dp 警示图标（`error` 色）+“需要审批”`titleSmall`。已执行状态沿用现有文案，图标改为中性色。
- 内容：友好工具名 + 执行目标（`bodyMedium`）；风险说明为 `bodySmall`、`error` 色文字，不使用任何红色填充块；参数块样式同 3.5 节参数块。
- 按钮行右对齐：“拒绝”`OutlinedButton`，“批准”为唯一实心主按钮，高 40dp；“本会话始终允许”为下方 `TextButton`；“全部批准”（`onApproveAll`）存在时改为 `TextButton`，与“本会话始终允许”同一行或相邻。
- 审批逻辑、回调和 `ApprovalCardTagsTest` 依赖的标签全部保持。

### 3.7 流式放出（规格 5.7）

- 在 `ui/common/` 新增纯 Kotlin 状态机 `StreamRevealer`，不依赖 Compose 与 Android 类，时间由调用方以纳秒或毫秒参数注入。按规格 5.7 节算法实现全部 8 条，其中：
  - 速率估计用指数移动平均，α = 0.3，以“本次增量字符数 / 距上次增长的间隔”为样本；
  - 每帧 `v = clamp(max(rate, backlog / 0.5s), 30, 2000)` 字符/秒；
  - 流结束后 `v` 不低于 `backlog / 0.3s`（同时不低于 30），保证 300ms 内完整；
  - 截断位置按字形边界对齐（`java.text.BreakIterator.getCharacterInstance()` 或等效实现），不得拆分代理对、组合字符和 emoji 序列；
  - 积压超过 4000 字符时直接跳到 `target.length - 200`；
  - 目标文本不是已显示文本的前缀时，`shown` 回退到公共前缀长度（同样对齐字形边界）。
- Compose 侧用 `withFrameNanos` 驱动，替换 `rememberSmoothStreamContent` 的 16ms `delay` 循环。以下情况直接显示全文：非流式消息、系统动画缩放为 0（读取 `Settings.Global.ANIMATOR_DURATION_SCALE`）、`LocalInspectionMode` 为真。
- `StreamSpeed` 与 `smoothingCps` 参数如已无调用方，连同 `reduceSmoothStreamContent` 一起删除，并同步改写 `SmoothStreamContentTest`。
- **新文字渐显**：
  - 首选方案：利用 0.41.0 `markdownAnnotator` 的注解回调，按 AST 节点的源偏移为末段中新近放出的文本追加透明度样式，分 3 档（距放出 <60ms：30%，<120ms：65%，其余不透明），只作用于最后一个 Markdown 片段，并且不得导致每帧重新解析整段 Markdown（解析结果按文本缓存，渐显只改注解）。实施前用 `javap` 或 sources jar 核实回调签名，在报告中贴出核实到的签名。
  - 若该回调无法拿到节点源偏移、或会导致每帧重新解析，则不做逐字渐显：删除 `streamingTailFade` 的 36dp 底部渐变遮罩与相关常量，只保留匀速放出，并在报告中说明不可行的具体原因（附签名证据）。
  - 两种方案都必须删除 36dp 底部渐变遮罩。
- `StreamingCursor` 保留或删除由视觉判断决定：若保留，不得在每帧改变布局尺寸。

### 3.8 输入区（规格 5.8）

- 删除 `ChatInputTopBar` 行。模型胶囊、上下文用量移入输入区工具行；任务胶囊与后处理胶囊移到输入区容器上方 8dp 的状态条，高 28dp，仅在存在任务或后处理时出现，内容横向排列、不换行（超出时可横向滚动，不得嵌套在第三方组件回调里叠加 `horizontalScroll`）。
- 容器：距屏幕左右 12dp，距导航栏或输入法上沿 8dp；`surfaceContainerHigh` 不透明、圆角 24dp、内边距左右 12dp / 上 12dp / 下 8dp；无阴影、无描边。现有 IME 与导航栏 inset 处理（`ChatComposerInsetsTest` 覆盖的逻辑）保持。
- 自上而下：
  1. 附件条（有附件时）：缩略图 56dp、圆角 12dp、间距 8dp，删除按钮保持；
  2. 输入框：`bodyLarge`，最少 1 行、最多 6 行，之后内部滚动；占位文字 `onSurfaceVariant`；
  3. 工具行：高 40dp，与输入框间距 4dp，任何字号下不换行。
- 工具行从左到右：
  - “+”附件按钮：40dp 圆形可见区、22dp 图标，附件菜单展开时图标旋转 45°；
  - 模型胶囊：可见高 32dp、触控高 40dp、圆角 16dp、透明底；16dp 品牌图标（复用现有品牌解析）+ 模型名 `labelLarge`（最大宽 160dp，单行截断）+ 16dp `ExpandMore`；点击打开会话设置弹层并定位到“模型”标签（若现有入口不支持指定标签，为会话设置弹层增加初始标签参数，默认值保持现有行为）；
  - 弹性空白；
  - 上下文用量：18dp 圆环（2dp 线宽，超过自动压缩阈值时 `tertiary`，否则 `primary`）+ 百分比 `labelMedium`、tnum；整体可点击，弹出现有 `ContextUsagePanel`；
  - 发送按钮：40dp 实心圆。可发送：`primary` 底 + `onPrimary` 20dp 向上箭头；不可发送：`surfaceContainerHighest` 底 + 38% 透明图标；生成中：`primary` 底 + 14dp 圆角方块停止图标；错误态保持现有语义与颜色。
- 生成期间输入框的启用状态、发送与停止逻辑、附件逻辑全部保持现状。
- 保留并挂到语义等价元素上的标签：`CHAT_COMPOSER`、`CHAT_INPUT_BAR_SURFACE`、`CHAT_INPUT`、`CHAT_ADD_ATTACHMENT`、`CHAT_MODEL_SELECTOR`、`CHAT_TOKEN_INDICATOR`、`CHAT_TASK_CAPSULE`，以及当前代码中其余所有 `UiTags` 与 testTag。

### 3.9 无障碍与适配（规格 6）

- 图标按钮都有 `contentDescription`；步骤行提供展开/收起的 `stateDescription`。
- 系统字体 130% 时，输入区工具行不换行（模型名先截断），步骤行允许增高但不裁切文字。
- 例外清单：步骤行 44dp（整行宽度可点击）、模型胶囊可见高 32dp（触控 40dp）。除此之外触控区不小于 48dp。

### 3.10 测试

- **JVM 单测**：
  - `StreamRevealer`：规格第 8 节 6 个场景，全部使用模拟时钟与 16.67ms 帧。场景 1 的判据：每 100ms 到达 5 个字符，稳态（前 500ms 之后）每帧放出 0–2 个字符，不出现单帧放出超过中位数 3 倍的跳变，且积压非零时无连续 6 帧以上停顿。另加：非流式直接全文、流结束后不再增长时稳定在全文。
  - `toolGroupSummary`：每个类别至少一例、混合 2 类、混合 3 类、进行中取最后未完成调用的类别、失败计数、未知与 MCP 工具、空列表。
  - 空行折叠：普通段落、围栏代码内保留多空行、缩进代码内保留、行内代码与链接内容不变、表格不变、CRLF 输入。
  - 消息间距函数（见 3.2）。
  - 既有源码字符串契约测试按新契约改写为断言当前实现的有意义片段；只验证已废弃写法的断言删除，并在报告中逐条列出。
- **设备布局测试**：在 `androidTest` 新增 `ChatLayoutTest`（或按组件拆分），用 `createComposeRule` 直接渲染组件，断言（容差 0.5dp）：
  1. 步骤行高 44dp，尾部箭头与文字中心线偏差 ≤1dp；折叠态文字不包含任何工具内部 ID；
  2. 输入区工具行高 40dp，发送按钮 40dp，“+”按钮可见区 40dp；1.3 倍字号下工具行仍为单行；
  3. 用户气泡宽度不超过可用宽度的 82%；
  4. 助手操作行高 36dp，三个图标按钮触控区 ≥48dp，生成中不存在操作行；
  5. 顶栏不存在智能体名副标题与铅笔图标，标题单行；
  6. 审批卡片只有一个实心按钮（“批准”），不存在红色填充块。
  - 既有设备测试（第 2.6 节所列）中的断言保持可满足；若断言针对规格明确删除的结构（例如铅笔图标、`ChatInputTopBar` 行），改写为验证原用户意图并在报告中逐条说明。
- **截图测试**：新增或更新组件级截图，全部为深色、中文、宽 411dp，另为会话页面级预览加浅色与英文各一张：
  - 顶栏（短标题、超长标题）；
  - 用户消息（单行、多行、带附件）；
  - 助手正文长回复（含 h1–h3、段落、有序/无序/嵌套列表、代码块、表格、引用、行内代码、含多余空行的原文）；
  - 助手操作行（完成态）；
  - 步骤行：思考进行中、思考完成、工具进行中、工具完成（单类别、两类别、三类别）、工具失败、工具展开（含超长参数与结果）、思考展开、检索完成、检索失败；
  - 审批卡片（单个调用、多个调用含“全部批准”、已执行）；
  - 输入区：空、有文字、6 行以上、生成中、有图片与文档附件、存在任务状态条、1.3 倍字号；
  - 完整会话页：一问一答含步骤行与操作行。
- **截图基线**：用 `updateSideloadDebugScreenshotTest` / `updatePlayDebugScreenshotTest` 更新后，逐张目视检查新基线，对照规格第 9.3 节逐项自检。报告中列出新增、更新、删除的基线文件名。

## 4. 设计判断原则

规格未覆盖的情况按以下原则处理，并在报告中写明判断：

1. 阅读舒适优先：正文的字号、行高和块间距优先于信息密度。
2. 折叠态只放用户能理解的自然语言，技术细节进入展开态。
3. 会话页的实心按钮只有发送按钮和审批卡的“批准”，不新增其它实心或色调按钮。
4. 不确定的业务行为一律保持原样，不得为了视觉效果改变生成、审批、压缩、附件或会话管理逻辑。

## 5. 边界

- **文件范围**：
  - 只修改本 worktree 内的文件；
  - 生产代码只修改规格第 7 节“批次二”所列文件、为实现所需的新增文件、相关中英文字符串资源，以及为“模型”标签定位所需的 `ui/chat/SessionSettingsSheet.kt` 最小改动；
  - 设置页组件（`ui/common/` 中的设置行、开关、分组、步进器、值行、选择弹层）与 `ui/settings/`、`ui/rag/`、`ui/hub/` 不修改；
  - 不得读取或修改 `secure_env/`、`artifacts/`、`.v2c/`、`.mimosa/`、`*.jks`、`*.keystore`、`native-ui/local.properties`。
- **依赖与数据**：不新增依赖；不修改 Room schema、偏好键、默认值、`.pdec/`；`MessageManager` 的节流与防抖常量不修改。
- **测试标签**：保留所有 `UiTags` 和 testTag；元素被替换时，把标签迁移到语义等价的新元素上。
- **git 与设备**：不运行 git commit / push；不追加 `.agent/handover.md`；不启动或操作模拟器与设备。
- **Gradle**：不得执行 `./gradlew --stop`，不得结束其它 Gradle 或 Kotlin 守护进程。
- **Compose 红线**：网络与 IO 只在 `Dispatchers.IO`；不在第三方组件回调中叠加 `horizontalScroll()` / `verticalScroll()`；流式放出不得引起每帧整段 Markdown 重新解析。
- **注释**：新增注释使用简体中文，数量与周边代码相称；不保留描述修改过程的注释。

## 6. 验收

在 `native-ui/` 下运行，全部通过：

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:lintSideloadDebug
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:validateSideloadDebugScreenshotTest :app:validatePlayDebugScreenshotTest
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:assembleSideloadDebug :app:assemblePlayDebug
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" -Pnexara.deviceE2e=true -Pnexara.deviceE2eAbi=arm64-v8a :app:assembleSideloadDeviceTest :app:assembleSideloadDeviceTestAndroidTest
```

在仓库根运行，全部通过：

```bash
python3 scripts/ci/check-text-wrapping.py
python3 scripts/ci/check-distribution-manifests.py
```

全部完成后执行静态核查，并把输出写入报告：

```bash
grep -rn "streamingTailFade\|STREAM_TAIL_FADE_DURATION_MS\|ChatInputTopBar\|Icons.*Edit" native-ui/app/src/main/java/com/promenar/nexara/ui/chat native-ui/app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt
grep -rn "spacedBy" native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt
```

第一条结果中不得出现 `streamingTailFade`、`STREAM_TAIL_FADE_DURATION_MS`、`ChatInputTopBar`，以及顶栏标题旁的 `Edit` 图标（其它业务中的编辑图标可以保留，需在报告中说明各自用途）；第二条结果中不得出现消息 `LazyColumn` 的 `spacedBy`。

DIA：在 `CHANGELOG.md` 的 `[Unreleased]` 下新增“会话界面视觉重设计”条目，用终态描述写清用户可见变化。检查 `README.md`、`design-qa.md` 和 `docs/` 中描述会话界面视觉、流式输出或输入区形式的段落，与当前实现不一致的同步修改；规格文件顶部状态行改为“批次一、批次二均已实施”。在报告中列出改动的文档。

## 7. 报告

完成后写入 `.agent/tmp-agent-reports/report-redesign-batch2.md`，包含以下内容：

- **组件落点**：新增和重写的组件及其职责，引用 `文件:行号`。
- **逐条对照**：规格 5.1–5.8 节每一条要求的实现位置（`文件:行号`）；未实现或偏离的写明原因。
- **Markdown 块间距实测**：说明如何保证截图实测间距符合 5.4 节（主题取值、padding、行高留白的计算），并给出一张截图中至少 4 处间距的像素实测（1dp = 420/160 px 换算或截图测试的实际密度）。
- **`markdownAnnotator` 核实**：贴出核实到的签名与所选方案。
- **`toolGroupSummary` 映射表与文案清单**（中英文、plurals）。
- **测试清单与结果**：新增 JVM 单测、设备布局测试、改写或删除的契约测试逐条说明。
- **截图基线清单**：新增、更新、删除的基线文件名，并按规格 9.3 节逐项写自检结论。
- **无障碍例外清单**。
- **第 6 节各条命令的结果**：给出最后几行输出与静态核查输出。
- **未完成项、偏离规格的地方及原因、已知限制**。
