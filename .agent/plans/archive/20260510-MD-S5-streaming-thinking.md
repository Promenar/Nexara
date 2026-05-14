<!-- AGENT INSTRUCTION: 阅读本文档后严格按步骤执行，不要自由发挥。完成后按验收标准逐项确认。 -->

# MD-S5: 流式渲染优化 + ThinkingBlock 接入

> **目标**: 优化流式输出的渲染性能，将 ThinkingBlock 也接入 Markdown 渲染  
> **预估**: 20 分钟 | **依赖**: MD-S1 已完成

---

## 步骤 1: 流式渲染 — 未闭合块保护

**修改文件**: `ui/common/MarkdownText.kt`

流式输出时，AI 的 token 逐个到达，可能出现未闭合的 Markdown 结构（如 `**粗体` 缺少闭合 `**`、代码围栏 ```` ``` ```` 缺少结束标记）。需要在传入解析器前进行预处理。

添加 `sanitizeStreamingMarkdown()` 函数：

```kotlin
/**
 * 流式输出时修补未闭合的 Markdown 结构，防止解析器产生异常渲染。
 * 仅在 isStreaming=true 时调用。
 */
private fun sanitizeStreamingMarkdown(text: String): String {
    var result = text

    // 1. 未闭合的代码围栏：如果 ``` 出现奇数次，追加闭合
    val fenceCount = Regex("```").findAll(result).count()
    if (fenceCount % 2 != 0) {
        result += "\n```"
    }

    // 2. 未闭合的 LaTeX 块：$$ 出现奇数次
    val latexCount = Regex("""\$\$""").findAll(result).count()
    if (latexCount % 2 != 0) {
        // 不渲染未闭合的 LaTeX，截断到最后一个 $$
        val lastIdx = result.lastIndexOf("$$")
        if (lastIdx >= 0) {
            result = result.substring(0, lastIdx)
        }
    }

    // 3. 未闭合的行内标记不需特殊处理，flexmark 会优雅降级

    return result
}
```

在 `MarkdownText` 中使用：

```kotlin
@Composable
fun MarkdownText(
    markdown: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val processed = remember(markdown, isStreaming) {
        val text = if (isStreaming) sanitizeStreamingMarkdown(markdown) else markdown
        splitRichSegments(text)
    }
    // ... 渲染逻辑不变
}
```

## 步骤 2: 流式渲染 — 延迟 WebView 渲染

流式输出时，LaTeX/Mermaid/ECharts 块在完全闭合前不应触发 WebView 渲染。
`splitRichSegments` 中的正则已自然处理了这一点（未闭合的 `$$` 或 `` ``` `` 不会匹配），但 `sanitizeStreamingMarkdown` 的截断逻辑进一步保证了安全。

## 步骤 3: ThinkingBlock 接入 Markdown 渲染

**修改文件**: `ui/chat/ChatScreen.kt`

当前 `ThinkingBlock` 组件（约第 495-583 行）中，reasoning 文本用纯 `Text()` 渲染。

### 3a. 添加 import（如 S1 未添加）

```kotlin
import com.promenar.nexara.ui.common.MarkdownText
```

### 3b. 修改 ThinkingBlock 内部

**定位**: ThinkingBlock 函数中约第 544-554 行

将：
```kotlin
if (reasoning.isNotBlank()) {
    Text(
        text = reasoning,
        style = NexaraTypography.bodyMedium.copy(
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
        ),
        color = NexaraColors.OnSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}
```

替换为：
```kotlin
if (reasoning.isNotBlank()) {
    MarkdownText(
        markdown = reasoning,
        isStreaming = isGenerating,
        modifier = Modifier.padding(top = 8.dp)
    )
}
```

> **注意**: ThinkingBlock 的背景色较暗，MarkdownText 中 mikepenz 的 text 颜色继承自 NexaraMarkdownTheme（OnBackground = #E5E1E4），在 ThinkingBlock 的半透明 SurfaceContainer 背景上可读性可接受。如对比度不足，可后续为 ThinkingBlock 创建独立的 markdownColors 变体。

## 步骤 4: 清理残留

- 检查 ChatScreen.kt 中是否还有 `import` 了旧组件但未使用的行
- 确保 ChatBubble 中不再有任何直接 `Text(message.content)` 调用（用户消息除外）
- 确保所有 `infiniteTransition` / `cursorAlpha` 旧光标代码已在 S1 中清理

## 验收标准

1. 编译通过
2. 流式输出时，未闭合的代码块不会导致渲染异常
3. 流式输出时，LaTeX 公式在 `$$` 闭合后才渲染（闭合前不显示半成品）
4. ThinkingBlock 中的 reasoning 支持 Markdown 格式（粗体、列表、代码）
5. 快速 token 流入时无明显 UI 卡顿

## DIA

- 更新 `.agent/handover.md`：记录 MD-S5 完成，标记 Markdown 渲染大修全部完成
- 更新 `CHANGELOG.md`：记录 Markdown 富文本渲染能力升级


