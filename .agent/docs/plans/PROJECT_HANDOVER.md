# Nexara 前端迁移 — 工作交接文档

> **日期**: 2026-05-01
> **会话范围**: Stitch 设计文档补充 + 迁移方案 v2.0 更新
> **状态**: 计划文档完成，等待 Stitch 设计输出

---

## 一、本次完成的工作

### 1.1 新建文档

| 文件 | 路径 | 说明 |
|------|------|------|
| **Stitch UI 功能参考** | `.agent/docs/plans/stitch-ui-functional-reference.md` | **全新创建**。全部 33 个界面 + 40+ 组件的功能需求，供 Stitch 设计参考。包含每个界面的：目的、UI 元素清单、交互规格、数据依赖、状态变化 |

### 1.2 重写文档

| 文件 | 路径 | 变更说明 |
|------|------|---------|
| **迁移路线图 v2.0** | `.agent/docs/plans/native-migration-roadmap.md` | **v1.0 → v2.0 完整重写**：架构从 Jetpack Compose 改为 Multiplatform-Ready 策略；新增 SSE 流解析器 Kotlin 协程重写（StreamParser + ThinkingDetector + StreamBufferManager）；项目目录从 `android/app/` 迁移到根目录 `compose-ui/`；补充遗漏的 40+ 个 UI 组件和界面 |

主要变更点：
- 架构：Android-only → Multiplatform-Ready（Ktor + Coil 3 + DataStore）
- SSE 流处理：JS 主线程 → Kotlin 协程 Flow 管线（首 Token < 30ms）
- 项目结构：`compose-ui/` 独立目录，后期 CMP 扩展成本 ~10%
- 完整覆盖：原计划遗漏的 40+ 组件全部补充（聊天功能组件、RAG 交互组件、技能系统、Artifact 系统）

### 1.3 补充文档

| 文件 | 路径 | 变更说明 |
|------|------|---------|
| **Stitch 设计规范 v1.1** | `.agent/docs/plans/stitch-full-app-visual-redesign-spec.md` | **v1.0 → v1.1 补充**：新增 Group H（技能与执行系统）；新增 G7~G12 通用组件设计要求；补充 B/C/D/E 组缺失组件的详细设计要求；更新 Stitch → Compose 映射表 |

---

## 二、文档依赖关系

```
stitch-full-app-visual-redesign-spec.md  (设计规范/Token/输出规范)
          │
          ▼ (视觉基准)
stitch-ui-functional-reference.md  (功能需求/UI元素/交互/数据)
          │
          ▼ (实现参考)
native-migration-roadmap.md  (技术架构/任务清单/项目结构)
```

**Stitch 使用顺序**：
1. 读取 `stitch-full-app-visual-redesign-spec.md` — 了解 Design Token、毛玻璃规范、输出格式
2. 读取 `stitch-ui-functional-reference.md` — 了解每个界面的功能需求
3. 按 G → A → B → E → C → D → H 顺序逐批设计

---

## 三、Stitch 设计结果核验清单

当 Stitch 完成设计后，需要核验以下要点：

### 3.1 覆盖度核验

| Group | 界面/组件数 | 核验要点 |
|-------|-----------|---------|
| G (通用组件) | 12 个 | G1~G12 全部覆盖？每个组件的 UI 元素与功能参考文档一致？ |
| A (外壳导航) | 3 个 | 欢迎页/底部 Tab/全局加载 全部完成？ |
| B (聊天首页) | 3 个 + 5 组件 | Agent 列表/会话列表/编辑器 全部完成？ |
| E (设置 Tab) | 14 个 + 6 面板 | 全部 14 个设置界面 + 共享面板完成？ |
| C (聊天二级) | 9 个界面 + 20+ 组件 | 会话设置/工作区/审批卡片/任务监控等全部完成？ |
| D (知识库) | 4 个界面 + 9 组件 | 首页/文件夹/编辑器/图谱 + RAG 交互组件全部完成？ |
| H (技能系统) | 3 个 | 核心记忆/时间线/技能设置面板完成？ |

### 3.2 设计一致性核验

对照 `stitch-full-app-visual-redesign-spec.md` 检查：
- [ ] 背景色：Dark `#131315` / Light `#ffffff`
- [ ] 毛玻璃：`backdrop-blur-xl` + `0.5px border-white/10` + `inset shadow`
- [ ] 字体：标题 Manrope，正文 Inter，代码 Space Grotesk
- [ ] 间距：4px 网格系统
- [ ] 圆角：sm(8px) / md(12px) / lg(16px) / xl(18px) / full(9999px)
- [ ] 品牌色：Indigo `#6366f1`(light) / `#c0c1ff`(dark)
- [ ] 状态色：成功 `#10b981`，错误 `#ef4444`，警告 `#f59e0b`
- [ ] 按钮弹性：`active:scale-[0.96]`
- [ ] 与主会话界面（已完成）视觉一致性

---

## 四、下一步行动计划

### 4.1 当前状态

- ✅ 迁移方案 v2.0 完成
- ✅ Stitch 功能参考文档完成
- ✅ Stitch 设计规范 v1.1 补充完成
- ⏳ **等待 Stitch 设计输出**（用户在 JetBrains IDE 中通过 StitchMCP 生成）

### 4.2 用户操作（新会话启动语）

```
我在 JetBrains IDE 中配置了 StitchMCP，已经完成了主会话界面的设计（Modern Session UI Redesign）。

现在需要你按照以下两个文档的指导，继续完成其他所有界面的设计：
1. .agent/docs/plans/stitch-full-app-visual-redesign-spec.md — 设计规范/Token/输出格式
2. .agent/docs/plans/stitch-ui-functional-reference.md — 每个界面的功能需求

请按 G → A → B → E → C → D → H 的顺序逐批设计。
每组设计完成后，将 HTML 文件保存到项目目录 .agent/docs/stitch-output/ 下，然后我来验证。

从 Group G（全局组件）开始。
```

### 4.3 我（opencode）的操作

当用户在新会话中告知 Stitch 设计完成后：
1. 读取 `.agent/docs/stitch-output/` 下的所有 HTML 文件
2. 对照 `stitch-ui-functional-reference.md` 核验覆盖度
3. 对照 `stitch-full-app-visual-redesign-spec.md` 核验设计一致性
4. 核验通过后，开始 `compose-ui/` 目录的 Kotlin/Compose 实现

---

## 五、关键技术决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| UI 框架 | Jetpack Compose (Multiplatform-Ready) | 后期 CMP 扩展成本仅 ~10% |
| 网络层 | Ktor Client + ktor-sse | 跨平台，协程原生，替代 OkHttp |
| 图片加载 | Coil 3 | 跨平台，API 与 Coil 2 基本一致 |
| 数据存储 | DataStore | 跨平台，替代 MMKV |
| SSE 流解析 | Kotlin 协程 Flow 重写 | 首 Token < 30ms，JS 方案 200-500ms |
| 项目目录 | `compose-ui/` (根目录) | 独立模块，不污染现有 RN 工程 |
| iOS 策略 | 后期 CMP 扩展 | 无需 SwiftUI 重写，节省 3-5 周 |

---

## 六、过期内容清理

以下文件/内容已过时，建议归档或删除：

| 文件 | 状态 | 说明 |
|------|------|------|
| `.agent/docs/plans/native-migration-roadmap.md` v1.0 | **已替换** | v2.0 已重写，旧版可删除 |
| 本文件 `PROJECT_HANDOVER.md` | **本次创建** | 交接完成后可删除 |

---

*交接文档结束*

> **新会话启动语已在第四节提供，用户可直接复制使用。**
