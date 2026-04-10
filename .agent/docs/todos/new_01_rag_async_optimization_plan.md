# RAG 异步优化与流式体验提升方案

**版本**: 1.2 (Merged Audit & Prefetch)  
**状态**: 待执行 (Pending)  
**依赖**: 原拟依赖的 [003_multithread_architecture_plan](./003_multithread_architecture_plan.md) 已废弃，现基于单线程架构优化。

## 1. 核心痛点审计 (Audit Results)

经过代码审计，确认当前系统在 RAG 与流式输出上存在以下瓶颈：

| 问题现象 | 核心技术原因 | 严重程度 |
| :--- | :--- | :--- |
| **RAG 检索等待时间长** | **同步阻塞链**：LLM 请求被 `MemoryManager.retrieveContext` 强阻塞，包含串行的 `Rewrite` -> `Embedding` -> `Rerank` 流程。 | 🔴 高 |
| **首字延迟 (TTFT) 高** | **Query Rewrite**：默认同步调用大模型进行重写，增加了 1-3 秒的固定开销。 | 🔴 高 |
| **流式输出卡顿** | **过度节流 (Over-Throttling)**：UI 更新强制限制为 200ms (5fps)，导致体感不流畅。 | 🟠 中 |

---

## 2. 核心设计方案

### 2.1 感知配置的预取 (Config-Aware Prefetch)
在用户输入期间（Debounce 800ms）启动后台预取，且严格遵循用户的功能开关：
*   **Embedding (Required)**：核心提速点，必须预取。
*   **Rerank (Optional)**：若开启则预取 Top 10，平衡成本与质量。
*   **Rewrite (Conditional)**：默认关闭同步 Rewrite，预取阶段仅使用原始 Query 或本地轻量级处理。

### 2.2 流式体验平滑化 (Streaming Smoothing)
*   **降低节流阈值**：将 `CONTENT_UPDATE_INTERVAL` 从 **200ms** 降低至 **33ms** (30fps)。
*   **异步化 RAG 初始化**：在 `ChatStore` 中尽早创建消息对象，避免 UI 在检索期间完全无响应。

---

## 3. 详细架构变更

### 3.1 状态管理 (`src/store/rag-store.ts`)
新增 `prefetch` 状态切片，记录配置快照以保证一致性。

```typescript
interface RagState {
  prefetch: {
    query: string;
    status: 'idle' | 'loading' | 'ready';
    context?: string;
    references?: any[];
    timestamp: number;
    configSnapshot?: { enableRewrite: boolean; enableRerank: boolean };
  }
}
```

### 3.2 核心逻辑 (`src/lib/rag/memory-manager.ts`)
改造 `retrieveContext`，将其拆解为原子步骤（Search, Rerank 等），以便在 `prefetchContext` 中动态组合。

---

## 4. UI 交互调整

*   **输入框感知**：在 `ChatInput` 中挂载 Debounce 监听器。
*   **消费逻辑**：在发送消息时 (`consumePrefetch`) 检查 Query 与 Config 的一致性。若不匹配，则回退到全量检索，确保结果正确。

---

## 5. 实施步骤规划

### Phase 1: 即时优化 (Quick Wins)
1.  **[x] 参数调整**：在 `src/store/chat-store.ts` 中将 `CONTENT_UPDATE_INTERVAL` 降至 33ms。
2.  **[x] 配置默认值**：将 `enableQueryRewrite` 默认设为关闭。

### Phase 2: 基础设施
3.  **RagStore**: 实现 `startPrefetch` 与配置快照逻辑。
4.  **MemoryManager**: 拆分原子检索步骤。

### Phase 3: UI 集成
5.  **ChatInput**: 集成状态指示器与预取触发任务。
6.  **ChatStore**: 在 `generateMessage` 中实现预取消费逻辑。

---

## 6. 风险控制
*   **Token 消耗**：通过 Min Length (8字) 和 Debounce (800ms) 严格控制触发频率。
*   **一致性**：若用户在输入期间快速切换 RAG 选项，结果必须以最终发送时刻的配置为准。
