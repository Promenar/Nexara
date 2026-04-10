# Nexara RAG 系统架构文档

## 1. 核心架构概览 (Architecture Overview)

Nexara 的 RAG 系统采用 **混合检索 (Hybrid Retrieval)** + **双层记忆 (Dual-Layer Memory)** + **本地优先 (Local-First)** 的架构设计。核心组件包括向量数据库 (SQLite + Vector Ext)、知识图谱 (Knowledge Graph)、会话管理器和后台向量化队列。

```mermaid
graph TD
    User["用户交互"] --> API["API 层 (ChatEngine)"]
    API --> MemoryManager["MemoryManager (检索核心)"]
    
    subgraph "Ingestion Pipeline (写入流程)"
        Doc["文档导入"] --> Queue["VectorizationQueue (后台队列)"]
        Chat["对话记录"] --> Queue
        Queue --> Splitter["TextSplitter"]
        Splitter --> Embed["EmbeddingClient"]
        Embed --> VectorStore[("VectorStore (SQLite)")]
        
        Queue --> KG_Extractor["GraphExtractor (LLM)"]
        KG_Extractor --> KG_Store[("KnowledgeGraph (Sqlite)")]
    end

    subgraph "Retrieval Pipeline (读取流程)"
        MemoryManager --> Rewrite["Query Rewriter"]
        Rewrite --> Hybrid["Hybrid Search (Vector + Keyword)"]
        Hybrid --> Rerank["Reranker (BGE-M3)"]
        Rerank --> Context["上下文组装"]
        
        MemoryManager --> KG_Query["KG Retrieval (一跳扩展)"]
        KG_Query --> Context
    end

    Context --> LLM["LLM 生成"]
```

## 2. 关键流程详解

### 2.1. 数据摄入与向量化 (Ingestion Flow)

所有耗时的各种处理（文档解析、分块、向量化、图谱提取）均通过 `VectorizationQueue` 异步执行，确保主线程 UI 不阻塞。

```mermaid
flowchart LR
    A["触发源: 导入/对话"] --> B{"任务类型"}
    B -- "文档" --> C["TextSplitter (递归字符/Trigram)"]
    B -- "记忆" --> D["TrigramSplitter (小块)"]
    
    C & D --> E["EmbeddingClient (本地/在线模型)"]
    E --> F["写入 SQLite (vectors表)"]
    
    B -- "知识图谱(Session)" --> G["累积 buffer"]
    G -- "阈值触发" --> H["GraphExtractor"]
    H -- "提取实体/关系" --> I["写入 kg_nodes / kg_edges"]
    
    style F fill:#dbf,stroke:#333
    style I fill:#dbf,stroke:#333
```

### 2.2. 混合检索与上下文构建 (Retrieval Flow)

检索过程实现了 "Recall -> Filter -> Rerank" 的经典漏斗模式，并结合了知识图谱的"读时增强"。

```mermaid
sequenceDiagram
    participant User
    participant MM as MemoryManager
    participant VS as VectorStore
    participant KW as KeywordSearch
    participant Rerank as Reranker
    participant KG as KnowledgeGraph

    User->>MM: 发送查询 "Project X 的截止日期?"
    
    rect rgb(240, 248, 255)
        Note right of MM: 阶段 1: 查询重写
        MM->>MM: LLM Rewrite -> ["Project X deadline", "When is Project X due?"]
    end
    
    rect rgb(255, 250, 240)
        Note right of MM: 阶段 2: 混合召回 (并行)
        par Vector Search
            MM->>VS: Cosine Similarity (Query Embedding)
        and Keyword Search
            MM->>KW: BM25/FTS5 Search
        end
        MM->>MM: RRF Fusion (加权融合)
    end
    
    rect rgb(255, 245, 245)
        Note right of MM: 阶段 3: 重排序 (Rerank)
        MM->>Rerank: Cross-Encoder Scoring (Top-K)
        Rerank-->>MM: Sorted Results
    end
    
    rect rgb(240, 255, 240)
        Note right of MM: 阶段 4: 知识图谱增强
        MM->>KG: Extract Entities from Chunks -> Query Edges
        Note over KG: 🔍 隐私检查: WHERE doc_id IN (authorized_ids)
        KG-->>MM: 关联关系 (Edges)
    end
    
    MM->>User: 返回最终 Context Block
```

## 3. 触发源映射 (Trigger Components)

| 动作 | 触发源 (Component/Store) | 处理逻辑位置 | 备注 |
| :--- | :--- | :--- | :--- |
| **文档导入** | `DocumentPicker` / `rag-store.ts` | `vectorization-queue.ts` (Type: `document`) | 支持断点续传 |
| **发送消息** | `ChatEngine.ts` | `memory-manager.ts` -> `vectorization-queue.ts` | 消息发送后立即入队 |
| **KG 提取** | `vectorization-queue.ts` | `graph-extractor.ts` | 基于 `kgStrategy` (Full/Summary/OnDemand) |
| **清理向量** | `GlobalRagConfigPanel.tsx` | `vector-store.ts` | 级联删除，支持事务 |
| **预设切换** | `ConfigPanel` (Agent/Global) | `settings-store.ts` | **SSOT**: 使用 `src/lib/rag/constants.ts` |

## 4. 安全与隐私架构

### 4.1. 文档隔离 (Isolation)
*   **Vector Search**: 强制应用 `WHERE doc_id IN (...)` 过滤。全局（Global）文档对所有会话可见，私有（Session）文档仅对所属会话可见。
*   **Knowledge Graph**: (修复后) 边查询强制检查 `doc_id` 归属，防止通过图谱关系泄露私有文档信息。

### 4.2. 原生桥接保护 (Native Bridge Safety)
*   UI 组件调用原生能力（Haptics, Navigation）时，遵循 **10ms 延迟原则**，防止 JS 线程死锁或 UI 冻结。

---
*文档版本: v1.0 | 上次更新: 2026-01-20*
