# 记忆生命周期与范围隔离、命令实时输出、轮次折叠与子代理实施计划（2026-10-21）

源码路径相对 `native-ui/app/src/main/java/com/promenar/nexara/`。

## 目标

1. 删除、丢弃、回退或清空聊天记录时，对应的对话记忆与知识图谱证据同步失效；撤销删除时随消息恢复。
2. 对话记忆、知识图谱、文档三类检索支持会话级与助手级范围控制，避免不同助手之间的记忆污染与跨工作区数据暴露。
3. 知识图谱可从会话中抽取并更新；图谱对话证据的共享范围可选“关闭 / 跨会话 / 跨助手”。
4. Agent 执行命令时，聊天界面实时显示命令输出。
5. 一轮完整结束后，中间步骤收成一条摘要，单独呈现最终正文；复制按钮只复制最终正文。
6. 子代理：主 Agent 派生子代理在独立上下文中完成子任务并返回结果；用户可只读查看子代理过程，子代理只与主 Agent 通信。
7. RAG 与知识图谱的检索、写入、抽取在界面上可感知，不做黑盒。

## 非目标

- 图谱事实冲突消解与时间版本（同等条件下检索优先较新证据即可）。
- 读取 `kgEntityTypes`、`kgDomainHint`、`kgFreeMode`、`kgDomainAuto`、`jitMaxChunks` 等目前未接线的抽取字段。
- 子代理嵌套（深度固定为 1）、用户与子代理直接对话、子代理作为独立会话出现在会话列表。
- Termux 实时输出（Termux 结果由广播一次性回传，无公开增量接口）。
- 在会话内显示费用；模型调用费用只在用量专区呈现。

## 已验证事实（2026-10-21，基于当前工作树）

### 记忆写入与删除

- 每轮生成结束后 `MemoryManager.addTurnToMemory` 把“User/Assistant”一轮文本切块嵌入（`data/generation/DefaultChatGenerationRuntime.kt:900`，`data/rag/MemoryManager.kt:368-394`）。粒度是一轮一组块。
- 记忆写入 `vectors` 表，`metadata.type="memory"`，`start_message_id`/`end_message_id` 为该轮用户消息与助手消息 id；两列无外键、无索引；唯一外键是 `session_id → sessions`（CASCADE）（`data/local/db/entity/VectorEntity.kt`）。文档向量同表，`session_id` 为空、`type="document"`。
- 只有删除整个会话会清理向量、`context_summaries`、`kg_evidence` 与孤立节点边（`data/session/RoomSessionDeletionTransaction.kt:14-55`）。删除单条消息、重新生成丢弃旧回复、编辑、回退截断、清空会话都只改消息（`ui/chat/manager/MessageManager.kt:300,775,831,862,956`）。
- 删除消息可撤销，撤销以原 id 重新插入消息（`MessageManager.restoreMessages`）。
- `VectorStore.clearSessionMemory`、`pruneOrphanSessions`、`VectorDao.deleteMemoryRange` 没有调用方；`deleteMemoryRange` 以 UUID 字符串做范围比较，语义不成立。
- 每条消息的 `vectorizationStatus` 字段只由未接线的 `PostProcessor.archiveMessagesToRag` 写入，界面不读取。
- 当前数据库版本 22（`data/local/db/NexaraDatabase.kt:100`）。

### 知识图谱

- 节点按“规范化名称 + 类型”、边按“源、目标、规范化关系”全局去重；作用域全部记在 `kg_evidence`（`source_type`、`doc_id`、`session_id`、`content_hash`、`task_id`），无证据后回收节点与边（`data/local/db/dao/KgEvidenceDao.kt:71-96`）。
- `KgScope` 带 `agentId`、`messageId`，但 `GraphStore.source()` 丢弃这两个字段（`data/rag/GraphStore.kt:214-233`）。
- 整篇文档抽取唯一入口是 `DocumentIndexService`；对话没有抽取管线。检索时临时抽取（JIT）会把当前会话记忆片段写成 `jit` 证据（`data/rag/PersistentGraphKgProvider.kt:28-38`）。
- 对话证据只在本会话可见（`data/rag/GraphRetrievalStore.kt:133-136`）；`isGlobal` 对图谱只额外放开 `__system__` 助手工作区的文档（`:116-131`）。

### 检索范围

- 新会话默认 `isGlobal = true`（`ui/chat/ChatViewModel.kt:2399`）。开启后对话记忆检索不加会话过滤，覆盖所有助手所有会话（`data/rag/MemoryManager.kt:134`）；文档检索不限文档，覆盖所有工作区全部已索引文档（`:65-71,178`）；关键词检索同样整体放开（`:209-211`）。
- 助手级检索配置只有 `enableMemory`/`enableDocs`/`enableKnowledgeGraph` 开关，没有范围（`data/agent/AgentConfigModels.kt:25-45`）。
- 会话所属助手 `Session.agentId` 创建后不变；`sessions` 表没有 `agent_id` 索引。

### 命令执行与工具结果

- 工具接口是一次性的 `suspend fun execute(args, context): ToolResult`，`SkillExecutionContext` 只有会话、助手、工作区与审批目标字段（`ui/chat/manager/registry/SkillRegistry.kt:18-51`）。
- 内置执行由守护线程按块读入尾部缓冲，进程结束后取结果（`data/exec/BuiltinCommandRunner.kt:93-129`）；SSH `ssh_exec` 结束后脱敏截断，`execStreaming` 是真流式但只给 git 使用（`data/ssh/SshRuntime.kt:158-256`）；Python 沙箱 `out` 帧与 QuickJS `console.log` 只做累积（`data/script/PythonSandboxEngine.kt:73-83`，`data/script/QuickJsScriptEngine.kt`）。
- 工具执行前追加 `ExecutionStep(type="tool_call")`，结束后追加结果；`appendStep` 可按 `(toolCallId, type)` 原位替换（`ui/chat/manager/ToolExecutor.kt:253-261,553-566`）。`GenerationPresentationStore` 不处理工具进度。
- 停止生成会取消生成任务；内置执行对进程组 SIGTERM，2 秒后 SIGKILL（`data/exec/BuiltinCommandRunner.kt:104-106,166-180`）。

### 渲染与生成并发

- 一轮在库中是交错的多条消息：助手 → TOOL → 助手 → … → 最后一条助手消息；最后一步以 `END_TURN` 结束且 `toolCalls` 为空（`data/generation/ChatGenerationRunner.kt:414`）。
- `buildPipelineGroups` 把一轮合成一组，`buildPipelineSteps` 把每条助手消息拆成思考、工具、正文三种块并按原顺序平铺；思考与工具组默认收起；完成后不重排；复制按钮拼接组内所有正文（`ui/chat/PipelineBubble.kt:95-285`）。
- 检索指示器已存在：`RagProgressCard` 以阶段进度显示检索中、命中条数，点开 `RagDetailsSheet` 显示引用、图谱路径与检索轨迹（`ui/chat/ChatInlineComponents.kt:276-340`）；阶段含 `kg`（`DefaultChatGenerationRuntime.kt:1172-1179`）。文档索引与文档图谱抽取的进度在知识库页面由 `IndexingNotice` 呈现。
- `DefaultGenerationCoordinator` 全局只有一个运行槽位，第二个生成请求返回 `Busy`（`data/generation/DefaultGenerationCoordinator.kt:77-89`）；每次请求新建运行时实例（`DefaultChatGenerationRunnerFactory.kt:55-60`）。
- 审批模型是“需要审批就结束本次运行，批准后恢复”（`DefaultChatGenerationRuntime.kt:722-801`，`ApprovalManager.kt:80-135`）；白名单在 `data/repository/ToolAllowlistStore.kt`。

## 设计决策

### D1 记忆生命周期：以消息存在为可见条件，物理删除延后

- 向量读取的公共可见条件（`VectorDao` 的 `ACTIVE_VECTOR`）要求对话记忆所属的用户消息与助手消息都存在，向量检索、关键词检索、按 id 读取与统计统一生效。删除、丢弃、回退、清空立即生效；撤销以原 id 恢复消息，记忆随之恢复，无需重新嵌入。不需要表结构变更。
- 聊天记录删除时以 `stale = 1` 与 `updated_at` 标记失去所属消息的记忆，作为撤销窗口起点；恢复消息时撤销标记。应用启动时补标并物理删除超过 24 小时窗口的孤儿记忆。
- 即时抽取把同一任务的整张图归给任务内每个来源，因此删除或编辑聊天记录时，凡用过本会话对话记忆的即时抽取任务，其全部即时证据随之删除并回收孤立节点与边，同时清空即时抽取缓存；即时证据可在下次检索时重新抽取。
- 编辑用户消息时删除该轮记忆，由随后的重新生成写入新记忆；编辑助手正文时按新正文重写该轮记忆，重写失败也删除旧正文的记忆。
- 记忆维护失败不影响聊天记录本身的变更。
- 会话图谱抽取（D3）写入的对话证据带 `message_id`，可见条件同样要求该消息存在。

### D2 记忆范围

| 资源 | 档位 |
|---|---|
| 对话记忆 | 仅本会话 / 本助手 / 全部助手 |
| 知识图谱对话证据 | 关闭（仅本会话） / 跨会话（本助手） / 跨助手（全部助手） |
| 文档 | 本会话工作区与所选文档 / 加全局知识库 / 全部已索引文档 |

- 助手级设置每类资源的默认档位与上限；会话只能在上限内选择，超出上限的档位显示为锁定并注明原因。
- 助手级“允许其他助手读取本助手的记忆”，默认开启；关闭即私密助手。“全部助手”档只读取开放共享的助手。
- 不迁移已有记忆数据：按 `sessions.agent_id` 关联过滤，新增 `sessions(agent_id)` 索引。
- 会话检索选项以 `memoryScope`、`graphScope`、`documentScope` 三个枚举替换 `isGlobal`，读取旧数据时兼容。已有会话统一迁移为“本助手”（对话记忆与图谱）和“本会话工作区与所选文档 + 全局知识库”（文档）；新会话取助手默认值。
- 向量检索、关键词检索、图谱可见性三处 SQL 使用同一个范围过滤器。
- 子代理继承父会话的读取范围，不写入记忆。

### D3 会话图谱抽取

- 全局开关“从对话抽取知识图谱”，默认关闭；开启时提示每轮会额外调用一次模型。
- 每轮写完记忆后，向 `vectorization_tasks` 队列加入会话图谱任务，后台执行；输入为本轮用户消息与最终正文，工具结果只取摘要；过短的轮次跳过。
- 模型选择沿用 `kgExtractionModel` → 快速模型 → 主模型，预算沿用 `KgExtractionBudget`，按本轮内容哈希做 checkpoint。
- 写入 `source_type='session'`，带 `session_id` 与 `message_id`，由 D1 统一处理删除与撤销，由 D2 决定可见范围。
- 图谱抽取的模型调用计入用量专区。

### D4 命令实时输出

- `SkillExecutionContext` 新增 `emitOutput(stream, chunk)`，默认空实现。内置执行在尾部缓冲上挂监听；`ssh_exec` 改走流式执行并在显示前经过 `SshOutputFilter`；Python 沙箱转发 `out` 帧；QuickJS 转发 `console.log`。Termux 显示“不支持实时输出”。
- 实时输出只存内存展示存储，按 `toolCallId` 保留尾部 16KB，界面节流 100ms，不写数据库；交给模型的最终结果不变。
- `InlineToolRow` 运行中展开等宽终端区，显示最后约 12 行并自动滚动到底；完成后收起，恢复现有结果展示。
- 单个命令的停止按钮：只结束该命令，并把“用户已停止”作为结果交回模型。

### D5 轮次折叠

- 完成态条件：非生成中、组内有工具或思考步骤、最后一条助手消息正文非空、没有待审批、没有出错。满足时渲染一条摘要（步数、文件改动数、用时）加最终正文；点开摘要显示完整时间线，中间正文收在其中。
- 生成中保持实时平铺；出错或待审批的轮不收起。
- 复制按钮只复制最终正文；长按菜单保留“复制全部过程”。
- 显示设置“完成后折叠过程”，默认开启；展开状态用 `rememberSaveable`，不写库。

### D6 子代理

- 内置工具 `agent_task`：参数 `description`、`prompt`，可选预置子代理与工具档位。预置只读子代理“探索者”。
- 在父工具协程内直接创建子运行时，不经过生成协调器；父轮取消时子任务一并取消。子代理数据挂在父会话该次工具调用下，不出现在会话列表，父会话删除时级联删除。
- 子代理工具集不含 `agent_task`；同一步内多个子任务可并行，上限 3 个；有最大轮数与 token 上限；用量计入父会话。
- 子代理的最终正文作为工具结果交回父 Agent，超长截断。
- 审批：子代理继承父会话执行模式与白名单；全自动模式下不出现审批。需要新授权时，主会话出现标注“来自子代理 · 名称”的审批卡，子代理原地挂起；“允许并记住”写入父会话白名单；拒绝只拒绝该次调用。应用在后台时发通知，10 分钟内未处理即以失败交回父 Agent。该机制为运行中等待审批，父会话自身的审批流程不变。
- 界面：父轮内显示“N 个子代理并行”分组卡，每个子代理一行（名称、任务、当前步骤、用时），运行中显示实时输出，完成后显示摘要并随轮次一起折叠。点按进入只读查看页：主 Agent 交给它的任务（含工具档位、模型、步数上限）、过程时间线、交回主 Agent 的结果；无输入框，底部标注只读与“子代理只与主 Agent 通信”。

### D7 记忆与图谱指示器

- 读取侧（扩展现有 `RagProgressCard` 与 `RagDetailsSheet`）：摘要行显示本次检索范围档位；摘要下一行按来源计数（记忆、文档、图谱路径）并标出 JIT 临时抽取次数；详情页列出范围、参与检索的会话数与助手数、JIT 抽取使用的模型。
- 写入侧：回复底部模型名所在行新增记忆状态，依次为“写入中 → 已记入记忆 → 图谱抽取中 → 图谱 +N 实体 +M 关系”，失败显示“图谱抽取失败 · 重试”，被删除或移除后显示“已从记忆移除”。记忆关闭的会话不显示。
- 点按写入状态打开“本轮记忆”面板：可见范围说明、本轮写入的记忆块预览、本轮抽取的实体与关系、“在图谱中查看”、“从记忆中移除本轮”（只移除记忆，不删消息）。
- 写入状态以每轮一个 JSON 字段持久化在助手消息上，后台图谱任务完成后更新；新增列随 P6 的 v23 迁移一并加入，P4 先使用助手消息已有的 JSON 列。
- 删除消息的撤销提示写明“已删除 N 条消息及其记忆”。
- 会话设置的范围档位旁显示当前可见范围（如“本助手 12 个会话”）；会话设置的知识图谱分组显示后台抽取队列（待处理数、今日调用次数）。
- 子代理查看页同样显示子代理的检索卡；子代理不写入，不显示写入状态。

## 分期与关键路径

| 期 | 内容 | 依赖 | 主要写入范围 |
|---|---|---|---|
| P1 | D1 记忆生命周期（无表结构变更）：向量可见条件、撤销窗口标记与启动清理、即时证据失效、编辑重写 | 无 | `data/local/db/dao/VectorDao.kt`、`KgEvidenceDao.kt`、`data/rag/ConversationMemoryLifecycle.kt`、`ui/chat/manager/MessageManager.kt` |
| P2 | D2 记忆范围与 D3 图谱三档的检索部分 | P1 | `data/rag/**`、检索选项模型、`ui/chat/SessionSettingsSheet.kt`、助手编辑页 |
| P3 | D5 轮次折叠与只复制正文 | 无，可与 P1、P2 并行 | `ui/chat/PipelineBubble.kt` 及新组件、显示设置 |
| P4 | D7 指示器（读取侧扩展、写入状态、本轮记忆面板、撤销提示文案） | P1；写入侧图谱状态在 P6 接入 | `ui/chat/**`、`data/generation/DefaultChatGenerationRuntime.kt` 写入状态 |
| P5 | D4 命令实时输出 | 无 | `data/exec/**`、`data/ssh/**`、`data/script/**`、`ui/chat/manager/ToolExecutor.kt`、展示存储、`InlineToolRow` |
| P6 | D3 会话图谱抽取与后台队列，含 v23 迁移（`kg_evidence.message_id` 及索引、助手消息记忆状态列、`sessions(agent_id)` 索引） | P1、P2 | `data/local/db/**`、`data/rag/**`、`VectorizationQueue`、RAG 设置页 |
| P7 | D6 子代理，含运行中审批与只读查看页 | P3、P5 | 新 `data/subagent/**`、`ApprovalManager`、`ToolAllowlistStore`、`ui/chat/**`、`NexaraApplication` 注册 |

每期独立提交推送；涉及 `DefaultChatGenerationRuntime`、`ApprovalManager`、`ChatViewModel`、`SessionSettingsSheet`、`NexaraApplication` 的期次，动工前确认没有其它会话在改同一文件。

## 验收

- P1：删除一轮后向量、关键词、按 id 读取都检索不到该轮记忆，撤销后恢复，超过窗口后行被物理删除；文档向量不受影响；用过本会话记忆的即时证据整体失效且文档证据保留；编辑用户消息删除该轮记忆，编辑助手正文按新正文重写；删除、丢弃、截断、清空四条路径都触发同步，删除失败不触发，同步失败不影响删除；去掉可见条件时用例失败。
- P6：迁移测试覆盖 v22 → v23。
- P2：三档范围在向量、关键词、图谱三条路径结果一致；私密助手不被“全部助手”读取；会话档位不能超过助手上限；旧 `isGlobal` 数据迁移为“本助手”。
- P3：截图预览覆盖浅色、深色、1.3 倍字号，以及无工具、出错、待审批三种边界；复制只得最终正文。
- P4：截图覆盖写入状态各形态与本轮记忆面板；“从记忆中移除本轮”后检索不可见且消息保留。
- P5：内置命令、SSH、Python 沙箱、QuickJS 在运行中可见增量输出；交给模型的结果与改动前一致；单命令停止返回“用户已停止”；SSH 实时输出经过脱敏。
- P6：开关关闭时不产生抽取调用；开启后每轮产生 `session` 证据并带消息 id；预算耗尽时停止并在队列状态显示。
- P7：子代理在父轮取消时停止；子代理工具集不含 `agent_task`；全自动模式无审批；需要授权时主会话出现标注来源的审批卡，后台 10 分钟未处理即失败交回；只读查看页无输入入口；父会话删除时子代理数据一并删除。
- 每期执行仓库标准验证：`native-ui/` 下串行运行 `:app:testSideloadDebugUnitTest`、`:app:lintSideloadDebug`、`:app:assembleSideloadDebug`、`:app:assemblePlayDebug`、`:app:validateSideloadDebugScreenshotTest`，仓库根运行 `python3 scripts/ci/check-distribution-manifests.py`；涉及界面的期次在真机验收。

## 风险

- 检索 SQL 增加消息存在性条件（按消息主键查找）与助手关联条件，大会话下需确认查询计划。
- 迁移为“本助手”会让依赖跨助手记忆的会话检索结果变少；会话设置可改回。
- 运行中等待审批引入新的挂起点，需要覆盖父轮取消、应用进入后台、进程被系统回收后的恢复与清理。
- 实时输出在高吞吐命令下的界面开销，依赖节流与尾部截断。
- 备份载荷需要包含新增列与检索选项字段，恢复旧备份时按默认值读取。
