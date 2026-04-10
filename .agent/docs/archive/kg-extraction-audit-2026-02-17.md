# 知识图谱抽取与更新机制审计报告

**审计日期**: 2026-02-17  
**审计范围**: 知识图谱抽取、存储、更新机制  
**审计文件**: `graph-extractor.ts`, `graph-store.ts`, `vectorization-queue.ts`, `post-processor.ts`

---

## 一、架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                        触发层                                    │
├─────────────────────────────────────────────────────────────────┤
│  文档上传 → VectorizationQueue.enqueueDocument()                │
│  对话完成 → PostProcessor.extractKnowledgeGraph()               │
│           → RagStore.accumulateForKG() → 批量阈值触发            │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    GraphExtractor (抽取层)                       │
│  - LLM 调用获取实体和关系                                        │
│  - JSON 解析和验证                                               │
│  - 调用 GraphStore 存储                                          │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    GraphStore (存储层)                           │
│  - upsertNode(): 创建/更新节点（按 name 唯一）                   │
│  - createEdge(): 创建边                                          │
│  - mergeNodes(): 合并节点                                        │
│  - getGraphData(): 查询图谱数据                                  │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SQLite 数据库                                 │
│  kg_nodes: id, name(UNIQUE), type, metadata, session_id         │
│  kg_edges: source_id, target_id, relation, doc_id, session_id   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、发现的问题

### 🔴 P0: 节点唯一性约束设计缺陷

**问题**: `kg_nodes` 表的 `UNIQUE(name)` 约束是全局的，不考虑 `session_id` 或 `agent_id` 隔离。

**影响**:
- 不同会话创建的同名节点会被强制合并
- 会话 A 的知识图谱可能污染会话 B
- 跨会话的知识图谱查询可能返回错误数据

**代码位置**: [schema.ts:248](file:///home/lengz/Nexara/src/lib/db/schema.ts#L248)
```sql
CREATE TABLE IF NOT EXISTS kg_nodes (
  ...
  UNIQUE(name)  -- ❌ 全局唯一，无 session_id 隔离
);
```

**代码位置**: [graph-store.ts:46-49](file:///home/lengz/Nexara/src/lib/rag/graph-store.ts#L46-L49)
```typescript
// 查询时只按 name 查询，忽略 session_id
let query = 'SELECT * FROM kg_nodes WHERE name = ?';
const params: any[] = [name];
const existing = await db.execute(query, params);
```

---

### 🔴 P0: 边创建无去重机制

**问题**: `createEdge` 方法没有检查是否已存在相同的边。

**影响**:
- 同一文档/会话多次抽取会产生重复边
- 知识图谱可视化时显示重复连线
- 存储空间浪费

**代码位置**: [graph-store.ts:258-288](file:///home/lengz/Nexara/src/lib/rag/graph-store.ts#L258-L288)
```typescript
async createEdge(...): Promise<string> {
  // ❌ 没有检查是否已存在相同的 (source_id, target_id, relation)
  const id = generateId();
  await db.execute('INSERT INTO kg_edges ...');
  return id;
}
```

---

### 🟡 P1: 节点合并时的自环和重复边问题

**问题**: `mergeNodes` 方法承认可能产生自环和重复边，但没有清理逻辑。

**影响**:
- 知识图谱可视化异常
- 图算法（如最短路径）可能出错

**代码位置**: [graph-store.ts:207-210](file:///home/lengz/Nexara/src/lib/rag/graph-store.ts#L207-L210)
```typescript
// Note: This might create self-loops or duplicate edges.
// For now we allow them, or the UI visualization handles them.
// Ideally we should cleanup duplicates here, but it requires complex query.
```

---

### 🟡 P1: 并发安全问题 (竞态条件)

**问题**: `upsertNode` 使用"先查询后插入"模式，存在竞态条件。

**影响**:
- 并发创建同名节点时可能触发 `UNIQUE constraint failed` 错误
- 抽取任务失败

**代码位置**: [graph-store.ts:46-121](file:///home/lengz/Nexara/src/lib/rag/graph-store.ts#L46-L121)
```typescript
// 1. 先查询
const existing = await db.execute(query, params);
if (existing.rows && existing.rows.length > 0) {
  // 更新逻辑
} else {
  // 2. 后插入
  await db.execute('INSERT INTO kg_nodes ...');  // ❌ 可能触发 UNIQUE 约束
}
```

---

### 🟡 P1: 文档更新时知识图谱不更新

**问题**: 当文档内容更新时，没有重新抽取知识图谱的逻辑。

**影响**:
- 旧的知识图谱数据残留
- 知识图谱与文档内容不一致

**建议**: 添加文档更新时的知识图谱清理/重新抽取逻辑。

---

### 🟡 P2: 累积器状态丢失风险

**问题**: `kgAccumulator` 存储在 Zustand 内存中，应用重启后会丢失。

**影响**:
- 未达到批量阈值的累积内容丢失
- 部分对话的知识图谱不会被抽取

**代码位置**: [rag-store.ts:1051-1083](file:///home/lengz/Nexara/src/store/rag-store.ts#L1051-L1083)
```typescript
accumulateForKG: (sessionId: string, content: string, messageId: string) => {
  // 存储在内存中，应用重启后丢失
  const acc = state.kgAccumulator[sessionId] || { contents: [], messageIds: [] };
  // ...
}
```

---

### 🟢 P3: 错误处理已完善

**正面发现**: `extractAndSave` 方法正确捕获异常并返回 `error` 字段，不会导致应用崩溃。

**代码位置**: [graph-extractor.ts:301-308](file:///home/lengz/Nexara/src/lib/rag/graph-extractor.ts#L301-L308)
```typescript
catch (error) {
  // 🔥 CRITICAL: 绝不在 RN 后台任务中抛出异常，否则触发红屏崩溃。
  // 通过 error 字段向调用方传递失败信号。
  return { nodes: [], edges: [], error: `图谱提取失败: ${errMsg.substring(0, 80)}` };
}
```

---

## 三、改进建议

### 建议 1: 修改节点唯一性约束

**方案 A**: 移除全局 UNIQUE 约束，改为应用层去重
```sql
-- 移除 UNIQUE(name) 约束
CREATE TABLE IF NOT EXISTS kg_nodes (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  type TEXT DEFAULT 'concept',
  metadata TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER,
  session_id TEXT,
  agent_id TEXT
);

-- 创建复合索引加速查询
CREATE INDEX idx_kg_nodes_name_session ON kg_nodes(name, session_id);
```

**方案 B**: 使用复合唯一约束（如果需要全局共享节点）
```sql
-- 允许不同会话有同名节点
CREATE UNIQUE INDEX idx_kg_nodes_unique ON kg_nodes(name, COALESCE(session_id, ''));
```

### 建议 2: 边去重机制

```typescript
async createEdge(
  sourceId: string,
  targetId: string,
  relation: string,
  docId?: string,
  weight: number = 1.0,
  scope?: { sessionId?: string; agentId?: string },
): Promise<string> {
  // 检查是否已存在相同的边
  const existing = await db.execute(
    `SELECT id FROM kg_edges 
     WHERE source_id = ? AND target_id = ? AND relation = ?
     AND (doc_id = ? OR (doc_id IS NULL AND ? IS NULL))`,
    [sourceId, targetId, relation, docId || null, docId || null]
  );

  if (existing.rows && existing.rows.length > 0) {
    // 已存在，更新权重并返回
    const existingId = (existing.rows as any)[0].id;
    await db.execute(
      'UPDATE kg_edges SET weight = weight + ? WHERE id = ?',
      [weight, existingId]
    );
    return existingId;
  }

  // 不存在，创建新边
  const id = generateId();
  await db.execute('INSERT INTO kg_edges ...');
  return id;
}
```

### 建议 3: 节点合并后清理自环和重复边

```typescript
async mergeNodes(sourceId: string, targetName: string): Promise<void> {
  // ... 现有合并逻辑 ...

  // 清理自环边（source_id = target_id）
  await db.execute(
    'DELETE FROM kg_edges WHERE source_id = ? AND target_id = ?',
    [targetId, targetId]
  );

  // 清理重复边（保留权重最高的）
  await db.execute(`
    DELETE FROM kg_edges 
    WHERE id NOT IN (
      SELECT MAX(id) FROM kg_edges 
      GROUP BY source_id, target_id, relation
    )
  `);
}
```

### 建议 4: 使用 UPSERT 模式避免竞态条件

```typescript
async upsertNode(name: string, type: string = 'concept', metadata: any = {}): Promise<string> {
  const id = generateId();
  const createdAt = Date.now();

  try {
    // 尝试插入
    await db.execute(
      'INSERT INTO kg_nodes (id, name, type, metadata, created_at) VALUES (?, ?, ?, ?, ?)',
      [id, name, type, JSON.stringify(metadata), createdAt]
    );
    return id;
  } catch (e: any) {
    if (e.message?.includes('UNIQUE constraint failed')) {
      // 已存在，执行更新
      const existing = await db.execute('SELECT id FROM kg_nodes WHERE name = ?', [name]);
      const existingId = (existing.rows as any)[0].id;
      
      await db.execute(
        'UPDATE kg_nodes SET type = ?, metadata = ?, updated_at = ? WHERE id = ?',
        [type, JSON.stringify(metadata), Date.now(), existingId]
      );
      return existingId;
    }
    throw e;
  }
}
```

### 建议 5: 文档更新时清理旧知识图谱

```typescript
// 在 rag-store.ts 的 updateDocument 方法中添加
updateDocument: async (id: string, updates: Partial<RagDocument>) => {
  // 如果内容更新，清理旧的知识图谱数据
  if (updates.content) {
    await db.execute('DELETE FROM kg_edges WHERE doc_id = ?', [id]);
    // 清理孤立节点
    await db.execute(`
      DELETE FROM kg_nodes 
      WHERE id NOT IN (SELECT source_id FROM kg_edges) 
      AND id NOT IN (SELECT target_id FROM kg_edges)
    `);
    
    // 重新触发知识图谱抽取
    const queue = getQueue();
    await queue.enqueueDocument(id, updates.title || '', updates.content, 'on-demand', true);
  }
  // ... 其他更新逻辑
}
```

### 建议 6: 持久化累积器状态

```typescript
// 将 kgAccumulator 添加到 Zustand persist 配置
partialize: (state: RagState) => ({
  processingHistory: state.processingHistory,
  expandedFolders: Array.from(state.expandedFolders),
  selectedFolder: state.selectedFolder,
  kgAccumulator: state.kgAccumulator,  // 新增
}),
```

---

## 四、问题严重程度汇总

| 问题 | 严重程度 | 影响 | 建议优先级 |
|------|----------|------|------------|
| 节点唯一性约束设计缺陷 | 🔴 P0 | 数据混淆、跨会话污染 | 高 |
| 边创建无去重机制 | 🔴 P0 | 重复数据、存储浪费 | 高 |
| 节点合并自环/重复边 | 🟡 P1 | 可视化异常 | 中 |
| 并发竞态条件 | 🟡 P1 | 抽取失败 | 中 |
| 文档更新不更新图谱 | 🟡 P1 | 数据不一致 | 中 |
| 累积器状态丢失 | 🟡 P2 | 部分图谱丢失 | 低 |
| 错误处理完善 | 🟢 正常 | - | - |

---

## 五、测试建议

### 测试 1: 节点唯一性隔离测试
```typescript
// 创建两个会话的同名节点
const node1 = await graphStore.upsertNode('Apple', 'concept', {}, { sessionId: 'session-A' });
const node2 = await graphStore.upsertNode('Apple', 'concept', {}, { sessionId: 'session-B' });

// 验证是否为不同节点
assert(node1 !== node2, 'Different sessions should have separate nodes');
```

### 测试 2: 边去重测试
```typescript
// 创建相同的边两次
const edge1 = await graphStore.createEdge(nodeId1, nodeId2, 'related_to');
const edge2 = await graphStore.createEdge(nodeId1, nodeId2, 'related_to');

// 验证只有一条边
const edges = await graphStore.getEdgesForNode(nodeId1);
assert(edges.length === 1, 'Duplicate edges should be merged');
```

### 测试 3: 并发创建测试
```typescript
// 并发创建同名节点
const promises = Array(10).fill(null).map(() => 
  graphStore.upsertNode('ConcurrentNode', 'concept')
);
const results = await Promise.all(promises);

// 验证所有返回相同的 ID
const uniqueIds = new Set(results);
assert(uniqueIds.size === 1, 'Concurrent upserts should return same ID');
```
