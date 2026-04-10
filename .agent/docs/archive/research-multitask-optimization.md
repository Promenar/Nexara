# 多任务衔接与提示词优化技术调研报告

本报告基于对 LangChain、AutoGPT 等成熟 Agent 框架及 DeepSeek、Qwen、Moonshot 等模型社区的深度调研，总结了保障多任务准确衔接与循环执行的关键技术手段。

## 1. 核心任务编排模式 (Orchestration Patterns)

### 1.1 ReAct (Reasoning + Acting) 循环
这是处理多步任务的基石，已被 LangChain 等框架广泛采用。
- **机制**：强制模型执行 `Though (思考) -> Action (行动) -> Observation (观察)` 的显式循环。
- **优化**：在 System Prompt 中强制要求输出 `<!-- THINKING -->` 块，让模型在行动前先进行逻辑推演，显著降低幻觉。

### 1.2 动态状态注入 (Dynamic State Injection)
AutoGPT 和 BabyAGI 的核心技术，用于解决长任务中的"迷失"问题。
- **技术手段**：在每一轮对话的 System Prompt 中，动态插入当前的 **[CURRENT TASK STATE]**。
- **关键字段**：
  - `Task List`: 待办/进行中/已完成的步骤列表。
  - `Last Action Result`: 上一步工具调用的直接结果（避免模型凭空猜测上一步发生了什么）。
  - `Constraints`: 当前阶段的限制条件（如"只剩3次尝试机会"）。

### 1.3 自我反思与修正 (Self-Correction / Reflexion)
用于保障抓取和执行的准确性。
- **Reflexion 模式**：在任务失败或结果不符合预期时，触发一个"反思"步骤，让模型分析错误原因，并生成一段"从错误中学习"的文本，注入到下一次 Prompt 中。
- **双重验证**：对于关键数据（如抓取的内容），引入 Critic Agent（或同一模型的 Critic 模式）进行校验。

---

## 2. 国内外模型特定优化实践

### 2.1 DeepSeek & Qwen (结构化工程派)
这两类模型在处理结构化指令时表现最优。

| 优化维度 | DeepSeek (Coder/Chat/R1) | Qwen (Alibaba) |
| :--- | :--- | :--- |
| **提示词格式** | **Markdown 结构化**<br>使用 `###` 标题分隔不同模块，指令越简洁越好。 | **角色沉浸 (Role Priming)**<br>强烈的角色设定（如"你是一个资深爬虫工程师"）能显著激活特定能力。 |
| **工具调用** | **Strict Mode (Schema)**<br>依赖严格的 JSON Schema 定义，偏好零样本 (Zero-shot) 直接调用。 | **分隔符隔离**<br>使用清晰的分隔符（如 `---`）将工具定义与上下文隔离。 |
| **续杯策略** | **禁止回溯指令**<br>需明确告知"检查历史记录"，否则容易忽略已完成的隐式步骤。 | **阶段性总结**<br>在多轮对话中，要求模型先输出一段"当前进度摘要"。 |

### 2.2 Moonshot (Kimi) & GLM (长文本/角色派)
擅长处理海量上下文，但容易注意力发散。

| 优化维度 | Moonshot (Kimi) | GLM (ChatGLM) |
| :--- | :--- | :--- |
| **提示词格式** | **详细自然语言 + 渐进式披露**<br>像给员工写文档一样详细，避免过于抽象的指令。 | **中文指令优先**<br>使用高质量的中文指令效果远优于英文。 |
| **长任务护栏** | **Reference Anchor (引用锚点)**<br>要求回复时引用原文片段，防止长文中产生幻觉。 | **思维链 (CoT)**<br>强制要求"请一步步思考"，能提升多步推理的稳定性。 |

### 2.3 LobeHub (LobeChat) 开源实践
作为顶级的开源 Agent 框架，LobeChat 在多模型适配与插件编排上有独特设计。

| 核心特性 | 技术实现 |
| :--- | :--- |
| **插件提示词工程** | 采用 **"Role + Objective + Context + Format"** 四段式结构。对于插件调用，System Prompt 明确指定 AI 在"插件返回后"应如何处理数据（如"总结插件返回的搜索结果"）。 |
| **Model Context Protocol (MCP)** | 基于标准化的 MCP 协议连接插件，实现了工具定义的标准化，使得同一套插件可以轻松适配 OpenAI、Claude、Gemini 等不同模型。 |
| **结构化输出** | 虽未强制所有输出结构化，但强烈推荐开发者使用 Markdown 格式化插件返回内容，利用 LLM 对 Markdown 的天然亲和力提升可读性。 |
| **多模型适配层** | 针对不同模型厂商（如 Gemini 不支持连字符工具名）做了**中间件适配**，自动转换工具名称格式，保障了跨模型调用的稳定性。 |

---

## 3. 准确性保障技术手段

### 3.1 结构化输出验证 (Structured Output Validation)
- **技术**：使用 Pydantic 或 Zod 定义严格的数据 Schema。
- **流程**：模型输出 -> 校验 Schema -> 若失败，将错误信息回传给模型重试 (Repair Loop)。
- **应用**：在网页抓取任务中，定义 `ExtractResult` 结构，强制模型填充，而非自由文本生成。

### 3.2 显式上下文清理 (Explicit Context Pruning)
- **问题**：随着任务进行，上下文越来越长，噪音增多。
- **解决**：
  - **滑动窗口**：仅保留最近 N 轮详细对话。
  - **重要信息摘要**：将早期的详细步骤压缩为"已完成：步骤1（结果：成功）"的摘要形式。

---

## 4. 我们的实现现状与优化建议

### ✅ 已实现 (Current Implementation)
1. **模型分层提示词**：已在 `model-prompts.ts` 中实现了针对 DeepSeek/Qwen 的结构化 Prompt 和 Moonshot 的详细 Prompt。
2. **任务状态管理**：`manage_task` 工具强制了步骤的显式管理。
3. **Draft Persistence**：解决了输入框草稿丢失问题。

### 🚀 建议下一步优化 (Recommendations)
1. **引入 "Self-Correction" 循环**：在工具执行失败（如抓取为空）时，自动触发一次"分析原因并重试"，而不是直接报错。
2. **增强 DeepSeek 的 "Strict Mode"**：在工具定义中尽可能使用严格的类型限制，减少参数错误的概率。
3. **上下文摘要机制**：当 Token 数超过阈值时，自动触发"总结当前进度"，清理历史消息，只保留 Summary 和最新几轮对话（针对非 Kimi 模型）。
