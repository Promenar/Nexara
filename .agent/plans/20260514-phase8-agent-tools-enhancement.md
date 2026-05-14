# Phase 8 — Agent 工具系统重构与增强方案

> **版本**: v1.0 (2026-05-14)
> **前置状态**: 审计确认 McpClient/McpSkill 完整实现，但 MCP 同步链路断裂；无文件系统工具；ImageGenerationSkill 设置中不可见
> **并行策略**: 2 会话并行（零文件冲突）

---

## 先行设计决策

### 工具分类模型

```
┌─────────────────────────────────────────────┐
│              内置工具体系                     │
│                                              │
│  被动注入类 (Passive Injection)              │
│  ┌──────────────────────────────────┐        │
│  │ 系统时间 → ContextBuilder 直接注入 │        │
│  │ 工具使用说明 → 条件注入              │        │
│  └──────────────────────────────────┘        │
│        ↑ 不注册为 Function Calling Tool       │
│                                              │
│  主动调用类 (Active Call)                    │
│  ┌──────────────────────────────────┐        │
│  │ calculator     数学计算             │        │
│  │ web_search     网络搜索（分发器）    │        │
│  │ search_searxng SearXNG 搜索        │        │
│  │ search_tavily  Tavily 搜索         │        │
│  │ create_tool    元工具（创建新工具）   │        │
│  │ image_generation AI 图像生成       │        │
│  │ file_read      文件读取    ← NEW   │        │
│  │ file_write     文件写入    ← NEW   │        │
│  │ file_list      目录列表    ← NEW   │        │
│  │ file_search    文件搜索    ← NEW   │        │
│  └──────────────────────────────────┘        │
│        ↓ 注册为 ProtocolTool，LLM 按需调用    │
│                                              │
│  MCP 动态工具 (MCP Tools)                    │
│  ┌──────────────────────────────────┐        │
│  │ 来自 MCP Server 的远程工具          │        │
│  │ McpClient.listTools() → 动态注册   │        │
│  └──────────────────────────────────┘        │
└─────────────────────────────────────────────┘
```

### 内置工具是否需要扩充？

| 判断 | 结论 |
|------|------|
| 搜索工具 | ✅ 够了（3 引擎 + 分发器）|
| 计算工具 | ✅ 够了（自研解析器）|
| 时间 | ✅ 够了（被动注入，主动调用冗余）|
| 生图 | ⚠️ 已实现但设置中不可见，需暴露 |
| **文件操作** | ❌ **缺失**，Agent 模式下最重要的能力 |
| 代码执行 | ❌ 缺失但风险高，待 MCP 接入后由外部沙箱提供 |

> **结论**: 当前缺少文件系统工具。Agent 无法读写工作区文件，是最直接的短板。

---

## 并行执行总览

```
Session A (T8.1+T8.2+T8.3) ── 工具分类 + 生图暴露 + MCP 同步修复 ──┐
                                                                       ├─ 并行
Session B (T8.4) ── 文件系统工具 ────────────────────────────────────┘
              │
              ▼  全部完成后
Session C (T8.5) ── 工具安全审批增强（依赖 S4 完成后的文件工具）
```

### 文件冲突矩阵

| 文件 | S-A | S-B | S-C |
|------|:---:|:---:|:---:|
| `NexaraApplication.kt` | ✏️ | ✏️ | |
| `SettingsViewModel.kt` | ✏️ | | |
| `SkillsScreen.kt` | ✏️ | | |
| `CurrentTimeSkill.kt` | ✏️ | | |
| `McpSkillRegistry.kt` | ✏️ | | |
| `FileReadSkill.kt` (新) | | ✏️ | |
| `FileWriteSkill.kt` (新) | | ✏️ | |
| `FileListSkill.kt` (新) | | ✏️ | |
| `FileSearchSkill.kt` (新) | | ✏️ | |
| `ApprovalManager.kt` | | | ✏️ |
| `SessionSettingsSheet.kt` | | | ✏️ |

> ⚠️ S-A 和 S-B 同时编辑 `NexaraApplication.kt`。S-A 修改注册段（移除时间工具、添加 MCP 同步），S-B 添加文件工具注册。两者在**不同行**，但建议 S-A 先完成、S-B 在其基础上修改；或 S-B 的提示词中注明"在 S-A 基础上追加"。

---

## SESSION A — 工具分类重构 + 生图暴露 + MCP 同步修复（T8.1+T8.2+T8.3）

**文件**: `NexaraApplication.kt`, `SettingsViewModel.kt`, `SkillsScreen.kt`, `CurrentTimeSkill.kt`, `McpSkillRegistry.kt`
**时长**: ~2h

### 提示词（直接复制）

```
## 任务：Nexara 工具系统重构 —— 被动/主动分类 + ImageGenerationSkill 暴露 + MCP 同步修复

### 背景
存在三个需要修复的问题：
1. CurrentTimeSkill 是冗余的——系统时间已通过 ContextBuilder 被动注入到每轮对话的 System Prompt，
   无需再暴露为可调用工具。应将其从工具列表中移除，仅保留为被动注入。
2. ImageGenerationSkill 已在 NexaraApplication 注册为工具，但 SettingsViewModel.loadSkills()
   中未列出，用户无法在设置界面看到/控制它。
3. McpClient 和 McpSkill 已完成，但 McpSkillRegistry.updateMcpTools() 从未被调用——MCP 服务器
   同步了工具列表但未注入到 Skill Registry，LLM 无法实际调用 MCP 工具。

### 项目路径
k:/Nexara/native-ui

---

### 任务 1：CurrentTimeSkill 退化为纯被动注入（T8.1）

#### 改动 1.1：从工具注册中移除

文件: app/src/main/java/com/promenar/nexara/NexaraApplication.kt

在 presetSkillRegistry 注册段中删除 CurrentTimeSkill 的注册行。
找到类似以下的行并删除：

register(CurrentTimeSkill())

#### 改动 1.2：保留类但标记为废弃

文件: app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/CurrentTimeSkill.kt

在类上添加 @Deprecated 注解和 KDoc：

/**
 * 系统时间查询技能 — 已废弃为被动注入。
 *
 * 时间已通过 ContextBuilder.buildSystemPrompt() 在每轮对话中自动注入到 System Prompt
 * （格式: [System Time: yyyy-MM-dd HH:mm:ss 星期X]），LLM 无需主动调用此工具。
 * 保留此类仅用于向后兼容或未来可能的精确时间查询场景。
 */
@Deprecated("时间已通过 ContextBuilder 被动注入，无需注册为可调用工具")
class CurrentTimeSkill : SkillDefinition { ... }

注意：类的 execute() 方法保持不变，只是不再注册到 SkillRegistry。

#### 改动 1.3：从 SettingsViewModel 默认技能列表移除

文件: app/src/main/java/com/promenar/nexara/ui/settings/SettingsViewModel.kt

在 loadSkills() 方法中（约第 274-286 行）：

- 从默认 enabledSet 中移除 "current_time"
- 从 _skills.value 列表中移除对应的 SkillInfo 行

修改后默认 enabledSet 应为：
setOf("web_search", "calculator", "create_tool")

#### 改动 1.4：更新 SkillsScreen 图标映射

文件: app/src/main/java/com/promenar/nexara/ui/settings/SkillsScreen.kt

找到 skillIcons map（约第 87-95 行），移除 "current_time" 条目。

同时检查 searchable preset skills 列表（如果有），移除 current_time。

---

### 任务 2：暴露 ImageGenerationSkill 到设置界面（T8.2）

#### 改动 2.1：添加到 skill 列表

文件: app/src/main/java/com/promenar/nexara/ui/settings/SettingsViewModel.kt

在 loadSkills() 的 _skills.value 列表中添加一行（放在 create_tool 之后）：

SkillInfo("image_generation", "Image Generation", "Generate AI images from text descriptions",
    enabledSet?.contains("image_generation") ?: true),

#### 改动 2.2：添加到图标映射

文件: app/src/main/java/com/promenar/nexara/ui/settings/SkillsScreen.kt

在 skillIcons map 中添加：

"image_generation" to Icons.Rounded.Image,

需要确认 import: androidx.compose.material.icons.rounded.Image

#### 改动 2.3（可选）：添加中英文字符串

如果 SkillsScreen 使用 string resource 显示名称，添加：

中文 strings.xml:
<string name="skill_image_generation">图像生成</string>
<string name="skill_image_generation_desc">根据文本描述生成 AI 图像</string>

英文 strings.xml:
<string name="skill_image_generation">Image Generation</string>
<string name="skill_image_generation_desc">Generate AI images from text descriptions</string>

如果 SkillInfo 使用硬编码字符串（如上），则跳过此步。

---

### 任务 3：修复 MCP 工具同步链路（T8.3）

#### 改动 3.1：在 SettingsViewModel.syncMcpServer() 中添加工具注册回调

文件: app/src/main/java/com/promenar/nexara/ui/settings/SettingsViewModel.kt

找到 syncMcpServer() 方法。在调用 McpClient.listTools() 获取工具列表后，
添加对 mcpSkillRegistry.updateMcpTools() 的调用。

需要在 SettingsViewModel 构造函数中注入 McpSkillRegistry：

class SettingsViewModel(
    ...
    private val mcpSkillRegistry: McpSkillRegistry? = null
) : ViewModel() {

然后在 syncMcpServer() 的 try 块中，listTools() 成功后：

val tools = mcpClient.listTools()
// ... 现有 UI 更新逻辑 ...

// 新增：将工具同步到 Skill Registry
mcpSkillRegistry?.updateMcpTools(server.name, tools, server.url)

需要导入: com.promenar.nexara.ui.chat.manager.registry.McpSkillRegistry

#### 改动 3.2：在 NexaraApplication 中传递 mcpSkillRegistry

文件: app/src/main/java/com/promenar/nexara/NexaraApplication.kt

确认 mcpSkillRegistry 是 public val 属性（应该已经是），以便 SettingsViewModel.Factory 可以访问。

检查 SettingsViewModel.factory() 中是否已注入 mcpSkillRegistry，如果没有则添加。

#### 改动 3.3：确认 ChatViewModel 中工具列表包含 MCP 工具

文件: app/src/main/java/com/promenar/nexara/ui/chat/ChatViewModel.kt

确认 buildToolList() 调用的 skillRegistry 是 ModularSkillRegistry，
它应该已经组合了 DefaultSkillRegistry + UserSkillRegistry + McpSkillRegistry。

检查 NexaraApplication 中 ModularSkillRegistry 的构造是否包含了 mcpSkillRegistry。

如果不是，修改 ModularSkillRegistry 的构造：

val modularSkillRegistry = ModularSkillRegistry(
    listOf(presetSkillRegistry, userSkillRegistry, mcpSkillRegistry)
)

### 验证标准
- Settings → 技能管理中不再显示 "Current Time" 工具
- Settings → 技能管理中显示 "Image Generation" 工具，可开关
- MCP 服务器同步后，其工具出现在 LLM 的工具列表中
- ContextBuilder 仍然注入 System Time 到每轮对话
```

---

## SESSION B — 文件系统工具（T8.4）

**文件**: 新建 4 个 Skill 文件 + `NexaraApplication.kt`（注册）
**时长**: ~2h
**注意**: 如 Session A 也修改了 NexaraApplication.kt，请以 S-A 完成后的代码为基线

### 提示词（直接复制）

```
## 任务：Nexara Agent 文件系统工具实现

### 背景
当前 Agent 无法操作工作区文件。需要实现 4 个文件系统工具，使 LLM 能读写工作区。
全部操作限定在 Session.workspacePath 内，不可逃逸。

### 项目路径
k:/Nexara/native-ui

### 工具设计

| 工具 ID | 名称 | 用途 | 风险 |
|---------|------|------|:---:|
| file_read | read_file | 读取文件内容 | 低 |
| file_write | write_file | 写入/创建文件 | 中 |
| file_list | list_dir | 列出目录内容 | 低 |
| file_search | search_file | 按名称搜索文件 | 低 |

所有操作绑定 Session.workspacePath 作为根目录，路径参数必须为相对路径。

---

### 任务 1：创建 FileReadSkill（T8.4a）

新建文件: app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/FileReadSkill.kt

package com.promenar.nexara.ui.chat.manager.skills

import com.promenar.nexara.data.model.ToolResult
import com.promenar.nexara.ui.chat.manager.registry.SkillDefinition
import com.promenar.nexara.ui.chat.manager.registry.SkillExecutionContext
import java.io.File

class FileReadSkill : SkillDefinition {
    override val id = "file_read"
    override val name = "read_file"
    override val description = "Read the contents of a file in the workspace."
    override val mcpServerId: String? = null

    override val parametersSchema = """{
        "type":"object",
        "properties":{
            "path":{"type":"string","description":"Relative path to the file within the workspace"}
        },
        "required":["path"]
    }""".trimIndent()

    override suspend fun execute(args: Map<String, Any>, context: SkillExecutionContext): ToolResult {
        val path = args["path"]?.toString()
            ?: return ToolResult("err", "Missing required parameter: path", "error")

        val wsPath = context.workspacePath
            ?: return ToolResult("err", "No workspace path configured", "error")

        return try {
            val file = File(wsPath, path).canonicalFile
            // 安全检查：禁止逃逸工作区
            if (!file.path.startsWith(File(wsPath).canonicalPath)) {
                return ToolResult("err", "Security: path escapes workspace", "error")
            }
            if (!file.exists()) {
                return ToolResult("err", "File not found: $path", "error")
            }
            if (file.length() > 1_000_000) {
                return ToolResult("err", "File too large (>1MB), use offset/limit parameters", "error")
            }
            val content = file.readText()
            ToolResult("file_read_${System.currentTimeMillis()}", content)
        } catch (e: Exception) {
            ToolResult("file_read_${System.currentTimeMillis()}", "Read failed: ${e.message}", "error")
        }
    }
}

---

### 任务 2：创建 FileWriteSkill（T8.4b）

新建文件: app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/FileWriteSkill.kt

package com.promenar.nexara.ui.chat.manager.skills

import com.promenar.nexara.data.model.ToolResult
import com.promenar.nexara.ui.chat.manager.registry.SkillDefinition
import com.promenar.nexara.ui.chat.manager.registry.SkillExecutionContext
import java.io.File

class FileWriteSkill : SkillDefinition {
    override val id = "file_write"
    override val name = "write_file"
    override val description = "Write or overwrite content to a file in the workspace. Creates parent directories if needed."
    override val mcpServerId: String? = null

    override val parametersSchema = """{
        "type":"object",
        "properties":{
            "path":{"type":"string","description":"Relative path to the file within the workspace"},
            "content":{"type":"string","description":"The content to write to the file"}
        },
        "required":["path","content"]
    }""".trimIndent()

    override suspend fun execute(args: Map<String, Any>, context: SkillExecutionContext): ToolResult {
        val path = args["path"]?.toString()
            ?: return ToolResult("err", "Missing required parameter: path", "error")
        val content = args["content"]?.toString()
            ?: return ToolResult("err", "Missing required parameter: content", "error")

        val wsPath = context.workspacePath
            ?: return ToolResult("err", "No workspace path configured", "error")

        return try {
            val file = File(wsPath, path).canonicalFile
            if (!file.path.startsWith(File(wsPath).canonicalPath)) {
                return ToolResult("err", "Security: path escapes workspace", "error")
            }
            file.parentFile?.mkdirs()
            file.writeText(content)
            val size = file.length()
            ToolResult("file_write_${System.currentTimeMillis()}",
                "Successfully wrote ${content.length} chars (${size} bytes) to $path")
        } catch (e: Exception) {
            ToolResult("file_write_${System.currentTimeMillis()}", "Write failed: ${e.message}", "error")
        }
    }
}

---

### 任务 3：创建 FileListSkill（T8.4c）

新建文件: app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/FileListSkill.kt

package com.promenar.nexara.ui.chat.manager.skills

import com.promenar.nexara.data.model.ToolResult
import com.promenar.nexara.ui.chat.manager.registry.SkillDefinition
import com.promenar.nexara.ui.chat.manager.registry.SkillExecutionContext
import java.io.File

class FileListSkill : SkillDefinition {
    override val id = "file_list"
    override val name = "list_dir"
    override val description = "List files and directories in the workspace."
    override val mcpServerId: String? = null

    override val parametersSchema = """{
        "type":"object",
        "properties":{
            "path":{"type":"string","description":"Relative path to directory. Default: workspace root"}
        },
        "required":[]
    }""".trimIndent()

    override suspend fun execute(args: Map<String, Any>, context: SkillExecutionContext): ToolResult {
        val relativePath = args["path"]?.toString() ?: ""

        val wsPath = context.workspacePath
            ?: return ToolResult("err", "No workspace path configured", "error")

        return try {
            val dir = File(wsPath, relativePath).canonicalFile
            if (!dir.path.startsWith(File(wsPath).canonicalPath)) {
                return ToolResult("err", "Security: path escapes workspace", "error")
            }
            if (!dir.exists() || !dir.isDirectory) {
                return ToolResult("err", "Directory not found: $relativePath", "error")
            }

            val sb = StringBuilder()
            sb.appendLine("Contents of ${relativePath.ifEmpty { "/" }}:")
            dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                val type = if (f.isDirectory) "[DIR] " else "[FILE]"
                val size = if (f.isFile) " (${formatSize(f.length())})" else ""
                sb.appendLine("  $type${f.name}$size")
            } ?: sb.appendLine("  (empty)")

            ToolResult("file_list_${System.currentTimeMillis()}", sb.toString())
        } catch (e: Exception) {
            ToolResult("file_list_${System.currentTimeMillis()}", "List failed: ${e.message}", "error")
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}

---

### 任务 4：创建 FileSearchSkill（T8.4d）

新建文件: app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/FileSearchSkill.kt

package com.promenar.nexara.ui.chat.manager.skills

import com.promenar.nexara.data.model.ToolResult
import com.promenar.nexara.ui.chat.manager.registry.SkillDefinition
import com.promenar.nexara.ui.chat.manager.registry.SkillExecutionContext
import java.io.File

class FileSearchSkill : SkillDefinition {
    override val id = "file_search"
    override val name = "search_file"
    override val description = "Search for files by name pattern in the workspace."
    override val mcpServerId: String? = null

    override val parametersSchema = """{
        "type":"object",
        "properties":{
            "pattern":{"type":"string","description":"File name pattern, supports wildcards (e.g. *.kt, test*.txt)"}
        },
        "required":["pattern"]
    }""".trimIndent()

    override suspend fun execute(args: Map<String, Any>, context: SkillExecutionContext): ToolResult {
        val pattern = args["pattern"]?.toString()
            ?: return ToolResult("err", "Missing required parameter: pattern", "error")

        val wsPath = context.workspacePath
            ?: return ToolResult("err", "No workspace path configured", "error")

        return try {
            val root = File(wsPath).canonicalFile
            val regex = globToRegex(pattern)
            val results = mutableListOf<String>()
            root.walkTopDown().maxDepth(20).forEach { file ->
                val relative = file.relativeTo(root).path
                if (file.isFile && regex.matches(file.name)) {
                    results.add(relative)
                }
            }

            if (results.isEmpty()) {
                ToolResult("file_search_${System.currentTimeMillis()}", "No files matching '$pattern' found.")
            } else {
                ToolResult("file_search_${System.currentTimeMillis()}",
                    "Found ${results.size} file(s):\n${results.joinToString("\n") { "  $it" }}")
            }
        } catch (e: Exception) {
            ToolResult("file_search_${System.currentTimeMillis()}", "Search failed: ${e.message}", "error")
        }
    }

    private fun globToRegex(glob: String): Regex {
        val escaped = glob
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex("^$escaped$", RegexOption.IGNORE_CASE)
    }
}

---

### 任务 5：注册到 NexaraApplication

文件: app/src/main/java/com/promenar/nexara/NexaraApplication.kt

在 presetSkillRegistry 注册段中添加（注意在 S-A 修改后的基线上添加）：

register(FileReadSkill())
register(FileWriteSkill())
register(FileListSkill())
register(FileSearchSkill())

需要添加 import:
import com.promenar.nexara.ui.chat.manager.skills.FileReadSkill
import com.promenar.nexara.ui.chat.manager.skills.FileWriteSkill
import com.promenar.nexara.ui.chat.manager.skills.FileListSkill
import com.promenar.nexara.ui.chat.manager.skills.FileSearchSkill

### 任务 6：添加到默认启用的技能列表

文件: app/src/main/java/com/promenar/nexara/ui/settings/SettingsViewModel.kt

在 loadSkills() 的 _skills.value 列表末尾（create_tool 之后）添加：

SkillInfo("file_read", "File Read", "Read file contents from workspace",
    enabledSet?.contains("file_read") ?: true),
SkillInfo("file_write", "File Write", "Write/create files in workspace",
    enabledSet?.contains("file_write") ?: true),
SkillInfo("file_list", "List Directory", "List workspace directory contents",
    enabledSet?.contains("file_list") ?: true),
SkillInfo("file_search", "Search Files", "Search files by name pattern",
    enabledSet?.contains("file_search") ?: true)

同时在默认 enabledSet 中添加 "file_read", "file_list", "file_search"（不包括 file_write，安全性）。

在 SkillsScreen.kt 的 skillIcons map 中添加对应的图标。

### 验证标准
- LLM 调用 file_list(path="") → 返回工作区根目录文件列表
- LLM 调用 file_read(path="test.txt") → 返回文件内容
- LLM 调用 file_write(path="notes.md", content="# Hello") → 创建文件成功
- file_read("../../../etc/passwd") → 返回 Security: path escapes workspace
```

---

## SESSION C — 工具安全审批增强（T8.5）

**文件**: `ApprovalManager.kt`, `SessionSettingsSheet.kt`
**前置**: Session B 完成（文件工具已存在）
**时长**: ~1h

### 提示词（直接复制）

```
## 任务：Nexara 工具安全审批增强 —— 文件写入需要审批

### 背景
当前 ApprovalManager 存在但实际审批流程依赖 pendingApprovalToolIds。
文件写入操作（file_write）具有数据修改能力，应在 Semi-Automatic 模式下触发用户审批。

### 项目路径
k:/Nexara/native-ui

### 任务

#### 改动 1：为高风险工具标记风险等级

文件: app/src/main/java/com/promenar/nexara/ui/chat/manager/ToolExecutor.kt

在 executeTools() 中执行工具前，检查工具的审批需求。

查找现有的 toolsEnabled 检查段（约第 35 行），在之后添加：

// 高风险工具审批检查
val approvalToolIds = targetMsg.pendingApprovalToolIds ?: emptyList()
if (tc.id in approvalToolIds) {
    continue  // 跳过等待审批的工具
}

并在执行成功后将结果同时写入 workspace 用于文件工具验证。

#### 改动 2：确保 ApprovalCard 审批后执行待审批工具

文件: app/src/main/java/com/promenar/nexara/ui/chat/manager/ApprovalManager.kt

检查 resumeGeneration() 中 approved 分支（约第 83-99 行），确认审批通过后：
1. 执行 pendingApprovalToolIds 对应的工具（已在今天早前的 ChatBubble 修复中接入 UI）
2. 清除 pendingApprovalToolIds
3. 继续 Agent 循环

#### 改动 3：SessionSettingsSheet 提示执行模式影响

文件: app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt

在 Tools 面板中，toolsEnabled 开关下方添加说明文字：

当前执行模式：
- Auto: 所有工具自动执行
- Semi: 文件写入等中高风险操作需要审批
- Manual: 所有操作需要审批

（如果已有执行模式选择器，确认其影响审批行为）

### 验证标准
- Semi-Automatic 模式下，LLM 调用 file_write → 用户看到审批卡片
- 用户批准 → 文件写入成功
- Auto 模式下，LLM 调用 file_write → 直接执行无需审批
```

---

## 附录：Phase 8 完成后工具全景

```
内置被动注入 ─────────────────────
  [System Time] → ContextBuilder 自动注入 ✅

内置主动调用 ─────────────────────
  calculator        ✅ 数学计算
  web_search        ✅ 网络搜索（分发器）
  search_searxng    ✅ SearXNG 专用
  search_tavily     ✅ Tavily 专用
  create_tool       ✅ 元工具
  image_generation  ✅ AI 生图（本次暴露到设置）
  file_read         🆕 文件读取
  file_write        🆕 文件写入（需审批）
  file_list         🆕 目录列表
  file_search       🆕 文件搜索

MCP 动态工具 ─────────────────────
  (来自 MCP Server) → McpSkillRegistry 同步 ✅
```

### 预估完成度变化

```
Phase 7 完成后     Phase 8 完成后
Agent 引擎  50% → Agent 引擎  70%
总体进度    75% → 总体进度    82%
```
