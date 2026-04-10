# Nexara 核心接口与服务定义

> **角色**: 逻辑层的接口契约
> **状态**: 实时同步 (`src/lib`)
> **法则**: 对服务层/LLM 接口的重大修改**必须**同步更新此处。

---

## 1. LLM 抽象层 (`src/lib/llm`)

### 1.1 `LlmClient` 接口
*所有 AI 供应商（OpenAI, Gemini, Vertex, 本地模型）的统一契约*

```typescript
interface LlmClient {
  /**
   * 实时流式对话。
   * 处理 SSE 解析并统一各渠道的增量数据。
   */
  streamChat(
    messages: ChatMessage[],
    onChunk: (chunk: StreamChunk) => void,
    onError: (err: Error) => void,
    options?: ChatMessageOptions
  ): Promise<void>;

  /**
   * 单次对话，用于验证或后台任务。
   * 应用场景：工具调用循环、摘要生成、标题生成。
   */
  chatCompletion(
    messages: ChatMessage[],
    options?: any
  ): Promise<CompletionResponse>;

  /**
   * 连接健康检查。
   */
  testConnection(): Promise<{ success: boolean; latency: number; error?: string }>;
}
```

### 1.2 核心数据结构
```typescript
interface ChatMessage {
  role: 'system' | 'user' | 'assistant' | 'tool';
  content: string | Array<ContentPart>;
  reasoning?: string;         // DeepSeek/Gemini 思维链内容
  thought_signature?: string; // Gemini 2.0 思考签名
  tool_calls?: ToolCall[];    // 待执行的工具调用
  tool_call_id?: string;      // 结果映射 ID
}

interface StreamChunk {
  content: string;            // 增量文本
  reasoning?: string;         // 增量思维链文本
  done: boolean;
  citations?: Citation[];     // 搜索/联网引用文献
}
```

---

## 2. RAG 系统 (`src/lib/rag`)

### 2.1 `VectorStore` (向量存储)
*管理嵌入向量与相似度检索 (SQLite)。*

```typescript
class VectorStore {
  /**
   * 暴力余弦相似度检索。
   * 支持通过会话 ID、文档 ID 或类型进行过滤。
   */
  async search(
    queryEmbedding: number[],
    options: {
      limit?: number;
      threshold?: number;
      filter?: { docId?: string; sessionId?: string; type?: string }
    }
  ): Promise<SearchResult[]>;

  async addVectors(vectors: VectorRecord[]): Promise<void>;
  async deleteDocumentVectors(docId: string): Promise<void>;
}
```

### 2.2 `GraphStore` (图存储)
*管理知识图谱（节点与边）。*

```typescript
class GraphStore {
  /**
   * 插入或更新节点。
   * 如果按名称匹配到节点，则执行元数据合并。
   */
  async upsertNode(
    name: string, 
    type: string, 
    metadata: any
  ): Promise<string>;

  /**
   * 创建有向边。
   */
  async createEdge(
    sourceId: string, 
    targetId: string, 
    relation: string
  ): Promise<string>;

  /**
   * 获取可视化图数据。
   * 支持根据文档或会话上下文进行过滤。
   */
  async getGraphData(
    docIds?: string[], 
    sessionId?: string
  ): Promise<{ nodes: KGNode[]; edges: KGEdge[] }>;
}
```

### 2.3 `VectorizationQueue` (向量化队列)
*沉重 RAG 任务的异步后台处理。*

```typescript
class VectorizationQueue {
  /**
   * 将文档加入队列：切片 -> 向量化 -> 图谱提取。
   * 非阻塞操作。
   */
  async enqueueDocument(
    docId: string, 
    title: string, 
    content: string
  ): Promise<void>;

  /**
   * 将对话轮次归档至长期记忆。
   */
  async enqueueMemory(
    sessionId: string,
    userContent: string,
    aiContent: string
  ): Promise<void>;
}
```

### 2.4 原生向量搜索模块 (`src/native/VectorSearch`)
*跨平台原生向量相似度计算模块，支持自动降级。*

```typescript
/**
 * 向量搜索结果接口
 */
interface VectorSearchResult {
  id: string;
  similarity: number;
}

/**
 * 向量搜索候选接口
 */
interface VectorSearchCandidate {
  id: string;
  embedding: Float32Array;
}

/**
 * 执行向量相似度搜索
 * @param queryEmbedding 查询向量
 * @param candidates 候选向量列表
 * @param threshold 相似度阈值
 * @param limit 结果数量限制
 * @returns 排序后的搜索结果
 */
export async function searchVectors(
  queryEmbedding: Float32Array,
  candidates: VectorSearchCandidate[],
  threshold: number = 0.7,
  limit: number = 5
): Promise<VectorSearchResult[]>;

/**
 * 检测原生模块是否可用
 * @returns 是否可用
 */
export function isNativeModuleAvailable(): boolean;
```

---

## 3. WebView 资源管理 (`src/lib/webview-assets.ts`)

### 3.1 本地资源解析

```typescript
/**
 * 解析本地打包的 WebView JS 库 URI
 * 使用 expo-asset 加载 Metro 打包的 .bundle 文件
 * @param lib 'echarts' | 'mermaid'
 * @returns file:// URI 或 null
 */
async function resolveLocalLibUri(lib: LibName): Promise<string | null>;
```

### 3.2 CDN 降级标签生成

```typescript
/**
 * 生成带 CDN 降级的 <script> 标签
 * 本地优先 → onerror 时动态插入 CDN script
 * @param lib 库名称
 * @param localUri 已解析的本地 URI（可为 null）
 * @param cdnUrl CDN fallback 地址
 */
function scriptTagWithFallback(
  lib: LibName,
  localUri: string | null,
  cdnUrl: string,
): string;
```

**本地资源映射**:
| 库 | 本地文件 | CDN fallback |
| :--- | :--- | :--- |
| echarts | `assets/web-libs/echarts.min.bundle` | `cdn.jsdelivr.net/npm/echarts@5.5.0` |
| mermaid | `assets/web-libs/mermaid.min.bundle` | `cdn.jsdelivr.net/npm/mermaid@10.9.0` |
