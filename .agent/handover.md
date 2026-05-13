# 交接文档 (2026-05-13 模型选择器修复)

## ✅ 已完成事项 (Done)
- **架构迁移 (Phase 2a/2b/2c)**: 
    - 建立了完整的 Domain 模型与 Repository 接口体系。
    - 全量迁移了 8 个核心 ViewModel（Chat, Settings, Rag, AgentHub 等），彻底消除了 DAO 依赖。
    - Repository 覆盖率提升至 100%（AD-1, AD-2, AD-3, AD-4 全部闭环）。
- **单元测试工程化**: 补齐了 450+ 个单元测试，修复了 ChatViewModel 的历史遗留失败用例。
- **功能标准化**: 
    - 统一接入 `ModelPicker`，支持 `multimodal` 筛选。
    - 修复了 SPA 设置页模型切换 Bug 并补全持久化。
- **通用设置默认模型选择器修复** (本次会话):
    - 修复 capabilities 构建缺陷：`refreshModels()`/`addCustomModel()` 仅映射 3/12 种能力。抽取统一的 `buildModelCapabilities()` 到 `ProviderManager`。
    - **自动迁移实装**: 在 `loadModels()` 中增加 `migrateModelIfNeeded()`，自动修复老用户 SharedPreferences 中的 `name` 和 `capabilities` 缺失问题，并静默持久化。
    - 修复 subtitle 显示模型 ID 而非友好名称：新增 `resolveModelName()` 辅助函数。
    - 补全 bge-reranker/jina-reranker/cohere-rerank 的 `capabilities = ModelCapabilities(rerank = true)`。
    - 修正 internet→web 命名映射，对齐 `ModelCapability.WEB` 枚举。
- **环境治理**: 
    - 彻底清理了所有 RN 时代残余文件，将 `native-kotlin-refactor` 设为默认分支。
    - 物理删除了 `worktree` 目录并解决了 VS Code Java 插件导致的缓存复现问题。
- **文档体系**: README.md 净化，README/handover/registry 全面同步。

## 🚀 下一步计划 (Next Steps)
- **已持久化模型数据清洗**: 已有用户的 SharedPreferences 中旧模型的 `name` 字段可能仍为原始 ID（因旧代码 `name = id`），需考虑在 `loadModels()` 中增加迁移逻辑，自动修正旧数据的 name 和 capabilities。
- **Phase 3 — Super Assistant 清理**: 移除 `isSuperAssistant` 检查、重命名 `spa_settings`、清理硬编码 `"super"` ID 逻辑。
- **Phase 4 — 核心引擎增强**: 
    - 实现 `FolderRepository` 补全 RAG 目录管理。
    - 重构 `VectorStatsService` 优化向量库指标统计。
    - 引入本地 Embedding 降级方案与 PDF 导入支持。
- **架构演进**: 视业务复杂度考虑抽取 `UseCase` 层以进一步解耦 ViewModel。

## ⚠️ 风险与阻塞 (Risks)
- **旧数据兼容**: 已安装用户的 SharedPreferences 中的模型 capabilities 仍然是旧的（仅 chat/vision/internet/reasoning），需要用户重新获取模型列表（refreshModels）或手动清除数据才能生效。考虑在 `loadModels()` 中增加自动修正逻辑。
- **新 Repository 性能**: 需在大数据量下观测新仓储层的查询效率，必要时引入二级缓存。

## DIA Status
- CHANGELOG.md ✅ 已更新
- handover.md ✅ 已更新
- 其他文档无影响（无架构/API/数据结构变更）
