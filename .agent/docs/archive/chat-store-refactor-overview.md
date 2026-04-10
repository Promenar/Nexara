# chat-store解耦重构计划

## 📊 现状分析

### 当前问题
- **文件大小**：3171行代码
- **职责混杂**：
  - 消息管理（CRUD）
  - AgentLoop逻辑
  - 工具执行引擎
  - 审批流程管理
  - RAG集成
  - 会话管理
  - 状态管理
- **维护难度**：单文件过大，难以定位和修改
- **测试困难**：职责耦合，单元测试难以编写
- **合并冲突**：多人协作时容易产生冲突

---

## 🎯 目标架构

### 模块划分方案

```
src/store/
├── chat-store.ts           # 核心状态管理 (~500行)
│   ├── ChatState接口定义
│   ├── 基础状态（sessions, activeRequests等）
│   ├── 基础操作（addSession, updateSession等）
│   └── 组合导出所有功能
│
├── chat/
│   ├── message-manager.ts  # 消息CRUD (~300行)
│   │   ├── addMessage
│   │   ├── updateMessageContent
│   │   ├── deleteMessage
│   │   └── vectorizeMessage
│   │
│   ├── session-manager.ts  # 会话管理 (~200行)
│   │   ├── 会话创建/删除/更新
│   │   ├── 草稿管理
│   │   ├── 滚动位置缓存
│   │   └── 会话统计
│   │
│   ├── agent-loop.ts       # AgentLoop核心 (~800行)
│   │   ├── generateMessage主逻辑
│   │   ├── 消息历史构建
│   │   ├── 流式处理
│   │   ├── 工具检测和调用
│   │   └── 循环控制
│   │
│   ├── tool-execution.ts   # 工具执行引擎 (~400行)
│   │   ├── executeTools
│   │   ├── 步骤更新逻辑
│   │   ├── 结果处理
│   │   └── 错误处理
│   │
│   ├── approval-manager.ts # 审批流程 (~300行)
│   │   ├── setApprovalRequest
│   │   ├── resumeGeneration
│   │   ├── 审批检测逻辑
│   │   └── 介入处理
│   │
│   └── rag-integration.ts  # RAG集成 (~400行)
│       ├── RAG检索逻辑
│       ├── 归档处理
│       ├── KG提取
│       └── 状态同步
│
└── chat-store.ts导出统一接口
```

---

## 🏗️ 重构策略

### 阶段1: 准备工作 ✅ 已完成
- [x] 创建新目录结构
- [x] 定义接口和类型
- [x] 设计模块间通信机制

### 阶段2: 消息管理模块 ✅ 已完成
- [x] 提取message-manager.ts
- [x] 迁移消息CRUD操作
- [x] 更新所有引用
- [x] 测试验证

### 阶段3: 会话管理模块 ✅ 已完成
- [x] 提取session-manager.ts
- [x] 迁移会话操作
- [x] 更新引用
- [x] 测试验证

### 阶段4a: 工具执行模块 ✅ 已完成
- [x] 提取tool-execution.ts
- [x] 完善 executeTools 逻辑
- [x] 处理步骤更新hook
- [x] 测试验证

### 阶段4b: SQLite Session 迁移 ✅ 已完成
> 详见 [chat-store-refactor-phase4b.md](./chat-store-refactor-phase4b.md)

- [x] 设计 sessions/messages 表 Schema
- [x] 创建 SessionRepository 数据访问层
- [x] 改造 SessionManager 使用 SQLite
- [x] 改造 MessageManager 使用 SQLite
- [x] 移除 AsyncStorage persist 中间件
- [x] 恢复 vectorization_tasks FK 约束
- [x] 端到端测试验证

### 阶段5: 审批管理模块 ✅ 已完成
- [x] 提取approval-manager.ts
- [x] 迁移审批相关逻辑
- [x] 处理状态同步
- [x] 测试验证

### 阶段6: AgentLoop核心 🔄 进行中 (部分迁移)
- [x] 扩展 AgentLoopManager 接口定义
- [x] 提取辅助函数 (formatContent, virtualSplit)
- [ ] 迁移 generateMessage 核心逻辑 (~2000行，决定保留在主文件以维持高性能，逐步拆分逻辑至 hooks)
- [x] 迁移 regenerateMessage 逻辑
- [x] 依赖注入重构 (使用 ManagerContext)
- [x] 端到端测试

### 阶段7: 清理和优化 ✅ 已完成
- [x] 删除冗余占位代码 (streaming-handler.ts, agent-loop.ts)
- [x] 添加JSDoc注释
- [x] 性能优化 (Inverted List, SSE Parser)
- [x] 代码审查 (架构红线确立)

---

## 🔑 关键原则

### 1. 向后兼容
- 保持外部API不变
- `useChatStore`钩子接口不变
- 渐进式迁移，逐步弃用旧API

### 2. 单一职责
- 每个模块只负责一个明确的功能域
- 模块间通过定义良好的接口通信
- 避免循环依赖

### 3. 类型安全
- 为每个模块定义明确的TypeScript接口
- 使用泛型提升复用性
- 严格的空值检查

### 4. 可测试性
- 每个模块独立可测试
- 使用依赖注入便于mock
- 编写单元测试覆盖核心逻辑

---

## 📋 模块接口设计示例

### message-manager.ts
```typescript
export interface MessageManager {
  addMessage: (sessionId: string, message: Message) => void;
  updateMessage: (sessionId: string, messageId: string, updates: Partial<Message>) => void;
  deleteMessage: (sessionId: string, messageId: string) => void;
  getMessage: (sessionId: string, messageId: string) => Message | undefined;
}

export const createMessageManager = (get: () => ChatState, set: SetState<ChatState>): MessageManager => ({
  addMessage: (sessionId, message) => {
    // 实现
  },
  // ...
});
```

### agent-loop.ts
```typescript
export interface AgentLoopManager {
  generateMessage: (sessionId: string, content: string, options?: GenerateOptions) => Promise<void>;
  regenerateMessage: (sessionId: string, messageId: string) => Promise<void>;
}

export const createAgentLoopManager = (
  get: () => ChatState,
  set: SetState<ChatState>,
  messageManager: MessageManager,
  toolExecutor: ToolExecutor
): AgentLoopManager => ({
  // 实现
});
```

---

## ✅ 验收标准

### 功能验证
- [ ] 所有现有功能正常工作
- [ ] 消息发送/接收正常
- [ ] 工具调用执行正常
- [ ] 审批流程正常
- [ ] RAG检索正常

### 代码质量
- [ ] 每个模块< 500行
- [ ] TypeScript编译无错误
- [ ] Lint检查通过
- [ ] 单元测试覆盖率> 60%

### 性能
- [ ] 启动时间无明显增加
- [ ] 消息发送延迟< 100ms
- [ ] 内存占用无明显增长

---

## 🚀 下一步行动

1. **审查此计划**并确认模块划分合理
2. **创建feature分支**：`feature/chat-store-refactor`
3. **按阶段执行**，每个阶段提交一次
4. **增量测试**，确保每步都可回退
5. **文档更新**，记录新的架构设计

---

## 📌 注意事项

- **不要一次性重构**：渐进式重构，保持系统可用
- **频繁提交**：每个模块迁移后立即提交
- **保留旧代码**：在确认新代码完全正常前不删除旧实现
- **通知团队**：重大重构需要团队知晓，避免冲突
