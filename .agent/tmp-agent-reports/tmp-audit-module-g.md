# Nexara UI/UX 设计合理性与可视化充分性审计报告（模块 G）

> 审计范围：`ui/`（Screen/Composable）、`ui/theme/`、`ui/common/`、`navigation/`
> 审计视角：用户交互体验与"避免过度黑盒"，非代码正确性
> 审计日期：2026-07-05｜只读审计，未修改任何文件
> 严重度分级：🔴 严重（阻断核心流程/显著误导用户）｜🟠 中等（体验明显受损）｜🟡 轻微（可优化）

---

## 一、黑盒程度矩阵（核心交付物）

| 功能模块 | 当前可见性 | 理想可见性 | 差距 | 相关文件:行号 |
|---|---|---|---|---|
| **Provider 选择** | 用户完全不知当前对话走哪个 Provider；模型 chip 只显示模型名（`note`/id），不显示 Provider | 对话页应同时显示"Provider · 模型"或至少在 TopBar 副标题可见 | 缺 Provider 维度指示 | `ChatScreen.kt:407-411`（仅取 `findModelSpec(id)?.note`）；`ChatScreen.kt:643-648`（chip 只显示 modelName） |
| **模型选择** | 输入框上方有模型 chip，可点击切换 ✅；但首次进入若未选模型，仅靠 toast 气泡提示 | 已较好；建议空状态时 chip 用高亮边框强提示 | 基本达标 | `ChatScreen.kt:469`（`isModelSelected` 判定）、`ChatScreen.kt:476-496`（hint 气泡） |
| **推理参数（temperature/topP/maxTokens…）** | 藏在 SessionSettingsSheet 的"思考"Tab + 高级折叠区，改完无任何"已生效/下条消息生效"反馈 | 改动后应有轻量 snackbar 或 chip 状态变化提示 | 缺生效反馈 | `SessionSettingsSheet.kt:416-725`（无保存提示）；`PipelineBubble.kt:220-228`（仅显示 modelId+时间，不显示参数） |
| **RAG 是否启用** | SessionSettingsSheet"设置"Tab 内多个 toggle；全关时有提示卡 ✅。但**对话页运行时无"RAG 开/关"总览指示** | 对话页应有"本次对话将检索：文档/记忆/图谱"的小徽章 | 缺运行时总览徽章 | `SessionSettingsSheet.kt:946-981`（仅 Sheet 内提示）；`ChatScreen.kt:413-420`（输入栏无 RAG 状态） |
| **RAG 检索了什么** | RagProgressCard 显示阶段 + 点击展开 RagDetailsSheet（检索片段/联网引用/图谱路径）✅，非常充分 | 已优秀 | 达标 | `ChatInlineComponents.kt:378-553`、`RagDetailsSheet.kt` 全文 |
| **工具是否启用/调用了什么** | SessionSettingsSheet"工具"Tab 控制；运行时 InlineToolRow 显示工具名+参数+结果（可展开）✅ | 已较好；但"工具总开关"与"执行模式(auto/semi/manual)"在对话页不可见 | 缺执行模式运行时指示 | `SessionSettingsSheet.kt:729-804`；`PipelineBubble.kt:512-654` |
| **工具执行模式（auto/semi/manual）** | 仅在 Sheet 内静态展示当前模式文本，对话流中工具调用前**无审批入口呈现**（ApprovalCard 组件存在但未在 ChatScreen 接线） | manual/semi 模式下应在工具调用前弹出 ApprovalCard 让用户确认 | 审批机制未接入对话流 | `ChatInlineComponents.kt:1094-1194`（ApprovalCard 定义但无调用方）；`SessionSettingsSheet.kt:776-802` |
| **Token/上下文用量** | 输入栏 TokenIndicator 圆环 + 点击展开明细（system/summary/active/rag）✅，优秀 | 已优秀 | 达标 | `ChatScreen.kt:666-749` |
| **当前用哪个模型生成本条回复** | PipelineBubble 元信息行显示 `modelId`（原始 id，未解析为友好名）+ 时间 | 应显示 `note`/友好名而非裸 id | 显示原始 id 不友好 | `PipelineBubble.kt:220-226`（直接用 `lastMsg.modelId`） |
| **错误"错在哪/怎么修"** | 错误以红色文本显示 `errorMessage`（常为英文异常 message）；按钮抖动+闪烁；无"重试/检查配置"引导 | 应分类错误（鉴权/超时/模型不存在）并给出可操作建议 | 缺错误分类与可操作恢复 | `PipelineBubble.kt:231-241`；`ChatViewModel.kt:401,568-569,591`（直接 e.message） |
| **向量化/导入进度** | RagHome 顶部 IndexingProgressBar（进度%+状态+子状态+错误可关闭）✅，优秀 | 已优秀 | 达标 | `RagHomeScreen.kt:220-252`；`components/IndexingProgressBar.kt` |
| **知识压缩/Summary** | SummaryCard（压缩中进度+完成可展开查看摘要）✅ | 已优秀 | 达标 | `ChatInlineComponents.kt:926-1092` |
| **Provider 是否已配置（首次）** | **无任何引导**；首次进 AgentHub 创建 Agent 时模型列表为空，用户不知要去设置页配 Provider | 首次启动应检测 Provider 空状态并引导至设置 | 缺首次配置引导 | `WelcomeScreen.kt`（无 Provider 引导）；`NavGraph.kt:142-155` |

---

## 二、严重问题（🔴）

### G-01 首次启动无引导，用户陷入"无法对话"死局
**文件**：`ui/welcome/WelcomeScreen.kt:37-131`、`navigation/NavGraph.kt:142-155`、`MainActivity.kt:42-47`

**用户视角**：用户第一次打开 app，WelcomeScreen 只让选语言，点完直接进 AgentHub。用户创建 Agent 时模型选择器是空的（因为还没配 Provider/拉取模型列表），但界面上没有任何提示告诉用户"你需要先去设置 → Provider 配置 API Key 才能用"。用户会以为是 bug。

**问题**：
- WelcomeScreen 完全没有 onboarding 引导（`WelcomeScreen.kt:93-102` 两个语言按钮 `onClick = onNavigateToChat` 都直接跳走，未检测 Provider 状态）
- AgentHub 空状态（`AgentHubScreen.kt:245-292`）只说"创建 Agent"，不提"先配 Provider"
- AddAgentDialog 模型选择（`AgentHubScreen.kt:341-351`）在无 Provider 时静默无选项

**建议**：WelcomeScreen 选完语言后检测 `app.getSavedProviderConfig()`，若为空则路由到 `provider_form`；或在 AgentHub 空状态加"未配置 Provider → 去设置"引导卡。

---

### G-02 浅色主题完全不可用，但代码假装支持
**文件**：`ui/theme/Theme.kt:16-59`、`ui/settings/ThemeScreen.kt:38-111`、`MainActivity.kt:27-28`

**用户视角**：系统是浅色模式或用户在（不可达的）ThemeScreen 选了"浅色"，app 仍然是深色，且所有颜色都是为深色设计的（`Color.kt` 全部硬编码深色）。用户会困惑"我选了浅色为什么没变化"。

**问题**：
- `Theme.kt` 只定义 `DarkColorScheme`（第16行），`else -> DarkColorScheme`（第58行）无论 `darkTheme` 真假都走深色
- `NexaraColors`（`Color.kt:5-62`）全部硬编码深色值，无浅色对应
- ThemeScreen（`ThemeScreen.kt:38`）定义了 LIGHT/DARK/SYSTEM 三选项，NavGraph 也注册了路由（`NavGraph.kt:424`），但 **UserSettingsHomeScreen 里没有任何入口跳转到 `theme_config`**（grep 确认无 `theme_config` onClick）→ 整个 ThemeScreen 是死代码
- MainActivity（`MainActivity.kt:28`）调用 `NexaraTheme{}` 未传 `darkTheme`，且忽略 `themeMode`

**建议**：要么移除 ThemeScreen 死代码并明确"仅深色"；要么实现完整浅色色板并在 MainActivity 根据 `themeMode` 传入 `darkTheme` 并在设置首页加入口。

---

### G-03 工具执行的"审批模式"对用户形同虚设
**文件**：`ui/chat/components/`（ApprovalCard 未接线）、`ui/chat/SessionSettingsSheet.kt:776-802`、`ui/chat/ChatInlineComponents.kt:1094-1194`

**用户视角**：用户在会话设置里把执行模式改成"manual"，期望工具调用前要自己点批准。但实际对话流里**根本没有 ApprovalCard 出现**——工具直接执行，用户以为开了审批其实没生效。这是严重的"用户控制感被剥夺"。

**问题**：
- `ApprovalCard` 组件完整存在（`ChatInlineComponents.kt:1094-1194`，含 onApprove/onDecline 按钮），但全代码库无任何调用方（grep 无 `ApprovalCard(` 调用）
- SessionSettingsSheet 只静态显示当前模式文本（`:776-802`），未将 `executionMode` 接入 ChatViewModel 的工具执行流

**建议**：在 PipelineBubble 的 ToolExec 步骤前，当 `executionMode != "auto"` 时渲染 ApprovalCard，由 ApprovalManager（已存在 `manager/ApprovalManager.kt`）挂起等待用户决策。

---

## 三、中等问题（🟠）

### G-10 对话页不知"当前 Provider 是谁"
**文件**：`ChatScreen.kt:407-411`、`ChatScreen.kt:631-649`

**用户视角**：用户配了多个 Provider（OpenAI、Gemini、本地 Ollama），在对话页只能看到模型名（如"gpt-4o"），无法一眼看出这个模型走的是哪个 Provider、是云端还是本地。切换会话时容易混淆"我刚才那条是本地跑的还是走 API 的"。

**建议**：模型 chip 改为 `[Provider图标] Provider简称 · 模型名`，或 TopBar 副标题显示 Provider。

---

### G-11 对话页无"RAG 本次将检索什么"的运行前总览
**文件**：`ChatScreen.kt:413-420`、`SessionSettingsSheet.kt:946-981`

**用户视角**：用户在设置里勾了"记忆+文档"，回到对话页看不到任何提示这次对话会去检索哪些知识源。只有等 AI 真去检索了才看到 RagProgressCard。用户无法在发送前确认"这次对话的知识范围"。

**建议**：输入栏 TokenIndicator 旁加一组小徽章（🧠记忆 / 📄文档 / 🕸图谱 / 🌐联网），反映当前 session ragOptions。

---

### G-12 错误信息对用户不友好，无恢复路径
**文件**：`PipelineBubble.kt:231-241`、`ChatViewModel.kt:401,568-569,591-595`

**用户视角**：对话失败时，用户看到一行红色英文异常文本（如"HTTP 401 Unauthorized"或"timeout"），不知道是 API Key 错了、还是网络断了、还是模型名写错。也没有"重试"或"去检查 Provider 设置"的按钮。

**问题**：`errorMessage` 直接来自 `e.message`（`ChatViewModel.kt:591`），未做错误分类与友好映射。

**建议**：错误分类（鉴权/超时/模型不存在/配额），渲染卡片含"重试 / 去设置"两个按钮。

---

### G-13 改完推理参数无"已生效"反馈
**文件**：`SessionSettingsSheet.kt:416-725`、`PipelineBubble.kt:220-228`

**用户视角**：用户在会话设置 Sheet 里拖动 temperature 滑块、改 maxTokens，改完关掉 Sheet，对话页没有任何变化提示。用户不确定"改的值存下来了没""下一条消息生效吗"。

**建议**：参数变更后显示 snackbar"已更新，下条消息生效"；或 PipelineBubble 元信息行附带关键参数（如 temp=0.7）。

---

### G-14 messageContextMenu 与 RagDetailsSheet 大量硬编码中文，破坏 i18n
**文件**：`PipelineBubble.kt:908,919,937`、`ResourceExplorerSheet.kt:59,65,100,116`、`FilesPanel.kt`（多处）、`SessionSettingsSheet.kt:758,764,936`、`RagDetailsSheet.kt:39-42,81,135,153`、`ChatInlineComponents.kt:367-374,404-417`、`SessionSettingsScreen.kt:99`

**用户视角**：用户在 WelcomeScreen 选了 English，进入对话后发现"复制正文/重发/删除消息/正在思考/思考完成/资源管理器/文件/回收站/调用参数/工具/指令有误/未配置默认重排模型..."全是中文。app 号称双语实际只翻译了一半。

**量化**：UI 层硬编码含中文字符串约 228 处（grep 统计），`contentDescription = null` 占比 121/187 ≈ 65%。

**建议**：将所有硬编码中文迁移到 `strings.xml` / `strings-zh-rCN`；这是"中英文混排"最严重的破坏点。

---

### G-15 深层功能入口可发现性差，层级过深
**文件**：`navigation/NavGraph.kt`、`hub/UserSettingsHomeScreen.kt:442-551`

**用户视角**：知识图谱是个重要功能，但入口是：底部 Tab 知识库 → 顶部 Tab"图谱"（且点了直接跳走，`RagHomeScreen.kt:209` `if (tab == PortalTab.GRAPH) onNavigateToGraph()`，Tab 本身不保持选中态）→ 进入 KnowledgeGraphScreen。或者：设置 → RAG 配置 → 高级检索 → 知识图谱（4 层）。

**问题**：
- 知识库 Tab 的"图谱"子 Tab 点击后跳页而非切内容（`:209`），交互模式与 Documents/Memory 不一致
- RAG 高级检索链路：Settings → rag_global_config → rag_advanced_kg → knowledge_graph（3-4 层深，`NavGraph.kt:294-298,287-292,275-279`）
- 开发者面板入口是"关于 Nexara"（`UserSettingsHomeScreen.kt:547-548` `onAboutClick → developer_panel`），命名误导

**建议**：统一 Tab 交互；图谱给独立入口或常驻操作；开发者面板正名为"开发者选项"。

---

### G-16 PipelineBubble 元信息显示原始 modelId 不友好
**文件**：`PipelineBubble.kt:220-226`

**用户视角**：每条 AI 回复下方显示"openai/gpt-4o-mini-2024-07-18"这样的原始 id，而非"GPT-4o mini"。ChatScreen 输入栏（`:407-411`）已经做了 `findModelSpec(id)?.note ?: id` 友好解析，但 PipelineBubble 没做。

**建议**：PipelineBubble 同样用 `findModelSpec(lastMsg.modelId)?.note ?: lastMsg.modelId`。

---

## 四、轻微问题（🟡）

### G-20 用户消息气泡最大宽度 280dp 偏窄
**文件**：`PipelineBubble.kt:785`（`.widthIn(max = 280.dp)`）

**用户视角**：用户发较长消息时，气泡被强制压到 280dp 后才换行，导致一行显示字数少、纵向过高。AI 回复是全宽，对比下用户气泡显得局促。

**建议**：放宽至 `max = 320.dp` 或用 `fillMaxWidth(0.82f)`。

---

### G-21 输入框无字数提示、无多行高度上限
**文件**：`ChatScreen.kt:936-955`（BasicTextField 无 maxLines/heightIn）

**用户视角**：用户粘贴长文本时，输入框会无限增高把对话内容顶上去，且无字数/token 估算提示（虽有 TokenIndicator 但那是整上下文，不是输入框当前文本）。

**建议**：输入框加 `heightIn(max = 160.dp)` 内部滚动；输入时实时显示当前字符/估算 token。

---

### G-22 底部导航无 contentDescription 风险 & 按压无 ripple
**文件**：`MainTabScaffold.kt:148-155`

**问题**：TabItem 用 `indication = null` 关闭了 ripple（`:151-154`），仅靠 scale 动画反馈。对触觉反馈期待 ripple 的用户，按压反馈偏弱。Icon 有 contentDescription（`:161`）✅。

**建议**：保留 scale 但恢复轻微 ripple 或确认 haptic 已开。

---

### G-23 动效可能过度（流式跟随 8ms 轮询 + 多处 infiniteTransition）
**文件**：`ChatScreen.kt:236-255`（8ms 轮询）、`ChatInlineComponents.kt` 多个 rememberInfiniteTransition、`NeonMicroRail`（`ChatInlineComponents.kt:556-667` 含 sweep/呼吸/shimmer 多层动画）

**用户视角**：低端机上 RagProgressCard 的雷达旋转+霓虹进度条+文本翻页+shimmer 流光同时跑，可能卡顿且耗电；8ms（120Hz）轮询滚动对电池不友好。

**建议**：对 `NeonMicroRail` 等纯装饰动画提供"减少动效"开关；流式跟随轮询放宽到 16-32ms。

---

### G-24 SessionSettingsSheet 与 SessionSettingsScreen 功能重叠
**文件**：`chat/SessionSettingsSheet.kt`（4 Tab 全功能）vs `chat/SessionSettingsScreen.kt`（独立页，含 attachedDocs 假数据）

**问题**：两者都叫"会话设置"，Sheet 已是完整 4-Tab（模型/思考/工具/设置）；而 SessionSettingsScreen（`SessionSettingsScreen.kt:81` `attachedDocs = remember { mutableStateListOf("Q3_Report_Final.pdf"...) }`）还含**硬编码假数据**，像是未完成的原型。NavGraph 中 SESSION_SETTINGS 路由（`NavGraph.kt:202-211`）虽有注册但 ChatScreen 的"会话设置"菜单项（`:791-797`）实际打开的是 Sheet（`showModelSettingsSheet`），而非 Screen → Screen 路由实际无入口，疑似死代码。

**建议**：移除未用的 SessionSettingsScreen 或明确其定位；清理假数据。

---

### G-25 Workspace/Session Settings Sheet 路由是 Placeholder
**文件**：`NavGraph.kt:250-262`

**问题**：`SESSION_SETTINGS_SHEET` 和 `WORKSPACE_SHEET` 路由渲染的是 `PlaceholderScreen("...")`（`:254,261`），即占位空屏。虽然实际功能用 ModalBottomSheet 实现无需这些路由，但留着的占位路由会混淆。

**建议**：删除未使用的占位路由。

---

## 五、维度小结

| 维度 | 评分 | 说明 |
|---|---|---|
| **新手友好度** | 🔴 差 | 无 onboarding，空状态不引导配 Provider（G-01） |
| **状态可见性** | 🟠 中 | RAG 检索内容、Token、向量化进度做得优秀；但 Provider、RAG 开关总览、执行模式运行时可见性不足（G-10/11/03） |
| **操作可达性** | 🟠 中 | 高频操作（发送/换模型/换会话）触手可及；深层功能（图谱/高级检索/开发者）层级过深（G-15） |
| **错误恢复** | 🔴 差 | 错误无分类、无重试按钮、无引导（G-12）；删除消息有 undo snackbar ✅ |
| **信息密度** | 🟢 良 | 各屏密度适中，Sheet 用 Tab+折叠分级合理 |
| **一致性** | 🟠 中 | 视觉风格（玻璃/极光）全站统一 ✅；但 Tab 交互不一致（G-15）、设置入口重叠（G-24）、i18n 不一致（G-14） |
| **无障碍** | 🔴 差 | contentDescription=null 占 65%；硬编码中文破坏 TalkBack/英文用户（G-14） |
| **多语言** | 🔴 差 | 约 228 处硬编码中文未走 strings.xml（G-14） |
| **视觉一致性** | 🟢 良 | 深色玻璃风格统一；但"仅深色"与 ThemeScreen 死代码矛盾（G-02） |
| **动效** | 🟡 可优化 | 部分过度（G-23） |

---

## 六、优先修复建议（按投入产出排序）

1. **G-01** 首次启动 Provider 引导（高价值，改动小）
2. **G-14** 硬编码中文迁移 strings.xml（工作量大但必须，影响 i18n/无障碍基线）
3. **G-03** 接入 ApprovalCard 让 manual/semi 模式真正生效（核心控制感）
4. **G-02** 决定 ThemeScreen 去留并补浅色或移除死代码
5. **G-12** 错误分类 + 重试/去设置 按钮
6. **G-10/11** 对话页补 Provider 指示与 RAG 运行前徽章
7. **G-15** 统一 Tab 交互 + 图谱独立入口
8. **G-24/25** 清理 SessionSettingsScreen 死代码与占位路由
