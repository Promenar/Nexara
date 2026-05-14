<!-- AGENT INSTRUCTION: 阅读本文档后严格按步骤执行，不要自由发挥。完成后按验收标准逐项确认。 -->

# MD-S2: 代码块增强（语法高亮 + 复制按钮 + 语言标签）

> **目标**: 为代码块添加语法高亮、顶部语言标签栏和复制到剪贴板按钮  
> **预估**: 20 分钟 | **依赖**: MD-S1 已完成

---

## 步骤 1: 添加 Code 高亮依赖

**文件**: `app/build.gradle.kts`  
**位置**: Markdown 渲染依赖块后追加

```kotlin
implementation("com.mikepenz:multiplatform-markdown-renderer-code:$markdownRendererVersion")
```

## 步骤 2: 创建自定义代码块组件

**新建文件**: `app/src/main/java/com/promenar/nexara/ui/renderer/CodeBlockHeader.kt`

功能需求：
- 顶部 Row：左侧显示语言名称（如 `kotlin`、`python`），右侧显示复制图标按钮
- 点击复制按钮 → `ClipboardManager` 复制代码内容，按钮切换为 ✓ 图标 2 秒后恢复
- 底部为高亮代码区域
- 整体圆角 `NexaraShapes.medium`，背景 `NexaraColors.SurfaceLowest`
- Header 背景 `NexaraColors.SurfaceContainer`

```kotlin
package com.promenar.nexara.ui.renderer

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraShapes
import com.promenar.nexara.ui.theme.NexaraTypography
import kotlinx.coroutines.delay

@Composable
fun CodeBlockWithHeader(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
    codeContent: @Composable () -> Unit  // 高亮代码内容 slot
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) { delay(2000); copied = false }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NexaraShapes.medium)
            .background(NexaraColors.SurfaceLowest)
    ) {
        // Header: 语言标签 + 复制按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexaraColors.SurfaceContainer)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language?.uppercase() ?: "CODE",
                style = NexaraTypography.labelMedium.copy(fontSize = 11.sp),
                color = NexaraColors.OnSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(code))
                    copied = true
                },
                modifier = Modifier.size(28.dp)
            ) {
                AnimatedContent(targetState = copied, label = "copy") { isCopied ->
                    Icon(
                        imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = if (isCopied) "Copied" else "Copy",
                        tint = if (isCopied) NexaraColors.StatusSuccess else NexaraColors.OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        // 代码体
        Box(modifier = Modifier.padding(16.dp)) {
            codeContent()
        }
    }
}
```

## 步骤 3: 将自定义代码块注入 Markdown 组件

**修改文件**: `ui/common/MarkdownText.kt`

更新 `Markdown()` 调用，注入自定义代码块渲染器：

```kotlin
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.promenar.nexara.ui.renderer.CodeBlockWithHeader

// 在 MarkdownText composable 中：
Markdown(
    content = markdown,
    colors = nexaraMarkdownColors(),
    typography = nexaraMarkdownTypography(),
    components = markdownComponents(
        codeBlock = { /* 自定义代码块 */
            CodeBlockWithHeader(
                code = it.content,
                language = it.language,
            ) {
                MarkdownHighlightedCodeBlock(
                    content = it.content,
                    language = it.language,
                )
            }
        },
        codeFence = { /* 自定义代码围栏 */
            CodeBlockWithHeader(
                code = it.content,
                language = it.language,
            ) {
                MarkdownHighlightedCodeFence(
                    content = it.content,
                    language = it.language,
                )
            }
        }
    ),
    modifier = Modifier.fillMaxWidth()
)
```

> **关键**: `markdownComponents`、`MarkdownHighlightedCodeBlock`、`MarkdownHighlightedCodeFence` 的实际 API 签名以 0.40.2 源码为准。构建前先用 IDE 检查 `it` 的属性名（可能是 `content`/`code`、`language`/`lang`）。

## 验收标准

1. 编译通过
2. `` ```kotlin ... ``` `` 代码块顶部显示 `KOTLIN` 标签和复制按钮
3. 代码内容有语法着色（关键字、字符串、注释等）
4. 点击复制按钮 → 图标变为 ✓ → 2 秒后恢复 → 剪贴板中有代码

## DIA

- 更新 `.agent/handover.md`：记录 MD-S2 完成


