# Markdown 富文本渲染引擎大修计划

> **创建**: 2026-05-10 | **状态**: 规划中  
> **前置审计**: 综合评分 0.4/10，ChatBubble 未接入 MarkdownText

## 技术选型决策

| 需求 | 选型 | 版本 | 理由 |
|:---|:---|:---|:---|
| Markdown AST 解析 + Compose 渲染 | `mikepenz/multiplatform-markdown-renderer` | 0.40.2 | Compose 原生、M3 主题、内置表格/图片、CMP 就绪 |
| 代码语法高亮 | `multiplatform-markdown-renderer-code` | 0.40.2 | 配套模块，含语言标签+复制按钮 |
| 图片加载 | `coil3`（已集成） | 3.0.0 | 项目已有依赖 |
| LaTeX 数学公式 | WebView + KaTeX | 0.16.x | 轻量、移动端首选 |
| Mermaid 图表 | WebView + Mermaid.js | 11.x | 官方方案 |
| ECharts 图表 | WebView + ECharts | 5.x | DeepSeek 同方案 |

## 会话拆分总览

| 会话 | 任务 | 预估时间 | 依赖 |
|:---|:---|:---|:---|
| **MD-S1** | 依赖集成 + MarkdownText 重写 + ChatBubble 接入 | 30min | 无 |
| **MD-S2** | 代码块增强（语法高亮 + 复制 + 语言标签） | 20min | S1 |
| **MD-S3** | WebView 沙箱基座 + LaTeX 渲染 | 30min | S1 |
| **MD-S4** | Mermaid + ECharts 渲染（复用 S3 基座） | 25min | S3 |
| **MD-S5** | 流式渲染优化 + ThinkingBlock 接入 | 20min | S1 |

## 关键文件清单

```
修改文件:
├── app/build.gradle.kts                          # 添加依赖
├── ui/common/MarkdownText.kt                     # 重写为 mikepenz 封装
├── ui/chat/ChatScreen.kt                         # ChatBubble 接入
新增文件:
├── ui/renderer/NexaraMarkdownTheme.kt            # M3 主题映射
├── ui/renderer/RichContentWebView.kt             # WebView 沙箱基座
├── ui/renderer/LatexRenderer.kt                  # LaTeX 块检测+渲染
├── ui/renderer/MermaidRenderer.kt                # Mermaid 块检测+渲染
├── ui/renderer/EChartsRenderer.kt                # ECharts 块检测+渲染
├── ui/renderer/CodeBlockHeader.kt                # 代码块头部（语言+复制）
├── app/src/main/assets/katex/                    # KaTeX 离线资源
├── app/src/main/assets/mermaid/                  # Mermaid.js 离线资源
├── app/src/main/assets/echarts/                  # ECharts 离线资源
```

---

*各会话详细实施方案见独立文件 `MD-S1` ~ `MD-S5`*
