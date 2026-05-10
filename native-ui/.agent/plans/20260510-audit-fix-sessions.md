# 审计修复分阶段实施方案

> **生成时间**: 2026-05-10 13:31
> **基线审计**: `.agent/plans/20260510-audit-p0-p3-fixes.md`
> **验证结果**: 21 项中 18 项完全确认，2 项部分确认，1 项（P1-3 描述）误报但底层安全问题同类存在
> **工作区**: `/Users/promenar/Codex/Nexara/native-ui`

---

## 概述

本方案将审计修复拆分为 **13 个独立会话**（4 个阶段），每个会话：
- 操作 **1~4 个文件**，改动范围可控
- 附带**即复制即用的提示词**，无需额外上下文即可启动
- 含**精确的代码变更**（old_str → new_str）
- 含**验证指令**，确保快速模型也能正确执行

### 会话依赖关系

```
Phase 1 (编译恢复) ── 无依赖，可任意顺序执行
    │
Phase 2 (安全修复) ── 无依赖，可任意顺序执行
    │
Phase 3 (功能修正) ── Session 3-5 依赖 3-3；其余无相互依赖
    │
Phase 4 (架构改进) ── 独立，可随时执行
```

---

## Phase 1：恢复编译 — 3 个会话（预估 0.5h）

---

### Session 1-1：修复 P0-1 ProviderListItem 数据类 + P0-2 deleteMcpServer 类型

**涉及文件**（1 个）：
- `app/src/main/java/com/promenar/nexara/ui/settings/SettingsViewModel.kt`

**问题**：
1. `ProviderListItem` 仅定义 `val model: String`，但 `loadProviders()` 和 `persistProviders()` 使用了 `id`/`name`/`typeName`/`baseUrl` 字段 → 编译失败
2. `deleteMcpServer()` 取出 `McpServerUiModel` 传给 `skillRepository.deleteMcpServer()`，但该方法签名要求 `McpServerEntity` → 类型不匹配

**修复方案**：
1. 补齐 `ProviderListItem` 缺失的 4 个字段，为 `model` 添加默认值以保证 JSON 反序列化兼容
2. 新增 `deleteMcpServerById()` 方法（Repository 侧），ViewModel 侧通过 ID 调用

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中执行以下两个修复（同一文件 SettingsViewModel.kt）：

【文件路径】
app/src/main/java/com/promenar/nexara/ui/settings/SettingsViewModel.kt

═══════════════════════════════════════
【变更 1】修复 ProviderListItem 数据类（行 66-68）

查找：
data class ProviderListItem(
    val model: String
)

替换为：
data class ProviderListItem(
    val id: String = "",
    val name: String = "",
    val typeName: String = "",
    val baseUrl: String = "",
    val model: String = ""
)

═══════════════════════════════════════
【变更 2】修复 deleteMcpServer 类型不匹配（行 586-593）

查找：
    fun deleteMcpServer(id: String) {
        viewModelScope.launch {
            val server = _mcpServers.value.find { it.id == id }
            if (server != null) {
                app.skillRepository.deleteMcpServer(server)
            }
        }
    }

替换为：
    fun deleteMcpServer(id: String) {
        viewModelScope.launch {
            val server = _mcpServers.value.find { it.id == id }
            if (server != null) {
                app.skillRepository.deleteMcpServer(com.promenar.nexara.data.local.db.entity.McpServerEntity(
                    id = server.id,
                    name = server.name,
                    url = server.url,
                    type = server.type,
                    enabled = server.isEnabled,
                    callIntervalMs = server.callIntervalMs,
                    isDefault = server.isDefault
                ))
            }
        }
    }

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
```

---

### Session 1-2：修复 P0-3 补齐英文 strings.xml

**涉及文件**（1 个）：
- `app/src/main/res/values/strings.xml`

**问题**：SkillsScreen.kt 引用了 13 个 string resource key，这些 key 在 `values-zh-rCN/strings.xml` 中存在，但在英文默认 `values/strings.xml` 中缺失。在非中文 locale 下运行会抛 `Resources$NotFoundException` 崩溃。

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中补齐英文默认 strings.xml。

【文件路径】
app/src/main/res/values/strings.xml

在 SkillsScreen 相关区域（约 502 行附近，</resources> 闭合标签之前）插入以下 13 个 key。
请在现有的 SkillsScreen 区块末尾追加，不要覆盖已有内容。

追加内容（插入到 `</resources>` 之前）：

    <!-- SkillsScreen (continued) -->
    <string name="skills_user_empty">No custom skills yet.\nTap the button below to create your first tool.</string>
    <string name="skills_mcp_empty">No MCP servers yet.\nExtend agent capabilities via Model Context Protocol.</string>
    <string name="skills_edit_custom">Edit Custom Skill</string>
    <string name="search_engine_select">Select Search Engine</string>
    <string name="search_engine_duckduckgo">DuckDuckGo (Local)</string>
    <string name="search_engine_tavily">Tavily AI (Deep Search)</string>
    <string name="search_engine_searxng">SearXNG (Privacy Meta-Search)</string>
    <string name="search_count_label">Result Count</string>
    <string name="search_api_key">API Key</string>
    <string name="search_depth_label">Search Depth</string>
    <string name="search_depth_basic">Basic (Fast)</string>
    <string name="search_depth_advanced">Advanced (Thorough)</string>
    <string name="search_instance_url">Instance URL (SearXNG)</string>

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
```
```

---

### Session 1-3：修复 P0-4 补齐 SkillsScreen.kt 缺失的 import

**涉及文件**（1 个）：
- `app/src/main/java/com/promenar/nexara/ui/settings/SkillsScreen.kt`

**问题**：3 个 Material Icons 引用缺少对应 import（`Icons.Rounded.Settings`、`Icons.Rounded.Sync`、`Icons.Rounded.Check`）。注意：审计报告还列了 `Color`，但该 import 已在第 65 行存在，无需添加。

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中补齐 SkillsScreen.kt 缺失的 import。

【文件路径】
app/src/main/java/com/promenar/nexara/ui/settings/SkillsScreen.kt

在现有 import 区域（约第 38 行 import Icons.Rounded.Calculate 之后，import Icons.Rounded.Build 之前）添加以下 3 行 import：

添加位置：在第 39 行 `import androidx.compose.material.icons.rounded.Build` 之前。

新增 import：
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync

注意：
- 插入到现有 import 块内，保持字母排序（Check 在 Build 之前，Settings 和 Sync 在 TravelExplore 之后）
- 不要添加 Color 的 import（已在第 65 行存在）
- 不要修改任何其他代码

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
```

---

## Phase 2：安全修复 — 3 个会话（预估 1h）

---

### Session 2-1：修复 P1-1 EChartsRenderer XSS/注入风险

**涉及文件**（1 个）：
- `app/src/main/java/com/promenar/nexara/ui/renderer/EChartsRenderer.kt`

**问题**：`optionJson` 直接拼入 HTML `<script>` 标签（`var option = $optionJson`），若 LLM 输出的 JSON 包含 `</script>` 会中断脚本执行。

**修复方案**：使用 Base64 编码传递 optionJson，JS 侧用 `atob()` 解码。

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中修复 EChartsRenderer 的 XSS/注入风险。

【文件路径】
app/src/main/java/com/promenar/nexara/ui/renderer/EChartsRenderer.kt

═══════════════════════════════════════
【变更】将 buildEChartsHtml 函数改为使用 Base64 编码传递 optionJson

查找整个 buildEChartsHtml 函数（行 20-48）：

private fun buildEChartsHtml(optionJson: String): String = """
    <!DOCTYPE html>
    ...（从 <!DOCTYPE 到 .trimIndent()）
""".trimIndent()

替换为：

private fun buildEChartsHtml(optionJson: String): String {
    val encoded = android.util.Base64.encodeToString(
        optionJson.toByteArray(), android.util.Base64.NO_WRAP
    )
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <script src="echarts/echarts.min.js"></script>
        <style>
            body { margin: 0; padding: 0; background: transparent; }
            #chart { width: 100%; height: 350px; }
        </style>
    </head>
    <body>
        <div id="chart"></div>
        <script>
            try {
                var chart = echarts.init(document.getElementById('chart'), 'dark');
                var option = JSON.parse(atob("$encoded"));
                option.backgroundColor = 'transparent';
                chart.setOption(option);
                window.addEventListener('resize', function() { chart.resize(); });
            } catch(e) {
                document.getElementById('chart').innerHTML =
                    '<p style="color:#FFB4AB;font-size:12px;">ECharts Error: ' + e.message + '</p>';
            }
        </script>
    </body>
    </html>
    """.trimIndent()
}

注意：
- 函数签名从 `= """` 表达式体改为 `{ return """` 块体
- `var option = $optionJson;` 改为 `var option = JSON.parse(atob("$encoded"));`
- 其他 HTML 结构完全不变

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
```
```

---

### Session 2-2：修复 P1-2 MermaidRenderer 转义不完整

**涉及文件**（1 个）：
- `app/src/main/java/com/promenar/nexara/ui/renderer/MermaidRenderer.kt`

**问题**：当前仅转义 `\`、`` ` ``、`$`，未处理 `</script>` 和 HTML 特殊字符。Mermaid 代码中可能包含 `<br>`、箭头文本含 `<`/`>` 等。

**修复方案**：改用 Base64 编码传递 Mermaid 代码（与 Session 2-1 一致模式）。

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中修复 MermaidRenderer 的转义不完整问题。

【文件路径】
app/src/main/java/com/promenar/nexara/ui/renderer/MermaidRenderer.kt

═══════════════════════════════════════
【变更】将 buildMermaidHtml 函数改为使用 Base64 编码传递 Mermaid 代码

查找整个 buildMermaidHtml 函数（行 20-65）：

private fun buildMermaidHtml(code: String): String {
    val escaped = code
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")

    return """
    ...（到 .trimIndent()）
}

替换为：

private fun buildMermaidHtml(code: String): String {
    val encoded = android.util.Base64.encodeToString(
        code.toByteArray(), android.util.Base64.NO_WRAP
    )
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <script src="mermaid/mermaid.min.js"></script>
        <style>
            body { margin: 0; padding: 12px; background: transparent; }
            .mermaid { display: flex; justify-content: center; }
            .mermaid svg { max-width: 100%; height: auto; }
            .node rect, .node circle, .node polygon { fill: #2A2A2C !important; stroke: #464554 !important; }
            .nodeLabel, .edgeLabel { color: #E5E1E4 !important; fill: #E5E1E4 !important; }
            .edgePath .path { stroke: #908FA0 !important; }
            .cluster rect { fill: #201F22 !important; stroke: #464554 !important; }
        </style>
    </head>
    <body>
        <div class="mermaid" id="mermaid-diagram"></div>
        <script>
            var diagramCode = atob("$encoded");
            document.getElementById('mermaid-diagram').textContent = diagramCode;
            mermaid.initialize({
                startOnLoad: true,
                theme: 'dark',
                themeVariables: {
                    darkMode: true,
                    background: 'transparent',
                    primaryColor: '#C0C1FF',
                    primaryTextColor: '#E5E1E4',
                    lineColor: '#908FA0',
                    secondaryColor: '#2A2A2C',
                    tertiaryColor: '#201F22'
                }
            });
            mermaid.run();
        </script>
    </body>
    </html>
    """.trimIndent()
}

注意：
- 移除了旧的 `escaped` 变量和 3 个 .replace() 调用
- 改为 Base64 编码
- HTML 中的 `<div class="mermaid">$escaped</div>` 改为通过 JS 动态注入：`document.getElementById('mermaid-diagram').textContent = diagramCode;` + 手动调用 `mermaid.run()`
- 因为 Mermaid 默认在 DOMContentLoaded 时扫描 `.mermaid` 类，动态注入后需手动触发 `mermaid.run()`
- CSS 样式完全不变

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
```
```

---

### Session 2-3：修复 P1-3（修正版）LatexRenderer 缺少注入防护

**涉及文件**（1 个）：
- `app/src/main/java/com/promenar/nexara/ui/renderer/LatexRenderer.kt`

**说明**：审计报告原描述"双重转义破坏 LaTeX 语法"经验证为误报——当前转义逻辑实际正确地将 LaTeX 传递给 KaTeX。但该文件与 P1-1/P1-2 同属一类问题：**未防护 `</script>` 注入**。若 LaTeX 代码中包含 `</script>`（虽然罕见但在数学模式中可能通过 `\text{</script>}` 出现），会导致脚本中断。修复方案与前面一致：改用 Base64。

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中修复 LatexRenderer 缺少 HTML 注入防护的问题。

注意：此问题与 EChartsRenderer/MermaidRenderer 同类，统一采用 Base64 编码方案。

【文件路径】
app/src/main/java/com/promenar/nexara/ui/renderer/LatexRenderer.kt

═══════════════════════════════════════
【变更】将 buildLatexHtml 函数改为使用 Base64 编码传递 LaTeX

查找整个 buildLatexHtml 函数（行 20-63）：

private fun buildLatexHtml(latex: String): String {
    val escaped = latex
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")

    return """
    ...（到 .trimIndent()）
}

替换为：

private fun buildLatexHtml(latex: String): String {
    val encoded = android.util.Base64.encodeToString(
        latex.toByteArray(), android.util.Base64.NO_WRAP
    )
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link rel="stylesheet" href="katex/katex.min.css">
        <script src="katex/katex.min.js"></script>
        <style>
            body {
                margin: 0; padding: 12px;
                background: transparent;
                color: #E5E1E4;
                display: flex; justify-content: center; align-items: center;
                min-height: 20px;
            }
            .katex { font-size: 1.1em; }
            .error { color: #FFB4AB; font-size: 12px; }
        </style>
    </head>
    <body>
        <div id="math"></div>
        <script>
            try {
                var latex = atob("$encoded");
                katex.render(latex, document.getElementById("math"), {
                    throwOnError: false,
                    displayMode: true
                });
            } catch(e) {
                document.getElementById("math").innerHTML =
                    '<span class="error">' + e.message + '</span>';
            }
        </script>
    </body>
    </html>
    """.trimIndent()
}

注意：
- 移除了旧的 `escaped` 变量和 4 个 .replace() 调用
- 改为 Base64 编码，JS 侧用 `atob()` 解码
- CSS 和 KaTeX 调用参数完全不变

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
```
```

---

## Phase 3：功能修正 — 5 个会话（预估 2h）

---

### Session 3-1：修复 P1-4 移除调试遗留 + P2-1 修复箭头图标语义

**涉及文件**（1 个）：
- `app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt`

**问题**：
1. 第 391 行 `if (true)` 是调试遗留代码，包裹了一个无点击行为的 AutoFixHigh 按钮
2. 第 500 行 ThinkingBlock 的展开/折叠箭头图标语义反转：展开态显示向下箭头（应为向上）

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目的 ChatScreen.kt 中修复两个问题。

【文件路径】
app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt

═══════════════════════════════════════
【变更 1】移除 P1-4 调试遗留 if (true) — 约第 391-404 行

查找：
        if (true) {
            NexaraGlassCard(
                onClick = { },
                shape = RoundedCornerShape(50),
                modifier = Modifier
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, null, tint = NexaraColors.Primary, modifier = Modifier.size(14.dp))
                }
            }
        }

替换为：（直接移除整个 if 块及其内容，不留空行）

═══════════════════════════════════════
【变更 2】修复 P2-1 ThinkingBlock 箭头图标语义 — 约第 499-504 行

查找：
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.ArrowDownward,
                null,
                tint = NexaraColors.OnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )

替换为：
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                null,
                tint = NexaraColors.OnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )

注意：
- 展开态 → KeyboardArrowUp（上箭头，表示"点击收起"）
- 折叠态 → KeyboardArrowDown（下箭头，表示"点击展开"）
- 两个箭头均使用 Rounded（描边）风格，保持视觉一致

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
检查 ChatInputTopBar 中 AutoFixHigh 按钮是否已移除。
```
```

---

### Session 3-2：修复 P1-5 ChatViewModel 工具注册硬编码

**涉及文件**（1 个）：
- `app/src/main/java/com/promenar/nexara/ui/chat/ChatViewModel.kt`

**问题**：`buildToolList()` 硬编码 `"current_time"` 和 `"calculator"` 为始终启用，不读取 SharedPreferences 中用户配置的工具启用状态。导致在 SkillsScreen 中禁用工具后，ChatViewModel 仍然向 LLM 发送这些工具定义。

**修复方案**：从 SharedPreferences 读取 `enabled_skills`，仅发送用户已启用的工具。

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中修复 ChatViewModel 的工具注册硬编码问题。

【文件路径】
app/src/main/java/com/promenar/nexara/ui/chat/ChatViewModel.kt

═══════════════════════════════════════
【变更】修改 buildToolList 函数，从 SharedPreferences 读取已启用的工具列表

查找（约第 569-583 行）：

    private fun buildToolList(session: Session): List<com.promenar.nexara.data.remote.protocol.ProtocolTool> {
        val options = session.options ?: return emptyList()
        if (!options.toolsEnabled) return emptyList()
        
        val allowedIds = mutableListOf<String>()
        // By default, add non-optional skills if any
        allowedIds.add("current_time")
        allowedIds.add("calculator")
        
        if (options.webSearch == true) {
            allowedIds.add("web_search")
        }
        
        return skillRegistry?.getAllTools(allowedIds) ?: emptyList()
    }

替换为：

    private fun buildToolList(session: Session): List<com.promenar.nexara.data.remote.protocol.ProtocolTool> {
        val options = session.options ?: return emptyList()
        if (!options.toolsEnabled) return emptyList()
        
        val prefs = application.getSharedPreferences("nexara_settings", 0)
        val enabledSkills = prefs.getStringSet("enabled_skills", null)
        
        if (enabledSkills.isNullOrEmpty()) return emptyList()
        
        val allowedIds = enabledSkills.toMutableList()
        
        // Respect web search toggle – if web search is enabled but the skill isn't, add it
        if (options.webSearch == true && "web_search" !in allowedIds) {
            allowedIds.add("web_search")
        }
        // If web search is disabled at session level, remove search skills
        if (options.webSearch != true) {
            allowedIds.removeAll { it == "web_search" || it.startsWith("search_") }
        }
        
        return skillRegistry?.getAllTools(allowedIds) ?: emptyList()
    }

注意：
- 使用 `application.getSharedPreferences("nexara_settings", 0)` — 与 SettingsViewModel 使用的 Prefs 保持一致
- `enabled_skills` 的默认值为 `null`，为空时返回 emptyList()（保守策略：不发送任何工具）
- 保留了对 `options.webSearch` 的会话级覆盖逻辑

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
```
```

---

### Session 3-3：修复 P1-6 setHaptic 参数被忽略 + P1-7 搜索配置 key 不一致 + P2-3 loopLimit 持久化（ViewModel 部分）

**涉及文件**（1 个）：
- `app/src/main/java/com/promenar/nexara/ui/settings/SettingsViewModel.kt`

**问题**：
1. `setHaptic(enabled: Boolean)` 无视参数，始终设为 `true`（第 393-395 行）
2. `loadSearchSettings()` 中 `resultCount` 使用 key `"search_result_count"`，但 `SearchConfigViewModel` 使用 key `"result_count"`，导致 SkillsScreen 搜索配置与 SearchConfigScreen 不同步
3. 需要新增 `loopLimit` 的读写支持（为 Session 3-4 做准备）

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中修复 SettingsViewModel 的三个功能问题。

【文件路径】
app/src/main/java/com/promenar/nexara/ui/settings/SettingsViewModel.kt

═══════════════════════════════════════
【变更 1】修复 P1-6 setHaptic 参数被忽略 — 第 393-395 行

查找：
    fun setHaptic(enabled: Boolean) {
        _hapticEnabled.value = true
    }

替换为：
    fun setHaptic(enabled: Boolean) {
        _hapticEnabled.value = enabled
        prefs.edit().putBoolean("haptic_enabled", enabled).apply()
    }

═══════════════════════════════════════
【变更 2】修复 P1-7 搜索配置 key 不一致 — 第 222 行

查找：
            resultCount = searchPrefs.getInt("search_result_count", 5)

替换为：
            resultCount = searchPrefs.getInt("result_count", 5)

═══════════════════════════════════════
【变更 3】同时修改 updateSearchSettings 持久化 key — 第 233 行

查找：
            .putInt("search_result_count", settings.resultCount)

替换为：
            .putInt("result_count", settings.resultCount)

═══════════════════════════════════════
【变更 4】新增 loopLimit 状态与读写方法（为 SkillsScreen 持久化做准备）

在 `searchPrefs` 声明之后（约第 151 行之后），`init` 块之前，插入：

    private val _loopLimit = MutableStateFlow(prefs.getInt("loop_limit", 15))
    val loopLimit: StateFlow<Int> = _loopLimit.asStateFlow()

    fun updateLoopLimit(limit: Int) {
        _loopLimit.value = limit
        prefs.edit().putInt("loop_limit", limit).apply()
    }

═══════════════════════════════════════
【变更 5】在 loadPreferences() 中恢复 loopLimit — 约第 206 行 `_hapticEnabled.value = true` 后面

将：
        _hapticEnabled.value = true // Force enable

替换为：
        _hapticEnabled.value = prefs.getBoolean("haptic_enabled", true)

        _loopLimit.value = prefs.getInt("loop_limit", 15)

注意：这里同时修复了 P1-6 的初始加载问题（原来始终写 true）。

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
确认 SearchConfigViewModel 和 SettingsViewModel 的 resultCount 使用相同 SharedPrefs key `result_count`。
```
```

---

### Session 3-4：修复 P2-2 WorkspaceSheet 空 clickable + P2-3 SkillsScreen loopLimit 持久化（UI 部分）

**涉及文件**（2 个）：
- `app/src/main/java/com/promenar/nexara/ui/chat/WorkspaceSheet.kt`
- `app/src/main/java/com/promenar/nexara/ui/settings/SkillsScreen.kt`

**前置依赖**：Session 3-3 必须先执行（SkillsScreen 需要 SettingsViewModel 的 loopLimit 状态）

**问题**：
1. WorkspaceSheet 第 417 行 `IconButton(onClick = {}, ...)` 和 第 439 行 `.clickable { }` 无行为，误导用户
2. SkillsScreen 中 `loopLimit` 使用 `remember { mutableStateOf(15) }` 不持久化，离开页面即丢失

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中修复 WorkspaceSheet 空 clickable 和 SkillsScreen loopLimit 持久化（UI 部分）。

【前置依赖】Session 3-3 必须先完成（SettingsViewModel 需已有 loopLimit 状态）。

═══════════════════════════════════════
【变更 1】修复 WorkspaceSheet.kt — 移除空的 clickable（P2-2）
【文件路径】
app/src/main/java/com/promenar/nexara/ui/chat/WorkspaceSheet.kt

变更 1a：移除 Artifacts 卡片空 clickable — 第 437-439 行

查找：
            NexaraGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { },
                shape = RoundedCornerShape(12.dp)

替换为：
            NexaraGlassCard(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)

变更 1b：移除 pending 任务的空 IconButton — 第 416-418 行

查找：
            } else if (task.status == "pending") {
                IconButton(onClick = {}, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Rounded.MoreVert, null, tint = NexaraColors.OutlineVariant, modifier = Modifier.size(16.dp))
                }
            }

替换为（直接删除 `else if` 分支，只保留前面两个分支）：

注意：将：
        if (task.time.isNotEmpty()) {
            Text(...)
        } else if (task.status == "in_progress") {
            Text(...)
        } else if (task.status == "pending") {
            IconButton(onClick = {}, ...)
        }
改为：
        if (task.time.isNotEmpty()) {
            Text(task.time, style = NexaraTypography.bodyMedium.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace), color = NexaraColors.OutlineVariant)
        } else if (task.status == "in_progress") {
            Text(stringResource(R.string.workspace_task_running), style = NexaraTypography.bodyMedium.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace), color = NexaraColors.Primary)
        }

═══════════════════════════════════════
【变更 2】修复 SkillsScreen.kt — loopLimit 持久化（P2-3）
【文件路径】
app/src/main/java/com/promenar/nexara/ui/settings/SkillsScreen.kt

变更 2a：在 `val mcpServers` 初始化之后（约第 94 行），添加从 ViewModel 读取 loopLimit

在：
    val presetSkills by viewModel.skills.collectAsState()
    val userSkills by viewModel.userSkills.collectAsState()
    val mcpServers by viewModel.mcpServers.collectAsState()

之后添加：
    val loopLimit by viewModel.loopLimit.collectAsState()

变更 2b：移除旧的组件级状态声明 — 第 97 行

查找：
    var loopLimit by remember { mutableStateOf(15) }

替换为（直接删除该行）：

变更 2c：修改增加/减少按钮的回调 — 第 151、167 行

将：
    .clickable { loopLimit = (loopLimit - 1).coerceAtLeast(1) },

替换为：
    .clickable { viewModel.updateLoopLimit((loopLimit - 1).coerceAtLeast(1)) },

将：
    .clickable { loopLimit = (loopLimit + 1).coerceAtMost(100) },

替换为：
    .clickable { viewModel.updateLoopLimit((loopLimit + 1).coerceAtMost(100)) },

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
确认 SkillsScreen 的 loopLimit 值在离开页面后重新进入时保持不变。
```
```

---

### Session 3-5：修复 P2-4 SearchConfigScreen 硬编码中文 + P3-6 数据库迁移

**涉及文件**（4 个）：
- `app/src/main/java/com/promenar/nexara/ui/settings/SearchConfigScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/java/com/promenar/nexara/data/local/db/NexaraDatabase.kt`

**问题**：
1. SearchConfigScreen 中 `"搜索引擎提供商"`、`"SearXNG 节点地址"`、`"Tavily API Key"` 和引擎选项标签硬编码
2. 数据库使用 `fallbackToDestructiveMigration()`，无版本迁移逻辑，升级时静默清除用户数据

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中修复 SearchConfigScreen 硬编码中文和数据库迁移问题。

═══════════════════════════════════════
【变更 1】添加 3 个新 string resource
【文件路径 1】app/src/main/res/values/strings.xml
【文件路径 2】app/src/main/res/values-zh-rCN/strings.xml

在 </resources> 之前、SearchConfigScreen 区域追加：

英文版 (values/strings.xml)：
    <string name="search_config_engine_label">Search Engine Provider</string>
    <string name="search_config_searxng_url_label">SearXNG Instance URL</string>
    <string name="search_config_tavily_key_label">Tavily API Key</string>

同时更新英文版中已有的引擎选项标签为完整形式（如果尚未）：
    <string name="search_config_engine_duckduckgo">DuckDuckGo (Local / Free)</string>
    <string name="search_config_engine_searxng">SearXNG (Meta-Search / Public Node)</string>
    <string name="search_config_engine_tavily">Tavily (AI Optimized / API Key)</string>

中文版 (values-zh-rCN/strings.xml)：
    <string name="search_config_engine_label">搜索引擎提供商</string>
    <string name="search_config_searxng_url_label">SearXNG 节点地址</string>
    <string name="search_config_tavily_key_label">Tavily API Key</string>
    <string name="search_config_engine_duckduckgo">DuckDuckGo (本地解析/免费)</string>
    <string name="search_config_engine_searxng">SearXNG (元搜索/公共节点)</string>
    <string name="search_config_engine_tavily">Tavily (AI 优化/API Key)</string>

═══════════════════════════════════════
【变更 2】SearchConfigScreen.kt 替换硬编码字符串
【文件路径】app/src/main/java/com/promenar/nexara/ui/settings/SearchConfigScreen.kt

变更 2a：第 110 行 — 引擎提供商标题

查找：
            text = "搜索引擎提供商",

替换为：
            text = stringResource(R.string.search_config_engine_label),

变更 2b：第 150 行 — SearXNG 地址标签

查找：
            Text(text = "SearXNG 节点地址", style = NexaraTypography.labelMedium, color = NexaraColors.OnSurface)

替换为：
            Text(text = stringResource(R.string.search_config_searxng_url_label), style = NexaraTypography.labelMedium, color = NexaraColors.OnSurface)

变更 2c：第 171 行 — Tavily API Key 标签

查找：
            Text(text = "Tavily API Key", style = NexaraTypography.labelMedium, color = NexaraColors.OnSurface)

替换为：
            Text(text = stringResource(R.string.search_config_tavily_key_label), style = NexaraTypography.labelMedium, color = NexaraColors.OnSurface)

变更 2d：第 117-121 行 — 引擎选项列表

查找：
            val engines = listOf(
                "duckduckgo" to "DuckDuckGo (本地解析/免费)",
                "searxng" to "SearXNG (元搜索/公共节点)",
                "tavily" to "Tavily (AI 优化/API Key)"
            )

替换为：
            val engines = listOf(
                "duckduckgo" to stringResource(R.string.search_config_engine_duckduckgo),
                "searxng" to stringResource(R.string.search_config_engine_searxng),
                "tavily" to stringResource(R.string.search_config_engine_tavily)
            )

═══════════════════════════════════════
【变更 3】修复 P3-6 数据库迁移
【文件路径】app/src/main/java/com/promenar/nexara/data/local/db/NexaraDatabase.kt

在文件末尾、最后一个 `}` 之前添加迁移对象：

    companion object {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_skills` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `parametersSchema` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `type` TEXT NOT NULL DEFAULT 'user',
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mcp_servers` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `type` TEXT NOT NULL DEFAULT 'http',
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `callIntervalMs` INTEGER NOT NULL DEFAULT 1000,
                        `isDefault` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }
    }

【文件路径】app/src/main/java/com/promenar/nexara/NexaraApplication.kt

在 database 的 by lazy 块中（约第 45-48 行），将：

    val database: NexaraDatabase by lazy {
        Room.databaseBuilder(this, NexaraDatabase::class.java, "nexara.db")
            .fallbackToDestructiveMigration()
            .build()
    }

替换为：

    val database: NexaraDatabase by lazy {
        Room.databaseBuilder(this, NexaraDatabase::class.java, "nexara.db")
            .addMigrations(NexaraDatabase.MIGRATION_4_5)
            .build()
    }

注意：
- 如果用户设备上的数据库版本已经是 5，此迁移不会执行（Room 只在升级时调用 Migration）
- 对于尚未定义的未来版本升级路径（如 5→6），Room 会抛出异常而非静默清除数据，这是预期行为

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
切换到英文 locale 验证 SearchConfigScreen 显示英文标签。
```
```

---

## Phase 4：架构改进 — 2 个会话（持续迭代）

---

### Session 4-1：修复 P3-5 CalculatorSkill 增加幂运算和取模

**涉及文件**（1 个）：
- `app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/CalculatorSkill.kt`

**问题**：CalculatorSkill 当前 tokenizer 只识别 `+-*/()`，缺少 `^`（幂）、`%`（取模）。解析器也缺少对应方法。

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中增强 CalculatorSkill，支持幂运算和取模。

【文件路径】
app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/CalculatorSkill.kt

═══════════════════════════════════════
【变更 1】tokenize 函数增加 `^` 和 `%` 识别 — 第 51-56 行

查找：
        when {
            c.isWhitespace() -> i++
            c in "+-*/()" -> {
                tokens.add(Token(c.toString(), TokenType.OP))
                i++
            }

替换为：
        when {
            c.isWhitespace() -> i++
            c in "+-*/^%()" -> {
                tokens.add(Token(c.toString(), TokenType.OP))
                i++
            }

═══════════════════════════════════════
【变更 2】ExprParser 增加 parsePower 方法，调整运算优先级

在 parseTerm() 方法之后（约第 95 行），parseFactor() 之前，插入：

        private fun parsePower(): Double {
            var result = parseUnary()
            while (pos < tokens.size && tokens[pos].value == "^") {
                pos++
                val right = parseUnary()
                result = Math.pow(result, right)
            }
            return result
        }

        private fun parseUnary(): Double {
            if (pos < tokens.size && tokens[pos].value == "-") {
                pos++
                return -parseUnary()
            }
            if (pos < tokens.size && tokens[pos].value == "+") {
                pos++
                return parseUnary()
            }
            if (pos < tokens.size && tokens[pos].value == "(") {
                pos++
                val result = parseExpression()
                if (pos < tokens.size && tokens[pos].value == ")") pos++
                return result
            }
            if (pos < tokens.size && tokens[pos].type == TokenType.NUM) {
                return tokens[pos++].value.toDouble()
            }
            throw IllegalArgumentException("Unexpected token at position $pos")
        }

═══════════════════════════════════════
【变更 3】修改 parseExpression 调用链以使用新的 parsePower

修改 parseTerm() 方法（约第 86-95 行），将其中的 `parseFactor()` 调用替换为 `parsePower()`：

查找 parseTerm 方法（约第 86-95 行）：

        private fun parseTerm(): Double {
            var result = parseFactor()
            while (pos < tokens.size && tokens[pos].value in listOf("*", "/")) {
                val op = tokens[pos].value
                pos++
                val right = parseFactor()
                result = if (op == "*") result * right else result / right
            }
            return result
        }

替换为：

        private fun parseTerm(): Double {
            var result = parseFactor()
            while (pos < tokens.size && tokens[pos].value in listOf("*", "/", "%")) {
                val op = tokens[pos].value
                pos++
                val right = parseFactor()
                result = when (op) {
                    "*" -> result * right
                    "/" -> result / right
                    "%" -> result % right
                    else -> result
                }
            }
            return result
        }

【变更 4】修改 parseExpression() 使其通过 parseTerm → parsePower → parseUnary 的调用链

修改 parseExpression() 方法（约第 75-84 行），保持 `parseTerm()` 调用不变（parseTerm 内部使用了 parsePower 链）。

注意：原来的 parseFactor() 方法中包含一元运算符和括号解析。新的 parseUnary() 替代了它的职责。请删除旧的 parseFactor() 方法（避免重复），仅保留 parsePower() 和 parseUnary()。

具体来说：删除约第 97-116 行的 parseFactor() 方法（整个方法体）。

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
```
```

---

### Session 4-2：P3-1 Markdown 增量解析 + P3-2 WebView 复用池（高级优化）

**涉及文件**（2 个）：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebView.kt`

**问题**：
1. 流式模式下 `splitRichSegments()` 每次对全文做三次正则匹配，长文本性能下降
2. 每个 LaTeX/Mermaid/ECharts 块创建独立 WebView，内存开销大

**备注**：此会话涉及较复杂的性能优化，建议由有经验的开发者执行。以下提供概要方案，具体实现可根据项目情况调整。

---

**📋 复制以下提示词到新会话：**

```
请在 native-ui 项目中优化 Markdown 解析性能和 WebView 实例管理。

═══════════════════════════════════════
【变更 1】P3-1 MarkdownText.kt — 增量解析优化
【文件路径】app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt

目标：对流式场景，仅在文本增长超过阈值时重新解析分段，避免每次字符增量都全文正则。

在 splitRichSegments 函数之前添加缓存机制：

// 添加在 ContentSegment sealed class 之后、splitRichSegments 之前
private var cachedText: String = ""
private var cachedSegments: List<ContentSegment> = emptyList()
private const val RE_PARSE_THRESHOLD = 100 // 仅当文本增长超过此字符数时重新解析

具体修改 splitRichSegments 函数开头：

private fun splitRichSegments(text: String): List<ContentSegment> {
    // 增量优化：如果文本未显著增长，直接返回缓存
    if (text.startsWith(cachedText) && text.length - cachedText.length < RE_PARSE_THRESHOLD) {
        // 只在末尾追加一个 Markdown segment（流式模式最末段通常不是 special block）
        val newPart = text.substring(cachedText.length)
        return cachedSegments + ContentSegment.Markdown(newPart)
    }
    cachedText = text

    // ... 原有正则解析逻辑不变（从 val blockPattern = Regex(...) 开始）
    val blockPattern = Regex(
        // ... 保持不变
    )
    // ...
    cachedSegments = result
    return result
}

注意：
- 需要在 remember 块的 lambda 内部做缓存管理
- 如果 remember key 变了（如从流式切换到完成态），缓存会自动重置
- 阈值 100 字符是一个经验值，可根据实际性能调整

═══════════════════════════════════════
【变更 2】P3-2 RichContentWebView.kt — 同类内容合并
【文件路径】app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebView.kt

目标：将同一消息中多个同类特殊块（如多个 LaTeX 公式）合并到一个 WebView 中渲染，减少 WebView 实例数。

方案：在各 Renderer（LaTexBlock/EChartsBlock/MermaidBlock）的调用方（MarkdownText.kt）中按类型分组，同类型连续块合并。

在 MarkdownText.kt 的 segments 渲染循环中（约第 103 行）：

    val segments = remember(processed) { splitRichSegments(processed) }

    Column(modifier = modifier.fillMaxWidth()) {
        var pendingLatex = mutableListOf<String>()
        var pendingMermaid = mutableListOf<String>()
        var pendingECharts = mutableListOf<String>()

        fun flushPending() {
            if (pendingLatex.isNotEmpty()) {
                LatexBlock(latex = pendingLatex.joinToString("\n\n"))
                pendingLatex.clear()
            }
            if (pendingMermaid.isNotEmpty()) {
                MermaidBlock(code = pendingMermaid.joinToString("\n"))
                pendingMermaid.clear()
            }
            if (pendingECharts.isNotEmpty()) {
                // ECharts 不能简单合并，每个图表独立渲染
                pendingECharts.forEach { EChartsBlock(optionJson = it) }
                pendingECharts.clear()
            }
        }

        for (segment in segments) {
            when (segment) {
                is ContentSegment.Markdown -> {
                    flushPending()
                    // ... 原有 Markdown 渲染不变
                }
                is ContentSegment.Latex -> pendingLatex.add(segment.content)
                is ContentSegment.Mermaid -> {
                    pendingLatex.clear(); pendingECharts.clear() // 不同类型间 flush
                    pendingMermaid.add(segment.content)
                }
                is ContentSegment.ECharts -> {
                    pendingLatex.clear(); pendingMermaid.clear()
                    pendingECharts.add(segment.content)
                }
            }
        }
        flushPending()

        // ... streaming cursor 不变
    }

═══════════════════════════════════════
【验证】
执行 ./gradlew :app:compileDebugKotlin 确认编译通过。
运行应用发送一条含多个公式的消息，验证渲染正确。
```
```

---

## 会话总览与执行清单

| 会话 | 阶段 | 问题 | 文件数 | 预估 | 可独立执行 |
|------|------|------|--------|------|-----------|
| 1-1 | P1 编译 | P0-1 + P0-2 | 1 | 15min | ✅ |
| 1-2 | P1 编译 | P0-3 | 1 | 10min | ✅ |
| 1-3 | P1 编译 | P0-4 | 1 | 5min | ✅ |
| 2-1 | P2 安全 | P1-1 | 1 | 10min | ✅ |
| 2-2 | P2 安全 | P1-2 | 1 | 10min | ✅ |
| 2-3 | P2 安全 | P1-3(修正) | 1 | 10min | ✅ |
| 3-1 | P3 功能 | P1-4 + P2-1 | 1 | 10min | ✅ |
| 3-2 | P3 功能 | P1-5 | 1 | 10min | ✅ |
| 3-3 | P3 功能 | P1-6 + P1-7 + P2-3(VM) | 1 | 15min | ✅ |
| 3-4 | P3 功能 | P2-2 + P2-3(UI) | 2 | 15min | ⚠️ 依赖 3-3 |
| 3-5 | P3 功能 | P2-4 + P3-6 | 4 | 20min | ✅ |
| 4-1 | P4 架构 | P3-5 | 1 | 15min | ✅ |
| 4-2 | P4 架构 | P3-1 + P3-2 | 2 | 30min | ✅ |

---

## DIA 提醒

每个 Phase 完成后，需更新以下文档：
- `docs/CHANGELOG.md` — 记录修复内容
- `docs/ARCHITECTURE.md` — 如有模块结构变更（Phase 4）
- `.agent/handover.md` — 更新 Done / Next Steps
