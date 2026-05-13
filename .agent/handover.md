# 交接文档 (2026-05-13 收尾)

## ✅ 已完成事项 (Done)
- **架构迁移 (Phase 2a/2b/2c)**: 
    - 建立了完整的 Domain 模型与 Repository 接口体系。
    - 全量迁移了 8 个核心 ViewModel（Chat, Settings, Rag, AgentHub 等），彻底消除了 DAO 依赖。
    - Repository 覆盖率提升至 100%（AD-1, AD-2, AD-3, AD-4 全部闭环）。
- **单元测试工程化**: 补齐了 450+ 个单元测试，修复了 ChatViewModel 的历史遗留失败用例。
- **功能标准化**: 
    - 统一接入 `ModelPicker`，支持 `multimodal` 筛选。
    - 修复了 SPA 设置页模型切换 Bug 并补全持久化。
- **环境治理**: 
    - 彻底清理了所有 RN 时代残余文件，将 `native-kotlin-refactor` 设为默认分支。
    - 物理删除了 `worktree` 目录并解决了 VS Code Java 插件导致的缓存复现问题。
- **文档体系**: README.md 净化，README/handover/registry 全面同步。

## 🚀 下一步计划 (Next Steps)
- **Phase 3 — Super Assistant 清理**: 移除 `isSuperAssistant` 检查、重命名 `spa_settings`、清理硬编码 `"super"` ID 逻辑。
- **Phase 4 — 核心引擎增强**: 
    - 实现 `FolderRepository` 补全 RAG 目录管理。
    - 重构 `VectorStatsService` 优化向量库指标统计。
    - 引入本地 Embedding 降级方案与 PDF 导入支持。
- **架构演进**: 视业务复杂度考虑抽取 `UseCase` 层以进一步解耦 ViewModel。

## ⚠️ 风险与阻塞 (Risks)
- **新 Repository 性能**: 需在大数据量下观测新仓储层的查询效率，必要时引入二级缓存。

## DIA Status
- 全部核心/按需文档已同步更新。详见 `.agent/registry.md`。
