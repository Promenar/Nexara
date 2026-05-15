# 任务规划与追踪工具 (Task Planner) — 完整架构设计

> **版本**: v3.4（跨模型审查通过 + 父节点误操作边界处理）
> **日期**: 2026-05-15
> **状态**: ✅ 终稿 — 可进入实施
> **审查结论**: 4 工具全部兼容 OpenAI/Anthropic/VertexAI 三协议，批处理模式已覆盖 Anthropic 单次 tool_use 限制
> **关联**: 统一资源 OS 设计规范 §3（工具链协议模式）、§12（UI 组件复用）

---

## 1. 核心原则

### 1.1 会话唯一性
- 每个 Session 同一时刻**强制只能有一个**活跃任务树。
- `initialize_plan` 检测到已有活跃任务时返回冲突提示（含已有任务摘要），由 LLM 自行决策或询问用户。

### 1.2 状态持久化与断点续行
- 任务树实时持久化至 Room 数据库（`task_nodes` 表）。
- 会话重启后，ContextBuilder 从 DB 恢复任务状态并注入 System Prompt。

### 1.3 生命周期
- 状态为 `DONE` 或 `DROPPED` 后，任务面板从 UI 隐藏，数据保留用于审计。

### 1.4 与统一资源 OS 的集成
- 任务节点可关联产物文件 UUID（`artifact_file_uuids`），利用 workspace_files 锚定体系。

---

## 2. 与现有代码的兼容策略

### 2.1 已有模型

项目中已存在以下代码，**必须兼容**：

```kotlin
// ChatModels.kt — 已存在，需扩展
@Serializable
data class TaskStep(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "pending"
    // + 新增字段（见下方）
)

@Serializable
data class TaskState(
    val id: String = "",
    val title: String = "",
    val status: String = "idle",
    val progress: Int = 0,
    val steps: List<TaskStep> = emptyList()
    // + 新增字段（见下方）
)

// SessionEntity.kt — 已有
@ColumnInfo(name = "active_task")
val activeTask: String? = null,  // 序列化 JSON，保留兼容

// Session / Message — 已有
val activeTask: TaskState? = null        // Session 级
val planningTask: TaskState? = null      // Message 级（每轮快照）
```

### 2.2 扩展策略

**新增字段（不删除旧字段）**：

```kotlin
// TaskStep — 统一节点模型（叶节点与父节点同构）
// 核心原则：仅叶节点（children 为空）拥有独立可设置的 status；
//          父节点的 status 完全派生自子节点（Repository 层实时计算）。
@Serializable
data class TaskStep(
    val id: String = "",
    val parentId: String? = null,           // ← 新增
    val title: String = "",
    val description: String = "",
    val status: String = "pending",         // 叶节点: "pending"|"doing"|"done"|"dropped" ; 父节点: 派生值
    val sortOrder: Int = 0,                 // ← 新增
    val note: String? = null,               // ← 新增（set_step_status 附加说明）
    val artifactFileUuids: List<String>? = null,  // ← 新增（关联产物文件）
    val children: List<TaskStep> = emptyList(),   // ← 新增（递归子节点）
    val isCollapsed: Boolean = false,       // ← 新增（UI 折叠状态）
    val createdAt: Long = 0,                // ← 新增
    val updatedAt: Long = 0                 // ← 新增
)

// TaskState — 聚合根（根节点元数据）
// 注意：progress/progressPercent/status 均为派生值，不在 DB 独立存储。
@Serializable
data class TaskState(
    val id: String = "",
    val title: String = "",
    val status: String = "idle",            // 派生: "active" | "done" | "dropped"
    val steps: List<TaskStep> = emptyList(),
    val currentFocusStepId: String? = null, // ← 新增: 当前 DOING 的叶节点 UUID
    val createdAt: Long = 0                 // ← 新增
)
```

**SessionEntity 兼容**：
- `active_task` 列**保留**（JSON 序列化 `TaskState`），用于跨版本兼容
- 新增 `active_task_tree_id` 列 → FK → `task_nodes` 表（逐步迁移）

---

## 3. 数据模型

### 3.1 Room Entity

```kotlin
@Entity(
    tableName = "task_nodes",
    indices = [
        Index("session_id"),
        Index("parent_id"),
        Index("status")
    ],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TaskNodeEntity(
    @PrimaryKey val id: String,                        // UUID
    @ColumnInfo(name = "session_id") val sessionId: String,  // FK → sessions
    @ColumnInfo(name = "parent_id") val parentId: String?,    // 父节点 UUID (null = root)
    @ColumnInfo(name = "sort_order") val sortOrder: Int,      // 同级排序
    val title: String,
    val description: String = "",
    val status: String,                               // "todo" | "doing" | "done" | "dropped"
    val note: String?,                                 // set_step_status 附加说明
    @ColumnInfo(name = "artifact_file_uuids") val artifactFileUuids: String? = null,  // JSON 数组
    @ColumnInfo(name = "is_collapsed") val isCollapsed: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

### 3.2 状态模型（叶节点 vs 父节点）

```
设计原则：状态仅属于叶节点。父节点的"状态"是从子节点实时推演的视觉提示。

┌─────────────────────────────────────────────────────────────┐
│  叶节点 (Leaf Node) — 可独立设置状态                          │
│                                                              │
│  TODO ──→ DOING ──→ DONE        （LLM/用户操作）             │
│    │                  │                                       │
│    └──────────────────┴──→ DROPPED  （任意时刻可终止）         │
│                                                              │
│  同一时刻，每个 Session 最多一个叶节点处于 DOING。              │
├─────────────────────────────────────────────────────────────┤
│  父节点 (Parent Node) — 状态为派生值，不存储                    │
│                                                              │
│  派生规则 (Repository 层实时计算):                              │
│    · 任一子节点 DOING    → 父节点显示 "● 进行中"               │
│    · 全部子节点 DONE     → 父节点显示 "✅ 完成"                │
│    · 全部子节点 TODO     → 父节点显示 "○ 待办"                │
│    · 任一子节点 DROPPED  → 父节点显示 "⊘ 部分废弃"            │
│                                                              │
│  级联规则 (Repository 写操作时触发):                           │
│    · 父节点所有叶节点 DONE → TaskState.status = "done"        │
│    · 父节点标记 DROPPED    → 所有子节点递归 DROPPED           │
└─────────────────────────────────────────────────────────────┘
```

**进度百分比**：`TaskState` 中不存储 `progress`。UI 展示时从树中实时计算：`done叶节点数 / 总叶节点数 × 100`。

### 3.3 DAO

```kotlin
@Dao
interface TaskNodeDao {
    // 查询
    @Query("SELECT * FROM task_nodes WHERE session_id = :sessionId AND status != 'dropped' ORDER BY sort_order")
    fun observeActiveTree(sessionId: String): Flow<List<TaskNodeEntity>>
    
    @Query("SELECT * FROM task_nodes WHERE id = :id")
    suspend fun getById(id: String): TaskNodeEntity?
    
    // 获取当前 DOING 的叶节点（同一时刻最多一个）
    @Query("SELECT * FROM task_nodes WHERE session_id = :sessionId AND status = 'doing' AND id NOT IN (SELECT DISTINCT parent_id FROM task_nodes WHERE parent_id IS NOT NULL) LIMIT 1")
    suspend fun getCurrentDoingLeaf(sessionId: String): TaskNodeEntity?
    
    // 写入
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: TaskNodeEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(nodes: List<TaskNodeEntity>)
    
    // 级联操作
    @Query("UPDATE task_nodes SET status = 'dropped', updated_at = :now WHERE id = :id")
    suspend fun markDropped(id: String, now: Long)
    
    @Query("UPDATE task_nodes SET status = 'dropped', updated_at = :now WHERE parent_id = :parentId")
    suspend fun markChildrenDropped(parentId: String, now: Long)
    
    @Query("DELETE FROM task_nodes WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
```

### 3.4 Domain Repository 接口

```kotlin
interface ITaskRepository {
    fun observeActiveTree(sessionId: String): Flow<List<TaskNode>>
    suspend fun initializePlan(sessionId: String, goal: String, tree: List<TaskNode>): TaskState
    suspend fun updatePlan(sessionId: String, operations: List<PlanPatchOp>): TaskState
    /**
     * 读取当前任务完整树。
     * 返回的 TaskState 中 status/进度 均为实时派生值，非 DB 存储字段。
     */
    suspend fun getPlan(sessionId: String): TaskState?
    suspend fun dropPlan(sessionId: String, reason: String)
    
    // 内部辅助（Repository 实现层调用）
    fun deriveParentStatus(children: List<TaskNode>): String
    fun countLeafProgress(nodes: List<TaskNode>): Pair<Int, Int>  // (done, total)
}

data class PlanPatchOp(
    val action: String,       // "set_status" | "add_step" | "remove_step" | "move_step" | "update_title" | "set_note"
    val stepId: String? = null,
    val parentId: String? = null,
    val payload: Map<String, String>? = null  // 动作参数
)
```

---

## 4. 工具接口（4 工具，消除冗余）

| 工具 | 用途 | 幂等性 |
|------|------|--------|
| `initialize_plan` | 创建任务树 | 重复调用返回冲突 |
| `update_plan` | 增量修改（状态/结构/内容），借鉴 `patch_file` 的 operations 数组模式 | 是 |
| `get_plan` | 读取当前任务完整树 | 是 |
| `drop_plan` | 终止任务，递归标记 DROPPED | 是 |

### 4.1 `initialize_plan`

```json
// 请求
{
  "tool": "initialize_plan",
  "parameters": {
    "goal": "编写一份关于 Nexara 架构的技术报告",
    "tree": [
      {
        "id": "s1",
        "title": "收集资料",
        "sortOrder": 0,
        "children": [
          {"id": "s1a", "title": "阅读 ARCHITECTURE_DESIGN.md", "sortOrder": 0},
          {"id": "s1b", "title": "整理关键模块清单", "sortOrder": 1}
        ]
      },
      {
        "id": "s2",
        "title": "撰写报告",
        "sortOrder": 1,
        "children": []
      }
    ]
  }
}

// 响应 — 成功
{
  "planId": "uuid-xxx",
  "goal": "...",
  "message": "任务已创建，共 4 个步骤"
}

// 响应 — 冲突（已有活跃任务，LLM 自行决策）
{
  "conflict": true,
  "existingPlanId": "uuid-existing",
  "existingGoal": "编写测试用例",
  "existingProgress": "1/3 done",
  "suggestion": "当前已有活跃任务。可调用 drop_plan 终止后重试，或向用户询问是否替换。"
}
```

### 4.2 `update_plan`（统一增量修改）

```json
// 请求 — 借鉴 patch_file 的 operations 数组模式
{
  "tool": "update_plan",
  "parameters": {
    "operations": [
      {
        "action": "set_status",
        "stepId": "s1a",                              // ← 叶节点
        "payload": {"status": "done", "note": "已完成阅读"}
      },
      {
        "action": "set_status",
        "stepId": "s1b",                              // ← 叶节点
        "payload": {"status": "doing"}
      },
      // 注意：不操作 "s1"（父节点），其状态从 s1a/s1b 自动派生
      {
        "action": "add_step",
        "parentId": "s2",
        "payload": {"title": "添加图表", "sortOrder": "0"}
      }
    ]
  }
}

// 响应
{
  "applied": 3,
  "currentPlan": { /* 最新 TaskState，父节点 status 已自动更新 */ }
}
```

**支持的 action 类型**：

| action | payload | 说明 |
|--------|---------|------|
| `set_status` | `status` ("todo"/"doing"/"done"/"dropped"), `note`? | **仅叶节点有效**。父节点状态为派生值，不可直接设置 |
| `add_step` | `title`, `parentId`?, `sortOrder`? | 新增子步骤 |
| `remove_step` | — | 删除步骤（级联删除子节点） |
| `move_step` | `newParentId`?, `newSortOrder` | 移动步骤位置 |
| `update_title` | `title` | 修改标题 |
| `set_note` | `note` | 添加/修改备注 |

### 4.3 `get_plan`

```json
// 请求
{ "tool": "get_plan" }

// 响应 — 进度为实时派生值
{
  "planId": "uuid-xxx",
  "goal": "...",
  "status": "active",
  "leafProgress": {"done": 2, "total": 5},
  "currentFocus": {"id": "s1b", "title": "整理关键模块清单", "status": "doing"},
  "tree": [ /* 完整 TaskStep[]，父节点 status 已由 Repository 预计算注入 */ ]
}
```

### 4.4 `drop_plan`

```json
// 请求
{ "tool": "drop_plan", "parameters": { "reason": "用户要求更换方向" } }

// 响应
{ "dropped": true, "planId": "uuid-xxx" }
```

---

## 5. 上下文注入

### 5.1 注入模式（系统性功能，非任务规划器独有）

Token 节约模式是一个**项目级开关**，由 `SessionOptions.economyMode: Boolean` 统一控制。该开关在会话设置面板 → Settings Tab → 上下文管理区域中展示，未来所有涉及 Token 敏感操作的组件均自动读取此标志决定行为。

| 模式 | `economyMode` | 任务规划器注入内容 | 预估 Token |
|------|:--:|------|-----------|
| **标准模式**（默认） | `false` | 完整任务树（含所有节点标题+状态+层级缩进） | ~1-3K |
| **节约模式** | `true` | root + DOING + 接下来 2-3 个 TODO | ~0.3-0.5K |

### 5.2 标准模式注入格式（默认）

```
## Task Board
🎯 编写 Nexara 架构报告   [2/5 done]

├─ ✅ 收集资料
│   ├─ ✅ 阅读 ARCHITECTURE_DESIGN.md
│   └─ ⟳ 整理关键模块清单
├─ ○ 撰写报告
│   ├─ ○ 添加图表
│   └─ ○ 编写正文
└─ ○ 审核校对
```

### 5.3 节约模式注入格式

```
## Current Mission
Goal: 编写 Nexara 架构报告
Progress: 2/5 leaf steps done

### Active
⟳ 整理关键模块清单 (in: 收集资料)

### Upcoming
○ 撰写报告
○ 审核校对
```

### 5.4 断点重连提示

会话重启时，标准模式下直接注入完整树（与 §5.2 一致）；节约模式下额外追加：

```
> Previous mission resumed. Current focus: 整理关键模块清单.
> Use get_plan for full task board.
```

---

## 6. UI/UX 设计

### 6.1 复用现有组件

| 元素 | 复用组件 |
|------|---------|
| 面板容器 | `NexaraGlassCard` |
| 进度条 | M3 `LinearProgressIndicator` |
| 列表项 | `NexaraSettingsItem` 模式 |
| 状态圆点 | `IndexStatusBadge` 模式（与统一资源 OS 一致） |
| 确认弹窗 | `NexaraConfirmDialog` |

### 6.2 TaskFloatingPanel 布局

```
┌──────────────────────────────────────────────┐
│  🎯 编写 Nexara 架构报告                      │  ← 根目标标题
│  ████████░░░░░░░░  2/5 步骤 · 40%            │  ← 进度条（实时计算: done叶节点/总叶节点）
│  ──────────────────────────────────────────  │
│                                              │
│  ● 收集资料                                  │  ← 父节点: 派生状态（因子节点 s1b=doing）
│    ├─ ✅ 阅读 ARCHITECTURE_DESIGN.md        │  ← 叶节点: 独立状态 DONE
│    └─ ⟳ 整理关键模块清单                      │  ← 叶节点: 独立状态 DOING
│                                              │
│  ○ 撰写报告                                  │  ← 父节点: 派生状态（全部子节点=todo）
│    ├─ ○ 添加图表                             │
│    └─ ○ 编写正文                             │
│                                              │
│  ▼ 收起                                      │
└──────────────────────────────────────────────┘
```

**显示规则**：
- 根目标进度条：`done叶节点数 / 总叶节点数 × 100`，UI 渲染时实时计算，不存储
- 父节点状态图标：`●(doing) / ✅(done) / ○(todo) / ⊘(partial-dropped)`，完全派生
- 叶节点状态图标：`✅(done) / ⟳(doing·脉冲) / ○(todo) / ✕(dropped)`，来自 DB 字段
- 父节点**不显示独立进度条**——其"完成度"由子节点的 ✅/○ 数量直接可视传达，额外百分比是信息冗余

### 6.3 交互规则

| 规则 | 说明 |
|------|------|
| 自动展开 | `initialize_plan` 完成后自动滑出面板 |
| 折叠持久化 | 用户手动折叠后，`isCollapsed` 写入 DB，AI 更新不解开 |
| 更新提示 | 后台状态变更时，折叠面板的标题栏微闪烁（`NexaraColors.Primary` 呼吸动画） |
| 位置 | `ChatInput` 上方，作为输入区动态扩展 |

---

## 7. 数据库变更

### 7.1 新建表

```sql
CREATE TABLE task_nodes (
    id TEXT PRIMARY KEY NOT NULL,
    session_id TEXT NOT NULL,
    parent_id TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'todo',
    note TEXT,
    artifact_file_uuids TEXT,
    is_collapsed INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

CREATE INDEX idx_tn_session ON task_nodes(session_id);
CREATE INDEX idx_tn_parent ON task_nodes(parent_id);
CREATE INDEX idx_tn_status ON task_nodes(status);
```

### 7.2 SessionEntity 扩展

```kotlin
@ColumnInfo(name = "active_task_tree_id")
val activeTaskTreeId: String? = null,  // 新增：指向 task_nodes 中 parent_id=null 的根节点
```

---

## 8. 与现有系统的集成点

### 8.1 ExecutionStep 关联

```kotlin
// ExecutionStep (ChatModels.kt) — 新增可选字段
data class ExecutionStep(
    val id: String,
    val type: String,
    val toolName: String? = null,
    val taskStepId: String? = null,  // ← 新增：关联 TaskStep UUID
    // ... 其余字段不变
)
```

**关系**：TaskStep(规划层) 1:N ExecutionStep(执行层)。LLM 调用 `update_plan(set_status)` 时自动将当前 `toolCallId` 关联到对应的 `taskStepId`。

### 8.2 统一资源 OS 产物关联

`TaskNodeEntity.artifact_file_uuids` 存储 JSON 数组 `["uuid-0xA1", "uuid-0xA2"]`，指向 `workspace_files.uuid`。

任务完成后，UI 可展示产物文件列表（通过 `IWorkspaceRepository.getByUuid()` 获取文件信息）。

---

## 9. 审批模型

任务管理器的所有操作（`set_status` / `add_step` / `remove_step` / `update_title`）均为**纯元数据操作**——修改的是任务节点在数据库中的标题和状态字段，不涉及文件系统写入、不涉及网络调用、不涉及系统命令执行。最坏情况是误删一个任务步骤，可通过 `add_step` 立即重建。

**结论：任务管理器不需要接入 Agent 审批循环。**

在 Nexara 的整体安全模型中，审批流（`ExecutionMode.SEMI/MANUAL`）的发力点是：
- SSH 远程命令执行（远期规划）——这才是真正需要用户确认的操作
- 文件系统写入（由统一资源 OS 的乐观锁覆盖，非审批流）
- 网络外呼（WebSearch 等，已有独立审批逻辑）

当前基于"不做 IDE"的框架内，所有内置工具均不具备本地破坏性执行能力。

---

## 11. 跨模型兼容性审查

### 11.1 工具协议审查

| 设计点 | OpenAI (function_calls) | Anthropic (tool_use) | 评估 |
|--------|------------------------|---------------------|------|
| `initialize_plan` 嵌套 tree JSON | ✅ 原生支持 | ✅ 原生支持 | 树深 2-3 层，各模型均稳定 |
| `update_plan` operations 数组 | ✅ 单次可并行多个 function_calls | ⚠️ 单次 tool_use 只能一个调用 | **已优化**：operations 数组支持批处理，一次调用完成多步修改 |
| `get_plan` 纯读取 | ✅ | ✅ | 无参数，极简 |
| `drop_plan` 单参数 | ✅ | ✅ | 仅 `reason` 字符串 |
| 错误回馈格式 | ✅ JSON 结构化 | ✅ JSON 结构化 | 扁平键值，无嵌套黑话 |

### 11.2 特殊边界处理

**`set_status` 作用于父节点**：若 LLM 误将 `set_status` 指向父节点（`children` 非空），返回：

```json
{
  "error": "PARENT_STATUS_DERIVED",
  "stepId": "s1",
  "message": "步骤 '收集资料' 是父节点（含 2 个子步骤），其状态由子节点自动派生，不可直接设置。",
  "suggestion": "请改为对子步骤 (s1a, s1b) 执行 set_status。父节点状态会自动更新。"
}
```

**跨模型一致性保证**：所有工具通过 Nexara 的 `ProtocolTool` → OpenAI/Anthropic/VertexAI 三协议适配层自动转换参数格式，无需为特定模型编写特殊逻辑。

---

## 12. 实施清单

| Phase | 内容 | 文件 |
|-------|------|------|
| T0 | `TaskNodeEntity` + `TaskNodeDao` + DB Migration v10 | Entity/DAO/Database |
| T1 | `ITaskRepository` + `TaskRepository` 实现 | Domain/Data |
| T2 | `TaskStep` 模型扩展（新增字段，兼容旧序列化） | `ChatModels.kt` |
| T3 | 4 个 Skill（`initialize_plan`/`update_plan`/`get_plan`/`drop_plan`） | `skills/` |
| T4 | `TaskFloatingPanel` UI + `ChatScreen` 集成 | `ui/chat/components/` |
| T5 | ContextBuilder 任务上下文注入 + 断点重连提示 | `ContextBuilder.kt` |
| T6 | `ExecutionStep.taskStepId` 扩展 | `ChatModels.kt` |

---

**设计编写**: DeepSeek（基于 Gemini 3 Flash v2 方案完善）
**审查闭合**: 20260515-task-planner-review.md 全部 12 项问题
**日期**: 2026-05-15
