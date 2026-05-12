# Nexara 交接文档

## 状态摘要 (2026-05-13)
完成了 Markdown 渲染、RAG 知识库系统、RAG 指示器的全栈审计，发现 3 个 P0 级致命缺陷。已生成 6 个独立会话修复提示词，准备分 Phase 0/1 并行执行。

## 已完成工作 (Done)

### 1. 品牌资产与 UI 优化 (上一会话)
- **品牌图标实装**：为所有 9 种协议实装了本地单色矢量图标。
- **UI 对齐修复**：修正了全局 `NexaraPageLayout` 及主页面标题对齐问题。
- **崩溃修复**：修复了 `ProtocolSelector` 嵌套滚动闪退。
- **预设重构**：统一了 `PROVIDER_PRESETS` 数据结构。

### 2. 全栈审计 (本次会话)
- **Markdown 渲染审计**：确认语法覆盖 90%+，发现 P1 级流式缓存边界、trimIndent/CJK 间距污染问题。
- **RAG 系统审计**：确认向量入库全链路工作正常，但发现 **P0 级致命 Bug**：`contextResult.ragReferences` 从未写入 Message 模型，导致引用指示器永远不可见。
- **RAG 指示器审计**：UI 设计完善，但数据断层导致引用态永不触发。
- **修复计划输出**：生成了 6 个独立会话提示词（`.agent/plans/20260513-fix-plan-prompts.md`）。

## 待办事项 (Next Steps)
- 🔴 [P0] **Session-A**：修复 ChatViewModel.kt — RAG 引用写入 Message（最高优先级，8 行代码）
- 🔴 [P0] **Session-B**：EmbedingClient.kt — 本地降级方案
- 🔴 [P0] **Session-C**：VectorStore.kt — 维度不匹配告警
- 🟡 [P1] **Session-D**：MarkdownText.kt — 综合修复（流式缓存+trimIndent+CJK+崩溃降级）
- 🟡 [P1] **Session-E**：DocumentImporter.kt — PDF/Word 格式支持
- 🟡 [P1] **Session-F**：GlobalRagConfigScreen + ChatInlineComponents — 阈值滑块 + 指示器优化
- [P2] 测试文件重构、模型能力维度补完（延续上一会话未完成项）

## 风险与注意点 (Risks)
- **RAG 静默失效**：如果 API 密钥未配置，整个 RAG 系统静默失败且用户无感知（P0-2 修复前）。
- **向量维度兼容**：更换 Embedding 模型会导致所有旧向量被静默跳过（P0-3 修复前）。
- **嵌套滚动约束**：在 Compose 中继续警惕 `LazyColumn` 与 `verticalScroll` 的嵌套。
- **文件冲突**：6 个 Session 修改的文件完全不重叠，全部可并行执行。

## DIA Status
- 审计报告: `docs/ARCHITECTURE.md` 无结构变更，无需更新
- 修复计划: `.agent/plans/20260513-fix-plan-prompts.md` 已存档

## DIA Status
- **registry.md**: 已检查
- **CHANGELOG.md**: 已同步 (根目录 & native-ui)
- **ARCHITECTURE.md**: 架构图需更新以包含 `ProtocolFactory` 与新图标资产。
