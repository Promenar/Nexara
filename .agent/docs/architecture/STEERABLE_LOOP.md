# Agent 自动执行干预与多模式控制方案 (Steerable Agent Loop)

## 1. 核心目标
将现有的 "Fire-and-Forget"（一键执行）Agent 循环升级为 **"Human-in-the-Loop"（人机协同）** 系统。允许用户通过多种模式控制执行节奏，并能在自动执行过程中动态插入意见进行干预，同时优化长任务的截断体验。

## 2. 核心架构升级：Steerable Loop
改造 `AgentLoop` (in `chat-store.ts`)，在每一轮模型生成前引入 **"Pre-Flight Check" (飞行前检查)** 阶段。

### 循环逻辑伪代码
```typescript
while (loopCount < MAX_LOOP_COUNT) {
    // [检查点 1]：动态干预注入
    if (pendingIntervention) {
         injectSystemMessage(`[用户干预指令]: ${pendingIntervention}`);
         clearIntervention();
    }

    // [检查点 2]：执行权限校验 (根据模式)
    if (shouldPauseForApproval(mode, lastToolCalls, reasoning)) {
         setStatus('waiting_for_approval');
         break; // 暂停循环，等待用户操作
    }

    // [检查点 3]：软限制预警 (Soft Limit)
    if (loopCount === MAX_LOOP_COUNT - 1) {
         injectSystemMessage("[系统警告]: 即将达到由用户设置的轮数上限。请总结工作，列出剩余任务，并询问用户是否继续。");
    }

    // -> 执行模型生成 (StreamChat) ...
}
```

## 3. 三大执行模式 (Execution Modes)
在 `Agent Settings` 中增加全局/会话级配置：

| 模式 | 名称 | 行为逻辑 | 适用场景 |
| :--- | :--- | :--- | :--- |
| **🚀 Auto** | **全自动 (默认)** | 连续执行直到任务完成或达到上限。支持中途“动态干预”。 | 绝大多数日常任务，调研，信息检索。 |
| **🧠 Semi** | **半自动 (智能)** | **风险分级逻辑**：<br>1. **安全操作** (Read/Search)：自动放行。<br>2. **高危操作** (Write/Command/Deploy)：**自动暂停**，需用户批准。<br>3. **模型请求**：模型输出 `[REQUEST_APPROVAL]` 时暂停。 | 代码修改，文件操作，系统设置变更。 |
| **🛡️ Manual** | **全手动** | **“走一步，停一步”**。每一轮工具调用前强制暂停，展示拟调用的工具与参数，用户点击“批准”后执行。 | 极高风险操作，或教学演示。 |

## 4. 关键特性详解

### 4.1 动态干预 (Dynamic Injection)
-   **机制**：用户在 Agent 思考或执行时发送的消息，不再作为普通对话 append，而是作为 `pendingIntervention` 存入 Store。
-   **生效时机**：Agent 完成当前步骤后，在下一轮 Input 中优先读取该指令。
-   **UI 交互**：当 Agent 处于 `generating` 状态时，输入框上方显示浮层提示 *"正在执行步骤 3/10..."*，并提供 **"插入干预意见"** 按钮。

### 4.2 优雅中断与续期 (Graceful Expiration)
-   **痛点**：达到 Loop Limit 被强制 Kill，上下文断裂，任务烂尾。
-   **优化**：
    -   **N-1 轮预警**：系统自动提示模型“额度快用完了，请收尾”。
    -   **状态报告**：模型会输出 *"当前已完成 X，剩余 Y，是否继续？"* 并自动停止（非强制截断）。
    -   **动态续期 (Dynamic Renewal)**：
        -   UI 底部展示 **"继续执行 (+N 轮)"** 按钮。
        -   **逻辑**：`N` 等于当前设置的 `MaxLoop` 值（例如设置上限为 10，则续期 +10；设置为 20，则续期 +20）。
        -   **实现**：`MAX_LOOP_COUNT += SETTINGS.MAX_LOOP_COUNT`。

## 5. 状态管理设计 (`chat-store.ts`)

### 新增 Session 状态
```typescript
interface ChatSession {
  // ... existing fields
  executionMode: 'auto' | 'semi' | 'manual'; // 执行模式
  loopStatus: 'running' | 'paused' | 'waiting_for_approval' | 'completed';
  pendingIntervention?: string; // 待注入的用户指令
  approvalRequest?: {          // 待批准的操作详情
     toolName: string;
     args: any;
     reason: string;
  };
}
```

## 6. UI/UX 设计方案
1.  **设置面板**：Agent 详情页增加 "Execution Mode" 下拉菜单。
2.  **聊天流 (Timeline)**：
    -   **Auto**：显示进度条。
    -   **Paused**：显示黄色卡片 *"Agent 请求批准操作: modify_file"*，带 [批准] [拒绝] [修改指令] 按钮。
    -   **Limit Reached**：显示 *"达到轮数上限"*，带 [继续执行] 按钮。
3.  **输入区域**：
    -   执行中：变为 "Intervention Mode"（插入指令）。

## 7. 实施计划 (Implementation Plan)
1.  **Store 层改造**：更新 `ChatSession` 接口与 `AgentLoop` 状态逻辑。
2.  **Logic 层实现**：
    -   实现 `shouldPauseForApproval` 判决函数 (Risk Classification)。
    -   实现 `injectSystemMessage` 队列机制。
3.  **UI 层实现**：
    -   开发 "Approval Card" (批准卡片)。
    -   开发 "Intervention Input" (干预输入框)。
    -   开发 "Continue Button" (续期按钮)。
4.  **集成测试**：针对三种模式分别进行多轮对话测试。
