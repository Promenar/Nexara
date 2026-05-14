# Domain 层 + Repository 层系统性实施方案

> **基准文档**: `docs/ARCHITECTURE_DESIGN.md` §2.1-2.2
> **创建日期**: 2026-05-13
> **总预估工时**: ~4 人天（4 个并行会话）
> **原则**: 依据架构设计文档自上而下建立，不基于现有代码修修补补

---

## 1. 设计起点：架构文档中的目标

### 1.1 目标包结构

```
com.promenar.nexara/
├── domain/                          ← 新建（纯 Kotlin，零 Android 依赖）
│   ├── model/
│   │   ├── Agent.kt                 # Agent 聚合根
│   │   ├── Session.kt               # Session 聚合根
│   │   ├── Message.kt              # Message 聚合根
│   │   ├── Document.kt             # Document 聚合根
│   │   ├── Enums.kt                # MessageRole, ExecutionMode 等
│   │   └── ValueObjects.kt         # TokenUsage, RagReference, ToolCall 等
│   └── repository/
│       ├── IAgentRepository.kt
│       ├── ISessionRepository.kt
│       ├── IMessageRepository.kt
│       ├── IDocumentRepository.kt
│       ├── IVectorRepository.kt
│       ├── IKnowledgeGraphRepository.kt
│       └── IProviderRepository.kt
│
├── data/
│   ├── repository/                  ← 部分新建，部分已有
│   │   ├── AgentRepository.kt       # NEW — 实现 IAgentRepository
│   │   ├── DocumentRepository.kt    # NEW — 实现 IDocumentRepository
│   │   ├── VectorRepository.kt      # NEW — 实现 IVectorRepository
│   │   ├── KnowledgeGraphRepository.kt  # NEW — 实现 IKnowledgeGraphRepository
│   │   ├── ProviderRepository.kt    # NEW — 实现 IProviderRepository
│   │   ├── SessionRepository.kt     # 已有 — 对齐 ISessionRepository
│   │   └── MessageRepository.kt     # 已有 — 对齐 IMessageRepository
│   └── mapper/                      ← 新建
│       ├── AgentMapper.kt           # AgentEntity ↔ Agent
│       ├── DocumentMapper.kt        # DocumentEntity ↔ Document
│       ├── VectorMapper.kt          # VectorEntity ↔ SearchResult
│       └── KgMapper.kt              # KgNodeEntity/KgEdgeEntity ↔ KgNode/KgEdge
│   │
│   └── local/db/...                 ← 现有 Room 层（不动）
```

### 1.2 关键设计决策

1. **Domain 模型与现有 `data.model.*` 共存过渡**：新建 `domain.model.*` 作为规范定义，现有的 `data.model.ChatModels.kt` 在 ViewModel 迁移完成前不动。Repository 的 Mapper 同时处理新旧两套类型。
2. **接口定义在 Domain 层，实现在 Data 层**：严格遵循依赖倒置。
3. **不新建 Gradle 模块**：先用 package 隔离，待 CMP 迁移时再拆模块。

---

## 2. 并行会话拆分

```
Session A (基础层)          ← 必须先完成
    │
    ├── Session B (Agent + Document)  ← 可并行
    ├── Session C (Vector + KG)       ← 可并行
    └── Session D (Provider + 对齐)   ← 可并行
```

**并行策略**：Session B / C / D 相互之间零文件冲突（各自创建/修改不同的文件）。

### 文件冲突矩阵

| 文件 | A | B | C | D |
|------|---|---|---|---|
| `domain/model/*.kt` (6 个新文件) | ✅ 创建 | — | — | — |
| `domain/repository/*.kt` (7 个新文件) | ✅ 创建 | — | — | — |
| `data/repository/AgentRepository.kt` | — | ✅ 创建 | — | — |
| `data/repository/DocumentRepository.kt` | — | ✅ 创建 | — | — |
| `data/mapper/AgentMapper.kt` | — | ✅ 创建 | — | — |
| `data/mapper/DocumentMapper.kt` | — | ✅ 创建 | — | — |
| `data/repository/VectorRepository.kt` | — | — | ✅ 创建 | — |
| `data/repository/KnowledgeGraphRepository.kt` | — | — | ✅ 创建 | — |
| `data/mapper/VectorMapper.kt` | — | — | ✅ 创建 | — |
| `data/mapper/KgMapper.kt` | — | — | ✅ 创建 | — |
| `data/repository/ProviderRepository.kt` | — | — | — | ✅ 创建 |
| `data/repository/SessionRepository.kt` | — | — | — | ✅ 修改 |
| `data/repository/MessageRepository.kt` | — | — | — | ✅ 修改 |

**零冲突，确认可并行。**

---

## 3. Session A — Domain 基础层（先执行）

### 任务范围

创建 `domain/` 包下的所有 Domain 模型、值对象、枚举、Repository 接口。

**不实现任何具体逻辑，不操作数据库，不依赖 Android 框架。**

### 交付物

| 文件 | 内容 |
|------|------|
| `domain/model/Agent.kt` | Agent 数据类（14 字段，见架构文档 §2.1） |
| `domain/model/Session.kt` | Session 数据类（7 字段） |
| `domain/model/Message.kt` | Message 数据类（8 字段） |
| `domain/model/Document.kt` | Document 数据类（9 字段） |
| `domain/model/Enums.kt` | MessageRole, ExecutionMode, ProtocolType, ModelType, ModelCapability |
| `domain/model/ValueObjects.kt` | TokenUsage, RagReference, ToolCall, ToolCallStatus, SearchFilters, SearchResult, VectorChunk |
| `domain/repository/IAgentRepository.kt` | Agent 仓库接口（5 方法） |
| `domain/repository/ISessionRepository.kt` | Session 仓库接口（5 方法） |
| `domain/repository/IMessageRepository.kt` | Message 仓库接口（4 方法） |
| `domain/repository/IDocumentRepository.kt` | Document 仓库接口（5 方法） |
| `domain/repository/IVectorRepository.kt` | Vector 仓库接口（3 方法） |
| `domain/repository/IKnowledgeGraphRepository.kt` | KG 仓库接口（3 方法） |
| `domain/repository/IProviderRepository.kt` | Provider 仓库接口（3 方法） |

### 关键约束

- 所有文件在 `com.promenar.nexara.domain.*` 包下
- 零 `import android.*`
- 零 `import androidx.*`
- Domain 模型字段优先用 `val`（不可变）
- Repository 接口中 `observe*` 方法返回 `kotlinx.coroutines.flow.Flow`
- `suspend` 方法用于一次性操作

---

## 4. Session B — Agent + Document Repository 实现

### 任务范围

实现 `IAgentRepository` 和 `IDocumentRepository`，创建对应的 Mapper。

### 交付物

| 文件 | 内容 |
|------|------|
| `data/repository/AgentRepository.kt` | 实现 IAgentRepository，注入 AgentDao |
| `data/repository/DocumentRepository.kt` | 实现 IDocumentRepository，注入 DocumentDao + FolderDao |
| `data/mapper/AgentMapper.kt` | AgentEntity ↔ domain.Agent 双向映射 |
| `data/mapper/DocumentMapper.kt` | DocumentEntity ↔ domain.Document 双向映射 |

---

## 5. Session C — Vector + KnowledgeGraph Repository 实现

### 任务范围

实现 `IVectorRepository` 和 `IKnowledgeGraphRepository`，创建对应的 Mapper。

### 交付物

| 文件 | 内容 |
|------|------|
| `data/repository/VectorRepository.kt` | 实现 IVectorRepository，注入 VectorDao + VectorFtsDao + EmbeddingClient |
| `data/repository/KnowledgeGraphRepository.kt` | 实现 IKnowledgeGraphRepository，注入 KgNodeDao + KgEdgeDao + GraphExtractor |
| `data/mapper/VectorMapper.kt` | VectorEntity ↔ SearchResult 映射 |
| `data/mapper/KgMapper.kt` | KgNodeEntity ↔ KgNode, KgEdgeEntity ↔ KgEdge 映射 |

---

## 6. Session D — Provider Repository + 现有 Repository 对齐

### 任务范围

实现 `IProviderRepository`（收编 ProviderManager 单例），将现有 SessionRepository / MessageRepository 对齐架构文档中的接口定义。

### 交付物

| 文件 | 操作 | 内容 |
|------|------|------|
| `data/repository/ProviderRepository.kt` | 新建 | 实现 IProviderRepository，替代 ProviderManager 单例 |
| `data/repository/SessionRepository.kt` | 修改 | 实现 ISessionRepository 接口，对齐方法签名 |
| `data/repository/MessageRepository.kt` | 修改 | 实现 IMessageRepository 接口，对齐方法签名 |

---

## 7. 每个会话的独立提示词

### Session A 提示词

```
你需要在 Nexara 原生 Kotlin 项目中建立 Domain 层基础。

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/

## 任务

创建 `domain/` 包，包含所有 Domain 模型和 Repository 接口。
所有文件放在 `com.promenar.nexara.domain.model` 和 `com.promenar.nexara.domain.repository` 包下。

## 关键约束

1. **零 Android 依赖**：所有文件中不允许出现 `import android.*` 或 `import androidx.*`
2. **纯 Kotlin**：只使用 Kotlin 标准库 + `kotlinx.coroutines.flow.Flow`
3. **数据类使用 val**：Domain 模型字段全部不可变
4. **接口命名**：Repository 接口以 `I` 前缀命名（如 `IAgentRepository`）

## 需要创建的文件

### domain/model/Enums.kt
```kotlin
package com.promenar.nexara.domain.model

enum class MessageRole { USER, ASSISTANT, SYSTEM, TOOL }
enum class ExecutionMode { AUTO, SEMI, MANUAL }
enum class ProtocolType { OPENAI, ANTHROPIC, VERTEX_AI }
enum class ModelType { CHAT, REASONING, IMAGE, EMBEDDING, RERANK }
enum class ModelCapability { CHAT, REASONING, VISION, WEB_SEARCH, EMBEDDING, RERANK, IMAGE_GEN, CODE, FUNCTION_CALLING, TOOL_USE, JSON_MODE, STREAMING }
enum class ToolCallStatus { PENDING, RUNNING, COMPLETED, FAILED }
```

### domain/model/ValueObjects.kt
```kotlin
package com.promenar.nexara.domain.model

data class TokenUsage(val input: Int, val output: Int, val estimated: Boolean = false)
data class RagReference(val chunkId: String, val documentTitle: String, val snippet: String, val score: Double)
data class ToolCall(val id: String, val name: String, val arguments: String, val result: String?, val status: ToolCallStatus)

data class SearchFilters(
    val documentIds: List<String>? = null,
    val sessionId: String? = null,
    val minScore: Double = 0.0
)

data class SearchResult(
    val chunkId: String,
    val documentId: String?,
    val documentTitle: String,
    val chunkText: String,
    val score: Double,
    val source: SearchSource
)

enum class SearchSource { VECTOR, FTS, HYBRID }

data class VectorChunk(
    val index: Int,
    val text: String,
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorChunk) return false
        return index == other.index && text == other.text && (embedding?.contentEquals(other.embedding) ?: (other.embedding == null))
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + text.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}

data class ProviderConfig(
    val id: String,
    val name: String,
    val protocolType: ProtocolType,
    val baseUrl: String,
    val apiKey: String,
    val defaultModel: String,
    val isEnabled: Boolean = true
)

data class ConnectionResult(val success: Boolean, val latencyMs: Long?, val error: String?)

data class ModelSpec(
    val id: String,
    val name: String,
    val type: ModelType,
    val capabilities: List<ModelCapability>,
    val providerId: String
)

// 知识图谱 Domain 模型
data class KgNode(
    val id: String,
    val label: String,
    val type: String,
    val properties: Map<String, String> = emptyMap()
)

data class KgEdge(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val relation: String,
    val weight: Double = 1.0
)

data class KgTriple(val subject: String, val predicate: String, val `object`: String)

data class ExtractionResult(val nodes: List<KgNode>, val edges: List<KgEdge>)
```

### domain/model/Agent.kt
```kotlin
package com.promenar.nexara.domain.model

// 注意：AgentRagConfig 和 AgentRetrievalConfig 引用现有 data.agent 包中的定义
// 暂不创建 domain 版本，待后续 UseCase 阶段统一迁移
import com.promenar.nexara.data.agent.AgentRagConfig
import com.promenar.nexara.data.agent.AgentRetrievalConfig

data class Agent(
    val id: String,
    val name: String,
    val description: String = "",
    val systemPrompt: String = "",
    val modelId: String = "",
    val icon: String = "✨",
    val color: String = "#C0C1FF",
    val avatarPath: String? = null,
    val isPinned: Boolean = false,
    val temperature: Double? = 0.7,
    val topP: Double? = 0.9,
    val maxTokens: Int? = 4096,
    val ragConfig: AgentRagConfig? = null,
    val retrievalConfig: AgentRetrievalConfig? = null,
    val useInheritedConfig: Boolean = true,
    val executionMode: ExecutionMode = ExecutionMode.SEMI,
    val skills: List<String> = emptyList(),
    val createdAt: Long = 0L
)
```

### domain/model/Session.kt
```kotlin
package com.promenar.nexara.domain.model

data class Session(
    val id: String,
    val agentId: String,
    val title: String,
    val modelId: String,
    val isPinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val messageCount: Int = 0
)
```

### domain/model/Message.kt
```kotlin
package com.promenar.nexara.domain.model

data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val thinking: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val ragReferences: List<RagReference>? = null,
    val tokenUsage: TokenUsage? = null,
    val timestamp: Long = 0L
)
```

### domain/model/Document.kt
```kotlin
package com.promenar.nexara.domain.model

data class Document(
    val id: String,
    val folderId: String,
    val title: String,
    val content: String,
    val summary: String? = null,
    val hash: String = "",
    val chunkSize: Int = 500,
    val chunkOverlap: Int = 50,
    val vectorizedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
```

### domain/repository/IAgentRepository.kt
```kotlin
package com.promenar.nexara.domain.repository

import com.promenar.nexara.domain.model.Agent
import kotlinx.coroutines.flow.Flow

interface IAgentRepository {
    fun observeAll(): Flow<List<Agent>>
    fun observeById(id: String): Flow<Agent?>
    suspend fun create(agent: Agent)
    suspend fun update(agent: Agent)
    suspend fun delete(id: String)
}
```

### domain/repository/ISessionRepository.kt
```kotlin
package com.promenar.nexara.domain.repository

import com.promenar.nexara.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface ISessionRepository {
    fun observeByAgent(agentId: String): Flow<List<Session>>
    fun observeById(id: String): Flow<Session?>
    suspend fun create(agentId: String, modelId: String): Session
    suspend fun updateTitle(id: String, title: String)
    suspend fun delete(id: String)
}
```

### domain/repository/IMessageRepository.kt
```kotlin
package com.promenar.nexara.domain.repository

import com.promenar.nexara.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface IMessageRepository {
    fun observeBySession(sessionId: String): Flow<List<Message>>
    suspend fun send(sessionId: String, content: String, role: com.promenar.nexara.domain.model.MessageRole): Message
    suspend fun appendContent(messageId: String, chunk: String)
    suspend fun delete(id: String)
}
```

### domain/repository/IDocumentRepository.kt
```kotlin
package com.promenar.nexara.domain.repository

import com.promenar.nexara.domain.model.Document
import kotlinx.coroutines.flow.Flow

interface IDocumentRepository {
    fun observeByFolder(folderId: String): Flow<List<Document>>
    suspend fun import(path: String, folderId: String): Document
    suspend fun update(id: String, content: String)
    suspend fun delete(id: String)
    suspend fun markVectorized(id: String)
}
```

### domain/repository/IVectorRepository.kt
```kotlin
package com.promenar.nexara.domain.repository

import com.promenar.nexara.domain.model.SearchFilters
import com.promenar.nexara.domain.model.SearchResult
import com.promenar.nexara.domain.model.VectorChunk

interface IVectorRepository {
    suspend fun search(query: String, topK: Int, filters: SearchFilters): List<SearchResult>
    suspend fun index(documentId: String, chunks: List<VectorChunk>)
    suspend fun deleteByDocument(documentId: String)
}
```

### domain/repository/IKnowledgeGraphRepository.kt
```kotlin
package com.promenar.nexara.domain.repository

import com.promenar.nexara.domain.model.ExtractionResult
import com.promenar.nexara.domain.model.KgNode
import com.promenar.nexara.domain.model.KgEdge

interface IKnowledgeGraphRepository {
    suspend fun extractFromDocument(documentId: String): ExtractionResult
    suspend fun getAllNodes(): List<KgNode>
    suspend fun getAllEdges(): List<KgEdge>
    suspend fun clear()
}
```

### domain/repository/IProviderRepository.kt
```kotlin
package com.promenar.nexara.domain.repository

import com.promenar.nexara.domain.model.ConnectionResult
import com.promenar.nexara.domain.model.ModelSpec
import com.promenar.nexara.domain.model.ProviderConfig
import kotlinx.coroutines.flow.Flow

interface IProviderRepository {
    fun observeAll(): Flow<List<ProviderConfig>>
    suspend fun testConnection(providerId: String): ConnectionResult
    suspend fun fetchModels(providerId: String): List<ModelSpec>
    suspend fun save(config: ProviderConfig)
    suspend fun delete(id: String)
}
```

## 执行要求

1. 在 `native-ui/app/src/main/java/com/promenar/nexara/domain/` 下创建上述所有目录和文件
2. 确保编译通过：`cd native-ui && ./gradlew :app:compileDebugKotlin`
3. 如果编译失败，修复所有错误后重新验证
4. 完成后报告创建的文件列表和编译结果

## 禁止事项
- 不要修改 `domain/` 包以外的任何现有文件
- 不要在 Domain 层引入 Android 框架依赖
- 不要实现任何 Repository 方法（只定义接口）
```

---

### Session B 提示词

```
你需要在 Nexara 原生 Kotlin 项目中实现 AgentRepository 和 DocumentRepository。

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/

## 前置条件
Session A 已完成，`domain/` 包下的所有 Domain 模型和 Repository 接口已存在。
Session A 的 Domain 接口是你需要实现的接口契约。

## 任务

实现以下两个 Repository + 两个 Mapper：

### 1. data/repository/AgentRepository.kt
- 实现 `com.promenar.nexara.domain.repository.IAgentRepository`
- 构造函数注入 `AgentDao`（类型：`com.promenar.nexara.data.local.db.dao.AgentDao`）
- AgentEntity 类型：`com.promenar.nexara.data.local.db.entity.AgentEntity`
- `observeAll()` → `agentDao.observeAll().map { list -> list.map { AgentMapper.toDomain(it) } }`
- `observeById(id)` → `agentDao.getByIdFlow(id).map { it?.let { AgentMapper.toDomain(it) } }`
  - 注意：如果 AgentDao 没有 `getByIdFlow`，使用 `agentDao.observeAll().map { list -> list.find { it.id == id } }` 降级
- `create(agent)` → `agentDao.insert(AgentMapper.toEntity(agent))`
- `update(agent)` → `agentDao.update(AgentMapper.toEntity(agent))`
- `delete(id)` → `agentDao.deleteById(id)`

### 2. data/repository/DocumentRepository.kt
- 实现 `com.promenar.nexara.domain.repository.IDocumentRepository`
- 构造函数注入 `DocumentDao` 和 `FolderDao`
  - DocumentDao: `com.promenar.nexara.data.local.db.dao.DocumentDao`
  - FolderDao: `com.promenar.nexara.data.local.db.dao.FolderDao`
- DocumentEntity: `com.promenar.nexara.data.local.db.entity.DocumentEntity`
- `observeByFolder(folderId)` → `documentDao.observeByFolder(folderId).map { list -> list.map { DocumentMapper.toDomain(it) } }`
  - 如果 DocumentDao 没有 `observeByFolder`，使用 `documentDao.observeAll().map { list -> list.filter { it.folderId == folderId } }` 降级
- `import(path, folderId)` → 读取文件内容，生成 ID (`doc_{timestamp}`)，计算 SHA-256 hash，创建 DocumentEntity 并 insert
  - 文件读取使用 `java.io.File(path).readText()`
  - SHA-256 计算使用 `java.security.MessageDigest`
- `update(id, content)` → 先 getById，更新 content + hash + updatedAt，再 update
- `delete(id)` → `documentDao.deleteById(id)`
- `markVectorized(id)` → 先 getById，设置 vectorizedAt = System.currentTimeMillis()，再 update

### 3. data/mapper/AgentMapper.kt
```kotlin
package com.promenar.nexara.data.mapper

import com.promenar.nexara.data.local.db.entity.AgentEntity
import com.promenar.nexara.domain.model.Agent
import com.promenar.nexara.domain.model.ExecutionMode

object AgentMapper {
    fun toDomain(entity: AgentEntity): Agent = Agent(
        id = entity.id,
        name = entity.name,
        description = entity.description,
        systemPrompt = entity.systemPrompt,
        modelId = entity.model,
        icon = entity.icon,
        color = entity.color,
        avatarPath = entity.avatarPath,
        isPinned = entity.isPinned != 0,
        temperature = entity.temperature,
        topP = entity.topP,
        maxTokens = entity.maxTokens,
        ragConfig = entity.ragConfig,
        retrievalConfig = entity.retrievalConfig,
        useInheritedConfig = entity.useInheritedConfig,
        executionMode = entity.executionMode ?: ExecutionMode.SEMI,
        skills = entity.skills ?: emptyList(),
        createdAt = entity.createdAt
    )

    fun toEntity(agent: Agent): AgentEntity = AgentEntity(
        id = agent.id,
        name = agent.name,
        description = agent.description,
        systemPrompt = agent.systemPrompt,
        model = agent.modelId,
        icon = agent.icon,
        color = agent.color,
        avatarPath = agent.avatarPath,
        isPinned = if (agent.isPinned) 1 else 0,
        temperature = agent.temperature,
        topP = agent.topP,
        maxTokens = agent.maxTokens,
        ragConfig = agent.ragConfig,
        retrievalConfig = agent.retrievalConfig,
        useInheritedConfig = agent.useInheritedConfig,
        executionMode = agent.executionMode,
        skills = agent.skills,
        createdAt = agent.createdAt
    )
}
```
- 在编写 Mapper 前，**务必先读取 AgentEntity 的实际字段定义**，确保字段名和类型完全匹配。上述代码基于架构设计预期，可能与实际 Entity 有差异。

### 4. data/mapper/DocumentMapper.kt
- 同理，先读取 `DocumentEntity` 的实际字段，编写 `toDomain()` 和 `toEntity()` 双向映射
- 使用 `java.security.MessageDigest.getInstance("SHA-256")` 计算 hash

## 执行要求

1. 先读取现有文件了解结构：
   - `data/local/db/entity/AgentEntity.kt`
   - `data/local/db/entity/DocumentEntity.kt`
   - `data/local/db/dao/AgentDao.kt`
   - `data/local/db/dao/DocumentDao.kt`
   - `domain/repository/IAgentRepository.kt`（Session A 产物）
   - `domain/repository/IDocumentRepository.kt`（Session A 产物）

2. 创建 4 个文件
3. 确保编译通过：`cd native-ui && ./gradlew :app:compileDebugKotlin`
4. 完成后报告创建的文件列表和编译结果

## 禁止事项
- 不要修改现有的 Entity、DAO 或 Domain 接口文件
- 不要在 Repository 中引入 UI 相关依赖
```

---

### Session C 提示词

```
你需要在 Nexara 原生 Kotlin 项目中实现 VectorRepository 和 KnowledgeGraphRepository。

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/

## 前置条件
Session A 已完成，`domain/` 包下的所有 Domain 模型和 Repository 接口已存在。

## 任务

实现以下两个 Repository + 两个 Mapper：

### 1. data/repository/VectorRepository.kt
- 实现 `com.promenar.nexara.domain.repository.IVectorRepository`
- 构造函数注入：
  - `VectorDao`（`com.promenar.nexara.data.local.db.dao.VectorDao`）
  - `VectorFtsDao`（如有，`com.promenar.nexara.data.local.db.dao.VectorFtsDao`）
  - `EmbeddingClient`（`com.promenar.nexara.data.rag.EmbeddingClient`）

- `search(query, topK, filters)`:
  1. 调用 EmbeddingClient.embed(query) 获取查询向量
  2. 调用 VectorDao 的相似度搜索方法获取 Top-K 结果
  3. 按 score 降序排列
  4. 应用 filters（documentIds 过滤、sessionId 过滤、minScore 阈值）
  5. 返回 `List<SearchResult>`

- `index(documentId, chunks)`:
  1. 对每个 chunk 调用 EmbeddingClient.embed(chunk.text)
  2. 将向量化后的 chunk 批量 insert 到 VectorDao
  3. 同时插入 FTS 索引（如有 FtsDao）

- `deleteByDocument(documentId)`: 调用 VectorDao.deleteByDocumentId(documentId)

### 2. data/repository/KnowledgeGraphRepository.kt
- 实现 `com.promenar.nexara.domain.repository.IKnowledgeGraphRepository`
- 构造函数注入：
  - `KgNodeDao`（`com.promenar.nexara.data.local.db.dao.KgNodeDao`）
  - `KgEdgeDao`（`com.promenar.nexara.data.local.db.dao.KgEdgeDao`）
  - `GraphExtractor`（如有，`com.promenar.nexara.data.rag.GraphExtractor`）

- `extractFromDocument(documentId)`:
  1. 通过 DocumentDao 获取文档内容（临时依赖，后续迁移到 UseCase 层协调）
  2. 调用 GraphExtractor 提取三元组
  3. 将提取的 nodes 和 edges 批量 insert
  4. 返回 ExtractionResult

- `getAllNodes()`: 查询全部 KgNodeEntity，通过 KgMapper 转为 domain.KgNode
- `getAllEdges()`: 查询全部 KgEdgeEntity，通过 KgMapper 转为 domain.KgEdge
- `clear()`: 先删除所有 edges，再删除所有 nodes

### 3. data/mapper/VectorMapper.kt
- 先读取 `VectorEntity` 的实际字段定义
- 编写 `VectorEntity → domain.SearchResult` 映射

### 4. data/mapper/KgMapper.kt
- 先读取 `KgNodeEntity`、`KgEdgeEntity` 的实际字段定义
- 编写 `KgNodeEntity → domain.KgNode` 和 `KgEdgeEntity → domain.KgEdge` 映射

## 执行要求

1. 先读取现有文件了解结构（VectorDao, VectorEntity, KgNodeDao, KgEdgeDao, EmbeddingClient, GraphExtractor）
2. 创建 4 个文件
3. 确保编译通过：`cd native-ui && ./gradlew :app:compileDebugKotlin`
4. 如果某些依赖类（如 GraphExtractor）不存在或名称不同，使用实际存在的类名，并在注释中说明差异

## 特别注意

- EmbeddingClient 的实际类路径、方法签名可能与预期不同，请以实际文件为准
- 如果 VectorDao 没有直接的相似度搜索方法，分析其现有方法并用应用层代码实现余弦相似度计算
- 如果 FTS 相关 DAO 不存在，跳过 FTS 部分并在 search() 方法注释中标注 "FTS 待集成"

## 禁止事项
- 不要修改现有的 Entity、DAO 或 Domain 接口文件
```

---

### Session D 提示词

```
你需要在 Nexara 原生 Kotlin 项目中实现 ProviderRepository，并将现有 SessionRepository 和 MessageRepository 对齐架构文档的接口定义。

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/

## 前置条件
Session A 已完成，`domain/` 包下的所有 Repository 接口已存在。

## 任务

### 1. 新建 data/repository/ProviderRepository.kt
- 实现 `com.promenar.nexara.domain.repository.IProviderRepository`
- 构造函数注入：
  - `SharedPreferences`（name = "nexara_providers"）
  - 现有 LLM 客户端工厂（如 `MessageFormatterFactory` 等，用于 testConnection）
  - `ProviderManager`（`com.promenar.nexara.data.manager.ProviderManager`）

- `observeAll()`: 从 ProviderManager 获取所有 ProviderConfig 列表，转换为 domain.ProviderConfig，包装为 Flow
- `testConnection(providerId)`: 
  1. 从 ProviderManager 获取 provider 配置
  2. 根据 protocolType 创建对应的 LLM 客户端
  3. 发送一个最小的测试请求（如列出模型）
  4. 记录延迟，返回 ConnectionResult
- `fetchModels(providerId)`: 调用 provider 的 /models 端点，解析并返回 List<ModelSpec>
- `save(config)`: 将 domain.ProviderConfig 转为 ProviderManager 的存储格式并保存
- `delete(id)`: 从 ProviderManager 删除指定 provider

### 2. 修改 data/repository/SessionRepository.kt
- 先读取现有文件完整内容（`data/repository/SessionRepository.kt`）
- **额外实现** `com.promenar.nexara.domain.repository.ISessionRepository` 接口
- 检查现有方法签名是否与 ISessionRepository 匹配：
  - `observeByAgent(agentId)` → 已有或新增
  - `observeById(id)` → 已有或新增
  - `create(agentId, modelId)` → 调整为返回 `domain.Session`（当前可能返回 Entity 或其他类型）
  - `updateTitle(id, title)` → 对齐
  - `delete(id)` → 对齐
- **如果现有返回类型是 Entity**：在 Repository 层内部做 Mapper 转换，对外返回 domain.Session
- **不要改变已有调用者**：如果现有代码通过其他方法签名调用，暂时保留旧方法并标记 `@Deprecated`

### 3. 修改 data/repository/MessageRepository.kt
- 先读取现有文件完整内容
- **额外实现** `com.promenar.nexara.domain.repository.IMessageRepository` 接口
- `send(sessionId, content, role)` → 创建 Message 并持久化，返回 domain.Message
- `appendContent(messageId, chunk)` → 追加流式内容到已有消息
- 其他方法类似对齐

## 执行要求

1. 先读取所有需要理解的文件：
   - `data/repository/SessionRepository.kt`（完整内容）
   - `data/repository/MessageRepository.kt`（完整内容）
   - `data/manager/ProviderManager.kt`（了解其数据结构和 API）
   - `domain/repository/IProviderRepository.kt`
   - `domain/repository/ISessionRepository.kt`
   - `domain/repository/IMessageRepository.kt`

2. 创建 1 个新文件，修改 2 个现有文件
3. 确保编译通过：`cd native-ui && ./gradlew :app:compileDebugKotlin`
4. 完成后报告变更摘要

## 特别注意

- 修改 SessionRepository 和 MessageRepository 时**必须保持向后兼容**——现有 ViewModel 调用方不能 broken
- 使用 `@Deprecated` 标记需要迁移的旧方法，并在注释中说明替代方案
- ProviderManager 可能使用了特殊的数据结构（如 SharedPreferences 序列化），ProviderRepository 中保持相同的存储方式

## 禁止事项
- 不要修改 Domain 层的接口定义
- 不要删除 SessionRepository / MessageRepository 中仍被使用的现有方法
```

---

## 8. 验证清单（所有 Session 完成后）

- [ ] `./gradlew :app:compileDebugKotlin` 零错误通过
- [ ] `domain/` 包下 13 个文件全部存在
- [ ] 7 个 Repository 接口全部有对应实现
- [ ] 现有 ViewModel 代码未被破坏（向后兼容）
- [ ] 无 `domain.*` 文件包含 Android 框架 import

---

**文档维护者**: AI Assistant
**最后更新**: 2026-05-13
