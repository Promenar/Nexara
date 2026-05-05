# B1-D3 迁移完整性审计报告

> **审计时间**: 2026-05-04 09:44
> **审计范围**: OpenCode 自动迁移 B1→D3 全部产出
> **审计基准**: 原 TS 源码 `src/lib/llm/`, `src/lib/rag/`, `src/store/chat/`, `lib/db/`
> **对比策略**: 模块级 1:1 对齐度 + 架构合理性独立评估

---

## 一、总体数据概览

| 维度 | 数量 |
|------|------|
| 主源码文件 (main) | 93 |
| 测试文件 (test) | 23 |
| 总计 Kotlin 文件 | **116** |

---

## 二、模块级对齐度评分

### B 阶段：SSE 解析与响应规范化 — **92分 (A)**

| TS 源文件 | Kotlin 目标 | 对齐度 |
|-----------|------------|--------|
| `stream-parser.ts` (17.13KB) | `StreamParser.kt` (19.14KB) | 95% |
| `response-normalizer.ts` (7.09KB) | `ResponseNormalizer.kt` (7.81KB) | 90% |
| `error-normalizer.ts` (7.18KB) | `ErrorNormalizer.kt` (8.16KB) | 95% |
| `stream-buffer.ts` (6.03KB) | `StreamBufferManager.kt` (4.48KB) | 85% |
| `message-formatter.ts` + `thinking-detector.ts` + `patterns.ts` + `types.ts` | 合并/对应 | 90% |

### C 阶段：三协议 Provider — **90分 (A)**

| TS 源文件 | Kotlin 目标 | 对齐度 |
|-----------|------------|--------|
| `openai.ts` + `openai-compatible.ts` | `OpenAIProtocol.kt` | 85% |
| `vertexai.ts` + `gemini.ts` | `VertexAIProtocol.kt` | 80% |
| *(新增)* | `AnthropicProtocol.kt` | 100% |
| `factory.ts` | `LlmProvider.kt` + `LlmProtocol.kt` | 95% |

### D 阶段：集成+RAG+数据 — **82分 (B)**

#### D1: ChatStore 拆分 → 6 Manager
- 6 个 Manager 全部完成（Message/Session/Approval/Context/Tool/PostProcessor）
- ChatState + ChatModels 完整域模型（Session 25+字段, Message 30+字段）
- ⚠️ `index.ts` 编排入口未迁移 → 无 ViewModel 串联

#### D2: RAG 模块 (16 TS → 14 Kotlin, 88% 覆盖率)
- 核心管线全部完成：VectorStore → EmbeddingClient → KeywordSearcher(FTS5) → Reranker → GraphStore
- ❌ `image-service.ts` 未迁移

#### D3: Room 数据层 (100% 覆盖率)
- 16 Entity + 15 DAO + 4 Repository + Converters + NexaraDatabase

---

## 三、🔴 关键缺陷

### C1. 双状态系统（最严重）
```
ChatScreen → NexaraStateStore (legacy, 5字段) + SseClient (直连)
所有 Manager → ChatStore (new, 30+字段) ← 未被 UI 调用
```
**影响**: Manager 层目前为"死代码"。用户发消息走遗留路径，完全绕过新架构。

### C2. 无 ViewModel 层
缺少 `ChatViewModel`/`RagViewModel`/`AgentHubViewModel`，Compose UI 无法接入 Manager 层。

### C3. RAG UI 为静态骨架
三个 RAG 页面无数据绑定、无 ViewModel、硬编码展示。

### C4. PostProcessor.archiveToRag() 空实现
RAG 归档管线断路，未调用 VectorizationQueue。

### C5. image-service.ts 未迁移
图片向量化管道缺失。

---

## 四、总体评分

| 维度 | 分数 | 评级 |
|------|------|------|
| B: SSE 解析 | 92 | A |
| C: 三协议 | 90 | A |
| D: 集成+RAG+数据 | 82 | B |
| **后端逻辑总分** | **88** | **A-** |
| 前后端对接 | 15 | F |

---

## 五、前后端对接规划

### Phase 1: ViewModel 桥梁搭建 (P0)
- P1-1: `ChatViewModel` — 串联 6 Manager + 订阅 ChatState
- P1-2: `RagViewModel` — 串联 RAG 服务栈
- P1-3: `AgentHubViewModel` — Agent 列表管理
- P1-4: `SessionListViewModel` — 会话列表管理

### Phase 2: 消除双状态系统 (P0)
- P2-1: ChatScreen → ChatViewModel 重构
- P2-2: AgentHubScreen → AgentHubViewModel
- P2-3: AgentSessionsScreen → SessionListViewModel
- P2-4~6: 删除 NexaraStateStore / SseClient / 旧 Models.kt

### Phase 3: ChatScreen 接入真实管线 (P0) ← **MVP 里程碑**
```
用户输入 → ChatViewModel.sendMessage()
  → ContextBuilder → LlmProvider(三协议) → StreamParser
  → MessageManager(实时UI) → PostProcessor(标题/统计/RAG)
```

### Phase 4: RAG UI 激活 (P1)
- 接通 RagViewModel + 修复 PostProcessor.archiveToRag()

### Phase 5: 补齐缺失模块 (P2)
- model-prompts, provider-formatters, message-preprocessor, SkillRegistry, RAG 测试

### 推荐执行顺序
```
Phase 1 → Phase 2 → Phase 3 (MVP) → Phase 4 → Phase 5
```

### OpenCode 会话规划
| 会话 | 阶段 | 模型推荐 |
|------|------|---------|
| Session 1 | Phase 1+2 | GLM-5 |
| Session 2 | Phase 3 | GLM-5 |
| Session 3 | Phase 4 | DeepSeek V3.2 |
| Session 4-5 | Phase 5 | Kimi K2.5 / MiniMax |

---

*审计完成。后端逻辑迁移质量优秀（88分），核心差距集中在 ViewModel/UI 对接层。*
