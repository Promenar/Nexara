# Nexara Kotlin 原生迁移全量审计报告

> **审计日期**: 2026-05-04
> **审计范围**: B1-D3 后端迁移 + Session A(ViewModel) + Session B(通用组件) + Session C(设置页) + Session D(RAG UI)
> **审计人**: OpenCode

---

## 1. 编译状态

**无法验证** — `JAVA_HOME` 未设置，`assembleDebug` 无法执行。需配置 JDK 后方可编译验证。

---

## 2. 关键缺陷修复验证

| 缺陷 | 状态 | 备注 |
|------|------|------|
| C1: 双状态系统 | ✅ 已修复 | `NexaraStateStore.kt` 已删除，无残留引用 |
| C2: 无 ViewModel 层 | ✅ 已修复 | 4 个 ViewModel 已创建且均含 `companion object { fun factory() }` |
| C3: RAG UI 静态骨架 | ✅ 已修复 | `RagViewModel` 连接 Room DAO，页面绑定 viewModel |
| C4: PostProcessor.archiveToRag() 空实现 | ⚠️ 仍为桩实现 | `PostProcessor.kt:92` 存在但仅做状态流转，无实际 embedding 调用 |
| C5: image-service.ts 未迁移 | ❌ 未迁移 | 无任何 `ImageService` 相关 Kotlin 文件 |

### 详细证据

**C1 — 双状态系统消除:**
```
grep -r "NexaraStateStore" → 无结果
find NexaraStateStore.kt → 文件不存在
grep -r "SseClient" → 无结果
find SseClient.kt → 文件不存在
grep -r "import.*ChatMessage" → 无结果（旧模型）
```

**C4 — archiveToRag 桩实现 (PostProcessor.kt:92-112):**
```kotlin
suspend fun archiveToRag(params: PostProcessorParams) {
    messageManager.setVectorizationStatus(
        params.sessionId,
        listOf(params.userMsgId, params.assistantMsgId),
        "processing"
    )
    try {
        messageManager.setVectorizationStatus(
            params.sessionId,
            listOf(params.userMsgId, params.assistantMsgId),
            "success"
        )
    } catch (_: Exception) {
        messageManager.setVectorizationStatus(
            params.sessionId,
            listOf(params.userMsgId, params.assistantMsgId),
            "error"
        )
    }
}
```
缺少: embedding API 调用、向量生成、Room 向量表写入。

---

## 3. ViewModel 层

| ViewModel | 文件路径 | 状态 | 编排完整性 |
|-----------|---------|------|-----------|
| ChatViewModel | `ui/chat/ChatViewModel.kt` (471行) | ✅ 完整 | 串联 6 个 Manager: SessionManager, MessageManager, ContextBuilder, ToolExecutor, PostProcessor, ApprovalManager → LlmProvider → 流式响应 |
| AgentHubViewModel | `ui/hub/AgentHubViewModel.kt` (62行) | ✅ 完整 | agents StateFlow + selectAgent/createAgent/deleteAgent |
| SessionListViewModel | `ui/hub/SessionListViewModel.kt` (94行) | ✅ 完整 | sessions StateFlow + 搜索/删除/置顶，通过 SessionManager 操作 |
| RagViewModel | `ui/rag/RagViewModel.kt` (198行) | ✅ 完整 | 连接 5 个 DAO (folderDao, documentDao, vectorDao, kgNodeDao, kgEdgeDao) + VectorStatsService |

### ViewModel 规范合规

- [x] 每个 ViewModel 继承 `ViewModel()`
- [x] 每个 ViewModel 提供 `companion object { fun factory(application: Application): ViewModelProvider.Factory }`
- [x] ChatViewModel 的 `sendMessage()` 编排完整链路: ContextBuilder → LlmProvider → StreamParser → MessageManager → PostProcessor
- [x] 所有 ViewModel 通过 `NexaraApplication` 获取依赖
- [x] ChatViewModel 的 `uiState` 使用 `combine()` 合并多个 StateFlow

---

## 4. NexaraApplication 依赖注入

文件: `NexaraApplication.kt` (45行)

| 依赖项 | 状态 | 备注 |
|--------|------|------|
| `database: NexaraDatabase` | ✅ | Room.databaseBuilder |
| `sessionRepository: ISessionRepository` | ✅ | SessionRepository(dao, dao) |
| `messageRepository: IMessageRepository` | ✅ | MessageRepository(dao) |
| `chatStore: ChatStore` | ✅ | ChatStore() |
| `llmProvider: LlmProvider` | ✅ | LlmProvider.builder() |
| `defaultAgents: List<Agent>` | ✅ | 3 个默认 Agent |

**已知问题:**
- `llmProvider` 硬编码 `baseUrl=""`, `apiKey=""`, `model="gpt-4o"` — 应改为从 DataStore 读取用户配置

---

## 5. 通用 UI 组件质量 (Session B)

### 组件清单 (11 文件)

| 组件 | 文件 | Token合规 | 功能完整 | 备注 |
|------|------|----------|---------|------|
| NexaraConfirmDialog | `NexaraConfirmDialog.kt` (145行) | ✅ | ✅ | glass背景 + destructive模式 + 0.5dp边框 |
| NexaraBottomSheet | `NexaraBottomSheet.kt` (89行) | ✅ | ✅ | M3 ModalBottomSheet + 拖拽手柄 |
| NexaraSnackbar | `NexaraSnackbar.kt` (149行) | ⚠️ | ✅ | SUCCESS/ERROR/INFO 三类型，**3处硬编码色** |
| MarkdownText | `MarkdownText.kt` (347行) | ✅ | ✅ | 粗体/斜体/代码/列表/链接 + 流式光标 |
| NexaraLoadingIndicator | `NexaraLoadingIndicator.kt` (139行) | ✅ | ✅ | 三圆点脉冲 + SMALL/MEDIUM/LARGE |
| NexaraSearchBar | `NexaraSearchBar.kt` (138行) | ✅ | ✅ | glass背景 + 聚焦态变色 + 清除按钮 |
| NexaraCollapsibleSection | `NexaraCollapsibleSection.kt` (112行) | ✅ | ✅ | 展开/折叠动画 (AnimatedVisibility) |
| NexaraPageLayout | `NexaraPageLayout.kt` (104行) | ✅ | ✅ | TopAppBar + scroll + padding |
| AgentAvatar | `AgentAvatar.kt` (96行) | ✅ | ✅ | 三尺寸(S/M/L) + hex颜色解析 + emoji |
| NexaraGlassCard | `NexaraGlassCard.kt` (52行) | ✅ | ✅ | 0.5dp边框 + glass背景 |
| NexaraSettingsItem | `NexaraSettingsItem.kt` (90行) | ✅ | ✅ | glass风格 + chevron导航箭头 |

### Token 合规详情

**违规项 (需修复):**

1. **NexaraSnackbar.kt:37-39** — 3 处硬编码颜色:
   ```kotlin
   SUCCESS(Icons.Rounded.CheckCircle, Color(0xFF10B981)),
   ERROR(Icons.Rounded.Error, Color(0xFFEF4444)),
   INFO(Icons.Rounded.Info, Color(0xFF3B82F6))
   ```
   → 应移至 `NexaraColors` (如 `NexaraColors.Success`, `NexaraColors.Error`, `NexaraColors.Info`)

2. **RagStatusChip.kt:29-32** — 4 处硬编码颜色:
   ```kotlin
   RagStatus.READY -> Color(0xFF4ADE80)
   RagStatus.INDEXING -> Color(0xFF60A5FA)
   RagStatus.ERROR -> Color(0xFFF87171)
   RagStatus.PENDING -> Color(0xFF9CA3AF)
   ```

3. **ThemeScreen.kt:43-50** — 8 处硬编码颜色 (accent 预设色板)
   → **可接受**，属于用户可选预设调色板，语义上不需要走语义色

4. **MainTabScaffold.kt:182** — 1 处硬编码 ARGB:
   ```kotlin
   android.graphics.Color.argb(128, 192, 193, 255)
   ```

**合规项:**
- [x] 无第三方 UI 库引入
- [x] 所有圆角使用 NexaraShapes
- [x] 所有间距符合 4/8/16/24/32 体系
- [x] 边框统一 0.5.dp

---

## 6. 设置页面完整性 (Session C)

| 页面 | 文件 | 布局规范 | 导航连接 | 功能覆盖 |
|------|------|---------|---------|---------|
| ProviderFormScreen | `ProviderFormScreen.kt` (341行) | ✅ NexaraPageLayout | ✅ onNavigateBack | ✅ 三协议卡片(OpenAI/Anthropic/VertexAI) + baseUrl/apiKey表单 + 密码显隐切换 + 测试连接 + 保存 |
| ProviderModelsScreen | `ProviderModelsScreen.kt` (195行) | ✅ NexaraPageLayout | ✅ onNavigateBack | ⚠️ 模型列表 + Switch + 搜索，但数据为硬编码 `remember` |
| TokenUsageScreen | `TokenUsageScreen.kt` (236行) | ✅ NexaraPageLayout | ✅ onNavigateBack | ⚠️ 汇总卡 + Provider分组 + 清除，但数据为硬编码 `remember` |
| SearchConfigScreen | `SearchConfigScreen.kt` (304行) | ✅ NexaraPageLayout | ✅ onNavigateBack | ✅ Web Search开关 + Depth选择(basic/advanced) + 域名列表 |
| SkillsScreen | `SkillsScreen.kt` (179行) | ✅ NexaraPageLayout | ✅ onNavigateBack | ⚠️ Skills列表 + Switch + 添加，但数据为硬编码 `remember` |
| ThemeScreen | `ThemeScreen.kt` (215行) | ✅ NexaraPageLayout | ✅ onNavigateBack | ✅ 三模式卡片(Light/Dark/System) + 8色强调色 + 预览 |

### 设置页通用验证

- [x] 每个页面使用 NexaraPageLayout 统一 Scaffold
- [x] 每个页面有 onNavigateBack 回调
- [x] 表单输入使用 glass 风格输入框 (GlassInputField)
- [ ] ProviderModelsScreen/TokenUsageScreen/SkillsScreen 使用硬编码数据，未接入 ViewModel

---

## 7. RAG UI 激活 (Session D)

### RagViewModel 验证

文件: `ui/rag/RagViewModel.kt` (198行)

- [x] 连接 Room DAO: `folderDao`, `documentDao`, `vectorDao`, `kgNodeDao`, `kgEdgeDao`
- [x] 提供 `folders` StateFlow (来自 `folderDao.observeAll()`)
- [x] 提供 `documents` StateFlow
- [x] 提供 `config` StateFlow (RagConfiguration)
- [x] 提供 `stats` StateFlow (RagStats)
- [x] 提供 `vectorStats` StateFlow
- [x] 实现 `applyPreset()` (Balanced/Writing/Coding 三预设)
- [x] 实现 `updateConfig()` (直接赋值 + transform lambda)
- [x] 实现 `loadDocumentsForFolder(folderId)`
- [x] 实现 `deleteCollection(id)` + `deleteDocuments(ids)`

### RAG 页面验证

| 页面/组件 | ViewModel绑定 | 设计稿对齐 | 文件 |
|----------|-------------|-----------|------|
| RagHomeScreen | ✅ stats/folders/isIndexing/indexingProgress 来自 RagViewModel | ✅ | `RagHomeScreen.kt` (243行) |
| GlobalRagConfigScreen | ✅ config/vectorStats 来自 RagViewModel | ✅ 三预设 + 4滑块 + Reranker开关 | `GlobalRagConfigScreen.kt` (301行) |
| AdvancedRetrievalScreen | ✅ config 来自 RagViewModel | ✅ Top-K/阈值/RRF权重/BM25Boost/Reranker | `AdvancedRetrievalScreen.kt` (287行) |
| RagFolderScreen | ✅ documents 来自 RagViewModel + folderId/folderName 参数 | ✅ 文档列表 + 选择 + 删除 + 上传按钮 | `RagFolderScreen.kt` (224行) |

### RAG 子组件验证

| 子组件 | 存在 | 文件 |
|--------|------|------|
| FolderItem | ✅ | `ui/rag/components/FolderItem.kt` |
| RagDocItem | ✅ | `ui/rag/components/RagDocItem.kt` |
| IndexingProgressBar | ✅ | `ui/rag/components/IndexingProgressBar.kt` |
| RagStatusChip | ✅ | `ui/rag/components/RagStatusChip.kt` |

---

## 8. 导航路由完整性

文件: `navigation/NavGraph.kt` (185行)

### 路由注册表

| 路由常量 | 路由值 | 注册 | 入口连接 |
|----------|--------|------|---------|
| WELCOME | `"welcome"` | ✅ NavGraph:83 | startDestination |
| MAIN_TAB_SCAFFOLD | `"main_tab_scaffold"` | ✅ NavGraph:93 | WelcomeScreen:86 |
| SESSION_LIST | `"session_list"` | ✅ NavGraph:101 | MainTabScaffold:65 |
| CHAT_HERO | `"chat_hero"` | ✅ NavGraph:108 | AgentSessionsScreen:104 |
| SESSION_SETTINGS | `"session_settings"` | ✅ NavGraph:143 | ChatScreen:111 |
| RAG_ADVANCED | `"rag_advanced"` | ✅ NavGraph:115 | MainTabScaffold:72 + Settings:70 |
| RAG_GLOBAL_CONFIG | `"rag_global_config"` | ✅ NavGraph:121 | MainTabScaffold:76 |
| RAG_FOLDER | `"rag_folder/{folderId}/{folderName}"` | ✅ NavGraph:127-141 | MainTabScaffold:73-74 |
| PROVIDER_FORM | `"provider_form"` | ✅ NavGraph:149 | Settings:62 |
| PROVIDER_MODELS | `"provider_models"` | ✅ NavGraph:155 | ⚠️ 无入口 |
| TOKEN_USAGE | `"token_usage"` | ✅ NavGraph:161 | Settings:94 |
| SEARCH_CONFIG | `"search_config"` | ✅ NavGraph:167 | Settings:78 |
| SKILLS_CONFIG | `"skills_config"` | ✅ NavGraph:173 | Settings:86 |
| THEME_CONFIG | `"theme_config"` | ✅ NavGraph:179 | Settings:102 |

### UserSettingsHomeScreen 导航项

文件: `ui/hub/UserSettingsHomeScreen.kt` (177行)

| 设置项 | onClick 路由 | 对应页面 |
|--------|------------|---------|
| Model & Inference | `"provider_form"` | ✅ ProviderFormScreen |
| Knowledge Base | `"rag_advanced"` | ✅ AdvancedRetrievalScreen |
| Search Configurations | `"search_config"` | ✅ SearchConfigScreen |
| Workbench / Skills | `"skills_config"` | ✅ SkillsScreen |
| Token Usage & Billing | `"token_usage"` | ✅ TokenUsageScreen |
| Appearance & Theme | `"theme_config"` | ✅ ThemeScreen |

**注意:** PROVIDER_MODELS 路由已注册但 Settings 页面无直接导航入口。

---

## 9. 后端逻辑与前次审计对比

| 原缺陷 | 当前状态 | 修复详情 |
|--------|---------|---------|
| C1: 双状态系统 | ✅ 已修复 | NexaraStateStore 已删除，所有 Screen 使用 ViewModel 驱动 |
| C2: 无 ViewModel 层 | ✅ 已修复 | ChatViewModel/AgentHubViewModel/SessionListViewModel/RagViewModel 均已创建 |
| C3: RAG UI 静态骨架 | ✅ 已修复 | RagViewModel 连接 Room DAO，4 个 RAG 页面 + 4 个子组件均绑定 ViewModel |
| C4: PostProcessor.archiveToRag() 空实现 | ⚠️ 桩实现 | 方法存在 (`PostProcessor.kt:92`)，仅做状态流转，无 embedding 生成 |
| C5: image-service.ts 未迁移 | ❌ 未迁移 | 全代码库无 ImageService 相关文件 |

---

## 10. 文件统计

### 总览

| 类别 | 文件数 |
|------|--------|
| 主源码 (`app/src/main/java`) | 115 |
| 测试 (`app/src/test/java`) | 23 |
| **总计** | **138** |

### 主源码分类

| 层/目录 | 文件数 |
|---------|--------|
| `data/` 层 (model/remote/local/repository/rag) | 65 |
| `ui/` 层 (chat/hub/rag/settings/common/theme/welcome/renderer) | 47 |
| `navigation/` | 1 |
| 其他 (NexaraApplication.kt, MainActivity.kt, MainTabScaffold.kt) | 3 |

---

## 11. 遗留问题清单

### 严重 (应优先修复)

1. **JAVA_HOME 未配置** — 无法执行编译验证，阻塞 CI/CD
2. **PostProcessor.archiveToRag()** 桩实现 — 缺少 embedding 生成和向量存储，RAG 归档功能不可用
3. **NexaraApplication.llmProvider** 硬编码空配置 — `baseUrl=""`, `apiKey=""`, `model="gpt-4o"`，应从 DataStore 动态读取

### 中等 (应尽快修复)

4. **image-service.ts 未迁移** — 图片处理服务无 Kotlin 对应
5. **NexaraSnackbar + RagStatusChip 硬编码颜色** — 7 处 `Color(0x...)` 未使用 NexaraColors 语义色，违反 Token 合规
6. **ProviderModelsScreen / TokenUsageScreen / SkillsScreen** 使用硬编码 `remember` 示例数据 — 未接入 ViewModel，设置不持久化
7. **PROVIDER_MODELS 路由无入口** — NavGraph 注册了路由但 Settings 页面无导航项

### 低 (可后续优化)

8. **MainTabScaffold:182** — 1 处硬编码 ARGB 值 (glow shadow)
9. **AgentHubViewModel** 的 createAgent/deleteAgent 仅操作内存 StateFlow，未持久化到 Room
10. **RagHomeScreen:176** — FolderItem 的 documentCount 硬编码为 0

---

## 12. 下一步建议

### P0 (阻塞级)

1. 配置 `JAVA_HOME` → 执行 `assembleDebug` 验证编译通过
2. 实现 `PostProcessor.archiveToRag()` 的 embedding 生成 + 向量存储逻辑
3. `NexaraApplication.llmProvider` 改为从 DataStore 读取用户配置，支持动态切换协议

### P1 (功能级)

4. 修复 NexaraSnackbar 和 RagStatusChip 的硬编码颜色 → 迁入 NexaraColors 定义语义色
5. 为 ProviderModelsScreen / TokenUsageScreen / SkillsScreen 创建 ViewModel 接入真实数据
6. 添加 ProviderModelsScreen 的导航入口（从 ProviderFormScreen 保存后跳转）
7. 迁移 image-service.ts → Kotlin ImageService

### P2 (优化级)

8. AgentHubViewModel 的 CRUD 操作持久化到 Room
9. RagHomeScreen 的 FolderItem 显示真实文档计数
10. 添加集成测试覆盖 ChatViewModel 的完整消息发送流程
