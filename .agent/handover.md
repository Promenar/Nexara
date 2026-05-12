# 交接文档 (2026-05-12 晚间更新)

## Done
- **字号调节闭环**: 实现了从设置界面到 `MarkdownText` 正文、LaTeX 公式、Mermaid 图表的全局字号穿透。
- **排版鲁棒性**: 修复了 AI 输出缩进导致解析错误的问题。
- **图片渲染**: 集成了 Coil3 渲染器支持 Markdown 图片。
- **WebView 优化**: `RichContentWebView` 现在支持动态字号和更好的样式控制。
- **代码块闪退修复**: 移除 `CodeBlockHeader` 中与 mikepenz `MarkdownCodeFence` 嵌套的 `horizontalScroll`，彻底解决 Compose 无限宽度约束崩溃。已提交推送 `c79966f`。
- **Markdown 渲染审计**: 完成全链路能力审计、行业对标（LobeChat/Cherry Studio）、字体大小 BUG 诊断。
- **分阶段实施计划**: 输出 11 个独立 Agent 任务的详细方案，含复制即用提示词。见 `docs/IMPLEMENTATION_PLAN.md`。
- **全局规则更新**: 新增 Compose 滚动容器嵌套红线，同步写入 4 个 IDE 规则文件。
- **助手名称本地化**: 修复了聊天界面输入框占位符中助手名称（如 "超级助手"）硬编码/启发式显示为英文的问题，通过 `ChatUiState` 实现了从数据库实时获取 localized name。

## Next Steps (按优先级)
### P0 - 立即修复（4 Agent 并行，详见 `docs/IMPLEMENTATION_PLAN.md`）
- **P0-T1** (0.5h): 字号统一修复 (Agent A)
- **P0-T2** (0.5h): CJK 中西文间距 (Agent B)
- **P0-T3** (0.5h): 段落排版与断行优化 (Agent C)
- **P0-T4** (0.3h): WebView 字号联动 (Agent D)

### P1 - 本迭代（P0 完成后 4 Agent 并行）
- **P1-T1** (1.5h): GFM Alert 支持 (Agent E)
- **P1-T2** (0.3h): LaTeX 定界符兼容 (Agent F)
- **P1-T3** (1h): 流式平滑调速 (Agent G)
- **P1-T4** (0.5h): 标题锚点 ID (Agent H)

### P2 - 差异化竞争力
- **P2-T1** (3h): HTML Artifacts (Agent I)
- **P2-T2** (2h): 代码可编辑模式 (Agent J, 依赖 I)
- **P2-T3** (1h): 图片灯箱增强 (Agent K, 独立)

## Risks
- **MarkdownText.kt 冲突**: 该文件被 7 个 Agent 修改（P0-T2/T4, P1-T1/T2/T3/T4, P2-T1）。虽然修改在不同区域，仍需注意合并顺序。建议 P0/P1 各 Phase 内严格隔离代码区域。
- **字号默认值不一致**: `ThinkingBlock` (14) vs `ChatBubble` (13) vs `NexaraTypography.bodyMedium` (15)，需在 P0-T1 统一。
- **CJK 排版无自动化**: 当前完全依赖 Android 系统断行引擎，P0-T2 解决中西文间距但中文行末标点溢出等问题需持续关注。

## DIA Status
- **CHANGELOG.md**: 已更新（含审计与修复记录、助手名称本地化修复）。
- **docs/MARKDOWN_RENDERING_AUDIT.md**: 审计报告。
- **docs/IMPLEMENTATION_PLAN.md**: 分阶段实施计划（含 Agent 提示词）。
- **ARCHITECTURE.md**: 待补充 Markdown 渲染架构图。
- **registry.md**: 已注册全部新增文档。
