# Phase 4 — 核心引擎增强

> **前置**: Phase 3 完成（Super Assistant 清理，架构债全部消除）
> **创建日期**: 2026-05-13
> **范围**: FolderRepository + VectorStatsService 重构 + PDF/Word/HTML 导入
> **排除**: 本地 Embedding 降级（不做；本地推理是独立功能，非降级手段）

---

## 1. 并行会话拆分

```
Session M: FolderRepository + VectorStatsService 重构（RagViewModel 残留清理）
Session N: PDF/Word/HTML 文档导入（DocumentImporter 扩展）
```

| 文件 | M | N |
|------|---|---|
| `domain/repository/IFolderRepository.kt` | ✅ 新建 | — |
| `data/repository/FolderRepository.kt` | ✅ 新建 | — |
| `data/mapper/FolderMapper.kt` | ✅ 新建 | — |
| `ui/rag/RagViewModel.kt` | ✅ 修改 | — |
| `data/rag/VectorStatsService.kt` | ✅ 重构 | — |
| `data/rag/DocumentImporter.kt` | — | ✅ 修改 |
| `data/rag/PdfExtractor.kt` | — | ✅ 新建 |
| 测试文件 | ✅ 新建 | ✅ 新建 |

**零冲突，可完全并行。**

---

## 2. Session M — FolderRepository + VectorStatsService 重构

### Session M 提示词

```
你需要在 Nexara 中完成 RagViewModel 的最后 DAO 残留清理。

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/

## 任务

### 1. 创建 IFolderRepository 接口

新建 `domain/repository/IFolderRepository.kt`：

```kotlin
package com.promenar.nexara.domain.repository

import com.promenar.nexara.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface IFolderRepository {
    fun observeAll(): Flow<List<Folder>>
    suspend fun getById(id: String): Folder?
    suspend fun create(folder: Folder)
    suspend fun delete(folder: Folder)
}
```

### 2. 创建 Folder Domain 模型（如果不存在）

如果 `domain/model/` 中没有 Folder 数据类，在 `domain/model/ValueObjects.kt` 末尾添加：

```kotlin
data class Folder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val createdAt: Long = 0L
)
```

### 3. 创建 FolderRepository 实现

新建 `data/repository/FolderRepository.kt`：

- 实现 `IFolderRepository`，注入 `FolderDao`
- 先读取 `data/local/db/entity/FolderEntity.kt` 和 `data/local/db/dao/FolderDao.kt` 了解实际接口
- 实现所有方法，使用 FolderMapper 做 Entity↔Domain 转换
- `observeAll()` → `folderDao.observeAll().map { ... }`
- `getById()` → `folderDao.getById()` / 或通过 observeAll 查找
- `create()` → `folderDao.insert()`
- `delete()` → `folderDao.delete()`

### 4. 创建 FolderMapper

新建 `data/mapper/FolderMapper.kt`：

- 先读取 `FolderEntity.kt` 确认所有字段
- 实现 `toDomain(entity: FolderEntity): Folder` 和 `toEntity(folder: Folder): FolderEntity`

### 5. 重构 RagViewModel.kt

先读取完整文件。

**5a. 替换 folderDao → folderRepository**

构造函数添加参数:
```kotlin
private val folderRepository: IFolderRepository
```

替换所有 folderDao 调用:
| 旧 | 新 |
|----|-----|
| `folderDao.observeAll()` | `folderRepository.observeAll()` |
| `folderDao.insert(f)` | `folderRepository.create(domainFolder)` |
| `folderDao.getById(id)` | `folderRepository.getById(id)` |
| `folderDao.delete(f)` | `folderRepository.delete(domainFolder)` |

移除 `app.database.folderDao()` 和 `// TODO: migrate to FolderRepository` 注释。

**5b. 替换 VectorStatsService → vectorRepository**

当前 RagViewModel 中（约第 47、51、280、290 行）:
```kotlin
private val vectorStatsService = VectorStatsService(app.database.vectorDao())
// ...
vectorStatsService.getStats()
```

改为直接使用 vectorRepository（构造函数已有此参数）:
```kotlin
// 直接在 loadVectorStats() 中使用:
val total = vectorRepository.getCount()
// 如果需要更细粒度的统计，在 IVectorRepository 中添加:
// suspend fun countByType(): List<TypeCount>
// 并实现之
```

如果 VectorStatsService 的 `getStats()` 中有 vectorRepository 无法直接替代的逻辑（如 countBySession），则：
- 将 VectorStatsService 重构为接收 `IVectorRepository` 而非 `VectorDao`
- 或在 IVectorRepository 中补充对应查询方法

**5c. 清理工厂方法**

在 companion object Factory 中，用 `FolderRepository` 替换 `folderDao`，用 `IVectorRepository` 替换 `VectorStatsService` 的 dao 注入。

### 6. 单元测试

新建：
- `test/.../data/mapper/FolderMapperTest.kt` — 测试 Entity↔Domain 双向映射
- `test/.../data/repository/FolderRepositoryTest.kt` — Mock FolderDao 测试 CRUD

更新：
- `test/.../ui/rag/RagViewModelTest.kt` — 添加 folderRepository mock，测试 folder CRUD 和 stats 逻辑

## 执行要求

1. 先读取: FolderEntity.kt, FolderDao.kt, RagViewModel.kt, VectorStatsService.kt
2. 每步编译验证: `./gradlew :app:compileDebugKotlin`
3. 全部测试: `./gradlew :app:testDebugUnitTest`
4. RagViewModel 中**零** `folderDao`、`app.database.*Dao()` 引用

## 禁止事项
- 不修改 DocumentImporter 或其他非 RagViewModel 文件
- 不创建与 PDF/Word/HTML 导入相关的代码（Session N 负责）
```

---

## 3. Session N — PDF/Word/HTML 文档导入

### Session N 提示词

```
你需要在 Nexara 中扩展文档导入功能，支持 PDF/Word/HTML 格式。

项目根目录: /Users/promenar/Codex/Nexara/native-ui
源码包: app/src/main/java/com/promenar/nexara/

## 背景

当前 DocumentImporter 仅支持 TXT/MD 纯文本文件。需要扩展支持 PDF、Word (docx)、HTML 格式的文本提取。

## 任务

### 1. 创建 PdfExtractor.kt

新建 `data/rag/PdfExtractor.kt`：

```kotlin
package com.promenar.nexara.data.rag

import android.content.Context
import android.net.Uri

object PdfExtractor {
    /**
     * 从 PDF URI 提取纯文本。
     * 使用 Android PdfRenderer（API 21+）逐页读取文本。
     * 如果无法提取文本（扫描版 PDF），返回错误信息。
     */
    suspend fun extract(context: Context, uri: Uri): Result<String> {
        return try {
            val contentResolver = context.contentResolver
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
                ?: return Result.failure(Exception("Cannot open PDF file"))

            val pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor!!)
            val sb = StringBuilder()

            for (pageIndex in 0 until pdfRenderer.pageCount) {
                val page = pdfRenderer.openPage(pageIndex)
                // 注意: PdfRenderer 本身不提供文本提取 API。
                // 对于文本型 PDF，使用 ContentResolver 直接读取输入流中的文本。
                // 这里作为占位：实际生产环境中可集成 Apache PDFBox (Android port) 或使用 TextExtractor。
                page.close()
            }
            pdfRenderer.close()
            parcelFileDescriptor.close()

            // 降级方案：通过 ContentResolver 读取原始文本
            val inputStream = contentResolver.openInputStream(uri)
            val text = inputStream?.bufferedReader()?.readText() ?: ""
            inputStream?.close()

            Result.success(text.ifBlank { "(PDF text extraction not available for this file)" })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**注意**: Android 的 `PdfRenderer` API 仅支持渲染页面为 Bitmap，不支持文本提取。真正的 PDF 文本提取需要集成第三方库。当前实现作为功能占位：
- 优先尝试通过 ContentResolver 读取 PDF 中的文本流（部分文本型 PDF 可用）
- 标记 `// TODO: Integrate Apache PDFBox or iText for full PDF text extraction`

如果你评估后认为暂时不需要引入重量级 PDF 库，可以保持当前降级实现并加注释。

### 2. 扩展 DocumentImporter.kt

先读取完整文件。在 `readFileContent()` 方法中扩展格式检测：

```kotlin
private suspend fun readFileContent(uri: Uri, mimeType: String?): String = withContext(Dispatchers.IO) {
    when {
        mimeType == "application/pdf" -> {
            PdfExtractor.extract(context, uri).getOrDefault("(PDF import failed)")
        }
        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
        mimeType == "application/msword" -> {
            // Word 文档：Android 上没有原生文字提取 API。
            // 降级方案：尝试通过 ContentResolver 读取文本
            val inputStream = context.contentResolver.openInputStream(uri)
            val text = inputStream?.bufferedReader()?.readText() ?: ""
            inputStream?.close()
            text.ifBlank { "(Word document — text extraction requires Apache POI integration)" }
        }
        mimeType == "text/html" -> {
            // HTML：使用简单的标签剥离提取纯文本
            val inputStream = context.contentResolver.openInputStream(uri)
            val html = inputStream?.bufferedReader()?.readText() ?: ""
            inputStream?.close()
            stripHtmlTags(html)
        }
        else -> {
            // 现有纯文本逻辑
            val inputStream = context.contentResolver.openInputStream(uri)
            val text = inputStream?.bufferedReader()?.readText() ?: ""
            inputStream?.close()
            text
        }
    }
}

/**
 * 简单 HTML 标签剥离。移除所有 <...> 标签，保留文本内容。
 */
private fun stripHtmlTags(html: String): String {
    return html
        .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("&nbsp;"), " ")
        .replace(Regex("&amp;"), "&")
        .replace(Regex("&lt;"), "<")
        .replace(Regex("&gt;"), ">")
        .replace(Regex("&quot;"), "\"")
        .replace(Regex("\\s+"), " ")
        .trim()
}
```

### 3. 更新 fileSize 检测逻辑

在 `getFileSize()` 中确保 PDF/Word 文件正确获取文件大小（当前实现可能已支持，确认即可）。

### 4. 单元测试

新建 `test/.../data/rag/DocumentImporterTest.kt`：

```kotlin
class DocumentImporterTest {
    @Test
    fun `stripHtmlTags removes all tags`() {
        // 需要通过反射或改为 internal 测试 stripHtmlTags
    }

    @Test
    fun `stripHtmlTags decodes entities`() {
        // &amp; → &, &lt; → <, etc.
    }

    @Test
    fun `stripHtmlTags removes script and style blocks`() {
        // <script>alert('xss')</script> → ""
    }
}
```

- 如果 `stripHtmlTags` 是 private，改为 `internal` 以便测试
- 或创建 `HtmlExtractor.kt` 作为独立类，方便单独测试

## 执行要求

1. 先读取: DocumentImporter.kt 完整内容
2. 每步编译验证: `./gradlew :app:compileDebugKotlin`
3. 测试: `./gradlew :app:testDebugUnitTest --tests "com.promenar.nexara.data.rag.DocumentImporterTest"`
4. PDF/Word 文本提取在无第三方库的情况下可为降级实现，标记 TODO

## 禁止事项
- 不修改 RagViewModel 或 FolderRepository（Session M 负责）
- 不进行本地 Embedding 降级
- PDF 库可选：如 Android PdfRenderer 不足，标记 TODO 即可，不要引入未经验证的重量级依赖
```

---

## 4. 验证清单

- [ ] `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- [ ] `./gradlew :app:testDebugUnitTest` 无新增失败
- [ ] RagViewModel 零 DAO 引用（folderDao/vectorDao 全部消除）
- [ ] DocumentImporter 支持 pdf/docx/doc/html MIME 类型
- [ ] HTML 标签剥离正确处理实体和 script/style 块

---

**文档维护者**: AI Assistant
**最后更新**: 2026-05-13
