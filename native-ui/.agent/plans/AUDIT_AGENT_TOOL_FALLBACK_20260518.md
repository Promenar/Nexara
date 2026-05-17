# Agent 工具 Fallback 方案 — 审后审计报告

> **审计对象**：`.agent/plans/20260518-agent-tool-fallback-and-workspace-icon-refactoring.md`
> **审计日期**：2026-05-18
> **审计方法**：静态方案审计 + SkillRegistry 接口比对 + Brace Scanner 边界推演

---

## 一、方案核心发现的正确性判定

### ✅ 1.1 `Collection.all` 空集合死锁 — 100% 正确

| 项 | 结论 |
|----|------|
| Kotlin `Collection.all {}` 对空集合返回 `true` | ✅ 数学定义正确，Kotlin 文档明确此行为 |
| 当前代码 `accumulatedToolCalls.all { ... }` 导致空列表 `hasCompleteToolCalls = true` | ✅ 已读源码确认（ChatViewModel.kt:578-581） |
| `!hasCompleteToolCalls` → `false` → Fallback 入口物理级闭锁 | ✅ 逻辑推演正确 |
| 修复方案：`isNotEmpty() && .all { ... }` | ✅ 正确且足够 |

**这是真正的 P0 级 Bug**，且是 2026-05-17 四模型审计中**未被发现的盲区**——GLM 报告指出 Fallback 触发过严（残缺 toolCall 阻止触发），但没有人发现空集合时 Fallback 完全瘫痪。

---

### ✅ 1.2 嵌套 JSON 截断缺陷 — 正确

现有 `bareJsonRegex` 使用 `[^}]*` 非贪婪匹配，遇到 `parameters: {"query": "test"}` 会在第一个 `}` 处截断。方案的**字符级大括号配对扫描器**可以完美解决此问题。

### ✅ 1.3 "不误杀"防御机制 — 设计正确，实现有漏洞

方案的核心思想：`stripToolCallJsonBlocks` 在剔除 JSON 段前先检查 `name` 是否属于已注册的合法工具——非合法工具则 100% 保留在正文中。这个设计思路非常优秀。

---

## 二、发现的问题

### 🔴 P0 — 编译阻塞 Bug：`skillRegistry?.hasTool(it)` 不存在

**方案代码**（`stripToolCallJsonBlocks` 中，第 301 行）：
```kotlin
val isRegistered = name?.let { skillRegistry?.hasTool(it) } ?: false
```

**实际 `SkillRegistry` 接口定义**（`SkillRegistry.kt:6-10`）：
```kotlin
interface SkillRegistry {
    fun getSkill(name: String): SkillDefinition?
    fun getAllSkills(): List<SkillDefinition>
    fun getAllTools(allowedIds: List<String>? = null): List<ProtocolTool>
}
```

**结论**：`hasTool()` 方法不存在于该接口上。直接提交将**编译失败**。

**修正建议**（二选一）：

```kotlin
// 方案 A：用 getSkill() 替代（最简洁）
val isRegistered = name?.let { skillRegistry?.getSkill(it) != null } ?: false

// 方案 B：用 getAllTools() 精确匹配（更严格）
val registeredNames = skillRegistry?.getAllTools()?.map { it.function.name }?.toSet() ?: emptySet()
val isRegistered = name?.let { it in registeredNames } ?: false
```

`ProtocolTool` 结构已确认：
```kotlin
data class ProtocolTool(
    val type: String = "function",
    val function: ProtocolToolFunction  // ← function.name 为工具名
)
```

推荐**方案 A**（`getSkill`），因为它是 O(1) 查找，且语义精确。

---

### 🟡 P1 — 大括号扫描器三处重复，维护隐患

方案中**完全相同的 brace-matching 算法**出现在：

| 位置 | 函数 | 行数 |
|------|------|------|
| `extractToolCallsFromText` 主线 | 裸 JSON 扫描 | ~50 行 |
| `extractToolCallsFromText` 异常回退 | 候选块二次扫描 | ~50 行 |
| `stripToolCallJsonBlocks` | 清洗扫描 | ~50 行 |

三段代码的差异**仅在于**：
1. 扫描目标文本（`content` vs `trimmed`）
2. 匹配后操作（添加 candidate vs 剔除 JSON 段）

**建议**：提取公共函数 `findBalancedBraceJson()`：

```kotlin
/**
 * 在文本中扫描配对大括号包围的 JSON 段。
 * @return 匹配到的 JSON 段列表（首尾索引对），已通过 triggerKeywords 过滤
 */
private data class JsonSegment(val start: Int, val end: Int, val content: String)

private fun scanBalancedJsonSegments(
    text: String,
    startIndex: Int = 0,
    triggerKeywords: List<String> = listOf("\"name\"", "\"tool\"", "\"tool_name\"", "\"function\"")
): List<JsonSegment> {
    val segments = mutableListOf<JsonSegment>()
    var index = startIndex
    while (index < text.length) {
        val openBraceIdx = text.indexOf('{', index)
        if (openBraceIdx == -1) break
        
        val closeBraceIdx = findMatchingCloseBrace(text, openBraceIdx)
        
        if (closeBraceIdx != -1) {
            val possibleJson = text.substring(openBraceIdx, closeBraceIdx + 1)
            if (triggerKeywords.any { possibleJson.contains(it) }) {
                segments.add(JsonSegment(openBraceIdx, closeBraceIdx, possibleJson))
            }
            index = closeBraceIdx + 1
        } else {
            index = openBraceIdx + 1
        }
    }
    return segments
}

private fun findMatchingCloseBrace(text: String, startAt: Int): Int {
    var braceCount = 0
    var inQuote = false
    var escaped = false
    for (i in startAt until text.length) {
        val c = text[i]
        if (escaped) { escaped = false; continue }
        if (c == '\\') { escaped = true; continue }
        if (c == '"' && !inQuote) { inQuote = true; continue }
        if (c == '"' && inQuote) { inQuote = false; continue }
        if (!inQuote) {
            if (c == '{') braceCount++
            else if (c == '}') {
                braceCount--
                if (braceCount == 0) return i
            }
        }
    }
    return -1
}
```

然后三处调用点只需调用 `scanBalancedJsonSegments()` 并对结果做各自操作。**这是最容易产生二次 Bug 的改进点——三处手写相同逻辑极易出现某处修了、别处没修的不一致问题。**

---

### 🟡 P1 — `stripToolCallJsonBlocks` 中 strip 后 `index = openBraceIdx` 的重复扫描

```kotlin
// 方案代码 line 304-307
result = result.substring(0, openBraceIdx) + result.substring(closeBraceIdx + 1)
index = openBraceIdx  // ← 从头重新扫描
continue
```

逻辑上**正确**（因为字符串被截断，原来 `openBraceIdx` 处的内容变了），但会导致 O(n²) 重复扫描。如果 3 人团队维护，不直观。建议改为：

```kotlin
result = result.substring(0, openBraceIdx) + result.substring(closeBraceIdx + 1)
// 不重置 index，因为开括号已删除，当前位置之后的内容就是原来 closeBrace 之后的内容
// 直接 continue 到下一轮即可（index 保持 openBraceIdx，但该位置的内容已变）
continue
```

实际上 `index = openBraceIdx` 是必须的——因为 `result` 被修改了，原来的 `closeBraceIdx + 1` 位置不对应新字符串。但注释上应说明原因。

---

### 🟢 P2 — `extractToolCallsFromText` 中 XML 正则重复定义

方案在 `extractToolCallsFromText` 中新定义了一个 XML 正则（line 93-96）：
```kotlin
val xmlToolCallRegex = Regex(
    """<(?:tool_call|function_call)[^>]*>(.*?)</(?:tool_call|function_call)>""",
    // ...
)
```

但 `ChatViewModel.kt` 中已存在 `XML_TOOL_PATTERN` 常量（line 1552-1555）：
```kotlin
private val XML_TOOL_PATTERN = Regex(
    """<(?:tool_call|function_call|function_name)[^>]*/?>[\s\S]*?</(?:tool_call|function_call)>""",
    // ...
)
```

两个正则相似但不完全相同（`extractToolCallsFromText` 使用 `(.*?)` 捕获组做提取；`XML_TOOL_PATTERN` 用于整体剔除）。这是合理的差异，但**应该加注释说明两处正则的用途不同**（一处提取、一处剔除），避免未来维护者混淆。

---

### 🟢 P2 — ChatScreen.kt 图标变更确认

源文件确认：line 58 `import Icons.Rounded.Tune`，line 780 `Icon(Icons.Rounded.Tune, ...)`。

方案要求改为 `Icons.Rounded.Folder`。`Folder` 是 Material Icons Extended 的一部分，项目中已有其他 Extended 图标使用（如 `Memory`、`HourglassEmpty`），**可以正常使用**。

需要同时修改两处：
1. Line 58：`import Icons.Rounded.Tune` → `import Icons.Rounded.Folder`
2. Line 780：`Icons.Rounded.Tune` → `Icons.Rounded.Folder`

---

## 三、二次 Bug 风险评估

| 风险 | 触发条件 | 严重度 | 缓解 |
|------|---------|--------|------|
| `hasTool()` 不编译 | 直接执行方案 | 🔴 P0 | 改用 `getSkill() != null` |
| Brace Scanner 三处不一致 | 未来某处修改遗漏另外两处 | 🟡 中 | 提取公共函数 |
| 嵌套 JSON 误匹配合法正文 | 正文包含 `{"name":...}` 但非工具调用 | 🟢 低 | "不误杀"机制已保护（`getSkill` 过滤） |
| 超大内容性能退化 | 消息超过 10 万字符 | 🟢 低 | 后处理非流式，可接受 |
| Folder 图标不显示 | 设备版本过老 | 🟢 极低 | Material Icons Extended 已广泛支持 |

---

## 四、修正后的综合结论

```
方案质量评分:

核心 Bug 诊断         ████████████ 10/10  ✅  all 空集合死锁发现极具价值
架构设计              ████████████  9/10  ✅  "不误杀"机制 + brace scanner 设计优秀
实现细节              ████████░░░░  7/10  ⚠️  hasTool() 不存在（编译阻塞）
代码规范性            ████████░░░░  7/10  ⚠️  brace scanner 三处重复
图标变更              ████████████ 10/10  ✅  无风险
─────────────────────────────────────
综合                   █████████░░░ 8.6/10 
```

**总体评价**：方案的核心诊断准确且有价值（all 空集合死锁是正确的盲区发现），架构设计清晰。但三处实现缺陷必须在执行前修正，否则**会导致编译失败或维护地狱**。

**修正优先级**：
1. **立即修**：`hasTool()` → `getSkill() != null`
2. **强烈建议修**：提取 brace scanner 公共函数，消除三处重复
3. **建议修**：增加 XML 正则用途差异的注释
4. **确认性修**：ChatScreen 同时改 import (line 58) + 调用 (line 780)
