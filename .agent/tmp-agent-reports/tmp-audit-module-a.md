# 审计报告：模块 A — 消息发送链路

> 只读审计 · 范围：用户输入 → API 请求发出 → 流式接收 → 错误处理 的完整链路
> 审计日期：2026-07-05

---

## 审计范围

实际通读并交叉核对的文件清单：

**入口与编排**
- `ui/chat/ChatViewModel.kt`（1847 行，核心编排）
- `ui/chat/ChatScreen.kt`（输入框 / 发送按钮 / 附件选择 UI）
- `ui/chat/manager/ContextBuilder.kt`（system prompt 拼装 + RAG/Web/KG 注入）
- `ui/chat/manager/MessageManager.kt`（消息落库 + UI 节流）
- `ui/chat/manager/SessionManager.kt`
- `ui/chat/manager/ToolExecutor.kt`
- `ui/chat/manager/PostProcessor.kt`
- `ui/chat/manager/SummaryManager.kt`
- `ui/chat/manager/ApprovalManager.kt`

**远程调用**
- `data/remote/UnifiedLlmClient.kt`
- `data/remote/provider/LlmProvider.kt`
- `data/remote/lifecycle/ToolCallLifecycleHandler.kt`
- `data/remote/middleware/LlmMiddleware.kt`、`LlmMiddlewareChain.kt`
- `data/remote/protocol/LlmProtocol.kt`（数据模型 + 协议接口）
- `data/remote/protocol/OpenAIProtocol.kt`
- `data/remote/protocol/AnthropicProtocol.kt`
- `data/remote/protocol/GenericOpenAICompatProtocol.kt`
- `data/remote/protocol/VertexAIProtocol.kt`
- `data/remote/protocol/ProtocolFactory.kt`、`ProtocolParamAdapter.kt`
- `data/remote/parser/ErrorNormalizer.kt`、`StreamBufferManager.kt`、`DsmlStreamParser.kt`、`StreamParser.kt`
- `data/remote/MessageFormatter.kt`、`MessageFormatterFactory.kt`
- `NexaraApplication.kt`（provider 构造，确认是否有 fallback）
- `data/model/ChatModels.kt`（InferenceParams / Message 模型）

---

## 总体评价

整体架构是**分层清晰、可扩展性较好**的设计：`UI → ChatViewModel → ContextBuilder/Managers → UnifiedLlmClient → Protocol(OpenAI/Anthropic/Vertex/Generic)`。`ProtocolFactory` 把各 provider 收敛为统一 `LlmProtocol` 接口、`StreamChunk` sealed class 把流式数据归一化、`ErrorNormalizer` 把异常分类为 NETWORK/AUTH/RATE_LIMIT/QUOTA 等枚举——这套抽象到位，新增 provider 成本低。`ContextBuilder.buildSystemPrompt` 对 time/tools/task/agent/session/RAG/KG/web/summary 的拼装顺序也基本合理，工具调用的增量 fragment 传递（避免双重累积）也做了正确的修复。

但这条链路存在**若干影响真实使用体验的硬伤**，集中在三方面：

1. **错误对用户不可见**：`ErrorNormalizer` 已经把错误归一化成了友好的中文文案（如「网络连接失败，请检查您的网络设置」），但 `ChatViewModel` 把这些文案写进 `_error` StateFlow 后，**`ChatScreen` 从未消费 `uiState.error`**——这个错误字符串永远不会显示给用户。错误唯一能被看到的路径是写进消息体的 `errorMessage`，而这只在部分错误分支发生。结果就是用户经常遇到「点了发送没反应」或「气泡里是空的」却看不到任何原因。

2. **完全没有自动重试与 provider fallback**：网络抖动 / 429 / 5xx 在移动端是常态。`StreamChunk.Error` 虽然带了 `retryable` 字段，但**整个链路没有任何一处读取 `retryable` 后做重试或退避**，更没有「主 provider 失败切备用 provider」的能力。一次偶发的网络闪断就等于一次彻底失败，用户只能手动点重发。

3. **多模态与重试/重发的数据丢失**：重发、重试、编辑重发三条路径都**丢掉了图片**——`retryLastMessage()` 调用 `sendMessage(content)` 时不传 `imageUris`，原始图片再也不会出现在请求里，但 UI 上用户以为图片还在。这是一个确定性的功能性 bug。

下面按严重程度展开。

---

## 发现的问题

### 🔴 严重问题（影响核心功能或数据安全）

#### A-001: 错误信息对用户完全不可见（静默吞错）
- **用户视角描述**：用户在弱网下点发送，转一下圈就停了，助手气泡是空的，**屏幕上没有任何错误提示**。用户会以为「app 卡了」反复点击，或者以为发送成功了。即便后端返回 401（密钥失效）或 429（限流），用户也只能看到一个空气泡，无法判断是网络问题、账号问题还是限流。
- **代码位置**：
  - `ChatViewModel.kt:563`、`588`、`398` 等处把错误写入 `_error.update { ... }`
  - `ChatViewModel.kt:95` `ChatUiState.error` 字段已暴露
  - **但 `ChatScreen.kt` 全文检索 `uiState.error` / `.error` 消费点为 0**（仅 `SnackbarHost` 用于「撤销删除」，从未绑定 error 流）
- **根因**：`_error` StateFlow → `ChatUiState.error` 这条数据流在 UI 层断裂。`ErrorNormalizer` 辛苦归一化出的友好文案（「API 密钥无效或已过期」「请求过于频繁，请等待 1 分钟后重试」）从未渲染到屏幕。
- **建议**：在 `ChatScreen` 用 `LaunchedEffect(uiState.error)` 监听，非空时 `snackbarHostState.showSnackbar(error)` 并调用 `chatViewModel.clearError()`；或单独渲染一个可点击的错误条。这是整个模块**优先级最高**的修复。

#### A-002: 无任何自动重试 / 退避，移动端弱网下极易失败
- **用户视角描述**：用户在地铁上、电梯里、切换 Wi-Fi/4G 的瞬间发消息，只要 SSE 连接被中断一次，就直接报错结束。用户必须手动重发，且重发时如果又抖动一次，再次失败。没有任何「正在重试第 2 次…」的提示。
- **代码位置**：
  - `StreamChunk.Error` 定义了 `retryable` 字段（`LlmProtocol.kt:142`）
  - `ErrorNormalizer` 已正确标注哪些可重试（`ErrorNormalizer.kt:26/45/62/80/89`）
  - 但 `UnifiedLlmClient.kt:79-101` 收到异常只是 `trySend(StreamChunk.Error(...))` 后结束；`ChatViewModel.kt:562-580` 收到 `StreamChunk.Error` 只更新 UI 状态，**从不读取 `chunk.retryable` 触发重试**
- **根因**：缺少一个「重试中间件」或在 `UnifiedLlmClient` / `ChatViewModel` 层的重试 + 指数退避逻辑。
- **建议**：对 `retryable=true` 的错误（NETWORK/RATE_LIMIT/SERVER_ERROR/TIMEOUT）在协议层或中间件层做 1~3 次指数退避重试；RATE_LIMIT 时读取 `retryAfter` 等待。至少应在 UI 给「重试」按钮。

#### A-003: 重试 / 重发 / 编辑重发会丢失图片（确定性数据丢失）
- **用户视角描述**：用户发了一张图+一段文字，失败后点「重发」。UI 上图片缩略图可能还在显示（或已被清空），但**重新发出去的请求里只有文字、没有图片**。模型完全看不到图，给出风马牛不相及的回答，用户会非常困惑。
- **代码位置**：
  - `ChatViewModel.kt:967` `retryLastMessage()` → `sendMessage(lastUserMsg.content)` —— 只传 content，不传 imageUris
  - `ChatViewModel.kt:991` `regenerateLastMessage()` 直接调 `retryLastMessage()`，同样丢图
  - 虽然 `lastUserMsg.userImages` 里其实存了 data URL，但重发路径没有把它取回传给 `sendMessage`
- **根因**：重发逻辑没有从历史消息中恢复多模态附件。
- **建议**：`retryLastMessage` 从 `lastUserMsg.userImages` 还原附件并传入；或在 `sendMessage` 内部支持「基于已有 user message 重发」的模式。

#### A-004: 无 Provider Fallback（主服务挂了无法自动切换）
- **用户视角描述**：用户配置了主模型（如 OpenAI），当 OpenAI 区域性宕机或被墙时，即便用户在设置里配过其它 provider，app 也只会报错，不会自动切到备用 provider。对国内用户尤其常见（OpenAI 不稳定）。
- **代码位置**：
  - `NexaraApplication.kt:585-600` `buildUnifiedLlmClient()` 只构建**单一** provider config
  - `NexaraApplication.kt:602-623` `buildProviderFromPrefs()` 同样是单 provider
  - 全局搜索 `backupProvider` / `secondaryProvider` / `fallback provider` 均无结果
- **根因**：架构上只有「主 provider」概念，没有 provider 优先级链。
- **建议**：引入 `ProviderChain`，允许用户配置备用 provider 列表；主 provider 抛 `retryable=false` 或重试耗尽后，自动用备用 provider 重发。至少对 AUTH 类错误不应 fallback（密钥问题换 provider 也无用），对 NETWORK/SERVER_ERROR 类应 fallback。

#### A-005: 多模态无大小 / 数量 / 类型校验，大图直接 OOM 或被 API 拒
- **用户视角描述**：
  - 用户选了一张 40MB 的相机原图，`sendMessage` 里 `readBytes()` 把整张图读进内存再 base64，**base64 后膨胀到 ~53MB 字符串**，多张图叠加极易触发 `OutOfMemoryError`，app 直接崩溃，且因为是在 `viewModelScope.launch` 里，崩溃可能不会被上层捕获。
  - 用户选了 10 张图，没有任何数量上限提示，请求体可能超过 provider 的 body 上限（多数 provider 限制单请求几 MB ~ 32MB），被 413/400 拒绝，但用户只看到空结果（叠加 A-001）。
  - 文件选择器用 `GetMultipleContents("image/*")`（`ChatScreen.kt:157`），但部分设备会返回 GIF/HEIC/SVG，mimeType 直接透传给 API，许多 provider 不支持，请求被拒。
- **代码位置**：
  - `ChatViewModel.kt:260-266` `imageUris.mapNotNull { ... readBytes() ... Base64.encodeToString(...) }` —— 全量读入内存，无压缩、无尺寸限制
  - 全文搜索 `MAX_IMAGE` / `maxSize` / `fileSize` / `compress` 在发送路径**均无**
  - `ChatScreen.kt:461` 发送判定只看 `isNotBlank() || isNotEmpty()`，无数量阈值
- **根因**：发送前缺一个「附件预处理」环节（压缩到合理尺寸/上限、限制数量、白名单 mimeType）。
- **建议**：发送前对每张图做下采样压缩（如长边压到 1568px、质量 80，参考业界做法）；硬限制单图 ≤ 5MB、单消息 ≤ 5 张；对不支持的 mimeType 给 UI 提示。

---

### 🟡 中等问题（影响体验或健壮性）

#### A-006: 历史消息窗口按「条数」截断，而非按 token，长消息会爆上下文
- **用户视角描述**：用户粘贴了一篇 2 万字的长文让模型总结，接下来几轮对话，`activeContextWindow=15` 会把这 2 万字连同其它 14 条消息全量塞进请求，**轻松超过模型的 context window**，请求被 provider 以 `context_length_exceeded` 拒绝，用户又一次看到空结果（叠加 A-001）。
- **代码位置**：
  - `ChatViewModel.kt:1280-1295` `getSafeActiveWindow()` 完全按 `messages.size - windowSize` 截断，只做了「不切断 tool pair」的保护，**没有任何 token 估算**
  - `ChatViewModel.kt:1238` `activeWindowSize = session.inferenceParams?.activeContextWindow ?: 10`
  - `PostProcessor.estimateTokens()`（`PostProcessor.kt:181`）只是 `length/4`，是个粗估，且**只在 token 指示器和统计里用，从未用于截断决策**
- **根因**：截断策略是「固定条数」而非「token 预算」。
- **建议**：改为 token-budget 截断——预留 system+tools+maxOutput 的预算后，从最新消息向前累加 token 直到预算耗尽；并对超长单条消息做截断或提示。

#### A-007: SSE 流中断后，已收到的部分内容会「卡住」且不可恢复
- **用户视角描述**：模型正在流式输出到一半（比如已经吐了 200 字），此时网络断开。用户看到的现象是：输出**停在半句话**，发送按钮恢复成可发送，但那半句话就挂在那儿，既不会继续、也没有错误标记（因为 `StreamChunk.Error` 的文案不可见，见 A-001）。用户无法区分「正常结束」还是「中断」。
- **代码位置**：
  - `OpenAIProtocol.kt:87-90`（及 Anthropic/Generic/Vertex 同构）：`withTimeoutOrNull(timeoutMs)` 超时后 `send(StreamChunk.Error("Streaming timeout..."))` 然后 `break`
  - `ChatViewModel.kt:562-580`：收到 Error 后，若已有内容则**不取消流、让流自然结束**（P0-2 修复的副作用），最终走到 `COMPLETED` 分支或 `ERROR` 分支——但「有半截内容 + 超时」会被当成「有内容」走工具反馈或 COMPLETED，**中断的内容被当成完整回答**
- **根因**：没有「流被中断」与「流正常结束」的显式区分；`StreamChunk.Done` 和超时 break 走同一收尾路径。
- **建议**：引入 `StreamChunk` 的「中断/未完成」状态；中断时在消息上打 `truncated=true` 标记并在 UI 显示「⚠️ 输出因网络中断未完成，点此重试」。

#### A-008: 流式错误与正常收尾的状态机不清晰，半截内容被当成功
- **用户视角描述**：接 A-007。当流式中途收到 `StreamChunk.Error` 但已累积了内容时，`ChatViewModel.kt:632` 的判断 `accumulatedToolCalls.isNotEmpty() && (... isActive || streamingError != null)` 会**带着错误继续走工具执行循环**。若半截内容里恰好被 Fallback 解析器误识别成工具调用（见 A-011），可能执行非预期工具。
- **代码位置**：`ChatViewModel.kt:575-632` 的 P0-2 修复逻辑
- **根因**：「错误后是否继续」缺少明确策略；目前是「只要有工具调用就继续」，对错误容错过度。
- **建议**：错误后只在「内容+工具调用都完整」时继续，否则标记中断并提示重试。

#### A-009: `MessageFormatter` / `formatHistory` 全链路从未被调用（死代码 + provider 特化逻辑缺失）
- **用户视角描述**：`MessageFormatter.kt` 里为 DeepSeek/Gemini 写了 `enhanceSystemPrompt`（注入工具调用规范），为 OpenAI/GLM/Kimi 标注了「不支持 reasoning 回传」。但这些逻辑**从未生效**——实际发送的请求里，assistant 历史消息的 `reasoning` 字段会被原样发给所有 provider（`ChatViewModel.kt:1254-1265` 的 `buildProtocolMessages` 不做 provider 特化），那些不支持 reasoning 的 provider（OpenAI/GLM/Kimi）可能因收到非法字段而报错或行为异常；DeepSeek/Gemini 的工具规范注入也没生效，导致这两个模型在工具调用时表现不如预期。
- **代码位置**：
  - 全局搜索 `MessageFormatterFactory` / `formatHistory` / `: MessageFormatter` 的**调用点为 0**（仅定义在 `MessageFormatter.kt` / `MessageFormatterFactory.kt`）
  - 实际拼装走 `ChatViewModel.buildProtocolMessages()`（`1228-1278`），它对**所有 provider 一视同仁**
- **根因**：`MessageFormatter` 抽象被建立后没有接入发送链路，沦为止损用的死代码；provider 特化（reasoning 裁剪、system prompt 增强）实际没生效。
- **建议**：要么删掉 `MessageFormatter` 减少误导，要么在 `buildProtocolMessages` 里按 `protocolType` 调用对应 formatter，让 reasoning 裁剪和 prompt 增强真正生效。

#### A-010: 大量数据库异常被 `catch (_: Exception) {}` 静默吞掉
- **用户视角描述**：用户的消息可能**落库失败但 UI 显示成功**（因为 `addMessage` 先更新内存 store 再异步落库，落库失败被吞）。用户切换设备或重启 app 后发现消息消失了，却完全不知情。同理，删除、批量删除、向量化状态更新失败都对用户不可见。
- **代码位置**：`MessageManager.kt:88`、`104`、`280`、`304`、`330`、`368`、`396`；`SessionManager.kt:23`、`34`、`43`
- **根因**：统一的「吞异常」模式，没有任何日志或用户提示。
- **建议**：至少 `NexaraLogger.logError` 记录；关键路径（消息插入）失败时给 UI 一个软提示「消息可能未持久化」。

#### A-011: Fallback 工具调用解析的「安全放行」可能执行任意已注册工具
- **用户视角描述**：`isKnownTool` 里有一行「**如果工具列表为空，说明 registry 可能未就绪，信任放行**」（`ChatViewModel.kt:1666`）。这意味着在 `skillRegistry` 未初始化好的窗口期，模型从文本里「吐」出的任何工具名（哪怕是幻觉）都会被执行。结合 `write_file` / `exec_js` / `generate_image` 这类高风险工具（`ChatViewModel.kt:1705`），存在「模型幻觉导致非预期文件写入/代码执行」的风险。
- **代码位置**：`ChatViewModel.kt:1659-1667` `isKnownTool()`
- **根因**：「registry 未就绪」与「信任放行」方向相反——未就绪时应**拒绝**才安全。
- **建议**：registry 为空时返回 `false`（拒绝），而不是放行；或仅对只读工具放行。

#### A-012: 发送中切换会话 / 退出 app，旧会话的流仍在跑，可能串扰新会话
- **用户视角描述**：用户在会话 A 发了一条消息（正在流式接收），立刻切到会话 B 发第二条消息。此时 `_currentSessionId` 变了，但会话 A 的 `generationJob` 虽会被 `cancelActiveGeneration()` 取消（`ChatViewModel.kt:268` 在 `sendMessage` 入口调用），取消的是协程，**协议层的 HTTP 连接和 `activeChannel` 不一定立即关闭**（`OpenAIProtocol.cancel()` 取消 channel，但取消时序依赖协程取消传播）。更隐蔽的是：会话 A 流式中途累积的状态（`accumulatedContent` 等）是局部变量，取消后丢弃，但**已经写入 store/DB 的半截内容留在会话 A 里**，用户切回 A 会看到一个半截气泡。
- **代码位置**：
  - `ChatViewModel.kt:268-269` `cancelActiveGeneration()` + `generationJob = launch{...}`
  - `ChatViewModel.kt:944-950` `cancelActiveGeneration()` 调 `generationJob?.cancel()` 和 `llmProvider.cancel()`
  - 但 `loadSession` 切换会话时（`ChatViewModel.kt:833`）**没有调用 `cancelActiveGeneration()`**
- **根因**：切换会话未显式中断当前生成；`unifiedLlmClient` 与 `llmProvider` 是两个独立路径，取消只走了 `llmProvider.cancel()`，`unifiedLlmClient.sendStream` 的 channelFlow 取消依赖外层协程。
- **建议**：`loadSession` 在切换 `_currentSessionId` 前先 `cancelActiveGeneration()`；退出 app（`onCleared`）已有取消（`ChatViewModel.kt:1329-1333`），但建议补 `unifiedLlmClient` 的取消。

#### A-013: `sendMessage` 在主线程同步读取大图字节
- **用户视角描述**：`sendMessage` 不是 suspend，它内部 `imageUris.mapNotNull { ... openInputStream(uri).readBytes() ... }`（`ChatViewModel.kt:260-266`）跑在调用线程上（UI 事件回调）。读大图会阻塞主线程，造成掉帧甚至 ANR。
- **代码位置**：`ChatViewModel.kt:247-291`（`sendMessage` 非 suspend，图片读取在 `launch` 之外）
- **根因**：IO 操作未切到 `Dispatchers.IO`。
- **建议**：把图片读取移进 `viewModelScope.launch(Dispatchers.IO)` 内部。

---

### 🟢 轻微问题（代码质量、可维护性）

#### A-014: 超时 / 重试次数 / 窗口大小等大量硬编码
- **用户视角描述**：用户想调「流式超时 5 分钟」或「重试 5 次」，发现无处可调。
- **代码位置**：
  - 各 Protocol 的 `requestTimeoutMillis = 120_000` / `connectTimeoutMillis = 30_000`（`OpenAIProtocol.kt:32-35` 及同构）
  - 流式空闲超时 `120000L`（`OpenAIProtocol.kt:82`，虽然支持 `request.streamTimeout`，但默认值散落）
  - `loop_limit` 默认 50（`ChatViewModel.kt:304`）
  - `activeContextWindow` 默认 10/15（`ChatModels.kt:79`、`ChatViewModel.kt:1238`）
  - `autoSummaryThreshold` 默认 0.8（`ChatModels.kt:78`）
  - Anthropic `max_tokens` 默认 4096（`AnthropicProtocol.kt:194`）、thinking `budget_tokens` 写死 10000（`AnthropicProtocol.kt:278`）
- **建议**：统一收到 `InferenceParams` 或远程配置；至少把 magic number 提为常量。

#### A-015: `cleanSearchQuery` 的关键词表硬编码且过于激进
- **用户视角描述**：用户搜「about 关于」「or 或者」这类词，或搜一个正好等于某个语气词的内容（如「吗」「啊」），`cleanSearchQuery` 会把 query 剥成空（虽然第 7 步有兜底回退原始 query，但中间逻辑复杂、维护成本高）。
- **代码位置**：`ContextBuilder.kt:100-221`（200 多行硬编码中英文前缀/后缀/语气词表）
- **建议**：用更稳健的方式（如直接用原始 query 的前 N 字符），或把关键词表外置。

#### A-016: `TokenIndicator` 与实际请求的 token 计算不一致
- **用户视角描述**：UI 上 token 进度条显示的「已用 token」与真实发给 API 的 token 偏差较大（因为 `updateTokenIndicator` 用 `length/4` 估算，且没有计入 tools schema、图片 base64、RAG 注入的实际长度），用户看到的预算和实际行为不符。
- **代码位置**：`ChatViewModel.kt:1449-1509` `updateTokenIndicator`
- **建议**：以 API 返回的 `usage.input` 为准回填，估算仅作占位。

#### A-017: `retryLastMessage` 与 `regenerateLastMessage` 完全等价，命名混乱
- **代码位置**：`ChatViewModel.kt:989-991` `regenerateLastMessage()` 直接调 `retryLastMessage()`
- **建议**：合并或明确语义差异（如 regenerate 保留图片）。

#### A-018: `StreamParser` / `StreamBufferManager` / `DsmlStreamParser` 三套解析器职责重叠
- **代码位置**：`parser/StreamParser.kt`（500 行）、`StreamBufferManager.kt`、`DsmlStreamParser.kt`、加上 `ChatViewModel` 内联的 `extractToolCallsFromText` —— 多套互相重叠的「从文本里抠工具调用 / thinking 块」逻辑。
- **建议**：收敛为一套有状态的流式解析器，避免规则散落、互相矛盾。

#### A-019: `VertexAIProtocol` 工具调用 id 在流式与同步中都用时间戳生成，可能重复
- **代码位置**：`VertexAIProtocol.kt:622`、`719` `vcall_${currentTimeMillis}_${random}` —— 流式中每个 chunk 都生成新 id，而 ViewModel 按 id 累积 tool call，可能导致同一个工具调用被拆成多个。
- **建议**：流式中应按 index 累积、只在完成时分配稳定 id。

---

### ✅ 设计合理的部分

为避免片面，下面这些做得不错：

1. **协议抽象到位**：`ProtocolFactory` + `LlmProtocol` 接口 + `StreamChunk` sealed class，把 6+ 个 provider 收敛为统一形状，新增 provider 只需实现一个类。`ProtocolParamAdapter` 把采样/惩罚参数的 provider 差异集中处理。
2. **错误归一化做得好**：`ErrorNormalizer` 把异常分成 NETWORK/AUTH/RATE_LIMIT/QUOTA/TIMEOUT/INVALID/SERVER 7 类，附带友好中文文案和 `retryAfter` 提取——**归一化质量很高**，问题只是它的输出没被 UI 用上（A-001）。
3. **工具调用增量 fragment 传递正确**：`OpenAIProtocol`/`GenericOpenAICompatProtocol`/`AnthropicProtocol` 都正确地把 SSE 增量片段发给 ViewModel、在 ViewModel 累积，避免了过去「双重累积导致参数膨胀」的 bug（代码注释也记录了这段修复历史）。
4. **HTML 响应检测**：各 Protocol 都做了 contentType 和行级 `<` 前缀检测，能有效拦截「baseUrl 配错返回 Nginx 错误页」的场景，给出明确提示。
5. **SSE 假死防护**：`OpenAIProtocol.kt:333-336` 无条件发送 `TextDelta`（即使内容为空），避免 UI 在 ThinkingDetector 扣留内容时陷入「长时间静止→全量弹出」的假死。
6. **结构化并发的取消语义正确**：各 Protocol 都显式 `catch (CancellationException) { throw e }` 透传取消异常，没有吞掉导致 `withTimeoutOrNull` 失效——这是常见坑，这里处理对了。
7. **RAG 阶段指示器的「不批量假完成」修复**（`ChatViewModel.kt:411-421`）逻辑清晰，只把真正 ACTIVE 过的阶段标 DONE。
8. **Provider 基础设施**：`VertexAIProtocol` 的 JWT/OAuth token 缓存（带 5 分钟提前量刷新）、`OkHttp` 引擎、`Accept-Encoding: identity` 禁压缩防攒块——这些工程细节到位。
9. **`ContextBuilder.buildSystemPrompt` 的拼装顺序合理**：time → tools → task → agent → session → RAG → KG → web → summary，重要信息在前。
10. **`UnifiedLlmClient` 不用 `awaitClose{}`** 并在注释里说明了原因（`UnifiedLlmClient.kt:104-106`）——避免了 channelFlow 永不完成导致 `isGenerating` 卡死的坑。

---

## 遗留疑问（审计中无法确认的）

1. **`unifiedLlmClient` 与 `llmProvider` 双路径并存的取数策略**：`ChatViewModel.kt:477` 优先用 `unifiedLlmClient`，否则回退 `llmProvider.sendPrompt`。但前者走 `ProtocolFactory.create`（每次新建 protocol）、后者用注入的 `LlmProvider`（单例）。两者用同一个 `providerConfig` 吗？`buildUnifiedLlmClient`（`NexaraApplication.kt:585`）与 `buildProviderFromPrefs`（`602`）读的是同一份 `getSavedProviderConfig()`，理论上一致，但**运行时如果用户在设置里改了 provider，`_unifiedLlmClient` 会被置空重建（`NexaraApplication.kt:574`），而 `_llmProvider` 是 StateFlow 也会更新**——这两个的时序是否总是同步？建议运行时验证。

2. **`activeChannel?.cancel()` 是否真能立即中断 HTTP 流**：`OkHttp` 的 `ByteReadChannel.cancel()` 取消的是读取端，底层 socket 的关闭时序需运行时抓包确认（用户弱网 + 主动停止时，流量是否真的停了）。

3. **Fallback 解析器对真实国产模型的命中率**：`extractToolCallsFromText`（`ChatViewModel.kt:1526`）支持 DSML / XML 标签 / JSON 三种格式，但「严格 XML 强标签约束升级」后（`1610-1614` 注释）废弃了 Markdown 代码块提取。对 MiniMax / 百川 / 智谱的实际工具调用格式覆盖率，需用真实模型日志验证。

4. **`tool_call_id` 在 Vertex 路径下的完整性**：Vertex 的 `functionCall` 不携带 id（`VertexAIProtocol.kt:617-625` 用时间戳生成），而工具结果回传时 `ProtocolMessage.toolCallId` 需要匹配。Vertex 多工具并发调用时 id 是否稳定，需运行时验证多工具场景。

5. **`MemoryManagerRagAdapter` / `KgProvider` 的实际异常行为**：`ContextBuilder.performRagRetrieval` 用 try-catch 兜底返回空（`ContextBuilder.kt:262-265`），但 RAG 检索超时 / 向量库损坏时，用户只会看到「没有检索结果」而不知是故障——建议补一个「检索失败」的软提示（区别于「没有相关结果」）。

---

*（报告完。所有问题均含精确 文件:行号，可按编号跟踪修复。）*
