# 会话界面视觉重设计交付报告

工作分支：`redesign-batch2`。范围依据 [任务书](brief-redesign-batch2.md) 及 [规格](../../docs/superpowers/specs/2026-10-17-nexara-settings-chat-redesign.md) 第 5、6、7、8、9.3 节。复用 Material 3、既有品牌解析、项目字体与间距令牌、mikepenz 0.41.0，无新增依赖。

已完成会话界面、阅读排版、步骤/审批、输入区和流式放出的工作树实现。独立审阅任务的第3.1–3.14项修正、当前验收与截图清单见第13节“独立审阅问题修正”；第1–12节保留对应阶段的实现与验证记录。设备运行边界见第13.5节。未commit/push、未追加handover。

## 1. 组件落点与所有权

下文 `chat/`、`common/`、`renderer/` 均相对于 `native-ui/app/src/main/java/com/promenar/nexara/ui/`；资源相对于 `native-ui/app/src/main/res/`。第1、2节保留初轮组件落点，当前工作树行号与最终验收以第13节为准。

| 组件 | 位置 | 职责 |
| --- | --- | --- |
| 顶栏与滚动分隔线 | `chat/ChatScreen.kt:1175` | 56dp 透明单行标题、重命名、文件与原更多菜单 |
| 消息节奏 | `chat/ChatItemSpacing.kt:7` | 按相邻角色计算首项、20/28dp 与末项 16dp |
| 状态条 | `chat/ChatScreen.kt:879` | 28dp 横向任务与后处理状态，位于输入容器上方 |
| 模型与用量 | `chat/ChatScreen.kt:926` | 32dp 可见胶囊、40dp 外层；复用品牌与设置入口 |
| 上下文入口 | `chat/ChatScreen.kt:959` | 18dp 圆环、百分比与原 ContextUsagePanel |
| 输入容器 | `chat/ChatScreen.kt:1431` | 六行文本与 40dp 单行工具栏 |
| 附件条 | `chat/ChatScreen.kt:1375` | 56dp 图片/文档及删除、导入状态 |
| 发送与停止 | `chat/ChatScreen.kt:1540` | 40dp 发送、停止、错误语义与禁用外观 |
| 用户消息 | `chat/PipelineBubble.kt:950` | 82% 气泡、附件在上、时间进入长按菜单 |
| 助手操作 | `chat/PipelineBubble.kt:597` | 完成态操作行，右侧模型与时间，生成中隐藏 |
| 正文归属 | `chat/PipelineBubble.kt:340` | 按实际正文消息绑定复制、编辑、分支和删除 |
| 步骤骨架 | `chat/CompactStepRow.kt:70` | 无底色 44dp 行、运行文字微光、失败段和旋转箭头 |
| 思考展开 | `chat/PipelineBubble.kt:522` | 自然语言摘要、渐隐推理尾部与 2dp 引线 |
| 工具展开 | `chat/PipelineBubble.kt:748` | 友好名、内部 ID、耗时与调用详情 |
| 长工具文本 | `chat/PipelineBubble.kt:817` | 参数六行、结果十二行与独立展开 |
| 工具摘要 | `chat/ToolInvocationPresentation.kt:143` | 严格 ID 分类与纯结构化状态 |
| 检索摘要 | `chat/RagInlineSummary.kt:23` | 来源、实际条目数、失败与部分成功 |
| 检索入口 | `chat/ChatInlineComponents.kt:277` | 新步骤行接入，保留原详情 sheet 与标签 |
| 审批 | `chat/ChatInlineComponents.kt:861` | 中性卡片、风险文字与唯一批准实心动作 |
| 流式状态机 | `common/StreamRevealer.kt:24` | 注入时间、速率估计、字形边界与积压处理 |
| 帧驱动 | `common/SmoothStreamContent.kt:24` | withFrameNanos 与系统动画开关 |
| 空行保护 | `common/MarkdownBlankLines.kt:13` | 只折叠受保护 Markdown 范围外的多空行 |
| Markdown 入口 | `common/MarkdownText.kt:334` | 预处理、分段、流式可见终点与富内容分派 |
| AST 可见视图 | `common/MarkdownText.kt:446` | 复用完整节点，裁剪尾部及安全补齐边界 |
| 阅读排版 | `common/MarkdownText.kt:611` | 统一文字注解与首末墨迹边界 |
| 代码块 | `common/MarkdownText.kt:697` | 36dp 头部、13/20sp 代码与既有 HTML 入口 |
| 列表 | `common/MarkdownText.kt:644` | 线性层级缩进、基线和项间距 |
| 主题 | `renderer/NexaraMarkdownTheme.kt:69` | 会话字号、标题倍数、行高、代码与引用角色 |

执行由主控集成与验证，原生施工 Agent `markdown_stream`、`pipeline_steps`、`inline_approval` 分别负责 Markdown/流式、消息/步骤、检索/审批。未参与施工的原生 Agent `review_chat_ui` 独立只读审阅，主控复核源码、diff、测试输出与像素图。权限按文件所有权作逻辑收窄，未使用外部 CLI Agent、按量付费模型或新增 Provider。

## 2. 规格 5.1–5.8 逐条对照

| 要求 | 实现位置 | 结果与边界 |
| --- | --- | --- |
| 5.1 高度、透明、返回 | `chat/ChatScreen.kt:1175` | expandedHeight=56dp、透明色，原返回回调与 48dp IconButton |
| 5.1 标题与重命名 | `chat/ChatScreen.kt:1207` | titleMedium、单行省略，仅会话名；删除标题旁铅笔/副标题与失效参数 |
| 5.1 文件与更多 | `chat/ChatScreen.kt:1216` | 文件 22dp；原设置/提示词/清空/重命名/删除顺序保留 |
| 5.1 滚动分隔线 | `chat/ChatScreen.kt:260` | firstVisibleItemIndex/offset 驱动 150ms alpha，0.5dp outlineVariant |
| 5.2 左右与宽屏 | `chat/ChatScreen.kt:492` | 列表最大 720dp 居中、内边距 20dp，无统一 spacedBy |
| 5.2 角色间距 | `chat/ChatItemSpacing.kt:7` | 用户→助手 20dp、助手→用户 28dp、连续助手 0；JVM 五场景 |
| 5.2 首尾 | `chat/ChatScreen.kt:511` | 首项 12dp；末 spacer=16dp，底部保留输入区实测高度 |
| 5.3 82% 与样式 | `chat/PipelineBubble.kt:950` | 右对齐，surfaceContainerHigh、20dp 圆角、16×10dp padding |
| 5.3 文字 | `chat/PipelineBubble.kt:1007` | bodyLarge、会话字号、1.5 行高、阅读换行 |
| 5.3 时间与长按 | `chat/PipelineBubble.kt:1021` | 时间只在长按菜单；原用户无编辑回调的前提差异见第 8 节 |
| 5.3 附件 | `chat/PipelineBubble.kt:984` | 附件在气泡上方右对齐，6dp 间隔 |
| 5.4 通栏正文 | `chat/PipelineBubble.kt:857` | 透明正文、无气泡/描边；长按原菜单与正文来源保持 |
| 5.4 字体与标题 | `renderer/NexaraMarkdownTheme.kt:69` | 默认 15sp/1.6；h1–h3 1.35/1.2/1.1，h4+1，SemiBold/1.35 |
| 5.4 块间距 | `common/MarkdownText.kt:432`；`common/MarkdownText.kt:567` | 单一边界节奏与首末墨迹裁剪，像素读数见第 3 节 |
| 5.4 列表 | `common/MarkdownText.kt:644` | 项间 10dp、相邻段落及标题后首个列表 12dp、普通嵌套每层 16dp |
| 5.4 引用 | `common/MarkdownText.kt:678` | 3dp outlineVariant，条后 12dp 内距 |
| 5.4 代码块 | `common/MarkdownText.kt:697` | Low/12dp，头部36dp/图标18dp，等宽13sp/20sp；编辑/复制保留 |
| 5.4 行内代码 | `common/MarkdownText.kt:1082` | High/4dp 圆角/左右4dp inline placeholder；不改用户字符 |
| 5.4 空行 | `common/MarkdownBlankLines.kt:13` | 保护 fence、缩进代码、code span、链接与表格，支持 CRLF |
| 5.4 操作行 | `chat/PipelineBubble.kt:597` | 距正文8dp、可见36dp、图标18dp/触控48dp，生成中隐藏；按回调显示动作 |
| 5.4 元信息与标签 | `chat/PipelineBubble.kt:609` | 右侧 labelSmall/onSurfaceVariant，无透明度；模型省略、时间保留，两个原 UiTags；对比度见第11节 |
| 5.5 无底步骤行 | `chat/CompactStepRow.kt:70` | 44dp 整行点击，13/18sp Normal、Variant85%，16dp箭头90°/150ms |
| 5.5 运行/失败 | `chat/CompactStepRow.kt:108` | 运行态无前导图标、文字1.2秒微光；失败后缀独立error色 |
| 5.5 行间节奏 | `chat/CompactStepRow.kt:51`；`chat/PipelineBubble.kt:185` | 相邻步骤0dp，正文边界8dp，RAG→正文同样处理 |
| 5.5 思考摘要与预览 | `chat/PipelineBubble.kt:522` | 中英数量资源；进行中渐隐推理尾部；无可靠耗时不虚构秒数 |
| 5.5 纯工具摘要 | `chat/ToolInvocationPresentation.kt:143` | 完整 ID 分类、双类首次顺序、三类通用、最后未完成、失败/耗时/空列表 |
| 5.5 折叠自然语言 | `chat/PipelineBubble.kt:704` | 完成/运行/等待资源；知识与记忆分开；折叠态无内部ID/参数 |
| 5.5 展开标题 | `chat/PipelineBubble.kt:748` | 左缩进0、调用间12；友好名bodySmall+ID11sp等宽onSurfaceVariant无透明度+耗时 |
| 5.5 参数与结果 | `chat/PipelineBubble.kt:817` | 参数12/18sp Low/8dp/pad10/6行；结果bodySmall/12行，错误error色，图片保留 |
| 5.5 展开全部 | `chat/PipelineBubble.kt:824` | 显式多行首帧可展开，自动折行由onTextLayout补充；参数/结果独立展开 |
| 5.5 思考展开 | `chat/PipelineBubble.kt:581` | bodySmall阅读换行、Variant、2dp条+12dp内距，原文显示 |
| 5.5 检索 | `chat/ChatInlineComponents.kt:277`；`chat/RagInlineSummary.kt:23` | 自然语言、新步骤行、保留RAG_PROGRESS_CARD与详情；实际计数及部分成功回归 |
| 5.6 容器与标题 | `chat/ChatInlineComponents.kt:861` | Low/16dp/pad16，无外描边或强调竖条；16dp警示+titleSmall，已执行中性色 |
| 5.6 内容与风险 | `chat/ChatInlineComponents.kt:951` | 友好工具名与目标bodyMedium；风险bodySmall/error文字，无红色填充 |
| 5.6 参数 | `chat/ChatInlineComponents.kt:817` | High、8dp/10dp、等宽12/18sp、六行与紧凑展开链接 |
| 5.6 按钮 | `chat/ChatInlineComponents.kt:1034` | 右对齐拒绝Outlined，批准唯一实心；可见40dp/触控48dp |
| 5.6 次动作与标签 | `chat/ChatInlineComponents.kt:1055` | 始终允许/全部批准TextButton；原标签、回调、执行/提交禁用不变 |
| 5.7 注入时间与速率 | `common/StreamRevealer.kt:24` | EMA .3、v=max(rate,backlog/.5)钳30–2000，浮点shown |
| 5.7 结束排空 | `common/StreamRevealer.kt:89` | 固定300ms终点，剩余积压/剩余时间，完成后稳定 |
| 5.7 字形、改写与积压 | `common/StreamRevealer.kt:43`；`common/StreamRevealer.kt:147` | BreakIterator+emoji保护、公共前缀回退、>4000跳末尾前200 |
| 5.7 帧时钟和直接全文 | `common/SmoothStreamContent.kt:24` | withFrameNanos；非流式首次载入/系统0/Inspection全文，IO读取系统设置 |
| 5.7 渐显 | `common/MarkdownText.kt:476` | 核实源偏移回调，末块30/65/100分档，完整节点/历史注解缓存；特殊渲染器边界见第4节 |
| 5.7 去旧实现与光标 | `common/MarkdownText.kt:1402` | 旧StreamSpeed/reducer/smoothingCps/36dp遮罩已清理；固定宽光标 |
| 5.8 外框与inset | `chat/ChatScreen.kt:647`；`chat/ChatScreen.kt:1431` | 侧边12dp、底8dp，High不透明、24dp、padding12/12/8、无阴影；原insets函数未变 |
| 5.8 状态条 | `chat/ChatScreen.kt:879` | 仅有任务/后处理时28dp，底8dp，第一方横向滚动不换行；删除ChatInputTopBar |
| 5.8 附件/输入 | `chat/ChatScreen.kt:1375`；`chat/ChatScreen.kt:1482` | 附件56dp/12dp/8dp；bodyLarge min1/max6，输入启用条件与附件回调保留 |
| 5.8 工具行与附件 | `chat/ChatScreen.kt:1502` | 高40dp、距输入4dp，+可见40/图标22/展开转45° |
| 5.8 模型 | `chat/ChatScreen.kt:926`；`chat/SessionSettingsSheet.kt:164` | 32可见/40触控、16品牌/labelLarge最大160/16展开；默认初始模型页 |
| 5.8 用量 | `chat/ChatScreen.kt:959` | 18dp环2dp、超过阈值tertiary、百分比tnum、原面板与压缩入口 |
| 5.8 发送停止与标签 | `chat/ChatScreen.kt:1540` | 40圆/箭头20、停14；可用primary、不可用Highest+38%，错误语义不变，原标签集合无删除 |

## 3. Markdown 排版与像素实测

正文默认15sp、行高1.6，标题1.35/1.2/1.1倍、SemiBold、行高1.35。`readingInkInsets` 按真实文本的 Paint 墨迹与基线移除首末行高留白；中间行距保持，边缘向外取整以保护字形。列表marker和正文仍使用真实层级与编号协议，每层16dp缩进；每项上下5dp，边界扣除首末项已有5dp，非首层列表借用末项底部留白移动5dp，保证进入/退出层级与同级节奏相同。

块边界为段落16dp、非首块标题前24dp/后10dp、列表与相邻段落或标题后首个列表12dp、各层列表项10dp、媒体和引用块16dp。代码由第一方宽内容宿主负责横向手势，文字无内部滚动裁剪，正文左右16dp/上4dp/下12dp；头部36dp。表头和正文单元格同字号，表头Medium。

最终截图墨迹坐标、像素换算与栅格取整边界见第11节及 `batch2-r2-logs/visual-measurements.json`；密度为420dpi，即1dp=2.625px。初轮读数保留在 `batch2-logs/green-markdown-final-pixels.log` 等历史日志，不作为修正后的读数。

## 4. 流式放出与 markdownAnnotator 证据

已核实缓存 AAR 的 `classes.jar` 与 [Maven Central 同版本官方 sources](https://repo.maven.apache.org/maven2/com/mikepenz/multiplatform-markdown-renderer-android/0.41.0/multiplatform-markdown-renderer-android-0.41.0-sources.jar)，不是猜测其它版本的 API。原始输出见 `batch2-logs/javap-markdown-041.log`，核心签名为：

```java
public static final MarkdownAnnotator markdownAnnotator(
    MarkdownAnnotatorConfig,
    Function3<? super AnnotatedString.Builder, ? super String, ? super ASTNode, Boolean>);
public abstract Function3<AnnotatedString.Builder, String, ASTNode, Boolean> getAnnotate();
```

即 Kotlin 回调 `AnnotatedString.Builder.(String, ASTNode) -> Boolean`。ASTNode 的 startOffset/endOffset 可将源范围映射为实际注解位置，Boolean 表示是否由自定义注解接管。库同时提供 `Markdown(State, …)` 与 `rememberMarkdownState`，所以选择缓存解析状态 + 尾部可见 AST + 末块注解方案；没有在每帧调用 String 入口重新解析。

真实 Compose `Recomposer`、`BroadcastFrameClock` 和官方 `buildMarkdownAnnotatedString` 的 12 帧测试证明：base annotator 引用稳定，历史块只构建一次，末块实际更新多次。此证据覆盖注解隔离，不是整屏帧率或内存 profile；真实设备性能未测。

`StreamRevealer` 为纯 Kotlin 状态机，时间由调用方注入：增长增量/增长间隔为样本，EMA α=0.3；每帧速度为 `clamp(max(rate, backlog / 0.5), 30, 2000)`。流结束时设置 300ms 固定终点，用剩余时间追平积压；仅使用每帧 `backlog / 0.3` 会指数逼近，不能保证完成，故使用剩余终点时间。超过 4000 字符跳到末尾前 200；文本改写回退到公共字形前缀。BreakIterator 之外补足 ZWJ、肤色、区域码、变体与组合标记保护。

Compose 用 `withFrameNanos` 驱动，动画缩放通过 `Settings.Global.ANIMATOR_DURATION_SCALE` 在 IO dispatcher 读取并观察。非流式首次载入、动画关闭和预览直接全文；活动流切换为结束后保留最长 300ms 的排空过程。删去 `StreamSpeed`、`smoothingCps`、旧 reducer 与 36dp 底部遮罩。光标保留固定布局宽度，其透明度动画不改变尺寸。

上游文本变化时才执行预处理、富内容分段与 Markdown 解析。每帧裁剪已解析 AST 的可见尾部，完整节点复用原引用；围栏/行内代码/链接补零长闭合节点以满足官方提取器，图片链接完整到达后才交图片组件。显式链接的目的地未完整可见时，标签只显示为普通文字，完整后才建立可点击 URL；防止半截隐藏网址成为点击目标，自动链接保持原行为。普通历史块使用稳定 base annotator，只有最后可见块替换 tail annotator；inline-content map 稳定缓存，宽表子树判断按节点缓存。

逐字透明度覆盖末个可见 AST 块内普通正文、标题、列表正文、普通引用、字面标点与行内代码，档位为 <60ms 30%、<120ms 65%、其余 100%。围栏/缩进代码的语法高亮、独立单行代码特例、表格、公式/图表、图片及 GFM alert 自有渲染器不经过该注解；它们遵守匀速到达或富内容完整显示，不声称具备逐字渐显。没有回退到整块底部遮罩。

复核任务书把2000字符突发场景裁定为受2000字符/秒上限约束、1.2秒内追平。模拟时钟回归同时检查600ms最多放出1200字符、1.2秒内全文；原600ms判据的初轮失败记录保留在 `batch2-logs/red-burst600ms-conflict.log`，当前规格与实现已一致。结束态仍按300ms固定终点排空。

## 5. 工具分类与中英文文案

纯 `toolGroupSummary` 返回动作/类别、数量、状态、失败调用数、耗时与最后未完成动作，不依赖 Compose。两类别按首次出现顺序连接，三类及以上使用通用数量摘要；同类中的知识/记忆、时间/计算保留各自文案。运行时取最后一个未完成调用，历史停止后但缺少结果的调用显示等待处理，不伪装成正在运行。不根据参数中的普通 `name` 猜工具名，MCP 命名空间不剥前缀。

| 类别 | 严格匹配 ID | 资源词干 chat_step_ |
| --- | --- | --- |
| 命令 | termux_exec、ssh_exec、ssh_session_open、ssh_session_send、ssh_session_read、ssh_session_close、run_script、exec_js | command |
| 网页搜索 | web_search、search_tavily、search_searxng | web_search |
| 网页读取 | web_fetch | web_read |
| 查看文件 | read_file、list_files、search_files、read_tool_output、read_skill_file | file_read |
| 修改文件 | write_file、create_file、patch_file、diff_file、move_file、delete_file、create_directory | file_edit |
| 检索：知识/记忆 | knowledge_search / memory_search | knowledge / memory |
| 图片 | generate_image、image_generation | image |
| 计划 | initialize_plan、update_plan、get_plan、drop_plan | plan |
| 实用：时间/计算 | current_time / calculator | time / calculate |
| 技能 | activate_skill | skill |
| 其它 | 所有其余ID，包括MCP与未知命名空间 | other |

中英文资源各 51 项、其中 20 个 plurals；中文使用 other，英文按数量提供 one/other。网页单次另设 once，避免中文 CLDR 无 one 分支导致文案不符。

| 资源键 | 类型 | 中文 | 英文 |
| --- | --- | --- | --- |
| `chat_step_summary_separator` | string | ， | ", " |
| `chat_step_more_actions` | string | 更多消息操作 | More message actions |
| `chat_step_expand_all` | string | 展开全部 | Show all |
| `chat_step_collapse_all` | string | 收起 | Collapse |
| `chat_step_thought` | string | 已思考 | Thought |
| `chat_step_thinking` | plurals | other: 正在思考 · %1$d 秒 | one: Thinking · %1$ds；other: Thinking · %1$ds |
| `chat_step_thought_duration` | plurals | other: 思考了 %1$d 秒 | one: Thought for %1$ds；other: Thought for %1$ds |
| `chat_step_failed_count` | plurals | other: %1$d 个失败 | one: %1$d failed；other: %1$d failed |
| `chat_step_duration_ms` | plurals | other: %1$d 毫秒 | one: %1$dms；other: %1$dms |
| `chat_step_duration_seconds` | plurals | other: %1$s 秒 | one: %1$ss；other: %1$ss |
| `chat_step_command_done` | plurals | other: 运行了 %1$d 条命令 | one: Ran %1$d command；other: Ran %1$d commands |
| `chat_step_web_search_once` | plurals | other: 搜索了网页 | other: Searched the web |
| `chat_step_web_search_done` | plurals | other: 搜索了 %1$d 次网页 | one: Searched the web；other: Searched the web %1$d times |
| `chat_step_web_read_done` | plurals | other: 读取了 %1$d 个网页 | one: Read %1$d page；other: Read %1$d pages |
| `chat_step_file_read_done` | plurals | other: 查看了 %1$d 个文件 | one: Read %1$d file；other: Read %1$d files |
| `chat_step_file_edit_done` | plurals | other: 修改了 %1$d 个文件 | one: Edited %1$d file；other: Edited %1$d files |
| `chat_step_knowledge_done` | plurals | other: 检索了知识库 | one: Searched knowledge；other: Searched knowledge %1$d times |
| `chat_step_memory_done` | plurals | other: 检索了记忆 | one: Searched memory；other: Searched memory %1$d times |
| `chat_step_image_done` | plurals | other: 生成了 %1$d 张图片 | one: Generated %1$d image；other: Generated %1$d images |
| `chat_step_plan_done` | plurals | other: 更新了任务计划 | one: Updated the plan；other: Updated the plan %1$d times |
| `chat_step_time_done` | plurals | other: 查询了时间 | one: Checked the time；other: Checked the time %1$d times |
| `chat_step_calculate_done` | plurals | other: 进行了计算 | one: Calculated；other: Calculated %1$d times |
| `chat_step_skill_done` | plurals | other: 启用了技能 | one: Activated a skill；other: Activated %1$d skills |
| `chat_step_other_done` | plurals | other: 调用了 %1$d 个工具 | one: Used %1$d tool；other: Used %1$d tools |
| `chat_step_command_running` | string | 正在运行命令… | Running a command… |
| `chat_step_web_search_running` | string | 正在搜索网页… | Searching the web… |
| `chat_step_web_read_running` | string | 正在读取网页… | Reading a webpage… |
| `chat_step_file_read_running` | string | 正在查看文件… | Reading files… |
| `chat_step_file_edit_running` | string | 正在修改文件… | Editing files… |
| `chat_step_knowledge_running` | string | 正在检索知识库… | Searching knowledge… |
| `chat_step_memory_running` | string | 正在检索记忆… | Searching memory… |
| `chat_step_image_running` | string | 正在生成图片… | Generating an image… |
| `chat_step_plan_running` | string | 正在更新任务计划… | Updating the plan… |
| `chat_step_time_running` | string | 正在查询时间… | Checking the time… |
| `chat_step_calculate_running` | string | 正在计算… | Calculating… |
| `chat_step_skill_running` | string | 正在启用技能… | Activating a skill… |
| `chat_step_other_running` | string | 正在调用工具… | Using a tool… |
| `chat_step_command_name` | string | 运行命令 | Command |
| `chat_step_web_search_name` | string | 网页搜索 | Web search |
| `chat_step_web_read_name` | string | 读取网页 | Webpage |
| `chat_step_file_read_name` | string | 查看文件 | Read files |
| `chat_step_file_edit_name` | string | 修改文件 | Edit files |
| `chat_step_knowledge_name` | string | 知识库检索 | Knowledge search |
| `chat_step_memory_name` | string | 记忆检索 | Memory search |
| `chat_step_image_name` | string | 生成图片 | Image generation |
| `chat_step_plan_name` | string | 任务计划 | Task plan |
| `chat_step_time_name` | string | 查询时间 | Current time |
| `chat_step_calculate_name` | string | 计算 | Calculation |
| `chat_step_skill_name` | string | 启用技能 | Activate skill |
| `chat_step_other_name` | string | 其它工具 | Other tool |
| `chat_step_pending_count` | plurals | other: 有 %1$d 个工具等待处理 | one: %1$d tool is waiting；other: %1$d tools are waiting |

检索摘要优先实际命中：知识库引用条数 + 图谱路径条数，不按内容去重；纯网页来源统计网页引用。部分来源失败时保留实际数量并追加错误色“部分来源未能检索 / Some sources failed”；失败且无实际命中才显示整体失败。混合来源知识计数不混入网页引用，完整来源仍可从详情查看。

## 6. 测试清单、契约迁移与结果

本节 JVM 文件路径相对于 `native-ui/app/src/test/java/com/promenar/nexara/`；设备文件路径相对于 `native-ui/app/src/androidTest/java/com/promenar/nexara/`。

相关16个测试类共97项，在两个渠道均无失败或跳过；其中工具22项、Pipeline10项包含既有回归，其余按下列场景新增或重写。初轮全套统计为 sideload 3856项、play 3832项；最终统计见第11节，各25项条件性跳过；具体跳过用例与统计见 `batch2-logs/quality-final-results.json`，最终命令输出见第9节。

- `ui/common/SmoothStreamContentTest.kt:8`：14项通过；新增/重写14项。
  每100毫秒到达5字符时稳态逐帧平滑且有积压时不停顿6帧；2000字符积压遵守每秒2000字符上限并在1200毫秒内追平；结束时300字符积压在300毫秒内完整放出；非流式消息直接显示全文；动画禁用时流式内容直接显示全文；流结束后没有增长时全文保持稳定；不拆开代理对组合字符肤色ZWJ国旗与变体序列；文本改写时立即退回已显示文本的公共字形前缀；改写发生在组合字形内部时回退至字形之前；积压超过4000字符时跳到末尾前200字符；积压跳转同样不能拆开emoji；到达速率按增长间隔以0点3指数平均；时钟没有推进时不额外放出字符；新文字注解透明度按60和120毫秒分档。

- `ui/chat/ToolInvocationPresentationTest.kt:7`：22项通过；新增/重写11项。
  停止生成后未完成工具展示等待而非运行或成功；活动工具组保留最后未完成类别且等待数量不包含已完成调用；历史等待组保留失败数量且完成组不会变成等待；工具摘要严格按规格映射所有工具且保留检索与实用子类别；未知名称与MCP工具不能凭子串归入内置类别；两类别按首次出现顺序汇总并且统计每次调用；三类别使用总调用数摘要而非列出内部工具；知识与记忆检索和时间与计算分别保留动作但只算两大类别；进行中取最后一个未完成调用而非最后一个事件或首个调用；失败数量按调用而非错误事件计数且进行状态优先；空工具组没有运行状态耗时或虚构调用。

- `ui/chat/PipelineBubbleTest.kt:11`：10项通过；新增/重写4项。
  工具轮尾部空助手消息不夺走最后正文的所属消息；多个正文片段各自保留所属消息供长按和编辑使用；工具信封清理后的正文与流式占位均保留真实消息身份；正文来源不存在时不会回退到其它消息。

- `ui/chat/ChatItemSpacingTest.kt:6`：5项通过；新增5项。
  首项距顶栏十二 dp；用户到助手二十 dp；助手到用户二十八 dp；连续助手片段不增加空白；末项距输入区十六 dp且不叠加相邻项下边距。

- `ui/chat/RagDetailsInlineSummaryTest.kt:9`：14项通过；新增14项。
  检索进行中不将内部阶段名作为摘要；完成检索优先显示实际引用数；没有引用时保留过程记录中的命中数；未命中是零条结果而非检索失败；ready阶段完成时已可显示检索结果；只有网页引用时摘要显示网页结果；混合来源与知识图谱不会被误标成仅网页检索；实际知识引用和图谱路径计数优先于过期未命中轨迹；没有实际结果时不可用未选范围及失败均隐藏轨迹中的结果数；知识库检索失败时独立图谱命中仍显示结果并提示部分失败；知识库不可用时网页引用显示网页命中并提示部分失败；部分失败的知识库计数合并实际引用与图谱路径但不混入网页引用；知识库未命中但网页有结果时显示实际网页结果且不虚报失败；检索尚未完成时保留运行态而不提前宣称部分成功。

- `ui/common/MarkdownBlankLinesTest.kt:6`：8项通过；新增8项。
  普通段落连续三个以上换行折叠为两个；两个换行与没有换行的内容保持原文；反引号波浪号和未闭合围栏里的多空行均保留；缩进代码内连续空行保持原文；行内代码的内容与多反引号边界保持原文；链接文字目标与引用定义不改变；表格行和表格内容保持原文；CRLF按原换行形式折叠并保护围栏代码。

- `ui/common/MarkdownReadingLayoutTest.kt:14`：4项通过；新增4项。
  段落标题和媒体块按边界节奏排版；列表首末项内边距包含在与段落或标题后的十二点间距中；帧裁剪复用完整块并隐藏未到达节点；部分行内代码保留可见正文与零长闭合节点。

- `ui/common/MarkdownStreamingFenceTest.kt:12`：3项通过；新增3项。
  带语言围栏当前行逐字符交给官方代码提取路径；无语言围栏当前行逐字符交给官方代码提取路径；波浪号围栏当前行逐字符交给官方代码提取路径。

- `ui/common/MarkdownStreamAstBoundaryTest.kt:11`：2项通过；新增2项。
  图片链接未完整可见时不把半截URL交给图片组件；图片链接完整可见时保持原始完整地址。

- `ui/common/MarkdownStreamingLinkTest.kt:18`：3项通过；新增3项。
  使用真实官方注解器验证显式链接目的地逐字放出期间标签可见且不建立半截点击目标；目的地完整后建立完整URL；普通自动链接保留原有随可见文本变化的目标。修复前的半截URL失败证据见 `batch2-logs/red-markdown-link-destination.log`，对应通过证据见 `batch2-logs/green-markdown-link-destination.log`，全套结果见第9节。

- `ui/common/MarkdownAnnotationFrameTest.kt:39`：1项通过；新增1项。
  渐显帧改变时历史块不重注解而末块更新透明度。

- `ui/common/MarkdownInkInsetsTest.kt:6`：2项通过；新增2项。
  首末字形外留白被裁去而中间基线跨度保持；向外取整保留抗锯齿边缘且不产生负高度。

- `ui/common/MarkdownInlineCodeInkTest.kt:8`：2项通过；新增2项。
  真实行内占位注解不参与墨迹测量且用户替换字符保留；没有占位注解的用户原文逐字符保留。

- `ui/theme/NexaraMarkdownTypographyTest.kt:12`：3项通过；新增3项。
  正文跟随会话字号并保留一点六倍内部行高且不叠加块外留白；标题字号层级半粗与一点三五倍行高适用于六级标题；代码始终使用等宽十三号与二十号行高。

新增设备测试共 **28 项**，全部写在 `androidTest`，只编译测试 APK，未执行：

| 文件 | 数量 | 断言范围 |
| --- | --- | --- |
| `ui/chat/ChatLayoutTest.kt` | 4 | 默认/1.3倍字体工具行40dp与同行中心线、加号/发送40dp可见与48dp触控、模型/用量回调、Markdown代码头36dp、单一标题与重命名 |
| `ui/chat/ChatPipelineLayoutTest.kt` | 14 | 步骤44dp/文字箭头中心线、自然语言无ID、82%用户气泡/6dp附件/菜单时间、36dp操作行与48dp触控/回调、生成隐藏/不可用动作、短正文触控、1.3倍字号、6/12行展开、历史等待、尾空消息与逐段来源 |
| `ui/chat/ChatApprovalLayoutTest.kt` | 5 | 按钮40dp可见/48dp触控、唯一实心批准/无红色填充、六行参数与展开 |
| `ui/chat/ChatScrollGeometryTest.kt` | 2 | 初始测量、视口/多行输入变化时尾部可见，主动上滑后不夺走滚动 |
| `ui/common/MarkdownNestedCodeLayoutTest.kt` | 2 | 引用/列表长代码滚到末端，尾部字形坐标位于视口内 |
| `ui/common/MarkdownResourceFixtureTest.kt` | 1 | 使用真实Android getString验证AAPT后中英文缩进及AST三层嵌套 |

此外在既有 `RagDetailsAccessibilityTest` 增加两项部分成功语义、真实数量、error Span 与详情可达断言。新尺寸断言容差0.5dp，步骤文字/箭头中心线容差1dp。资源夹具曾尝试 Robolectric 实际 getString，但项目未启用 app 资源加载，故保留真实断言转到设备源集，未修改 Gradle 或用手工字符串伪装 AAPT 证据。主控另从已构建的真实 deviceTest APK 用 `aapt2 dump resources` 提取唯一目标资源，中英文两配置均保留3/6空格的子列表缩进；去掉工具输出统一6空格展示前缀后验证，证据见 `batch2-logs/aapt-markdown-fixture.log`。

以下既有契约按当前用户意图调整，没有删除与视觉无关的业务保护：

- `ChatRenderStateContractTest`：AssistChip 独立顶行改为一条 40dp 工具行、模型与用量入口、bodyLarge 与六行输入；横屏统一 spacedBy 断言改为角色间距与最大阅读宽度，同时保留无初始索引裁剪消息的断言。
- `ChatRouteContractTest`：文档草稿条调用改为一体化附件条及删除回调；模型名缓存链保持，改为从 ChatInputBar 与 AssistantActionRow 所在位置断言，未增加逐消息数据库解析。
- `ReleaseReachableUiLocalizationContractTest`：取消无人使用的旧阶段名/技术摘要资源要求，验证自然语言摘要、失败和结果数量的中英文资源；其余发行入口本地化门禁保留。
- `PipelineBubbleTest`：移除元信息左对齐与旧最小布局高 48dp 的源码契约，替换为右侧操作行/元信息与实际设备触控 bounds 断言；补正文来源、尾部空消息和分段编辑归属。
- `MarkdownWideContentContractTest`：顶层 `children.forEach` 改为过滤后的块遍历，`state.content` 改为稳定本地别名 `renderContent`；保留宽内容宿主、表格真实宽度、代码与嵌套表格识别保护。
- `SmoothStreamContentTest`：固定速率 reducer 测试改为注入时钟状态机的速率、稳定放出、字形、改写、积压与结束行为。突发场景按复核任务书验证2000cps上限与1.2秒内追平；原冲突日志按上节保留。
- `ThinkingTraceTest`、`RagDetailsAccessibilityTest`：步骤布局高 48dp 改为规格 44dp，点击、展开/收起、详情与读屏语义保留；后者另加两个部分成功状态测试。
- `ChatScreenContentStateTest`：重复标题数量改为单一标题，模型/时间改为操作行右侧，任务胶囊布局高改为 28dp；既有会话操作与状态入口断言保留。
- `ApprovalCardTagsTest`：审批按钮使用原标签定位，不再依赖含当前/目标的旧长按钮文案；所有审批回调与执行禁用语义保留。
- `AccessibilitySmokeTest`：模型/用量/附件可见布局改为 40dp，发送/停止改测 `touchBoundsInRoot >=48dp`；40dp 可见区不被误当作触控区。

## 7. 截图基线与逐图验收（初轮）

最终有效基线277张：新增36、更新27、删除2。更新采用两渠道update任务。63张新增/更新图均逐张检查：先生成16页联系表逐项目视；最后两张长文因递归边界修复重出后，主控重新检查原图裁片，另61张SHA完全一致，复用已完成的视觉检查。文件及SHA证据见 `batch2-logs/baseline-final-diff.json`、`visual-reviewed-final.json`。删除的两张为已被411dp新预览替代、且不在当前descriptor中的412dp旧图，没有删除其它基线。

### 新增（36）

相对 `app/src/screenshotTestDebug/reference/` 的目录：`com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/`。

- `chatComposerAttachmentsBatch2Preview_输入图片文档 深中411_bf739bff_0.png`
- `chatComposerEmptyBatch2Preview_输入空态 深中411_1e0619ea_0.png`
- `chatComposerGeneratingBatch2Preview_输入生成中 深中411_29ce6478_0.png`
- `chatComposerLargeFontBatch2Preview_输入放大字号 深中411_87069706_0.png`
- `chatComposerLargeFontBatch2Preview_输入窄屏 深中360_14e3b21e_0.png`
- `chatComposerSixLinesBatch2Preview_输入超过六行 深中411_afaafb0f_0.png`
- `chatComposerTaskBatch2Preview_输入任务状态 深中411_4d9cbec6_0.png`
- `chatComposerTextBatch2Preview_输入文字 深中411_12d11cce_0.png`
- `chatPageAdaptiveBatch2Preview_会话完整 深中360_9270378e_0.png`
- `chatPageAdaptiveBatch2Preview_会话完整 深中平板_de570f39_0.png`
- `chatPageDarkBatch2Preview_会话完整 深中411_eb195068_0.png`
- `chatPageEmptyBatch2Preview_会话空态 深中411_14dfa456_0.png`
- `chatPageEnglishBatch2Preview_会话完整 深英411_11413684_0.png`
- `chatPageLightBatch2Preview_会话完整 浅中411_d1890a42_0.png`
- `chatTopBarLongBatch2Preview_顶栏长标题 深中411_26c90513_0.png`
- `chatTopBarShortBatch2Preview_顶栏短标题 深中411_1236deed_0.png`

相对 `app/src/screenshotTestDebug/reference/` 的目录：`com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/`。

- `approvalExecutedBatch2Preview_已执行审批 深中411_d43d33cc_0.png`
- `approvalLargeFontBatch2Preview_单项审批 大字深中411_dc1bc9c0_0.png`
- `approvalMultipleBatch2Preview_多项审批 深中411_736f9259_0.png`
- `approvalSingleBatch2Preview_单项审批 深中411_09ec2598_0.png`
- `retrievalCompletedBatch2Preview_检索完成 深中411_6793ac38_0.png`
- `retrievalFailedBatch2Preview_检索失败 深中411_a34591ee_0.png`
- `retrievalPartialKnowledgeBatch2Preview_知识库部分来源失败 深中411_9ace520c_0.png`
- `retrievalPartialWebBatch2Preview_网页部分来源失败 深中411_e6893d36_0.png`
- `retrievalRunningBatch2Preview_检索进行中 深中411_17547905_0.png`

相对 `app/src/screenshotTestDebug/reference/` 的目录：`com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/`。

- `chatMarkdownBatch2DarkChinese_助手正文 长回复 深色中文_25d52f12_0.png`
- `chatMarkdownBatch2HtmlArtifact_助手正文 HTML长代码 深色中文_63d15949_0.png`
- `chatMarkdownBatch2NarrowLargeFont_助手正文 长回复 窄屏大字_4dbb2d04_0.png`

相对 `app/src/screenshotTestDebug/reference/` 的目录：`com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/`。

- `chatBatch2AssistantActionsDarkPreview_助手操作行 完成_136cb8f8_0.png`
- `chatBatch2ExpandedThinkingDarkPreview_思考展开 阅读竖线_1af44649_0.png`
- `chatBatch2ExpandedToolsDarkPreview_工具展开 长参数与结果_b449bf94_0.png`
- `chatBatch2StepStatesDarkPreview_步骤行 思考与工具各状态_a81cc86d_0.png`
- `chatBatch2StepsLargeFontDarkPreview_步骤行 130%字号_18a3ecad_0.png`
- `chatBatch2UserMessagesDarkPreview_用户消息 单行多行附件_6cee3261_0.png`

相对 `app/src/screenshotTestDebug/reference/` 的目录：`com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/`。

- `compactStepsDarkPreview_紧凑步骤 深色中文_cb80b7c1_0.png`
- `compactStepsLightPreview_紧凑步骤 浅色英文_a4e31dc2_0.png`

### 更新（27）

相对 `app/src/screenshotTestDebug/reference/` 的目录：`com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/`。

- `attachmentActionsAnchoredReleasePreview_Attachment actions anchored_06db5765_0.png`
- `attachmentActionsChineseLargeFontReleasePreview_Attachment actions Chinese font scale 2_2ce0607c_0.png`
- `attachmentActionsCompactViewportReleasePreview_Attachment actions compact viewport_1c28da71_0.png`
- `attachmentActionsLargeFontReleasePreview_Attachment actions font scale 2_dea1d01b_0.png`
- `attachmentActionsRtlReleasePreview_Attachment actions RTL_32298043_0.png`
- `chatApprovalTabletReleasePreview_Chat approval tablet English_893d1f95_0.png`
- `chatApprovalWritePreviewReleasePreview_Chat approval write preview Chinese_dbbe7da6_0.png`
- `chatErrorLargeFontReleasePreview_Chat error font scale 2_1dd83379_0.png`
- `chatFullContextDocumentsChineseReleasePreview_Chat full context documents Chinese_85b07ee7_0.png`
- `chatFullContextDocumentsEnglishLargeFontReleasePreview_Chat full context documents English large font_35ac1c07_0.png`
- `chatLargeFontReleasePreview_Chat large font scale 2_510f2739_0.png`
- `chatLongModelMetadataReleasePreview_Chat long model metadata_5a74c56c_0.png`
- `chatReadyReleasePreview_Chat ready with metadata_a90c576d_0.png`
- `chatReasoningModelReleasePreview_Chat reasoning metadata_13d8936e_0.png`
- `chatTaskGeneratingReleasePreview_Chat task generating Chinese_88fa6d34_0.png`
- `chatTaskPendingLargeFontReleasePreview_Chat task pending Chinese font scale 2_99b9eaf5_0.png`
- `chatTaskPendingReleasePreview_Chat task pending Chinese_3db1c0ae_0.png`
- `docEditorRichMarkdownPreviewEnglishLightReleasePreview_DocEditor rich markdown preview English light_f71a33a1_0.png`
- `docEditorRichMarkdownPreviewEnglishReleasePreview_DocEditor rich markdown preview English_56bfef50_0.png`
- `docEditorSplitEnglishTabletReleasePreview_DocEditor split English tablet_e8860e5e_0.png`
- `emptyChatEnglishLandscapeReleasePreview_Empty chat English landscape_a72a9f74_0.png`
- `emptyChatReleasePreview_Empty chat English_085f32cb_0.png`
- `ragCardOutcomesReleasePreview_RAG card outcomes Chinese_1c327bf9_0.png`
- `streamingChatChineseLandscapeReleasePreview_Streaming chat Chinese landscape_50d024bd_0.png`
- `streamingChatReleasePreview_Streaming chat Chinese_f65d4631_0.png`

相对 `app/src/screenshotTestDebug/reference/` 的目录：`com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/`。

- `compactStepsNarrowDarkPreview_紧凑步骤 窄屏大字深色_22927655_0.png`
- `compactStepsNarrowLightPreview_紧凑步骤 窄屏大字浅色_86cb35ea_0.png`

### 删除（2）

相对 `app/src/screenshotTestDebug/reference/` 的目录：`com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/`。

- `compactStepsDarkPreview_紧凑步骤 深色中文_29b4f929_0.png`
- `compactStepsLightPreview_紧凑步骤 浅色英文_4ea00e09_0.png`

| 9.3 状态 | 自检结论与证据边界 |
| --- | --- |
| 新会话空态 | 单一标题、返回/文件/更多，输入区四要素同容器；短/长标题与空页图逐张通过 |
| 输入多行 | 九行fixture只显示六行，工具行不换行；内部滚动交互设备用例仅编译 |
| 图片/文档附件 | 输入上方56dp、圆角12dp、删除入口；用户历史附件在气泡上方6dp，两类分别检查 |
| 生成中思考 | 无底色步骤、自然语言计时、文字微光与推理尾部；发送变停止 |
| 正文流式 | 注入时钟及真Compose注解测试通过；无10秒设备录屏，特殊渲染器渐显边界见第4节 |
| 工具运行/完成/失败 | 单/双/三类别、运行/历史等待/失败均有截图，折叠无ID/参数，失败仅error文字 |
| 工具展开 | 友好名/ID/耗时、六/十二行、省略与两个展开按钮可见；点击展开的设备断言仅编译 |
| 审批 | 单/多调用/已执行/1.3字号均检查，无红色填充，只有批准为实心，全部批准为文字按钮 |
| 长回复 | h1–h3、段落、三层列表及退栈、代码、表格、引用、行内代码、多空行均覆盖；像素读数小于2px偏差，真实值见第3节 |
| 完成后助手 | 三个图标和右侧模型/时间；长模型截断仍保留时间；用户气泡无下方时间 |
| 上下文面板 | 原面板与压缩入口源码接线保留，新直接渲染设备测试覆盖点击；未运行设备，不记为运行通过 |
| 横屏/平板 | 360dp、1000dp与既有横屏图逐张检查，消息流/输入区最大720dp居中；角色间距纯函数通过 |
| 浅色/英文/130% | 整页浅中/深英及130%窄屏均检查；浅色/深色元信息按新颜色实测对比度；无动态色设备结论 |

完整页面基线以主机Layoutlib渲染，不是模拟器实拍；上述“检查”均指原图/联系表与源码，不把截图入口当成真实IME或触控运行结果。

## 8. 无障碍、适配与业务边界

- 步骤行为 44dp 整行触控，状态使用展开/收起 `stateDescription`；13sp/18sp 正常字重，放大字号允许按内容增长。模型胶囊可见 32dp、外层 40dp 触控，是规格声明的例外。
- 加号与发送可见 40dp、助手操作行可见 36dp、代码头部可见 36dp、审批按钮可见 40dp；这些视觉尺寸不是额外的触控例外，图标/按钮保留 Compose 48dp 触控扩展。Android 测试对关键动作的实际 touch bounds 作断言，当前只完成编译。
- 状态条可见 28dp，与容器间隔 8dp；其任务胶囊复用点击语义。工具行固定 40dp 单行，模型优先截断；130% 字号及 360dp 基线作目视复核，宽屏内容居中。
- 助手元信息与展开工具ID使用不加透明度的onSurfaceVariant，浅色与深色截图实测对比度见第11节。固定主题截图不替代动态取色与设备读屏验收。
- 原始 `MessageContextMenu` 的用户分支包含时间、复制、重新发送、分支、删除，没有用户编辑回调；任务书将“用户编辑”列为既有项与源码不符。保持原业务可用性，没有为视觉任务新增用户编辑业务。助手代码编辑、长按操作与分支/删除入口保持，并按实际正文消息绑定回调。
- 未新增偏好键、修改默认值、迁移 Room、改动 MessageManager 节流/防抖、权限、依赖或渠道策略。模型入口使用会话设置默认模型页，初始标签参数默认 0 保持原调用行为。
- 不启动/操作设备或模拟器，不做提交/推送、tag、PR、Release、发布、HLG append，不读取任务禁止路径。没有停止守护进程。

## 9. 第 6 节命令与输出（初轮验证）

全部 Gradle 命令在 `native-ui/` 执行，Python 在仓库根执行。受当前文件系统沙箱限制，Kotlin 编译增加 `-Pkotlin.compiler.execution.strategy=in-process`，避免尝试在不可写用户 Library 中建立 daemon 标记；JVM 测试另用任务级 `-I ../.agent/tmp-agent-reports/batch2-logs/test-sandbox.init.gradle`。该脚本只给 Test JVM 指定已有 Robolectric 本地依赖目录与 offline 模式，目录内为既有缓存 JAR 的符号链接，避免不可写的 `~/.robolectric-download-lock`。未改变系统 HOME、仓库构建配置、测试筛选、依赖版本或测试断言。

### 双渠道 JVM 与 Sideload Lint

日志：`batch2-logs/quality-final-link.log`。

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process -I ../.agent/tmp-agent-reports/batch2-logs/test-sandbox.init.gradle :app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:lintSideloadDebug
```

```text
> Task :app:lintReportSideloadDebug
Wrote HTML report to file:///Users/promenar/Codex/Nexara-wt-redesign2/native-ui/app/build/reports/lint-results-sideloadDebug.html

> Task :app:lintSideloadDebug

BUILD SUCCESSFUL in 3m 53s
73 actionable tasks: 20 executed, 53 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

### 双渠道截图校验

日志：`batch2-logs/screenshots-validate-link.log`。

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process :app:validateSideloadDebugScreenshotTest :app:validatePlayDebugScreenshotTest
```

```text
> Task :app:generatePlayDebugAssets UP-TO-DATE
> Task :app:mergePlayDebugAssets UP-TO-DATE
> Task :app:packagePlayDebugScreenshotTestForScreenshotTest UP-TO-DATE
> Task :app:validatePlayDebugScreenshotTest

BUILD SUCCESSFUL in 2m 18s
63 actionable tasks: 6 executed, 57 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

### 双渠道 Debug APK

日志：`batch2-logs/debug-apk-final.log`。

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process :app:assembleSideloadDebug :app:assemblePlayDebug
```

```text
> Task :app:createPlayDebugApkListingFileRedirect
> Task :app:assemblePlayDebug

[Incubating] Problems report is available at: file:///Users/promenar/Codex/Nexara-wt-redesign2/native-ui/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 4m 56s
87 actionable tasks: 32 executed, 10 from cache, 45 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

### 设备主包与测试包编译

日志：`batch2-logs/device-apk-accepted.log`。

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process -Pnexara.deviceE2e=true -Pnexara.deviceE2eAbi=arm64-v8a :app:assembleSideloadDeviceTest :app:assembleSideloadDeviceTestAndroidTest
```

```text
> Task :app:createSideloadDeviceTestAndroidTestApkListingFileRedirect
> Task :app:assembleSideloadDeviceTestAndroidTest

[Incubating] Problems report is available at: file:///Users/promenar/Codex/Nexara-wt-redesign2/native-ui/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 24s
80 actionable tasks: 12 executed, 68 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

### 结构化结果

| 任务 | 总数 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| `testSideloadDebugUnitTest` | 3856 | 0 | 0 | 25 |
| `testPlayDebugUnitTest` | 3832 | 0 | 0 | 25 |
| `validateSideloadDebugScreenshotTest` | 277 | 0 | 0 | 0 |
| `validatePlayDebugScreenshotTest` | 277 | 0 | 0 | 0 |

JVM 中本任务相关14类93项均无失败、错误或跳过；25项条件性跳过的逐项清单见 `batch2-logs/quality-final-results.json`。Lint 为 **0 Error/Fatal、651 Warning、11 Hint**；门禁通过不代表无警告，也未将全部警告未经比较认定为基线。日志中的 JVM class sharing 与 image decoder 提示没有对应失败测试。

### 仓库根 Python 门禁

```bash
python3 scripts/ci/check-text-wrapping.py
```

```text
文本换行门禁通过
```

```bash
python3 scripts/ci/check-distribution-manifests.py
```

```text
发行清单检查通过（debug）：play 无所有文件访问权限，sideload 包含；包身份一致
```

### 构建产物与范围核查

以下路径相对于仓库根，文件仅用于主机产物复核，未安装：

| APK | 字节数 | SHA-256 |
| --- | --- | --- |
| `native-ui/app/build/outputs/apk/sideload/debug/app-sideload-debug.apk` | 150562970 | `1a67588a87f31be3f2e859322b01d096f60b659e94f1550833ac655f559e72a4` |
| `native-ui/app/build/outputs/apk/play/debug/app-play-debug.apk` | 150464638 | `01373356180b62f4064d79e127d69b10717da4c8162b811e8d3280796fea9870` |
| `native-ui/app/build/outputs/apk/sideload/deviceTest/app-sideload-deviceTest.apk` | 136062363 | `bf9939a49b5bd9bfda81c8dad9288b0284f28dc204a3414bac009dbb81003217` |
| `native-ui/app/build/outputs/apk/androidTest/sideload/deviceTest/app-sideload-deviceTest-androidTest.apk` | 8605174 | `a5236ca83eae3beb98359ef3aea2368828801ca05d83827ae93123966b6df25b` |

既有普通Debug配置启用本地推理，其CMake依赖使用llama.cpp的master；此次实际解析为 `797de63b2fa7396dcc8caca76834c699ac84e1c3`，已完成原生编译，未运行推理。未修改此构建配置或依赖声明。

`git diff --check` 返回0且无输出；暂存区为空。范围检查126个新增/修改/删除文件，禁止路径、构建配置、MessageManager与handover均无变更，见 `batch2-logs/final-scope-check.json`。

指定静态核查第一条输出：

```text
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/components/FilesPanel.kt:743:                                imageVector = Icons.Rounded.Edit,
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1291:                                imageVector = Icons.Rounded.Edit,
native-ui/app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt:742:                Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, editDescription, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
```

三个 Edit 分别属于文件重命名菜单、会话更多菜单中的重命名、代码块编辑/保存；标题旁无Edit。streamingTailFade、STREAM_TAIL_FADE_DURATION_MS、ChatInputTopBar均无生产命中。

指定静态核查第二条输出：

```text
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:892:        horizontalArrangement = Arrangement.spacedBy(8.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:905:                horizontalArrangement = Arrangement.spacedBy(6.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:942:            horizontalArrangement = Arrangement.spacedBy(4.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:976:            horizontalArrangement = Arrangement.spacedBy(4.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1035:        verticalArrangement = Arrangement.spacedBy(NexaraSpacing.Small),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1166:        horizontalArrangement = Arrangement.spacedBy(NexaraSpacing.Small),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1387:        horizontalArrangement = Arrangement.spacedBy(8.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1632:        verticalArrangement = Arrangement.spacedBy(24.dp)
```

分别为状态条、任务胶囊、模型、用量、面板、明细行、附件条及骨架屏布局；消息LazyColumn没有spacedBy。原有52种UiTags/testTag引用均保留，现有70种引用，集合差为空，证据见batch2-logs/tag-scope-check.json。

## 10. 文档同步、限制与治理

DIA 已同步 `CHANGELOG.md` 的 `[Unreleased] / 会话界面视觉重设计`、`README.md`、`design-qa.md`、`docs/context-management.md`、`docs/ADR/ADR-020-layered-model-metadata-registry.md` 与规格顶部状态。正文描述当前界面和真实验证边界；旧发行/设备/历史验收记录按其日期保留，未当作当前验收凭据。

按任务书不追加 `.agent/handover.md`；恢复时已读取项目规则、registry、生成索引与相关原记录。PDEC 检查为 approved、valid、execution_ready，drift 为空；仅使用已批准的本机 Android 构建例外，没有修改契约。

未完成的验收与明确偏离：

1. 已按复核裁定完成：2000cps上限与2000字符/1.2秒判据一致，回归通过。
2. 已按复核裁定完成：元信息和内部ID去掉透明度，固定浅色/深色主题对比度均超过4.5:1，实测见第11节。
3. 逐字alpha只覆盖走注解的普通Markdown末块，特殊代码/表格/富媒体路径仅匀速或完整到达；不把此范围扩大为全部内容渐显。
4. 没有设备运行，因此设备布局断言、真实IME/导航栏、TalkBack、OEM、动态取色对比度、真实附件打开/图片lightbox、HTML预览/全屏/PNG导出与10秒流式录屏均无当前运行证据。HTML源码截图使用不触发WebView的fixture；真实可渲染HTML在Layoutlib中因缺少WebView.getSettings实现不能出图，现有功能接线保留并编译。
5. 墨迹裁剪使用真实基础字体/字号/字重的Paint边界并向外取整以保留边缘；混合字体span、emoji、组合字在所有设备字体下的墨迹差异未穷尽。像素读数保留，不能用几何单测宣称所有字形零像素误差。
6. 旧用户菜单没有编辑业务；按任务“不改变不确定业务”保留原有菜单能力，未新增编辑回调。自动折行的溢出状态需完成onTextLayout，显式多行已有首帧展开入口。

HLG：按任务书未写handover；交付事实、对照和可复核日志集中保存在本报告及 `batch2-logs/`。Git：未commit/push，成果保留在工作树供上级集成。

## 11. 复核问题修正

依据 `brief-redesign-batch2-r2.md` 第2–4节完成修正。源码路径相对于 `native-ui/app/src/main/java/com/promenar/nexara/ui/`；截图相对于 `native-ui/app/src/screenshotTestDebug/reference/com/promenar/nexara/ui/chat/`。所有读数取自最终420dpi原图，1dp=2.625px；原图哈希和坐标见 `batch2-r2-logs/visual-measurements.json`、`additional-pixels.json`。初轮旧读数与命令输出保留为历史。

### 11.1 六项设备失败的根因、修改与证据

设备失败来自任务书提供的API31实测。主控完成源码定位、相应修正、回归用例及测试APK编译；按任务书禁止操作设备，**没有把六项用例登记为设备重跑通过**。

| 任务书第2节用例 | 根因与修正 | 当前落点与对应截图 |
| --- | --- | --- |
| `ChatImeInteractionTest#lastMessage_remainsVisible_whenImeShows` | 非生成态没有观察输入区首次实测/多行高度与列表底部padding变化；旧校正可能用旧视口或旧留白执行后不再触发。IME原监听也只在键盘可见时观察inset/viewport，没有覆盖初始化与afterContentPadding。保留insets函数与imePadding，统一观察实测输入高度、inset、已布局viewport、afterContentPadding及item数量；仅在autoFollowEnabled时校正活动尾项。 | `chat/ChatScreen.kt:318`、`:404`；`ChatComposerBatch2ScreenshotTestKt/chatPageDarkBatch2Preview_会话完整 深中411_eb195068_0.png` 为静态入口，不能证明IME。新增 `androidTest/ui/chat/ChatScrollGeometryTest.kt` 验证尺寸变化；两个原IME用例不改。 |
| `ChatImeInteractionTest#openingIme_doesNotStealDeliberateUpScroll` | 任务书所指145行是主动上滑前的尾消息可见断言，和首次高度测量缺少跟随属于同一问题；并非据此认定上滑后的逻辑已强拉到底。保留UserInput切断autoFollow，几何监听不恢复该标志。审批/压缩反馈存在时对齐对应尾项。 | 同上；`ChatScrollGeometryTest#geometryChangesRespectDeliberateUpScroll`、原IME测试保留，上滑后尾消息不可见断言不放宽。 |
| `ChatScreenContentStateTest#approvalState_rendersToolDetailsAndDispatchesApprove` | 友好名称替代内部ID后，旧测试仍要求卡片内出现write_file/delete_file。按任务书允许的等价语义，断言两项友好名称、两个实际参数路径、风险说明与审批原因，并点击原批准标签验证回调；没有删除审批保护或添加无意义ID。 | `chat/ChatInlineComponents.kt:954`；改写 `androidTest/ui/chat/ChatScreenContentStateTest.kt:191`；`ChatInlineBatch2ScreenshotTestKt/approvalMultipleBatch2Preview_多项审批 深中411_736f9259_0.png`。 |
| `ChatPipelineLayoutTest#stepRowIs44DpAndArrowSharesTextCenter` | min44dp叠加上下13dp padding与文字按像素取整后的18sp高度，得到44.57dp。改用最小44dp外框、无垂直padding的居中Row；默认与130%均不会被padding撑高。 | `chat/CompactStepRow.kt:97`；`ChatPipelineBatch2ScreenshotTestKt/chatBatch2StepStatesDarkPreview_步骤行 思考与工具各状态_a81cc86d_0.png`。原44dp/0.5dp及箭头中心1dp断言保留。 |
| `ChatPipelineLayoutTest#userBubbleUsesAtMost82PercentAndAttachmentsAreAboveWithSixDpGap` | 原标签仍在，但位于combinedClickable合并后的子语义中；不是气泡消失。测试对气泡和附件使用unmerged tree，82%、右缘与6dp三项原几何断言保持。 | `chat/PipelineBubble.kt:955`；`androidTest/ui/chat/ChatPipelineLayoutTest.kt`；`ChatPipelineBatch2ScreenshotTestKt/chatBatch2UserMessagesDarkPreview_用户消息 单行多行附件_6cee3261_0.png`（几何未变，沿用初轮图）。 |
| `ChatApprovalLayoutTest#approvalActionsHave40DpVisualHeightAnd48DpTouchHeight` | minimumInteractiveComponentSize主要保留布局，原Button在显式40dp约束下没有为被测标签提供至少48dp touch bounds。40dp可见Box与clickable合为同一层，使用Material颜色、形状和描边；平台最小命中范围向外扩展，按钮行仍预留48dp。 | `chat/ChatCompactControls.kt:40`、`chat/ChatInlineComponents.kt:1014`；`ChatInlineBatch2ScreenshotTestKt/approvalSingleBatch2Preview_单项审批 深中411_09ec2598_0.png`。原40dp可见、≥47.5dp触控断言保留，仅编译；实际命中需设备重跑确认。 |

四项任务书列明的既有基线失败未修复、未删除、未放宽；未运行设备，不能对其当前结果作新结论。

### 11.2 第3节逐条视觉修正与像素实测

| 任务书项 | 修改位置与终态 | 最终截图与实测 |
| --- | --- | --- |
| 3.1 输入区 | `chat/ChatScreen.kt:1435`、`:1477`、`:1548`；`chat/ChatCompactControls.kt:25`。移除输入框48dp最小布局高，统一bodyLarge输入/占位样式；发送与加号使用40dp可见框，点击扩展不参与工具行高度；加号图标左移到输入文字列。 | `ChatComposerBatch2ScreenshotTestKt/chatComposerTextBatch2Preview_输入文字 深中411_12d11cce_0.png`：容器y=21–250，230px=87.62dp；上32px+输入实际61px+间隔11px+工具105px+下21px=230px。默认字体实际单行约23.24dp，含逐段像素取整，容器接近规格约86dp。发送圆形含抗锯齿边缘x=910–1014、y=125–229，直径105px=40dp；右侧32px=12.19dp，下侧21px=8dp。加号墨迹与输入墨迹左缘均x=65，偏差0px。40dp加号布局与48dp触控由保留设备断言验证，截图不证明触控。 |
| 3.2 助手操作行 | `chat/PipelineBubble.kt:597`，Row按17.5dp向正文列左侧扩展，按钮仍48dp，不移动元信息右缘。 | `ChatComposerBatch2ScreenshotTestKt/chatPageDarkBatch2Preview_会话完整 深中411_eb195068_0.png`：正文墨迹左缘x=55，复制墨迹x=53，差2px=0.76dp，≤1dp。 |
| 3.3 步骤行 | `chat/CompactStepRow.kt:61`、`:75`、`:97`，运行态无前导圈；onSurfaceVariant底色上onSurface微光，1200ms LinearEasing，系统动画0和预览静态。`chat/PipelineBubble.kt:572` 预览紧跟标签12dp、溢出时保留最新文字并在左24dp渐隐，右侧留给箭头。 | `ChatPipelineBatch2ScreenshotTestKt/chatBatch2StepStatesDarkPreview_步骤行 思考与工具各状态_a81cc86d_0.png`：相邻同构行首墨迹y差全部116px=44.19dp；运行/完成左缘54/55px，差1px=0.38dp。首行标签墨迹中心101.5px、箭头100.5px，差1px=0.38dp；12dp布局间隔约32px、24dp渐隐宽63px，新增布局断言保留这两个几何关系。预览图仅静态帧，未声称验证了真机动画时序。 |
| 3.4 展开全部 | `chat/ChatCompactControls.kt:71`、`chat/PipelineBubble.kt:821`、`chat/ChatInlineComponents.kt:825`，统一labelLarge/primary文字链接，无水平padding；距内容布局4dp，平台48dp命中范围不扩大容器，参数块底部padding10dp。 | `ChatPipelineBatch2ScreenshotTestKt/chatBatch2ExpandedToolsDarkPreview_工具展开 长参数与结果_b449bf94_0.png`：参数内容列x≈79，链接墨迹x=80（1px）；参数表面末端y=618，链接末墨迹y=584，含文字行框余白34px=12.95dp。`ChatInlineBatch2ScreenshotTestKt/approvalSingleBatch2Preview_单项审批 深中411_09ec2598_0.png`：内容列x≈121，链接墨迹x=122；末端782，末墨迹748，同为34px。实际底部布局padding26px≈10dp，其余约8px来自labelLarge行框；没有旧TextButton额外的30dp底部空白。触控不由PNG推定。 |
| 3.5 审批卡 | `chat/ChatInlineComponents.kt:816`、`:825`、`:954`。首行友好工具名·目标；预览路径相同只留动作；每调用一段风险说明，无说明才回退标签；永久删除/联网风险事实不能被Execution mode技术原因覆盖，单项/多项/已执行同一规则。 | `ChatInlineBatch2ScreenshotTestKt/approvalSingleBatch2Preview_单项审批 深中411_09ec2598_0.png`、`approvalMultipleBatch2Preview_多项审批 深中411_736f9259_0.png`、`approvalExecutedBatch2Preview_已执行审批 深中411_d43d33cc_0.png`。卡片RGB(28,27,29)，参数RGB(42,42,44)；单项示例目标路径1处、风险1段，风险墨迹高约33px，参数区表面y=381–782，背景层级可辨。 |
| 3.6 代码与表格 | `common/MarkdownText.kt:716`、`:785`，第一方高亮Text不再嵌套第三方滚动/背景；保留Highlights着色、编辑/复制/HTML入口。代码正文16/4/12dp，表头body同字号、Medium（`renderer/TableWidget.kt:159`），这是该明确要求所需的最小renderer改动。 | `ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownBatch2DarkChinese_助手正文 长回复 深色中文_25d52f12_0.png`：代码表面y=1202–1429，头部95px≈36dp，下沿至首行墨迹11px=4.19dp，末行墨迹至底32px=12.19dp；语言名/正文列约x=95/96，左16dp（42px）。表头/正文字号均15sp=39.375px，所测不同字形墨迹高36/38px，表头已不大于正文。新增 `chatMarkdownCodeInkRegression_代码首列字形 与未裁剪文字对照` 图对照p/j/g/y/f，未见裁切。 |
| 3.7 正文块间距 | `common/MarkdownText.kt:451`、`:1153`；段落16、标题前24/后10、列表边界12、列表项10、媒体/引用边界16dp。多行内部行距保留。 | 默认中文长回复的12组墨迹读数见下表；360dp/130%长回复图也逐图检查，内部行距保持。列表末墨迹1158→代码表面1202，中间43px=16.38dp；代码表面1429→表格1472，中间42px=16dp。容器外缘计量不能混同容器内部行距。 |
| 3.8 对比度 | `chat/PipelineBubble.kt:609`、`:791`；元信息和内部ID均为无透明度onSurfaceVariant，步骤文字85%保持。 | 浅/深截图实测见下表；两类文本均达到4.5:1。 |

代码裁切根因：0.41.0的 `MarkdownHighlightedCode` 自带 `MarkdownCodeBackground`、上下各8dp与 `horizontalScroll(...).padding(codeBlockPadding)`；当前codeBlockPadding为0，内侧裁剪边界没有字形余量，外部padding无法保护该内层边缘。移除内层滚动裁剪后，第一方16dp内边距保护墨迹，`println` 首字母完整。实际依赖源码已核对并保存在 `batch2-r2-logs/dependency-sources/MarkdownHighlightedCode.kt`，签名为 `@Composable fun MarkdownHighlightedCode(code: String, language: String?, style: TextStyle, highlightsBuilder: Highlights.Builder, showHeader: Boolean, immediate: Boolean)`。

墨迹边界向外取整会留下约1–2物理像素保护余量；以下保留真实读数，不宣称零误差，也没有反向取整裁字。

| 同一长回复截图边界 | 上块墨迹y | 下块墨迹y | 空白px | dp | 目标dp |
| --- | --- | --- | --- | --- | --- |
| 标题后 | 32–82 | 110–146 | 27 | 10.29 | 10 |
| 段落间 | 173–209 | 254–290 | 44 | 16.76 | 16 |
| 标题前 | 317–353 | 418–462 | 64 | 24.38 | 24 |
| 标题后首个列表 | 418–462 | 495–531 | 32 | 12.19 | 12 |
| 同级列表项 | 495–531 | 560–596 | 28 | 10.67 | 10 |
| 进入子列表 | 560–596 | 625–661 | 28 | 10.67 | 10 |
| 进入第三层 | 690–726 | 754–790 | 27 | 10.29 | 10 |
| 退出第三层 | 754–790 | 819–855 | 28 | 10.67 | 10 |
| 退出子列表 | 819–855 | 884–920 | 28 | 10.67 | 10 |
| 列表后标题 | 884–920 | 985–1025 | 64 | 24.38 | 24 |
| 三级标题后列表 | 985–1025 | 1057–1093 | 31 | 11.81 | 12 |
| 无序列表同级 | 1057–1093 | 1122–1158 | 28 | 10.67 | 10 |

| 主题与文本 | 前景RGB | 背景RGB | 实测对比度 |
| --- | --- | --- | --- |
| 浅色元信息 | (70, 70, 79) | (251, 248, 251) | 8.8566:1 |
| 深色元信息 | (199, 196, 215) | (19, 19, 21) | 10.8739:1 |
| 浅色内部ID | (70, 70, 79) | (251, 248, 251) | 8.8566:1 |
| 深色内部ID | (199, 196, 215) | (19, 19, 21) | 10.8739:1 |

元信息分别取完整会话浅色/深色图 `(650,910)–(1035,954)`；ID分别取新浅色工具展开图与深色长参数图的ID区域，坐标及前景实心像素频次见JSON。按sRGB相对亮度与 `(Lmax+0.05)/(Lmin+0.05)` 计算，不取抗锯齿灰边作为前景。

### 11.3 规格、测试与独立复核

- 规格5.4、5.5、5.6、5.8和第8节已同步终态表述；StreamRevealer突发2000字符在2000cps上限内、1.2秒追平。
- `MarkdownReadingLayoutTest` 两项新间距断言在修正前失败，修正后通过；`ApprovalRiskSummaryTest` 两项与 `MarkdownNestedWideContentTest` 两项在修正前共三失败，修正后通过。日志为 `red-reading-layout.log`、`green-reading-layout.log`、`red-review-regressions.log`、`green-review-regressions.log`。
- 设备新增7项：`ChatScrollGeometryTest` 2、`MarkdownNestedCodeLayoutTest` 2、`ChatApprovalLayoutTest` 2、`ChatPipelineLayoutTest` 1。另保留六项原失败的原意图，未放宽44dp、82%、6dp、40/48dp等断言。测试双包已编译，未执行。嵌套代码测试用5000ms有界waitUntil等待后台解析完成，再滚至最大范围并验证末尾字形坐标。
- 高风险实现由原生只读 `review_r2` 串行复核。发现并关闭了审批风险被技术原因覆盖、嵌套代码缺少滚动宿主、设备测试解析竞态三项；最终无待修正的静态发现，完整记录见 `independent-review.md`。主控复核源码、diff、回归输出、全部受影响图与像素，并复跑必要命令。
- 纯视觉颜色/间距未增加镜像单元测试，使用截图与设备布局断言；新增纯风险规则与宽节点判断均有实际逻辑测试。

### 11.4 最终截图清单与目视检查

相对复核开始时的已有工作树，新增3张、更新56张、删除0张。主控逐张检查了59张最终图，联系表位于 `batch2-r2-logs/visual-final/contact-01.png` 至 `contact-15.png`，代码、输入、浅色ID与审批另检查原图/裁片。每图哈希与检查状态见 `visual-reviewed.json`；未批量接受未经目视检查的新基线。无关且哈希不变的初轮图不重复生成新结论。

**新增（3）**

- `com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownNestedCodeRegression_引用与列表长代码 可横向滚动_a083d8eb_0.png`
- `com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownCodeInkRegression_代码首列字形 与未裁剪文字对照_6edcef63_0.png`
- `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2ToolIdLightContrastPreview_展开工具标识 浅色对比度_016be1c6_0.png`

**更新（56）**

- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/attachmentActionsAnchoredReleasePreview_Attachment actions anchored_06db5765_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatLongModelMetadataReleasePreview_Chat long model metadata_5a74c56c_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/streamingChatChineseLandscapeReleasePreview_Streaming chat Chinese landscape_50d024bd_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragCardOutcomesReleasePreview_RAG card outcomes Chinese_1c327bf9_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatApprovalTabletReleasePreview_Chat approval tablet English_893d1f95_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatReadyReleasePreview_Chat ready with metadata_a90c576d_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatFullContextDocumentsEnglishLargeFontReleasePreview_Chat full context documents English large font_35ac1c07_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/attachmentActionsCompactViewportReleasePreview_Attachment actions compact viewport_1c28da71_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorRichMarkdownPreviewEnglishReleasePreview_DocEditor rich markdown preview English_56bfef50_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/streamingChatReleasePreview_Streaming chat Chinese_f65d4631_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatLargeFontReleasePreview_Chat large font scale 2_510f2739_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatReasoningModelReleasePreview_Chat reasoning metadata_13d8936e_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/attachmentActionsChineseLargeFontReleasePreview_Attachment actions Chinese font scale 2_2ce0607c_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/attachmentActionsLargeFontReleasePreview_Attachment actions font scale 2_dea1d01b_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorRichMarkdownPreviewEnglishLightReleasePreview_DocEditor rich markdown preview English light_f71a33a1_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorSplitEnglishTabletReleasePreview_DocEditor split English tablet_e8860e5e_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatTaskGeneratingReleasePreview_Chat task generating Chinese_88fa6d34_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatTaskPendingReleasePreview_Chat task pending Chinese_3db1c0ae_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/emptyChatEnglishLandscapeReleasePreview_Empty chat English landscape_a72a9f74_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatFullContextDocumentsChineseReleasePreview_Chat full context documents Chinese_85b07ee7_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/emptyChatReleasePreview_Empty chat English_085f32cb_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatErrorLargeFontReleasePreview_Chat error font scale 2_1dd83379_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatApprovalWritePreviewReleasePreview_Chat approval write preview Chinese_dbbe7da6_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/attachmentActionsRtlReleasePreview_Attachment actions RTL_32298043_0.png`
- `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatTaskPendingLargeFontReleasePreview_Chat task pending Chinese font scale 2_99b9eaf5_0.png`
- `com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownBatch2DarkChinese_助手正文 长回复 深色中文_25d52f12_0.png`
- `com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownBatch2NarrowLargeFont_助手正文 长回复 窄屏大字_4dbb2d04_0.png`
- `com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownBatch2HtmlArtifact_助手正文 HTML长代码 深色中文_63d15949_0.png`
- `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2AssistantActionsDarkPreview_助手操作行 完成_136cb8f8_0.png`
- `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2StepStatesDarkPreview_步骤行 思考与工具各状态_a81cc86d_0.png`
- `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2StepsLargeFontDarkPreview_步骤行 130%字号_18a3ecad_0.png`
- `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2ExpandedToolsDarkPreview_工具展开 长参数与结果_b449bf94_0.png`
- `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2ExpandedThinkingDarkPreview_思考展开 阅读竖线_1af44649_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatPageEnglishBatch2Preview_会话完整 深英411_11413684_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatComposerLargeFontBatch2Preview_输入放大字号 深中411_87069706_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatPageAdaptiveBatch2Preview_会话完整 深中平板_de570f39_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatPageDarkBatch2Preview_会话完整 深中411_eb195068_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatComposerAttachmentsBatch2Preview_输入图片文档 深中411_bf739bff_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatComposerLargeFontBatch2Preview_输入窄屏 深中360_14e3b21e_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatPageAdaptiveBatch2Preview_会话完整 深中360_9270378e_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatPageEmptyBatch2Preview_会话空态 深中411_14dfa456_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatComposerGeneratingBatch2Preview_输入生成中 深中411_29ce6478_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatPageLightBatch2Preview_会话完整 浅中411_d1890a42_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatComposerSixLinesBatch2Preview_输入超过六行 深中411_afaafb0f_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatComposerTaskBatch2Preview_输入任务状态 深中411_4d9cbec6_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatComposerEmptyBatch2Preview_输入空态 深中411_1e0619ea_0.png`
- `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatComposerTextBatch2Preview_输入文字 深中411_12d11cce_0.png`
- `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/approvalMultipleBatch2Preview_多项审批 深中411_736f9259_0.png`
- `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/retrievalRunningBatch2Preview_检索进行中 深中411_17547905_0.png`
- `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/approvalExecutedBatch2Preview_已执行审批 深中411_d43d33cc_0.png`
- `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/approvalLargeFontBatch2Preview_单项审批 大字深中411_dc1bc9c0_0.png`
- `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/approvalSingleBatch2Preview_单项审批 深中411_09ec2598_0.png`
- `com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/compactStepsNarrowLightPreview_紧凑步骤 窄屏大字浅色_86cb35ea_0.png`
- `com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/compactStepsNarrowDarkPreview_紧凑步骤 窄屏大字深色_22927655_0.png`
- `com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/compactStepsLightPreview_紧凑步骤 浅色英文_a4e31dc2_0.png`
- `com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/compactStepsDarkPreview_紧凑步骤 深色中文_cb80b7c1_0.png`

**删除（0）**

无。

按9.3复核：空态/完整页、六行输入、附件、生成中、所有工具状态、长参数与结果、思考展开、单/多/已执行审批、长回复、完成态操作、浅色/英文/130%与宽屏布局均检查。生成/滚动/审批/上下文入口的动态业务行为仅有相应测试源码及编译证据，不以Layoutlib代替设备运行。嵌套引用围栏仍使用库既有源码提取语义（新回归图可见引用前缀），该附加用例验收的是横向可达性，未重写CommonMark源码提取。

### 11.5 验收命令、统计与产物

以下均在当前最终工作树执行。Gradle按任务书的四组验收命令运行；附加in-process和原有Robolectric offline init脚本只适配沙箱编译/依赖锁路径，不变更测试筛选与断言。最终门禁按顺序串行，未停止任何守护进程。早期一次定向测试未结束时提前启动了截图更新；该批证据没有用于最终验收，完整门禁随后串行重跑。

**双渠道JVM与Lint**（`batch2-r2-logs/quality-verified.log`）：

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process -I ../.agent/tmp-agent-reports/batch2-logs/test-sandbox.init.gradle :app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:lintSideloadDebug
```

```text
> Task :app:lintAnalyzeSideloadDebug
> Task :app:lintAnalyzeSideloadDebugUnitTest
> Task :app:lintReportSideloadDebug UP-TO-DATE
> Task :app:lintSideloadDebug

BUILD SUCCESSFUL in 3m 43s
73 actionable tasks: 13 executed, 60 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

**双渠道截图校验**（`batch2-r2-logs/screenshots-validate.log`）：

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process :app:validateSideloadDebugScreenshotTest :app:validatePlayDebugScreenshotTest
```

```text
> Task :app:generatePlayDebugAssets UP-TO-DATE
> Task :app:mergePlayDebugAssets UP-TO-DATE
> Task :app:packagePlayDebugScreenshotTestForScreenshotTest UP-TO-DATE
> Task :app:validatePlayDebugScreenshotTest

BUILD SUCCESSFUL in 2m 8s
63 actionable tasks: 4 executed, 59 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

**双渠道Debug APK**（`batch2-r2-logs/debug-apk.log`）：

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process :app:assembleSideloadDebug :app:assemblePlayDebug
```

```text
> Task :app:mergeProjectDexPlayDebug
> Task :app:packagePlayDebug
> Task :app:createPlayDebugApkListingFileRedirect UP-TO-DATE
> Task :app:assemblePlayDebug

BUILD SUCCESSFUL in 21s
87 actionable tasks: 12 executed, 75 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

**设备主包与测试包编译**（`batch2-r2-logs/device-apk-final.log`）：

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process -Pnexara.deviceE2e=true -Pnexara.deviceE2eAbi=arm64-v8a :app:assembleSideloadDeviceTest :app:assembleSideloadDeviceTestAndroidTest
```

```text
> Task :app:createSideloadDeviceTestAndroidTestApkListingFileRedirect UP-TO-DATE
> Task :app:assembleSideloadDeviceTestAndroidTest

[Incubating] Problems report is available at: file:///Users/promenar/Codex/Nexara-wt-redesign2/native-ui/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 26s
80 actionable tasks: 14 executed, 66 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

双渠道基线更新另执行 `:app:updateSideloadDebugScreenshotTest :app:updatePlayDebugScreenshotTest`，同一组Gradle参数，日志 `screenshots-update-final.log`。

| 任务 | 总数 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| testSideloadDebugUnitTest | 3860 | 0 | 0 | 25 |
| testPlayDebugUnitTest | 3836 | 0 | 0 | 25 |
| validateSideloadDebugScreenshotTest | 280 | 0 | 0 | 0 |
| validatePlayDebugScreenshotTest | 280 | 0 | 0 | 0 |

Lint为0 Error/Fatal、652 Warning、11 Hint。相对初轮651 Warning新增一项UnusedResources：旧“执行目标：%1$s”资源 `chat_approval_execution_target` 已无UI消费者；资源键保留但不再显示重复目标。该警告不冒充基线，也不影响Lint门禁。当前测试统计、截图统计、Lint与命令回执分别见 `unit-results.json`、`screenshot-results.json`、`lint-summary.json`、`acceptance-results.json`。

仓库根：`python3 scripts/ci/check-text-wrapping.py`

```text
文本换行门禁通过
```

仓库根：`python3 scripts/ci/check-distribution-manifests.py`

```text
发行清单检查通过（debug）：play 无所有文件访问权限，sideload 包含；包身份一致
```

指定静态核查第一条：

```text
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/components/FilesPanel.kt:743:                                imageVector = Icons.Rounded.Edit,
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1295:                                imageVector = Icons.Rounded.Edit,
native-ui/app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt:761:                Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, editDescription, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
```

分别是文件重命名、会话更多菜单重命名、代码编辑/保存；顶栏标题旁没有Edit。streamingTailFade、STREAM_TAIL_FADE_DURATION_MS、ChatInputTopBar无生产命中。

指定静态核查第二条：

```text
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:896:        horizontalArrangement = Arrangement.spacedBy(8.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:909:                horizontalArrangement = Arrangement.spacedBy(6.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:946:            horizontalArrangement = Arrangement.spacedBy(4.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:980:            horizontalArrangement = Arrangement.spacedBy(4.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1039:        verticalArrangement = Arrangement.spacedBy(NexaraSpacing.Small),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1170:        horizontalArrangement = Arrangement.spacedBy(NexaraSpacing.Small),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1391:        horizontalArrangement = Arrangement.spacedBy(8.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1638:        verticalArrangement = Arrangement.spacedBy(24.dp)
```

命中依次属于状态条、任务胶囊、模型、用量、面板、明细行、附件和骨架屏；消息LazyColumn无spacedBy。`git diff --check`通过，暂存区为空。

| 当前构建产物 | 字节 | SHA-256 |
| --- | --- | --- |
| `native-ui/app/build/outputs/apk/sideload/debug/app-sideload-debug.apk` | 152609394 | `975de487414d7083564a6cebfbf0d136e8128671d7157069cc51ef85285edeaf` |
| `native-ui/app/build/outputs/apk/play/debug/app-play-debug.apk` | 152511062 | `b693f465f6e2b3025a945270d38148fbdf60f83f8c102367fb6a704422a95a62` |
| `native-ui/app/build/outputs/apk/sideload/deviceTest/app-sideload-deviceTest.apk` | 138108799 | `ef1e0aa6343e7f8f1c88da4f76c7541523a90d1596a4b13645076c04fba7ac91` |
| `native-ui/app/build/outputs/apk/androidTest/sideload/deviceTest/app-sideload-deviceTest-androidTest.apk` | 9153939 | `577e1f9e585ef846eef1eec53737406648372108c58070a6d1e93ca666bf76ac` |

未安装任何APK。设备构建只证明主包和新增/改写测试可编译，不证明API31、IME、48dp实际触控、TalkBack、OEM或动态色验收。原有特殊富内容渐显和真实HTML/WebView等运行边界继续按第10节披露。

### 11.6 DIA、边界与交付

DIA：已同步 `CHANGELOG.md`、`design-qa.md`、规格及本报告；复核检查 `README.md`、`docs/context-management.md`、`docs/ADR/ADR-020-layered-model-metadata-registry.md` 的现有描述，无需再改其初轮已同步内容。规格顶部仍为“批次一、批次二均已实施”。完整源码/基线哈希与范围核查见 `source-and-baseline-manifest.json`、`scope-check.json`。

HLG：按明确任务边界未追加handover。未提交、未推送、未启动/操作设备、未改设置组件/偏好键/默认值/Room/MessageManager/PDEC、未新增依赖、未执行gradlew --stop。工作树保留供主控集成；6项设备行为仍需由主控按设备授权实跑，不能用本报告的主机验证替代。

## 12. 主控设备复核修正

主控在 API 31 / API 35 模拟器上运行设备测试，修正复核问题修正后仍然存在的失败，以及主线（第一批）遗留的两项回归。

| 问题 | 根因 | 修改 |
| --- | --- | --- |
| `ChatImeInteractionTest` ×2、`ChatScrollGeometryTest` 尾消息不可见 | mikepenz `rememberMarkdownState` 默认异步解析：历史消息先以空正文布局（约 169px），滚到底部后才解析完成并增高，把尾消息挤出视口 | `ui/common/MarkdownText.kt`：非流式且不超过 12000 字符的片段同步解析（`immediate = true`），流式与超长片段仍在后台解析；以显式 `streaming` 标志判定（最后一段即使已完成，`reveal` 也非空） |
| `ChatLayoutTest` 加号可见宽 38.86dp | 加号偏移 -13.5dp 越出输入区容器，被容器裁剪 | `ui/chat/ChatScreen.kt` 偏移改为 -12dp，按钮左缘贴齐容器内侧；规格 §5.8 容差改为 ≤2dp；测试夹具按会话页加 12dp 水平边距 |
| `ChatApprovalLayoutTest` 触控高 43.89dp | 测试用 `rule.density` 换算，而 `DeviceConfigurationOverride.WindowSize` 会按窗口缩放组合密度（2.4 对 2.625）；实际触控高为 115.2px = 48dp | 测试改用节点自身的布局密度换算 |
| `AccessibilitySmokeTest` 输入框高 18.67dp | 输入框去掉了 48dp 最小高度 | 输入框 `heightIn(min = 48dp)`，文字上下各 12dp 留白并垂直居中；无附件时容器顶边不再加 12dp，有附件时保留；规格 §5.8 单行容器高改为 `max(48, 12 + 行高 + 12) + 40 + 8`，约 96dp |
| 六行输入第六行底部被裁约 1.5dp | 行高样式默认裁掉首末行行距，最大行数上限比 6 行实际高度略矮 | 输入样式 `LineHeightStyle(Center, Trim.None)` |
| `TextWrappingDeviceTest` 技能脚注 300dp/1.3x 孤字“码。” | 第一批脚注改用段落换行后不再平衡行长 | `ui/theme/Type.kt` 新增 `keepCjkTailTogether()`：最后两个相邻中日韩字符之间插入 U+2060；`NexaraSettingsSection` 脚注统一使用；`TextLineBreakPolicyTest` 补单测 |
| `ShareImportAndroidEndToEndTest` ×2 | 收件箱按 action+MIME+URI 永久记录最近 32 个已消费指纹，固定 fixture URI 在同一设备第二次运行起被判为重复，分享弹层不出现；测试另在启动就绪前读取分享状态，存在启动竞态 | 测试改用每次唯一的目录段 URI，轮询前等待启动 `Ready`，失败信息带启动状态；生产端“重复分享被静默丢弃”另行跟踪 |

截图：重新生成 41 组基线（输入区、会话页、发布预览会话页与附件菜单；SSH、Termux、检索高级、模型编辑器的脚注末两字连写），逐张目视检查。单行输入区实测 252px = 96dp，六行输入六行墨迹完整。


## 13. 独立审阅问题修正

### 13.1 执行计划与所有权

按任务书 `brief-redesign-batch2-r3.md` 修正第 3.1–3.14 项，继承前两份任务书的验收与边界。

1. 主控独占 `chat/`、中英文资源、设备与截图测试、规格及 DIA 文档：先建立风险说明和整轮复制回归，再修正审批、尾部布局跟随、步骤行、思考 Markdown 与横向安全区。
2. 原生 `stream_markdown` Agent 独占 `common/` 中放出、宽内容与高亮相关实现和对应 JVM 测试，先运行修前失败测试；只处理流式首挂、原始文本速率、行缓存、高亮调度与单例动画观察。
3. 两部分合并后由未参与施工的原生 Agent 只读审阅。主控核对 diff、保留项和测试结果，串行运行双风味 JVM、Lint、双风味截图更新与校验、Debug APK、ARM64 DeviceTest 主包/测试包编译和两项 Python 门禁。
4. 新增设备用例只编译；不启动或操作设备。截图基线逐张目视检查，清单按本轮开始前的工作树快照比较。保留第 12 节已验证修正，不改设置组件、默认值、存储键、依赖和 PDEC。
5. 回滚边界为本轮文件增量；初始工作树快照位于 `/tmp/nexara-r3-before/`，不重置或覆盖既有未提交实现。不提交、不推送、不追加 handover，不执行 Gradle `--stop`。

### 13.2 第3节逐项实现、测试与截图

源码位置以 `native-ui/app/src/main/java/com/promenar/nexara/ui/` 为基准；设备与截图源文件在相应测试 source set。准确 PNG 文件名见第13.6节清单。

| 项目 | 修改位置 | 行为与根因修正 | 测试 | 对应截图 |
| --- | --- | --- | --- | --- |
| 3.1 | `chat/ChatScreen.kt:416` | 目标条目 offset/size 与输入区、IME、视口一起被观察；任何布局变化触发无延迟的遮挡校正；入口与校正前均检查自动跟随，用户手动滚动后保持位置。 | ChatFollowCompletionTest（三项设备用例） | 会话页既有基线；动态几何由设备断言覆盖 |
| 3.2 | `chat/ChatInlineComponents.kt:834` | 每个调用显示可选择复制的内部 ID，labelSmall/Monospace/onSurfaceVariant，无透明度；单项、多项、已执行与未知/MCP 均保留身份。 | ChatScreenContentStateTest；ApprovalCardTagsTest | 审批单项、多项、已执行、大字与窄屏英文 |
| 3.3 | `common/SmoothStreamContent.kt:24` | 首次合成显示当前全文；只对挂载后的增量放出/渐显；已读片段重新挂载不重播，改写按公共字形前缀回退。 | SmoothStreamContentTest；SmoothStreamCompositionTest | 生成与会话页既有基线 |
| 3.4 | `chat/CompactStepRow.kt:153` | 失败段独立占位且不设置 weight，摘要使用剩余宽度并先省略，读屏内容保持完整。 | ChatStepFailureLayoutTest | chatBatch2FailureNarrowEnglishPreview |
| 3.5 | `chat/CompactStepRow.kt:116` | 工具失败、检索失败与部分来源失败使用 error；tone 参与文字颜色计算，删除 UNUSED_PARAMETER 总体抑制。 | ChatStepFailureLayoutTest；既有检索用例 | 失败工具与检索失败/部分失败 |
| 3.6 | `chat/CompactStepRow.kt:120` | 读屏使用 chat_step_summary_separator：中文“，”、英文“, ”。 | ChatStepFailureLayoutTest 英文描述断言 | 无可见像素变化 |
| 3.7 | `chat/PipelineBubble.kt:585` | 推理详情使用 MarkdownText，bodySmall 字号、onSurfaceVariant；2dp 竖线与12dp间距由自然高度 Box/不参与测量的覆盖层实现，避免代码块固有测量。 | ThinkingTraceTest#expandedThinkingRendersMarkdownListsAndCode；#expandedThinkingRendersFormulaWithoutRawMarkdownDelimiters | chatBatch2ExpandedThinkingDarkPreview；公式为设备用例 |
| 3.8 | `chat/ChatInlineComponents.kt:1043` | 多项按钮为批准当前/拒绝当前，单项保持批准/拒绝；空间不足时按钮分行右对齐，仍为40dp可见/48dp触控。 | ApprovalCardTagsTest；ChatApprovalScopeLayoutTest | approvalMultipleNarrowEnglishBatch2Preview 与多项审批 |
| 3.9 | `chat/ChatInlineComponents.kt:815`；`chat/ChatScreen.kt:601` | 原因、风险语句/结构化风险事实与参数分别处理。普通原因中性色，风险为空时显示风险标签；生产入口只传 reason，历史字段参数也仅进入单一参数块。高风险目标说明仅在有目标时作为目标事实，未知目标保留风险标签。 | ApprovalRiskSummaryTest；ChatScreenContentStateTest；ChatApprovalLayoutTest 中语义冲突用例同步 | 审批系列 |
| 3.10 | `chat/PipelineBubble.kt:122` | 操作行复制该轮全部非空 Content，按顺序以一个空行连接；排除 Thinking、ToolExec 和工具结果。 | PipelineBubbleTest 新增整轮复制用例 | 操作行可见结构不变 |
| 3.11 | `chat/ChatScreen.kt:1179` | 顶栏默认保留 safeDrawing 水平部分，页面垂直处理不变；正常的 windowInsets 参数用于宿主/横屏预览。 | ChatHorizontalInsetsTest | chatTopBarLandscapeSafeInsetsBatch2Preview |
| 3.12 | `common/MarkdownStreamingSupport.kt:13`；`common/MarkdownText.kt:792`、`:1402`；`common/StreamRevealer.kt:45`；`common/StreamAnimationSettings.kt:18`；`common/SmoothStreamContent.kt:21` | 逐行测量缓存、串行节流高亮/未着色新尾部、原文放出与来源映射、历史绕过/结束排空、进程单一动画设置观察。详见复杂度表。 | MarkdownStreamingSupportTest；StreamAnimationSettingsTest；SmoothStreamCompositionTest；全部旧 common 契约 | 正文代码、嵌套代码/表格与生成预览 |
| 3.13 | `androidTest/java/com/promenar/nexara/ui/AccessibilitySmokeTest.kt:89`；`androidTest/java/com/promenar/nexara/ui/chat/ChatScreenContentStateTest.kt:412` | 保留可见高度断言，附件、上下文与任务胶囊新增 touchBoundsInRoot 宽高≥47.5dp；按节点自身 layoutInfo.density 换算。现有平台最小触控扩展保持，未缩小触控区。 | AccessibilitySmokeTest；ChatScreenContentStateTest | 输入区与任务状态条既有基线 |
| 3.14 | `chat/SessionSettingsSheet.kt:160`；`screenshotTest/kotlin/com/promenar/nexara/ui/common/MarkdownBatch2Fixtures.kt:3`；`androidTest/java/com/promenar/nexara/ui/common/MarkdownBatch2Fixtures.kt:3` | 删除无调用者传值的 initialTab；默认页仍为模型页。两份正式 Markdown 示例 XML 移除，内容进入 screenshotTest/androidTest 常量，保留原中英文正文及缩进。 | MarkdownResourceFixtureTest 改为测试源码常量；应用与设备测试编译 | Markdown 长回复与 HTML 预览 |

### 13.3 测试与独立复核证据

- 先建立失败证据，再修正逻辑：`ApprovalRiskSummaryTest` 首轮3项2失败；`common-red` 31项3失败（含整轮复制漏掉前段）；性能回归10项10失败；补充边界34项3失败（表格补管道、原文来源、结束态单帧全放）。失败均来自断言，不通过删例或放宽断言解决。
- 新增 `MarkdownStreamingSupportTest` 14项、`SmoothStreamCompositionTest` 3项、`StreamAnimationSettingsTest` 1项；`SmoothStreamContentTest` 新增首挂与增量/速率场景，原有积压/字形/零时钟用例明确先挂载空串，保留其对后续增量的约束。`ApprovalRiskSummaryTest` 更新普通原因、参数、风险语句分类；`PipelineBubbleTest` 检查工具前后正文复制且不夹带思考、参数或结果。
- common全包加两项chat逻辑的最终定向验证265项，0失败、0错误、0跳过，`BUILD SUCCESSFUL in 12s`。
- 原生 `stream_markdown` 仅施工 common 文件与相关 JVM 测试；原生 `independent_review` 在实现稳定后只读审阅，不参与施工。审阅发现360dp/130%英文多项审批按钮宽度不足，已用 FlowRow 与独立截图/设备用例封闭；最终生产边界审阅无剩余阻断。主控复核源码、相对初始工作树 diff、实际测试回执与最终截图。
- `ChatApprovalLayoutTest` 原40dp/48dp几何与节点密度写法完整保留；仅将“普通原因替代风险标签”的旧语义用例改为原因中性色与风险标签同时存在，符合3.9。`ChatLayoutTest`、`ShareImportAndroidEndToEndTest`、`NexaraSettingsSection` 相对本轮开始快照未变；输入48dp、上下12dp、Trim.None、无附件顶0dp/有附件12dp、加号-12dp与CJK尾字规则保留。

### 13.4 流式复杂度与边界

设 N 为当前总字符数、L 为逻辑行数、D 为新增/改变行的字符数、H(N) 为一次完整高亮成本、R 为最近渐显范围数、V 为可见字符数。

| 环节 | 修正前 | 当前复杂度与约束 |
| --- | --- | --- |
| 宽度测量 | 每个内容帧对全部L行做字体测量，约O(N)的字体排布成本 | 拆行/哈希仍O(N)、求最大O(L)，仅D参与新字体测量；缓存仅保留当前不同逻辑行，尾行旧版本不累积；顶层/嵌套共用宿主，表格使用既有固定列宽 |
| 代码高亮 | 每帧全文变化可能启动/取消一次H(N)，执行超过一帧时可能一直无法落地 | 每块单并发、单份合并等待，流式每秒最多4次H(N)，结束对全文最终高亮；已覆盖前缀与原样尾部拼接仍O(代码长度+样式范围)，不会隐藏新尾 |
| 原文与解析映射 | 临时补尾进入速率估计，可能反复重置 | 每个上游文本版本惰性O(N+预处理长度)建立映射、O(N)数组；原样稳定来源O(1)复用，转换映射O(R)加既有字符串切片O(V)，来源切换过渡才比较公共前缀；帧内不重新解析完整Markdown |
| 非流式字形 | 每历史片段创建放出器并切分字形，O(N) | 从未流式的历史实例直接全文，放出器/字形成本为0；已挂载流式结束保留≤300ms排空，稳定后卸载；活跃流式每原文版本字形切分仍O(N) |
| 动画设置 | 每正文片段一份观察与IO读取 | 应用进程一份StateFlow/ContentObserver，由IO读取，正文与步骤微光共享 |

未运行性能基准，以上为代码路径复杂度。设备上的旋转、滚出再回、离开返回、真实动画开关、实际帧时限、IME、触控命中与TalkBack需要设备执行；状态机与无界面Compose时钟只证明确定逻辑。来源映射与当前全部预处理分支配套，未来新增非保序转换需同时更新映射测试。

### 13.5 设备用例清单与编译边界

以下新增用例必须由持有设备的主控后续运行；本轮只构建 ARM64 DeviceTest 主包与测试 APK，不声称设备执行通过。两个新增英文窄屏用例显式提供英文配置，避免依赖设备语言。

| 完整类名与方法名 | 源码 |
| --- | --- |
| `com.promenar.nexara.ui.chat.ChatFollowCompletionTest#completionKeepsLastActionButtonAboveComposer` | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/chat/ChatFollowCompletionTest.kt:33` |
| `com.promenar.nexara.ui.chat.ChatFollowCompletionTest#completionDoesNotMoveListAfterDeliberateUpScroll` | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/chat/ChatFollowCompletionTest.kt:41` |
| `com.promenar.nexara.ui.chat.ChatFollowCompletionTest#asynchronousLongHistoryKeepsFinalActionAboveComposer` | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/chat/ChatFollowCompletionTest.kt:53` |
| `com.promenar.nexara.ui.chat.ChatStepFailureLayoutTest#twoCategoriesKeepFullFailureCountAndErrorColorAt360DpFont130` | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/chat/ChatStepFailureLayoutTest.kt:37` |
| `com.promenar.nexara.ui.chat.ChatApprovalScopeLayoutTest#englishCurrentActionsKeepFullLabelsAnd48DpTouchBoundsAt360DpFont130` | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/chat/ChatApprovalScopeLayoutTest.kt:36` |
| `com.promenar.nexara.ui.chat.ChatHorizontalInsetsTest#landscapeBackAndOptionsStayInsideCutoutAndNavigationInsets` | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/chat/ChatHorizontalInsetsTest.kt:27` |
| `com.promenar.nexara.ui.chat.ApprovalCardTagsTest#mcpAndUnknownCallsKeepTheirExactVisibleIds` | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/chat/ApprovalCardTagsTest.kt:79` |
| `com.promenar.nexara.ui.chat.ThinkingTraceTest#expandedThinkingRendersMarkdownListsAndCode` | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/chat/ThinkingTraceTest.kt:39` |
| `com.promenar.nexara.ui.chat.ThinkingTraceTest#expandedThinkingRendersFormulaWithoutRawMarkdownDelimiters` | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/chat/ThinkingTraceTest.kt:55` |
| `com.promenar.nexara.ui.chat.ChatScreenContentStateTest#legacyApprovalKeepsArgumentsInOneParameterBlock` | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/chat/ChatScreenContentStateTest.kt:216` |

同步更新的设备用例包括：

- `com.promenar.nexara.ui.chat.ChatScreenContentStateTest#approvalState_rendersToolDetailsAndDispatchesApprove`：内部ID、友好名、风险标签、原因中性色与批准回调；`#pendingTask_rendersThirdCapsuleAndDispatchesManualActions`：任务胶囊真实触控范围。
- `com.promenar.nexara.ui.chat.ApprovalCardTagsTest#pendingApproval_exposesStableStateAndActionTags`、`#exactQueueLabelsCurrentAndRemainingAndDisablesActionsInFlight`、`#executedApproval_exposesExecutedStateWithoutActionTags`：单项/多项按钮文案、可见ID、已执行状态。
- `com.promenar.nexara.ui.chat.ChatApprovalLayoutTest#executedApprovalKeepsOrdinaryReasonSeparateFromRiskLabel`：普通原因与风险标签分别保留；原按钮触控密度写法不变。
- `com.promenar.nexara.ui.common.MarkdownResourceFixtureTest#中英文示例保留三层列表与原始缩进`：直接读取测试源码常量，保留中英文真实缩进和三层AST；正式资源无示例内容。
- `AccessibilitySmokeTest` 的会话输入区测试：附件与上下文指示在原可见尺寸之外检查节点自身密度下的真实48dp触控宽高。

主机公式验证边界：新增思考示例的公式触发 Layoutlib `NoSuchMethodError: android.webkit.WebView.getSettings()`，发生在 `InlineLatexSpan → RichContentWebViewPool` 的真实 WebView 获取阶段。主机截图保留标题、粗体、列表、代码和竖线；公式由上述新增设备用例覆盖，生产渲染器与既有用例不变。独立审阅核对了这条调用链与验证分工。图像语义只能证明渲染到达 bitmap 分支，不能单独证明公式像素正确。


### 13.6 截图基线、逐图目视与像素复核

相对任务开始前的280张快照：新增4张、更新22张、删除1张，最终有效283张；两个渠道均校验283项。思考预览高度由340dp扩为620dp以覆盖真实Markdown，其哈希变化计为新增/删除；其余3张为新增场景。逐张检查26张新增/变化图，联系表见 `batch2-r3-logs/contact-1.png` 至 `contact-5.png`，逐图SHA与结论见 `visual-reviewed.json`。文件名以下均相对于 `native-ui/app/src/screenshotTestDebug/reference/`。

#### 新增（4）

| 基线文件名 | 目视结论 |
| --- | --- |
| `com/promenar/nexara/ui/chat/ChatComposerBatch2ScreenshotTestKt/chatTopBarLandscapeSafeInsetsBatch2Preview_顶栏横屏 横向安全区_a0128d12_0.png` | 800dp横屏，返回和更多均处于左右安全区内，标题未覆盖按钮。 |
| `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/approvalMultipleNarrowEnglishBatch2Preview_Approval current English 360dp font130_9fc75603_0.png` | 360dp/130%英文，精确ID可见；普通原因与风险分色；当前动作分行右对齐，标签完整。 |
| `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2ExpandedThinkingDarkPreview_思考展开 阅读竖线_e2c3c960_0.png` | 标题、粗体、列表和语法着色代码完整；竖线与正文间距正确，无原始Markdown标记。 |
| `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2FailureNarrowEnglishPreview_Step failure English 360dp font130_6814283d_0.png` | 360dp/130%英文，两类别摘要与1 failed完整，失败error色，尾箭头独立。 |

#### 更新（22）

| 基线文件名 | 目视结论 |
| --- | --- |
| `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatApprovalTabletReleasePreview_Chat approval tablet English_893d1f95_0.png` | 平板英文审批，ID、原因、风险与参数独立，当前/全部动作含义清楚。 |
| `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatApprovalWritePreviewReleasePreview_Chat approval write preview Chinese_dbbe7da6_0.png` | 中文写入审批，ID与风险可见，参数只在参数块显示。 |
| `com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragCardOutcomesReleasePreview_RAG card outcomes Chinese_1c327bf9_0.png` | 成功、部分失败与失败区别清楚，失败说明为error色。 |
| `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/approvalExecutedBatch2Preview_已执行审批 深中411_d43d33cc_0.png` | 已执行状态保留ID、风险标签和参数，无待审批操作。 |
| `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/approvalLargeFontBatch2Preview_单项审批 大字深中411_dc1bc9c0_0.png` | 大字ID与风险可读，参数展开入口及单项动作完整。 |
| `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/approvalMultipleBatch2Preview_多项审批 深中411_736f9259_0.png` | 中文多项调用逐项显示ID，批准当前/拒绝当前及全部批准范围清楚，无裁切。 |
| `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/approvalSingleBatch2Preview_单项审批 深中411_09ec2598_0.png` | 单项ID、普通原因、风险与参数层级清楚，批准/拒绝文案完整。 |
| `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/retrievalFailedBatch2Preview_检索失败 深中411_a34591ee_0.png` | 检索失败摘要为error色，点击箭头与正文对齐。 |
| `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/retrievalPartialKnowledgeBatch2Preview_知识库部分来源失败 深中411_9ace520c_0.png` | 知识库部分来源失败为error色，成功结果数量与失败提示可辨。 |
| `com/promenar/nexara/ui/chat/ChatInlineBatch2ScreenshotTestKt/retrievalPartialWebBatch2Preview_网页部分来源失败 深中411_e6893d36_0.png` | 网页部分来源失败为error色，摘要与详情入口未裁切。 |
| `com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownBatch2DarkChinese_助手正文 长回复 深色中文_25d52f12_0.png` | 长回复标题、列表、表格与代码层级清楚，代码首列完整。 |
| `com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownBatch2HtmlArtifact_助手正文 HTML长代码 深色中文_63d15949_0.png` | 长HTML源码首合成即高亮，代码头/首列字形清楚，源码未被隐藏。 |
| `com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownBatch2NarrowLargeFont_助手正文 长回复 窄屏大字_4dbb2d04_0.png` | 360dp大字正文与层级缩进可读，代码和表格保留横向宿主。 |
| `com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownCodeInkRegression_代码首列字形 与未裁剪文字对照_6edcef63_0.png` | 代码首列与对照文字完整，println首字母左侧无裁切。 |
| `com/promenar/nexara/ui/chat/ChatMarkdownBatch2ScreenshotTestKt/chatMarkdownNestedCodeRegression_引用与列表长代码 可横向滚动_a083d8eb_0.png` | 引用和嵌套列表代码层级与首列完整，保留横向内容宿主。 |
| `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2ExpandedToolsDarkPreview_工具展开 长参数与结果_b449bf94_0.png` | 失败摘要/计数清楚，内部ID、参数、结果和展开链接对齐。 |
| `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2StepStatesDarkPreview_步骤行 思考与工具各状态_a81cc86d_0.png` | 运行、完成、失败各状态层级清楚，失败计数独立保留。 |
| `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2StepsLargeFontDarkPreview_步骤行 130%字号_18a3ecad_0.png` | 130%字号失败段完整，箭头与文字无重叠。 |
| `com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/compactStepsDarkPreview_紧凑步骤 深色中文_cb80b7c1_0.png` | 深色紧凑步骤失败error色，正文与步骤节奏正确。 |
| `com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/compactStepsLightPreview_紧凑步骤 浅色英文_a4e31dc2_0.png` | 浅色英文步骤和失败提示清楚，正文可读。 |
| `com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/compactStepsNarrowDarkPreview_紧凑步骤 窄屏大字深色_22927655_0.png` | 窄屏大字深色，摘要可先省略，失败提示与正文可辨。 |
| `com/promenar/nexara/ui/chat/CompactStepsScreenshotTestKt/compactStepsNarrowLightPreview_紧凑步骤 窄屏大字浅色_86cb35ea_0.png` | 窄屏大字浅色，摘要可先省略，失败提示与正文可辨。 |

#### 删除（1）

- `com/promenar/nexara/ui/chat/ChatPipelineBatch2ScreenshotTestKt/chatBatch2ExpandedThinkingDarkPreview_思考展开 阅读竖线_1af44649_0.png`：同一思考预览的340dp旧哈希；当前620dp预览生成物及校验通过，旧文件已核对与初始快照一致并保留备份。未删除有效测试。

#### 当前长回复像素复核

长回复深色中文图的前1160px与任务开始快照逐像素一致；整图因代码高亮首合成路径变化而更新，不能称整图像素不变。直接从当前PNG以RGB最大通道>120识别文字墨迹，统计相邻墨迹之间的空白行；密度420dpi，1dp=2.625px。Theme/padding/行高与第11.2节相同，采用单一块间距和首末墨迹边界，未叠加外层spacedBy。抗锯齿阈值会带来约1–2px边缘差异。

| 边界 | 上块墨迹y | 下块墨迹y | 空白px | dp | 目标dp |
| --- | --- | --- | --- | --- | --- |
| 标题后 | 32–82 | 110–146 | 27 | 10.29 | 10 |
| 段落间 | 173–210 | 254–290 | 43 | 16.38 | 16 |
| 标题前 | 317–354 | 418–462 | 63 | 24.00 | 24 |
| 标题后首个列表 | 418–462 | 495–531 | 32 | 12.19 | 12 |
| 同级列表项 | 495–531 | 560–596 | 28 | 10.67 | 10 |

规格9.3自检：顶栏标题和输入工具行结构保持，既有输入与页面基线无本轮像素变化；代码首列、列表层级、宽表格/代码与正文块节奏通过目视；审批精确ID、原因/风险和动作范围完整；失败计数在360dp/130%英文完整。触控、跟随时限、系统动画、真实横屏系统栏、TalkBack和公式像素以设备运行结果为准。第12节输入与CJK修正未回退；没有扩大第8节可见尺寸例外，3.13已补足真实触控断言。

### 13.7 当前工作树验收命令与实际输出

执行环境为本机macOS/zsh，分支 `redesign-batch2`，基准HEAD为 `0b81703ce669e4e02f1557eb03b896c94088a64a`，验证对象为其上现有未提交工作树。`source-and-baseline-manifest.json` 记录相对任务开始快照的源码与基线SHA，不能将HEAD单独当作本轮代码版本。

Gradle均在 `native-ui/` 运行。额外使用 `-Pkotlin.compiler.execution.strategy=in-process` 避免沙箱外Kotlin守护进程目录写入；JVM测试复用项目已有 `test-sandbox.init.gradle` 与本地Robolectric依赖，避免写入无权限的用户根锁文件。没有修改构建配置、依赖、测试筛选条件或断言。

#### 双风味JVM与Lint

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process -I ../.agent/tmp-agent-reports/batch2-logs/test-sandbox.init.gradle :app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:lintSideloadDebug
```

```text
BUILD SUCCESSFUL in 3m 53s
73 actionable tasks: 27 executed, 46 up-to-date
```

完整输出：`batch2-r3-logs/quality-final.log`。

#### 双风味截图更新

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process :app:updateSideloadDebugScreenshotTest :app:updatePlayDebugScreenshotTest
```

```text
BUILD SUCCESSFUL in 2m 24s
63 actionable tasks: 9 executed, 54 up-to-date
```

完整输出：`batch2-r3-logs/screenshot-update-final.log`。

#### 双风味截图校验与最终Lint

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process :app:validateSideloadDebugScreenshotTest :app:validatePlayDebugScreenshotTest :app:lintSideloadDebug
```

```text
BUILD SUCCESSFUL in 2m 34s
73 actionable tasks: 7 executed, 66 up-to-date
```

完整输出：`batch2-r3-logs/screenshot-validate.log`。

#### 双风味Debug APK

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process :app:assembleSideloadDebug :app:assemblePlayDebug
```

```text
BUILD SUCCESSFUL in 21s
87 actionable tasks: 12 executed, 75 up-to-date
```

完整输出：`batch2-r3-logs/apk-build.log`。

#### R3新增ARM64编译门禁

```bash
./gradlew --no-daemon --max-workers=4 '-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process -Pnexara.deviceE2e=true -Pnexara.deviceE2eAbi=arm64-v8a :app:assembleSideloadDeviceTest :app:assembleSideloadDeviceTestAndroidTest
```

```text
BUILD SUCCESSFUL in 20s
80 actionable tasks: 10 executed, 70 up-to-date
```

完整输出：`batch2-r3-logs/device-build-final.log`。

#### 原任务书ARM64编译门禁

```bash
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g' -Pkotlin.compiler.execution.strategy=in-process -Pnexara.deviceE2e=true -Pnexara.deviceE2eAbi=arm64-v8a :app:assembleSideloadDeviceTest :app:assembleSideloadDeviceTestAndroidTest
```

```text
BUILD SUCCESSFUL in 4s
80 actionable tasks: 3 executed, 77 up-to-date
```

完整输出：`batch2-r3-logs/device-build-original-gate.log`。

| 任务 | 总项 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| `testSideloadDebugUnitTest` | 3885 | 0 | 0 | 25 |
| `testPlayDebugUnitTest` | 3861 | 0 | 0 | 25 |
| `updateSideloadDebugScreenshotTest` | 283 | 0 | 0 | 0 |
| `updatePlayDebugScreenshotTest` | 283 | 0 | 0 | 0 |
| `validateSideloadDebugScreenshotTest` | 283 | 0 | 0 | 0 |
| `validatePlayDebugScreenshotTest` | 283 | 0 | 0 | 0 |

JVM实际通过项分别为3860与3836；每渠道25项条件性跳过单独计入，未计作通过。Lint为0 Error/Fatal、651 Warning、11 Hint。XML汇总见 `quality-results.json`。JVM与生产代码在完整质量门禁后未修改；最终新增公式设备测试经历一次错误import导致的编译失败，移除错误的顶层 `onAllNodes` import、保留其规则成员调用及全部断言后，两个设备编译门禁均通过（失败回执 `device-build-import-error.log`）。

#### 仓库根门禁与静态核查

```bash
python3 scripts/ci/check-text-wrapping.py
```

```text
文本换行门禁通过
```

```bash
python3 scripts/ci/check-distribution-manifests.py
```

```text
发行清单检查通过（debug）：play 无所有文件访问权限，sideload 包含；包身份一致
```

指定静态核查第一条：

```bash
grep -rn "streamingTailFade\|STREAM_TAIL_FADE_DURATION_MS\|ChatInputTopBar\|Icons.*Edit" native-ui/app/src/main/java/com/promenar/nexara/ui/chat native-ui/app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt
```

```text
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/components/FilesPanel.kt:743:                                imageVector = Icons.Rounded.Edit,
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1286:                                imageVector = Icons.Rounded.Edit,
native-ui/app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt:768:                Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, editDescription, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
```

三个Edit依次用于文件重命名、会话更多菜单的重命名、代码编辑/保存；标题旁无Edit。streamingTailFade、STREAM_TAIL_FADE_DURATION_MS、ChatInputTopBar无生产命中。

指定静态核查第二条：

```bash
grep -rn "spacedBy" native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt
```

```text
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:886:        horizontalArrangement = Arrangement.spacedBy(8.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:899:                horizontalArrangement = Arrangement.spacedBy(6.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:936:            horizontalArrangement = Arrangement.spacedBy(4.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:970:            horizontalArrangement = Arrangement.spacedBy(4.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1029:        verticalArrangement = Arrangement.spacedBy(NexaraSpacing.Small),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1160:        horizontalArrangement = Arrangement.spacedBy(NexaraSpacing.Small),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1382:        horizontalArrangement = Arrangement.spacedBy(8.dp),
native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt:1637:        verticalArrangement = Arrangement.spacedBy(24.dp)
```

命中依次属于状态条、任务胶囊、模型、用量、上下文面板、Token明细行、附件条和空态布局；消息LazyColumn无spacedBy。`SessionSettingsSheet` 无initialTab，RagDetailsSheet自己的实际调用参数不受影响；正式资源中两项测试Markdown内容已移除。

#### 当前APK与范围核查

下列产物均为主机编译结果，未安装。ARM64 DeviceTest主包实际只含arm64-v8a原生库，测试包无原生库。

| 产物 | 字节 | SHA-256 |
| --- | --- | --- |
| `native-ui/app/build/outputs/apk/sideload/debug/app-sideload-debug.apk` | 152617002 | `b123e0b4c21181ac059afaa75ce38c0e72f690e18f082fbcbb4977be0999d00b` |
| `native-ui/app/build/outputs/apk/play/debug/app-play-debug.apk` | 152518670 | `883d4a280ffe009d45c706d41532d3305dbc842a3712d8ec132c4d62d3009e14` |
| `native-ui/app/build/outputs/apk/sideload/deviceTest/app-sideload-deviceTest.apk` | 138116399 | `b6a6bbbf5307941ca2033f7b80850d9f892912f3ca5c1796411d7e3fc8c366fe` |
| `native-ui/app/build/outputs/apk/androidTest/sideload/deviceTest/app-sideload-deviceTest-androidTest.apk` | 9458031 | `62b8b55124ba3332fe10d144941cf2f958ae7878d20860770a1de7ee34aa2e04` |

`git diff --check` 返回0、无输出；暂存区为空。相对初始快照，本轮不含报告目录共有新增15、更新49、删除3个文件，其中图片新增4/更新22/删除1，正式资源删除2。受保护的ChatLayoutTest、ShareImportAndroidEndToEndTest、NexaraSettingsSection和MessageManager与初始快照SHA相同。handover/index、PDEC、构建配置与ui/settings、ui/rag、ui/hub无Git差异；既有Type.kt与其他初始未提交改动保持。没有新增依赖、存储键或默认值变化。

### 13.8 DIA、HLG与验收界限

DIA：已同步 `CHANGELOG.md` 的会话界面条目、`README.md`、`design-qa.md` 与 `docs/superpowers/specs/2026-10-17-nexara-settings-chat-redesign.md`。规格5.6已明确可见内部ID、普通原因中性色、风险与参数分离；5.5/5.7及复制/安全区规则与实现一致，顶部状态保持“批次一、批次二均已实施”。复核context-management与ADR-020的既有批次二说明，无本轮新增文档影响，未再改动。

HLG：已按项目规则恢复必要上下文；按任务书明确边界未追加handover，也未创建另一治理根。未提交、未推送、未运行或操作任何设备/模拟器，未执行Gradle --stop或结束其它守护进程，未读取禁止的秘密/制品目录与签名材料。

Agent路由：原生stream_markdown负责流式与性能工作包，原生independent_review在集成后串行只读审阅，主控负责chat实现、测试、集成和验收。仅有文件所有权及提示词逻辑边界，无硬文件隔离；未使用外部CLI Agent、付费Provider或新增能力。主控复核了相对初始快照的源码diff、断言、保留项、JVM/Lint/编译回执、26张变化图及五处墨迹间距。

第3.1–3.14项的实现与要求的主机验收均已完成。新增设备用例只编译；跟随校正是否达到同帧/下一帧、手动上滑后的真实列表行为、异步长历史尾部、48dp触控命中、英文换行、横屏系统安全区、公式像素、旋转/滚出返回、TalkBack与实际动画设置尚无本轮设备运行证据。第12节API31/API35是已有主控记录，不能替代这些新用例的实跑。主机未运行性能基准或10秒流式录屏；复杂度为源码路径分析，未宣称设备帧率、所有字体或OEM已验收。

### 13.8 主控设备复核补充

- `chat/CompactStepRow.kt`：部分失败只把失败段标红，摘要与箭头保持中性色；整步失败（`tone` 为 `error`）时整行为错误色。摘要与失败段放入占满剩余宽度的内层行，摘要 `weight(1f, fill = false)` 先截断，失败段紧跟摘要，箭头固定在行尾；修正前摘要 `weight(1f)` 填满剩余宽度，宽屏上失败段被推到行尾、与摘要分离。`ChatStepFailureLayoutTest` 断言摘要为中性色、失败段紧邻摘要，新增 600dp 宽行用例 `shortSummaryKeepsFailureAdjacentAndArrowAtRowEndOnWideRow`。
- `ChatStepFailureLayoutTest`、`ChatApprovalScopeLayoutTest`：不换行文本按最大宽度排版，`hasVisualOverflow` 在文字未截断时也为真（实测文字宽 186px、段落宽 887px）；改为断言末行未省略且行右缘不超过文字节点宽度。
- `RagDetailsAccessibilityTest#partialKnowledgeRetrievalKeepsActualResultsErrorTextAndDetailsAction`：失败提示改由独立的 `CHAT_STEP_FAILURE` 文字段承载，用例改为断言该段包含失败提示且为错误色、摘要不含失败提示且为中性色。
- API 31：会话包 111 项中仅 4 项为基线既有失败（`AttachmentActionMenuTest`、`ChatProviderSwitchSurfaceTest`、`RagDetailsAccessibilityTest#tabsExposeRoleSelection…`、`ResourceExplorerInteractionTest`），`AccessibilitySmokeTest`、`MarkdownResourceFixtureTest`、`TextWrappingDeviceTest`、`ShareImportAndroidEndToEndTest` 共 18 项全部通过；第 13.5 节新增设备用例全部通过。
