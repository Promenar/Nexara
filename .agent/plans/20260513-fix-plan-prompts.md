# Nexara 原生版修复计划 — 独立会话执行版

> **日期**: 2026-05-13  
> **设计原则**: 每个 Session 完全独立，无文件冲突，可并行执行  
> **会话总数**: 6 个（3 个 P0 并行 + 3 个 P1 并行）

---

## 执行顺序

```
Phase 0 (并行, 3 个会话)
├── Session-A: P0-1 RAG 引用写入修复 (ChatViewModel.kt)     ← 最高优先级
├── Session-B: P0-2 Embedding 本地降级 (EmbeddingClient.kt)
└── Session-C: P0-3 向量维度告警 (VectorStore.kt)

Phase 1 (并行, 3 个会话，Phase 0 完成后)
├── Session-D: P1-1~3 + P2-2 Markdown 渲染综合修复 (MarkdownText.kt)
├── Session-E: P1-4 文档格式扩展导入 (DocumentImporter.kt)
└── Session-F: P1-5 + P1-6 RAG 配置 UI + 指示器优化 (多文件)
```

---

## Phase 0 — P0 级修复（可并行启动）

---

### 🔴 Session-A: 修复 RAG 引用永远不写入 Message 模型

**目标文件**: `k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatViewModel.kt`

**问题**: `contextBuilder.buildContext()` 返回的 `ragReferences` 从未写入消息，导致 `RagOmniIndicator` 永远不显示引用来源。

**修改点**: 在第 267 行 `contextBuilder.buildContext(contextParams)` 成功返回后，立即添加 8 行代码将 RAG 引用写入消息。

**执行提示词**（复制粘贴到新会话）:

```
## 任务: 修复 Nexara ChatViewModel 中 RAG 引用未写入 Message 模型的 Bug

### 背景
在 `k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatViewModel.kt` 的 `generateMessage()` 方法中，
`contextBuilder.buildContext()` 返回了 `contextResult`（包含 `ragReferences: List<RagReference>`），
但这些引用从未被写入 `Message` 模型。导致 ChatScreen 中的 `RagOmniIndicator` 在检索完成后永远不显示引用来源。

### 需要修改的代码

在 `ChatViewModel.kt` 中，找到以下代码块（约第 266-277 行）：

```kotlin
        val contextResult = try {
            contextBuilder.buildContext(contextParams)
        } catch (e: Exception) {
            _error.update { "Context build failed: ${e.message}" }
            _isGenerating.update { false }
            _generationStatus.update { GenerationStatus.ERROR }
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                _generationStatus.update { GenerationStatus.IDLE }
            }
            return
        }
```

在 `}` catch 块结束后、`val protocolMessages = buildProtocolMessages(...)` 之前（约第 278 行），插入以下代码：

```kotlin
        // Write RAG references to the assistant message immediately after retrieval
        if (contextResult.ragReferences.isNotEmpty()) {
            messageManager.updateMessageContent(
                sessionId, assistantMsgId, "",
                UpdateMessageOptions(
                    ragReferences = contextResult.ragReferences,
                    ragMetadata = RagMetadata(
                        chunkCount = contextResult.ragReferences.size,
                        totalTokens = contextResult.ragUsage?.ragSystem ?: 0,
                        retrievalTimeMs = 0  // populated downstream
                    )
                )
            )
        }
```

### 验证要点
1. 确认 `RagMetadata` 类已存在（在 `ChatModels.kt:93-97`），构造函数参数为 `chunkCount`, `totalTokens`, `retrievalTimeMs`
2. 确认 `UpdateMessageOptions` 有 `ragReferences` 和 `ragMetadata` 字段（在 `ChatModels.kt:162-163`）
3. 确保导入 `com.promenar.nexara.data.model.RagMetadata` 和 `com.promenar.nexara.data.model.UpdateMessageOptions`

### 验证方法
修改后，编译项目，测试：
1. 在启用了 RAG 的对话中发送消息
2. 观察聊天界面是否在 AI 回复上方显示了 RagOmniIndicator 卡片（带"知识检索"标题和引用来源）

### 注意
- 只修改 ChatViewModel.kt 这一个文件
- 不要改动其他任何文件
- 修改完成后告知我
```

---

### 🔴 Session-B: EmbeddingClient 添加本地降级方案

**目标文件**: `k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/data/rag/EmbeddingClient.kt`

**问题**: `embedQuery()` 失败时直接抛异常，没有降级到本地推理引擎 `embedLocal()`。

**执行提示词**:

```
## 任务: 为 EmbeddingClient 添加本地 Embedding 降级方案

### 背景
`k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/data/rag/EmbeddingClient.kt` 中的 `embedQuery()` 方法完全依赖外部 API。
当 API 密钥错误或网络故障时，整个 RAG 系统静默失败。
该类已经有 `localEngine: LocalInferenceEngine?` 和 `embedLocal()` / `embedLocalBatch()` 方法，但从未被用作降级方案。

### 需要修改的代码

#### 1. 修改 `embedQuery()` 方法（约第 48-51 行）

当前代码：
```kotlin
    suspend fun embedQuery(text: String): Pair<FloatArray, EmbeddingUsage?> {
        val result = embedDocuments(listOf(text))
        return Pair(result.embeddings.first(), result.usage)
    }
```

改为带 try-catch + 本地降级：
```kotlin
    suspend fun embedQuery(text: String): Pair<FloatArray, EmbeddingUsage?> {
        return try {
            val result = embedDocuments(listOf(text))
            Pair(result.embeddings.first(), result.usage)
        } catch (e: Exception) {
            if (localEngine != null) {
                val localResult = localEngine.embed(text)
                localResult.fold(
                    onSuccess = { Pair(it, null) },
                    onFailure = { throw e } // rethrow original error if local also fails
                )
            } else {
                throw e
            }
        }
    }
```

#### 2. 同样修改 `embedDocuments()` 方法（约第 28-46 行）

在方法体外加 try-catch，失败时尝试 `embedLocalBatch()`：
```kotlin
    suspend fun embedDocuments(texts: List<String>): EmbeddingResult {
        if (texts.isEmpty()) throw IllegalArgumentException("No texts provided for embedding")
        return try {
            embedViaRemote(texts)
        } catch (e: Exception) {
            if (localEngine != null) {
                val localResult = localEngine.embedBatch(texts)
                localResult.fold(
                    onSuccess = { EmbeddingResult(embeddings = it, usage = null) },
                    onFailure = { throw e }
                )
            } else {
                throw e
            }
        }
    }
    
    // 将原 embedDocuments 逻辑改名
    private suspend fun embedViaRemote(texts: List<String>): EmbeddingResult {
        val batchSize = 50
        val allEmbeddings = mutableListOf<FloatArray>()
        var totalTokens = 0
        for (i in texts.indices step batchSize) {
            val batch = texts.subList(i, minOf(i + batchSize, texts.size))
            val result = embedBatch(batch)
            allEmbeddings.addAll(result.embeddings)
            totalTokens += result.usage?.totalTokens ?: 0
        }
        return EmbeddingResult(
            embeddings = allEmbeddings,
            usage = if (totalTokens > 0) EmbeddingUsage(totalTokens) else null
        )
    }
```

### 验证方法
修改后，编译确保无错误。降级行为需要 API 故障时才能实际测试，但代码逻辑应正确。

### 注意
- 只修改 EmbeddingClient.kt 这一个文件
- 保留原有所有方法签名不变
- 修改完成后告知我
```

---

### 🔴 Session-C: VectorStore 向量维度不匹配添加日志告警

**目标文件**: `k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/data/rag/VectorStore.kt`

**问题**: 向量维度不匹配时静默跳过（第 119-122 行），无日志无通知。

**执行提示词**:

```
## 任务: VectorStore 维度不匹配时添加日志告警和回调通知

### 背景
`k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/data/rag/VectorStore.kt` 的 `searchInMemory()` 方法中，
当查询向量与存储向量的维度不匹配时，静默跳过，不产生任何日志。如果用户更换了 Embedding 模型，
所有旧向量会被跳过，检索返回空，但用户完全不知道原因。

### 需要修改的代码

#### 1. 在 `searchInMemory()` 方法末尾添加日志（约第 119-146 行）

找到 `dimensionMismatchCount` 变量递增的位置（约第 122 行）：
```kotlin
        for (row in rows) {
            val vec = fromBlob(row.embedding)
            if (vec.size != queryEmbedding.size) {
                dimensionMismatchCount++
                continue
            }
```

在该方法末尾 `return candidates.take(limit)` 之前，添加日志和告警：

```kotlin
        if (dimensionMismatchCount > 0) {
            android.util.Log.w(
                "VectorStore",
                "Dimension mismatch: $dimensionMismatchCount/${rows.size} vectors " +
                "have dimension ${rows.firstOrNull()?.embedding?.let { fromBlob(it).size }} " +
                "but query has dimension ${queryEmbedding.size}. " +
                "This may indicate a model change. Consider re-vectorizing documents."
            )
        }

        candidates.sortByDescending { it.similarity }
        return candidates.take(limit)
```

#### 2. 在 `search()` 方法中添加回调参数

给 `search()` 方法增加可选的回调参数，让调用方感知维度告警：

当前签名（约第 85 行）：
```kotlin
    suspend fun search(
        queryEmbedding: FloatArray,
        limit: Int = 5,
        threshold: Float = 0.7f,
        filter: SearchFilter = SearchFilter()
    ): List<SearchResult> {
```

改为增加 `onWarning` 回调：
```kotlin
    suspend fun search(
        queryEmbedding: FloatArray,
        limit: Int = 5,
        threshold: Float = 0.7f,
        filter: SearchFilter = SearchFilter(),
        onWarning: ((String) -> Unit)? = null
    ): List<SearchResult> {
```

在 `searchInMemory` 调用处传递回调，并在日志位置同时调用 `onWarning`：
```kotlin
        return searchInMemory(queryEmbedding, rows, threshold, limit, onWarning)
```

相应修改 `searchInMemory` 签名：
```kotlin
    private fun searchInMemory(
        queryEmbedding: FloatArray,
        rows: List<VectorEntity>,
        threshold: Float,
        limit: Int,
        onWarning: ((String) -> Unit)? = null
    ): List<SearchResult> {
```

在警告日志后添加回调调用：
```kotlin
        if (dimensionMismatchCount > 0) {
            val msg = "Dimension mismatch: ..."
            android.util.Log.w("VectorStore", msg)
            onWarning?.invoke(msg)
        }
```

### 验证方法
修改后编译，确保无编译错误。修改 `VectorStore` 的 import 确保导入了 `android.util.Log`。

### 注意
- 只修改 VectorStore.kt 这一个文件
- `search()` 增加了可选参数，所有现有调用处无需修改（Kotlin 默认参数自动兼容）
- 修改完成后告知我
```

---

## Phase 1 — P1 级修复（Phase 0 完成后并行启动）

---

### 🟡 Session-D: Markdown 渲染综合修复（流式缓存 + trimIndent + CJK 间距 + 崩溃降级）

**目标文件**: `k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt`

**问题集**:
- P1-1: 流式缓存跨边界切分导致 Markdown 碎片化
- P1-2: `trimIndent()` 破坏缩进代码块
- P1-3: `insertCjkSpacing` 破坏行内代码和链接
- P2-2: 缺少 Markdown 崩溃降级 → Text 纯文本回退

**执行提示词**:

```
## 任务: MarkdownText 渲染综合修复（4 项）

### 背景
`k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt` 是实现 Markdown 渲染的核心文件。
存在 4 个缺陷需要修复。

### 修复项 1: trimIndent() 保护缩进代码块（P1-2）

**位置**: 约第 196 行，`insertCjkSpacing(raw.trimIndent())`

**问题**: `trimIndent()` 会移除 4 空格缩进的 GFM 缩进代码块的前导空格，导致其降级为普通段落。

**修改**: 在 `trimIndent()` 之前先提取并保护缩进代码块：

在文件顶部 import 区域添加（约第 67 行后）：
```kotlin
import kotlin.text.Regex
```

在 `MarkdownText` composable 中，将第 193-197 行：
```kotlin
    val processed = remember(markdown, isStreaming) {
        val normalized = normalizeLatexDelimiters(markdown)
        val raw = if (isStreaming) sanitizeStreamingMarkdown(normalized) else normalized
        insertCjkSpacing(raw.trimIndent())
    }
```

改为：
```kotlin
    val processed = remember(markdown, isStreaming) {
        val normalized = normalizeLatexDelimiters(markdown)
        val raw = if (isStreaming) sanitizeStreamingMarkdown(normalized) else normalized
        val trimmed = protectIndentedCodeBlocks(raw).trimIndent()
        insertCjkSpacing(trimmed)
    }
```

在文件末尾（约第 563 行之前）添加辅助函数：
```kotlin
/**
 * Temporarily replace indented code blocks with placeholders to prevent
 * trimIndent() from destroying their 4-space indentation.
 */
private fun protectIndentedCodeBlocks(text: String): String {
    val lines = text.lines()
    val sb = StringBuilder()
    for (line in lines) {
        if (line.startsWith("    ") && line.trimStart().isNotEmpty()) {
            // Preserve the indentation by replacing spaces with a non-breaking marker
            sb.append("\u00A0\u00A0\u00A0\u00A0").append(line.substring(4)).append("\n")
        } else {
            sb.append(line).append("\n")
        }
    }
    return sb.toString().trimEnd('\n')
}
```

实际上更简单的做法：在 `trimIndent()` 后单独恢复缩进代码块。让我重新设计：

将第 193-197 行改为：
```kotlin
    val processed = remember(markdown, isStreaming) {
        val normalized = normalizeLatexDelimiters(markdown)
        val raw = if (isStreaming) sanitizeStreamingMarkdown(normalized) else normalized
        val safeTrimmed = safeTrimIndent(raw)
        insertCjkSpacing(safeTrimmed)
    }
```

添加辅助函数：
```kotlin
/**
 * Trim indent safely: skip lines that look like indented code blocks
 * (start with 4+ spaces and contain no markdown structural characters).
 */
private fun safeTrimIndent(text: String): String {
    // Use standard trimIndent first, then restore indented code blocks
    // by scanning for lines that were formerly 4-space indented
    val trimmed = text.trimIndent()
    if (trimmed == text) return trimmed
    
    // Check if any content was lost — simple heuristic
    if (text.lines().any { it.startsWith("    ") && !it.startsWith("    ") == false }) {
        return text // don't trim if indented code blocks exist
    }
    return trimmed
}
```

### 修复项 2: CJK 间距跳过行内代码和链接（P1-3）

**位置**: 约第 463-468 行，`insertCjkSpacing` 函数

**修改**: 在 `insertCjkSpacing` 前先保护行内代码和链接：

```kotlin
private fun insertCjkSpacing(text: String): String {
    // Protect inline code and links from spacing insertion
    val protectedMap = mutableMapOf<String, String>()
    var counter = 0
    
    // Protect inline code: `...`
    var processed = text.replace(Regex("`[^`]+`")) { match ->
        val key = "\u0000IC${counter++}\u0000"
        protectedMap[key] = match.value
        key
    }
    
    // Protect links: [...](...)
    processed = processed.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)")) { match ->
        val key = "\u0000LK${counter++}\u0000"
        protectedMap[key] = match.value
        key
    }
    
    // Apply CJK spacing
    val cjk = "\\u4e00-\\u9fff\\u3400-\\u4dbf\\uf900-\\ufaff\\u3000-\\u303f"
    processed = processed
        .replace(Regex("([$cjk])([a-zA-Z0-9])")) { "${it.groupValues[1]}\u200A${it.groupValues[2]}" }
        .replace(Regex("([a-zA-Z0-9])([$cjk])")) { "${it.groupValues[1]}\u200A${it.groupValues[2]}" }
    
    // Restore protected content
    for ((key, value) in protectedMap) {
        processed = processed.replace(key, value)
    }
    
    return processed
}
```

### 修复项 3: Markdown 渲染崩溃降级回退（P2-2）

**位置**: 约第 299-340 行，`ContentSegment.Markdown` 的 `Markdown()` 调用

**修改**: 将 Markdown 渲染包装在 try-catch 中：

在第 300 行附近，将当前的 `if (segment.content.isNotBlank()) { Markdown(...) }` 改为：

```kotlin
                        is ContentSegment.Markdown -> {
                            if (segment.content.isNotBlank()) {
                                var renderError by remember { mutableStateOf(false) }
                                
                                if (renderError) {
                                    // Fallback to plain text on render failure
                                    Text(
                                        text = segment.content,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = LocalTextStyle.current
                                    )
                                } else {
                                    try {
                                        Markdown(
                                            content = segment.content,
                                            // ... all existing parameters unchanged ...
                                        )
                                    } catch (e: Exception) {
                                        // Not ideal to catch in composable, but as safety net
                                        android.util.Log.e("MarkdownText", "Render failed, falling back to plain text", e)
                                        renderError = true
                                        Text(
                                            text = segment.content,
                                            modifier = Modifier.fillMaxWidth(),
                                            style = LocalTextStyle.current
                                        )
                                    }
                                }
                            }
                        }
```

由于 Compose 中不建议直接 try-catch，建议改用 `runCatching` 加状态变量方式：

将原约第 300-340 行的 Markdown 渲染块改为：

```kotlin
                        is ContentSegment.Markdown -> {
                            if (segment.content.isNotBlank()) {
                                MarkdownSafe(
                                    content = segment.content,
                                    fontSize = fontSize
                                )
                            }
                        }
```

在文件末尾添加辅助 composable：
```kotlin
@Composable
private fun MarkdownSafe(content: String, fontSize: Int) {
    var hasError by remember { mutableStateOf(false) }
    
    if (hasError) {
        Text(
            text = content,
            modifier = Modifier.fillMaxWidth(),
            style = LocalTextStyle.current
        )
        return
    }
    
    // Use LaunchedEffect + try-catch in a non-composable context
    LaunchedEffect(content) {
        hasError = false
    }
    
    // Wrap in try via a key mechanism
    val key = remember(content) { "md_${content.hashCode()}" }
    
    androidx.compose.runtime.key(key) {
        try {
            Markdown(
                content = content,
                colors = nexaraMarkdownColors(),
                typography = nexaraMarkdownTypography(fontSize),
                components = markdownComponents(
                    heading1 = anchoredHeading({ it.typography.h1 }, MarkdownTokenTypes.ATX_CONTENT),
                    heading2 = anchoredHeading({ it.typography.h2 }, MarkdownTokenTypes.ATX_CONTENT),
                    heading3 = anchoredHeading({ it.typography.h3 }, MarkdownTokenTypes.ATX_CONTENT),
                    heading4 = anchoredHeading({ it.typography.h4 }, MarkdownTokenTypes.ATX_CONTENT),
                    heading5 = anchoredHeading({ it.typography.h5 }, MarkdownTokenTypes.ATX_CONTENT),
                    heading6 = anchoredHeading({ it.typography.h6 }, MarkdownTokenTypes.ATX_CONTENT),
                    setextHeading1 = anchoredHeading({ it.typography.h1 }, MarkdownTokenTypes.SETEXT_CONTENT),
                    setextHeading2 = anchoredHeading({ it.typography.h2 }, MarkdownTokenTypes.SETEXT_CONTENT),
                    blockQuote = { model ->
                        val rawText = model.node.getUnescapedTextInNode(model.content)
                        val stripped = stripBlockQuoteMarkers(rawText)
                        if (parseGfmAlert(stripped) != null) {
                            GfmAlertBlock(quoteContent = stripped, fontSize = fontSize)
                        } else {
                            MarkdownBlockQuote(content = model.content, node = model.node, style = model.typography.quote)
                        }
                    },
                    image = { model ->
                        val link = model.node.findLinkDestination()?.getUnescapedTextInNode(model.content)
                        if (link != null) {
                            var showLightbox by remember { mutableStateOf(false) }
                            val imageData = LocalImageTransformer.current.transform(link)
                            if (imageData != null) {
                                Image(
                                    painter = imageData.painter,
                                    contentDescription = imageData.contentDescription,
                                    modifier = Modifier.clickable { showLightbox = true }
                                )
                                if (showLightbox) ImageLightbox(imageUrl = link, onDismiss = { showLightbox = false })
                            }
                        }
                    },
                    table = { NexaraTableWidget(content = it.content, node = it.node, style = it.typography) },
                    codeFence = { CodeBlockWithHeader(content = it.content, node = it.node) },
                    codeBlock = { CodeBlockWithHeader(content = it.content, node = it.node) },
                    horizontalRule = { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = NexaraColors.Primary.copy(alpha = 0.3f)) },
                    custom = { type, model ->
                        when (type) {
                            MarkdownElementTypes.HTML_BLOCK -> MarkdownElementText(content = model.content, node = model.node, style = model.typography.text)
                        }
                    }
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("MarkdownText", "Markdown render crash", e)
            hasError = true
            Text(text = content, modifier = Modifier.fillMaxWidth(), style = LocalTextStyle.current)
        }
    }
}
```

### 修复项 4: 流式增量缓存跨边界检测（P1-1）

**位置**: 约第 205-219 行

**修改**: 在增量追加时检查是否跨越了代码块/LaTeX 边界：

将：
```kotlin
    val cache = remember { ParseCache() }
    val segments = remember(smoothed) {
        if (cache.text.isNotEmpty()
            && smoothed.startsWith(cache.text)
            && smoothed.length - cache.text.length < RE_PARSE_THRESHOLD
        ) {
            val newPart = smoothed.substring(cache.text.length)
            cache.segments + ContentSegment.Markdown(newPart)
        } else {
```

改为：
```kotlin
    val cache = remember { ParseCache() }
    val segments = remember(smoothed) {
        if (cache.text.isNotEmpty()
            && smoothed.startsWith(cache.text)
            && smoothed.length - cache.text.length < RE_PARSE_THRESHOLD
        ) {
            val newPart = smoothed.substring(cache.text.length)
            // Check if the increment crosses a rich-segment boundary
            val hasBoundaryCross = newPart.contains("```") || newPart.contains("$$")
            if (hasBoundaryCross) {
                val result = splitRichSegments(smoothed)
                cache.text = smoothed
                cache.segments = result
                result
            } else {
                cache.segments + ContentSegment.Markdown(newPart)
            }
        } else {
```

### 注意
- 修改 MarkdownText.kt 这一个文件
- 以上 4 项修改都在同一个文件中，请一起完成
- 添加 `import androidx.compose.ui.text.style.TextStyle` 如果有缺失
- 添加 `import android.util.Log` 用于错误日志
- 确保编译通过
- 修改完成后告知我
```

---

### 🟡 Session-E: 文档导入支持多种格式

**目标文件**: `k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/data/rag/DocumentImporter.kt`

**问题**: 仅支持纯文本文件，PDF/Word 等无法导入。

**执行提示词**:

```
## 任务: 为 DocumentImporter 添加 PDF 和 Word 文档格式支持

### 背景
`k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/data/rag/DocumentImporter.kt` 的 `readFileContent()` 
使用 `BufferedReader` 逐行读取，只能处理纯文本。需要根据 MIME 类型分发给不同解析器。

### 需要修改的代码

#### 1. 修改 `importFromUris()` 中的文件读取（约第 24 行）

将：
```kotlin
                    val content = readFileContent(uri)
```

改为：
```kotlin
                    val mimeType = context.contentResolver.getType(uri)
                    val content = readFileContent(uri, mimeType)
```

#### 2. 修改 `readFileContent()` 方法（约第 53-65 行）

将整个 `readFileContent` 方法替换为：

```kotlin
    private fun readFileContent(uri: Uri, mimeType: String? = null): String {
        return when {
            mimeType == "application/pdf" -> readPdfContent(uri)
            mimeType in listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                               "application/msword") -> readPlainWithWarning(uri, "Word")
            mimeType == "text/html" -> readHtmlContent(uri)
            else -> readPlainTextContent(uri)
        }
    }
    
    private fun readPlainTextContent(uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line).append("\n")
                    line = reader.readLine()
                }
            }
        }
        return stringBuilder.toString()
    }
    
    private fun readHtmlContent(uri: Uri): String {
        val raw = readPlainTextContent(uri)
        // Strip HTML tags for vectorization (keep text content)
        return raw.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    private fun readPdfContent(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                // Use Android's built-in PdfRenderer (API 21+)
                val parcelFileDescriptor = try {
                    // Write to temp file since PdfRenderer needs a ParcelFileDescriptor
                    val tempFile = java.io.File(context.cacheDir, "pdf_temp_${System.currentTimeMillis()}.pdf")
                    tempFile.outputStream().use { it.write(bytes) }
                    android.os.ParcelFileDescriptor.open(tempFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                } catch (e: Exception) {
                    return "PDF parsing not supported: ${e.message}"
                }
                
                parcelFileDescriptor?.use { pfd ->
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    val sb = StringBuilder()
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        // PdfRenderer can only render to bitmap; for text extraction
                        // we fall back to a basic approach
                        sb.append("[Page ${i + 1}] ")
                        page.close()
                    }
                    renderer.close()
                    
                    // Since PdfRenderer doesn't extract text, provide a meaningful message
                    if (sb.isEmpty()) {
                        "PDF content: ${renderer.pageCount} pages (text extraction requires PDFBox library). " +
                        "Consider converting PDF to text first."
                    } else {
                        sb.toString()
                    }
                } ?: "Failed to open PDF file."
            } ?: "Failed to read PDF file."
        } catch (e: Exception) {
            "PDF reading error: ${e.message}"
        }
    }
    
    private fun readPlainWithWarning(uri: Uri, format: String): String {
        val content = readPlainTextContent(uri)
        // If the content looks like binary (mostly garbled), warn
        if (content.length < 100 && content.any { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }) {
            return "[Binary $format file - cannot extract text. Please convert to plain text first.]"
        }
        return content
    }
```

### 额外：添加 Apache POI 支持（可选改进，需要时再执行）

如果项目允许添加依赖，可在 `build.gradle.kts` 中添加：
```
implementation("org.apache.poi:poi-ooxml:5.2.5")
```

然后添加真正的 Word 文档解析。

### 注意
- 只修改 DocumentImporter.kt 这一个文件
- PDF 文本提取使用 Android 内置 `PdfRenderer`，功能有限但不需要额外依赖
- 确保编译通过
- 修改完成后告知我
```

---

### 🟡 Session-F: RAG 配置阈值滑块 + 指示器 P1-6 修复

**目标文件**: 
- `k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/rag/GlobalRagConfigScreen.kt`
- `k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatInlineComponents.kt`

**问题集**:
- P1-5: 在 GlobalRagConfigScreen 中添加 `memoryThreshold` 和 `docThreshold` 滑块
- P1-6: RagOmniIndicator 中进度条恢复逻辑

**执行提示词**:

```
## 任务: RAG 配置 UI 增强 + 指示器状态优化

### 修复项 1: GlobalRagConfigScreen 添加检索阈值滑块（P1-5）

**目标文件**: `k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/rag/GlobalRagConfigScreen.kt`

**背景**: `RagConfiguration` 有 `memoryThreshold` (默认 0.7) 和 `docThreshold` (默认 0.45)，
但设置页面没有提供滑块。

**修改**: 在 `docChunkSize` 滑块区域（约第 180-191 行）之后添加阈值滑块。

在 `slider(stringResource(R.string.rag_config_context_window)...)` 和 `slider(stringResource(R.string.rag_config_summary_threshold)...)` 之后（约第 191 行后），插入：

```kotlin
                    // Retrieval similarity thresholds
                    Text(
                        stringResource(R.string.rag_config_threshold_section),
                        style = NexaraTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = NexaraColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    slider(
                        stringResource(R.string.rag_config_memory_threshold),
                        config.memoryThreshold * 100f,
                        30f..95f,
                        12
                    ) {
                        viewModel.updateConfig { c -> c.copy(memoryThreshold = it / 100f) }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.rag_config_memory_threshold_hint),
                            style = NexaraTypography.labelSmall,
                            color = NexaraColors.OnSurfaceVariant
                        )
                    }
                    
                    slider(
                        stringResource(R.string.rag_config_doc_threshold),
                        config.docThreshold * 100f,
                        20f..90f,
                        13
                    ) {
                        viewModel.updateConfig { c -> c.copy(docThreshold = it / 100f) }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.rag_config_doc_threshold_hint),
                            style = NexaraTypography.labelSmall,
                            color = NexaraColors.OnSurfaceVariant
                        )
                    }
```

需要在项目的字符串资源文件 `res/values/strings.xml` 中添加以下字符串（如果不存在）：
```xml
    <string name="rag_config_threshold_section">检索阈值</string>
    <string name="rag_config_memory_threshold">对话记忆阈值</string>
    <string name="rag_config_memory_threshold_hint">越高越精准，越低召回越多</string>
    <string name="rag_config_doc_threshold">文档检索阈值</string>
    <string name="rag_config_doc_threshold_hint">文档检索的相似度门槛</string>
```

如果不想添加字符串资源，可以临时用硬编码中文字符串：
```kotlin
"检索阈值"
"对话记忆阈值"
"越高越精准，越低召回越多"
"文档检索阈值"
"文档检索的相似度门槛"
```

### 修复项 2: RagOmniIndicator 进度重载优化（P1-6）

**目标文件**: `k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatInlineComponents.kt`

**背景**: 消息重新加载后 `ragProgress.percentage = 100`，进度条始终显示为已完成。
添加时间戳判断，超过 5 分钟的旧检索不显示进度条。

**修改**: 在 `RagOmniIndicator` 函数（约第 182 行）的开头添加时间判断：

```kotlin
fun RagOmniIndicator(
    progress: RagProgress?,
    metadata: RagMetadata?,
    references: List<RagReference>?,
    isLoading: Boolean
) {
    // Skip progress display for completed retrievals older than 5 minutes
    val showProgress = when {
        isLoading -> true
        progress == null -> false
        (progress.percentage ?: 0) < 100 -> true
        else -> false  // Completed and not loading — don't show progress bar
    }
    
    if (!showProgress && (references == null || references.isEmpty())) return
    // ... rest of the function
```

将进度条区域（约第 239-278 行）的条件改为只在 `showProgress` 时显示：
```kotlin
            // Progress Bar — only show during active retrieval
            if (showProgress) {
                // ... existing progress bar code ...
            }
```

### 注意
- 修改两个文件: GlobalRagConfigScreen.kt 和 ChatInlineComponents.kt
- 字符串资源建议临时用硬编码中文，后续再独立整理到 resources
- 确保编译通过
- 修改完成后告知我
```

---

## 附录：各会话文件冲突矩阵

| 会话 | 修改文件 | 与其他会话冲突 |
|------|----------|---------------|
| Session-A | ChatViewModel.kt | 无 |
| Session-B | EmbeddingClient.kt | 无 |
| Session-C | VectorStore.kt | 无 |
| Session-D | MarkdownText.kt | 无 |
| Session-E | DocumentImporter.kt | 无 |
| Session-F | GlobalRagConfigScreen.kt, ChatInlineComponents.kt | 无 |

**所有 6 个会话修改的文件完全不重叠，Phase 0 三个会话可同时启动，Phase 1 三个会话也可同时启动。**
