# 文档注册表

## 核心文档（始终同步，不可跳过）
- CHANGELOG.md — 版本变更记录
- README.md — 项目概览（v2.0.0-beta, 92% 完成度）
- .agent/handover.md — 跨会话交接
- AGENTS.md — 开发者与 AI Agent 协同开发规范

## 按需文档
- docs/PRD.md — 产品需求文档 v2.0（进度已更新至 2026-05-15）
- docs/ARCHITECTURE_DESIGN.md — 全局架构设计（含 §2.4.1 KG 双模式策略）
- docs/IMPLEMENTATION_ANALYSIS.md — 当前实现分析（总体 92%）
- docs/ARCHITECTURE.md — 架构快速参考（含 ADR 索引，已至 ADR-016）
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
  - ADR-012: Embedding 跨提供商配置解析架构
  - ADR-013: WebView 生命周期管理 — 测高 WebViewClient 前置绑定（2026-05-18）
  - ADR-014: 工具调用系统架构移植 — 基于 Cherry-Studio 参考实现（2026-05-18）
  - ADR-015: Nexara Metro 调试桥系统 (Phase 1)（2026-05-18）
  - ADR-016: CancellationException 传播模式与 channelFlow 生命周期规范（2026-05-18）
  - ADR-017: 知识图谱可视化 176+ 大数据量防崩溃与性能优化（2026-05-18）
  - ADR-018: 极致原生化 Jetpack Compose Canvas 知识图谱引擎演进（2026-05-18）
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
  - 20260517-Gemini+Opus4.6-Chat-UI-Audit.md — Gemini+Opus4.6 联合审计原始报告（从 native-ui 合并）
  - DeepSeekV4-RENDER_DEFECT_AUDIT_20260517.md — DeepSeekV4 渲染缺陷审计报告（从 native-ui 合并）
  - 20260515-rag-parameter-audit.md — RAG 参数全链路审计报告（从 native-ui 合并）
  - XML_RENDERER_BUG_AUDIT_20260518.md — XML/HTML 预览卡片自适应失效与按钮未上移故障诊断报告
- docs/plans/RAG_INDICATOR_MULTI_SESSION_EXECUTION.md — RAG 指示器 6 会话并行执行方案

## 已归档文档（docs/archive/）
- docs/archive/CLEANUP_PLAN.md — RN 残余清理记录（2026-05-13 已完成）

## 活跃实施计划（.agent/plans/）
- .agent/plans/20260519-room-database-migration-schema-mismatch-fix.md — Room 数据库迁移 Schema 不匹配闪退缺陷彻底根治设计与修复方案 ✅
- .agent/plans/20260514-phase7-knowledge-base-repair.md — Phase 7 知识库修复与增强 ✅
- .agent/plans/20260514-phase8-agent-tools-enhancement.md — Phase 8 Agent 工具重构 ✅
- .agent/plans/20260515-phase9-polish-and-tests.md — Phase 9 发布冲刺 + 测试 ✅
- .agent/plans/20260514-prompt-editor-agentvisual.md — 会话提示词编辑器 + Agent 视觉美化（待实施）
- .agent/plans/20260515-unified-resource-os-execution.md — 统一资源 OS 多会话并行执行计划 ✅
- .agent/plans/20260515-ResourceManagerArchitecture.md — 全局资源管理器架构设计方案（从 native-ui 合并）
- .agent/plans/20260515-task-planning-tool-architecture.md — 任务规划器完整架构设计 v3.4 ✅
- .agent/plans/20260515-TaskPlanningToolArchitecture.md — 任务规划工具 V2 架构设计方案（从 native-ui 合并）
- .agent/plans/20260515-task-planner-execution.md — 任务规划器多会话并行执行计划 ✅
- .agent/plans/20260515-protocol-refactor-plan.md — 协议参数透传与 RAG UX 修复执行方案（从 native-ui 合并）
- C:/Users/lengz/.gemini/antigravity/brain/60254fc1-1574-4eae-ab08-36869e9a2b5d/implementation_plan.md — UI 细节微调（间距、图标颜色、菜单精简） ✅
- C:/Users/lengz/.gemini/antigravity/brain/66582369-ade2-4501-879a-f732188a092b/implementation_plan.md — 高级 RAG 重命名为知识图谱 ✅
- C:/Users/lengz/.gemini/antigravity/brain/616233d1-033a-45c9-8e1d-10e80c343e3d/implementation_plan.md — Embedding 跨提供商配置加载与响应式同步 ✅
- .agent/plans/20260517-skills-i18n-icons-fix.md — 工具管理国际化与 UI 细节深度优化减法 ✅
- .agent/plans/20260517-rag-neon-microrail.md — 方案二多段极细霓虹导电轨 RAG 指示器重构 💡
- .agent/plans/20260517-dialog-unification.md — 统一危险操作删除二次确认弹窗实施计划（从 native-ui 合并）
- .agent/plans/20260519-toolchain-argument-double-accumulation-fix.md — 工具链参数双重累积与错误处理审计修复（P0-1+P0-2+P1-1+P1-2） ✅
- .agent/plans/20260518-agent-tool-fallback-and-workspace-icon-refactoring.md — Agent工具Fallback兜底防线与工作区图标优化方案 ✅
- .agent/plans/AUDIT_AGENT_TOOL_FALLBACK_20260518.md — Agent工具Fallback解析方案设计与DeepSeek审计存档 ✅
- .agent/plans/20260518-CherryStudio-ToolCall-Transplant-Design.md — Cherry-Studio 工具调用系统移植设计方案（从用户 globalStorage 同步）
- .agent/plans/20260518-Parallel-Session-Implementation-Plan.md — 工具调用系统并行独立会话实施方案（4 会话 + 共享契约）
- .agent/plans/20260518-NexaraMetroDebuggerFix.md — 调试桥诊断与 Ktor-OkHttp 超时及日志不完整性的终极修复实施方案
- .agent/plans/20260518-NexaraDynamicTimeoutFix.md — 知识图谱抽取超时时间可配置化与 RAG 全链路动态超时穿透设计方案
- C:/Users/lengz/.gemini/antigravity/brain/d8c20b3e-a41f-4c5c-afd0-ba01f5ac1f1a/implementation_plan.md — 提供商自定义模型参数配置保存缺陷与会话 RAG 指示器内联排版美化修复 ✅
## 归档计划（.agent/plans/archive/）
已完成的所有 Phase 2-6 计划文件（22 个），留存备查。

## 关键指标 (2026-05-19)
- Kotlin 源文件: ~315 个
- Room Entity: 18 个（TaskNodeEntity）
- Repository 覆盖率: 12/12 (100%)
- 内置 Skill: 18 个（新增 4 个任务规划 Skill + 1 个网页降噪抓取 WebFetch Skill）
- 测试文件: 45 个
- 总体进度: 98%
- 剩余: Phase 10 发布准备（编译清零/APK 签名/E2E 测试）

## 2026-05-18 DIA 全站清理记录
- **合并 native-ui/.agent/** → 根 `.agent/plans/`：5 个 unique plans + 3 个 audit 文档迁移至 `docs/audit/`
- **合并 native-ui/docs/** → 根 `CHANGELOG.md`：追加 7 条唯一变更记录
- **删除** `native-ui/.agent/` 和 `native-ui/docs/` 目录（共 13 文件）
- **结果**：全站文档统一为根级 `.agent/` + `docs/` + `CHANGELOG.md` 三根体系，零重复
