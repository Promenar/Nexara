---
name: update-readme
overview: 重写 README.md，基于项目实际代码扫描结果，用中英双语客观介绍 Nexara 项目，英文在前中文在后，只写重点，不堆砌技术细节和营销形容词。
todos:
  - id: rewrite-readme
    content: 重写 README.md 为 Nexara 项目，中英双语客观介绍
    status: completed
---

## 用户需求

重写 /home/lengz/Codex/Nexara/README.md，将旧的 "NeuralFlow" 更新为当前项目名 "Nexara"。

## 约束条件

1. 技术栈只写重点，不列太多细节
2. 不使用浮夸的营销形容词，客观介绍项目
3. 中英双语，英文在前，中文在后
4. 基于已完成的代码库全量扫描结果编写

## 已确认的项目事实

- 项目名: Nexara (package: com.promenar.nexara)
- 版本: 1.2.75, versionCode 137
- 协议: GPLv3
- 移动端: Expo SDK 54 + React Native 0.81.5 (New Architecture)
- Web 面板: 独立 Vite + React 18 项目，通过 WebSocket 连接移动端
- 状态管理: Zustand 5, 数据库: op-sqlite (SQLite + FTS5 + 向量存储)
- 本地推理: llama.rn (三槽位: main/embedding/rerank)
- 支持 12+ AI 服务商，RAG 知识引擎，Agent 系统，MCP 协议，Workbench 远程管理

## 技术方案

纯文档任务，修改单个文件 `/home/lengz/Codex/Nexara/README.md`。

基于代码库扫描结果，按以下结构组织内容：

1. 项目标题 + 简介徽章
2. 项目简介（中英双语）
3. 核心特性（按功能模块分块，中英双语）
4. 技术栈（精简版，只列关键选型）
5. 快速开始（构建步骤）
6. Roadmap / 待完成项（UI 交互打磨、中文原生 Markdown 排版优化、部分 Provider 未经测试）
7. 许可证

技术栈精简原则：只保留框架、语言、状态管理、数据库、动画库等关键选型，省略次要依赖。