# 协议参数透传与 RAG UX 修复 — 分会话执行方案

> **生成日期**: 2026-05-15
> **目标执行模型**: GLM-5.1 / 独立会话
> **依赖关系**: Phase 1 四会话并行 → Phase 2 单会话串行（依赖 A+D 完成）
> **前置审计**: `.agent/plans/20260515-rag-parameter-audit.md`

---

## 会话拓扑

```
Phase 1 (并行)
┌─ Session A: ProtocolParamAdapter 建设
├─ Session B: LocalProtocol + GenerateConfig 修复 + 极端值防护
├─ Session C: RAG UX + 思考级别联动 + 小屏动画优化
└─ Session D: LlmProvider 工厂路由修复

Phase 2 (A+D 完成后)
└─ Session E: 全协议迁移至 Adapter + 完整单元测试
```

---

# Session A: ProtocolParamAdapter 建设

> **角色**: 协议层架构师
> **依赖**: 无
> **输出**: 新建 `ProtocolParamAdapter.kt` + 单元测试
> **关键**: 你的 API 设计将直接被 Session E 调用，务必稳定

---

## 任务上下文

当前 Nexara 项目的 5 个协议实现类各自在 `buildRequestBody()` 中手动映射 `PromptRequest` 的参数字段到厂商 API JSON。**参数透传矩阵严重不一致** — GenericOpenAICompat 是唯一全参实现，OpenAI/Anthropic/VertexAI/Local 各有 2-3 个参数被静默丢弃。

你的任务是**构建一个共享的参数映射工具** `ProtocolParamAdapter`，将分散在 5 个协议中的参数映射逻辑统一到一处。

## 涉及文件

| 文件 | 作用 |
|------|------|
| **新建** `app/src/main/java/com/promenar/nexara/data/remote/protocol/ProtocolParamAdapter.kt` | 核心 Adapter |
| **新建** `app/src/test/java/com/promenar/nexara/data/remote/protocol/ProtocolParamAdapterTest.kt` | 单元测试 |

## 参考文件（只读）

- `data/remote/protocol/LlmProtocol.kt:78-96` — `PromptRequest` 定义（17 字段）
- `data/remote/protocol/GenericOpenAICompatProtocol.kt:242-248` — **唯一全参实现，作为黄金参考**
- `data/remote/protocol/OpenAIProtocol.kt:247-251` — 缺失 `topK`/`repetitionPenalty`
- `data/remote/protocol/AnthropicProtocol.kt:248-250` — 仅 `temperature`/`topP`/`topK`
- `data/remote/protocol/VertexAIProtocol.kt:363-370` — 缺失 `repetitionPenalty`
- `data/remote/protocol/LocalProtocol.kt:34-40` — 仅 5 参数，缺 `freqPenalty`/`presPenalty`
- `data/local/inference/InferenceBackend.kt:33-39` — `GenerateConfig` 定义（5 字段）

## 任务详情

### 1. 新建 `ProtocolParamAdapter.kt`

文件位于 `app/src/main/java/com/promenar/nexara/data/remote/protocol/ProtocolParamAdapter.kt`

**API 设计** — 使用 `kotlinx.serialization.json.JsonObjectBuilder` 的扩展函数模式，因为所有协议的 `buildRequestBody()` 都使用 `buildJsonObject { ... }` DSL：

```kotlin
package com.promenar.nexara.data.remote.protocol

import com.promenar.nexara.data.local.inference.GenerateConfig
import kotlinx.serialization.json.*

/**
 * 跨协议的生成参数统一适配器。
 *
 * 所有协议实现类的 buildRequestBody() 应通过此工具注入参数映射，
 * 而非各自手写 request.temperature?.let { put("temperature", it) }。
 *
 * 用法示例:
 * ```
 * val body = buildJsonObject {
 *     put("model", ...)
 *     ProtocolParamAdapter.mapCommonParams(this, request)
 *     ProtocolParamAdapter.mapPenaltyParams(this, request, protocolType)
 *     ProtocolParamAdapter.mapSamplingParams(this, request)
 * }
 * ```
 */
object ProtocolParamAdapter {

    /**
     * 映射通用生成参数: temperature, topP, maxTokens
     */
    fun mapCommonParams(body: JsonObjectBuilder, request: PromptRequest) {
        request.temperature?.let { body.put("temperature", it) }
        request.topP?.let { if (it < 1.0) body.put("top_p", it) }
        request.maxTokens?.let { body.put("max_tokens", it) }
    }

    /**
     * 映射惩罚类参数: frequencyPenalty, presencePenalty, repetitionPenalty
     * @param protocolType 当前协议类型，用于判断是否跳过某厂商不支持的参数
     */
    fun mapPenaltyParams(body: JsonObjectBuilder, request: PromptRequest, protocolType: ProtocolType) {
        when (protocolType) {
            // Anthropic API 不支持 frequency_penalty / presence_penalty / repetition_penalty
            is ProtocolType.Anthropic_Messages -> {
                // 静默跳过 — Anthropic 通过其他机制控制重复
            }
            // Local 不支持 frequencyPenalty / presencePenalty (GenerateConfig 暂不含)
            is ProtocolType.Local -> {
                // frequencyPenalty / presencePenalty 可通过 GenerateConfig 扩展后支持
                // 当前版本静默跳过，待 GenerateConfig 扩展后移除此分支
            }
            // OpenAI / VertexAI / GenericCompat 均支持全部 penalty 参数
            else -> {
                request.frequencyPenalty?.let { if (it != 0.0) body.put("frequency_penalty", it) }
                request.presencePenalty?.let { if (it != 0.0) body.put("presence_penalty", it) }
                request.repetitionPenalty?.let { body.put("repetition_penalty", it) }
            }
        }
    }

    /**
     * 映射采样参数: topK
     */
    fun mapSamplingParams(body: JsonObjectBuilder, request: PromptRequest) {
        request.topK?.let { body.put("top_k", it) }
    }

    // ─── VertexAI 专用 (key 名不同) ───

    /**
     * VertexAI 的参数映射 (generation_config 内使用不同的 key 名)
     */
    fun mapCommonParamsVertexAI(body: JsonObjectBuilder, request: PromptRequest) {
        body.put("temperature", request.temperature ?: 0.7)  // VertexAI 必须有默认值
        request.topP?.let { if (it < 1.0) body.put("top_p", it) }
        request.maxTokens?.let { body.put("max_output_tokens", it) }  // 注意 key 名不同
    }

    fun mapPenaltyParamsVertexAI(body: JsonObjectBuilder, request: PromptRequest) {
        request.frequencyPenalty?.let { body.put("frequency_penalty", it) }
        request.presencePenalty?.let { body.put("presence_penalty", it) }
        request.repetitionPenalty?.let { body.put("repetition_penalty", it) }
    }

    // ─── LocalProtocol 专用 (GenerateConfig 映射) ───

    /**
     * 从 PromptRequest 构建 GenerateConfig，包含极端值安全裁剪
     */
    fun buildGenerateConfig(request: PromptRequest): GenerateConfig {
        return clampGenerateConfig(GenerateConfig(
            maxTokens = request.maxTokens ?: 512,
            temperature = (request.temperature ?: 0.7).toFloat(),
            topP = (request.topP ?: 0.9).toFloat(),
            topK = request.topK ?: 40,
            repeatPenalty = (request.repetitionPenalty ?: 1.0).toFloat()
        ))
    }

    /**
     * 极端值安全裁剪：防止本地模型因非法参数崩溃
     */
    fun clampGenerateConfig(config: GenerateConfig): GenerateConfig {
        return config.copy(
            temperature = config.temperature.coerceIn(0.0f, 2.0f),
            topK = if (config.topK <= 0) 1 else config.topK,
            repeatPenalty = config.repeatPenalty.coerceIn(1.0f, 1.5f)
        )
    }
}
```

### 2. 新建 `ProtocolParamAdapterTest.kt`

文件位于 `app/src/test/java/com/promenar/nexara/data/remote/protocol/ProtocolParamAdapterTest.kt`

```kotlin
package com.promenar.nexara.data.remote.protocol

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.*
import org.junit.Test

class ProtocolParamAdapterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `mapCommonParams puts all standard params`() {
        val body = buildJsonObject {
            ProtocolParamAdapter.mapCommonParams(this, PromptRequest(
                messages = emptyList(),
                model = "test",
                temperature = 0.5,
                topP = 0.8,
                maxTokens = 100
            ))
        }
        assertThat(body["temperature"]?.jsonPrimitive?.double).isEqualTo(0.5)
        assertThat(body["top_p"]?.jsonPrimitive?.double).isEqualTo(0.8)
        assertThat(body["max_tokens"]?.jsonPrimitive?.int).isEqualTo(100)
    }

    @Test
    fun `topP equals 1_0 is skipped`() {
        val body = buildJsonObject {
            ProtocolParamAdapter.mapCommonParams(this, PromptRequest(
                messages = emptyList(),
                model = "test",
                topP = 1.0
            ))
        }
        assertThat(body.containsKey("top_p")).isFalse()
    }

    @Test
    fun `mapPenaltyParams for OpenAI puts all penalties`() {
        val body = buildJsonObject {
            ProtocolParamAdapter.mapPenaltyParams(this, PromptRequest(
                messages = emptyList(),
                model = "test",
                frequencyPenalty = 0.3,
                presencePenalty = 0.5,
                repetitionPenalty = 1.2
            ), ProtocolType.OpenAI_ChatCompletions)
        }
        assertThat(body["frequency_penalty"]?.jsonPrimitive?.double).isEqualTo(0.3)
        assertThat(body["presence_penalty"]?.jsonPrimitive?.double).isEqualTo(0.5)
        assertThat(body["repetition_penalty"]?.jsonPrimitive?.double).isEqualTo(1.2)
    }

    @Test
    fun `mapPenaltyParams for Anthropic skips all penalties`() {
        val body = buildJsonObject {
            ProtocolParamAdapter.mapPenaltyParams(this, PromptRequest(
                messages = emptyList(),
                model = "test",
                frequencyPenalty = 0.3,
                presencePenalty = 0.5,
                repetitionPenalty = 1.2
            ), ProtocolType.Anthropic_Messages)
        }
        assertThat(body.containsKey("frequency_penalty")).isFalse()
        assertThat(body.containsKey("presence_penalty")).isFalse()
        assertThat(body.containsKey("repetition_penalty")).isFalse()
    }

    @Test
    fun `mapPenaltyParams for Local skips freq and presence penalties`() {
        val body = buildJsonObject {
            ProtocolParamAdapter.mapPenaltyParams(this, PromptRequest(
                messages = emptyList(),
                model = "test",
                frequencyPenalty = 0.3,
                presencePenalty = 0.5,
                repetitionPenalty = 1.2
            ), ProtocolType.Local)
        }
        assertThat(body.containsKey("frequency_penalty")).isFalse()
        assertThat(body.containsKey("presence_penalty")).isFalse()
        assertThat(body.containsKey("repetition_penalty")).isTrue()  // Local 仍支持 repetition
        assertThat(body["repetition_penalty"]?.jsonPrimitive?.double).isEqualTo(1.2)
    }

    @Test
    fun `mapSamplingParams puts topK`() {
        val body = buildJsonObject {
            ProtocolParamAdapter.mapSamplingParams(this, PromptRequest(
                messages = emptyList(),
                model = "test",
                topK = 40
            ))
        }
        assertThat(body["top_k"]?.jsonPrimitive?.int).isEqualTo(40)
    }

    @Test
    fun `clampGenerateConfig prevents extreme values`() {
        val config = ProtocolParamAdapter.clampGenerateConfig(GenerateConfig(
            temperature = 3.0f,
            topK = 0,
            repeatPenalty = 3.0f
        ))
        assertThat(config.temperature).isWithin(0.001f).of(2.0f)
        assertThat(config.topK).isEqualTo(1)
        assertThat(config.repeatPenalty).isWithin(0.001f).of(1.5f)
    }

    @Test
    fun `clampGenerateConfig leaves valid values unchanged`() {
        val config = ProtocolParamAdapter.clampGenerateConfig(GenerateConfig(
            temperature = 0.7f,
            topK = 40,
            repeatPenalty = 1.0f
        ))
        assertThat(config.temperature).isWithin(0.001f).of(0.7f)
        assertThat(config.topK).isEqualTo(40)
        assertThat(config.repeatPenalty).isWithin(0.001f).of(1.0f)
    }
}
```

### 3. 验证标准

```bash
# 编译验证
./gradlew :app:compileDebugKotlin

# 单元测试（仅 Adapter 相关）
./gradlew :app:testDebugUnitTest --tests "com.promenar.nexara.data.remote.protocol.ProtocolParamAdapterTest"
```

### 4. 完成信号

- [ ] `ProtocolParamAdapter.kt` 编译通过
- [ ] `ProtocolParamAdapterTest.kt` 全部 7 个测试通过
- [ ] 无 Lint 错误
- [ ] API 签名稳定，可供 Session E 调用

---

# Session B: LocalProtocol 修复 + GenerateConfig 扩展 + 极端值防护

> **角色**: 本地推理引擎开发者
> **依赖**: 无（独立于 Session A）
> **输出**: 修改 3 个文件
> **关键发现**: `LlamaCppBackend.generate()` 只传了 `maxTokens`，其余参数全部丢失

---

## 任务上下文

Nexara 的本地推理链路存在**两层参数丢失**：

1. **LocalProtocol → GenerateConfig**: `frequencyPenalty` 和 `presencePenalty` 从未传入
2. **GenerateConfig → JNI**: `LlamaCppBackend.generate()` 第 57-60 行只将 `maxTokens` 传给底层 C++，`temperature`/`topP`/`topK`/`repeatPenalty` 全部被忽略

此外，极端值（如 `repetitionPenalty=2.0`）可能导致 llama.cpp 崩溃。

## 涉及文件

| 文件 | 操作 | 行号 |
|------|------|------|
| `data/local/inference/InferenceBackend.kt:33-39` | **扩展** `GenerateConfig` | 新增 2 字段 |
| `data/local/inference/LocalInferenceEngine.kt:57-60` | **修复** `LlamaCppBackend.generate()` | 传递全部字段 |
| `data/remote/protocol/LocalProtocol.kt:34-40` | **重构** 使用 `ProtocolParamAdapter` | 替换手写映射 |

## 任务详情

### 步骤 1: 扩展 `GenerateConfig` (InferenceBackend.kt)

当前定义（第 33-39 行）只含 5 字段，需扩展为 7 字段：

```kotlin
data class GenerateConfig(
    val maxTokens: Int = 256,
    val temperature: Float = 0.0f,
    val topP: Float = 1.0f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.0f,
    val frequencyPenalty: Float = 0.0f,   // 新增
    val presencePenalty: Float = 0.0f      // 新增
)
```

### 步骤 2: 修复 `LlamaCppBackend.generate()` (LocalInferenceEngine.kt)

当前第 57-60 行：
```kotlin
override fun generate(prompt: String, config: GenerateConfig): Flow<String> {
    val context = ctx ?: throw IllegalStateException("No model loaded")
    return context.generate(prompt, config.maxTokens)  // ← BUG: 仅传 maxTokens
}
```

修复为向 JNI 传递完整的 `GenerateConfig`：
```kotlin
override fun generate(prompt: String, config: GenerateConfig): Flow<String> {
    val context = ctx ?: throw IllegalStateException("No model loaded")
    return context.generate(
        prompt = prompt,
        maxTokens = config.maxTokens,
        temperature = config.temperature,
        topP = config.topP,
        topK = config.topK,
        repeatPenalty = config.repeatPenalty,
        frequencyPenalty = config.frequencyPenalty,
        presencePenalty = config.presencePenalty
    )
}
```

> **注意**: 需要确认 `LlamaContext.generate()` 的实际签名。如果当前 JNI 接口只接受 `(String, Int)`，则需同时更新 JNI 绑定。请在执行时搜索 `LlamaContext` 的定义确认。

### 步骤 3: 重构 `LocalProtocol` (LocalProtocol.kt)

将第 34-40 行的手写映射替换为 `ProtocolParamAdapter`：

```kotlin
import com.promenar.nexara.data.remote.protocol.ProtocolParamAdapter

// 在 sendPrompt() 中替换 genConfig 构造：
val genConfig = ProtocolParamAdapter.buildGenerateConfig(request)
```

同时移除 `LocalProtocol.kt` 第 1-7 行 import 中的 `GenerateConfig`（如果 `buildGenerateConfig` 已内部 import）。

### 步骤 4（可选）: 新增 `suspend fun generate(prompt: String, config: GenerateConfig)` 到 `LocalInferenceEngine.kt` 第 160 行

当前：
```kotlin
fun generate(prompt: String, config: GenerateConfig = GenerateConfig()): Flow<String>
```

如果你的修改涉及协程上下文切换（`withContext(Dispatchers.Default)`），无需改动此签名。

## 验证标准

```bash
./gradlew :app:compileDebugKotlin
```

## 完成信号

- [ ] `GenerateConfig` 包含 7 字段
- [ ] `LlamaCppBackend.generate()` 传递全部配置到 JNI
- [ ] `LocalProtocol` 使用 `ProtocolParamAdapter.buildGenerateConfig()`
- [ ] 编译通过，无 Lint 错误

---

# Session C: RAG UX 修复 + 思考级别联动 + 小屏动画优化

> **角色**: UI/UX 开发者
> **依赖**: 无（纯 UI 变更，独立于协议层）
> **输出**: 修改 4 个文件
> **可并行**: 与 Session A/B/D 完全并行

---

## 任务上下文

审计发现 3 个 UX 问题：

1. **P0**: 用户在 SettingsPanel 中同时关闭"会话 RAG"和"跨会话检索"后，无任何提示表明"RAG 已完全禁用"
2. **P2**: 点击思考级别卡片（Minimal/Low/Medium/High）仅修改 `temperature`，用户期望联动调整
3. **P2**: `NexaraCollapsibleSection` 在 `<360dp` 小屏上 300ms 动画可能卡顿

## 涉及文件

| 文件 | 操作 | 区域 |
|------|------|------|
| `ui/chat/SessionSettingsSheet.kt` | **修改** `SettingsPanel()` + `ParamsPanel()` | 新增指示器 + 联动逻辑 |
| `ui/common/NexaraCollapsibleSection.kt` | **修改** 动画时长 | 第 45 行 |
| `res/values/strings.xml` | **新增** 2 个字符串 | 英文 |
| `res/values-zh-rCN/strings.xml` | **新增** 2 个字符串 | 中文 |

## 任务详情

### 步骤 1: RAG 全局关闭状态反馈

在 `SettingsPanel()` (SessionSettingsSheet.kt 第 878-901 行) 的 ToolToggleRow 列表**之后**，插入一个状态指示器。

在最后一个 `ToolToggleRow` (kgEnabled) 之后、`Column` 闭合之前，添加：

```kotlin
// 在 ToolToggleRow(stringResource(R.string.sheet_settings_kg), ...) 之后
val isRagFullyDisabled = !memoryEnabled && !globalMemoryEnabled && !docsEnabled && !kgEnabled
if (isRagFullyDisabled) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = NexaraColors.SurfaceLow.copy(alpha = 0.5f),
        border = BorderStroke(0.5.dp, NexaraColors.GlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = NexaraColors.OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    stringResource(R.string.sheet_settings_rag_disabled),
                    style = NexaraTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = NexaraColors.OnSurfaceVariant
                )
                Text(
                    stringResource(R.string.sheet_settings_rag_disabled_hint),
                    style = NexaraTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = NexaraColors.OnSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
```

需要新增 import: `androidx.compose.foundation.BorderStroke`

### 步骤 2: strings.xml 新增

**values/strings.xml** (英文):
```xml
<string name="sheet_settings_rag_disabled">All memory features disabled</string>
<string name="sheet_settings_rag_disabled_hint">AI will only use current conversation context</string>
```

**values-zh-rCN/strings.xml** (中文):
```xml
<string name="sheet_settings_rag_disabled">所有记忆功能已关闭</string>
<string name="sheet_settings_rag_disabled_hint">AI 将仅基于当前对话上下文回答</string>
```

### 步骤 3: 思考级别联动扩展

在 `ParamsPanel()` (SessionSettingsSheet.kt 第 430-439 行) 中，修改 `onLevelClick`：

当前（仅修改 temperature）:
```kotlin
val onLevelClick: (String) -> Unit = { levelId ->
    val newTemp = when (levelId) {
        "minimal" -> 0.1f
        "low" -> 0.4f
        "medium" -> 0.7f
        "high" -> 1.0f
        else -> 0.7f
    }
    currentTemperature = newTemp
    chatViewModel.updateInferenceParams(params.copy(temperature = newTemp.toDouble()))
}
```

改为联动更新全部参数：
```kotlin
val onLevelClick: (String) -> Unit = { levelId ->
    val (newTemp, newTopP, newTopK) = when (levelId) {
        "minimal" -> Triple(0.1f, 0.5f, 10)
        "low"     -> Triple(0.4f, 0.8f, 30)
        "medium"  -> Triple(0.7f, 0.9f, 50)
        "high"    -> Triple(1.0f, 1.0f, 100)
        else      -> Triple(0.7f, 0.9f, 50)
    }
    currentTemperature = newTemp
    currentTopP = newTopP
    currentTopK = newTopK
    chatViewModel.updateInferenceParams(params.copy(
        temperature = newTemp.toDouble(),
        topP = newTopP.toDouble(),
        topK = newTopK
    ))
}
```

### 步骤 4: 小屏动画优化

在 `NexaraCollapsibleSection.kt` 第 40-46 行，将固定时长改为屏幕自适应：

在 `NexaraCollapsibleSection` composable 中添加：
```kotlin
import androidx.compose.ui.platform.LocalConfiguration

// 在函数体内：
val screenWidthDp = LocalConfiguration.current.screenWidthDp
val animDuration = if (screenWidthDp < 360) 150 else 300
```

然后替换第 45 行:
```kotlin
// 旧:
animateContentSize(animationSpec = tween(300))
// 新:
animateContentSize(animationSpec = tween(animDuration))
```

## 验证标准

```bash
./gradlew :app:compileDebugKotlin
```

## 完成信号

- [ ] RAG 关闭时显示"所有记忆功能已关闭"提示
- [ ] 中英文 strings 新增完成
- [ ] 思考级别卡片修改 temperature + topP + topK
- [ ] `<360dp` 设备上折叠动画 150ms
- [ ] 编译通过

---

# Session D: LlmProvider 工厂路由修复

> **角色**: 协议路由层开发者
> **依赖**: 无（独立修改）
> **输出**: 修改 1 个文件
> **关键发现**: Cohere/Mistral/DeepSeek/GenericCompat 全部被错误路由到 `OpenAIProtocol`

---

## 任务上下文

`LlmProvider.kt` 的工厂方法 `createProtocol()` (第 61-86 行) 存在路由错误：

```kotlin
is ProtocolType.Cohere_Chat           -> OpenAIProtocol(baseUrl, apiKey, model)  // ❌ 缺少参数
is ProtocolType.Mistral_Chat          -> OpenAIProtocol(baseUrl, apiKey, model)  // ❌ 缺少参数
is ProtocolType.DeepSeek              -> OpenAIProtocol(baseUrl, apiKey, model)  // ❌ 缺少参数
is ProtocolType.Generic_OpenAI_Compat -> OpenAIProtocol(baseUrl, apiKey, model)  // ❌ 缺少参数
```

这些协议类型**应该路由到 `GenericOpenAICompatProtocol`**（该类的 `buildRequestBody()` 实现了全部 7 个高级参数），而非 `OpenAIProtocol`（缺少 `topK`/`repetitionPenalty`）。

## 涉及文件

| 文件 | 操作 | 行号 |
|------|------|------|
| `data/remote/provider/LlmProvider.kt` | **修改** `createProtocol()` 路由 | 第 79-82 行 |

## 任务详情

**修改前** (LlmProvider.kt 第 79-82 行):
```kotlin
is ProtocolType.Cohere_Chat -> OpenAIProtocol(baseUrl, apiKey, model)
is ProtocolType.Mistral_Chat -> OpenAIProtocol(baseUrl, apiKey, model)
is ProtocolType.DeepSeek -> OpenAIProtocol(baseUrl, apiKey, model)
is ProtocolType.Generic_OpenAI_Compat -> OpenAIProtocol(baseUrl, apiKey, model)
```

**修改后**:
```kotlin
is ProtocolType.Cohere_Chat -> GenericOpenAICompatProtocol(baseUrl, apiKey, model)
is ProtocolType.Mistral_Chat -> GenericOpenAICompatProtocol(baseUrl, apiKey, model)
is ProtocolType.DeepSeek -> GenericOpenAICompatProtocol(baseUrl, apiKey, model)
is ProtocolType.Generic_OpenAI_Compat -> GenericOpenAICompatProtocol(baseUrl, apiKey, model)
```

需要新增 import:
```kotlin
import com.promenar.nexara.data.remote.protocol.GenericOpenAICompatProtocol
```

### 额外检查

确认 `GenericOpenAICompatProtocol` 与 `OpenAIProtocol` 在以下方面行为一致：
1. SSE 流式解析逻辑 → 两者共用 `ThinkingDetector`/`AccumulatedToolCall`，行为一致 ✅
2. `buildRequestBody()` 消息构造 → 两者结构相同，但 `GenericOpenAICompatProtocol` 额外包含 `topK`/`repetitionPenalty` ✅
3. `sendPromptSync()` 错误处理 → 两者结构相同 ✅
4. `listModels()` → `GenericOpenAICompatProtocol` 也实现了 ✅

**无需额外修改**。

## 验证标准

```bash
./gradlew :app:compileDebugKotlin
```

## 完成信号

- [ ] 4 个协议类型路由至 `GenericOpenAICompatProtocol`
- [ ] import 语句补充完整
- [ ] 编译通过

---

# Session E: 全协议迁移至 Adapter + 完整单元测试补全

> **角色**: 协议重构工程师
> **依赖**: Session A (Adapter API) + Session D (LlmProvider 路由) 先完成
> **输出**: 修改 5 个协议文件 + 新建/扩展 2 个测试文件
> **此会话应在 A 和 D 完成后启动**

---

## 任务上下文

Session A 已创建 `ProtocolParamAdapter`，Session D 已将非 OpenAI 协议路由到 `GenericOpenAICompatProtocol`。

你的任务是将 5 个协议的 `buildRequestBody()` 全部迁移到 Adapter，并编写完整的协议参数回归测试套件。

## 涉及文件

| 文件 | 操作 | 关键行号 |
|------|------|---------|
| `data/remote/protocol/OpenAIProtocol.kt` | 重构参数映射 | 247-251 |
| `data/remote/protocol/AnthropicProtocol.kt` | 重构 + 注释不支持参数 | 248-250 |
| `data/remote/protocol/VertexAIProtocol.kt` | 重构参数映射 | 363-370 |
| `data/remote/protocol/GenericOpenAICompatProtocol.kt` | 重构参数映射 | 242-248 |
| `data/remote/protocol/LocalProtocol.kt` | 重构（如 Session B 未做） | 34-40 |
| **新建** `test/.../CrossProtocolParamAuditTest.kt` | 协议参数矩阵单元测试 | — |
| `test/.../chat/ProtocolParamTest.kt` | **扩展** 增加 OpenAI+Local 测试 | — |

## 任务详情

### 步骤 1: OpenAIProtocol 迁移

`OpenAIProtocol.kt` 第 247-251 行 — 替换为 Adapter 调用：

```kotlin
// 旧代码 (第 247-251 行):
request.temperature?.let { put("temperature", it) }
request.topP?.let { if (it < 1.0) put("top_p", it) }
request.maxTokens?.let { put("max_tokens", it) }
request.frequencyPenalty?.let { if (it != 0.0) put("frequency_penalty", it) }
request.presencePenalty?.let { if (it != 0.0) put("presence_penalty", it) }

// 新代码:
ProtocolParamAdapter.mapCommonParams(this, request)
ProtocolParamAdapter.mapPenaltyParams(this, request, protocolType)
ProtocolParamAdapter.mapSamplingParams(this, request)
```

添加 import: `import com.promenar.nexara.data.remote.protocol.ProtocolParamAdapter`

### 步骤 2: AnthropicProtocol 迁移

`AnthropicProtocol.kt` 第 248-250 行 — 替换为 Adapter 调用，并添加注释说明 Anthropic API 的限制：

```kotlin
// 旧代码 (第 248-250 行):
request.temperature?.let { put("temperature", it) }
request.topP?.let { if (it < 1.0) put("top_p", it) }
request.topK?.let { put("top_k", it) }

// 新代码:
ProtocolParamAdapter.mapCommonParams(this, request)
// Anthropic Messages API 不支持 frequency_penalty / presence_penalty / repetition_penalty
// ProtocolParamAdapter 已内置此协议类型的空操作降级
ProtocolParamAdapter.mapPenaltyParams(this, request, protocolType)
ProtocolParamAdapter.mapSamplingParams(this, request)
```

### 步骤 3: VertexAIProtocol 迁移

`VertexAIProtocol.kt` 第 363-370 行 — 替换为 Adapter 调用（注意：VertexAI 使用专用方法，因为 key 名不同）：

```kotlin
// 旧代码 (第 363-370 行):
put("temperature", request.temperature ?: 0.7)
request.topP?.let { if (it < 1.0) put("top_p", it) }
request.maxTokens?.let { put("max_output_tokens", it) }
request.topK?.let { put("top_k", it) }
request.frequencyPenalty?.let { put("frequency_penalty", it) }
request.presencePenalty?.let { put("presence_penalty", it) }

// 新代码:
ProtocolParamAdapter.mapCommonParamsVertexAI(this, request)
ProtocolParamAdapter.mapPenaltyParamsVertexAI(this, request)
ProtocolParamAdapter.mapSamplingParams(this, request)
```

### 步骤 4: GenericOpenAICompatProtocol 迁移

`GenericOpenAICompatProtocol.kt` 第 242-248 行：

```kotlin
// 旧代码 (第 242-248 行):
request.temperature?.let { put("temperature", it) }
request.topP?.let { if (it < 1.0) put("top_p", it) }
request.maxTokens?.let { put("max_tokens", it) }
request.frequencyPenalty?.let { if (it != 0.0) put("frequency_penalty", it) }
request.presencePenalty?.let { if (it != 0.0) put("presence_penalty", it) }
request.topK?.let { put("top_k", it) }
request.repetitionPenalty?.let { put("repetition_penalty", it) }

// 新代码:
ProtocolParamAdapter.mapCommonParams(this, request)
ProtocolParamAdapter.mapPenaltyParams(this, request, protocolType)
ProtocolParamAdapter.mapSamplingParams(this, request)
```

### 步骤 5: LocalProtocol 迁移（如 Session B 已做则跳过）

`LocalProtocol.kt` 第 34-40 行：

```kotlin
// 旧代码:
val genConfig = GenerateConfig(
    maxTokens = request.maxTokens ?: 512,
    temperature = (request.temperature ?: 0.7).toFloat(),
    topP = (request.topP ?: 0.9).toFloat(),
    topK = request.topK ?: 40,
    repeatPenalty = (request.repetitionPenalty ?: 1.0).toFloat()
)

// 新代码:
val genConfig = ProtocolParamAdapter.buildGenerateConfig(request)
```

### 步骤 6: 新建 `CrossProtocolParamAuditTest.kt`

文件位于 `app/src/test/java/com/promenar/nexara/data/remote/protocol/CrossProtocolParamAuditTest.kt`

```kotlin
package com.promenar.nexara.data.remote.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Test

class CrossProtocolParamAuditTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `OpenAIProtocol transmits all 7 parameters`() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteReadPacket().readText()
            respond(content = "{}", status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val protocol = OpenAIProtocol(
            baseUrl = "https://api.openai.com", apiKey = "test-key",
            model = "gpt-4o", httpClient = HttpClient(mockEngine)
        )
        val request = PromptRequest(
            messages = listOf(ProtocolMessage(role = "user", content = "hi")),
            model = "gpt-4o",
            temperature = 0.5, topP = 0.8, maxTokens = 100,
            topK = 40, repetitionPenalty = 1.1,
            frequencyPenalty = 0.3, presencePenalty = 0.2,
            stream = false
        )
        try { protocol.sendPromptSync(request) } catch (_: Exception) {}
        assertThat(capturedBody).isNotNull()
        val body = json.parseToJsonElement(capturedBody!!).jsonObject
        assertThat(body["temperature"]?.jsonPrimitive?.double).isEqualTo(0.5)
        assertThat(body["top_p"]?.jsonPrimitive?.double).isEqualTo(0.8)
        assertThat(body["max_tokens"]?.jsonPrimitive?.int).isEqualTo(100)
        assertThat(body["top_k"]?.jsonPrimitive?.int).isEqualTo(40)
        assertThat(body["repetition_penalty"]?.jsonPrimitive?.double).isEqualTo(1.1)
        assertThat(body["frequency_penalty"]?.jsonPrimitive?.double).isEqualTo(0.3)
        assertThat(body["presence_penalty"]?.jsonPrimitive?.double).isEqualTo(0.2)
    }

    @Test
    fun `AnthropicProtocol does not crash with unsupported penalty params`() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteReadPacket().readText()
            respond(content = "{\"type\":\"message\",\"content\":[],\"usage\":{\"input_tokens\":10,\"output_tokens\":10}}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val protocol = AnthropicProtocol(
            baseUrl = "https://api.anthropic.com", apiKey = "test-key",
            model = "claude-3", httpClient = HttpClient(mockEngine)
        )
        val request = PromptRequest(
            messages = listOf(ProtocolMessage(role = "user", content = "hi")),
            model = "claude-3",
            temperature = 0.5, topK = 40,
            repetitionPenalty = 1.2,  // Anthropic 不支持
            frequencyPenalty = 0.3,    // Anthropic 不支持
            presencePenalty = 0.2,     // Anthropic 不支持
            stream = false
        )
        // 不应崩溃
        try { protocol.sendPromptSync(request) } catch (_: Exception) {}
        assertThat(capturedBody).isNotNull()
        val body = json.parseToJsonElement(capturedBody!!).jsonObject
        // Anthropic API 不支持 penalty 字段，确认未被注入
        assertThat(body.containsKey("frequency_penalty")).isFalse()
        assertThat(body.containsKey("presence_penalty")).isFalse()
        assertThat(body.containsKey("repetition_penalty")).isFalse()
    }

    @Test
    fun `VertexAIProtocol includes repetitionPenalty`() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteReadPacket().readText()
            respond(content = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hi\"}]}}]}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val tempKey = java.io.File.createTempFile("sa_key", ".json")
        tempKey.writeText("{\"client_email\":\"test@test.com\",\"private_key\":\"-----BEGIN PRIVATE KEY-----\\nMIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDE...\\n-----END PRIVATE KEY-----\"}")
        val protocol = VertexAIProtocol(
            serviceAccountKeyPath = tempKey.absolutePath,
            projectId = "test-project", model = "gemini-1.5",
            httpClient = HttpClient(mockEngine)
        )
        val request = PromptRequest(
            messages = listOf(ProtocolMessage(role = "user", content = "hi")),
            model = "gemini-1.5",
            temperature = 0.6, topK = 30,
            repetitionPenalty = 1.3, frequencyPenalty = 0.1, presencePenalty = 0.2,
            stream = false
        )
        try { protocol.sendPromptSync(request) } catch (_: Exception) {}
        if (capturedBody != null) {
            val body = json.parseToJsonElement(capturedBody!!).jsonObject
            val config = body["generation_config"]?.jsonObject
            if (config != null) {
                assertThat(config["repetition_penalty"]?.jsonPrimitive?.double).isEqualTo(1.3)
                assertThat(config["frequency_penalty"]?.jsonPrimitive?.double).isEqualTo(0.1)
            }
        }
    }

    @Test
    fun `GenericOpenAICompatProtocol still transmits all 7 parameters after Adapter migration`() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteReadPacket().readText()
            respond(content = "{}", status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val protocol = GenericOpenAICompatProtocol(
            baseUrl = "http://localhost", apiKey = "test-key",
            model = "test-model", httpClient = HttpClient(mockEngine)
        )
        val request = PromptRequest(
            messages = listOf(ProtocolMessage(role = "user", content = "hi")),
            model = "test-model",
            temperature = 0.8, topP = 0.9, maxTokens = 100,
            topK = 50, repetitionPenalty = 1.2,
            presencePenalty = 0.5, frequencyPenalty = 0.3,
            stream = false
        )
        try { protocol.sendPromptSync(request) } catch (_: Exception) {}
        assertThat(capturedBody).isNotNull()
        val body = json.parseToJsonElement(capturedBody!!).jsonObject
        assertThat(body["temperature"]?.jsonPrimitive?.double).isEqualTo(0.8)
        assertThat(body["top_p"]?.jsonPrimitive?.double).isEqualTo(0.9)
        assertThat(body["max_tokens"]?.jsonPrimitive?.int).isEqualTo(100)
        assertThat(body["top_k"]?.jsonPrimitive?.int).isEqualTo(50)
        assertThat(body["repetition_penalty"]?.jsonPrimitive?.double).isEqualTo(1.2)
        assertThat(body["presence_penalty"]?.jsonPrimitive?.double).isEqualTo(0.5)
        assertThat(body["frequency_penalty"]?.jsonPrimitive?.double).isEqualTo(0.3)
    }

    @Test
    fun `PromptRequest serialization carries images and all advanced params`() {
        val request = PromptRequest(
            messages = listOf(ProtocolMessage(
                role = "user", content = "Describe",
                imageUrls = listOf(ImageInput(base64 = "abc", mimeType = "image/png"))
            )),
            model = "gpt-4o",
            temperature = 0.5, topK = 40, repetitionPenalty = 1.1,
            frequencyPenalty = 0.3, presencePenalty = 0.2
        )
        val encoded = json.encodeToString(PromptRequest.serializer(), request)
        assertThat(encoded).contains("\"topK\":40")
        assertThat(encoded).contains("\"repetitionPenalty\":1.1")
        assertThat(encoded).contains("\"frequencyPenalty\":0.3")
        assertThat(encoded).contains("\"imageUrls\"")
    }
}
```

### 步骤 7: 扩展 `ProtocolParamTest.kt`

在现有测试文件中新增两个测试方法：

```kotlin
@Test
fun testOpenAIProtocol_parametersMapping() = runTest {
    var capturedBody: String? = null
    val mockEngine = MockEngine { request ->
        capturedBody = request.body.toByteReadPacket().readText()
        respond(content = "{}", status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val protocol = OpenAIProtocol(
        baseUrl = "https://api.openai.com", apiKey = "test-key",
        model = "gpt-4o", httpClient = HttpClient(mockEngine)
    )
    val request = PromptRequest(
        messages = listOf(ProtocolMessage(role = "user", content = "hi")),
        model = "gpt-4o",
        temperature = 0.5, topP = 0.8, maxTokens = 100,
        topK = 40, repetitionPenalty = 1.1,
        frequencyPenalty = 0.3, presencePenalty = 0.2,
        stream = false
    )
    try { protocol.sendPromptSync(request) } catch (_: Exception) {}
    assertThat(capturedBody).isNotNull()
    val body = json.parseToJsonElement(capturedBody!!).jsonObject
    assertThat(body["temperature"]?.jsonPrimitive?.double).isEqualTo(0.5)
    assertThat(body["top_k"]?.jsonPrimitive?.int).isEqualTo(40)
    assertThat(body["repetition_penalty"]?.jsonPrimitive?.double).isEqualTo(1.1)
}

@Test
fun `Anthropic gracefully skips unsupported penalties`() = runTest {
    var capturedBody: String? = null
    val mockEngine = MockEngine { request ->
        capturedBody = request.body.toByteReadPacket().readText()
        respond(content = "{\"type\":\"message\",\"content\":[],\"usage\":{\"input_tokens\":10,\"output_tokens\":10}}",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val protocol = AnthropicProtocol(
        baseUrl = "https://api.anthropic.com", apiKey = "test-key",
        model = "claude-3", httpClient = HttpClient(mockEngine)
    )
    val request = PromptRequest(
        messages = listOf(ProtocolMessage(role = "user", content = "hi")),
        model = "claude-3",
        temperature = 0.5, topK = 40,
        repetitionPenalty = 1.2, frequencyPenalty = 0.3,  // 均不支持
        stream = false
    )
    try { protocol.sendPromptSync(request) } catch (_: Exception) {}
    val body = json.parseToJsonElement(capturedBody!!).jsonObject
    assertThat(body.containsKey("repetition_penalty")).isFalse()
    assertThat(body.containsKey("frequency_penalty")).isFalse()
}
```

## 验证标准

```bash
# 编译
./gradlew :app:compileDebugKotlin

# 运行全部协议相关测试
./gradlew :app:testDebugUnitTest --tests "com.promenar.nexara.data.remote.protocol.*"
./gradlew :app:testDebugUnitTest --tests "com.promenar.nexara.ui.chat.ProtocolParamTest"
```

## 完成信号

- [ ] 5 个协议全部迁移至 `ProtocolParamAdapter`
- [ ] `CrossProtocolParamAuditTest.kt` 5 个测试全部通过
- [ ] `ProtocolParamTest.kt` 扩展测试通过
- [ ] 原有 `LlmProtocolSerializationTest` 无回归
- [ ] 编译通过，无 Lint 错误

---

# 附录: 执行检查清单

| 会话 | 状态 | 依赖 | 验证命令 |
|------|------|------|---------|
| Session A: ProtocolParamAdapter | ⬜ 待执行 | 无 | `./gradlew :app:testDebugUnitTest --tests "*.ProtocolParamAdapterTest"` |
| Session B: LocalProtocol 修复 | ⬜ 待执行 | 无 | `./gradlew :app:compileDebugKotlin` |
| Session C: RAG UX 修复 | ⬜ 待执行 | 无 | `./gradlew :app:compileDebugKotlin` |
| Session D: LlmProvider 路由 | ⬜ 待执行 | 无 | `./gradlew :app:compileDebugKotlin` |
| Session E: 全协议迁移 + 测试 | ⬜ 待执行 | A+D | `./gradlew :app:testDebugUnitTest --tests "*.ProtocolParam*" --tests "*.CrossProtocol*"` |

## 执行顺序

```
Day 1: 同时启动 Session A, B, C, D (4 个独立会话并行)

Day 2 (A+D 完成后): 启动 Session E
```

---

*本文档由 2026-05-15 审计驱动生成，供 GLM-5.1 独立会话执行*
