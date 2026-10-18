# 任务书：会话界面视觉重设计——独立审阅问题修正

## 1. 背景

Nexara 是 Android 原生 AI 客户端（Kotlin + Jetpack Compose + Material 3，模块 `native-ui/app`）。当前目录是独立 git worktree，工作树中已有会话界面视觉重设计的未提交实现，依据为：

- 规格：`docs/superpowers/specs/2026-10-17-nexara-settings-chat-redesign.md`（下称“规格”）；
- 任务书：`.agent/tmp-agent-reports/brief-redesign-batch2.md`、`.agent/tmp-agent-reports/brief-redesign-batch2-r2.md`；
- 实施报告：`.agent/tmp-agent-reports/report-redesign-batch2.md`。

独立审阅者对工作树做了只读审阅，主控逐条核对源码后确认了第 3 节所列问题。本任务在现有工作树上修正这些问题。原任务书的边界、验收命令与报告要求仍全部有效。

所有源码路径相对于 `native-ui/app/src/main/java/com/promenar/nexara/ui/`。

## 2. 必须保留的现有修正

报告第 12 节列出的主控修正已经通过 API 31 / API 35 设备测试，修改时不得回退：

- `common/MarkdownText.kt`：非流式且不超过 12000 字符的片段同步解析（`immediate`），流式与超长片段异步解析；
- `chat/ChatScreen.kt`：输入框 `heightIn(min = 48dp)`、装饰框上下 12dp 留白、`LineHeightStyle(Center, Trim.None)`，无附件时容器顶边 0dp、有附件时 12dp；“+”按钮偏移 -12dp；
- `theme/Type.kt` 的 `keepCjkTailTogether()` 及 `common/NexaraSettingsSection.kt` 的脚注调用；
- `androidTest` 中 `ChatApprovalLayoutTest`、`ChatLayoutTest`、`ShareImportAndroidEndToEndTest` 的现有写法。

## 3. 修正项

按根因修复生产代码；不得通过放宽断言、删除用例或缩小触控区让测试通过。

### 3.1 生成结束后尾部跟随（高）

现状：`chat/ChatScreen.kt` 约 388–399 行只在生成中按内容长度触发 `scrollToStreamingTail()`；约 409 行的 `snapshotFlow` 只观察输入区高度、键盘、视口、底部留白与条目数，不观察跟随目标条目自身的位置与高度。生成结束后条目仍会增高：完成态操作行（约 36dp + 8dp 上间距，`chat/PipelineBubble.kt` 约 607 行）只在生成结束后出现；`StreamRevealer` 在流结束后还有最多 300ms 的积压排空；超过 12000 字符的历史片段异步解析完成后增高。结果是回复完成后末尾几行与操作行落在输入区下方，“回到底部”按钮出现。

要求：

- 自动跟随开启时，`snapshotFlow` 同时观察跟随目标条目在 `layoutInfo.visibleItemsInfo` 中的 `offset` 与 `size`（目标不在可见列表中时按现有逻辑处理），任何一项变化都调用 `scrollItemAboveComposer(followTargetIndex)`；不得在用户主动上滑（自动跟随关闭）时拉回底部。
- 生成中不得再出现末行先落到输入区下方、约 100ms 后才被拉回的现象：跟随校正与条目增高在同一帧或下一帧内完成。
- 新增设备用例（`androidTest/java/com/promenar/nexara/ui/chat/`）：
  1. 生成中自动跟随开启，切换为生成结束（出现完成态操作行）后，最后一个操作行按钮完全位于输入区上方；
  2. 用户上滑使自动跟随关闭后，生成结束不改变列表滚动位置；
  3. 超过 12000 字符的非流式助手消息作为最后一条时，异步解析完成后其末尾仍位于输入区上方。

### 3.2 审批卡显示内部工具 ID（高）

现状：`chat/ChatInlineComponents.kt` 约 955 行首行只显示 `toolFriendlyName(call.name)`。MCP 与未知工具统一显示为“其它工具”，`write_file`、`delete_file`、`move_file` 都显示为“修改文件”；内部 ID 只出现在读屏描述中。审批是安全决策，用户必须能看到将要执行的确切工具。`ChatScreenContentStateTest` 约 192 行把内部 ID 可见的断言改成了只断言友好名称。

要求：

- 审批卡每个调用的首行下方增加一行内部工具 ID：`labelSmall`、等宽字体、`onSurfaceVariant`（不加透明度），可选择复制；单项、多项与已执行状态一致。规格第 5.6 节写入此要求（审批卡属于展开态，允许出现内部 ID）。
- 恢复 `ChatScreenContentStateTest` 中内部 ID（`write_file`、`delete_file` 等）可见的断言，同时保留友好名称断言。

### 3.3 流式消息重新挂载时不得重放（高）

现状：`common/SmoothStreamContent.kt` 约 28 行流式时初始帧为空串，`common/StreamRevealer.kt` 首次 `updateTarget` 从第 0 个字形开始放出。生成中把消息滚出屏幕再滚回、旋转屏幕、离开会话再返回，或 `PipelineBubble` 中 `key("content:$index")` 因步骤序列变化而改变时，已读内容会以最高 2000 字符/秒重放，尾部跟随也随之抖动。

要求：

- 首次挂载时已显示长度等于当前全文长度；只有挂载之后新到达的增量参与放出与渐显。
- 单元测试覆盖：流式中途挂载立即显示当前全文；挂载后的增量正常放出；文本改写回退规则不变。

### 3.4 步骤行失败计数不得被截断（中）

现状：`chat/CompactStepRow.kt` 约 145–161 行摘要与“· N 个失败”位于同一个单行、末尾省略的 `Text` 中，窄屏、130% 字号、英文或多类别摘要时失败段最先被截掉。

要求：失败段拆成独立、不收缩的 `Text`（`error` 色），摘要占用剩余宽度并先截断；读屏描述不变。新增设备或截图用例：360dp、130% 字号、英文、两类别摘要加失败时，失败段完整可见。

### 3.5 检索失败使用错误色（中）

现状：`chat/ChatInlineComponents.kt` 约 322 行向 `CompactStepRow` 传入错误色，但 `CompactStepRow` 未使用该颜色参数（约 95 行标注未使用），“未能检索知识库”与成功态同色。

要求：步骤行在失败态（工具失败、检索失败）文字使用 `error` 色，与规格第 5.5 节一致；删除未使用参数的标注或真正使用该参数，二选一并保持接口清晰。

### 3.6 读屏描述分隔符本地化（低）

现状：`chat/CompactStepRow.kt` 约 120 行用硬编码全角逗号拼接读屏描述，英文环境也读作全角逗号。

要求：分隔符使用字符串资源（中文“，”，英文“, ”）。

### 3.7 思考展开内容恢复 Markdown 渲染（中）

现状：`chat/PipelineBubble.kt` 约 584–590 行展开态改为 `Text(reasoning)`，推理中的列表、代码、公式以原始 `**`、`#`、反引号显示。实现前展开态使用 `MarkdownText` 渲染。

要求：展开态继续使用 `MarkdownText` 渲染，样式按规格第 5.5 节（`bodySmall` 字号、`onSurfaceVariant`、左侧 2dp `outlineVariant` 竖线、12dp 间距）。确认 `MarkdownText` 支持传入字号与颜色；不支持时以最小改动扩展参数，不影响正文渲染。更新受影响截图。

### 3.8 多项审批按钮文案（中）

现状：`chat/ChatInlineComponents.kt` 约 1015–1024 行多项待审批时显示“批准 / 拒绝”，旁边另有“全部批准”；实现前为“批准当前 / 拒绝当前”。看屏幕的用户可能以为“批准”作用于全部。`ApprovalCardTagsTest` 约 65 行改为按标签查找，未保留文案含义。

要求：存在多项待审批调用时按钮显示“批准当前 / 拒绝当前”（英文对应 “Approve current / Reject current”），单项时显示“批准 / 拒绝”。`ApprovalCardTagsTest` 恢复对多项时可见文案的断言。

### 3.9 审批参数不参与风险说明（中）

现状：`chat/ChatScreen.kt` 约 812–829 行 `approvalDescription` 把请求原因与“参数：…”拼成一段；`chat/ChatInlineComponents.kt` 约 816–822 行 `approvalRiskSummary` 只过滤 `Execution mode:` 开头的行，其余整段作为红色风险说明显示，并顶替风险标签。带参数的请求会把参数 JSON 以红字再显示一遍；`ChatScreenContentStateTest` 约 192–196 行删除了两条风险标签断言，并断言普通原因 “Update workspace” 显示为风险。

要求：

- 风险说明只来自风险事实（删除不可逆、高风险目标、脚本联网等）与请求原因中的风险语句；参数摘要不进入风险说明，只在参数块中显示一次。
- 请求原因不是风险说明：没有风险事实时显示风险标签；请求原因（去除 `Execution mode:` 行后非空时）以 `bodySmall`、`onSurfaceVariant` 单独显示在首行与内部 ID 之下，不使用 `error` 色。
- 恢复 `ChatScreenContentStateTest` 的风险标签断言，删除“普通原因显示为风险”的断言，改为断言原因以非错误色显示；`ApprovalRiskSummaryTest` 同步更新。规格第 5.6 节写入此规则。

### 3.10 复制整轮回复（中）

现状：`chat/PipelineBubble.kt` 约 180、243 行操作行“复制”只复制最后一个非空正文片段；“正文 → 工具 → 正文”的回复只会复制末段。实现前复制整条消息内容。

要求：复制该轮所有非空正文片段，按出现顺序以一个空行连接；不包含思考、工具参数与结果。补充单元测试。

### 3.11 顶栏保留横向安全区（中）

现状：`chat/ChatScreen.kt` 约 1200 行把顶栏 `windowInsets` 清零，约 450 行只补了状态栏。横屏三键导航或侧边刘海时，返回与“更多”按钮可能被遮挡。

要求：顶栏保留 `WindowInsets.safeDrawing` 的水平部分（`only(WindowInsetsSides.Horizontal)`），垂直部分维持现状。新增横屏截图或设备断言，证明返回与“更多”按钮位于横向安全区内。

### 3.12 流式性能（中）

1. `common/MarkdownText.kt` 约 1388 行 `WideContentHost` 以代码全文为缓存键逐行测量宽度，流式时每帧重新测量全部行。改为按行缓存测量结果：已测量行复用，只测量新增或改变的行；流式中取已测量行的最大宽度。嵌套代码与表格同样处理。
2. `common/MarkdownText.kt` 约 793 行代码高亮以代码全文为键，代码逐帧变化时上一轮后台高亮被取消，单次高亮超过一帧即永远无法落地，代码块在流式期间保持旧内容。改为：保留最近一次高亮结果并应用于其覆盖的前缀，未覆盖的尾部以未着色文本显示；高亮在流式期间节流（不超过每 250ms 启动一次，且不取消正在执行的一轮），流结束后对全文做一次最终高亮。
3. `common/StreamRevealer.kt` 约 77–80 行：流式预处理给未闭合的代码围栏或 `**` 临时补尾，使新文本不再以旧文本开头，速率估计被反复重置。放出器只处理原始累计文本；临时补尾在放出切片之后施加，或在比较前进行等价处理，使速率估计在代码块与加粗段中不被重置。补充单元测试。
4. 非流式消息不经过放出器与字形切分，直接显示全文。系统动画缩放的读取与 `ContentObserver` 注册在应用内只保留一份（例如进程级 `StateFlow` 或 `CompositionLocal`），不得每个正文片段各注册一个。
5. 在报告中说明上述修改前后的复杂度变化；不要求运行性能基准。

### 3.13 触控断言检查真实触控范围（低）

现状：`AccessibilitySmokeTest` 约 68–79 行把附件按钮的断言从 48dp 改为 40dp、上下文指示从 36dp 改为 40dp，`ChatScreenContentStateTest` 约 120 行把任务胶囊从 48dp 改为 28dp，均为可见高度断言，守不住 48dp 触控门禁；上下文指示与任务胶囊不在规格第 6 节例外清单中。

要求：这些节点在可见尺寸断言之外，增加 `touchBoundsInRoot` 宽高不小于 47.5dp 的断言，换算使用节点自身布局密度（`node.layoutInfo.density`，参考 `ChatApprovalLayoutTest`）。生产代码达不到时修生产代码。

### 3.14 资源与接口清理（低）

- `res/values*/chat_markdown_batch2.xml` 中仅被测试引用的 `chat_markdown_batch2_preview`、`chat_markdown_batch2_html_preview` 移出正式资源，测试改用测试源码内的常量。
- 删除 `chat/SessionSettingsSheet.kt` 约 164 行无调用方的 `initialTab` 参数。

## 4. 截图与报告

- 修正后重新生成受影响的截图基线（`updateSideloadDebugScreenshotTest`），逐张目视检查，列出新增、更新、删除的基线文件名。
- 在报告末尾追加章节“独立审阅问题修正”，逐条对应第 3 节，写明修改位置（`文件:行号`）、新增或更新的测试、对应截图文件名。
- 新增的设备用例只需编译通过（`assembleSideloadDeviceTestAndroidTest` 带 `-Pnexara.deviceE2e=true -Pnexara.deviceE2eAbi=arm64-v8a`）；报告中列出这些用例的完整类名与方法名，由主控在设备上运行。

## 5. 边界与验收

- 边界同原任务书第 5 节：不提交、不推送、不追加 handover、不操作设备、不改设置页组件（`common/NexaraSettingsSection.kt` 的现有脚注调用除外）、不改存储键与默认值、不执行 `./gradlew --stop`。
- 验收命令同原任务书第 6 节，全部通过；另外运行：

```bash
cd native-ui && ./gradlew --max-workers=4 "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=1g" -Pnexara.deviceE2e=true -Pnexara.deviceE2eAbi=arm64-v8a :app:assembleSideloadDeviceTest :app:assembleSideloadDeviceTestAndroidTest
```
