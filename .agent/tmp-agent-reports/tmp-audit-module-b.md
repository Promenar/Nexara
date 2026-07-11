# Nexara 输出渲染链路审计报告（模块 B）

> 审计范围：从 API 流式返回 → 用户看到渲染结果的完整链路（SSE 累积 / 增量渲染 / Markdown / 多模态 / 思考 / 工具 / 错误 / 滚动）。
> 审计方式：只读代码审查，未修改任何文件。
> 报告时间：2026-07-05。

---

## 一、严重问题（直接破坏可用性 / 视觉体验 / 稳定性）

### B-S1. 思考过程在流式生成期间无法实时更新（用户看到的是"思考已停止"的假象）

**文件**：`ui/chat/PipelineBubble.kt:148-159`、`ui/chat/ChatViewModel.kt:518-523`

**用户视角**：当模型边推理边输出时，"正在思考" 区域的脉冲圆点会一直闪烁，但**里面的文字完全不动**——用户以为模型卡死了。实际上推理 token 正在流入 `msg.reasoning`（ViewModel 通过 `UpdateMessageOptions(reasoning=...)` 实时写库），但因为：

- `PipelineStep.Thinking` 在 `buildPipelineSteps` 里是**一次性快照**（`PipelineBubble.kt:341-342` 从 `msg.reasoning` 取值），`remember(group.messages)` 在消息对象引用变化时会重建，但 `InlineThinkingRow` 的"流式"判定（`PipelineBubble.kt:152`）要求 `streamingContent.isEmpty()`——而 `streamingContent` 只累积**正文 content**，从不包含 reasoning。
- 即使 `isThinkingStreaming` 为 true，`InlineThinkingRow` 把 `isGenerating` 透传给 `MarkdownText(isStreaming = isGenerating)`（`:494`），但由于 `streamingContent` 不含 reasoning，`MarkdownText` 拿到的 `markdown` 仍是上次快照，smooth 引擎无新增内容可推进 → **打字机不动**。

**后果**：深度思考模型的"思考流"视觉上完全静止，与实际 token 速率严重脱节，用户误判为掉线。

---

### B-S2. `StreamingCursor` / `MarkdownSafe` 的 `ParseCache` 在气泡间串味，导致历史消息渲染残留前一条流式片段

**文件**：`ui/common/MarkdownText.kt:218-253`、`ui/chat/ChatScreen.kt:305-342`

**用户视角**：在长会话里滚动 / 切换会话时，偶尔会看到**某条历史消息末尾闪烁着上一条流式消息的代码块或 Mermaid 图**，或代码块渲染异常（语言高亮丢失），持续到手动触发该消息重组。

**根因**：`ParseCache` 是 `remember { ParseCache() }`（**无 key**，`:218`）。LazyColumn 复用 item 槽位时，同一 Composable 实例被绑定到新的 `markdown`，但 `cache.text` 仍是旧内容。`:220-222` 的增量判定 `smoothed.startsWith(cache.text)` 会拿旧缓存和新内容做前缀比对：
- 若新内容恰好以旧缓存为前缀（短消息常见）→ 错误地走"增量合并"分支，把新内容拼到旧 segment 列表后；
- 否则回退到全量解析（`:248`），但 `cache.text` 已被污染，下一次又可能命中错误分支。

**正确做法**：`remember(markdown)` 或 `remember(content)` 把缓存绑定到内容标识，而非 Composable 生命周期。

---

### B-S3. 代码块无折叠 / 无高度上限，500 行代码占满整屏且无法快速跳过

**文件**：`ui/renderer/CodeBlockHeader.kt:88-232`、`ui/common/MarkdownText.kt:540-590`

**用户视角**：模型输出一个 500 行的代码块时，用户看到的是**从头到尾铺满屏幕的代码**，没有"展开/折叠"、没有最大高度、没有内部滚动——必须大量手动滑动才能看到代码之后的正文。长会话里多个大代码块会让整页变得不可导航。

**根因**：`CodeBlockWithHeader` 的非编辑态（`:190-221`）直接用 `Column { lines.forEach { Text } }` 渲染所有行号 + 内容，外层 `Row` 没有 `verticalScroll`，没有 `heightIn(max = ...)`。代码内容区域唯一的高度约束是编辑态的 `heightIn(min = 100.dp)`（`:176`），非编辑态完全没有上限。

**附加问题**：行号列 `Column` 与内容 `Box` 之间用 `Box.fillMaxHeight()`（`:207-213`）画分隔线，但外层 `Row` 本身高度由内容撑开——当代码很长时 `fillMaxHeight` 会拉到数千 px，分隔线正常；但代码块在 `LazyColumn` item 里作为整体测量，超长内容会触发 Compose 的"测量约束超限"警告或 jank。

---

### B-S4. 表格单元格错位：列数不匹配时直接渲染会导致行列错乱

**文件**：`ui/renderer/TableWidget.kt:114-142`

**用户视角**：当模型生成的表格**某行单元格数量与表头不一致**（GFM 规范允许，应按缺失/多余对齐处理）时，用户看到表格**列边界完全错位**——表头 3 列但某行有 5 个单元格，多余单元格会挤到下一行或溢出容器，斑马纹背景对不齐，分隔线断开。

**根因**：`NexaraTableWidget` 直接遍历 `table.rows.forEachIndexed { row.forEach { TableCell } }`（`:114-134`），既不做列数补齐（缺的填空、多的截断），也不校验 `row.size == headerCells.size`。每一行独立用 `weight`/`width` 分配宽度，行间列宽不一致 → 视觉错位。

**次要 bug**：`:66-68` 在 `BoxWithConstraints` 外面算了 `cellWidth`/`columnCount`/`totalWidth` 三个值却**完全没用**（被 `:76-78` 同名变量 shadow 掉），属于死代码——说明这里经历过重构但残留。

---

### B-S5. `MermaidBlock` / `EChartsBlock` 在流式期间对不完整代码反复渲染，WebView 频繁 reload 闪烁

**文件**：`ui/common/MarkdownText.kt:225-226`、`ui/renderer/RichContentWebView.kt:113-127`

**用户视角**：模型一边输出 Mermaid/ECharts 代码一边渲染时，用户看到**图表区域疯狂闪烁**——一会儿是半截源码、一会儿是渲染失败的错误图、一会儿是空图，每来一个 chunk 都触发一次 WebView 重新 `loadDataWithBaseURL`。

**根因**：`splitRichSegments` 的增量合并逻辑（`MarkdownText.kt:225`）只在新增部分**不含** ``` ``` ``` 或 `$$` 时才走"追加到最后一段"路径；一旦图表代码块正在流入（每个 chunk 都不带这些边界字符，但前面已有未闭合 fence），实际上每次重组都会重新切分整个图表 segment。`RichContentWebView` 虽然用 `lastLoadedHtml != finalHtml`（`:117`）做了去重，但 `buildMermaidHtml`/`buildEChartsHtml` 每次都把最新（不完整）的 code Base64 编码进 HTML → `finalHtml` 每帧不同 → WebView 每帧 reload → 闪烁。

**正确做法**：流式期间对图表代码块应"等闭合 fence 后再渲染"，或对不完整代码做 debounce / 占位（"图表生成中…"），而不是逐字渲染半截语法树。

---

## 二、中等问题（影响体验，但有规避路径或可接受）

### B-M1. Mermaid / ECharts / LaTeX 全部硬编码深色主题，浅色模式下不可读

**文件**：`ui/renderer/MermaidRenderer.kt:265-289`、`ui/renderer/LatexRenderer.kt:42`、`ui/renderer/EChartsRenderer.kt:197`、`ui/renderer/PlantUmlRenderer.kt:42-47`、`ui/renderer/RichContentWebView.kt:88`

**用户视角**：虽然当前 `NexaraTheme`（`ui/theme/Theme.kt:53-59`）无论系统主题都强制用 `DarkColorScheme`，但如果未来支持浅色主题，所有 WebView 渲染的图表/公式会变成**深色背景配深色背景**或**浅色文字配白底**——完全不可读。Mermaid 写死 `theme: 'dark'`、节点填充 `#2A2A2C`、文字 `#E5E1E4`；KaTeX 写死 `color: #E5E1E4`；ECharts 写死 `echarts.init(..., 'dark')`。

**根因**：WebView 内部的 CSS/JS 主题完全绕过 Compose 的 `MaterialTheme`，没有从 `LocalConfiguration`/`isSystemInDarkTheme()` 注入主题变量。

---

### B-M2. ECharts 强制注入 `toolbox` / `dataZoom`，破坏饼图/雷达图等无笛卡尔坐标的图表

**文件**：`ui/renderer/EChartsRenderer.kt:202-211`

**用户视角**：当模型输出一个饼图（pie）或漏斗图（funnel）时，用户看到图表上方**多出一排工具按钮**（saveAsImage/dataView/restore），且底部多出一个**无意义的 dataZoom 滑块**（饼图根本不能缩放），视觉杂乱且交互怪异。

**根因**：`buildEChartsHtml` 无条件覆盖 `option.toolbox` 和 `option.dataZoom`，不判断 `series.type` 是否支持。对饼图/仪表盘/关系图等非笛卡尔图表，dataZoom 会被 ECharts 忽略但 toolbox 仍显示。

---

### B-M3. `RichContentWebViewPool` 池容量仅 3，长消息含多图时会排队/重建 WebView

**文件**：`ui/renderer/RichContentWebViewPool.kt:11`、`ui/renderer/MermaidRenderer.kt:67`、`ui/renderer/LatexRenderer.kt:15`、`ui/renderer/PlantUmlRenderer.kt:15`

**用户视角**：当一条 AI 消息里同时包含多个 Mermaid 图 + 多个 LaTeX 公式 + PlantUML 时，用户看到**图表轮流"蹦"出来**，前面的图先空白再渲染，滑动时还会重新加载。原因是池子只有 3 个 WebView，第 4 个图表 acquire 时池空 → `createWebView` 新建（`:21-23`），而 release 时若池满直接 `destroy()`（`:30-31`）→ 频繁创建/销毁。

**性能影响**：WebView 创建是重操作（~50-100ms + JS 引擎初始化），长消息含 10+ 公式时会出现明显卡顿和内存抖动。

---

### B-M4. `InlineLatexSpan` 在 `update` 回调里重新 new `WebViewClient`，且 `DisposableEffect(Unit)` 生命周期与 latex 内容脱钩

**文件**：`ui/renderer/InlineLatexSpan.kt:43-94`

**用户视角**：行内公式 `$E=mc^2$` 在流式输出或滚动时偶尔**变成空白方块**或**尺寸跳变**（先显示一整行宽的空白，再缩成正确大小）。

**根因**：
1. `update { wv -> wv.webViewClient = object : WebViewClient() {...} }`（`:58-86`）——每次重组都新建一个匿名 `WebViewClient` 实例赋值，导致 `onPageFinished` 回调里捕获的闭包可能拿到过期状态。
2. `DisposableEffect(Unit)`（`:43`）的 key 是 `Unit`，意味着只要这个 Composable 在组合里就不释放 WebView；但 `bitmap`/`contentWidthDp` 用 `remember(latex)` 绑定内容——内容变（流式追加）时 bitmap 不清空，旧图先显示一会再被新图覆盖 → 跳变。
3. 测量依赖 `view.post { ... view.draw(...) }`（`:73-83`），若 WebView 此刻被池回收或尺寸为 0，`Bitmap.createBitmap(viewW, viewH, ...)` 可能抛 `IllegalArgumentException`（宽高 ≤ 0，虽有 `coerceAtLeast(1)` 兜底但裁剪 `createBitmap(full, 0, 0, cropW, cropH)` 在 cropW/cropH 越界时会崩）。

---

### B-M5. 错误信息仅在气泡底部以纯文本展示，无重试入口、无错误类型区分

**文件**：`ui/chat/PipelineBubble.kt:231-241`

**用户视角**：当 API 返回 401（鉴权失败）/ 429（限流）/ 超时 / 网络断开时，用户只在消息气泡下方看到一行**红色小字**（如 `"Generation failed: ..."`)，没有错误图标、没有"重试"按钮、没有错误分类提示。长消息里这条错误可能被滚出视口，用户根本不知道出错了。

**根因**：`PipelineBubble` 末尾用 `Text(text = lastMsg.errorMessage, color = NexaraColors.Error)`（`:234-239`）单行展示，无 `contentDescription`、无 `Icon`、无 `onRegenerate` 联动。`GenerationStatus.ERROR`（`ChatViewModel.kt:595`）只更新底部发送按钮状态，不与气泡错误提示联动。

---

### B-M6. 工具执行结果被硬截断 300 字，长结果静默丢失

**文件**：`ui/chat/PipelineBubble.kt:638`、`ui/chat/PipelineBubble.kt:317`（`buildSyntheticExecutionSteps` 里 `result.take(500)`）

**用户视角**：当工具（如 web_search / 代码执行）返回较长结果时，用户展开工具卡片只看到**前 300 字 + "…"**，无法查看完整结果，也没有"展开全部"入口。对于"模型基于工具结果回答"的场景，用户无法核对模型是否正确使用了工具输出。

**根因**：`InlineToolRow` 的结果展示 `result.content!!.take(300) + "…"`（`:638`）+ `maxLines = 4`（`:645`）双重截断，且不可展开看全文。

---

### B-M7. 自动滚动追踪用 8ms 轮询 `scrollToItem`，与 LazyColumn 测量竞态导致回流抖动

**文件**：`ui/chat/ChatScreen.kt:237-255`

**用户视角**：流式输出时，消息列表会出现**轻微的上下抖动**（尤其在高分屏/120Hz 设备上明显），像是在"争抢"滚动位置——用户手指放上去想停下来时，列表会跳一下才停。

**根因**：`while (isActive) { ... listState.scrollToItem(totalItems - 1); delay(8) }`（`:249-253`）以 125Hz 频率强制滚动到底，与 LazyColumn 自身的测量/布局异步过程竞争。`scrollToItem`（非 animate）是瞬间跳转，每帧都跳会覆盖用户的手势滚动意图。注释自称"120Hz 物理帧率级像素实时追踪"，但 `scrollToItem` 是 item 级别对齐，不是像素级，会产生跳跃感。

---

### B-M8. `repairCompressedMarkdownBoundaries` 对正常 Markdown 也有副作用，误伤合法内容

**文件**：`ui/common/MarkdownText.kt:387-452`

**用户视角**：当模型输出包含合法的、紧贴前文的 Markdown 元素时（例如 `过程如下1. 第一项` 或 `标题### 注意`），用户看到的渲染结果会被**强行插入空行**，段落被打断；或代码块语言标记后紧跟内容时（如 ` ```pythonfun `）被插入换行导致语法高亮失败。

**根因**：`repairCompressedMarkdownBoundaries` 用大量正则（`:404-446`）"修复"被流式压缩的 Markdown 边界，但这些正则没有区分"真的被压缩"和"用户/模型本来就这么写"。例如 `:408` 的标题检测 `[ \t]*(#{1,6})...` 会在 `a### b` 里把 `### ` 强制换行；`:424` 的代码语言嗅探 `(javascript|js|...)(?=(function|fun|...))` 会把 ` ```pythonfun ` 误判（`fun` 命中）。

**影响范围**：仅流式期间触发（`isStreaming` 为真时才走 `sanitizeStreamingMarkdown` → `repairCompressedMarkdownBoundaries`，`:206-209`），但生成的 `processed` 会喂给 smooth 引擎，渲染完成前用户已经看到错误排版。

---

## 三、轻微问题（细节 / 一致性 / 可维护性）

### B-L1. `TableWidget` 的 `minColumnWidth` / `maxColumnWidth` 参数完全未使用

**文件**：`ui/renderer/TableWidget.kt:61-62`

`NexaraTableWidget` 声明了 `minColumnWidth: Dp = 80.dp` 和 `maxColumnWidth: Dp = 200.dp` 两个参数，但实际列宽硬编码为 `columnWidth = 120.dp`（`:76`），参数被忽略。调用方（`MarkdownText.kt:531-538`）也没传值。属于"参数 API 但不生效"的死接口。

---

### B-L2. `insertCjkSpacing` 用 `\u200A`（六分之一空格）做中英文混排间距，部分字体下不可见

**文件**：`ui/common/MarkdownText.kt:631-657`

中英文之间插入 `U+200A` HAIR SPACE。在某些无衬线字体（尤其等宽字体）里 hair space 宽度接近 0，起不到视觉分隔作用；而在有衬线字体里又偏宽。业界惯例多用 `U+00A0` 或直接用 ` `（普通空格）。不影响功能，但排版效果不稳定。

---

### B-L3. `sanitizeStreamingMarkdown` 对奇数个 `$` 的处理会吞掉合法内容

**文件**：`ui/common/MarkdownText.kt:680-685`

当流式 buffer 里有奇数个 `$`（行内公式未闭合）时，`:682-684` 直接 `dropLast(1)` 删掉末尾的 `$`。如果末尾 `$` 实际上是货币符号（如 `价格是 5$`），会被误删；更糟的是 `:688-693` 对奇数个 `$$` 直接 `substring(0, lastIdx)` 截断，会丢掉 `$$` 之后的整段已生成内容。

---

### B-L4. GFM Alert 的强调色是 GitHub 浅色主题色，在深色背景上对比度不足

**文件**：`ui/renderer/GfmAlertBlock.kt:47-53`

`NOTE` 用 `#0969DA`（蓝）、`TIP` 用 `#1A7F37`（绿）、`WARNING` 用 `#9A6700`（暗黄）——这些都是 GitHub **浅色主题**的色值。在 Nexara 的深色背景（`#131315`）上，`#1A7F37`（深绿）和 `#9A6700`（暗黄）的对比度低于 WCAG AA 4.5:1 标准，标题文字读起来费力。

---

### B-L5. 工具执行时间的 `"1.2s"` 是写死的假数据

**文件**：`ui/chat/ChatInlineComponents.kt:817-822`

`TimelineStep` 里 `text = "1.2s"` 旁注 `// In real app, would come from step.duration`。用户看到的每个工具执行时间都是同样的 1.2 秒，误导性强。

---

### B-L6. `ImageLightbox` 的图片图标按钮全部 `contentDescription = null`

**文件**：`ui/renderer/ImageLightbox.kt:124, 148, 159, 168, 180, 190`

放大/旋转/保存/分享/关闭按钮的 `contentDescription` 全为 `null`，无障碍用户无法操作图片查看器。对比之下 `MermaidBlock`/`EChartsBlock` 的按钮都有描述（如 `"Export PNG"`），不一致。

---

### B-L7. `EChartsBlock` 绕过 `RichContentWebView`，自己维护一套独立的 WebView + 测高逻辑

**文件**：`ui/renderer/EChartsRenderer.kt:66-105`

`MermaidBlock`/`LatexBlock`/`PlantUmlBlock` 都用 `RichContentWebView`（统一测高 + 池化 + 去重 load），唯独 `EChartsBlock` 自己 `remember { RichContentWebViewPool.acquire }` + 手写 `onPageFinished` 测高（`:70-81`）+ 直接 `AndroidView`。导致：测高逻辑分叉（`RichContentWebView` 的双重测量 + 100ms 延迟重测这里没有）、去重 load（`lastLoadedHtml`）这里也没有、池化释放逻辑重复。维护成本高且行为不一致。

---

### B-L8. `PipelineBubble` 里的工具状态判定依赖 `step.type == "error"` 字符串，无类型安全

**文件**：`ui/chat/PipelineBubble.kt:522-523`、`ui/chat/PipelineBubble.kt:313`

`hasError = steps.any { it.type == "error" }`、`type == "tool_result" || it.type == "error"` 用裸字符串比较 `ExecutionStep.type`。若上游协议变更错误类型标识（如改为 `"failed"` / `"exception"`），渲染层会静默失效（不显示错误图标和"指令有误"）。应改为枚举或 sealed type。

---

### B-L9. `UserMessageBubble` 的 `widthIn(max = 280.dp)` 在大屏/横屏下过窄

**文件**：`ui/chat/PipelineBubble.kt:785`

用户消息气泡最大宽度写死 280dp。在平板/折叠屏（>600dp 宽）上，用户发的长消息会被挤成很窄的一长条，浪费屏幕空间。应跟随 `WindowWidthSizeClass` 或按比例（如 0.7 × maxWidth）。

---

### B-L10. 思考块 / 工具块的指示器宽度 `fillMaxWidth(0.7f)` 也是硬编码比例

**文件**：`ui/chat/PipelineBubble.kt:418`、`ui/chat/PipelineBubble.kt:535`

"正在思考"/工具名胶囊固定占 70% 宽度。短文本（如 `web_search`）时右侧留大片空白，长工具名（如 `execute_python_code`）时又可能溢出。应根据内容 `wrapContentWidth` 或设合理 maxWidth。

---

## 四、可视化充分性评估

| 阶段 | 用户能否清楚感知？ | 说明 |
|---|---|---|
| **正在思考** | ⚠️ 部分 | 有脉冲圆点 + "正在思考"，但思考文字不动（B-S1），用户误判卡死 |
| **正在调用工具** | ✅ 良好 | 工具名 + 执行态脉冲 + 错误态红叉，但默认折叠、结果截断（B-M6） |
| **正在生成正文** | ✅ 良好 | 有 StreamingCursor 闪烁光标 + smooth 引擎逐字呈现 |
| **正在生成图** | ❌ 差 | Mermaid/ECharts 流式期间疯狂闪烁（B-S5），无"渲染中"占位 |
| **RAG 检索** | ✅ 优秀 | `RagProgressCard` 8 阶段微轨 + 雷达旋转 + 百分比，可视化充分 |
| **错误发生** | ❗ 不足 | 仅底部红字，无图标无重试（B-M5） |

---

## 五、性能风险汇总

| 场景 | 风险 | 文件 |
|---|---|---|
| 10000+ 字超长消息 | `splitRichSegments` 每次重组都跑多个正则 findAll（`MarkdownText.kt:126-150`），复杂度 O(n×m)；`repairCompressedMarkdownBoundaries` 7 个 replace 正则全量扫描 | `MarkdownText.kt:114-169, 387-452` |
| 超大代码块（500+ 行） | 行号 `Column { lines.forEach { Text } }` 每行一个 Text 组件，500 行 = 500 次组合；无虚拟化 | `CodeBlockHeader.kt:195-205` |
| 大量 WebView 图表 | 池仅 3 个，超出的 destroy/recreate | `RichContentWebViewPool.kt:11` |
| InlineLatex 每帧新建 WebViewClient | 重组时 GC 压力 | `InlineLatexSpan.kt:58-86` |
| autoFollow 8ms 轮询 | 125Hz scrollToItem 与布局竞态 | `ChatScreen.kt:249-253` |

---

## 六、滚动嵌套审查

- ✅ `NexaraTableWidget` 的 `horizontalScroll`（`TableWidget.kt:84`）在 `BoxWithConstraints` 内，是顶层横向滚动，未嵌套在其他滚动容器里——合规。
- ✅ `HtmlArtifactsPopup` 的 `verticalScroll`（`HtmlArtifactRenderer.kt:163`）在 Dialog 内独立使用——合规。
- ⚠️ `CodeBlockWithHeader` 非编辑态**没有**滚动（`:190-221`），靠外层 LazyColumn 滚动——不违反嵌套规则，但导致 B-S3 的"代码占满整屏"问题。
- ✅ 未发现在第三方组件回调（如 `onPageFinished`/`detectTapGestures`）里叠加 scroll 的违规。

---

## 七、总结

输出渲染链路整体架构清晰（PipelineBubble 的 Thinking→Tool→Content 线性管道设计合理，RAG 进度可视化是亮点），但在**流式渲染的正确性**（B-S1 思考不更新、B-S2 缓存串味、B-S5 图表闪烁）和**长内容的可导航性**（B-S3 代码无折叠、B-S4 表格错位）上存在直接影响用户体验的严重问题，建议优先处理 B-S1～B-S5。

中等问题集中在**主题硬编码**（B-M1，为未来浅色主题埋雷）和**可视化的人性化**（B-M5 错误提示、B-M6 工具结果截断）。性能上对超长消息和大量 WebView 的处理（第五节）需要做虚拟化或池扩容。
