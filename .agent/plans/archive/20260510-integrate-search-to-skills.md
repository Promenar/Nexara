# 实施计划：Web 搜索配置集成至技能系统

## 1. 目标
*   简化设置层级：移除一级页面的“Web 搜索”入口。
*   增强技能系统：在 `SkillsScreen` 中直接管理各搜索工具的参数。
*   提升交互：为 `web_search`、`search_tavily`、`search_searxng` 提供专用配置面板。

## 2. 详细步骤

### Phase 1: 移除冗余入口
- [x] **修改 `UserSettingsHomeScreen.kt`**
    - 移除 `AppSettingsContent` 中的 `web_search` 设置项。
- [ ] **审计 `NavGraph.kt`**
    - 检查是否可以停用 `search_config` 路由。

### Phase 2: 增强 `SkillsScreen.kt` 交互
- [ ] **配置入口标识**
    - 定义哪些预设工具支持“配置”。
    - 目前为：`web_search`, `search_tavily`, `search_searxng`。
- [ ] **实装配置逻辑**
    - 在 `SkillCard` (针对预设工具) 中增加“配置”图标。
    - 修改 `SkillsScreen` 状态，支持 `showSearchSettings` 弹窗。
- [ ] **专用配置面板 (ModalBottomSheet)**
    - **通用搜索配置**：切换搜索引擎、搜索深度、结果数量。
    - **Tavily 配置**：设置 API Key。
    - **SearXNG 配置**：设置自定义节点 URL。

### Phase 3: 数据与 ViewModel 联动
- [ ] **修改 `SettingsViewModel.kt`**
    - 增加管理搜索配置的状态流与更新方法。
    - 确保 `loadPreferences` 加载这些参数。

### Phase 4: 验证与清理
- [ ] **验证执行**：测试 `WebSearchSkill` 是否能正确读取新配置并执行。

## 3. 风险与考量
*   **配置碎片化**：将配置移入技能系统更符合逻辑，但需要确保用户知道在哪里配置。
*   **数据一致性**：继续沿用 `nexara_search` 存储，保证平滑迁移。
