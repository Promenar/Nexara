# Artifact 系统完整实施方案

> **Status**: Ready for Implementation
> **Created**: 2026-02-18
> **Branch**: artifact
> **Estimated Time**: 27h (Backend 11h + Frontend 16h)

---

## 1. 项目概述

### 1.1 目标

将任务管理从"代码状态机"转向"文档协议"，实现：
- 跨会话持久化
- RAG 索引支持
- 模型无关性
- 工作区隔离

### 1.2 核心变更

| 变更 | 说明 |
|------|------|
| 废弃 Task Manager | 移除 `TaskManagementSkill` 和相关状态 |
| 新增 Artifact 系统 | 基于 Markdown 的任务追踪 + 工件存储 |
| 新增工作区绑定 | 会话级别的工作区隔离 |
| 重构工具栏 UI | 整合设置面板 + 新增工作区面板 |

---

## 2. 后端实施计划 (11h)

### 2.1 Phase 1: 工件存储 (3h)

#### 新增 Skill: `save_artifact`

**文件**: `src/lib/skills/definitions/artifact.ts`

```typescript
export const SaveArtifactSkill: Skill = {
  id: 'save_artifact',
  name: 'Save Artifact',
  description: `Save a structured artifact to the workspace.
Artifacts are persistent outputs that can be referenced across sessions.
Types: code, data, chart, document, image`,
  schema: z.object({
    type: z.enum(['code', 'data', 'chart', 'document', 'image']),
    name: z.string(),
    content: z.string(),
    description: z.string().optional(),
    tags: z.array(z.string()).optional(),
  }),
  // ...
};
```

#### 新增 Skill: `list_artifacts`

```typescript
export const ListArtifactsSkill: Skill = {
  id: 'list_artifacts',
  name: 'List Artifacts',
  description: 'List all artifacts in the workspace',
  schema: z.object({
    type: z.enum(['code', 'data', 'chart', 'document', 'image']).optional(),
  }),
  // ...
};
```

### 2.2 Phase 2: 任务追踪 (4h)

#### 新增 Skill: `manage_task` (Markdown 版)

**文件**: `src/lib/skills/definitions/task.ts`

```typescript
export const ManageTaskSkill: Skill = {
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
- 'list': List all tasks
- 'archive': Archive a completed task`,
  schema: z.object({
    action: z.enum(['create', 'update', 'complete', 'list', 'archive']),
    taskId: z.string().optional(),
    title: z.string().optional(),
    steps: z.array(z.object({
      title: z.string(),
      status: z.enum(['pending', 'in_progress', 'completed', 'skipped']),
      notes: z.string().optional(),
    })).optional(),
    notes: z.string().optional(),
    artifacts: z.array(z.string()).optional(),
  }),
  // ...
};
```

#### 任务文档格式

```markdown
# Task: [Task Title]

**Status**: active
**Progress**: 40%
**Created**: 2026-02-18 10:30
**Updated**: 2026-02-18 14:15

## Steps

- [x] Step 1: Data Collection
  - Notes: Collected 500 records from API
- [ ] Step 2: Data Processing
  - Status: in_progress
- [ ] Step 3: Generate Report

## Artifacts

- [[data/records.json]] - Raw data
- [[code/processor.js]] - Processing script

## Notes

- API rate limit reached, waiting for reset

## History

- 2026-02-18 10:30: Task created
- 2026-02-18 12:00: Step 1 completed
```

### 2.3 Phase 3: 工作区绑定 (1h)

#### 新增 Skill: `bind_workspace`

```typescript
export const BindWorkspaceSkill: Skill = {
  id: 'bind_workspace',
  name: 'Bind Workspace',
  description: `Bind the current session to a specific workspace directory.
This ensures all file operations and artifacts are scoped to that directory.`,
  schema: z.object({
    path: z.string(),
    createIfNotExists: z.boolean().optional(),
  }),
  // ...
};
```

#### Session 类型扩展

```typescript
interface Session {
  // ... 现有字段
  workspacePath?: string; // 绑定的工作区路径
}
```

### 2.4 Phase 4: 废弃旧 Task Manager (2h)

#### 移除内容

| 文件 | 操作 |
|------|------|
| `src/lib/skills/core/task.ts` | 删除或重写为 Markdown 版本 |
| `src/types/chat.ts` | 移除 `TaskState` 类型（保留简化版） |
| `src/store/chat-store.ts` | 移除 `activeTask` 相关逻辑 |
| `src/features/chat/components/TaskMonitor.tsx` | 改造为任务文档渲染器 |
| `src/features/chat/components/TaskFinalResult.tsx` | 移除 |

#### 移除状态

```typescript
// 从 Session 移除
interface Session {
  // 移除:
  // activeTask?: TaskState;
  // loopStatus: 'idle' | 'running' | 'paused';
  // pendingIntervention?: string;
}
```

### 2.5 Phase 5: System Prompt 更新 (1h)

更新模型提示词，指导使用新的 Artifact 系统：

```
## Task Management

When working on complex multi-step tasks:
1. Use `manage_task` to create a task document
2. Update progress as you complete steps
3. Save outputs using `save_artifact`
4. When you need user input, simply ask in your response

Do NOT use the old Task Manager tool. All task tracking is now document-based.
```

---

## 3. 前端实施计划 (16h)

### 3.1 Phase 1: SessionSettingsSheet (4h)

#### 组件结构

```
src/features/chat/components/
└── SessionSettingsSheet/
    ├── index.tsx              # 主组件
    ├── TabBar.tsx             # Tab 栏
    ├── ModelSelector.tsx      # 模型选择器
    ├── ThinkingLevelSelector.tsx
    ├── ExecutionModePanel.tsx
    └── ToolsPanel.tsx
```

#### 功能

- 整合 4 个设置面板为 Tab 切换
- 复用 GlassBottomSheet
- 保持现有功能不变

### 3.2 Phase 2: WorkspaceSheet (2h)

#### 组件结构

```
src/features/chat/components/
└── WorkspaceSheet/
    ├── index.tsx              # 主组件
    ├── TabBar.tsx             # Tab 栏
    ├── WorkspacePathIndicator.tsx
    └── ActionBar.tsx
```

#### 功能

- 显示当前绑定的工作区路径
- Tab 切换：任务 / 工件 / 文件
- 底部操作栏：绑定工作区 / 新建任务

### 3.3 Phase 3: TaskDocumentViewer (4h)

#### 组件结构

```
src/features/chat/components/
└── TaskDocumentViewer/
    ├── index.tsx              # 主组件
    ├── TaskHeader.tsx         # 头部：标题 + 状态 + 操作
    ├── TaskPreview.tsx        # 预览模式
    ├── TaskEditor.tsx         # 编辑模式
    ├── TaskSteps.tsx          # 步骤列表
    └── TaskArtifacts.tsx      # 工件引用
```

#### 功能

- 解析 Markdown 任务文档
- 只读预览 + 编辑模式切换
- 状态可视化（进度条、步骤图标）
- 工件引用点击跳转

### 3.4 Phase 4: ArtifactList & FileBrowser (3h)

#### 组件结构

```
src/features/chat/components/
└── WorkspaceSheet/
    ├── TaskList.tsx           # 任务列表
    ├── ArtifactList.tsx       # 工件列表
    └── FileBrowser.tsx        # 文件浏览器
```

#### 功能

- 任务列表：显示活跃/已完成任务
- 工件列表：按类型分组显示
- 文件浏览器：目录树 + 文件预览

### 3.5 Phase 5: ChatInput 工具栏重构 (2h)

#### 修改文件

- `src/features/chat/components/ChatInput.tsx`

#### 变更

```typescript
// 移除:
// - ThinkingLevelButton
// - ExecutionModeSelector
// - Token 计数按钮

// 新增:
// - SettingsButton (打开 SessionSettingsSheet)
// - WorkspaceButton (打开 WorkspaceSheet)
```

### 3.6 Phase 6: 工作区绑定 UI (1h)

#### 组件

```typescript
// WorkspacePathIndicator.tsx
const WorkspacePathIndicator: React.FC<{ sessionId: string }> = ({ sessionId }) => {
  const session = useChatStore(s => s.sessions.find(sk => sk.id === sessionId));
  
  return (
    <View style={styles.container}>
      <FolderOpen size={12} />
      <Typography>
        {session?.workspacePath || '默认工作区'}
      </Typography>
      <TouchableOpacity onPress={bindWorkspace}>
        <Typography>绑定</Typography>
      </TouchableOpacity>
    </View>
  );
};
```

---

## 4. 目录结构

### 4.1 工作区目录

```
agent_sandbox/
├── workspace/              # 默认工作区
├── projects/               # 项目目录
│   ├── 数据分析/
│   │   ├── .tasks/
│   │   │   ├── active/
│   │   │   │   └── 销售数据分析.md
│   │   │   └── archive/
│   │   ├── .artifacts/
│   │   │   ├── code/
│   │   │   ├── data/
│   │   │   └── charts/
│   │   └── output/
│   └── 文档撰写/
└── shared/                 # 跨会话共享
```

### 4.2 代码目录

```
src/
├── lib/
│   └── skills/
│       └── definitions/
│           ├── artifact.ts      # 新增
│           ├── task.ts          # 重写
│           ├── workspace.ts     # 新增
│           └── filesystem.ts    # 保留
├── features/
│   └── chat/
│       └── components/
│           ├── SessionSettingsSheet/  # 新增
│           ├── WorkspaceSheet/        # 新增
│           ├── TaskDocumentViewer/    # 新增
│           └── ChatInput.tsx          # 修改
└── types/
    └── chat.ts                # 修改
```

---

## 5. 迁移计划

### 5.1 数据迁移

无需迁移，新系统使用文件存储，与旧系统独立。

### 5.2 用户引导

首次使用时显示引导提示：

```
任务管理系统已升级！

现在任务以 Markdown 文档形式存储，支持：
- 跨会话持久化
- 手动编辑
- RAG 索引

点击工具栏右侧的 [工作区] 按钮查看任务。
```

---

## 6. 测试计划

### 6.1 单元测试

| 测试项 | 说明 |
|--------|------|
| `parseTaskMarkdown` | 解析各种格式的任务文档 |
| `saveArtifact` | 工件保存和索引 |
| `bindWorkspace` | 工作区绑定逻辑 |

### 6.2 集成测试

| 测试项 | 说明 |
|--------|------|
| 任务创建流程 | 创建 → 更新 → 完成 |
| 跨会话恢复 | 关闭应用后重新打开 |
| 工件引用 | 点击工件跳转到详情 |

### 6.3 手动测试

| 测试项 | 说明 |
|--------|------|
| 多模型兼容 | 测试不同模型的任务创建行为 |
| UI 响应性 | 确保不影响聊天体验 |
| 编辑模式 | Markdown 编辑和保存 |

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 模型不遵循 Markdown 格式 | 中 | Prompt 约定 + 格式校验 + 自动修复 |
| 无进度条 UI | 低 | 解析 Markdown 渲染状态 |
| 用户习惯改变 | 中 | 提供迁移引导 |
| 文件监听不生效 | 低 | 轮询作为降级方案 |

---

## 8. 里程碑

| 里程碑 | 内容 | 预计完成 |
|--------|------|----------|
| M1 | 后端 Skills 完成 | Day 2 |
| M2 | 前端 UI 框架完成 | Day 4 |
| M3 | 任务文档渲染器完成 | Day 5 |
| M4 | 工具栏重构完成 | Day 6 |
| M5 | 测试通过，合并 main | Day 7 |

---

## 9. 相关文档

- [任务管理器 vs Artifact 系统分析](./task-manager-vs-artifact-analysis.md)
- [工作区浏览器 UI 设计](./workspace-browser-ui-design.md)
- [工作区与 Artifacts 设计方案](./workspace-artifacts-design.md)
