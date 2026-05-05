# OpenCode 修复任务指令模板 (基于全量审计 2026-05-04)

> **版本**: v1 (2026-05-04)
> **依据**: `native-ui/.agent/docs/audits/full-migration-audit-2026-05-04.md`
> **遗留问题**: 3 严重 + 4 中等 + 3 低 = 10 项

完整指令模板见 artifact 文件 `fix-opencode-templates.md`。

## 会话概览

| 会话 | 优先级 | 修复项 | 预估代码 |
|------|--------|-------|---------|
| **F1** | P0 | 硬编码颜色→语义色 + archiveToRag接入embedding + llmProvider动态配置 | ~300行 |
| **F2** | P1 | 设置页ViewModel + ProviderModels入口 + ImageService迁移 | ~600行 |
| **F3** | P2 | Agent持久化 + FolderItem真实计数 + ChatViewModel集成测试 | ~400行 |
