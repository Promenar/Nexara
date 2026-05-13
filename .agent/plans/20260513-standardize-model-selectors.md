# 方案设计：Nexara 模型选择器标准化与关键修复

> **Date**: 2026-05-13
> **Status**: Ready for Implementation
> **Target**: 统一全平台模型选择逻辑，修复 SPA 设置 Bug

## 1. 核心目标
解决当前模型选择逻辑碎片化的问题，确保所有界面对模型能力（Capability）的判断遵循统一标准，并修复超能助手（SPA）无法切换模型的 P0 级 Bug。

## 2. 架构设计

### 2.1 统一过滤协议 (Filtering Protocol)
不再在各 UI 界面手动编写 `filter` 代码，统一基于 `ModelCapability` 枚举进行场景划分：

| 场景标签 (Tag) | 涵盖能力 (Capabilities) | 应用界面 |
| :--- | :--- | :--- |
| `chat` | `CHAT`, `REASONING` | 摘要模型、智能体主模型、RAG 提取、文本对话 |
| `image` | `IMAGE` | 绘图模型设置 |
| `embedding` | `EMBEDDING` | 向量化设置 |
| `rerank` | `RERANK` | 检索重排设置 |
| `multimodal` | `CHAT`, `REASONING`, `IMAGE`, `VISION` | 主会话模型选择（支持视觉的对话模型） |

### 2.2 逻辑分层
- **Data 层**: `ModelItem` 提供标准的 `capabilities` 转换。
- **Component 层**: `ModelPicker` 升级，支持作为独立弹窗或嵌入式面板（Nested Panel）使用。
- **UI 层**: `SessionSettingsSheet` 仅负责展示 Pager 框架，内部 `ModelPanel` 调用统一的过滤逻辑。

## 3. 详细实施步骤

### 第一阶段：修复 P0 级功能缺失
1. **SpaSettingsScreen.kt**: 
   - 修复 `clickable { }` 空回调。
   - 接入 `ModelPicker` 组件。
   - 筛选逻辑设定为 `multimodal`（超能助手作为全能终端，应支持视觉能力）。

### 第二阶段：RAG 高级设置重构
1. **RagAdvancedScreen.kt**:
   - 移除手动实现的 `ModalBottomSheet` 模型列表。
   - 统一调用 `ModelPicker`。
   - 强制应用 `chat` 标签（即 CHAT/REASONING），防止嵌入模型被选作提取模型。

### 第三阶段：主会话面板 (SessionSettingsSheet) 对齐
1. **SessionSettingsSheet.kt**:
   - 保持 `ModelPanel` 的 Pager 布局不变。
   - 替换内部硬编码的类型检查逻辑。
   - 引入 `multimodal` 标签，允许主会话使用具备视觉能力的对话模型。

## 4. 风险评估
- **UI 破坏性**: 仅修改 `ModelPanel` 内部列表内容，不触及 Pager、ThinkingLevel、Tools、Settings 其它三个标签页的 UI 结构。
- **兼容性**: 确保旧版本中未定义能力的模型默认归类为 `CHAT`，防止列表出现空白。

## 5. DIA 影响评估
- **ARCHITECTURE.md**: 无影响（仅内部实现优化）。
- **CHANGELOG.md**: 记录 SPA 修复与 RAG 过滤逻辑增强。
