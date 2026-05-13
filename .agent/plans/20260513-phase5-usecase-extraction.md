# Phase 5 — UseCase 层抽取

> **前置**: Phase 4 完成（架构债全部消除，ViewModels 全部使用 Repository）
> **创建日期**: 2026-05-13
> **目标**: 将 ViewModel 中残留的业务逻辑抽取为纯 Kotlin UseCase

---

## 1. 当前现状

8 个 ViewModel 已全部使用 Repository 接口，但仍存在内联业务逻辑：

| 问题 | 影响范围 |
|------|---------|
| ID 生成散落 | 7 个 VM 各自 `System.currentTimeMillis()` / `UUID.randomUUID()` |
| Config 三级 fallback 内联 | ChatViewModel、AgentEditViewModel 各有 ~100 行回退链 |
| Config 序列化重复 | RagViewModel 和 AgentEditViewModel 各有 ~30 个 SharedPreferences 键读写 |
| 多步骤编排内联 | RagViewModel.deleteDocuments() 先删 document 再删 vector |

---

## 2. 并行会话拆分

```
Session P（先执行: IdGenerator，基础依赖）

    ├── Session Q（并行）AgentConfigResolver + CreateAgentUseCase
    └── Session R（并行）DeleteDocumentUseCase + RagConfigPersistence
```

### 文件冲突矩阵

| 文件 | P | Q | R |
|------|---|---|---|
| `domain/usecase/IdGenerator.kt` | ✅ 新建 | — | — |
| 7 个 VM（ID 生成行） | ✅ 修改 | — | — |
| `domain/usecase/AgentConfigResolver.kt` | — | ✅ 新建 | — |
| `domain/usecase/CreateAgentUseCase.kt` | — | ✅ 新建 | — |
| `ChatViewModel.kt` | ✅ P 改 ID | ✅ 改 config | — |
| `AgentHubViewModel.kt` | ✅ P 改 ID | ✅ 改 create | — |
| `AgentEditViewModel.kt` | ✅ P 改 ID | ✅ 改 config | — |
| `domain/usecase/DeleteDocumentUseCase.kt` | — | — | ✅ 新建 |
| `domain/usecase/RagConfigPersistence.kt` | — | — | ✅ 新建 |
| `RagViewModel.kt` | ✅ P 改 ID | — | ✅ 改 delete+config |

**Q 和 R 之间零文件冲突，可并行。** P 先执行是因为 Q/R 依赖 P 的 IdGenerator。

---

## 3. Session P — IdGenerator

### Session P 提示词

```
你需要在 Nexara 中创建统一的 ID 生成器并替换所有散落的 ID 生成逻辑。

项目根目录: /Users/promenar/Codex/Nexara/native-ui

## Step 1: 创建 IdGenerator

新建 `domain/usecase/IdGenerator.kt`:

```kotlin
package com.promenar.nexara.domain.usecase

import java.util.UUID

/**
 * 统一 ID 生成器。
 * 所有实体 ID 必须通过此类生成，禁止在 ViewModel 中直接使用 System.currentTimeMillis() 或 UUID。
 */
object IdGenerator {
    fun agent(): String = "agent_${System.currentTimeMillis()}"
    fun session(): String = "session_${System.currentTimeMillis()}"
    fun message(prefix: String = "msg"): String = "${prefix}_${System.currentTimeMillis()}"
    fun document(): String = "doc_${System.currentTimeMillis()}"
    fun folder(): String = "folder_${System.currentTimeMillis()}"
    fun uuid(): String = UUID.randomUUID().toString()
    fun skill(): String = "skill_${System.currentTimeMillis()}"
}
```

## Step 2: 替换所有 ViewModel 中的 ID 生成

在以下文件中搜索 `System.currentTimeMillis()` 和 `UUID.randomUUID()` 用于 ID 生成的地方，替换为 IdGenerator 调用：

| ViewModel | 旧模式 | 替换为 |
|-----------|--------|--------|
| `AgentHubViewModel.kt` | `"agent_${System.currentTimeMillis()}"` | `IdGenerator.agent()` |
| `ChatViewModel.kt` | `"msg_${System.currentTimeMillis()}_user"` / `_ai` | `IdGenerator.message("user")` / `IdGenerator.message("ai")` |
| `ChatViewModel.kt` | `"session_${System.currentTimeMillis()}"` | `IdGenerator.session()` |
| `SessionListViewModel.kt` | `"session_${System.currentTimeMillis()}"` | `IdGenerator.session()` |
| `RagViewModel.kt` | `UUID.randomUUID().toString()` (folder) | `IdGenerator.uuid()` |
| `RagViewModel.kt` | 其他 ID 生成 | 对应 IdGenerator 方法 |
| `KnowledgeGraphViewModel.kt` | `UUID.randomUUID().toString()` | `IdGenerator.uuid()` |
| `SettingsViewModel.kt` | `"mcp_${System.currentTimeMillis()}"` / `"user_${System.currentTimeMillis()}"` | `IdGenerator.skill()` / `IdGenerator.uuid()` |

**关键**: 只替换 ID 生成行，不要修改其他业务逻辑。

## Step 3: 编写测试

新建 `test/.../domain/usecase/IdGeneratorTest.kt`:

```kotlin
package com.promenar.nexara.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IdGeneratorTest {
    @Test fun `agent starts with agent_`() { assertThat(IdGenerator.agent()).startsWith("agent_") }
    @Test fun `session starts with session_`() { assertThat(IdGenerator.session()).startsWith("session_") }
    @Test fun `message starts with prefix`() { assertThat(IdGenerator.message("user")).startsWith("user_") }
    @Test fun `document starts with doc_`() { assertThat(IdGenerator.document()).startsWith("doc_") }
    @Test fun `folder starts with folder_`() { assertThat(IdGenerator.folder()).startsWith("folder_") }
    @Test fun `uuid is 36 chars`() { assertThat(IdGenerator.uuid()).hasLength(36) }
    @Test fun `ids are unique`() {
        val ids = (1..100).map { IdGenerator.agent() }.distinct()
        assertThat(ids).hasSize(100)
    }
}
```

## 执行要求
- 编译验证 + 全部测试通过
- 确认 ViewModel 中不再有内联的 `System.currentTimeMillis()` 用于 ID 生成

## 禁止事项
- 不修改业务逻辑
- 不创建其他 UseCase（Session Q/R 负责）
```

---

## 4. Session Q — AgentConfigResolver + CreateAgentUseCase

### Session Q 提示词

```
你需要创建 AgentConfigResolver 和 CreateAgentUseCase 两个 UseCase。

项目根目录: /Users/promenar/Codex/Nexara/native-ui
前置: Session P 已完成（IdGenerator 可用）

## 任务 1: AgentConfigResolver

新建 `domain/usecase/AgentConfigResolver.kt`:

```kotlin
package com.promenar.nexara.domain.usecase

import android.content.SharedPreferences
import com.promenar.nexara.data.agent.AgentRagConfig
import com.promenar.nexara.data.agent.AgentRetrievalConfig
import com.promenar.nexara.domain.model.Agent

/**
 * Agent → Session → Global 三级配置 fallback 解析器。
 * 消除 ChatViewModel 和 AgentEditViewModel 中重复的配置回退逻辑。
 */
class AgentConfigResolver(
    private val globalPrefs: SharedPreferences
) {
    data class ResolvedConfig(
        val systemPrompt: String,
        val modelId: String,
        val temperature: Double,
        val topP: Double,
        val maxTokens: Int,
        val ragConfig: AgentRagConfig?,
        val retrievalConfig: AgentRetrievalConfig?
    )

    /**
     * 从 Agent 解析有效配置，缺失字段回退到全局 SharedPreferences。
     */
    fun resolve(agent: Agent?): ResolvedConfig {
        return ResolvedConfig(
            systemPrompt = agent?.systemPrompt ?: "",
            modelId = agent?.modelId ?: globalPrefs.getString("default_model", "") ?: "",
            temperature = agent?.temperature ?: globalPrefs.getFloat("default_temperature", 0.7f).toDouble(),
            topP = agent?.topP ?: globalPrefs.getFloat("default_top_p", 0.9f).toDouble(),
            maxTokens = agent?.maxTokens ?: globalPrefs.getInt("default_max_tokens", 4096),
            ragConfig = agent?.ragConfig,
            retrievalConfig = agent?.retrievalConfig
        )
    }

    /**
     * 获取 Agent 名称（用于 UI 显示）。
     */
    fun resolveName(agent: Agent?): String = agent?.name ?: ""
}
```

### 在 ChatViewModel 中使用

先读取 ChatViewModel.kt，找到以下位置替换：

**generateMessage()** (约 247-307 行):
```kotlin
// 旧代码：手动 agentDao.getById() → 读取各个字段做 fallback
val agent = agentRepository.getById(sessionForCtx.agentId)
val config = AgentConfigResolver(prefs).resolve(agent)
val systemPrompt = config.systemPrompt
// effectiveModel = config.modelId
// temperature = config.temperature
// ...
```

**updateAgentName()** (约 587 行):
```kotlin
val agent = agentRepository.getById(agentId)
_agentName.value = AgentConfigResolver(prefs).resolveName(agent)
```

构造函数添加 `configResolver: AgentConfigResolver` 参数（或直接注入 SharedPreferences 并在内部创建）。

### 任务 2: CreateAgentUseCase

新建 `domain/usecase/CreateAgentUseCase.kt`:

```kotlin
package com.promenar.nexara.domain.usecase

import com.promenar.nexara.domain.model.Agent
import com.promenar.nexara.domain.model.ExecutionMode
import com.promenar.nexara.domain.repository.IAgentRepository

class CreateAgentUseCase(
    private val agentRepository: IAgentRepository
) {
    suspend operator fun invoke(
        name: String,
        description: String,
        modelId: String,
        systemPrompt: String,
        icon: String = "✨",
        color: String = "#C0C1FF"
    ): Agent {
        require(name.isNotBlank()) { "Agent name cannot be blank" }
        val agent = Agent(
            id = IdGenerator.agent(),
            name = name.trim(),
            description = description,
            systemPrompt = systemPrompt,
            modelId = modelId,
            icon = icon,
            color = color,
            executionMode = ExecutionMode.SEMI,
            createdAt = System.currentTimeMillis()
        )
        agentRepository.create(agent)
        return agent
    }
}
```

### 在 AgentHubViewModel 中使用

将 `createAgent()` 方法改为:
```kotlin
fun createAgent(name: String, description: String, model: String, systemPrompt: String) {
    viewModelScope.launch {
        createAgentUseCase(name, description, model, systemPrompt)
    }
}
```

移除原有的 Entity 构建/ID 生成逻辑。

### 测试

新建 `test/.../domain/usecase/AgentConfigResolverTest.kt` 和 `CreateAgentUseCaseTest.kt`:
- 测试三级 fallback 链路
- 测试空名称校验

## 执行要求
- 先读取 ChatViewModel / AgentHubViewModel / AgentEditViewModel 完整代码
- 每步编译 + 测试验证
- 仅修改 ChatViewModel + AgentHubViewModel + AgentEditViewModel

## 禁止事项
- 不修改 RagViewModel（Session R 负责）
```

---

## 5. Session R — DeleteDocumentUseCase + RagConfigPersistence

### Session R 提示词

```
你需要创建 DeleteDocumentUseCase 和 RagConfigPersistence。

项目根目录: /Users/promenar/Codex/Nexara/native-ui
前置: Session P 已完成

## 任务 1: DeleteDocumentUseCase

新建 `domain/usecase/DeleteDocumentUseCase.kt`:

```kotlin
package com.promenar.nexara.domain.usecase

import com.promenar.nexara.domain.repository.IDocumentRepository
import com.promenar.nexara.domain.repository.IVectorRepository

/**
 * 文档+向量联动删除。
 * 从 RagViewModel.deleteDocuments() 中抽取编排逻辑。
 */
class DeleteDocumentUseCase(
    private val documentRepository: IDocumentRepository,
    private val vectorRepository: IVectorRepository
) {
    suspend operator fun invoke(documentIds: List<String>) {
        for (docId in documentIds) {
            vectorRepository.deleteByDocument(docId)
            documentRepository.delete(docId)
        }
    }
}
```

### 在 RagViewModel 中使用

找到 `deleteDocuments()` 方法，将其中:
```kotlin
for (docId in docIds) {
    vectorDao.deleteByDocId(docId)  // 或 vectorRepository.deleteByDocument(docId)
    documentRepository.delete(docId)
}
```
替换为:
```kotlin
deleteDocumentUseCase(docIds)
```

## 任务 2: RagConfigPersistence

新建 `domain/usecase/RagConfigPersistence.kt`:

```kotlin
package com.promenar.nexara.domain.usecase

import android.content.SharedPreferences
import com.promenar.nexara.data.agent.AgentRagConfig
import com.promenar.nexara.data.agent.AgentRetrievalConfig

/**
 * RAG 配置的 SharedPreferences 持久化。
 * 消除 RagViewModel 和 AgentEditViewModel 中重复的 ~30 个 SharedPreferences 键读写。
 */
class RagConfigPersistence(
    private val prefs: SharedPreferences
) {
    fun loadRagConfig(): AgentRagConfig { ... }
    fun saveRagConfig(config: AgentRagConfig) { ... }
    fun loadRetrievalConfig(): AgentRetrievalConfig { ... }
    fun saveRetrievalConfig(config: AgentRetrievalConfig) { ... }

    companion object {
        const val KEY_CHUNK_SIZE = "rag_chunk_size"
        const val KEY_CHUNK_OVERLAP = "rag_chunk_overlap"
        // ... 列出所有 30 个键名常量
    }
}
```

**关键**: 先读取 RagViewModel.loadConfig() / saveConfig() 和 AgentEditViewModel.getGlobalRagConfig() / getGlobalRetrievalConfig()，提取所有 SharedPreferences 键和默认值到此类中。

### 在 RagViewModel 和 AgentEditViewModel 中使用

- 构造函数添加 `ragConfigPersistence: RagConfigPersistence` 参数
- `loadConfig()` → `ragConfigPersistence.loadRagConfig()`
- `saveConfig()` → `ragConfigPersistence.saveRagConfig()`
- 移除 ViewModel 中的内联 SharedPreferences 读写代码

### 测试

新建 `test/.../domain/usecase/DeleteDocumentUseCaseTest.kt` 和 `RagConfigPersistenceTest.kt`

## 执行要求
- 先读取 RagViewModel 和 AgentEditViewModel 完整代码
- 每步编译 + 测试验证
- 仅修改 RagViewModel（deleteDocuments + config 方法）
- AgentEditViewModel 的全局 config 加载改用 RagConfigPersistence

## 禁止事项
- 不修改 ChatViewModel / AgentHubViewModel（Session Q 负责）
```

---

## 6. 验证清单

- [ ] `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- [ ] `./gradlew :app:testDebugUnitTest` 无新增失败
- [ ] 所有 ID 生成使用 IdGenerator（7 个 VM 零内联 `System.currentTimeMillis()` for ID）
- [ ] ChatViewModel / AgentHubViewModel / AgentEditViewModel 使用对应 UseCase
- [ ] RagViewModel deleteDocuments 使用 DeleteDocumentUseCase
- [ ] RAG config 序列化统一使用 RagConfigPersistence

---

**文档维护者**: AI Assistant
**最后更新**: 2026-05-13
