# WebView 聊天渲染器 Phase 0~2 交付归档

> **日期**: 2026-04-28 | **分支**: `webview-refactor-2nd`
> **状态**: Phase 0~2 全量交付完成，视觉对齐待推进

---

## 一、项目概述

将 Nexara 聊天界面的消息渲染从 RN `FlatList` 迁移到**单 HTML WebView** 方案。WebView 内承载全部富文本渲染（Markdown、代码高亮、数学公式、图表），RN 侧仅负责导航栏与输入框。

**核心价值**：一次构建产出 `web-renderer.bundle`（2.23MB 自包含 HTML），通过 Bridge 协议实现 RN ↔ WebView 双向通信。

---

## 二、交付物清单

### Phase 0 — POC 基建 ✅

| 交付项 | 文件 | 说明 |
|--------|------|------|
| Bridge 协议类型 | `src/types/webview-bridge.ts` | RN 侧消息类型定义 |
| Bridge 协议类型 | `src/web-renderer/src/types/bridge.ts` | Web 侧消息类型定义 |
| Bridge 通信层 | `src/web-renderer/src/bridge/index.ts` | postMessage 封装 |
| CSS 变量主题 | `src/web-renderer/src/bridge/theme.ts` | 动态主题注入 |
| Vite 构建管线 | `src/web-renderer/vite.config.ts` | single-file 插件，产出单 HTML |
| RN WebView 容器 | `src/components/chat/WebViewMessageList.tsx` | 加载 bundle + Bridge 桥接 |
| 自动滚动 Hook | `src/web-renderer/src/hooks/useAutoScroll.ts` | 流式场景自动滚底 |

### Phase 1 — 核心渲染器 ✅

| 组件 | 文件 | 功能 |
|------|------|------|
| MessageBubble | `src/web-renderer/src/components/MessageBubble.tsx` | 用户/AI 气泡布局、错误卡片、推理折叠、流式脉冲 |
| MarkdownRenderer | `src/web-renderer/src/components/MarkdownRenderer.tsx` | react-markdown + remark-gfm + remark-math + rehype-katex |
| CodeBlock | `src/web-renderer/src/components/CodeBlock.tsx` | prism-react-renderer + 复制按钮 |
| MessageList | `src/web-renderer/src/components/MessageList.tsx` | 消息列表容器、加载动画、流式脉冲 |
| MermaidRenderer | `src/web-renderer/src/components/MermaidRenderer.tsx` | **iframe 隔离**，CDN + postMessage 传 SVG |
| EChartsRenderer | `src/web-renderer/src/components/EChartsRenderer.tsx` | **iframe 隔离**，CDN + getDataURL PNG 导出 |
| MessageActions | `src/web-renderer/src/components/MessageActions.tsx` | 复制/分享/图谱/向量/摘要/删除 |

### Phase 2 — 高级业务组件 ✅

| 组件 | 文件 | 功能 |
|------|------|------|
| RagOmniIndicator | `src/web-renderer/src/components/RagOmniIndicator.tsx` | CSS 脉冲动画 + 进度条 + 网络统计 |
| TaskMonitor | `src/web-renderer/src/components/TaskMonitor.tsx` | 步骤列表 + 智能折叠 + 干预卡片 |
| ApprovalCard | `src/web-renderer/src/components/ApprovalCard.tsx` | continuation/action 双模式 + 干预输入 |
| ProcessingIndicator | `src/web-renderer/src/components/ProcessingIndicator.tsx` | 胶囊入口 + 归档/摘要详情 |
| ToolExecutionTimeline | `src/web-renderer/src/components/ToolExecutionTimeline.tsx` | 750 行 RN → WebView 移植，含折叠/搜索/RAG引用/干预 |

### 辅助文件

| 文件 | 说明 |
|------|------|
| `app/webview-renderer-demo.tsx` | 21 条 mock 消息测试页面，覆盖全部组件 |
| `assets/web-renderer/web-renderer.bundle` | 2.23MB 构建产物（Vite single-file） |
| `src/web-renderer/src/index.css` | 全局样式 + 15 个组件 CSS |
| `src/web-renderer/src/App.tsx` | WebView 入口 |
| `src/web-renderer/src/main.tsx` | React 挂载点 |
| `src/web-renderer/index.html` | HTML 模板 |

---

## 三、Bridge 协议规范

### RN → WebView（8 种消息）

| 消息类型 | 用途 |
|----------|------|
| `INIT` | 初始化消息列表、主题、sessionId |
| `APPEND_MESSAGE` | 追加新消息 |
| `UPDATE_MESSAGE` | 全量更新某条消息 |
| `STREAM_CHUNK` | 流式追加内容片段 |
| `THEME_CHANGE` | 切换明暗主题 |
| `APPROVE_ACTION` | 审批操作回调 |
| `SET_INTERVENTION` | 设置干预内容 |
| `TOGGLE_COMPONENT` | 展开/折叠组件 |

### WebView → RN（13 种消息）

| 消息类型 | 用途 |
|----------|------|
| `READY` | WebView 加载就绪 |
| `COPY_TEXT` | 复制文本到剪贴板 |
| `SHARE_MESSAGE` | 分享消息 |
| `OPEN_LINK` | 打开外部链接 |
| `OPEN_IMAGE` | 查看大图 |
| `SCROLL_POSITION` | 上报滚动位置 |
| `REQUEST_APPROVE` | 请求审批操作 |
| `SET_INTERVENTION` | 发送干预内容 |
| `TOGGLE_COMPONENT` | 展开/折叠状态变更 |
| `OPEN_MERMAID_FULLSCREEN` | Mermaid 全屏查看 |
| `OPEN_ECHARTS_FULLSCREEN` | ECharts 全屏查看 |
| `DELETE_MESSAGE` | 删除消息 |
| `REQUEST_SUMMARY` | 请求生成摘要 |

### BridgeMessage 扩展字段（Phase 2 新增）

```typescript
interface BridgeMessage {
  // ... Phase 0 基础字段
  task?: TaskInfo;                    // 任务信息
  executionSteps?: ExecutionStep[];   // 工具执行步骤
  ragState?: RagState;                // RAG 检索状态
  approvalRequest?: ApprovalRequest;  // 审批请求
  processingState?: ProcessingState;  // 处理状态
  loopStatus?: LoopStatus;            // 循环状态
}
```

---

## 四、关键技术决策

### 4.1 mermaid.render() 白屏崩溃

- **问题**: mermaid 引擎内部异常导致 WebView 容器级崩溃，try-catch 无法捕获
- **方案**: iframe 隔离渲染 + CDN 加载 + postMessage 传递 SVG/PNG
- **副作用**: bundle 从 4.8MB 降至 2.23MB，但首次渲染需网络

### 4.2 sessionId 缺失导致 ApprovalCard 空白

- **问题**: `MessageBubble` 渲染 ApprovalCard 需要 `sessionId`，但 INIT 未携带
- **方案**: `WebViewMessageList` 新增 `sessionId` prop，INIT payload 携带

### 4.3 CSS 变量主题系统

- 所有颜色/字号/间距通过 CSS custom properties 驱动
- RN 侧通过 `THEME_CHANGE` 消息动态注入变量值
- 支持明暗主题实时切换

### 4.4 视觉修复记录

| 修复项 | 提交 | 说明 |
|--------|------|------|
| Timeline 标题色 | `e561139` | `rgba(255,255,255,0.8)` → `var(--text-primary)` |
| AI 头像移除 | `e561139` | 移除 avatar-wrapper，气泡填满宽度 |
| 布局密度 | `e561139` | assistant padding 20px→16px，RAG 高度 34px→30px |

---

## 五、提交历史

```
e561139 fix(webview): 视觉修复 — Timeline标题色 + 移除头像 + 气泡填满
0fbd437 feat(webview): ToolExecutionTimeline + 依赖清理
b00a658 feat(webview): Phase 2 — 高级业务组件 + Bridge 协议扩展
628d40d feat(webview): Phase 1 — 完整渲染器 + 图表 iframe 隔离方案
64a480d feat: implement single-webview architecture and web-renderer
```

---

## 六、工作量统计

| 阶段 | 预估 | 实际 | 偏差 |
|------|------|------|------|
| Phase 0 | 3-5 天 | 1 天 | -70% |
| Phase 1 | 8-12 天 | 2 天 | -80% |
| Phase 2 | 12-16 天 | 3 天 | -78% |
| **合计** | **23-33 天** | **6 天** | **-80%** |

新建文件 19 个，修改文件 6 个，总代码量约 8,000+ 行。

---

## 七、下一步计划

### 7.1 视觉对齐专项（预估 1-2 天）

逐组件对比 RN 原生 vs WebView 渲染效果，系统修复：
- 间距/字号/圆角/色值偏差
- 动画时序对齐
- 触摸反馈一致性

### 7.2 Google Stitch UI 重构

使用 `webview-ui-design-spec.md` 作为输入，通过 Stitch 生成优化版 UI：
- 参考文档：`.agent/docs/plans/webview-ui-design-spec.md`
- 参考归档：本文档

### 7.3 Phase 3 集成（预估 8-12 天）

- Feature flag 接入 `app/chat/[id].tsx`
- 滚动系统重设计（MutationObserver + 用户打断检测）
- 键盘协调（ChatInput ↔ WebView padding-bottom）
- 长会话优化（虚拟列表）
- ExecutionModeSelector（气泡外 BottomSheet）

---

## 八、风险项

| 风险 | 影响 | 缓解 |
|------|------|------|
| 图表离线不可用 | CDN 不可达时 Mermaid/ECharts 显示错误提示 | 已实现 onerror fallback |
| Phase 3 滚动复杂度 | 长会话流畅度 | 虚拟列表 + 增量渲染 |
| 视觉精细度未系统验证 | 排版偏差 | 视觉对齐专项 |
| Stitch 产出与现有 CSS 变量体系兼容性 | 重构成本 | 保留变量接口，替换实现 |
