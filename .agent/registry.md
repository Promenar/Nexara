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
- .agent/plans/20260513-domain-repository-implementation.md — Domain + Repository 层实施方案（4 个并行会话，已完成）
- .agent/plans/20260513-viewmodel-migration-tests.md — ViewModel 迁移至 Repository + 单元测试（3 个并行会话，已完成）
- .agent/plans/20260513-phase2c-remaining-vm-migration.md — 剩余 3 个 ViewModel 迁移（已完成）
- .agent/plans/20260513-phase3-super-assistant-cleanup.md — Super Assistant 清理（ADR-001，已完成）
- .agent/plans/20260513-phase4-engine-enhancement.md — Phase 4 核心引擎增强（已完成）
- .agent/plans/20260513-phase5-usecase-extraction.md — Phase 5 UseCase 层抽取（已完成）
- .agent/plans/20260514-phase6-features-tests.md — Phase 6 测试补缺 + 功能增强（已完成）
- .agent/plans/20260514-输入持久化后台生成思考自动展开.md — 输入持久化 + 后台生成 + 思考自动展开方案（Phase I 已完成，Phase II 待定）

## 补充说明
- PRD.md / ARCHITECTURE_DESIGN.md / IMPLEMENTATION_ANALYSIS.md 于 2026-05-13 创建，为 Kotlin 原生时代核心三文档
- 超级助手（Super Assistant）决策见 ARCHITECTURE.md ADR-001
- Domain 层 + Repository 层已于 2026-05-13 实施完成（28 个文件，编译通过）
- Repository 覆盖率：7/7（100%），架构债 AD-2 已消除
- 不再使用 Qoder repowiki 自动文档生成系统；DIA 机制 + 手工维护即足够
