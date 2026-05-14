# Phase 2b — ViewModel 迁移至 Repository + 单元测试

> **前置**: Phase 2a 完成（Domain 层 + Repository 层 28 文件，编译通过）
> **创建日期**: 2026-05-13
> **总预估工时**: ~3 人天（3 个部分并行会话）
> **核心要求**: **所有新增/修改的业务逻辑代码必须编写单元测试并通过**（MockK + JUnit5 + Truth）

---

## 1. 测试基础设施准备

### 1.1 需新增的依赖

当前 `build.gradle.kts` 缺少 MockK 和 Turbine：

```kotlin
// 添加到 app/build.gradle.kts 的 testImplementation 块
testImplementation("io.mockk:mockk:1.13.12")
testImplementation("app.cash.turbine:turbine:1.1.0")
```

### 1.2 测试工具链

| 工具 | 用途 |
|------|------|
| JUnit5 (已有) | 测试运行框架 |
| MockK (新增) | Mock DAO / Repository，支持 Kotlin suspend/Flow |
| Turbine (新增) | Flow 断言（`flow.test { ... }`） |
| Truth (已有) | 流式断言（`assertThat(x).isEqualTo(y)`） |
| kotlinx-coroutines-test (已有) | `runTest { }` 协程测试作用域 |

---

## 2. 并行会话拆分

```
Session E (先执行: 添加依赖 + Agent 迁移)
    │
    ├── Session F (并行: Document 迁移)
    └── Session G (并行: KG 迁移)
```

### 文件冲突矩阵

| 文件 | E | F | G |
|------|---|---|---|
| `build.gradle.kts` | ✅ 修改 | — | — |
| `AgentHubViewModel.kt` | ✅ 修改 | — | — |
| `AgentEditViewModel.kt` | ✅ 修改 | — | — |
| `SessionListViewModel.kt` | ✅ 修改 | — | — |
| `DocEditorViewModel.kt` | — | ✅ 修改 | — |
| `KnowledgeGraphViewModel.kt` | — | — | ✅ 修改 |
| 测试文件 | 6 个新建 | 3 个新建 | 3 个新建 |

**零冲突，确认可并行。**

---

## 3. Session E — 测试依赖 + Agent ViewModel 迁移

### 任务范围

1. 添加 MockK + Turbine 依赖
2. 将 AgentHubViewModel / AgentEditViewModel / SessionListViewModel 中的 `AgentDao` 替换为 `AgentRepository`
3. 编写全套单元测试（Mapper + Repository + ViewModel）

### 交付物

| 文件 | 操作 |
|------|------|
| `app/build.gradle.kts` | 修改 — 添加 mockk + turbine |
| `ui/hub/AgentHubViewModel.kt` | 修改 — AgentDao → AgentRepository |
| `ui/hub/AgentEditViewModel.kt` | 修改 — AgentDao → AgentRepository |
| `ui/hub/SessionListViewModel.kt` | 修改 — AgentDao → AgentRepository |
| `test/.../data/mapper/AgentMapperTest.kt` | 新建 |
| `test/.../data/repository/AgentRepositoryTest.kt` | 新建 |
| `test/.../ui/hub/AgentHubViewModelTest.kt` | 新建 |
| `test/.../ui/hub/AgentEditViewModelTest.kt` | 新建 |
| `test/.../ui/hub/SessionListViewModelTest.kt` | 新建 |

### Session E 提示词

```
你需要在 Nexara 原生 Kotlin 项目中完成三项工作：
1. 添加 MockK + Turbine 测试依赖
2. 将 3 个 Agent 相关 ViewModel 从 DAO 迁移到 Repository
3. 编写全套单元测试并通过

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/
测试包: app/src/test/java/com/promenar/nexara/

## Step 1: 添加测试依赖

修改 `app/build.gradle.kts`，在 `testImplementation` 块中添加：
```kotlin
testImplementation("io.mockk:mockk:1.13.12")
testImplementation("app.cash.turbine:turbine:1.1.0")
```

## Step 2: 迁移 ViewModel

### 2.1 AgentHubViewModel.kt
- 先读取完整文件内容
- 将构造函数中的 `AgentDao` 参数替换为 `AgentRepository`（`com.promenar.nexara.data.repository.AgentRepository`）
- `createAgent()` 方法：将 `agentDao.insert(entity)` 改为 `agentRepository.create(domainAgent)`，调用 `AgentMapper.toDomain()` 和 `AgentMapper.toEntity()`
- `deleteAgent()` 方法：将 `agentDao.deleteById(id)` 改为 `agentRepository.delete(id)`
- `togglePin()` 方法：读取 agent → 翻转 isPinned → `agentRepository.update(agent)`
- `init` 中的种子数据：保持原有默认 Agent 逻辑，但通过 `agentRepository.create()` 写入
- 移除所有 `import ...AgentDao` 和 `import ...AgentEntity`
- 编译验证

### 2.2 AgentEditViewModel.kt
- 先读取完整文件内容
- 将 `app.database.agentDao()` 替换为 `AgentRepository` 注入（通过构造函数）
- `loadAgent()` / `saveAgent()` / `deleteAgent()` 方法全部改用 `agentRepository`
- 移除 Entity 映射代码（已由 AgentMapper 处理）
- 编译验证

### 2.3 SessionListViewModel.kt
- 先读取完整文件内容
- 将 `AgentDao` 参数替换为 `AgentRepository`
- `loadSessions()` 中获取 agent 元数据改用 `agentRepository.observeById(agentId)`
- 编译验证

## Step 3: 编写单元测试

所有测试必须在 JVM 上运行（纯 Kotlin，不需要 Android 模拟器）。

### 3.1 AgentMapperTest.kt
```kotlin
package com.promenar.nexara.data.mapper

import com.promenar.nexara.data.local.db.entity.AgentEntity
import com.promenar.nexara.domain.model.ExecutionMode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AgentMapperTest {

    @Test
    fun `toDomain maps all fields correctly`() {
        val entity = AgentEntity(
            id = "test-id",
            name = "Test Agent",
            description = "desc",
            systemPrompt = "prompt",
            model = "gpt-4",
            icon = "✨",
            color = "#FF0000",
            avatarPath = "/path/avatar.png",
            isPinned = 1,
            temperature = 0.7,
            topP = 0.9,
            maxTokens = 4096,
            ragConfig = null,
            retrievalConfig = null,
            useInheritedConfig = true,
            executionMode = ExecutionMode.SEMI,
            skills = listOf("search", "code"),
            createdAt = 1000L
        )
        val agent = AgentMapper.toDomain(entity)
        assertThat(agent.id).isEqualTo("test-id")
        assertThat(agent.name).isEqualTo("Test Agent")
        assertThat(agent.isPinned).isTrue()
        assertThat(agent.executionMode).isEqualTo(ExecutionMode.SEMI)
        assertThat(agent.skills).containsExactly("search", "code")
    }

    @Test
    fun `toDomain handles isPinned=0`() {
        val entity = AgentEntity(
            id = "id", name = "n", description = "", systemPrompt = "",
            model = "m", icon = "✨", color = "#000", avatarPath = null,
            isPinned = 0, temperature = null, topP = null, maxTokens = null,
            ragConfig = null, retrievalConfig = null, useInheritedConfig = true,
            executionMode = null, skills = null, createdAt = 0L
        )
        assertThat(AgentMapper.toDomain(entity).isPinned).isFalse()
    }

    @Test
    fun `toDomain defaults executionMode to SEMI when null`() {
        val entity = AgentEntity(
            id = "id", name = "n", description = "", systemPrompt = "",
            model = "m", icon = "✨", color = "#000", avatarPath = null,
            isPinned = 0, temperature = null, topP = null, maxTokens = null,
            ragConfig = null, retrievalConfig = null, useInheritedConfig = true,
            executionMode = null, skills = null, createdAt = 0L
        )
        assertThat(AgentMapper.toDomain(entity).executionMode).isEqualTo(ExecutionMode.SEMI)
    }

    @Test
    fun `roundtrip preserves all fields`() {
        val entity = AgentEntity(
            id = "roundtrip", name = "RT", description = "d", systemPrompt = "sp",
            model = "gpt-4", icon = "🧪", color = "#ABC", avatarPath = null,
            isPinned = 1, temperature = 0.5, topP = 0.8, maxTokens = 2048,
            ragConfig = null, retrievalConfig = null, useInheritedConfig = false,
            executionMode = ExecutionMode.AUTO, skills = emptyList(), createdAt = 42L
        )
        val domain = AgentMapper.toDomain(entity)
        val back = AgentMapper.toEntity(domain)
        assertThat(back.id).isEqualTo(entity.id)
        assertThat(back.name).isEqualTo(entity.name)
        assertThat(back.isPinned).isEqualTo(entity.isPinned)
        assertThat(back.executionMode).isEqualTo(entity.executionMode)
    }
}
```
- **重要**: 上述 Entity 构造函数基于架构设计预期。编写前**务必先读取 `AgentEntity.kt` 的实际字段定义**，确保参数顺序和名称完全匹配。如果 Entity 使用命名参数或字段名不同（如 snake_case 映射），请以实际文件为准。

### 3.2 AgentRepositoryTest.kt
```kotlin
package com.promenar.nexara.data.repository

import com.promenar.nexara.data.local.db.dao.AgentDao
import com.promenar.nexara.data.local.db.entity.AgentEntity
import com.promenar.nexara.data.mapper.AgentMapper
import com.promenar.nexara.domain.model.Agent
import com.promenar.nexara.domain.model.ExecutionMode
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AgentRepositoryTest {

    // 使用真实 Entity 构建测试数据。请在编写前读取 AgentEntity.kt 确认字段。
    private fun createEntity(id: String, name: String) = AgentEntity(
        id = id, name = name, description = "", systemPrompt = "",
        model = "m", icon = "✨", color = "#000", avatarPath = null,
        isPinned = 0, temperature = null, topP = null, maxTokens = null,
        ragConfig = null, retrievalConfig = null, useInheritedConfig = true,
        executionMode = null, skills = null, createdAt = 0L
    )

    @Test
    fun `observeAll maps entities to domain`() = runTest {
        val dao: AgentDao = mockk()
        val repo = AgentRepository(dao)
        val entities = listOf(createEntity("a1", "Agent1"), createEntity("a2", "Agent2"))
        every { dao.observeAll() } returns flowOf(entities)

        val result = repo.observeAll()
        result.collect { agents ->
            assertThat(agents).hasSize(2)
            assertThat(agents[0].name).isEqualTo("Agent1")
            assertThat(agents[1].name).isEqualTo("Agent2")
        }
    }

    @Test
    fun `observeById finds matching agent`() = runTest {
        val dao: AgentDao = mockk()
        val repo = AgentRepository(dao)
        val entities = listOf(createEntity("target", "Target"), createEntity("other", "Other"))
        every { dao.observeAll() } returns flowOf(entities)

        val result = repo.observeById("target")
        result.collect { agent ->
            assertThat(agent).isNotNull()
            assertThat(agent!!.name).isEqualTo("Target")
        }
    }

    @Test
    fun `observeById returns null for missing id`() = runTest {
        val dao: AgentDao = mockk()
        val repo = AgentRepository(dao)
        every { dao.observeAll() } returns flowOf(emptyList())

        repo.observeById("nonexistent").collect { agent ->
            assertThat(agent).isNull()
        }
    }

    @Test
    fun `create delegates to dao insert`() = runTest {
        val dao: AgentDao = mockk(relaxed = true)
        val repo = AgentRepository(dao)
        val agent = Agent(id = "new", name = "New", executionMode = ExecutionMode.SEMI)

        repo.create(agent)

        coVerify { dao.insert(any()) }
    }

    @Test
    fun `delete delegates to dao deleteById`() = runTest {
        val dao: AgentDao = mockk(relaxed = true)
        val repo = AgentRepository(dao)

        repo.delete("delete-me")

        coVerify { dao.deleteById("delete-me") }
    }
}
```
- **同上**: 验证 Entity 字段后调整构造参数。

### 3.3 AgentHubViewModelTest.kt
```kotlin
package com.promenar.nexara.ui.hub

import com.promenar.nexara.data.repository.AgentRepository
import com.promenar.nexara.domain.model.Agent
import com.promenar.nexara.domain.model.ExecutionMode
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AgentHubViewModelTest {

    @Test
    fun `init loads agents from repository`() = runTest {
        val repo: AgentRepository = mockk()
        val agents = listOf(Agent(id = "a1", name = "Agent1", executionMode = ExecutionMode.SEMI))
        every { repo.observeAll() } returns flowOf(agents)

        // 注意: AgentHubViewModel 的构造函数可能已变更，请以实际签名为准
        // val vm = AgentHubViewModel(repo)
        // vm.agents.collect { ... }
    }

    @Test
    fun `createAgent delegates to repository`() = runTest {
        val repo: AgentRepository = mockk(relaxed = true)
        every { repo.observeAll() } returns flowOf(emptyList())

        // val vm = AgentHubViewModel(repo)
        // vm.createAgent("Test", "desc", "gpt-4", "prompt")
        // coVerify { repo.create(any()) }
    }

    @Test
    fun `deleteAgent delegates to repository`() = runTest {
        val repo: AgentRepository = mockk(relaxed = true)
        every { repo.observeAll() } returns flowOf(emptyList())

        // val vm = AgentHubViewModel(repo)
        // vm.deleteAgent("delete-me")
        // coVerify { repo.delete("delete-me") }
    }
}
```
- **关键**: ViewModel 测试需要根据**实际修改后的 ViewModel 构造函数签名**来编写。如果 ViewModel 使用 `ViewModelProvider.Factory` 模式，请调整测试中的实例化方式。

### 3.4 AgentEditViewModelTest.kt & SessionListViewModelTest.kt
- 参考 AgentHubViewModelTest 的模式编写
- AgentEditViewModelTest: 测试 saveAgent() 调用 repo.update()、loadAgent() 调用 repo.observeById()
- SessionListViewModelTest: 测试 loadSessions() 使用 repo.observeById(agentId) 获取元数据

## 执行要求

1. **严格按顺序**: Step 1 (依赖) → 验证编译 → Step 2 (迁移) → 验证编译 → Step 3 (测试)
2. 每步编译验证: `cd native-ui && ./gradlew :app:compileDebugKotlin`
3. **所有测试必须通过**: `./gradlew :app:testDebugUnitTest`
4. 如果 Entity 字段与模板不匹配，**以实际 Entity 为准**调整测试数据
5. 测试中不允许使用 `Thread.sleep()`，统一使用 `runTest { }`

## 禁止事项
- 不要修改 Domain 层接口
- 不要修改 Repository 实现（除非发现 Bug）
- 不要在测试中依赖 Android 框架（不要用 Robolectric 除非必要）
```

---

## 4. Session F — Document ViewModel 迁移 + 测试

### 任务范围

1. 将 DocEditorViewModel 中的 `DocumentDao` 替换为 `DocumentRepository`
2. 编写 DocumentMapperTest + DocumentRepositoryTest + DocEditorViewModelTest

### Session F 提示词

```
你需要在 Nexara 原生 Kotlin 项目中完成：
1. 将 DocEditorViewModel 从 DAO 迁移到 DocumentRepository
2. 编写全套单元测试并通过

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/
测试包: app/src/test/java/com/promenar/nexara/

## 前置条件
- build.gradle.kts 已添加 MockK + Turbine（Session E 产物）
- DocumentRepository 已存在: `data.repository.DocumentRepository`
- DocumentMapper 已存在: `data.mapper.DocumentMapper`
- IDocumentRepository 已存在: `domain.repository.IDocumentRepository`

## Step 1: 迁移 DocEditorViewModel

- 先读取 `DocEditorViewModel.kt` 完整内容
- 将 `app.database.documentDao()` 替换为构造函数注入 `DocumentRepository`
- `loadDocument()` 改用 `documentRepository.observeByFolder()` 或类似方法
- `generateMockContent()` / `saveDocument()` 等逻辑保持不变，仅替换数据访问方式
- 编译验证: `./gradlew :app:compileDebugKotlin`

## Step 2: 编写单元测试

### DocumentMapperTest.kt
```kotlin
package com.promenar.nexara.data.mapper

import com.promenar.nexara.data.local.db.entity.DocumentEntity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DocumentMapperTest {
    @Test
    fun `toDomain maps all fields`() {
        // 先读取 DocumentEntity 实际字段，构造测试 entity
        // val doc = DocumentMapper.toDomain(entity)
        // assertThat(doc.id).isEqualTo(...)
    }

    @Test
    fun `roundtrip preserves content`() {
        // Entity → Domain → Entity
    }

    @Test
    fun `toDomain handles null summary`() {
        // summary = null → domain.summary isNull()
    }

    @Test
    fun `toDomain handles zero timestamps`() {
        // createdAt/updatedAt = 0
    }
}
```

### DocumentRepositoryTest.kt
```kotlin
package com.promenar.nexara.data.repository

import com.promenar.nexara.data.local.db.dao.DocumentDao
import com.promenar.nexara.data.local.db.dao.FolderDao
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DocumentRepositoryTest {
    @Test
    fun `observeByFolder returns documents from dao`() = runTest { ... }
    @Test
    fun `import creates entity with SHA256 hash`() = runTest { ... }
    @Test
    fun `delete delegates to dao`() = runTest { ... }
    @Test
    fun `markVectorized sets timestamp`() = runTest { ... }
}
```

### DocEditorViewModelTest.kt
- 测试 loadDocument() 使用 repo.observeByFolder()
- 测试 saveDocument() 调用 repo.update()
- 测试大文件检测逻辑（>10MB）

## 执行要求
- **先读取实际 Entity/Dao/Repository 文件**了解真实接口
- 所有测试通过: `./gradlew :app:testDebugUnitTest --tests "com.promenar.nexara.data.mapper.DocumentMapperTest"`
- 逐步扩大测试范围直到全部通过

## 禁止事项
- 不要修改 Domain 接口或 Repository 实现
```

---

## 5. Session G — KnowledgeGraph ViewModel 迁移 + 测试

### 任务范围

1. 将 KnowledgeGraphViewModel 中的 `KgNodeDao`/`KgEdgeDao` 替换为 `KnowledgeGraphRepository`
2. 编写 KgMapperTest + KnowledgeGraphRepositoryTest + KnowledgeGraphViewModelTest

### Session G 提示词

```
你需要在 Nexara 原生 Kotlin 项目中完成：
1. 将 KnowledgeGraphViewModel 从 DAO 迁移到 KnowledgeGraphRepository
2. 编写全套单元测试并通过

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/
测试包: app/src/test/java/com/promenar/nexara/

## 前置条件
- build.gradle.kts 已添加 MockK + Turbine
- KnowledgeGraphRepository 已存在: `data.repository.KnowledgeGraphRepository`
- KgMapper 已存在: `data.mapper.KgMapper`
- IKnowledgeGraphRepository 已存在: `domain.repository.IKnowledgeGraphRepository`

## Step 1: 迁移 KnowledgeGraphViewModel

- 先读取 `KnowledgeGraphViewModel.kt` 完整内容
- 将 `kgNodeDao` / `kgEdgeDao` 替换为构造函数注入 `KnowledgeGraphRepository`
- `injectMockData()` 改用 `repo` 的方法
- `clearGraph()` / `loadGraph()` 改用 `repo.getAllNodes()` / `repo.getAllEdges()` / `repo.clear()`
- 编译验证: `./gradlew :app:compileDebugKotlin`

## Step 2: 编写单元测试

### KgMapperTest.kt
```kotlin
class KgMapperTest {
    @Test fun `toDomain maps node entity correctly`() { ... }
    @Test fun `toDomain maps edge entity correctly`() { ... }
    @Test fun `roundtrip node preserves label and type`() { ... }
    @Test fun `empty properties maps to emptyMap`() { ... }
}
```

### KnowledgeGraphRepositoryTest.kt
```kotlin
class KnowledgeGraphRepositoryTest {
    @Test fun `getAllNodes returns from dao`() = runTest { ... }
    @Test fun `getAllEdges returns from dao`() = runTest { ... }
    @Test fun `clear deletes edges then nodes`() = runTest { ... }
    @Test fun `extractFromDocument returns extraction result`() = runTest { ... }
}
```

### KnowledgeGraphViewModelTest.kt
- 测试 loadGraph() 使用 repo.getAllNodes()/getAllEdges()
- 测试 clearGraph() 调用 repo.clear() 后重新加载
- 测试 injectMockData() 逻辑

## 执行要求
- 先读取实际 Entity/Dao/Repository 文件
- 所有测试通过: `./gradlew :app:testDebugUnitTest`
- 如果 GraphExtractor 不存在于实际代码中，`extractFromDocument` 相关测试可跳过

## 禁止事项
- 不要修改 Domain 接口或 Repository 实现
```

---

## 6. 验证清单（所有 Session 完成后）

- [ ] `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- [ ] `./gradlew :app:testDebugUnitTest` 全部测试通过
- [ ] AgentHubViewModel / AgentEditViewModel / SessionListViewModel 不再 import AgentDao
- [ ] DocEditorViewModel 不再 import DocumentDao
- [ ] KnowledgeGraphViewModel 不再 import KgNodeDao / KgEdgeDao
- [ ] 新增测试 ≥ 12 个文件

---

**文档维护者**: AI Assistant
**最后更新**: 2026-05-13
