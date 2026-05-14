# Phase 9 — 发布冲刺 + 单元测试补全方案

> **版本**: v1.0 (2026-05-15)
> **前置状态**: Phase 7-8 完成，总体 84%；6 个模块缺失测试
> **并行策略**: 3 功能会话 + 2 测试会话，全部并行（零文件冲突）

---

## 并行执行总览

```
Phase 9a (功能冲刺)
  ├─ S9-A ── 多模态图片/VLM ──┐
  ├─ S9-B ── Token 仪表盘 ────┤  全部并行
  └─ S9-C ── HTML Artifacts ──┘
                  │
Phase 9b (测试补全)  ─ 可与 9a 同时启动
  ├─ S9-D ── 文件类 Skill 测试 + RerankClient ──┐
  └─ S9-E ── ViewModel 测试补全 + ExecJsSkill ──┘ 并行
```

### 冲突矩阵（零重叠）

| 区域 | S9-A | S9-B | S9-C | S9-D | S9-E |
|------|:----:|:----:|:----:|:----:|:----:|
| `main/` 源代码 | ✏️ | ✏️ | ✏️ | | |
| `test/` 测试代码 | | | | ✏️ | ✏️ |

> 功能会话与测试会话操作不同目录，全部 5 个会话可同时启动。

---

## 测试现状与目标

| 模块 | 现状 | 目标 |
|------|:--:|------|
| `RerankClient` | ❌ 无测试 | ✅ 逻辑与解析测试 |
| `FileReadSkill` | ❌ 无测试 | ✅ 路径安全 + 边界条件 |
| `FileWriteSkill` | ❌ 无测试 | ✅ 路径安全 + 写入验证 |
| `FileListSkill` | ❌ 无测试 | ✅ 目录列表格式 |
| `FileSearchSkill` | ❌ 无测试 | ✅ Glob 匹配 |
| `ExecJsSkill` | ❌ 无测试 | ✅ 基本执行 + 超时 + 错误 |
| `RagViewModel` | ⚠️ 存在但可能未覆盖 | ✅ 级联删除 + renameFolder |
| `ToolExecutor` | ⚠️ 存在但可能未覆盖 | ✅ 审批跳过逻辑 |
| `SettingsViewModel` | ⚠️ 存在但可能未覆盖 | ✅ 新技能列表断言 |

---

## SESSION 9-A — 多模态图片/VLM 支持

**文件**: `ChatScreen.kt`, `ChatViewModel.kt`, `Message.kt`, `OpenAIProtocol.kt`
**时长**: ~2h
**依赖**: 无

### 提示词（直接复制）

```
## 任务：Nexara 多模态图片上传与 VLM 视觉理解

### 背景
当前 Nexara 仅支持文本对话。需要加入图片上传、预览、以及 VLM (Vision Language Model) 协议适配，
使支持 Vision 能力的模型（GPT-4V、Claude 3.5 Sonnet、Gemini 1.5 Pro 等）能理解图片。

### 项目路径
k:/Nexara/native-ui

### 任务 1：ChatInputBar 添加图片选择按钮（T9.1）

文件: app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt

在 ChatInputBar 的 Row 中，BasicTextField 之前添加一个图片选择 IconButton：

```kotlin
// 新增状态
var selectedImageUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }

// 文件选择器
val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
) { uris -> selectedImageUris = uris }

// ChatInputBar 中 BasicTextField 之前新增：
IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = NexaraColors.Primary)
}
```

需要 import: `androidx.activity.compose.rememberLauncherForActivityResult`
和 `androidx.activity.result.contract.ActivityResultContracts`

### 任务 2：图片预览缩略图

文件: 同上 ChatScreen.kt

ChatInputBar 上方（或输入栏与消息列表之间）添加缩略图预览行：

```kotlin
if (selectedImageUris.isNotEmpty()) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(selectedImageUris) { uri ->
            Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = { selectedImageUris = selectedImageUris - uri },
                    modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                ) {
                    Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
```

需要 import: `coil.compose.AsyncImage` (Coil 3.x 已集成)

### 任务 3：图片编码为 base64 发送

文件: app/src/main/java/com/promenar/nexara/ui/chat/ChatViewModel.kt

在 sendMessage() 中，将 selectedImageUris 编码为 data URL 并附加到 PromptRequest：

```kotlin
fun sendMessage(text: String, imageUris: List<Uri> = emptyList()) {
    // ... 现有逻辑 ...
    
    // 图片编码
    val imageContents = imageUris.mapNotNull { uri ->
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            bytes?.let { "data:$mimeType;base64,${Base64.encodeToString(it, Base64.NO_WRAP)}" }
        } catch (e: Exception) { null }
    }
    // 将 imageContents 传递给 generateMessage() 或存入 Message
}
```

### 任务 4：VLM 协议适配

文件: app/src/main/java/com/promenar/nexara/data/remote/protocol/OpenAIProtocol.kt

在 sendPrompt() 的消息构造中，当 message 包含 images 时，使用 OpenAI Vision API 格式：

```kotlin
// 在 { role = "user" } 的消息中
val content = if (images.isNotEmpty()) {
    val parts = mutableListOf<JsonObject>()
    parts.add(buildJsonObject { put("type", "text"); put("text", text) })
    images.forEach { img ->
        parts.add(buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject {
                put("url", img)
                put("detail", "auto")
            })
        })
    }
    JsonArray(parts)
} else {
    JsonPrimitive(text)
}
```

类似适配 AnthropicProtocol（Claude 使用不同的 image 格式）。

### 任务 5：ChatBubble 渲染图片

文件: 同上 ChatScreen.kt

在 ChatBubble 中，USER 消息气泡内，MarkdownText 下方添加图片展示。
ASSISTANT 消息如有返回图片（如 ImageGenerationSkill 结果），也渲染图片。

```kotlin
// 在 message.images 非空时渲染
if (!message.images.isNullOrEmpty()) {
    message.images!!.forEach { img ->
        AsyncImage(
            model = img,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.FillWidth
        )
    }
}
```

### 验证标准
- 输入栏有图片选择按钮
- 选择图片后显示缩略图，可删除
- 发送后 LLM 能理解图片内容（Vision 模型）
- 历史消息中显示已发送的图片
```

---

## SESSION 9-B — Token 统计仪表盘

**文件**: `TokenUsageScreen.kt`（或类似占位文件）, `TokenStatsRepository.kt`
**时长**: ~1.5h
**依赖**: 无

### 提示词（直接复制）

```
## 任务：Nexara Token 统计仪表盘实现

### 背景
TokenUsageScreen 当前可能是占位或仅展示基础数据。需要接入 PostProcessor 实时统计，
展示会话级 + 全局级 Token 消耗，含图表和趋势。

### 项目路径
k:/Nexara/native-ui

### 任务 1：确认数据源

在 app/src/main/java/com/promenar/nexara 中搜索 "TokenUsageScreen" 找到现有文件。
如果是占位，找到对应的路由和 ViewModel。

PostProcessor 已在每轮对话后计算 TokenUsage 并写入 Session.stats。
数据可从 SessionRepository 或 ChatStore 读取。

### 任务 2：实现 TokenUsageScreen

文件: 找到 TokenUsageScreen.kt 后重写。

界面结构：
- 顶部：全局统计卡片（总输入/输出 Token、估算费用）
- 中间：当前会话统计（如有当前会话）
- 列表：按会话分组的 Token 消耗排行（Top 10）
- 图表：近 7 天 Token 消耗趋势（可使用 Canvas 简单绘制）

```kotlin
@Composable
fun TokenUsageScreen(viewModel: TokenUsageViewModel = ...) {
    val stats by viewModel.stats.collectAsState()
    
    Scaffold { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(20.dp)) {
            item { GlobalStatsCard(stats.global) }
            item { Spacer(Modifier.height(16.dp)) }
            item { SessionRankingList(stats.topSessions) }
            item { Spacer(Modifier.height(16.dp)) }
            item { TrendChart(stats.dailyTrend) }
        }
    }
}
```

### 任务 3：TokenUsageViewModel

新建或修改 TokenUsageViewModel：

```kotlin
class TokenUsageViewModel(
    private val sessionRepository: ISessionRepository,
    private val tokenStatsRepository: ITokenStatsRepository
) : ViewModel() {
    
    data class TokenStatsState(
        val global: GlobalStats = GlobalStats(),
        val topSessions: List<SessionTokenStats> = emptyList(),
        val dailyTrend: List<DailyStats> = emptyList()
    )
    
    fun loadStats() {
        viewModelScope.launch {
            val allSessions = sessionRepository.observeAll().first()
            val totalInput = allSessions.sumOf { it.stats?.billing?.chatInput?.count ?: 0 }
            val totalOutput = allSessions.sumOf { it.stats?.billing?.chatOutput?.count ?: 0 }
            val dailyTrend = tokenStatsRepository.getDailyStats(7)
            // ...
        }
    }
}
```

### 任务 4：费用估算

根据当前使用的模型 ID，从 ModelSpecs 查找定价信息：
- 输入: $X / 1M tokens
- 输出: $Y / 1M tokens

在统计卡片中显示估算费用。
如果模型无定价信息，显示 "Pricing unavailable"。

### 验证标准
- TokenUsageScreen 真实展示统计数据
- 切换不同会话后数据更新
- 趋势图表有数据点
```

---

## SESSION 9-C — HTML Artifacts 预览

**文件**: `HtmlArtifactRenderer.kt`(新), `MarkdownText.kt`, `CodeBlockHeader.kt`
**时长**: ~2h
**依赖**: 无

### 提示词（直接复制）

```
## 任务：Nexara HTML Artifacts —— 代码块 WebView 实时预览

### 背景
对标 Cherry Studio 的 HTML Artifacts 功能。当 LLM 返回 HTML/CSS/JS 代码块时，
在代码块下方展示 WebView 实时预览，支持全屏分屏模式和 PNG 导出。

### 项目路径
k:/Nexara/native-ui

### 任务 1：创建 HtmlArtifactCard

新建文件: app/src/main/java/com/promenar/nexara/ui/renderer/HtmlArtifactCard.kt

```kotlin
@Composable
fun HtmlArtifactCard(
    htmlCode: String,
    language: String?,
    modifier: Modifier = Modifier
) {
    val isHtml = language in listOf("html", "htm", "svg")
    if (!isHtml) return
    
    var expanded by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
    
    NexaraGlassCard(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // 头部：Label + 展开/全屏按钮
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Code, null, tint = NexaraColors.Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("HTML Preview", style = NexaraTypography.labelMedium, color = NexaraColors.Primary)
                }
                Row {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { showFullscreen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Fullscreen, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            // WebView 预览
            AnimatedVisibility(visible = expanded) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = false
                            loadDataWithBaseURL(null, htmlCode, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 400.dp)
                )
            }
        }
    }
    
    // 全屏模态
    if (showFullscreen) {
        Dialog(onDismissRequest = { showFullscreen = false }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = NexaraColors.SurfaceLow
            ) {
                Column {
                    TopAppBar(
                        title = { Text("HTML Preview") },
                        navigationIcon = {
                            IconButton(onClick = { showFullscreen = false }) {
                                Icon(Icons.Rounded.Close, null)
                            }
                        }
                    )
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                loadDataWithBaseURL(null, htmlCode, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
```

需要 import: `android.webkit.WebView`, `androidx.compose.ui.viewinterop.AndroidView`

### 任务 2：集成到代码块渲染

文件: app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt

在 MarkdownText 的 markdownComponents 中，找到代码块渲染的位置。
在 CodeBlockWithHeader 的下方（或内部），当 language 是 HTML/SVG 时，插入 HtmlArtifactCard。

```kotlin
// 在 CodeBlockWithHeader { codeContent() } 之后添加
if (language in listOf("html", "htm", "svg")) {
    HtmlArtifactCard(htmlCode = code, language = language)
}
```

需要从 MarkdownText 获取 fontSize 传递给 HtmlArtifactCard。

### 任务 3：添加 Fullscreen icon import

如果 Icons.Rounded.Fullscreen 不可用，使用 Icons.Rounded.OpenInFull 或 Icons.Rounded.AspectRatio。

### 验证标准
- LLM 返回 HTML 代码块 → 代码下方出现 "HTML Preview" 可折叠区域
- 展开后 WebView 渲染 HTML 内容
- 点击全屏 → 全屏模态 WebView
- 非 HTML 语言代码块不受影响
```

---

## SESSION 9-D — 文件类 Skill + RerankClient 测试

**文件**: 新建 5 个测试文件
**时长**: ~1.5h
**依赖**: 无

### 提示词（直接复制）

```
## 任务：Nexara 文件类 Skill 单元测试补全 + RerankClient 测试

### 背景
Phase 8 新增的 FileReadSkill、FileWriteSkill、FileListSkill、FileSearchSkill 和
RerankClient 均无单元测试。需要补全。

### 项目路径
k:/Nexara/native-ui

### 已有的测试模式参考

项目使用 JUnit 5 + MockK + Google Truth。
测试基目录: app/src/test/java/com/promenar/nexara/
参考: ui/chat/manager/skills/CalculatorSkillTest.kt

通用模板：
```kotlin
package com.promenar.xxx

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class XxxTest {
    @Test
    fun `test case name`() = runTest {
        // arrange → act → assert
    }
}
```

### 任务 1：创建 FileReadSkillTest

新建: app/src/test/java/com/promenar/nexara/ui/chat/manager/skills/FileReadSkillTest.kt

测试用例（使用 @TempDir 创建临时目录作为工作区）：
1. `reads existing file` — 创建临时文件，写入内容，调用 file_read 返回正确内容
2. `returns error for missing file` — 不存在的路径返回错误
3. `returns error for path escaping workspace` — "../" 路径返回 Security 错误
4. `returns error for missing workspacePath` — workspacePath=null 返回错误
5. `returns error for missing path parameter` — 不传 path 返回错误

需要：创建 SkillExecutionContext(stub) with workspacePath, agentId, sessionId

### 任务 2：创建 FileWriteSkillTest

新建: app/src/test/java/com/promenar/nexara/ui/chat/manager/skills/FileWriteSkillTest.kt

测试用例：
1. `writes content to file` — 写入内容，验证文件存在且内容匹配
2. `creates parent directories` — 写入 "subdir/test.txt"，验证父目录被创建
3. `returns error for path escaping workspace` — "../" 安全拦截
4. `returns error for missing content` — 不传 content 返回错误

### 任务 3：创建 FileListSkillTest

新建: app/src/test/java/com/promenar/nexara/ui/chat/manager/skills/FileListSkillTest.kt

测试用例：
1. `lists workspace root` — 创建几个临时文件，list 返回带 [FILE] 标记的列表
2. `lists subdirectory` — 创建子目录+文件，list(path="sub") 返回正确内容
3. `returns error for non-existent directory` — 不存在的目录返回错误
4. `lists empty directory` — 空目录返回 "(empty)"

### 任务 4：创建 FileSearchSkillTest

新建: app/src/test/java/com/promenar/nexara/ui/chat/manager/skills/FileSearchSkillTest.kt

测试用例：
1. `finds files by glob pattern` — 创建 "test.kt", "test.txt", "other.kt"，pattern="*.kt" 返回 2 个
2. `finds files in subdirectories` — 子目录中的文件也被搜到
3. `returns no match message` — 无匹配时返回 "No files matching"
4. `returns error for missing pattern` — 不传 pattern 返回错误

### 任务 5：创建 RerankClientTest

新建: app/src/test/java/com/promenar/nexara/data/rag/RerankClientTest.kt

注意：文件中类名是 `RerankClient`（可能在 `Reranker.kt` 中）。

测试用例：
1. `returns empty for empty candidates` — 空列表返回空
2. `returns input for single candidate` — 单个候选人直接返回
3. `parses valid rerank JSON response` — Mock LlmProtocol 返回有效 JSON → 验证排序
4. `returns original order on parse failure` — Mock 返回非 JSON → 保留原排序
5. `handles LLM timeout gracefully` — Mock 抛异常 → 返回原列表

需要 mock: com.promenar.nexara.data.remote.protocol.LlmProtocol
使用: io.mockk.coEvery { protocol.sendPromptSync(any()) } returns ...

### 验证标准
- 所有测试通过 `./gradlew test --tests "com.promenar.nexara.*SkillTest"`
- 每个 Skill 至少 3 个测试用例
- 安全测试覆盖（路径逃逸拦截）
```

---

## SESSION 9-E — ViewModel 测试补全 + ExecJsSkill 测试

**文件**: 修改 3 个已有测试 + 新建 1 个测试
**时长**: ~1.5h
**依赖**: 无

### 提示词（直接复制）

```
## 任务：Nexara ViewModel 测试覆盖补全 + ExecJsSkill 测试

### 背景
3 个已有 ViewModel 测试需要补全 Phase 7-8 新增逻辑的测试覆盖。
ExecJsSkill 需要新建测试文件。

### 项目路径
k:/Nexara/native-ui

### 任务 1：补全 RagViewModelTest

文件: app/src/test/java/com/promenar/nexara/ui/rag/RagViewModelTest.kt

先读出当前文件内容，然后在文件末尾追加 Phase 7 新增逻辑的测试：

```kotlin
@Test
fun `deleteCollection cascade deletes documents and vectors`() = runTest {
    // Arrange: 创建含有 2 个文档的文件夹
    val folder = Folder(id = "f1", name = "Test")
    val docs = listOf(
        Document(id = "d1", folderId = "f1", title = "Doc1", content = "a"),
        Document(id = "d2", folderId = "f1", title = "Doc2", content = "b")
    )
    coEvery { folderRepository.getById("f1") } returns folder
    coEvery { documentRepository.getByFolderId("f1") } returns docs
    coEvery { deleteDocumentUseCase.invoke(any()) } just Runs
    coEvery { folderRepository.delete(folder) } just Runs
    coEvery { folderRepository.observeAll() } returns emptyFlow()
    coEvery { documentRepository.observeAll() } returns emptyFlow()
    
    // Act
    viewModel.deleteCollection("f1")
    advanceUntilIdle()
    
    // Assert
    coVerify { deleteDocumentUseCase.invoke(listOf("d1", "d2")) }
    coVerify { folderRepository.delete(folder) }
}

@Test
fun `renameFolder updates folder name`() = runTest {
    val folder = Folder(id = "f1", name = "Old")
    coEvery { folderRepository.getById("f1") } returns folder
    coEvery { folderRepository.update(any()) } just Runs
    
    viewModel.renameFolder("f1", "New")
    advanceUntilIdle()
    
    coVerify { folderRepository.update(match { it.name == "New" }) }
}

@Test
fun `deleteFolder delegates to deleteCollection`() = runTest {
    // Same setup as deleteCollection test
    // Verify deleteFolder("f1") 调用了相同的级联逻辑
}
```

### 任务 2：补全 ToolExecutorTest

文件: app/src/test/java/com/promenar/nexara/ui/chat/manager/ToolExecutorTest.kt

先读出当前文件，追加审批跳过逻辑的测试：

```kotlin
@Test
fun `skips tools in pending approval list`() = runTest {
    // Arrange: Assistant 消息上 pendingApprovalToolIds = ["tc1"]
    val session = mockSessionWithTools(messages = listOf(
        Message(id = "msg1", role = MessageRole.ASSISTANT,
            toolCalls = listOf(ToolCall("tc1", "file_write", "{...}"), ToolCall("tc2", "file_read", "{...}")),
            pendingApprovalToolIds = listOf("tc1"))
    ))
    coEvery { store.getSession(any()) } returns session
    coEvery { skillRegistry.getSkill("file_write") } returns mockSkill
    coEvery { skillRegistry.getSkill("file_read") } returns mockSkill
    
    // Act
    toolExecutor.executeTools("s1", listOf(ToolCall("tc1", "file_write", "{...}"), ToolCall("tc2", "file_read", "{...}")), "msg1")
    
    // Assert: tc1 (pending) is skipped, tc2 is executed
    coVerify(exactly = 0) { skillRegistry.getSkill("file_write") } // was NOT called
    coVerify(exactly = 1) { skillRegistry.getSkill("file_read") } // WAS called
}
```

### 任务 3：补全 SettingsViewModelTest

文件: app/src/test/java/com/promenar/nexara/ui/settings/SettingsViewModelTest.kt

追加验证：

```kotlin
@Test
fun `skills list excludes deprecated current_time`() = runTest {
    val skills = viewModel.skills.value
    val currentTimeSkill = skills.find { it.id == "current_time" }
    assertThat(currentTimeSkill).isNull() // 不应该存在
}

@Test
fun `skills list includes image_generation`() = runTest {
    val skills = viewModel.skills.value
    val imgSkill = skills.find { it.id == "image_generation" }
    assertThat(imgSkill).isNotNull()
    assertThat(imgSkill!!.name).isNotEmpty()
}

@Test
fun `skills list includes file tools`() = runTest {
    val skills = viewModel.skills.value
    val toolIds = skills.map { it.id }
    assertThat(toolIds).containsAtLeast("file_read", "file_list", "file_search", "exec_js")
}

@Test
fun `mcp server sync calls updateMcpTools`() = runTest {
    // Mock McpClient.listTools() 返回工具列表
    // Verify mcpSkillRegistry.updateMcpTools() 被调用
}
```

### 任务 4：创建 ExecJsSkillTest

新建: app/src/test/java/com/promenar/nexara/ui/chat/manager/skills/ExecJsSkillTest.kt

注意：ExecJsSkill 依赖 WebView (Android API)，单元测试需要使用 Robolectric 或 mock。

```kotlin
package com.promenar.nexara.ui.chat.manager.skills

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

// ExecJsSkill requires WebView → use Robolectric or create a testable variant
// For pure unit test: test parameter validation (no WebView needed)
class ExecJsSkillTest {
    // 注意：此 Skill 需要 Application Context 创建 WebView
    // 以下测试仅覆盖参数校验（不依赖 WebView）
    
    @Test
    fun `returns error for missing code parameter`() = runTest {
        // 此测试需要 appContext，如不可用则跳过
        // val skill = ExecJsSkill(mockContext)
        // val result = skill.execute(emptyMap(), ctx)
        // assertThat(result.status).isEqualTo("error")
    }
    
    @Test
    fun `returns error for code too long`() = runTest { /* > 50000 chars */ }
    
    // 完整执行测试需要 Robolectric @RunWith(AndroidJUnit4::class)
    // 或使用 org.robolectric.RobolectricTestRunner
}
```

> ⚠️ ExecJsSkill 的完整测试需要 Android 环境（WebView），建议使用 Robolectric 或在 androidTest 中实现。
如果纯 unit test 不可行，至少覆盖参数校验逻辑。

### 验证标准
- `./gradlew test` 或 IDE 运行所有新增测试通过
- RagViewModelTest: 级联删除 + renameFolder 覆盖
- ToolExecutorTest: 审批跳过覆盖
- SettingsViewModelTest: 新增技能列表断言
```

---

## 完成后预估

```
Phase 9 完成后
  Chat 引擎    90%→95%  (+多模态)
  Token 统计   60%→85%  (+仪表盘)
  Agent 引擎   75%→82%  (+HTML Artifacts)
  测试覆盖率           (+12 个新测试文件)
  ────────────────────────────
  总体进度     84%→92%
```
