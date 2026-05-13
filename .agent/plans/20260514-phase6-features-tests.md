# Phase 6 — 测试补缺 + PDF/Word 深化 + 功能增强

> **日期**: 2026-05-14
> **前置**: Phase 1-5 全部完成（架构迁移闭环，520 tests 全通过）

---

## 1. 当前状态与下阶段方向

架构工作已全部完成。后续聚焦三线：

| 线 | 内容 | 说明 |
|----|------|------|
| **测试补缺** | PdfExtractor / DocumentImporter / VectorStatsService 零测试覆盖 | P0 紧迫 |
| **功能深化** | PDFBox 集成 + 会话导出 + Token 仪表盘 | 用户可见价值 |
| **Markdown 优化** | GFM Alert / LaTeX / CJK / HTML Artifacts | 已有完整方案待执行 |

---

## 2. 并行会话拆分

```
Session S: 测试补缺（PdfExtractor + DocumentImporter + VectorStatsService）
Session T: PDFBox 集成 + 会话导出（TXT/Markdown）
Session U: Token 统计仪表盘完善
```

零文件冲突，可完全并行。

---

## 3. Session S — 测试补缺

### Session S 提示词

```
你需要为 3 个零测试覆盖的模块编写单元测试。

项目根目录: /Users/promenar/Codex/Nexara/native-ui

## 任务

### 1. HtmlExtractor 测试（如不存在则创建）

新建或补充 `test/.../data/rag/HtmlExtractorTest.kt`（如已存在则补充）：

- 测试 `stripHtmlTags` 移除所有标签
- 测试 `<script>` 和 `<style>` 块内容被移除
- 测试 HTML 实体解码（&amp;→&, &lt;→<, &gt;→>, &quot;→", &nbsp;→空格）
- 测试多余空白合并

### 2. PdfExtractor 测试

新建 `test/.../data/rag/PdfExtractorTest.kt`：

- 测试无法打开的文件返回 Failure
- 测试通过 ContentResolver 的文本读取（用 Mock ContentResolver）
- 标记：真 PDF 文本提取需 PDFBox，当前仅验证降级路径不崩溃

### 3. DocumentImporter 测试

新建 `test/.../data/rag/DocumentImporterTest.kt`：

- 测试 MIME 类型路由：pdf→readPdfContent, text/html→readHtmlContent, 其他→readPlainText
- 测试 getFileName() 从 Uri 提取文件名
- 测试 getFileSize() 正确读取大小

### 4. VectorStatsService 测试

新建 `test/.../data/rag/VectorStatsServiceTest.kt`：

- Mock IVectorRepository：`getCount()` 返回总数，`countByType()` 返回类型分布
- 测试 getStats() 聚合正确
- 测试 storageSizeMb 计算公式

## 执行要求
- 所有测试通过: `./gradlew :app:testDebugUnitTest`
- 只新建测试文件，不修改源码
- Mock ContentResolver 使用 `mockk`(relaxed = true)
```

---

## 4. Session T — 会话导出 + PDF 深化

### Session T 提示词

```
你需要实现会话导出功能并完善 PDF 文本提取。

项目根目录: /Users/promenar/Codex/Nexara/native-ui

## 任务 1: 会话导出（TXT/Markdown）

新建 `domain/usecase/ExportSessionUseCase.kt`：

```kotlin
class ExportSessionUseCase(
    private val messageRepository: IMessageRepository,
    private val sessionRepository: ISessionRepository
) {
    suspend fun exportAsText(sessionId: String): String {
        val session = /* 获取 session */ 
        val messages = /* 获取全部消息 */
        return buildString {
            appendLine("# ${session.title}")
            appendLine("Date: ${formatTimestamp(session.createdAt)}")
            appendLine("Model: ${session.modelId}")
            appendLine()
            messages.forEach { msg ->
                appendLine("## ${msg.role.name}")
                appendLine(msg.content)
                if (msg.thinking != null) {
                    appendLine("--- Thinking ---")
                    appendLine(msg.thinking)
                }
                appendLine()
            }
        }
    }

    suspend fun exportAsMarkdown(sessionId: String): String {
        // 同 TXT 但保留 Markdown 格式
        val session = /* ... */
        val messages = /* ... */
        return buildString {
            appendLine("# ${session.title}")
            appendLine("*${formatTimestamp(session.createdAt)}* | Model: `${session.modelId}`")
            appendLine()
            messages.forEach { msg ->
                appendLine("### ${msg.role.name}")
                appendLine()
                appendLine(msg.content)
                appendLine()
                if (msg.thinking != null) {
                    appendLine("> **Thinking:**")
                    appendLine("> ${msg.thinking?.replace("\n", "\n> ")}")
                    appendLine()
                }
            }
        }
    }

    private fun formatTimestamp(ts: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(ts))
}
```

在 ChatScreen 或 SessionSettingsScreen 中添加"导出会话"按钮，调用 `ExportSessionUseCase` 并使用 Android `Intent.ACTION_CREATE_DOCUMENT` 让用户选择保存位置。

### 测试

新建 `test/.../domain/usecase/ExportSessionUseCaseTest.kt`：
- 测试 TXT 导出包含标题和消息
- 测试 Markdown 导出格式正确
- 测试空会话导出不崩溃

## 任务 2: PDFBox 集成（可选，标注实验性）

如果用户后续需要生产级 PDF 提取，在 PdfExtractor 中集成 Apache PDFBox Android port：

```kotlin
// build.gradle.kts 添加（标注 experimental）:
// implementation("com.tom-roush:pdfbox-android:2.0.27.0")

// PdfExtractor 中使用:
// val document = PDDocument.load(inputStream)
// val stripper = PDFTextStripper()
// val text = stripper.getText(document)
```

当前暂不强制集成——标记 `// TODO: PDFBox integration for production use`。

## 执行要求
- 编译 + 测试通过
- ExportSessionUseCase 测试覆盖 TXT 和 Markdown 两种格式
```

---

## 5. Session U — Token 统计仪表盘

### Session U 提示词

```
你需要完善 Token 统计功能。

项目根目录: /Users/promenar/Codex/Nexara/native-ui

## 任务

### 1. TokenStatsRepository（如不存在）

新建或完善 `domain/repository/ITokenStatsRepository.kt` 和实现：

```kotlin
interface ITokenStatsRepository {
    suspend fun getTotalUsage(): TokenUsage
    suspend fun getUsageByModel(): Map<String, TokenUsage>
    suspend fun getUsageBySession(sessionId: String): TokenUsage
    suspend fun resetStats()
}

data class TokenUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val estimated: Boolean = false
)
```

实现从 MessageEntity 的 `tokenUsage` 字段聚合统计。

### 2. TokenUsageScreen 完善

路径: `ui/settings/TokenUsageScreen.kt`（如已存在则完善，不存在则新建）

- 显示总 Token 消耗（Input / Output 分别展示）
- 按模型维度分组统计
- 如果用估算值，标注 ≈ 符号
- 添加"重置统计"按钮

### 3. 测试

新建对应测试文件。

## 执行要求
- 编译 + 测试通过
```

---

## 6. 验证清单

- [ ] `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- [ ] `./gradlew :app:testDebugUnitTest` 全通过
- [ ] 3 个之前零测试的模块至少各有 3 个测试
- [ ] 会话导出功能可生成有效的 TXT/Markdown 文件
- [ ] Token 仪表盘显示总消耗和分模型统计

---

**文档维护者**: AI Assistant
**最后更新**: 2026-05-14
