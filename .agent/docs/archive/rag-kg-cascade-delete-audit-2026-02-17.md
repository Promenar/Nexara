# RAG 与知识图谱数据级联删除审计报告

**审计日期**: 2026-02-17  
**审计范围**: 会话/文档删除时 RAG 向量和知识图谱数据的级联清理机制  
**严重程度**: 高

---

## 一、审计发现摘要

| 问题 | 严重程度 | 状态 |
|------|----------|------|
| 会话删除时未清理知识图谱数据 | 🔴 高 | 待修复 |
| 批量删除文档时不删除物理文件 | 🟡 中 | 待修复 |
| 文档删除时正确清理向量和知识图谱 | 🟢 正常 | - |
| 向量表会话级联删除正常 | 🟢 正常 | - |

---

## 二、数据库外键约束分析

### vectors 表
```sql
FOREIGN KEY (doc_id) REFERENCES documents(id) ON DELETE CASCADE,  -- ✅ 正常
FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE  -- ✅ 正常
```

### kg_edges 表
```sql
FOREIGN KEY (source_id) REFERENCES kg_nodes(id) ON DELETE CASCADE,  -- ✅ 正常
FOREIGN KEY (target_id) REFERENCES kg_nodes(id) ON DELETE CASCADE,  -- ✅ 正常
FOREIGN KEY (doc_id) REFERENCES documents(id) ON DELETE CASCADE      -- ✅ 正常
-- session_id 列存在，但无外键约束 ❌
```

### kg_nodes 表
```sql
-- session_id 列存在，但无外键约束 ❌
-- 无任何外键约束
```

---

## 三、问题详情

### 🔴 问题 1: 会话删除时未清理知识图谱数据

**影响**: 删除会话后，该会话关联的知识图谱节点和边会残留在数据库中，造成数据冗余。

**当前代码** ([session-manager.ts:59-71](file:///home/lengz/Nexara/src/store/chat/session-manager.ts#L59-L71)):
```typescript
deleteSession: async (id: SessionId) => {
    // 1. 从 SQLite 删除（CASCADE 自动删除 messages）
    try {
        await SessionRepository.delete(id);
    } catch (e) {
        console.warn('[SessionManager] DB delete failed:', e);
    }

    // 2. 更新 Zustand 缓存
    set((state) => ({
        sessions: state.sessions.filter((s) => s.id !== id),
    }));
},
```

**问题分析**:
- `vectors` 表有 `session_id` 外键约束，会自动级联删除 ✅
- `kg_nodes` 和 `kg_edges` 表的 `session_id` 列通过 Migration 8 添加，但**未建立外键约束**
- 因此删除会话时，会话关联的知识图谱数据**不会被自动删除**

**数据残留示例**:
```sql
-- 删除会话后残留的数据
SELECT * FROM kg_nodes WHERE session_id = 'deleted_session_id';  -- 仍有数据
SELECT * FROM kg_edges WHERE session_id = 'deleted_session_id';  -- 仍有数据
```

---

### 🟡 问题 2: 批量删除文档时不删除物理文件

**影响**: 批量删除文档后，物理文件会残留在存储中，占用磁盘空间。

**当前代码** ([rag-store.ts:807-831](file:///home/lengz/Nexara/src/store/rag-store.ts#L807-L831)):
```typescript
deleteBatch: async (docIds: string[]) => {
  try {
    for (const id of docIds) {
      await db.execute('DELETE FROM vectors WHERE doc_id = ?', [id]);
      await db.execute('DELETE FROM kg_edges WHERE doc_id = ?', [id]);
      await db.execute('DELETE FROM documents WHERE id = ?', [id]);
    }

    // Cleanup orphaned nodes once for the batch
    await db.execute(`
        DELETE FROM kg_nodes 
        WHERE id NOT IN (SELECT source_id FROM kg_edges) 
        AND id NOT IN (SELECT target_id FROM kg_edges)
    `);

    set((state: RagState) => ({
      documents: state.documents.filter((d: RagDocument) => !docIds.includes(d.id)),
    }));
    get().loadFolders();
  } catch (e) {
    console.error('Failed to delete batch:', e);
    throw e;
  }
},
```

**对比单文档删除** ([rag-store.ts:451-484](file:///home/lengz/Nexara/src/store/rag-store.ts#L451-L484)):
```typescript
// 单文档删除有物理文件删除逻辑
if (doc) {
  const physicalDir = await get()._getPhysicalPath(doc.folderId);
  await FileSystem.deleteAsync(physicalDir + doc.title, { idempotent: true });
}
```

**问题**: `deleteBatch` 缺少物理文件删除逻辑。

---

## 四、修复方案

### 修复 1: 会话删除时清理知识图谱数据

**修改文件**: `src/store/chat/session-manager.ts`

```typescript
deleteSession: async (id: SessionId) => {
    // 1. 清理会话关联的知识图谱数据（无外键约束，需手动清理）
    try {
        await db.execute('DELETE FROM kg_edges WHERE session_id = ?', [id]);
        // 清理孤立节点
        await db.execute(`
            DELETE FROM kg_nodes 
            WHERE session_id = ? 
            AND id NOT IN (SELECT source_id FROM kg_edges WHERE source_id = kg_nodes.id)
            AND id NOT IN (SELECT target_id FROM kg_edges WHERE target_id = kg_nodes.id)
        `, [id]);
    } catch (e) {
        console.warn('[SessionManager] KG cleanup failed:', e);
    }

    // 2. 从 SQLite 删除（CASCADE 自动删除 messages 和 vectors）
    try {
        await SessionRepository.delete(id);
    } catch (e) {
        console.warn('[SessionManager] DB delete failed:', e);
    }

    // 3. 更新 Zustand 缓存
    set((state) => ({
        sessions: state.sessions.filter((s) => s.id !== id),
    }));
},
```

### 修复 2: 批量删除文档时删除物理文件

**修改文件**: `src/store/rag-store.ts`

```typescript
deleteBatch: async (docIds: string[]) => {
  try {
    // 🛡️ 在状态变更前缓存文档信息，防止 set 后 get 找不到
    const docs = get().documents.filter(d => docIds.includes(d.id));

    for (const id of docIds) {
      await db.execute('DELETE FROM vectors WHERE doc_id = ?', [id]);
      await db.execute('DELETE FROM kg_edges WHERE doc_id = ?', [id]);
      await db.execute('DELETE FROM documents WHERE id = ?', [id]);
    }

    // Cleanup orphaned nodes once for the batch
    await db.execute(`
        DELETE FROM kg_nodes 
        WHERE id NOT IN (SELECT source_id FROM kg_edges) 
        AND id NOT IN (SELECT target_id FROM kg_edges)
    `);

    set((state: RagState) => ({
      documents: state.documents.filter((d: RagDocument) => !docIds.includes(d.id)),
    }));

    // 🛡️ 删除物理文件
    const folderGroups = new Map<string, string[]>();
    for (const doc of docs) {
      if (!folderGroups.has(doc.folderId)) {
        folderGroups.set(doc.folderId, []);
      }
      folderGroups.get(doc.folderId)!.push(doc.title);
    }

    for (const [folderId, titles] of folderGroups) {
      const physicalDir = await get()._getPhysicalPath(folderId);
      for (const title of titles) {
        try {
          await FileSystem.deleteAsync(physicalDir + title, { idempotent: true });
        } catch (e) {
          console.warn('Failed to delete physical file:', title, e);
        }
      }
    }

    get().loadFolders();
  } catch (e) {
    console.error('Failed to delete batch:', e);
    throw e;
  }
},
```

---

## 五、数据流分析

### 会话删除数据流

```
用户删除会话
    │
    ▼
session-manager.deleteSession(id)
    │
    ├─► [当前缺失] 清理 kg_edges WHERE session_id = ?
    │
    ├─► [当前缺失] 清理 kg_nodes (孤立节点)
    │
    ├─► SessionRepository.delete(id)
    │       │
    │       ├─► DELETE FROM sessions WHERE id = ?
    │       │
    │       └─► [CASCADE] DELETE FROM messages WHERE session_id = ?
    │       └─► [CASCADE] DELETE FROM vectors WHERE session_id = ? ✅
    │
    └─► 更新 Zustand 缓存
```

### 文档删除数据流

```
用户删除文档
    │
    ▼
rag-store.deleteDocument(id) / deleteBatch(ids)
    │
    ├─► DELETE FROM documents WHERE id = ?
    │
    ├─► DELETE FROM vectors WHERE doc_id = ?  ✅
    │
    ├─► DELETE FROM kg_edges WHERE doc_id = ?  ✅
    │
    ├─► 清理孤立 kg_nodes  ✅
    │
    └─► [deleteBatch 缺失] 删除物理文件 ❌
```

---

## 六、建议优先级

| 优先级 | 问题 | 影响 |
|--------|------|------|
| P0 | 会话删除时未清理知识图谱数据 | 数据冗余、存储泄漏 |
| P1 | 批量删除文档时不删除物理文件 | 磁盘空间泄漏 |

---

## 七、测试用例

### 测试 1: 会话删除后知识图谱清理

```typescript
// 1. 创建会话
const session = await createSession({ agentId: 'test' });

// 2. 创建知识图谱节点
await graphStore.upsertNode({
  name: 'Test Node',
  type: 'concept',
  sessionId: session.id,
});

// 3. 删除会话
await deleteSession(session.id);

// 4. 验证知识图谱数据已清理
const nodes = await db.execute('SELECT * FROM kg_nodes WHERE session_id = ?', [session.id]);
assert(nodes.rows?.length === 0, 'KG nodes should be deleted');
```

### 测试 2: 批量删除文档后物理文件清理

```typescript
// 1. 创建文档
const doc1 = await createDocument({ title: 'test1.txt', content: 'content1' });
const doc2 = await createDocument({ title: 'test2.txt', content: 'content2' });

// 2. 批量删除
await deleteBatch([doc1.id, doc2.id]);

// 3. 验证物理文件已删除
const file1Exists = await FileSystem.getInfoAsync(physicalPath + 'test1.txt');
const file2Exists = await FileSystem.getInfoAsync(physicalPath + 'test2.txt');
assert(!file1Exists.exists && !file2Exists.exists, 'Physical files should be deleted');
```
