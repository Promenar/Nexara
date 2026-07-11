# Nexara 审计报告 — 模块 F：工作区系统 + 任务管理模块

> 审计范围：工作区文件系统（创建/切换/删除、文件读写删、安全边界、与 Skill 的关系、UI 展示）以及任务管理（创建/更新/完成/删除、与对话关联、持久化、可视化）。
> 审计方式：只读代码审计，未修改任何文件。
> 报告日期：2026-07-05

---

## 架构概览（先理清事实，再谈问题）

### 工作区系统是"混合模型"：虚拟元数据 + 真实磁盘文件

工作区不是纯虚拟文件系统，也不是纯真实文件系统，而是两者的混合：

- **数据库层（`workspace_files` 表）**：存储文件元数据（UUID、parentUuid、name、hash、materializedPath、physicalRootPath、是否在回收站、向量化状态等）。见 `FileEntry.kt:8-82`。
- **磁盘层**：每个 `FileEntry` 持有两个路径字段：`physicalRootPath`（工作区物理根目录）和 `materializedPath`（相对根目录的逻辑路径）。真实文件由 `File(physicalRootPath, materializedPath)` 定位，所有读写都直接调用 `java.io.File.writeText()` / `readText()`。见 `WorkspaceRepository.kt:50-52`、`FileOperationRepository.kt:41-42`、`68`、`160-161`。
- **物理根目录**：会话创建时设为 `<app filesDir>/workspaces/<sessionId>`，见 `ChatViewModel.kt:906-908`。RAG 工作区根目录为 `<app filesDir>/rag_workspace`，见 `RagViewModel.kt:66`。应用级总目录 `<app filesDir>/WorkSpace` 在启动时创建，见 `NexaraApplication.kt:267`。

### 文件操作的真实性结论

| 操作 | 实现方式 | 是否真实落盘 |
|---|---|---|
| 创建文件 | `physicalFile.writeText(content)` 后 `dao.insert(entry)` | ✅ 真实写盘 + 元数据入库 |
| 读取文件 | `physicalFile.readLines()` | ✅ 读真实磁盘 |
| 写入/覆盖 | `physicalFile.writeText(newContent)` + `dao.update(hash)` | ✅ 真实写盘 + hash 更新 |
| Patch | 读磁盘 → 内存改行 → `writeText` 落盘 | ✅ 真实写盘 |
| Diff | 读磁盘当前内容 vs basisHash | ✅（但 basis 只能用内存当前内容，见下文） |
| 删除 | `physicalFile.delete()` / `deleteRecursively()` + `dao.deleteByUuid()` | ✅ 真实删除 |
| 回收站 | `srcFile.renameTo(dstFile)` + 元数据标记 | ✅ 真实移动 |

### AI 可用的文件类 Skill

在 `NexaraApplication.kt:205-216` 注册了 6 个文件 Skill 和 4 个任务 Skill：

- `read_file` / `write_file` / `patch_file` / `diff_file` / `list_files` / `search_files`
- `initialize_plan` / `update_plan` / `get_plan` / `drop_plan`

---

## 严重问题（Critical）

### F-C1. AI 在对话中无法创建新文件——`write_file` 需要 UUID 但无 `create_file` 工具

**文件**：`FileWriteSkill.kt:16`、`NexaraApplication.kt:205-216`、`WorkspaceRepository.createFile`（`WorkspaceRepository.kt:41-68`）

**问题描述**：
`write_file` 工具的参数 schema 要求 `uuid`（`FileWriteSkill.kt:16`：`"required":["uuid","content","expectedHash"]`），且实现里第一步就是 `dao.getByUuid(uuid)`，找不到就返回 `NotFound`（`FileOperationRepository.kt:29`）。但**整个代码库中没有任何一个 Skill 能让 AI 创建一个新的 `FileEntry`**。

`workspaceRepository.createFile(...)` 这个创建方法**只被 RAG 导入流程调用**（`RagViewModel.kt:541`、`662`），在 chat 模块的 `ToolExecutor`、`ChatViewModel`、各 Skill 中均无调用。

**用户体验影响**：
用户对 AI 说"帮我新建一个 `notes.md` 文件，写上今天的心得"，AI 会尝试调用 `write_file`，但因为无法获得一个已存在的文件 UUID，必然返回 `File not found` 错误。用户会困惑：明明是"新建"文件，为什么 AI 一直报错、不停重试。AI 在对话中**只能修改已存在的工作区文件，无法凭空创建文件**——但系统提示（`ContextBuilder.kt:291-310`）却告诉 AI "use them when you need ... file operations"，给 AI 制造了它能操作文件的错觉。

**根因**：缺少一个 `create_file`（或 `write_file` 支持新建模式）的工具，且 `WorkspaceRepository.createFile` 没有被 chat 侧任何 Skill 调用。

---

### F-C2. 写文件非原子——名为 `writeFileAtomic`，实际用 `writeText` 直接覆盖，崩溃会丢数据

**文件**：`FileOperationRepository.kt:23-59`（`writeFileAtomic`）、`FileOperationRepository.kt:248`（patch 落盘）、`WorkspaceRepository.kt:52`

**问题描述**：
方法名是 `writeFileAtomic`（暗示原子性），实现却是 `physicalFile.writeText(newContent)`（`FileOperationRepository.kt:42`）。`writeText` 内部是"打开文件 → 截断 → 写入 → 关闭"的非原子序列。若在写入过程中发生 OOM、进程被杀、磁盘满、设备断电，**原文件已被截断，新内容没写完，结果是部分内容或空文件**，而数据库里的 hash 也可能已被更新（`dao.update` 在 `writeText` 之后，`FileOperationRepository.kt:45-52`）。

更糟的是顺序：先 `writeText`（覆盖磁盘），再 `dao.update`（改 hash）。若 `writeText` 成功但 `dao.update` 失败，磁盘是新内容、DB 是旧 hash——下一次 `write_file` 的乐观锁会基于旧 hash 错误地放行，导致静默覆盖。

**用户体验影响**：
用户让 AI 修改一个重要文档，AI 写到一半 app 崩溃。再打开文件发现内容残缺（甚至变空），且因为没有任何版本历史（见 F-C3），**用户无法恢复原来的内容**。对于一个宣称"类 IDE 文件操作能力"的工作区，这是不可接受的数据安全风险。

**正确做法**：写临时文件 `*.tmp` → `fsync` → `renameTo` 原目标（POSIX 原子 rename），成功后再更新 DB hash。

---

### F-C3. 文件操作完全没有版本控制/历史——AI 写错即永久丢失，用户无法回滚

**文件**：整个 `FileOperationRepository.kt`、`WorkspaceRepository.kt`

**问题描述**：
`write_file` 和 `patch_file` 都是**全量覆盖式**写入（`FileOperationRepository.kt:42`、`248`），覆盖前不保留旧版本。数据库只存当前 hash（`FileEntry.kt:27`），没有 `file_versions` 表，没有快照，没有 undo 栈。`diff_file` 的 `basisHash` 还原逻辑形同虚设——`reconstructBasisContent`（`FileOperationRepository.kt:267-277`）只能返回当前磁盘内容或空字符串，因为旧版本从未被保存。

**用户体验影响**：
典型场景：用户让 AI"重构这个文件"，AI 误解意图后用 `write_file` 全量覆盖，原代码瞬间消失。用户发现不对，但**没有任何"撤销"或"恢复到上一版"的入口**（回收站只管"删除"，不管"覆盖"）。用户辛苦写的代码被 AI 一次错误的 `write_file` 永久抹掉。这相比 Claude Code 等工具（有 git、有 checkpoint）是巨大的体验倒退。

---

### F-C4. `patch_file` 不在"高风险工具"白名单——AI 可在无审批下静默篡改文件

**文件**：`ChatViewModel.kt:1705-1720`（`highRiskToolNames` / `determinePendingToolIds`）

**问题描述**：
高风险工具白名单定义为 `setOf("write_file", "exec_js", "generate_image", "create_tool")`（`ChatViewModel.kt:1705-1707`），**不包含 `patch_file`**。在默认的 `"semi"` 执行模式下（`ChatViewModel.kt:633`），只有白名单内的工具才需要用户审批（`ChatViewModel.kt:1716-1718`）。

**用户体验影响**：
用户把执行模式设为"semi"（半自动），以为所有改文件的操作都会先问自己。但 AI 调用 `patch_file`（同样是修改文件内容、同样会落盘覆盖）**会绕过审批直接执行**。AI 可能删掉文件里的关键行（`delete_lines`）或替换大段内容（`replace_lines`），用户毫不知情。这是安全预期与实际行为的严重不一致——用户以为半自动模式下文件修改是受控的，实际上 `patch_file` 是个"后门"。

---

### F-C5. 路径穿越（path traversal）零防护——`materializedPath` 可被注入 `../`

**文件**：`WorkspaceRepository.kt:50-52`、`77-78`、`102-105`；`FileOperationRepository.kt:41`、`68`、`100`、`160`

**问题描述**：
所有文件定位都是 `File(physicalRootPath, materializedPath)`，而 `materializedPath` 在创建后**从不校验是否包含 `../` 或绝对路径**。`createFile`/`createDirectory` 接收调用方传入的 `materializedPath` 原样拼路径（`WorkspaceRepository.kt:50`：`File(physicalRootPath, materializedPath)`）。虽然目前 chat 侧没有 `create_file` 工具（见 F-C1），但：
- RAG 导入时 `materializedPath = "/${fileName}"`（`RagViewModel.kt:539`），`fileName` 来自 SAF 的文件名解析，若文件名含 `../../etc/passwd` 之类，会逃逸。
- 一旦未来补上 `create_file` 工具，`materializedPath` 直接来自 AI 的 JSON 参数，AI（或被注入的 prompt）可构造 `materializedPath = "/../../../databases/nexara.db"` 来读写应用私有数据库，甚至覆盖其他会话的文件。

没有任何一处使用 `canonicalPath` / `normalize` / `startsWith(root)` 做边界校验（全仓库 grep 无命中）。

**用户体验影响**：
当前因无 `create_file` 工具，风险被"意外封印"。但这是一个**潜伏的安全洞**：一旦补齐创建文件的能力（这是 F-C1 要求的），路径穿越会立刻变成可利用的漏洞，AI 可能被诱导写出工作区外的文件，破坏应用数据库或窃取其他会话数据。

---

## 中等问题（Medium）

### F-M1. 并发读写无锁——AI 写文件时用户/另一会话读到半截内容

**文件**：`FileOperationRepository.kt:23-59`、`61-92`

**问题描述**：
`writeFileAtomic` 和 `readFileRange` 都只用 `withContext(Dispatchers.IO)`，没有任何文件锁或读写协调。`FileEntry` 里虽然定义了 `lockedBySessionId`、`lockExpiresAt` 字段（`FileEntry.kt:60-62`），但**这两个字段从未被任何代码读写**（全仓库 grep 仅命中 entity 定义和建表语句）。`patch_file` 的错误提示里还提到 `LOCKED_BY_OTHER`（`FilePatchSkill.kt:135`），但这个错误码永远不会被触发，因为根本没有加锁逻辑。

**用户体验影响**：
用户开了两个会话（或一个会话里 AI 连续调用多个工具），AI 正在 `write_file` 写一个 5000 行的文件（写了一半），此时另一个 `read_file` 或 UI 刷新读取同一文件，会读到不完整内容（写到第 2000 行就截断）。乐观锁（hash 校验）只能防止"基于过期版本的并发写"，**无法防止"写过程中读"的脏读**。

---

### F-M2. `FileEntryDao` 缺少 `@Transaction`——moveToRecycleBin/updateParent 的"移动+改库"非原子

**文件**：`WorkspaceRepository.kt:95-125`（moveToRecycleBin）、`188-233`（updateParent）、`FileEntryDao.kt`（无事务注解的多步查询）

**问题描述**：
`moveToRecycleBin` 的流程是：①磁盘 rename（`srcFile.renameTo(dstFile)`，`:105`）→ ②更新本条记录（`dao.update`，`:110`）→ ③若为目录，递归移动子树（`moveSubtreeToRecycleBin`，`:123`，内部对每个子节点再 rename + update）。这三步没有包在 Room 事务里。若第 ②步后、第③步中崩溃，磁盘上文件已移走，但部分子节点的 DB 记录还指向旧路径——`physicalRootPath + materializedPath` 定位的文件不存在，导致文件"失踪"。

`updateParent`（移动文件到新目录，`:188-233`）有同样问题：rename 本体后递归改子树 materializedPath，中途失败会留下半迁移的混乱状态。

**用户体验影响**：
用户把一个含 20 个文件的文件夹移到回收站，过程中 app 闪退。重启后发现：文件夹"消失"了（不在原位），但回收站里只有部分文件，另一半文件的 DB 记录指向已不存在的磁盘路径——既无法恢复也无法删除（`permanentDelete` 里 `physicalFile.exists()` 为 false 时只删 DB 不删盘，但 UI 还显示着幽灵条目）。

---

### F-M3. `renameTo` 的返回值被全程忽略——移动/回收失败时静默继续

**文件**：`WorkspaceRepository.kt:105`、`137`、`202`、`223`、`282`、`308`

**问题描述**：
`srcFile.renameTo(dstFile)` 的返回值（Boolean，表示移动是否成功）在所有调用点都被丢弃。`renameTo` 在跨挂载点、目标已存在、权限不足时会返回 false。代码无视结果，紧接着就去更新数据库，于是出现"DB 说文件在新路径，磁盘上文件还在旧路径（或已丢）"的不一致。

**用户体验影响**：
用户删除一个文件，UI 显示已进入回收站（DB 更新了），但磁盘上文件可能没移动成功（`renameTo` 失败）。用户从回收站"恢复"时，又执行反向 rename——若此时原文件还在原位，恢复的 rename 可能覆盖或失败，文件状态彻底混乱。整个过程没有任何错误提示。

---

### F-M4. 乐观锁冲突的"冲突"语义脆弱——check-then-write 之间存在 TOCTOU 竞态

**文件**：`FileOperationRepository.kt:29-52`

**问题描述**：
`writeFileAtomic` 的乐观锁是"读 hash → 比较 → 写"。`dao.getByUuid`（读，`:29`）和 `dao.update`（写，`:45`）之间没有行锁。两个会话同时基于同一 expectedHash 调用：都读到相同 hash、都通过校验、都写盘、都更新 DB——后写的覆盖先写的，**先写的内容丢失且无任何报错**。乐观锁名不副实。

**用户体验影响**：
两个 AI 会话同时改同一文件，用户以为有冲突检测会保护自己，实际上最后一个写入者静默获胜，前一个的修改凭空消失，且没有任何冲突提示。

---

### F-M5. 会话的 `activeTask` 快照从不持久化——重启后任务面板标题丢失，仅靠 task_nodes 表兜底

**文件**：`SessionDao.kt:65-66`（`updateActiveTask` 定义存在）、`SessionEntity.kt:42`（`activeTask` 列）、全仓库 `updateActiveTask` 无调用方

**问题描述**：
`SessionDao.updateActiveTask` 这个 DAO 方法**从未被任何代码调用**（grep 无调用点）。`Session.activeTask`（`ChatModels.kt:359`）这个内存字段在 app 运行期间可能被更新，但从不落盘到 `sessions.active_task` 列。`TaskFloatingPanel` 的 `goalTitle` 取自 `uiState.session?.activeTask?.title`（`ChatScreen.kt:451`）。

任务节点本身**是持久化的**（`task_nodes` 表有 `ForeignKey` 到 `sessions`，`TaskNodeEntity.kt:16-23`，`onDelete = CASCADE`），所以 app 重启后任务树能从 DB 重建（`TaskRepository.observeActiveTree` 走 DAO）。但 `activeTask` 这个快照（含 title/currentFocusStepId）不持久化。

**用户体验影响**：
任务树本身在重启后还在（✅），但 `TaskFloatingPanel` 的顶部标题（goalTitle）在会话恢复流程未重建 `activeTask` 时会显示为空，需要等 `getPlan` 重读后才有——存在短暂的"标题丢失"体验。影响较轻，但反映出 `active_task` 列是死代码。

---

### F-M6. `FileSearchSkill` 全量递归遍历——大工作区下极慢，且只搜文件名

**文件**：`FileSearchSkill.kt:18-65`

**问题描述**：
`search_files` 的实现是：取所有 roots → 对每个 root 递归 `observeChildren` → 逐个匹配文件名（`:45-65`）。每层目录都是一次独立的 DB 查询（`repo.observeChildren(parentUuid).firstOrNull()`，`:53`），N 层目录就是 N 次查询 + N 次 Flow 订阅。文件名用 `contains` 模糊匹配（`:57`），`fts` 模式在 skill 描述里声称支持（`FileSearchSkill.kt:15`："全文 FTS5 搜索"），但实现里 `mode` 只处理了 `"name"`，`"fts"` 分支根本没写（`mode != "name"` 时啥也不匹配）。

**用户体验影响**：
用户让 AI "搜索工作区里提到'架构'的文件"，AI 调用 `search_files(query="架构")`。如果 AI 用默认 name 模式，只能匹配文件名；如果 AI 聪明地传 `mode="fts"`（schema 里写了支持），结果**直接返回空**（因为没有 fts 实现）。用户会以为"工作区里确实没有相关文件"，实际上是搜索功能没实现。这是"文档/Schema 撒谎"的典型——告诉 AI 有能力，实际没有。

---

### F-M7. `patch_file` 行号校验用 `totalLines`（patch 前的行数）却声称反映当前行数

**文件**：`FileOperationRepository.kt:163`、`172`、`194`、`214`

**问题描述**：
`patchFile` 里 `val totalLines = lines.size`（`:163`）是读磁盘后的初始行数。但后续 `replace_lines`/`insert_after`/`delete_lines` 会改变行数（用 `lineOffset` 跟踪，`:165`）。而错误提示里 `"超出文件行数 (当前共 $totalLines 行)"`（`:176`、`:198`、`:218`）一直用这个**不变的初始 totalLines**，且校验条件 `start > totalLines + lineOffset`（`:172`）混用了初始值和偏移量，逻辑混乱。对于多操作序列，后续操作的行号边界判断会基于错误的基准。

**用户体验影响**：
AI 发出含多个 patch 操作的序列，前几个操作改变了行数，后面的操作行号校验给出错误的"总行数"提示，AI 据此重新规划时被误导，陷入"越改越乱"的循环。

---

## 轻微问题（Minor）

### F-L1. `WorkspaceViewModel`（旧的文件系统扫描器）是死代码，与真实工作区数据源脱节

**文件**：`WorkspaceViewModel.kt`（整个文件）

**问题描述**：
`WorkspaceViewModel` 用 `File(workspacePath).listFiles()` 直接扫描磁盘（`:42-48`、`:71-81`），绕过数据库元数据。但全仓库 grep 显示**没有任何 Composable 使用它**（`WorkspaceViewModel` 在 `ui/` 下零引用，仅自身定义）。真实的工作区 UI（`FilesPanel`、`ResourceExplorerSheet`）走的是 `workspaceRepo.observeRoots()`。

**用户体验影响**：
无直接影响（死代码）。但它反映了"工作区 UI 经历过一次重构"——从直接扫磁盘改为走 DB。残留的死代码会误导后续维护者，且 `scanDirectory`（`:71-81`）这种递归扫盘逻辑若被误用，会暴露磁盘真实结构（绕过 DB 的安全边界）。

---

### F-L2. 工作区物理根目录硬编码为 `filesDir/workspaces/$sessionId`

**文件**：`ChatViewModel.kt:906`

**问题描述**：
`File(filesDir, "workspaces/$sessionId")` 路径硬编码，sessionId 直接拼进路径。sessionId 由 `IdGenerator.session()` 生成，若含特殊字符（虽然概率低）会创建异常路径。无配置项让用户选择工作区存储位置（如外置 SD 卡）。

**用户体验影响**：
用户无法把工作区放到外置存储；卸载 app 后所有工作区文件随 `filesDir` 一起被清除（除非用户做过备份）。对于"类 IDE"定位，工作区不能导出/迁移是体验短板。

---

### F-L3. `read_file` 把整个文件 `readLines()` 读入内存——大文件 OOM

**文件**：`FileOperationRepository.kt:69`

**问题描述**：
`readFileRange` 即使只读某几行，也先 `physicalFile.readLines()` 把**整个文件**读成 List（`:69`），再切片返回。没有文件大小上限校验。

**用户体验影响**：
用户导入一个 50MB 的日志文件到工作区，AI 调用 `read_file(uuid, startLine=1, endLine=10)` 只想看前 10 行，但 app 会把 50MB 全读进内存，在低端设备上可能 OOM 崩溃。

---

### F-L4. `WorkspaceSeqDao.getNextSeqForDate` 用 `RawQuery` 拼 INSERT...ON CONFLICT——虽参数化但仍偏脆弱

**文件**：`WorkspaceSeqDao.kt:19-27`

**问题描述**：
用 `SimpleSQLiteQuery` 手写 `INSERT ... ON CONFLICT(date_key) DO UPDATE SET last_seq = last_seq + 1`，参数 `dateKey` 虽然用 `arrayOf(dateKey)` 占位符传入（安全），但这种 raw SQL 绕过了 Room 的编译期校验。且紧接着又用 `getSeq` 再查一次（`:26`），两次查询之间没有事务，高并发下两个调用可能拿到相同 seq。

**用户体验影响**：
影响轻微（仅用于文件命名序号），但极端并发下可能产生重复序号，导致文件名碰撞。

---

### F-L5. 任务状态字符串前后端不一致——实体默认 `"pending"`，但 skill/repository 用 `"todo"`/`"doing"`/`"done"`

**文件**：`TaskNodeEntity.kt:37`（默认 `"pending"`）、`TaskRepository.kt`（全程用 `todo`/`doing`/`done`/`dropped`）、`ContextBuilder.kt:441-446`（渲染时判断 `completed`/`in_progress`/`failed`）

**问题描述**：
存在三套状态词汇：
- DB 实体默认值：`"pending"`（`TaskNodeEntity.kt:37`）
- Repository 实际写入：`"todo"`、`"doing"`、`"done"`、`"dropped"`（`TaskRepository.kt:52`、`98`、`111` 等）
- ContextBuilder 渲染判断：`"completed"`、`"in_progress"`、`"failed"`（`ContextBuilder.kt:442-445`）——这组值与 repository 写入的**完全不匹配**，导致 `renderTaskTree` 里所有步骤都掉进 `else` 分支显示 `○`（空心圆）。

**用户体验影响**：
`TaskFloatingPanel` 的图标逻辑（`TaskFloatingPanel.kt:184-205`）用的是 `done`/`doing`/`dropped`（正确匹配 repository），所以**浮动面板显示正常**。但 ContextBuilder 注入给 AI 的任务树文本（`renderTaskTree`，`ContextBuilder.kt:438-456`）因为状态值不匹配，AI 看到的任务树里**所有步骤都显示为未开始（○）**，AI 会误以为没有进展，反复要求"重新开始"或重复执行已完成步骤。

---

### F-L6. 任务步骤的 `note`/`description` 无长度限制——AI 可写入超长内容撑爆 prompt

**文件**：`TaskRepository.kt:144-151`（set_note 直接存入）、`TaskNodeEntity.kt:36`（description 无长度约束）

**问题描述**：
`update_plan` 的 `set_note` 把任意长度 note 原样存库（`TaskRepository.kt:150`），`description` 同理。ContextBuilder 注入时虽有 `.take(120)`（`:451`）和 `.take(80)`（`:428`）截断，但 `appendFullTaskContext` 里 title、note 全量拼进 system prompt（`ContextBuilder.kt:448-451`），大量步骤时显著消耗 token。

**用户体验影响**：
AI 给每个步骤写很长的心得笔记，几十个步骤累积下来，system prompt 膨胀，挤占用户实际对话的 token 预算，响应变慢、成本升高。

---

## 设计观察（非缺陷，但值得注意）

### O-1. 工作区与 RAG 共用同一套 `FileEntry`/`FileEntryDao`

聊天工作区（`workspaces/$sessionId`）和 RAG 知识库（`rag_workspace`）都写入同一张 `workspace_files` 表，靠 `physicalRootPath` 区分。好处是统一的回收站、向量化、知识图谱提取；风险是一方的 bug 会污染另一方（例如 F-C5 路径穿越若被利用，可跨工作区破坏）。

### O-2. 任务树是真正的树结构（parentId 自引用 + 物化排序）

`task_nodes` 表用 `parentId` + `sortOrder` 表达层级（`TaskNodeEntity.kt:31-33`），`TaskRepository.buildTree`（`:245-281`）递归重建。父节点状态由子节点派生（`deriveParentStatus`，`:185-195`），禁止直接设置父节点状态（`ParentStatusDerivedException`，`:87-92`）——设计合理。但 `collectDescendants`（`:293-305`）用 `allNodes.filter { it.parentId == current }` 在循环里反复全表过滤，任务节点多时是 O(n²)。

### O-3. 任务的可视化做得较好（相对工作区而言）

`TaskFloatingPanel`（`TaskFloatingPanel.kt`）实时展示进度条（`LinearProgressIndicator`，`:129-138`）、步骤树（`:149-160`）、状态图标（✅/⟳/✕/○，`:184-205`）、折叠展开（`:76`、`:121-125`）。用户能直观看到 AI 的任务分解与进度——这是该模块的亮点。相比之下，工作区文件树（`FilesPanel`）的查看需要点开"资源管理器"侧边面板（`ResourceExplorerSheet`），不在主对话流中，可见性较差。

### O-4. 回收站有 30 天自动清理（`RecycleBinCleanupWorker`，`cleanup` 用 `30.days`）

这是为数不多的"防积累"设计，但它遍历所有 roots 再遍历各自的回收站（`RecycleBinCleanupWorker.kt:14-26`），且 `cleanup` 的触发时机/调度方未在审计范围内确认（需查 WorkManager 注册）。

---

## 优先级修复建议（按用户影响排序）

| 优先级 | 问题 | 建议修复 |
|---|---|---|
| P0 | F-C1 AI 无法创建新文件 | 新增 `create_file` Skill，调用 `WorkspaceRepository.createFile` |
| P0 | F-C2 写文件非原子 | 改为"写临时文件 → renameTo"模式，先成功再更新 DB |
| P0 | F-C3 无版本历史 | 增加 `file_versions` 表，每次 write/patch 前存快照（至少保留最近 N 版） |
| P1 | F-C4 patch_file 绕过审批 | 把 `patch_file` 加入 `highRiskToolNames`（`ChatViewModel.kt:1705`） |
| P1 | F-C5 路径穿越 | 所有 `File(root, path)` 前校验 `path.canonicalPath.startsWith(root.canonicalPath)` |
| P2 | F-M1 并发脏读 | 实现 `lockedBySessionId`/`lockExpiresAt` 的真实加锁逻辑（字段已存在） |
| P2 | F-M2/M3 移动非原子+忽略 renameTo 返回值 | 用 `@Transaction` 包裹，校验 renameTo 返回值 |
| P2 | F-M6 search fts 未实现 | 实现 FTS5 全文搜索，或从 schema 里删掉 `fts` mode 避免误导 AI |
| P3 | F-L5 状态字符串不一致 | 统一为一套状态枚举；修复 `ContextBuilder.renderTaskTree` 的状态判断 |

---

## 审计边界说明

- 本报告未覆盖：RAG 向量化/知识图谱抽取的内部逻辑（仅审计了其与工作区的交互边界）、WorkManager 的调度配置、网络层工具调用协议的序列化细节。
- `activeTask` 持久化（F-M5）的结论基于"全仓库 grep `updateActiveTask` 无调用方"，若存在反射/动态调用则结论需复核（概率极低）。
- 所有文件行号基于审计时的代码版本，后续提交可能偏移。
