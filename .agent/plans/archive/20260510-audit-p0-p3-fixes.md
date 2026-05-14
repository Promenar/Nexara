# 审计报告：最近两次提交 (0dfc329 + 5eaf905)

> 审计时间：2026-05-10
> 覆盖范围：Markdown 渲染引擎重构、ChatScreen/ChatViewModel 变更、WorkspaceSheet 新增、SkillsScreen 重构、SettingsViewModel/SearchConfigScreen 变更、CalculatorSkill/CurrentTimeSkill 新增、数据模型变更

---

## 一、P0 — 编译阻断性问题（必须修复）

### P0-1: `ProviderListItem` 数据类定义不完整

**文件**: `ui/settings/SettingsViewModel.kt:66`

**现状**: `ProviderListItem` 只有一个 `val model: String` 字段，但 `loadProviders()` 使用了 `id`, `name`, `typeName`, `baseUrl` 等命名参数（:251-:257, :266-:272），`deleteProvider()` 和 `persistProviders()` 也引用了这些字段（:399, :412-:421）。

**修复方案**:
```kotlin
data class ProviderListItem(
    val id: String = "",
    val name: String = "",
    val typeName: String = "",
    val baseUrl: String = "",
    val model: String
)
```

---

### P0-2: `deleteMcpServer()` 类型不匹配

**文件**: `ui/settings/SettingsViewModel.kt:590`

**现状**: 传入 `McpServerUiModel` 给 `skillRepository.deleteMcpServer()`，但后者期望 `McpServerEntity`。

**修复方案**: 通过 ID 查询数据库获取 Entity 后删除，或让 Repository 接受 ID 参数：
```kotlin
fun deleteMcpServer(id: String) {
    viewModelScope.launch {
        app.skillRepository.deleteMcpServerById(id)
    }
}
```

---

### P0-3: `values/strings.xml` 缺失大量 string resource

**文件**: `app/src/main/res/values/strings.xml`

**现状**: 以下 key 被 `SkillsScreen.kt` 引用但在英文默认版中不存在（中文版 `values-zh-rCN/strings.xml` 中已有）：

| Key | 引用位置 |
|-----|---------|
| `skills_user_empty` | SkillsScreen.kt:250 |
| `skills_mcp_empty` | SkillsScreen.kt:307 |
| `skills_edit_custom` | SkillsScreen.kt:434 |
| `search_engine_select` | SkillsScreen.kt:777 |
| `search_engine_duckduckgo` | SkillsScreen.kt:779 |
| `search_engine_tavily` | SkillsScreen.kt:782 |
| `search_engine_searxng` | SkillsScreen.kt:785 |
| `search_count_label` | SkillsScreen.kt:794 |
| `search_api_key` | SkillsScreen.kt:802 |
| `search_depth_label` | SkillsScreen.kt:822 |
| `search_depth_basic` | SkillsScreen.kt:824 |
| `search_depth_advanced` | SkillsScreen.kt:827 |
| `search_instance_url` | SkillsScreen.kt:835 |

**修复方案**: 在 `values/strings.xml` 中补齐以上所有英文翻译。

---

### P0-4: `SkillsScreen.kt` 缺失 import

**文件**: `ui/settings/SkillsScreen.kt`

**现状**: 以下引用未导入：

| 引用 | 行号 | 应导入 |
|------|------|--------|
| `Icons.Rounded.Settings` | :566 | `import androidx.compose.material.icons.rounded.Settings` |
| `Icons.Rounded.Sync` | :686 | `import androidx.compose.material.icons.rounded.Sync` |
| `Color` | :864 | `import androidx.compose.ui.graphics.Color` |
| `Icons.Rounded.Check` | :876 | `import androidx.compose.material.icons.rounded.Check` |

**修复方案**: 添加对应 import 语句。

---

## 二、P1 — 设计/功能问题

### P1-1: EChartsRenderer — XSS/注入风险

**文件**: `ui/renderer/EChartsRenderer.kt:37`

**现状**: `optionJson` 直接拼入 HTML (`var option = $optionJson`)，如果 LLM 输出含 `</script>` 会中断脚本。

**修复方案**: 使用 Base64 编码传递：
```kotlin
private fun buildEChartsHtml(optionJson: String): String {
    val encoded = Base64.encodeToString(optionJson.toByteArray(), Base64.NO_WRAP)
    return """
    ...
    <script>
        try {
            var chart = echarts.init(document.getElementById('chart'), 'dark');
            var option = JSON.parse(atob("$encoded"));
            ...
        } catch(e) { ... }
    </script>
    """
}
```

---

### P1-2: MermaidRenderer — 转义不完整

**文件**: `ui/renderer/MermaidRenderer.kt:22-25`

**现状**: 只转义了 `\`, `` ` ``, `$`，未处理 `</script>` 或 `<`/`>`。

**修复方案**: 使用 Base64 同 P1-1，或增加 HTML 特殊字符转义：
```kotlin
val escaped = code
    .replace("\\", "\\\\")
    .replace("`", "\\`")
    .replace("$", "\\$")
    .replace("<", "\\x3c")
    .replace(">", "\\x3e")
```

---

### P1-3: LatexRenderer — 双重转义破坏 LaTeX 语法

**文件**: `ui/renderer/LatexRenderer.kt:22-25`

**现状**: `\frac{a}{b}` 被转义为 `\\frac{a}{b}`，破坏 KaTeX 语法。

**修复方案**: 使用 Base64 传递（同 P1-1），或在 JS 侧 decodeURIComponent：
```kotlin
private fun buildLatexHtml(latex: String): String {
    val encoded = android.util.Base64.encodeToString(
        latex.toByteArray(), android.util.Base64.NO_WRAP
    )
    return """
    ...
    <script>
        try {
            var latex = atob("$encoded");
            katex.render(latex, document.getElementById("math"), {
                throwOnError: false, displayMode: true
            });
        } catch(e) { ... }
    </script>
    """
}
```

---

### P1-4: `ChatScreen.kt:391` — 硬编码 `if (true)`

**文件**: `ui/chat/ChatScreen.kt:391`

**现状**: `ChatInputTopBar` 中 `if (true)` 块是调试遗留。

**修复方案**: 移除 `if (true)` 条件包裹，直接渲染按钮；或将条件改为有意义的控制逻辑（如检查某个 feature flag）。

---

### P1-5: `ChatViewModel.kt:569-583` — 工具注册硬编码

**文件**: `ui/chat/ChatViewModel.kt:569-583`

**现状**: `buildToolList()` 硬编码 `"current_time"` 和 `"calculator"` 为始终启用。

**修复方案**: 从 SettingsViewModel 的启用状态或 SharedPreferences 读取已启用的工具列表：
```kotlin
private fun buildToolList(session: Session): List<ProtocolTool> {
    val options = session.options ?: return emptyList()
    if (!options.toolsEnabled) return emptyList()
    val enabledSkills = prefs.getStringSet("enabled_skills", null) ?: return emptyList()
    return skillRegistry?.getAllTools(enabledSkills.toList()) ?: emptyList()
}
```

---

### P1-6: `SettingsViewModel.kt:206` — `hapticEnabled` 硬编码

**文件**: `ui/settings/SettingsViewModel.kt:206`

**现状**: `setHaptic()` 无视参数，始终设为 `true`。

**修复方案**:
```kotlin
fun setHaptic(enabled: Boolean) {
    _hapticEnabled.value = enabled
    prefs.edit().putBoolean("haptic_enabled", enabled).apply()
}
```

---

### P1-7: 搜索配置双写

**文件**: `ui/settings/SettingsViewModel.kt` + `ui/settings/SearchConfigViewModel.kt`

**现状**: 两个 ViewModel 管理搜索设置，使用不同的 SharedPrefs key：
- `SearchConfigViewModel` 使用 `nexara_search` SharedPreferences，key 如 `result_count`
- `SettingsViewModel` 使用 `nexara_search` SharedPreferences，key 如 `search_result_count`

导致 SearchConfigScreen 和 SkillsScreen 的搜索配置互不同步。

**修复方案**: 统一 key 命名。选定 `SearchConfigViewModel` 的 key 为标准，修改 `SettingsViewModel.loadSearchSettings()` 和 `updateSearchSettings()` 使用相同的 key。或者抽取 `SearchSettingsRepository` 统一管理。

---

## 三、P2 — UI/UX 问题

### P2-1: ThinkingBlock 箭头图标语义反转

**文件**: `ui/chat/ChatScreen.kt:500`

**现状**: 展开态用 `KeyboardArrowDown`，折叠态用 `ArrowDownward`。

**修复方案**:
```kotlin
Icon(
    if (expanded) Icons.Rounded.KeyboardArrowUp
    else Icons.Rounded.KeyboardArrowDown,
    ...
)
```

---

### P2-2: WorkspaceSheet — 空实现 clickable

**文件**: `ui/chat/WorkspaceSheet.kt:443`

**现状**: Artifacts 的 `clickable { }` 和 `:417` 的 `IconButton(onClick = {})` 无行为。

**修复方案**: 移除空 `clickable`，或添加文件预览/下载功能。短期建议移除避免误导用户。

---

### P2-3: `SkillsScreen` — `loopLimit` 状态未持久化

**文件**: `ui/settings/SkillsScreen.kt:97`

**现状**: `loopLimit` 是组件级 `remember`，不持久化。

**修复方案**: 连接到 SettingsViewModel，通过 SharedPreferences 持久化：
```kotlin
// SettingsViewModel
private val _loopLimit = MutableStateFlow(prefs.getInt("loop_limit", 15))
val loopLimit: StateFlow<Int> = _loopLimit.asStateFlow()

fun updateLoopLimit(limit: Int) {
    _loopLimit.value = limit
    prefs.edit().putInt("loop_limit", limit).apply()
}
```

---

### P2-4: `SearchConfigScreen.kt` — 硬编码中文

**文件**: `ui/settings/SearchConfigScreen.kt:110, :150, :171`

**现状**: `"搜索引擎提供商"`、`"SearXNG 节点地址"`、`"Tavily API Key"` 直接硬编码中文。

**修复方案**: 提取到 `strings.xml`，使用 `stringResource()`。

---

## 四、P3 — 代码质量/架构问题

### P3-1: Markdown 分段解析性能

**文件**: `ui/common/MarkdownText.kt:42-88`

**现状**: `splitRichSegments()` 每次用正则重新解析全文。

**修复方案**: 对流式场景做增量匹配（只检查新增部分是否完成了一个 block），或缓存分段结果只在文本增长超过阈值时重新解析。

---

### P3-2: WebView 实例管理

**文件**: `ui/renderer/RichContentWebView.kt`

**现状**: 每个 LaTeX/Mermaid/ECharts 块创建独立 WebView。

**修复方案**: 短期可合并同类内容块到一个 WebView（如所有 LaTeX 合并为一个 HTML）。长期考虑 WebView 复用池。

---

### P3-3: 手动 DI

**文件**: `NexaraApplication.kt`

**现状**: 全部 `by lazy` 手动管理依赖。

**修复方案**: 引入 Koin 或 Hilt。当前阶段可暂缓，但建议在 ViewModel factory 统一抽取为辅助函数减少模板代码。

---

### P3-4: `ChatViewModel` 职责过重

**文件**: `ui/chat/ChatViewModel.kt` (651 行)

**现状**: 承担消息管理、上下文构建、工具执行、流式处理、后处理全部职责。

**修复方案**: 按 AGENTS.md 计划拆分：
- `StreamingViewModel` — 流式生成与 token 管理
- `ToolViewModel` — 工具调用与执行
- 当前 `ChatViewModel` 仅管理会话生命周期与 UI 状态

---

### P3-5: CalculatorSkill 功能不足

**文件**: `ui/chat/manager/skills/CalculatorSkill.kt`

**现状**: 不支持 `^`（幂）、`%`（取模）、数学函数。

**修复方案**: 增加 `parsePower()` 和 `parseUnary()` 方法：
```kotlin
private fun parsePower(): Double {
    var result = parseUnary()
    while (pos < tokens.size && tokens[pos].value == "^") {
        pos++
        result = result.pow(parseUnary())
    }
    return result
}
```

---

### P3-6: `fallbackToDestructiveMigration`

**文件**: `data/local/db/NexaraDatabase.kt:48`

**现状**: 数据库升级时删除所有数据。

**修复方案**: 为每次 version 变更编写 `Migration` 对象。当前 version 5，建议：
```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `custom_skills` (...)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `mcp_servers` (...)")
    }
}
```

---

## 五、修复优先级路线图

| 阶段 | 范围 | 预估工时 |
|------|------|---------|
| **Phase 1** — 恢复编译 | P0-1 ~ P0-4 | 0.5h |
| **Phase 2** — 安全修复 | P1-1 ~ P1-3 (XSS/转义) | 1h |
| **Phase 3** — 功能修正 | P1-4 ~ P1-7 + P2-1 ~ P2-4 | 2h |
| **Phase 4** — 架构改进 | P3-1 ~ P3-6 | 持续迭代 |
