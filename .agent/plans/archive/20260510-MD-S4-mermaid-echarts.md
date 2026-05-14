<!-- AGENT INSTRUCTION: 阅读本文档后严格按步骤执行，不要自由发挥。完成后按验收标准逐项确认。 -->

# MD-S4: Mermaid 流程图 + ECharts 图表渲染

> **目标**: 复用 S3 的 WebView 沙箱，实现 Mermaid 和 ECharts 渲染  
> **预估**: 25 分钟 | **依赖**: MD-S3 已完成

---

## 步骤 1: 准备离线资源

### Mermaid
**新建目录**: `app/src/main/assets/mermaid/`
- `mermaid.min.js` — https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js

### ECharts
**新建目录**: `app/src/main/assets/echarts/`
- `echarts.min.js` — https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js

## 步骤 2: Mermaid 渲染器

**新建文件**: `app/src/main/java/com/promenar/nexara/ui/renderer/MermaidRenderer.kt`

```kotlin
package com.promenar.nexara.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MermaidBlock(
    code: String,
    modifier: Modifier = Modifier
) {
    val html = buildMermaidHtml(code)
    RichContentWebView(
        html = html,
        modifier = modifier,
        minHeight = 100,
        maxHeight = 600
    )
}

private fun buildMermaidHtml(code: String): String {
    val escaped = code
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")

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
            /* 暗色主题覆盖 */
            .node rect, .node circle, .node polygon { fill: #2A2A2C !important; stroke: #464554 !important; }
            .nodeLabel, .edgeLabel { color: #E5E1E4 !important; fill: #E5E1E4 !important; }
            .edgePath .path { stroke: #908FA0 !important; }
            .cluster rect { fill: #201F22 !important; stroke: #464554 !important; }
        </style>
    </head>
    <body>
        <div class="mermaid">
            $code
        </div>
        <script>
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
        </script>
    </body>
    </html>
    """.trimIndent()
}
```

## 步骤 3: ECharts 渲染器

**新建文件**: `app/src/main/java/com/promenar/nexara/ui/renderer/EChartsRenderer.kt`

```kotlin
package com.promenar.nexara.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun EChartsBlock(
    optionJson: String,
    modifier: Modifier = Modifier
) {
    val html = buildEChartsHtml(optionJson)
    RichContentWebView(
        html = html,
        modifier = modifier,
        minHeight = 200,
        maxHeight = 500
    )
}

private fun buildEChartsHtml(optionJson: String): String = """
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
                var option = $optionJson;
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
```

## 步骤 4: 集成到 MarkdownText 的分段逻辑

**修改文件**: `ui/common/MarkdownText.kt`

### 4a. 扩展 ContentSegment

```kotlin
private sealed class ContentSegment {
    data class Markdown(val content: String) : ContentSegment()
    data class Latex(val content: String) : ContentSegment()
    data class Mermaid(val content: String) : ContentSegment()
    data class ECharts(val content: String) : ContentSegment()
}
```

### 4b. 增强分段函数

将 `splitLatexSegments` 重命名为 `splitRichSegments`，增加 mermaid 和 echarts 代码块检测：

```kotlin
private fun splitRichSegments(text: String): List<ContentSegment> {
    // 优先提取特殊代码块：```mermaid ... ``` 和 ```echarts ... ```
    // 然后提取 $$ ... $$ LaTeX 块
    // 剩余部分作为普通 Markdown

    val blockPattern = Regex(
        """```(mermaid|echarts)\s*\n(.*?)```""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    val latexPattern = Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)

    data class RichSpan(val start: Int, val end: Int, val segment: ContentSegment)

    val spans = mutableListOf<RichSpan>()

    // 收集特殊代码块
    for (m in blockPattern.findAll(text)) {
        val type = m.groupValues[1].lowercase()
        val content = m.groupValues[2].trim()
        val seg = when (type) {
            "mermaid" -> ContentSegment.Mermaid(content)
            "echarts" -> ContentSegment.ECharts(content)
            else -> continue
        }
        spans.add(RichSpan(m.range.first, m.range.last + 1, seg))
    }

    // 收集 LaTeX 块（排除已被占用的区间）
    for (m in latexPattern.findAll(text)) {
        if (spans.none { it.start <= m.range.first && m.range.last < it.end }) {
            spans.add(RichSpan(m.range.first, m.range.last + 1,
                ContentSegment.Latex(m.groupValues[1].trim())))
        }
    }

    if (spans.isEmpty()) return listOf(ContentSegment.Markdown(text))

    spans.sortBy { it.start }

    val result = mutableListOf<ContentSegment>()
    var cursor = 0
    for (span in spans) {
        if (span.start > cursor) {
            result.add(ContentSegment.Markdown(text.substring(cursor, span.start)))
        }
        result.add(span.segment)
        cursor = span.end
    }
    if (cursor < text.length) {
        result.add(ContentSegment.Markdown(text.substring(cursor)))
    }
    return result
}
```

### 4c. 渲染分支

```kotlin
for (segment in segments) {
    when (segment) {
        is ContentSegment.Markdown -> { /* 同之前 */ }
        is ContentSegment.Latex -> { LatexBlock(latex = segment.content) }
        is ContentSegment.Mermaid -> { MermaidBlock(code = segment.content) }
        is ContentSegment.ECharts -> { EChartsBlock(optionJson = segment.content) }
    }
}
```

## 验收标准

1. 编译通过
2. ` ```mermaid\ngraph LR\nA-->B\n``` ` 渲染为流程图（暗色主题）
3. ` ```echarts\n{"xAxis":{"data":["A","B"]},...}\n``` ` 渲染为图表
4. Mermaid/ECharts 渲染错误时显示错误信息
5. 与 LaTeX、普通 Markdown 混排正常

## DIA

- 更新 `.agent/handover.md`：记录 MD-S4 完成


