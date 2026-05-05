# OpenCode 指令模板 — Session G4: 设置 Tab 全面补齐

> **项目**: Nexara Native UI  
> **工作目录**: `k:/Nexara/native-ui/`  
> **Session**: G4 — 设置 Tab 全面补齐  
> **前置依赖**: G0 (全局组件) + G1 (导航参数)  
> **设计参考根目录**: `k:/Nexara/.stitch/`

---

## 你的任务

完善设置首页的动态数据绑定，将当前 13 处硬编码替换为真实数据。实现提供商管理双 Tab 结构，补齐备份设置、便携工作台、本地模型管理等缺失页面。

**核心原则**:
- 视觉样式**完全以 `.stitch/` 中的 Stitch MD3 设计稿为准**
- 绝不参考原 RN UI 样式
- **最高优先级: 消除所有硬编码**，所有数据从 ViewModel/SharedPreferences 动态加载

---

## 设计参考

**必须先打开以下 HTML 设计稿**：

| 页面 | Stitch 设计稿 |
|------|-------------|
| E1 设置首页 | `.stitch/screens/e29e5edff1034b3e8e1099b572466ec8.html` |
| E2 提供商表单 | `.stitch/screens/eb34c022baa045dbafab586d774e766e.html` |
| E3 提供商模型 | `.stitch/screens/87c9584895e14a59a492bad0ff398f04.html` |
| E14 备份设置 | `.stitch/screens/acadb66f584643f69abc45d7b469e0a0.html` |
| E5 便携工作台 | `.stitch/screens/9a90f1c02c3f4eb4b012438eb8059140.html` |
| E7 本地模型 | `.stitch/screens/5127ef1d652e4f0398ccd345758514c5.html` |

**功能参考**: `.stitch/design_system/stitch-ui-functional-reference.md` → Group E

---

## 任务 1: 重写 `UserSettingsHomeScreen.kt` (E1) — 核心任务！

### 现有文件: `ui/hub/UserSettingsHomeScreen.kt` (178行)

**当前问题**: 13 处硬编码 (用户名 "Alex Nova"、头像 "AN"、模型 "GPT-4 Turbo" 等)

### 重写为双 Tab 结构

**Stitch 参考**: `.stitch/screens/e29e5edff1034b3e8e1099b572466ec8.html`

### 完整 UI 结构

```
Scaffold
├── TopAppBar (大标题 "设置")
└── Column
    ├── 【动画分段 Tab 控制】(应用 / 提供商) + 交叉淡入切换动画
    │
    ├── [应用 Tab]
    │   └── LazyColumn
    │       ├── 【用户头像区】NexaraGlassCard
    │       │   ├── 64dp 圆形头像 (品牌色渐变边框)
    │       │   ├── 名称 (可点击编辑 → 行内 AlertDialog)
    │       │   └── 编辑图标
    │       │
    │       ├── SettingsSectionHeader "通用"
    │       ├── SettingsItem "语言" (中/EN 切换)
    │       ├── SettingsItem "外观" (浅色/系统/深色 三选一)
    │       ├── SettingsItem "主题色" → theme_config
    │       ├── SettingsToggle "触觉反馈"
    │       │
    │       ├── SettingsSectionHeader "模型预设"
    │       ├── SettingsItem "摘要模型" → ModelPicker
    │       ├── SettingsItem "图像模型" → ModelPicker
    │       ├── SettingsItem "嵌入模型" → ModelPicker
    │       ├── SettingsItem "重排模型" → ModelPicker
    │       │
    │       ├── SettingsSectionHeader "知识管理"
    │       ├── SettingsItem "RAG 配置" → rag_global_config
    │       ├── SettingsItem "高级检索" → rag_advanced
    │       ├── SettingsItem "Token 用量" → token_usage
    │       │
    │       ├── SettingsSectionHeader "工具"
    │       ├── SettingsItem "Web 搜索" → search_config
    │       ├── SettingsItem "工作台" (实验性) → workbench
    │       ├── SettingsItem "技能" → skills_config
    │       ├── SettingsItem "本地模型" (实验性) → local_models
    │       │
    │       ├── SettingsSectionHeader "数据"
    │       ├── SettingsItem "备份" → backup_settings
    │       ├── SettingsToggle "日志"
    │       ├── SettingsItem "导出日志"
    │       │
    │       ├── SettingsSectionHeader "关于"
    │       ├── SettingsItem "关于 Nexara" (含版本号彩蛋)
    │       │
    │       └── 底部品牌: "Nexara AI • Project Narcis" + GitHub 链接
    │
    └── [提供商 Tab]
        └── Column
            ├── 【添加提供商按钮】虚线边框 + Plus 图标 + "添加提供商"
            └── 【提供商列表】
                └── 每个提供商卡片:
                    ├── 图标 + 名称 + URL
                    ├── 编辑按钮 → provider_form/{id}
                    ├── 删除按钮 → ConfirmDialog
                    └── "管理模型" → provider_models/{id}
```

### ViewModel 增强 (`SettingsViewModel.kt`)

```kotlin
// 新增 StateFlows
val userName: StateFlow<String>
val userAvatar: StateFlow<String?>
val language: StateFlow<String>          // "zh", "en"
val themeMode: StateFlow<String>         // "light", "system", "dark"
val hapticEnabled: StateFlow<Boolean>
val logEnabled: StateFlow<Boolean>
val providers: StateFlow<List<ProviderConfig>>
val currentModelSummary: StateFlow<String>
val activeSourcesCount: StateFlow<Int>
val tokenCostThisMonth: StateFlow<String>

// 新增方法
fun updateUserName(name: String)
fun setLanguage(lang: String)
fun setThemeMode(mode: String)
fun setHaptic(enabled: Boolean)
fun setLogEnabled(enabled: Boolean)
fun deleteProvider(providerId: String)
```

**所有数据从 `SharedPreferences` + `NexaraApplication` 容器读取，消除硬编码。**

---

## 任务 2: 增强 `ProviderFormScreen.kt` (E2)

### 现有文件: `ui/settings/ProviderFormScreen.kt` (14KB)

### 增强内容

1. **18+ 预设提供商网格** (2 列): OpenAI/Anthropic/Gemini/DeepSeek/Groq/Mistral/Together/Cohere/Fireworks/Perplexity/OpenRouter/xAI/SiliconFlow/ByteDance/百度/阿里/腾讯/自定义
2. **选中预设**: 品牌色边框 (2dp) + 着色背景
3. **自动填充**: 点击预设 → 自动填充 name/type/baseUrl
4. **焦点边框色过渡动画**: 输入框获得焦点时边框从透明渐变到品牌色
5. **Vertex AI 支持**: 粘贴 JSON → 自动解析 Project ID / Region / Service Account
6. **编辑模式**: 当 `providerId` 参数非空时，加载现有提供商数据

### Stitch 参考

`.stitch/screens/eb34c022baa045dbafab586d774e766e.html`

---

## 任务 3: 增强 `ProviderModelsScreen.kt` (E3)

### 现有文件: `ui/settings/ProviderModelsScreen.kt` (6.7KB)

### 增强内容

1. **搜索栏**: 过滤模型列表
2. **操作栏**: 自动获取 / 添加 / 全部禁用 / 全部删除
3. **测试连接按钮**: 旋转图标，成功=绿色延迟，失败=红色错误
4. **模型类型选择器**: chat/reasoning/image/embedding/rerank 水平胶囊按钮组，滑动指示器
5. **能力标签**: Vision=粉 / Internet=天蓝 / Reasoning=紫 小胶囊
6. **上下文长度**: 可编辑输入框
7. **批量操作**: ConfirmDialog 确认

### Stitch 参考

`.stitch/screens/87c9584895e14a59a492bad0ff398f04.html`

---

## 任务 4: 新建 `BackupSettingsScreen.kt` (E14)

### 创建文件: `ui/settings/BackupSettingsScreen.kt`

**Stitch 参考**: `.stitch/screens/acadb66f584643f69abc45d7b469e0a0.html`

### UI 结构

```
Scaffold
├── TopAppBar (GlassHeader "备份与恢复")
└── LazyColumn
    ├── 【内容选择区】CollapsibleSection "备份内容"
    │   ├── SettingsToggle "会话"
    │   ├── SettingsToggle "知识库"
    │   ├── SettingsToggle "文件"
    │   ├── SettingsToggle "设置"
    │   └── SettingsToggle "密钥"
    │
    ├── SettingsSectionHeader "本地存储"
    ├── 【导出按钮】NexaraGlassCard (Download 图标 + "导出备份")
    ├── 【导入按钮】NexaraGlassCard (Upload 图标 + "导入备份")
    │
    ├── SettingsSectionHeader "WebDAV 云端"
    ├── SettingsToggle "启用 WebDAV"
    ├── SettingsToggle "自动备份" (启用后显示)
    ├── 【上传按钮】"上传到云端"
    ├── 【下载按钮】"从云端恢复"
    └── 【配置按钮】→ ModalBottomSheet (URL + 用户名 + 密码 + 测试/保存)
```

---

## 任务 5: 新建 `WorkbenchScreen.kt` (E5)

### 创建文件: `ui/settings/WorkbenchScreen.kt`

**Stitch 参考**: `.stitch/screens/9a90f1c02c3f4eb4b012438eb8059140.html`

### UI 结构

```
Scaffold
├── TopAppBar (GlassHeader "工作台")
└── LazyColumn
    ├── 【状态卡片】NexaraGlassCard
    │   ├── 监视器图标 (活跃=绿/停用=灰)
    │   ├── 服务器状态文字
    │   └── Switch 启停开关
    │
    ├── 【稳定性引导卡片】(黄色警告)
    │   ├── "请求通知权限" + 按钮
    │   ├── "关闭电池优化" + 按钮
    │   └── "锁定最近应用" + 说明
    │
    ├── SettingsSectionHeader "连接详情" (仅运行中显示)
    ├── 【URL】NexaraGlassCard (可复制)
    ├── 【访问码】6 位大号等宽字体 + 刷新按钮
    └── 【已连接客户端】计数
```

**注意**: 实际的本地 Web 服务器逻辑可以暂用占位实现（仅 UI），后续接入 NanoHTTPD 等。

---

## 任务 6: 新建 `LocalModelsScreen.kt` (E7)

### 创建文件: `ui/settings/LocalModelsScreen.kt`

**Stitch 参考**: `.stitch/screens/5127ef1d652e4f0398ccd345758514c5.html`

### UI 结构

```
Scaffold
├── TopAppBar (GlassHeader "本地模型")
└── LazyColumn
    ├── SettingsToggle "启用本地模型"
    │
    ├── 【导入模型按钮】虚线边框 + FileDown 图标
    │
    ├── SettingsSectionHeader "已导入模型"
    ├── 【模型列表】
    │   └── 每个模型卡片:
    │       ├── 模型名称 + 大小
    │       ├── Q8 警告标签 (如适用)
    │       ├── 加载状态指示器 (Main/Emb/Rerank 插槽)
    │       ├── 加载按钮 (选择插槽)
    │       └── 删除按钮
    │
    ├── SettingsSectionHeader "引擎状态"
    ├── 【状态区】引擎信息 + 3 插槽状态 + HardwareBadge
```

**注意**: 实际的 GGUF 加载逻辑暂用占位（仅 UI），后续接入 llama.cpp。

---

## 导航集成

确保 `NavGraph.kt` 和 `UserSettingsHomeScreen` 中的所有导航目标正确连接：

```kotlin
composable(NavDestinations.BACKUP_SETTINGS) { BackupSettingsScreen(...) }
composable(NavDestinations.WORKBENCH) { WorkbenchScreen(...) }
composable(NavDestinations.LOCAL_MODELS) { LocalModelsScreen(...) }
```

---

## 完成标准

- [ ] UserSettingsHomeScreen 双 Tab (应用/提供商)，零硬编码
- [ ] 所有设置项 subtitle 从 ViewModel 动态加载
- [ ] 提供商列表管理 (增/删/编辑/管理模型) 正常
- [ ] ProviderFormScreen 18+ 预设网格 + 自动填充
- [ ] ProviderModelsScreen 搜索 + 测试 + 类型选择器 + 能力标签
- [ ] BackupSettingsScreen 本地 + WebDAV 完整实现
- [ ] WorkbenchScreen 状态 + 连接详情 UI
- [ ] LocalModelsScreen 模型管理 UI
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
