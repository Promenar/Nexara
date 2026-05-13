# 交接文档 (2026-05-13 最终收尾)

## ✅ 已完成 — 架构迁移全线

### 项目治理
- RN 时代残余清理（25+ 目录/文件）→ 纯粹 Kotlin 原生项目
- 文档体系治理（清理 240+ 过时文档，统一单 .agent/）
- PRD v2.0 + 架构设计 + 实现分析三份核心文档
- README 重写，.gitignore 精简，废弃 Worktree/Qoder repowiki

### 架构迁移（Phase 2a/2b/2c/3/4）
| 阶段 | 内容 | 文件 |
|------|------|------|
| 2a | Domain 层 + Repository 接口 | 13 新建 |
| 2a | Repository 实现 + Mapper | 9 新建 |
| 2b | 5 ViewModel 迁移 + 测试 | 11 修改 + 11 测试 |
| 2c | 3 ViewModel 迁移（Chat/Settings/Rag）| 6 修改 + 3 测试 |
| 3 | Super Assistant 清理（ADR-001）| 2 删除 + 9 修改 |
| 4 | FolderRepository + VectorStats + 文档导入 | 5 新建 + 3 修改 + 3 测试 |

### 架构债状态
| 编号 | 问题 | 状态 |
|------|------|:---:|
| AD-1 | Domain 层缺失 | ✅ |
| AD-2 | Repository 覆盖率 37.5% | ✅ 100% |
| AD-3 | ProviderManager 单例 | ✅ |
| AD-4 | ViewModel 直接操作 DAO | ✅ 8/8 |

### 测试
- 40 个测试文件，489 tests，仅剩 1 个预存失败 (ModelSpecs)
- 修复了 ChatViewModel 5 个历史失败

## 🚀 下一步 — UseCase 层抽取

### Phase 5 — UseCase 层抽取 ✅ 完成
- 5 个 UseCase: IdGenerator / AgentConfigResolver / CreateAgentUseCase / DeleteDocumentUseCase / RagConfigPersistence
- 45 个测试文件，520 tests，仅剩 1 个预存失败 (ModelSpecs)
- 7 个 VM 统一使用 IdGenerator，消除内联 ID 生成

### 已知局限
- PDF 文本提取需 Apache PDFBox（PdfExtractor 中仅 1 个 TODO 标记）

## 🚀 Phase 7 — Markdown 渲染行业对齐
- 已有完整审计方案：`docs/MARKDOWN_RENDERING_AUDIT.md`
- 已有分阶段实施计划：`docs/IMPLEMENTATION_PLAN.md`（11 个 Agent 任务）
- P0: 字号统一 / CJK 间距 / 段落排版 / WebView 联动
- P1: GFM Alert / LaTeX 定界符 / 流式平滑 / 标题锚点
- P2: HTML Artifacts / 代码可编辑 / 图片灯箱

## DIA Status
- 全部文档已同步，见 `.agent/registry.md`
