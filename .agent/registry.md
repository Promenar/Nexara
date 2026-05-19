# 文档注册表

## 核心文档（始终同步，不可跳过）
- CHANGELOG.md — 版本变更记录
- README.md — 项目概览（v1.0.0, 98% 完成度）
- .agent/handover.md — 跨会话交接
- AGENTS.md — 开发者与 AI Agent 协同开发规范

## 按需文档
- docs/PRD.md — 产品需求文档 v2.0（进度已更新至 2026-05-15）
- docs/ARCHITECTURE_DESIGN.md — 全局架构设计（含 §2.4.1 KG 双模式策略）
- docs/ARCHITECTURE.md — 架构快速参考（含 ADR 索引，已至 ADR-018）
- docs/IMPLEMENTATION_ANALYSIS.md — 当前实现分析（总体 98%）
- docs/DOCUMENT_GOVERNANCE.md — 文档治理方案（v2.0, 2026-05-19 更新）
- native-ui/AGENTS.md — Kotlin 迁移技术规范（归档参考用）

## 架构决策记录（docs/ADR/）
- ADR-001: 取消 Super Assistant 概念
- ADR-002: Embedding/Rerank 配置回退策略
- ADR-003: 图像生成工具设计
- ADR-004: 后台生成架构（GenerationService 计划中，待实施）
- ADR-005: NexaraPageLayout 架构重构
- ADR-006: 数据库架构一致性校验修复
- ADR-007: RAG 知识库现代化改造
- ADR-008: RAG 可观测性增强
- ADR-009: 提示词编辑器原子化标准化
- ADR-010: Provider 管理多路保存
- ADR-011: 模型能力数据库 2026-04 更新
- ADR-012: Embedding 跨提供商配置解析架构
- ADR-013: WebView 生命周期管理 — 测高 WebViewClient 前置绑定（2026-05-18）
- ADR-014: 工具调用系统架构移植 — 基于 Cherry-Studio 参考实现（2026-05-18）
- ADR-015: Nexara Metro 调试桥系统 Phase 1（2026-05-18）
- ADR-016: CancellationException 传播模式与 channelFlow 生命周期规范（2026-05-18）
- ADR-017: 知识图谱可视化 176+ 大数据量防崩溃与性能优化（2026-05-18）
- ADR-018: 极致原生化 Jetpack Compose Canvas 知识图谱引擎演进（2026-05-18）

## 专项审计报告（docs/audit/）
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
- RAG_CONFIG_ARCHITECTURE_GAP_AUDIT_20260517.md — 高级检索配置架构缺口全链路审计
- 20260517-Gemini-Chat-UI-Audit-Consolidated-Execution-Report.md — 聊天界面渲染缺陷多维联合审计终极整合
- 20260517-Gemini+Opus4.6-Chat-UI-Audit.md — Gemini+Opus4.6 联合审计原始报告
- DeepSeekV4-RENDER_DEFECT_AUDIT_20260517.md — DeepSeekV4 渲染缺陷审计报告
- 20260515-rag-parameter-audit.md — RAG 参数全链路审计报告
- XML_RENDERER_BUG_AUDIT_20260518.md — XML/HTML 预览卡片自适应失效故障诊断
- GLM_CHAT_TOOL_THINKING_RENDERING_AUDIT_20260517.md — GLM 聊天工具思考渲染审计
- MiniMax-聊天界面渲染缺陷静态审计报告_20260517.md — MiniMax 渲染缺陷静态审计
- 20260512-markdown-rendering-audit.md — Markdown 渲染能力审计（6 项全部修复）

## 活跃实施计划（.agent/plans/）
- .agent/plans/20260519-room-database-migration-schema-mismatch-fix.md — Room 数据库迁移 Schema 不匹配修复 ✅
- .agent/plans/20260514-phase7-knowledge-base-repair.md — Phase 7 知识库修复与增强 ✅
- .agent/plans/20260514-phase8-agent-tools-enhancement.md — Phase 8 Agent 工具重构 ✅
- .agent/plans/20260515-phase9-polish-and-tests.md — Phase 9 发布冲刺 + 测试 ✅
- .agent/plans/20260514-prompt-editor-agentvisual.md — 会话提示词编辑器 + Agent 视觉美化（提示词编辑器✅，视觉美化🟡）
- .agent/plans/20260515-unified-resource-os-execution.md — 统一资源 OS 多会话并行执行计划 ✅
- .agent/plans/20260515-ResourceManagerArchitecture.md — 全局资源管理器架构设计方案
- .agent/plans/20260515-task-planning-tool-architecture.md — 任务规划器完整架构设计 v3.4 ✅
- .agent/plans/20260515-TaskPlanningToolArchitecture.md — 任务规划工具 V2 架构设计方案
- .agent/plans/20260515-task-planner-execution.md — 任务规划器多会话并行执行计划 ✅
- .agent/plans/20260515-protocol-refactor-plan.md — 协议参数透传与 RAG UX 修复执行方案
- .agent/plans/20260517-skills-i18n-icons-fix.md — 工具管理国际化与 UI 细节深度优化减法 ✅
- .agent/plans/20260517-rag-neon-microrail.md — RAG 指示器重构 💡
- .agent/plans/20260517-dialog-unification.md — 统一危险操作删除二次确认弹窗实施计划
- .agent/plans/20260517-rag-indicator-execution.md — RAG 指示器 6 会话并行执行方案
- .agent/plans/20260519-toolchain-argument-double-accumulation-fix.md — 工具链参数双重累积与错误处理修复 ✅
- .agent/plans/20260518-agent-tool-fallback-and-workspace-icon-refactoring.md — Agent 工具 Fallback 兜底防线 ✅
- .agent/plans/AUDIT_AGENT_TOOL_FALLBACK_20260518.md — Agent 工具 Fallback 解析方案审计 ✅
- .agent/plans/20260518-NexaraMetroDebuggerFix.md — 调试桥诊断与超时修复
- .agent/plans/20260518-NexaraDynamicTimeoutFix.md — 知识图谱抽取超时可配置化
- .agent/plans/20260519-model-settings-rag-indicator-fix.md — 模型设置与 RAG 指示器修复
- .agent/plans/20260519-ui-consistency-settings.md — UI 一致性设置优化
- .agent/plans/20260519-xml-fallback-constraint-and-prompt-optimization.md — XML 回退约束与提示词优化
- .agent/plans/20260517-rag-indicator-glow-refactor.md — RAG 指示器发光效果重构

## 已归档计划（.agent/plans/archive/）
已完成的所有 Phase 2-6 计划文件（22 个）+ 本轮归档（1 个），留存备查。
- .agent/plans/archive/20260512-markdown-rendering-plan.md — Markdown 渲染分阶段实施计划（P0+P1+P2 已完成）

## 已归档文档（docs/archive/）
- docs/archive/CLEANUP_PLAN.md — RN 残余清理记录（2026-05-13 已完成）

## 其他参考文档
- .agent/checklists/CODE_REVIEW.md — 代码评审清单

## 关键指标 (2026-05-19)
- Kotlin 源文件: ~342 个
- Room Entity: 18 个
- Repository 覆盖率: 12/12 (100%)
- 内置 Skill: 18 个
- 测试文件: 58 个
- 总体进度: 98%
- 剩余: 后台生成服务 (GenerationService) + 发布会准备

## DIA 清理记录
### 2026-05-19 本轮文档优化
- **移动** `docs/IMPLEMENTATION_PLAN.md` → `.agent/plans/archive/20260512-markdown-rendering-plan.md`
- **移动** `docs/MARKDOWN_RENDERING_AUDIT.md` → `docs/audit/20260512-markdown-rendering-audit.md`
- **移动** `docs/plans/RAG_INDICATOR_MULTI_SESSION_EXECUTION.md` → `.agent/plans/20260517-rag-indicator-execution.md`
- **移除** `docs/plans/` 空目录
- **更新** `docs/DOCUMENT_GOVERNANCE.md` → v2.0 反映当前状态

### 2026-05-18 全站清理
- **合并** native-ui/.agent/ → 根 .agent/plans/：5 个 unique plans + 3 个 audit 文档迁移至 docs/audit/
- **合并** native-ui/docs/ → 根 CHANGELOG.md：追加 7 条唯一变更记录
- **删除** native-ui/.agent/ 和 native-ui/docs/ 目录（共 13 文件）
- **结果**：全站文档统一为根级 .agent/ + docs/ + CHANGELOG.md 三根体系，零重复
