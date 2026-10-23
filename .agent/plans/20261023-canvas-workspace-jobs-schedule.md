# 画布落入工作区、后台任务与定时唤醒

## 目标

1. 会话画布的当前版本作为普通文件落在会话工作区，资源管理器可见、`patch_file` 可改；消息中的画布卡片改为附件式的紧凑布局。
2. Agent 可以把耗时脚本（`shell_exec`、`python_exec`、`termux_exec`）放到后台运行，立即拿到任务号继续做别的事，之后按任务号查看输出、等待或停止；脚本结束时可以通知用户，也可以唤醒 Agent 接着处理。
3. Agent 可以创建定时任务（单次、固定间隔、cron 表达式），到点由应用在原会话里自动发起一轮，用于轮询、定期检查和提醒。

## 已确认的产品决定（2026-10-23 用户确认）

- 画布以工作区为主：当前版本写到会话工作区 `canvas/<名称>.html`，资源管理器可见、可被 `patch_file` 编辑；工作区文件被外部修改后，下次打开或更新时识别为新版本；版本历史保留在应用私有目录；删除会话不删除工作区里的画布文件。
- 定时唤醒方式为自动发起一轮：到点在原会话插入一条系统触发消息并开始生成；遇到需要审批或确认时暂停，并发送通知，用户点通知回到该会话处理。
- 定时精度：两个渠道都提供非精确调度；侧载版额外支持精确闹钟（`SCHEDULE_EXACT_ALARM`），允许小于 15 分钟的间隔；Play 版只有非精确调度，最短 15 分钟。
- 推进方式：先出本方案，用户确认后再分 worktree 施工。

## 方案默认值（随本方案一并确认，施工中不再询问）

- 侧载版精确调度的最短间隔为 5 分钟。每次触发都是一轮完整的模型调用，更短的间隔成本和耗电都不合理。
- 每个会话最多 5 个启用中的定时任务，全局最多 20 个；每个会话最多同时运行 3 个后台任务，全局最多 6 个。
- `schedule_create` 无论审批模式如何都必须经用户确认，因为它会建立持续的自主行为。确认卡片展示接下来 3 次触发时间。
- 后台任务默认最长运行 120 分钟，可设置到 1440 分钟。日志单文件上限 20 MiB，超过后轮转一次。

## 非目标

- 不接入 FCM 或任何云端推送，不做跨设备唤醒。
- Play 版不申请精确闹钟，也不使用 specialUse 前台服务；渠道清单检查继续禁止这些权限。
- 应用进程被系统杀死后，不重新接管内置进程（`shell_exec`、`python_exec`）启动的后台任务：重启时按进程组号清理残留进程，并把任务标为“已中断”。Termux 任务运行在 Termux 进程里，重启后可以继续查询。
- 定时任务的确认不预先授权该任务触发轮次里的工具调用。触发轮次沿用会话当前的审批模式。
- 不把工作区里删除画布文件的操作同步回版本历史；工作区文件丢失时，下次更新会重新写出。
- 用户界面只提供定时任务的查看、暂停、恢复和删除，不提供手工新建或编辑。
- 定时任务和后台任务记录不进入备份；恢复备份不会带回会自动触发的任务。

## 方案

### 一、画布：工作区文件与卡片重做（施工包 A）

**工作区映射**

- 画布元数据新增 `workspacePath`（相对会话工作区的路径）和 `workspaceHash`（最后一次写出内容的 SHA-256）。
- 名称从标题生成：保留中文、字母、数字和连字符，最长 48 字符；同一会话内重名时追加 `-2`、`-3`。重命名不改变已有路径。
- `canvas_create` 和 `canvas_update` 先写私有版本，再把当前版本写到工作区。工作区目标解析复用 `data/media/DefaultWorkspaceMedia.kt` 的 `target(sessionId, path, writing)`：私有根经 `IWorkspaceRepository`（按需 `ensureSessionRoot`）、目录根写真实路径、SAF 树、`ExternalMountPaths` 别名。施工时把这段目标解析抽成两边共用的 `WorkspaceFileTarget`，媒体保存的行为保持不变。
- 外部修改识别：打开画布、执行 `canvas_update` 或读取画布前，比较工作区文件哈希与 `workspaceHash`。不一致就把工作区内容导入为新版本（来源标记为“工作区”），再继续原操作。`canvas_update` 的查找替换基于导入后的最新内容。
- 工作区写出失败（如 SAF 权限失效）不影响私有版本落盘。工具结果附带警告，卡片显示“未同步到工作区”。
- 分支会话会拿到全新的工作区（`BranchSessionUseCase` 置空 `workspacePath`）。`copySession` 照常复制版本历史，并清空复制出的 `workspaceHash`；分支里第一次打开或更新时重新写出。
- 删除会话只删私有版本目录，不删工作区文件。

**卡片布局**（`ui/canvas/CanvasComponents.kt` 中的 `CanvasCard`）

- 去掉两条分隔线和底部“打开”按钮。整张卡片是一个圆角容器，宽度跟随消息正文，最大 360dp。
- 缩略图按 16:10 显示：以 360dp 视口渲染后缩放到卡片宽度，从页面顶部开始，底部 48dp 渐隐。缩略图不可交互。
- 渐隐区上叠两行文字：标题（单行省略）和“v3 · canvas/xxx.html”。当前消息对应的不是最新版本时，右上角显示“已有 v5”的小标签；未同步到工作区时显示警告标签。
- 轻点整卡全屏打开该消息对应的版本。长按弹出菜单：复制源码、在工作区中显示、打开最新版本。
- 保留现有测试标签 `canvas_card`、`canvas_open`、`canvas_preview`，截图基线同步更新。

### 二、后台任务（施工包 B）

**工具契约**

- `shell_exec`、`python_exec`、`termux_exec` 新增两个参数：
  - `background: boolean`，默认 false。
  - `on_exit: "none" | "notify" | "wake"`，默认 `notify`。
- 后台启动立即返回 `job_id`、日志路径与启动时间。审批与前台执行一致（`ToolRisk.PROCESS`），审批卡片显示“后台运行”标记。
- 新工具：

  | 工具 | 风险 | 作用 |
  |---|---|---|
  | `job_status(job_id)` | `SESSION_STATE`，与 `ssh_session_read` 同级 | 返回状态、退出码、已运行时长和日志末尾（最多 8,000 字符） |
  | `job_wait(job_id, timeout_seconds ≤ 600)` | `SESSION_STATE` | 阻塞到任务结束或超时，返回同样的摘要 |
  | `job_stop(job_id)` | `SESSION_STATE` | 停止任务 |
  | `job_list()` | `SESSION_STATE` | 列出本会话的任务 |

  `job_stop` 先对进程组发 SIGTERM，2 秒后发 SIGKILL。
- `on_exit = wake` 时，任务结束后经下文第三部分的系统触发调度器，在原会话发起一轮。触发消息写明任务号、命令摘要、退出码，以及日志末尾 2,000 字符。

**内置进程**（`BuiltinCommandRunner`）

- 复用 `setsid` 包装和进程组号文件。后台模式下进程与发起它的协程解耦：取消本轮生成或停止生成都不杀后台任务；删除会话会停止该会话的全部任务。
- 输出直接写入工作区 `jobs/<job_id>.log`，不再经 `TailTextCollector` 汇总。退出码写入任务记录。
- 私有根工作区目前在整个命令期间持有根写锁（`WorkspaceRepository.runCommandInWorkspace`）。后台任务不能这样持锁：
  - 启动时写入按任务区分的 pending 标记。
  - 运行中不持锁。
  - `job_status`、`job_wait` 返回前和任务结束时，短暂持锁做一次增量对账。
  - 施工前必须先读清对账逻辑，确认它可以重复执行、与前台工具交错不会互相覆盖。若无法保证，私有根工作区的后台任务改为在独立的临时目录运行，结束时再合入，并在报告中说明取舍。

**Termux**（`TermuxExecSkill`）

- 后台模式通过 RUN_COMMAND 写一个启动脚本：脚本用 `setsid nohup` 运行命令，把输出写到日志、退出码写到 `.exit` 文件，立即回传 PID。
- 日志位置：工作区物理路径对 Termux 可读写时（共享存储目录根），日志直接放在工作区 `jobs/`；否则放在 Termux 家目录 `~/.nexara/jobs/`，由 `job_status` 再发一次短 RUN_COMMAND 读取末尾和存活状态。
- Termux 任务不受 Nexara 进程重启影响，重启后可以继续查询和停止。

**任务记录与保活**

- 新增 `data/jobs/BackgroundJobStore`：JSON 文件存储，位于 `noBackupFilesDir/background-jobs/`，参照 `SubagentRunStore` 的重启处理。应用启动时，内置进程的 RUNNING 记录按进程组号清理残留并标为 INTERRUPTED；Termux 记录保持并在首次查询时刷新。
- 侧载版：有后台任务运行时，`ExecForegroundService`（specialUse）按引用计数保持。通知显示运行中的任务数，并带“全部停止”操作。`on_exit = wake` 的任务在唤醒轮次结束或暂停之前不释放引用计数，保证进程存活。
- Play 版：只在应用可见期间持有唤醒锁。应用进入后台后任务可能被系统冻结或终止，`job_status` 如实报告。工具描述和设置页说明这一限制。

**界面**

- 会话顶部状态区新增“后台任务”标签，显示运行数，点开是底部面板。面板列出任务和实时日志末尾（复用 `ToolLiveOutputPanel`，新键 `job:<id>`），提供停止和在工作区中打开日志。
- 后台启动的工具卡片显示任务号与实时状态。

### 三、系统触发调度器与定时任务（施工包 C）

**系统触发调度器**（新建 `domain/trigger/`，应用级单例，在 `NexaraApplication` 装配）

- 契约：`SystemTurnDispatcher.request(SystemTurnRequest(sessionId, kind, title, body, sourceId)): SystemTurnOutcome`。它挂起直到该轮结束、暂停或被跳过。主控在派发施工前提交这个接口文件，B 和 C 都依赖它。
- 执行步骤：
  1. 等待 `BackupStartupState.Ready`。
  2. 确认会话存在，并把会话载入 `ChatStore`（`DefaultChatGenerationRuntime.prepare` 依赖 `store.getSession`）。
  3. 插入触发消息：`role = USER`，`name = "system_trigger"`，正文带“[定时任务「名称」第 N 次触发]”或“[后台任务 job-xx 已结束]”前缀。
  4. 经 `generationCoordinator.start` 发起生成，交给 `generationForegroundController.track`，结束后完成终态确认与释放。
- 忙碌处理：全局只能有一轮在生成。触发时若有其它生成在跑，每个来源最多排队一个待发请求，在生成器空闲后发出；30 分钟内仍未空闲就记为“跳过（忙）”。进程死亡导致丢失的待发请求，在下次启动时记为跳过。
- 无界面确认：触发轮次以 HEADLESS 交互模式运行。`GenerationInteractionStore.awaitDecision`（无限自动、重复工具确认）在该模式下不再无限等待，而是立即以“需要用户处理”结束本轮，并保留可继续的状态。工具审批沿用会话 `approvalRequest` 的持久化，用户打开会话后照常批准并通过 `resumeAfterApproval` 继续。两种情况都发送通知。
- 通知：新渠道 `agent_trigger`，参照 `SubagentApprovalNotifications`。
  - 需要处理时发高优先级通知；触发轮次完成时发普通通知，内容为回复首行。
  - 新增深链 `ACTION_OPEN_SESSION`（携带会话 ID），点击直接进入会话。现有 `ACTION_OPEN_GENERATION` 只对运行中的任务有效，保持不变。
- 消息展示：`PipelineBubble` 为 `system_trigger` 增加专门分支，渲染成居中的紧凑行，例如“⏰ 定时任务「名称」· 第 3 次 · 10:30”。展开可以看完整提示，不显示为用户气泡。

**定时任务**

- 工具：
  - `schedule_create(name, prompt, trigger, max_runs?, expires_at?)`。`trigger` 取以下三种之一：
    - `{type: "once", at}`：本地时间 ISO 字符串。
    - `{type: "interval", every_minutes}`。
    - `{type: "cron", expr}`：5 段 cron，按创建时的设备时区解释。
  - `schedule_list()`、`schedule_pause(id)`、`schedule_resume(id)`、`schedule_cancel(id)`：作用于本会话。
  - 自研最小 cron 解析器（不引入依赖），支持 `*`、列表、范围和步长，配完整测试。
- 存储：Room 新表 `scheduled_tasks`（数据库 v25 → v26，带迁移测试），记录：
  - 触发定义与时区
  - 下次触发时间
  - 已触发次数
  - 最近一次结果（完成、需要处理、跳过、失败）
  - 是否精确
  - 所属会话

  删除会话时级联删除其定时任务；该表不进入备份载荷。
- 调度机制（不新增依赖）：
  - 非精确（两个渠道）：`JobScheduler`。单次任务用 `setMinimumLatency`，间隔任务用周期 Job（最短 15 分钟），并 `setPersisted(true)`，需要 `RECEIVE_BOOT_COMPLETED`。cron 任务每次只排下一次的单次 Job。到点后在 `JobService` 内调用调度器，等到本轮结束或暂停再 `jobFinished`。后台应用不能从普通 Job 启动 dataSync 前台服务，所以生成前台控制器在启动失败时退化为无前台服务运行，只在 Job 窗口内执行；被系统停止时记录为“中断”，并发需要处理的通知。
  - 精确（仅侧载）：`AlarmManager.setExactAndAllowWhileIdle`，前提是用户已授予精确闹钟权限。没有权限时自动退回非精确，并在任务卡片上提示，可一键跳转系统授权页。闹钟广播属于官方豁免中“为完成用户请求的精确闹钟”，在接收器中启动前台服务执行本轮。
  - 重新排期：应用启动、`BOOT_COMPLETED`、`MY_PACKAGE_REPLACED`、`TIME_SET`、`TIMEZONE_CHANGED` 时对全部启用任务做幂等重排。开机广播只做重排，不在接收器里启动 dataSync 前台服务（Android 15 禁止）。错过的触发合并为一次补发，并标注“补发”。
- 界面：
  - 会话顶部状态区显示“定时”标签，点开是本会话的定时任务列表：名称、下次触发、精确/非精确、最近结果，可暂停、恢复和删除。
  - 设置中新增“后台与定时”页面，汇总全部会话的定时任务与后台任务，并提供系统授权入口（侧载版的精确闹钟和“不受电池优化限制”）。
  - `schedule_create` 的确认卡片展示名称、提示、触发规则和接下来 3 次触发时间。
- 渠道清单：
  - 侧载版新增 `SCHEDULE_EXACT_ALARM`。
  - 两个渠道新增 `RECEIVE_BOOT_COMPLETED` 和 JobService 声明（`BIND_JOB_SERVICE`）。
  - 更新 `scripts/ci/check-distribution-manifests.py`，禁止 Play 出现任何精确闹钟权限。

### 施工划分与文件所有权

- 三个施工包各用一个独立 worktree：`../Nexara-wt-canvas`、`../Nexara-wt-jobs`、`../Nexara-wt-schedule`。
- 主控在派发前提交契约文件 `domain/trigger/SystemTurnDispatcher.kt`（接口与数据类，不含实现）。
- 文件所有权：

  | 施工包 | 独占的文件与目录 |
  |---|---|
  | A | `data/canvas/`、`ui/canvas/`、`CanvasSkills.kt`，新抽出的 `WorkspaceFileTarget` 及 `DefaultWorkspaceMedia.kt` 中对应的改动 |
  | B | `BuiltinCommandRunner`、`BuiltinExecSkills.kt`、`TermuxExecSkill.kt`、`ExecForegroundService`、`data/jobs/`、job 工具、`WorkspaceRepository` 中命令执行与对账相关部分 |
  | C | `domain/trigger/` 实现、`data/schedule/`、`NexaraDatabase` 迁移、接收器与 JobService、两个渠道清单、渠道检查脚本、`GenerationInteractionStore`、`PipelineBubble` 的触发消息分支、深链与通知 |

- 字符串资源各用独立文件：`strings_canvas.xml`、`strings_jobs.xml`、`strings_schedule.xml`，中英文各一份，避免合并冲突。
- 共享文件（工具注册、`BuiltinToolPresentation`、`ToolInvocationPresentation`、`ApprovalPreview`、`SystemPromptSections`、会话顶部状态区）由各包只做追加式改动，主控按 A → B → C 的顺序合并并处理冲突。
- B 只调用调度器契约。C 合并前，B 用测试替身验证 `on_exit = wake`。

## 验收

- JVM 测试：
  - 画布：名称生成与重名、工作区写出（私有根、目录根）、外部修改导入为新版本、写出失败不丢私有版本、分支后重新写出、删除会话保留工作区文件。
  - 后台任务：
    - 内置后台任务的启动、日志写入、退出码、停止整个进程组、超时。
    - 停止本轮生成不影响后台任务，删除会话会停止。
    - 重启后标为 INTERRUPTED 并清理残留。
    - 私有根对账与前台写入交错。
  - Termux 启动脚本的生成与转义。
  - cron 解析与下次触发计算（含跨月、步长、时区变化）。
  - 定时任务排期：间隔下限、精确退回非精确、错过合并、`max_runs` 与过期。
  - 调度器：等待就绪、会话载入、忙碌排队与 30 分钟跳过、HEADLESS 下 `awaitDecision` 立即暂停、审批暂停后可继续。
  - 数据库 v25 → v26 迁移。
  - 渠道清单脚本拒绝 Play 中的精确闹钟权限。
- 门禁全套（含 `--rerun` 单元测试、lint、两个渠道构建、截图测试）与 `scripts/ci/check-distribution-manifests.py`。
- 设备测试（emulator-5564）：
  - 画布卡片的轻点与长按菜单。
  - 后台任务面板展示与停止。
  - 触发消息的渲染。
  - 用 1 分钟单次任务验证精确闹钟到点在原会话发起一轮。
  - 通知深链进入会话。
- 真机（侧载版，DeepSeek V4 Flash）：
  - 画布生成后在工作区 `canvas/` 中可见；用 `patch_file` 改动后打开画布显示为新版本。
  - 让 Agent 后台运行 `sleep 90; echo done` 并同时做别的事，结束后自动唤醒并基于日志继续。
  - Termux 后台任务的启动、查询和停止。
  - 建一个 5 分钟间隔的精确定时任务，锁屏后到点自动发起一轮并通知；需要审批时暂停并通知，点通知进入会话批准后继续。
  - 记录 ColorOS 后台冻结对两类唤醒的影响。

## 风险

- ColorOS 后台冻结：Hans 会冻结后台应用，精确闹钟和后台任务都可能被推迟。验收如实记录，设置页引导开启“允许后台运行”和“不受电池优化限制”。后者同时是官方的后台启动前台服务豁免条件。
- dataSync 前台服务每 24 小时共享 6 小时，频繁触发的长轮次会耗尽额度。超额时启动失败，按“中断”记录并通知。
- Play 版非精确 Job 的执行窗口有限，长轮次可能被系统停止。官方文档未给出精确时长，施工中以 `JobParameters` 的停止原因实测并写入文档。
- 私有根工作区的后台任务不再持锁，存在与前台工具并发写同一文件的可能。按 B 中的对账核查结论决定是否改用临时目录。
- 定时任务每次触发都是完整模型调用，可能产生费用。确认卡片与列表都展示频率，用量统计照常计入用量专区。
