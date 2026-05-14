<!-- AGENT INSTRUCTION: 阅读本文档后严格按步骤执行，不要自由发挥。完成后按验收标准逐项确认。 -->

# MD-S3: WebView 沙箱基座 + LaTeX 数学公式渲染

> **目标**: 建立统一 WebView 沙箱组件，实现 LaTeX（KaTeX）数学公式渲染  
> **预估**: 30 分钟 | **依赖**: MD-S1 已完成

---

## 步骤 1: 下载 KaTeX 离线资源

**新建目录**: `app/src/main/assets/katex/`

需要的文件（从 CDN 下载或项目内 bundled）：
- `katex.min.js` — https://cdn.jsdelivr.net/npm/katex@0.16.21/dist/katex.min.js
- `katex.min.css` — https://cdn.jsdelivr.net/npm/katex@0.16.21/dist/katex.min.css
- `fonts/` — KaTeX 字体文件夹（从 katex release 包中完整复制）

也可以选择在线模式（不下载），直接用 CDN URL，但离线方案对移动端更佳。

## 步骤 2: 创建通用 WebView 沙箱组件

**新建文件**: `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebView.kt`

```kotlin
package com.promenar.nexara.ui.renderer

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraShapes

/**
 * 通用 WebView 沙箱，用于渲染 LaTeX / Mermaid / ECharts 等富内容。
 *
 * @param html 完整的 HTML 字符串（含 <html><head><body>）
 * @param minHeight 最小高度
 * @param maxHeight 最大高度
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichContentWebView(
    html: String,
    modifier: Modifier = Modifier,
    minHeight: Int = 40,
    maxHeight: Int = 600,
) {
    var webViewHeight by remember { mutableIntStateOf(minHeight) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // 获取内容实际高度并调整 WebView 高度
                        view?.evaluateJavascript(
                            "(function() { return document.body.scrollHeight; })()"
                        ) { heightStr ->
                            val h = heightStr.toIntOrNull() ?: minHeight
                            webViewHeight = h.coerceIn(minHeight, maxHeight)
                        }
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp, max = maxHeight.dp)
            .clip(NexaraShapes.medium)
            .background(NexaraColors.SurfaceLowest)
    )
}
```

## 步骤 3: 创建 LaTeX 渲染器

**新建文件**: `app/src/main/java/com/promenar/nexara/ui/renderer/LatexRenderer.kt`

```kotlin
package com.promenar.nexara.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 检测并渲染 LaTeX 数学公式块。
 * 支持：$$ ... $$（块级）和 $ ... $（行内，后续支持）
 */
@Composable
fun LatexBlock(
    latex: String,
    modifier: Modifier = Modifier
) {
    val html = buildLatexHtml(latex)
    RichContentWebView(
        html = html,
        modifier = modifier,
        minHeight = 30,
        maxHeight = 400
    )
}

private fun buildLatexHtml(latex: String): String {
    // 转义 HTML 特殊字符
    val escaped = latex
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

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
                katex.render("$escaped", document.getElementById("math"), {
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
```

## 步骤 4: 集成 LaTeX 块到 Markdown 渲染流

**修改文件**: `ui/common/MarkdownText.kt`

在 `Markdown()` 调用前添加 LaTeX 预处理逻辑：

```kotlin
import com.promenar.nexara.ui.renderer.LatexBlock

@Composable
fun MarkdownText(
    markdown: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 将内容拆分为普通 markdown 块和 LaTeX 块
    val segments = remember(markdown) { splitLatexSegments(markdown) }

    Column(modifier = modifier.fillMaxWidth()) {
        for (segment in segments) {
            when (segment) {
                is ContentSegment.Markdown -> {
                    if (segment.content.isNotBlank()) {
                        Markdown(
                            content = segment.content,
                            colors = nexaraMarkdownColors(),
                            typography = nexaraMarkdownTypography(),
                            // ... components 同 S2 中的配置
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is ContentSegment.Latex -> {
                    LatexBlock(latex = segment.content)
                }
            }
        }
        if (isStreaming) { StreamingCursor() }
    }
}

// 内容段类型
private sealed class ContentSegment {
    data class Markdown(val content: String) : ContentSegment()
    data class Latex(val content: String) : ContentSegment()
}

// 从 markdown 文本中提取 $$ ... $$ LaTeX 块
private fun splitLatexSegments(text: String): List<ContentSegment> {
    val pattern = Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
    val segments = mutableListOf<ContentSegment>()
    var lastIndex = 0

    for (match in pattern.findAll(text)) {
        if (match.range.first > lastIndex) {
            segments.add(ContentSegment.Markdown(text.substring(lastIndex, match.range.first)))
        }
        segments.add(ContentSegment.Latex(match.groupValues[1].trim()))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        segments.add(ContentSegment.Markdown(text.substring(lastIndex)))
    }
    return segments.ifEmpty { listOf(ContentSegment.Markdown(text)) }
}
```

## 验收标准

1. 编译通过
2. AI 回复中 `$$ E = mc^2 $$` 渲染为数学公式（非原始文本）
3. 复杂公式（矩阵、分式、积分）渲染正确
4. LaTeX 块前后的普通 Markdown 内容正常渲染
5. WebView 高度自适应内容

## DIA

- 更新 `.agent/handover.md`：记录 MD-S3 完成
- 更新 `ARCHITECTURE.md`：记录 `ui/renderer/` 新目录


