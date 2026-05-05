# OpenCode 指令模板 — Session I3: settings 模块 i18n

> **项目**: Nexara Native UI
> **工作目录**: `k:/Nexara/native-ui/`
> **Session**: I3 — settings 模块 10 个文件 i18n
> **前置依赖**: I0 (基础设施已完成)

---

## 你的任务

将 `ui/settings/` 目录下全部 10 个 .kt 文件中的硬编码文本外部化。其中 5 个文件是中文硬编码，5 个是英文硬编码，需要统一替换为 string resource。

---

## 需修改的文件 (10个)

### 1. BackupSettingsScreen.kt — 当前全中文 (24处)

| key | 英文 | 中文 |
|-----|------|------|
| `backup_title` | Backup & Restore | 备份与恢复 |
| `backup_desc` | Manage data security, local export, and cloud sync settings. | 管理数据安全、本地导出与云端同步设置。 |
| `backup_content_title` | Backup Content | 备份内容 |
| `backup_items_selected` | %1$d items selected | %1$d 项已选择 |
| `backup_content_sessions` | Sessions | 会话 |
| `backup_content_library` | Knowledge Base | 知识库 |
| `backup_content_files` | Files | 文件 |
| `backup_content_settings` | Settings | 设置 |
| `backup_content_keys` | API Keys | 密钥 |
| `backup_section_local` | Local Storage | 本地存储 |
| `backup_export_title` | Export Backup | 导出备份 |
| `backup_export_subtitle` | JSON Archive | JSON 归档 |
| `backup_import_title` | Import Backup | 导入备份 |
| `backup_import_subtitle` | Restore Data | 恢复数据 |
| `backup_section_webdav` | WebDAV Cloud | WebDAV 云端 |
| `backup_webdav_sync` | WebDAV Sync | WebDAV 同步 |
| `backup_webdav_configured` | Configured | 已配置 |
| `backup_webdav_not_configured` | Not Configured | 未配置 |
| `backup_auto_backup` | Auto Backup | 自动备份 |
| `backup_upload_cloud` | Upload to Cloud | 上传到云端 |
| `backup_restore_cloud` | Restore from Cloud | 从云端恢复 |
| `backup_config_webdav` | Configure WebDAV | 配置 WebDAV |
| `backup_info_text` | Data is stored locally by default. Configure WebDAV for end-to-end encrypted cross-device sync. | 数据默认仅在本地存储。配置 WebDAV 后可启用端到端加密的跨设备同步。 |
| `backup_webdav_config_title` | WebDAV Configuration | WebDAV 配置 |
| `backup_webdav_url_label` | Server URL | 服务器 URL |
| `backup_webdav_url_hint` | https://your-webdav-server.com | https://your-webdav-server.com |
| `backup_webdav_user_label` | Username | 用户名 |
| `backup_webdav_pass_label` | Password / Token | 密码 / Token |
| `backup_test_connection` | Test Connection | 测试连接 |
| `backup_save_config` | Save Configuration | 保存配置 |

### 2. LocalModelsScreen.kt — 当前全中文 (12处)

| key | 英文 | 中文 |
|-----|------|------|
| `local_models_title` | Local Models | 本地模型 |
| `local_models_desc` | Manage and run on-device lightweight models for maximum privacy and low latency. | 管理并运行端侧轻量模型，实现最高隐私保护与低延迟。 |
| `local_models_enable_engine` | Enable Local Engine | 启用本地引擎 |
| `local_models_engine_subtitle` | Required to run downloaded GGUF models | 运行已下载的 GGUF 模型所必需 |
| `local_models_import_title` | Import GGUF File | 导入 GGUF 文件 |
| `local_models_import_subtitle` | Select a .gguf file from device | 从设备中选择 .gguf 文件 |
| `local_models_active_slots` | Active Slots | 活跃插槽 |
| `local_models_slot_main` | Main Generator | 主生成器 |
| `local_models_slot_embeddings` | Embeddings | 嵌入 |
| `local_models_slot_reranker` | Reranker | 重排序 |
| `local_models_slot_idle` | Idle | 空闲 |
| `local_models_imported` | Imported Models | 已导入模型 |
| `local_models_engine_status` | Engine Status | 引擎状态 |
| `local_models_not_loaded` | Not Loaded | 未加载 |
| `local_models_load_model` | Load Model | 加载模型 |
| `local_models_memory_heavy` | Q8 Memory Heavy | Q8 高内存 |

### 3. WorkbenchScreen.kt — 当前全中文 (15处)

| key | 英文 | 中文 |
|-----|------|------|
| `workbench_title` | Workbench | 工作台 |
| `workbench_desc` | Turn your device into a local web server for browser access. | 将设备变成本地 Web 服务器供浏览器访问。 |
| `workbench_server_status` | Server Status | 服务器状态 |
| `workbench_server_active` | Active | 活跃 |
| `workbench_server_inactive` | Inactive | 未启用 |
| `workbench_section_stability` | Stability Guide | 稳定性引导 |
| `workbench_notification_perm` | Notification Permission | 通知权限 |
| `workbench_battery_opt` | Battery Optimization | 电池优化 |
| `workbench_recent_apps` | Lock Recent Apps | 锁定最近应用 |
| `workbench_section_connection` | Connection Details | 连接详情 |
| `workbench_url_label` | Browser URL | 浏览器 URL |
| `workbench_access_code` | Access Code | 访问码 |
| `workbench_connected_clients` | Connected Clients | 已连接客户端 |
| `workbench_copy` | Copy | 复制 |
| `workbench_refresh` | Refresh | 刷新 |

### 4. ProviderFormScreen.kt — 当前全中文 (13处)

| key | 英文 | 中文 |
|-----|------|------|
| `provider_form_title_add` | Add Provider | 添加提供商 |
| `provider_form_title_edit` | Edit Provider | 编辑提供商 |
| `provider_form_preset_title` | Presets | 预设提供商 |
| `provider_form_label_name` | Name | 名称 |
| `provider_form_label_url` | Base URL | Base URL |
| `provider_form_label_api_key` | API Key | API Key |
| `provider_form_label_project` | Google Cloud Project ID | Google Cloud Project ID |
| `provider_form_label_region` | Region | 区域 |
| `provider_form_label_sa` | Service Account JSON | 服务账号 JSON |
| `provider_form_btn_save` | Save | 保存 |
| `provider_form_btn_test` | Test Connection | 测试连接 |
| `provider_form_custom` | Custom | 自定义 |
| `provider_form_paste_json` | Paste Vertex AI JSON | 粘贴 Vertex AI JSON |

### 5. ProviderModelsScreen.kt — 当前全中文 (14处)

| key | 英文 | 中文 |
|-----|------|------|
| `provider_models_title` | Model Management | 模型管理 |
| `provider_models_search` | Search models… | 搜索模型… |
| `provider_models_auto_fetch` | Auto Fetch | 自动获取 |
| `provider_models_add` | Add Model | 添加模型 |
| `provider_models_disable_all` | Disable All | 全部禁用 |
| `provider_models_delete_all` | Delete All | 全部删除 |
| `provider_models_test` | Test | 测试 |
| `provider_models_enable` | Enable | 启用 |
| `provider_models_type_chat` | Chat | 对话 |
| `provider_models_type_reasoning` | Reasoning | 推理 |
| `provider_models_type_image` | Image | 图像 |
| `provider_models_type_embedding` | Embedding | 嵌入 |
| `provider_models_type_rerank` | Rerank | 重排 |
| `provider_models_context_length` | Context Length | 上下文长度 |

### 6. SearchConfigScreen.kt — 当前英文 (~20处)

| key | 英文 | 中文 |
|-----|------|------|
| `search_config_title` | Web Search Configuration | Web 搜索配置 |
| `search_config_engine` | Search Engine | 搜索引擎 |
| `search_config_max_results` | Max Results | 最大结果数 |
| `search_config_api_key` | API Key | API Key |
| `search_config_save` | Save | 保存 |
| `search_config_get_key` | Get API Key | 获取 API Key |

### 7. SkillsScreen.kt — 当前英文 (~30处)

| key | 英文 | 中文 |
|-----|------|------|
| `skills_title` | Agent Skills | Agent 技能 |
| `skills_loop_limit` | Loop Limit | 循环限制 |
| `skills_unlimited` | Unlimited | 无限 |
| `skills_warning_unlimited` | Unlimited mode may cause infinite loops | 无限模式可能导致无限循环 |
| `skills_tab_preset` | Preset Skills | 预设技能 |
| `skills_tab_user` | User Skills | 用户技能 |
| `skills_tab_mcp` | MCP Servers | MCP 服务器 |
| `skills_mcp_add` | Add Server | 添加服务器 |
| `skills_mcp_name` | Server Name | 服务器名称 |
| `skills_mcp_url` | Server URL | 服务器 URL |
| `skills_mcp_sync` | Sync | 同步 |
| `skills_mcp_connected` | Connected | 已连接 |
| `skills_mcp_disconnected` | Disconnected | 已断开 |
| `skills_mcp_interval` | Call Interval | 调用间隔 |

### 8. ThemeScreen.kt — 当前英文 (~10处)

| key | 英文 | 中文 |
|-----|------|------|
| `theme_title` | Theme | 个性化 |
| `theme_preview` | Preview | 预览 |

### 9. TokenUsageScreen.kt — 当前英文 (~15处)

| key | 英文 | 中文 |
|-----|------|------|
| `token_title` | Token Usage | Token 用量 |
| `token_total` | Total | 总计 |
| `token_input` | Input | 输入 |
| `token_output` | Output | 输出 |
| `token_system` | System | 系统 |
| `token_by_provider` | By Provider | 按提供商 |
| `token_by_model` | By Model | 按模型 |
| `token_reset` | Reset All Stats | 重置所有统计 |
| `token_reset_title` | Reset Statistics | 重置统计 |
| `token_reset_message` | All token usage data will be permanently deleted. | 所有 Token 用量数据将被永久删除。 |

### 10. SettingsViewModel.kt — 检查

检查 `SettingsViewModel.kt` 中是否有用户可见的硬编码文本（默认值如 `"Nexara User"` 等），如有需一并处理。

---

## 完成标准

- [ ] BackupSettingsScreen.kt — 24 处中文替换完毕
- [ ] LocalModelsScreen.kt — 12 处中文替换完毕
- [ ] WorkbenchScreen.kt — 15 处中文替换完毕
- [ ] ProviderFormScreen.kt — 13 处中文替换完毕
- [ ] ProviderModelsScreen.kt — 14 处中文替换完毕
- [ ] SearchConfigScreen.kt — 英文替换完毕
- [ ] SkillsScreen.kt — 英文替换完毕
- [ ] ThemeScreen.kt — 英文替换完毕
- [ ] TokenUsageScreen.kt — 英文替换完毕
- [ ] `values/strings.xml` 新增所有 settings_ 相关 key
- [ ] `values-zh-rCN/strings.xml` 新增所有对应中文翻译
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
