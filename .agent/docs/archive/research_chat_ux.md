# Nexara Chat 2.0: 会话界面与流程系统性重构研究

> [!NOTE]
> 本文档旨在系统性梳理会话流程中的 UI 表现与后台机制问题，重点解决多轮对话思考、复杂任务执行中的体验割裂问题，并探讨“卡片式 UI”的可行性。

## 1. 现状问题诊断 (Diagnostics)

基于场外援助 (DeepSeek) 的实测反馈及截图分析，当前系统存在以下核心缺陷：

### 1.1 视觉渲染缺陷
- **Markdown 排版丢失换行**
  - **现象**：大模型输出的长文本在正文区域堆积成块，缺乏段落区分，极度影响阅读体验。
  - **原因推测**：`react-native-markdown-display` 样式配置问题，或后端流式输出时的换行符处理不当（如 `\n` 被转义或忽略）。

### 1.2 多轮任务体验割裂 (Fragmentation in Multi-turn Tasks)
- **“虎头蛇尾”的展示**
  - **现象**：正文区域仅显示了任务第一步的执行结果（如“分析 manage_task 工具描述...”），而后续步骤及最终的 `Final Summary` 没有在正文区域得到连贯展示。
  - **后果**：用户看到的“正文”与“最终结论”脱节，绿色的“最终结果”标签虽然出现，但真正的总结内容缺失或被折叠，导致体验反直觉。

### 1.3 交互机制冗余 (Backend Inefficiency)
- **任务状态“盲人摸象”**
  - **现象**：模型在创建新任务受阻时，不知道当前活跃任务是什么，需要反复调用 `update` 指令询问进度。
  - **后果**：浪费 Token，增加时间开销，导致用户等待时间变长。

## 2. 解决方案：UI 模式深度对比 (Mode Architecture)

针对“无限卡片流”的实现，目前有两种主要的技术路线。我们需要权衡信息密度与交互流畅度。

### 路线 A：高密度混合流 (Timeline + Streaming Cards)
- **架构**：
  - **Header (置顶)**: `Timeline` 组件。保留现有的结构化展示，收纳所有 [Thinking]、[Plan]、[Tool Call]、[Tool Result]。
  - **Body (流式)**: `StreamingCardList` 组件。仅承载模型的**自然语言输出**。
- **解析逻辑**：
  - 能够被解析为结构化数据的（思考、工具），自动“吸入”顶部的 Timeline。
  - 无法被解析的自然语言文本，按生成顺序被切割为独立的卡片（Card 1, Card 2...），依次追加到 Body 区域。
- **优点**：
  - **信息密度极高**：复杂的思考和几十步工具调用被折叠在一个有限高度的容器中，不会占据屏幕空间。
  - **阅读体验好**：用户只需关注下方的自然语言流，需要究根问底时再去展开顶部的 Timeline。
- **风险**：
  - **时序错位**：如果模型的自然语言与工具调用穿插过密（如 说一句话 -> 调一个工具 -> 再说一句话），置顶的 Timeline 可能会让用户感觉工具调用“全是先发生的”，打乱了实际的因果流。

### 路线 B：全量线性流水账 (Full Linear Stream)
- **架构**：
  - 取消置顶 Timeline。
  - **所有内容**（思考、工具、文本）一视同仁，全部作为独立的卡片，严格按时间顺序向下排列。
- **优点**：
  - **逻辑直观**：完全还原模型的时间线，“想到 -> 做到 -> 说到”，符合直觉。
  - **实现简单**：前端无需复杂的“吸入/分流”逻辑，只需渲染一个异构 List。
- **缺点**：
  - **屏幕爆炸**：在移动端，一个复杂任务可能有 50+ 个步骤，导致几十屏的滚动距离，用户很难快速找到最终结论（虽然可以一键置底，但中间过程过于冗长）。

---

## 3. System Prompt 系统性重构 (System Architecture)

> [!IMPORTANT]
> 拒绝“打补丁”式优化。我们需要从底层 Protocol 层面重新定义模型与前端的交互契约。

### 3.1 核心原则
1.  **角色分离 (Separation of Concerns)**：
    - **思考 (Thinking)**：必须由于 `thinking` 块包裹，或在 System Prompt 中强制定义为内部独白，前端仅在 Timeline 或特定卡片中渲染，默认折叠。
    - **行动 (Acting)**：工具调用必须是原子的，且每次行动前必须先有规划。
    - **表达 (Talking)**：自然语言必须是面向用户的最终交付物，而非中间碎碎念。

### 3.2 现有 Prompt 审计 (Audit Findings)

通过分析 `src/lib/llm/model-prompts.ts`，发现当前设计存在严重的**碎片化与硬编码**问题：

1.  **家族式硬编码 (Family-Hardcoded)**: 对 `DeepSeek`, `Gemini`, `Moonshot` 等不同模型家族维护了完全独立的 Prompt 文本（如 `getTaskPlanningGuidance` 中的 switch-case）。这种“打补丁”方式导致维护成本极高，且容易导致不同模型行为不一致。
2.  **指令混杂 (Instruction Mixing)**: 任务规划、工具调用、能力声明、输出格式等逻辑分散在不同函数中，且互相穿插。例如，`getTaskPlanningGuidance` 同时包含了工具调用指令和总结要求。
3.  **格式约束疲软 (Weak Formatting)**: 对 Markdown 换行的要求仅隐含在部分描述中，缺乏全局强制性系统指令 (System Directive)。
4.  **缺乏统一身份 (No Unified Identity)**: 缺少一个全局统一的 `Identity` 模块，导致模型在不同场景下“人设”可能发生漂移。

### 3.3 提示词工程整改方案 (Refactoring Plan)

我们将废弃现有的 switch-case 拼凑模式，转为基于**组件化 (Component-based)** 的 Prompt 构建系统。

#### 核心模块架构

1.  **Identity (双层身份架构 - Dual-Layer)**:
    - **Kernel Layer (系统内核)**: 物理层。确立 "Nexara Assistant" 的基本行为准则（Markdown, Thinking, Tools）。**不可变**。
    - **Persona Layer (业务人格)**: 
        - **Super Assistant**: 默认全知中枢。
        - **Expert Agent**: 动态注入的用户自定义 Prompt（如"猫娘"、"律师"）。

2.  **Capability (能力声明)**:
    - **Tool Registry**: 
        - **Native**: `manage_task`, `fs_*`, `browser`...
        - **MCP**: 外部工具。
    - **Renderer (智能分流策略)**:
        - **Mermaid**: 推荐直接 Markdown 输出 (` ```mermaid `)。
        - **ECharts**: **强制使用 Tool** (`render_echarts`)，以利用 JSON 校验及专用 UI 卡片。
    - **Knowledge (RAG)**:
        - **定位**: Context 数据层。
        - **Protocol**: 优先参考本地知识库，但需保持客观。

3.  **Protocol (交互协议)**:
    - **Thinking Protocol**: 强制思考过程包裹在 `<!-- THINKING_START -->` 块中。
    - **Task Protocol**: 强制 `manage_task` 的生命周期管理（Create -> Update -> Complete）。
    - **Formatting Protocol**:
      - **CRITICAL**: 严禁使用“连续文本块”，必须使用 `\n\n` 进行段落分隔。
      - **Structure**: 规定“思考 -> 工具 -> 结论”的线性输出顺序。

#### 示例：Formatting Protocol 片段

```markdown
## OUTPUT FORMATTING (CRITICAL)

1. **Paragraphs**: You MUST use double line breaks (`\n\n`) to separate paragraphs. Single line breaks are often ignored by renderers.
2. **Thinking**: ALways output your internal reasoning first, wrapped in:
   <!-- THINKING_START -->
   ... reasoning ...
   <!-- THINKING_END -->
3. **No Fluff**: Do NOT output phrases like "I will now...", "Let me check...". Just ACT.
```

## 4. 行动路线图 (Roadmap)

1.  **UI 修复 (Quick Fixes)**
    - **排版问题**：检查 `react-native-markdown-display` 的 `body` 样式，确认是否缺少 `white-space: pre-wrap;` 或类似设置。
    - **流式处理**：检查后端或前端对 SSE 流的处理，防止 `\n` 被错误地转义为 `\\n`，导致无法被 Markdown 引擎识别为换行。

2.  **后端逻辑优化 (Backend Logic)**
    - 实施 `manage_task` 的 `renderTaskUI` 优化，注入任务全貌。
    - 优化 System Prompt，加入排版和总结的强约束。

3.  **前端架构重构 (Frontend Refactor)**
    - 设计 `StreamingCardList` 组件，替换现有的单一 `Markdown` 视图。
    - 将流式消息解析为 `[Thought, Tool, Text, Tool, Text...]` 的结构化数组，并映射为卡片流。

## 5. 待讨论事项 (RFC)
- **卡片切分粒度**：是仅按工具调用切分，还是允许模型插入“中间评论”卡片？
- **历史兼容性**：旧的历史消息如何适配新的卡片式 UI？（可能需要回退模式）
