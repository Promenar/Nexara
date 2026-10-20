# 技能文件系统运行时：用户级与项目级技能实施计划

> 状态（2026-10-20）：S1–S7 完成。S1–S6 已合入 `B-native-refactor`；技能目录与技能恢复设备测试在 API 31/35 ARM64 模拟器通过；S7 真机（Android 16）与 DeepSeek V4 Flash 验收通过，期间修复技能分区恢复调用 Android 禁用的 `Files.getFileStore` 导致恢复失败的问题。

## 目标

1. 技能以目录形式存放在虚拟文件系统中，文件是唯一事实源。目录格式遵循 Codex 技能规范：每个技能一个目录，必含 `SKILL.md`，可选 `scripts/`、`references/`、`assets/`、`agents/openai.yaml`。
2. 技能分两级：
   - **用户级**：虚拟路径 `/skills`，所有 Agent、所有会话、所有工作区共享。
   - **项目级**：目录型工作区（项目目录、共享存储目录）根下的 `.agents/skills/`，只在该工作区生效；会话私有工作区没有项目级技能。
3. Agent 和全部工具（文件工具、脚本网关、`shell_exec`、`python_exec`、内置 git）都能访问两级技能目录，可以查看、新建、修改、删除技能，可以在工作区与技能目录之间复制、移动；技能目录可以是 git 仓库，支持 clone/pull/push。
4. 按需加载：系统提示词只列出技能的名称、描述与路径，模型匹配后再读取正文，需要时读取附带文件或运行附带脚本。
5. 会话设置上滑面板“工具”标签内提供技能视图，按用户级、项目级分组，支持基础管理；详细编辑交给 Agent。设置页“技能包”标签保留为用户级技能的查看、管理与导入入口。
6. 用户级技能纳入本地与 WebDAV 备份，换机或重装后随备份恢复。
7. “工具”标签按当前有效能力重新编排；审批白名单统一为全局级，在设置页集中查看与管理。

## 非目标

- 不提供技能市场或在线检索安装。
- 不在界面内做完整的技能编辑器。
- 不支持 Codex 的 ADMIN 级（`/etc/codex/skills`）与配置文件 `config.toml`；停用状态由 Nexara 设置保存。
- 不扫描 `.claude/skills/` 等其它客户端的约定目录。

## 外部规范依据（2026-10-20 核对 Codex 官方文档 https://learn.chatgpt.com/docs/build-skills ）

- 扫描位置：REPO 级为当前目录到仓库根之间每一级的 `.agents/skills`；USER 级为 `$HOME/.agents/skills`；另有 ADMIN 级与内置 SYSTEM 级。
- `SKILL.md` frontmatter 必含 `name` 与 `description`；正文为使用说明。
- `agents/openai.yaml` 可选，提供界面元数据、`policy.allow_implicit_invocation`（默认 true，为 false 时只在用户显式点名时使用）与工具依赖声明。
- 初始技能清单预算为上下文窗口的 2% 或 8000 字符；同名技能不合并，同时列出。

## 已验证事实（2026-10-20，基于当前工作树源码）

- 现有技能存储为 `AgentSkillStore`，根目录 `filesDir/agent_skills/<name>/`，frontmatter 名称须与目录名一致，否则静默忽略。安装经界面导入 zip、导入 `SKILL.md` 或应用内新建，先写暂存目录再重命名切换。限制：单技能 200 个文件、单文件 1 MiB、总量 5 MiB，单次读取 256 KiB。
- 启用状态存在 SharedPreferences `agent_skills_enabled`；内存索引只在 `AgentSkillStore` 自身操作后重载。
- 按需加载三层：`SystemPromptSections.skillCatalog`（最多 50 项、4000 字符）、`activate_skill`（返回正文与文件清单，并声明附带脚本“只供参考、不能执行”）、`read_skill_file`；两个工具为 `SAFE_READ`，只在存在已启用技能时提供。
- 技能目录不在虚拟文件系统内，任何工具都无法访问。
- 虚拟路径体系：外部挂载为 `/mnt/<别名>`，`mnt` 是保留别名（`ExternalMountPaths.validateAlias`）。自选目录工作区为“根模式”，`/` 下除 `/mnt` 外的路径都属于工作区挂载（`ExternalMountPaths.aliasOf`）。
- `shell_exec`/`python_exec`/内置 git 运行真实进程，cwd 为工作区真实路径，环境变量白名单由 `BuiltinExecEnvironment.forWorkspace` 构造（HOME、TMPDIR、`NEXARA_WORKSPACE`、`NEXARA_WORKSPACE_KIND`、`NEXARA_BACKEND` 等），真实进程看不到 `/mnt`、`/skills` 这类虚拟路径。
- 时间注入：`ContextBuilder.buildSystemPrompt` 在系统提示词末尾追加 `[System Time: yyyy-MM-dd HH:mm EEEE (时区)]`，取值为构建时刻；`ChatGenerationRunner` 在首轮、每个工具续轮与进入最终回答模式时都调用 `runtime.buildContext`，因此每次模型调用都携带最新时间（分钟精度）。
- `economyMode`（Token 节约模式）只决定任务计划写入上下文的方式（`appendEconomyTaskContext` 与 `appendFullTaskContext`），不影响其它部分。
- `toolsEnabled`（界面名“搜索与检索”）实际是会话工具总开关：关闭时 `DefaultSessionToolResolver` 返回空工具表，`ToolExecutor` 对工具调用返回禁用警告，`ContextBuilder` 省略通用工具指引。
- 审批授权：`SessionToolGrantStore` 按会话保存 `(toolName, target)`，审批卡片“始终允许”经 `ChatViewModel.alwaysAllowTarget` 写入；不存在全局白名单。
- 备份：只覆盖 Room 跟踪的私有工作区文件（`BackupPackageLimits`，载荷版本 v5）；`filesDir/agent_skills` 不在备份内。

## 设计决策

### `/skills` 的访问方式

- **物理位置**：沿用 `filesDir/agent_skills/`，现有技能原地生效，无需迁移。
- **文件工具与脚本网关**：`/skills` 作为与 `/mnt` 同级的保留虚拟根，在所有工作区模式下都可见，映射到物理目录；实现为一个固定的内部挂载后端，复用挂载体系已有的 stat/list/read/write/createDirectory/delete 与回收区。`skills` 加入保留名称，外部挂载不能使用该别名。
- **根模式下的遮蔽**：`/skills` 路径固定，不随工作区变化（用户已确认）。自选目录工作区根下若有 `skills/`（常见于开发技能的仓库），文件工具中的 `/skills` 指向用户级目录，该仓库目录只能经 `shell_exec` 以相对路径 `./skills` 访问；工作区存在此目录时，工具指引向模型说明这一点。
- **真实进程**：`BuiltinExecEnvironment.forWorkspace` 导出 `NEXARA_SKILLS=<物理路径>`，并在 HOME 下建立符号链接 `~/.agents/skills → <物理路径>`，与 Codex 的 USER 级位置一致；shell 指引写明“`/skills` 在 shell 中对应 `$NEXARA_SKILLS`”。内置 git 因此可以直接 `git -C "$NEXARA_SKILLS" pull`。
- **项目级目录**：位于工作区内（`<工作区根>/.agents/skills`），所有工具按工作区原有方式访问，无需额外映射。

### 目录格式与发现（遵循 Codex）

- **用户级**：`/skills/<name>/SKILL.md`。根目录本身可以是 git 仓库；扫描深度为一级，嵌套布局（如 `skills/<name>/`）需要用户把技能目录放到一级。
- **项目级**：扫描目录型工作区根下的 `.agents/skills/<name>/SKILL.md`。Codex 会从当前目录向上扫描到仓库根；Nexara 的会话目录就是工作区根，根以上不在虚拟文件系统可见范围内，因此只扫描这一级。
- **frontmatter**：`name`、`description` 必填；名称与目录名不一致时标记为损坏并在界面显示原因。
- **`agents/openai.yaml`**：读取 `interface.display_name`、`interface.short_description` 用于界面；`policy.allow_implicit_invocation: false` 的技能不进入自动清单，只在用户以 `$技能名` 点名时提供给模型。工具依赖声明只在界面展示，不自动安装。
- **同名**：与 Codex 一致不合并，两级同名都列出并标注来源，模型按路径区分。
- **重载**：每轮构建上下文前按目录修改时间增量重扫；损坏、超限的技能不进入清单，界面显示原因。
- **清单预算**：取模型上下文窗口的 2% 与 8000 字符中的较小值；超出时按启用状态与最近使用排序截断，并告知模型清单不完整。

### 模型侧

- 技能清单每项包含名称、描述、来源级别与 `SKILL.md` 的虚拟路径，模型可以直接用文件工具读取。
- `activate_skill`、`read_skill_file` 保留，使只启用技能、未启用文件工具的 Agent 也能使用技能；参数接受名称或路径，同名时要求路径。
- `activate_skill` 的说明写明附带脚本可以经执行工具运行，并沿用执行工具的审批规则。

### 启用、信任与写入安全

- 用户级技能的启用状态全局生效，保存在 Nexara 设置中，按技能目录路径记录。
- 新出现的用户级技能（Agent 新建、git 拉取、外部复制）默认停用，由用户在界面确认后启用；用户在设置页导入的技能属于用户本人操作，导入即启用。已启用技能被修改后保持启用，界面标注“已修改”，用户查看并确认后清除标注。
- 项目级技能来自仓库内容，按不受信任输入处理：按“工作区根路径 + 技能目录内容哈希”记录信任，首次出现或内容变化时须用户确认后才进入清单。
- 文件工具与脚本网关对两级技能目录的写入、移动、删除逐次审批并展示差异，全局白名单不覆盖这些路径。`shell_exec`/`python_exec` 无法按路径拦截写入，安全边界由执行工具本身的审批与上面的启用/信任状态共同承担。

### “工具”标签编排

自上而下：

1. **执行模式**，与“启用工具”总开关（`toolsEnabled` 的正确命名，关闭后本会话不提供任何工具）并列置顶。
2. **技能**：用户级、项目级两组，每行显示名称、描述、状态（已启用、停用、已修改、待信任、损坏及原因、`仅显式调用`），支持启用/停用、查看 `SKILL.md`（只读）、删除（先确认，进入回收区）；组尾提供“在文件中打开技能目录”入口。
3. **自定义工具与 MCP 服务器**：沿用现有分组。
4. **会话选项**：时间注入（说明文字写明“每次请求附带当前日期时间”）；Gemini 搜索增强（仅 Gemini 模型显示）。

移除项：

- “Token 节约模式”开关：任务计划固定使用一种写法（已完成步骤压缩、当前与未完成步骤完整），会话选项字段保留读取兼容，不再写入。
- “本会话已允许的目标”分组。

### 全局审批白名单

- 审批卡片“始终允许”写入全局白名单，条目为“工具 + 目标”；工作区内文件目标按“工作区 + 路径”记录，网络目标按域名记录，命令按命令前缀记录。
- 设置页提供白名单管理页面：列表、来源（何时由哪个会话加入）、单条删除、全部清空。
- `SessionToolGrantStore` 的现有会话授权不迁移，升级后清除（用户已确认：现有会话均为测试会话）。

### 设置页“技能包”标签

- 保留，作为用户级技能的查看与管理入口，与会话面板共用同一数据源。
- 导入 zip 与 `SKILL.md` 的入口保留，导入结果写入 `/skills` 并启用。

### 备份

- 备份包新增 `skills/` 分区，内容为 `/skills` 整棵目录与启用状态；项目级技能随所在工作区的备份范围处理。
- 分区限额独立于 `BackupPackageLimits` 的工作区限额：`.git` 目录在分区总量不超过 64 MiB 时一并备份，超出时跳过 `.git`，记录远端地址与提交号，导入后提示用户重新 clone。
- 备份包格式版本升为 2（数据库载荷版本与加密信封不变），导入兼容格式 1（无技能分区，恢复时不触碰现有技能目录）。恢复时在同一事务 journal 中整体替换用户级技能目录。
- 跳过 `.git` 的仓库在恢复后由主界面提示，列出路径、远端地址、分支与提交号。
- WebDAV 备份使用同一包格式。

## 分期与关键路径

1. **S1 技能仓库层**：两级扫描与增量重载、Codex frontmatter 与 `agents/openai.yaml` 解析、损坏原因、启用/信任/已修改状态、现有 `agent_skills` 与启用集合兼容。
2. **S2 虚拟文件系统接入**：`/skills` 保留根与内部挂载后端、`NEXARA_SKILLS` 与 `~/.agents/skills` 链接、根模式遮蔽提示、技能目录写入专项审批。
3. **S3 模型侧**：清单含路径与来源、预算、`$技能名` 显式调用、`activate_skill`/`read_skill_file` 支持两级与路径、脚本说明。
4. **S4 “工具”标签编排与全局审批白名单**：标签重排、移除两个分组/开关、全局白名单存储与设置页管理、会话授权清除。
5. **S5 界面**：会话面板技能视图、设置页技能包标签接入新数据源、截图预览。
6. **S6 备份**：技能分区、包格式 2 与格式 1 兼容、`.git` 限额策略、本地与 WebDAV 往返测试、恢复后重新 clone 提示。
7. **S7 验收**：真机验收（见下）。

S1 先于 S2、S3、S5、S6；S4 与 S1–S3 无依赖，可以独立实施；每期独立提交推送。

## 执行编排

共享接口 `data/skills/SkillCatalog.kt`（`SkillCatalog`、`DiscoveredSkill`、`SkillSnapshot`、`SkillWorkspace`）先于各工作包提交，作为施工契约。纯后端业务逻辑由 Codex CLI（`gpt-6.1-sol`，推理 `max`，`service_tier=priority` 快速档）在独立 worktree 施工；界面由主控施工；每个工作包合并前由主控复核 diff 并运行定向测试。

| 工作包 | 内容 | 执行者 | 依赖 | 写入范围 |
|---|---|---|---|---|
| A | S1 技能仓库层：实现 `SkillCatalog` | Codex | 共享接口 | `data/skills/**` 及其测试；`NexaraApplication` 只加构造 |
| B | S4 后端：全局审批白名单、清除会话授权 | Codex | 无 | 审批与授权相关文件；`SessionSettingsSheet.kt` 只删除会话授权分组 |
| C | S6 备份技能分区 | Codex | 无（技能根目录与状态文件位置由共享接口约定） | `data/backup/**` 及其测试 |
| D | S2 虚拟文件系统接入与技能目录写入审批 | Codex | A、B 合并后 | 挂载、脚本网关、执行环境 |
| E | S3 模型侧、移除 Token 节约模式 | Codex | A、B 合并后 | 提示词、技能工具、`ContextBuilder` |
| U | S4 与 S5 界面：“工具”标签重排、技能视图、白名单管理页、设置页技能包标签 | 主控 | 接口提交后即可开工，接入 A、B 实现后完成 | 界面与字符串资源 |

A、B、C 并行；D、E 在 A、B 合并后并行，D 完成后由未参与施工的审阅者独立复核写入审批与路径映射；最后 S7 真机验收。状态记录（启用、信任、已修改基线）存放在 `noBackupFilesDir`，不写入技能目录；用户级启用集合沿用已在备份范围内的 `agent_skills_enabled`。

## 验收

- 单元测试：两级扫描与重载、frontmatter 与 `openai.yaml` 解析、损坏原因、同名并列、信任随内容哈希失效、已修改标注、启用状态迁移、技能目录写入不受全局白名单覆盖、清单预算截断、备份格式 2 往返与格式 1 兼容、`.git` 超限降级。
- 设备测试：Agent 用文件工具在 `/skills` 新建技能后，下一轮出现在清单中（停用，确认后启用）；`shell_exec` 中 `git clone` 到 `$NEXARA_SKILLS` 后可发现并启用；附带脚本经 `shell_exec` 运行；切换工作区后项目级技能随之变化并触发信任确认；备份后清除数据再恢复，技能与启用状态一致。
- 截图：会话面板“工具”标签与技能视图、全局白名单管理页的浅色、深色与 1.3 倍字号。
