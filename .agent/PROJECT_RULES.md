# Nexara 项目规则与准则 (v5.0 - 2026-02-14)

> [!CAUTION]
> **顶级红线指令 (Highest Priority Directive)**:
> **严禁在项目根目录 (Project Root) 构建发行包 (Release APK)！**
> 发行包构建**永远且只能**在专用的 Worktree 编译工厂 (`../worktrees/release` 或 `worktrees/release`) 中进行。
> 任何违反此规则的行为均被视为破坏开发/编译环境一致性的严重错误。本规则永久有效，不得以任何理由规避。

## 1. 项目概述

- **项目名称**: NeuralFlow (Nexara)
- **技术栈**: React Native (Expo) + TypeScript + Zustand + NativeWind
- **环境**: Hybrid (WSL2 Ubuntu / macOS)
- **核心架构**:
    - **路由**: `expo-router` (File-based routing)
    - **状态**: `zustand` + `persist`
    - **样式**: `nativewind` (Tailwind CSS)

---

## 2. 核心架构原则 (Architecture)

### 2.1 导航与状态
- **Tab 导航**: 使用 `key={language}` 强制语言切换时重新挂载。
- **状态初始化**: 必须通过 `_hasHydrated` 标志等待持久化状态加载完成。

### 2.2 LLM 抽象层架构 (Layered Architecture) 🔥
*详见 `.agent/docs/llm-abstraction-layer-guide.md`*

- **业务层 (chat-store.ts)**: 纯业务，**严禁**包含 Provider 判断逻辑。
- **抽象层 (Response/Formatter)**: 处理 Provider 差异。
- **网络层 (HTTP Clients)**: 纯通信。
- **原则**: 添加新模型时，只允许修改抽象层和配置，绝不允许触碰业务层。

### 2.3 虚拟拆分架构 (Virtual Split) 🔥
*针对 DeepSeek/VertexAI 等复杂协议的适配层*

- **核心逻辑**: 将 One-Assistant-Multi-Tool 拆分为 Multi-Turn (Assistant-Tool Pairs)。
- **字段继承**: 严格继承 `reasoning_content` (DeepSeek) 和 `thought_signature` (Gemini)。
- **持久化**: 历史记录必须累积拆分后的消息，而非原始 Session。

### 2.4 LLM 集成测试基建 (LLM Integration Testing) 🔥
*详见 `.agent/memory/TESTING_GUIDE.md`*

- **适用场景**: 所有涉及 LLM 连接、Prompt 调整、Tool Calling 逻辑的修改。
- **强制要求**: 提交代码前，必须运行 `scripts/test-llm.ts` 验证核心逻辑。
- **基础设施**:
    - **脚本**: `scripts/test-llm.ts` (基于 `tsx` 直接运行，无需编译 App)。
    - **配置**: `secure_env/test_api.json` (独立于 `env.d.ts`)。
    - **原则**: 优先使用脚本测试业务逻辑闭环，避免在真机上反复试错。

---

## 3. 记忆与交接 (Memory & Handoff)

### 3.1 跨会话交接
- **文件位置**: `.agent/memory/SESSION_HANDOVER.md`
- **操作**: 每次任务结束前，必须更新此文件，记录当前进度与下一步建议。

### 3.2 严谨工程流执行
*遵循全局规则 Part C*

- **目录规范**: 所有架构图、流程图、Step计划必须存入 `.agent/docs/plans/{feature_name}/`。
- **禁止**: 严禁将大段设计文档仅留在对话中。

### 3.3 文档即代码 (Documentation as Code) 🔥
*作为 Agent 的核心导航地图，必须与代码同步更新*

- **核心地图 (The 4 Maps)**:
    1. **`CODE_STRUCTURE.md`**: 全局架构与核心机制 (Sequence Diagrams)。
    2. **`DATA_SCHEMA.md`**: 数据结构真理源 (Store/Types/DB)。
    3. **`CORE_INTERFACES.md`**: 关键服务接口契约 (LLM/RAG)。
    4. **`UI_KIT.md`**: 设计系统与组件规范。
- **同步法则**:
    - 修改 `src/types` 或 `src/store` -> 更新 `DATA_SCHEMA.md`。
    - 修改 `src/lib` 接口 -> 更新 `CORE_INTERFACES.md`。
    - 新增/修改 UI 组件 -> 更新 `UI_KIT.md`。
    - 重构模块 -> 更新 `CODE_STRUCTURE.md`。
- **强制性**: 视为 Code Review 的一部分。代码变了文档没变 = **PR 拒绝**。

---

## 4. 技术栈实施细则 (Implementation Details)

### 4.1 原生桥接防御 (Native Bridge)
*继承全局规则 [React Native] 模块，详见 `.agent/docs/native-bridge-defensive-guide.md`*

- **Nexara 特有检查**:
    - `setLanguage` 必须延迟。
    - `router.push` 必须延迟。
    - 所有 Haptics 调用必须延迟 10ms。

### 4.2 设置页一致性 (Settings UI)
- **容器化**: 所有设置项必须包裹在 `SettingsSection` 或 `Card` 中。
- **禁止裸渲染**: 严禁直接在 ScrollView 中放置无容器的 Switch/Button。

### 4.3 机制显式化 (Mechanism Visibility)
- **原则**: 所有后台机制（重试、轮询、Token消耗）必须在 UI 上有数字化体现。
- **实现**: 使用 Toast、Badge 或 Debug 面板展示隐性状态。

---

## 5. 发布与构建 (Release & Build)

### 5.1 双流水线 (Dual Pipeline)
- **Dev 环境**: Project Root (Debug Keystore)
- **Release 环境**: `worktrees/release` (Release Keystore + Secure Env)

### 5.2 Gradle 卫生 (Gradle Hygiene)
*继承全局规则 [Android] 模块*

- **操作**: `scripts/clean-android.sh` (如果存在) 或手动执行物理删除。
- **时机**: Git Pull 后 -> 物理清理 -> 构建。

---

## 6. 视觉系统 (Visual System)

- **主题**: "Midnight Indigo" (水晶态)。
- **组件**:
    - **背景**: `bg-white/80` (Light) / `bg-zinc-900/60` (Dark)。
    - **模糊**: 使用原生 `BlurView`。
    - **动效**: 标签切换强制 `FadeIn` (300ms)。

---

## 7. 待办与规划 (Todo & Planning)
*单一事实来源: `.agent/TODO.md`*

请定期查阅 `TODO.md` 以获取最新的任务优先级。
