# 文档注册表

## 核心文档（始终同步，不可跳过）
- CHANGELOG.md — 版本变更记录
- README.md — 项目概览（v2.0.0-beta, 92% 完成度）
- .agent/handover.md — 跨会话交接

## 按需文档
- docs/PRD.md — 产品需求文档 v2.0（进度已更新至 2026-05-15）
- docs/ARCHITECTURE_DESIGN.md — 全局架构设计（含 §2.4.1 KG 双模式策略）
- docs/IMPLEMENTATION_ANALYSIS.md — 当前实现分析（总体 92%）
- docs/ARCHITECTURE.md — 架构快速参考（含 ADR 索引，已至 ADR-012）
- docs/IMPLEMENTATION_PLAN.md — Markdown 渲染分阶段实施计划（P0+P1 已完成）
- docs/MARKDOWN_RENDERING_AUDIT.md — Markdown 渲染能力审计（6 项 ❌→✅）
- docs/DOCUMENT_GOVERNANCE.md — 文档治理方案
- docs/ADR/ — 架构决策记录
  - ADR-001: 取消 Super Assistant 概念
  - ADR-002: Embedding/Rerank 配置回退策略
  - ADR-003: 图像生成工具设计
  - ADR-004: 工具分类体系（被动注入/主动调用/MCP 动态三轨）
  - ADR-010: Provider 管理多路保存
  - ADR-011: 模型能力数据库 2026-04 更新
- docs/audit/ — 专项审计报告
  - RAG_SETTINGS_AUDIT_20260516.md — RAG 四页设置全量审计
  - PROVIDER_MANAGEMENT_AUDIT_20260516.md — 提供商管理系统全量审计
  - PROVIDER_MODELS_AUDIT_20260516.md — 服务商管理与模型管理全量架构审计
  - MODEL_DATABASE_RESEARCH_20260516.md — 模型能力数据库调研报告
  - EMBEDDING_RESOLUTION_DIAGNOSIS_20260516.md — 向量化 Embedding 配置解析失败合并诊断
  - RAG_INDICATOR_ARCHITECTURE_DESIGN_20260517.md — RAG 检索指示器架构审计与 UI 设计方案
  - RAG_INDICATOR_ACCEPTANCE_20260517.md — RAG 指示器 6 会话全量验收报告
  - RAG_KG_FULL_PIPELINE_AUDIT_20260517.md — RAG+KG 全链路审计报告（6 项发现）
  - IDEA_CROSS_VERIFICATION_20260517.md — IDEA 会话交叉验证报告
  - RAG_MEMORY_STORAGE_GAP_AUDIT_20260517.md — RAG 记忆存储链路缺口审计（G-1~G-4）
  - RAG_CONFIG_ARCHITECTURE_GAP_AUDIT_20260517.md — 高级检索配置架构缺口全链路审计（P0 配置断链 + 22 死字段）
  - 20260517-Gemini-Chat-UI-Audit-Consolidated-Execution-Report.md — 聊天界面渲染缺陷多维联合审计终极整合与无侵入重构执行报告
- docs/plans/RAG_INDICATOR_MULTI_SESSION_EXECUTION.md — RAG 指示器 6 会话并行执行方案

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
- C:/Users/lengz/.gemini/antigravity/brain/60254fc1-1574-4eae-ab08-36869e9a2b5d/implementation_plan.md — UI 细节微调（间距、图标颜色、菜单精简） ✅
- C:/Users/lengz/.gemini/antigravity/brain/66582369-ade2-4501-879a-f732188a092b/implementation_plan.md — 高级 RAG 重命名为知识图谱 ✅
- C:/Users/lengz/.gemini/antigravity/brain/616233d1-033a-45c9-8e1d-10e80c343e3d/implementation_plan.md — Embedding 跨提供商配置加载与响应式同步 ✅
- .agent/plans/20260517-skills-i18n-icons-fix.md — 工具管理国际化与 UI 细节深度优化减法 ✅
- .agent/plans/20260517-rag-neon-microrail.md — 方案二多段极细霓虹导电轨 RAG 指示器重构 💡
- .agent/plans/20260518-agent-tool-fallback-and-workspace-icon-refactoring.md — Agent工具Fallback兜底防线与工作区图标优化方案 ✅
- .agent/plans/AUDIT_AGENT_TOOL_FALLBACK_20260518.md — Agent工具Fallback解析方案设计与DeepSeek审计存档 ✅


## 归档计划（.agent/plans/archive/）
已完成的所有 Phase 2-6 计划文件（22 个），留存备查。

## 关键指标 (2026-05-17)
- Kotlin 源文件: ~310 个
- Room Entity: 18 个（TaskNodeEntity）
- Repository 覆盖率: 12/12 (100%)（新增 TaskRepository）
- 内置 Skill: 17 个（新增 4 个任务规划 Skill）
- 测试文件: 44 个
- 总体进度: 97%
- 剩余: Phase 10 发布准备（编译清零/APK 签名/E2E 测试）
