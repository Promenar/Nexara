# 按范围授权的权限模型与工作区多路径

日期：2026-10-24 · 状态：已完成（2026-10-24 真机验收通过）

## 目标

以“工作区路径 + 两档范围”取代手动、半自动、全自动三种按动作区分的审批模式。

## 权限模型

两档执行模式：

- **工作区（默认）**：文件工具与隔离脚本（`exec_js`、`run_script`、`python_sandbox`）只能看到工作区路径，路径内读写、移动、删除（含不进回收区的删除）免审批；命令逐条审批。
- **完全访问**：命令也免审批。

命令指以真实进程运行的工具：`shell_exec`、`python_exec`（内置，以 Nexara 应用身份运行，可访问全部应用数据，不受工作区约束）、`termux_exec`（以 Termux 身份运行）、`ssh_exec`、`ssh_session_open`、`ssh_session_send`（在远程主机运行）。Android 内核未提供可用的路径级进程沙箱（真机 6.6 GKI 内核 `CONFIG_SECURITY_LANDLOCK is not set`），命令无法按路径约束，因此在工作区档对命令本身审批。

两档都审批：技能目录（`/skills`、工作区 `/.agents/skills`）的变更、`schedule_create`、SSH 主机配置（`ssh_host_save`、`ssh_host_delete`、`ssh_host_install_key`）。

两档都免审批：MCP 工具、`web_fetch`（含可能夹带数据的网址）、其余只读与会话状态工具。

取消：只读档、单条路径的独立权限（挂载只读）、外发数据审批、按主机设置、“始终允许”清单与脚本写入审批设置。

工作区路径之外的位置对文件工具不可见，只能由用户在界面上加入工作区；Agent 只能经命令查看外部，工作区档下需逐条批准。

运行时关键数据：数据库目录、`shared_prefs`、`no_backup`、用户级技能目录 `files/agent_skills`、诊断日志。系统提示词列出这些位置，要求模型在对话中取得用户明确同意后才可经命令改动；内置命令结束后检测这些位置的非预期变化并在结果中标出。

## 迁移

- 执行模式：`auto` → 完全访问；`semi`、`manual` 及未知值 → 工作区。
- 挂载只读标记全部转为读写。
- 全局挂载复制到每个现有会话的路径列表后删除全局条目；新建挂载只属于会话。
- “始终允许”清单清空，不转换为附加路径。

## 阶段

1. 权限后端（Codex，worktree `../Nexara-wt-perm`，分支 `codex/scope-permissions`）。
2. 多路径后端（Codex，worktree `../Nexara-wt-paths`，分支 `codex/workspace-paths`）：挂载改为工作区附加路径、去掉只读与全局作用域、标明命令可见性。
3. 前端（主控）：审批卡片类别与说明、模式选择、路径列表与资源管理器重设计。

## 文件所有权

| 范围 | 所有者 |
|---|---|
| `domain/**`（除 `domain/storage/**`）、`data/generation/**`、`data/subagent/**`、`data/script/**`、`data/exec/**`、`data/repository/ToolAllowlistStore.kt`、`data/mapper/**`、`data/model/**`、`data/repository/SessionRepository.kt`、`ui/chat/manager/**`（除 `skills/MountedWorkspaceSkill.kt`）、`NexaraApplication.kt` | 阶段 1 |
| `data/storage/**`、`domain/storage/**`、`data/local/db/**`、`data/workspace/WorkspaceFileTarget.kt`、`ui/chat/manager/skills/MountedWorkspaceSkill.kt` | 阶段 2 |
| `ui/**` 的 Compose 界面、`res/**` | 主控（后端阶段仅允许最小编译修正与模式标签字符串） |

## 验收

- 单元测试：两档 × 各类工具的审批判定、模式迁移映射、提示词包含权限说明与关键数据清单、关键数据变化检测（无应用自身并发写入误报）、挂载数据库迁移。
- 全量：`testSideloadDebugUnitTest`、`testPlayDebugUnitTest`、`lintSideloadDebug`、`assembleSideloadDebug`、`assemblePlayDebug`、`validateSideloadDebugScreenshotTest`、发行清单检查。
- 真机：工作区档内文件写入免审批、命令审批卡片类别与说明、完全访问档命令免审批、技能目录变更审批。
