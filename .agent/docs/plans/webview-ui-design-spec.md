# WebView 聊天渲染器 — UI 设计规范

> **版本**: v1.0 (2026-04-28) | **用途**: 前端设计工具参考
> **技术栈**: React 19 + CSS Variables + react-markdown + KaTeX + Prism.js
> **运行环境**: React Native WebView 容器内，移动端优先

---

## 1. 整体架构

### 1.1 容器结构

```
┌──────────────────────────────────────┐
│  React Native GlassHeader (原生导航栏) │
├──────────────────────────────────────┤
│  WebView (flex: 1)                   │
│  ┌──────────────────────────────────┐│
│  │  .message-list                   ││
│  │  height: 100dvh                  ││
│  │  overflow-y: auto                ││
│  │  padding: 16px 8px               ││
│  │  padding-bottom: 80px            ││
│  │                                  ││
│  │  [Message Row - User]            ││
│  │  [Message Row - Assistant]       ││
│  │  [Message Row - Assistant]       ││
│  │  ...                             ││
│  │                                  ││
│  │  [stream-pulse 底部渐变]          ││
│  └──────────────────────────────────┘│
│                                      │
│  [scroll-to-bottom 悬浮按钮]          │
│  position: fixed, bottom: 90px       │
│                                      │
├──────────────────────────────────────┤
│  ChatInput (RN 原生输入栏，非 WebView)  │
└──────────────────────────────────────┘
```

### 1.2 消息行布局

**User 消息**：
```
┌───────────────────────────────────┐
│                    ┌─────────────┐ │
│                    │ bubble-user │ │  ← 右对齐，max-width: 85%
│                    │   你         │ │  ← 标签
│                    │   内容...    │ │
│                    │   [操作按钮] │ │
│                    └─────────────┘ │
└───────────────────────────────────┘
padding-inline: 16px
```

**Assistant 消息**：
```
┌───────────────────────────────────┐
│ ┌───────────────────────────────┐ │
│ │ bubble-assistant              │ │  ← 左对齐，width: 100%
│ │ [RAG 指示器]                   │ │  ← 可选，30px高
│ │ [审批卡片]                     │ │  ← 可选
│ │ 内容...                        │ │
│ │ [推理折叠]                     │ │  ← 可选
│ │ [工具时间线]                   │ │  ← 可选
│ │ [任务监控]                     │ │  ← 可选
│ │ [记忆处理]                     │ │  ← 可选
│ │ [流式动画]                     │ │  ← 生成中
│ │ [操作按钮]                     │ │
│ └───────────────────────────────┘ │
└───────────────────────────────────┘
padding-inline: 16px
```

---

## 2. 主题系统

### 2.1 CSS 变量完整定义

所有颜色通过 CSS 变量控制，由 RN 侧通过 Bridge 动态注入：

```css
:root {
  /* 背景 */
  --bg-primary: #ffffff;
  --bg-secondary: #f4f4f5;
  --bg-tertiary: #e4e4e7;

  /* 文本 */
  --text-primary: #09090b;
  --text-secondary: #71717a;
  --text-tertiary: #a1a1aa;

  /* 边框 */
  --border-default: #e4e4e7;
  --border-subtle: #f4f4f5;
  --border-glass: rgba(0,0,0,0.08);

  /* 气泡 */
  --bubble-user-bg: rgba(244,244,245,0.6);
  --bubble-user-border: #e5e7eb;
  --bubble-assistant-bg: transparent;

  /* 代码块 */
  --code-bg: rgba(0,0,0,0.02);
  --code-border: rgba(0,0,0,0.08);
  --code-header-bg: rgba(0,0,0,0.02);
  --code-inline-bg: rgba(0,0,0,0.05);

  /* 强调色（可配置，默认 Indigo） */
  --accent: #6366f1;
  --accent-50 到 --accent-900: 色阶渐变;

  /* 状态色 */
  --color-success: #10b981;
  --color-error: #ef4444;
  --color-warning: #f59e0b;

  /* 排版 */
  --font-sans: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  --font-mono: 'SF Mono', 'Fira Code', 'Courier New', monospace;
  --font-size-xs: 10px;
  --font-size-sm: 13px;
  --font-size-base: 15px;
  --line-height: 25px;

  /* 圆角 */
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
}
```

### 2.2 暗色模式

通过 `document.documentElement.setAttribute('data-theme', 'dark')` 切换。所有变量由 RN 侧的 `applyTheme()` 函数覆盖为暗色值。

---

## 3. 组件清单与视觉规格

### 3.1 消息气泡

| 属性 | User | Assistant |
|------|------|-----------|
| max-width | 85% | 100% |
| min-width | 60px | 60px |
| background | `var(--bubble-user-bg)` | transparent |
| border-radius | 16px (top-right: 4px 尾巴) | 无 |
| border | 1px solid `var(--bubble-user-border)` | 无 |
| padding | 16px | 无 |
| 对齐 | flex-end (右对齐) | flex-start (左对齐) |
| 标签 | "你" (12px, 600, text-tertiary) | 无 |

### 3.2 Markdown 排版

```css
.markdown-body {
  font-size: 15px;
  line-height: 25px;
}
p { margin-bottom: 8px; }
h1 { 1.5em, 700, border-bottom }
h2 { 1.25em, 600, border-bottom }
h3 { 1.1em, 600 }
ul, ol { padding-left: 20px }
blockquote { bg-secondary, border-left: 3px accent, radius: 8px }
code(inline) { bg: code-inline-bg, padding: 2px 4px, radius: 4px }
```

### 3.3 代码块

```
┌─────────────────────────────────────┐
│ TYPESCRIPT                    [复制] │  ← header bar
│ bg: code-header-bg, 9px badge, 12px
├─────────────────────────────────────┤
│                                     │
│  Prism.js 语法高亮代码               │  ← 代码区
│  font-family: mono, 13px           │
│  bg: code-bg                       │
│                                     │
└─────────────────────────────────────┘
border: 1px solid code-border
border-radius: 12px
overflow: hidden
```

### 3.4 KaTeX 数学公式

- 行内公式 `$E=mc^2$` → KaTeX 内联渲染，`font-size: 1.1em`
- 块级公式 `$$...$$` → KaTeX display 模式，`margin: 10px 0, overflow-x: auto`

### 3.5 Mermaid 图表

```
┌─────────────────────────────────────┐
│ ● MERMAID                           │  ← badge bar
├─────────────────────────────────────┤
│                                     │
│  [Mermaid SVG 图表]                  │  ← iframe 隔离渲染
│  padding: 16px                     │
│                                     │
└─────────────────────────────────────┘
border-radius: 16px
border: 1px solid border-glass
background: bg-secondary
```

**渲染机制**：通过独立 iframe 加载 CDN `mermaid@11`，`postMessage` 传递 SVG 回主文档。

### 3.6 ECharts 图表

```
┌─────────────────────────────────────┐
│ ▦ ECHARTS  WebView vs 原生...       │  ← badge bar + 标题
├─────────────────────────────────────┤
│                                     │
│  [ECharts PNG 图片]                  │  ← iframe 渲染 → getDataURL
│  width: 100%, auto height          │
│                                     │
└─────────────────────────────────────┘
同 Mermaid 卡片样式
```

### 3.7 RAG 指示器 (RagOmniIndicator)

```
┌──────────────────────────────────────┐
│ 🔍 已关联 5 个知识点 (就绪) ▼  8 Chunks│  ← 30px高，透明无边
├──────────────────────────────────────┤
│ ████████████████████░░░░░░░░░        │  ← 1.5px 进度条（仅活跃时可见）
└──────────────────────────────────────┘
```

状态优先级：活动检索 > 图谱抽取 > 后台归档 > 历史记录 > 生成中保活

| 状态 | 左侧图标 | 颜色 | 右侧统计 |
|------|---------|------|---------|
| 检索中 | Search | accent | ↑15K ↓87K |
| 图谱中 | Brain | #60a5fa | — |
| 归档中 | Database | #34d399 | — |
| 完成 | Library / Database | text-secondary | X Chunks |

CSS 动画：`.rag-pulse` — `scale(1→1.15)` 呼吸脉冲 1.6s

### 3.8 工具执行时间线 (ToolExecutionTimeline)

```
┌───────────────────────────────────────────┐
│ 🧠 已思考 3 轮，已调用工具 2 轮         ▼  │  ← 折叠 Header
│   backdrop-filter: blur(20px)
│   bg: bg-secondary
│   border-radius: 16px
├───────────────────────────────────────────┤
│                                           │
│  ●─── Thought                          ▶ │  ← 时间线项
│  │    展开内容...                          │
│  │                                        │
│  ●─── Using search_internet            ▶ │
│  │    { "query": "..." }                   │
│  │                                        │
│  ●─── Result (search_internet)         ▶ │
│  │    ┌──────────────────────────┐        │
│  │    │ 🔗 Source Title          │        │  ← 搜索结果卡片
│  │    │ snippet text...          │        │
│  │    └──────────────────────────┘        │
│  │                                        │
│  ●     Thinking...                     ▶ │  ← 当前活跃步骤
│                                           │
│  ┌────────────────────────────────────┐   │
│  │ Direct agent...              [Send]│   │  ← 底部干预输入
│  └────────────────────────────────────┘   │
│                                           │
└───────────────────────────────────────────┘
max-height: 35vh, overflow-y: auto
```

StepIcon 类型对照表：

| type | 图标 | 颜色 | 背景 |
|------|------|------|------|
| thinking | Brain | #A0A0A0 | rgba(0,0,0,0.05) |
| error | AlertCircle | #FF6B6B | rgba(255,107,107,0.2) |
| intervention (Loop) | RotateCw | #3b82f6 | rgba(59,130,246,0.2) |
| intervention (Action) | Hand | #f59e0b | rgba(245,158,11,0.2) |
| intervention_result | User | #10b981 | — |
| native_search | Globe | #a855f7 | — |
| throttled | RotateCw | #3b82f6 | — |
| search_internet | Globe | #4F8EF7 | — |
| query_vector_db | Database | #FF9F43 | — |
| generate_image | Image | #2ED573 | — |
| default tool | Terminal | #A0A0A0 | — |

### 3.9 任务监控 (TaskMonitor)

```
┌───────────────────────────────────────────┐
│ 🧠 代码重构 — WebView 渲染器迁移          │
│    4/6 · 60%                            ▼│  ← 进度胶囊
├───────────────────────────────────────────┤
│  ✓ 分析现有架构                            │
│  ✓ 设计 Bridge 协议                        │
│  ✓ 构建 Web Renderer                      │
│  ⟳ 实现核心组件  ← in-progress             │
│  ○ 集成图表渲染                            │
│  ○ 实机验证与优化                          │
│                                           │
│  ⚠ Decision Required                     │  ← 干预卡片（可选）
│  等待审批...                               │
│  Please reply to continue...              │
└───────────────────────────────────────────┘
border-radius: 16px
backdrop-filter: blur(20px)
border: 0.5px solid border-glass
```

### 3.10 审批卡片 (ApprovalCard)

**continuation 模式（蓝色）**：
```
┌───────────────────────────────────────────┐
│ 🔄  Loop Limit Reached                    │  ← 蓝色标题
│                                           │
│  Reason: Agent 已执行 10 轮...            │
│  ┌─────────────────────────────────────┐  │
│  │ Tool: Loop Limit                    │  │  ← 工具详情
│  └─────────────────────────────────────┘  │
│                                           │
│  可选：提供修改指令                         │
│  ┌─────────────────────────────────────┐  │
│  │ 例如: '仅写入 /tmp 目录'           │  │  ← 干预输入
│  └─────────────────────────────────────┘  │
│                                           │
│  [  End Task  ] [ Continue (+10)  ]       │  ← 操作按钮
└───────────────────────────────────────────┘
bg: rgba(59,130,246,0.1), border: rgba(59,130,246,0.3)
```

**action 模式（琥珀色）**：同上，主色 `#d97706`，背景 `rgba(217,119,6,0.1)`

### 3.11 记忆处理指示器 (ProcessingIndicator)

**完成状态（归档）**：
```
消息气泡底部：
  ✅  ← 绿勾图标，可点击展开详情
```

**完成状态（摘要）**：
```
  ✅ 🧠  ← 绿勾 + 蓝色大脑图标
```

**活跃处理中**：
```
  ┌──────────────────────────────┐
  │ ⟳ 切片归档中...          ▼  │  ← 胶囊按钮
  └──────────────────────────────┘
```

展开详情：
```
  ┌─ 核心摘要 ──────────────────────┐  ← 蓝色左边框
  │  用户询问了 WebView 渲染器...     │
  └─────────────────────────────────┘
  ┌─ 背景归档已完成 ────────────────┐  ← 绿色左边框
  │  消息已成功切片 (6 个)...        │
  └─────────────────────────────────┘
```

### 3.12 推理折叠 (ReasoningBlock)

```html
<details>
  <summary>思考过程</summary>  ← 可点击展开
  <div class="reasoning-content">
    预格式化推理文本...           ← bg-secondary, pre-wrap
  </div>
</details>
```

### 3.13 消息操作按钮 (MessageActions)

```
─────────────────────────────────  ← border-top: 1px subtle
  📋复制  📤分享  🧠图谱  📊向量  📖摘要  🗑删除
  opacity: 0.5 → 1 on hover/active
  gap: 2px, font-size: 11px
```

User 消息：复制 + 分享 + 重发 + 删除
Assistant 消息：复制 + 分享 + 图谱 + 向量 + 摘要 + 删除

### 3.14 流式动画

**加载中（无内容）**：
```
  ● ● ●  ← 三个圆点脉冲动画，1.5s 循环
```

**流式输出中（有内容）**：
```
  内容末尾 ● ● ●  ← 三个小圆点跟随文字
  ────────────────  ← 底部 72px 渐变脉冲
```

### 3.15 滚动到底部按钮

```
  position: fixed, bottom: 90px, right: 16px
  40x40px 圆形按钮
  bg: bg-secondary, border: border-default
  box-shadow: 0 2px 8px rgba(0,0,0,0.12)
  opacity: 0 → 1 (用户上滑时显示)
  ▼ 向下箭头图标
```

---

## 4. Bridge 通信协议

### 4.1 RN → WebView 消息 (RNToWebMessage)

| type | payload | 说明 |
|------|---------|------|
| `INIT` | `{ messages: BridgeMessage[], theme: WebViewThemePayload, sessionId?: string }` | 初始化全部数据 |
| `APPEND_MESSAGE` | `BridgeMessage` | 新增消息 |
| `UPDATE_MESSAGE` | `{ id: string, partial: Partial<BridgeMessage> }` | 部分更新 |
| `STREAM_CHUNK` | `{ messageId: string, content: string }` | 流式追加内容 |
| `DELETE_MESSAGE` | `{ id: string }` | 删除消息 |
| `THEME_CHANGE` | `WebViewThemePayload` | 主题切换 |
| `SCROLL_TO_BOTTOM` | `{ animated?: boolean }` | 滚动到底部 |
| `SET_GENERATING` | `{ isGenerating: boolean }` | 生成状态 |

### 4.2 WebView → RN 消息 (WebToRNMessage)

| type | 字段 | 说明 |
|------|------|------|
| `READY` | — | WebView 加载完成 |
| `REQUEST_SCROLL_TO_BOTTOM` | — | 请求 RN 侧滚动 |
| `DELETE_MESSAGE` | `messageId` | 删除消息 |
| `RESEND_MESSAGE` | `messageId, content` | 重发消息 |
| `EXTRACT_GRAPH` | `messageId` | 提取知识图谱 |
| `VECTORIZE` | `messageId` | 向量化 |
| `SUMMARIZE` | — | 摘要 |
| `SHARE_MESSAGE` | `messageId` | 分享消息 |
| `SCROLL_POSITION` | `offset` | 滚动位置上报 |
| `ERROR` | `message` | 错误上报 |
| `APPROVE_ACTION` | `sessionId, approved, instruction?` | 审批操作 |
| `SET_INTERVENTION` | `sessionId, instruction` | 干预指令 |
| `TOGGLE_COMPONENT` | `messageId, component, expanded` | 展开/折叠 |

### 4.3 BridgeMessage 数据结构

```typescript
interface BridgeMessage {
  id: string
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  createdAt: number
  status?: 'sending' | 'sent' | 'error' | 'streaming'
  isError?: boolean
  errorMessage?: string
  reasoning?: string
  // Phase 2 扩展
  task?: TaskState
  executionSteps?: ExecutionStep[]
  ragState?: RagIndicatorState
  approvalRequest?: ApprovalRequest | null
  processingState?: ProcessingState
  loopStatus?: 'idle' | 'running' | 'waiting_for_approval' | 'completed'
}
```

### 4.4 WebViewThemePayload

```typescript
interface WebViewThemePayload {
  isDark: boolean
  accentColor: string
  palette: {
    50/100/200/300/400/500/600/700/800/900: string
    opacity10/opacity20/opacity30: string
  }
}
```

---

## 5. 构建与部署

### 5.1 构建流程

```bash
cd src/web-renderer
npm run build          # tsc + vite build → dist/index.html (~2.23MB)
cp dist/index.html ../../assets/web-renderer/web-renderer.bundle
```

### 5.2 Metro 集成

- `metro.config.js` 已注册 `assetExts.push('bundle')`
- `assets/web-renderer/web-renderer.bundle` 作为 asset 打包进 APK
- RN 侧通过 `expo-asset` + `expo-file-system/legacy` `readAsStringAsync()` 加载为 HTML 字符串
- 全局缓存 `htmlCache` 避免重复读取

### 5.3 Bundle 体积

| 组成 | 大小 |
|------|------|
| React 19 + react-dom | ~130KB |
| react-markdown + remark-gfm + remark-math | ~80KB |
| rehype-katex + katex CSS | ~350KB |
| prism-react-renderer | ~50KB |
| 业务组件 CSS + JS | ~100KB |
| **总计 (gzip)** | **~1.18MB / ~2.23MB 未压缩** |

mermaid/echarts 通过 CDN 按需加载（iframe），不计入 bundle。

---

## 6. 设计约束

1. **移动端优先**：所有尺寸基于 375-430px 宽度
2. **无外部字体**：使用系统字体栈 `--font-sans` / `--font-mono`
3. **CSS 变量驱动**：所有颜色/字号/间距通过变量控制，支持动态主题
4. **iframe 隔离**：mermaid/echarts 在独立 iframe 中渲染，崩溃不影响主文档
5. **触摸友好**：按钮最小点击区域 44px，hover 状态仅作视觉反馈
6. **性能优先**：单 HTML 文件，无外部请求（除图表 CDN），CSS 动画替代 JS 动画
7. **无障碍**：语义化 HTML，`aria-label` 标注，`tabindex` 控制
