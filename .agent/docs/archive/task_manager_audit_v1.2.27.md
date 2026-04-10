# 任务管理器 (Task Manager) 审计报告

## 1. 核心架构概述

任务管理器（Task Manager）是一个跨越前端 UI 和后端逻辑的系统，允许 AI 在会话中创建、更新和跟踪多步骤任务。

-   **后端逻辑**: `src/lib/skills/core/task.ts` (基于 `manage_task` 工具)
-   **状态存储**: `src/store/chat-store.ts` (全局 Session `activeTask`) 和 `src/store/chat/message-manager.ts` (消息快照 `planningTask`)
-   **前端渲染**: `src/features/chat/components/TaskMonitor.tsx` (卡片)

## 2. 跨消息气泡行为逻辑

系统采用了 **"快照式 (Snapshot)"** 与 **"全局式 (Global)"** 相结合的混合模式。

### 2.1 状态快照 (Message-Level Snapshot)
-   **机制**: 每当 AI 调用 `manage_task` 工具更新任务状态时，`MessageManager` 会将**当前时刻**的任务状态 (`TaskState`) 写入该条 AI 消息的 `planningTask` 属性中。
-   **视觉表现**: 
    -   历史消息中的 `TaskMonitor` 卡片展示的是**该消息生成时**的任务状态。
    -   例如：消息 A 显示 "步骤 1 进行中"，消息 B 显示 "步骤 1 完成，步骤 2 进行中"。
    -   用户可以通过滚动聊天记录，回溯任务执行的完整历史轨迹。
-   **UI 优化**: `TaskMonitor` 组件通过 `isLatest` 属性区分最新状态与历史快照。历史快照的透明度会降低 (`opacity: 0.6`)，以减少视觉干扰。

### 2.2 全局状态 (Session-Level State)
-   **机制**: `ChatStore` 在 `session.activeTask` 中维护当前会话的最新任务状态。
-   **作用**: 
    -   为新生成的 AI 消息提供上下文基础。
    -   在 `TaskMonitor` 中，如果是最新消息 (`isLatest=true`)，它优先展示 Props 传入的快照，但其交互逻辑（如展开/收起）与全局状态关联。

## 3. AI 调用行为逻辑

AI 通过 `manage_task` 工具与任务系统交互。

### 3.1 触发机制
-   AI 决定需要执行复杂任务时，主动调用 `manage_task`。
-   **Action 驱动**: 工具支持 `create` (创建), `update` (更新), `complete` (完成), `fail` (失败), `ask_user` (询问)。
-   **智能推断**: 如果 AI 忘记传 `action`，后端代码 (`task.ts`) 会根据是否存在 `activeTask` 自动推断是 `create` 还是 `update`。

### 3.2 状态流转
1.  **Create**: 初始化任务，生成 `activeTask`。AI 必须提供 `title` 和 `steps`。
2.  **Update**: 更新步骤状态 (`pending` -> `in-progress` -> `completed`)。
    -   **严格模式**: 代码中包含 "Strict Mode" 逻辑，防止 AI 一次性完成多个步骤（幻觉防御），强制 AI 逐步执行。
3.  **Ask User**: 
    -   **暂停机制**: AI 可以通过 `action: 'ask_user'` 暂停任务执行，等待用户反馈。
    -   **UI 响应**: `TaskMonitor` 会显示琥珀色的 "Decision Required" 卡片，提示用户回复。
4.  **Complete**: 任务结束，要求提供 `final_summary`。

### 3.3 数据流向
1.  **AI 输出**: `Tool Call (manage_task)`
2.  **后端执行**: `TaskManagementSkill.execute` -> 更新 Store `activeTask` -> 返回 `ToolResult (data: activeTask)`
3.  **消息处理**: `StreamParser` / `MessageManager` -> 检测到 Tool Result 中的 data -> 更新当前消息的 `planningTask` 属性。
4.  **前端渲染**: `ChatBubble` -> 检测到 `message.planningTask` -> 渲染 `TaskMonitor`。

## 4. 视觉设计细节 (TaskMonitor.tsx)

-   **容器**: 使用 `BlurView` (毛玻璃) 效果，适应深色/浅色模式。
-   **展开/收起**:
    -   默认行为：如果是最新任务且正在进行中，默认展开。
    -   干预模式：如果有 `pendingIntervention` (Ask User)，强制展开并高亮。
-   **进度指示**:
    -   **微型进度条**: 标题栏显示 "1/5 • 20%"。
    -   **步骤列表**: 展开后显示详细步骤，当前步骤有动画效果 (`animate-pulse`)。
-   **历史态**: 非最新消息的卡片会自动降低透明度，减少对当前对话的干扰。

## 5. 发现的问题与建议

1.  **Final Result 冗余**: `TaskFinalResult.tsx` 组件目前逻辑较简单，仅显示一个 "Final Result" 徽章。如果 `activeTask.final_summary` 已经作为文本内容输出在消息体中，这个组件可能略显多余，或者应该承担更多展示总结的职责。
2.  **Dismiss 逻辑**: `TaskMonitor` 包含一个关闭按钮 (`X`)，调用 `dismissActiveTask`。这会清除全局 `activeTask`。在历史消息上点击此按钮可能会导致当前进行中的任务被意外清除（如果用户误操作了历史卡片）。建议限制仅在最新消息上可操作，或增加确认。
