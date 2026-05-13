# Phase 2c — 剩余 ViewModel 迁移 + 单元测试

> **前置**: Phase 2b 完成（5 个 VM 已迁移，11 个测试通过）
> **创建日期**: 2026-05-13
> **核心要求**: 所有新增/修改代码必须编写单元测试并通过

---

## 1. 并行会话拆分

```
Session H (ChatViewModel — 最核心，agentDao → AgentRepository)
    │
    ├── Session I (SettingsViewModel — vectorDao/documentDao → Repository)
    └── Session J (RagViewModel — documentDao/vectorDao/kgNodeDao → Repository)
```

**零文件冲突，完全可并行。**

| 文件 | H | I | J |
|------|---|---|---|
| `domain/repository/IAgentRepository.kt` | ✅ 添加 getById | — | — |
| `data/repository/AgentRepository.kt` | ✅ 实现 getById | — | — |
| `ui/chat/ChatViewModel.kt` | ✅ 迁移 3 处 agentDao | — | — |
| `domain/repository/IDocumentRepository.kt` | — | ✅ 添加 count/getGlobal | — |
| `domain/repository/IVectorRepository.kt` | — | ✅ 添加 count | — |
| `data/repository/DocumentRepository.kt` | — | ✅ 实现 count | — |
| `data/repository/VectorRepository.kt` | — | ✅ 实现 count | ✅ 使用已有方法 |
| `ui/settings/SettingsViewModel.kt` | — | ✅ 迁移 | — |
| `ui/rag/RagViewModel.kt` | — | — | ✅ 迁移 |

---

## 2. Session H — ChatViewModel 迁移 + AgentRepository.getById

### Session H 提示词

```
你需要在 Nexara 原生 Kotlin 项目中完成：
1. 为 IAgentRepository 添加 suspend fun getById
2. 将 ChatViewModel 中 3 处 agentDao 调用替换为 agentRepository
3. 编写/更新单元测试并通过

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/

## Step 1: 添加 IAgentRepository.getById

### domain/repository/IAgentRepository.kt
添加方法：
```kotlin
suspend fun getById(id: String): Agent?
```

### data/repository/AgentRepository.kt
实现：
```kotlin
override suspend fun getById(id: String): Agent? =
    agentDao.getById(id)?.let { AgentMapper.toDomain(it) }
```
- 如果 agentDao 没有 getById 方法，查看其实际方法名（可能是 getByIdFlow 或其他），用实际方法实现

## Step 2: 迁移 ChatViewModel

先读取完整文件。构造函数已有 `ISessionRepository` 和 `IMessageRepository`，添加 `agentRepository: IAgentRepository` 参数（或 `com.promenar.nexara.data.repository.AgentRepository`）。

替换 3 处 agentDao 调用：

**位置 1** — `generateMessage()` 中（约 247-252 行）:
```kotlin
// 旧代码:
val agentDao = (application as NexaraApplication).database.agentDao()
val agentEntity = withContext(Dispatchers.IO) { agentDao.getById(sessionForCtx.agentId) }

// 替换为:
val agent = agentRepository.getById(sessionForCtx.agentId)
```
然后将后续 `agentEntity?.systemPrompt` 改为 `agent?.systemPrompt`，`agentEntity?.model` 改为 `agent?.modelId`（注意：Domain Agent 用 modelId 不是 model）。

**位置 2** — `updateAgentName()`（约 587-589 行）:
```kotlin
// 旧代码:
val agentDao = (application as NexaraApplication).database.agentDao()
val agent = agentDao.getById(agentId)
_agentName.value = agent?.name ?: ""

// 替换为:
val agent = agentRepository.getById(agentId)
_agentName.value = agent?.name ?: ""
```

**位置 3** — `updateTokenIndicator()`（约 1008-1009 行）:
```kotlin
// 旧代码:
val agentDao = (application as NexaraApplication).database.agentDao()
val agent = session.agentId.let { agentDao.getById(it) }

// 替换为:
val agent = session.agentId?.let { agentRepository.getById(it) }
```
然后 `agent?.systemPrompt` 保持不变。

## Step 3: 更新 ChatViewModelTest

先读取现有测试文件，找到所有创建 ChatViewModel 实例的地方，添加 `agentRepository` mock 参数。

```kotlin
private val mockAgentRepo: IAgentRepository = mockk(relaxed = true)

// 在每个测试中，mock agentRepository.getById 返回必要的 Agent:
coEvery { mockAgentRepo.getById(any()) } returns Agent(
    id = "agent-1", name = "Test Agent", systemPrompt = "test prompt",
    modelId = "gpt-4", executionMode = ExecutionMode.SEMI
)
```

- 确保构造 ChatViewModel 时传入 mockAgentRepo
- 添加 1 个新测试：验证 `getById` 被正确调用

## 执行要求
1. 每步编译验证: `./gradlew :app:compileDebugKotlin`
2. 全部测试通过: `./gradlew :app:testDebugUnitTest --tests "com.promenar.nexara.ui.chat.ChatViewModelTest"`
3. 确认 ChatViewModel 中不再有 `agentDao` 或 `NexaraApplication.database` 引用

## 禁止事项
- 不要修改 generateMessage() 的核心业务逻辑（仅替换数据访问方式）
- 注意 modelId vs model 的字段名差异（Domain Agent 用 modelId）
```

---

## 3. Session I — SettingsViewModel 迁移

### Session I 提示词

```
你需要在 Nexara 原生 Kotlin 项目中完成：
1. 为 IDocumentRepository/IVectorRepository 添加计数方法
2. 将 SettingsViewModel 中 2 处 DAO 调用替换为 Repository
3. 编写单元测试并通过

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/

## Step 1: 添加计数方法

### domain/repository/IDocumentRepository.kt
添加：
```kotlin
suspend fun getCount(): Int
```

### data/repository/DocumentRepository.kt
实现：
```kotlin
override suspend fun getCount(): Int = documentDao.getCount()
```
- 如 DocumentDao 无 getCount 方法，用 `documentDao.observeAll()` 或类似方法取 `.size`

### domain/repository/IVectorRepository.kt
添加：
```kotlin
suspend fun getCount(): Int
```

### data/repository/VectorRepository.kt
实现：
```kotlin
override suspend fun getCount(): Int = vectorDao.getCount()
```
- 如 VectorDao 无 getCount，使用 `vectorDao.getAll().size`

## Step 2: 迁移 SettingsViewModel

先读取完整文件。

**位置 1** — `loadTokenStats()`（约 223-249 行）:
```kotlin
// 旧:
val vectorDao = app.database.vectorDao()
val totalCount = vectorDao.getAll().size

// 替换为:
val totalCount = vectorRepository.getCount()
```
- 构造函数添加 `vectorRepository: IVectorRepository` 参数

**位置 2** — `loadKnowledgeStats()`（约 266-273 行）:
```kotlin
// 旧:
val docCount = app.database.documentDao().getGlobalDocuments().size

// 替换为:
val docCount = documentRepository.getCount()
```
- 构造函数添加 `documentRepository: IDocumentRepository` 参数

## Step 3: 编写 SettingsViewModelTest.kt

新建 `test/.../ui/settings/SettingsViewModelTest.kt`：

```kotlin
package com.promenar.nexara.ui.settings

import com.promenar.nexara.domain.repository.IDocumentRepository
import com.promenar.nexara.domain.repository.IVectorRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SettingsViewModelTest {

    @Test
    fun `loadTokenStats uses vectorRepository count`() = runTest {
        val vectorRepo: IVectorRepository = mockk()
        val docRepo: IDocumentRepository = mockk()
        coEvery { vectorRepo.getCount() } returns 42
        coEvery { docRepo.getCount() } returns 10

        // 构造 ViewModel（需根据实际构造函数签名调整）
        // val vm = SettingsViewModel(..., vectorRepo, docRepo)
        // vm.loadTokenStats()
        // 验证使用了 vectorRepo.getCount()
        // coVerify { vectorRepo.getCount() }
    }

    @Test
    fun `loadKnowledgeStats uses documentRepository count`() = runTest { ... }
}
```
- **重要**: 先读取 SettingsViewModel 的实际构造函数，确认参数名称和位置，再编写测试中的实例化代码。

## 执行要求
1. 每步编译验证
2. 测试通过: `./gradlew :app:testDebugUnitTest --tests "com.promenar.nexara.ui.settings.SettingsViewModelTest"`
3. SettingsViewModel 不再有 `app.database.vectorDao()` 或 `app.database.documentDao()` 调用

## 禁止事项
- 不要修改 SettingsViewModel 中已有的 SkillRepository 相关代码（已正确使用 Repository 模式）
```

---

## 4. Session J — RagViewModel 迁移

### Session J 提示词

```
你需要在 Nexara 原生 Kotlin 项目中完成：
1. 将 RagViewModel 中的 documentDao / vectorDao / kgNodeDao 替换为 Repository
2. 编写单元测试并通过
3. folderDao 暂时保留（标记 TODO，等待 FolderRepository 创建）

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/

## Step 1: 添加必需的 Repository 方法

### domain/repository/IDocumentRepository.kt
添加（如果 Session I 已添加则跳过）：
```kotlin
suspend fun getByFolderId(folderId: String): List<Document>
suspend fun getCount(): Int
suspend fun countByFolderId(folderId: String): Int
```

### data/repository/DocumentRepository.kt
实现以上方法（使用 DocumentDao 对应方法，如无则用 observeAll 降级）。

### domain/repository/IVectorRepository.kt
确认已有：
```kotlin
suspend fun deleteByDocument(documentId: String)
```

### domain/repository/IKnowledgeGraphRepository.kt
添加（如无）：
```kotlin
suspend fun getNodeCount(): Int
```

### data/repository/KnowledgeGraphRepository.kt
实现 getNodeCount()。

## Step 2: 迁移 RagViewModel

先读取完整文件。

**构造函数修改**:
```kotlin
// 当前 (第 40-47 行):
private val database = app.database
private val folderDao = database.folderDao()         // TODO: 保留，等待 FolderRepository
private val documentDao = database.documentDao()      // → documentRepository
private val vectorDao = database.vectorDao()          // → vectorRepository
private val kgNodeDao = database.kgNodeDao()          // → kgRepository
private val kgEdgeDao = database.kgEdgeDao()          // → kgRepository

// 替换为:
private val folderDao = app.database.folderDao()      // TODO: 等待 FolderRepository
private val documentRepository: IDocumentRepository
private val vectorRepository: IVectorRepository
private val kgRepository: IKnowledgeGraphRepository
```

迁移映射表：
| 旧 DAO 调用 | 新 Repository 调用 |
|-------------|-------------------|
| `documentDao.observeAll()` | 用 Flow 从 documentRepository.observeAll() 降级或文档级别处理 |
| `documentDao.getCount()` | `documentRepository.getCount()` |
| `documentDao.countByFolderId(id)` | `documentRepository.countByFolderId(id)` |
| `documentDao.getByFolderId(id)` | `documentRepository.getByFolderId(id)` |
| `documentDao.deleteById(id)` | 通过 `documentRepository.delete(id)` |
| `vectorDao.deleteByDocId(id)` | `vectorRepository.deleteByDocument(id)` |
| `kgNodeDao.getCount()` | `kgRepository.getNodeCount()` |

**loadStats() 方法**（约 285-299 行）：将 `documentDao.getCount()` 改为 `documentRepository.getCount()`，`kgNodeDao.getCount()` 改为 `kgRepository.getNodeCount()`。

**loadDocumentsForFolder()**（约 318 行）：将 `documentDao.getByFolderId()` 改为 `documentRepository.getByFolderId()`。

**deleteDocuments()**（约 333-343 行）：将 `documentDao.deleteById()` 改为 `documentRepository.delete()`，`vectorDao.deleteByDocId()` 改为 `vectorRepository.deleteByDocument()`。

## Step 3: 编写 RagViewModelTest.kt

新建 `test/.../ui/rag/RagViewModelTest.kt`：

```kotlin
class RagViewModelTest {
    @Test
    fun `loadStats uses repository counts`() = runTest {
        val docRepo: IDocumentRepository = mockk()
        val vectorRepo: IVectorRepository = mockk()
        val kgRepo: IKnowledgeGraphRepository = mockk()
        coEvery { docRepo.getCount() } returns 10
        coEvery { kgRepo.getNodeCount() } returns 5

        // 构造并验证
    }

    @Test
    fun `deleteDocuments calls vectorRepo deleteByDocument`() = runTest { ... }
}
```

## 执行要求
1. 先读取 RagViewModel / DocumentDao / VectorDao / KgNodeDao 实际接口
2. 每步编译验证
3. 测试通过
4. RagViewModel 中不再有 `documentDao` / `vectorDao` / `kgNodeDao` / `kgEdgeDao` 引用（仅保留 folderDao）

## 禁止事项
- 不要删除或修改 folderDao 相关代码（标记 `// TODO: migrate to FolderRepository`）
- 注意 VectorStatsService 可能在内部使用 vectorDao — 暂时保留其行为
```

---

## 5. 验证清单

- [ ] `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- [ ] `./gradlew :app:testDebugUnitTest` 无新增失败（仅预存 ChatViewModel × 5 + ModelSpecs × 1）
- [ ] ChatViewModel 不再有 `agentDao` 引用
- [ ] SettingsViewModel 不再有 `vectorDao` / `documentDao` 引用
- [ ] RagViewModel 不再有 `documentDao` / `vectorDao` / `kgNodeDao` / `kgEdgeDao` 引用
- [ ] 仅剩 folderDao (RagViewModel) 标记 TODO

---

**文档维护者**: AI Assistant
**最后更新**: 2026-05-13
