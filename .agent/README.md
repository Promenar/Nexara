# 📚 Nexara 知识库 (Knowledge Base)

> **版本**: v4.0 (2026-02-14)
> **状态**: 唯一的知识入口 (Single Source of Truth)
> **法则**: 任何代码变更必须同步更新对应文档。

---

## ⚡ 1. 核心地图 (The 4 Maps) 🔥

> **Agent 工作的绝对真理源**

| 文档 | 描述 | 维护频率 |
| :--- | :--- | :--- |
| **[CODE_STRUCTURE.md](./memory/CODE_STRUCTURE.md)** | **全局架构与核心机制** (目录/时序图/RAG触发器) | ⭐⭐⭐⭐⭐ |
| **[DATA_SCHEMA.md](./docs/DATA_SCHEMA.md)** | **数据真理源** (Store状态/Type定义/DB模式) | ⭐⭐⭐⭐⭐ |
| **[CORE_INTERFACES.md](./docs/CORE_INTERFACES.md)** | **服务契约** (LLM适配器/RAG接口) | ⭐⭐⭐⭐⭐ |
| **[UI_KIT.md](./docs/UI_KIT.md)** | **设计系统** (原子组件/颜色/排版) | ⭐⭐⭐⭐⭐ |

## 📐 2. 深度架构 (Deep Dives)

> **子系统详细设计**

| 文档 | 子系统 | 说明 |
| :--- | :--- | :--- |
| **[LLM_ARCHITECTURE.md](./docs/architecture/LLM_ARCHITECTURE.md)** | `lib/llm` | LLM 抽象层完整架构指引 |
| **[ARTIFACT_RENDERING.md](./docs/architecture/ARTIFACT_RENDERING.md)** | `Artifacts` | 工件渲染架构 (v2) - 图表与代码执行 |
| **[STEERABLE_LOOP.md](./docs/architecture/STEERABLE_LOOP.md)** | `AgentLoop` | 可控代理循环 (Auto/Semi/Manual) |
| **[SETTINGS_ARCHITECTURE.md](./docs/architecture/SETTINGS_ARCHITECTURE.md)** | `Settings` | 四层设置面板架构参考 |
| **[ANIMATION_SPECS.md](./docs/architecture/ANIMATION_SPECS.md)** | `UI` | 移动端过渡动画规范 |

## 🛠️ 3. 工程指南 (Engineering Guides)

> **构建、发布与防御**

| 文档 | 用途 |
| :--- | :--- |
| **[PRODUCT_REQUIREMENTS.md](./docs/PRODUCT_REQUIREMENTS.md)** | 产品需求规格 (PRD) |
| **[ANDROID_BUILD_GUIDE.md](./docs/ANDROID_BUILD_GUIDE.md)** | Android 构建指南 |
| **[RELEASE_PROTOCOL.md](./docs/RELEASE_PROTOCOL.md)** | 发布流程协议 |
| **[NATIVE_BRIDGE_DEFENSE.md](./docs/NATIVE_BRIDGE_DEFENSE.md)** | 原生桥接防御指南 (防死锁) |
| **[DOCS_MAINTENANCE.md](./docs/DOCS_MAINTENANCE.md)** | 文档维护工作流 |

## 📦 4. 归档 (Archives)

> **[./docs/archive/](./docs/archive/)**: 历史提案、决策记录 (ADR) 与旧版审计。

---
**维护者**: AI Assistant

