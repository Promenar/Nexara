# Agent 核心大脑与任务链路优化方案

基于前期调研与 `manage_task` 的成功试点，本方案旨在解决复杂任务链中的"健忘"、"脆弱"和"中途放弃"三大核心问题，将 Agent 的鲁棒性提升至生产级水平。

## 1. 核心目标
1.  **消除幻觉回溯**：确保模型永远知道"我现在走到哪一步了"（解决 DeepSeek 重复执行问题）。
2.  **零格式错误**：通过 Strict Mode 确保所有工具调用 100% 符合 Schema（解决 JSON 解析红屏）。
3.  **自动错误恢复**：工具调用失败时，自动触发"反思-重试"循环，而不是直接报错停止。

## 2. 技术架构规划

### Phase 1: 认知增强 (Cognitive Anchoring)
**解决"健忘"与"格式错误"**

#### 1.1 动态上下文锚点 (Context Anchoring)
*   **痛点**：长对话中，History 里的状态容易被模型"视而不见"。
*   **实施**: 修改 `src/store/chat/context-builder.ts`。
*   **逻辑**: 在 System Prompt **最顶部**（权重最高区域）开辟动态区块：
    ```markdown
    ### [PRIORITIZED STATE - READ THIS FIRST]
    - **Current Task**: "优化数据库 Schema" (Step 3/5: Running)
    - **Last Action**: `read_file` (Success) -> Found schema.prisma
    - **Immediate Goal**: Update the schema based on requirements.
    ```

#### 1.2 全局 Strict Mode (Structured Outputs)
*   **痛点**：模型输出的 JSON 经常少括号、多逗号，导致 `manage_task` 失败。
*   **实施**: 修改 `src/lib/llm/providers/*`。
*   **逻辑**:
    *   **OpenAI / DeepSeek**: 开启 `response_format: { type: "json_schema", strict: true }`。
    *   **Gemini**: 配置 `generationConfig: { response_mime_type: "application/json" }`。
    *   **Qwen**: 适配其特有的 Tool Call 格式。

---

### Phase 2: 弹性与反思 (Resilience & Reflexion)
**解决"中途放弃"**

#### 2.1 全局工具拦截器 (Global Tool Interceptor)
*   **痛点**：`search_internet` 搜不到东西时，模型往往直接回复"找不到"。
*   **实施**: 修改 `src/store/chat-store.ts` -> `ToolExecutor`。
*   **逻辑**: 拦截所有工具的返回结果。
    *   **触发条件**: `status === 'error'` 或 `content` 为空/无意义。
    *   **动作**: 自动在 Result 后追加 System Note：
        > `[SYSTEM: 结果不理想。禁止直接放弃。请思考(THINK)：是因为关键词不对？还是路径错误？尝试使用不同的参数重试。]`
    *   **效果**: 强制模型进入 Retry Loop，无需用户干预。

#### 2.2 自我修正 Schema (Self-Correction Schema)
*   **实施**: 推广 `manage_task` 的成功经验到 `write_file`, `cmd_run` 等核心工具。
*   **逻辑**: 在 Zod Schema 的 `description` 中硬编码"错误纠正指南"（例如："如果目录不存在，请先调用 mkdir，不要直接报错"）。

---

## 3. 执行评估与建议

### 当前会话状态评估
*   **Session ID**: `2000-2100+ steps` (Context Window Heavy)
*   **已加载文件**: `chat-store.ts` (2000+ lines), `context-builder.ts`, `task.ts` 等大量核心代码。
*   **风险**:
    1.  **上下文污染**: 旧的调试逻辑和 Prompt 可能干扰 Strict Mode 的调试。
    2.  **Token 溢出**: 每次请求都携带大量历史代码，容易触达上下文上限，导致 DeepSeek 等模型"变笨"。

### 结论
**强烈建议新开一个会话 (Start Fresh)** 来执行此方案。
新的会话将提供一个干净的"手术台"，让我们能专注于 `chat-store` 和 `provider` 层的底层重构，避免历史包袱。
