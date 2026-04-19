# 文档维护流程（repowiki SSOT 体系）

> **版本**: v2.0 (2026-04-20)
> **核心原则**: `.qoder/repowiki/` 是项目架构文档的**唯一事实源 (SSOT)**，由工具基于代码库自动生成，始终保持同步。

---

## 1. 文档体系分层

| 层级 | 位置 | 维护方式 | 内容 |
|------|------|---------|------|
| **自动生成层** | `.qoder/repowiki/zh/content/` | 工具自动生成 | 架构设计、数据模型、接口定义、组件库、子系统详解（145+ 篇） |
| **指针层** | `.agent/docs/*.md` | 仅保留指向 repowiki 的引用 | 已覆盖的核心文档（CODE_STRUCTURE、CORE_INTERFACES、DATA_SCHEMA、UI_KIT、NATIVE_BRIDGE_DEFENSE、ANDROID_BUILD_GUIDE） |
| **手工维护层** | `.agent/docs/*.md` | 人工维护 | 操作 SOP、审计报告、优化方案、PRD 等含人工决策上下文的文档 |

---

## 2. 自动生成层（repowiki）维护

### 2.1 何时触发重新生成

- 重大架构变更（新增/删除核心模块）
- 数据库 schema 变更
- 新增核心依赖或技术栈升级
- 功能模块大幅重构

### 2.2 repowiki 覆盖范围

repowiki 当前覆盖以下 19 个子系统：

- 项目概述、核心架构设计、架构文档
- 聊天系统详解、状态管理、数据库设计
- UI 组件系统、服务层设计、工具库和实用程序
- MCP 协议集成、RAG 知识引擎、智能代理系统
- 多提供商模型集成、本地推理引擎
- Web 客户端、Workbench 远程管理
- 部署与运维、API 参考、开发指南

### 2.3 引用规范

四端 Agent 在需要查阅架构信息时，**必须优先检索 repowiki**，而非 `.agent/docs/` 下的手工文档。

---

## 3. 手工维护层文档清单

以下文档包含人工决策上下文或操作流程，repowiki 无法自动生成，需人工维护：

| 文件 | 内容 | 更新触发条件 |
|------|------|-------------|
| `DOCS_MAINTENANCE.md` | 本文件（维护流程） | 文档体系变更时 |
| `RELEASE_PROTOCOL.md` | Android Release 编译 SOP | 构建流程变更时 |
| `PRODUCT_REQUIREMENTS.md` | 产品需求文档 (PRD) | 版本发布、功能规划变更时 |
| `audit-report-final.md` | 审计报告 | 新一轮审计时 |
| `comprehensive-defect-analysis.md` | 缺陷分析报告 | 新一轮审计时 |
| `industry-comparison-report.md` | 行业对比分析 | 需要重新对标时 |
| `artifacts-optimization-plan.md` | Artifacts 优化方案 | 实施进度变更时 |
| `artifacts-upgrade-plan.md` | Artifacts 升级计划 | 实施进度变更时 |

---

## 4. 指针层文档列表

以下文件已替换为指向 repowiki 的指针，**不要在这些文件中写入实质内容**：

| 指针文件 | repowiki 目标 |
|---------|--------------|
| `CODE_STRUCTURE.md` | `核心架构设计/整体架构设计.md` |
| `CORE_INTERFACES.md` | `架构文档/核心接口.md` |
| `DATA_SCHEMA.md` | `架构文档/数据架构.md` |
| `UI_KIT.md` | `架构文档/UI组件库.md` |
| `NATIVE_BRIDGE_DEFENSE.md` | `架构文档/原生桥接防护.md` |
| `ANDROID_BUILD_GUIDE.md` | `部署与运维/构建配置.md` |

---

## 5. 不再使用的目录

以下目录已清理，**不要重新创建**：

- ~~`plans/`~~ — 历史实施计划，repowiki 已覆盖
- ~~`todos/`~~ — 历史待办事项，应使用 GitHub Issues 或 `.agent/queue/`
- ~~`archive/`~~ — 历史归档，Git 历史即可回溯
- ~~`architecture/`~~ — 深度架构文档，repowiki 已全面覆盖

---

## 6. 变更检查清单

当发生代码变更时，按以下流程判断是否需要更新文档：

1. **架构/接口/数据模型变更** → 触发 repowiki 重新生成
2. **新功能上线** → 更新 `PRODUCT_REQUIREMENTS.md`
3. **构建/发布流程变更** → 更新 `RELEASE_PROTOCOL.md`
4. **新增人工决策** → 创建独立报告文档（如 `audit-*.md`、`*-plan.md`）
5. **文档体系自身变更** → 更新本文件
