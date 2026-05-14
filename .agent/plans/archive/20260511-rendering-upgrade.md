# Nexara 渲染系统分阶段提升方案

> 基于审计报告：`.agent/audits/20260511-rendering-audit.md`
> 策略：11 个独立会话，3 波并行执行
> 每个会话附带可直接复制粘贴的提示词指令

---

## 执行策略概览

```
Wave 1 (6 会话并行, 零依赖)          Wave 2 (4 会话并行, 依赖 S1)       Wave 3 (1 会话, 依赖 S1+S2+S7)
┌──────────────────────────┐       ┌──────────────────────────┐       ┌──────────────────────────┐
│ S1: WebView 池化         │──┬───→│ S7: 内联数学渲染         │──┬───→│ S11: MarkdownText 集成  │
│ S2: 自定义表格组件       │  │    │ S8: Mermaid 交互增强     │  │    └──────────────────────────┘
│ S3: 代码块行号           │  │    │ S9: ECharts 增强         │  │
│ S4: 图片 Lightbox        │  │    │ S10: PlantUML 支持      │  │
│ S5: CJK 排版优化         │  │    └──────────────────────────┘  │
│ S6: 无障碍 (TalkBack)    │  │                                   │
└──────────────────────────┘  └───────────────────────────────────┘
```

---

## Wave 1 — 基础增强 (全并行，6 会话)

### 会话 S1: WebView 池化工具

**目标**：创建共享 WebView 池，消除每个 LaTeX/Mermaid/ECharts 块创建新 WebView 的内存开销。

**创建文件**：
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebViewPool.kt`

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebView.kt`

**依赖**：无

**预计行数**：~80 行新建 + ~30 行修改

---

#### 会话 S1 提示词（复制此段到新会话）

```
## 任务：为 Nexara Native 创建 WebView 池化工具

### 背景
当前 `RichContentWebView.kt` 每次组件组合都创建新的 Android WebView 实例。
一个包含 5 个 LaTeX 等式 + 2 个 Mermaid 图表的文档会同时持有 7 个 WebView，每个消耗 50-80MB 内存。
需要创建一个复用池来限制同时存在的 WebView 数量。

### 当前代码参考

先读取这些文件理解当前架构：
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebView.kt` (73 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/LatexRenderer.kt` (61 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/MermaidRenderer.kt` (64 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/EChartsRenderer.kt` (53 行)

### 需要创建的文件

#### 文件 1: `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebViewPool.kt`

```kotlin
package com.promenar.nexara.ui.renderer

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Shared WebView pool to avoid creating a new WebView per LaTeX/Mermaid/ECharts block.
 * Pool size capped at 3. On cache miss, creates a new one; on release, resets to about:blank.
 */
object RichContentWebViewPool {
    private const val MAX_POOL_SIZE = 3
    private val pool = ArrayDeque<WebView>(MAX_POOL_SIZE)
    private var totalCreated = 0

    fun acquire(context: Context): WebView {
        val webView = pool.removeFirstOrNull()
        if (webView != null) {
            webView.visibility = android.view.View.VISIBLE
            return webView
        }
        totalCreated++
        return createWebView(context)
    }

    fun release(webView: WebView) {
        webView.visibility = android.view.View.GONE
        // Reset WebView state to prevent stale content flickering
        webView.loadUrl("about:blank")
        if (pool.size < MAX_POOL_SIZE) {
            pool.addLast(webView)
        } else {
            webView.destroy()
        }
    }

    fun destroyAll() {
        pool.forEach { it.destroy() }
        pool.clear()
    }

    private fun createWebView(context: Context): WebView {
        return WebView(context).apply {
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
                // onPageFinished callback is set per-use in RichContentWebView
            }
        }
    }
}
```

关键设计决策：
- 池大小 = 3：覆盖大多数使用场景（1 个 LaTeX + 1 个 Mermaid + 1 个 ECharts 同时可见）
- `acquire()` 从池中取或新建
- `release()` 重置为 about:blank 并归还
- `destroyAll()` 用于 Application.onTerminate 或内存压力回调

#### 文件 2: 修改 `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebView.kt`

关键改动：
1. 不再在 `factory` lambda 中创建 WebView，改为从池中获取
2. 在 `onPageFinished` 中设置 webViewClient 以支持高度回调
3. 添加 `DisposableEffect` 在组件销毁时将 WebView 归还池

修改后的 `RichContentWebView` 签名和行为：
```kotlin
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichContentWebView(
    html: String,
    modifier: Modifier = Modifier,
    minHeight: Int = 40,
    maxHeight: Int = 600,
) {
    val context = LocalContext.current
    var webViewHeight by remember { mutableIntStateOf(minHeight) }
    
    // Acquire from pool instead of factory-creating
    val webView = remember { RichContentWebViewPool.acquire(context) }

    // Set the page-finished callback dynamically
    LaunchedEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(
                    "(function() { return document.body.scrollHeight; })()"
                ) { heightStr ->
                    val h = heightStr.toIntOrNull() ?: minHeight
                    webViewHeight = h.coerceIn(minHeight, maxHeight)
                }
            }
        }
    }

    // Return to pool on dispose
    DisposableEffect(webView) {
        onDispose { RichContentWebViewPool.release(webView) }
    }

    AndroidView(
        factory = { webView },
        update = { wv ->
            wv.loadDataWithBaseURL(
                "file:///android_asset/",
                html, "text/html", "UTF-8", null
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

注意：`factory` 现在直接返回从池中获取的 webView，不再调用 `WebView(context)`。
需要添加 import:
```kotlin
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
```

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 编译通过
2. 逻辑正确：LazyColumn 滚动时 WebView 被正确复用（离屏时 release，入屏时 acquire）
3. 无内存泄漏：池中 WebView 数量不超过 3
```

---

### 会话 S2: 自定义表格组件

**目标**：注入原生 Compose 表格组件到 mikepenz markdown 渲染管线，支持水平滚动响应式。

**创建文件**：
- `app/src/main/java/com/promenar/nexara/ui/renderer/TableWidget.kt`

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt`

**依赖**：无（可独立于 S1 运行）

**预计行数**：~160 行新建 + ~15 行修改

---

#### 会话 S2 提示词

```
## 任务：为 Nexara 创建移动端响应式 Markdown 表格组件

### 背景
当前表格渲染委托给 mikepenz 库内建组件，无水平滚动支持。
在窄屏 (360dp) 手机上，超过 3 列的表格会溢出屏幕或被裁剪。
需要通过 mikepenz 的 `markdownComponents(table = ...)` 注入自定义表格组件。

### 当前代码参考

先读取这些文件：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt` — 理解 `markdownComponents` 注入点
- `app/src/main/java/com/promenar/nexara/ui/theme/Color.kt` — 表格颜色
- `app/src/main/java/com/promenar/nexara/ui/theme/Type.kt` — 表格文本样式

### 需要创建的文件

#### 文件: `app/src/main/java/com/promenar/nexara/ui/renderer/TableWidget.kt`

设计思路：
1. 使用 `mikepenz` 的 `MarkdownTable` 组件模型 (从 `markdownComponents(table = { model -> ... })` 接收)
2. 用 `horizontalScroll(rememberScrollState())` 包裹整个表格实现水平滚动
3. 添加视觉增强：标题行背景色区分、交替行色 (`zebra striping`)、边框线

```kotlin
package com.promenar.nexara.ui.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableCell
import com.mikepenz.markdown.model.TableCellAlignment
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraShapes
import com.promenar.nexara.ui.theme.NexaraTypography

@Composable
fun NexaraTableWidget(
    table: MarkdownTable,
    modifier: Modifier = Modifier,
    minColumnWidth: Dp = 80.dp,
    maxColumnWidth: Dp = 200.dp
) {
    val horizontallScrollState = rememberScrollState()
    val rows = remember(table) { table.rows }
    val columnAlignments = remember(table) { table.columnAlignments }
    val columnCount = rows.firstOrNull()?.cells?.size ?: 0

    Column(
        modifier = modifier
            .horizontalScroll(horizontallScrollState)
            .clip(NexaraShapes.medium)
            .border(0.5.dp, NexaraColors.OutlineVariant, NexaraShapes.medium)
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .background(
                        when {
                            rowIndex == 0 -> NexaraColors.SurfaceContainer
                            rowIndex % 2 == 0 -> NexaraColors.SurfaceLow
                            else -> NexaraColors.SurfaceLowest
                        }
                    )
            ) {
                row.cells.forEachIndexed { colIndex, cell ->
                    val alignment = columnAlignments.getOrElse(colIndex) { TableCellAlignment.LEFT }
                    val textAlign = when (alignment) {
                        TableCellAlignment.LEFT -> TextAlign.Left
                        TableCellAlignment.CENTER -> TextAlign.Center
                        TableCellAlignment.RIGHT -> TextAlign.Right
                    }

                    Text(
                        text = cell.content,
                        textAlign = textAlign,
                        style = if (rowIndex == 0) {
                            NexaraTypography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NexaraColors.OnSurface
                            )
                        } else {
                            NexaraTypography.bodySmall.copy(
                                color = NexaraColors.OnSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .widthIn(min = minColumnWidth, max = maxColumnWidth)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .then(
                                if (colIndex < columnCount - 1) {
                                    Modifier.border(
                                        width = 0.5.dp,
                                        color = NexaraColors.OutlineVariant
                                    )
                                } else Modifier
                            )
                    )
                }
            }
        }
    }
}
```

### 需要修改的文件

#### 在 `MarkdownText.kt` 中注入表格组件

在 `markdownComponents(...)` 块中添加 `table` 参数。找到大约第 177 行的 `components = markdownComponents(` 块，在其中添加：

```kotlin
table = { model ->
    NexaraTableWidget(
        table = model,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
    )
},
```

需要添加 import:
```kotlin
import com.promenar.nexara.ui.renderer.NexaraTableWidget
```

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 编译通过
2. 表格在窄屏 (360dp) 可以水平滚动
3. 标题行有视觉区分 (SurfaceContainer 背景 + Bold)
4. 交替行有斑马条纹效果
5. 列对齐方向 (左/中/右) 正确
```

---

### 会话 S3: 代码块行号增强

**目标**：为代码块添加行号显示，对标 ChatGPT iOS 代码渲染。

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/renderer/CodeBlockHeader.kt`

**依赖**：无

**预计行数**：~60 行修改

---

#### 会话 S3 提示词

```
## 任务：为 Nexara 代码块添加行号

### 背景
当前 `CodeBlockHeader.kt` 提供语言标签 + 复制按钮，缺少行号。
需要为代码内容添加左侧行号列，对标 ChatGPT iOS/Android 代码渲染。

### 当前代码参考

先读取 `app/src/main/java/com/promenar/nexara/ui/renderer/CodeBlockHeader.kt` 全文 (92 行)。

### 修改方案

修改 `CodeBlockWithHeader` composable 的 `codeContent` 区域 (大约第 88-90 行)：
当前是 `Box(modifier = Modifier.padding(16.dp)) { codeContent() }`，
改为 `Row` 布局：左侧行号列 + 右侧代码列，支持水平滚动。

关键改动点：

1. **拆分代码为行**：在 composable 开头用 `remember(code)` 拆分 `code.lines()` 得到行数组和行数

2. **行号列**：使用 `Text` 渲染右对齐的行号，用 `OnSurfaceVariant.copy(alpha=0.4f)` 颜色

3. **代码 + 行号放在水平可滚动的 Row 中**：
```kotlin
Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
    // 行号列
    Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp)) {
        lines.forEachIndexed { index, _ ->
            Text(
                text = "${index + 1}",
                style = NexaraTypography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = NexaraColors.OnSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.End
                ),
                modifier = Modifier.widthIn(min = 28.dp)
            )
        }
    }
    // 代码列
    Box(modifier = Modifier.padding(end = 16.dp, top = 16.dp, bottom = 16.dp)) {
        codeContent()
    }
}
```

需要添加 import:
```kotlin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
```

4. **微调**：行号宽度需要根据最大行数动态计算（如 1-9 用 28.dp，10-99 用 36.dp，100+ 用 44.dp）

5. **分隔线**：在行号列和代码列之间添加一条竖线：`Modifier.width(0.5.dp).height(...).background(OutlineVariant)`

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 编译通过
2. 代码块显示行号，从 1 开始递增
3. 行号颜色为半透明灰色，右对齐
4. 代码块支持水平滚动 (当代码行超出屏幕宽度时)
5. 行号与代码之间有视觉分隔线
```

---

### 会话 S4: 图片 Lightbox 全屏查看器

**目标**：实现点击 markdown 图片时全屏缩放查看，对标 ChatGPT iOS 图片查看器。

**创建文件**：
- `app/src/main/java/com/promenar/nexara/ui/renderer/ImageLightbox.kt`

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt`

**依赖**：无

**预计行数**：~120 行新建 + ~10 行修改

---

#### 会话 S4 提示词

```
## 任务：为 Nexara 添加图片 Lightbox 全屏查看器

### 背景
markdown 中的图片通过 mikepenz + Coil 3 内建渲染，无点击放大功能。
需要拦截图片点击事件，打开全屏 dialog 支持双指缩放和拖拽。

### 当前代码参考

先读取：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt` — 理解如何注入自定义图片组件
- `app/src/main/java/com/promenar/nexara/ui/renderer/NexaraMarkdownTheme.kt` — 颜色体系

### 需要创建的文件

#### 文件: `app/src/main/java/com/promenar/nexara/ui/renderer/ImageLightbox.kt`

```kotlin
package com.promenar.nexara.ui.renderer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.promenar.nexara.ui.theme.NexaraColors

@Composable
fun ImageLightbox(
    imageUrl: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    // Note: visible state is managed externally via markdownComponents(image = ...)
    // This composable is the dialog body.
}
```

完整实现要求：
1. **全屏 Dialog**：`Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` 实现真正的全屏
2. **背景**：`Color.Black.copy(alpha = 0.95f)` 半透明黑色背景
3. **双指缩放 + 平移手势**：使用 `detectTransformGestures` 处理 `scale`, `pan`, `zoom`
4. **关闭按钮**：右上角圆形关闭按钮 (IconButton + Icons.Close)
5. **点击背景关闭**：`detectTapGestures { onDismiss() }`
6. **Coil AsyncImage**：`contentScale = ContentScale.Fit`，支持 `graphicsLayer { scaleX/scaleY/translationX/translationY }`
7. **动画进出**：`AnimatedVisibility(enter = fadeIn(), exit = fadeOut())`

关键手势处理代码：
```kotlin
var scale by remember { mutableFloatStateOf(1f) }
var offsetX by remember { mutableFloatStateOf(0f) }
var offsetY by remember { mutableFloatStateOf(0f) }

Modifier.pointerInput(Unit) {
    detectTransformGestures { centroid, pan, zoom, rotation ->
        scale = (scale * zoom).coerceIn(0.5f, 5f)
        offsetX += pan.x
        offsetY += pan.y
    }
}
```

### 需要修改的文件

在 `MarkdownText.kt` 的 `markdownComponents(...)` 中添加 `image` 参数来拦截图片点击：

```kotlin
image = { model ->
    var showLightbox by remember { mutableStateOf(false) }
    
    // 使用 Coil AsyncImage 渲染（mikepenz 默认行为）
    AsyncImage(
        model = model.src,
        contentDescription = model.contentDescription,
        modifier = Modifier
            .clickable { showLightbox = true }
            // ... 保持 mikepenz 默认样式
    )
    
    if (showLightbox) {
        ImageLightbox(
            imageUrl = model.src,
            visible = showLightbox,
            onDismiss = { showLightbox = false }
        )
    }
},
```

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 编译通过
2. 点击 markdown 中的图片弹出全屏 Dialog
3. 支持双指缩放 (0.5x ~ 5x) 和拖拽平移
4. 右上角关闭按钮可见
5. 点击背景也可关闭
```

---

### 会话 S5: CJK 排版优化

**目标**：优化中文字体回退、行高和字间距。

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/theme/Type.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/NexaraMarkdownTheme.kt`

**依赖**：无

**预计行数**：~20 行修改

---

#### 会话 S5 提示词

```
## 任务：优化 Nexara CJK 中文字体排版

### 背景
当前 `Type.kt` 使用 `FontFamily.SansSerif` 和 `FontFamily.Monospace`，
均为系统默认拉丁字体。Inter/Manrope 不支持 CJK 字符，Android 自动回退到
Noto Sans CJK，但行高和字间距未针对 CJK 优化。

### 当前代码参考

先读取：
- `app/src/main/java/com/promenar/nexara/ui/theme/Type.kt` (51 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/NexaraMarkdownTheme.kt` (34 行)

### 需要修改的文件

#### 文件 1: `app/src/main/java/com/promenar/nexara/ui/theme/Type.kt`

具体改动：

1. **bodyMedium 行高提升**：15sp/25sp → 15sp/26sp (CJK 字符视觉密度更高，需要更多行高)
2. **bodyLarge 行高提升**：17sp/26sp → 17sp/28sp
3. **bodySmall (代码) 行高提升**：14sp/22sp → 14sp/24sp
4. **添加 bodySmallCJK**：专用 CJK 文本样式，含略微字间距
5. **添加 letterSpacing**：bodyMedium + 0.01.em (em-based spacing for CJK readability)

```kotlin
// 在 Type.kt 中添加
val bodyMediumCJK = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 15.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.01.em,
    fontWeight = FontWeight.Normal
)
```

6. **标题行高提升**：
   - h1: 24sp/32sp → 24sp/36sp (+12%)
   - h2: 20sp/28sp → 20sp/32sp (+14%)
   - h3-h6: 同理 +2~4sp 行高

#### 文件 2: `app/src/main/java/com/promenar/nexara/ui/renderer/NexaraMarkdownTheme.kt`

将 `text = NexaraTypography.bodyMedium` 改为使用新 CJK 样式：
```kotlin
text = NexaraTypography.bodyMedium.copy(
    lineHeight = 26.sp,
    letterSpacing = 0.01.em
),
h1 = NexaraTypography.headlineLarge.copy(lineHeight = 36.sp),
h2 = NexaraTypography.headlineMedium.copy(lineHeight = 32.sp),
// 以及 h3-h6 的行高调整
```

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 编译通过
2. 中文文本在暗色背景上清晰可读
3. 中文行间距比英文文本略大 (视觉舒适)
4. 标题行高不低于字体大小的 1.4x
```

---

### 会话 S6: 无障碍 (TalkBack)

**目标**：为 Markdown 渲染元素添加语义标注和 contentDescription。

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/renderer/CodeBlockHeader.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/LatexRenderer.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/MermaidRenderer.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/EChartsRenderer.kt`
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt`

**依赖**：无

**预计行数**：~40 行修改（分布在 5 个文件）

---

#### 会话 S6 提示词

```
## 任务：为 Nexara 渲染组件添加 TalkBack 无障碍支持

### 背景
当前所有渲染组件缺少语义 contentDescription，导致视力障碍用户无法使用 TalkBack 导航。
每个组件类型需要一个有意义的 contentDescription。

### 当前代码参考

先读取这 5 个文件：
- `app/src/main/java/com/promenar/nexara/ui/renderer/CodeBlockHeader.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/LatexRenderer.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/MermaidRenderer.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/EChartsRenderer.kt`
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt`

### 修改方案

为每个组件类型添加 Compose `semantics` 和 `contentDescription`：

#### 1. `CodeBlockHeader.kt` — 代码块
在最外层 `Column` 或 `Row` 上添加：
```kotlin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// 在 CodeBlockWithHeader 最外层布局上
Modifier.semantics {
    contentDescription = "Code block in ${language ?: "plain text"}, ${code.lines().size} lines"
}
```

#### 2. `LatexRenderer.kt` — LaTeX 数学公式
在 `RichContentWebView` 外层添加：
```kotlin
Modifier.semantics {
    contentDescription = "Mathematical formula: $latex"
}
```
注意：需要将 latex 参数传入 LatexBlock composable 的 modifier 链。

#### 3. `MermaidRenderer.kt` — 图表
在 `RichContentWebView` 外层添加：
```kotlin
Modifier.semantics {
    contentDescription = "Diagram: ${code.take(60)}..."
}
```

#### 4. `EChartsRenderer.kt` — 数据图表
```kotlin
Modifier.semantics {
    contentDescription = "Interactive chart"
}
```

#### 5. MarkdownText.kt — 流式状态
在 `StreamingCursor` 的 Box 上：
```kotlin
Modifier.semantics {
    contentDescription = "Generating response"
}
```

关键 import：
```kotlin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
```

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 编译通过
2. 用 Android TalkBack 或 Accessibility Scanner 测试时，每个组件能有意义的描述
3. contentDescription 精准描述内容类型，不暴露原始 Base64 数据
```

---

## Wave 2 — 可视化增强 (依赖 S1 WebView 池, 4 会话并行)

### 会话 S7: 内联数学渲染

**目标**：支持 `$...$` 内联数学公式渲染，直接在文本中显示 KaTeX 渲染的数学符号。

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/LatexRenderer.kt`

**创建文件**：
- `app/src/main/java/com/promenar/nexara/ui/renderer/InlineLatexSpan.kt`

**依赖**：S1 (WebViewPool)

**预计行数**：~140 行新建 + ~30 行修改

---

#### 会话 S7 提示词

```
## 任务：为 Nexara 实现内联 LaTeX 数学公式渲染 ($...$)

### 背景
当前 `splitRichSegments()` 的 regex 仅匹配 `$$...$$` 显示模式。
`$...$` 内联数学（例如面积公式 `$A = \pi r^2$`）被当作纯文本渲染。
需要检测内联数学标识符，将其渲染为内嵌的 KaTeX 位图。

### 当前代码参考

先读取：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt` (全文 270 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/LatexRenderer.kt` (61 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebView.kt` (73 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebViewPool.kt` (S1 创建的文件)
- `app/src/main/java/com/promenar/nexara/ui/theme/Color.kt` — 数学文本颜色

### 技术方案

内联数学渲染方案：**KaTeX WebView → Canvas → Bitmap → Image composable**

原因是 Android Compose 难以在文本中间精确嵌入 WebView，因此采用位图方案：
1. 用一个隐藏的 KaTeX WebView 渲染公式
2. 通过 `evaluateJavascript` 获取渲染后 DOM 的宽高
3. 用 `Canvas` + `drawBitmap` 将渲染结果转为 Bitmap
4. 在 Compose 文本中通过 `InlineTextContent` / `AnnotatedString` 嵌入

简化方案（推荐）：将 `$...$` 公式单独渲染为小图片，插入到 Compose `Row` 中

### 需要修改的文件

#### 文件 1: 修改 `MarkdownText.kt` 的 `splitRichSegments()`

在 `splitRichSegments()` 函数中（大约第 49-95 行），添加对内联数学的检测。

需要修改 latexPattern regex 以同时匹配 `$$...$$` 和 `$...$`：
```kotlin
// 替换第 54 行的 latexPattern
val latexPattern = Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
val inlineLatexPattern = Regex("""(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)""")
```

关键区分逻辑：
- `$$...$$` → `ContentSegment.Latex` (显示模式，块级)
- `$...$` → 新类型 `ContentSegment.InlineLatex` (内联模式)

在 `ContentSegment` sealed class (第 35-40 行) 中添加：
```kotlin
data class InlineLatex(val content: String) : ContentSegment()
```

然后在 `splitRichSegments` 中：检测内联数学 span，确保不与 `$$` 块重叠。

#### 文件 2: 创建 `app/src/main/java/com/promenar/nexara/ui/renderer/InlineLatexSpan.kt`

```kotlin
package com.promenar.nexara.ui.renderer

import android.util.Base64
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.promenar.nexara.ui.theme.NexaraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun InlineLatexSpan(
    latex: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Use hidden WebView from pool to render LaTeX
    // The WebView renders with transparent background, displayMode=false (inline style)
    // Then captures to bitmap via drawToBitmap

    DisposableEffect(latex) {
        // Async rendering logic here
        onDispose { }
    }

    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Math: $latex",
            modifier = modifier
                .width(size.width.dp)
                .height(size.height.dp)
        )
    }
}
```

完整实现注意：
- displayMode=false (KaTeX 内联模式，公式高度更小)
- 颜色匹配 `NexaraColors.OnBackground` (#E5E1E4)
- 位图尺寸需要从 WebView 的 `document.body.scrollWidth/Height` 动态获取
- 异步渲染在 `Dispatchers.IO` 执行
- 确保 WebView 从池中获取并在完成后归还

#### 文件 3: 修改 `MarkdownText.kt` 渲染分发 (约第 169-228 行)

在 `when (segment)` 分支中添加：
```kotlin
is ContentSegment.InlineLatex -> {
    // Inline math: render inline within text flow
    // Since these appear within Markdown segments, we handle them
    // by splitting markdown segments into InlineTextContent
    InlineLatexSpan(latex = segment.content)
}
```

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 编译通过
2. `$E = mc^2$` 在聊天消息中渲染为内嵌数学公式
3. 内联数学公式颜色与周围文本协调 (OnBackground)
4. `$$...$$` 显示数学不受影响
5. 内联数学公式高度不超过周围文本行高
```

---

### 会话 S8: Mermaid 图表交互增强

**目标**：为 Mermaid 图表添加双指缩放、双击重置、PNG 导出按钮。

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/renderer/MermaidRenderer.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebView.kt` (如果池化未完成，使用已有池)

**依赖**：S1 (WebViewPool)

**预计行数**：~80 行修改

---

#### 会话 S8 提示词

```
## 任务：为 Nexara Mermaid 图表添加交互增强

### 背景
当前 MermaidRenderer 仅支持静态图表渲染，缺少缩放、全屏、导出功能。
对标专业图表工具：需要支持双指缩放 + PNG 导出。

### 当前代码参考

先读取：
- `app/src/main/java/com/promenar/nexara/ui/renderer/MermaidRenderer.kt` (64 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebView.kt` (73 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebViewPool.kt` (如果 S1 已完成)
- `app/src/main/java/com/promenar/nexara/ui/theme/Color.kt`

### 修改方案

#### 文件: 修改 `MermaidRenderer.kt`

增强 `MermaidBlock` composable：

1. **添加状态变量**：
```kotlin
var showFullScreen by remember { mutableStateOf(false) }
var isZoomed by remember { mutableStateOf(false) }
```

2. **在图表下方添加操作栏** (Row + 2 个 IconButton)：
```
[🔍 放大] [📥 导出 PNG]
```
- 放大：切换 `showFullScreen` → 全屏 Dialog 显示相同 Mermaid HTML（更大 maxHeight）
- 导出 PNG：通过 WebView 的 `evaluateJavascript` 调用 Mermaid API 获取 SVG，转换为 PNG → 保存到系统相册

3. **全屏 Dialog**：
```kotlin
if (showFullScreen) {
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
            RichContentWebView(html = html, maxHeight = Int.MAX_VALUE)
            IconButton(
                onClick = { showFullScreen = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) { Icon(Icons.Close, ...) }
        }
    }
}
```

4. **PNG 导出功能** (复杂部分)：

在 HTML 模板中添加导出按钮的 JS 桥接。在 `buildMermaidHtml` 中注入以下 JS：
```javascript
function exportPNG() {
    var svg = document.querySelector('#mermaid-diagram svg');
    var serializer = new XMLSerializer();
    var svgStr = serializer.serializeToString(svg);
    // Pass to Android via JavascriptInterface
    Android.onSvgReady(svgStr);
}
```

在 Kotlin 端注册 JavaScriptInterface：
```kotlin
class MermaidExportInterface(private val onSvg: (String) -> Unit) {
    @JavascriptInterface
    fun onSvgReady(svgString: String) { onSvg(svgString) }
}
```

SVG → PNG 转换使用 Android `android.graphics` API：
```kotlin
// 1. Parse SVG with AndroidSVG or manual parser
// 2. Render to Bitmap via Canvas
// 3. Save to MediaStore
```

如果不想引入额外依赖，简化方案：用 WebView 的 `capturePicture()` + `Canvas.drawPicture()` 获取位图。

5. **双指缩放**（在全屏模式下）：
使用 Compose `detectTransformGestures` 包裹全屏 WebView。

6. **Mermaid HTML 模板增强**：
在 CSS 中添加缩放过渡效果：
```css
#mermaid-diagram { transition: transform 0.2s; transform-origin: top left; }
```

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 编译通过
2. 图表下方显示操作按钮 (放大 + 导出)
3. 点击放大进入全屏查看模式
4. 点击导出能保存 PNG 到设备相册
5. 全屏模式支持关闭
```

---

### 会话 S9: ECharts 图表增强

**目标**：为 ECharts 添加全屏查看、数据导出 CSV、交互手势。

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/renderer/EChartsRenderer.kt`

**依赖**：S1 (WebViewPool)

**预计行数**：~70 行修改

---

#### 会话 S9 提示词

```
## 任务：为 Nexara ECharts 添加交互增强

### 背景
当前 EChartsRenderer 仅渲染静态图表，无全屏、无数据导出、无交互手势。
对标 ECharts 官方网站的交互能力。

### 当前代码参考

先读取：
- `app/src/main/java/com/promenar/nexara/ui/renderer/EChartsRenderer.kt` (53 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebView.kt` (73 行)
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebViewPool.kt`

### 修改方案

修改 `EChartsBlock` composable，添加以下功能：

#### 1. 操作按钮栏
在 ECharts WebView 下方添加 Row：
```kotlin
var showFullScreen by remember { mutableStateOf(false) }

Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically
) {
    // 全屏按钮
    IconButton(onClick = { showFullScreen = true }) {
        Icon(Icons.Rounded.Fullscreen, "Fullscreen", tint = NexaraColors.OnSurfaceVariant)
    }
    // 导出 CSV 按钮
    IconButton(onClick = { exportCSV(optionJson) }) {
        Icon(Icons.Rounded.TableChart, "Export CSV", tint = NexaraColors.OnSurfaceVariant)
    }
    // 导出 PNG 按钮
    IconButton(onClick = { exportPNG(webView) }) {
        Icon(Icons.Rounded.Download, "Export PNG", tint = NexaraColors.OnSurfaceVariant)
    }
}
```

#### 2. 全屏 Dialog (同 S8 方案)
```kotlin
if (showFullScreen) {
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.95f))) {
            RichContentWebView(html = buildEChartsHtml(optionJson, fullScreen = true), maxHeight = Int.MAX_VALUE)
            IconButton(onClick = { showFullScreen = false }, modifier = Modifier.align(TopEnd).padding(16.dp)) {
                Icon(Icons.Close, ...)
            }
        }
    }
}
```

#### 3. CSV 导出功能
在 `EChartsRenderer.kt` 中实现 `exportCSV()` 函数：
```kotlin
private fun exportCSV(optionJson: String, context: Context) {
    // 解析 ECharts option JSON → 提取 series data
    val option = Json.parseToJsonElement(optionJson).jsonObject
    val series = option["series"]?.jsonArray ?: return
    val csv = buildString {
        series.forEach { s ->
            val data = s.jsonObject["data"]?.jsonArray ?: return@forEach
            data.forEach { appendLine(it.toString()) }
        }
    }
    // 通过 ShareSheet 或保存 CSV 文件
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    context.startActivity(intent)
}
```

#### 4. PNG 导出
通过 WebView `capturePicture()` 或 `snapshot()` API 获取当前图表截图：
```kotlin
private fun exportPNG(webView: WebView, context: Context) {
    val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    webView.draw(canvas)
    // Save to MediaStore
    // ... standard Android media save code
}
```

#### 5. HTML 模板增强
修改 `buildEChartsHtml()` 添加以下功能：
- ECharts `toolbox` 配置（内建保存图片、数据视图按钮）
- `dataZoom` 组件（允许滑动缩放数据范围）
- 响应式 `resize` 事件

```javascript
option.toolbox = {
    show: true,
    feature: {
        saveAsImage: { show: true, title: 'Save' },
        dataView: { show: true, title: 'Data', readOnly: true },
        restore: { show: true, title: 'Reset' }
    }
};
option.dataZoom = [{ type: 'inside' }, { type: 'slider' }];
```

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 编译通过
2. 图表下方显示操作按钮 (全屏 + CSV + PNG)
3. ECharts 内建 toolbox 在 WebView 中可用
4. 全屏模式正确渲染
5. CSV/PNG 导出可正常工作
```

---

### 会话 S10: PlantUML / Kroki 支持

**目标**：支持 ` ```plantuml ` 代码块的渲染，通过 Kroki.io API 或本地 PlantUML.js。

**创建文件**：
- `app/src/main/java/com/promenar/nexara/ui/renderer/PlantUmlRenderer.kt`

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt`

**依赖**：S1 (WebViewPool)

**预计行数**：~100 行新建 + ~5 行修改

---

#### 会话 S10 提示词

```
## 任务：为 Nexara 添加 PlantUML 图表支持

### 背景
当前仅支持 Mermaid 图表 (` ```mermaid `)。需要支持 PlantUML (` ```plantuml `)。
方案：通过 Kroki.io API (https://kroki.io) 将 PlantUML 代码转为 SVG，
然后在 WebView 中渲染。离线时降级为纯文本代码块。

### 当前代码参考

先读取：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt` — 理解 `splitRichSegments()` 和 `ContentSegment`
- `app/src/main/java/com/promenar/nexara/ui/renderer/RichContentWebViewPool.kt`
- `app/src/main/java/com/promenar/nexara/ui/renderer/MermaidRenderer.kt` — 参考类似实现
- `app/src/main/java/com/promenar/nexara/ui/theme/Color.kt`

### 需要创建的文件

#### 文件: `app/src/main/java/com/promenar/nexara/ui/renderer/PlantUmlRenderer.kt`

```kotlin
package com.promenar.nexara.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PlantUmlBlock(
    code: String,
    modifier: Modifier = Modifier
) {
    val html = buildPlantUmlHtml(code)
    RichContentWebView(
        html = html,
        modifier = modifier,
        minHeight = 100,
        maxHeight = 600
    )
}
```

核心实现：

1. **Kroki API 集成**：PlantUML 代码 → Kroki API → SVG
   ```kotlin
   private fun buildPlantUmlHtml(code: String): String {
       val encoded = encodeForKroki(code) // deflate + base64url
       val krokiUrl = "https://kroki.io/plantuml/svg/$encoded"
       // 将 SVG URL 嵌入 HTML，用 fetch 动态加载
       return """
       <!DOCTYPE html>
       <html><head><meta charset="UTF-8"></head>
       <body style="background:transparent; margin:0; padding:12px; display:flex; justify-content:center;">
           <img id="diagram" style="max-width:100%;" src="" />
           <script>
               fetch('$krokiUrl')
                   .then(r => r.text())
                   .then(svg => {
                       document.getElementById('diagram').src = 'data:image/svg+xml;base64,' + btoa(svg);
                   })
                   .catch(e => {
                       document.getElementById('diagram').alt = 'Failed to load diagram: ' + e;
                   });
           </script>
       </body></html>
       """.trimIndent()
   }
   ```

2. **Kroki 编码函数** (deflate + base64url)：
   ```kotlin
   private fun encodeForKroki(source: String): String {
       // Use java.util.zip.Deflater + Base64 URL-safe encoding
       val deflater = java.util.zip.Deflater(9)
       deflater.setInput(source.toByteArray(Charsets.UTF_8))
       deflater.finish()
       val output = ByteArray(32768)
       val size = deflater.deflate(output)
       deflater.end()
       
       // Base64 URL-safe without padding
       return android.util.Base64.encodeToString(
           output.copyOf(size),
           android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
       )
   }
   ```

3. **离线降级**：如果 Kroki 请求失败，显示原始 PlantUML 代码：
   在 HTML 中添加 fallback 逻辑

### 需要修改的文件

#### 修改 `MarkdownText.kt` 的 `splitRichSegments()` (第 50 行)

在 blockPattern regex 中添加 `plantuml`：
```kotlin
// 将第 50-53 行:
val blockPattern = Regex(
    """```(mermaid|echarts)\s*\n(.*?)```""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)
// 改为:
val blockPattern = Regex(
    """```(mermaid|echarts|plantuml)\s*\n(.*?)```""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)
```

在 `ContentSegment` sealed class (第 35-40 行) 中添加：
```kotlin
data class PlantUml(val content: String) : ContentSegment()
```

在 when 分支 (第 63-66 行) 添加：
```kotlin
"plantuml" -> ContentSegment.PlantUml(content)
```

在渲染分发 when 分支 (约第 169-228 行) 添加：
```kotlin
is ContentSegment.PlantUml -> {
    PlantUmlBlock(code = segment.content)
}
```

添加 import:
```kotlin
import com.promenar.nexara.ui.renderer.PlantUmlBlock
```

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 编译通过
2. ` ```plantuml ` 代码块被识别并发送到 Kroki API
3. Kroki 返回的 SVG 在 WebView 中正确渲染
4. 离线/请求失败时显示原始 PlantUML 代码（降级）
```

---

## Wave 3 — 集成收尾 (依赖 Wave 1+2)

### 会话 S11: MarkdownText 集成重构

**目标**：整合 S2 (表格)、S4 (图片 Lightbox)、S7 (内联数学) 的所有变更到 `MarkdownText.kt`，
确保无冲突，统一所有 markdownComponents 注入。

**修改文件**：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt`

**依赖**：S1 (WebViewPool)、S2 (表格)、S7 (内联数学)

**预计行数**：~30 行修改

---

#### 会话 S11 提示词

```
## 任务：整合 MarkdownText.kt 所有渲染增强

### 背景
多个并行会话分别修改了 MarkdownText.kt 的不同部分：
- S2 添加了 table 组件注入
- S4 添加了 image 组件注入 (Lightbox)
- S7 添加了 InlineLatex content segment + 渲染分支
- S10 添加了 PlantUml content segment + 渲染分支

需要整合所有变更到最终版本，解决可能的合并冲突，统一 imports 和组件注册。

### 当前代码参考

先读取：
- `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt` — 当前状态（可能已被部分修改）
- `app/src/main/java/com/promenar/nexara/ui/renderer/TableWidget.kt` — S2 创建的组件
- `app/src/main/java/com/promenar/nexara/ui/renderer/InlineLatexSpan.kt` — S7 创建的组件
- `app/src/main/java/com/promenar/nexara/ui/renderer/PlantUmlRenderer.kt` — S10 创建的组件
- `app/src/main/java/com/promenar/nexara/ui/renderer/ImageLightbox.kt` — S4 创建的组件

### 整合检查清单

1. **ContentSegment sealed class** (第 35-40 行)：确认包含所有类型：
   ```kotlin
   private sealed class ContentSegment {
       data class Markdown(val content: String) : ContentSegment()
       data class Latex(val content: String) : ContentSegment()
       data class InlineLatex(val content: String) : ContentSegment()  // S7
       data class Mermaid(val content: String) : ContentSegment()
       data class ECharts(val content: String) : ContentSegment()
       data class PlantUml(val content: String) : ContentSegment()     // S10
   }
   ```

2. **splitRichSegments()** (第 49-95 行)：
   - blockPattern regex 包含 `mermaid|echarts|plantuml`
   - 内联数学检测逻辑正确添加
   - inlineLatex spans 不与 displayLatex spans 重叠

3. **mergedSegments 逻辑** (第 123-166 行)：
   - 确认 InlineLatex 的合并逻辑（通常不合并，每个内联公式独立渲染）
   - 确认 PlantUml 的合并逻辑（类似 Mermaid 的处理方式）

4. **markdownComponents 注入** (第 177-207 行)：
   确认所有自定义组件已注册：
   ```kotlin
   components = markdownComponents(
       codeFence = { ... },
       codeBlock = { ... },
       table = { model ->
           NexaraTableWidget(table = model, modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth())
       },
       image = { model ->
           var showLightbox by remember { mutableStateOf(false) }
           // Default Coil image rendering + click → lightbox
       },
   ```

5. **渲染分发 when 分支** (约第 169-228 行)：
   确认包含所有 segment 类型：
   ```kotlin
   when (segment) {
       is ContentSegment.Markdown -> { /* Markdown(...) */ }
       is ContentSegment.Latex -> { LatexBlock(...) }
       is ContentSegment.InlineLatex -> { /* InlineLatexSpan 或嵌入方案 */ }
       is ContentSegment.Mermaid -> { MermaidBlock(...) }
       is ContentSegment.ECharts -> { EChartsBlock(...) }
       is ContentSegment.PlantUml -> { PlantUmlBlock(...) }
   }
   ```

6. **imports 清理**：移除未使用的 import，确保所有新组件 import 存在

7. **编译验证**：
   ```bash
   ./gradlew :app:compileDebugKotlin
   ```

### 验收标准
1. `./gradlew :app:compileDebugKotlin` 零错误零警告编译通过
2. 所有 imports 无未使用警告
3. splitRichSegments 正确处理：纯文本、代码块、表格、`$$`, `$`, ` ```mermaid `, ` ```echarts `, ` ```plantuml `
4. 所有自定义组件正确注入到 markdownComponents
```

---

## 执行顺序总结

```
第 1 天 (并行启动 6 个会话):
  S1: WebView 池化          ← 0 依赖
  S2: 自定义表格            ← 0 依赖
  S3: 代码块行号            ← 0 依赖
  S4: 图片 Lightbox         ← 0 依赖
  S5: CJK 排版优化          ← 0 依赖
  S6: 无障碍支持            ← 0 依赖

第 2 天 (等 S1 完成后，并行启动 4 个会话):
  S7: 内联数学渲染          ← 依赖 S1
  S8: Mermaid 增强          ← 依赖 S1
  S9: ECharts 增强          ← 依赖 S1
  S10: PlantUML 支持        ← 依赖 S1

第 3 天 (等 S1+S2+S7+S10 完成后):
  S11: MarkdownText 集成    ← 依赖 S1, S2, S7, S10
```

### 冲突预防
- S2、S4、S7、S10 都修改 `MarkdownText.kt` → 使用不同的 inject 点（各自独立）→ S11 负责最终合并
- S1 和 S8/S9/S10 都涉及 `RichContentWebView` → S1 先完成，S8/S9 在已有池基础上构建
- S5 修改 `Type.kt` 和 `NexaraMarkdownTheme.kt` → 独立于所有其他会话

### 每个会话结束后的验证命令
```bash
./gradlew :app:compileDebugKotlin
```

如果编译失败，使用 `./gradlew :app:compileDebugKotlin 2>&1 | head -50` 查看错误。
