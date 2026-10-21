# 推理强度与参数方案实施计划（2026-10-21）

源码路径相对 `native-ui/app/src/main/java/com/promenar/nexara/`。

## 目标

1. 推理强度从会话设置的参数页移到聊天输入区，紧挨模型选择器；控件形态由当前模型的能力决定（不显示 / 只能开关 / 多档），选择按会话保存。
2. 推理强度真实发送到各协议：每个模型只出现它支持的档位，字段名与取值按厂商文档转换。
3. “模型默认”成为显式基线：未自定义的参数一律不发送，由厂商按模型默认值处理。参数页逐项标明“默认 / 已自定义”，可一键恢复默认。
4. 参数页只显示当前模型和协议真正生效的参数；会报错或被忽略的参数不显示，并在请求层统一剔除。
5. 四个固定档位改为用户保存的“参数方案”，方案按模型保存；助手也可以选用方案或自定义参数，作为其会话的默认值。
6. 旧数据中由档位写入的采样参数和助手的 0.7 / 0.9 / 4096 默认值，一次性恢复为模型默认。

## 非目标

- 聚合服务（OpenRouter 等）自有的推理字段；通用 OpenAI 兼容服务只在能识别模型系列时提供推理控件。
- 本地推理（`ProtocolType.Local`）的采样默认值，保持现状。
- 按思考 token 预算的数值滑杆；只提供档位。Gemini 2.5 与 Anthropic 旧模型由档位映射到预算。
- 跨模型共享的方案；方案只对保存它的模型可见。

## 已验证事实（2026-10-21，基于当前工作树）

### 参数链路

- `InferenceParams` 含 temperature、topP、maxTokens、frequencyPenalty、presencePenalty、topK、repetitionPenalty、`thinkingLevel`、streamTimeout、autoSummaryThreshold、keepRecentTurns、activeContextWindow（`data/model/ChatModels.kt:103-119`）。`thinkingLevel` 没有任何读写方。会话参数存在 `sessions.inference_params` JSON 列。
- 领域 `Agent` 默认 temperature 0.7、topP 0.9、maxTokens 4096（`domain/model/Agent.kt`）；`AgentEntity` 三列默认 null，`AgentMapper` 直接映射；`AgentEditViewModel.buildAgent` 写死 maxTokens 4096；助手没有采样参数界面。
- `AgentConfigResolver.resolve` 在助手值为空时回退到偏好 `default_temperature` 0.7、`default_top_p` 0.9、`default_max_tokens` 4096（`domain/usecase/AgentConfigResolver.kt:27-29`），这三个键从未写入，但在备份白名单中（`data/backup/BackupPreferencePolicy.kt:26`，`data/backup/AndroidTransactionalBackupPreferenceStore.kt:412-415`）。
- `withDefaults` 只让会话覆盖助手的 temperature、topP、maxTokens（`InferenceParamsResolution.kt:32-40`）。结果是每个聊天请求都带 temperature 0.7、top_p 0.9、max_tokens 4096，“模型默认”从未生效；推理模型的思考加正文容易超过 4096 被截断。
- `SessionListViewModel.createSession` 把助手的三个值复制进新会话（`ui/hub/SessionListViewModel.kt:119-125`）；`ChatViewModel.createNewSession` 不复制。
- 参数页 `SessionParamsContent`（`ui/chat/SessionSettingsSheet.kt:335-466`）的四张“思考强度”卡片写入温度 / Top P / Top K 三元组（0.1/0.5/10、0.4/0.8/30、0.7/0.9/50、1.0/1.0/100），选中项由温度反推；页面没有温度控件。`InferencePresets.kt` 未被使用。

### 推理字段

- `PromptRequest.reasoning: Boolean?`（`data/remote/protocol/LlmProtocol.kt:127`）在生产中恒为 null：`StreamTextParams`（`data/remote/middleware/LlmMiddleware.kt:10-32`）没有推理字段，`UnifiedLlmClient.sendStream` 构造请求时也不传。
- Anthropic：`anthropicThinkingConfig` 对 Claude 5 固定发 adaptive，其它模型仅在 reasoning == true 时开启（`data/remote/protocol/AnthropicProtocol.kt:760-778`）；`rejectsSamplingParams` 覆盖 Claude 5 与 4.7-4.9；`DEFAULT_MAX_TOKENS = 4096`（`:714`）。没有 `output_config.effort`。
- Gemini 只在 reasoning == true 时发 `thinkingConfig.includeThoughts`（`data/remote/protocol/GeminiContent.kt:107`）；Vertex 无条件发 temperature，缺省 0.7（`ProtocolParamAdapter.mapCommonParamsVertexAI`）。
- OpenAI Chat / 通用兼容协议通过 `ProtocolParamAdapter` 发采样参数（`OpenAIProtocol.kt:317-319`，`GenericOpenAICompatProtocol.kt:333-335`）；Responses 直接写 temperature / top_p / max_output_tokens（`OpenAIResponsesProtocol.kt:220-222`）。任何协议都不发 `reasoning_effort`。
- `ProtocolFactory` 把 DeepSeek、Moonshot_Kimi、Qwen_DashScope、Zhipu_GLM、Doubao、Baichuan、Mistral、Generic 都路由到 `GenericOpenAICompatProtocol`，`protocolType` 保留厂商身份。
- 模型能力只有布尔 `REASONING`（`data/model/catalog/ModelMetadata.kt`，`data/model/ModelSpecs.kt:13-16`）。

### 持久化与备份

- Room 版本 23，迁移在 `data/local/db/NexaraDatabaseMigrations.kt`。备份 payload 有独立版本 `DATABASE_SCHEMA_VERSION = 5` 与逐级升级函数（`data/backup/RoomBackupDataSource.kt:504-511,1555`），表结构变化要同步备份版本和升级函数。

### 厂商接口（官方文档，2026-10-21 调研）

- 没有统一的字段和档位。全集为 none、minimal、low、medium、high、xhigh、max，各模型取子集。例如 Kimi K3、GLM-5.3 只有 low/high/max；Qwen3.8 只有 low/medium/xhigh；Gemini 4 Argon 没有 minimal；OpenAI gpt-6-astra 不能选 none。
- 字段位置：OpenAI Chat 与多数兼容服务为顶层 `reasoning_effort`；Responses 为 `reasoning.effort`；Anthropic 与其兼容端点为 `output_config.effort` 加 `thinking`；Gemini 3 及以后为 `thinkingConfig.thinkingLevel`，2.5 为 `thinkingBudget`；DeepSeek、GLM、Kimi K2.6 的开关为 `thinking.type`；Qwen 混合模型为 `enable_thinking`。
- 采样参数在推理模型上分三类：会返回 400（Claude 4.7 及以后偏离默认；OpenAI effort 不为 none 时的 temperature/top_p；Grok 推理模型的两个惩罚参数；Kimi 的固定 temperature/top_p/惩罚参数）；不报错但无效（DeepSeek 思考模式、Gemini 3.6 Flash 及以后、MiniMax）；真正生效（Qwen、GLM、SenseNova、Gemini 2.5）。
- 推论：当前每个请求都带 temperature 0.7，在 Kimi 上会被拒绝，在 OpenAI 推理模型上与官方要求冲突。本计划的请求层剔除同时修复这一点。

## 设计

### 1. 模型参数档案 `ModelParameterProfile`

新增 `data/model/params/`，以纯函数 `resolveParameterProfile(protocolType, remoteModelId)` 返回：

- `reasoning`：`Hidden` / `Toggle(defaultOn)` / `Levels(levels, defaultLevel, canDisable)`；档位使用统一枚举 `ReasoningEffort { NONE, MINIMAL, LOW, MEDIUM, HIGH, XHIGH, MAX }`。
- `wire`：推理字段的发送方式（OpenAI 顶层 / Responses 嵌套 / Anthropic effort / Gemini level / Gemini budget / 开关字段），由协议形态决定字段外形，由模型系列决定取值与开关字段。
- `sampling`：每个采样参数的支持状态 `Supported` / `Ignored` / `Rejected`，可随“当前是否在推理”变化。

识别顺序：先按模型 id 识别系列（同一系列在不同协议上字段外形不同），再结合 `protocolType` 决定外形。识别不了的模型：推理控件隐藏，采样参数全部显示（保持现有行为）。档案表以调研表为准，逐模型写单元测试。

### 2. 请求层

- `StreamTextParams` 与 `PromptRequest` 新增 `reasoningEffort: ReasoningEffort?`（null 即模型默认，不发任何推理字段）；保留 `reasoning: Boolean?` 供 Gemini 显示思考内容。
- 各协议按档案写推理字段，并按 `sampling` 剔除 Rejected / Ignored 的参数。剔除对所有调用方生效，包括摘要、标题、查询改写、重排、图谱抽取。
- 存了不支持的档位（例如换了模型）：发送时取最接近的受支持档位；模型不能关闭思考而存的是“关闭”时，按模型默认处理。
- Anthropic 必须带 max_tokens：未自定义时取模型元数据的输出上限，未知时 16384。Vertex 不再缺省发 temperature 0.7。

### 3. 参数解析

逐项按“会话 → 助手 → 模型默认（不发送）”解析。新建会话不再复制助手参数，改为运行时继承，修改助手方案会影响尚未自定义的会话。删除 `AgentConfigResolver` 的偏好回退和 `Agent` 的 0.7/0.9/4096 默认值；`AgentEditViewModel` 不再写死 4096。

### 4. 推理强度控件（输入区）

位于模型选择器右侧，界面稿待用户确认后实现。选择写入会话的 `InferenceParams.reasoningEffort`（替换死字段 `thinkingLevel`）。助手可设默认推理强度，会话未选择时继承。

### 5. 参数页与参数方案

- 顶部为方案行：“模型默认”固定在首位，其后是当前模型已保存的方案，末尾“存为方案”。修改任一参数后方案标记为“已修改”，可覆盖保存或另存。
- 参数逐项显示：未自定义显示“默认”；已自定义显示数值与来源（本会话 / 助手），带恢复按钮。新增温度控件。被档案判定为不支持的参数不显示，底部说明“此模型固定了 N 项参数”。
- 方案存于新表 `parameter_schemes(id, model_key, name, params_json, created_at, updated_at)`；`model_key` 为稳定模型 id。
- 助手编辑页新增“默认参数”区：推理强度默认值，以及方案选择或自定义参数，存入 `agents` 新列 `inference_params`（JSON）。

### 6. 迁移（Room 23 → 24，备份 payload 5 → 6）

- 新建 `parameter_schemes` 表；`agents` 新增 `inference_params`，并把 temperature、top_p、max_tokens 置为 NULL。
- `sessions.inference_params` 在 Kotlin 中逐行改写：清空 temperature、topP、maxTokens、topK、frequencyPenalty、presencePenalty、repetitionPenalty、thinkingLevel；保留 streamTimeout、autoSummaryThreshold、keepRecentTurns、activeContextWindow。不依赖 SQLite JSON1。
- 备份升级函数对旧备份做同样处理。三个从未写入的偏好键保留在备份白名单中，只是不再读取，以便旧备份照常通过偏好校验。

### 7. 后台调用

子代理继承父会话的有效推理强度；摘要、标题、查询改写、重排、图谱抽取不发推理字段（模型默认），其显式温度经请求层剔除后才发送。

## 阶段与验收

1. 档案与请求层：档案表、`ReasoningEffort`、三层透传、各协议推理字段与采样剔除、Anthropic max_tokens 与 Vertex 温度修正。验收：每家厂商代表模型的请求体单元测试（含不支持档位的就近映射、关闭思考、剔除参数）；真机对 DeepSeek 发起真实调用，确认思考开关与档位生效。
2. 参数解析与迁移：解析顺序、新建会话不复制、Room 与备份迁移。验收：迁移测试（23→24 数据改写、保留非采样字段）、备份升级测试。
3. 输入区推理控件（界面稿确认后）。验收：截图测试（隐藏 / 开关 / 多档 / 下拉展开）、ViewModel 会话持久化测试、真机切换与发送。
4. 参数页与方案、助手默认参数。验收：截图测试、方案保存/覆盖/删除与“已修改”判定测试、助手继承测试、真机操作。
5. 全量 `verify.sh`、release 校验与真机安装；同步 CHANGELOG、`docs/ARCHITECTURE.md` 与本计划。

## 实施决策

- 会话选用参数方案或“模型默认”后记录 `InferenceParams.parameterSchemeId`（方案 id 或 `model-default`），此时采样参数完全由会话决定，不再逐项沿用助手；推理强度仍逐项回退。会话里推理取值 `default` 表示显式模型默认，用于助手设有档位、会话要改回模型默认的情况。
- 正在沿用助手值的参数被单独恢复为模型默认时，其余沿用值固化进会话，会话改为 `model-default` 基准。
- 助手编辑页的“已修改”基准只保存在本次编辑中；重新打开时按取值匹配方案。
- 参数方案只保存当前模型会发送的参数，“已修改”判定也只比较这些参数。
- 本计划不做“复制方案到其它模型”。
- 模型族的采样限制只对厂商官方接口剔除。同名模型经第三方中转、自建或本地服务时支持情况不确定，界面全部显示并标“可能被忽略”，用户设置的值照常发送；协议请求体本来没有的字段仍不显示。官方判断按主机名匹配厂商域名及其子域名，误判只会把官方接口当作第三方处理。
- 参数编辑不用三级弹层：参数行原位展开，所有参数统一为“减 / 输入框 / 加 / 恢复”加一排等宽快捷值；最大输出按 2 的幂翻倍减半。请求超时改为行内步进器。

## 风险

- 档案表与厂商官方域名需要随厂商更新维护；识别不到的新模型退回“隐藏推理控件、显示全部采样参数”，未列入的官方域名按第三方处理（只提醒不剔除）。
- 通用 OpenAI 兼容中转对 `reasoning_effort` 的支持无法保证；只在能识别模型系列时显示控件。
- 文档存在矛盾或缺口的项按保守处理：Gemini 4 只用 thinkingLevel；MiniMax-M3 默认状态不确定时不提供“关闭”以外的推断；OpenAI o3 档位未确认时只给 low/medium/high。
- 迁移会清掉用户手动设置过的会话采样参数（无法与档位写入区分），这是用户确认的取舍。
