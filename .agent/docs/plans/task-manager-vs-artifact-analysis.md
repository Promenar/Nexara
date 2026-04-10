# 架构决策分析：Task Manager vs 纯 Artifact 系统

> **Status**: Decision Analysis
> **Created**: 2026-02-18
> **Question**: 是否应该废弃 Task Manager，全面转向基于 Markdown 的 Artifact 系统？

---

## 1. 交互式暂停的本质分析

### 1.1 当前实现机制

```
用户请求 → Task Manager 创建任务 → 执行到 ask_user
    ↓
设置 loopStatus = 'paused' + pendingIntervention = "问题"
    ↓
Agent Loop 检测到暂停状态，退出循环
    ↓
UI 显示 "请回复以继续任务..."
    ↓
用户回复 → 检测到 paused 状态 → 自动恢复为 running
    ↓
继续执行，activeTask 状态保持
```

### 1.2 与自然语言询问的对比

| 维度 | 交互式暂停 (ask_user) | 自然语言询问 |
|------|----------------------|--------------|
| **状态保持** | ✅ activeTask 保持 | ❌ 需要从历史推断 |
| **上下文连续性** | ✅ 同一执行上下文 | ❌ 新的推理轮次 |
| **Token 消耗** | 低（状态已缓存） | 高（需重新理解历史） |
| **可靠性** | 依赖代码逻辑 | 依赖模型理解能力 |
| **跨模型一致性** | ❌ 不同模型表现不稳定 | ✅ 更通用 |

### 1.3 核心问题

**交互式暂停的价值**：
- 对于**同一模型**：确实能保持状态连续性
- 对于**不同模型**：表现不稳定，因为不同模型对 Task Manager 工具的理解和调用方式不同

**用户的观察是正确的**：
> "目前的整个多步骤复杂任务规划机制的实现实际在面对多种模型时表现并不稳定"

**根本原因**：
1. Task Manager 的复杂 schema 和行为逻辑对模型理解能力要求高
2. 不同模型的工具调用风格差异大
3. 强制顺序检查等逻辑在某些模型上会导致意外行为

---

## 2. 纯 Artifact 系统的可行性

### 2.1 设计理念

```
核心思想：将任务管理从"代码逻辑"转向"文档协议"

传统方式：
  Task Manager (代码) → 强制状态机 → UI 进度条

Artifact 方式：
  Markdown 文档 (协议) → 模型自觉 → UI 渲染文档
```

### 2.2 优势分析

| 优势 | 说明 |
|------|------|
| **模型无关性** | Markdown 是通用格式，所有模型都能理解 |
| **可调试性** | 文档可手动编辑、查看、版本控制 |
| **RAG 索引** | 任务历史可被检索，支持跨会话引用 |
| **灵活性** | 任务结构可自由扩展，不受代码限制 |
| **持久化** | 物理文件存储，永不丢失 |

### 2.3 劣势分析

| 劣势 | 说明 | 缓解措施 |
|------|------|----------|
| **无强制顺序检查** | 模型可能跳步 | Prompt 约定 + 文档格式规范 |
| **无实时进度条** | UI 无法显示进度 | 解析 Markdown 状态渲染 |
| **依赖模型自觉** | 可能格式不规范 | 校验 + 自动修复 |
| **无暂停机制** | 无法中断执行 | 用户可手动停止 |

### 2.4 关于"交互式暂停"的重新思考

**用户的核心洞察**：
> "即便没有这个功能，模型依旧可以在需要时用自然语言向用户发出询问结束对话然后用户回复意见消息后，模型继续，这个在本质上是否与交互式暂停是一样的？"

**答案：是的，本质相同**

```
交互式暂停：
  模型调用 ask_user → 循环暂停 → 用户回复 → 循环恢复

自然语言询问：
  模型输出问题 → 对话结束 → 用户回复 → 新对话开始

区别仅在于：
1. 交互式暂停保持 activeTask 状态
2. 自然语言询问需要模型从历史推断状态

但如果任务状态存储在 Markdown 文档中：
- 新对话可以读取文档恢复状态
- RAG 可以检索相关上下文
- 模型不需要"记住"之前的状态
```

---

## 3. 决策建议

### 3.1 方案对比

| 方案 | 复杂度 | 模型兼容性 | 可维护性 | 推荐度 |
|------|--------|------------|----------|--------|
| **保留 Task Manager** | 高 | 低（不稳定） | 低 | ⭐⭐ |
| **纯 Artifact 系统** | 中 | 高 | 高 | ⭐⭐⭐⭐ |
| **混合架构** | 最高 | 中 | 最低 | ⭐⭐⭐ |

### 3.2 推荐方案：纯 Artifact 系统

**理由**：

1. **模型无关性是核心价值**
   - 用户使用多种模型，工具调用行为不一致
   - Markdown 是所有模型都能理解的通用格式

2. **简化架构，减少维护负担**
   - Task Manager 代码复杂（394行），维护成本高
   - Markdown 协议简单，扩展性强

3. **RAG 索引带来额外价值**
   - 任务历史可被检索
   - 支持跨会话引用

4. **交互式暂停的价值有限**
   - 本质上与自然语言询问相同
   - 增加了代码复杂度但收益有限

### 3.3 实施建议

**Phase 1：创建 Artifact 任务系统**

```typescript
// 新增 Skill: manage_artifact_task
export const ManageArtifactTaskSkill: Skill = {
  id: 'manage_task',
  name: 'Manage Task',
  description: `Create and manage long-running tasks as Markdown documents.
  
Task documents are stored in .tasks/ directory and can be:
- Persisted across sessions
- Indexed by RAG system
- Manually edited by user

When you need user input, simply ask in your response and wait.
The user's reply will be added to conversation history.

Actions:
- 'create': Create a new task document
- 'update': Update task progress
- 'complete': Mark task as completed
- 'list': List all tasks`,
  // ...
};
```

**Phase 2：UI 渲染支持**

```tsx
// 任务文档渲染组件
const TaskDocumentViewer = ({ path }: { path: string }) => {
  const content = useFileContent(path);
  const parsed = parseTaskMarkdown(content);
  
  return (
    <View>
      <TaskHeader title={parsed.title} status={parsed.status} />
      <TaskSteps steps={parsed.steps} />
      <TaskArtifacts artifacts={parsed.artifacts} />
      <Markdown content={parsed.notes} />
    </View>
  );
};
```

**Phase 3：废弃 Task Manager**

- 移除 `TaskManagementSkill`
- 移除 `activeTask` 相关状态
- 移除 `loopStatus: 'paused'` 逻辑
- 简化 `ask_user` 为自然语言询问

---

## 4. 问题 2：工作区绑定能力

### 4.1 当前问题

```
agent_sandbox/workspace/  ← 所有会话共享同一工作区
```

**问题**：
- 不同会话的工件可能混淆
- 无法实现项目级别的隔离
- RAG 索引可能返回不相关的结果

### 4.2 解决方案：会话-工作区绑定

```typescript
interface Session {
  // ... 现有字段
  workspacePath?: string; // 绑定的工作区路径
}

// 示例：
// 会话 A: workspacePath = "projects/数据分析"
// 会话 B: workspacePath = "projects/文档撰写"
// 会话 C: workspacePath = undefined (使用默认工作区)
```

### 4.3 实现方式

**新增 Skill**: `bind_workspace`

```typescript
export const BindWorkspaceSkill: Skill = {
  id: 'bind_workspace',
  name: 'Bind Workspace',
  description: `Bind the current session to a specific workspace directory.
This ensures all file operations and artifacts are scoped to that directory.`,
  schema: z.object({
    path: z.string().describe('Relative path to workspace directory'),
    createIfNotExists: z.boolean().optional(),
  }),
  execute: async (params, context) => {
    // 1. 验证路径安全性
    // 2. 创建目录（如果需要）
    // 3. 更新 session.workspacePath
    // 4. 返回绑定结果
  },
};
```

### 4.4 目录结构设计

```
agent_sandbox/
├── workspace/              # 默认工作区（未绑定的会话）
├── projects/               # 项目目录
│   ├── 数据分析/
│   │   ├── .tasks/
│   │   ├── .artifacts/
│   │   └── output/
│   └── 文档撰写/
│       ├── .tasks/
│       ├── .artifacts/
│       └── output/
└── shared/                 # 跨会话共享资源
    ├── templates/
    └── libraries/
```

---

## 5. 最终建议

### 5.1 架构决策

| 决策 | 建议 |
|------|------|
| Task Manager | **废弃**，转向纯 Artifact 系统 |
| 交互式暂停 | **移除**，使用自然语言询问 |
| 顺序检查 | **移除**，依赖 Prompt 约定 |
| 工作区绑定 | **新增**，支持会话级别隔离 |

### 5.2 实施路线

| Phase | 内容 | 工作量 |
|-------|------|--------|
| 1 | 新增 `manage_task` (Markdown 版) | 3h |
| 2 | 新增 `bind_workspace` | 1h |
| 3 | UI 支持任务文档渲染 | 4h |
| 4 | 废弃旧 Task Manager | 2h |
| 5 | 更新 System Prompt | 1h |
| **总计** | | **11h** |

### 5.3 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 模型不遵循 Markdown 格式 | 中 | Prompt 约定 + 格式校验 |
| 无进度条 UI | 低 | 解析 Markdown 渲染状态 |
| 用户习惯改变 | 低 | 提供迁移文档 |

---

## 6. 结论

**用户的直觉是正确的**：

1. **混合架构不可靠**：不同模型对 Task Manager 的理解差异大，表现不稳定
2. **交互式暂停价值有限**：本质上与自然语言询问相同，但增加了代码复杂度
3. **纯 Artifact 系统更健壮**：Markdown 是通用协议，所有模型都能理解
4. **工作区绑定是必要的**：防止会话间混淆，支持项目级隔离

**建议**：废弃 Task Manager，全面转向基于 Markdown 的 Artifact 系统，并增加工作区绑定能力。
