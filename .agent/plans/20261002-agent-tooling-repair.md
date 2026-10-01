# Agent 工具链、长程任务与 Skills 修复计划

## 目标与边界

让默认会话中的 Agent 能够可靠完成多步骤工作区任务：内置工具按全局启用状态暴露并由执行模式审批，工作区具备新建、移动、重命名与回收能力，工具循环可自我纠错并在预算耗尽时优雅收尾，上下文在长循环中不丢失当前任务，System Prompt 给出完整运行时契约。随后引入 Agent Skills（SKILL.md 指令包）。

非目标：不改变工具账本、审批幂等、事务工作区与备份的既有合同；不扩大网络、权限或自定义脚本执行能力；不发布 tag、PR 或 Release。

## 工作包与顺序

1. WP1 工具暴露：内置已启用工具不再受会话 `activeSkillIds` 是否为空影响，风险统一由执行模式审批；自定义数据库工具仍不广告。同步发行合同 5.3 与测试。
2. WP2 工具循环：协议层 `END_TURN` 携带完整工具调用时提升为 `TOOL_CALLS`，空参数归一为 `{}`；未知工具与参数不合法由执行器回写可纠错的工具结果；轮次与调用预算可配置，耗尽时拒绝剩余调用并进入无工具的总结轮；多轮后空正文不判失败。
3. WP3 上下文：当前轮（固定用户消息之后）完整保留，历史按窗口截断；超出模型上下文时由旧到新省略当前轮早期工具结果；窗口溢出消息批量摘要，不再静默丢弃。
4. WP4 工作区工具：新增 create_file、create_directory、move_file（含重命名）、delete_file（回收站）；read_file 输出行号；search_files 修复根层匹配并移除未实现的 fts 模式；patch_file 错误模板与写入索引未入队状态修正。
5. WP5 exec_js 结果解码修正。
6. WP6 System Prompt 分层与预置 Agent 提示词，外部检索内容加数据边界。
7. WP7 设置与会话 UI：移除“无限”循环选项，自定义工具页标注不可执行，审批卡片展示写入预览。
8. WP8 Agent Skills：规格与 ADR-022 先行，再实现包模型、导入、渐进披露运行时与 UI。

## 验收

- 每个工作包补充或更新 JVM 测试；本机 `./gradlew :app:testDebugUnitTest`、`lintDebug`、`assembleDebug` 串行通过，涉及截图的 UI 变更运行 `validateDebugScreenshotTest` 并审阅差异。
- 提交前复核暂存范围，排除 `artifacts/`、APK、`secure_env/`、`.v2c/` 与 `.mimosa/`；推送后核对远端 SHA 与 CI。
- 回滚：各工作包独立提交，可逐个 revert；不涉及数据库 schema 变更前不改迁移。

## 执行结果

- WP1–WP7 已实施：内置工具按全局启用状态暴露并由执行模式审批；协议层 stop+完整工具调用归一、未知工具与参数错误回写可纠错结果；工具预算（轮次 1–100、200 次调用）耗尽后进入总结轮；当前轮完整保留、超限省略早期工具结果、溢出消息批量摘要后归档并在摘要前保留于上下文；新增新建/目录/移动/回收工具与路径定位、行号、内容搜索；exec_js 解码；System Prompt 分层与数据边界；审批写入预览；新增内置工具在旧启用集合下默认启用（`known_builtin_skills`）。
- WP8 已实施首版（ADR-022）：SKILL.md 解析与校验、私有目录存储与 ZIP 门禁、`activate_skill`/`read_skill_file`、System Prompt 技能目录、设置页技能包管理。
- 验证：JVM 全量、Lint 0 Error、Debug 构建与截图校验在本机通过，新增截图基线 2 张经人工复核；GitHub CI 以推送 SHA 为准。
- 真实网关设备验证（2026-10-02，本机 Nexara_API_31/35 arm64）：四模型 × 工作区多步任务/技能激活 8 项与审批、预算收尾 2 项在 API31 全部通过，API35 分批通过；既有设备套件 API35 full 138 项、API31 minimum 98 项通过。过程中修复 Gemini 兼容请求注入 `googleSearchRetrieval` 与调试构建回环网关路由。
- 未覆盖：MiniMax-M3（网关缺陷）、物理设备、SAF 导入实机交互、技能目录备份、`allowed-tools` 收窄与按 Agent 绑定技能。
