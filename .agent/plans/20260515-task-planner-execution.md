# 任务规划器 — 多会话并行执行计划

> **日期**: 2026-05-15
> **设计规范**: `20260515-task-planning-tool-architecture.md` v3.4（终稿）
> **执行引擎**: GLM-5.1（独立会话）
> **项目路径**: `/Users/promenar/Codex/Nexara/native-ui/`
> **设计规范路径**: `/Users/promenar/Codex/Nexara/.agent/plans/20260515-task-planning-tool-architecture.md`

---

## 0. 执行架构

```
Batch 1 (并行 — 2 会话)
  Session 1: 数据基础层 [~1.5h]   ∥  Session 2: 域接口 + SessionOptions [~0.5h]

Batch 2 (并行 — 2 会话，依赖 1+2)
  Session 3: Repository + 4 Skill [~1.5h]   ∥  Session 4: UI 全量 [~1.5h]

Batch 3 (串行 — 依赖 3)
  Session 5: ContextBuilder + ExecutionStep [~1h]

Batch 4 (串行 — 依赖全部)
  Session 6: 测试 + DIA 收尾 [~1h]

总耗时（最大并行）: ~5h
```

---

## Session 1: 数据基础层

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 1（可与 Session 2 并行） |
| **目标** | TaskNodeEntity + TaskNodeDao + DB Migration v10 + ChatModels 扩展 TaskStep/TaskState |
| **创建文件** | 3 个 |
| **修改文件** | 3 个 |
| **预估** | ~1.5h |

### 复制此提示词启动会话

```
# 任务：任务规划器数据基础层

## 上下文
为 Nexara 实现任务规划器的数据层基础。先读取设计规范了解完整上下文：

设计规范文件: /Users/promenar/Codex/Nexara/.agent/plans/20260515-task-planning-tool-architecture.md

重点阅读：§3.1（Room Entity）、§3.2（状态模型）、§3.3（DAO）、§7（DB 变更）、§2.2（TaskStep/TaskState 扩展）、§8.1（ExecutionStep 关联）

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 必须读取的现有文件
1. app/src/main/java/com/promenar/nexara/data/local/db/NexaraDatabase.kt — 当前 VERSION=9, 所有 DAO
2. app/src/main/java/com/promenar/nexara/data/local/db/entity/SessionEntity.kt — SessionEntity 所有字段
3. app/src/main/java/com/promenar/nexara/data/model/ChatModels.kt — TaskStep / TaskState / ExecutionStep 定义

## 需要创建的文件

### 1. TaskNodeEntity.kt
路径: app/src/main/java/com/promenar/nexara/data/local/db/entity/TaskNodeEntity.kt

按照设计规范 §3.1 创建 Room Entity，字段：
- id (String, @PrimaryKey)
- sessionId (String, FK → sessions)
- parentId (String?)
- sortOrder (Int)
- title (String)
- description (String, default "")
- status (String, default "todo")
- note (String?)
- artifactFileUuids (String? — JSON 数组)
- isCollapsed (Boolean, default false)
- createdAt (Long)
- updatedAt (Long)

索引：session_id / parent_id / status。FK: session_id → sessions(id) ON DELETE CASCADE

### 2. TaskNodeDao.kt
路径: app/src/main/java/com/promenar/nexara/data/local/db/dao/TaskNodeDao.kt

按照设计规范 §3.3 创建 DAO，含全部 7 个方法（observeActiveTree / getById / getCurrentDoingLeaf / upsert / upsertAll / markDropped / markChildrenDropped / deleteBySession）。

### 3. NexaraDatabase.kt — 修改
- VERSION: 9 → 10
- 新增 Migration 9→10：CREATE TABLE task_nodes（含全部列+索引+FK）+ ALTER TABLE sessions ADD COLUMN active_task_tree_id TEXT
- 新增 DAO 声明：`abstract fun taskNodeDao(): TaskNodeDao`

### 4. ChatModels.kt — 修改 TaskStep
在现有 `TaskStep` 中新增字段（不删除旧字段，保证序列化兼容）：
```kotlin
@Serializable
data class TaskStep(
    val id: String = "",
    val parentId: String? = null,           // ← 新增
    val title: String = "",
    val description: String = "",
    val status: String = "pending",
    val sortOrder: Int = 0,                 // ← 新增
    val note: String? = null,               // ← 新增
    val artifactFileUuids: List<String>? = null,  // ← 新增
    val children: List<TaskStep> = emptyList(),   // ← 新增
    val isCollapsed: Boolean = false,       // ← 新增
    val createdAt: Long = 0,                // ← 新增
    val updatedAt: Long = 0                 // ← 新增
)
```

### 5. ChatModels.kt — 修改 TaskState
```kotlin
@Serializable
data class TaskState(
    val id: String = "",
    val title: String = "",
    val status: String = "idle",
    val progress: Int = 0,                  // ← 保留兼容，但不作为主要数据源
    val steps: List<TaskStep> = emptyList(),
    val currentFocusStepId: String? = null, // ← 新增
    val createdAt: Long = 0                 // ← 新增
)
```

### 6. ChatModels.kt — 修改 ExecutionStep
```kotlin
data class ExecutionStep(
    val id: String,
    val type: String,
    val toolName: String? = null,
    val taskStepId: String? = null,  // ← 新增
    // ... 其余字段不变，保持原有顺序
)
```

### 7. SessionEntity.kt — 修改
在 `workspaceRootUuid` 之后新增：
```kotlin
@ColumnInfo(name = "active_task_tree_id")
val activeTaskTreeId: String? = null,
```

## 验证标准
1. 编译通过: `./gradlew compileDebugKotlin`
2. DB version = 10
3. TaskStep 旧字段（id/title/description/status）保持原有顺序和默认值不变
4. Migration SQL 语法正确

## 注意事项
- ChatModels.kt 使用 @Serializable，新增字段必须有默认值
- TaskStep 原有字段顺序不要改变（id → title → description → status ... 其他新增字段追在后面）
- 保持与现有 Entity 相同的代码风格
```


---

## Session 2: 域接口 + SessionOptions 扩展

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 1（可与 Session 1 并行） |
| **目标** | ITaskRepository 接口 + PlanPatchOp + SessionOptions.economyMode |
| **创建文件** | 1 个 |
| **修改文件** | 1 个 |
| **预估** | ~0.5h |

### 复制此提示词启动会话

```
# 任务：任务规划器域接口 + Token 预算开关

## 上下文
为 Nexara 实现任务规划器的 Domain 层接口和系统性 Token 节约模式开关。先读取设计规范：

设计规范文件: /Users/promenar/Codex/Nexara/.agent/plans/20260515-task-planning-tool-architecture.md

重点阅读：§3.4（ITaskRepository + PlanPatchOp）、§5.1（economyMode 开关）、以及 ARCHITECTURE_DESIGN.md §6.3

项目路径: /Users/promenar/Codex/Nexara/native-ui/
架构设计文档: /Users/promenar/Codex/Nexara/docs/ARCHITECTURE_DESIGN.md

## 必须读取的现有文件
1. app/src/main/java/com/promenar/nexara/domain/repository/IWorkspaceRepository.kt — 接口风格参考
2. app/src/main/java/com/promenar/nexara/data/model/ChatModels.kt — SessionOptions 类（约第 224 行）

## 需要创建的文件

### 1. ITaskRepository.kt
路径: app/src/main/java/com/promenar/nexara/domain/repository/ITaskRepository.kt

```kotlin
package com.promenar.nexara.domain.repository

import com.promenar.nexara.data.model.*
import kotlinx.coroutines.flow.Flow

interface ITaskRepository {
    fun observeActiveTree(sessionId: String): Flow<List<TaskStep>>
    suspend fun initializePlan(sessionId: String, goal: String, tree: List<TaskStep>): TaskState
    suspend fun updatePlan(sessionId: String, operations: List<PlanPatchOp>): TaskState
    /**
     * 读取当前任务完整树。
     * 返回的 TaskState 中 status/进度 均为实时派生值，非 DB 存储字段。
     */
    suspend fun getPlan(sessionId: String): TaskState?
    suspend fun dropPlan(sessionId: String, reason: String)
    
    // 内部辅助
    fun deriveParentStatus(children: List<TaskStep>): String
    fun countLeafProgress(steps: List<TaskStep>): Pair<Int, Int>  // (done, total)
}

data class PlanPatchOp(
    val action: String,
    val stepId: String? = null,
    val parentId: String? = null,
    val payload: Map<String, String>? = null
)
```

## 需要修改的文件

### 2. ChatModels.kt — SessionOptions 新增 economyMode
在 `SessionOptions` 末尾（`fontSize` 之后）新增：
```kotlin
val economyMode: Boolean = false,  // ← 新增。Token 节约模式开关
```

## 验证标准
1. 编译通过
2. SessionOptions 新字段有默认值 false
```

---

## Session 3: Repository 实现 + 4 Skill

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 2（依赖 Session 1+2，可与 Session 4 并行） |
| **目标** | TaskRepository 实现 + 4 个 Skill（initialize/update/get/drop） |
| **创建文件** | 5 个 |
| **预估** | ~1.5h |

### 复制此提示词启动会话

```
# 任务：任务规划器 Repository + Skill 实现

## 上下文
实现 TaskRepository 和 4 个任务管理 Skill。先完整读取设计规范：

设计规范文件: /Users/promenar/Codex/Nexara/.agent/plans/20260515-task-planning-tool-architecture.md

重点阅读：§3.3（DAO）、§3.4（ITaskRepository）、§4（工具接口全部）、§11.2（PARENT_STATUS_DERIVED 边界处理）、§9（不用审批）

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 前置确认
以下必须在 Session 1+2 已完成：
1. TaskNodeDao 存在
2. ITaskRepository 接口存在
3. PlanPatchOp 类型存在
4. TaskStep/TaskState 含新增字段
5. SessionOptions.economyMode 存在

## 需要创建的文件

### 1. TaskRepository.kt
路径: app/src/main/java/com/promenar/nexara/data/repository/TaskRepository.kt

实现 ITaskRepository，构造函数注入 TaskNodeDao。

关键实现：
- `initializePlan(sessionId, goal, tree)`:
  1. 检查是否有活跃任务 → 有则返回冲突（返回 TaskState 含 existing 信息）
  2. 递归展平 tree → List<TaskNodeEntity>，根节点 parentId=null
  3. 批量 upsertAll
  4. 第一个叶节点自动标记 DOING
  5. 返回 TaskState
  
- `updatePlan(sessionId, operations)`:
  1. 遍历 operations
  2. `set_status`: 检查目标是否父节点（有子节点）→ 是则抛 PARENT_STATUS_DERIVED 错误
  3. `set_status`: 若设置为 DOING → 先取消当前所有 DOING 节点（改回 TODO）
  4. 每个 operation 映射到 DB upsert
  5. 操作完成后调用 deriveParentStatus 更新父节点派生状态
  6. 返回更新后的 TaskState

- `getPlan(sessionId)`: 从 DB 加载树 → 调用 deriveParentStatus + countLeafProgress → 返回

- `dropPlan(sessionId, reason)`: 递归 markDropped(markChildrenDropped)

- `deriveParentStatus(children)`: 按规范 §3.2 实现 4 种派生规则

- `countLeafProgress(steps)`: 递归统计叶节点 done/total

### 2-5. 4 个 Skill 文件

全部放在: app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/

#### 2. InitializePlanSkill.kt
- id: "initialize_plan"
- 参数: goal (String), tree (JSON Array of TaskStep)
- 调用 taskRepo.initializePlan()
- 返回创建结果或冲突信息

#### 3. UpdatePlanSkill.kt
- id: "update_plan"
- 参数: operations (JSON Array of PlanPatchOp)
- 调用 taskRepo.updatePlan()
- 成功返回 applied count + 最新 TaskState
- 失败返回 PARENT_STATUS_DERIVED 错误（按 §11.2 格式）

#### 4. GetPlanSkill.kt
- id: "get_plan"
- 无参数
- 调用 taskRepo.getPlan(sessionId)
- 返回完整 TaskState JSON（含 leafProgress、currentFocus）

#### 5. DropPlanSkill.kt
- id: "drop_plan"
- 参数: reason (String)
- 调用 taskRepo.dropPlan()
- 返回 dropped: true

所有 Skill 实现 SkillDefinition 接口，使用 SkillExecutionContext.sessionId 获取会话 ID。

### 6. NexaraApplication.kt — 注册 Skill
找到 skill 注册区（FilePatchSkill 之后），新增：
```kotlin
register(InitializePlanSkill(taskRepository))
register(UpdatePlanSkill(taskRepository))
register(GetPlanSkill(taskRepository))
register(DropPlanSkill(taskRepository))
```

## 验证标准
1. 编译通过
2. `set_status` 作用于父节点时返回 PARENT_STATUS_DERIVED 错误
3. 设置 DOING 时自动取消其他 DOING 节点
4. 所有 Skill 的 parametersSchema 为合法 JSON（参照设计规范 §4 的请求格式编写 JSON Schema）

## 注意事项
- Skill 注册使用与现有 Skill 一致的 register() 模式
- TaskRepository 构造函数需注入到 NexaraApplication 的 lazy 初始化中（参照 WorkspaceRepository 的模式）
- `updatePlan` 必须处理 `set_status` 设置 DOING 时的互斥逻辑（同一时刻最多一个 DOING）
```

---

## Session 4: UI 全量

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 2（依赖 Session 1+2，可与 Session 3 并行） |
| **目标** | TaskFloatingPanel + ChatScreen 集成 + SessionSettings economyMode 开关 |
| **创建文件** | 1 个 |
| **修改文件** | 2 个 |
| **预估** | ~1.5h |

### 复制此提示词启动会话

```
# 任务：任务规划器 UI 全量

## 上下文
为 Nexara 实现任务规划器的全部 UI 组件，复用项目已有的 Material3 组件和 Nexara 系列组件。完整读取：

设计规范文件: /Users/promenar/Codex/Nexara/.agent/plans/20260515-task-planning-tool-architecture.md

重点阅读：§6（UI/UX 全部）、§5.1（economyMode 开关位置）、ARCHITECTURE_DESIGN.md §6.3

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 必须读取的现有文件
1. app/src/main/java/com/promenar/nexara/ui/common/NexaraGlassCard.kt — 玻璃卡片
2. app/src/main/java/com/promenar/nexara/ui/common/NexaraConfirmDialog.kt — 确认弹窗
3. app/src/main/java/com/promenar/nexara/ui/common/IndexStatusBadge.kt — 状态徽标模板
4. app/src/main/java/com/promenar/nexara/ui/theme/Color.kt — 完整色值
5. app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt — 找到 ChatInput 组件位置和 showWorkspaceSheet 模式
6. app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt — 找到 Settings Tab 的 LazyColumn 内容（约第 820 行起，含 summary/active window 等滑块）

### 1. TaskFloatingPanel.kt（新建）
路径: app/src/main/java/com/promenar/nexara/ui/chat/components/TaskFloatingPanel.kt

按照设计规范 §6.2 实现。核心结构：

```kotlin
@Composable
fun TaskFloatingPanel(
    sessionId: String,
    taskRepo: ITaskRepository,
    modifier: Modifier = Modifier
) {
    val activeTree by taskRepo.observeActiveTree(sessionId).collectAsState(emptyList())
    if (activeTree.isEmpty()) return  // 无任务不渲染
    
    // 计算: done叶节点 / 总叶节点
    val (doneCount, totalCount) = taskRepo.countLeafProgress(activeTree)
    val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    
    var isCollapsed by rememberSaveable { mutableStateOf(false) }
    
    NexaraGlassCard(modifier = modifier) {
        Column {
            // 标题行 + 进度条
            Row { Text(goal) + Spacer + Text("$doneCount/$totalCount") }
            if (!isCollapsed) {
                LinearProgressIndicator(progress = progress)
                HorizontalDivider(...)
                // 递归树: TaskNodeRow(node, depth=0)
                LazyColumn { items(tree) { TaskNodeRow(it, 0) } }
            }
            // 折叠按钮
        }
    }
}

@Composable
private fun TaskNodeRow(node: TaskStep, depth: Int) {
    val statusIcon = when {
        node.children.isNotEmpty() -> deriveParentIcon(node.children)
        node.status == "done" -> "✅"
        node.status == "doing" -> "⟳"  // 脉冲动画
        node.status == "dropped" -> "✕"
        else -> "○"
    }
    val indent = (depth * 16).dp
    
    Row(modifier = Modifier.padding(start = indent)) {
        Text(statusIcon)
        Text(node.title, style = NexaraTypography.bodyMedium, color = if (status == "doing") NexaraColors.Primary else NexaraColors.OnSurface)
    }
    
    if (node.children.isNotEmpty()) {
        node.children.sortedBy { it.sortOrder }.forEach { TaskNodeRow(it, depth + 1) }
    }
}
```

关键细节：
- 叶节点 DOING 状态：文字 Primary 色，配合脉冲动画圆点（复用 IndexStatusBadge 的 PulseDot）
- 父节点图标完全派生，不由 DB 读取
- 使用 NexaraTypography / NexaraColors，不硬编码色值
- 进度条使用 M3 LinearProgressIndicator
- `rememberSaveable` 持久化折叠状态

### 2. ChatScreen.kt — 集成 TaskFloatingPanel
在 ChatInput 上方插入 TaskFloatingPanel：
- 搜索 ChatScreen 中 ChatInput 的渲染位置
- 在 ChatInput 之前插入：
```kotlin
TaskFloatingPanel(
    sessionId = sessionId,
    taskRepo = taskRepository,  // 需通过参数传入或从 ViewModel 获取
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
)
```
- 确保 taskRepository 实例可在 ChatScreen 作用域内访问（通过 ChatViewModel 暴露或参数传递）

### 3. SessionSettingsSheet.kt — 添加 economyMode 开关
在 Settings Tab 的 LazyColumn 中，找到现有滑块之后的位置（约第 870 行），新增一个 Item：

```kotlin
// Token 节约模式开关
item {
    Text(
        text = "Token 节约模式",
        style = NexaraTypography.titleSmall,
        color = NexaraColors.OnSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Token, null, tint = NexaraColors.OnSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("精简上下文注入", style = NexaraTypography.bodyMedium, color = NexaraColors.OnSurface)
            Text("减少 Token 消耗，适用于极低成本 API 场景", style = NexaraTypography.labelSmall, color = NexaraColors.OnSurfaceVariant)
        }
        Switch(
            checked = economyMode,
            onCheckedChange = {
                economyMode = it
                chatViewModel.updateSessionOptions(options.copy(economyMode = it))
            },
            colors = SwitchDefaults.colors(checkedThumbColor = NexaraColors.Primary)
        )
    }
}
```
需要新增 `var economyMode by remember(options.economyMode) { mutableStateOf(options.economyMode) }` 在 Settings Tab 的作用域内。

## 验证标准
1. 编译通过
2. TaskFloatingPanel 在无任务时不渲染
3. 父节点图标从子节点派生，不由 DB 读取
4. economyMode 开关在 SessionSettingsSheet → Settings Tab 中可见且可切换
```

---

## Session 5: ContextBuilder 注入 + ExecutionStep 关联

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 3（依赖 Session 3） |
| **目标** | ContextBuilder 注入任务上下文 + ToolExecutor 挂载 taskStepId |
| **修改文件** | 2 个 |
| **预估** | ~1h |

### 复制此提示词启动会话

```
# 任务：任务规划器 ContextBuilder + ExecutionStep 集成

## 上下文
将任务规划器的上下文注入到 ContextBuilder，并将 ExecutionStep 关联到 TaskStep。完整读取：

设计规范文件: /Users/promenar/Codex/Nexara/.agent/plans/20260515-task-planning-tool-architecture.md

重点阅读：§5（上下文注入全部）、§5.4（断点重连）、§8.1（ExecutionStep 关联）

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 必须读取的现有文件
1. app/src/main/java/com/promenar/nexara/ui/chat/manager/ContextBuilder.kt — 当前上下文构建管线，找到 System Prompt 拼接位置
2. app/src/main/java/com/promenar/nexara/ui/chat/manager/ToolExecutor.kt — 找到 executeSkill() 和 ExecutionStep 创建位置（约第 121 行）
3. app/src/main/java/com/promenar/nexara/ui/chat/manager/SessionManager.kt — 查找 Session 恢复逻辑

## 需要修改的文件

### 1. ContextBuilder.kt — 注入任务上下文
在 System Prompt 拼接之前，新增任务上下文注入：

```kotlin
// 读取任务状态
val taskState = taskRepository?.getPlan(sessionId)
val economyMode = session.options.economyMode

if (taskState != null) {
    val (done, total) = taskRepository.countLeafProgress(taskState.steps)
    
    if (economyMode) {
        // 节约模式: 仅注入摘要
        systemPrompt += buildString {
            appendLine("## Current Mission")
            appendLine("Goal: ${taskState.title}")
            appendLine("Progress: $done/$total leaf steps done")
            appendLine()
            // 找到 DOING 步骤
            val doing = findDoingLeaf(taskState.steps)
            if (doing != null) {
                appendLine("### Active")
                appendLine("⟳ ${doing.title} (in: ${findParentTitle(taskState.steps, doing.parentId)})")
            }
            // 接下来 2 个 TODO
            val todos = findNextTodos(taskState.steps, 2)
            if (todos.isNotEmpty()) {
                appendLine("### Upcoming")
                todos.forEach { appendLine("○ ${it.title}") }
            }
        }
    } else {
        // 标准模式: 完整任务树
        systemPrompt += "## Task Board\n"
        systemPrompt += renderTaskTree(taskState.steps, done, total)
    }
}

// 断点重连提示
if (taskState != null && isSessionResume(session)) {
    val doing = findDoingLeaf(taskState.steps)
    if (doing != null) {
        systemPrompt += "\n> Previous mission resumed. Current focus: ${doing.title}.\n"
        if (economyMode) systemPrompt += "> Use get_plan for full task board.\n"
    }
}
```

辅助方法（在 ContextBuilder 中实现或委托给 taskRepository）：
- `findDoingLeaf(steps)`: 递归查找 status=="doing" 的叶节点
- `findParentTitle(steps, parentId)`: 查找父节点标题
- `findNextTodos(steps, limit)`: 查找接下来 limit 个 TODO 叶节点
- `renderTaskTree(steps, done, total)`: 生成规范 §5.2 的树形文本
- `isSessionResume(session)`: 判断是否为断点重连（loopStatus==IDLE && 有活跃任务）

### 2. ToolExecutor.kt — taskStepId 关联
在 `executeSkill()` 方法中创建 ExecutionStep 时，如果存在活跃任务上下文，自动挂载 taskStepId：

```kotlin
// 在 executeSkill() 内，创建 ExecutionStep 时
val currentTaskStepId = taskRepository?.getPlan(sessionId)?.currentFocusStepId
val step = ExecutionStep(
    id = UUID.randomUUID().toString(),
    type = "tool_execution",
    toolName = skill.id,
    taskStepId = currentTaskStepId,  // ← 新增
    // ... 其余字段
)
```

## 验证标准
1. 编译通过
2. 标准模式下 System Prompt 含完整任务树
3. 节约模式下 System Prompt 仅含摘要
4. 断点重连提示正确注入
5. ExecutionStep 挂载 taskStepId（当有活跃任务时）

## 注意事项
- taskRepository 通过构造函数注入 ContextBuilder（若当前没有则新增可选参数）
- 树形渲染文本格式严格按规范 §5.2/§5.3
- Economy mode 开关读取 `session.options.economyMode`
```

---

## Session 6: 测试 + DIA 收尾

### 元信息

| 属性 | 值 |
|------|-----|
| **批次** | Batch 4（依赖 Session 1-5 全部） |
| **目标** | 单元测试 + 集成测试 + DIA 文档更新 |
| **创建文件** | 2 个测试 |
| **修改文件** | 多个 |
| **预估** | ~1h |

### 复制此提示词启动会话

```
# 任务：任务规划器收尾 — 测试 + DIA

## 上下文
任务规划器实现已完成，现在编写测试并更新项目文档。

设计规范文件: /Users/promenar/Codex/Nexara/.agent/plans/20260515-task-planning-tool-architecture.md

项目路径: /Users/promenar/Codex/Nexara/native-ui/

## 任务清单

### 任务 1: 编写测试

#### 1.1 TaskRepositoryTest.kt
路径: app/src/test/java/com/promenar/nexara/data/repository/TaskRepositoryTest.kt

使用 Room in-memory database，测试：
- `initializePlan`: 正常创建 + 重复调用返回冲突
- `updatePlan(set_status)`: 正常设置叶节点状态 + 设置 DOING 互斥
- `updatePlan(set_status)`: 对父节点设置状态 → PARENT_STATUS_DERIVED 错误
- `getPlan`: 返回正确的 leafProgress 和派生父节点状态
- `dropPlan`: 递归标记所有节点 dropped
- `deriveParentStatus`: 4 种场景（全部 done / 部分 doing / 全部 todo / 部分 dropped）

#### 1.2 TaskFloatingPanel 编译验证
确保 UI 组件编译通过（无需 Compose UI 测试，仅编译验证）。

### 任务 2: 全量编译 + 测试
```bash
cd /Users/promenar/Codex/Nexara/native-ui
./gradlew compileDebugKotlin
./gradlew testDebugUnitTest --tests "*TaskRepository*"
```

### 任务 3: DIA 文档更新
更新 `/Users/promenar/Codex/Nexara/.agent/registry.md`：
- 新增: `.agent/plans/20260515-task-planner-execution.md — 任务规划器多会话并行执行计划 ✅`

更新 `/Users/promenar/Codex/Nexara/.agent/handover.md`：
- 新增: task planner 实施完成摘要（6 会话、新增文件数、关键产出）
- DIA Status: CHANGELOG / ARCHITECTURE_DESIGN 确认已更新

更新 `/Users/promenar/Codex/Nexara/native-ui/docs/CHANGELOG.md`：
- [Unreleased] 下新增条目: 任务规划器实现

## 验证标准
1. 全量编译通过
2. TaskRepositoryTest 全部通过
3. registry.md / handover.md / CHANGELOG.md 更新完毕
```

---

## 执行后验证清单

```bash
cd /Users/promenar/Codex/Nexara/native-ui

# 1. 编译
./gradlew compileDebugKotlin

# 2. 测试
./gradlew testDebugUnitTest

# 3. 新文件确认
ls app/src/main/java/com/promenar/nexara/data/local/db/entity/TaskNodeEntity.kt
ls app/src/main/java/com/promenar/nexara/data/local/db/dao/TaskNodeDao.kt
ls app/src/main/java/com/promenar/nexara/domain/repository/ITaskRepository.kt
ls app/src/main/java/com/promenar/nexara/data/repository/TaskRepository.kt
ls app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/InitializePlanSkill.kt
ls app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/UpdatePlanSkill.kt
ls app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/GetPlanSkill.kt
ls app/src/main/java/com/promenar/nexara/ui/chat/manager/skills/DropPlanSkill.kt
ls app/src/main/java/com/promenar/nexara/ui/chat/components/TaskFloatingPanel.kt

# 4. DB 版本
grep -n "VERSION" app/src/main/java/com/promenar/nexara/data/local/db/NexaraDatabase.kt
# 预期: version = 10
```

---

**计划编写**: AI Assistant
**编写日期**: 2026-05-15
**设计规范参考**: `20260515-task-planning-tool-architecture.md` v3.4
**预计总耗时**: ~5h (含并行)
