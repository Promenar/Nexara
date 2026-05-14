<!-- AGENT INSTRUCTION: 阅读本文档后严格按步骤执行，不要自由发挥。完成后按验收标准逐项确认。 -->

# MD-S1: 依赖集成 + MarkdownText 重写 + ChatBubble 接入

> **目标**: 用 `mikepenz/multiplatform-markdown-renderer` 替换手写解析器，并将 ChatBubble 从纯文本切换到 Markdown 渲染  
> **预估**: 30 分钟 | **依赖**: 无

---

## 步骤 1: 添加 Gradle 依赖

**文件**: `app/build.gradle.kts`  
**位置**: 在 `// ─── Markdown 渲染 ───` 注释后（第 110-111 行）

```kotlin
// ─── Markdown 渲染 ───
val markdownRendererVersion = "0.40.2"
implementation("com.mikepenz:multiplatform-markdown-renderer-m3:$markdownRendererVersion")
implementation("com.mikepenz:multiplatform-markdown-renderer-coil3:$markdownRendererVersion")
```

删除原来的 `// TODO: 迁移阶段按需添加 Markdown 渲染库` 注释。

## 步骤 2: 创建 Nexara Markdown 主题映射

**新建文件**: `app/src/main/java/com/promenar/nexara/ui/renderer/NexaraMarkdownTheme.kt`

```kotlin
package com.promenar.nexara.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraTypography

@Composable
fun nexaraMarkdownColors(): MarkdownColors = markdownColor(
    text = NexaraColors.OnBackground,
    codeText = NexaraColors.OnSurface,
    codeBackground = NexaraColors.SurfaceLowest,
    dividerColor = NexaraColors.OutlineVariant,
    linkText = NexaraColors.Primary,
    inlineCodeText = NexaraColors.OnSurface,
    inlineCodeBackground = NexaraColors.SurfaceHigh,
    tableText = NexaraColors.OnSurface,
    tableBorderColor = NexaraColors.OutlineVariant,
    tableHeaderBackground = NexaraColors.SurfaceContainer,
)

@Composable
fun nexaraMarkdownTypography(): MarkdownTypography = markdownTypography(
    h1 = NexaraTypography.headlineLarge,
    h2 = NexaraTypography.headlineMedium,
    h3 = NexaraTypography.headlineMedium.copy(fontSize = 18.sp),
    h4 = NexaraTypography.headlineMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    h5 = NexaraTypography.headlineMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    h6 = NexaraTypography.headlineMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    body1 = NexaraTypography.bodyMedium,
    body2 = NexaraTypography.bodySmall,
    code = NexaraTypography.bodySmall.copy(fontFamily = FontFamily.Monospace),
)
```

> **注意**: `markdownColor` 和 `markdownTypography` 的参数名以 mikepenz 0.40.2 实际 API 为准。构建前用 IDE 自动补全确认参数名，如 API 有差异则根据实际签名调整。

## 步骤 3: 重写 MarkdownText 组件

**文件**: `app/src/main/java/com/promenar/nexara/ui/common/MarkdownText.kt`  
**操作**: 完全重写文件内容

```kotlin
package com.promenar.nexara.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.promenar.nexara.ui.renderer.nexaraMarkdownColors
import com.promenar.nexara.ui.renderer.nexaraMarkdownTypography
import com.promenar.nexara.ui.theme.NexaraColors

@Composable
fun MarkdownText(
    markdown: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (markdown.isNotBlank()) {
            Markdown(
                content = markdown,
                colors = nexaraMarkdownColors(),
                typography = nexaraMarkdownTypography(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isStreaming) {
            StreamingCursor()
        }
    }
}

@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Box(
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp)
            .width(8.dp)
            .height(16.dp)
            .alpha(alpha)
            .background(NexaraColors.Primary, RoundedCornerShape(1.dp))
    )
}
```

## 步骤 4: ChatBubble 接入 MarkdownText

**文件**: `app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt`

### 4a. 添加 import

在文件顶部 import 区域添加：

```kotlin
import com.promenar.nexara.ui.common.MarkdownText
```

### 4b. 修改助手消息渲染

**定位**: `ChatBubble` 函数中 `} else {` 分支内（约第 453-459 行）

将：
```kotlin
if (message.content.isNotBlank() || isGenerating) {
    Text(
        text = message.content,
        style = NexaraTypography.bodyMedium,
        color = NexaraColors.OnBackground,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
```

替换为：
```kotlin
if (message.content.isNotBlank() || isGenerating) {
    MarkdownText(
        markdown = message.content,
        isStreaming = isGenerating,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
```

### 4c. 移除重复的流式光标

由于 `MarkdownText` 内部已包含 `StreamingCursor`，删除 ChatBubble 中第 462-480 行的两个光标 Box：

```kotlin
// 删除以下整段代码（约第 462-480 行）
if (isGenerating && message.content.isEmpty()) {
    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .width(12.dp)
            .height(16.dp)
            .alpha(cursorAlpha)
            .background(NexaraColors.Primary, RoundedCornerShape(2.dp))
    )
} else if (isGenerating) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp)
            .width(12.dp)
            .height(16.dp)
            .alpha(cursorAlpha)
            .background(NexaraColors.Primary, RoundedCornerShape(2.dp))
    )
}
```

同时，删除 ChatBubble 顶部现在不再需要的 `infiniteTransition` 和 `cursorAlpha` 变量（第 413-422 行）：

```kotlin
// 删除这段
val infiniteTransition = rememberInfiniteTransition(label = "cursor")
val cursorAlpha by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(530),
        repeatMode = RepeatMode.Reverse
    ),
    label = "cursorAlpha"
)
```

## 验收标准

1. `./gradlew compileDebugKotlin` 编译通过
2. AI 助手回复中 `**粗体**`、`# 标题`、`- 列表`、`` `代码` ``、`[链接](url)` 正确渲染
3. GFM 表格 `| col | col |` 正确渲染为网格
4. 引用块 `>` 渲染为左侧竖线缩进样式
5. 流式生成时尾部显示呼吸光标
6. 用户消息气泡保持纯文本（不渲染 Markdown）

## DIA

- 更新 `.agent/handover.md`：记录 MD-S1 完成
- 无需更新其他文档（不涉及架构/API 变更）


