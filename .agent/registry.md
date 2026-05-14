# 文档注册表

## 核心文档（始终同步，不可跳过）
- CHANGELOG.md — 版本变更记录
- README.md — 项目概览
- .agent/handover.md — 跨会话交接

## 按需文档
- docs/PRD.md — 产品需求文档 v2.0（Kotlin 原生时代）
- docs/ARCHITECTURE_DESIGN.md — 全局架构设计（理想架构 + CMP 路线）
- docs/IMPLEMENTATION_ANALYSIS.md — 当前实现分析与开发进度
- docs/ARCHITECTURE.md — 架构快速参考（含 ADR 索引）
- docs/IMPLEMENTATION_PLAN.md — 分阶段实施计划
- docs/MARKDOWN_RENDERING_AUDIT.md — Markdown 渲染能力审计与行业对齐
- docs/CLEANUP_PLAN.md — 项目目录清理方案（RN 残余清理记录）
- docs/DOCUMENT_GOVERNANCE.md — 文档治理方案
- docs/ADR/ — 架构决策记录（每项一个文件）
  - ADR-001: 取消 Super Assistant 概念
  - ADR-002: Embedding/Rerank 配置回退策略
  - ADR-003: 图像生成工具设计 (image-generation-tool.md)
- docs/API.md — 接口文档（如有）
- docs/DEPLOY.md — 部署运维手册（如有）

## 活跃实施计划（.agent/plans/）
- .agent/plans/20260514-输入持久化后台生成思考自动展开.md — 输入持久化 + 后台生成 + 思考自动展开方案（Phase I ✅，Phase II 待定）
- .agent/plans/20260514-prompt-editor-agentvisual.md — 会话级提示词 + Markdown 编辑器 + 助手设置视觉美化（3 并行会话，待实施）

## 归档计划（已完成，留存备查）
- .agent/plans/20260514-phase6-features-tests.md — Phase 6 测试补缺 + 功能增强
- .agent/plans/20260514-auto-scroll.md — 智能视角追踪
- .agent/plans/20260513-phase5-usecase-extraction.md — Phase 5 UseCase 层抽取
- .agent/plans/20260513-phase4-engine-enhancement.md — Phase 4 核心引擎增强
- .agent/plans/20260513-phase3-super-assistant-cleanup.md — Super Assistant 清理
- .agent/plans/20260513-phase2c-remaining-vm-migration.md — 剩余 ViewModel 迁移
- .agent/plans/20260513-viewmodel-migration-tests.md — ViewModel 迁移 + 单元测试
- .agent/plans/20260513-domain-repository-implementation.md — Domain + Repository 层实施
- .agent/plans/20260513-fix-plan-prompts.md — RAG 修复计划
- .agent/plans/20260513-standardize-model-selectors.md — 模型选择标准化
- .agent/plans/20260512-provider-model-parallel-audit.md — Provider 模型审计
- .agent/plans/repair-roadmap-20260511.md — 修复路线图
- .agent/plans/20260511-chat-session-management.md — 聊天会话管理
- .agent/plans/20260511-rendering-upgrade.md — 渲染升级
- .agent/plans/20260510-*.md — RN→Kotlin 迁移期（12 个早期文件）

## 补充说明
- PRD.md / ARCHITECTURE_DESIGN.md / IMPLEMENTATION_ANALYSIS.md 于 2026-05-13 创建，2026-05-14 全面更新至当前状态
- 超级助手（Super Assistant）已于 Phase 3 (2026-05-13) 完成清理，见 ADR-001
- Domain 层已于 Phase 5 (2026-05-13) 抽取完成（9 Repository 接口 + 6 UseCase）
- Repository 覆盖率：9/9（100%），架构债 AD-1/AD-2/AD-4 已于 2026-05-13 全部消除
- Kotlin 源文件从 235 增长至 ~300，19 个 Room Entity 保持不变
- 不再使用 Qoder repowiki 自动文档生成系统；DIA 机制 + 手工维护即足够
