# 交接文档 (2026-05-13 收尾)

## Done
- **RAG 观测链路打通**: 实装了从 `MemoryManager` -> `ContextBuilder` -> `ChatViewModel` -> `ChatScreen` 的端到端进度报告管道。
- **RAG UI 指标展示**: 在助手消息上方集成了 `RagOmniIndicator` 磨砂玻璃指示器。
- **原生版全局审计与文档体系建设**: 产出 PRD v2.0、全局架构设计、实现分析与开发进度三份核心文档。完成超级助手取舍决策（ADR-001: 去繁就简）。
- **ContextBuilder 设计修正**: 补充工具调用回传数据层（`toolResults: List<ToolCallResult>`）。
- **RN 时代残余清理**: 删除 25+ 个 RN 目录/文件（`app/`、`src/`、`android/`、`web-client/`、`package.json` 等），分支变为纯粹 Kotlin 原生项目。
- **文档体系治理**: 
  - 清理根 `.agent/docs/`（57 文件）、`.agent/memory/`（4 文件）、`.qoder/repowiki/`（145+ 文件）
  - 合并 `native-ui/.agent/plans/` → 根 `.agent/plans/`（17 个活跃计划）
  - 删除双 `.agent/` 目录重复、废弃 Qoder repowiki 自动文档系统
  - 建立统一文档结构：`docs/`（公共）+ `.agent/`（工作区）
  - 废弃 Worktree 发行分支模式（原生 Kotlin + AS Build Variant 无环境污染）
- **README.md** 重写为原生版，`.gitignore` 精简。
- **模型选择器标准化**: 统一接入 `ModelPicker`，支持 `multimodal` 能力标签，修复了 SPA 设置页模型切换 Bug 并补全了 `SpaViewModel` 持久化。
- **RAG 重构**: 全量替换 `RagAdvancedScreen` 模型列表，接入统一筛选协议。
- **分支与缓存清理**: 
    - 清理了所有冗余 Git 分支，手动切换主分支。
    - 解决了 VS Code Java 插件缓存导致的 `worktree` 目录自动恢复问题（需清理 Java LS Cache）。

## Next Steps
- **Domain + Repository 层实施（Phase 2a，4 个并行会话）**:
  - Session A（先执行）: 创建 Domain 模型 + Repository 接口（13 个文件，零 Android 依赖）
  - Session B/C/D（A 完成后并行）: 实现 Agent/Document/Vector/KG/Provider Repositories + 对齐现有 Session/Message
  - 详细方案见 `.agent/plans/20260513-domain-repository-implementation.md`
- **Super Assistant 清理（Phase 3）**: 移除 `isSuperAssistant` 检查、重命名 `spa_settings`、清理硬编码 `"super"` ID 逻辑、更新字符串资源
- **Phase 2b 后续**: Embedding 本地降级、PDF/Word/HTML 导入、混合检索集成

## Risks
- **架构债积累**: Domain 层缺失 + Repository 覆盖率不足（37.5%）若不及时修复，将加速代码腐化 — 建议 Phase 2 优先补齐

## DIA Status
- 全部核心/按需文档已同步更新。详见 `.agent/registry.md`。

