# 文档注册表

## 核心文档（始终同步，不可跳过）
- CHANGELOG.md — 版本变更记录
- README.md — 项目概览（v2.0.0-beta, 92% 完成度）
- .agent/handover.md — 跨会话交接

## 按需文档
- docs/PRD.md — 产品需求文档 v2.0（进度已更新至 2026-05-15）
- docs/ARCHITECTURE_DESIGN.md — 全局架构设计（含 §2.4.1 KG 双模式策略）
- docs/IMPLEMENTATION_ANALYSIS.md — 当前实现分析（总体 92%）
- docs/ARCHITECTURE.md — 架构快速参考（含 ADR 索引）
- docs/IMPLEMENTATION_PLAN.md — Markdown 渲染分阶段实施计划（P0+P1 已完成）
- docs/MARKDOWN_RENDERING_AUDIT.md — Markdown 渲染能力审计（6 项 ❌→✅）
- docs/DOCUMENT_GOVERNANCE.md — 文档治理方案
- docs/ADR/ — 架构决策记录
  - ADR-001: 取消 Super Assistant 概念
  - ADR-002: Embedding/Rerank 配置回退策略
  - ADR-003: 图像生成工具设计
  - ADR-004: 工具分类体系（被动注入/主动调用/MCP 动态三轨）

## 已归档文档（docs/archive/）
- docs/archive/CLEANUP_PLAN.md — RN 残余清理记录（2026-05-13 已完成）

## 活跃实施计划（.agent/plans/）
- .agent/plans/20260514-phase7-knowledge-base-repair.md — Phase 7 知识库修复与增强 ✅
- .agent/plans/20260514-phase8-agent-tools-enhancement.md — Phase 8 Agent 工具重构 ✅
- .agent/plans/20260515-phase9-polish-and-tests.md — Phase 9 发布冲刺 + 测试 ✅
- .agent/plans/20260514-prompt-editor-agentvisual.md — 会话提示词编辑器 + Agent 视觉美化（待实施）
- .agent/plans/20260515-unified-resource-os-execution.md — 统一资源 OS 多会话并行执行计划 ✅
- .agent/plans/20260515-task-planning-tool-architecture.md — 任务规划器完整架构设计 v3.4 ✅
- .agent/plans/20260515-task-planner-execution.md — 任务规划器多会话并行执行计划 ✅

## 归档计划（.agent/plans/archive/）
已完成的所有 Phase 2-6 计划文件（22 个），留存备查。

## 关键指标 (2026-05-15)
- Kotlin 源文件: ~300 个
- Room Entity: 17 个（移除 DocumentEntity、FolderEntity）
- Repository 覆盖率: 11/11 (100%)（新增 WorkspaceRepository、FileOperationRepository）
- 内置 Skill: 13 个
- 测试文件: 44 个
- 总体进度: 93%
- 剩余: Phase 10 发布准备（编译清零/APK 签名/E2E 测试）
