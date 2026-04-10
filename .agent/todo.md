# Nexara Project Dashboard

> **单一事实来源 (SSOT)**: 
> 本文档仅作为仪表盘。具体实施细节请查阅 `.agent/docs/plans` 下的方案文档。

---

## 🚀 进行中 (Active Tasks)

| ID | 任务名称 | 优先级 | 对应方案文档 | 当前进度 |
| :--- | :--- | :--- | :--- | :--- |
| **014** | **架构优化方案 v3** | 🔴 High | [nexara-architecture-optimization-v3.md](docs/plans/nexara-architecture-optimization-v3.md) | **规划完成**，待实施。预计 14.5h。 |

### 子任务分解

| Phase | 任务 | 工作量 | 状态 |
|-------|------|--------|------|
| 0 | Vector Search TurboModule | 7.5h | 📋 待实施 |
| 1 | Audit Logging | 3h | 📋 待实施 |
| 2 | PDF Robustness | 3h | 📋 待实施 |
| 3 | 扩展功能面板集成 | 1h | 📋 待实施 |

---

## ✅ 最近完成 (Recently Completed)

| ID | 任务名称 | 完成时间 | 实施方案 (Archive) | 验证结论 |
| :--- | :--- | :--- | :--- | :--- |
| **---** | **移动端视觉与性能审计** | 2026-02-17 | [mobile-visual-performance-audit-2026-02-17.md](docs/plans/mobile-visual-performance-audit-2026-02-17.md) | 16项修复完成，6项跳过。版本 v1.2.64-v1.2.73 |
| **008** | **Markdown 预处理器修复** | 2026-02-11 | [markdown-preprocessing-guide.md](docs/archive/markdown-preprocessing-guide.md) | 7 条幂等正则，DeepSeek/Gemini 双模型验证通过。 |
| **003** | **全局动画升级** | 2026-02-03 | [003_global_animation_upgrade_done.md](docs/archive/003_global_animation_upgrade_done.md) | 全面回归原生 (System Default/Fade)，移除 JS 动画。 |
| **001** | **MCP SSE 传输支持** | 2026-02-03 | [001_mcp_sse_transport_plan_done.md](docs/archive/001_mcp_sse_transport_plan_done.md) | 单元测试通过 (4 pass)。代码已合入主线。 |

---

## 🧊 待办规划 (Backlog)

### High Priority
| ID | 任务名称 | 方案草稿 |
| :--- | :--- | :--- |
| **004** | **应用冷启动优化** | [004_app_cold_start_optimization.md](docs/todos/004_app_cold_start_optimization.md) |
| **009** | **工具描述双通道去重** | [009_tool_prompt_dedup.md](docs/todos/009_tool_prompt_dedup.md) |

### Medium Priority
| ID | 任务名称 | 方案草稿 |
| :--- | :--- | :--- |
| **005** | **多模态 RAG** | [005_multimodal_rag.md](docs/todos/005_multimodal_rag.md) |
| **006** | **性能监控** | [006_performance_monitoring.md](docs/todos/006_performance_monitoring.md) |
| **007** | **Expo FS API 迁移** | [007_expo_fs_migration.md](docs/todos/007_expo_fs_migration.md) |
| **010** | **MCP Server 连接池** | [010_mcp_connection_pool.md](docs/todos/010_mcp_connection_pool.md) |

---

## 🗄️ 归档历史

### 已废弃方案
| ID | 任务名称 | 废弃原因 |
| :--- | :--- | :--- |
| **011** | Nexara 全面优化方案 (Old) | 发现幻觉内容，已替换为 014 |
| **012** | Nexara 架构优化 v2 | Worklet 方案不可行，已更新为 v3 |
| **013** | Vector Search TurboModule | 已合并到 014 Phase 0 |

### 历史完成
- [x] **本地模型落地**: `llama.rn` 集成 (v1.2.0)
- [x] **Markdown 渲染引擎重构**: Webview 迁移 (v.1.1.0)
- [x] **v1 动画策略**: [000_animation_v1_strategy.md](docs/archive/000_animation_v1_strategy.md)
