# OpenCode 自循环迁移 — 启动指令模板

> 将以下内容复制到 OpenCode 的 `build` 模式中执行。
> 每次启动一个迁移任务时，复制对应的指令块。

---

## 通用前缀（每次启动时附带）

```
你是 Nexara 项目的 Kotlin 原生迁移 Agent。请严格按照 AGENTS.md 中的规范执行迁移。

核心规则：
1. 先读取 TS 源文件，理解其完整逻辑
2. 编写对应的 Kotlin 实现
3. 运行 .\gradlew.bat compileDebugKotlin 验证编译通过
4. 运行 .\gradlew.bat test 验证单元测试通过
5. 如果失败，分析错误原因，修复代码，重新编译测试
6. 循环直到全部通过
7. 编译成功后用 git add + git commit 提交

TS 源码参考位置（只读）：../src/
Kotlin 输出位置：app/src/main/java/com/promenar/nexara/
测试输出位置：app/src/test/java/com/promenar/nexara/

测试配置文件：test-resources/llm-test-config.json（已被 .gitignore 排除）
  - openai_compatible: MiniMax M2.7 的 OpenAI 兼容端点
  - anthropic_compatible: MiniMax M2.7 的 Anthropic 兼容端点
  - vertexai: Google Vertex AI（私钥在 test-resources/vertexai/test.json，模型 gemini-3-flash-preview）

Provider 架构为三协议 + 可扩展：
  - OpenAIProtocol: 覆盖所有 OpenAI 兼容厂商（OpenAI/DeepSeek/Moonshot/GLM/MiniMax）
  - AnthropicProtocol: 覆盖 Anthropic 兼容端点（含 thinking 模式）
  - VertexAIProtocol: Google Vertex AI（含 RSA-JJWT 认证）
  - 后续扩展只需新增 LlmProtocol 实现即可
```

---

## 阶段 B：确定性模块（逐文件执行）

### B1: StreamParser

```
迁移 ../src/lib/llm/stream-parser.ts 到 app/src/main/java/com/promenar/nexara/data/remote/StreamParser.kt

这是一个增量式 SSE 流解析器，需要处理：
- XML 工具调用标签（<tool_call_xml>...</tool_call_xml>）
- Plan 块（<plan>...</plan>）
- 代码围栏（```language...```）的跨 chunk 拼接
- 普通文本的增量累积

要求：
1. 使用 Kotlin 协程 Flow<String> 作为增量输出
2. 用状态机替代 JS 的正则匹配，避免 O(N²) 回溯
3. 编写单元测试 app/src/test/java/com/promenar/nexara/data/remote/StreamParserTest.kt
4. 测试用例应覆盖：普通文本、跨 chunk 标签、代码围栏拼接、XML 工具调用嵌套
5. 运行 .\gradlew.bat compileDebugKotlin && .\gradlew.bat test
6. 失败则修复并重试，直到通过
```

### B2: ResponseNormalizer

```
迁移 ../src/lib/llm/response-normalizer.ts 到 app/src/main/java/com/promenar/nexara/data/remote/ResponseNormalizer.kt

将多个 LLM Provider 的响应格式统一为内部标准格式。

要求：
1. 使用 sealed class 定义 NormalizedResponse 类型
2. 编写对比测试，覆盖至少 3 种 Provider 的响应格式
3. 运行测试直到全部通过
```

### B3: ErrorNormalizer

```
迁移 ../src/lib/llm/error-normalizer.ts 到 app/src/main/java/com/promenar/nexara/data/remote/ErrorNormalizer.kt

将多个 LLM Provider 的错误响应归一化为统一的错误类型，包含：
- 错误类型枚举（RateLimit, Auth, Server, Network, Timeout, Unknown）
- 是否可重试的判断
- 建议的退避时间

要求：
1. 使用 sealed class 定义 NormalizedError
2. 编写测试覆盖各 Provider 的典型错误 JSON
3. 运行测试直到全部通过
```

### B4: MessageFormatter

```
迁移 ../src/lib/llm/message-formatter.ts 到 app/src/main/java/com/promenar/nexara/data/remote/MessageFormatter.kt

将内部 ChatMessage 列表格式化为各 Provider 要求的请求体格式（OpenAI / Gemini 等）。

要求：
1. 定义 MessageRole enum（System, User, Assistant, Tool）
2. 编写格式转换测试
3. 运行测试直到全部通过
```

### B5: StreamBufferManager

```
迁移 ../src/lib/llm/stream-buffer.ts 到 app/src/main/java/com/promenar/nexara/data/remote/StreamBufferManager.kt

管理流式内容的缓冲区，负责：
- 思考内容与正文的分离
- 标签边界的抖动处理（跨 chunk 的标签拼接）
- 状态切换时的边界回调

要求：
1. 使用 MutableStateFlow<ParsedContent> 管理状态
2. 编写测试覆盖跨 chunk 标签拼接场景
3. 运行测试直到全部通过
```

### B6: ThinkingDetector

```
迁移 ../src/lib/llm/thinking-detector.ts 到 app/src/main/java/com/promenar/nexara/data/remote/ThinkingDetector.kt

检测多种格式的思考标签：
- <think/>...</think/>
- <thought/>...</thought/>
- <!-- THINKING_START -->...<!-- THINKING_END -->
- 跨 chunk 的标签边界

要求：
1. 使用 Channel<ParseResult> 背压控制
2. 编写测试覆盖所有标签格式 + 跨 chunk 边界
3. 运行测试直到全部通过
```

### B7: ModelSpecs

```
迁移 ../src/lib/llm/model-specs.ts 到 app/src/main/java/com/promenar/nexara/data/model/ModelSpecs.kt

这是一个模型能力数据库，包含：
- 模型名称、Provider、最大 token、支持的功能（工具调用、视觉、流式等）
- 价格信息

要求：
1. 使用 data class 定义 ModelSpec
2. 纯数据映射，直接翻译
3. 编写测试验证关键模型的字段完整性
4. 运行测试直到全部通过
```

---

## 阶段 C：协议层（需读取 test-resources/llm-test-config.json）

### C1: LlmProtocol 接口 + 数据类

```
创建 Provider 架构基础：

1. 创建 app/src/main/java/com/promenar/nexara/data/remote/protocol/LlmProtocol.kt
   - 定义 interface LlmProtocol { val id, sendPrompt(): Flow<StreamChunk>, sendPromptSync() }
   - 定义 data class PromptRequest（messages, model, temperature, maxTokens, tools, stream）
   - 定义 sealed class StreamChunk（TextDelta, ToolCall, Thinking, Error, Done）

2. 创建 app/src/main/java/com/promenar/nexara/data/remote/provider/LlmProvider.kt
   - 聚合层：接收 LlmProtocol 实例，路由请求
   - 提供 builder 工厂方法根据配置创建对应协议实例

3. 编写测试验证数据类序列化/反序列化
4. .\gradlew.bat compileDebugKotlin && .\gradlew.bat test
```

### C2: OpenAIProtocol（用 MiniMax M2.7 测试）

```
参考 ../src/lib/llm/providers/openai-compatible.ts，创建：
app/src/main/java/com/promenar/nexara/data/remote/protocol/OpenAIProtocol.kt

实现 OpenAI 兼容协议（覆盖 OpenAI/DeepSeek/Moonshot/GLM/MiniMax 等所有兼容厂商）：
- POST /v1/chat/completions (stream: true)
- SSE 格式: data: {"choices":[{"delta":{"content":"..."}}]}
- 支持 tool_calls、function_call
- 支持 thinking/reasoning 标签处理（DeepSeek 等模型的特殊输出）

测试：
1. 读取 test-resources/llm-test-config.json 获取 openai_compatible 配置
2. 如果配置未填写（仍为占位文字），则 skip 测试
3. 发送简单 prompt，验证收到至少 1 个 StreamChunk.TextDelta
4. 验证流式完成的 Done 信号

.\gradlew.bat compileDebugKotlin && .\gradlew.bat test
```

### C3: AnthropicProtocol（用 MiniMax M2.7 Anthropic 端点测试）

```
参考 ../src/lib/llm/providers/openai-compatible.ts 中对 Anthropic 格式的处理（如有），
以及网上 Anthropic Messages API 文档，创建：
app/src/main/java/com/promenar/nexara/data/remote/protocol/AnthropicProtocol.kt

实现 Anthropic 兼容协议：
- POST /v1/messages (stream: true)
- SSE 格式: event: content_block_delta, data: {"delta":{"text":"..."}}
- 支持 thinking 模式（thinking block + text block 分离）
- 特殊 header: x-api-key, anthropic-version

测试：
1. 读取 test-resources/llm-test-config.json 获取 anthropic_compatible 配置
2. 如果配置未填写则 skip
3. 测试普通调用和 thinking 模式调用

.\gradlew.bat compileDebugKotlin && .\gradlew.bat test
```

### C4: VertexAIProtocol（用 test.json 私钥测试）

```
参考 ../src/lib/llm/providers/vertexai.ts（40KB，最复杂的 Provider），创建：
app/src/main/java/com/promenar/nexara/data/remote/protocol/VertexAIProtocol.kt

实现 VertexAI 协议：
- Google Auth: 从 test-resources/vertexai/test.json 读取服务账号私钥
- RSA-JWT 签名生成 Access Token
- POST /v1/projects/{project}/locations/{location}/publishers/google/models/{model}:streamGenerateContent
- Gemini 特殊的 SSE 格式解析
- 固定模型: gemini-3-flash-preview

测试：
1. 读取 test-resources/llm-test-config.json 获取 vertexai 配置
2. 读取 test-resources/vertexai/test.json 获取私钥
3. 如果文件不存在或配置未填写则 skip
4. 测试认证流程 + 简单 prompt

.\gradlew.bat compileDebugKotlin && .\gradlew.bat test
```

---

## 阶段 D：集成层

### D1: 数据库层（Room Schema）

```
参考 ../src/lib/db/schema.ts（14KB）和 ../src/lib/db/migration.ts（17KB），
创建 Room 数据库和全部 15 张表：

app/src/main/java/com/promenar/nexara/data/local/db/
├── NexaraDatabase.kt         # @Database 主类
├── dao/                      # DAO 接口
│   ├── SessionDao.kt
│   ├── MessageDao.kt
│   ├── VectorDao.kt
│   ├── KgNodeDao.kt
│   ├── KgEdgeDao.kt
│   └── ...
└── entity/                   # @Entity 数据类
    ├── SessionEntity.kt
    ├── MessageEntity.kt
    ├── VectorEntity.kt       # BLOB FloatArray 向量
    ├── KgNodeEntity.kt
    └── ...

技术选型（参见 AGENTS.md）：
- 向量存储：Room + FloatArray BLOB（纯 Kotlin 余弦相似度）
- 全文检索：Room FTS4 虚拟表
- 知识图谱：Room 三表（kg_nodes, kg_edges, kg_jit_cache）

要求：
1. Schema 与 TS 端一一对应，字段名和类型保持一致
2. 编写 DAO 测试（使用 Room in-memory database）
3. .\gradlew.bat test
```

### D2: Chat Store 拆分迁移

```
将 ../src/store/chat-store.ts（126KB 巨型 Store）拆分迁移为多个 Kotlin ViewModel。

参考已有的 Phase 4b 子模块拆分：
- ../src/store/chat/message-manager.ts → MessageManager.kt
- ../src/store/chat/tool-execution.ts → ToolExecutor.kt
- ../src/store/chat/session-manager.ts → SessionManager.kt
- ../src/store/chat/context-builder.ts → ContextBuilder.kt
- ../src/store/chat/post-processor.ts → PostProcessor.kt
- ../src/store/chat/approval-manager.ts → ApprovalManager.kt

要求：
1. 每个子模块独立迁移，独立测试
2. 使用 ViewModel + StateFlow 管理状态
3. 使用 Room Repository 持久化
4. 逐个编译测试通过后 commit
```

### D3: RAG 流水线 + 知识图谱

```
迁移 ../src/lib/rag/ 目录下的 16 个文件到 Kotlin。

技术选型（参见 AGENTS.md）：
- 向量存储：Room + FloatArray BLOB
- 相似度计算：纯 Kotlin 余弦相似度（无需 C++ TurboModule）
- 全文检索：Room FTS4
- 混合检索：向量 + FTS4 加权融合（RRF 策略）
- Embedding 客户端：Ktor + OpenAI 兼容协议
- 知识图谱存储：Room 三表（kg_nodes, kg_edges, kg_jit_cache）
- 实体抽取：LLM 驱动 JSON Mode（通过 LlmProtocol）
- 查询重写：Kotlin HyDE/Multi-Query 实现
- Reranker：LLM 驱动或 API 调用
- JIT 动态建图：Room + 内存 LRU 缓存

按依赖顺序迁移：
1. text-splitter.ts → rag/TextSplitter.kt
2. trigram-splitter.ts → rag/TrigramSplitter.kt
3. vector-store.ts → rag/VectorStore.kt（Room + 余弦相似度）
4. keyword-search.ts → rag/KeywordSearcher.kt（FTS4）
5. embedding.ts → rag/EmbeddingClient.kt（Ktor + LlmProtocol）
6. reranker.ts → rag/Reranker.kt
7. query-rewriter.ts → rag/QueryRewriter.kt
8. memory-manager.ts → rag/MemoryManager.kt（检索编排器）
9. graph-store.ts → rag/GraphStore.kt（Room 三表）
10. graph-extractor.ts → rag/GraphExtractor.kt（LLM 驱动）
11. micro-graph-extractor.ts → rag/MicroGraphExtractor.kt
12. vectorization-queue.ts → rag/VectorizationQueue.kt

要求：
1. 每个模块独立测试
2. 向量操作使用 Kotlin 直接操作 FloatArray
3. 逐个编译测试通过后 commit
```

---

## 批量执行模板（一次性启动多个文件迁移）

```
按照 AGENTS.md 中定义的阶段 B 迁移顺序，从 B1 到 B7 依次执行迁移。

每完成一个文件的迁移：
1. 编译通过（.\gradlew.bat compileDebugKotlin）
2. 测试通过（.\gradlew.bat test）
3. git add 并 git commit -m "feat: 迁移 [模块名] 从 TS 到 Kotlin"

如果某个文件迁移遇到阻塞（如缺少依赖类型定义），先 commit 已完成部分，然后继续下一个文件。

完成后输出总结：哪些文件迁移成功，哪些有残留问题。
```

---

## 配置文件设置（OpenCode 启动前）

### 1. 填写 LLM 测试配置

编辑 `k:\Nexara\native-ui\test-resources\llm-test-config.json`，填入：
- MiniMax M2.7 的 OpenAI 兼容端点地址、API Key、模型 ID
- MiniMax M2.7 的 Anthropic 兼容端点地址、API Key、模型 ID
- VertexAI 的 Google Cloud Project ID

### 2. 放置 VertexAI 私钥

将你的 VertexAI 服务账号私钥 JSON 文件改名为 `test.json`，
放入 `k:\Nexara\native-ui\test-resources\vertexai\test.json`

### 3. 启动 OpenCode

```bash
cd k:\Nexara\native-ui
opencode
```

---

## 注意事项

1. **不要修改** `../src/` 下的任何 TS 文件（只读参考）
2. **不要修改** `../android/` 下的任何文件（RN 项目，独立维护）
3. 所有新代码都在 `native-ui/` 目录内
4. 遇到不确定的设计决策时，选择与 TS 实现行为一致的方案
5. 每个模块迁移完成后立即 commit，避免丢失进度
6. Provider 迁移简化为三协议：OpenAIProtocol、AnthropicProtocol、VertexAIProtocol
7. 原 7 个 Provider 的模型输出适配逻辑统一移至 parser/ 层处理
8. 测试 API Key 从 test-resources/llm-test-config.json 读取，不用环境变量
9. 向量数据库使用 Room + FloatArray BLOB，知识图谱使用 Room 三表
