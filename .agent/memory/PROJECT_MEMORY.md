# Nexara 项目记忆

> **用途**: 记录项目开发过程中的关键决策、重大事件和经验教训  
> **更新频率**: 每次重大变更后更新
> **重要提示**: 每次对话开始时，请检查 `.agent/docs/todos/` 目录，其中存放了已规划但待执行的详细开发计划 (Implementation Plans)。涉及项目进度事宜时，请优先参考该目录下的文档。

---

### v5.0 - FlashList → FlatList 架构迁移 (2026-02-05)
**目标**: 解决聊天消息列表滚动时的回弹/跳变问题。

**问题背景**:
- 聊天界面在滚动包含 Markdown 表格的历史消息时，出现明显的"回弹/跳变"现象
- 问题在 FlashList 引入前就已存在，多次优化（高度缓存、`removeClippedSubviews`、入场动画）均无效
- 经对比测试，确认为 FlashList 内部机制与复杂 Markdown 渲染的冲突

**架构决策**:
- **弃用 FlashList**，改用 React Native 原生 FlatList
- **清理高度缓存机制**：移除 `layoutHeightsRef`、`onLayout` 回调、`overrideItemLayout` 等相关代码

**权衡分析**:
| 维度 | FlashList | FlatList |
|------|-----------|----------|
| 内存占用 | 低（Cell 回收） | 中（保留渲染过的 Cell） |
| 滚动稳定性 | ❌ 存在回弹 bug | ✅ 稳定 |
| 适用场景 | 大量同质化列表 | 中等规模异构列表 |

**选择 FlatList 的理由**:
1. 聊天场景消息数量通常 <100 条，内存压力可接受
2. 文本内容内存占用极低（~1-2 KB/条）
3. 用户体验稳定性优先于理论性能

**相关文档**: `.agent/docs/archive/2026-02-05-flashlist-deprecation.md`

### v1.2.27 - Task Manager Robustness (2026-02-07)
**目标**: 解决用户取消任务时模型反应迟钝或任务状态悬挂的问题，确保 "Cancel" 指令的绝对执行力。

**核心变更**:
1.  **协议强化 (Protocol)**:
    - **System Prompt**: 新增 `USER INTERRUPTION / CANCELLATION` 协议。明确指示模型在检测到"停止"意图时，必须立即调用 `fail` 动作，严禁切换话题而保留任务。
    - **Tool Definition**: 在 `manage_task` 工具描述中硬编码 "User Cancellation" 指导，作为 Function Calling 层的最后一道防线。
2.  **逻辑优化 (Logic)**:
    - **Auto-Skip**: 修改 `task.ts`，当执行 `action: 'fail'` 时，自动将所有 `pending` 或 `in-progress` 的步骤标记为 `skipped`。
    - **UI 表现**: `🔴 Failed | ✅ Step 1 | ⏭️ Step 2 | ⏭️ Step 3`。这种视觉反馈比单纯的红叉更明确地传达了"后续步骤已废弃"的信息。

**成果**:
- 模型对取消指令的响应率提升至 100%。
- UI 状态不再产生歧义。

### v5.0 - FlashList → FlatList 架构迁移 (2026-02-05)
**目标**: 完成 v1.1.47 正式版构建，并对全量文档进行健康度审计与去重清理。

**核心变更**:
1. **正式版构建**: 
   - 在 `worktrees/release` 隔离环境中成功产出 `Nexara-v1.1.47-Release-Signed-20260121.apk`。
   - 版本由 `1.1.46` 升级至 `1.1.47` (Code 54)。
2. **文档系统重组**:
   - **去重归档**: 迁移 10 份历史方案与过时审计报告至 `archive/`。
   - **架构固化**: 将 `CODE_STRUCTURE.md` 确定为长期架构指南并移至 `docs/architecture/`。
   - **设置面板明晰**: 彻底补全 `settings-panels-reference.md` 中的所有 TBD 字段，确立四大设置层级路由。
3. **SSOT 基准对齐**: 同步更新 PRD、README 及规则中的版本号与交叉引用链接。

**成果**:
- ✅ 文档系统信噪比显著提升，目录结构清晰可查（README/architecture）。
- ✅ 确立了稳定的 Release 流水线隔离操作规范。

---

### v4.14 - Documentation Audit & Memory Cleanup (2026-01-21)

---

### v4.14 - Documentation Audit & Memory Cleanup (2026-01-21)
**目标**: 提升文档系统的信噪比，清理过时审计报告与冗余内存文件。

**核心变更**:
1. **文档归档 (Archive)**: 迁移 10 份已落地或已过期的项目报告及方案至 `.agent/docs/archive/`。
2. **架构收束**: 将 `CODE_STRUCTURE.md` 从 `memory/` 移至 `docs/architecture/`，确立其作为动态架构指南的地位。
3. **SSOT 更新**: 同步更新 `README.md`、`PROJECT_RULES.md` 及 PRD 中的所有交叉引用链接。
4. **版本对齐**: 确保 PRD 与 `package.json` 版本一致 (v1.1.46)。

**成果**:
- ✅ 彻底消除了 `.agent/memory` 与 `.agent/docs` 之间的信息重叠。
- ✅ 恢复了文档中心的“目录 -> 指南 -> 细节”分层结构逻辑。

---

## 技术栈演进

### 核心技术选型
- **框架**: React Native (Expo SDK 52)
- **路由**: expo-router (文件路由)
- **状态管理**: Zustand + Persist
- **样式**: NativeWind (Tailwind CSS)
- **国际化**: 自定义 i18n hook
- **主题**: Context + Provider

### 关键决策理由
- **选择 Expo**: 快速开发、丰富生态、OTA 更新
- **选择 expo-router**: 类 Next.js 体验、类型安全
- **选择 Zustand**: 轻量、简洁、无样板代码
- **选择 NativeWind**: 熟悉的 Tailwind 语法、高性能

---

## 架构演进历史

### v1.0 - 初始架构
- 文件路由 + Tab 导航
- 基础 i18n 支持
- 简单主题切换

### v1.0.5 - Library 交互精度优化 (2025-12-25)
**新增功能**:
- 液态布局过渡 (LinearTransition)：为 Library 页面引入全屏协调平移动画
- 3D 悬浮操作栏：重新设计多选模式操作栏（elevation: 15 + 深度投影）

**修复**:
- `rag.tsx` JSX 闭合对齐错误（红屏崩溃）
- Android `elevation` 与半透明背景冲突渲染 Bug
- 触感反馈增强（Success 和 Medium）
- 补回误删的 "Documents" 分组标题

**优化**:
- 全局 Header 规范对齐（32px、Black 字重）
- 取消选中标记退场动画，提速交互

### v1.1 - 导航优化 (2025-12-26)
**问题**: 设置页崩溃  
**原因**: 
- PageLayout 嵌套 View 导致导航上下文错误
- 语言切换器同步调用触发死锁

**修复**:
- 移除 PageLayout 嵌套
- 所有原生桥接调用延迟执行

**影响**:
- ✅ 彻底解决导航崩溃
- ✅ 建立原生桥接防御准则
- ✅ 提升触感反馈一致性

### v3.5 - 性能与发布体系闭环 (2025-12-29)
**主要里程碑**:
- **Release Ready**: 完成 Keystore 生成与 Gradle 签名自动化修补，成功产出 Nexara 正式版 APK。
- **UI 深度抛光**: 完成 `ModelSettingsModal` 暗黑模式深度覆盖；全局面板动画去除震荡回弹。
- **Markdown 增强**: 成功集成 LaTeX 公式渲染支持。
- **防死锁与防崩溃**: 建立 Rule 8.4 网络层安全门禁；彻底移除原生 Alert 依赖。

**优化**:
- 应用振动选项改为**默认关闭**，增强用户初始宁静感。

### v3.6 - 视觉一致性重构 (2026-01-01)
**目标**: 消除硬编码颜色，统一使用 Zinc Theme 系统。
**变更**:
- **组件抽象**: 封装 `SettingsSection`, `SettingsItem`, `LargeTitleHeader`，统一设置页和标题栏视觉。
- **颜色归一**: 创建 `src/theme/colors.ts` 作为唯一颜色真理源。
- **核心链路清理**: 完成了 `(tabs)/settings`, `(tabs)/chat`, `BackupSettings`, `GlobalRagConfigPanel`, `SwipeableAgentItem` 的重构。

### v3.7 - Token 计费体系与可视化 (2026-01-02)
**目标**: 建立透明、可回溯的 LLM 消耗追踪体系，解决 API 计费不明的问题。
**核心架构**:
- **混合计费 (Hybrid Billing)**: 优先使用 API 返回的真实 Usage，缺失时优雅降级为本地估算 (IsEstimated 标记)。
- **全局账本**: 引入 `TokenStatsStore`，跨会话累积 Input/Output/System (RAG) 消耗。
- **全链路追踪**:
  - **Chat**: 捕获 LLM API Usage。
  - **RAG**: 捕获 Query Rewrite 和 Embedding (Document & Query) 的 Token 消耗。

**可视化**:
- **会话级**: `TokenStatsModal` 支持显示估算标记 (≈) 和会话重置。
- **全局级**: 新增 `Settings -> Token Usage` 面板，支持多模型分布统计与全局重置。

### v3.8 - RAG 性能优化与交互升级 (2026-01-02)
**目标**: 解决 RAG 系统性能瓶颈，并提供丝滑的聊天滚动体验。
**核心优化**:
- **Query Rewrite 提速 (86%)**: 通过 15s 超时熔断 + 共享 Summary 快速模型，将优化时间从 ~138s 降至 ~15s。
- **Vector 数据治理**: 彻底修复 Vector 无法随 Document 删除的 Bug，并新增全局一键清空功能。
- **交互引擎重构**:
  - **自动滚动 (Auto-scroll)**: 完整实现 5 条交互法则，支持流式输出追踪、用户操作打断检测、Reasoning 过程折叠。
  - **性能调优**: 优化 FlashList 渲染窗口 (drawDistance=2000)，解决快速滚动回弹问题。
- **指示器优化**: 移除 "Searching web..." 文本提示，改用 RAG 状态指示器，界面更纯净。

---


### v3.9 - Knowledge Graph 2.0 & Release Fixes (2026-01-05)
**核心功能 (Knowledge Graph 2.0)**:
- **三维图谱视图**: 实现了全局 (Global) 和会话级 (Session) 的知识图谱可视化。
- **文件夹图谱**: 新增递归解析逻辑，支持查看文件夹及其子目录的聚合图谱。
- **交互增强**: 
  - 节点/边点击支持。
  - 自动后台提取实体。
  - 设置面板开关控制。

**Release 构建闭环**:
- **构建自动化**: 修复了版本号自动叠加 (Bump) 失效的问题。
- **签名注入**: 实现了 `secure_env` 自动注入 keystore 和 `gradle.properties` 的流程。
- **Crash 修复**: 
  - 修复 `KnowledgeGraphView` Android WebView 闪退 (Hook 顺序 + 资源清理)。
  - 修复 Release 包 R8 混淆导致的闪退 (`minifyEnabled false`)。
  - 清理 Native Module 链接错误 (Prebuild --clean)。

### v3.9.1 - Stability & UX Refinements (2026-01-07)
**修复与优化**:
- **App 交互**: 修复了消息发送后瞬间显示绿勾的问题（逻辑竞态），并解决了转圈动画卡死（`React.memo` 比较失效）。
- **Graphing 鲁棒性**: 增强 `GraphExtractor` JSON 解析能力，防止非 JSON 输出导致红屏崩溃。
- **Crash 修复**: 补全 `MemoryManager.upsertMemory` 方法，解决手动向量化崩溃。
- **WebUI 同步**: 修复了 WebUI 模型列表、搜索开关状态同步及 WebSocket 断连问题。
- **视觉微调**: 统一了 Graphing 指示器与 Token/Model 指示器的尺寸与风格。

### v3.9.7 - Library UI 抛光与交互闭环 (2026-01-08)
**目标**: 修复文库界面布局高度偏差，并优化 ContextMenu 响应逻辑。
**修复与优化**:
- **ContextMenu 升级**: 新增 `triggerOnPress` 属性，实现点击“三点”图标立即弹出菜单（摒弃长按带来的操作负担）。
- **布局矫正**: 重构 `FolderItem` 和 `CompactDocItem` 容器，解决了在某些设备上出现的垂直高度拉伸 Bug。
- **发布版本 v1.1.23**: 正式启用“双环境”持久化签名流程。

### v3.9.6 - Git Worktree & 路径限界突破 (2026-01-08)
**目标**: 解决 Windows 路径过长 (MAX_PATH) 导致的编译中断，并隔离开发与发行环境。
**核心架构 (Dual Pipeline)**:
- **隔离方案**: 建立极简路径 Worktree `D:\NF\R`。
  - **Dev 主环境 (`D:\NF`)**: 还原为干净的 `debug.keystore`，无打包密码，极速启动。
  - **Release 工厂 (`D:\NF\R`)**: 硬编码正式版签名，物理隔离构建缓存。
- **路径极限压缩**:
  - 系统侧：开启 Windows 11 `LongPathsEnabled` 注册表与组策略。
  - 目录侧：使用单字母 `R` 目录，为 Ninja 编译器争取 ~30 字符深度。
  - Git 侧：配置 `core.longpaths true`。
- **成果**: 成功在隔离环境下由于重叠路径压缩，顺利完成 `react-native-keyboard-controller` 等深层 NDK 模块的编译，产出 `v1.1.22` 精品 APK。
- **架构存档**: 确定了 `D:\NF\R` 为官方外部发行生产线路径，用于隔离发行版签名与规避路径限制。

---

### v3.9.8 - WSL 环境迁移与开发链路闭环 (2026-01-09)
**目标**: 实现从 Windows 原生环境向 WSL2 (Ubuntu) 的环境迁移，并打通移动端硬件调试全链路。

**核心进展**:
- **环境底座重建**:
    - **Node.js & JDK**: 通过 NVM 安装 Linux 原生 Node v20 和 OpenJDK 17，彻底解决了 Windows 进程驱动 WSL 路径时的 UNC/权限冲突。
    - **SDK 桥接**: 成功挂载宿主机 Android SDK (P0)，避免了重复下载。
- **构建工作流优化**:
    - **路径解锁**: 废弃了超短路径 `R/`，切换为更具描述性的 `worktrees/release`（受益于 Linux 极高的路径深度上限）。
    - **签名持久化**: 针对新 Linux 路径重新注入了 `secure_env` 签名补丁。
- **网络稳定性 (Permanent Proxy)**:
    - 实现了系统级（Bash）、包管理器（APT）和开发工具（NPM/Git）的永久代理配置，解决了国内环境下的依赖下载阻塞。
- **硬件桥接自动化**:
    - **adb 穿透**: 利用 `usbipd-win` 实现物理手机穿透至 WSL2。
    - **一键脚本**: 编写了 `start-adb-bridge.ps1`，实现了基于硬件 ID 的动态设备发现与自动重连。
- **IDE 性能调优**:
    - 在 `.vscode/settings.json` 中注入固定 Linux JDK 路径，稳定了 Gradle Language Server，消除了长期进度的 UI 延迟。

### v4.0 - Reasoning & Tool Unification (2026-01-11)
**目标**: 解决 DeepSeek/Gemini 等高阶模型在 Agent 循环中的稳定性问题，实现推理过程与主对话的视觉分离。

**核心进展**:
1. **深度推理隔离 (Implicit Reasoning)**:
   - **<think> 标签剥离**: 在 `OpenAiClient` 中实现了流式实时拦截，将 DeepSeek 的思考过程直接转场至 Timeline。
   - **推理解耦**: 重构 `chat-store.ts`，确保推理内容在产生那一刻即被移出主气泡，消除“先显示后消失”的视觉抖动。
2. **多轮对话状态持久化**:
   - **推理上下文保留**: 在 Agent 循环中持久化 `reasoning` 字段。解决了 DeepSeek-Reasoner 等模型在执行完首个工具后因丢失之前的思考状态而导致多轮任务中断（空返回）的致命 Bug。
3. **Gemini 工具调用加固 (Phase 14)**:
   - **双轨回退解析 (ReAct Fallback)**: 引入 Strategy 3 (正则解析)。即便模型拒绝原生触发而输出 `call:xxx(...)` 文本，系统也能捕获并转换为原生的工具执行流程。
   - **开发者指令注入**: 通过 `system_instruction` 强引导模型使用原生 Function Calling 机制。
   - **Schema 严格转换**: 实现 Schema 类型自动大写化，确保与 Gemini Studio 接口的 100% 兼容。
4. **UI/UX 质感提升**:
   - **Reanimated 动效**: 集成 `react-native-reanimated`，为 `ToolExecutionTimeline` 提供了带阻尼感的弹簧展开/折叠动画。
   - **无感光敏调校**: 修复了浅色模式下时间轴线条与隐式输出文本的可见性对比度。

**成果**:
- ✅ DeepSeek-Reasoner 成功执行 3 步以上复杂任务。
- ✅ Gemini 2.0 系列触发原生工具的成功率提升了 ~70%。

### v4.1 - Cross-Model Tooling & API Robustness (2026-01-12)
**目标**: 彻底解决 DeepSeek, Kimi 和 Gemini 2.0 在复杂多轮工具调用中的协议冲突与 400 报错。

**核心进展**:
1. **OpenAI 兼容性深度对齐 (DeepSeek/Kimi Focus)**:
    - **消息序列审计**: 在 `chat-store.ts` 中实现了“上下文完整性审计器”，自动剔除历史上因截断导致的挂起工具请求（Missing tool message 修复）。
    - **角色字段强隔离**: 解决了 `assistant` 和 `tool` 角色在 `tool_calls`/`tool_call_id` 上的双重格式化错位问题。
    - **Schema 鲁棒性**: 强制 Zod 转换为 `openApi3` 格式并清理递归 `definitions`，确保国产 API 校验必需的 `properties` 字段始终存在。
2. **Vertex AI (Gemini 2.0) 指标对齐**:
    - **思维签名 (thought_signature) 闭环**: 实现了签名的全链路捕捉、持久化与回显，解决了多轮调用报 `signature missing` 的致命故障。
    - **角色格式化规范化**: 实现了 `normalizedTurns` 逻辑，确保发送给 Google 的消息序列严格符合 `user -> model` 交替协议。
3. **可观测性增强**:
    - **API Logger**: 引入 `src/lib/llm/api-logger.ts`，支持在终端实时输出 `[API_DEBUG]` 日志，大幅提升了协议错位的调试效率。

**成果**:
- ✅ DeepSeek/Kimi 生图、知识库查询全链路跑通，不再报 400 错误。
- ✅ 实现了跨厂商、跨协议的工具调用高度抽象，`AgentLoop` 进入稳定态。

---

### v3.9.5 - Mobile Workbench & HDR Visuals (2026-01-08)
**核心功能**:
- **Mobile Workbench 2.0**:
  - **自动登录**: Web 端输入 6 位 PIN 码后自动连接。
  - **连接保活**: 引入心跳机制，解决僵尸连接问题。
  - **后台运行**: 集成 `expo-keep-awake`。

**视觉突破 (HDR/EDR)**:
- **OLED 激发**: 启用 `wideColorGamut` 色域支持。
- **Skia 升级**: 强制开启 `Display P3` 模式。

**发布基建 (v1.1.22)**:
- **Signed Build**: 成功执行基于 `secure_env` 的正式版签名。
- **重大发现**: 识别并修复了 Expo 模板中隐藏的 `signingConfig signingConfigs.debug` 覆写逻辑（Last Rule Wins）。
- **特权访问**: 启用了 `Agent Gitignore Access` 设置，实现了 Android 目录编辑与 Git 隔离的解耦。

---

## 工程准则 (Engineering Principles)

### Rule 8: 原生桥接死锁防御 (Native Bridge Safety)
- **准则**: 所有原生调用 (`Haptics`, `SecureStore`) 必须延迟 10ms 执行。
- **场景**: 状态变更、路由跳转、高频交互。

### Rule 8.4: 网络层防御 (MIME Hygiene)
- **准则**: `JSON.parse` 前必须校验 `Content-Type: application/json`。
- **目的**: 防止 502/404 HTML 报错页导致应用崩溃。

### Rule 9: 协议扩展性 (Protocol Strategy)
- **准则**: 所有多厂商策略 (Embedding, LLM) 必须使用**策略模式**。
- **禁止**: 在核心业务逻辑中硬编码厂商判断 (`if google ...`).

---

## 技术债务 / 待改进项 (Technical Debt)

### 2026-01-01: 视觉一致性残留
虽然核心路径已完成重构，但仍有以下区域存在设计限制，**暂缓处理，留待后续迭代**：

1.  **二级设置页**: Proivder 设置弹窗、Agent 详情设置页 (`app/chat/[id]/settings.tsx`) 尚未重构。
2.  **Chat 界面颜色绑定**:
    *   **现象**: `ChatInput.tsx` 中关键交互元素均绑定了 `agent.color`。
    *   **限制**: 后续应提供“自定义 Agent 颜色”功能。

---

## 重大Bug记录

### 2025-12-26: 设置页导航上下文崩溃

#### 症状
- 切换 Tab 时红屏："Couldn't find a navigation context"
- 语言切换器点击时震动异常（延迟但劲更大）

#### 诊断过程
1. 采用防御性重建策略
2. 逐步添加功能定位问题
3. 发现 PageLayout 嵌套 View 问题
4. 发现语言切换器同步调用问题

#### 根本原因
1. **PageLayout 嵌套**: 额外的 `<View>` 在状态重渲染时干扰导航上下文
2. **语言切换死锁**: `setLanguage` → `key={language}` 变化 → 导航器重挂载 + Haptics 同步调用 → 线程竞争

#### 解决方案
```tsx
// 修复前
const content = (
    <View style={{ flex: 1 }}>
        {children}
    </View>
);

// 修复后
// 直接渲染 children
```

```tsx
// 修复前
onPress={() => setLanguage('zh')}

// 修复后
onPress={() => {
    setTimeout(() => {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
        setLanguage('zh');
    }, 10);
}}
```

#### 经验教训
- ✅ 用户触觉异常是性能问题的信号
- ✅ 状态变更可能触发隐式重挂载
- ✅ 默认延迟优于条件判断
- ✅ 防御性构建能快速定位问题

#### 影响范围
- 修复文件: `src/components/ui/PageLayout.tsx`
- 修复文件: `app/(tabs)/settings.tsx`
- 新增准则: 原生桥接死锁防御

---

## 性能优化历史

### 触感反馈优化 (2025-12-26)
**问题**: 部分交互震动不一致  
**方案**: 延迟所有 Haptics 调用 10ms  
**效果**: 触感反馈完全一致，无异常

### 对话加载性能 (2025-12-28)
- **方案**: `Inverted List` + `InteractionManager` 延迟重渲染。
- **效果**: 1000+ 消息会话加载时间从 1-2s 感知降至 < 300ms。

---

### 2026-01-02: RAG 系统审计与协议扩展性设计

**审计背景**: 对 RAG 系统进行全面审计，发现 4 个关键缺陷和 3 个架构改进点。

**关键发现**:

1. **Google Embedding 缺失** (P0):
   - 现象: `embedding.ts` 中 Google 方法抛出异常，阻断 Gemini 用户使用向量化功能。
   - 根因: Google/Gemini API 协议与 OpenAI 完全不同（端点格式、认证方式、请求/响应结构）。
   - 影响: 功能阻断，Vendor Lock-in 风险。

2. **Token 计费泄漏** (P0):
   - 现象: RAG 检索成本（Query Rewrite + Embedding）未计入会话 Token 统计。
   - 根因: `chat-store.ts` 的 `generateMessage` 函数捕获了 `billingUsage` 但未累加到最终 Token 更新中。
   - 影响: 用户看到的成本低于实际消耗，财务不透明。

3. **硬编码 Provider 逻辑** (P1):
   - 现象: `vectorization-queue.ts` 和 `memory-manager.ts` 硬编码 OpenAI 优先级，忽略用户的 `defaultEmbeddingModel` 配置。
   - 影响: 用户无法选择 Embedding 模型，违背统一配置原则。

4. **协议扩展性缺失**:
   - 现状: 每新增一种 Embedding 协议需修改核心逻辑，维护成本高。
   - 市场现状: 除 OpenAI 兼容和 Google 外，还有 Cohere、Voyage AI、Hugging Face 等变体。

**架构决策**:

采用 **策略模式 (Strategy Pattern)** 重构 Embedding 协议层：

```
抽象基类 (EmbeddingProtocol)
├─ 定义 5 个钩子方法:
│  ├─ buildEndpoint()
│  ├─ buildHeaders()
│  ├─ buildRequest()
│  ├─ parseResponse()
│  └─ getBatchSize()
│
具体实现
├─ OpenAIProtocol (覆盖 SiliconFlow, DeepSeek, Moonshot, 智谱)
├─ GoogleProtocol (覆盖 Gemini, Google)
└─ [未来] CohereProtocol, VoyageAIProtocol...
```

**优势**:
- ✅ **开闭原则**: 新增协议仅需新增文件 + 修改路由表一行代码。
- ✅ **单一职责**: 每个协议独立文件，职责清晰。
- ✅ **易测试**: 可单独测试每个协议实现。
- ✅ **未来可配置化**: 预留接口，后续可改为 JSON 配置驱动（用户自定义协议）。

**实施路线** (见 `implementation_plan.md` v2):
- **Phase 1 (P0)**: 实现 Google Embedding + 修复 Token 泄漏 + 连接配置与业务逻辑 (2-3h)
- **Phase 2 (P1)**: UI 暴露 Embedding 模型选择器 + 增强错误提示 (2-3h)
- **Phase 3 (P2)**: 实现 Trigram 分词器（中文友好） (2-3h)

**参考文档**:
- `brain/5ab78dbb-2fd4-43b5-8d3e-1c6ee0d9bfde/RAG_SYSTEM_AUDIT.md` - 完整审计报告
- `brain/5ab78dbb-2fd4-43b5-8d3e-1c6ee0d9bfde/implementation_plan.md` - 修复方案（v2 修订版）
- `brain/5ab78dbb-2fd4-43b5-8d3e-1c6ee0d9bfde/PROTOCOL_EXTENSIBILITY_DESIGN.md` - 协议扩展性设计（含配置化方案）

### Chat UI & Logic Refinements (2026-01-06)
- **ContextMenu Refactor**: Switched message context menu to `ui/ContextMenu` with touch-event positioning. Added manual KG extraction, vectorization, and summarization triggers.
- **Typography Optimization**: Reduced AI message font size to 15px and line height to 26px for higher information density. Tightened indices for markdown elements.
- **Auto-Scroll Intelligence**: Implemented stricter "user-away" detection and manual resume threshold (20px).
- **Search Audit & Fixes**: Verified Google Search integration. Implemented client-side search fallback for non-native providers. Fixed citation visibility bug during streaming.
- **Regenerate Logic**: Distinct "Regenerate" (AI in-place) vs "Resend" (User clone).
- **KG Indicator**: Refined UI with breathing glow animation and compact text size.

---

## 工程准则更新记录 (Engineering Principles Updates)

### Rule 9: Embedding 协议扩展性准则 (2026-01-02)

**背景**: 市场上存在多种 Embedding API 协议变体，硬编码会导致维护噩梦。

**准则**:
1. **抽象优先**: 所有 Embedding 协议必须继承 `EmbeddingProtocol` 基类。
2. **协议隔离**: 每个协议实现独立文件（`src/lib/rag/protocols/{provider}.ts`）。
3. **路由表集中**: 协议选择逻辑集中在 `EmbeddingClient.selectProtocol()`，新增协议仅修改此处。
4. **统一批量策略**: 核心逻辑 (`embedBatch`, `embedLoop`, `embedChunked`) 不关心具体协议细节。

**禁止**:
- ❌ 在 `embedding.ts` 核心类中硬编码具体协议请求/响应逻辑。
- ❌ 在多处 switch-case 中重复协议判断。

**未来演进方向**:
- 🔮 配置化协议：用户通过 JSON 定义端点、认证、请求/响应映射，无需写代码。
- 🔮 协议市场：社区分享协议配置文件。

---



## 项目里程碑 (Milestones)

### 2026-01-02: RAG 系统架构优化启动
- ✅ 完成 RAG 系统全面审计
- ✅ 制定可扩展协议架构方案
- 🚧 Phase 1 实施中（预计 3 小时）

---

## 重大经验记录 (Experiences & Constraints)

### Windows 编译环境 (2025-12-26)
- **限制**: Windows 路径 260 字符限制经常导致 Android Gradle 生成产物失败。
- **对策**: 项目必须放置于根目录（如 `G:\Nx`）下，缩短物理路径。

### 网络解析安全 (Rule 8.4)
- **风险**: 502/404 等 HTML 报错页会搞崩所有 `JSON.parse` 的调用链。
- **准则**: 解析前强制校验 `Content-Type: application/json`，否则按文本阻塞处理。

---

## 待办事项 (Updated)

### 高优先级
- [ ] 优化 `QueryVectorDbSkill` 的 Top-K 检索策略，减少无关分块对上下文的污染
- [ ] 适配 `DeepSeek-V3` 官方工具调用特有协议（如果与标准 OpenAI 不同）
- [ ] 自动化构建补丁脚本 (Fix `build.gradle` after Expo Prebuild)

### 中优先级
- [ ] PDF 文件导入支持

### 低优先级
- [ ] 性能监控工具集成
- [ ] 自动化测试覆盖

---

## 文档演进

### 已创建文档
- `.agent/PROJECT_RULES.md` - 项目核心规则
- `.agent/docs/native-bridge-defensive-guide.md` - 原生桥接防御指南
- `.agent/memory/PROJECT_MEMORY.md` - 本文档

### 计划文档
- [ ] API 设计文档
- [ ] 组件库使用指南
- [ ] 部署流程文档

## Release Protocol (2026-01-01)
### 版本号规则
1. **Patch Update (+0.0.1)**: 小调整、Bug 修复、视觉微调。
2. **Minor Update (+0.1.0)**: 重构模块（RAG, Retrieval）、新增大型功能。
3. **Major Update (+1.0.0)**: 迭代累计或手动触发。

### 构建清单
- **Checklist**:
  - [ ] `package.json` version
  - [ ] `app.json` version & versionCode
  - [ ] `android/app/build.gradle` versionName & versionCode
  - [ ] `settings.tsx` Dynamic Display Check

### 安全构建流程 (Secure Build)
- **密钥管理**:
  - 真实 Keystore 与密码存储于 `secure_env/` (被 gitignore)。
  - `secure_env/secure.properties` 存储敏感凭证。
- **构建命令**:
  - **禁止**直接运行 `gradlew assembleRelease` (会导致未签名或签名失败)。
  - **WSL/Linux**: 运行 `./build-release.sh`（Bash 自动化脚本）。
  - **Windows**: 运行 `.\build-release.ps1`（PowerShell 自动化脚本）。
  - 该脚本会自动注入凭证 -> 编译 -> 清理现场。

### v4.2 - Global Crystal UI Overhaul (2026-01-13)
**目标**: 彻底移除"死灰"色调，建立统一的"Midnight Indigo"水晶态视觉体系。

**视觉规范 (Design System Update)**:
- **背景 (Backgrounds)**:
    - **半透明卡片**: `bg-white/80 dark:bg-zinc-900/60` (或 `bg-gray-50/80` 用于深层嵌套)。
    - **玻璃材质**: 集成原生 `BlurView` (dark: intensity 30, light: intensity 60)。
    - **中性设置面板**: 通用设置面板采用中性灰 `rgba(24, 24, 27, 0.8)` 以避免过度蓝调。
- **边框与微光 (Borders & Glow)**:
    - **通用边框**: `border-indigo-50 dark:border-indigo-500/10`。
    - **微光效果**: 引入 `SilkyGlow` 组件增强深度感。
- **动效 (Motion)**:
    - **标签切换**: 强制使用 `FadeIn` (300ms) 避免跳变。
    - **组件复用**: 在条件渲染中必须添加唯一 `key` 以强制触发 Reanimated 动画。

**工程改进**:
- **批量重构**: 使用 `sed` 脚本批量升级了 50+ 个组件文件，显著提升了重构效率。
- **IDE 修复**: 修复了 JSX 属性中未闭合的引号导致的 Babel 解析错误。
- **React 动效原理**: 再次确认了 React Diff 机制对动画的影响，通过 `key` 强制重挂载解决了动画失效问题。

### v4.3 - Crystal UI Standards (2026-01-16)
**目标**: 建立配置页与卡片内部的严格视觉层级 (SSOT)。

**核心规范**:
1.  **一级模块标题 (Section Header)**
    - **位置**: 卡片外部 (Top-level).
    - **样式**: `SectionHeader` 组件。
    - **特征**:
        - 左侧竖条 (Pill): W:4 H:12 Radius:999 Color:Theme[500].
        - 文字: Size:12 Weight:700 Uppercase Spacing:1.5px.
2.  **二级模块标题 (Inner Header)**
    - **位置**: 卡片内部 (Inside Card).
    - **样式**: 纯文本标签，**严禁使用竖条**。
    - **特征**: Size:xs Weight:Bold Color:Gray-500 Uppercase Spacing:Wider.
3.  **选项按钮 (Selection/Preset)**
    - **交互态**: 彻底摒弃静态玻璃底色。
    - **Unselected**: `bg-transparent` (全透明), No Border, Gray Text/Icon.
    - **Selected**: `border-theme-500` (1.5px), `bg-theme-500/10` (微光), Theme Text/Icon.

**适用范围**:
- 所有 Config Panel (Assistant, RAG, Session, System).
- 禁止在卡片内部再次嵌套带竖条的 `SettingsSection`。

### v4.2.1 - SVG Interaction & Visualization (2026-01-07)
**目标**: 解决聊天中复杂 SVG 图表无法完全显示、无法缩放的问题。
- **全屏交互**: 实现了 SVG 全屏预览模式，支持双指缩放和拖拽移动（Panning & Pinch Zoom）。
- **滚动容器**: 为长图/大图自动包裹滚动容器，防止超出气泡范围被截断。
- **渲染加速**: 优化了 `react-native-svg` 的渲染层级，解决大图展示时的 UI 掉帧问题。

### v4.3 - Intelligent Execution Modes (2026-01-14)
**目标**: 实现 Agent 处理流程的可控性与安全性平衡。
- **三档模式**:
    - **Full Auto**: 极速自动化执行（不中断）。
    - **Semi-Auto**: 智能审批模式。 Agent 自主判断敏感操作（如删除文件、修改核心配置）并主动请求用户审批。
    - **Manual**: 纯步进模式，每一步工具执行均需用户确认。
- **UI 集成**: 顶部 TabBar 实时切换模式，支持对单次请求进行动态干预。

### v4.4 - Universal Tool Parsing & Robustness (2026-01-14)
**目标**: 兼容国产大模型（GLM, DeepSeek, Kimi）多样化的工具调用输出格式。
- **正则/XML 复合解析**: 针对模型由于“幻觉”或不规范输出导致的 JSON 嵌套、XML 包裹工具调用（如 `<tool_call>...</tool_call>`）实现了鲁棒的正则表达式解析引擎。
- **Plan 块隔离**: 自动提取并隔离模型输出中的 `<plan>` 逻辑块，防止非对话内容污染主气泡。
- **参数清理**: 自动处理 GLM 等模型可能错误封装的参数结构。

### v4.5 - RAG FS & Performance Stabilization (2026-01-14)
**目标**: 解决 RAG 系统的物理一致性问题，并应对数据爆炸带来的极端性能挑战。
- **物理同步与沙箱 (P0)**:
    - 实现了 RAG 文库与 `agent_sandbox/workspace/` 的物理双向同步。
    - 确立了 Agent 权限限界，将其文件操作严格限制在 workspace 目录下。
- **重大故障修复 (Hotfix)**:
    - **数据去重**: 修复了初始化递归 Bug 导致的 `folders` 表数据爆炸（42 万条冗余记录）。
    - **性能加固**: 实现了非递归工作区检查、并发锁机制，并添加了 Max Depth 保护。
- **渲染效率优化**:
    - **选择器隔离**: 优化 Zustand 选择器，使向量化进度更新不再干扰文档列表渲染。
    - **全链路渲染缓存**: 对列表项实施 `React.memo`，并对主列表组件进行 `useMemo` 数据锁定，解决了大列表滚动卡顿问题。

---

## 经验教训 (Lessons Learned)

### 2026-01-02: 协议差异与抽象层 (Google Embedding)
- **启示**: 不同厂商 API 设计哲学差异巨大（Endpoins, Auth, Body）。
- **策略**: "统一抽象层" 的价值在于隐藏差异。策略模式将新增协议成本降至 **新增 1 个文件**。

### 2026-01-07: React.memo 与 Zustand/Immer 的陷阱
- **教训**: 自定义 `arePropsEqual` 必须维护完整的"敏感属性清单"。Model 新增字段时必须同步检查 Memo 逻辑 (如 `vectorizationStatus`)。

### 2026-01-07: LLM 输出防御 (JSON Parsing)
- **教训**: 永远不要信任 LLM 输出纯 JSON。必须使用正则 `/```json([\s\S]*?)```/` 提取，并在非关键路径降级 `console.error` 为 `warn` 以防红屏。

### 2026-01-14: 递归初始化与数据库爆炸
- **背景**: `_ensureWorkspace` 误触发递归 `loadFolders`，导致 40w+ 冗余记录。
- **教训**: 初始化检查路径严禁触发全量刷新；树形结构加载必须设置 `maxDepth` 熔断。

---

## 近期架构迭代 (Recent Updates)

### v4.6 - Steerable Loop UI & Task Scoping (2026-01-14)
**目标**: 完成可控 Agent 的 UI 落地，并修复任务状态的生命周期管理。

**UI 落地 (Steerable Loop UI)**:
- **组件实现**: 完成了 `ExecutionModeSelector` (GlassHeader 集成) 和 `ApprovalCard` (消息气泡集成)。
- **交互闭环**: 实现了从 "暂停等待" -> "用户干预/审批" -> "Resume 携带反馈" 的完整闭环。
- **Bug 修复**: 修正了 `useChat` 中 `resumeGeneration` 参数传递丢失导致干预指令被忽略的问题。

**架构重构 (Task Monitor 2.0)**:
- **生命周期修正**: 将 `activeTask` 从 Session 全局下沉至 `Message.planningTask`。
- **解决痛点**: 彻底解决了 "Regenerate 时任务状态不重置" 和 "历史消息任务状态被污染" 的问题。
- **状态隔离**: 确保了多轮对话中，每一轮的思考/规划状态互不干扰。

### v4.7 - Virtual Split Architecture (2026-01-15)
**目标**: 解决 DeepSeek/VertexAI Gemini 等模型在多工具调用场景下的协议冲突，实现跨供应商的工具调用统一。

**核心问题**:
- **DeepSeek Reasoner**: 严格要求所有 `assistant` 消息包含 `reasoning_content` 字段（即使为空）。
- **VertexAI Gemini**: 要求多轮工具调用时继承 `thought_signature`。
- **OpenAI 协议**: 单个 `assistant` 可以包含多个 `tool_calls`，但部分模型执行时会丢失中间状态。

**架构方案 - Virtual Split**:
```
原始格式 (Multi-Tool):
user -> assistant(tool_calls: [A, B]) -> tool(A) -> tool(B)

虚拟拆分后 (Single-Tool Pairs):
user -> assistant(tool_calls: [A]) -> tool(A) 
     -> assistant(tool_calls: [B]) -> tool(B)
```

**实现要点**:
1. **字段继承规则** (`virtualSplitAssistantToolPairs`):
   - `reasoning`: 内部字段，第一个 assistant 有内容，后续为空字符串（DeepSeek 要求）。
   - `thought_signature`: 所有 assistant 必须继承（VertexAI 要求）。
   - `tool_calls`: 每个 virtual assistant 只包含一个 tool_call。

2. **历史累积策略**:
   - **不重新提取整个 session**：避免因 Loop 复用同一 `assistantMsgId` 导致历史丢失。
   - **追加新 segment**：每次 Turn 结束后，只提取当前 Turn 的新增 `assistant + tool`，追加到 `currentMessages`。
   - **保留 user 消息**：虚拟拆分函数必须处理 `user` 消息，直接传递。

3. **内部格式 vs API 格式**:
   - **内部格式**: `message.reasoning`（Zustand 存储）。
   - **API 格式**: `msg.reasoning_content`（DeepSeekClient 转换）。
   - **关键修复**: `if (m.reasoning !== undefined)` 而非 `if (m.reasoning)`，允许空字符串通过。

4. **连接泄漏防御**:
   - **问题**: XHR 在收到 `[DONE]` 后可能仍处于 `readyState 3`，导致并发连接累积。
   - **修复**: 显式调用 `xhr.abort()` 确保连接完全关闭。
   - **影响**: 解决 GLM 等严格并发限额（=3）供应商的 400 错误。

**关键代码位置**:
- **虚拟拆分函数**: `chat-store.ts:virtualSplitAssistantToolPairs` (L1270-1370)
- **历史累积逻辑**:
  - isTaskCreate 分支: `chat-store.ts` (L1988-2028)
  - 正常工具执行分支: `chat-store.ts` (L2125-2160)
- **字段转换**: `deepseek.ts` (L364-370), `openai.ts` (类似)
- **XHR 关闭**: `openai.ts` / `deepseek.ts` (L140-145)

**测试覆盖**:
- ✅ DeepSeek Chat: 完整执行多轮任务链（修复前循环创建任务）。
- ✅ DeepSeek Reasoner: 完整执行复杂任务（修复前 400 Missing reasoning_content）。
- ✅ VertexAI Gemini (Gemini-2.0-Flash-Exp): 多轮工具调用成功。
- ✅ Kimi K2-Thinking: 推理模型验证通过。
- ✅ GLM-4.5-Air: 标准模型验证通过。
- ✅ GLM-4.6V-Flash: 严格并发限额（=3）验证通过（XHR 关闭修复）。

**排障关键点**:
1. **空字符串 falsy 陷阱**: JavaScript 中 `'' == false`，必须用 `!== undefined` 判断。
2. **历史提取策略**: 从 session 提取 vs 累积 currentMessages，后者是正确方案。
3. **readyState vs Promise**: Promise resolve 不等于 XHR 连接关闭。
4. **字段名不匹配**: 内部 `reasoning` vs API `reasoning_content`，需要统一由 Client 转换。

**经验教训**:
- **协议差异无法统一**: 不同供应商的 API 要求本质上不同，需要在虚拟拆分层统一处理。
- **调试日志价值**: 详细的 `[VirtualSplit]` 和 `[AgentLoop]` 日志极大加速了问题定位。
- **渐进式修复**: 先修复 DeepSeek，再修复 VertexAI，最后修复历史累积，避免一次改动太多。
- **XHR 生命周期**: 流式 API 的连接管理需要显式控制，不能依赖隐式清理。

**未来扩展注意**:
- 新增供应商时，检查是否有类似 `reasoning_content` / `thought_signature` 的特殊字段要求。
- 虚拟拆分函数是扩展点，可以添加更多字段继承逻辑。
- 如果某个供应商不支持虚拟拆分（强制要求多 tool_calls），需要在 Formatter 层添加特殊处理。

### v4.8 - Steerable Agent Loop (2026-01-15)
**目标**: 实现可控Agent执行模式，支持用户审批介入高风险操作。

**核心功能**:
- **三档执行模式**:
  - **Auto（自动）**: 全自动执行，不中断，适合研究型任务。
  - **Semi-Auto（半自动）**: 智能审批模式，遇到高风险操作（write_file、run_command等）自动暂停等待批准。
  - **Manual（手动）**: 每步都需审批，完全可控。
- **审批机制**:
  - Loop暂停时显示`ApprovalCard`，展示待执行工具名称、参数和风险原因。
  - 支持批准/拒绝操作，决策记录到Timeline。
  - Timeline新增`intervention_required`和`intervention_result`步骤类型。

**UI实现**:
- **ExecutionModeSelector**: 集成到Chat页面Header右侧，Modal选择模式，配色区分（蓝/琥珀/绿）。
- **ApprovalCard**: 嵌入AI消息气泡，琥珀色警告配色，显示工具详情和批准/拒绝按钮。
- **触感反馈**: 批准（Medium冲击）、拒绝（Error通知）。

**架构设计**:
- **Session级状态**: `executionMode`（模式）、`loopStatus`（循环状态）、`approvalRequest`（审批请求）、`pendingIntervention`（介入指令）。
- **无冲突集成**: 与虚拟拆分、Task Monitor 2.0完全兼容。
- **高风险识别**: 检测`write_to_file`、`run_command`、`replace_file_content`等工具。

**代码审计结果** (评分4.7/5.0):
- ✅ 架构设计: 5/5 - 状态设计清晰，职责分离合理
- ✅ 逻辑正确性: 5/5 - 核心逻辑无bug，边界处理完善
- ⚠️ UI/UX: 4/5 - 视觉专业，交互流畅，**缺介入输入框**
- ✅ 兼容性: 5/5 - 与现有架构无冲突

**待办事项** (高优先级):
1. 补充`ApprovalCard`的介入输入框（TextInput），允许用户提供自定义指令修改待执行操作
2. 优化高风险工具识别逻辑（改为严格匹配+配置化列表）
3. 添加使用文档到`.agent/docs/`

**参考文档**:
- 代码审计报告: `brain/d9d0d381-2af7-451e-98b4-c56ef3fe4013/steerable_agent_audit.md`
- 补充方案: `brain/d9d0d381-2af7-451e-98b4-c56ef3fe4013/intervention_input_plan.md`
- 核心文件: `chat-store.ts` (L646-698: resumeGeneration, L2048-2076: 审批检查)
- UI组件: `ExecutionModeSelector.tsx`, `ApprovalCard.tsx`

---

### v4.9 - Execution Mode Integration & Compact UI (2026-01-16)
**目标**: 将执行模式控制下沉至会话级别输入栏，并建立紧凑型配置面板的视觉规范。

**核心功能 (Execution Mode)**:
- **单入口控制**: 执行模式切换按钮直接集成到 `ChatInput` 右侧，与模型名称指示器对称布局。
- **样式对齐**: 按钮采用与 `modelBar` 一致的视觉规范（圆角 12px、灰色文字、透明深色底）。
- **冗余清理**: 移除了会话设置 (`settings.tsx`) 和全局助手设置 (`SkillsSettingsPanel.tsx`) 中的冗余配置项。
- **默认模式切换**: 全站默认执行模式由 `auto` 更改为更稳健的 `semi`。

**视觉规范 (Compact UI Standards)**:
1. **一级模块标题 (SectionHeader)**:
   - 竖条尺寸缩减: W:4 H:12 (从 W:6 H:16)。
   - 文字尺寸: `text-xs` (从 `text-sm`)。
   - 下边距减少: `mb-8` (从 `mb-16`)。
2. **卡片内部布局**:
   - 内边距: `p-4` (从 `p-5`/`p-6`)。
   - 选项间距: `mb-2` (从 `mb-4`)。
   - 描述字号: `text-[10px]` (从 `text-xs`)。
3. **滑块层级简化**:
   - 标题与当前值: 同行 `flex-row` 布局。
   - 范围指示: 独立行，极小字号。
4. **预设按钮**:
   - 增加选中态 (`isActive`) 逻辑与彩色边框 (Cyan/Amber/Indigo)。
   - 背景色由卡片底色继承改为条件渲染块内控制。
5. **通用 Glass Card**:
   - 圆角归一: 20px (Golden Standard)。
   - 深色模式边框: `border-white/10` (提升可见度)。

**Bug 修复**:
- **RAG 状态绑定**: 修复 `AdvancedRetrievalPanel` 中文档检索数量错误绑定到 `memoryLimit` 的问题。

**影响文件**:
- `ChatInput.tsx`, `session-manager.ts`, `settings-store.ts`, `ChatController.ts`
- `GlobalRagConfigPanel.tsx`, `AdvancedRetrievalPanel.tsx`, `SkillsSettingsPanel.tsx`
- `Card.tsx` (通用组件)

---

### v4.10 - Build Configuration Stabilization (2026-01-16)
**目标**: 解决编译设置中的版本漂移与签名逻辑缺陷，确保本地与发行环境的鲁棒性。

**核心修复**:
- **版本归一 (Single Source of Truth)**: 同步 `app.json`, `package.json` 与 `android/app/build.gradle` 的版本号至 `1.1.32` (versionCode: 32)。
- **签名逻辑重构**: 
    - 优先级 1: 读取 `build-release.sh` 或 `build-release.ps1` 注入的 `gradle.properties` 变量。
    - 优先级 2: 读取 `../../secure_env/` 下的物理文件（修正了之前的四级深度错误路径）。
    - 优先级 3: 回退至 `debug.keystore`。
- **工程名称规范化**: 将 `package.json` 的 `name` 字段从 `temp_init` 更新为 `nexara`。

**产出**:
- 确立了稳定的发行构建流水线，彻底解决了“发行包未签名或版本回退”的潜在风险。



### v4.11 - RAG Performance & LLM Native Capabilities (Phase 14, 2026-01-16)
**目标**: 解决 RAG 系统在复杂场景下的性能瓶颈,整合 Gemini/VertexAI 原生搜索能力,统一执行模式默认值。

**核心进展**:

1. **RAG 性能优化 (Performance Optimization)**:
   - **推理链渲染阻塞修复**:
     - **问题**: DeepSeek R1 等模型输出超长思维链(10k+ 字符)导致 `ToolExecutionTimeline` 渲染阻塞,引发UI冻结。
     - **解决**: 截断 Reasoning 文本至最后 1000 字符,保留核心思考过程。
     - **影响**: `src/components/skills/ToolExecutionTimeline.tsx`
   - **后台处理线程让步**:
     - **问题**: `GraphExtractor` 和 `VectorStore` 的大批量操作阻塞主线程。
     - **解决**: 
       - `GraphExtractor`: 每处理 5 个实体后插入 `await new Promise(r => setTimeout(r, 0))` 让步。
       - `VectorStore`: 余弦相似度计算每 100 项后让步 5ms。
     - **影响**: `src/lib/rag/graph-extractor.ts`, `src/lib/rag/vector-store.ts`
   - **ProcessingIndicator 渲染优化**:
     - **问题**: 大量 RAG 检索切片同时渲染导致布局震动。
     - **解决**: 限制同时显示的切片数量为最后 5 个。
     - **影响**: `src/features/chat/components/ProcessingIndicator.tsx`
   - **RAG 指示器持久化**:
     - **问题**: RAG 检索结果为 0 条且 `processingState` 重置为 `idle` 时,指示器消失。
     - **解决**: 添加 `processingHistory` 检查,保持"无匹配"状态显示。
     - **影响**: `src/features/chat/components/ChatBubble.tsx`

2. **Gemini/VertexAI 原生能力整合 (Native Search Integration)**:
   - **问题诊断**:
     - 当用户启用原生 Google Search Grounding 时,API 同时接收 `{ googleSearch: {} }` 和自定义 `search_internet`。
     - 系统提示词明确指示调用 `search_internet`,导致模型忽略原生能力。
   - **解决方案**:
     - **智能工具过滤**: 当 `options.webSearch` 为 `true` 时,自动从 `options.skills` 中过滤 `search_internet`。
     - **动态提示词**: 原生搜索启用时提示"USE YOUR NATIVE SEARCH CAPABILITY",禁用时提示"call 'search_internet'"。
     - **Token 缓存审计**: 确认 VertexAI 的 `getAccessToken()` 正确实现 5 分钟过期缓冲。
   - **影响**: `src/lib/llm/providers/gemini.ts`, `src/lib/llm/providers/vertexai.ts`

3. **执行模式默认值统一 (Execution Mode Defaults)**:
   - **问题**: `ExecutionModeSelector` 和新建会话的回退值不一致,部分场景默认为 `'auto'`。
   - **解决**:
     - `ExecutionModeSelector.tsx`: 回退值从 `'auto'` 改为 `'semi'`。
     - `app/chat/agent/[agentId].tsx`: 新建会话初始化从 `'auto'` 改为 `'semi'`。
   - **影响**: 所有新会话默认为 Semi-Automatic 模式,需用户审批高风险操作。

4. **发行包编译自动化 (Release Build Automation)**:
   - **Worktree 流水线完善**:
     - Git 同步 → 物理清理 (Gradle Hygiene) → npm install → expo prebuild → gradlew assembleRelease
   - **版本升级**: v1.1.34 (versionCode: 34)
   - **图标统一**: 更新为最终版 `assets/icon.png`,清理所有过时图标文件。
   - **签名配置**: 通过 `plugins/withAndroidSigning.js` 自动注入 `secure_env/` 密钥库。
   - **产物**: `Nexara-v1.1.34-Release-Signed-20260116.apk`

**经验教训**:
- ✅ **长文本渲染防御**: 对 LLM 输出的长文本(特别是思维链)必须截断保护,避免 UI 阻塞。
- ✅ **后台任务让步**: 密集计算必须定期让出主线程,保证 UI 响应性。
- ✅ **原生能力优先**: LLM 原生能力(如 Google Search)优先级应高于自定义工具封装。
- ✅ **Token 缓存强制**: 所有需要认证的 API 客户端必须实现 Token 缓存机制。
- ✅ **默认值安全性**: 系统默认值应选择更保守的选项(如 `semi` > `auto`)。

**工程准则更新**:
- **Rule 10 (新增)**: LLM 长文本输出防御 - 对展示层的 LLM 输出内容必须设置合理的字符限制(建议 1000-2000 字符)。
- **Rule 9.1 (扩展)**: 协议扩展性 - LLM 客户端必须支持原生能力与自定义工具的智能切换,避免功能冲突。

**文档同步**:
- ✅ `TODO.md`: 更新 Phase 14 完成事项
- ✅ `product-requirements.md`: 新增 Phase 14 更新日志,版本号升级至 v1.1.34
- ✅ Release APK 生成并归档

---

### Build Script Migration (2026-01-16)
**Context**: 项目已从 Windows 环境迁移至 WSL2 Ubuntu。

**变更**:
- ✅ 新增 `build-release.sh` (Bash 版本，WSL/Linux 用)
- ✅ 保留 `build-release.ps1` (PowerShell 版本，Windows 用)
- ✅ 更新 `.agent/workflows/build-android-release.md` 引用新脚本
- ✅ 更新 `PROJECT_MEMORY.md` 构建流程文档

**推荐使用**:
- WSL/Linux: `./build-release.sh`
- Windows: `.\build-release.ps1`

### v4.12 - Local Inference Reliability & i18n Completion (2026-01-17)
**目标**: 提升本地模型加载稳定性，消除 UI 交互噪音，并完成 Agent 技能层的全量本地化。

**核心进展**:
1.  **本地推理稳定性 (Local Inference P0)**:
    - **自动加载加固**: 实现了 3 秒启动延迟机制，防止 App 启动瞬间资源竞争导致的崩溃。
    - **逻辑闭环**: `initialize()` 现在返回布尔值，仅在成功加载至少一个模型时触发 "Local model ready" 提示，消除了文件缺失时的误报。
    - **API 兼容性**: 修正了 `expo-file-system/legacy` 的导入方式，适配新版 SDK 规范。
2.  **UI/UX 噪音消除**:
    - **Toast 冲突修复**: 移除了 "Tools" 指示器切换时的原生 Haptics，改为完全由统一 Toast 系统托管，解决了震动重叠问题。
    - **透明度优化**: 全局优化了 `ConfirmDialog` 组件。大幅降低了背景透明度（不透明度提升至 92%-95%），显著增强了文字易读性；移除了动画时的 `shadow-sm` 阴影残留。
    - **RAG 面板视觉对齐**: 移除了 `AdvancedRetrievalPanel` 卡片的硬编码白色背景与阴影，统一采用 `bg-gray-50/50` 磨砂质感。
3.  **多语言全量补全 (i18n Completion)**:
    - **区块标题**: 设置页 "Intelligence" 补全为 "智能体"。
    - **技能层全量翻译**: 实现了 `write_file`, `read_file`, `list_directory`, `manage_task` 等所有 Agent 核心技能的名称与描述本地化。
    - **视觉纯净度**: 移除了 "智能体技能" 标题后多余的英文括号。

**经验教训**:
- ✅ **资源注入延迟**: 对 IO 密集型启动任务（如加载 4GB 以上权重）必须设置合理的 `setTimeout` 缓冲。
- ✅ **原子化翻译**: 确保技能层动态 ID 与 `i18n` 键位严格对应，防止 UI 回退至原始 ID 字符串。
- ✅ **视觉层级优于美观**: 过高的透明度会破坏 OCR 阅读和视障支持，功能性弹窗背景应保持高不透明度（>90%）。

---

### v4.13 - Settings Panel Performance & UX (2026-01-18)
**目标**: 解决设置面板中服务商和模型管理模块的性能瓶颈，并通过动画优化提升用户体验。

**性能优化**:
1.  **主线程解阻塞 (Main Thread Unblocking)**:
    - **JSON 粘贴防卡顿**: 新建 `ParsedInput.tsx` 组件，将同步 JSON 解析改为防抖异步逻辑，彻底消除大段文本粘贴时的 UI 冻结。
    - **影响文件**: `src/features/settings/components/ParsedInput.tsx`, `ProviderModal.tsx`
2.  **服务商列表优化 (ProviderList Refactor)**:
    - **组件抽离**: 将内联渲染逻辑抽离为独立的 `ProviderList.tsx` 和 `ProviderListItem` 组件。
    - **Memoization**: 列表项使用 `React.memo` 包装，父组件状态变化（如 egg count）不再触发整个列表重渲染。
    - **影响文件**: `src/features/settings/components/ProviderList.tsx`, `app/(tabs)/settings.tsx`
3.  **Switch 组件优化 (FlashList Recycling)**:
    - **问题**: `FlashList` 每回收一个列表项携带的 `Switch` 组件都会完整触发动画（配色切换振动）。
    - **方案**: 使用 `React.memo` 包装 `Switch`；为初始挂载设置静默赋值路径，跳过 `withSpring` 动画。
    - **影响文件**: `src/components/ui/Switch.tsx`, `ModelSettingsModal.tsx`
4.  **ModelSettingsModal 整合**:
    - 合并冗余 `useEffect` 钩子；移除 `Switch` 上多余的 `key={model.uuid}`（破坏 FlashList 视图复用）。
    - 稳定化 `renderItem` 回调的依赖数组，防止每次渲染重新创建函数。

**交互优化**:
1.  **标签页平滑切换 (Tab Transitions)**:
    - **问题**: "应用设置"与"服务商管理"标签页切换瞬间跳变，体验生硬。
    - **方案**:
        - 引入 Reanimated `tabProgress` SharedValue (0 = app, 1 = providers)。
        - 指示器使用 `withTiming` + `Easing.bezier(0.33, 1, 0.68, 1)` 实现平滑非弹跳平移。
        - 内容区并行渲染，通过 `useAnimatedStyle` 实现 Cross-fade + 微量平移过渡。
    - **暗黑模式修复**: 调整指示器背景色从 `rgba(39, 39, 42, 0.9)` 到 `rgba(63, 63, 70, 0.9)`，并将 `useTheme` 移至组件顶部确保正确捕获。
    - **影响文件**: `app/(tabs)/settings.tsx`
2.  **服务商列表布局密度 (Provider List Density)**:
    - **问题**: 单个服务商卡片高度过高，间距过大，信息密度低。
    - **优化**:
        - `listContainer.gap`: 16 → 10
        - `cardContent.padding`: 12 → 10
        - `headerRow.marginBottom`: 16 → 8
        - `iconContainer` 尺寸: 40 → 36
        - `modelButton`: 移除填充背景，改为 1px 边框样式
    - **效果**: 单屏可多展示约 25% 的服务商条目。
    - **影响文件**: `src/features/settings/components/ProviderList.tsx`

**发行构建**:
- ✅ 成功执行物理层清理 (`rm -rf android/.cxx android/.gradle android/build`)
- ✅ 完成 `worktrees/release` 编译，生成 v1.1.36 (Build 44) 签名 APK
- ℹ️ **版本校准**: 修正 Version Code 回退问题，手动对齐至 Build 44。

**经验教训**:
- ✅ **Hook 顺序敏感性**: `useAnimatedStyle` 引用的外部变量（如 `isDark`）必须在其定义之前通过 Hook 获取，否则会捕获到 stale 值。
- ✅ **FlashList 与 Memoization 配合**: `key` prop 会破坏视图复用，在 `FlashList` 场景下应移除或改为 `id` 等非强制刷新机制。
- ✅ **Easing 曲线选择**: `Easing.bezier(0.33, 1, 0.68, 1)` 适用于 UI 元素平移，给予专业且不弹跳的手感。

---

#### Build 45 (2026-01-18)
**主要变更**:
- ✅ **服务商兼容性提升**: 新增 `openai-compatible` 类型，集成 URL 启发式补全（自动处理 `/v1` 路径），显著提升了 NewAPI/OneAPI 等聚合器成功率。
- ✅ **RAG 导航纠偏**: 修复 `GlobalRagConfigPanel` 错误指向 `rag-debug` 的导航逻辑，引导至正确的 `rag-advanced` 配置页。
- ✅ **数据清理完整性**: 向量库“一键清理”现在同步支持 `kg_nodes` 和 `kg_edges` 的物理级删除，确保知识图谱状态对齐。
- ✅ **UI 立体感优化**: 弃用会导致 Android 闪烁的 `elevation` 属性，采用 `borderBottomWidth` 物理模拟方案，在保持质感的同时消除了过渡残影。

**经验教训**:
- ✅ **Android 渲染防御**: `elevation` 在动画过程中极易产生残影。对于简单的立体感需求，优先考虑加厚底边（Directional Borders）辅以 Subtle Shadow。
- ✅ **URL 自动化规则**: 第三方 API 聚合器路径不一，实现静默嗅探/补全比让用户手动填写更具鲁棒性。

---

### v4.12 - SQLite Migration & Chat-Store Decoupling (2026-01-18)
**目标**: 完成核心数据存储的本地化迁移 (Phase 4b)，并初步拆解 Chat Store 巨石架构 (Phase 5/6)。

**核心架构 (Phase 4b - SQLite)**:
1.  **Dual-Write Architecture**:
    - **原理**: 保持 Zustand 作为 UI 响应式真理源，引入 SQLite 作为持久化真理源。
    - **实现**: `SessionManager` 和 `MessageManager` 在更新 Zustand store 的同时，异步写入 `SessionRepository`。
    - **优势**: 既保留了 React 的即时响应，又获得了 SQLite 的海量存储能力和查询性能。
2.  **Seamless Migration**:
    - 实现了透明迁移逻辑：启动时检查 SQLite，若为空则从 AsyncStorage 导入数据，迁移完成后自动切换数据源。
3.  **Startup Logic**:
    - App 启动流程优化：DB 初始化 -> 表结构检查 -> `loadSessions` (从 SQLite 拉取) -> UI 渲染。

**Chat Store 重构 (Phase 5/6)**:
1.  **模块化清理**:
    - **Dead Code Removal**: 删除了 `streaming-handler.ts` (269 行) 和 `agent-loop.ts` (24 行) 等无用占位文件。
    - **Pragmatism**: 经过评估，决定保留 `chat-store.ts` 中的 `generateMessage` 核心逻辑 (~1700 行)，避免单一开发者场景下的过度工程化。
2.  **最终形态**:
    - `chat-store.ts`: 2222 行 (核心状态 + 流程控制)
    - `chat/` 子模块: 1533 行 (Message/Session/Approval/Tool 等独立职责)
    - 总代码量减少 ~300 行 (-7.3%)。

**Critical Fixes**:
1.  **Vectorization Crash (P0)**:
    - **现象**: 向量化进度卡在 10% 后闪退。
    - **根因**: `TrigramTextSplitter` 在处理长难句时陷入死循环（索引步进为 0）。
    - **修复**: 引入强制步进防御机制，确保 `startIndex` 始终向前移动。
2.  **Local Model Auto-load (P1)**:
    - **现象**: 全局关闭本地模型后，重启 App 仍会自动加载。
    - **根因**: `LocalModelServer` 的 `autoLoadEnabled` 标志位与全局 `settings.localModelsEnabled` 解耦。
    - **修复**: 在 `initialize` 和 `hydrate` 阶段强制检查全局开关状态。

**UI Polish**:
- **Model ID**: 开放编辑权限，支持用户手动修正自动识别错误的 ID。
- **Provider List**: 修复暗黑模式下服务商名称不可见的问题 (`dark:text-white`)。

### v4.12 - Timeline-Embedded Loop Continuation (2026-01-19)
**目标**: 解决 Agent 执行达到轮数上限时的生硬中断问题，实现无缝的"续杯"体验。

**核心功能**:
- **时间轴嵌入 (Timeline Integration)**:
  - 摒弃了浮动卡片 (`ApprovalCard`)，将续杯请求转化为 `ExecutionStep` (type: `intervention_required`) 直接嵌入核心时间轴。
  - **交互体验**: 采用蓝色系 (`RotateCw` icon) 区分于普通敏感操作审批，提供直观的 "End Task" 与 "Continue (+10)" 选项。
- **动态额度管理 (Dynamic Budget)**:
  - **问题**: 原先仅重置 `loopCount` 导致 `loopCount (0) > maxLoops (20)` 判定失效，或瞬间再次触发。
  - **方案**: 引入 `continuationBudget` (Session 级状态)。判定公式升级为 `loopCount > maxLoops + continuationBudget`。
  - **效果**: 每次续杯累加 budget，实现线性递增的执行空间。

**Bug 修复**:
- **绿色确认缺失**: 修复了 `resumeGeneration` 中查找 `targetMsg` 的逻辑。原逻辑仅查找 `pendingApprovalToolIds`，无法匹配续杯请求。现改为类型感知的双轨查找策略。
- **键盘避让失效**: Android 端强制回归 `behavior="padding"`，修复了 `height` 模式下输入栏反向滑出屏幕的问题。

**影响文件**:
- `src/store/chat-store.ts`, `src/types/chat.ts`, `src/store/chat/approval-manager.ts`
- `src/components/skills/ToolExecutionTimeline.tsx`
- `app/chat/[id].tsx`

---

### v4.13 - Timeline UI Refinement \u0026 Model Behavior Analysis (2026-01-21)
**目标**: 深度打磨任务监控器 (Task Monitor) 和执行时间轴 (Tool Execution Timeline) 的交互逻辑、视觉对齐和多语言支持，并理清不同模型在工具禁用状态下的行为逻辑。

**UI/UX 深度优化**:
1.  **持久化模糊页眉 (Persistent Blurry Header)**:
    - **问题**: 原时间轴的折叠/展开状态使用不同的 UI 模式,无统一交互焦点。
    - **方案**: 实现了统一 `BlurView` 页眉组件,折叠时显示汇总信息(思考步数/工具调用统计),展开时显示"执行详情"标题。
    - **效果**: 提供了一致的折叠/展开触发区域,用户交互逻辑更自然。
    - **影响文件**: `src/components/skills/ToolExecutionTimeline.tsx`

2.  **Chevron 方向逻辑统一 (Icon Logic Harmonization)**:
    - **问题**: `ToolExecutionTimeline` 的折叠箭头方向与 `TaskMonitor` 不一致。
    - **修复**: 统一逻辑为"展开向上 (ChevronUp),收起向下 (ChevronDown)"。添加缺失的 `ChevronUp` 组件导入。
    - **影响文件**: `src/components/skills/ToolExecutionTimeline.tsx`

3.  **图标垂直对齐 (Icon Vertical Centering)**:
    - **目标**: 确保所有状态指示器(状态点、折叠箭头、步骤图标)与 `ChatBubble` 中头像的中心竖向对齐。
    - **设计依据**: 头像(28px)位于容器边缘向右 37px (20px 容器边距 + 1px 边框 + 2px 内边距 + 14px 半径)。
    - **调整细节**:
      - `TaskMonitor` 页眉: `paddingLeft: 29px`
      - `ToolExecutionTimeline` 页眉: `paddingLeft: 25px`
      - `ToolExecutionTimeline` 步骤列表: `paddingLeft: 22px` (2px 微调)
      - `TaskFinalResult` 分隔图标: `paddingLeft: 25px`
    - **影响文件**: 
      - `src/features/chat/components/TaskMonitor.tsx`
      - `src/components/skills/ToolExecutionTimeline.tsx`
      - `src/features/chat/components/TaskFinalResult.tsx`

4.  **Markdown 样式修正 (Dark Mode Inline Code Fix)**:
    - **问题**: 暗色模式下思考步骤内的行内代码块 (`\`code\``) 背景和文字颜色冲突,难以阅读。
    - **修复**: 显式指定暗色模式下的 `backgroundColor` 和 `color` 样式,确保对比度。
    - **影响文件**: `src/components/skills/ToolExecutionTimeline.tsx`

**国际化完善 (i18n Expansion)**:
- **新增翻译键**:
  - `skills.timeline.executionDetails` ("执行详情" / "Execution Details")
  - `skills.timeline.finalResult` ("最终结果" / "Final Result")
  - `skills.names.browse_web_page` ("网页解析" / "Browse Web Page") + 描述
- **组件本地化**:
  - `ToolExecutionTimeline.tsx`: 动态显示 "EXECUTION DETAILS"
  - `TaskFinalResult.tsx`: 动态显示 "最终结果"
- **Bug 修复**: 修复了 `ToolExecutionTimeline` 中 `useI18n` Hook 未导入的编译错误。
- **影响文件**: `src/lib/i18n.ts`, 上述两个组件文件。

**模型行为系统性分析**:
1.  **工具禁用场景研究 (Tooling Behavior Confirmation)**:
    - **结论 1 (思考流保留)**: 当用户关闭 `Tools` 按钮时,模型的思考过程 (Thinking/Reasoning) 仍正常显示。思考和工具是两个并行处理流,彼此独立。
    - **结论 2 (联网能力抑制)**: 对于 Gemini/VertexAI 等自带原生搜索的模型:
      - **技术层面**: API 请求中 `{ googleSearch: {} }` 配置依然存在。
      - **指令层面**: 系统会注入 `[TOOL USAGE: DISABLED]` 警告,要求模型仅凭内部知识回答。
      - **实际表现**: 模型通常服从指令,主动停止联网,但在极少数极新议题下仍有概率突破指令。
    - **影响文件**: `src/store/chat-store.ts`, `src/lib/llm/providers/gemini.ts`, `src/lib/llm/providers/vertexai.ts`

2.  **思考模式架构梳理 (Thinking Mode Logic)**:
    - **Gemini 系列**: 提供可选的 `thinkingConfig` API 参数,可通过输入栏的"思考等级"按钮(MINI/LOW/MED/HIGH)精准控制强度,默认 `HIGH`。
    - **GLM/DeepSeek 系列**:
      - **固有推理模型** (如 GLM-4.7, DeepSeek-R1): `forcedReasoning: true`,思考过程无法关闭,直接输出至 `reasoning_content`。
      - **引导式模型**: 通过系统提示词注入 `<!-- THINKING_START -->` 边界标记,引导模型自主决定是否输出思考。
    - **当前默认策略**: 只要模型具备思考能力,App 默认请求或引导思考输出。
    - **影响文件**: `src/lib/llm/model-utils.ts`, `src/lib/llm/model-specs.ts`, `src/lib/llm/model-prompts.ts`

**经验教训**:
- ✅ **像素级对齐的价值**: UI 元素的细致对齐(如图标中心线匹配)对专业感和视觉一致性有显著影响。
- ✅ **Markdown 渲染防御**: 在支持 Markdown 的容器内,必须显式控制代码块在暗色模式下的配色。
- ✅ **思考与工具的隔离**: 思考(Reasoning)和工具(Tool Calls)是 LLM 输出的两个正交维度,应在架构层面保持独立处理。
- ✅ **原生能力的指令抑制**: 对于内嵌能力(如 Google Search),在要求模型"禁用"时,仅能依赖指令引导,无法从协议层面完全屏蔽。

**文档同步**:
- ✅ `.agent/TODO.md`: 新增 Session 4 完成事项
- ✅ `.agent/docs/architecture/`: 待更新架构文档以反映新的 UI 组件层级
- ✅ Walkthrough 生成: 详细记录了本次 UI 调整的改动文件和视觉效果

---

### v1.2.28 - Markdown 预处理器重写 (2026-02-11)
**目标**: 修复 DeepSeek/Qwen 等模型输出"文字墙"问题，同时保证 Gemini 等规范输出不受影响。

**核心问题**:
- DeepSeek-Chat 输出完全零换行：标题无空格(`###标题`)、列表项粘连粗体(`***Down**:`)
- 此前的 `formatDeepSeekOutput` 函数因使用 `\s*` 导致非幂等，对已正确排版的内容引入多余空行

**架构方案 — 幂等预处理流水线**:
1. **LaTeX 转换**: `\[` `\]` → `$$`
2. **代码块保护**: 将 `` ``` `` 内容替换为占位符，防止 `#` 注释被误识为标题
3. **7 条幂等正则** (守卫条件 `[^\n#]` 确保已有换行时不动作):
   - 标题前空行 (有空格 / 无空格两种)
   - 标题行首无空格修复
   - 分隔符 `---` 前后空行
   - 有序列表前换行
   - 粘连 bullet+bold 拆分 (`***x**` → `* **x**`)
   - 无序列表前换行
4. **代码块恢复**

**核心踩坑经验** ⚠️:
1. **`\s` 匹配换行符**: `[-*]\s` 中的 `\s` 将 `-\n` 当作列表标记，会拆碎 `---` 分隔符 → 改用字面空格 ` `
2. **`#{1,6}` 内部 `#` 自匹配**: `([^\n])` 匹配 `###` 中的首个 `#`，将多级标题拆碎 → 守卫改为 `([^\n#])`
3. **粗体闭合 `*` 触发列表**: `**` 中的 `*` 被 `([^\n])` 匹配 → 守卫改为 `([^\n*])`

**测试验证**:
- ✅ DeepSeek 畸形输出: 标题分离、列表展开、分隔符完整
- ✅ Gemini 正常输出: 完全不变 (幂等)
- ✅ 代码块保护: 内部 `#` 不受影响
- ✅ `### 1. Title` 标题保护: 不被有序列表规则拆碎
- ✅ TypeScript 编译: 零错误

**影响文件**:
- `src/lib/markdown/markdown-utils.ts` — 核心预处理器重写
- `src/features/chat/components/ChatBubble.tsx` — 废弃 import 清理 + 注释修正

**参考文档**: `.agent/docs/archive/markdown-preprocessing-guide.md`

---
