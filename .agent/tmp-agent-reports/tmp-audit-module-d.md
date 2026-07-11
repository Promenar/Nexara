# 模块 D 审计报告：配置系统载入与生效

**审计范围**：Nexara Android 项目"用户在设置页配置的各项参数是否真正在业务链路中载入并发挥作用"
**审计方式**：只读代码审计（未修改任何文件）
**审计日期**：2026-07-05
**审计状态**：完成

---

## 一、执行摘要（TL;DR）

本次审计的核心结论是：**配置系统的"存"与"读"两层基本健全，但"用"这一层存在大量断点**。从用户视角看，多个 UI 上明确可调节、甚至带有详细描述的配置项，最终要么完全不生效、要么被错误地用其它来源覆盖。整体可分为三类问题：

1. **死配置（UI 能改、代码完全不读）**：`SessionSettingsScreen`（旧版会话设置页）的温度 / topP / maxTokens 三个滑块只更新局部 Compose state，**从不调用任何持久化方法**；会话级 `activeSkillIds` / `activeMcpServerIds` 有完整的 CRUD 与持久化，但工具列表构建时只读全局 `enabled_skills`，会话级永远不生效；全局默认推理参数（`default_model` / `default_temperature` / `default_top_p` / `default_max_tokens`）被 `AgentConfigResolver` 读取但全仓库**从不写入**，永远只能取到硬编码默认值。

2. **会话 vs 全局优先级倒置**：`ChatViewModel.generateMessage` 中 `agentConfig.modelId`、`agentConfig.temperature/topP/maxTokens` 都被当作"**回退（fallback）**"使用——只有当 `session.modelId` / `session.inferenceParams` 为 `null` 时才采用。实际上一旦用户在会话里设过任何参数，Agent 配置就彻底失效；而 Agent 自身的 `ragConfig` / `retrievalConfig` / `skills` 在会话链路里**从未被读取**。

3. **额外 Provider（主 + 额外）配置近乎摆设**：`UnifiedLlmClient` 与 `LlmProvider` 都在 `NexaraApplication` 启动时用**主提供商**配置一次性构建，会话层无法按模型选择实际提供商。用户额外配置的 Provider 的 baseUrl/apiKey/protocolType 永远不会进入聊天请求路径——即便用户在会话里选了一个属于额外提供商的模型，请求仍会发到主提供商的 endpoint，几乎必然导致 404/模型不存在。

下面按审计维度逐一展开。

---

## 二、配置项生效审计表

> 说明：✅ = 链条完整；⚠️ = 生效但存在覆盖/降级；❌ = 死配置或断链。

| 配置项 | UI 位置 | 存储位置 | 读取位置 | 业务生效位置 | 状态 |
|---|---|---|---|---|---|
| **主 Provider baseUrl/apiKey/protocolType/model** | `ProviderFormScreen` | SP `nexara_provider`: `base_url`/`api_key`/`protocol_id`/`model` | `ProviderManager.getMainProviderConfig` (`ProviderManager.kt:89`) | `NexaraApplication.buildUnifiedLlmClient:585` / `buildProviderFromPrefs:602` | ✅ |
| **额外 Provider（主+额外）** | `ProviderFormScreen`（"新增额外提供商"分支） | SP `nexara_settings`: `extra_provider_N_*` | `ProviderManager.getProviderConfig:137` / `loadProviders:185` | **聊天请求路径完全未读取**（见 §三.3） | ❌ |
| **session.modelId（会话模型）** | `SessionSettingsSheet.ModelPanel:248` | DB sessions 表 | `ChatViewModel.generateMessage:442` | `PromptRequest.model:453` / `UnifiedLlmClient.sendStream:46` | ⚠️ 模型 ID 生效，但 baseUrl/apiKey 仍锁定主 Provider |
| **session.temperature（会话温度）— 旧页 `SessionSettingsScreen`** | `SessionSettingsScreen.kt:342` InferenceSlider | **不存储**（仅 `mutableStateOf`） | 无 | 无 | ❌ 死配置 |
| **session.temperature（会话温度）— 新页 `SessionSettingsSheet.ParamsPanel`** | `SessionSettingsSheet.kt:449` (通过 thinkingLevel 预设) | DB sessions.inferenceParams | `ChatViewModel.generateMessage:445` | `PromptRequest.temperature:454` | ⚠️ 仅 4 档预设生效，自由滑动滑块未单独绑定 |
| **session.topP** | 同上两处 | 同上两处 | 同上 | `PromptRequest.topP:455` | 同上（旧页 ❌，新页 ⚠️） |
| **session.maxTokens** | `SessionSettingsScreen.kt:346`（旧页）/ `SessionSettingsSheet.kt:583`（新页） | 旧页❌ / 新页 DB | 旧页无 / 新页 `ChatViewModel:445` | `PromptRequest.maxTokens:456` | 旧页 ❌，新页 ✅ |
| **session.frequencyPenalty** | `SessionSettingsSheet.kt:704` | DB sessions.inferenceParams | `ChatViewModel:457` | `PromptRequest.frequencyPenalty:457` | ⚠️ 走 `llmProvider.sendPrompt` 时生效；走 `unifiedLlmClient` 时**丢失**（`StreamTextParams` 无此字段） |
| **session.presencePenalty** | `SessionSettingsSheet.kt:677` | 同上 | `ChatViewModel:458` | `PromptRequest.presencePenalty:458` | ⚠️ 同上 |
| **session.topK** | `SessionSettingsSheet.kt:620` | 同上 | `ChatViewModel:459` | `PromptRequest.topK:459` | ⚠️ 同上 |
| **session.repetitionPenalty** | `SessionSettingsSheet.kt:649` | 同上 | `ChatViewModel:460` | `PromptRequest.repetitionPenalty:460` | ⚠️ 同上 |
| **session.streamTimeout** | `SessionSettingsSheet.kt:529` | 同上 | `ChatViewModel:465` | `PromptRequest.streamTimeout:465` | ⚠️ 走 `unifiedLlmClient` 时同样丢失 |
| **session.activeContextWindow** | `SessionSettingsSheet.kt:879` | 同上 | `ChatViewModel.buildProtocolMessages:1238` / `updateTokenIndicator:1479` | ✅ 实际控制滑动窗口 |
| **session.autoSummaryThreshold** | `SessionSettingsSheet.kt:851` | 同上 | `ChatViewModel:781` | 控制自动摘要触发 | ✅ |
| **session.customPrompt** | `SessionSettingsScreen.kt:103` / `UnifiedPromptEditor` | DB sessions.customPrompt | `ContextBuilder.buildSystemPrompt:330` | 拼入 system prompt | ✅ |
| **session.fontSize** | `SessionSettingsSheet.SettingsPanel:1007` | DB sessions.options | ChatScreen 渲染层 | ✅ |
| **session.options.enableTimeInjection** | `SessionSettingsSheet.ToolsPanel:752` | DB sessions.options | `ContextBuilder.buildSystemPrompt:280` | ✅ |
| **session.options.toolsEnabled** | `SessionSettingsSheet.ToolsPanel:771` | DB | `ChatViewModel.buildToolList:1208` / `ContextBuilder:291` | ✅ |
| **session.options.economyMode** | `SessionSettingsSheet.ToolsPanel:758` | DB | `ContextBuilder:314` | ✅（仅影响任务上下文精简） |
| **session.options.webSearch** | （UI 仅在 `toggleTool:1153` 暴露，无独立开关） | DB | `ChatViewModel:462,485,488` / `ContextBuilder:60` | ⚠️ 走 unified 路径时仅作为 `enableWebSearch` 透传 |
| **session.options.enableGeminiSearch** | `SessionSettingsSheet.ToolsPanel:764` | DB | `ChatViewModel:463` | ❌ 走 unified 路径时**完全丢失**（见 §三.5） |
| **session.ragOptions.enableMemory/enableDocs/enableRerank/enableKnowledgeGraph/isGlobal** | `SessionSettingsScreen` + `SessionSettingsSheet.SettingsPanel` | DB sessions.ragOptions | `ChatViewModel.generateMessage:340-358` / `ContextBuilder:242` | ✅ |
| **session.activeSkillIds** | （无 UI 入口，仅有 toggleSkill API） | DB | **业务链路从未读取**（`buildToolList:1214` 只读全局 `enabled_skills`） | ❌ 死配置 |
| **session.activeMcpServerIds** | （无 UI 入口，仅有 toggleMcpServer API） | DB | 同上，从未读取 | ❌ 死配置 |
| **Agent.systemPrompt** | `AgentEditScreen` | DB agents 表 | `AgentConfigResolver.resolve:23` → `ChatViewModel:391` → `ContextBuilder:323` | ✅ |
| **Agent.modelId** | `AgentEditScreen` | DB | `AgentConfigResolver:24` → `ChatViewModel:443` | ⚠️ 仅作 fallback，会话设过模型后被覆盖 |
| **Agent.temperature/topP** | `AgentEditScreen` | DB | `AgentConfigResolver:25-26` → `ChatViewModel:446-447` | ⚠️ 同上 fallback |
| **Agent.maxTokens** | （`AgentEditViewModel:208` 硬编码 4096，UI 无入口） | DB（始终=4096） | `AgentConfigResolver:27` → `ChatViewModel:448` | ⚠️ 硬编码 + fallback |
| **Agent.ragConfig / Agent.retrievalConfig** | `AgentRagConfigScreen` | DB | `AgentConfigResolver:28-29` 解析后**ChatViewModel 从不读取** | ❌ 死配置（详见 §三.4） |
| **Agent.skills** | （UI 无入口，仅 Agent domain model 字段） | DB | 从不读取 | ❌ 死配置 |
| **全局默认 `default_model/default_temperature/default_top_p/default_max_tokens`** | （UI 无入口） | **从不写入**（全仓库无 put 调用） | `AgentConfigResolver:24-27` | ❌ 永远只有硬编码默认值 |
| **全局 loop_limit（Agent Loop 上限）** | `SkillsScreen.kt:152` | SP `nexara_settings`: `loop_limit` | `ChatViewModel.generateMessage:304` | ✅ |
| **preset_summary_model（摘要模型）** | `UserSettingsHomeScreen` ModelPicker | SP `nexara_settings` | `ChatViewModel:793,1045,1084` | ✅ |
| **preset_image_model（图像模型）** | `UserSettingsHomeScreen` | SP | `ImageGenerationSkill` | ✅（未在本次审计深查） |
| **preset_embedding_model** | `UserSettingsHomeScreen` | SP | `NexaraApplication.buildEmbeddingClient:318` | ✅ |
| **preset_rerank_model** | `UserSettingsHomeScreen` | SP | `NexaraApplication.buildRerankClient:373` + `SessionSettingsSheet:814` 控制重排开关可用性 | ✅ |
| **全局 enabled_skills（技能开关）** | `SkillsScreen` | SP `nexara_settings`: `enabled_skills` | `ChatViewModel.buildToolList:1214` | ✅ |
| **user_name / user_avatar** | `UserSettingsHomeScreen` | SP `nexara_settings` | UI 显示 | ✅（仅展示） |
| **language / theme_mode** | `UserSettingsHomeScreen` | SP | `LocaleHelper` / Theme | ✅ |
| **haptic_enabled** | （UI 已移除，代码保留） | SP | `NexaraApplication.hapticEnabled:287` | ⚠️ 用户无法再改 |

---

## 三、按问题类别的深度分析

### 3.1 死配置一：旧版会话设置页的推理参数滑块（P0）

**文件**：`app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsScreen.kt:342-346`

```kotlin
InferenceSlider(..., temperature, ...) { temperature = it }   // 仅 setState
InferenceSlider(..., topP, ...)       { topP = it }            // 仅 setState
InferenceSliderInt(..., maxTokens, ...) { maxTokens = it }     // 仅 setState
```

**问题**：这三个滑块的 `onChange` 回调只更新了 `mutableStateOf` 局部变量，**未调用 `chatViewModel.updateInferenceParams(...)`**，也未写 DB。用户拖动滑块时 UI 数值会变，但一旦离开页面（`onNavigateBack`）配置立即丢失；正在进行的对话也读不到这个值（因为 `generateMessage` 读的是 `sessionForCtx.inferenceParams`，而非 UI 局部变量）。

**用户视角影响**："我把温度拖到 0.1 想要确定性回答，但下一次发消息时模型依然用 0.7 应答，并且我重新打开设置页时数值又变回 0.7。"——纯 UI 摆设。

**对比**：同一参数在新版 `SessionSettingsSheet.kt`（底部弹窗 `ParamsPanel`）里是正确持久化的（`chatViewModel.updateInferenceParams(params.copy(...))`，如 561、588、624 行）。问题在于旧页 `SessionSettingsScreen` 仍然存在于导航图里、用户可能从不同入口进入。

**修复建议**：要么删除旧页只保留 Sheet，要么补上 `updateInferenceParams` 调用。

---

### 3.2 死配置二：会话级 activeSkillIds / activeMcpServerIds（P0）

**数据流**：
- 持久化完整：`SessionManager.toggleSkill:130` / `toggleMcpServer:119` 写入 DB；`SessionDao.updateActiveSkillIds` 有专门 query；`SessionEntity.activeSkillIds` 有字段。
- **业务读取为零**：`ChatViewModel.buildToolList:1207-1226` 只读取全局 SP `enabled_skills`：

```kotlin
val enabledSkills = prefs.getStringSet("enabled_skills", null)?.toSet()
if (enabledSkills.isNullOrEmpty()) return emptyList()
val allowedIds = enabledSkills.toList()
return skillRegistry?.getAllTools(allowedIds) ?: emptyList()
```

`session.activeSkillIds` 与 `session.activeMcpServerIds` 在工具构建过程中**一次都没被读取**。

**用户视角影响**：UI 上 `SessionManager.toggleSkill/toggleMcpServer` 这些 API 表面可调，但即便用户在 A 会话关闭某工具、B 会话开启，实际请求中工具集合永远等于"全局开关 ∩ 全局 enabled_skills"。会话级工具裁剪完全无效。

**修复建议**：`buildToolList` 应取 `(全局 enabled_skills) ∩ (session.activeSkillIds.isEmpty ? 全部 : session.activeSkillIds)`。

---

### 3.3 死配置三：额外 Provider 在聊天链路永不生效（P0，影响多 Provider 用户）

**根因**：聊天请求只走两条路径，二者都用 `NexaraApplication` 启动时构建的、绑定主 Provider 的客户端：

```kotlin
// NexaraApplication.kt:241-243
private var _unifiedLlmClient: UnifiedLlmClient? = null
val unifiedLlmClient get() = _unifiedLlmClient ?: buildUnifiedLlmClient().also { ... }

// buildUnifiedLlmClient:585-600 只用 getSavedProviderConfig()（主 Provider）
```

`ChatViewModel` 工厂注入 `app.llmProvider` 与 `app.unifiedLlmClient`（`ChatViewModel.kt:1431-1432`），二者**只在 `updateProvider`（编辑主 Provider）时被重建**。新增/编辑额外 Provider 走 `viewModel.addProvider/updateExtraProvider`（`NavGraph.kt:370,386`），**完全不触发客户端重建**。

`UnifiedLlmClient.sendStream`（`UnifiedLlmClient.kt:46-54`）会用 `providerConfig.protocolType/baseUrl/apiKey` 创建协议实例；`StreamTextParams.model` 会传给请求，但 **baseUrl/apiKey/protocolType 都是构建时锁定的主 Provider 值**。

**用户视角影响**：
1. 用户配置了"主 Provider = OpenAI"+"额外 Provider = 智谱 GLM"，并在会话里选择了一个 GLM 模型。
2. 请求会带着 `model="glm-4"` 发到 `https://api.openai.com/v1/chat/completions`，用 OpenAI 的 apiKey。
3. OpenAI 返回 "model not found" 错误。用户以为 GLM 配置坏了，反复重试无果。

`ProviderManager.getProviderConfigByModelId`（`ProviderManager.kt:111`）和 `ProviderRepository.fetchModels`（`ProviderRepository.kt:46`）都正确实现了按模型反查 Provider，但**这条数据从未被聊天链路使用**——只在 Embedding/Rerank 客户端构建时被复用（`NexaraApplication.buildEmbeddingClient:320`）。

**修复建议**：`UnifiedLlmClient` 需要支持按请求的 `model` 动态选择 Provider，或者在 `ChatViewModel.generateMessage` 中根据 `effectiveModel` 反查 ProviderConfig 并构建一次性 client。

---

### 3.4 死配置四：Agent 的 ragConfig / retrievalConfig / skills（P0）

**文件**：`AgentConfigResolver.kt:21-31` + `ChatViewModel.kt:337-449`

`AgentConfigResolver.resolve(agent)` 解析了 7 个字段，包括 `ragConfig` 和 `retrievalConfig`：

```kotlin
data class ResolvedConfig(
    val systemPrompt, val modelId, val temperature, val topP, val maxTokens,
    val ragConfig: AgentRagConfig?,          // ← 解析了
    val retrievalConfig: AgentRetrievalConfig?  // ← 解析了
)
```

但 `ChatViewModel.generateMessage` 在拿到 `agentConfig` 后，**只使用了 systemPrompt / modelId / temperature / topP / maxTokens**：

```kotlin
val agentConfig = configResolver.resolve(agent)        // line 337
// ...
agentSystemPrompt = agentConfig.systemPrompt,           // line 391 ✓
val effectiveModel = sessionForCtx.modelId ?: agentConfig.modelId   // line 442-443 ⚠️fallback
val effectiveParams = sessionForCtx.inferenceParams ?: InferenceParams(
    temperature = agentConfig.temperature, topP = agentConfig.topP, maxTokens = agentConfig.maxTokens  // line 445-449 ⚠️fallback
)
```

`agentConfig.ragConfig` / `retrievalConfig` **从未被引用**。RAG 检索实际用的是 `session.ragOptions`（会话级）+ 全局 `RagConfigPersistence.loadFullConfig()`（`MemoryManager` 构建时读取，`NexaraApplication.kt:476`）。

`Agent.skills` 字段同理：`AgentEditViewModel.saveAgent:213` 保留了 `skills`，但 `ChatViewModel.buildToolList` 完全不读 Agent，只读全局 SP。

**用户视角影响**：用户在"Agent 高级检索设置"（`AgentAdvancedRetrievalScreen` / `AgentRagConfigScreen`）里为某个 Agent 单独配置了 `memoryLimit=10`、`docThreshold=0.6`、自定义 chunk size 等，期望这个 Agent 用更激进的检索策略。但实际检索参数永远来自全局 `rag_settings` SP，Agent 级配置在每次对话中毫无作用。"为编程 Agent 配大窗口、为写作 Agent 配精检索"的产品愿景无法实现。

**修复建议**：`ContextBuilder.performRagRetrieval` 与 `MemoryManager` 调用需要接收并应用 `agentConfig.retrievalConfig`（覆盖全局），实现真正的 Agent 级 RAG 个性化。

---

### 3.5 死配置五：enableGeminiSearch 在 UnifiedLlmClient 路径丢失（P1）

**问题**：`ChatViewModel.generateMessage` 构造 `PromptRequest` 时正确设置了 `enableGeminiSearch = sessionForCtx.options.enableGeminiSearch`（line 463）。但当走 `unifiedLlmClient` 分支（line 477-490）时，构造的是 `StreamTextParams` + `StreamConfig`，二者**都没有 enableGeminiSearch 字段**：

```kotlin
// StreamTextParams (LlmMiddleware.kt:10-24) — 无 enableGeminiSearch
// StreamConfig (UnifiedLlmClient.kt:26-36) — 只有 enableWebSearch

// UnifiedLlmClient.sendStream:63-72 构造 PromptRequest：
val request = PromptRequest(
    messages, model, temperature, topP, maxTokens, tools, stream=true,
    webSearch = finalParams.enableWebSearch || config.enableWebSearch
    // ← enableGeminiSearch 完全没传，默认 null
)
```

**后果**：`GenericOpenAICompatProtocol.buildRequestBody:274-275` 判断：
```kotlin
val shouldAddGeminiSearch = isGemini && request.enableGeminiSearch != false
```
`null != false` 为 true → **只要模型名含 "gemini"，就强制注入 `googleSearchRetrieval` 工具**，即使用户在会话设置里关掉了"Gemini 联网 Grounding"开关。

**用户视角影响**：用户关掉 Gemini Grounding 后，请求里仍然带 `googleSearchRetrieval`，每次回答都触发联网搜索，延迟增加、可能产生额外费用，且用户完全无法关闭。

**修复建议**：`StreamTextParams` 增加 `enableGeminiSearch` 字段并在 `UnifiedLlmClient.sendStream` 透传到 `PromptRequest`。

---

### 3.6 配置覆盖优先级问题：会话级硬覆盖 Agent 级（P1）

**问题位置**：`ChatViewModel.kt:442-449`

```kotlin
val effectiveModel = sessionForCtx.modelId ?: agentConfig.modelId
val effectiveParams = sessionForCtx.inferenceParams ?: InferenceParams(
    temperature = agentConfig.temperature,
    topP = agentConfig.topP,
    maxTokens = agentConfig.maxTokens
)
```

**问题**：`session.modelId` 默认值是 `null`，但 `SessionSettingsSheet.ModelPanel` 一旦让用户选过模型，`session.modelId` 就非空——之后**Agent 的 modelId 永久失效**。同理 `inferenceParams` 一旦在会话里调整过（哪怕只是点了一下 thinking level 预设），Agent 的 temperature/topP/maxTokens 全部失效。

而且 `SessionManager.updateSessionModel:77-83` 还有个副作用：选模型时会强制 `toolsEnabled = (modelId != null)`，间接影响工具开关。

**用户视角影响**：用户为"编程专家"Agent 设置了 `temperature=0.2`（追求代码确定性）。在某个会话里他不小心点了一下"创意"预设（temperature=1.0），从此这个会话——以及任何后续在该会话里的对话——都以 1.0 运行，Agent 配置被彻底压制，无法恢复（除非用户知道去会话设置里手动调回）。

**合理优先级**：通常期望 `会话级 > Agent 级 > 全局默认`，但当前实现是"会话级一旦设过就**永久**压制 Agent 级"，缺少"重置为 Agent 默认"或"清空会话覆盖"的入口。

**修复建议**：UI 提供"恢复 Agent 默认"按钮，调用 `updateModelId(null)` / `updateInferenceParams(null)`；或当 Agent 切换时自动清空会话级覆盖。

---

### 3.7 UnifiedLlmClient 路径丢失多个高级参数（P1）

**问题**：`ChatViewModel.generateMessage` 走 unified 分支时构造的 `StreamTextParams`（`LlmMiddleware.kt:10-24`）只携带 `temperature / topP / maxOutputTokens`，**不携带** `frequencyPenalty / presencePenalty / topK / repetitionPenalty / streamTimeout`。

而 `UnifiedLlmClient.sendStream:63-72` 重建 `PromptRequest` 时这些字段全部默认 `null`：

```kotlin
val request = PromptRequest(
    messages, model,
    temperature = finalParams.temperature,
    topP = finalParams.topP,
    maxTokens = finalParams.maxOutputTokens,
    // frequencyPenalty / presencePenalty / topK / repetitionPenalty / streamTimeout 全部丢失
    tools = ..., stream = true, webSearch = ...
)
```

**用户视角影响**：用户在 `SessionSettingsSheet.ParamsPanel` 的"高级参数"折叠区里精心调了 topK=40、repetition_penalty=1.1 来抑制本地模型重复，结果发现在 unified 路径下这些参数从未发到 API（只有 `llmProvider.sendPrompt` 老路径才生效，但默认走 unified）。Stream timeout 同理丢失，长任务可能因为无法应用用户设置的 300s 超时而提前中断。

**注意**：当前代码 `if (unifiedLlmClient != null)` 优先走 unified 路径（line 477），所以**绝大多数用户的 advanced 参数都是失效的**。

**修复建议**：扩展 `StreamTextParams` 字段或在 `UnifiedLlmClient.sendStream` 直接接收完整 `PromptRequest`。

---

### 3.8 持久化一致性问题

#### 3.8.1 ProtocolType 存读不对称（潜在 bug，P2）

**写**（`ProviderManager.updateMainProvider:73`）：
```kotlin
putString("protocol_id", protocolType::class.simpleName)
```
存的是 sealed class 的 simpleName，如 `Generic_OpenAI_Compat`、`OpenAI_ChatCompletions`。

**读**（`ProviderManager.getMainProviderConfig:91`）：
```kotlin
val protocolType = ProtocolType.fromLegacyName(protocolName)
```
`fromLegacyName`（`LlmProtocol.kt:220-230`）先 `uppercase()` 后匹配旧枚举名 `OPENAI/ANTHROPIC/VERTEX_AI/LOCAL`，匹配不上才走 `entries.first { it::class.simpleName == name }`。

**风险**：`Generic_OpenAI_Compat`.uppercase() = `GENERIC_OPENAI_COMPAT`，不在 legacy 列表，会走 `entries.first`，OK。但 `OpenAI_ChatCompletions`.uppercase() = `OPENAI_CHAT_COMPLETIONS`，也不在 legacy 列表——而 legacy 别名 `OPENAI` 是另一个映射。当前数据流不会存 `OPENAI`（simpleName 是 `OpenAI_ChatCompletions`），所以暂时无 bug，但 legacy 兼容代码（`fromLegacyName` 的上半段）实际是**死代码**，且若未来有人手动改 SP 会踩坑。

#### 3.8.2 maxTokens 类型语义不一致（P2）

- UI `SessionSettingsSheet.ParamsPanel:588` 允许 maxTokens=0 表示"无限"（`if (currentMaxTokens == 0) null`）。
- `InferenceParams.maxTokens: Int?` 默认 `null`。
- `AgentConfigResolver:27` 默认 `4096`。
- `AnthropicProtocol.buildRequestBody:192`：`put("max_tokens", request.maxTokens ?: 4096)`——null 兜底 4096，**用户选"无限"实际被改成 4096**。
- `ProtocolParamAdapter.mapCommonParams:11`：`request.maxTokens?.let { body.put("max_tokens", it) }`——null 时不写，由服务端默认。

**用户视角影响**：用户把 maxTokens 拖到"无限"，期望模型自由发挥。Anthropic 协议下被强制改成 4096；OpenAI 协议下不传字段由服务端决定（行为不一致）。

#### 3.8.3 temperature/topP 在 VertexAI 协议被强制覆盖（P2）

`ProtocolParamAdapter.mapCommonParamsVertexAI:34`：
```kotlin
body.put("temperature", request.temperature ?: 0.7)
```
即使用户传 `temperature=null`（希望服务端默认），VertexAI 协议也会强制写 0.7。其他协议（`mapCommonParams:9`）则是 `request.temperature?.let { ... }`，null 时不写。**协议间行为不一致**。

---

### 3.9 配置变更感知：进行中的会话能否感知新配置？

**结论**：**部分能，部分不能。**

| 配置变更 | 进行中会话感知？ | 原因 |
|---|---|---|
| 主 Provider 编辑 | ✅ | `NexaraApplication.updateProvider:574` 设 `_unifiedLlmClient = null`，下次请求重建。但进行中（已发起）的请求不会切换。 |
| 额外 Provider 增删 | N/A | 聊天链路本来就不读额外 Provider |
| Agent 配置修改 | ✅（下条消息） | `agentRepository.getById(sessionForCtx.agentId)` 在每次 `generateMessage:336` 重新查询 |
| 全局 enabled_skills | ✅（下条消息） | `buildToolList` 每次读 SP |
| preset_summary_model | ✅ | 每次摘要时读 SP |
| 全局 RAG 配置（rag_settings） | ❌ | `MemoryManager` 在 `NexaraApplication` lazy 构建时读取，之后**不感知 SP 变化**，除非 `rebuildMemoryManager` 被调用（仅在 `rebuildEmbeddingClient/rebuildRerankClient` 链路里） |
| Embedding/Rerank baseUrl | ✅ | SP listener (`NexaraApplication:407,419`) 触发重建 |
| Language/Theme | ✅ | `activity.recreate()` |
| haptic | ✅ | 但 UI 已无入口 |

**关键缺陷**：用户在 `GlobalRagConfigScreen` / `RagAdvancedScreen` 改了 `memoryLimit` / `docThreshold` / `chunkSize` 等检索参数后，**`MemoryManager` 不会重建**，下一条消息仍用旧参数。这些 SP 写入没有注册 listener。

---

### 3.10 默认值合理性审计

| 配置项 | 当前默认 | 评价 |
|---|---|---|
| `loop_limit` | 50 | ⚠️ 偏高，无限循环风险（每轮可能消耗 token）；UI 上限 100 标注"unlimited"有歧义 |
| `default_temperature` | 0.7 | ✅ 合理 |
| `default_top_p` | 0.9 | ✅ 合理 |
| `default_max_tokens` | 4096 | ✅ 合理 |
| `activeContextWindow` | 15 (`InferenceParams:79`) | ⚠️ `SessionSettingsSheet` 默认显示但 `InferenceParams` 数据类默认 15；UI slider 范围 5-50 |
| `streamTimeout` | 120s | ✅ 合理 |
| `autoSummaryThreshold` | 0.8 | ✅ 合理 |
| `RagOptions.enableDocs` | true | ⚠️ 注释说"用户导入文档的意图就是检索"，但默认开会导致无向量库的用户每次都触发（失败）检索 |
| `RagOptions.enableMemory` | true | ⚠️ 同上，新用户首次对话即触发 embedding 调用 |
| `RagOptions.enableRerank` | true | ⚠️ 未配置 rerank 模型时 `SessionSettingsSheet:934` 会强制禁用并警告，但 `RagOptions` 数据默认仍为 true，逻辑层需各自判断 |
| `SessionOptions.toolsEnabled` | true | ✅ |
| `SessionOptions.webSearch` | false | ✅ 注释明确"默认关闭被动预注入" |
| `SessionOptions.enableGeminiSearch` | true | ❌ 配合 §3.5 的 bug，导致 Gemini 用户默认强制联网 |
| `Agent.maxTokens`（AgentEditViewModel 硬编码） | 4096 | ❌ 硬编码，UI 无入口，无法配置 |

---

## 四、按审计维度的总结

### 4.1 配置链条完整性
- **完整链条**：主 Provider、preset models、全局 skills、loop_limit、会话 RAG 开关、Agent systemPrompt/model/temp/topP、session.customPrompt/fontSize/activeContextWindow/autoSummaryThreshold、timeInjection/toolsEnabled/economyMode。
- **断链**：见 §3.1–3.5、3.7。最严重的是旧会话设置页推理参数、会话级 skill/MCP、Agent ragConfig/retrievalConfig/skills、额外 Provider、UnifiedLlmClient 路径的高级参数。

### 4.2 死配置清单（UI/API 可改但代码不读）
1. `SessionSettingsScreen` 旧页 temperature/topP/maxTokens 滑块（§3.1）
2. `session.activeSkillIds` / `session.activeMcpServerIds`（§3.2）
3. 额外 Provider 配置在聊天链路（§3.3）
4. `Agent.ragConfig` / `Agent.retrievalConfig` / `Agent.skills`（§3.4）
5. 全局 `default_model/default_temperature/default_top_p/default_max_tokens`（被读不被写，§执行摘要）
6. `haptic_enabled`（UI 已移除入口）

### 4.3 硬编码 vs 配置
- `Agent.maxTokens = 4096`（`AgentEditViewModel:208`）硬编码，UI 无入口。
- `ProtocolParamAdapter.mapCommonParamsVertexAI:34` 硬编码 `temperature ?: 0.7`。
- `AnthropicProtocol.buildRequestBody:192` 硬编码 `max_tokens ?: 4096`。
- `ProtocolParamAdapter.buildGenerateConfig:48-52` Local 协议默认 `temperature=0.7, topP=0.9, topK=40, repeatPenalty=1.0`。
- `InferenceParams` 默认 `activeContextWindow=15`、`autoSummaryThreshold=0.8`、`streamTimeout=120`。

### 4.4 配置覆盖优先级
- **当前**：会话级 > Agent 级（fallback）> 全局默认（永远硬编码）。
- **问题**：会话级一旦设过就**永久压制** Agent 级，无"重置"入口（§3.6）。
- **Agent ragConfig/retrievalConfig/skills 完全不参与优先级**（§3.4）。

### 4.5 持久化一致性
- SP key 读写基本一致（§3.8.1 有潜在 ProtocolType 隐患但当前数据流不触发）。
- 类型转换：maxTokens "0=无限" 语义在不同协议下行为不一致（§3.8.2）。
- VertexAI 与其他协议在 temperature=null 时行为不一致（§3.8.3）。
- `default_*` 全局默认 SP key 只读不写（§执行摘要）。

### 4.6 配置变更感知
- 主 Provider、Agent、skills、preset models、Embedding/Rerank 客户端：✅ 可感知。
- 全局 RAG 检索参数（memoryLimit 等）：❌ 不感知（§3.9）。
- 进行中已发出的请求：不可切换（符合预期）。

### 4.7 默认值合理性
- 大部分合理。
- 问题项：`RagOptions.enableMemory/enableDocs/enableRerank` 默认 true 对新用户不友好；`enableGeminiSearch` 默认 true 叠加 §3.5 bug；`loop_limit` 上限 100 标注"unlimited"歧义；`Agent.maxTokens` 硬编码。

---

## 五、修复优先级建议

| 优先级 | 问题 | 影响 |
|---|---|---|
| **P0** | §3.3 额外 Provider 在聊天链路不生效 | 多 Provider 用户根本无法使用非主 Provider 模型 |
| **P0** | §3.4 Agent ragConfig/retrievalConfig/skills 不生效 | Agent 个性化检索/工具完全失效，违背产品定位 |
| **P0** | §3.1 旧会话设置页推理参数滑块死配置 | 用户调整不生效（已部分被 Sheet 替代，但旧页仍在导航图） |
| **P0** | §3.2 会话级 activeSkillIds/activeMcpServerIds 死配置 | 会话级工具裁剪无效 |
| **P1** | §3.5 enableGeminiSearch 在 unified 路径丢失 | Gemini 用户无法关闭联网，强制产生费用/延迟 |
| **P1** | §3.7 UnifiedLlmClient 路径丢失高级参数 | topK/penalty/streamTimeout 等对多数用户失效 |
| **P1** | §3.6 会话级硬覆盖 Agent 级无重置入口 | Agent 配置被意外压制后无法恢复 |
| **P1** | §3.9 全局 RAG 参数变更 MemoryManager 不重建 | 用户调检索参数不生效 |
| **P2** | §3.8.2 maxTokens "0=无限" 协议间不一致 | 用户期望与实际不符 |
| **P2** | §3.8.3 VertexAI temperature=null 强制 0.7 | 协议行为不一致 |
| **P2** | §3.8.1 ProtocolType legacy 兼容死代码 | 维护风险 |
| **P2** | Agent.maxTokens 硬编码 4096 | 无法配置 |

---

## 六、审计方法说明

本次审计为纯静态代码分析，覆盖以下关键文件的数据流追踪：
- 持久化层：`ProviderManager.kt`、`RagConfigPersistence.kt`、`SessionDao`、`SessionRepository.kt`、`SessionManager.kt`
- 配置解析：`AgentConfigResolver.kt`
- 业务链路：`ChatViewModel.kt`（generateMessage / buildToolList / buildProtocolMessages）、`ContextBuilder.kt`、`UnifiedLlmClient.kt`、`SummaryManager.kt`
- 协议层：`OpenAIProtocol.kt`、`AnthropicProtocol.kt`、`GenericOpenAICompatProtocol.kt`、`ProtocolParamAdapter.kt`、`ProtocolFactory.kt`、`LlmProvider.kt`
- UI 层：`SessionSettingsScreen.kt`、`SessionSettingsSheet.kt`、`ProviderFormScreen.kt`、`SettingsViewModel.kt`、`UserSettingsHomeScreen.kt`、`AgentEditViewModel.kt`、`SkillsScreen.kt`
- 装配层：`NexaraApplication.kt`、`NavGraph.kt`

对每个配置项追踪了 UI 输入 → StateFlow/State → 持久化（SP/DB）→ 读取 → 业务使用 五个环节，标注断点位置与行号。
