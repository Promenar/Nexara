---
name: webview-poc-phase0
overview: WebView 单一容器架构重构 - 阶段零 POC 基建：配置 Vite 构建管线、实现 Bridge 通信协议、开发基础 Markdown/代码/数学渲染、创建 RN 侧 WebView 容器、搭建 POC 测试页面
todos:
  - id: bridge-types-foundation
    content: 创建 Bridge 协议类型定义 + CSS 变量主题映射方案 + Mock 测试数据
    status: completed
  - id: web-renderer-pipeline
    content: 配置 Vite 构建管线并安装依赖，实现 web-renderer 核心 Web 组件（Markdown/KaTeX/CodeBlock/MessageBubble/MessageList）
    status: completed
    dependencies:
      - bridge-types-foundation
  - id: rn-webview-container
    content: 实现 RN 侧 WebViewMessageList 容器组件和 Bridge 双向通信层
    status: completed
    dependencies:
      - bridge-types-foundation
  - id: poc-test-page
    content: 扩展 visual-demo 入口并创建 webview-renderer-demo POC 测试页面，集成预置数据和流式模拟
    status: completed
    dependencies:
      - web-renderer-pipeline
      - rn-webview-container
  - id: build-integrate-verify
    content: 构建产物集成到 Metro 并执行端到端 POC 验证（渲染质量/滚动/主题/流式/内存）
    status: completed
    dependencies:
      - poc-test-page
---

## 产品概述

将 Nexara APP 聊天消息列表的全部渲染逻辑从 React Native 原生组件（FlatList + 多个独立 WebView 渲染器）迁移到单一 WebView 容器中，消除多 WebView 碎片化、滚动系统脆弱、视觉一致性差等问题。

## 当前阶段：阶段零 POC 基建

在独立的测试页面中验证核心技术假设，不触碰生产代码。POC 通过后才启动后续阶段。

### 核心功能（阶段零交付物）

1. 配置 web-renderer 的 Vite 构建管线，生成单 HTML 内联产物
2. 实现 Bridge 双向通信协议（RNToWebMessage / WebToRNMessage）
3. 实现基础 Markdown 渲染（react-markdown + remark-gfm + 代码高亮 + KaTeX 公式）
4. 实现 CSS 变量主题系统，映射 RN 侧 Colors 色值
5. 在 RN 侧实现 WebView 容器组件，加载单 HTML 并管理 Bridge 通信
6. 创建独立的 POC 测试页面，包含预置测试数据，验证渲染质量、滚动体验、主题切换、流式输出

### 视觉效果

- 独立的隐藏测试页面（通过 visual-demo 进入）
- 消息气泡列表在 WebView 内渲染，支持 Dark/Light 主题切换
- Markdown 内容包含代码高亮、数学公式、表格等复杂排版
- 模拟流式输出时文字平滑追加
- 100 条消息流畅滚动，自动追踪底部

## 技术栈

### Web 端（web-renderer 子项目）

- **框架**: React 19.2 + TypeScript 6
- **构建**: Vite 8 + vite-plugin-singlefile（单 HTML 内联产物）
- **Markdown**: react-markdown + remark-gfm + rehype-katex
- **代码高亮**: Prism.js（prism-react-renderer）
- **数学公式**: KaTeX（通过 rehype-katex 集成）
- **样式**: CSS 变量体系（映射 RN 侧 Colors + ColorPalette）

### RN 端（主项目）

- **WebView**: react-native-webview
- **资源加载**: expo-asset（将 web-renderer 产物打包为 Metro asset）
- **Bridge 通信**: WebView postMessage / onMessage 双向协议
- **主题数据**: 复用 ThemeProvider（isDark + ColorPalette）

## 实施方案

### 整体策略

将阶段零拆分为 3 个可并行的工作流 + 1 个集成验证流：

```
Stream A (基础设施)          Stream B (Web 端)           Stream C (RN 端)
┌──────────────────┐     ┌──────────────────────┐    ┌─────────────────────┐
│ Bridge 类型定义   │     │ Vite 构建配置          │    │ WebView 容器组件     │
│ webview-bridge.ts│     │ vite.config.ts        │    │ WebViewMessageList  │
│ CSS 变量主题映射  │     │ react-markdown 集成   │    │ Bridge 消息收发      │
│ Mock 测试数据     │     │ KaTeX + Prism.js      │    │ HTML 资源加载        │
└────────┬─────────┘     │ MessageBubble 组件     │    └──────────┬──────────┘
         │               │ CSS 主题系统           │               │
         │               └──────────┬───────────┘               │
         │                          │                           │
         └──────────────────────────┼───────────────────────────┘
                                    │
                          Stream D (集成验证)
                    ┌──────────────────────────┐
                    │ POC 测试页面              │
                    │ visual-demo 扩展入口      │
                    │ webview-renderer-demo     │
                    │ 预置数据 + 主题切换 +     │
                    │ 流式模拟 + 性能观察       │
                    └──────────────────────────┘
```

### 关键技术决策

1. **产物加载方式**：采用 `require()` 直接读取 dist/index.html 为字符串，通过 `source={{ html }}` 注入。比 expo-asset 方案更简单，且不依赖 Metro assetExts 扩展。web-renderer 产物预计 2-3MB，作为字符串常量内联到 JS bundle 中。
2. **Bridge 通信**：WebView 内通过 `window.ReactNativeWebView.postMessage()` 发送，RN 侧通过 `onMessage` 回调接收。反向通过 `webviewRef.injectJavaScript()` 注入。复用现有 WebView 组件的通信模式（参考 EChartsRenderer）。
3. **CSS 变量方案**：WebView 内使用 CSS custom properties 定义完整色值体系，主题切换时通过 `document.documentElement.style.setProperty()` 批量修改，仅 repaint 不 reflow。
4. **流式输出**：RN 侧维持现有 50ms 节流机制，WebView 侧使用 `requestAnimationFrame` 合并 DOM 更新，避免高频 postMessage 导致 UI 闪烁。

## 目录结构

### 新建文件

```
src/web-renderer/
├── vite.config.ts                    # [MODIFY] 添加 viteSingleFile + 构建优化配置
├── package.json                      # [MODIFY] 添加 react-markdown, katex, prismjs 等依赖
├── index.html                        # [MODIFY] 精简为最小 HTML 壳，添加 viewport meta
├── src/
│   ├── main.tsx                      # [MODIFY] 移除 StrictMode（避免 WebView 双重渲染）
│   ├── App.tsx                       # [REWRITE] Bridge 消息监听 + 消息列表渲染
│   ├── index.css                     # [REWRITE] CSS 变量体系 + 基础排版样式
│   ├── types/
│   │   └── bridge.ts                 # [NEW] RNToWebMessage / WebToRNMessage 类型定义
│   ├── bridge/
│   │   ├── index.ts                  # [NEW] Bridge 通信层：postToRN + onRNMessage
│   │   └── theme.ts                  # [NEW] CSS 变量主题管理：applyTheme()
│   ├── components/
│   │   ├── MessageList.tsx           # [NEW] 消息列表容器（滚动管理 + 自动追踪）
│   │   ├── MessageBubble.tsx         # [NEW] 消息气泡（User/Assistant 区分）
│   │   ├── MarkdownRenderer.tsx      # [NEW] Markdown 渲染器（react-markdown + remark-gfm）
│   │   ├── CodeBlock.tsx             # [NEW] 代码块（Prism.js 高亮 + 复制按钮）
│   │   └── MathBlock.tsx             # [NEW] 数学公式（KaTeX 行内/块级）
│   └── hooks/
│       └── useBridgeMessages.ts      # [NEW] Bridge 消息状态管理 Hook

src/types/
│   └── webview-bridge.ts             # [NEW] Bridge 协议类型（RN 侧引用）

src/components/chat/
│   └── WebViewMessageList.tsx        # [NEW] RN 侧 WebView 容器组件

app/
│   ├── visual-demo.tsx               # [MODIFY] 新增 Section 3: WebView Renderer Lab 入口
│   └── webview-renderer-demo.tsx     # [NEW] POC 独立测试页面
```

### 各文件职责详述

**`src/web-renderer/vite.config.ts`** — 构建核心

- 添加 `viteSingleFile()` 插件，内联所有 JS/CSS 为单 HTML
- `build.target: 'es2015'` 兼容 Android WebView Chrome 60+
- `build.cssCodeSplit: false`，`assetsInlineLimit: 1000000`
- `rollupOptions.output.inlineDynamicImports: true`

**`src/web-renderer/src/types/bridge.ts`** — 双向协议类型

- `RNToWebMessage` 联合类型（INIT / APPEND_MESSAGE / UPDATE_MESSAGE / STREAM_CHUNK / DELETE_MESSAGE / THEME_CHANGE / SCROLL_TO_BOTTOM / SET_GENERATING）
- `WebToRNMessage` 联合类型（READY / REQUEST_SCROLL_TO_BOTTOM / DELETE_MESSAGE / RESEND_MESSAGE / SCROLL_POSITION / ERROR）
- `WebViewTheme` 接口（isDark + accentColor + ColorPalette 色阶值）

**`src/web-renderer/src/bridge/index.ts`** — 通信层

- `postToRN(msg: WebToRNMessage)` 封装 `window.ReactNativeWebView.postMessage(JSON.stringify(msg))`
- `onRNMessage(handler)` 封装 `window.addEventListener('message', ...)` 监听
- 启动时发送 `READY` 消息，RN 侧收到后触发 INIT

**`src/web-renderer/src/bridge/theme.ts`** — CSS 变量主题

- `applyTheme(isDark, accentColor, palette)` 批量设置 CSS 变量
- 变量映射：`--bg-primary` / `--text-primary` / `--surface-secondary` / `--accent-50` 到 `--accent-900` 等
- 色值来源：精确对应 `src/theme/colors.ts` Colors.light/dark + ColorPalette

**`src/web-renderer/src/components/MessageList.tsx`** — 滚动管理

- CSS `overflow-y: auto` + `-webkit-overflow-scrolling: touch`
- `MutationObserver` 监听最后一条消息 DOM 变化，自动 `scrollIntoView`
- 用户打断检测：`scroll` / `touchmove` 事件标记 `userScrolledAway`
- 浮动"回到底部"按钮

**`src/web-renderer/src/components/MarkdownRenderer.tsx`** — Markdown 核心

- `react-markdown` + `remark-gfm`（表格、删除线、任务列表）
- 自定义 `code` 渲染器：检测 language 调用 CodeBlock
- 行内数学 `$... 和块级数学 `$... 检测，调用 MathBlock
- CSS 排版对齐 RN 侧 ChatBubble 的视觉风格

**`src/web-renderer/src/components/CodeBlock.tsx`** — 代码高亮

- `prism-react-renderer` 实现 Syntax Highlighting
- 支持 20+ 常用语言（js/ts/python/go/rust/java/json/yaml/bash 等）
- 复制按钮（通过 Bridge 通知 RN 侧使用 Clipboard API）
- 深色/浅色主题自动切换

**`src/web-renderer/src/components/MathBlock.tsx`** — 数学公式

- `rehype-katex` 集成，渲染行内和块级数学公式
- KaTeX CSS 字体需在 Vite 构建中内联（vite-plugin-singlefile 处理）
- WebView 内 KaTeX 渲染性能远优于 RN 侧多 WebView 方案

**`src/components/chat/WebViewMessageList.tsx`** — RN 侧容器

- 接收 `messages: Message[]` + `isDark` + `colors: ColorPalette` props
- 通过 `require()` 加载 web-renderer 的 dist/index.html 字符串
- `useRef<WebView>` 管理 Bridge 通信
- `onMessage` 解析 WebToRNMessage，分发到对应回调
- `useEffect` 监听 messages/isDark/colors 变化，通过 `injectJavaScript` 推送增量更新
- 保留 `style={{ flex: 1 }}` 填充父容器

**`app/webview-renderer-demo.tsx`** — POC 测试页面

- 包含 3 个区域：控制面板（主题切换 + 流式模拟按钮）、WebView 消息列表、性能面板
- 预置 10-15 条测试消息（覆盖 Markdown/代码/表格/公式等场景）
- 流式模拟：50ms 定时器逐字符推送 STREAM_CHUNK
- 性能监控：WebView 内 `performance.now()` 计时，通过 Bridge 回传 FPS/渲染时间

**`src/types/webview-bridge.ts`** — RN 侧 Bridge 类型

- 复用 `src/types/chat.ts` 中的 Message 类型
- 定义 RNToWebMessage / WebToRNMessage 联合类型（与 web-renderer 侧 mirror）
- RN 侧组件和 web-renderer 侧各自 import 自己的类型定义，保持解耦

## 实施注意事项

### 性能

- web-renderer 产物约 2-3MB，作为字符串 require 到 JS bundle。首次加载后缓存在内存，不影响后续渲染性能
- 流式输出使用 `requestAnimationFrame` 合并 DOM 更新，避免每个 chunk 触发独立渲染
- Prism.js 按需引入语言包，避免全量打包（约 30KB vs 100KB+）
- KaTeX 字体通过 vite-plugin-singlefile 内联为 base64，确保离线可用

### 兼容性

- `build.target: 'es2015'` 确保兼容 Android WebView Chrome 60+ 和 iOS WKWebView
- CSS 变量在 Chrome 49+ / Safari 9.1+ 均支持，满足 WebView 最低版本要求
- `-webkit-overflow-scrolling: touch` 确保 iOS 弹性滚动

### 爆炸半径控制

- POC 代码全部在 `app/webview-renderer-demo.tsx` 中使用，不触碰 `app/chat/[id].tsx`
- 新建独立的 RN 组件 `WebViewMessageList.tsx`，不修改任何现有组件
- web-renderer 子项目独立构建，不影响主项目 Metro 打包流程
- `visual-demo.tsx` 仅新增一个 Section 卡片，改动量 < 20 行

### 构建产物集成

- web-renderer 构建后生成 `dist/index.html`
- RN 侧通过 `require('../../src/web-renderer/dist/index.html')` 读取为字符串
- 需要在 Metro config 中确保 `.html` 扩展名被 `assetExts` 包含，或使用 `sourceExts` 配置
- 开发时手动 `cd src/web-renderer && npm run build` 生成产物

## SubAgent

- **code-explorer**
- Purpose: 在实施过程中搜索项目文件、定位具体代码模式、验证类型引用路径
- Expected outcome: 快速定位依赖文件和现有模式，减少手工搜索时间