# Nexara 项目目录结构映射

> **Version**: v1.0 (2026-04-09)
> **Protocol**: v5.0 Standard

---

## 1. 核心目录结构

### 1.1 `app/` (页面层 - Expo Router)
基于文件系统的路由定义。
- `chat/`: 聊天会话详情页及设置。
- `settings/`: 全局设置页面 (Token 使用统计、API 配置等)。
- `rag/`: 知识库管理与文档详情。
- `(tabs)/`: 底部标签栏主入口。

### 1.2 `src/features/` (业务特性层)
按功能模块解耦的业务逻辑。
- `chat/`: 聊天气泡渲染、输入框逻辑、Hooks。
- `settings/`: 模型选择器、Provider 配置 UI。

### 1.3 `src/store/` (状态管理层 - Zustand)
全局逻辑的 SSOT (单一事实来源)。
- `chat-store.ts`: 会话与消息流转中心。
- `artifact-store.ts`: 生成内容 (Workspace) 管理。
- `token-stats-store.ts`: 使用量与计费统计。
- `rag-store.ts`: 知识库与向量化状态。

### 1.4 `src/lib/` (基础设施与库)
底层能力抽象。
- `db/`: SQLite (op-sqlite) 迁移与核心查询。
- `llm/`: 统一模型请求适配器 (Gemini, OpenAI, Claude)。
- `rag/`: 向量检索、重写、知识图谱提取。
- `sanitizer/`: Markdown 与 AI 内容格式化管道。
- `mcp/`: Model Context Protocol 协议实现。

### 1.5 `src/components/` (通用组件库)
跨功能复用的 UI 原件。
- `ui/`: 统一设计语言组件 (Glassmorphism, Typography, PageLayout)。
- `chat/`: 消息专用渲染组件。

---

## 2. 关键文件约定

- `src/types/`: 统一类型定义 (SSOT)。
- `src/theme/`: 实时主题方案及动画定义。
- `.agent/docs/`: 核心架构文档所在位置。
- `assets/`: 静态资源 (WebView 离线包、插图)。

---

## 3. 开发规范
1. **模块化**: 新功能应优先在 `src/features/` 下建立子目录。
2. **类型驱动**: 修改数据结构前，必须先更新 `src/types/` 及 `DATA_SCHEMA.md`。
3. **状态下沉**: 尽量将复杂的 UI 逻辑抽离到专门的 Hooks 或 Store 中。
