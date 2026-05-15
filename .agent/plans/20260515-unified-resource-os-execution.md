# 统一资源操作系统 — 多会话并行执行计划

> **日期**: 2026-05-15
> **设计规范**: `20260515-unified-resource-os-design-spec.md` v2.3
> **执行引擎**: GLM-5.1（独立会话）
> **项目路径**: `/Users/promenar/Codex/Nexara/native-ui/`
> **设计规范路径**: `/Users/promenar/Library/Application Support/CodeBuddy CN/User/globalStorage/tencent-cloud.coding-copilot/brain/e3245ce0f1d14a83a5e79ea4bb9e90b9/20260515-unified-resource-os-design-spec.md`

---

## 0. 总体执行架构

```
Batch 1 (串行 — 关键路径)
  Session 1: 数据基础层  [~2h]

Batch 2 (并行 — 依赖 Session 1)
  Session 2: 域接口 + 旧表扩展  [~1h]   ∥  Session 3: 基础设施工具  [~1h]

Batch 3 (串行 — 依赖 Session 1+2+3)
  Session 4: Repository 实现全量  [~1.5h]

Batch 4 (并行 — 依赖 Session 4)
  Session 5: Skill 工具升级  [~1.5h]   ∥  Session 6: UI 全量 + Worker  [~1.5h]

Batch 5 (串行 — 依赖全部)
  Session 7: 旧系统清理 + 测试 + DIA  [~1.5h]

总耗时（串行关键路径）: ~7.5h
总耗时（最大并行）: ~8h (含验证)
```

### 前置检查（执行前）

在所有会话启动前，运行以下命令确认项目可编译：

```bash
cd /Users/promenar/Codex/Nexara/native-ui
./gradlew compileDebugKotlin 2>&1 | tail -5
```

---

## Session 1: 数据基础层

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 1（首次执行，无依赖） |
| **目标** | 新建 `workspace_files` / `workspace_seq` Entity + DAO + Migration + SessionEntity 扩展 |
| **浏览文件** | 不需要 |
| **创建文件** | 5 个 |
| **修改文件** | 2 个 |
| **预估** | ~2h |

### 复制此提示词启动会话

```
# 任务：统一资源 OS 数据基础层

## 上下文
你正在为 Nexara（Android / Kotlin / Jetpack Compose / Room）项目实现统一资源操作系统的数据层基础。请先通读以下路径的完整设计规范，重点关注 §2.3（FileEntry 数据模型）、§1.3.1（原子序号机制）、§8（DB Schema 变更清单）：

设计规范文件: /Users/promenar/Library/Application Support/CodeBuddy CN/User/globalStorage/tencent-cloud.coding-copilot/brain/e3245ce0f1d14a83a5e79ea4bb9e90b9/20260515-unified-resource-os-design-spec.md

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 必须读取的现有文件
在开始修改前，先读取以下文件了解现有代码结构：
1. app/src/main/java/com/promenar/nexara/data/local/db/NexaraDatabase.kt — 当前版本号 VERSION=7，所有 DAO 声明
2. app/src/main/java/com/promenar/nexara/data/local/db/entity/SessionEntity.kt — 当前 SessionEntity 所有字段
3. app/src/main/java/com/promenar/nexara/data/local/db/entity/VectorEntity.kt — 当前 VectorEntity 字段
4. app/src/main/java/com/promenar/nexara/data/local/db/entity/KgNodeEntity.kt — 若存在
5. app/src/main/java/com/promenar/nexara/data/local/db/entity/KgEdgeEntity.kt — 若存在

## 需要创建的文件

### 1. FileEntry.kt
路径: app/src/main/java/com/promenar/nexara/data/local/db/entity/FileEntry.kt

创建 `workspace_files` 表的 Room Entity，字段与设计规范 §2.3 完全一致：
- uuid (String, @PrimaryKey)
- parentUuid (String?)
- name (String)
- hash (String) — SHA-256，目录为 ""
- mimeType (String?)
- sizeBytes (Long, default 0)
- isDirectory (Boolean, default false)
- physicalRootPath (String)
- materializedPath (String)
- vectorizedAt (Long?)
- vectorVersion (Int, default 1)
- kgExtractedAt (Long?)
- kgVersion (Int, default 1)
- lastWriteSessionId (String?)
- lockedBySessionId (String?)
- lockExpiresAt (Long?)
- inRecycleBin (Boolean, default false)
- recycledAt (Long?)
- originalParentUuid (String?)
- originalMaterializedPath (String?)
- createdAt (Long)
- updatedAt (Long)

索引：parent_uuid / materialized_path / hash / is_directory / (in_recycle_bin, physical_root_path, recycled_at)

### 2. FileEntryDao.kt
路径: app/src/main/java/com/promenar/nexara/data/local/db/dao/FileEntryDao.kt

Room DAO 接口，包含方法：
- @Insert suspend fun insert(entry: FileEntry)
- @Update suspend fun update(entry: FileEntry)
- @Delete suspend fun delete(entry: FileEntry)
- @Query getByUuid(uuid: String): FileEntry?
- @Query observeByUuid(uuid: String): Flow<FileEntry?>
- @Query observeChildren(parentUuid: String): Flow<List<FileEntry>> — 按 is_directory DESC, name ASC 排序
- @Query observeRoots(): Flow<List<FileEntry>> — WHERE parent_uuid IS NULL AND in_recycle_bin = 0
- @Query observeRecycleBin(physicalRootPath: String): Flow<List<FileEntry>> — WHERE in_recycle_bin = 1 AND physical_root_path = :physicalRootPath ORDER BY recycled_at DESC
- @Query getByMaterializedPath(path: String): FileEntry?
- @Query searchByName(query: String): Flow<List<FileEntry>> — name LIKE '%' || :query || '%'
- @Query getSubtree(prefix: String): List<FileEntry> — materialized_path LIKE :prefix || '%' AND in_recycle_bin = 0
- @Transaction + @Query deleteByUuid(uuid: String) — 物理删除

### 3. WorkspaceSeqDao.kt
路径: app/src/main/java/com/promenar/nexara/data/local/db/dao/WorkspaceSeqDao.kt

```kotlin
@Dao
interface WorkspaceSeqDao {
    // 原子 UPSERT：INSERT OR UPDATE last_seq + 1
    @Query("""
        INSERT INTO workspace_seq(date_key, last_seq) 
        VALUES(:dateKey, 1)
        ON CONFLICT(date_key) 
        DO UPDATE SET last_seq = last_seq + 1
    """)
    suspend fun upsertAndIncrement(dateKey: String)
    
    // 读取当前序号
    @Query("SELECT last_seq FROM workspace_seq WHERE date_key = :dateKey")
    suspend fun getSeq(dateKey: String): Int?
    
    // 事务方法：原子 UPSERT + 读取
    @Transaction
    suspend fun getNextSeqForDate(dateKey: String): Int {
        upsertAndIncrement(dateKey)
        return getSeq(dateKey) ?: 1
    }
}
```

### 4. NexaraDatabase.kt — 修改
版本号: 7 → 8

新增 Migration 7→8：
```sql
-- workspace_files 表
CREATE TABLE workspace_files (
    uuid TEXT PRIMARY KEY NOT NULL,
    parent_uuid TEXT, name TEXT NOT NULL, hash TEXT NOT NULL,
    mime_type TEXT, size_bytes INTEGER NOT NULL DEFAULT 0,
    is_directory INTEGER NOT NULL DEFAULT 0,
    physical_root_path TEXT NOT NULL, materialized_path TEXT NOT NULL,
    vectorized_at INTEGER, vector_version INTEGER NOT NULL DEFAULT 1,
    kg_extracted_at INTEGER, kg_version INTEGER NOT NULL DEFAULT 1,
    last_write_session_id TEXT, locked_by_session_id TEXT, lock_expires_at INTEGER,
    in_recycle_bin INTEGER NOT NULL DEFAULT 0, recycled_at INTEGER,
    original_parent_uuid TEXT, original_materialized_path TEXT,
    created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE INDEX idx_wf_parent ON workspace_files(parent_uuid);
CREATE INDEX idx_wf_materialized_path ON workspace_files(materialized_path);
CREATE INDEX idx_wf_hash ON workspace_files(hash);
CREATE INDEX idx_wf_is_directory ON workspace_files(is_directory);
CREATE INDEX idx_wf_recycle_bin ON workspace_files(in_recycle_bin, physical_root_path, recycled_at);

-- 序号计数器表
CREATE TABLE workspace_seq (
    date_key TEXT PRIMARY KEY NOT NULL,
    last_seq INTEGER NOT NULL
);

-- Session 表新增 workspace_root_uuid 列
ALTER TABLE sessions ADD COLUMN workspace_root_uuid TEXT;
```

在类体中新增 DAO 声明：
```kotlin
abstract fun fileEntryDao(): FileEntryDao
abstract fun workspaceSeqDao(): WorkspaceSeqDao
```

### 5. SessionEntity.kt — 修改
在现有字段列表末尾（updatedAt 之前）新增：
```kotlin
@ColumnInfo(name = "workspace_root_uuid")
val workspaceRootUuid: String? = null,
```

## 验证标准
1. 编译通过: `./gradlew compileDebugKotlin`
2. 所有新建文件包路径正确，导入正确
3. Migration 7→8 SQL 语法正确
4. SessionEntity 新字段与 Migration 中 ALTER TABLE 一致

## 注意事项
- 使用 kotlinx.serialization 标注（如需序列化）
- 保持与现有 Entity 相同的代码风格（注释格式、@ColumnInfo 命名规范）
- 不要修改任何功能性代码，仅执行上述创建/修改
- 完成任务后报告"Session 1 完成"并提供编译验证结果
```

---

## Session 2: 域接口 + 旧表扩展

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 2（依赖 Session 1 完成，可与 Session 3 并行） |
| **目标** | 定义 Domain 层 Repository 接口 + Vector/KG Entity 扩展 stale/file_uuid 字段 |
| **创建文件** | 2 个 |
| **修改文件** | 4 个 |
| **预估** | ~1h |

### 复制此提示词启动会话

```
# 任务：统一资源 OS 域接口 + 旧表扩展

## 上下文
你正在为 Nexara 项目的统一资源操作系统编写 Domain 层接口，同时扩展已有 Vector/KG Entity。请先读取设计规范 §7.1（架构分层映射中 IWorkspaceRepository / IFileOperationRepository 接口定义）、§5.3（向量/KG 表扩展）、§3.3（Diff/Patch 协议）、§4.2（乐观锁写入协议）：

设计规范文件: /Users/promenar/Library/Application Support/CodeBuddy CN/User/globalStorage/tencent-cloud.coding-copilot/brain/e3245ce0f1d14a83a5e79ea4bb9e90b9/20260515-unified-resource-os-design-spec.md

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 必须读取的现有文件
1. app/src/main/java/com/promenar/nexara/domain/repository/IDocumentRepository.kt — 接口风格参考
2. app/src/main/java/com/promenar/nexara/data/local/db/entity/VectorEntity.kt — 当前字段
3. app/src/main/java/com/promenar/nexara/data/local/db/entity/KgNodeEntity.kt — 当前字段（若存在）
4. app/src/main/java/com/promenar/nexara/data/local/db/entity/KgEdgeEntity.kt — 当前字段（若存在）

## 需要创建的文件

### 1. IWorkspaceRepository.kt
路径: app/src/main/java/com/promenar/nexara/domain/repository/IWorkspaceRepository.kt

Domain 层接口，定义以下方法（返回类型使用 Flow / suspend）：

```kotlin
interface IWorkspaceRepository {
    fun observeRoots(): Flow<List<FileEntry>>
    fun observeChildren(parentUuid: String): Flow<List<FileEntry>>
    fun observeRecycleBin(workspaceRootUuid: String): Flow<List<FileEntry>>
    suspend fun getByUuid(uuid: String): FileEntry?
    suspend fun createFile(
        uuid: String, name: String, content: String,
        parentUuid: String?, physicalRootPath: String,
        materializedPath: String
    ): FileEntry
    suspend fun createDirectory(
        uuid: String, name: String,
        parentUuid: String?, physicalRootPath: String,
        materializedPath: String
    ): FileEntry
    suspend fun moveToRecycleBin(uuid: String)
    suspend fun restoreFromRecycleBin(uuid: String)
    suspend fun permanentDelete(uuid: String)
    suspend fun emptyRecycleBin(workspaceRootUuid: String)
    suspend fun updateParent(uuid: String, newParentUuid: String)
    suspend fun getNextSeqForDate(dateKey: String): Int
}
```

### 2. IFileOperationRepository.kt
路径: app/src/main/java/com/promenar/nexara/domain/repository/IFileOperationRepository.kt

```kotlin
interface IFileOperationRepository {
    suspend fun writeFileAtomic(
        uuid: String, newContent: String,
        sessionId: String, expectedHash: String
    ): WriteResult
    
    suspend fun readFileRange(
        uuid: String, startLine: Int? = null, endLine: Int? = null
    ): ReadResult
    
    suspend fun diffFile(
        uuid: String, basisHash: String? = null
    ): DiffResult
    
    suspend fun patchFile(
        uuid: String, operations: List<PatchOperation>,
        expectedHash: String
    ): PatchResult
}

// —— Result Types ——
sealed class WriteResult {
    data class Success(val newHash: String) : WriteResult()
    data class Conflict(val currentHash: String, val expectedHash: String, val message: String) : WriteResult()
    object NotFound : WriteResult()
}

data class ReadResult(
    val uuid: String, val name: String, val totalLines: Int,
    val startLine: Int, val endLine: Int,
    val content: String, val hash: String, val lastModified: Long
)

data class DiffResult(
    val uuid: String, val basisHash: String, val currentHash: String,
    val hunks: List<DiffHunk>
)

data class DiffHunk(
    val oldStart: Int, val oldCount: Int,
    val newStart: Int, val newCount: Int,
    val lines: List<DiffLine>
)

data class DiffLine(val type: String, val content: String)  // "context" | "removed" | "added"

data class PatchOperation(
    val action: String,  // "replace_lines" | "insert_after" | "delete_lines"
    val startLine: Int? = null, val endLine: Int? = null,
    val afterLine: Int? = null, val newContent: String? = null
)

sealed class PatchResult {
    data class Success(val newHash: String, val appliedOperations: Int) : PatchResult()
    data class Failure(val error: PatchError) : PatchResult()
}

data class PatchError(
    val code: String, val message: String,
    val operationIndex: Int, val fileUuid: String,
    val totalLines: Int? = null, val suggestion: String? = null
)
```

## 需要修改的文件

### 3. VectorEntity.kt — 新增字段
在现有字段中新增：
```kotlin
@ColumnInfo(name = "stale") val stale: Boolean = false,
@ColumnInfo(name = "version") val version: Int = 1,
@ColumnInfo(name = "file_uuid") val fileUuid: String? = null,
```

### 4. VectorDao.kt — 新增查询方法（若存在，定位文件后修改）
新增方法：
```kotlin
@Query("SELECT * FROM vectors WHERE file_uuid = :fileUuid AND stale = 0 ORDER BY chunk_index")
suspend fun getActiveChunks(fileUuid: String): List<VectorEntity>

@Query("DELETE FROM vectors WHERE stale = 1 AND updated_at < :cutoff")
suspend fun cleanupStaleChunks(cutoff: Long)
```

### 5. KgNodeEntity.kt — 新增字段（若存在）
```kotlin
@ColumnInfo(name = "stale") val stale: Boolean = false,
@ColumnInfo(name = "file_uuid") val fileUuid: String? = null,
```

### 6. KgEdgeEntity.kt — 新增字段（若存在）
```kotlin
@ColumnInfo(name = "stale") val stale: Boolean = false,
@ColumnInfo(name = "file_uuid") val fileUuid: String? = null,
```

## 注意事项
- IWorkspaceRepository / IFileOperationRepository 放在 domain/repository/ 目录（与 IDocumentRepository 同级）
- Result types 可以放在与接口同一文件，或新建 domain/model/ 下的独立文件
- Vector/KG Entity 新增字段保持与现有字段一致的注解风格
- 不要实做 Repository 实现类（留给 Session 4）
- 搜索 "kg_node" 或 "kg_edge" 确认实际的 Entity 类名，可能是 KgNode 或 KgNodeEntity
- 若某个 Entity 文件不存在（如 kg_nodes 表尚未创建），跳过该文件的修改，只修改存在的
```

---

## Session 3: 基础设施工具

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 2（可与 Session 2 并行） |
| **目标** | 实现 MyersDiff 引擎 + SHA256 工具 + SAF 导入 Handler 骨架 |
| **创建文件** | 3 个 |
| **预估** | ~1h |

### 复制此提示词启动会话

```
# 任务：统一资源 OS 基础设施工具

## 上下文
为 Nexara 项目实现基础工具层：Myers Diff 引擎、SHA256 工具、SAF 导入 Handler。请先读取设计规范 §3.3（Diff/Patch JSON 协议）、§5.1（Hash-Triggered 自动重索引）：

设计规范文件: /Users/promenar/Library/Application Support/CodeBuddy CN/User/globalStorage/tencent-cloud.coding-copilot/brain/e3245ce0f1d14a83a5e79ea4bb9e90b9/20260515-unified-resource-os-design-spec.md

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 需要创建的文件

### 1. MyersDiff.kt
路径: app/src/main/java/com/promenar/nexara/infra/util/MyersDiff.kt

纯 Kotlin 实现（无外部依赖），需求：
- 输入两个字符串列表 `List<String>`（按行分割）
- 输出 `List<DiffLine>`，每项含 type("context"|"removed"|"added") 和 content
- 使用经典 Myers 算法 O(ND)
- 输出合并连续的 context 行（不超过 3 行 context 间隔）
- 提供扩展函数 `String.lineDiff(other: String): List<DiffLine>`

关键方法签名：
```kotlin
object MyersDiff {
    data class DiffLine(val type: String, val content: String)
    data class DiffHunk(
        val oldStart: Int, val oldCount: Int,
        val newStart: Int, val newCount: Int,
        val lines: List<DiffLine>
    )
    
    fun compute(oldLines: List<String>, newLines: List<String>): List<DiffLine>
    fun computeHunks(oldLines: List<String>, newLines: List<String>, contextLines: Int = 3): List<DiffHunk>
}

fun String.lineDiff(other: String): List<MyersDiff.DiffLine> {
    return MyersDiff.compute(this.lines(), other.lines())
}
```

### 2. Sha256Utils.kt
路径: app/src/main/java/com/promenar/nexara/infra/util/Sha256Utils.kt

```kotlin
object Sha256Utils {
    fun hash(content: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    
    fun hashFile(file: java.io.File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
```

### 3. SafImportHandler.kt
路径: app/src/main/java/com/promenar/nexara/infra/fs/SafImportHandler.kt

```kotlin
class SafImportHandler(private val context: Context) {
    /**
     * 使用 SAF (Storage Access Framework) 打开目录选择器
     * 返回用户选择的目录 URI，用于外部 workspace 导入
     */
    fun createOpenDirectoryIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                     Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                     Intent.FLAG_GRANT_PERSISTABLE_URI_PERISSION)
        }
    }
    
    /**
     * 持久化 URI 权限（跨进程重启）
     */
    fun persistUriPermission(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }
    
    /**
     * 从 SAF URI 递归扫描文件树，返回 FileEntry 列表
     * TODO: 完整实现在 Phase R3
     */
    suspend fun scanSafDirectory(uri: Uri, parentUuid: String?, physicalRootPath: String): List<FileEntry> {
        // 骨架实现：返回空列表，标记 TODO
        return emptyList()
    }
}
```

## 验证标准
1. 创建 `MyersDiffTest.kt` 测试文件在 `app/src/test/.../infra/util/` 下：
   - 测试相同内容 → 全部 context
   - 测试单行变更 → 1 removed + 1 added
   - 测试多行插入/删除
   - 测试空文件
2. `./gradlew testDebugUnitTest --tests "*MyersDiffTest"` 通过
3. 编译通过

## 注意事项
- MyersDiff 算法实现必须正确，这是 diff/patch 工具链的核心
- 测试文件必须包含至少 4 个测试用例
- 不要引入第三方 diff 库
```

---

## Session 4: Repository 实现全量

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 3（依赖 Session 1+2+3 全部完成） |
| **目标** | 实现 WorkspaceRepository + FileOperationRepository（含乐观锁 + 重 RAG 触发） |
| **创建文件** | 2 个 |
| **预估** | ~1.5h |

### 复制此提示词启动会话

```
# 任务：统一资源 OS Repository 实现

## 上下文
为 Nexara 项目实现两个核心 Repository：WorkspaceRepository（资源树 CRUD + 回收站）和 FileOperationRepository（原子写入 + 乐观锁 + diff/patch）。

必须完整读取：
设计规范文件: /Users/promenar/Library/Application Support/CodeBuddy CN/User/globalStorage/tencent-cloud.coding-copilot/brain/e3245ce0f1d14a83a5e79ea4bb9e90b9/20260515-unified-resource-os-design-spec.md

重点阅读：§4.2（乐观锁写入协议）、§5.2.0（回收站数据模型映射）、§2.2（物化路径方案）

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 前置确认
以下接口必须在 Session 1+2 中已完成创建，如果不存在则停止并报告：
1. FileEntry Entity 类存在且可编译
2. FileEntryDao 接口存在
3. WorkspaceSeqDao 接口存在
4. IWorkspaceRepository 接口存在
5. IFileOperationRepository + Result types 存在
6. MyersDiff / Sha256Utils 类存在

## 需要创建的文件

### 1. WorkspaceRepository.kt
路径: app/src/main/java/com/promenar/nexara/data/repository/WorkspaceRepository.kt

实现 IWorkspaceRepository，构造函数注入 `FileEntryDao` 和 `WorkspaceSeqDao`。

关键实现：
- `createFile(uuid, name, content, parentUuid, physicalRootPath, materializedPath)`: 
  1. 计算 SHA-256 hash
  2. 创建物理文件: `File(physicalRootPath + materializedPath).writeText(content)`
  3. 创建 FileEntry 并插入数据库
  4. 返回 FileEntry
  
- `createDirectory(...)`: 
  1. 创建物理目录: `File(physicalRootPath + materializedPath).mkdirs()`
  2. 创建 FileEntry（hash=""，isDirectory=true）并插入

- `moveToRecycleBin(uuid)`:
  1. 获取 FileEntry
  2. 通过 materializedPath 前缀变换计算回收站目标路径
  3. 物理文件 rename
  4. 更新 DB: inRecycleBin=true, recycledAt=now, originalParentUuid=原值, originalMaterializedPath=原值, parentUuid 指向 .recycle_bin 目录, materializedPath 更新为回收站路径

- `restoreFromRecycleBin(uuid)`: 逆向操作

- `permanentDelete(uuid)`: 物理文件删除 + DB 级联删除

- `emptyRecycleBin(workspaceRootUuid)`: 获取该 workspace 下所有 inRecycleBin=true 的文件，逐个 permanentDelete

- `updateParent(uuid, newParentUuid)`: 移动文件/目录 → 更新 parentUuid + 重新计算 materializedPath（对子树递归更新）

- `getNextSeqForDate(dateKey)`: 委托给 WorkspaceSeqDao.getNextSeqForDate()

使用 @Inject 标注构造函数（若项目使用 Hilt/Koin；否则使用手动 DI 注册在 NexaraApplication 中）。

### 2. FileOperationRepository.kt
路径: app/src/main/java/com/promenar/nexara/data/repository/FileOperationRepository.kt

实现 IFileOperationRepository，构造函数注入 `FileEntryDao`。

关键实现：

**writeFileAtomic(uuid, newContent, sessionId, expectedHash)**：
按照设计规范 §4.2 的乐观锁写入协议实现：
```
1. db.withTransaction {
2.   val entry = dao.getByUuid(uuid) ?: return NotFound
3.   if (entry.hash != expectedHash) return Conflict(...)
4.   val newHash = sha256(newContent)
5.   物理写入: File(entry.physicalRootPath + entry.materializedPath).writeText(newContent)
6.   dao.update(entry.copy(hash=newHash, sizeBytes=..., lastWriteSessionId=sessionId, updatedAt=now))
7.   if (hash 变更 && entry.vectorizedAt != null) → 触发异步重索引（TODO: 调用向量化队列，后续接入）
8.   return Success(newHash)
```

**readFileRange(uuid, startLine?, endLine?)**：
- 读取物理文件
- 按行分割，截取 [startLine-1, endLine) 范围
- 返回 ReadResult（含 totalLines, hash, 等）

**diffFile(uuid, basisHash?)**：
- 用 basisHash 确定对比基准（若无则与当前 hash 对比）
- 读取文件内容 → MyersDiff.computeHunks()
- 返回 DiffResult

**patchFile(uuid, operations, expectedHash)**：
- 先校验 expectedHash（乐观锁）
- 读取文件所有行 → 逐个应用 PatchOperation
- 处理 LINE_OUT_OF_RANGE / HASH_MISMATCH 等错误
- 成功后写入新内容并更新 DB
- 返回 PatchResult

## 验证标准
1. 编译通过: `./gradlew compileDebugKotlin`
2. 若已注入 DI，确保应用启动不崩溃
3. 关键逻辑（路径变换、hash 计算、乐观锁判断）正确

## 注意事项
- 使用 kotlinx.coroutines.Dispatchers.IO 执行文件 IO
- 所有文件写入用 withContext(Dispatchers.IO) 包裹
- 更新 materializedPath 时注意同时更新物理文件路径（rename）
- 暂不接入真实向量化队列（标记 TODO），但保留接口
```

---

## Session 5: Skill 工具升级

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 4（依赖 Session 4，可与 Session 6 并行） |
| **目标** | 重写 6 个文件操作 Skill，支持 UUID 锚定 + 分页读取 + JSON diff/patch + 错误回馈 |
| **创建文件** | 6 个 Skill 文件 |
| **修改文件** | 2 个（SkillRegistry, ChatScreen/ToolExecutor） |
| **预估** | ~1.5h |

### 复制此提示词启动会话

```
# 任务：统一资源 OS Skill 工具升级（6 个文件操作 Skill）

## 上下文
重写 Nexara 的文件操作 Skill 系统，从物理路径模式升级为 UUID 锚定 + 分页/行范围读取 + JSON diff/patch 工具链。请完整阅读以下设计规范：

设计规范文件: /Users/promenar/Library/Application Support/CodeBuddy CN/User/globalStorage/tencent-cloud.coding-copilot/brain/e3245ce0f1d14a83a5e79ea4bb9e90b9/20260515-unified-resource-os-design-spec.md

重点阅读：§3.1（工具能力矩阵）、§3.2（Read 协议）、§3.3（Diff/Patch JSON 协议、错误回馈矩阵）、§5.1（Hash-Triggered 自动重 RAG）

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 必须读取的现有文件
1. app/src/main/java/com/promenar/nexara/ui/chat/manager/registry/SkillRegistry.kt — SkillDefinition 接口
2. app/src/main/java/com/promenar/nexara/ui/chat/manager/ToolExecutor.kt — SkillExecutionContext
3. 搜索现有的 File*Skill.kt 文件（如 FileReadSkill.kt, FileWriteSkill.kt 等）— 了解现有 Skill 结构

## Common Imports for All Skills
```kotlin
import com.promenar.nexara.domain.repository.IFileOperationRepository
import com.promenar.nexara.domain.repository.IWorkspaceRepository
import com.promenar.nexara.domain.repository.WriteResult
import com.promenar.nexara.domain.repository.ReadResult
import com.promenar.nexara.domain.repository.DiffResult
import com.promenar.nexara.domain.repository.PatchResult
import com.promenar.nexara.domain.repository.PatchOperation
import com.promenar.nexara.infra.util.Sha256Utils
```

所有 Skill 放在: app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/

## 需要创建的 6 个 Skill

### 1. FileReadSkill.kt
路径: .../skills/FileReadSkill.kt

```kotlin
class FileReadSkill(
    private val workspaceRepo: IWorkspaceRepository,
    private val fileOpRepo: IFileOperationRepository
) : SkillDefinition {
    override val id = "read_file"
    override val name = "读取文件"
    override val description = "读取工作区文件内容。支持分页（offset/limit）和行号范围（startLine/endLine）两种模式。"
    override val mcpServerId = null
    override val parametersSchema = """{
        "type": "object",
        "properties": {
            "uuid": {"type": "string", "description": "文件UUID"},
            "mode": {"type": "string", "enum": ["page", "range"], "default": "page"},
            "offset": {"type": "integer", "description": "分页偏移(行号)"},
            "limit": {"type": "integer", "description": "分页大小(行数)", "default": 200},
            "startLine": {"type": "integer", "description": "起始行号(1-based)"},
            "endLine": {"type": "integer", "description": "结束行号(1-based)"}
        },
        "required": ["uuid"]
    }"""
    
    override suspend fun execute(args: Map<String, Any>, context: SkillExecutionContext): ToolResult {
        val uuid = args["uuid"] as? String ?: return ToolResult.error("缺少 uuid 参数")
        val mode = args["mode"] as? String ?: "page"
        
        val result = when (mode) {
            "range" -> {
                val start = (args["startLine"] as? Number)?.toInt()
                val end = (args["endLine"] as? Number)?.toInt()
                fileOpRepo.readFileRange(uuid, start, end)
            }
            else -> {
                val offset = (args["offset"] as? Number)?.toInt() ?: 0
                val limit = (args["limit"] as? Number)?.toInt() ?: 200
                fileOpRepo.readFileRange(uuid, offset + 1, offset + limit)
            }
        }
        
        return ToolResult.success(
            buildString {
                appendLine("文件: ${result.name}")
                appendLine("行数: ${result.totalLines} (返回 ${result.startLine}-${result.endLine})")
                appendLine("Hash: ${result.hash}")
                appendLine("---")
                append(result.content)
            }
        )
    }
}
```

### 2. FileWriteSkill.kt
路径: .../skills/FileWriteSkill.kt

```kotlin
class FileWriteSkill(
    private val workspaceRepo: IWorkspaceRepository,
    private val fileOpRepo: IFileOperationRepository
) : SkillDefinition {
    override val id = "write_file"
    override val name = "写入文件"
    override val description = "将内容写入工作区文件（全量覆盖）。自动进行乐观锁冲突检测。"
    override val mcpServerId = null
    override val parametersSchema = """{
        "type": "object",
        "properties": {
            "uuid": {"type": "string", "description": "目标文件UUID"},
            "content": {"type": "string", "description": "要写入的完整内容"},
            "expectedHash": {"type": "string", "description": "文件的当前hash(乐观锁)"}
        },
        "required": ["uuid", "content", "expectedHash"]
    }"""
    
    override suspend fun execute(args: Map<String, Any>, context: SkillExecutionContext): ToolResult {
        val uuid = args["uuid"] as? String ?: return ToolResult.error("缺少 uuid")
        val content = args["content"] as? String ?: return ToolResult.error("缺少 content")
        val expectedHash = args["expectedHash"] as? String ?: return ToolResult.error("缺少 expectedHash")
        
        return when (val result = fileOpRepo.writeFileAtomic(uuid, content, context.sessionId, expectedHash)) {
            is WriteResult.Success -> ToolResult.success("写入成功。新 Hash: ${result.newHash}")
            is WriteResult.Conflict -> ToolResult.error(
                "写入冲突！文件已被其他会话修改。当前 Hash: ${result.currentHash}，你的基准: ${result.expectedHash}" +
                "。请先调用 read_file 获取最新内容，或调用 diff_file 查看差异后重新写入。"
            )
            is WriteResult.NotFound -> ToolResult.error("文件不存在: $uuid")
        }
    }
}
```

### 3. FileDiffSkill.kt
路径: .../skills/FileDiffSkill.kt

```kotlin
class FileDiffSkill(
    private val fileOpRepo: IFileOperationRepository
) : SkillDefinition {
    override val id = "diff_file"
    override val name = "文件对比"
    override val description = "生成文件的差异报告（JSON格式）。可用于查看文件变更情况。"
    override val mcpServerId = null
    override val parametersSchema = """{
        "type": "object",
        "properties": {
            "uuid": {"type": "string", "description": "文件UUID"},
            "basisHash": {"type": "string", "description": "对比基准hash(可选，默认与上次已知版本对比)"}
        },
        "required": ["uuid"]
    }"""
    
    override suspend fun execute(args: Map<String, Any>, context: SkillExecutionContext): ToolResult {
        val uuid = args["uuid"] as? String ?: return ToolResult.error("缺少 uuid")
        val basisHash = args["basisHash"] as? String
        
        val result = fileOpRepo.diffFile(uuid, basisHash)
        return ToolResult.success(
            buildString {
                appendLine("{")
                appendLine("  \"uuid\": \"${result.uuid}\",")
                appendLine("  \"basisHash\": \"${result.basisHash}\",")
                appendLine("  \"currentHash\": \"${result.currentHash}\",")
                appendLine("  \"hunks\": [")
                result.hunks.forEachIndexed { i, hunk ->
                    appendLine("    {")
                    appendLine("      \"oldStart\": ${hunk.oldStart}, \"oldCount\": ${hunk.oldCount},")
                    appendLine("      \"newStart\": ${hunk.newStart}, \"newCount\": ${hunk.newCount},")
                    appendLine("      \"lines\": [")
                    hunk.lines.forEach { line ->
                        appendLine("        {\"type\": \"${line.type}\", \"content\": ${escapeJson(line.content)}},")
                    }
                    appendLine("      ]")
                    append("    }${if (i < result.hunks.lastIndex) "," else ""}")
                }
                appendLine("  ]")
                append("}")
            }
        )
    }
    
    private fun escapeJson(s: String): String = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
```

### 4. FilePatchSkill.kt
路径: .../skills/FilePatchSkill.kt

按照设计规范 §3.3 的 error feedback matrix 实现。核心逻辑：
- 解析 args["operations"] JSON 为 List<PatchOperation>
- 调用 fileOpRepo.patchFile()
- 成功返回结果摘要
- 失败时根据 PatchError.code 返回详细修正指导（参考规范 §3.3 错误回馈矩阵的 6 种错误码）

### 5. FileSearchSkill.kt
路径: .../skills/FileSearchSkill.kt

```kotlin
class FileSearchSkill(
    private val workspaceRepo: IWorkspaceRepository
) : SkillDefinition {
    override val id = "search_files"
    override val name = "搜索文件"
    override val description = "在工作区中搜索文件（文件名匹配 + 全文 FTS5 搜索）。"
    override val mcpServerId = null
    override val parametersSchema = """{
        "type": "object",
        "properties": {
            "query": {"type": "string", "description": "搜索关键词"},
            "mode": {"type": "string", "enum": ["name", "fts"], "default": "name"}
        },
        "required": ["query"]
    }"""
    
    override suspend fun execute(args: Map<String, Any>, context: SkillExecutionContext): ToolResult {
        val query = args["query"] as? String ?: return ToolResult.error("缺少 query")
        val mode = args["mode"] as? String ?: "name"
        
        // TODO: 接入 FileEntryDao.searchByName() / FTS5
        return ToolResult.success("搜索完成: 找到 N 个结果")
    }
}
```

### 6. FileListSkill.kt
路径: .../skills/FileListSkill.kt

```kotlin
class FileListSkill(
    private val workspaceRepo: IWorkspaceRepository
) : SkillDefinition {
    override val id = "list_files"
    override val name = "列出文件"
    override val description = "列出工作区指定目录下的文件和子目录。"
    override val mcpServerId = null
    override val parametersSchema = """{
        "type": "object",
        "properties": {
            "parentUuid": {"type": "string", "description": "父目录UUID(不传则列出根目录)"}
        }
    }"""
    
    override suspend fun execute(args: Map<String, Any>, context: SkillExecutionContext): ToolResult {
        val parentUuid = args["parentUuid"] as? String
        val children = if (parentUuid != null) {
            workspaceRepo.observeChildren(parentUuid) // 一次性获取
        } else {
            workspaceRepo.observeRoots()
        }
        // 注意: Flow 需要 collect 一次
        // 直接用 suspend 方法: workspaceRepo.getChildren(parentUuid)
        return ToolResult.success("目录内容: ...")
    }
}
```

## 需要修改的文件

### SkillRegistry.kt
在 getAllSkills() 或等价方法中注册新的 6 个 Skill（替换旧的 FileRead/Write/Search/List）。

### ToolExecutor.kt
确保 SkillExecutionContext 包含新 Skill 需要的上下文（目前已有 sessionId, agentId, workspacePath，可能需要新增 workspaceRootUuid）。

## 验证标准
1. 编译通过
2. 6 个 Skill 的 parametersSchema 为合法 JSON
3. 每个 Skill 的 execute 方法正确处理参数缺失情况

## 注意事项
- 使用 kotlinx.serialization 解析 JSON（项目中已有）
- SkillRegistry 中替换旧 Skill，不要新旧并存
- FilePatchSkill 的错误回馈必须完整实现 6 种错误码
```

---

## Session 6: UI 全量 + Worker

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 4（依赖 Session 4，可与 Session 5 并行） |
| **目标** | 创建 ResourceExplorerSheet + FilesPanel + RecycleBinPanel + IndexStatusBadge + ViewModel，修改 RagHomeScreen 门户 |
| **创建文件** | 5 个 |
| **修改文件** | 2 个 |
| **预估** | ~1.5h |

### 复制此提示词启动会话

```
# 任务：统一资源 OS UI 全量实现

## 上下文
为 Nexara 项目实现统一资源管理器的全部 UI 组件，同时改造 Library Tab 的门户导航。所有 UI 必须复用项目已有的 Material3 组件和 Nexara 系列组件。请完整阅读：

设计规范文件: /Users/promenar/Library/Application Support/CodeBuddy CN/User/globalStorage/tencent-cloud.coding-copilot/brain/e3245ce0f1d14a83a5e79ea4bb9e90b9/20260515-unified-resource-os-design-spec.md

重点阅读：§12（视觉 UI 设计说明）— 重点看 §12.0（Library Tab 门户）、§12.2（整体布局）、§12.3（文件树面板）、§12.4（回收站面板）、§12.5（索引状态徽标）、§12.6-12.7（色彩/间距）

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 必须读取的现有文件（先读再写）
1. app/src/main/java/com/promenar/nexara/ui/common/NexaraBottomSheet.kt — 容器组件
2. app/src/main/java/com/promenar/nexara/ui/common/NexaraGlassCard.kt — 卡片组件
3. app/src/main/java/com/promenar/nexara/ui/common/NexaraSearchBar.kt — 搜索栏
4. app/src/main/java/com/promenar/nexara/ui/common/SwipeableItem.kt — 滑动操作
5. app/src/main/java/com/promenar/nexara/ui/common/NexaraConfirmDialog.kt — 确认弹窗
6. app/src/main/java/com/promenar/nexara/ui/common/NexaraSnackbar.kt — 反馈提示
7. app/src/main/java/com/promenar/nexara/ui/rag/components/RagStatusChip.kt — 状态徽标模板
8. app/src/main/java/com/promenar/nexara/ui/theme/Color.kt — 完整色值
9. app/src/main/java/com/promenar/nexara/ui/chat/WorkspaceSheet.kt — 参考旧版布局
10. app/src/main/java/com/promenar/nexara/ui/rag/RagHomeScreen.kt — PortalView 枚举和 TAB 实现
11. app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt — 找到 showWorkspaceSheet 和 WorkspaceSheet 调用的位置

## 需要创建的文件

### 1. IndexStatusBadge.kt
路径: app/src/main/java/com/promenar/nexara/ui/common/IndexStatusBadge.kt

按照设计规范 §12.5 实现。5 种状态：INDEXED(绿) / INDEXING(蓝+脉冲) / STALE(黄) / NOT_INDEXED(灰) / FAILED(红)。复用 NexaraColors.RagReady/RagIndexing/StatusWarning/RagPending/RagError。视觉模式与 RagStatusChip 一致（圆角 50% + 半透明背景 + 8dp 圆点 + labelSmall 文字）。

### 2. FilesPanel.kt
路径: app/src/main/java/com/promenar/nexara/ui/chat/components/FilesPanel.kt

实现 FileTreeNode 递归组件 + FileRow 文件行，按照设计规范 §12.3：
- LazyColumn + key=uuid + rememberSaveable expanded 状态
- 默认展开 2 层深度
- FileRow 使用 NexaraGlassCard 包裹
- 文件图标 24dp（Folder/FolderOpen/Description 等 M3 Icons.Rounded）
- 文件名: NexaraTypography.bodyLarge, OnSurface
- 元数据: NexaraTypography.labelSmall, OnSurfaceVariant（格式: "12 KB · 3h ago"）
- 右侧 IndexStatusBadge
- 所有数据从 IWorkspaceRepository.observeChildren / observeRoots 获取
- 搜索功能通过 NexaraSearchBar + name LIKE 过滤

### 3. RecycleBinPanel.kt
路径: app/src/main/java/com/promenar/nexara/ui/chat/components/RecycleBinPanel.kt

按照设计规范 §12.4 实现：
- 顶部操作栏: 恢复 / 永久删除 / 清空全部 三个按钮
- 清空全部用 NexaraColors.Error 色
- 回收站文件卡片: 显示回收日期 + 原始路径 + 回收前索引状态
- 原始路径用 labelSmall, OnSurfaceVariant
- 底部提示: "30 天后自动清理"
- 恢复调用 workspaceRepo.restoreFromRecycleBin()
- 永久删除先弹出 NexaraConfirmDialog → workspaceRepo.permanentDelete()
- 清空全部先弹出确认 → workspaceRepo.emptyRecycleBin()

### 4. ResourceExplorerSheet.kt
路径: app/src/main/java/com/promenar/nexara/ui/chat/ResourceExplorerSheet.kt

整体容器：复用 NexaraBottomSheet，title="资源管理器"。
内部结构：
```kotlin
NexaraBottomSheet(show, onDismiss, title = "资源管理器") {
    NexaraSearchBar(query, onQueryChange, placeholder = "搜索文件...")
    TabRow(selectedTabIndex) {
        Tab(selected, onClick) { Text("文件") }
        Tab(selected, onClick) { Text("回收站", 带计数 badge) }
    }
    HorizontalPager(pagerState) { page ->
        when (page) {
            0 -> FilesPanel(workspaceRootUuid, fileRepo)
            1 -> RecycleBinPanel(workspaceRootUuid, fileRepo)
        }
    }
}
```

### 5. ResourceExplorerViewModel.kt
路径: app/src/main/java/com/promenar/nexara/ui/chat/components/ResourceExplorerViewModel.kt

管理 UI 状态：selectedTab, searchQuery, workspaceRootUuid。

## 需要修改的文件

### 6. RagHomeScreen.kt — 门户导航精简
定位到 PortalView 枚举（约第 97 行）和三个大卡片按钮（约第 208-296 行），按照设计规范 §12.0 改造：

**替换内容**：
将当前的三按钮 NexaraGlassCard 布局替换为 M3 TabRow + Tab：

```kotlin
// 删除: PortalView enum, currentView, 三个 NexaraGlassCard 按钮 (约 208-291 行)
// 删除: 统计数字渲染 (stats.documentCount 等)

// 新增: 在内容区上方
TabRow(
    selectedTabIndex = currentTab.ordinal,
    containerColor = Color.Transparent,
    divider = { HorizontalDivider(color = NexaraColors.OutlineVariant) }
) {
    listOf(
        PortalTab.DOCUMENTS to (Icons.Rounded.Description to "文件"),
        PortalTab.MEMORY to (Icons.Rounded.Psychology to "记忆"),
        PortalTab.GRAPH to (Icons.Rounded.AccountTree to "图谱")
    ).forEach { (tab, data) ->
        Tab(
            selected = currentTab == tab,
            onClick = { currentTab = tab },
            icon = { Icon(data.first, null, Modifier.size(20.dp)) },
            text = { Text(data.second, style = NexaraTypography.labelMedium) }
        )
    }
}
```

将 PortalView 枚举重命名为 PortalTab（保持 DOCUMENTS/MEMORY/GRAPH 三值）。
移除 TAB 卡片副标题中的动态统计（保留 ViewModel 中的 loadStats 调用，仅 TAB 行不再消费）。
文件门户视图中接入 IWorkspaceRepository 的数据源（替换旧的 IDocumentRepository）。

### 7. RecycleBinCleanupWorker.kt
路径: app/src/main/java/com/promenar/nexara/data/worker/RecycleBinCleanupWorker.kt

```kotlin
class RecycleBinCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = NexaraDatabase.getInstance(applicationContext)
        val dao = db.fileEntryDao()
        val cutoff = System.currentTimeMillis() - 30.days.inWholeMilliseconds
        
        val staleFiles = dao.getRecycleBinFilesOlderThan(cutoff)
        staleFiles.forEach { file ->
            // 物理删除 + DB 级联删除
            dao.deleteByUuid(file.uuid)
            java.io.File(file.physicalRootPath, file.materializedPath).deleteRecursively()
        }
        
        return Result.success()
    }
}
```

## 验证标准
1. 编译通过
2. UI 组件与现有视觉系统一致（颜色/字体/间距/圆角全来自 NexaraColors/NexaraTypography）
3. RagHomeScreen 转为 TabRow 后三门户仍可切换
4. ResourceExplorerSheet 替换 ChatScreen 中的 WorkspaceSheet 引用

## 注意事项
- 所有新 UI 组件引用 NexaraColors / NexaraTypography / NexaraShapes — 不硬编码色值
- 使用 NexaraGlassCard 包裹文件行（一致的气泡风格）
- 索引状态徽标继承 RagStatusChip 的圆点+半透明背景模式
- LazyColumn 使用 key = uuid 确保重组稳定性
- RagHomeScreen 中原有的 stats 加载逻辑保留不删（记忆视图内部仍需要）
```

---

## Session 7: 旧系统清理 + 测试 + DIA

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 5（依赖 Session 1-6 全部完成） |
| **目标** | 移除旧 documents/folders 表、编写集成测试、更新 DIA 文档 |
| **修改文件** | 多个 |
| **预估** | ~1.5h |

### 复制此提示词启动会话

```
# 任务：统一资源 OS 收尾 — 旧系统清理 + 测试 + DIA

## 上下文
Session 1-6 已完成统一资源操作系统的全部实现。现在需要清理旧代码、补充测试、更新项目文档。请完整读取：

设计规范文件: /Users/promenar/Library/Application Support/CodeBuddy CN/User/globalStorage/tencent-cloud.coding-copilot/brain/e3245ce0f1d14a83a5e79ea4bb9e90b9/20260515-unified-resource-os-design-spec.md

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 任务清单

### 任务 1: 移除旧 documents/folders 系统
设计规范 §7.2 声明"无需迁移，直接替换"。定位并执行以下操作：

1. 在 ChatScreen.kt 中：
   - 将 `WorkspaceSheet(...)` 引用替换为 `ResourceExplorerSheet(...)`
   - 移除旧的 import WorkspaceSheet

2. 搜索整个项目中引用 `DocumentEntity`、`FolderEntity`、`IDocumentRepository`、`IFolderRepository`、`DocumentDao`、`FolderDao` 的地方：
   - 若在 RagHomeScreen 的 DOCUMENTS 门户中使用 → 替换为 IWorkspaceRepository
   - 若在 RAG 检索中使用（如 ContextBuilder 引用 Document） → 保留 VectorDao 和 KG Dao（这些表仍存在），仅断开 Document 表的引用

3. 删除以下文件（如确认无其他引用）：
   - DocumentEntity.kt / FolderEntity.kt（entity 文件）
   - DocumentDao.kt / FolderDao.kt（dao 文件）
   - DocumentRepository.kt / FolderRepository.kt（repository 文件）
   - IDocumentRepository.kt / IFolderRepository.kt（domain 接口）

4. 在 NexaraDatabase.kt 中移除 documentDao() 和 folderDao() 声明

**注意**：不要删除 Document 相关的 data class (ChatModels.kt 中的 Document)，它可能被序列化协议引用。仅删除 Room Entity/DAO/Repository 层。

### 任务 2: 编写关键测试

#### 2.1 WorkspaceSeqDaoTest.kt
路径: app/src/test/java/com/promenar/nexara/data/local/db/dao/WorkspaceSeqDaoTest.kt

使用 Room in-memory database，测试原子递增：
```kotlin
@Test
fun `concurrent increment produces unique sequences`() = runBlocking {
    // 启动 20 个协程同时调用 getNextSeqForDate("2026-05-15")
    // 验证最后 last_seq == 20，无重复
}
```

#### 2.2 乐观锁写入测试
路径: app/src/test/java/com/promenar/nexara/data/repository/FileOperationRepositoryTest.kt

测试场景：
- 正常写入成功
- expectedHash 不匹配返回 Conflict
- uuid 不存在返回 NotFound

#### 2.3 RagHomeScreen 编译验证
确保三 Tab 切换正常（无需 UI 测试，仅编译通过即可）。

### 任务 3: DIA 文档更新

更新 `.agent/registry.md`（项目根目录）：
- 在"活跃实施计划"中新增: `.agent/plans/20260515-unified-resource-os-execution.md — 统一资源 OS 多会话并行执行计划 ✅`

更新 `.agent/handover.md`（项目根目录）：
- 在已完成区域新增本批次执行摘要（Session 1-7 完成标记）
- 更新 DIA Status：确认以下文档已同步：
  - CHANGELOG.md — 新增"统一资源操作系统"条目
  - README.md — 功能描述刷新（若有必要）
  - docs/ARCHITECTURE_DESIGN.md — 新增 FileEntry/WorkspaceRepository 到模块列表
  - docs/ARCHITECTURE.md — 更新 Repository 计数器 (9→11)

### 任务 4: 编译全量验证

```bash
cd /Users/promenar/Codex/Nexara/native-ui
./gradlew compileDebugKotlin 2>&1 | tail -10
```

确保无编译错误和警告。若有，逐项修复。

## 验证标准
1. 全量编译通过
2. 新增测试通过
3. 旧 documents/folders DAO 无未解决的编译引用
4. handover.md 和 registry.md 更新完毕
5. CHANGELOG.md 新增条目

## 注意事项
- 删除旧文件前先用 `search_content` 确认无其他文件引用
- 若有文件引用了 DocumentDao 但逻辑必须保留（如 VectorDao 中的 JOIN），保留该引用并改为直接引用 VectorDao
- DIA 更新不能跳过（项目规则 §4.3 强制要求）
```

---

## 执行验证清单

所有 Session 完成后，在项目根目录执行：

```bash
# 1. 全量编译
cd /Users/promenar/Codex/Nexara/native-ui
./gradlew compileDebugKotlin

# 2. 单元测试
./gradlew testDebugUnitTest

# 3. 检查数据库版本
grep -n "VERSION" app/src/main/java/com/promenar/nexara/data/local/db/NexaraDatabase.kt
# 预期输出: VERSION = 8

# 4. 确认新文件存在
ls app/src/main/java/com/promenar/nexara/data/local/db/entity/FileEntry.kt
ls app/src/main/java/com/promenar/nexara/data/local/db/dao/FileEntryDao.kt
ls app/src/main/java/com/promenar/nexara/data/local/db/dao/WorkspaceSeqDao.kt
ls app/src/main/java/com/promenar/nexara/domain/repository/IWorkspaceRepository.kt
ls app/src/main/java/com/promenar/nexara/domain/repository/IFileOperationRepository.kt
ls app/src/main/java/com/promenar/nexara/data/repository/WorkspaceRepository.kt
ls app/src/main/java/com/promenar/nexara/data/repository/FileOperationRepository.kt
ls app/src/main/java/com/promenar/nexara/infra/util/MyersDiff.kt
ls app/src/main/java/com/promenar/nexara/ui/chat/ResourceExplorerSheet.kt
ls app/src/main/java/com/promenar/nexara/ui/chat/components/FilesPanel.kt
ls app/src/main/java/com/promenar/nexara/ui/chat/components/RecycleBinPanel.kt
ls app/src/main/java/com/promenar/nexara/ui/common/IndexStatusBadge.kt
```

---

**计划编写**: AI Assistant
**编写日期**: 2026-05-15
**设计规范参考**: `20260515-unified-resource-os-design-spec.md` v2.3
**预计总耗时**: ~8h (含并行)
