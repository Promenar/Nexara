# KG JIT Micro-Graph 分阶段实施方案

> 基于 KG 系统审计结论，涵盖泛用性增强 + JIT 动态建图 + 风险防控三大主线

---

## 审计结论摘要

| 维度 | 当前评分 | 目标评分 |
|------|---------|---------|
| 泛用性 (Object > Attribute) | 3/5 | 5/5 |
| JIT 就绪度 | 2/5 | 5/5 |
| 性能 | 3/5 | 4.5/5 |
| 正确性 | 3.5/5 | 5/5 |

---

## Phase 1: 泛用性增强 — 语义下放 (优先级 P0)

**目标**: 移除所有硬编码的实体类型约束，让 LLM 真正自由推演

### 1.1 entityTypes 从必填改为可选

**文件**: `src/lib/rag/graph-extractor.ts`

**现状问题**: `getSystemPrompt()` (L114-144) 始终注入 `kgEntityTypes` 到 `{entityTypes}` 占位符，LLM 输出被锚定在预设框架内。

**改造方案**:

```
当 kgEntityTypes 未配置或为空数组时:
  - Prompt 中不注入 {entityTypes} 占位符
  - 替换为: "自由识别文本中所有值得关注的实体对象，类型名称由你自行判断。优先识别核心对象(Object)，其次才是属性(Attribute)。"
  - 保留 JSON 输出格式约束

当 kgEntityTypes 已配置且非空时:
  - 行为不变，作为用户显式约束的降级模式
```

**改动点**:
- `graph-extractor.ts` → `getSystemPrompt()`: 增加 entityTypes 为空时的分支
- `src/lib/rag/defaults.ts` → `DEFAULT_KG_PROMPT`: 增加"自由识别"模式的备选 Prompt
- `src/lib/llm/prompts/i18n/en.ts` / `zh.ts`: 增加 `kgFreeModePrompt` 本地化文案

**验证标准**:
- [ ] 不配置 kgEntityTypes 时，Prompt 不包含实体类型列表
- [ ] 小说文本抽取能产出 FictionCharacter、Plot、Motif 等自定义类型
- [ ] 论文文本抽取能产出 Method、Experiment、Dataset 等学术类型
- [ ] 已配置 kgEntityTypes 的用户行为不变

### 1.2 修复 resolveType 优先级

**文件**: `src/lib/rag/graph-store.ts` (L60-67)

**现状问题**: `typePriority` 硬编码 6 种类型，未知类型 `indexOf` 返回 -1，导致更精确的自定义类型被丢弃。

**改造方案**:

```typescript
private resolveType(existingType: string, newType: string): string {
  // 策略: 未知类型优先 (更精确的类型胜出)
  const KNOWN_TYPES = new Set(['concept', 'person', 'org', 'location', 'event', 'product']);
  
  const existingIsKnown = KNOWN_TYPES.has(existingType.toLowerCase());
  const newIsKnown = KNOWN_TYPES.has(newType.toLowerCase());
  
  // 已知 → 未知: 升级为更精确的类型
  if (existingIsKnown && !newIsKnown) return newType;
  // 未知 → 已知: 保留更精确的未知类型
  if (!existingIsKnown && newIsKnown) return existingType;
  // 同类: 新类型覆盖 (最近更新优先)
  return newType;
}
```

**验证标准**:
- [ ] "Person" → "Mathematician" 升级成功
- [ ] "Mathematician" → "Person" 不降级
- [ ] "Chemist" → "Physicist" 覆盖更新

### 1.3 领域自适应 Prompt 片段

**文件**: 新增 `src/lib/rag/kg-domain-hints.ts`

**设计思路**: 不自动判断文档类型 (过度工程)，而是通过用户在文档/会话上标记 `domain` 标签，系统根据标签注入对应的 Prompt 引导片段。

```
预置 domain hints:
- fiction: "重点关注角色关系(师徒、宿敌、联盟)、情节结构(伏笔、转折、高潮)、主题与意象"
- academic: "重点关注方法-实验-结论链条、引用关系、数据集与基准"
- technical: "重点关注模块依赖、接口契约、数据流向、配置项"
- dialogue: "重点关注意图、决策、执行结果、参与者立场"

接口:
  getDomainHint(domainTag?: string): string | null
```

**改动点**:
- 新增 `kg-domain-hints.ts` 导出 `getDomainHint()`
- `graph-extractor.ts` → `getSystemPrompt()`: 末尾追加 domain hint
- `RagConfiguration` 类型: 可选新增 `kgDomainHint?: string` 字段

**验证标准**:
- [ ] 标记 `fiction` 的文档抽取包含角色关系类实体
- [ ] 标记 `academic` 的文档抽取包含方法链关系
- [ ] 无标签时行为与现有一致

---

## Phase 2: JIT Micro-Graph 管线 (优先级 P0)

**目标**: 实现"向量召回 → 动态建图 → 沉淀合并"的完整 JIT 管线

### 2.1 核心: MemoryManager 中的 JIT 抽取路径

**文件**: `src/lib/rag/memory-manager.ts` (L628-699)

**现状**: KG 检索阶段只做已有图谱反查，不触发新抽取。`on-demand` 策略在 post-processor 中直接 return。

**改造方案 — 在现有 KG 检索阶段之后，新增 JIT 分支**:

```
MemoryManager.retrieveContext() 中 KG 阶段改造:

if (enableKG && costStrategy === 'on-demand') {
  // Step A: 先尝试现有图谱反查 (现有逻辑)
  const staticKGResult = await queryExistingGraph(finalResults, ...);
  
  if (staticKGResult.edges.length < MIN_KG_EDGES_THRESHOLD) {
    // Step B: 现有图谱不足 → 触发 JIT 动态抽取
    const microGraph = await extractMicroGraph(finalResults, query, sessionId);
    
    // Step C: 即时注入 context
    kgContext += microGraph.context;
    
    // Step D: 异步沉淀到全局图谱 (不阻塞当前请求)
    backgroundMergeMicroGraph(microGraph);
  }
}
```

### 2.2 新增 Micro-Graph 抽取器

**文件**: 新增 `src/lib/rag/micro-graph-extractor.ts`

```typescript
interface MicroGraphResult {
  nodes: Array<{ name: string; type: string; metadata?: any }>;
  edges: Array<{ source: string; target: string; relation: string; weight?: number }>;
  context: string;           // 即时可用的 KG 上下文文本
  sourceChunkIds: string[];  // 来源文本块 ID (用于去重标记)
  query: string;             // 触发查询 (用于缓存 key)
  extractedAt: number;       // 抽取时间戳
}

export class MicroGraphExtractor {
  /**
   * 从 Top-K 召回文本中动态抽取微型图谱
   * 关键: 异步 + 超时设计
   */
  async extract(
    topKResults: SearchResult[],
    query: string,
    sessionId: string,
    options?: {
      timeout?: number;    // 默认 5000ms
      maxChars?: number;   // 喂给 LLM 的最大字符数, 默认 2000
    }
  ): Promise<MicroGraphResult | null>;
}
```

**关键设计决策**:

1. **超时设计** (缓解延迟风险):
   - 默认 5s 超时，通过 `Promise.race([extractPromise, timeoutPromise])` 实现
   - 超时后返回 null，不影响主 RAG 流程
   - 用户可在 `RagConfiguration` 中配置 `jitTimeoutMs`

2. **输入截断** (控制 Token 成本):
   - 只取 Top-K 中的前 N 条 (默认 3 条)
   - 每条截断到 maxChars (默认 2000 字符)
   - 总输入不超过 6000 字符 (约 2000 tokens)

3. **Prompt 差异化**:
   - Micro-Graph Prompt 更轻量，强调"快速提取最核心的关系"
   - 限制输出节点数 ≤ 10，边数 ≤ 15

### 2.3 缓存与标记机制 (缓解重复抽取风险)

**文件**: `src/lib/rag/micro-graph-extractor.ts` 内部 + `src/lib/rag/graph-store.ts` 扩展

**设计方案**:

```
┌──────────────────────────────────────────────────────────┐
│  缓存层: MicroGraphCache                                  │
│  (内存 LRU + SQLite 持久化双层)                           │
│                                                          │
│  Cache Key: hash(query + sorted(chunkIds))               │
│  Cache Value: MicroGraphResult                           │
│  TTL: 1 小时 (可配置)                                    │
│                                                          │
│  触发条件:                                               │
│  - 缓存命中 → 直接返回, 不调用 LLM                      │
│  - 缓存未命中 → 执行抽取, 写入缓存                       │
│                                                          │
│  标记层: kg_edges / kg_nodes 扩展                        │
│  - 新增 source_type 字段: 'full' | 'summary' | 'jit'     │
│  - JIT 生成的边/节点标记为 source_type = 'jit'           │
│  - 合并时: jit → full/summary 可升级，不可降级           │
│  - 清理时: 可选择性清理仅 jit 来源的低权重节点            │
└──────────────────────────────────────────────────────────┘
```

**DB Schema 变更**:

```sql
-- Migration: Micro-Graph 标记
ALTER TABLE kg_nodes ADD COLUMN source_type TEXT DEFAULT 'full';
ALTER TABLE kg_edges ADD COLUMN source_type TEXT DEFAULT 'full';

-- JIT 来源索引 (用于选择性清理)
CREATE INDEX IF NOT EXISTS idx_kg_nodes_source_type ON kg_nodes(source_type);
CREATE INDEX IF NOT EXISTS idx_kg_edges_source_type ON kg_edges(source_type);

-- JIT 抽取缓存表
CREATE TABLE IF NOT EXISTS kg_jit_cache (
  cache_key TEXT PRIMARY KEY,
  query_hash TEXT NOT NULL,
  chunk_ids_hash TEXT NOT NULL,
  result_json TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_kg_jit_cache_expires ON kg_jit_cache(expires_at);
```

**缓存清理策略**:
- 每次 JIT 查询前: `DELETE FROM kg_jit_cache WHERE expires_at < ?`
- 定期: 可选的 LRU 淘汰 (保留最近 100 条)
- 缓存命中时: 不重新调用 LLM，直接返回上次结果

**验证标准**:
- [ ] 相同 query + 相同 Top-K 文本 → 缓存命中，无 LLM 调用
- [ ] 缓存 TTL 过期 → 重新抽取
- [ ] JIT 生成的节点/边在 DB 中标记为 `source_type = 'jit'`
- [ ] 全量抽取的同一关系可覆盖 JIT 标记为 `full`

### 2.4 异步沉淀合并

**文件**: `src/lib/rag/micro-graph-extractor.ts`

**设计思路**: Micro-Graph 抽取结果在返回给当前请求后，异步写入全局图谱。

```
async backgroundMergeMicroGraph(result: MicroGraphResult, scope): Promise<void> {
  // 不 await, 立即返回
  // 使用 vectorization-queue 的 enqueueSessionKG 通道
  // 或直接调用 graphStore (轻量场景)
  
  // 关键: 用事务包裹，避免部分写入
  try {
    // 1. 批量 upsertNode (source_type = 'jit')
    // 2. 批量 createEdge (source_type = 'jit', weight 从 result)
    // 3. 更新 jit_cache 的 result_json (补充持久化后的 node/edge IDs)
  } catch (e) {
    // 沉淀失败不影响已返回的结果
    console.warn('[MicroGraph] Background merge failed:', e);
  }
}
```

**批量写入优化** (同时解决 4.1 审计问题):
```typescript
// 事务包裹单次抽取的全部写入
await db.execute('BEGIN TRANSACTION');
try {
  for (const node of result.nodes) {
    await db.execute(
      `INSERT OR IGNORE INTO kg_nodes (id, name, type, metadata, created_at, source_type) 
       VALUES (?, ?, ?, ?, ?, 'jit')`,
      [id, node.name, node.type, JSON.stringify(node.metadata), Date.now()]
    );
  }
  for (const edge of result.edges) {
    await db.execute(/* ... */);
  }
  await db.execute('COMMIT');
} catch (e) {
  await db.execute('ROLLBACK');
  throw e;
}
```

---

## Phase 3: 性能与正确性修复 (优先级 P1)

### 3.1 消除 KG 检索的全表扫描

**文件**: `src/lib/rag/memory-manager.ts` (L641-647)

**现状**: `SELECT id, name, type FROM kg_nodes` 全表加载后在 JS 层 `includes()` 子串匹配。

**改造方案 — 基于召回文本的定向查询**:

```typescript
// 替代全表扫描:
// 1. 从召回文本中提取候选实体名 (简单策略: 利用已有 chunk 中的名词短语)
//    或更简单: 利用 GraphExtractor 上次抽取时缓存的 name→id 映射
// 2. 用 WHERE name IN (...) 定向查询

// 分步实现:
// Step 1: 先尝试用召回文本中的关键短语匹配
const candidateNames = extractCandidateNames(allReferenceText, maxNamesPerChunk: 3);

if (candidateNames.length > 0) {
  const placeholders = candidateNames.map(() => '?').join(',');
  const nodeRes = await db.execute(
    `SELECT id, name, type FROM kg_nodes WHERE name IN (${placeholders})`,
    candidateNames
  );
  // ... 后续边查询不变
}
```

**辅助函数 `extractCandidateNames`**:
- 策略 1 (轻量): 利用文本中出现的已知节点名做精确匹配 → `WHERE name IN (...)`
- 策略 2 (中期): 在向量入库时同步建立 FTS 索引，查询时用 FTS5 MATCH
- 策略 3 (远期): 调用 Embedding 做实体链接 (成本较高)

### 3.2 边去重补全 agent_id 维度

**文件**: `src/lib/rag/graph-store.ts` (L257-262)

**改造**:
```typescript
// createEdge 去重查询增加 agent_id 维度
const existing = await db.execute(
  `SELECT id, weight FROM kg_edges 
   WHERE source_id = ? AND target_id = ? AND relation = ?
   AND (doc_id = ? OR (doc_id IS NULL AND ? IS NULL))
   AND (session_id = ? OR (session_id IS NULL AND ? IS NULL))
   AND (agent_id = ? OR (agent_id IS NULL AND ? IS NULL))`,
  [sourceId, targetId, relation, 
   docId || null, docId || null,
   sessionId, sessionId,
   agentId || null, agentId || null]
);
```

### 3.3 kgAccumulator 排除持久化

**文件**: `src/store/rag-store.ts`

**改造**: 在 persist 配置中排除 `kgAccumulator` 字段:
```typescript
// persist 配置中增加 partialize
partialize: (state) => {
  const { kgAccumulator, ...rest } = state;
  return rest;
}
```

---

## Phase 4: 实体消歧与质量治理 (优先级 P2)

### 4.1 基于 doc_id 的同名实体隔离

**文件**: `src/lib/rag/graph-store.ts`

**现状**: `kg_nodes` 表 `UNIQUE(name)` 约束导致同名不同义实体强制合并。

**改造方案 — 两阶段消歧**:

```
阶段 1 (本 Phase): 放宽唯一约束
  - 移除 kg_nodes 的 UNIQUE(name) 约束
  - 改为 UNIQUE(name, doc_id) 或 UNIQUE(name, disambiguation_tag)
  - upsertNode 时:
    - 同名 + 同 doc_id → 合并
    - 同名 + 不同 doc_id → 创建独立节点，metadata 中记录别名来源
  - KG 检索时: 用名称模糊匹配，展示所有候选节点

阶段 2 (远期): LLM 消歧
  - 当检测到同名节点来自不同文档时
  - 调用 LLM 判断是否为同一实体
  - 是 → 合并; 否 → 保持独立，附加 disambiguation 描述
```

**DB Migration**:
```sql
-- 移除旧的 UNIQUE 约束 (SQLite 需要重建表)
-- 创建新表 + 数据迁移 + 重命名
-- 新约束: UNIQUE(name, disambiguation)  where disambiguation 可为 NULL
ALTER TABLE kg_nodes ADD COLUMN disambiguation TEXT;
-- 注意: SQLite 不支持 DROP CONSTRAINT，需要重建表
```

### 4.2 Micro-Graph 质量评估

**新增**: `src/lib/rag/kg-quality.ts`

```typescript
interface KGQualityMetrics {
  nodeCount: number;
  edgeCount: number;
  avgWeight: number;
  // 一跳连通率: 有至少一条边的节点 / 总节点数
  connectivity: number;
  // 去重率: 本次新发现的关系 / 总抽取关系
  noveltyRate: number;
}

export function assessQuality(result: MicroGraphResult, existingGraph): KGQualityMetrics;

// 用途:
// 1. 低质量结果 (connectivity < 0.3) 不沉淀到全局图谱
// 2. 高 noveltyRate 的结果标记为"高价值发现"
// 3. 评估不同 Prompt 的抽取质量差异
```

---

## Phase 5: 配置集成与 UI 适配 (优先级 P3)

### 5.1 RagConfiguration 类型扩展

**文件**: `src/types/chat.ts`

```typescript
// 新增字段:
jitTimeoutMs?: number;        // JIT 抽取超时 (默认 5000ms)
jitMaxChunks?: number;        // JIT 输入最大 chunk 数 (默认 3)
jitMaxCharsPerChunk?: number; // 每个 chunk 最大字符数 (默认 2000)
jitCacheTTL?: number;         // JIT 缓存 TTL 秒数 (默认 3600)
kgDomainHint?: string;        // 领域自适应标签 (fiction/academic/technical/dialogue)
kgFreeMode?: boolean;         // 启用自由实体识别模式 (默认 false)
```

### 5.2 Settings UI 适配

**文件**: `src/features/settings/screens/RagAdvancedSettings.tsx`

- 策略选择卡片中 `on-demand` 的描述更新为 "JIT 按需建图: 查询时动态抽取微型图谱"
- 新增 JIT 参数配置区域 (超时、缓存 TTL 等)
- 新增"自由实体识别"开关

---

## 风险防控总览

| 风险点 | 影响范围 | 缓解措施 | 归属 Phase |
|--------|---------|---------|-----------|
| JIT 延迟增加 (2-5s) | 用户体验 | Promise.race 超时 + 缓存命中免调用 | Phase 2 |
| JIT Token 成本转移 | 长期运营 | 输入截断 (6000 chars cap) + 轻量 Prompt | Phase 2 |
| 重复抽取浪费 | Token + 延迟 | LRU 缓存 + chunk_ids_hash 去重 | Phase 2 |
| 沉淀失败丢失图谱 | 数据完整性 | 异步 + 重试 + 不影响当前请求 | Phase 2 |
| 全表扫描性能退化 | 检索速度 | 定向查询替代全表扫描 | Phase 3 |
| 同名实体误合并 | 图谱质量 | doc_id 维度隔离 + 消歧列 | Phase 4 |
| 低质量图谱污染 | 全局图谱 | 质量评估门控 + source_type 标记 | Phase 4 |
| kgAccumulator 状态膨胀 | AsyncStorage | 从持久化中排除 | Phase 3 |

---

## 实施时间线

```
Week 1: Phase 1 (泛用性增强)
  - Day 1-2: entityTypes 可选化 + Prompt 改造
  - Day 3: resolveType 修复
  - Day 4-5: 领域自适应 Prompt + 验证

Week 2: Phase 2 (JIT Micro-Graph 核心)
  - Day 1-2: micro-graph-extractor.ts 核心 + 超时设计
  - Day 3: 缓存层 (kg_jit_cache 表 + LRU)
  - Day 4: 异步沉淀 + 事务批量写入
  - Day 5: MemoryManager 集成 + 端到端验证

Week 3: Phase 3 (性能与正确性)
  - Day 1-2: 全表扫描消除
  - Day 3: 边去重 agent_id 补全
  - Day 4: kgAccumulator 排除持久化
  - Day 5: 回归测试

Week 4: Phase 4-5 (质量治理 + UI)
  - Day 1-2: 实体消歧 (doc_id 隔离)
  - Day 3: 质量评估模块
  - Day 4: RagConfiguration 扩展 + Settings UI
  - Day 5: 全流程集成验证
```

---

## 依赖关系图

```
graph TD
    P1_1[P1.1 entityTypes 可选化] --> P2_1[P2.1 JIT 核心管线]
    P1_2[P1.2 resolveType 修复] --> P2_1
    P1_3[P1.3 领域 Prompt] --> P2_1
    P2_1 --> P2_2[P2.2 缓存标记机制]
    P2_1 --> P2_3[P2.3 异步沉淀]
    P2_2 --> P3_1[P3.1 消除全表扫描]
    P2_3 --> P3_1
    P3_1 --> P4_1[P4.1 实体消歧]
    P3_2[P3.2 边去重补全] --> P4_1
    P4_1 --> P5_1[P5.1 配置扩展]
    P4_2[P4.2 质量评估] --> P5_1
    P5_1 --> P5_2[P5.2 UI 适配]
```

---

## 验收标准

### 功能验收
- [ ] 不预设 kgEntityTypes 时，小说/论文/对话均可产出有意义的实体和关系
- [ ] `on-demand` 策略下，首次查询触发 JIT 抽取，二次相同查询命中缓存
- [ ] JIT 抽取超时不影响主 RAG 流程
- [ ] Micro-Graph 自动沉淀到全局图谱，可通过可视化页面查看

### 性能验收
- [ ] KG 检索阶段耗时 < 100ms (全表扫描消除后)
- [ ] JIT 抽取整体耗时 < 5s (含 LLM 调用)
- [ ] 缓存命中时 KG 阶段耗时 < 50ms
- [ ] 1000 节点规模下图谱可视化流畅

### 正确性验收
- [ ] 不同 Agent 产生的同名关系不被错误合并
- [ ] JIT 标记的节点可被全量抽取升级
- [ ] 同名不同义实体不被强制合并 (Phase 4 后)
