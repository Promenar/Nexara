# Nexara 设置页卡片及标题一致性重构实施计划

本项目正根据用户对于“记忆设置”、“检索设置”、“知识图谱”以及“Web 搜索设置”等二级设置页面的视觉设计规范，进行深度的 UI 一致性重构。

## 1. 重构目标与核心原则

根据用户原意与具体需求：
1. **删除外侧小标题**：移除所有卡片顶部外侧的小字标题或裸露的外侧大标题（如 `SettingsSectionHeader`、`Text(style = headlineMedium)` 等），并保持所有卡片在垂直布局中的间隔绝对一致（24.dp 或 16.dp）。
2. **统一卡片内侧中等标题**：将所有的功能标题统一移至**卡片顶部内侧左侧**位置（包括此前位于外侧被移入内侧的标题），且字号改为中等字号（统一使用 `NexaraTypography.titleMedium`，并辅以 `FontWeight.SemiBold`），确保视觉高规一致，避免过大或过小。
3. **消除页面顶部描述**：移除设置页顶部存在的小字描述文本（例如“微调全局检索增强生成逻辑...”）。
4. **完全卡片化 (Premium Design)**：对于原本裸露在外的输入框、列表或多选按钮（例如 Web 搜索中的搜索引擎选择、搜索深度、包含域名列表等），全部用精致的 `NexaraGlassCard` 进行包裹，使其拥有一致的卡片形态和卡片内侧标题。

---

## 2. 架构设计与重构对比

```mermaid
graph TD
    subgraph 重构前 (乱象)
        A1[页面小字描述] --> B1[裸露的 SettingsSectionHeader]
        B1 --> C1[GlassCard 1: 仅包含滑块组件]
        C1 --> D1[裸露的 Text: 搜索引擎列表]
        D1 --> E1[裸露的 输入框及域名列表]
    end

    subgraph 重构后 (极致对称与一致)
        A2[去除了小字描述/无抖动页头]
        A2 --> C2[GlassCard 1: 预设选择 - 包含内侧 titleMedium 标题]
        C2 --> D2[GlassCard 2: 检索参数 - 包含内侧 titleMedium 标题]
        D2 --> E2[GlassCard 3: 引擎配置 - 包含内侧 titleMedium 标题]
        E2 --> F2[GlassCard 4: 域名过滤 - 包含内侧 titleMedium 标题]
    end
```

---

## 3. 分阶段实施计划

### 阶段一：重构“记忆设置” `GlobalRagConfigScreen.kt`
- **删除** 顶部的 `R.string.rag_config_desc` 描述文字。
- **删除** 外部小标题 `SettingsSectionHeader(stringResource(R.string.rag_config_presets))`。
- **卡片化** 预设选择：使用 `NexaraGlassCard` 包裹 balanced/writing/coding 三个预设按钮，并在内侧顶部左侧添加中等标题 `stringResource(R.string.rag_config_presets)`。
- **调整字号**：将“检索参数”和“Embedding模型”卡片内侧的 `headlineMedium` 标题，统一替换为：
  `Text(text = ..., style = NexaraTypography.titleMedium, fontWeight = FontWeight.SemiBold, color = NexaraColors.OnSurface)`。
- **物理间隔**：保持所有卡片在垂直布局中的间隔绝对一致。

### 阶段二：重构“检索设置” `AdvancedRetrievalScreen.kt` 和 `AgentAdvancedRetrievalScreen.kt`
- **删除** `AdvancedRetrievalScreen` 顶部的 `R.string.retrieval_desc` 描述文字。
- **移入内侧并调整字号**：
  - 将所有外部 `SettingsSectionHeader` 移入其下方的 `NexaraGlassCard` 内部，改为内侧顶部左侧的中等字号标题（`titleMedium` + `SemiBold`）。
  - 将原本位于内侧的 `headlineMedium` 标题（如 memory 模块）统一调整为中等字号（`titleMedium` + `SemiBold`）。
- **统一助手检索设置**：对 `AgentAdvancedRetrievalScreen.kt` 内部所有卡片的 `headlineMedium` 标题也做同样的中等字号（`titleMedium` + `SemiBold`）调整。

### 阶段三：重构“知识图谱设置” `RagAdvancedScreen.kt`
- **删除** 顶部的 `R.string.rag_advanced_desc` 描述文字。
- **大卡片拆分与卡片化**：
  - 将原本混合在同一个大卡片内的“知识图谱提取配置”和“即时检索配置 (JIT RAG)”拆分为两个独立的 `NexaraGlassCard`。
  - 将所有外侧标题 `SettingsSectionHeader` 移入对应的卡片内侧顶部左侧，全部改为统一的中等字号标题（`titleMedium` + `SemiBold`）。
  - 提取提示词等卡片的外侧标题也同样移入内侧并规范字号。

### 阶段四：重构“Web 搜索设置” `SearchConfigScreen.kt`
- **删除** 顶部的 `R.string.search_config_desc` 描述文字及关联的 Spacer。
- **全部卡片化重构**：
  - **启用网页搜索卡片**：将内侧标题 `search_config_web_search` 的字号改为中等字号 `titleMedium`。
  - **搜索引擎配置卡片**：使用 `NexaraGlassCard` 将搜索引擎单选列表以及特定引擎的 URL/API Key 输入框完整包裹，在内侧顶部左侧添加中等标题 `search_config_engine_label`。
  - **搜索深度卡片**：使用 `NexaraGlassCard` 包裹搜索深度单选按钮组，在内侧顶部左侧添加中等标题 `search_config_search_depth`
  - **结果数量卡片**：使用 `NexaraGlassCard` 包裹结果数量滑块，在内侧顶部左侧添加中等标题 `search_config_result_count`
  - **域名过滤卡片**：将“包含域名”和“排除域名”分别用 `NexaraGlassCard` 包裹，使其内部具有中等大小标题。

---

## 4. 豁免声明与测试审计 (DIA Gate)
- **单元测试豁免**：由于此任务涉及纯 Compose 布局、视觉与样式代码，属于纯 UI 重构，不包含任何独立的数据处理/判定规则或算法逻辑。根据 `AGENTS.md` 规则第 3.4 节之“豁免范围”（纯布局/样式代码），我们豁免单元测试编写。
- **DIA 审计门禁**：重构完成后，通过编译命令验证代码稳定性，并全面更新 `CHANGELOG.md`、`.agent/handover.md` 完成闭环。
