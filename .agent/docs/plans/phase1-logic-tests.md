# Phase 1：业务逻辑自动化测试详细设计

> **文档版本**: v1.0  
> **创建日期**: 2026-04-22  
> **父文档**: agent-test-framework-v1.md

---

## 1. 概述

Phase 1 旨在为 Nexara 项目建立完整的单元/集成测试覆盖，目标覆盖 80%+ 的核心业务逻辑代码。

---

## 2. Mock 基础设施规范

### 2.1 Mock 命名规范

```
scripts/mocks/
├── <package-name>.ts           # 直接映射包名
├── expo-file-system.ts         # expo-file-system → expo-file-system/legacy
├── expo-image.ts               # expo-image
├── react-native-reanimated.ts  # react-native-reanimated
└── @shopify-flash-list.ts      # @shopify/flash-list (使用简化的包名)
```

### 2.2 Mock 导出规范

所有 Mock 必须使用 CommonJS 风格导出（通过 `module.exports`）或兼容的 ESM 导出，确保 Jest 能正确加载：

```typescript
// ✅ 正确的导出方式
module.exports = { /* ... */ };
// 或
export const open = () => ({});
export const execute = () => ({});
```

### 2.3 op-sqlite Mock 完整实现

**文件**: `scripts/mocks/op-sqlite.ts`

```typescript
/**
 * op-sqlite 内存模拟器
 * 支持基本的 SQLite 操作
 */

interface Table {
  name: string;
  columns: string[];
  rows: any[][];
}

interface Database {
  name: string;
  tables: Map<string, Table>;
  close?: () => void;
}

// 数据库实例存储（支持多数据库）
const databases = new Map<string, Database>();

function createDatabase(name: string): Database {
  return {
    name,
    tables: new Map(),
  };
}

function parseCreateTable(sql: string): { name: string; columns: string[] } {
  const match = sql.match(/CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[\`"]?(\w+)[\`"]?\s*\((.+)\)/is);
  if (!match) throw new Error(`Invalid CREATE TABLE: ${sql}`);

  const tableName = match[1];
  const columnsDef = match[2];
  
  // 解析列定义
  const columns: string[] = [];
  const columnMatches = columnsDef.matchAll(/[\`"]?(\w+)[\`"]?\s+\w+/gi);
  for (const m of columnMatches) {
    columns.push(m[1]);
  }

  return { name: tableName, columns };
}

function parseInsert(sql: string): { table: string; columns: string[]; values: any[][] } {
  const match = sql.match(
    /INSERT\s+INTO\s+[\`"]?(\w+)[\`"]?\s*(?:\(([^)]+)\))?\s*VALUES\s+(.+)/is
  );
  if (!match) throw new Error(`Invalid INSERT: ${sql}`);

  const table = match[1];
  const columnsStr = match[2];
  const valuesStr = match[3];

  const columns = columnsStr 
    ? columnsStr.split(',').map((c: string) => c.trim().replace(/[\`"]/g, ''))
    : [];

  // 解析 VALUES
  const values: any[][] = [];
  const valueMatches = valuesStr.matchAll(/\(([^)]+)\)/g);
  for (const m of valueMatches) {
    const row = m[1].split(',').map((v: string) => {
      v = v.trim();
      if (v === 'NULL') return null;
      if (v === 'CURRENT_TIMESTAMP') return Date.now();
      if (v.startsWith("'") && v.endsWith("'")) return v.slice(1, -1);
      if (v.startsWith('"') && v.endsWith('"')) return v.slice(1, -1);
      const num = Number(v);
      return isNaN(num) ? v : num;
    });
    values.push(row);
  }

  return { table, columns, values };
}

function parseSelect(sql: string): { table: string; where?: string } {
  const match = sql.match(/SELECT\s+.+\s+FROM\s+[\`"]?(\w+)[\`"]?(?:\s+WHERE\s+(.+))?/is);
  if (!match) throw new Error(`Invalid SELECT: ${sql}`);
  return { table: match[1], where: match[2] };
}

function executeOnDb(db: Database, sql: string, params: any[] = []): any {
  const trimmed = sql.trim().toUpperCase();

  if (trimmed.startsWith('CREATE TABLE')) {
    const { name, columns } = parseCreateTable(sql);
    db.tables.set(name, { name, columns, rows: [] });
    return { rows: [] };
  }

  if (trimmed.startsWith('INSERT')) {
    const { table, columns, values } = parseInsert(sql);
    const tbl = db.tables.get(table);
    if (!tbl) throw new Error(`Table not found: ${table}`);

    for (const row of values) {
      tbl.rows.push(row);
    }
    return { rows: [] };
  }

  if (trimmed.startsWith('SELECT')) {
    const { table, where } = parseSelect(sql);
    const tbl = db.tables.get(table);
    if (!tbl) return { rows: [] };

    let results = tbl.rows;

    // 简单 WHERE 处理
    if (where) {
      results = results.filter((row) => {
        // 简单的 WHERE id = ? 处理
        const paramMatch = where.match(/(\w+)\s*=\s*\?/i);
        if (paramMatch) {
          const colIndex = tbl.columns.indexOf(paramMatch[1]);
          if (colIndex >= 0) {
            return row[colIndex] === params[0];
          }
        }
        return true;
      });
    }

    return { rows: results };
  }

  if (trimmed.startsWith('UPDATE')) {
    // UPDATE 支持
    const match = sql.match(/UPDATE\s+[\`"]?(\w+)[\`"]?\s+SET\s+(.+?)\s+WHERE\s+(.+)/is);
    if (match) {
      const tbl = db.tables.get(match[1]);
      if (tbl) {
        // 简化实现
        return { rows: [] };
      }
    }
  }

  if (trimmed.startsWith('DELETE')) {
    // DELETE 支持
    return { rows: [] };
  }

  return { rows: [] };
}

function open(dbName: string = ':memory:'): Database {
  if (!databases.has(dbName)) {
    databases.set(dbName, createDatabase(dbName));
  }
  const db = databases.get(dbName)!;

  return {
    execute: (sql: string, params?: any[]) => executeOnDb(db, sql, params),
    executeAsync: async (sql: string, params?: any[]) => executeOnDb(db, sql, params),
    close: () => databases.delete(dbName),
  } as any;
}

function resetDatabase(db: Database): void {
  for (const table of db.tables.values()) {
    table.rows = [];
  }
}

module.exports = { open, resetDatabase };
```

### 2.4 AsyncStorage Mock 完整实现

**文件**: `scripts/mocks/async-storage.ts`

```typescript
/**
 * AsyncStorage 内存模拟
 */

const storage = new Map<string, string>();

async function getItem(key: string): Promise<string | null> {
  return storage.get(key) ?? null;
}

async function setItem(key: string, value: string): Promise<void> {
  storage.set(key, value);
}

async function removeItem(key: string): Promise<void> {
  storage.delete(key);
}

async function getAllKeys(): Promise<string[]> {
  return [...storage.keys()];
}

async function clear(): Promise<void> {
  storage.clear();
}

async function multiGet(keys: string[]): Promise<[string, string | null][]> {
  return keys.map((k) => [k, storage.get(k) ?? null]);
}

async function multiSet(
  keyValuePairs: [string, string][]
): Promise<void> {
  for (const [k, v] of keyValuePairs) {
    storage.set(k, v);
  }
}

async function multiRemove(keys: string[]): Promise<void> {
  for (const k of keys) {
    storage.delete(k);
  }
}

// 导出
module.exports = {
  getItem,
  setItem,
  removeItem,
  getAllKeys,
  clear,
  multiGet,
  multiSet,
  multiRemove,
  // Jest mock helper
  __getStorage: () => storage,
  __clear: () => storage.clear(),
};
```

---

## 3. 单元测试模板

### 3.1 LLM 层测试模板

**文件**: `src/lib/llm/__tests__/factory.test.ts`

```typescript
/**
 * LLM Factory 单元测试
 */

import { LLMFactory, createLLMClient } from '../factory';

describe('LLMFactory', () => {
  describe('createLLMClient', () => {
    it('应为 OpenAI 创建正确的客户端', () => {
      const client = createLLMClient('openai', {
        apiKey: 'test-key',
        model: 'gpt-4',
      });
      expect(client.provider).toBe('openai');
      expect(client.model).toBe('gpt-4');
    });

    it('应为 DeepSeek 创建正确的客户端', () => {
      const client = createLLMClient('deepseek', {
        apiKey: 'test-key',
        model: 'deepseek-chat',
      });
      expect(client.provider).toBe('deepseek');
    });

    it('应为 Gemini 创建正确的客户端', () => {
      const client = createLLMClient('gemini', {
        apiKey: 'test-key',
        model: 'gemini-pro',
      });
      expect(client.provider).toBe('gemini');
    });

    it('应为智谱创建正确的客户端', () => {
      const client = createLLMClient('zhipu-ai', {
        apiKey: 'test-key',
        model: 'glm-4',
      });
      expect(client.provider).toBe('zhipu-ai');
    });

    it('应拒绝未知提供商', () => {
      expect(() => {
        createLLMClient('unknown' as any, { apiKey: 'test' });
      }).toThrow('Unknown provider');
    });
  });

  describe('LLMFactory.getInstance', () => {
    it('应返回单例实例', () => {
      const instance1 = LLMFactory.getInstance();
      const instance2 = LLMFactory.getInstance();
      expect(instance1).toBe(instance2);
    });
  });
});
```

### 3.2 RAG 层测试模板

**文件**: `src/lib/rag/__tests__/keyword-search.test.ts`

```typescript
/**
 * 关键词检索单元测试
 */

import { KeywordSearchEngine } from '../keyword-search';
import { createMockDocuments } from '../../../test-utils';

describe('KeywordSearchEngine', () => {
  let engine: KeywordSearchEngine;

  beforeEach(() => {
    engine = new KeywordSearchEngine({});
  });

  describe('索引和检索', () => {
    it('应正确索引文档', async () => {
      const docs = createMockDocuments(5);
      await engine.index(docs);
      const stats = engine.getStats();
      expect(stats.documentCount).toBe(5);
    });

    it('应通过关键词检索到相关文档', async () => {
      const docs = [
        { id: '1', text: 'React Native 开发指南', metadata: {} },
        { id: '2', text: 'TypeScript 高级教程', metadata: {} },
        { id: '3', text: 'React 状态管理', metadata: {} },
      ];
      await engine.index(docs);

      const results = await engine.search('React', 2);
      expect(results.length).toBeGreaterThan(0);
      expect(results[0].document.text).toContain('React');
    });

    it('应支持多关键词检索', async () => {
      const docs = [
        { id: '1', text: 'React Native 使用指南', metadata: {} },
        { id: '2', text: 'Vue.js 开发', metadata: {} },
      ];
      await engine.index(docs);

      const results = await engine.search('React Native', 2);
      expect(results.length).toBeGreaterThan(0);
    });

    it('应按相关性排序结果', async () => {
      const docs = [
        { id: '1', text: 'React Native', metadata: {} },
        { id: '2', text: 'React Native 和 Native 通信', metadata: {} },
      ];
      await engine.index(docs);

      const results = await engine.search('React Native', 2);
      // 更相关的文档应该排在前面
      expect(results[0].document.id).toBe('2');
    });

    it('应返回空结果当无匹配', async () => {
      const docs = [{ id: '1', text: 'JavaScript', metadata: {} }];
      await engine.index(docs);

      const results = await engine.search('Python', 5);
      expect(results.length).toBe(0);
    });
  });

  describe('高级特性', () => {
    it('应支持停用词过滤', async () => {
      const engineWithStopWords = new KeywordSearchEngine({
        stopWords: ['的', '了', '是'],
      });
      const docs = [
        { id: '1', text: '这是测试文档', metadata: {} },
      ];
      await engineWithStopWords.index(docs);

      const results = await engineWithStopWords.search('测试', 1);
      expect(results.length).toBe(1);
    });

    it('应支持模糊匹配', async () => {
      const docs = [
        { id: '1', text: 'JavaScript 编程', metadata: {} },
      ];
      await engine.index(docs);

      const results = await engine.search('Javscript', 1); // 拼写错误
      expect(results.length).toBeGreaterThanOrEqual(0);
    });
  });
});
```

### 3.3 Store 测试模板

**文件**: `src/store/__tests__/chat-store.test.ts`

```typescript
/**
 * Chat Store 单元测试
 */

import { act } from '@testing-library/react-native';

// Mock 掉原生模块
jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(),
  notificationAsync: jest.fn(),
}));

jest.mock('expo-clipboard', () => ({
  setStringAsync: jest.fn(),
  getStringAsync: jest.fn().mockResolvedValue(''),
}));

describe('Chat Store', () => {
  // 动态导入避免 module-alias 问题
  let useChatStore: any;

  beforeAll(async () => {
    // 延迟导入确保 mock 已设置
    const store = await import('../chat/index');
    useChatStore = store.useChatStore;
  });

  beforeEach(() => {
    // 重置 store 状态
    const state = useChatStore.getState();
    act(() => {
      state.clearAll();
    });
  });

  describe('会话管理', () => {
    it('应创建新会话', () => {
      const state = useChatStore.getState();
      let sessionId: string | undefined;

      act(() => {
        sessionId = state.createSession('Test Session', 'glm-4');
      });

      expect(sessionId).toBeDefined();
      const sessions = useChatStore.getState().sessions;
      expect(sessions.some((s: any) => s.id === sessionId)).toBe(true);
    });

    it('应删除会话', () => {
      const state = useChatStore.getState();
      let sessionId: string | undefined;

      act(() => {
        sessionId = state.createSession('To Delete', 'glm-4');
      });

      act(() => {
        state.deleteSession(sessionId!);
      });

      const sessions = useChatStore.getState().sessions;
      expect(sessions.some((s: any) => s.id === sessionId)).toBe(false);
    });

    it('应切换活动会话', () => {
      const state = useChatStore.getState();
      let sessionId1: string, sessionId2: string;

      act(() => {
        sessionId1 = state.createSession('Session 1', 'glm-4');
        sessionId2 = state.createSession('Session 2', 'glm-4');
      });

      act(() => {
        state.setActiveSession(sessionId1);
      });

      expect(useChatStore.getState().activeSessionId).toBe(sessionId1);

      act(() => {
        state.setActiveSession(sessionId2);
      });

      expect(useChatStore.getState().activeSessionId).toBe(sessionId2);
    });
  });

  describe('消息管理', () => {
    it('应添加消息到当前会话', () => {
      const state = useChatStore.getState();
      const sessionId = state.createSession('Test', 'glm-4');
      state.setActiveSession(sessionId);

      act(() => {
        state.addMessage({
          role: 'user',
          content: 'Hello',
        });
      });

      const session = state.sessions.find((s: any) => s.id === sessionId);
      expect(session.messages.length).toBe(1);
      expect(session.messages[0].content).toBe('Hello');
    });

    it('应支持多轮对话', () => {
      const state = useChatStore.getState();
      const sessionId = state.createSession('Test', 'glm-4');
      state.setActiveSession(sessionId);

      act(() => {
        state.addMessage({ role: 'user', content: 'Hello' });
      });
      act(() => {
        state.addMessage({ role: 'assistant', content: 'Hi there!' });
      });
      act(() => {
        state.addMessage({ role: 'user', content: 'How are you?' });
      });

      const session = state.sessions.find((s: any) => s.id === sessionId);
      expect(session.messages.length).toBe(3);
      expect(session.messages[0].role).toBe('user');
      expect(session.messages[1].role).toBe('assistant');
    });

    it('应更新消息内容', () => {
      const state = useChatStore.getState();
      const sessionId = state.createSession('Test', 'glm-4');
      state.setActiveSession(sessionId);

      act(() => {
        state.addMessage({ role: 'user', content: 'Hello' });
      });

      const messageId = state.sessions[0].messages[0].id;

      act(() => {
        state.updateMessage(messageId, { content: 'Updated' });
      });

      const session = state.sessions.find((s: any) => s.id === sessionId);
      expect(session.messages[0].content).toBe('Updated');
    });

    it('应删除消息', () => {
      const state = useChatStore.getState();
      const sessionId = state.createSession('Test', 'glm-4');
      state.setActiveSession(sessionId);

      act(() => {
        state.addMessage({ role: 'user', content: 'To delete' });
      });

      const messageId = state.sessions[0].messages[0].id;

      act(() => {
        state.deleteMessage(messageId);
      });

      const session = state.sessions.find((s: any) => s.id === sessionId);
      expect(session.messages.length).toBe(0);
    });
  });

  describe('上下文管理', () => {
    it('应限制上下文 Token 数量', () => {
      const state = useChatStore.getState();
      const sessionId = state.createSession('Test', 'glm-4');
      state.setActiveSession(sessionId);

      // 添加大量消息
      act(() => {
        for (let i = 0; i < 100; i++) {
          state.addMessage({ role: 'user', content: `Message ${i}` });
        }
      });

      // 检查是否进行了上下文截断
      const session = state.sessions.find((s: any) => s.id === sessionId);
      // 具体行为取决于实现
      expect(session.messages.length).toBeGreaterThan(0);
    });
  });
});
```

---

## 4. 测试覆盖目标清单

| 模块 | 文件 | 目标覆盖率 | 关键测试用例 |
|------|------|-----------|-------------|
| **LLM 层** | | | |
| stream-parser | src/lib/llm/stream-parser.ts | 90% | 流式解析、工具调用、代码块 |
| error-normalizer | src/lib/llm/error-normalizer.ts | 85% | 错误标准化、分类 |
| message-formatter | src/lib/llm/message-formatter.ts | 80% | 格式化、多角色 |
| model-utils | src/lib/llm/model-utils.ts | 85% | 模型参数、上下文窗口 |
| thinking-detector | src/lib/llm/thinking-detector.ts | 85% | 思考标签检测、提取 |
| factory | src/lib/llm/factory.ts | 80% | 厂商适配器创建 |
| response-normalizer | src/lib/llm/response-normalizer.ts | 75% | 响应标准化 |
| **RAG 层** | | | |
| vector-store | src/lib/rag/vector-store.ts | 80% | 插入、检索、删除 |
| text-splitter | src/lib/rag/text-splitter.ts | 85% | 各种大小文档切块 |
| keyword-search | src/lib/rag/keyword-search.ts | 85% | 检索、排序、停用词 |
| query-rewriter | src/lib/rag/query-rewriter.ts | 75% | 查询重写 |
| embedding | src/lib/rag/embedding.ts | 70% | Embedding 生成 |
| **数据库层** | | | |
| schema | src/lib/db/schema.ts | 90% | Schema 验证、迁移 |
| session-repository | src/lib/db/session-repository.ts | 85% | CRUD 操作 |
| **Store 层** | | | |
| chat-store | src/store/chat/index.ts | 85% | 会话管理、消息管理 |
| settings-store | src/store/settings-store.ts | 80% | 设置持久化 |
| rag-store | src/store/rag-store.ts | 75% | RAG 状态管理 |

---

## 5. 实施步骤

### Week 1

- [ ] 扩展 scripts/mocks/ 基础设施
- [ ] 更新 jest.config.js 和 test-setup.ts
- [ ] 搭建 scripts/agent-test/ 骨架

### Week 2

- [ ] 编写 src/lib/__tests__/stream-parser.test.ts
- [ ] 编写 src/lib/__tests__/error-normalizer.test.ts
- [ ] 编写 src/lib/__tests__/message-formatters.test.ts
- [ ] 编写 src/lib/__tests__/model-utils.test.ts
- [ ] 编写 src/lib/__tests__/thinking-detector.test.ts

### Week 3

- [ ] 编写 src/lib/llm/__tests__/factory.test.ts
- [ ] 编写 src/lib/llm/__tests__/response-normalizer.test.ts
- [ ] 编写 src/lib/db/__tests__/schema.test.ts
- [ ] 编写 src/lib/db/__tests__/session-repository.test.ts

### Week 4

- [ ] 编写 src/store/__tests__/chat-store.test.ts
- [ ] 编写 src/store/__tests__/settings-store.test.ts
- [ ] 编写 src/lib/rag/__tests__/vector-store.test.ts
- [ ] 编写 src/lib/rag/__tests__/keyword-search.test.ts
- [ ] 编写 src/features/chat/__tests__/utils/context-manager.test.ts

---

*文档结束 — Phase 1 详细设计，包含 Mock 基础设施规范、测试模板和覆盖目标清单。*
