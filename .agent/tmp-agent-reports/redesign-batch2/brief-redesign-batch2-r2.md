# 任务书：会话界面视觉重设计——复核问题修正

## 1. 背景

Nexara 是 Android 原生 AI 客户端（Kotlin + Jetpack Compose + Material 3，模块 `native-ui/app`）。当前目录是独立 git worktree，工作树中已有会话界面视觉重设计的未提交实现，依据为：

- 规格：`docs/superpowers/specs/2026-10-17-nexara-settings-chat-redesign.md`（下称“规格”）；
- 原任务书：`.agent/tmp-agent-reports/brief-redesign-batch2.md`；
- 实施报告：`.agent/tmp-agent-reports/report-redesign-batch2.md`。

主控在 API 31 模拟器上运行了设备测试，并逐张检查了截图基线，发现下列问题。本任务在现有工作树上修正这些问题，并把第 4 节的规格裁定写入规格文件。原任务书的边界、验收命令与报告要求仍全部有效。

所有源码路径相对于 `native-ui/app/src/main/java/com/promenar/nexara/`，截图路径相对于 `native-ui/app/src/screenshotTestDebug/reference/com/promenar/nexara/ui/chat/`。

## 2. 设备测试失败（API 31 模拟器实测）

主控用当前工作树构建的 `SideloadDeviceTest` 与 `SideloadDeviceTestAndroidTest` APK，运行了 `com.promenar.nexara.ui.chat` 包（95 项）以及 `AccessibilitySmokeTest`、`MarkdownResourceFixtureTest`（10 项，全部通过）。以下 6 项失败，且在实现前的基线上不存在（新增用例视为本批引入）：

| 用例 | 失败信息 |
| --- | --- |
| `ChatImeInteractionTest#lastMessage_remainsVisible_whenImeShows` | 第 117 行：输入法弹出后，最后一条消息 `last-message-unique-9427` 不在可见区域 |
| `ChatImeInteractionTest#openingIme_doesNotStealDeliberateUpScroll` | 第 145 行：`scroll-intent-tail-3108` 不在可见区域 |
| `ChatScreenContentStateTest#approvalState_rendersToolDetailsAndDispatchesApprove` | 第 192 行：审批状态下找不到可见的 `write_file` 文本 |
| `ChatPipelineLayoutTest#stepRowIs44DpAndArrowSharesTextCenter` | 第 61 行：步骤行实际高 44.57dp，期望 44dp（容差 0.5dp） |
| `ChatPipelineLayoutTest#userBubbleUsesAtMost82PercentAndAttachmentsAreAboveWithSixDpGap` | 第 95 行：`assertExists` 失败 |
| `ChatApprovalLayoutTest#approvalActionsHave40DpVisualHeightAnd48DpTouchHeight` | 第 59 行：触控高度低于 47.5dp |

另有 4 项在基线上同样失败，不在本任务范围：`AttachmentActionMenuTest#dismissAnimation_keepsInterceptingTouchesUntilMenuExitCompletes`、`ChatProviderSwitchSurfaceTest#selectingAnotherProviderModel_dispatchesStableCompositeId`、`RagDetailsAccessibilityTest#tabsExposeRoleSelectionAndMinimumTouchTarget`、`ResourceExplorerInteractionTest#phoneWithDoubleFontKeepsPrimaryControlsVisibleAndReachable`。

修正要求：

- 按根因修复生产代码，不得通过放宽断言、删除用例或缩小触控区让测试通过。
- 两个输入法用例保护的是真实用户行为：输入法弹出时最后一条消息必须可见；用户主动上滑后，输入法弹出不得把列表强行拉回底部。先查明是输入区高度、底部留白、`imePadding`/inset 还是滚动锚定逻辑的变化导致，再修复，并在报告中写明根因。
- `write_file` 用例：审批卡展开或详情中必须仍能看到内部工具 ID（规格允许展开态出现内部 ID），或者用例改为断言语义等价的可见内容；若改写用例，必须在报告中说明它仍验证“审批状态渲染工具详情并能派发批准”。
- 步骤行布局高度必须为 44dp（容差 0.5dp），不得由内容撑高。
- 用户气泡用例先确认失败原因（标签缺失、节点被合并还是布局变化），按原意图修复。
- 审批按钮可见高 40dp、触控高至少 48dp，两者都要成立。

## 3. 视觉问题

### 3.1 输入区

截图 `ChatComposerBatch2ScreenshotTestKt/chatComposerTextBatch2Preview_输入文字` 实测：发送按钮可见圆形高 48dp（规格 40dp），单行输入时容器高 112dp，发送圆形底部距容器底只有约 4dp。

- 发送按钮与“+”按钮的可见区严格为 40dp。48dp 触控区不得放大可见背景，也不得撑高工具行；工具行布局高 40dp。
- 单行输入时容器高 = 12 + 输入行高 + 4 + 40 + 8，默认字号下约 86dp。
- “+”图标的可见左缘与输入文字左缘对齐（偏差 ≤1dp）；发送圆形右缘距容器右边 12dp。

### 3.2 助手操作行

截图 `ChatComposerBatch2ScreenshotTestKt/chatPageDarkBatch2Preview_会话完整` 中，复制图标的可见左缘比正文左缘缩进约 15dp。第一个图标的可见左缘必须与正文左缘对齐（偏差 ≤1dp），48dp 触控区保留，可向左超出正文列。

### 3.3 步骤行

截图 `ChatPipelineBatch2ScreenshotTestKt/chatBatch2StepStatesDarkPreview_步骤行 思考与工具各状态`：

- 12dp 进度圈在静止帧中看起来像一个逗号；运行态又因前导进度圈把文字右移 20dp，完成时文字跳回左侧。规格裁定：运行态**不显示前导进度圈**，文字左缘始终与正文对齐；运行态文字使用微光扫过效果（`onSurfaceVariant` 底色上一道 `onSurface` 高光，1.2 秒线性循环）。系统动画缩放为 0 时显示静态文字。
- 进行中的思考行，推理预览当前被推到最右侧，与标签之间留下大段空白。改为：预览紧跟标签之后，间距 12dp，左对齐；预览超出剩余宽度时保留最新文字，在预览左缘做 24dp 渐隐，不与箭头重叠。

### 3.4 “展开全部”

截图 `ChatPipelineBatch2ScreenshotTestKt/chatBatch2ExpandedToolsDarkPreview_工具展开 长参数与结果` 与 `ChatInlineBatch2ScreenshotTestKt/approvalSingleBatch2Preview_单项审批`：“展开全部”因 `TextButton` 默认内边距，比上方内容多缩进 12–28dp；在参数块内还留下约 30dp 的底部空白。

- 改为紧凑文字链接：`labelLarge`、`primary` 色，无水平内边距，文字左缘与所属内容左缘对齐；与上方内容间距 4dp；触控高仍为 48dp，但不撑大所在容器的可见留白（参数块底部内边距仍为 10dp）。

### 3.5 审批卡

截图 `ChatInlineBatch2ScreenshotTestKt/approvalSingleBatch2Preview_单项审批`：

- 参数预览块与卡片同为 `surfaceContainerLow`，看不出容器。卡片内的参数块改用 `surfaceContainerHigh`。
- 信息重复：执行目标 `/notes/meeting.md` 出现两次（“执行目标：…”与“覆盖写入 /notes/meeting.md”），风险出现两段红字（“文件写入”与“此操作会修改目标文件…”）。改为：
  - 第一行：友好工具名 + “ · ” + 执行目标（`bodyMedium`）；
  - 预览标题与执行目标相同时省略重复的路径，只保留动作词（如“覆盖写入”）；
  - 风险只显示一段 `bodySmall` `error` 文字：有风险说明句时只显示说明句；没有说明句时才显示风险标签。
- 多调用与已执行状态按同一规则处理。

### 3.6 代码块与表格

截图 `ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownBatch2DarkChinese_助手正文 长回复 深色中文`：

- 第二行 `println(message)` 的首字母 `p` 被裁掉左半部分。按根因修复（检查墨迹裁剪、横向滚动容器的裁剪边界与内边距的顺序），任何字形都不得被裁切。新增一条能复现该裁切的测试（截图或设备断言均可）。
- 代码头部（36dp）下沿到首行代码墨迹约 25dp，而末行墨迹到容器底约 21dp，上下失衡且偏松。代码正文内边距改为：左右 16dp（与头部语言名左缘对齐）、上 4dp、下 12dp。
- 表格表头文字比正文单元格大。表头与正文单元格使用相同字号，表头为 Medium 字重。

### 3.7 正文块间距（规格裁定）

截图实测：默认 15sp、1.6 倍行高下，段落内部相邻两行的墨迹间距约 8.4dp。规格原定的段落间 10dp、标题下 8dp、列表项 4dp 与之几乎相同甚至更小，导致段落之间分不开、列表显得拥挤。以下为新的硬性要求（截图墨迹实测，默认字号）：

| 边界 | 间距 |
| --- | --- |
| 段落之间 | 16dp |
| 标题上方（标题不是首块时） | 24dp |
| 标题下方 | 10dp |
| 列表项之间（同级、进入或退出层级均相同） | 10dp |
| 列表与相邻段落或标题之后的首个列表 | 12dp |
| 代码块、表格、公式、图表、引用块与相邻块 | 16dp |

多行列表项、多行引用的内部行距与正文相同。更新 `MarkdownReadingLayoutTest` 等相关测试与报告中的像素实测表。

### 3.8 对比度（规格裁定）

浅色主题下助手元信息“模型名 · 时间”实测 3.12:1，不满足规格第 6 节 4.5:1。裁定：助手元信息与工具展开态的内部 ID 改用不加透明度的 `onSurfaceVariant`，层级由字号与字体区分；步骤行文字的 85% 透明度保持。报告中给出浅色与深色两套实测对比度。

## 4. 规格同步

把下列裁定写入规格文件对应小节，使用终态表述：

- 5.4 节：第 3.7 节的块间距表；代码块正文内边距；表头与单元格同字号；元信息颜色。
- 5.5 节：运行态无前导进度圈、文字微光；思考预览紧跟标签；“展开全部”为紧凑文字链接；内部 ID 颜色。
- 5.6 节：卡内参数块 `surfaceContainerHigh`；执行目标与风险各只出现一次。
- 5.8 节：“+”与发送按钮可见区 40dp、触控区不放大可见背景；“+”图标左缘与输入文字对齐。
- 8 节：`StreamRevealer` 场景 2 改为“一次到达 2000 字符：受 2000 字符/秒上限约束，1.2 秒内追平”。

## 5. 截图与报告

- 修正后重新生成受影响的截图基线，并逐张目视检查。
- 在报告末尾追加章节“复核问题修正”，逐条对应本任务书第 2、3 节，写明根因（第 2 节）、修改位置（`文件:行号`）与对应截图文件名；第 3 节各项给出修正后的像素实测值。
- 只更新已有章节中与修正直接矛盾的条目（如像素实测表、未完成项第 1、2 条），其余历史内容不改写。

## 6. 边界与验收

- 边界同原任务书第 5 节：不提交、不推送、不追加 handover、不操作设备、不改设置页组件、不改存储键与默认值、不执行 `./gradlew --stop`。
- 验收命令同原任务书第 6 节，全部通过。
