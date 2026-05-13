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
- docs/API.md — 接口文档（如有）
- docs/DEPLOY.md — 部署运维手册（如有）

## 补充说明
- PRD.md / ARCHITECTURE_DESIGN.md / IMPLEMENTATION_ANALYSIS.md 于 2026-05-13 创建，为 Kotlin 原生时代核心三文档
- 超级助手（Super Assistant）决策见 ARCHITECTURE.md ADR-001
- MARKDOWN_RENDERING_AUDIT.md 与 IMPLEMENTATION_PLAN.md 于 2026-05-12 创建
- DOCUMENT_GOVERNANCE.md 于 2026-05-13 创建，记录文档体系清理与统一
- RN 时代的 `.agent/docs/PRODUCT_REQUIREMENTS.md`、`.qoder/repowiki/` 等已清理
- 不再使用 Qoder repowiki 自动文档生成系统；DIA 机制 + 手工维护即足够
