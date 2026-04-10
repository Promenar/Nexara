# 工作区资源管理器 UI 设计方案

> **Status**: Design Proposal
> **Created**: 2026-02-18
> **Goal**: 为 Artifact 系统提供优雅、轻量、符合整体视觉风格的 UI 组件

---

## 1. 现有 UI 结构分析

### 1.1 当前工具栏布局

```
┌─────────────────────────────────────────────────────────────┐
│ [模型名称] [Token计数] [思考等级] [执行模式]     [空白区域] │
│  (Cpu)      (Calculator)  (Zap)      (Shield)               │
└─────────────────────────────────────────────────────────────┘
```

**问题**：
- 4 个独立按钮占用大量空间
- 功能分散，用户需要在多个面板间切换
- 缺少工作区/Artifact 访问入口

### 1.2 现有组件复用潜力

| 组件 | 可复用程度 | 说明 |
|------|------------|------|
| GlassBottomSheet | ✅ 高 | 已有完善的动画和样式 |
| TaskMonitor | ✅ 高 | 可改造为任务文档渲染器 |
| ExecutionModeSelector | ✅ 中 | Tab 切换逻辑可复用 |
| Markdown 渲染器 | ✅ 高 | 已集成 react-native-markdown-display |

---

## 2. 设计方案

### 2.1 方案概述：统一设置面板 + 扩展功能面板

```
┌─────────────────────────────────────────────────────────────┐
│ [模型选择器]                                      [工作区] │
│  (Cpu + 下拉指示)                                  (Folder) │
└─────────────────────────────────────────────────────────────┘
```

**核心思想**：
1. **设置面板整合**：将 4 个设置按钮合并为 1 个，使用 Tab 切换
2. **扩展面板新增**：新增工作区入口，使用 GlassBottomSheet + Tab

### 2.2 设置面板重构

#### 视觉设计

```
┌─────────────────────────────────────────────────────────────┐
│                      会话设置                                │
│  ┌────────┬────────┬────────┬────────┐                      │
│  │ 模型   │ 思考   │ 执行   │ 工具   │                      │
│  │ (Cpu)  │ (Brain)│ (Zap)  │ (Wrench)│                     │
│  └────────┴────────┴────────┴────────┘                      │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                                                         ││
│  │              [当前选中 Tab 的内容]                       ││
│  │                                                         ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

#### Tab 内容

| Tab | 图标 | 内容 |
|-----|------|------|
| 模型 | Cpu | 模型选择器 + 当前模型信息 |
| 思考 | Brain | 思考等级切换 (Gemini 专用) |
| 执行 | Zap | 执行模式 + MCP/Skill 开关 |
| 工具 | Wrench | Token 统计 + 会话工具箱 |

### 2.3 扩展功能面板设计

#### 视觉设计

```
┌─────────────────────────────────────────────────────────────┐
│                      工作区                                 │
│  ┌────────┬────────┬────────┐                              │
│  │ 任务   │ 工件   │ 文件   │                              │
│  │ (List) │ (Box)  │ (File) │                              │
│  └────────┴────────┴────────┘                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                                                         ││
│  │              [当前选中 Tab 的内容]                       ││
│  │                                                         ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  [绑定工作区] [新建任务] [导入文件]                          │
└─────────────────────────────────────────────────────────────┘
```

#### Tab 内容

| Tab | 图标 | 内容 |
|-----|------|------|
| 任务 | ListCheck | 任务文档列表 + 状态预览 |
| 工件 | Box | 代码/数据/图表工件列表 |
| 文件 | FileText | 工作区文件浏览器 |

---

## 3. 任务文档渲染器设计

### 3.1 核心需求

| 需求 | 说明 |
|------|------|
| 自动预览 | 模型创建任务文档后自动显示预览 |
| 只读模式 | 默认只读，防止误操作 |
| 编辑模式 | 支持切换到编辑模式 |
| 状态解析 | 解析 Markdown 中的任务状态 |
| 轻量高效 | 不影响聊天交互体验 |

### 3.2 组件架构

```typescript
// TaskDocumentViewer.tsx
interface TaskDocumentViewerProps {
  path: string;              // 任务文档路径
  autoPreview?: boolean;     // 自动预览模式
  onStatusChange?: (status: TaskStatus) => void;
}

const TaskDocumentViewer: React.FC<TaskDocumentViewerProps> = ({
  path,
  autoPreview = true,
  onStatusChange,
}) => {
  const [mode, setMode] = useState<'preview' | 'edit'>('preview');
  const [content, setContent] = useState('');
  const [parsed, setParsed] = useState<ParsedTask | null>(null);
  
  // 加载文档
  useEffect(() => {
    loadDocument(path).then(({ content, parsed }) => {
      setContent(content);
      setParsed(parsed);
      onStatusChange?.(parsed.status);
    });
  }, [path]);
  
  return (
    <View style={styles.container}>
      {/* 头部：标题 + 状态 + 操作按钮 */}
      <TaskHeader 
        title={parsed?.title}
        status={parsed?.status}
        progress={parsed?.progress}
        onEdit={() => setMode('edit')}
        onSave={handleSave}
      />
      
      {/* 内容区 */}
      {mode === 'preview' ? (
        <TaskPreview parsed={parsed} />
      ) : (
        <TaskEditor content={content} onChange={setContent} />
      )}
    </View>
  );
};
```

### 3.3 任务文档解析器

```typescript
// task-document-parser.ts
interface ParsedTask {
  title: string;
  status: 'active' | 'completed' | 'paused' | 'cancelled';
  progress: number;
  steps: ParsedStep[];
  artifacts: ArtifactReference[];
  notes: string;
  history: HistoryEntry[];
}

interface ParsedStep {
  id: string;
  title: string;
  status: 'pending' | 'in_progress' | 'completed' | 'skipped';
  notes?: string;
}

const parseTaskMarkdown = (content: string): ParsedTask => {
  const lines = content.split('\n');
  const task: ParsedTask = {
    title: '',
    status: 'active',
    progress: 0,
    steps: [],
    artifacts: [],
    notes: '',
    history: [],
  };
  
  // 解析逻辑...
  // 1. 提取标题: # Task: [Title]
  // 2. 提取状态: **Status**: active
  // 3. 提取进度: **Progress**: 40%
  // 4. 解析步骤: - [x] 或 - [ ]
  // 5. 解析工件: [[artifact/path]]
  
  return task;
};
```

### 3.4 预览模式 UI

```
┌─────────────────────────────────────────────────────────────┐
│ 📋 数据分析报告生成                              [编辑]    │
│ 状态: 进行中  进度: 60%                                     │
├─────────────────────────────────────────────────────────────┤
│ 步骤                                                        │
│ ┌─────────────────────────────────────────────────────────┐│
│ │ ✅ Step 1: 数据收集                                      ││
│ │    已收集 500 条记录                                     ││
│ │ 🔄 Step 2: 数据清洗                                      ││
│ │    正在处理...                                           ││
│ │ ⬜ Step 3: 生成报告                                      ││
│ └─────────────────────────────────────────────────────────┘│
│                                                             │
│ 工件                                                        │
│ ┌──────────────────┐ ┌──────────────────┐                  │
│ │ 📊 records.json  │ │ 📝 processor.js  │                  │
│ │ 2.3 KB           │ │ 1.1 KB           │                  │
│ └──────────────────┘ └──────────────────┘                  │
│                                                             │
│ 备注                                                        │
│ API 限流已触发，等待重置...                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. 工具栏重构方案

### 4.1 新工具栏布局

```typescript
// ChatInput.tsx 修改
const ChatInput = ({ ... }) => {
  const [settingsVisible, setSettingsVisible] = useState(false);
  const [workspaceVisible, setWorkspaceVisible] = useState(false);
  
  return (
    <View>
      {/* 顶部工具栏 */}
      <View style={styles.topBar}>
        {/* 左侧：模型选择器入口 */}
        <TouchableOpacity onPress={() => setSettingsVisible(true)}>
          <View style={styles.modelButton}>
            <Cpu size={12} color={agentColor} />
            <Typography>{currentModel}</Typography>
            <ChevronDown size={10} />
          </View>
        </TouchableOpacity>
        
        <View style={{ flex: 1 }} />
        
        {/* 右侧：工作区入口 */}
        <TouchableOpacity onPress={() => setWorkspaceVisible(true)}>
          <View style={styles.workspaceButton}>
            <FolderOpen size={14} color={isDark ? '#a1a1aa' : '#6b7280'} />
            {hasActiveTask && (
              <View style={styles.activeIndicator} />
            )}
          </View>
        </TouchableOpacity>
      </View>
      
      {/* 设置面板 */}
      <SessionSettingsSheet 
        visible={settingsVisible}
        onClose={() => setSettingsVisible(false)}
        sessionId={sessionId}
      />
      
      {/* 工作区面板 */}
      <WorkspaceSheet
        visible={workspaceVisible}
        onClose={() => setWorkspaceVisible(false)}
        sessionId={sessionId}
      />
    </View>
  );
};
```

### 4.2 设置面板组件

```typescript
// SessionSettingsSheet.tsx
const TABS = [
  { id: 'model', label: '模型', icon: Cpu },
  { id: 'thinking', label: '思考', icon: Brain },
  { id: 'execution', label: '执行', icon: Zap },
  { id: 'tools', label: '工具', icon: Wrench },
];

const SessionSettingsSheet: React.FC<Props> = ({ visible, onClose, sessionId }) => {
  const [activeTab, setActiveTab] = useState('model');
  
  return (
    <GlassBottomSheet visible={visible} onClose={onClose} title="会话设置">
      {/* Tab 栏 */}
      <View style={styles.tabBar}>
        {TABS.map(tab => (
          <TouchableOpacity
            key={tab.id}
            onPress={() => setActiveTab(tab.id)}
            style={[styles.tab, activeTab === tab.id && styles.tabActive]}
          >
            <tab.icon size={16} />
            <Typography>{tab.label}</Typography>
          </TouchableOpacity>
        ))}
      </View>
      
      {/* Tab 内容 */}
      <View style={styles.tabContent}>
        {activeTab === 'model' && <ModelSelector sessionId={sessionId} />}
        {activeTab === 'thinking' && <ThinkingLevelSelector sessionId={sessionId} />}
        {activeTab === 'execution' && <ExecutionModeSelector sessionId={sessionId} />}
        {activeTab === 'tools' && <ToolsPanel sessionId={sessionId} />}
      </View>
    </GlassBottomSheet>
  );
};
```

### 4.3 工作区面板组件

```typescript
// WorkspaceSheet.tsx
const TABS = [
  { id: 'tasks', label: '任务', icon: ListCheck },
  { id: 'artifacts', label: '工件', icon: Box },
  { id: 'files', label: '文件', icon: FileText },
];

const WorkspaceSheet: React.FC<Props> = ({ visible, onClose, sessionId }) => {
  const [activeTab, setActiveTab] = useState('tasks');
  const [selectedTask, setSelectedTask] = useState<string | null>(null);
  
  return (
    <GlassBottomSheet 
      visible={visible} 
      onClose={onClose} 
      title="工作区"
      height="85%"
    >
      {/* 工作区路径指示器 */}
      <WorkspacePathIndicator sessionId={sessionId} />
      
      {/* Tab 栏 */}
      <View style={styles.tabBar}>
        {TABS.map(tab => (
          <TouchableOpacity
            key={tab.id}
            onPress={() => setActiveTab(tab.id)}
            style={[styles.tab, activeTab === tab.id && styles.tabActive]}
          >
            <tab.icon size={16} />
            <Typography>{tab.label}</Typography>
          </TouchableOpacity>
        ))}
      </View>
      
      {/* Tab 内容 */}
      <View style={styles.tabContent}>
        {activeTab === 'tasks' && (
          selectedTask ? (
            <TaskDocumentViewer 
              path={selectedTask} 
              onBack={() => setSelectedTask(null)}
            />
          ) : (
            <TaskList onSelect={setSelectedTask} />
          )
        )}
        {activeTab === 'artifacts' && <ArtifactList />}
        {activeTab === 'files' && <FileBrowser />}
      </View>
      
      {/* 底部操作栏 */}
      <View style={styles.actionBar}>
        <TouchableOpacity onPress={bindWorkspace}>
          <Typography>绑定工作区</Typography>
        </TouchableOpacity>
        <TouchableOpacity onPress={createTask}>
          <Typography>新建任务</Typography>
        </TouchableOpacity>
      </View>
    </GlassBottomSheet>
  );
};
```

---

## 5. 交互流程设计

### 5.1 任务创建流程

```
用户: "帮我分析销售数据"
    ↓
模型: 调用 manage_task({ action: 'create', title: '销售数据分析', ... })
    ↓
系统: 创建 .tasks/active/销售数据分析.md
    ↓
UI: 自动弹出工作区面板，显示任务预览
    ↓
模型: 执行步骤，更新任务文档
    ↓
UI: 实时更新任务状态（轮询或文件监听）
```

### 5.2 任务查看流程

```
用户: 点击工具栏 [工作区] 按钮
    ↓
UI: 弹出工作区面板，默认显示任务列表
    ↓
用户: 点击某个任务
    ↓
UI: 显示任务文档预览（只读模式）
    ↓
用户: 点击 [编辑] 按钮
    ↓
UI: 切换到编辑模式，支持修改 Markdown
```

### 5.3 跨会话恢复流程

```
用户: 打开历史会话
    ↓
系统: 检测 session.workspacePath
    ↓
UI: 工作区按钮显示活跃状态指示器
    ↓
用户: 点击工作区按钮
    ↓
UI: 显示该会话绑定的任务和工件
```

---

## 6. 性能优化策略

### 6.1 懒加载

```typescript
// 任务列表只加载元数据
const TaskList = () => {
  const [tasks, setTasks] = useState<TaskMetadata[]>([]);
  
  useEffect(() => {
    // 只读取文件头部信息，不加载完整内容
    loadTaskMetadata().then(setTasks);
  }, []);
  
  return (
    <FlatList
      data={tasks}
      renderItem={({ item }) => <TaskCard metadata={item} />}
    />
  );
};
```

### 6.2 缓存策略

```typescript
// 文档内容缓存
const documentCache = new Map<string, { content: string; timestamp: number }>();

const loadDocument = async (path: string) => {
  const cached = documentCache.get(path);
  if (cached && Date.now() - cached.timestamp < 5000) {
    return cached.content;
  }
  
  const content = await FileSystem.readAsStringAsync(path);
  documentCache.set(path, { content, timestamp: Date.now() });
  return content;
};
```

### 6.3 增量更新

```typescript
// 文件监听（可选）
useEffect(() => {
  if (!selectedTask) return;
  
  const subscription = FileSystem.watchDirectoryAsync(
    getTaskPath(selectedTask),
    () => {
      // 文件变化时重新加载
      loadDocument(selectedTask);
    }
  );
  
  return () => subscription?.remove();
}, [selectedTask]);
```

---

## 7. 视觉风格一致性

### 7.1 颜色方案

| 元素 | Light Mode | Dark Mode |
|------|------------|-----------|
| 背景 | `rgba(255,255,255,0.8)` | `rgba(0,0,0,0.6)` |
| 边框 | `rgba(0,0,0,0.08)` | `rgba(255,255,255,0.12)` |
| 活跃状态 | `#6366f1` (indigo-500) | `#818cf8` (indigo-400) |
| 完成状态 | `#22c55e` (green-500) | `#4ade80` (green-400) |
| 进行中 | `#6366f1` | `#818cf8` |
| 暂停 | `#eab308` (amber-500) | `#fbbf24` (amber-400) |

### 7.2 动画规范

| 动画类型 | 时长 | 缓动函数 |
|----------|------|----------|
| 面板弹出 | 280ms | `Easing.out(Easing.quad)` |
| 面板关闭 | 200ms | `Easing.in(Easing.quad)` |
| Tab 切换 | 200ms | `withSpring` |
| 状态更新 | 150ms | `withTiming` |

---

## 8. 实施计划

| Phase | 内容 | 工作量 |
|-------|------|--------|
| 1 | 创建 SessionSettingsSheet（整合 4 个设置面板） | 4h |
| 2 | 创建 WorkspaceSheet（基础框架） | 2h |
| 3 | 创建 TaskDocumentViewer（预览 + 编辑） | 4h |
| 4 | 创建 ArtifactList 和 FileBrowser | 3h |
| 5 | 重构 ChatInput 工具栏 | 2h |
| 6 | 添加工作区绑定功能 | 1h |
| **总计** | | **16h** |

---

## 9. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 用户习惯改变 | 中 | 提供迁移引导，保持快捷操作 |
| 性能影响 | 低 | 懒加载 + 缓存策略 |
| Markdown 解析失败 | 低 | 格式校验 + 自动修复 |
| 文件监听不生效 | 低 | 轮询作为降级方案 |

---

## 10. 总结

**核心设计理念**：

1. **整合而非堆砌**：将分散的设置面板整合为统一的 Tab 界面
2. **复用现有组件**：GlassBottomSheet、TaskMonitor 等可复用
3. **渐进式展示**：列表 → 预览 → 编辑，按需加载
4. **保持轻量**：懒加载 + 缓存，不影响聊天体验

**UI 可读性评估**：

| 方面 | 评分 | 说明 |
|------|------|------|
| 信息密度 | ⭐⭐⭐⭐ | Tab 分类清晰，避免拥挤 |
| 操作效率 | ⭐⭐⭐⭐⭐ | 2 次点击即可访问任何功能 |
| 视觉一致性 | ⭐⭐⭐⭐⭐ | 复用 Glass 风格，无缝融合 |
| 学习成本 | ⭐⭐⭐⭐ | 图标 + 标签，直观易懂 |
