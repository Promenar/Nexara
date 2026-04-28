# 混合 WebView 聊天架构迁移计划 v2 (Single WebView Architecture)

> **版本**: v2.0 (2026-04-24) | **分支**: `webview-refactor-2nd`
> **前置评审**: v1 方案经可行性评估后修订，详见 v1 文档及评估记录
> **核心变更**: 禁止从 `web-client` 搬运代码、全部从零开发、新增 POC 实验入口、滚动系统重设计、长按菜单策略调整、四项技术决策落地

---

## 1. Background & Motivation (背景与动机)

当前 Nexara APP 主会话界面依托 React Native 的原生 Markdown 渲染器（`FlatList` 结合各类 renderer）处理复杂的 LLM 输出（代码高亮、表格、公式、Mermaid 图表、ECharts 等）。随着场景复杂化，RN 的 Markdown 渲染存在不可克服的性能和扩展性瓶颈：

- **多 WebView 碎片化**：`MathRenderer`(604行)、`MermaidRenderer`(401行)、`EChartsRenderer`(418行) 各自创建独立 WebView 实例，每个消息可能产生多个 WebView，造成严重的内存开销和 `postMessage` 边界通信抖动
- **滚动系统脆弱**：当前 `app/chat/[id].tsx` 中 ~200 行滚动管理代码（4个 `useSharedValue` + 3组 Effect + `useAnimatedScrollHandler`）已存在竞态问题，且无法照搬到 WebView 中
- **视觉一致性问题**：Web 渲染器与 RN 原生渲染器之间的颜色/字体/间距对齐困难

**核心决策**：将聊天消息列表的**全部渲染逻辑**迁移到单一 WebView 容器中，消除多 WebView 碎片化问题。

**与 `web-client` 的关系**：`web-client` 完成度低且视觉层面未经打磨，**严禁从中搬运代码**。所有 Web 端组件全部从零开发。WebView 版本跑通后，再将组件反向搬运到 `web-client` 做大屏适配。

---

## 2. Scope & Impact (范围与影响)

### 2.1 受影响范围

- `app/chat/[id].tsx` — 主聊天会话界面中的消息列表区域
- `src/features/chat/components/message/` 下的所有原生渲染组件
- `src/components/chat/` 下的 WebView 渲染器 (`MathRenderer`, `MermaidRenderer`, `EChartsRenderer`)

### 2.2 Web 技术从零重写范围

| 模块 | 当前 RN 组件 | Web 端状态 | 说明 |
|------|-------------|-----------|------|
| 会话气泡 | `MessageBubble` / `ChatBubble` | 从零开发 | User / Assistant 区分 |
| Markdown 渲染 | `react-native-markdown-display` + 自定义 rules | 从零开发 | react-markdown + remark-gfm |
| 代码高亮 | 自定义 `CodeBlock` | 从零开发 | Prism.js / Shiki |
| 数学公式 | `MathRenderer`(WebView 604行) + `NativeMathRenderer`(78行) | 从零开发 | KaTeX 内联渲染 |
| Mermaid 图表 | `MermaidRenderer`(WebView 401行) | 从零开发 | mermaid.js |
| ECharts 图表 | `EChartsRenderer`(WebView 418行) | 从零开发 | echarts |
| RAG 指示器 | `RagOmniIndicator`(345行, Reanimated 动画密集) | 从零开发 | CSS 动画替代 |
| 工具时间线 | `ToolExecutionTimeline` | 从零开发 | |
| 任务监控 | `TaskMonitor`(225行) | 从零开发 | |
| 执行模式 | `ExecutionModeSelector`(19.62KB) | 从零开发 | |
| 处理指示器 | `ProcessingIndicator`(360行, Reanimated 动画密集) | 从零开发 | framer-motion 替代 |
| 审批卡片 | `ApprovalCard`(7.13KB) | 从零开发 | |
| 推理折叠 | `ReasoningBlock` | 从零开发 | |
| 消息操作 | 原生 `MessageContextMenu` | 重新设计 | 详见 §3.4 |

### 2.3 保留原生组件

| 组件 | 保留原因 |
|------|---------|
| `GlassHeader` | 原生导航栏体验 |
| `ChatInput` | 键盘控制、文件选择、图片拍摄 |
| `ChatInputTopBar` | 模型选择、工具开关 |
| `SessionSettingsSheet` | 全局会话设置 |
| `ModelPicker` | 模型选择弹窗 |
| `TokenStatsModal` | 统计弹窗 |

---

## 3. Key Design Decisions (关键设计决策)

### 3.1 滚动系统：完全重设计

**现状分析**：当前 RN 滚动系统（inverted FlatList + Reanimated SharedValues + 50ms setTimeout 节流）并不完美，无法照搬到 WebView 中。WebView 内的滚动由浏览器引擎原生管理，性能远优于 RN 的 JS 线程滚动。

**新方案设计**：

```
┌─────────────────────────────────────┐
│  RN GlassHeader (z-index: 50)       │
├─────────────────────────────────────┤
│  WebView (flex: 1)                  │
│  ┌───────────────────────────────┐  │
│  │  CSS overflow-y: auto         │  │
│  │  scroll-snap-type: y proximity│  │
│  │                               │  │
│  │  [Message 1]                  │  │
│  │  [Message 2]                  │  │
│  │  ...                         │  │
│  │  [Message N] (最新)           │  │
│  │                               │  │
│  │  padding-bottom: 80px         │  │ ← 为底部输入框留空
│  └───────────────────────────────┘  │
├─────────────────────────────────────┤
│  RN ChatInput (KeyboardStickyView)  │
└─────────────────────────────────────┘
```

**核心技术点**：

1. **WebView 内部滚动**：使用 CSS `overflow-y: auto` + `-webkit-overflow-scrolling: touch`，消息列表在 WebView 内部自行管理滚动
2. **流式追踪**：WebView 内通过 `MutationObserver` 监听最后一条消息的 DOM 变化，配合 `element.scrollIntoView({ behavior: 'smooth', block: 'end' })` 实现自动追踪
3. **用户打断检测**：WebView 内监听 `scroll` / `touchmove` 事件，当用户主动上滑时标记 `userScrolledAway = true`
4. **回到底部按钮**：WebView 内渲染浮动按钮，通过 Bridge 通知 RN 侧（或纯 WebView 内处理）
5. **滚动位置持久化**：WebView 在 `beforeunload` 时通过 Bridge 将 `scrollTop` 回传 RN，恢复时通过 `injectJavaScript` 设置
6. **高度协调**：WebView 的 `padding-bottom` 动态匹配 `ChatInput` 高度（通过 Bridge 推送键盘高度 + 输入框高度）

### 3.2 长按菜单：双轨制策略

**放弃 WebView 自定义长按菜单**，采用双轨制：

| 操作类型 | 实现方式 | 说明 |
|---------|---------|------|
| 复制、粘贴、选择文本 | 系统 WebView 原生菜单 | iOS/Android WebView 内建支持 |
| 重新发送 | 底部按钮栏 | 消息气泡底部的操作按钮组 |
| 删除消息 | 底部按钮栏 → Bridge 回调 `DELETE_MESSAGE` | |
| 知识图谱抽取 | 底部按钮栏 → Bridge 回调 `EXTRACT_GRAPH` | |
| 向量记忆 | 底部按钮栏 → Bridge 回调 `VECTORIZE` | |
| 手动摘要 | 底部按钮栏 → Bridge 回调 `SUMMARIZE` | |
| 分享消息截图 | 底部按钮栏 → Bridge 回调 `SHARE_MESSAGE` | 截图在 RN 侧完成 |

**交互设计**：每个消息气泡底部展示一行紧凑的操作按钮（图标 + 文字），hover/长按时展开完整按钮组。这样规避了 WebView 中实现复杂长按菜单的开发成本和触摸手势冲突。

### 3.3 React 版本与组件来源

| 子项目 | React 版本 | 说明 |
|--------|-----------|------|
| RN 主项目 | `react@19.1.0` | 不变 |
| `web-renderer` | `react@^19.2.5` | Vite 独立打包，不存在版本冲突 |
| `web-client` | `react@^18.3.1` | **禁止搬运代码**，避免 18 vs 19 兼容问题 |

所有 Web 端组件**全部从零开发**，使用 React 19 + TailwindCSS（Web）生态。

### 3.4 流式输出与节流

**现状**：项目中已有流式输出节流机制（`app/chat/[id].tsx` 中 50ms `setTimeout` 节流 + `useStreamingIndicator` 的 600ms 空闲超时）。

**WebView 适配方案**：

- **RN 侧**：流式 chunk 仍然通过 `useChat` → `useChatStore` 更新 `messages` 数组，与现有逻辑一致
- **Bridge 推送**：采用增量推送协议（见 §4.2），每次仅推送变化的 chunk 而非全量 `Message[]`
- **WebView 侧节流**：WebView 内部使用 `requestAnimationFrame` 合并 DOM 更新，避免高频 `postMessage` 导致的 UI 闪烁

### 3.5 流式 Chunk 推送频率评估

**问题**：高频流式（每秒 10+ chunk）下 WebView JS 线程是否成为瓶颈？

**结论**：WebView 内部是完整的浏览器引擎（V8/JSCore），其 JS 执行性能远优于 RN 的 JS 线程（因为不承担 RN Bridge 开销）。`postMessage` 本身是异步的，不会阻塞 WebView 渲染。配合增量推送 + `requestAnimationFrame` 合并，性能不会成为问题。但需要在 POC 阶段实测验证。

---

## 4. Technical Architecture (技术架构)

### 4.1 Vite 构建管线集成方案

**技术选型**：`vite-plugin-singlefile` — 将所有 JS/CSS 内联为单 HTML 文件

**构建流程**：

```
┌──────────────────────┐
│  src/web-renderer/    │
│  (Vite + React 项目)  │
│                       │
│  vite build           │
│  ↓                    │
│  vite-plugin-singlefile│
│  ↓                    │
│  dist/index.html      │ ← 单文件，内联所有 JS/CSS
│  (约 2-5MB)           │
└──────────┬───────────┘
           │ require() / Asset.fromModule()
           ↓
┌──────────────────────┐
│  Metro (RN 打包)      │
│  将 index.html 作为   │
│  asset 打包进 APK     │
└──────────┬───────────┘
           │
           ↓
┌──────────────────────┐
│  WebView source={{    │
│    html: htmlContent  │ ← 直接读取 asset 内容
│  }}                   │
└──────────────────────┘
```

**关键配置**：

```typescript
// src/web-renderer/vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { viteSingleFile } from 'vite-plugin-singlefile'

export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    target: 'es2015', // 兼容 WebView Chrome 60+
    cssCodeSplit: false,
    assetsInlineLimit: 1000000, // 1MB 以下全部内联
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
      },
    },
  },
})
```

**产物集成到 Metro**：

- 方案 A（推荐）：将 `dist/index.html` 通过 `expo-asset` + `Asset.fromModule()` 加载为 file:// URI，与现有 `webview-assets.ts` 的模式一致
- 方案 B（备选）：直接 `require('./dist/index.html')` 读取为字符串，通过 `source={{ html }}` 注入

**开发模式热更新**：

- `src/web-renderer/` 修改后需执行 `npm run build` 重新生成产物
- 可通过 `nodemon` / `chokidar` 监听文件变化自动 rebuild，但不如 Vite dev server 的 HMR 体验
- **POC 阶段不需要热更新**，手动 build 即可

**产物大小预估**：

| 依赖 | 压缩后大小 | 说明 |
|------|----------|------|
| React + ReactDOM | ~45KB | production build |
| react-markdown + plugins | ~30KB | remark-gfm, rehype-katex |
| KaTeX CSS + fonts | ~300KB | 字体内联 |
| Mermaid | ~800KB | 精简版本 |
| ECharts (精简) | ~500KB | 按需引入 |
| Prism.js | ~30KB | 代码高亮 |
| 业务代码 | ~200KB | 估算 |
| **合计** | **~2MB** | 可接受，单次加载 |

### 4.2 Bridge 增量数据同步协议

**设计原则**：避免每次 `postMessage` 传输全量 `Message[]`，采用增量推送。

**消息类型定义**：

```typescript
// RN → WebView 消息类型
type RNToWebMessage =
  | { type: 'INIT'; payload: { messages: Message[]; isDark: boolean; accentColor: string } }
  | { type: 'APPEND_MESSAGE'; payload: Message }
  | { type: 'UPDATE_MESSAGE'; payload: { id: string; partial: Partial<Message> } }
  | { type: 'STREAM_CHUNK'; payload: { messageId: string; content: string } }
  | { type: 'DELETE_MESSAGE'; payload: { id: string } }
  | { type: 'THEME_CHANGE'; payload: { isDark: boolean; accentColor: string } }
  | { type: 'SCROLL_TO_BOTTOM'; payload?: { animated: boolean } }
  | { type: 'SET_GENERATING'; payload: { isGenerating: boolean } };

// WebView → RN 消息类型
type WebToRNMessage =
  | { type: 'READY' }
  | { type: 'REQUEST_SCROLL_TO_BOTTOM' }
  | { type: 'DELETE_MESSAGE'; messageId: string }
  | { type: 'RESEND_MESSAGE'; messageId: string; content: string }
  | { type: 'EXTRACT_GRAPH'; messageId: string }
  | { type: 'VECTORIZE'; messageId: string }
  | { type: 'SUMMARIZE' }
  | { type: 'SHARE_MESSAGE'; messageId: string }
  | { type: 'SCROLL_POSITION'; offset: number }
  | { type: 'ERROR'; message: string };
```

**节流策略**：`STREAM_CHUNK` 在 RN 侧使用 50ms 节流（复用现有机制），WebView 侧使用 `requestAnimationFrame` 合并 DOM 更新。

### 4.3 主题同步方案

**决策**：采用 **CSS 变量方案**，不使用 class 切换，不完全重渲染。

**理由**：
- CSS 变量方案是行业主流（Tailwind CSS v4 原生支持、Android WebView `prefers-color-scheme` 原生适配）
- 切换主题时仅修改 CSS 变量值，DOM 结构不变，浏览器会自动触发 repaint 但不 reflow，性能开销极低
- 对比 class 切换方案（需要在 `<html>` 上添加/移除 `dark` class，Tailwind 的 `dark:` 变体会触发子树重新匹配选择器），CSS 变量更轻量

**实现方案**：

```typescript
// Bridge 推送主题变化
{ type: 'THEME_CHANGE', payload: { isDark: boolean, accentColor: string } }

// WebView 内部处理
function applyTheme(isDark: boolean, accentColor: string) {
  const root = document.documentElement;
  root.style.setProperty('--bg-primary', isDark ? '#0a0a0c' : '#ffffff');
  root.style.setProperty('--text-primary', isDark ? '#ffffff' : '#09090b');
  root.style.setProperty('--accent', accentColor);
  // ... 其他变量映射自 src/theme/colors.ts 的 Colors 对象
}
```

**变量体系**：直接映射 `src/theme/colors.ts` + `src/lib/artifact-theme.ts` 的色值体系，确保视觉一致性。

**动态色阶**：`generatePalette(accentColor)` 的计算在 RN 侧完成（已有实现），通过 Bridge 将 `ColorPalette` 完整推送到 WebView。

### 4.4 无障碍评估

**结论**：**先不考虑 WebView 内部内容的无障碍支持**。

**理由**：
- WebView 内部内容对 RN 的 Screen Reader（VoiceOver/TalkBack）基本不可见，需要额外实现 ARIA 属性和 Bridge 通信
- 行业中 WebView 内部无障碍适配的复杂度极高，且需要双平台分别调试
- 当前项目没有已知的视障用户需求
- **后续如需支持**：可在 POC 跑通后，通过 `aria-label` + WebView `accessibilityLabel` prop + Bridge 暴露关键操作来逐步改善

---

## 5. POC 实验计划 (Proof of Concept)

### 5.1 实验入口：开发者模式

**利用已有的彩蛋触发机制**：

当前 `app/(tabs)/settings.tsx` 第 765-791 行已实现：连续 5 次快速点击"关于"→ 跳转 `/visual-demo`。

**扩展方案**：

1. 在 `visual-demo.tsx` 中新增一个 **"WebView Renderer 实验室"** 按钮
2. 点击后跳转独立的测试页面 `/webview-renderer-demo`
3. 该页面包含：
   - 单一 WebView 渲染器组件
   - 预置的测试消息数据（Markdown、代码、数学公式、Mermaid、ECharts）
   - 主题切换开关
   - 性能监控面板（FPS、内存）

### 5.2 POC 验证目标

| 验证项 | 通过标准 |
|--------|---------|
| Markdown 渲染质量 | 代码高亮、表格、列表与原生版本视觉一致 |
| 数学公式 | KaTeX 渲染清晰，行内/块级正确 |
| Mermaid 图表 | 流程图、时序图正确渲染，支持交互 |
| ECharts 图表 | 折线图、柱状图、饼图正确渲染 |
| 流式输出 | 模拟流式 chunk 推送，文字平滑出现无闪烁 |
| 滚动体验 | 100 条消息流畅滚动，自动追踪底部 |
| 主题切换 | Dark/Light 切换无明显闪烁 |
| 内存占用 | 100 条消息 < 150MB（vs 当前多 WebView 可能 > 300MB） |

---

## 6. Implementation Plan (实施计划)

### 阶段零：POC 基建 ✅ 已完成 (2026-04-24)

**目标**：在独立的测试页面中验证核心假设

1. ✅ 在 `visual-demo.tsx` 中新增"WebView Renderer 实验室"入口
2. ✅ 创建 `app/webview-renderer-demo.tsx` 测试页面
3. ✅ 配置 `web-renderer` 的 Vite 构建管线：
   - 安装 `vite-plugin-singlefile`
   - 配置 `vite.config.ts`（见 §4.1）
   - 实现基础 Markdown 渲染（react-markdown + remark-gfm）
   - 实现代码高亮（Prism.js）
   - 实现数学公式渲染（KaTeX）
4. ✅ 打通 Bridge 通信：`INIT` + `THEME_CHANGE`
5. ✅ 在 RN 侧实现 WebView 容器组件（加载单 HTML）
6. ✅ 预置测试数据并验证各项指标

**交付物**：
- ✅ 可运行的独立测试页面
- ✅ POC 验证通过（设备端到端验证通过）
- ✅ Go 决策：启动阶段一

**构建产物**：2.1MB 单 HTML 内联产物

### 阶段一：核心渲染器完善 ✅ 已完成 (2026-04-26)

**前置条件**：POC 验证通过 ✅

1. ✅ 集成 Mermaid 渲染 — **iframe 隔离方案**（CDN + postMessage），避免 WebView 白屏崩溃
2. ✅ 集成 ECharts 渲染 — **iframe 隔离方案**（CDN + getDataURL PNG 导出）
3. ✅ 实现 `MessageBubble` 组件（User / Assistant 区分）
4. ✅ 实现视觉还原（基础版）：
   - 字体、行高、间距与 RN 版本对齐
   - 气泡样式（圆角、阴影、颜色）
   - 头像显示
5. ✅ 实现流式输出渲染（`STREAM_CHUNK` 处理 + 动画）
6. ✅ 实现消息操作按钮栏（MessageActions）

**关键技术决策**：
- mermaid/echarts 不打包进 bundle，通过 CDN 在独立 iframe 中加载
- bundle 保持 ~2.2MB（移除 mermaid/echarts npm 依赖）
- 所有图表首次渲染需网络连接

**构建产物**：2.19MB

### 阶段二：高级业务组件 ✅ 已完成 (2026-04-28)

**前置条件**：阶段一视觉还原度 ≥ 90% ✅

1. ✅ **RAG 组件**：`RagOmniIndicator` — CSS 动画替代 Reanimated（脉冲/进度条/淡入淡出）
2. ✅ **工具时间线**：`ToolExecutionTimeline`（750行 RN → WebView）
   - BlurView 磨砂容器（backdrop-filter: blur）
   - 折叠 Header + 自动展开/折叠
   - 7 种 StepIcon 类型
   - 搜索结果卡片 / RAG 引用列表 / 干预 UI
3. ✅ **审批卡片**：`ApprovalCard`（continuation/action 双模式 + 干预输入）
4. ✅ **处理指示器**：`ProcessingIndicator`（胶囊入口 + 归档/摘要详情）
5. ✅ **任务监控**：`TaskMonitor`（进度百分比 + 步骤列表 + 智能折叠 + 干预卡片）
6. ✅ **推理折叠**：`ReasoningBlock`（内置 `<details>` 折叠）
7. ✅ **消息操作按钮栏**：复制/分享/图谱/向量/摘要/删除（Phase 1 已完成）

**Bridge 协议扩展**：
- BridgeMessage 新增 task/executionSteps/ragState/approvalRequest/processingState/loopStatus
- WebToRNMessage 新增 APPROVE_ACTION/SET_INTERVENTION/TOGGLE_COMPONENT
- INIT payload 支持 sessionId

**未实现**（不属于消息内容，留到 Phase 3）：
- `ExecutionModeSelector`（气泡外组件，BottomSheet，属于 ChatInput 区域）

**构建产物**：2.23MB

**测试覆盖**：21 条 mock 消息覆盖全部组件

### 阶段三：集成与优化 ⏳ 待启动

**前置条件**：阶段二功能完整度 ≥ 95% ✅

1. 将 WebView 渲染器集成到 `app/chat/[id].tsx`：
   - 保留 `FlatList` 作为 fallback（`USE_WEBVIEW_RENDERER` 开关）
   - WebView 容器替代 `AnimatedFlatList`
2. 滚动系统实现（见 §3.1）：
   - WebView 内部 MutationObserver 自动追踪
   - 用户打断检测
   - 滚动位置持久化
3. 键盘协调：
   - `ChatInput` 键盘弹出时通知 WebView 调整 `padding-bottom`
   - 使用 `useAnimatedKeyboard` 获取键盘高度
4. 长会话优化：
   - 虚拟列表（WebView 内仅渲染可视区域消息）
   - 增量 Bridge 推送性能实测
5. 全量迁移验证：对比 WebView 版本与原生版本的功能清单

### 阶段四：回迁 web-client (后续独立规划)

**前置条件**：阶段三在 APP 内完全跑通

1. 将 `web-renderer` 中打磨好的组件搬运到 `web-client`
2. 适配大屏布局（响应式设计）
3. 解决 React 18 vs 19 兼容性问题（或升级 `web-client` 到 React 19）
4. 独立规划文档

---

## 7. Work Estimate (工作量修正估算)

| 阶段 | 工作量 | 状态 | 说明 |
|------|--------|------|------|
| 阶段零：POC 基建 | 3-5 天 | ✅ 完成 | 1天完成，Bridge/Vite/基础 Markdown |
| 阶段一：核心渲染器 | 5-8 天 | ✅ 完成 | 2天完成，含图表 iframe 隔离方案 |
| 阶段二：高级组件 | 15-20 天 | ✅ 完成 | 3天完成，全部组件从零开发 + CSS 动画 |
| 视觉对齐 | 1-2 天 | ⏳ 待启动 | 逐组件对比 RN 原生，修复排版偏差 |
| 阶段三：集成优化 | 8-12 天 | ⏳ 待启动 | 滚动系统、键盘协调、长会话优化 |
| **已完成** | **~6 天** | | **原估 23-33 天，实际 6 天** |
| **剩余** | **~10 天** | | 视觉对齐 + Phase 3 |
| 阶段四：web-client | 待评估 | 独立规划 |

---

## 8. Verification (验证与测试)

### 8.1 UI 保真度

- 严格比对 Web 重写的各类组件与原 Native 版本的视觉差异
- 截图对比工具自动化（可复用 `scripts/agent-test/visual/diff-engine.ts`）
- 通过标准：像素差异 < 5%

### 8.2 交互验证

- WebView 内展开/折叠交互流畅
- 底部按钮操作正确触发 Bridge 回调
- 滚动流畅度：60fps（iOS）/ 55fps+（Android）

### 8.3 性能监控

| 指标 | 目标值 | 测试条件 |
|------|--------|---------|
| 内存占用 | < 150MB | 100 条消息，含 10 图表 |
| 首次渲染 | < 500ms | 50 条消息冷启动 |
| 流式延迟 | < 100ms | chunk 到达 → DOM 更新可见 |
| 主题切换 | < 200ms | 无明显闪烁 |
| APK 体积增量 | < 5MB | 单 HTML 内联产物 |

---

## 9. Migration & Rollback (迁移与回滚策略)

- **特性开关**：`USE_WEBVIEW_RENDERER`（默认 `false`），遇到致命 Bug 可热切换回原 Native FlatList
- **旧代码保留**：彻底验证无误前，不删除 `src/features/chat/components/message/` 下的原生实现
- **渐进式迁移**：每个阶段独立可交付，任一阶段失败不影响现有功能

---

## 10. Risk Register (风险登记)

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 滚动体验不及原生 | 高 | POC 阶段专项验证，准备虚拟列表方案 |
| 产物体积过大（>5MB）| 中 | 按需引入 ECharts/Mermaid，探索 code splitting |
| 流式高频推送性能 | 中 | 增量协议 + RAF 合并 + POC 实测 |
| Dark/Light 闪烁 | 低 | CSS 变量方案，仅 repaint 不 reflow |
| 无障碍不可用 | 低 | 已确认先不考虑 |
| web-client 回迁兼容性 | 中 | 后续独立评估，可能需升级 React 版本 |

---

## 11. 附录

### A. 现有 WebView 资源加载模式参考

当前项目中 6 个 WebView 组件均使用 `source={{ html }}` 内联加载：

- `webview-assets.ts`：通过 `expo-asset` 将 `assets/web-libs/*.bundle` 解析为 `file://` URI
- `scriptTagWithFallback()`：本地优先 + CDN onerror 降级
- `artifact-theme.ts`：语义化颜色 Token 系统

这些基础设施在 `web-renderer` 中可以沿用相同的颜色体系，但资源加载方式改变为 `vite-plugin-singlefile` 单文件内联。

### B. 开发者模式入口实现参考

```typescript
// app/(tabs)/settings.tsx 第 765-791 行（已有）
// 连续 5 次点击"关于"→ showToast('Developer Mode') → router.push('/visual-demo')

// app/visual-demo.tsx（需扩展）
// 新增 "WebView Renderer 实验室" 按钮 → router.push('/webview-renderer-demo')
```
