# 混合 WebView 聊天架构迁移计划 (Single WebView Architecture)

## 1. Background & Motivation (背景与动机)
当前 Nexara APP 主会话界面依托 React Native 的原生 Markdown 渲染器（`FlatList` 结合各类 renderer）处理复杂的 LLM 输出（如：代码高亮、表格、公式、Mermaid 图表、ECharts 等）。随着场景复杂化，RN 的 Markdown 渲染存在不可克服的性能和扩展性瓶颈。由于当前 `web-client` 项目处于 WIP（开发中）状态，直接复用其组件存在极高风险。因此，我们计划引入单 Webview 容器架构，在兼顾性能与功能完整性的同时，彻底解决复杂 Markdown 渲染问题。

## 2. Scope & Impact (范围与影响)
- **受影响范围**: 
  - `app/chat/[id].tsx`（主聊天会话界面中的列表部分）
  - `src/features/chat/components/message/` 下的所有原生渲染组件。
- **Web 技术重写范围 (100% Web实现)**: 
  - 所有的会话气泡 (`MessageBubble`)
  - 集成式 RAG 指示器 (`RagOmniIndicator`, `RagReferences` 等)
  - 工具调用与时间线 (`ToolArtifacts`, `TaskMonitor` 等)
  - 深度思考/推理折叠区块 (`ReasoningBlock`)
  - 长按弹出的交互式菜单 (`MessageContextMenu`) 与选词功能
- **保留原生组件 (仅限会话区域外)**: 
  - 顶部的 `GlassHeader`
  - 底部的 `ChatInput` 及其相关的 TopBar (模型选择、工具开关)
  - 全局的会话设置 Sheet (Session Settings)
- **核心设计要求**: 重构为 Webview 大容器后，**其界面外观（包括字体、间距、气泡样式、Dark/Light 模式颜色等）必须与原先的 React Native 原生实现保持高度一致或近似**，确保用户无感知过渡。

## 3. Proposed Solution (提出的解决方案)
采用 **独立轻量级 Web 渲染器 (Dedicated Web Renderer)**。
1. **新建工程独立构建**: 在 `src/web-renderer/` (或类似独立目录) 初始化 Vite + React 工程，作为专门提供给 App WebView 的本地前端项目。
2. **全面 Web 化重写与 1:1 视觉还原**:
   - 不直接复用残缺的 `web-client`，而是基于当前 App 端的 UI 设计稿和业务逻辑，在 `web-renderer` 中**使用 Web 技术 (React + TailwindCSS) 完整重写** RAG 指示器、工具调用时间线、深度思考模块等。
   - 使用成熟的 Web 生态 (`react-markdown`, `mermaid`, `echarts`, 高亮库) 解决所有的文本和图表渲染问题，同时通过 Tailwind 配置严格映射原生的颜色变量和样式规范。
   - 将长按菜单、浮动操作面板等原先依赖 RN 弹窗的组件，直接使用 Web 原生的绝对定位/相对定位弹层重写，实现内聚的交互闭环。
3. **Bridge 数据流闭环**:
   - **RN -> Web**: 仅作为数据提供方，推送全量 `Message[]` 数组、当前的流式 Chunk、RAG 的 `processingState` 状态更新、当前主题 (Dark/Light)。
   - **Web -> RN**: 当 Web 内发生需要跨越 WebView 边界的操作时（例如：调用系统分享、调用相册/相机、震动反馈、全屏打开第三方链接、发送新消息重试指令）才通过 Bridge 通知原生层。

## 4. Alternatives Considered (考虑过的替代方案)
- **Local HTML String Injection**: 直接拼接 HTML 字符串。由于现在需要在 Web 端实现复杂的 RAG 指示器和工具时间线，纯字符串拼接无法维护组件状态，不可行。
- **复用 web-client 组件**: `web-client` 目前 RAG 和工具生态组件缺失，强行提取会导致巨大的适配阻力，不如建立专用的轻量级 `web-renderer` 稳妥。

## 5. Implementation Plan (实施计划)

### 阶段零：准备工作
1. 基于当前仓库追踪的分支创建并切换到新分支 `webview-refactor-2nd`。
2. 所有后续的开发与实施工作均在该分支内进行。

### 阶段一：搭建 Web Renderer 工程与通信基建
1. 初始化 `src/web-renderer` (React + TypeScript + TailwindCSS)。配置 Vite 将产物打包为单文件或 App 本地可读的静态资源结构。
2. 在 RN `app/chat/[id].tsx` 中移除 `AnimatedFlatList`，植入 `react-native-webview`，打通基础的 `postMessage` 通信，验证数据注入和高度自适应。

### 阶段二：重写核心展示层 (Markdown & 图表)
1. 在 Web 端实现基础的会话气泡 (`MessageBubble`)，区分 User / Assistant，**确保视觉上与原有原生气泡 1:1 像素级对齐**。
2. 接入 `react-markdown`, `remark-gfm`, `rehype-katex`，实现代码高亮与表格。
3. 集成 Web 版 Mermaid 和 ECharts，确保图表在移动端 WebView 中完美自适应渲染。

### 阶段三：重写高级业务组件 (RAG & Tools)
1. **RAG 组件 Web 化**: 使用 React/Tailwind 在 Web 端逐一复刻 `RagOmniIndicator`, `RagReferences`，支持展开/折叠显示引用片段和向量化进度，**保持与原生一致的 UI 动画和配色**。
2. **Tools 组件 Web 化**: 在 Web 端重写 `ToolArtifacts` 和 `TaskMonitor`，实现工具调用的骨架屏、执行状态流转和结果展示。
3. **Reasoning Web 化**: 重写深度思考流式展开效果。

### 阶段四：交互闭环与优化
1. **会话菜单**: 使用 Web 技术实现长按/右键弹出的菜单（复制、重试、删除、向量化等），并通过 Bridge 将业务指令（如重发、删除库内记录）传回 RN 执行。
2. **滚动与体验**: 解决输入法弹出时的 Web 容器高度压缩与自适应滚动（Scroll to bottom），确保输入时气泡不被遮挡。

## 6. Verification (验证与测试)
- **UI 保真度**: 严格比对 Web 重写的各类指示器、工具时间线、会话气泡与原 Native 版本的视觉差异，实现无感知平滑过渡。
- **交互验证**: 确保 Web 内展开交互流畅，长按菜单在边界不被裁剪。
- **性能监控**: 在 Android/iOS 真机测试长会话（100+ 消息，含多图表），评估 WebView 的内存占用和滑动帧率。

## 7. Migration & Rollback (迁移与回滚策略)
- **特性开关**: 通过 `USE_WEBVIEW_RENDERER` 配置项（默认 false），以便遇到致命 Bug 时可热切换回原 Native FlatList 渲染。
- **旧代码保留**: 彻底验证无误前，不删除原 `src/features/chat/components/message/` 下的原生实现。