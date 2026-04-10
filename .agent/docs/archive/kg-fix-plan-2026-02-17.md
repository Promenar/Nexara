# 知识图谱系统完整修复方案

**规划日期**: 2026-02-17  
**目标**: 修复知识图谱抽取与更新机制审计中发现的所有问题  
**预计版本**: v1.2.31

---

## 一、修复任务清单

| 优先级 | 问题 | 状态 |
|--------|------|------|
| P0 | 节点唯一性约束设计缺陷 | 待修复 |
| P0 | 边创建无去重机制 | 待修复 |
| P1 | 节点合并产生自环/重复边 | 待修复 |
| P1 | 并发竞态条件 | 待修复 |
| P1 | 文档更新时不更新图谱 | 待修复 |
| P2 | 累积器状态丢失 | 待修复 |

---

## 二、修复方案详解

### 修复 1: 节点唯一性约束设计缺陷 (P0)

#### 2.1.1 问题分析

当前 `kg_nodes` 表使用 `UNIQUE(name)` 全局唯一约束，导致：
- 不同会话的同名节点被强制合并
- 无法实现会话级别的知识图谱隔离

#### 2.1.2 设计决策

**方案选择**: 采用「全局共享节点 + 会话隔离边」模式

理由：
1. 知识图谱的本质是共享知识，同名实体应合并
2. 通过 `session_id` 在边上隔离，实现会话级视图
3. 避免数据冗余，保持图谱简洁

#### 2.1.3 实施步骤

**步骤 1**: 修改 `upsertNode` 查询逻辑，考虑 scope 隔离

```typescript
// graph-store.ts
async upsertNode(
  name: string,
  type: string = 'concept',
  metadata: any = {},
  scope?: { sessionId?: string; agentId?: string },
): Promise<string> {
  // 全局共享节点：按 name 查找
  // 但更新时保留 scope 信息用于追踪
  const existing = await db.execute('SELECT * FROM kg_nodes WHERE name = ?', [name]);
  
  if (existing.rows && existing.rows.length > 0) {
    // 节点已存在，执行合并更新
    const row = (existing.rows as any)[0];
    const id = row.id;
    
    // 合并 metadata
    const mergedMetadata = this.mergeMetadata(row.metadata, metadata);
    
    // 更新类型（更具体的类型覆盖通用类型）
    const updatedType = this.resolveType(row.type, type);
    
    await db.execute(
      'UPDATE kg_nodes SET type = ?, metadata = ?, updated_at = ? WHERE id = ?',
      [updatedType, JSON.stringify(mergedMetadata), Date.now(), id]
    );
    
    return id;
  }
  
  // 创建新节点
  const id = generateId();
  await db.execute(
    'INSERT INTO kg_nodes (id, name, type, metadata, created_at, session_id, agent_id) VALUES (?, ?, ?, ?, ?, ?, ?)',
    [id, name, type, JSON.stringify(metadata), Date.now(), scope?.sessionId || null, scope?.agentId || null]
  );
  return id;
}
```

**步骤 2**: 保持现有 Schema 不变，在应用层实现隔离

无需修改数据库 Schema，因为：
- `UNIQUE(name)` 约束符合全局共享设计
- `session_id` 列已存在，用于追踪节点来源
- 隔离通过边的 `session_id` 实现

---

### 修复 2: 边创建无去重机制 (P0)

#### 2.2.1 实施方案

**修改文件**: `src/lib/rag/graph-store.ts`

```typescript
async createEdge(
  sourceId: string,
  targetId: string,
  relation: string,
  docId?: string,
  weight: number = 1.0,
  scope?: { sessionId?: string; agentId?: string },
): Promise<string> {
  try {
    // 检查是否已存在相同的边（相同源、目标、关系、文档）
    const existingQuery = `
      SELECT id, weight FROM kg_edges 
      WHERE source_id = ? AND target_id = ? AND relation = ?
      AND (doc_id = ? OR (doc_id IS NULL AND ? IS NULL))
      AND (session_id = ? OR (session_id IS NULL AND ? IS NULL))
    `;
    const existing = await db.execute(existingQuery, [
      sourceId, targetId, relation,
      docId || null, docId || null,
      scope?.sessionId || null, scope?.sessionId || null
    ]);

    if (existing.rows && existing.rows.length > 0) {
      // 边已存在，累加权重并返回
      const existingEdge = (existing.rows as any)[0];
      const newWeight = existingEdge.weight + weight;
      
      await db.execute(
        'UPDATE kg_edges SET weight = ?, created_at = ? WHERE id = ?',
        [newWeight, Date.now(), existingEdge.id]
      );
      
      console.log(`[GraphStore] Edge upserted: ${sourceId} -> ${targetId} (${relation}), weight: ${newWeight}`);
      return existingEdge.id;
    }

    // 创建新边
    const id = generateId();
    const createdAt = Date.now();
    await db.execute(
      'INSERT INTO kg_edges (id, source_id, target_id, relation, weight, doc_id, created_at, session_id, agent_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
      [id, sourceId, targetId, relation, weight, docId || null, createdAt, scope?.sessionId || null, scope?.agentId || null]
    );
    
    console.log(`[GraphStore] Edge created: ${sourceId} -> ${targetId} (${relation})`);
    return id;
  } catch (e) {
    console.error('[GraphStore] Failed to create edge:', e);
    throw e;
  }
}
```

---

### 修复 3: 节点合并产生自环/重复边 (P1)

#### 2.3.1 实施方案

**修改文件**: `src/lib/rag/graph-store.ts`

```typescript
async mergeNodes(sourceId: string, targetName: string): Promise<void> {
  try {
    // 1. 获取目标节点
    const targetRes = await db.execute('SELECT * FROM kg_nodes WHERE name = ?', [targetName]);
    if (!targetRes.rows || targetRes.rows.length === 0) {
      throw new Error(`Target node '${targetName}' not found`);
    }
    const targetNode = (targetRes.rows as any)[0];
    const targetId = targetNode.id;

    if (sourceId === targetId) return; // 相同节点

    console.log(`[GraphStore] Merging node ${sourceId} into ${targetId} (${targetName})`);

    // 2. 迁移边
    await db.execute('UPDATE kg_edges SET source_id = ? WHERE source_id = ?', [targetId, sourceId]);
    await db.execute('UPDATE kg_edges SET target_id = ? WHERE target_id = ?', [targetId, sourceId]);

    // 3. 清理自环边（source_id = target_id）
    const selfLoopResult = await db.execute(
      'DELETE FROM kg_edges WHERE source_id = ? AND target_id = ?',
      [targetId, targetId]
    );
    if (selfLoopResult.rowsAffected > 0) {
      console.log(`[GraphStore] Cleaned ${selfLoopResult.rowsAffected} self-loop edges`);
    }

    // 4. 合并重复边（保留权重最高的）
    const duplicateResult = await db.execute(`
      DELETE FROM kg_edges 
      WHERE id NOT IN (
        SELECT id FROM (
          SELECT id, ROW_NUMBER() OVER (
            PARTITION BY source_id, target_id, relation 
            ORDER BY weight DESC, created_at DESC
          ) as rn
          FROM kg_edges
          WHERE source_id = ? OR target_id = ?
        ) WHERE rn = 1
      )
      AND (source_id = ? OR target_id = ?)
    `, [targetId, targetId, targetId, targetId]);
    
    if (duplicateResult.rowsAffected > 0) {
      console.log(`[GraphStore] Cleaned ${duplicateResult.rowsAffected} duplicate edges`);
    }

    // 5. 合并 metadata
    const sourceRes = await db.execute('SELECT * FROM kg_nodes WHERE id = ?', [sourceId]);
    if (sourceRes.rows && sourceRes.rows.length > 0) {
      const sourceNode = (sourceRes.rows as any)[0];
      const mergedMeta = this.mergeMetadata(targetNode.metadata, sourceNode.metadata);
      
      await db.execute(
        'UPDATE kg_nodes SET metadata = ?, updated_at = ? WHERE id = ?',
        [JSON.stringify(mergedMeta), Date.now(), targetId]
      );
    }

    // 6. 删除源节点
    await db.execute('DELETE FROM kg_nodes WHERE id = ?', [sourceId]);

  } catch (e) {
    console.error('Failed to merge KG nodes:', e);
    throw e;
  }
}

// 辅助方法：合并 metadata
private mergeMetadata(existingMeta: string | null, newMeta: any): Record<string, any> {
  let result: Record<string, any> = {};
  
  try {
    result = existingMeta ? JSON.parse(existingMeta) : {};
  } catch (e) {}
  
  if (!newMeta) return result;
  
  for (const key of Object.keys(newMeta)) {
    const newVal = newMeta[key];
    const oldVal = result[key];
    
    if (Array.isArray(newVal) && Array.isArray(oldVal)) {
      // 数组合并去重
      const combined = [...oldVal, ...newVal];
      result[key] = combined.filter((item, index, self) =>
        index === self.findIndex((t) => JSON.stringify(t) === JSON.stringify(item))
      );
    } else if (newVal !== undefined && newVal !== null) {
      result[key] = newVal;
    }
  }
  
  return result;
}

// 辅助方法：解析类型
private resolveType(existingType: string, newType: string): string {
  // 更具体的类型覆盖通用类型
  const typePriority = ['concept', 'person', 'org', 'location', 'event', 'product'];
  const existingPriority = typePriority.indexOf(existingType);
  const newPriority = typePriority.indexOf(newType);
  
  if (newPriority > existingPriority) return newType;
  return existingType;
}
```

---

### 修复 4: 并发竞态条件 (P1)

#### 2.4.1 实施方案

**修改文件**: `src/lib/rag/graph-store.ts`

使用「先插入后更新」模式，利用 SQLite 的 `ON CONFLICT` 处理：

```typescript
async upsertNode(
  name: string,
  type: string = 'concept',
  metadata: any = {},
  scope?: { sessionId?: string; agentId?: string },
): Promise<string> {
  try {
    // 方案：使用事务 + INSERT OR IGNORE + 后续更新
    const id = generateId();
    const createdAt = Date.now();
    
    // 尝试插入（如果 name 不存在则成功）
    const insertResult = await db.execute(
      `INSERT OR IGNORE INTO kg_nodes (id, name, type, metadata, created_at, session_id, agent_id) 
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [id, name, type, JSON.stringify(metadata), createdAt, scope?.sessionId || null, scope?.agentId || null]
    );
    
    if (insertResult.rowsAffected > 0) {
      // 插入成功，返回新 ID
      console.log(`[GraphStore] Node created: ${name} (${id})`);
      return id;
    }
    
    // 插入失败（name 已存在），查询并更新
    const existing = await db.execute('SELECT * FROM kg_nodes WHERE name = ?', [name]);
    if (existing.rows && existing.rows.length > 0) {
      const row = (existing.rows as any)[0];
      const existingId = row.id;
      
      // 合并更新
      const mergedMetadata = this.mergeMetadata(row.metadata, metadata);
      const updatedType = this.resolveType(row.type, type);
      
      await db.execute(
        'UPDATE kg_nodes SET type = ?, metadata = ?, updated_at = ? WHERE id = ?',
        [updatedType, JSON.stringify(mergedMetadata), Date.now(), existingId]
      );
      
      console.log(`[GraphStore] Node updated: ${name} (${existingId})`);
      return existingId;
    }
    
    // 理论上不应该到达这里
    throw new Error(`Failed to upsert node: ${name}`);
  } catch (e) {
    console.error('Failed to upsert KG node:', e);
    throw e;
  }
}
```

---

### 修复 5: 文档更新时不更新图谱 (P1)

#### 2.5.1 实施方案

**修改文件**: `src/store/rag-store.ts`

添加文档更新时的知识图谱同步逻辑：

```typescript
updateDocument: async (id: string, updates: Partial<RagDocument>) => {
  try {
    // 如果内容更新，需要重新处理知识图谱
    const shouldReextractKG = updates.content !== undefined;
    
    // 如果内容更新，先清理旧的知识图谱数据
    if (shouldReextractKG) {
      console.log(`[RagStore] Content updated, cleaning old KG data for doc: ${id}`);
      await db.execute('DELETE FROM kg_edges WHERE doc_id = ?', [id]);
      // 清理孤立节点
      await db.execute(`
        DELETE FROM kg_nodes 
        WHERE id NOT IN (SELECT source_id FROM kg_edges) 
        AND id NOT IN (SELECT target_id FROM kg_edges)
      `);
    }
    
    // 更新文档记录
    const setClauses: string[] = [];
    const params: any[] = [];
    
    if (updates.title !== undefined) {
      setClauses.push('title = ?');
      params.push(updates.title);
    }
    if (updates.content !== undefined) {
      setClauses.push('content = ?');
      params.push(updates.content);
      setClauses.push('vectorized = 0'); // 重置向量化状态
      setClauses.push('vector_count = 0');
    }
    if (updates.folderId !== undefined) {
      setClauses.push('folder_id = ?');
      params.push(updates.folderId);
    }
    
    if (setClauses.length > 0) {
      params.push(id);
      await db.execute(`UPDATE documents SET ${setClauses.join(', ')} WHERE id = ?`, params);
    }
    
    // 更新状态
    set((state: RagState) => ({
      documents: state.documents.map((d: RagDocument) => 
        d.id === id ? { ...d, ...updates, vectorized: updates.content ? false : d.vectorized } : d
      ),
    }));
    
    // 如果内容更新，重新触发向量化（包含 KG 抽取）
    if (shouldReextractKG && updates.content) {
      const doc = get().documents.find(d => d.id === id);
      if (doc) {
        const queue = getQueue();
        await queue.enqueueDocument(id, doc.title, updates.content, 'on-demand');
      }
    }
    
    get().loadFolders();
  } catch (e) {
    console.error('Failed to update document:', e);
    throw e;
  }
},
```

---

### 修复 6: 累积器状态丢失 (P2)

#### 2.6.1 实施方案

**修改文件**: `src/store/rag-store.ts`

将 `kgAccumulator` 添加到持久化配置：

```typescript
{
  name: 'nexara-rag-storage',
  storage: createJSONStorage(() => AsyncStorage),
  partialize: (state: RagState) => ({
    processingHistory: state.processingHistory,
    expandedFolders: Array.from(state.expandedFolders),
    selectedFolder: state.selectedFolder,
    kgAccumulator: state.kgAccumulator, // 新增：持久化累积器
  }),
  // ...
}
```

---

## 三、实施计划

### Phase 1: 核心修复 (v1.2.31)

| 任务 | 预计时间 | 风险 |
|------|----------|------|
| 修复边去重机制 | 30min | 低 |
| 修复并发竞态条件 | 30min | 低 |
| 修复节点合并自环/重复边 | 45min | 中 |
| 持久化累积器状态 | 15min | 低 |

### Phase 2: 功能增强 (v1.2.32)

| 任务 | 预计时间 | 风险 |
|------|----------|------|
| 文档更新时同步图谱 | 45min | 中 |
| 添加图谱更新 API | 30min | 低 |

### Phase 3: 测试验证

| 测试项 | 说明 |
|--------|------|
| 边去重测试 | 验证相同边只创建一次 |
| 并发创建测试 | 验证并发 upsertNode 正确工作 |
| 节点合并测试 | 验证合并后无自环和重复边 |
| 文档更新测试 | 验证内容更新后图谱重新抽取 |
| 累积器持久化测试 | 验证应用重启后累积器状态保留 |

---

## 四、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Schema 变更导致数据丢失 | 高 | 不修改 Schema，仅改应用逻辑 |
| 并发问题修复不彻底 | 中 | 添加详细日志，便于排查 |
| 文档更新触发大量重新抽取 | 中 | 使用 on-demand 策略，按需抽取 |

---

## 五、回滚方案

如果修复导致严重问题，可通过以下方式回滚：

1. **边去重**: 恢复 `createEdge` 为原始版本
2. **并发处理**: 恢复 `upsertNode` 为原始版本
3. **文档更新**: 移除 `updateDocument` 中的 KG 清理逻辑

所有修改都保持向后兼容，不修改数据库 Schema。
