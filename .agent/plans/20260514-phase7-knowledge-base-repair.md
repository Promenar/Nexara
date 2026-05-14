# Phase 7 — 知识库系统修复与增强方案

> **版本**: v1.0 (2026-05-14)
> **前置审计**: 知识库完成度深度审计
> **并行策略**: 3 会话完全并行（Phase 7a），零文件冲突

---

## 并行执行总览

```
Session 1 (T7.1+T7.2) ── PDF + Word 格式支持 ────┐
Session 2 (T7.3+T7.4) ── 文档编辑器修复 ──────────┤ 并行，互不依赖
Session 3 (T7.5+T7.6) ── 文件夹管理补全 ──────────┘
              │
              ▼
Session 4 (T7.7+T7.8+T7.9) ── 检索增强
Session 5 (T7.10+T7.11+T7.12) ── UI 补全（与 S4 独立）
```

### 文件冲突矩阵

| 文件 | S1 | S2 | S3 | S4 | S5 |
|------|:--:|:--:|:--:|:--:|:--:|
| `build.gradle.kts` | ✏️ | | | | |
| `PdfExtractor.kt` | ✏️ | | | | |
| `DocumentImporter.kt` | ✏️ | | | | |
| `DocEditorViewModel.kt` | | ✏️ | | | |
| `RagViewModel.kt` | | | ✏️ | | ✏️ |
| `RagConfiguration.kt` | | | | ✏️ | |
| `RerankClient.kt` | | | | ✏️ | |
| `RagHomeScreen.kt` | | | | | ✏️ |
| `KnowledgeGraphScreen.kt` | | | | | ✏️ |
| `GlobalRagConfigScreen.kt` | | | | ✏️ | |

---

## SESSION 1 — PDF + Word 格式支持（T7.1 + T7.2）

**文件**: `build.gradle.kts`, `PdfExtractor.kt`, `DocumentImporter.kt`
**时长**: ~1.5h

### 提示词（直接复制）

```
## 任务：Nexara 知识库 PDF + Word 文档格式支持集成

### 背景
当前 PdfExtractor 返回占位文本，DocumentImporter.readWordContent() 对二进制 Word 返回占位提示。
需要接入 Apache PDFBox (Android 版) 和 Apache POI 实现真实文本提取。

### 项目路径
k:/Nexara/native-ui

### 任务 1.1：集成 Apache PDFBox

#### 步骤 1：添加依赖

文件: app/build.gradle.kts

在 Jsoup 段之后（约第 158 行）新增：

    // ─── 文档解析 ───
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

#### 步骤 2：重写 PdfExtractor

文件: app/src/main/java/com/promenar/nexara/data/rag/PdfExtractor.kt

完全替换文件内容：

package com.promenar.nexara.data.rag

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfExtractor {

    data class PdfResult(val pageCount: Int, val text: String)

    fun extract(context: Context, uri: Uri): Result<PdfResult> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open PDF file"))
            val document = PDDocument.load(inputStream)
            try {
                val pageCount = document.numberOfPages
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val text = stripper.getText(document)
                if (text.isBlank()) {
                    Result.failure(Exception("PDF 可能为扫描件，无可提取文本层"))
                } else {
                    Result.success(PdfResult(pageCount = pageCount, text = text))
                }
            } finally { document.close() }
        } catch (e: Exception) { Result.failure(e) }
    }
}

### 任务 1.2：集成 Apache POI（.docx）

文件: app/src/main/java/com/promenar/nexara/data/rag/DocumentImporter.kt

替换 readWordContent() 方法（约第 89-98 行）：

private fun readWordContent(uri: Uri): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = org.apache.poi.xwpf.usermodel.XWPFDocument(inputStream)
            val sb = StringBuilder()
            for (paragraph in document.paragraphs) {
                val text = paragraph.text
                if (text.isNotBlank()) sb.appendLine(text)
            }
            for (table in document.tables) {
                for (row in table.rows) {
                    for (cell in row.tableCells) {
                        val cellText = cell.text.trim()
                        if (cellText.isNotBlank()) sb.append(cellText).append("\t")
                    }
                    sb.appendLine()
                }
            }
            document.close()
            sb.toString().ifBlank { "[Word 文档已导入，但未包含可读文本]" }
        } ?: "[无法打开 Word 文档]"
    } catch (e: Exception) {
        "[Word 文档解析失败: ${e.message?.take(80)}。" +
            "可能是旧版 .doc 格式，请转换为 .txt 或 .md 后重新导入]"
    }
}

注意：XWPFDocument 仅处理 .docx，旧版 .doc 在 catch 中提示转换格式。无需修改 WORD_MIME_TYPES。
```

---

## SESSION 2 — 文档编辑器修复（T7.3 + T7.4）

**文件**: `DocEditorViewModel.kt`
**时长**: ~1h

### 提示词（直接复制）

```
## 任务：Nexara 文档编辑器修复 —— 移除 Mock 内容 + 标题持久化

### 背景
DocEditorViewModel.loadDocument() 第 44 行用 generateMockContent() 生成了硬编码假内容，
用户打开任何文档编辑看到的都是同一段假文本。此外 updateTitle() 仅修改内存未写 DB。

### 项目路径
k:/Nexara/native-ui

### 任务详情

#### 改动 1：loadDocument() 显示真实内容

文件: app/src/main/java/com/promenar/nexara/ui/rag/DocEditorViewModel.kt

修改 loadDocument() 方法（约第 36-49 行）：

- 移除 `val mockContent = generateMockContent(...)` 这一行
- 改为直接使用 `doc.content`（文档的真实内容）
- `originalContent` 也指向真实内容

修改后 loadDocument() 应为：

fun loadDocument(docId: String) {
    viewModelScope.launch {
        try {
            val doc = documentRepository.getById(docId)
            _document.value = doc
            if (doc != null) {
                val realContent = doc.content
                val sizeMb = (realContent.length.toDouble()) / (1024.0 * 1024.0)
                _isLargeFile.value = sizeMb > 10.0
                _content.value = realContent
                originalContent = realContent
            }
        } catch (_: Exception) {}
    }
}

#### 改动 2：标题重命名持久化到 DB

修改 updateTitle() 方法（约第 84-87 行）：

当前仅更新内存中的 _document.value，需要补充持久化调用。

修改后：

fun updateTitle(newTitle: String) {
    val doc = _document.value ?: return
    _document.value = doc.copy(title = newTitle)
    _isDirty.value = true
    viewModelScope.launch {
        try {
            documentRepository.updateTitle(doc.id, newTitle)
        } catch (_: Exception) {}
    }
}

注意：需要在 IDocumentRepository 接口中确认是否有 updateTitle 方法。
如果没有，可以改为调用 documentRepository.update(doc.id, _content.value) 后，
再通过 documentDao 补充 title 更新。

如果 IDocumentRepository 没有 updateTitle，请在 DocumentRepository 实现中添加：

override suspend fun updateTitle(id: String, title: String) {
    documentDao.updateTitle(id, title)
}

并在 DocumentDao 中添加：

@Query("UPDATE documents SET title = :title WHERE id = :id")
suspend fun updateTitle(id: String, title: String)

#### 改动 3：删除 generateMockContent()（可选，清理死代码）

generateMockContent() 方法不再被调用，可删除（第 89-123 行）。
如果不确定是否其他地方还有引用，可以先保留不删。
```

---

## SESSION 3 — 文件夹管理补全（T7.5 + T7.6）

**文件**: `RagViewModel.kt`
**时长**: ~1h

### 提示词（直接复制）

```
## 任务：Nexara 文件夹管理补全 —— 级联删除 + 重命名

### 背景
1. deleteCollection() 只删 Folder 记录，不删文件夹内文档及其向量，造成数据泄漏
2. RagViewModel 缺少 renameFolder / deleteFolder 的显式方法

### 项目路径
k:/Nexara/native-ui

### 任务详情

#### 改动 1：修复 deleteCollection() 级联删除

文件: app/src/main/java/com/promenar/nexara/ui/rag/RagViewModel.kt

当前 deleteCollection() 约在第 356-364 行：

fun deleteCollection(id: String) {
    viewModelScope.launch {
        try {
            val folder = folderRepository.getById(id) ?: return@launch
            folderRepository.delete(folder)
            loadStats()
        } catch (_: Exception) { }
    }
}

修改为：先删文件夹内所有文档（及其向量），再删文件夹本身：

fun deleteCollection(id: String) {
    viewModelScope.launch {
        try {
            // 1. 先获取文件夹内所有文档
            val docs = documentRepository.getByFolderId(id)
            // 2. 级联删除文档（DeleteDocumentUseCase 会同时清理向量）
            if (docs.isNotEmpty()) {
                deleteDocumentUseCase(docs.map { it.id })
            }
            // 3. 删除文件夹自身
            val folder = folderRepository.getById(id)
            if (folder != null) folderRepository.delete(folder)
            loadStats()
        } catch (_: Exception) { }
    }
}

#### 改动 2：添加 renameFolder() 方法

在 deleteCollection() 之后新增：

fun renameFolder(id: String, newName: String) {
    viewModelScope.launch {
        try {
            val folder = folderRepository.getById(id) ?: return@launch
            folderRepository.update(folder.copy(name = newName))
        } catch (_: Exception) { }
    }
}

需要确认 IFolderRepository 中有 update() 方法。
如果没有，在 IFolderRepository 接口中添加：suspend fun update(folder: Folder)
并在 FolderRepository 实现中添加对应的 Dao 调用。

#### 改动 3（可选）：添加 deleteFolder 便捷方法

如果希望保持命名一致性，添加一个明确命名为 deleteFolder 的方法（内部委托给 deleteCollection）：

fun deleteFolder(id: String) = deleteCollection(id)

### 验证标准
- 删除一个有文档的文件夹 → 确认文件夹和文档都被删除，向量未被孤立
- 重命名文件夹 → 名称持久化，重启应用后不丢失
```

---

## SESSION 4 — 检索增强（T7.7 + T7.8 + T7.9）

**文件**: `RagConfiguration.kt`, `RerankClient.kt`, `GlobalRagConfigScreen.kt`
**时长**: ~2h
**前置**: Session 1-3 全部完成

### 提示词（直接复制）

```
## 任务：Nexara RAG 检索增强 —— 混合检索 + Rerank + 查询重写

### 背景
三个检索增强能力已实现但默认关闭或为桩：
1. 混合检索（向量+关键词 RRF 融合）已实现但 enableHybridSearch 默认 false
2. RerankClient 存在但逻辑为空桩
3. 查询重写已实现但 enableQueryRewrite 默认 false

### 项目路径
k:/Nexara/native-ui

### 改动 1：默认开启混合检索（T7.7）

文件: app/src/main/java/com/promenar/nexara/data/rag/RagModels.kt

找到 RagConfiguration 数据类（约第 64 行），修改 enableHybridSearch 默认值：

enableHybridSearch: Boolean = true,  // 从 false 改为 true

### 改动 2：实现 RerankClient（T7.8）

文件: app/src/main/java/com/promenar/nexara/data/rag/RerankClient.kt

请先读出该文件的当前内容。如果存在，将其改为调用 LLM Rerank API 的真实实现。

设计：使用与主模型相同的 LLM Provider 协议，构造 rerank prompt 对候选结果重排序。

核心逻辑：
- 输入：query (用户查询) + candidates (SearchResult 列表)
- 构造 Prompt：要求模型对每个候选结果打分（0-10），按相关性排序
- 返回：重新排序的 SearchResult 列表，含 rerank_score

class RerankClient(
    private val llmProtocol: LlmProtocol,
    private val modelId: String? = null
) {
    suspend fun rerank(query: String, candidates: List<SearchResult>): List<SearchResult> {
        if (candidates.isEmpty()) return emptyList()
        if (candidates.size <= 1) return candidates

        val prompt = buildRerankPrompt(query, candidates)
        val request = PromptRequest(
            messages = listOf(ProtocolMessage(role = "user", content = prompt)),
            model = modelId ?: "default",
            temperature = 0.0,
            stream = false
        )

        return try {
            val response = llmProtocol.sendPromptSync(request)
            parseRerankScores(response.content, candidates)
        } catch (e: Exception) {
            candidates // 失败时返回原始排序
        }
    }

    private fun buildRerankPrompt(query: String, candidates: List<SearchResult>): String {
        val sb = StringBuilder()
        sb.appendLine("对以下文档片断与查询「$query」的相关性进行评分（0-10分），")
        sb.appendLine("返回 JSON 数组 [{ \"index\": 序号, \"score\": 分数 }]，仅返回 JSON：\n")
        candidates.forEachIndexed { i, c ->
            sb.appendLine("[${i}] ${c.content.take(300)}")
        }
        return sb.toString()
    }

    private fun parseRerankScores(response: String, candidates: List<SearchResult>): List<SearchResult> {
        // 从 LLM 响应中提取 JSON 分数，重新排序
        val jsonRegex = Regex("""\[\s*\{.*?\}\s*\]""", RegexOption.DOT_MATCHES_ALL)
        val match = jsonRegex.find(response) ?: return candidates
        val json = Json { ignoreUnknownKeys = true }
        val scores = json.decodeFromString<List<RerankScore>>(match.value)
        return candidates.mapIndexed { i, c ->
            val score = scores.find { it.index == i }?.score ?: 5.0
            c.copy(similarity = score.toFloat() / 10f)
        }.sortedByDescending { it.similarity }
    }

    @Serializable data class RerankScore(val index: Int, val score: Double)
}

然后在 NexaraApplication 中初始化 RerankClient 并注入 MemoryManager。
检查 MemoryManager 的 rerank 调用点（如果有 rerankClient 参数）并接入。

### 改动 3：默认开启查询重写（T7.9）

文件: app/src/main/java/com/promenar/nexara/data/rag/RagModels.kt

修改 RagConfiguration:

enableQueryRewrite: Boolean = true,  // 从 false 改为 true

同时在 GlobalRagConfigScreen 中确认用户可手动开关这些配置项。
```

---

## SESSION 5 — UI 补全（T7.10 + T7.11 + T7.12）

**文件**: `RagHomeScreen.kt`, `KnowledgeGraphScreen.kt`
**时长**: ~3h
**注意**: 如 Session 4 也修改了 RagViewModel.kt，请等 S4 完成后再启动；或先拉取最新代码。

### 提示词（直接复制）

```
## 任务：Nexara 知识库 UI 补全 —— Memory 视图 + KG 可视化 + 全文搜索

### 背景
1. RagHomeScreen 的 Memory 视图（PortalView.MEMORY）是占位空卡片
2. KnowledgeGraphScreen 是占位路由
3. 全文搜索存在后端（KeywordSearcher + FTS5）但缺少独立 UI 入口

### 项目路径
k:/Nexara/native-ui

### 改动 1：实现 Memory 视图（T7.10）

文件: app/src/main/java/com/promenar/nexara/ui/rag/RagHomeScreen.kt

找到 PortalView.MEMORY 分支（约第 427-446 行），当前渲染空卡片。

改造为：从 VectorStore 加载当前会话的记忆向量化记录，以列表展示。
每项显示：截断文本 + 创建时间 + 所属会话 ID（若跨会话）。

在 RagViewModel 中添加加载方法：

fun loadMemoryVectors() {
    viewModelScope.launch {
        try {
            // 从 vectorRepository 获取 type=memory 的向量记录
            val memories = vectorRepository.getMemoryVectors()
            _memoryVectors.value = memories
        } catch (_: Exception) { }
    }
}

并在 ViewModel 中添加 _memoryVectors 状态流，在切换到 MEMORY 视图时调用。

Memory 视图 UI 结构：
- 顶部统计卡片：记忆总数、总 Token 估计
- 列表展示最近 N 条记忆，点击展开全文
- 支持长按删除单条记忆

### 改动 2：知识图谱可视化（T7.11）

文件: app/src/main/java/com/promenar/nexara/ui/rag/KnowledgeGraphScreen.kt

请先读出当前文件内容。如果是占位，改造为：

使用 WebView 加载本地 HTML 文件，内嵌 ECharts 的力导向图（force-directed graph）。
从 GraphStore.getGraphData() 获取 nodes 和 edges，序列化为 JSON 注入 HTML 模板。

核心步骤：
1. 创建 assets/kg_template.html，包含 ECharts CDN 加载 + 力导向图配置模板
2. KnowledgeGraphScreen 中：viewModel 加载 graphData → 替换 HTML 模板中的 data JSON → WebView 渲染
3. 支持切换视图：全局视图 / 会话视图 / 文件夹视图（通过 top tabs）

HTML 模板核心（保存为 app/src/main/assets/kg_template.html）：

<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
    <style>
        * { margin: 0; padding: 0; }
        #chart { width: 100vw; height: 100vh; }
    </style>
</head>
<body>
<div id="chart"></div>
<script>
    var graphData = __GRAPH_DATA__;
    var chart = echarts.init(document.getElementById('chart'));
    var option = {
        series: [{
            type: 'graph', layout: 'force',
            roam: true, draggable: true,
            force: { repulsion: 200, edgeLength: [100, 300] },
            data: graphData.nodes.map(function(n) { return { name: n.name, category: n.type }; }),
            links: graphData.edges.map(function(e) {
                return { source: e.sourceId, target: e.targetId, label: { show: true, formatter: e.relation } };
            })
        }]
    };
    chart.setOption(option);
</script>
</body>
</html>

KnowledgeGraphScreen 中将 graphData JSON 替换 __GRAPH_DATA__ 占位符后加载。

### 改动 3：全文搜索 UI（T7.12）

文件: app/src/main/java/com/promenar/nexara/ui/rag/RagHomeScreen.kt

当前 RagHomeScreen 顶部 SearchBar 只按标题搜索。改造为：

搜索时同时触发：
1. 标题匹配（现有逻辑）
2. 全文 FTS5 搜索（通过 KeywordSearcher）

ViewModel 中添加：

fun searchFullText(query: String) {
    viewModelScope.launch {
        try {
            // 并行搜索
            val titleMatches = _documents.value.filter {
                it.title.contains(query, ignoreCase = true)
            }
            val contentMatches = keywordSearcher.search(query, limit = 20)
            // 合并去重
            val contentDocIds = contentMatches.map { it.docId }.toSet()
            val contentDocs = _documents.value.filter { it.id in contentDocIds }
            _searchResults.value = (titleMatches + contentDocs).distinctBy { it.id }
        } catch (_: Exception) {
            _searchResults.value = _documents.value.filter {
                it.title.contains(query, ignoreCase = true)
            }
        }
    }
}

搜索结果中全文命中项展示匹配摘要（SearchResult.content 截取前 100 字符）。

注意：需要在 RagViewModel 中注入 KeywordSearcher（从 NexaraApplication 获取）。
```

---

## 附录：修复后的知识库完成度预估

```
功能                    Phase 7 前    Phase 7 后
────────────────────────────────────────────
文档导入 (TXT/MD)        ✅ 100%        ✅ 100%
文档导入 (PDF)           ❌ 0%          ✅ 100%
文档导入 (Word)          ❌ 0%          ✅ 90% (.docx only)
文档编辑                 ⚠️ 50%         ✅ 100%
文档重命名持久化         ❌ 0%          ✅ 100%
文件夹级联删除           ❌ 0%          ✅ 100%
混合检索 (RRF Fusion)    ⚠️ 已实现       ✅ 默认开启
重排序                   ❌ 桩          ✅ 100%
查询重写                 ⚠️ 已实现       ✅ 默认开启
Memory 视图              ❌ 占位         ✅ 100%
KG 可视化                ❌ 占位         ✅ 100%
全文搜索 UI              ⚠️ 无独立入口   ✅ 100%

总体进度                 ~65%           ~85%
```
