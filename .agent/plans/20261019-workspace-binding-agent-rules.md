# 自选工作区、Agent 用户规则、项目规则与 Git 凭据实施计划

## 目标

1. 用户可通过可视化目录选择器为 Agent 或单个会话指定工作区；未指定时沿用每会话私有工作区。多个会话可同时使用同一工作区，删除会话不删除自选工作区。文件工具、`shell_exec` 与内置 git 都在当前工作区运行。
2. 每个 Agent 有独立的“用户规则”文本。Agent 通过专用工具读取并提出修改；用户在审批卡中看到差异并批准后，由工具直接写入。
3. 自动读取当前工作区根目录的 `AGENTS.md`（不存在时读 `CLAUDE.md`）作为项目规则；首次加载或内容变化时由用户确认是否信任。
4. 内置 git 通过 HTTPS 推送拉取私有仓库，凭据安全存储，不进入模型上下文、命令文本、输出或日志。

## 非目标

- 不做全局用户规则。
- 不给工作区加会话锁；多会话并发修改交给 git 与工具的乐观并发校验，冲突由用户与 Agent 处理。
- 不自动把自选工作区整体纳入知识库向量索引。
- 不支持 SSH 协议的 git 远端（内置 git 为 dulwich，Android 沙箱无 ssh 客户端）；需要时继续使用 `termux_exec` 或 `ssh_exec`。
- Play 渠道不申请“所有文件访问”。

## 已验证事实

- 私有工作区位于 `filesDir/session_workspaces/<sha256(sessionId)>`，由 Room `workspace_files` 树跟踪。`WorkspaceRepository` 强制一个物理根只属于一个会话，绑定后不可修改。会话删除要求根目录直接位于 `session_workspaces` 下，并原子移动后整树删除。
- 外部挂载存于 `external_mounts`（`saf_tree` / `real_path`，全局或会话范围），模型以 `/mnt/<别名>` 访问。后端支持 stat/list/read/write（`expectedSha256`、`createOnly`）/createDirectory/delete，移动为复制校验后删除，删除先进入 `external-recycle`，diff 依赖 `external-versions` 缓存。
- `real_path` 后端仅 sideload 渠道提供，需要 `MANAGE_EXTERNAL_STORAGE`。路径校验要求位于共享卷内、不进入 `Android/`、逐级拒绝符号链接，并用 `O_NOFOLLOW` 打开。当前只能手输路径，没有可视化选择器。Play 渠道只有 SAF。SAF 是内容提供者接口而非文件系统路径，shell 与 dulwich 无法在 SAF 树内运行。
- `shell_exec` 在 App 沙箱中以 `/system/bin/sh` 运行，工作目录为会话私有根，经 `runCommandInWorkspace` 持锁并前后扫描对账 Room；看不到 `/mnt`。内置 git 是 shell 函数 `python3 -m nexara_git`，转发到 `dulwich.cli`，只支持 HTTPS 远端，没有凭据注入。
- 共享存储（API 31 与 API 35 模拟器实测）大小写不敏感、不支持符号链接、`chmod` 无效且不可执行。dulwich 1.2.17 读取 `core.symlinks`（为 false 时把符号链接检出为普通文件）与 `core.filemode`。
- dulwich 的 porcelain 网络函数接受 `username`/`password`，但 `dulwich.cli` 不传递；HTTP 客户端从 URL userinfo 取凭据，不执行 `credential.helper`。同一 uid 的 shell 命令能读到环境变量、继承的 fd 与私有文件，因此凭据不能交给 shell 进程。
- Agent 无规则字段，无工具修改 Agent。`ContextBuilder.buildSystemPrompt` 每个工具轮次重建，顺序为 Agent 提示词 → Session Instructions → 工具说明 → 技能目录 → 计划 → 检索资料 → 摘要 → 时间。全仓库没有读取 `AGENTS.md`/`CLAUDE.md` 的逻辑。
- 数据库版本 21。版本号与 identity hash 写死在 `NexaraDatabase`、`DualDatabaseRecoveryContract`、`RoomBackupDataSource`。备份按整行导出 agents 并要求列集合与当前 schema 一致，备份 payload 版本为 4。
- `AgentEditViewModel.buildAgent()` 从界面状态重建整个 Agent 再整行写入，新字段必须在此保留，否则编辑页保存会清空规则。
- 备份只覆盖 Room 跟踪的私有工作区文件，总量上限 16 MiB、文件项上限 10 000（`BackupPackageLimits`），超限时整份备份失败。`.git/` 下的对象照常进入 Room 索引（只是不向量化），因此在私有工作区 clone 稍大的仓库就会让本地与 WebDAV 备份失败。`android:allowBackup="false"`，卸载或清除数据会删除 `filesDir` 下全部内容。
- 内置 git 的性能（Mac、Python 3.14、同一 dulwich 1.2.17 纯 Python wheel 实测）：requests 仓库完整 clone 5.2 秒（原生 git 3.1 秒）；Django 完整 clone 76.5 秒（原生 44.9 秒，网络占主要部分）；Django 工作树 7 084 个文件的 status 1.3 秒（原生 0.2 秒）；2 万文件合成仓库的 status 1.0 秒。Django `--depth 1` clone 用时 43 秒、`.git` 146 MB，原生 git 5.5 秒、13 MB：`nexara_git` 只把引用限制到分支与标签，浅克隆仍会拉取全部标签，每个标签带一份完整快照；只取默认分支时降为 6.1 秒、13 MB，`git fsck` 无错误。手机上的耗时尚未测量。
- 打包的是 dulwich 纯 Python wheel，不含其可选的 Rust 加速模块（`_objects`、`_pack`、`_diff_tree`）。dulwich 本身提供 submodule、worktree、bisect、gc、maintenance、lfs、sparse-checkout 等命令，`nexara_git` 的白名单未开放；SSH 远端需要 ssh 程序或 paramiko 及其原生依赖，App 内均不具备。
- Termux 以独立 uid 运行，无法读取 Nexara 的 `filesDir`；只能经 `termux-setup-storage` 访问共享存储。
- `ssh_host_save`/`ssh_host_delete` 的模式可复用：工具列表按设备开关过滤，执行时再检查；`ToolArgumentApprovalRules` 让这些工具在任何模式下逐次审批。审批预览只基于参数，`infra/util/MyersDiff.kt` 可计算行差异。

## 设计决策

### 工作区类型

| 类型 | 位置 | 渠道 | 跟踪方式 | 适用 |
|---|---|---|---|---|
| 会话私有工作区（默认） | `filesDir/session_workspaces/…` | 全部 | Room 树，含版本、回收站、向量索引 | 一次性对话、知识库 |
| Nexara 项目文件夹 | `filesDir/projects/<id>` | 全部 | 不进 Room，按目录直接读写；不进备份，卸载即删除 | Play 用户、需要完整 POSIX 语义的仓库 |
| 设备共享文件夹 | `/storage/emulated/0/…` 等共享卷 | sideload | 不进 Room，按目录直接读写；卸载后保留 | 需要与文件管理器、Termux、电脑同步共享的仓库 |

仓库的持久备份以推送到远端为准；会话私有工作区不适合存放 git 仓库，`nexara_git clone` 在私有工作区执行时提示改用自选工作区。

自选工作区不进 Room：避免在用户仓库内写 `.nexara*` 标记、避免对大仓库全量扫描与对账、避免与私有根的独占约束冲突。两类自选工作区共用一个“目录工作区”后端，由现有 `RealPathExternalFolderBackend` 下沉到 main 源集实现；共享卷根仅在 sideload 渠道可选，私有项目根在两个渠道都可选。

### 绑定与解析

- Agent 可设置默认工作区；会话可单独设置。新建会话时继承 Agent 当前默认值并记录在会话上；之后修改 Agent 默认值只影响新会话，避免进行中的对话路径突然改变。
- 解析顺序：会话设置 → 会话私有工作区。会话切换工作区时插入一条可见的系统提示，并在下一轮系统提示词中写明当前根目录，让模型知道历史路径可能已失效。
- 私有工作区在切换后保留，切回即可继续使用；会话删除只删除私有工作区，自选工作区只解除引用。
- 工作区记录（`workspaces` 表：id、类型、显示名、绝对路径、创建时间）独立于会话；会话与 Agent 只保存 `workspace_id`。目录不存在或权限丢失时，会话显示“工作区不可用”并拒绝在该工作区执行，不自动回退到私有工作区。

### 工具行为

- 当前工作区为自选目录时，文件工具的 `/` 指向该目录，经目录后端读写，保留 `expectedSha256` 乐观并发校验、删除回收与差异缓存；`/mnt/<别名>` 挂载照旧。
- `shell_exec` 在自选目录中运行，不持工作区锁、不做 Room 对账；结果不再提供 `workspace_changes`，工具说明提示用 `git status` 查看改动。
- `nexara_git` 的抓取范围对齐 git 语义：`--depth` 隐含 `--single-branch`，只取远端默认分支（或 `-b` 指定分支）及指向其历史的标签；支持 `--single-branch`、`--no-single-branch`、`--no-tags`。
- 共享卷工作区内运行 git 时，wrapper 自动设置 `core.symlinks=false`、`core.filemode=false`、`core.ignorecase=true`，并在 clone 或 checkout 遇到符号链接或仅大小写不同的路径时给出说明。

### 可视化选择器

- 设置入口在 Agent 编辑页和会话设置面板：“工作区”项显示当前名称与路径，点开为目录浏览器。浏览器逐级列出目录（跳过符号链接与 `Android/`），支持新建文件夹，底部“使用此文件夹”。
- 浏览器顶层列出可选根：Nexara 项目文件夹（可新建项目），sideload 渠道另列共享存储卷；未获“所有文件访问”授权时显示授权引导。
- 知识库“全部文件”保持 Room 树不变，新增“工作区”分组，列出已登记的自选工作区，可浏览、打开与把单个文件加入知识库（复用现有快照复制），不自动向量化整个目录。

### 差异视图与 Git 变更面板

- 新增可复用的差异视图组件，基于 `MyersDiff` 计算行差异，提供统一视图与并排视图（窄屏默认统一视图），行号、增删着色、折叠未变更区段、长行横向滚动限定在组件内部。大文件与二进制文件只显示摘要。
- 工具执行差异指示器：文件类工具（写入、补丁、删除、移动）的执行卡显示每个文件的路径与 `+N −M`，卡片标题汇总“N 个文件 +X −Y”；新建文件只有 `+`，删除只有 `−`，二进制文件标注“二进制”。行数在工具执行时由写入前后内容计算，随工具结果持久化，历史消息无需重算；单文件超过上限时只显示“大文件”。点击指示器打开对应文件的差异视图（读取版本历史或差异缓存；缓存已失效时只保留数字）。`shell_exec` 在私有工作区按 `workspace_changes` 显示改动文件列表；自选工作区不逐次统计，由 Git 变更面板查看。
- 使用位置：`write_file`/`patch_file` 等文件修改的审批卡与执行结果、`agent_rules_update` 审批卡、规则历史版本对比、Git 变更面板。
- Git 变更面板：当前工作区为 git 仓库时，会话工作区入口显示分支与改动数；面板列出已暂存、未暂存、未跟踪文件，点开单个文件显示相对 HEAD 的差异，另有最近提交列表与单个提交的差异。数据经 `nexara_git` 新增的只读 JSON 子命令获取（状态、HEAD 中的文件内容、提交列表），由 Kotlin 计算差异，不经 `shell_exec` 与执行审批。
- 首版面板只读；暂存、提交、推送、冲突处理仍由用户在对话中交给 Agent 执行，避免在界面重复实现一套写操作。

### 内置 git 性能

按投入从低到高推进，每一步以手机实测决定是否进入下一步：

1. 抓取范围对齐 git 语义（见“工具行为”），消除浅克隆拉取全部标签快照的问题；clone 与 fetch 显示进度。
2. 交叉编译 dulwich 的可选 Rust 加速模块（maturin + NDK，cp314 arm64），对比对象解析、pack 解增量与树差异的耗时，收益不足则不引入。
3. 共享存储工作区的大仓库可经已有的 `termux_exec` 使用 Termux 的原生 git；`nexara_git` 在遇到超大仓库或不支持的命令时给出该指引。
4. 前三步仍不满足时，评估随 APK 打包原生 git（NDK 交叉编译 git、libcurl、OpenSSL、zlib，以 `lib*.so` 形式放入原生库目录以满足 Android 10+ 的 W^X 限制），需核算 APK 50 MB 上限、证书包、`git-remote-https` 执行路径与凭据代理的兼容方式。

### Agent 用户规则

- `agents` 表新增 `user_rules`（TEXT，默认空串）。Agent 编辑页在“人格”分区后新增“用户规则”，复用 `UnifiedPromptEditor`；`buildAgent()` 与 `isDirty` 纳入该字段。
- 新工具：`agent_rules_read`（SAFE_READ）返回当前 Agent 规则与内容哈希；`agent_rules_update`（新风险类别 AGENT_CONFIG）参数为完整新规则与 `expected_hash`，任何执行模式下逐次审批。审批卡异步读取当前规则，以 `MyersDiff` 展示差异；执行时哈希不一致返回 `RULES_CHANGED`，不覆盖。写入使用单列 UPDATE，避免与编辑页整行写入互相覆盖；每次写入保留最近 20 个历史版本，编辑页可查看与回滚。
- 规则注入位置：Agent 提示词之后，标题 `## User Rules`。

### 项目规则

- 每轮从当前工作区根读取 `AGENTS.md`，不存在时读取 `CLAUDE.md`，上限 32 KiB，超出截断并说明。
- 以“工作区根路径 + 文件 SHA-256”记录信任。首次出现或内容变化时，生成前展示确认卡（显示来源路径、大小与前若干行）：信任后注入；不信任则本次与后续不注入，直到内容再次变化。
- 注入位置在 User Rules 之后，标题 `## Project Rules (<文件名>)`，并声明其优先级低于系统安全约束与审批规则。内容变化从下一轮生效。

### Git 凭据

- 设置页新增“Git 凭据”：按主机（如 `github.com`）保存用户名与令牌，存入 `SecretStore`，不进备份。
- 凭据代理：Kotlin 在 `127.0.0.1` 随机端口启动凭据注入代理。wrapper 把 dulwich 的 HTTPS 请求改发到代理，并带上本次命令的一次性 nonce；代理只接受 git 智能协议路径（`/info/refs`、`/git-upload-pack`、`/git-receive-pack`），只对已配置主机添加 `Authorization` 后经 HTTPS 转发。令牌只存在于 Kotlin 进程内存。
- shell 进程只能看到 nonce，nonce 随命令结束失效，且只能用于 git 智能协议路径，无法读出令牌或调用其它 API。
- push 仍是普通 `shell_exec` 命令，沿用现有执行审批；`nexara_git` 的用法说明改为引导配置 Git 凭据，不再建议把令牌写进 URL。

## 分期与关键路径

1. **P1 数据层（单次迁移 21→22）**：新增 `workspaces` 表；`sessions.workspace_id`、`agents.default_workspace_id`、`agents.user_rules`、`agent_rule_revisions` 表；同步 identity hash、双库恢复契约、备份校验与 payload v5 升级（新列默认值）；迁移、备份恢复与 schema 测试。
2. **P2 目录工作区后端与执行**：后端下沉到 main，共享卷根按渠道开放；`WorkspaceResolver` 统一解析当前工作区；文件工具、脚本网关、`SessionToolTargetResolver`、`shell_exec`/`python_exec` 与 git 配置接入；私有工作区 clone 提示；`nexara_git` 抓取范围对齐 git 语义；删除流程识别自选工作区只解除引用。
3. **P3 差异视图组件**：统一与并排视图、折叠与大文件降级；工具执行差异指示器（执行时计算、随结果持久化）；接入文件修改审批卡与执行结果；截图预览。
4. **P4 选择器与界面**：目录浏览器组件、Agent 编辑页与会话设置入口、知识库“工作区”分组、切换提示与不可用状态；`nexara_git` 只读 JSON 子命令与 Git 变更面板；截图预览与设备测试。
5. **P5 Agent 用户规则**：编辑页、`agent_rules_read`/`agent_rules_update`、审批差异卡、历史与回滚、提示词注入。
6. **P6 项目规则**：读取、截断、信任记录与确认卡、提示词注入。
7. **P7 Git 凭据代理**：凭据设置页、代理、wrapper 改造、nonce 生命周期、Python 与 JVM 测试。

P1 先于其它各期；P2 先于 P4、P6；P3 不依赖其它各期；P4 的 Git 面板与 P5 的审批卡依赖 P3；P5 只另依赖 P1；P7 只依赖 P2 的执行环境接线。每期独立提交推送。

## 不变量

- 删除会话、删除 Agent、恢复备份都不会删除或改写自选工作区目录内的任何文件。
- 自选工作区内不写入 `.nexara*` 标记；Nexara 自身的回收与差异缓存存放在 App 私有目录。
- 目录后端逐级拒绝符号链接与 `..`，共享卷根不进入 `Android/`，所有路径解析后仍位于工作区根内。
- 规则修改与项目规则注入都需要用户明确批准或信任；Agent 不能修改自身身份提示词。
- Git 令牌不出现在参数、命令文本、环境变量、文件、stdout/stderr、日志、异常与备份中。
- 网络与文件 IO 在 `Dispatchers.IO`。

## 验收

- 单元测试：迁移 21→22 与备份 v4→v5 升级；工作区解析优先级、不可用状态、删除只解除引用；目录后端路径逃逸、符号链接、并发写冲突；`shell_exec` 工作目录；规则读写、哈希冲突、历史回滚、编辑页保存不清空规则；项目规则选择、截断、信任与变化重确认；代理主机/路径/nonce 限制与令牌不外泄；`nexara_git` 在共享卷上的配置注入与只读 JSON 子命令；差异组件的统一/并排行对齐、折叠区段与大文件降级；差异指示器的增删计数（新建、删除、二进制、超限）与持久化；浅克隆只取默认分支。
- 设备测试（emulator-5562）：选择器在私有项目根与共享卷根选择目录；两个会话共用同一工作区读写同一文件；删除其中一个会话后目录完好；在共享卷与私有项目根中 `git clone`（公开仓库）、提交、查看日志。
- 性能：真机在私有项目根与共享卷根分别记录 Django `--depth 1` clone、完整 clone 与 status 耗时，作为是否推进 Rust 加速模块或原生 git 的依据。
- 真机：sideload Release 选择共享存储目录作为工作区，DeepSeek 会话通过 `shell_exec` 完成 clone、修改、commit；配置 Git 凭据后推送到用户指定的测试仓库（需用户提供仓库与令牌并在设置页自行输入）。

```bash
cd native-ui
./gradlew :app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest
./gradlew :app:lintSideloadDebug :app:assembleSideloadDebug :app:assemblePlayDebug
./gradlew :app:validateSideloadDebugScreenshotTest
cd ..
python3 scripts/ci/check-distribution-manifests.py
python3 -m unittest scripts/tests/test_nexara_git.py
```

## 风险

- 共享存储 FUSE 上的 IO 明显慢于私有目录，大仓库 clone 与 `git status` 耗时较长；需在设备测试中记录耗时。
- 含符号链接、可执行位或仅大小写不同路径的仓库在共享卷上无法完全还原，只能降级处理并提示，此类仓库应使用 Nexara 项目文件夹。
- 自选工作区不进 Room，文件面板、版本历史与向量索引能力弱于私有工作区。
- Nexara 项目文件夹不进备份且卸载即删除，未推送的提交会随之丢失；界面需在该类型说明中明示。
- `dulwich` 纯 Python 实现，大仓库的 status 与本地对象处理明显慢于原生 git，手机 CPU 上差距可能更大；SSH 远端不可用，其它高级命令需逐个验证后再开放白名单。
- Play 渠道的共享存储能力依赖“所有文件访问”政策审批，当前不纳入。
