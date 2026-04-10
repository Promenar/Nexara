# chat-store模块化重构 Phase 2 实施指南

## 📋 Phase 1 回顾

### 已完成工作
- ✅ 创建6个模块文件（types, message, session, approval, tool, agent-loop）
- ✅ 完整实现MessageManager、SessionManager、ApprovalManager
- ✅ 创建ToolExecutor和AgentLoopManager包装器
- ✅ 定义所有接口和类型

### 当前状态
模块已创建但**尚未集成到chat-store**。chat-store仍使用原有实现。

---

## 🎯 Phase 2 目标

将已创建的模块**真正集成**到chat-store中，实现模块化架构。

---

## 📝 实施步骤

### 步骤1: 集成MessageManager（1小时）

#### 1.1 初始化管理器
```typescript
// chat-store.ts L175左右
export const useChatStore = create<ChatState>()(
  persist(
    (set, get): ChatState => {
      // 初始化模块管理器
      const context = { get, set };
      const messageManager = createMessageManager(context);
      
      return {
        sessions: [],
        // ...
```

#### 1.2 替换addMessage
```typescript
// 原有实现（L202-210）
addMessage: (sessionId, message) => {
  set((state) => ({
    sessions: state.sessions.map((s) =>
      s.id === sessionId
        ? { ...s, messages: [...s.messages, message] }
        : s
    ),
  }));
},

// 替换为
addMessage: (sessionId, message) => {
  messageManager.addMessage(sessionId, message);
},
```

#### 1.3 替换其他消息方法
- updateMessageContent → messageManager.updateMessageContent
- deleteMessage → messageManager.deleteMessage
- vectorizeMessage → messageManager.vectorizeMessage
- updateMessageProgress → messageManager.updateMessageProgress
- updateMessageLayout → messageManager.updateMessageLayout

#### 1.4 测试验证
```bash
# 测试消息发送
# 测试消息更新
# 测试消息删除
```

### 步骤2: 集成SessionManager（0.5小时）

#### 2.1 替换会话方法
- addSession → sessionManager.addSession
- updateSession → sessionManager.updateSession
- deleteSession → sessionManager.deleteSession
- getSession → sessionManager.getSession
- updateSessionDraft → sessionManager.updateSessionDraft
- toggleSessionPin → sessionManager.toggleSessionPin

#### 2.2 测试验证
```bash
# 测试会话创建
# 测试会话更新
# 测试会话删除
```

### 步骤3: 集成ApprovalManager（0.5小时）

#### 3.1 替换审批方法
- setApprovalRequest → approvalManager.setApprovalRequest
- resumeGeneration → approvalManager.resumeGeneration
- setExecutionMode → approvalManager.setExecutionMode
- setLoopStatus → approvalManager.setLoopStatus
- setPendingIntervention → approvalManager.setPendingIntervention

#### 3.2 测试验证
```bash
# 测试Semi模式审批流程
# 测试Manual模式审批流程
# 测试intervention注入
```

### 步骤4: 提取executeTools核心逻辑（1小时）

#### 4.1 当前状态
tool-execution.ts目前只是包装器：
```typescript
executeTools: async (sessionId, toolCalls, targetMessageId) => {
  await get().executeTools(sessionId, toolCalls, targetMessageId);
},
```

#### 4.2 迁移方案
将chat-store.ts L508-655的executeTools实现迁移到tool-execution.ts：

```typescript
// tool-execution.ts
export const createToolExecutor = (context: ManagerContext): ToolExecutor => {
  const { get, set } = context;

  return {
    executeTools: async (sessionId, toolCalls, targetMessageId) => {
      // 迁移L508-655的完整逻辑
      const state = get();
      const session = state.getSession(sessionId);
      // ... 完整实现
    },
  };
};
```

#### 4.3 更新chat-store
```typescript
// chat-store.ts
executeTools: async (sessionId, toolCalls, targetMessageId) => {
  await toolExecutor.executeTools(sessionId, toolCalls, targetMessageId);
},
```

### 步骤5: 提取generateMessage核心逻辑（1-2小时）

#### 5.1 当前状态
agent-loop.ts目前只是包装器

#### 5.2 迁移方案（可选）
这是**最复杂的部分**（2000+行），建议：
- **短期**：保持包装器模式
- **中期**：逐步提取工具检测、streaming等子功能
- **长期**：完全迁移generateMessage到agent-loop.ts

### 步骤6: 清理和优化（0.5小时）

#### 6.1 删除冗余代码
在chat-store中删除被模块替换的实现

#### 6.2 添加文档注释
```typescript
/**
 * 消息管理
 * @deprecated 直接使用messageManager代替
 */
```

#### 6.3 验证TypeScript
```bash
npx tsc --noEmit
```

---

## ✅ 验收标准

### 功能验证
- [ ] 消息发送/接收正常
- [ ] 会话创建/删除正常
- [ ] 工具调用执行正常
- [ ] 审批流程正常
- [ ] RAG检索正常

### 代码质量
- [ ] TypeScript无错误
- [ ] Lint检查通过
- [ ] 模块导入正确

### 性能
- [ ] 无明显性能下降
- [ ] 内存占用正常

---

## ⚠️ 注意事项

### 1. 循环依赖风险
模块可能依赖chat-store的其他功能，注意：
- 通过context传递依赖
- 避免直接import chat-store

### 2. 状态同步
确保模块中的set调用正确更新状态：
```typescript
set((state) => ({
  sessions: state.sessions.map(...)
}));
```

### 3. 向后兼容
保持外部API不变：
```typescript
// 组件中仍然这样使用
const { addMessage } = useChatStore();
addMessage(sessionId, message);
```

---

## 🚀 快速开始

### 最小可行方案（30分钟）
只集成MessageManager和SessionManager：

```typescript
// chat-store.ts
import { createMessageManager } from './chat/message-manager';
import { createSessionManager } from './chat/session-manager';

export const useChatStore = create<ChatState>()(
  persist((set, get): ChatState => {
    const messageManager = createMessageManager({ get, set });
    const sessionManager = createSessionManager({ get, set });

    return {
      addMessage: messageManager.addMessage,
      updateMessageContent: messageManager.updateMessageContent,
      addSession: sessionManager.addSession,
      updateSession: sessionManager.updateSession,
      // ... 其他方法保持不变
    };
  })
);
```

测试验证后提交，其他模块后续渐进集成。

---

## 📊 预计时间分配

| 步骤 | 预计时间 | 优先级 |
|------|---------|--------|
| 集成MessageManager | 1小时 | P0 |
| 集成SessionManager | 0.5小时 | P0 |
| 集成ApprovalManager | 0.5小时 | P1 |
| 提取executeTools | 1小时 | P1 |
| 提取generateMessage | 可选 | P2 |
| 清理优化 | 0.5小时 | P1 |

**总计**: 2-4小时（取决于是否提取generateMessage）

---

## 📝 下次开始时

1. 查看此文档："Phase 2实施指南"
2. 从步骤1开始：集成MessageManager
3. 每完成一个步骤提交一次代码
4. 遇到问题参考"注意事项"部分
