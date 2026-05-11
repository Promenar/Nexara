# Nexara Native 渲染系统审计报告

> 日期：2026-05-11
> 版本：v1.0
> 审计范围：Markdown 渲染管线 + 多维可视化组件 + 流式性能 + 视觉规范

---

## 1. 当前架构概览

```
ChatScreen.kt (line 483)
  └─> MarkdownText.kt (270 lines)  ← 中央编排器
        ├── splitRichSegments()     ← Regex 内容类型切割
        │     ├── ```mermaid → ContentSegment.Mermaid
        │     ├── ```echarts → ContentSegment.ECharts
        │     ├── $$...$$ → ContentSegment.Latex
        │     └── 其余 → ContentSegment.Markdown
        ├── ParseCache              ← 流式增量解析缓存 (阈值 100 字符)
        ├── sanitizeStreamingMarkdown() ← 关闭未闭合的 fences/$$
        └── 渲染分发：
              ├── Markdown → mikepenz multiplatform-markdown-renderer-m3 v0.40.2
              │     ├── codeFence/codeBlock → CodeBlockWithHeader (92 lines)
              │     └── 其余 → mikepenz 内建组件
              ├── Latex → LatexBlock → RichContentWebView → KaTeX v0.16
              ├── Mermaid → MermaidBlock → RichContentWebView → Mermaid.js v11+
              └── ECharts → EChartsBlock → RichContentWebView → Apache ECharts v5+
```

### 依赖库

| 库 | 版本 | 用途 |
|----|------|------|
| `com.mikepenz:multiplatform-markdown-renderer-m3` | 0.40.2 | GFM Markdown 渲染 |
| `com.mikepenz:multiplatform-markdown-renderer-coil3` | 0.40.2 | Markdown 内图片 (Coil 3) |
| `com.mikepenz:multiplatform-markdown-renderer-code` | 0.40.2 | 代码语法高亮 |
| Coil 3 (`io.coil-kt.coil3:coil-compose`) | 3.0.0 | 图片加载 |
| KaTeX (assets) | v0.16 | LaTeX 数学渲染 (WebView) |
| Mermaid.js (assets) | v11+ | 图表渲染 (WebView) |
| Apache ECharts (assets) | v5+ | 数据可视化 (WebView) |

### 关键文件清单

| 文件 | 行数 | 职责 |
|------|------|------|
| `ui/common/MarkdownText.kt` | 270 | 内容切割 + 渲染分发 + 流式优化 |
| `ui/renderer/NexaraMarkdownTheme.kt` | 34 | Markdown 颜色/排版适配 |
| `ui/renderer/CodeBlockHeader.kt` | 92 | 代码块头部 + 复制按钮 |
| `ui/renderer/RichContentWebView.kt` | 73 | 共享 WebView 容器 |
| `ui/renderer/LatexRenderer.kt` | 61 | KaTeX 渲染 (仅 `$$...$$`) |
| `ui/renderer/MermaidRenderer.kt` | 64 | Mermaid 图表渲染 |
| `ui/renderer/EChartsRenderer.kt` | 53 | ECharts 图表渲染 |
| `ui/theme/Type.kt` | 51 | 排版定义 (Manrope/Inter/SpaceGrotesk) |
| `ui/theme/Color.kt` | 62 | 色彩系统 (深色 Only) |
| `ui/theme/Theme.kt` | 84 | MaterialTheme 包装 (深色 Only) |
| `ui/chat/ChatScreen.kt` | 714 | 聊天界面 (含 ChatBubble → MarkdownText) |

---

## 2. 渲染能力差距分析表

### A. 解析能力

| # | 能力 | 状态 | 等级 | 详情 |
|---|------|------|------|------|
| A1 | GFM 标准 Markdown | 基本完整 | 🟡 | mikepenz 覆盖大部分 GFM。未明确验证：任务列表 (`- [ ]`)、脚注、删除线、定义列表。 |
| A2 | 表格渲染 | 委托给库 | 🔴 | 无自定义表格组件。mikepenz 内建表格无水平滚动。在窄屏手机上宽表格溢出或被裁剪。 |
| A3 | 代码块 | 良好 | 🟢 | 语法高亮 + 语言标签 + 复制按钮 (2s 动画确认)。缺少行号。 |
| A4 | 内联代码 | 基本 | 🟢 | mikepenz 内建，背景色 SurfaceHigh。 |
| A5 | LaTeX 显示数学 (`$$`) | 存在 | 🟡 | KaTeX WebView，但每个块创建新 WebView (内存浪费)。 |
| A6 | LaTeX 内联数学 (`$`) | **缺失** | 🔴 | `splitRichSegments()` regex 仅匹配 `$$...$$`。`$...$` 被当作纯文本渲染。 |
| A7 | Mermaid 图表 | 存在 | 🟡 | WebView 渲染。缺少缩放、导出、错误恢复 UI。 |
| A8 | ECharts | 存在 | 🟡 | WebView 渲染。缺少交互、数据导出。 |
| A9 | PlantUML / D2 / Graphviz | **缺失** | 🔴 | 仅支持 Mermaid。 |
| A10 | SVGO 渲染 | **缺失** | 🟡 | 图片通过 Coil 加载，无内联 SVG 支持。 |

### B. 流式性能

| # | 能力 | 状态 | 等级 | 详情 |
|---|------|------|------|------|
| B1 | 流式增量解析 | 良好 | 🟢 | `ParseCache` + `RE_PARSE_THRESHOLD=100` 避免完整重解析。 |
| B2 | 不完整语法保护 | 良好 | 🟢 | `sanitizeStreamingMarkdown()` 关闭未闭合的 fences 和 `$$`。 |
| B3 | 重组优化 | 最小 | 🟡 | 无 `@Stable`/`@Immutable` 标注。每个 `Markdown(...)` 在流式时可能触发完整重组。 |
| B4 | 长文本性能 | 未优化 | 🟡 | 无文本分块、无虚拟化。全文传递给 mikepenz 解析器。 |
| B5 | WebView 创建开销 | **严重** | 🔴 | 每个 LaTeX/Mermaid/ECharts 块创建一个新 WebView。10 个等式 = 10 个 WebView。 |
| B6 | 流式光标 | 良好 | 🟢 | 脉冲动画条 (8x16dp, alpha 1.0↔0.2)。 |

### C. 视觉规范

| # | 能力 | 状态 | 等级 | 详情 |
|---|------|------|------|------|
| C1 | CJK 字体排版 | 基本 | 🟡 | `FontFamily.SansSerif`（无自定义 CJK 字体）。Inter/Manrope 为拉丁字体。 |
| C2 | 行高优化 | 可接受 | 🟡 | bodyMedium 15sp/25sp 对 CJK 可接受。标题行高偏紧 (h1: 24sp/32sp)。 |
| C3 | 字间距 | **缺失** | 🟡 | `letterSpacing` 全为 0。CJK 文本建议 +0.01~0.02sp。 |
| C4 | 深色模式代码高亮 | 良好 | 🟢 | mikepenz 尊重 Material 3 深色主题。 |
| C5 | 表格深色主题 | 基本 | 🟡 | `tableBackground = SurfaceContainer` 已设置，但无边框/行交替色。 |
| C6 | 可访问性 (TalkBack) | **缺失** | 🔴 | 渲染的 markdown 元素上无 `contentDescription`、无语义标注。 |

### D. 交互组件

| # | 能力 | 状态 | 等级 | 详情 |
|---|------|------|------|------|
| D1 | 图片点击放大 | **缺失** | 🔴 | 对标 ChatGPT iOS 的全屏图片查看器。 |
| D2 | 图表缩放/全屏 | **缺失** | 🔴 | Mermaid/ECharts 无交互手势。 |
| D3 | 图表导出/分享 | **缺失** | 🟡 | 无 PNG/SVG 导出。 |
| D4 | 消息 Diff 视图 | **缺失** | 🟡 | 编辑后消息显示为原始文本。 |
| D5 | 视频/音频嵌入 | **缺失** | 🟡 | Coil video 已注册但未用于 markdown 嵌入。 |

---

## 3. 架构建议

### 推荐：原生 Compose + 策略性 WebView 混合架构

| 层面 | 方案 | 理由 |
|------|------|------|
| 标准 Markdown (GFM) | **保留 mikepenz (100% 原生 Compose)** | 成熟稳定，通过 `markdownComponents` 可注入自定义组件。 |
| 表格 | **原生 Compose `LazyRow` + 自定义 `TableLayout`** | 最紧迫问题。通过 `markdownComponents(table = ...)` 注入原生渲染。 |
| 代码块 | **增强现有原生方案** | 添加行号、语言图标。 |
| LaTeX 数学 | **WebView 池化 + 内联位图方案** | KaTeX 质量无可替代。池化消除重复创建开销。 |
| Mermaid / ECharts | **WebView 池化 + 交互增强** | 保留 JS 引擎渲染能力，添加缩放/导出。 |
| PlantUML 等 | **Kroki.io 代理 + WebView 池** | 无需捆绑更多 JS 库。离线时降级为纯文本代码块。 |

### 为什么不是纯 WebView？为什么不是纯原生？

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| 纯 WebView | 一行代码部署 | Android WebView ≈ 80-150MB 基线内存，滚动嵌套问题，丢失原生手势 | **否决** |
| 纯原生 | Compose 性能，60fps | 重写 KaTeX 解析器 ≈ 数周，Mermaid 布局引擎极复杂 | **否决用于图表/数学** |
| **混合** | 最大杠杆 | 维护 WebView 池 + JS↔Kotlin 互操作层 | **采纳** |

---

## 4. 与 ChatGPT iOS/Android 对标

| 功能 | ChatGPT | Nexara 当前 | 差距 |
|------|---------|------------|------|
| GFM Markdown | ✅ | ✅ | 0% |
| LaTeX 内联 | ✅ | ❌ | 100% |
| LaTeX 显示 | ✅ | ✅ (无池化) | 30% |
| 代码块 | ✅ 复制+行号 | ✅ 复制 / ❌ 行号 | 30% |
| 表格 | ✅ 水平滚动 | ❌ | 100% |
| 图片查看器 | ✅ 全屏+缩放 | ❌ | 100% |
| 图表 (Mermaid/ECharts) | ❌ (不存在) | ✅ | **领先** |
| 流式渲染 | ✅ | ✅ | 0% |
| CJK 字体 | ✅ (系统字体) | ⚠️ 拉丁回退 | 30% |
| 深色模式 | ✅ | ✅ | 0% |
| 可访问性 | ✅ TalkBack | ❌ | 100% |
| 思考块 | ✅ (折叠) | ✅ (折叠) | 0% |
