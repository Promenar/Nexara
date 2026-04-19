---
name: docs-cleanup-repowiki-migration
overview: 清理 .agent/docs/ 下过时/重复的手工维护文档，将已由 repowiki 全量覆盖的核心架构文档替换为指向 repowiki 的指针，保留少量含人工决策上下文的近期文档。
todos:
  - id: verify-coverage
    content: Use [subagent:code-explorer] 验证 repowiki 中 6 个指针目标文件的存在性与内容完整性
    status: completed
  - id: delete-subdirs
    content: 删除 plans/、todos/、archive/、architecture/ 四个子目录及其全部内容（共约 86 个文件）
    status: completed
    dependencies:
      - verify-coverage
  - id: replace-pointers
    content: 将根目录 6 个核心文档替换为指向 repowiki 的指针文件
    status: completed
    dependencies:
      - verify-coverage
  - id: delete-stale-root
    content: 删除根目录中已被归档的重复文件（optimization-plan.md 等与 archive/plans 重复的文件）
    status: completed
    dependencies:
      - verify-coverage
  - id: rewrite-maintenance
    content: 重写 DOCS_MAINTENANCE.md 为基于 repowiki SSOT 的新维护流程
    status: completed
    dependencies:
      - replace-pointers
  - id: final-verify
    content: 最终验证：确认保留文件完整、指针有效、目录结构清洁
    status: completed
    dependencies:
      - delete-subdirs
      - replace-pointers
      - delete-stale-root
      - rewrite-maintenance
---

## 用户需求

基于前序分析结果，执行 `.agent/docs/` 目录的大规模文档清理计划。具体包括：

1. 删除 86 个过时/重复的手工文档（plans/、todos/、archive/、architecture/ 全目录 + 根目录冗余文件）
2. 将 6 个已由 repowiki 全量覆盖的核心架构文档替换为指向 repowiki 对应文件的指针文件
3. 重写 `DOCS_MAINTENANCE.md`，确立"以 repowiki 为 SSOT"的新文档维护流程
4. 保留 9 个仍有价值的近期文档不动

## 操作范围

- 删除 4 个子目录（plans/、todos/、archive/、architecture/）及其全部内容
- 替换 6 个根目录文件为指针文件
- 重写 1 个文件（DOCS_MAINTENANCE.md）
- 不涉及任何代码变更

## 技术方案

纯文件系统操作，无需代码技术栈。

### 指针文件格式

被 repowiki 覆盖的核心文档，在原路径保留一个精简指针文件，格式如下：

```markdown
# {原标题}

> **本文档已迁移至 repowiki 自动维护体系。**
> 新文档路径：[.qoder/repowiki/zh/content/{对应目录}/{对应文件}.md](../../../.qoder/repowiki/zh/content/{对应目录}/{对应文件}.md)
> repowiki 基于代码库实时生成，始终保持同步，无需手工更新。
```

### 指针映射关系

| 原文件 | repowiki 指针目标 |
| --- | --- |
| `CODE_STRUCTURE.md` | `核心架构设计/整体架构设计.md` |
| `CORE_INTERFACES.md` | `架构文档/核心接口.md` |
| `DATA_SCHEMA.md` | `架构文档/数据架构.md` |
| `UI_KIT.md` | `架构文档/UI组件库.md` |
| `NATIVE_BRIDGE_DEFENSE.md` | `架构文档/原生桥接防护.md` |
| `ANDROID_BUILD_GUIDE.md` | `部署与运维/构建配置.md` |


### DOCS_MAINTENANCE.md 重写要点

- 以 repowiki 为唯一事实源（SSOT）
- 明确哪些文档由 repowiki 自动维护、哪些仍需手工维护
- 删除旧流程中对 plans/todos/archive 目录的引用
- 新增"触发 repowiki 重新生成"的操作指引

## Agent Extensions

### SubAgent

- **code-explorer**
- Purpose: 在执行删除前，最终确认 repowiki 中确实存在对应的覆盖文档，避免误删后无法回溯
- Expected outcome: 6 个指针映射关系全部验证通过，确认 repowiki 文件存在且内容完整