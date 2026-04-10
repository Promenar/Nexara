# 工作区与 Artifacts 系统设计方案

> **Status**: Draft
> **Created**: 2026-02-18
> **Based on**: 现有文件系统工具 + Artifacts 渲染架构

---

## 1. 架构概述

### 1.1 设计理念

基于现有文件系统工具，构建一个**分层工作区系统**：

```
agent_sandbox/workspace/
├── .artifacts/          # 工件存储（代码、数据、图表）
│   ├── code/            # 代码片段
│   ├── data/            # 数据文件 (JSON, CSV)
│   └── charts/          # 图表配置
├── .tasks/              # 任务追踪（Markdown）
│   ├── active/          # 进行中的任务
│   └── archive/         # 已完成任务
├── .notes/              # 笔记和备忘
├── output/              # 最终输出文件
└── projects/            # 项目目录（用户自定义）
```

### 1.2 与现有系统的关系

| 系统 | 职责 | 保留原因 |
|------|------|----------|
| **Task Manager** | 短期交互式任务 | UI 进度条、ask_user、顺序检查 |
| **Artifacts 渲染** | 结构化输出展示 | ECharts、Mermaid、图片渲染 |
| **工作区系统** | 长期工件存储 | 跨会话持久化、RAG 索引 |

---

## 2. 核心组件设计

### 2.1 工件存储 (Artifacts Storage)

**新增 Skill**: `save_artifact`

```typescript
interface ArtifactMetadata {
  id: string;
  type: 'code' | 'data' | 'chart' | 'document' | 'image';
  name: string;
  path: string;
  createdAt: number;
  updatedAt: number;
  tags?: string[];
  description?: string;
}

export const SaveArtifactSkill: Skill = {
  id: 'save_artifact',
  name: 'Save Artifact',
  description: `Save a structured artifact to the workspace.
Artifacts are persistent outputs that can be referenced across sessions.
Types: code, data, chart, document, image`,
  schema: z.object({
    type: z.enum(['code', 'data', 'chart', 'document', 'image']),
    name: z.string().describe('Artifact name (without extension)'),
    content: z.string().describe('Artifact content'),
    description: z.string().optional(),
    tags: z.array(z.string()).optional(),
  }),
  execute: async (params, context) => {
    // 1. 确定存储路径
    const extMap = {
      code: 'js',
      data: 'json',
      chart: 'json',
      document: 'md',
      image: 'png',
    };
    const ext = extMap[params.type];
    const path = `.artifacts/${params.type}s/${params.name}.${ext}`;
    
    // 2. 调用 write_file
    // 3. 更新工件索引
    // 4. 返回工件 ID 和路径
  },
};
```

### 2.2 任务追踪 (Task Tracking)

**新增 Skill**: `track_task`

```typescript
interface TaskDocument {
  id: string;
  title: string;
  status: 'active' | 'completed' | 'paused' | 'cancelled';
  progress: number;
  steps: TaskStep[];
  createdAt: number;
  updatedAt: number;
  artifacts: string[]; // 关联的工件 ID
  notes: string;
}

export const TrackTaskSkill: Skill = {
  id: 'track_task',
  name: 'Track Task',
  description: `Create or update a long-running task document.
Task documents are stored as Markdown files and can be:
- Persisted across sessions
- Indexed by RAG system
- Manually edited by user`,
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
  execute: async (params, context) => {
    // 1. 创建/更新 Markdown 文件
    // 2. 更新任务索引
    // 3. 返回任务状态
  },
};
```

### 2.3 任务文档格式

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
- Consider caching strategy for next run

## History

- 2026-02-18 10:30: Task created
- 2026-02-18 12:00: Step 1 completed
- 2026-02-18 14:15: Step 2 started
```

---

## 3. 与 Task Manager 的协同

### 3.1 职责划分

| 场景 | 使用工具 | 原因 |
|------|----------|------|
| 单次对话内的简单任务 | Task Manager | 实时 UI 反馈 |
| 需要 ask_user 的交互任务 | Task Manager | 暂停机制 |
| 跨会话的长期项目 | track_task | 持久化 |
| 需要 RAG 检索的任务历史 | track_task | Markdown 可索引 |
| 生成代码/数据工件 | save_artifact | 结构化存储 |

### 3.2 协同工作流

```
用户请求: "帮我分析销售数据并生成报告"

1. Task Manager 创建短期任务（UI 展示）
   - Step 1: 数据收集
   - Step 2: 数据分析
   - Step 3: 生成报告

2. 执行过程中调用 save_artifact
   - save_artifact({ type: 'data', name: 'sales_data', content: ... })
   - save_artifact({ type: 'code', name: 'analysis', content: ... })

3. 如果任务需要跨会话，调用 track_task
   - track_task({ action: 'create', title: '销售数据分析', ... })

4. 任务完成后
   - Task Manager: complete (UI 更新)
   - track_task: archive (持久化历史)
```

---

## 4. 实施建议

### 4.1 Phase 1: 工件存储（低风险）

- 新增 `save_artifact` skill
- 新增 `list_artifacts` skill
- 新增 `load_artifact` skill
- 创建 `.artifacts/` 目录结构

**预计工作量**: 2-3h

### 4.2 Phase 2: 任务追踪（中风险）

- 新增 `track_task` skill
- 设计 Markdown 任务文档格式
- 创建任务索引机制
- 集成 RAG 索引

**预计工作量**: 4-5h

### 4.3 Phase 3: 协同优化（低风险）

- 更新 System Prompt，指导模型选择合适的工具
- 添加任务迁移功能（Task Manager → track_task）
- 添加工件引用语法 `[[artifact/path]]`

**预计工作量**: 2h

---

## 5. 是否保留 Task Manager？

### 结论：**建议保留**

**原因**：

1. **UI 集成深度**：Task Manager 与 ChatBubble 的进度条 UI 紧密耦合
2. **交互式暂停**：`ask_user` 机制是核心功能，无法用 Markdown 替代
3. **顺序检查**：严格的步骤顺序检查防止模型跳步
4. **会话生命周期**：短期任务与会话绑定是合理的设计

**改进方向**：

1. 添加 `track_task` 作为补充，用于长期项目
2. 添加 `export_to_markdown` 功能，将 Task Manager 任务导出为 Markdown
3. 添加 `import_from_markdown` 功能，从 Markdown 恢复任务

---

## 6. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 工件路径冲突 | 中 | 使用时间戳 + UUID 生成唯一路径 |
| Markdown 解析失败 | 低 | 使用标准格式 + 校验 |
| RAG 索引延迟 | 低 | 异步索引 + 手动触发 |
| 存储空间增长 | 中 | 添加清理策略 + 归档机制 |

---

## 7. 下一步行动

1. **确认方案**：是否采用混合架构？
2. **优先级排序**：先实现工件存储还是任务追踪？
3. **UI 设计**：是否需要工件浏览器界面？
