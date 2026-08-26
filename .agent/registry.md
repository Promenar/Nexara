# 文档注册表

## 核心文档（始终同步，不可跳过）
- CHANGELOG.md — 版本变更记录
- README.md — 当前 `v0.2.1-beta` 候选概览、同签名覆盖升级、Android 12+、侧载/密钥/后台生成/GGUF/Metro TUI 边界
- .agent/handover.md — 跨会话交接
- AGENTS.md — 开发者与 AI Agent 协同开发规范

## 按需文档
- design-qa.md — DocEditor、会话任务进度面板、设置全层级及 Agent 首页身份列表的 MD3/SE 源图、实现对比、视觉复核与自动化验收记录
- .agent/plans/20260803-se-settings-visual-system.md — 设置首页及二、三、四级页面向 Solid Explorer 连续列表视觉语言收敛的实施与验收计划（底部三按钮导航零改动）
- docs/superpowers/specs/2026-07-16-nexara-md3-redesign-design.md — Nexara 全站 Material 3 视觉体系重设计契约（方案 3、稳定 M3 基线、Nexara 专属表达、分阶段迁移与 UI 验收）
- docs/superpowers/plans/2026-07-16-nexara-md3-phase1-foundation-chat.md — Nexara Material 3 第一阶段实施计划（稳定主题基线、会话 composer、思考轨迹、IME/无障碍与截图验收）
- docs/superpowers/plans/2026-07-16-nexara-md3-phase2-management.md — Nexara Material 3 第二阶段管理母版实施计划（设置列表、Provider、表单、模型渐进披露与视觉/设备门禁）
- docs/superpowers/plans/2026-07-16-nexara-md3-phase3-rag-resources.md — Nexara Material 3 第三阶段知识库与资源管理实施计划（状态原语、文件树、Memory、RAG 详情、资源管理器与回收站）
- docs/superpowers/plans/2026-07-16-nexara-md3-phase4-doceditor.md — Nexara Material 3 第四阶段 DocEditor 实施计划（保存/重命名/索引可靠性、长文档性能、视觉、响应式与无障碍）
- docs/superpowers/specs/2026-07-20-nexara-md3-convergence-design.md — Nexara Material 3 交互、设置与主题总收敛规格（导航、附件、搜索、模型选择、完整设置层级、浅色/系统/动态主题与视觉门禁）
- docs/superpowers/plans/2026-07-20-nexara-md3-convergence.md — Nexara Material 3 总收敛 14-Task 实施计划（TDD、逐任务提交、完整 UI 主题迁移、API 31/35/36、性能、截图、TalkBack 与发行收口）
- docs/superpowers/plans/2026-07-17-model-catalog-and-chat-metadata.md — 分层模型元数据注册中心、离线目录供应链、Provider/引导迁移、友好名称统一与 AI 消息尾注左对齐实施计划
- docs/superpowers/specs/2026-07-20-chat-full-context-documents-and-branching.md — 输入栏 TXT/MD 完整上下文、路由后预算硬门禁、可回传导出与消息分支冻结规格
- docs/superpowers/plans/2026-07-20-chat-full-context-documents-and-branching.md — 会话完整文档、草稿时序、导出、分支、设备与发行验证实施计划
- docs/superpowers/specs/2026-08-24-nexara-release-contract-repair.md — Provider/Tool/MCP、会话工作区、备份、RAG/KG 与发行升级的当前冻结合同
- docs/superpowers/plans/2026-08-24-nexara-release-contract-repair.md — 当前发行合同修复、独立审计、全量验证和签名 APK 交付计划
- docs/superpowers/specs/2026-07-12-v0.2-beta-release-readiness-design.md — v0.2-beta GitHub 侧载发行整改设计（安全、数据、业务、后台生成、UI/E2E 与发行门禁）
- docs/superpowers/plans/2026-07-12-v0.2-beta-release-roadmap.md — v0.2-beta 四阶段发行整改主路线与门禁顺序
- docs/superpowers/plans/2026-07-12-v0.2-beta-phase1-security-data.md — P0 密钥、日志、网络、Room、备份与 WebDAV 实施计划
- docs/superpowers/plans/2026-07-12-v0.2-beta-phase2-core-business-flows.md — P1 Provider、工具幂等、工作区、索引、KG 与分享导入实施计划
- docs/superpowers/plans/2026-07-12-v0.2-beta-phase3-background-ui.md — P2 后台生成、首次引导、双语、自适应、无障碍与视觉回归实施计划
- docs/superpowers/plans/2026-07-12-v0.2-beta-phase4-release-engineering.md — P3 CI、签名、冷安装、文档与 GitHub Release 实施计划
- docs/release/v0.2-beta.md — 2026-07-29 已发布 GitHub prerelease 的历史发行正文
- docs/release/v0.2-beta-validation.md — 发行事实账本；记录设备/UI/真实 API/分支 CI、本地稳定证书签名 R8 与双版本冷安装证据，并区分待执行的 TalkBack 真机、核心业务人工验收与 tag release workflow
- docs/release/v0.2.1-beta.md — 当前本地签名候选发行说明、覆盖升级方法与已知边界；不构成 GitHub 发布授权
- docs/release/v0.2.1-beta-validation.md — 当前候选全量测试、签名、冷装、v0.1→v0.2.1 数据继承与外部门禁事实账本
- docs/PRD.md — 产品需求文档 v2.0（进度已更新至 2026-05-15）
- docs/ARCHITECTURE_DESIGN.md — 全局架构设计（含 §2.4.1 KG 双模式策略）
- docs/ARCHITECTURE.md — 架构快速参考（含 ADR 索引，已至 ADR-021）
- docs/ADR/ADR-019-transactional-workspace-indexing.md — 工作区文件、版本化索引目标、删除屏障、补偿重试与进程恢复的事务候选切换决策
- docs/ADR/ADR-020-layered-model-metadata-registry.md — 分层模型元数据来源、逐字段优先级、离线快照、三态能力、精确匹配、用户覆盖与回滚决策
- docs/ADR/ADR-021-chat-full-context-documents-and-branching.md — 完整文档消息快照、路由后预算门禁、可靠重试、可回传导出与稳定消息分支决策
- docs/legal/THIRD_PARTY_NOTICES.md — models.dev 离线模型目录与 Bettbox Material 3 界面转译的来源、固定提交、许可及使用边界
- docs/IMPLEMENTATION_ANALYSIS.md — 当前实现分析（总体 98%）
- docs/DOCUMENT_GOVERNANCE.md — 文档治理方案（v2.0, 2026-05-19 更新）
- native-ui/AGENTS.md — Kotlin 迁移技术规范（归档参考用）

## 架构决策记录（docs/ADR/）
- ADR-001: 取消 Super Assistant 概念
- ADR-002: Embedding/Rerank 配置回退策略
- ADR-003: 图像生成工具设计
- ADR-004: 后台生成架构（GenerationCoordinator + Foreground Service 已实施；发行级设备矩阵待最终验收）
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
- ADR-019: 工作区文件、派生索引与删除恢复采用事务候选切换（2026-07-13）
- ADR-020: 分层模型元数据注册中心（2026-07-18）
- ADR-021: 会话完整文档上下文与消息分支（2026-07-20）

## 专项审计报告（docs/audit/）
- 20260706-final-business-flow-cross-audit.md — APP 业务流程终版交叉审计报告（整合 GLM-5.2 并行审计与 Codex 原审计，作为修复排期主入口）
- 20260706-business-flow-full-code-audit.md — Codex APP 业务流程完整代码审计报告（对话/API/RAG/KG/知识库/工具/Skill/工作区/任务/UI 可观测性）
- 20260706-fullstack-business-audit.md — APP 全栈业务流程审计报告（2026-07-06，7 模块并行 + 交叉核验，子报告在 .agent/tmp-agent-reports/tmp-audit-module-{a..g}.md）
- RAG_SETTINGS_AUDIT_20260516.md — RAG 四页设置全量审计
- PROVIDER_MANAGEMENT_AUDIT_20260516.md — 提供商管理系统全量审计
- PROVIDER_MODELS_AUDIT_20260516.md — 服务商管理与模型管理全量架构审计
- MODEL_DATABASE_RESEARCH_20260516.md — 模型能力数据库调研报告（保留 2026-05-16 原文，并追加 2026-07-17 结构化目录勘误）
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
- .agent/plans/20260520-techdebt-multi-flavor-visual-isolation.md — 多渠道 Gradle Build Flavor 视觉物理隔离设计（技术债务已归档 💡）

## 已归档计划（.agent/plans/archive/）
已完成的所有 Phase 2-6 计划文件（22 个）+ 本轮归档（1 个），留存备查。
- .agent/plans/archive/20260512-markdown-rendering-plan.md — Markdown 渲染分阶段实施计划（P0+P1+P2 已完成）

## 已归档文档（docs/archive/）
- docs/archive/CLEANUP_PLAN.md — RN 残余清理记录（2026-05-13 已完成）

## 其他参考文档
- .agent/checklists/CODE_REVIEW.md — 代码评审清单

## 关键指标与发行门禁 (2026-07-13)
- 主源码 Kotlin 文件: 337 个
- JVM 测试 Kotlin 文件: 130 个
- Android 设备测试 Kotlin 文件: 12 个
- Room Entity: 18 个
- Repository 覆盖率: 12/12 (100%)
- 内置 Skill: 18 个
- P0/P1 基线: 已完成；基线证据为 JVM 1232（0 失败，14 跳过）、Android 28（0 失败，2 跳过）、Lint 0 Error/Fatal、Debug APK 与备份/恢复多阶段流程。
- P2 当前状态: 后台持续生成、结构化错误/取消、RAG/Prompt、资源树/设置与协议专项实现已完成；Material 3 第四阶段可靠性检查点已完成版本化索引、删除屏障、跨页面补偿与 KG 冷恢复，1872 JVM、AndroidTest 编译和 Lint 通过。DocEditor 长文档性能、视觉基线、API 31/35/36 设备矩阵及最终签名 APK 的 TalkBack 人工听觉/全焦点遍历仍待后续门禁。
- P3 当前状态: SSH 签名 annotated tag `v0.2-beta` 已由 GitHub 验证为 `valid` 并固定提交 `a7c94f03`；Release workflow run `30420324239` 的 API 31/35/36 deviceTest、API 35/36 minifiedTest、签名 R8 构建、双 API 无密钥冷安装和 prerelease 发布全部 PASS。GitHub Release 远端 APK/checksum 已独立下载回读，SHA-256 为 `59d641b6b8f04a15bc9f4ce064a7a116df3def4efabf8231feb408612246fe9a`。物理真机 TalkBack、完整焦点遍历和更广泛 OEM/IME 体验按用户授权作为 beta 后续验证项，不冒充 PASS；既有 `v0.1-beta` Release 保留。

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
