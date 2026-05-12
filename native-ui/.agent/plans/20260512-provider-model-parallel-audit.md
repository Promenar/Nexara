# 提供商—模型管理全链路审计与并行升级方案

> **创建日期**: 2026-05-12
> **Phase 0**: ✅ 2026-05-12 (DeepSeek) | **Phase 1A/1B/1C**: ✅ 2026-05-12 (Gemini 3 Flash, 2分钟并行) | **Phase 2**: ✅ 2026-05-12 (DeepSeek 收尾)
> **最终状态**: BUILD SUCCESSFUL，12文件变更，3新文件，290+行净增
> **目标**: 系统性修复 native-ui 的提供商管理、模型管理、全站模型选择器四大缺陷
> **执行策略**: 1 个串行基石阶段 + 3 个并行阶段，每阶段独立会话执行

---

## 一、并行可行性评估

### 1.1 结论：高度可行

经过依赖分析，本方案 4 个核心模块之间存在以下依赖关系：

```
Phase 0 (Foundation)
  ├── 创建 ProviderManager.kt (NEW)         ← 被 Phase 1C 消费
  ├── 创建 ProviderModels.kt (NEW)          ← 被 Phase 1A/1C 消费
  ├── 升级 LlmProtocol.kt (ProtocolType)    ← 被 Phase 1A/1B 消费
  ├── 扩展 ChatModels.kt (多模态字段)       ← 被 Phase 1B 消费
  ├── 刷新 ModelSpecs.kt (全量模型条目)     ← 被 Phase 1C 消费
  ├── 重构 SettingsViewModel.kt (ProviderManager集成) ← 被 Phase 1C 消费
  └── 重构 NexaraApplication.kt (委托ProviderManager)  ← 被 Phase 0 自身消费
        │
Phase 1A: ProviderForm修复 + ProtocolSelector ← 无文件冲突
Phase 1B: 多模态协议层升级                      ← 无文件冲突
Phase 1C: 模型选择器同步修复                    ← 无文件冲突
        │
Phase 2: 集成验证 + DIA (串行，依赖所有Phase 1)
```

**关键设计决策：Phase 0 吸收所有共享文件修改**，确保三个并行 Phase 1 操作的文件集完全不相交：

| Phase | 文件 | 冲突检查 |
|-------|------|---------|
| 0 | ProviderManager.kt, ProviderModels.kt, LlmProtocol.kt, ChatModels.kt, ModelSpecs.kt, SettingsViewModel.kt, NexaraApplication.kt | — |
| 1A | ProviderFormScreen.kt, ProtocolSelector.kt(NEW) | ✅ 独立 |
| 1B | OpenAIProtocol.kt, AnthropicProtocol.kt, VertexAIProtocol.kt, LocalProtocol.kt, GenericOpenAICompatProtocol.kt(NEW), ProtocolFactory.kt(NEW) | ✅ 独立 |
| 1C | UserSettingsHomeScreen.kt, ModelPicker.kt, ProviderModelsScreen.kt, NavGraph.kt | ✅ 独立 |

### 1.2 Gemini 3 Flash 适配性

- **优势**: Flash 模型速度快(>200 tok/s)，适合中等复杂度的单文件修改任务
- **每个 Phase 1 子阶段的代码量**: 200-400 行净新增/修改，Flash 完全胜任
- **关键要求**: 每个 Phase 提示词必须自包含，包含完整的接口契约（Phase 0 输出）
- **风险**: Flash 对长上下文理解稍弱，提示词需精确描述数据结构和修改位置
- **建议**: 如果 Flash 上下文窗口较小，可将 Phase 0 拆为 Part A + Part B 两个 Flash 会话

---

## 二、阶段拆分方案

### Phase 0 — 基石层（串行，必须先完成）

**时长估算**: ~60 分钟 / ~15K 上下文
**目标**: 创建所有共享数据类型、ProviderManager 基础设施、刷新模型数据库

**文件清单**:
| 操作 | 文件 | 说明 |
|------|------|------|
| NEW | `data/model/ProviderModels.kt` | 扩展的 ProviderListItem（含 protocolType/apiKey），ProviderConfig 提取 |
| NEW | `data/manager/ProviderManager.kt` | 统一 CRUD 单例，暴露 StateFlow |
| MODIFY | `data/remote/protocol/LlmProtocol.kt` | ProtocolId enum → ProtocolType sealed class（9个子类型） |
| MODIFY | `data/model/ChatModels.kt` | PromptRequest 增加 images/audio/documents 多模态字段 |
| MODIFY | `data/model/ModelSpecs.kt` | ModelCapabilities 扩展至12维度 + 50+新模型条目 |
| MODIFY | `ui/settings/SettingsViewModel.kt` | 新增 getProviderConfig() + 对接 ProviderManager |
| MODIFY | `NexaraApplication.kt` | updateProvider() 委托 ProviderManager，ProviderConfig 迁移 |

---

### Phase 1A — ProviderForm 全链路修复 & 协议选择器 (并行)

**时长估算**: ~30 分钟 / ~8K 上下文
**目标**: 修复编辑回填、动态标题、新增协议类型选择器

**文件清单**:
| 操作 | 文件 | 说明 |
|------|------|------|
| NEW | `ui/common/ProtocolSelector.kt` | 独立的协议类型选择器可组合组件 |
| MODIFY | `ui/settings/ProviderFormScreen.kt` | 编辑回填 + 协议选择器集成 + 动态标题 |

**依赖接口 (Phase 0 提供)**:
- `ProtocolType` sealed class（9种子类型，含 displayName/defaultBaseUrl/defaultPath 属性）
- `ProviderListItem` 包含 `protocolType: ProtocolType`、`apiKey: String` 字段
- `SettingsViewModel.getProviderConfig(providerId: String): ProviderConfig?` 方法
- `ProviderManager` 提供的 StateFlow

---

### Phase 1B — 多模态协议层升级 (并行)

**时长估算**: ~45 分钟 / ~12K 上下文
**目标**: 所有协议的 sendPrompt 流程增加多模态消息构建支持

**文件清单**:
| 操作 | 文件 | 说明 |
|------|------|------|
| MODIFY | `data/remote/protocol/OpenAIProtocol.kt` | 增加 `image_url`、`input_audio` 等多模态 content block |
| MODIFY | `data/remote/protocol/AnthropicProtocol.kt` | 增加 `image`、`document` 等 Anthropic 多模态块 |
| MODIFY | `data/remote/protocol/VertexAIProtocol.kt` | 补全 `fileData`、`videoMetadata` 等 parts 类型 |
| MODIFY | `data/remote/protocol/LocalProtocol.kt` | 增加基于模板的图像输入传递支持 |
| NEW | `data/remote/protocol/GenericOpenAICompatProtocol.kt` | 通用 OpenAI 兼容协议（Ollama/vLLM/LiteLLM） |
| NEW | `data/remote/protocol/ProtocolFactory.kt` | 协议实例工厂 |

**依赖接口 (Phase 0 提供)**:
- `ProtocolType` sealed class 及其子类型
- `PromptRequest` 扩展后的多模态字段（`images: List<ImageInput>?` 等）
- `ProtocolMessage` 扩展的 `imageUrls/audioData/documentData` 字段
- `ImageInput/AudioInput/DocumentInput` 数据类定义

---

### Phase 1C — 模型选择器同步修复 (并行)

**时长估算**: ~30 分钟 / ~8K 上下文
**目标**: 修复模型配置后全站选择器不同步问题

**文件清单**:
| 操作 | 文件 | 说明 |
|------|------|------|
| MODIFY | `ui/hub/UserSettingsHomeScreen.kt` | 预设模型选择器增加刷新机制 |
| MODIFY | `ui/common/ModelPicker.kt` | 支持新 ModelCapability 维度 + audio_input 等标签 |
| MODIFY | `ui/settings/ProviderModelsScreen.kt` | 对接 ProviderManager 数据源 |
| MODIFY | `navigation/NavGraph.kt` | ProviderForm save 回调调用 refreshProviders() |

**依赖接口 (Phase 0 提供)**:
- `ProviderManager.providerModels` StateFlow
- `ModelCapabilities` 扩展后的字段（`audioInput`, `videoUnderstanding`, `structuredOutput` 等 12 字段）
- `ModelSpecs.kt` 刷新后的完整模型数据库（50+ 新条目）
- `SettingsViewModel` 已对接 ProviderManager，新增 `refreshProviderModels()` 方法

---

### Phase 2 — 集成验证 & DIA（串行，依赖所有 Phase 1）

**时长估算**: ~20 分钟 / ~5K 上下文
**目标**: 跨模块一致性验证，构建通过性检查，DIA 文档更新

**任务**:
1. 全局搜索所有 `ProtocolId` 引用，确认已迁移为 `ProtocolType`
2. 验证 `ProviderManager` StateFlow 的订阅链完整性
3. 模拟用户路径：添加提供商 → 编辑 → 管理模型 → 返回设置选择模型
4. DIA 检查并更新 registry.md 和 handover.md

---

## 三、并行执行提示词 (可直接粘贴至 Gemini Flash)

> **以下提示词每个均可直接在空的 Gemini 3 Flash 会话中执行。**
> **前提条件：Phase 0 已完成，`native-ui/` 为工作目录。**

---

### Phase 1A 提示词 — ProviderForm 修复 & 协议选择器

```
## 任务背景

你是 Android/Kotlin/Jetpack Compose 开发专家。修复 Nexara 项目提供商编辑功能。

Phase 0 已完成的基础工作（你需要知道这些接口，但不要修改对应文件）：

1. `data/remote/protocol/LlmProtocol.kt` — ProtocolId 枚举已升级为 ProtocolType sealed class：
sealed class ProtocolType(val displayName: String, val defaultBaseUrl: String, val defaultPath: String) {
    data object OpenAI_ChatCompletions : ProtocolType("OpenAI Chat Completions", "https://api.openai.com", "/v1/chat/completions")
    data object OpenAI_Responses : ProtocolType("OpenAI Responses", "https://api.openai.com", "/v1/responses")
    data object Anthropic_Messages : ProtocolType("Anthropic Messages", "https://api.anthropic.com", "/v1/messages")
    data object Google_VertexAI : ProtocolType("Google Vertex AI", "https://generativelanguage.googleapis.com", "/v1beta/models")
    data object Cohere_Chat : ProtocolType("Cohere Chat", "https://api.cohere.ai", "/v2/chat")
    data object Mistral_Chat : ProtocolType("Mistral Chat", "https://api.mistral.ai", "/v1/chat/completions")
    data object DeepSeek : ProtocolType("DeepSeek", "https://api.deepseek.com", "/v1/chat/completions")
    data object Generic_OpenAI_Compat : ProtocolType("OpenAI 兼容 (通用)", "", "/v1/chat/completions")
    data object Local : ProtocolType("本地推理", "", "")
}

2. `data/model/ProviderModels.kt` — ProviderListItem 扩展了字段：
   data class ProviderListItem(
       val id: String = "",
       val name: String = "",
       val typeName: String = "",
       val baseUrl: String = "",
       val model: String = "",
       val protocolType: ProtocolType = ProtocolType.Generic_OpenAI_Compat,  // 新增
       val apiKey: String = ""  // 新增
   )

3. `ui/settings/SettingsViewModel.kt` 新增方法：
   fun getProviderConfig(providerId: String): ProviderConfig?
      — providerId 为 "default" 时从主配置加载
      — providerId 为 "extra_N" 时从 extra_provider_N_* 键加载
      — 返回 ProviderConfig(protocolType, baseUrl, apiKey, model, name)

4. `NexaraApplication.ProviderConfig` 数据类：
   data class ProviderConfig(
       val protocolType: ProtocolType,
       val baseUrl: String,
       val apiKey: String,
       val model: String,
       val name: String?
   )

## 任务 A: 创建 ui/common/ProtocolSelector.kt (新文件)

创建独立的协议类型选择器组件。使用现有的 NexaraGlassCard、NexaraTypography、NexaraColors 主题组件。

组件签名：
@Composable
fun ProtocolSelector(
    selected: ProtocolType,
    onSelect: (ProtocolType) -> Unit,
    modifier: Modifier = Modifier
)

UI 要求：
- LazyColumn 展示所有 non-Local ProtocolType 选项
- 每行显示协议名称 + 端点路径描述
- 选中项用 Primary 颜色边框 + CheckCircle 图标高亮
- Material 3 暗色主题适配
- 所有 ProtocolType.entries 过滤掉 Local

## 任务 B: 修改 ui/settings/ProviderFormScreen.kt

### B1: 编辑模式回填 (修复 Bug#1)

在 ProviderFormScreen 函数体中（第 82-97 行区域），在状态初始化后添加编辑回填逻辑：

在第 88 行 `var name by remember...` 之前，添加获取 ViewModel：
```
val context = LocalContext.current
val app = context.applicationContext as NexaraApplication
val viewModel: SettingsViewModel = viewModel(
    factory = SettingsViewModel.factory(app)
)
```

在第 92 行（apiKeyVisible 之后）添加 LaunchedEffect：
```
LaunchedEffect(providerId) {
    if (providerId != null) {
        val config = viewModel.getProviderConfig(providerId)
        if (config != null) {
            name = config.name ?: ""
            baseUrl = config.baseUrl
            apiKey = config.apiKey
            val matched = PROVIDER_PRESETS.find { it.protocolType == config.protocolType }
            selectedPreset = matched ?: PROVIDER_PRESETS.last()
        }
    }
}
```

### B2: 动态标题 (修复 Bug#1 的 Header 问题)

修改第 98 行 NexaraPageLayout 的 title 参数：
```
title = when {
    isEditing && name.isNotBlank() -> name
    isEditing -> stringResource(R.string.provider_form_title_edit)
    else -> stringResource(R.string.provider_form_title_add)
}
```

### B3: 协议类型选择器 (新增功能)

在 PROVIDER_PRESETS 的 Column 循环下方（第 131 行之后，第 133 行的 Spacer 之前）新增以下代码：
```
if (selectedPreset.name == "Custom") {
    Spacer(modifier = Modifier.height(24.dp))
    Text("协议类型", style = NexaraTypography.headlineMedium, color = NexaraColors.OnSurface)
    Spacer(modifier = Modifier.height(12.dp))
    var localProto by remember { mutableStateOf(ProtocolType.Generic_OpenAI_Compat) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProtocolType.entries.filter { it !is ProtocolType.Local }.forEach { proto ->
            val selected = localProto == proto
            Box(
                Modifier.fillMaxWidth().clip(NexaraShapes.medium)
                    .background(if (selected) NexaraColors.Primary.copy(alpha = 0.1f) else NexaraColors.SurfaceContainer.copy(alpha = 0.3f))
                    .border(1.dp, if (selected) NexaraColors.Primary else NexaraColors.GlassBorder, NexaraShapes.medium)
                    .clickable { localProto = proto }.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(proto.displayName, style = NexaraTypography.bodyMedium,
                        color = if (selected) NexaraColors.Primary else NexaraColors.OnSurface,
                        modifier = Modifier.weight(1f))
                    Text(proto.defaultPath, style = NexaraTypography.labelMedium.copy(fontSize = 11.sp),
                        color = NexaraColors.OnSurfaceVariant)
                }
            }
        }
    }
}
```

### B4: 保存时使用正确的 protocolType

修改第 328 行 onSave 调用，将 `selectedPreset.protocolId` 替换为：
```
if (selectedPreset.name == "Custom") localProto else selectedPreset.protocolType
```

### B5: 保存后触发刷新

在第 328-334 行的 onSave clickable 块中，onSave 调用后添加：
```
viewModel.refreshProviders()
```

### B6: PROVIDER_PRESETS 更新

修改第 64-70 行 ProviderPreset 数据类，将 `protocolId: ProtocolId` 改为 `protocolType: ProtocolType`：
```
data class ProviderPreset(
    val name: String,
    val iconUrl: String,
    val defaultBaseUrl: String,
    val protocolType: ProtocolType,
    val fallbackIcon: ImageVector
)
```

修改第 72-78 行 PROVIDER_PRESETS 列表，将每个 preset 的 `ProtocolId.XXX` 改为 `ProtocolType.XXX`（将 OPENAI 映射为 OpenAI_ChatCompletions，ANTHROPIC 映射为 Anthropic_Messages，VERTEX_AI 映射为 Google_VertexAI）。

同时需要新增更多 preset 项以覆盖新增协议类型。

### B7: 导入更新

删除 `import com.promenar.nexara.data.remote.protocol.ProtocolId`
添加以下导入：
```
import com.promenar.nexara.NexaraApplication
import com.promenar.nexara.data.remote.protocol.ProtocolType
import com.promenar.nexara.data.model.ProviderListItem
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
```
```

---

### Phase 1B 提示词 — 多模态协议层升级

```
## 任务背景

你是 Android/Kotlin/Ktor 开发专家。为 Nexara 项目升级所有 LLM 协议实现，使其支持多模态输入（图片、音频、文档）。

Phase 0 已完成的基础工作（不要修改这些文件，但需要知道接口）：

1. `data/model/ChatModels.kt` 中 PromptRequest 新增多模态字段：
   data class PromptRequest(...,
       val images: List<ImageInput>? = null,
       val audio: List<AudioInput>? = null,
       val documents: List<DocumentInput>? = null
   )

2. `data/model/ChatModels.kt` 新增数据类（在文件末尾附近）：
   @Serializable data class ImageInput(val url: String? = null, val base64: String? = null, val mimeType: String = "image/png")
   @Serializable data class AudioInput(val base64: String, val mimeType: String = "audio/wav")
   @Serializable data class DocumentInput(val url: String? = null, val base64: String? = null, val name: String? = null, val mimeType: String = "application/pdf")

3. `data/remote/protocol/LlmProtocol.kt` 中 ProtocolMessage 新增字段：
   @Serializable data class ProtocolMessage(...,
       val imageUrls: List<ImageInput>? = null,
       val audioData: List<AudioInput>? = null,
       val documentData: List<DocumentInput>? = null
   )

4. ProtocolType sealed class 定义（9个子类型，含 displayName/defaultBaseUrl/defaultPath）

## 任务列表

### 任务 1: OpenAIProtocol.kt 多模态支持
文件：app/src/main/java/com/promenar/nexara/data/remote/protocol/OpenAIProtocol.kt

在消息构建逻辑中（寻找构建请求 JSON 的 messages 数组部分），将单文本 content 字段改为支持多模态 content 数组：

- 当 message.imageUrls 非空时，构建 content: [{type:"text", text:...}, {type:"image_url", image_url:{url:...}}]
- 纯文本时保持 `"content": "text"` 格式（向后兼容）
- 添加 modalities 字段（["text", "image"]）当有图片时

实现提示：
```
val hasMultimodal = message.imageUrls?.isNotEmpty() == true || message.audioData?.isNotEmpty() == true
if (!hasMultimodal) {
    put("content", message.content)
} else {
    val parts = JsonArray(buildList {
        add(buildJsonObject { put("type", "text"); put("text", message.content) })
        message.imageUrls?.forEach { img ->
            add(buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject {
                    put("url", img.url ?: "data:${img.mimeType};base64,${img.base64}")
                })
            })
        }
    })
    put("content", parts)
}
```

### 任务 2: AnthropicProtocol.kt 多模态支持
文件：app/src/main/java/com/promenar/nexara/data/remote/protocol/AnthropicProtocol.kt

修改 Anthropic 消息的 content 构建：

- Anthropic 格式：content 数组，image 块使用 {type:"image", source:{type:"base64", media_type:"...", data:"..."}}
- document 块使用 {type:"document", source:{type:"base64", media_type:"...", data:"..."}}
- 支持将提示中的图片和文档 attachment 转换为 Anthropic 格式

### 任务 3: VertexAIProtocol.kt 多模态支持
文件：app/src/main/java/com/promenar/nexara/data/remote/protocol/VertexAIProtocol.kt

在 Gemini 的 parts 数组中：

- 图片使用 inlineData: {mimeType, data}
- 音频使用 inlineData: {mimeType, data}
- 文档如果有 url，使用 fileData: {mimeType, fileUri}

### 任务 4: LocalProtocol.kt 多模态支持
文件：app/src/main/java/com/promenar/nexara/data/remote/protocol/LocalProtocol.kt

在格式化的 prompt 文本末尾追加图片标记：
```
message.imageUrls?.forEach { img ->
    append("\n[Image: ${img.mimeType}]")
    // LLaVA 等多模态本地模型通过特殊标记传递
}
```

### 任务 5: 创建 GenericOpenAICompatProtocol.kt (新文件)
路径：app/src/main/java/com/promenar/nexara/data/remote/protocol/GenericOpenAICompatProtocol.kt

创建适用于 Ollama/vLLM/LiteLLM/LocalAI 的通用 OpenAI 兼容协议。
- 复制 OpenAIProtocol 的核心逻辑（SSE 流式解析、多模态消息构建）
- 不硬编码 baseUrl
- ProtocolType 设为 Generic_OpenAI_Compat

### 任务 6: 创建 ProtocolFactory.kt (新文件)
路径：app/src/main/java/com/promenar/nexara/data/remote/protocol/ProtocolFactory.kt

创建协议实例工厂对象 ProtocolFactory，包含 create() 方法，根据 ProtocolType 返回对应的 LlmProtocol 实现：
- OpenAI_ChatCompletions / OpenAI_Responses → OpenAIProtocol
- Anthropic_Messages → AnthropicProtocol
- Google_VertexAI → VertexAIProtocol
- Cohere_Chat / Mistral_Chat → GenericOpenAICompatProtocol
- DeepSeek → OpenAIProtocol (DeepSeek 兼容 OpenAI)
- Generic_OpenAI_Compat → GenericOpenAICompatProtocol
- Local → LocalProtocol

### 关键约束

1. 所有修改必须向后兼容：images/audio/documents 为 null 或空时，行为与现有代码完全一致
2. 使用 kotlinx.serialization.json 构建 JSON
3. 保持与现有代码风格一致（4空格缩进）
4. 包路径遵循现有结构
```

---

### Phase 1C 提示词 — 模型选择器同步修复

```
## 任务背景

你是 Android/Kotlin/Jetpack Compose 开发专家。修复 Nexara 项目模型选择器跨界面不同步问题。

Phase 0 已完成的基础工作：

1. `data/manager/ProviderManager.kt` — 统一单例，暴露 StateFlow：
   - providerModels: StateFlow<List<ModelInfo>>
   - summaryModelId / imageModelId / embeddingModelId / rerankModelId: StateFlow<String>
   - fun refreshAll() / fun setPresetModel(type, modelId)

2. `ui/settings/SettingsViewModel.kt` 已重构为订阅 ProviderManager：
   - providerModels → 直接委托 ProviderManager.providerModels
   - setPresetModel() → 委托 ProviderManager.setPresetModel()
   - 新增 refreshProviderModels() → 调用 ProviderManager.refreshAll()

3. `data/model/ModelSpecs.kt` 中 ModelCapabilities 扩展为 12 字段：
   data class ModelCapabilities(
       val vision, internet, reasoning, image, embedding, rerank: Boolean,
       val audioInput, audioOutput, videoUnderstanding: Boolean,
       val structuredOutput, promptCaching, computerUse: Boolean
   )

4. `ui/common/ModelPicker.kt` 中 ModelCapability 枚举新增：
   AUDIO_INPUT, AUDIO_OUTPUT, VIDEO, STRUCTURED_OUTPUT, PROMPT_CACHING, COMPUTER_USE

## 任务列表

### 任务 1: 修复 UserSettingsHomeScreen.kt 模型选择器同步
文件：app/src/main/java/com/promenar/nexara/ui/hub/UserSettingsHomeScreen.kt

当前问题：从 ProviderModelsScreen 返回后，4 个预设模型选择器显示旧列表。

修复方案：
1. 在 4 个 ModelPicker 的 show 触发前（onClick 中），添加 viewModel.refreshProviderModels()
2. 将 modelItems 映射从 remember 改为 derivedStateOf，确保每次 recomposition 重新计算：
```
val modelItems by remember {
    derivedStateOf {
        viewModel.providerModels.value
            .filter { it.enabled }
            .map { model -> ModelItem(
                id = model.id, name = model.name.ifEmpty { model.id },
                providerName = model.providerName,
                capabilities = model.capabilities.mapNotNull { cap ->
                    try { ModelCapability.valueOf(cap.uppercase()) } catch (_: Exception) { null }
                },
                contextLength = model.contextLength
            )}
    }
}
```

### 任务 2: 更新 ModelPicker.kt 能力标签颜色
文件：app/src/main/java/com/promenar/nexara/ui/common/ModelPicker.kt

在私有 capabilityColors map（约第 64-72 行）中，为新增枚举值添加颜色：
```
ModelCapability.AUDIO_INPUT to (Color(0xFF38BDF8) to Color(0xFF0C2D48)),
ModelCapability.AUDIO_OUTPUT to (Color(0xFF818CF8) to Color(0xFF1E1B4B)),
ModelCapability.VIDEO to (Color(0xFFFB7185) to Color(0xFF4A0514)),
ModelCapability.STRUCTURED_OUTPUT to (Color(0xFF34D399) to Color(0xFF022C22)),
ModelCapability.PROMPT_CACHING to (Color(0xFFA3E635) to Color(0xFF1A2E05)),
ModelCapability.COMPUTER_USE to (Color(0xFFFBBF24) to Color(0xFF451A03))
```

同时在 filterTag 逻辑（第 98-106 行）中增加对新能力的过滤支持。

### 任务 3: 更新 ProviderModelsScreen.kt 的数据刷新
文件：app/src/main/java/com/promenar/nexara/ui/settings/ProviderModelsScreen.kt

在 toggleModel / addCustomModel / deleteModel 方法调用后添加：
```
viewModel.refreshProviderModels()
```

### 任务 4: 更新 NavGraph.kt ProviderForm 保存回调
文件：app/src/main/java/com/promenar/nexara/navigation/NavGraph.kt

在 ProviderFormScreen 的 onSave 回调（约第 363-365 行）中，确保保存后全局刷新：
```
onSave = { protocolType, baseUrl, apiKey, model, name ->
    app.updateProvider(protocolType, baseUrl, apiKey, model, name)
}
// 保存后 ProviderManager 通过 StateFlow 自动通知订阅者
```

关键：确保 ProviderModelsScreen 的 composable 路由（约第 369-378 行）在 LaunchedEffect 中调用 refreshProviderModels() 以加载最新数据。

### 关键约束
- 修改 UserSettingsHomeScreen.kt 时保持 UI 结构不变，只修改数据流
- ModelCapability 枚举新增值需与 ModelCapabilities 数据类字段字面一致
- 所有修改使用现有主题组件
```
```

---

## 四、执行检查清单

### Phase 0 完成标准
- [ ] `ProviderManager.kt` 编译通过，所有 StateFlow 正确暴露
- [ ] `ProviderModels.kt` 编译通过，所有数据类向后兼容
- [ ] `LlmProtocol.kt` 中 `ProtocolType` sealed class 含 9 个子类型
- [ ] `ChatModels.kt` 中 `PromptRequest` 支持 images/audio/documents 字段
- [ ] `ModelSpecs.kt` 中 `ModelCapabilities` 扩展为 12 字段，MODEL_SPECS 新增 50+ 条目
- [ ] `SettingsViewModel.kt` 实现 `getProviderConfig()` 并订阅 ProviderManager
- [ ] `NexaraApplication.kt` 中 `updateProvider()` 委托 ProviderManager
- [ ] 项目编译通过（`./gradlew assembleDebug`）

### Phase 1A 完成标准
- [ ] 进入已有提供商编辑页时，name/baseUrl/apiKey 正确回填
- [ ] 页面标题显示提供商名称（非硬编码 "Provider"）
- [ ] Custom preset 下显示协议类型选择器
- [ ] 保存时传递正确的 protocolType

### Phase 1B 完成标准
- [ ] OpenAIProtocol 支持 `image_url` 多模态内容块
- [ ] AnthropicProtocol 支持 `image`/`document` 多模态块
- [ ] VertexAIProtocol 补全 `fileData`/`videoMetadata`
- [ ] GenericOpenAICompatProtocol 可处理 Ollama/vLLM
- [ ] ProtocolFactory 根据 ProtocolType 创建正确协议实例

### Phase 1C 完成标准
- [ ] ProviderModelsScreen 配置模型后，返回设置页，预设模型选择器立即显示新列表
- [ ] ModelPicker 显示所有 12 种能力标签（含新增颜色）
- [ ] NavGraph 保存回调触发全局刷新

### Phase 2 完成标准
- [ ] 全局无残留 `ProtocolId` 引用（可保留向后兼容别名）
- [ ] ProviderManager StateFlow 订阅链完整
- [ ] 用户全路径模拟通过
- [ ] DIA 文档更新完成

---

## 五、风险与注意事项

1. **Phase 0 可能超过单次 Flash 会话的最佳上下文区间**。建议将 Phase 0 拆为两个会话：
   - Phase 0A: 纯数据模型（ProviderModels.kt + LlmProtocol.kt + ChatModels.kt）
   - Phase 0B: 基础设施 + 数据库（ProviderManager.kt + ModelSpecs.kt + SettingsViewModel.kt + NexaraApplication.kt）

2. **ModelSpecs.kt 条目匹配顺序极为关键**：`gpt-4o` 必须在 `gpt-4` 之前，`gemini-2.5-pro` 在 `gemini-2.5` 之前，否则泛模式会错误匹配。Phase 0 必须严格遵守精确→模糊的排列顺序。

3. **向后兼容**：所有数据类新增字段必须有默认值。Phase 1A/1B/1C 的代码不能破坏现有纯文本聊天流程。

4. **SharedPreferences 迁移**：旧 `protocol_id` 键存储的是 `ProtocolId` 枚举名（如 "OPENAI"），新系统需要兼容。ProviderManager 的加载逻辑必须处理 `try { ProtocolId.valueOf(legacy) } → ProtocolType` 的映射。

5. **Flash 上下文容量**：每个 Phase 提示词已优化至 3K-6K tokens。加上代码文件读取（每个文件 100-500 行），总上下文应在 15K-30K tokens 范围内，适合 Flash 模型。
