# chat-store解耦重构 Phase 4b: SQLite Session 迁移

> 背景: 合并 Session SQLite 迁移与解耦计划，一次性完成架构升级

---

## 🎯 目标

将 Sessions 和 Messages 从 AsyncStorage 迁移到 SQLite，实现：
1. 数据完整性（FK 约束生效）
2. 高效查询（分页加载、全文搜索）
3. 性能优化（避免全量序列化）

---

## 📊 Schema 设计

### sessions 表
```sql
CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY NOT NULL,
  agent_id TEXT NOT NULL,
  title TEXT NOT NULL,
  last_message TEXT,
  model_id TEXT,
  custom_prompt TEXT,
  is_pinned INTEGER DEFAULT 0,
  scroll_offset REAL,
  draft TEXT,
  execution_mode TEXT DEFAULT 'auto',
  loop_status TEXT DEFAULT 'idle',
  rag_options TEXT, -- JSON
  inference_params TEXT, -- JSON
  active_task TEXT, -- JSON
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

### messages 表
```sql
CREATE TABLE IF NOT EXISTS messages (
  id TEXT PRIMARY KEY NOT NULL,
  session_id TEXT NOT NULL,
  role TEXT NOT NULL, -- 'user' | 'assistant' | 'system' | 'tool'
  content TEXT NOT NULL,
  model_id TEXT,
  status TEXT,
  reasoning TEXT,
  thought_signature TEXT,
  images TEXT, -- JSON array
  tokens TEXT, -- JSON
  citations TEXT, -- JSON
  rag_references TEXT, -- JSON
  rag_metadata TEXT, -- JSON
  execution_steps TEXT, -- JSON
  tool_calls TEXT, -- JSON
  tool_call_id TEXT,
  name TEXT,
  is_archived INTEGER DEFAULT 0,
  vectorization_status TEXT,
  layout_height REAL,
  created_at INTEGER NOT NULL,
  FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id);
CREATE INDEX IF NOT EXISTS idx_messages_created ON messages(session_id, created_at);
```

---

## 🏗️ 实施步骤

### 步骤 1: 添加 Schema (15分钟)

在 `schema.ts` 中添加 `sessions` 和 `messages` 表定义。

### 步骤 2: 创建 SessionRepository (1小时)

创建 `src/lib/db/session-repository.ts`:

```typescript
export class SessionRepository {
  // Session CRUD
  static async create(session: Session): Promise<void>
  static async getById(id: string): Promise<Session | null>
  static async getAll(): Promise<Session[]>
  static async update(id: string, updates: Partial<Session>): Promise<void>
  static async delete(id: string): Promise<void>
  
  // Message CRUD
  static async addMessage(sessionId: string, message: Message): Promise<void>
  static async updateMessage(sessionId: string, msgId: string, updates: Partial<Message>): Promise<void>
  static async deleteMessage(sessionId: string, msgId: string): Promise<void>
  static async getMessages(sessionId: string, limit?: number, offset?: number): Promise<Message[]>
}
```

### 步骤 3: 改造 SessionManager (1小时)

修改 `src/store/chat/session-manager.ts`:

```typescript
export const createSessionManager = (context: ManagerContext): SessionManager => {
  const { get, set } = context;

  return {
    addSession: async (session) => {
      // 1. 写入 SQLite
      await SessionRepository.create(session);
      // 2. 更新 Zustand 缓存
      set(state => ({ sessions: [...state.sessions, session] }));
    },
    
    deleteSession: async (sessionId) => {
      // 1. 从 SQLite 删除（CASCADE 会自动删除 messages）
      await SessionRepository.delete(sessionId);
      // 2. 更新 Zustand 缓存
      set(state => ({ 
        sessions: state.sessions.filter(s => s.id !== sessionId)
      }));
    },
    // ... 其他方法
  };
};
```

### 步骤 4: 改造 MessageManager (1小时)

修改 `src/store/chat/message-manager.ts`:

```typescript
export const createMessageManager = (context: ManagerContext): MessageManager => {
  return {
    addMessage: async (sessionId, message) => {
      // 1. 写入 SQLite
      await SessionRepository.addMessage(sessionId, message);
      // 2. 更新 Zustand 缓存
      set(state => ({
        sessions: state.sessions.map(s => 
          s.id === sessionId 
            ? { ...s, messages: [...s.messages, message] } 
            : s
        )
      }));
    },
    
    updateMessageContent: async (sessionId, msgId, content, usage, reasoning, ...) => {
      // 🔑 流式更新优化：防抖写入 SQLite
      debouncedDbUpdate(sessionId, msgId, { content, ... });
      
      // 立即更新 Zustand（保持 UI 响应性）
      set(state => ({
        sessions: state.sessions.map(s => ...)
      }));
    },
    // ...
  };
};
```

### 步骤 5: 修改 chat-store 初始化 (30分钟)

```typescript
// chat-store.ts
export const useChatStore = create<ChatState>()(
  // 移除 persist 中间件（不再需要 AsyncStorage）
  (set, get): ChatState => {
    const messageManager = createMessageManager({ get, set });
    const sessionManager = createSessionManager({ get, set });
    
    return {
      sessions: [], // 初始为空，由 loadSessions 填充
      
      // 🔑 App 启动时调用
      loadSessions: async () => {
        const sessions = await SessionRepository.getAll();
        set({ sessions });
      },
      
      // ... 其他方法
    };
  }
);
```

### 步骤 6: App 启动加载 (15分钟)

修改 `app/_layout.tsx`:

```typescript
useEffect(() => {
  const setup = async () => {
    await createTables(); // 创建 sessions/messages 表
    await useChatStore.getState().loadSessions(); // 从 DB 加载
  };
  setup();
}, []);
```

### 步骤 7: 修复 FK 约束 (5分钟)

恢复 `vectorization_tasks` 的 session_id FK 约束：
```sql
FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
```

---

## ⚡ 流式更新优化

```typescript
// 防抖 DB 写入（流式回复时）
const pendingUpdates = new Map<string, Partial<Message>>();
const DEBOUNCE_MS = 500;

function debouncedDbUpdate(sessionId: string, msgId: string, updates: Partial<Message>) {
  const key = `${sessionId}:${msgId}`;
  const existing = pendingUpdates.get(key) || {};
  pendingUpdates.set(key, { ...existing, ...updates });
  
  // 防抖执行
  debounce(() => {
    const finalUpdates = pendingUpdates.get(key);
    if (finalUpdates) {
      SessionRepository.updateMessage(sessionId, msgId, finalUpdates);
      pendingUpdates.delete(key);
    }
  }, DEBOUNCE_MS)();
}
```

---

## ✅ 验收标准

### 功能验证
- [ ] 消息发送/接收正常
- [ ] 会话创建/删除正常
- [ ] App 重启后数据保留
- [ ] 删除会话时消息自动删除（CASCADE）
- [ ] vectorization_tasks FK 约束生效

### 性能验证
- [ ] 流式回复无卡顿
- [ ] 大量消息（100+）时滚动流畅
- [ ] App 启动时间无明显增加

---

## 📊 预计时间

| 步骤 | 时间 |
|------|------|
| Schema 设计 | 15分钟 |
| SessionRepository | 1小时 |
| SessionManager 改造 | 1小时 |
| MessageManager 改造 | 1小时 |
| chat-store 初始化 | 30分钟 |
| App 启动加载 | 15分钟 |
| FK 恢复 | 5分钟 |
| 测试验证 | 30分钟 |
| **总计** | **约 4-5 小时** |

---

## ⚠️ 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 双写遗漏 | 统一通过 Repository 操作 |
| 响应式丢失 | 所有写操作同步更新 Zustand |
| 流式性能 | 防抖 DB 写入 |
| 数据丢失 | 不保留旧数据，全新开始 |

---

## 📝 下一步

批准后开始实施，按步骤逐一完成并测试。
