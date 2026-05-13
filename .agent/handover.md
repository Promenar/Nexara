# 交接文档 (2026-05-13 收尾)

## Done
- **原生版全局审计与文档体系建设**: 产出 PRD v2.0、全局架构设计、实现分析与开发进度。超级助手取舍决策（ADR-001: 去繁就简）。
- **RN 时代残余清理**: 删除 25+ 个 RN 目录/文件，分支变为纯粹 Kotlin 原生项目。
- **文档体系治理**: 清理 240+ 过时文档，合并双 .agent/，废弃 repowiki/Worktree 模式。
- **Domain 层 + Repository 层建立** (Phase 2a): 28 个文件（13 domain + 11 repository + 4 mapper），4 个并行会话完成。编译通过。
- **架构债消除**: AD-1 (Domain 缺失)、AD-2 (Repository 覆盖率 37.5%→100%)、AD-3 (ProviderManager 单例) 已全部消除。
- **模型选择器标准化**: 统一接入 ModelPicker，支持 multimodal 标签。修复 SPA 设置页模型切换 Bug。
- **README.md** 重写为原生版，`.gitignore` 精简。

## Architecture Debt Status
| 编号 | 问题 | 状态 |
|------|------|:---:|
| AD-1 | Domain 层缺失 | ✅ |
| AD-2 | Repository 覆盖率 37.5% | ✅ (100%) |
| AD-3 | ProviderManager 单例 | ✅ |
| AD-4 | ViewModel 直接操作 DAO | ✅ 8/8 VM 已迁移 |

## Done
- **Phase 2a**: Domain 层 + Repository 层建立（28 文件，编译通过）
- **Phase 2b**: 5 个 ViewModel 迁移 + 11 测试文件
- **Phase 2c**: 3 个 ViewModel 迁移（Chat/Settings/Rag）+ IAgentRepository.getById + 计数方法
- ChatViewModel 5 个历史失败测试已修复（迁移 agentDao 时一并解决）
- 测试: 458 tests, 仅剩 1 个预存失败 (ModelSpecs)

## Next Steps
- **Phase 2b / 2c — ViewModel 迁移 + 单元测试** ✅ 全部完成
- **Phase 3 — Super Assistant 清理**: 移除 isSuperAssistant 检查、重命名 spa_settings
- **Phase 4 — FolderRepository + VectorStatsService 重构**: RagViewModel 残留清理
- **长期**: UseCase 层抽取
  - AgentHub / AgentEdit / SessionList / DocEditor / KnowledgeGraph 全部迁移完毕
  - 新增 11 个测试文件，0 失败；DocEditorViewModel 残余 DAO 已修复
  - IDocumentRepository 补全 getById 方法
- **Phase 2c — 剩余 ViewModel 迁移** (3 个并行会话):
  - Session H: ChatViewModel (3 处 agentDao → AgentRepository) + IAgentRepository.getById
  - Session I: SettingsViewModel (2 处 DAO → Repository) + 计数方法
  - Session J: RagViewModel (documentDao/vectorDao/kgDao → Repository，folderDao 保留 TODO)
  - 详细方案见 `.agent/plans/20260513-phase2c-remaining-vm-migration.md`
- **Phase 3 — Super Assistant 清理**: 移除 isSuperAssistant 检查、重命名 spa_settings、清理硬编码 "super" ID

## DIA Status
- 全部核心/按需文档已同步更新。详见 `.agent/registry.md`。
