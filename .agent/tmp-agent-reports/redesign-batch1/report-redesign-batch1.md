# 设置页视觉重设计：批次一交付报告

日期：2026-10-18。工作分支：redesign-batch1。对照版本：19060788be16895fd2e32704f6de045b2c09e962；实现保留在当前工作树，未提交或推送。

设置组件、设置页面及会话设置弹层已实现，任务书第 6 节全部主机命令通过。两渠道 JVM 测试、lint、截图校验、Debug APK 与设备测试 APK 构建均通过。设备断言仅编译，未运行设备或模拟器。既有 200% 字号压力预览有 G3/G5 偏离，见第 8、10 节，因此本报告不宣称所有字号与设备场景均完成规格验收。

## 1. 组件落点

以下路径均相对于仓库根。行号依据最终源文件定位。

| 文件:行号 | 组件及职责 |
| --- | --- |
| native-ui/app/src/main/java/com/promenar/nexara/ui/theme/Type.kt:47 | 8 个界面字体角色按规格 2.1 配置，保持既有换行策略；未实施批次二会话布局。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/theme/Spacing.kt:14 | 新增设置行、图标、分组、脚注及会话尺寸令牌。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraSettingsPageLayout.kt:28 | 移除局部字体副本，统一使用全局令牌。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraSettingsItem.kt:27 | 替换 ListItem；保留原参数并支持图标留白、只读复用与单选语义。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraSettingsItem.kt:69 | Item、Toggle、ValueRow、Stepper 共用几何；48/56dp 最小高度、中心对齐、20+16dp 图标列、单行副标题、整体禁用透明度 38%。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/SettingsToggle.kt:11 | 复用共享行，保留 checked、开关及整行切换语义。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraSettingsSection.kt:18 | 新增 footer/footerModifier；脚注 bodySmall、上 8dp、左右 16dp；Section 独占 24dp 分组留白；SelectionContainer 保持 SSH 地址和路由可复制。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraValueRow.kt:24 | 值文本 bodyMedium/tnum、最大 40% 尾宽、18dp 箭头、Role.Button 与当前值 stateDescription。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraValueRow.kt:52 | 值行与数值选择弹层的组合包装。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NumericSettingSpec.kt:10 | 纯 Kotlin 数值合同：解析、校验、夹取、步长对齐、格式、预设选中与自动补入默认值。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraValuePickerSheet.kt:29 | 基于 NexaraBottomSheet，内容最大屏高 70% 并内部滚动。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraValuePickerSheet.kt:45 | 离散即选即写并关闭；自定义错误不提交；连续值大号显示、滑块、预设与恢复默认；两模式均标默认项。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraInlineStepper.kt:32 | 36dp 胶囊、18dp 圆角、32dp 可见按钮、18dp 图标、48dp 触控区、tnum；边界禁用；长按 400ms 后每 80ms 步进。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraStepperSetting.kt:9 | 共享设置行尾部嵌入步进器，去除值与按钮上下堆叠。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraBottomSheet.kt:33 | 弹层标题统一 titleLarge 22/28。数值选择内容的小节标题为 titleMedium。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/ColorPickerPanel.kt:92 | 颜色选择盘保留，预设颜色触控区补足 48dp，双语颜色描述。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/settings/SettingsNumericSpecs.kt:7 | 轮次、Token 和文字缩放的可复用数值合同。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/common/SessionRetrievalSettings.kt:8 | 五个开关按字段更新；记忆来源先检查 enableMemory 总门。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt:356 | 保留思考强度卡片，其余七项推理参数改为值行。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt:557 | 工具统一设置开关，执行模式为命名枚举值行及底部单选。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt:642 | 上下文管理、检索、长期记忆、显示四组；RAG 更新基于同步 currentRagOptions 缓存组合，避免连续快切覆盖。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt:703 | 阈值值行、保留轮次步进器、立即压缩设置行；完整原理进入阈值弹层，脚注保留单句摘要及不可用原因。 |
| native-ui/app/src/main/java/com/promenar/nexara/ui/hub/UserSettingsHomeScreen.kt:556 | 语言两项枚举的底部单选；首页显示当前语言值，不使用分组脚注，原 zh/en 写入与重建回调保留。 |

## 2. 控件迁移映射

### 2.1 工具与主题（5 项）

“值行”由 NexaraNumericSetting 组合 NexaraValueRow 与 NexaraValuePickerSheet。自定义先校验范围，再按原步长对齐；不限预设不会扩大自定义范围。


| 调用点 | 原控件 → 新控件 | 范围 / 步长 | 预设 / 默认 | 存储键与语义 |
| --- | --- | --- | --- | --- |
| `LoopLimitCard` | 步进器 + 独立不限开关 → `NexaraNumericSetting` | 自定义 1–100，步长 1；另接受不限值 0 | 10、25、50（默认）、100、不限 | `loop_limit`，Int，默认 50；0 仍表示不限 |
| `AgentRuntimeSettingsCard` Token | 10,000 步长步进器 → `NexaraNumericSetting` | 0–10,000,000，步长 10,000 | 关闭（默认）、100K、500K、1M、5M | `task_token_limit`，Int，默认 0；0 仍表示关闭预算 |
| `AgentRuntimeSettingsCard` 重复上限 | 叠列步进器 → 公共行内步进器 | 2–20，步长 1 | 无独立预设；默认 3 | `tool_loop_repeat_limit`，Int；边界 canDecrease/canIncrease 保留 |
| `SearchConfigContent` 结果数量 | 滑块 1–20（18 个中间档） → 行内步进器 | 1–20，步长 1 | 无独立预设；默认 5 | `result_count`，Int |
| `ThemeScreenContent` 文字缩放 | 滑块 0.8–1.4（5 个中间档） → `NexaraNumericSetting` | UI 为 80–140%，步长 10%；保存前除以 100 转 Float | 80%、90%、100%（默认）、110%、120%、130%、140% | `theme_text_scale`，Float，默认 1f；开关键 `theme_text_scale_enabled` 不变 |

三个值选择契约由 `SettingsNumericSpecs.kt` 的 `ToolLoopLimitSpec`、`ToolTokenLimitSpec`、`InterfaceTextScaleSpec` 提供，供真实页面与弹层截图复用。默认后缀由公共选择弹层按 `defaultValue` 添加，资源标签本身不含默认后缀。

模型上下文长度、SSH 端口等既有数字文本输入属于规格允许的输入框例外，保持原校验和取值语义，没有改成会限制原值域的数值选择器。


### 2.2 检索（35 项，29 值行、6 步进器）

下表每行的原控件均为滑块。逐页原调用链为：GlobalRagConfigScreen=NexaraSlider；RagAdvancedScreen=ConfigSlider→NexaraSlider；AdvancedRetrievalScreen=AdaptiveSlider→NexaraSlider；其中 KgRetrievalSettings=KgIntegerSetting→AdaptiveSlider→NexaraSlider；AgentRagConfigScreen=RagConfigSlider→NexaraSlider；AgentAdvancedRetrievalScreen=RetrievalParamSlider→NexaraSlider。新控件一栏的值行均接数值选择弹层。离散模式允许表列范围内自定义；连续模式通过滑块及预设取值，保留原连续性。


| 页面 | 配置字段 | 新控件 | 原范围 / 保留步长 | 默认值 | 预设 | 原键或助手字段路径 | 自定义 / 语义 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 全局检索 | docChunkSize | 值行 / 离散 | 100–2000，100 | 800 | 100 / 400 / 800 / 1200 / 1600 / 2000 | doc_chunk_size | 原 steps=18；自定义按100对齐 |
| 全局检索 | chunkOverlap | 值行 / 离散 | 0–500，50 | 100 | 0 / 50 / 100 / 200 / 300 / 500 | chunk_overlap | 原 steps=9；自定义按50对齐 |
| 检索高级 | kgExtractionTimeoutSeconds | 值行 / 离散 | 5–120，1 | 120 | 5 / 30 / 60 / 90 / 120 | kg_extraction_timeout | 原无档位滑块转整数逐秒；自定义5–120 |
| 高级检索 | memoryLimit | 值行 / 离散 | 1–50，1 | 5 | 1 / 5 / 10 / 20 / 30 / 50 | memory_limit | enableRerank=true时禁用，保留原条件 |
| 高级检索 | memoryThreshold | 值行 / 连续 | 0–1，连续 | 0.7 | 0 / 0.5 / 0.7 / 0.9 / 1 | memory_threshold | step=0.0；Float原值，显示2位小数；原禁用条件保留 |
| 高级检索 | docLimit | 值行 / 离散 | 1–50，1 | 8 | 1 / 5 / 8 / 10 / 20 / 50 | doc_limit | enableRerank=true时禁用，保留原条件 |
| 高级检索 | docThreshold | 值行 / 连续 | 0–1，连续 | 0.45 | 0 / 0.3 / 0.45 / 0.7 / 1 | doc_threshold | step=0.0；Float原值，显示2位小数；原禁用条件保留 |
| 高级检索 | hybridAlpha | 值行 / 连续 | 0–1，连续 | 0.6 | 0% / 25% / 50% / 60% / 100% | hybrid_alpha | UI为0–100%；写回除100并toFloat；step=0.0 |
| 高级检索 | hybridBM25Boost | 值行 / 连续 | 0.5–2，连续 | 1 | 0.5× / 1× / 1.5× / 2× | hybrid_bm25_boost | step=0.0；Float原值，显示1位小数 |
| 高级检索 | rerankTopK | 值行 / 离散 | 5–100，1 | 30 | 5 / 10 / 30 / 50 / 100 | rerank_top_k | 自定义5–100；未配置重排模型时禁用 |
| 高级检索 | rerankFinalK | 行内步进器 | 1–20，1 | 5 | — | rerank_final_k | 20个整数；未配置重排模型时禁用 |
| 高级检索 | rerankMaxPerCall | 值行 / 离散 | 8–200，1 | 100 | 8 / 32 / 64 / 100 / 200 | rerank_max_per_call | 自定义8–200；未配置重排模型时禁用 |
| 知识图谱检索 | kgRetrievalHops | 行内步进器 | 1–2，1 | 1 | — | kg_retrieval_hops | 2个整数 |
| 知识图谱检索 | kgMaxSeeds | 值行 / 离散 | 1–32，1 | 12 | 1 / 8 / 12 / 16 / 32 | kg_max_seeds | 自定义1–32 |
| 知识图谱检索 | kgMaxNodes | 值行 / 离散 | 2–256，1 | 64 | 2 / 32 / 64 / 128 / 256 | kg_max_nodes | 自定义2–256 |
| 知识图谱检索 | kgMaxEdges | 值行 / 离散 | 1–32，1 | 24 | 1 / 8 / 16 / 24 / 32 | kg_max_edges | 自定义1–32 |
| 知识图谱检索 | kgContextMaxTokens | 值行 / 离散 | 128–16384，1 | 2048 | 128 / 1024 / 2048 / 8192 / 16384 | kg_context_max_tokens | 自定义128–16384；保守估算说明进对应选择弹层 |
| 知识图谱检索 | kgJitMinSeeds | 值行 / 离散 | 1–32，1 | 2 | 1 / 2 / 8 / 16 / 32 | kg_jit_min_seeds | 自定义1–32 |
| 知识图谱预算 | kgMaxCalls | 值行 / 离散 | 1–1024，1 | 32 | 1 / 16 / 32 / 128 / 1024 | kg_max_calls | 自定义1–1024；每文档版本调用次数 |
| 知识图谱预算 | kgMaxOutputTokens | 值行 / 离散 | 256–1048576，1 | 32768 | 256 / 8192 / 32768 / 131072 / 1048576 | kg_max_output_tokens | 自定义256–1048576；每文档版本输出Token |
| 知识图谱预算 | kgRequestMaxOutputTokens | 值行 / 离散 | 128–16384，1 | 2048 | 128 / 1024 / 2048 / 8192 / 16384 | kg_request_max_output_tokens | 自定义128–16384；单次请求输出Token |
| 知识图谱预算 | kgMaxDurationSeconds | 值行 / 离散 | 5–3600，1 | 180 | 5 / 60 / 180 / 600 / 3600 | kg_max_duration_seconds | 自定义5–3600秒 |
| 知识图谱预算 | kgLargeDocumentConfirmCalls | 值行 / 离散 | 1–128，1 | 16 | 1 / 8 / 16 / 32 / 128 | kg_large_document_confirm_calls | 自定义1–128；预计调用超过阈值须确认 |
| 助手检索 | docChunkSize | 值行 / 离散 | 100–2000，50 | 800 | 100 / 400 / 800 / 1200 / 1600 / 2000 | Agent.ragConfig.docChunkSize | 自定义按50对齐；区别于全局100步长 |
| 助手检索 | chunkOverlap | 值行 / 离散 | 0–500，10 | 100 | 0 / 50 / 100 / 200 / 300 / 500 | Agent.ragConfig.chunkOverlap | 自定义按10对齐；区别于全局50步长 |
| 助手检索 | memoryChunkSize | 值行 / 离散 | 500–3000，100 | 1000 | 500 / 1000 / 1500 / 2000 / 3000 | Agent.ragConfig.memoryChunkSize | 自定义按100对齐 |
| 助手高级检索 | memoryLimit | 行内步进器 | 3–10，1 | 5 | — | Agent.retrievalConfig.memoryLimit | 8个整数；保留原始可用状态 |
| 助手高级检索 | memoryThreshold | 值行 / 连续档位 | 0.5–0.95，0.05 | 0.7 | 50% / 60% / 70% / 80% / 95% | Agent.retrievalConfig.memoryThreshold | UI50–95%、步长5%；写回除100并toFloat |
| 助手高级检索 | docLimit | 行内步进器 | 5–15，1 | 8 | — | Agent.retrievalConfig.docLimit | 11个整数 |
| 助手高级检索 | docThreshold | 值行 / 连续档位 | 0.3–0.8，0.05 | 0.45 | 30% / 45% / 60% / 70% / 80% | Agent.retrievalConfig.docThreshold | UI30–80%、步长5%；写回除100并toFloat |
| 助手高级检索 | rerankTopK | 值行 / 离散 | 10–100，5 | 30 | 10 / 20 / 30 / 50 / 100 | Agent.retrievalConfig.rerankTopK | 自定义按5对齐；仅可用且重排开启时显示 |
| 助手高级检索 | rerankFinalK | 行内步进器 | 3–20，1 | 5 | — | Agent.retrievalConfig.rerankFinalK | 18个整数；仅可用且重排开启时显示 |
| 助手高级检索 | queryRewriteCount | 行内步进器 | 2–5，1 | 3 | — | Agent.retrievalConfig.queryRewriteCount | 4个整数；仅查询改写开启时显示 |
| 助手高级检索 | hybridAlpha | 值行 / 连续档位 | 0–1，0.05 | 0.6 | 0% / 25% / 50% / 60% / 100% | Agent.retrievalConfig.hybridAlpha | UI0–100%、步长5%；写回除100并toFloat |
| 助手高级检索 | hybridBM25Boost | 值行 / 连续档位 | 0.5–2，0.1 | 1 | 0.5× / 1× / 1.5× / 2× | Agent.retrievalConfig.hybridBM25Boost | Float原值，步长0.1、显示1位小数 |

全局原无 `steps` 的小数使用 `NumericSettingKind.Continuous` 与 `step=0.0`；显示精度只用于显示。助手原步长 0.05/0.1 保留。百分比先通过 `Float.toString().toDouble()` 转为可往返的小数再乘100，避免 Float 扩精度误差让默认60%无法选中；写回除100并转回 Float。

两页的查询改写策略均改为值行加底部单选列表，选项仍为 `hyde`、`multi-query`、`expansion`，默认 `multi-query`。未知策略值保持原样显示，只有选择新项时才写入原字符串；没有数字自定义入口。

`AgentRagConfigScreen` 提取 state/actions/content，模板保存仍传递 suspend `Result<Unit>`，继续调用 `saveRagConfig` 的原严格持久化流程。两助手页的恢复全局操作继续调用同一个 `resetToGlobal`，因此保留其同时恢复 RAG 与检索配置的原行为。


### 2.3 会话设置（10 项）

| 调用点 | 原控件 → 新控件 | 范围 / 步长 | 预设 / 默认 | 自定义 / 原字段与写入语义 |
| --- | --- | --- | --- | --- |
| 流式超时 | 滑块 → 离散值行 | 30–300 秒 / 10 | 60、120（默认）、180、300 | 30–300；InferenceParams.streamTimeout；Int?，未设置时沿用原 120 回退 |
| Top P | 滑块 → 连续值行 | 0–1 / 连续 | 0.5、0.8、0.95、1（UI 默认） | 连续拖动；topP；原 nullable/agent 继承不变，UI 未设置显示 1 |
| 最大输出 Token | 整数滑块 → 离散值行 | 0–8192 / 1 | 不限、1024、2048、4096、8192 | 0–8192；maxTokens；不限 0 仍转换为 null，原默认/继承语义不变 |
| Top K | 整数滑块 → 离散值行 | 0–100 / 1 | 不限、20、40、100 | 0–100；topK；0 仍转换为 null，原 nullable 语义不变 |
| 重复惩罚 | 滑块 → 连续值行 | 0.5–2 / 连续 | 0.5、1（UI 默认）、1.5、2 | repetitionPenalty；原 nullable，UI 回退 1；不量化连续存储值 |
| 存在惩罚 | 滑块 → 连续值行 | −2–2 / 连续 | −2、−1、0（UI 默认）、1、2 | presencePenalty；原 nullable，UI 回退 0 |
| 频率惩罚 | 滑块 → 连续值行 | −2–2 / 连续 | −2、−1、0（UI 默认）、1、2 | frequencyPenalty；原 nullable，UI 回退 0 |
| 自动压缩阈值 | 叠列步进器 → 离散值行 | 50–95% / 5% | 70%、80%（默认）、90% | 50–95；autoSummaryThreshold；百分数除 100 写原 Double，默认 0.8 |
| 保留最近轮次 | 叠列步进器 → 行内步进器 | 1–20 / 1 | 无独立预设；原解析回退 5 | keepRecentTurns；保持 activeContextWindow 每 3 条消息折算 1 轮的原兼容解析 |
| 字号 | 滑块 → 行内步进器 | 10–18 / 1 | 无独立预设；默认 15 | Session.options.fontSize；保持 updateFontSizeLocally 后原持久化回调 |

思考强度按任务书例外保留四档卡片及既有温度/Top P/Top K 组合，不新增设置键。

### 2.4 枚举与输入例外

主题、语言、模型类型、搜索深度、提供商协议、查询改写策略、会话执行模式为值行进入底部单选；保留原字符串/枚举回调。首页语言值反映当前配置的实际语言，zh/en 选项沿用原 setLanguage 与 Activity.recreate。执行模式仅 auto/semi/manual，semi 标默认，无数字自定义入口。查询策略仍为 hyde/multi-query/expansion，默认 multi-query，未知 ID 原样保留至用户选择。搜索引擎、工具设置顶层类别、检索全局预设属于页面主维度，保留现有分段选择。

模型上下文长度、SSH 端口、地址、密钥、模板等原输入框保留。温度卡片、颜色盘、命令块、搜索框和弹层滑块按任务书例外处理；没有把原任意数字文本输入收窄为新的值域。

## 3. 文案改写与说明落点

### 3.1 设置页副标题（逐条中英文）

解释保留在脚注、选择弹层或目标页面；重复说明按第 12 节删除。下表的迁移落点以第 12 节复核结果为准。


第 12 节列出复核后的脚注压缩、重复说明删除及值项解释迁移；原资源保留不代表其仍在原分组重复展示。

| 旧资源 → 新资源 | 中文：旧 → 新 | 英文：旧 → 新 | 完整解释位置 |
| --- | --- | --- | --- | --- |
| `script_approve_writes_desc` → `settings_batch1_script_write_summary` | 默认开启。关闭后写入继承脚本审批；删除始终单独审批。 → 写入文件前确认 | Enabled by default. Disabling inherits script approval for writes. Deletion always requires file approval. → Confirm before writing files | 脚本运行环境 |
| `theme_dynamic_color_desc` → `settings_batch1_dynamic_color_summary` | 根据系统壁纸动态生成应用配色 (Android 12+) → 使用系统配色 | Use colors generated from your wallpaper (Android 12+) → Use the system palette | 主题 / 强调色 |
| `theme_dynamic_color_unavailable` → `settings_batch1_dynamic_color_unavailable_summary` | 当前 Android 版本不支持动态取色 → 需要 Android 12 或更高版本 | Dynamic color is not supported on this Android version → Android 12 or later required | 副标题已完整表达，不重复脚注 |
| `theme_pure_black_desc` → `settings_batch1_pure_black_summary` | 在深色模式下使用纯黑色界面背景 → 使用纯黑背景 | Use a true black surface in dark mode → Pure black background | 深色外观组名与副标题共同表达，不重复脚注 |
| `theme_text_scale_desc` → `settings_batch1_text_scale_summary` | 仅缩放应用文字，不改变界面显示尺寸 → 调整界面文字大小 | Scale app text without changing display size → Adjust the interface text size | 文字大小选择弹层头部 |
| `termux_integration_description` → `settings_batch1_termux_integration_summary` | 启用后，Agent 可使用本机终端工具运行命令，执行遵循会话工具审批设置。 → 通过 Termux 执行命令 | Allow the Agent to run commands in the local terminal. Execution follows the tool approval settings for each session. → Run commands through Termux | Termux / 集成与诊断 |
| `termux_mount_read_only_description` → `settings_batch1_mount_read_only_summary` | 关闭后允许通过 Nexara 写入所选文件夹。 → 禁止修改挂载文件 | Turn off to allow Nexara to write to the selected folder. → Mounted files are read-only | Termux / 目录挂载 |
| `settings_default_models_desc` → `settings_batch1_home_default_models_summary` | 配置各项任务的默认模型 → 按任务选择默认模型 | Configure default models for different tasks → Default models by task | 默认模型目标页；首页无脚注 |
| `settings_skills_desc` → `settings_batch1_home_skills_summary` | 配置 Agent 工具与 MCP 服务器 → Agent 工具与 MCP | Configure Agent tools and MCP servers → Agent tools and MCP servers | 对应工具、用量或导出入口；首页无脚注 |
| `settings_token_usage_desc` → `settings_batch1_home_token_summary` | 查看模型调用与费用统计 → 模型用量与费用 | Review model usage and cost statistics → Model usage and cost | 对应工具、用量或导出入口；首页无脚注 |
| `settings_diagnostics_export_desc` → `settings_batch1_home_diagnostics_summary` | 分享有界且已脱敏的事件日志，用于精确排障 → 导出脱敏排障日志 | Share a bounded, redacted event log for troubleshooting → Redacted logs for troubleshooting | 对应工具、用量或导出入口；首页无脚注 |
| `settings_about_nexara_desc` → `settings_batch1_home_about_summary` | 版本、开源许可与项目信息 → 版本、许可与项目信息 | Version, licenses, and project information → Version, licenses, project details | 关于入口与目标页面；首页无脚注 |
| `settings_retrieval_desc` → `settings_batch1_home_retrieval_summary` | 重排序、查询改写、混合搜索 → 重排、改写与混合搜索 | Reranking, query rewriting, hybrid search → Reranking and hybrid search | 检索目标页面；首页无脚注 |
| `ssh_settings_description` → `settings_batch1_ssh_summary` | 为终端和 SSH 工具配置主机。工具仅接收主机别名。 → 保存的 SSH 连接 | Configure a host for the terminal and SSH tools. Tools receive only the host alias. → Saved SSH connections | SSH / 主机管理 |
| `ssh_keep_terminal_help` → `settings_batch1_ssh_keep_summary` | 重进页面时复用会话。空闲 10 分钟后关闭；应用进程结束时连接关闭。 → 保留终端进程 | Reuse the session when returning. Idle sessions close after 10 minutes; app process termination closes connections. → Keep the terminal running | SSH 编辑 / 终端 |
| `ssh_forward_loopback_only` → `settings_batch1_ssh_forward_summary` | 仅绑定当前设备的 127.0.0.1，目标地址由 SSH 主机解析。 → 仅监听本机 | Binds only to 127.0.0.1 on this device. The destination is resolved from the SSH host. → Listen on this device only | SSH 编辑 / 转发 |
| `backup_core_content_summary` → `settings_batch1_backup_core_summary` | 核心数据固定包含，密钥可选；本机个人头像与 Agent 头像不随备份迁移。 → 会话、资料与应用设置 | Core data is always included; API keys are optional. Device-local profile and agent avatars are not transferred. → Chats, library, and app settings | 备份 / 备份内容 |
| `backup_encrypt_package_summary` → `settings_batch1_backup_encrypt_summary` | 建议用于本地导出和 WebDAV 上传；包含密钥时必须加密。 → 加密导出备份 | Recommended for local exports and WebDAV uploads; keys always require encryption. → Encrypt exported backup files | 备份 / 备份内容 |
| `backup_export_subtitle` → `settings_batch1_backup_export_summary` | Nexara 安全归档 → 保存到本机文件 | Nexara secure archive → Save a backup to this device | 备份 / 本地 |
| `backup_import_subtitle` → `settings_batch1_backup_import_summary` | 恢复数据 → 从本机文件恢复 | Restore Data → Restore from a local backup | 备份 / 本地 |
| `local_models_engine_subtitle` → `settings_batch1_local_engine_summary` | 运行已下载的 GGUF 模型所必需 → 在设备上运行 GGUF 模型 | Required to run downloaded GGUF models → Run GGUF models on this device | 本地模型 / 引擎与导入 |
| `local_models_import_subtitle` → `settings_batch1_local_import_summary` | 从设备中选择 .gguf 文件 → 导入 GGUF 文件 | Select a .gguf file from device → Import a GGUF file | 本地模型 / 引擎与导入 |
| settings_language_desc → 同行语言值 | 选择应用界面语言 → 无副标题，同行值取 settings_language_zh/en | Choose the app interface language → No subtitle; current language value | 标题和所选值已表达，不设置首页脚注 |

其它说明与数据保持方式：

- 执行轮次与 Token 的解释分别进入对应选择弹层；重复上限与重置授权各保留一句紧凑脚注。预算组全部无图标，无 `leadingSpacer` 调用。
- Termux 的安装、权限、SSH 与目录挂载说明使用双语紧凑脚注并按步骤分组；版本可识别条件、命令、错误恢复和完整后台风险说明保留。命令块和诊断 stdout/stderr 保留独立块，状态仍来自原 `visualState()`。
- SSH 主机的地址、终端保留策略与转发路由保留为脚注；信任状态为简短同行副标题，认证类型列入同组完整脚注。认证方式、私钥导入/生成和保留凭据说明在编辑分组脚注。
- 默认模型的 `settings_model_quick_description` 移入模型分组脚注，并将 `default_model_quick_description` 标签迁移到脚注文本。
- 提供商的品牌/首字图标保留为 20dp 图标列。协议为一行副标题；完整地址、启用状态、凭据状态与未知协议 ID 保留在分组脚注。
- 模型列表保留名称、远程 ID、品牌图标、启用开关和能力；能力通过卡片外 `NexaraMetaLine` 呈现。远程 ID 原标签通过公共 `subtitleModifier` 保留。
- 模型编辑的类型为值行加底部单选，能力为公共开关；远程 ID 解释和输入/输出限制及知识截止信息进入脚注。名称、远程 ID、上下文输入保持原回调。原编辑器通过 `LaunchedEffect` 自动写入 `onUpdate`，未新增保存事务或改变写入时机。
- 本地模型保留模型大小、量化类型（空值仍显示原 N/A）、架构、内存提示、已加载槽位、后端、模型名、进度和错误信息。模型大小和量化等元数据及完整错误位于分组脚注；槽位行仍显示动态模型名、后端、载入状态与进度。
- 技能包保留紧凑加载说明，详情由编辑器查看；预设工具、自定义工具、MCP 的完整 ID、描述、URL 和错误紧邻所属项目。行内显示名称、开关与必要操作，展开 MCP 工具列表仍可查看完整工具名。
- 搜索的 Keenable 免密钥说明进入凭据分组脚注；密钥编辑的 SecretField 流程保留，失败重试为 TextButton。
- 主题、模型类型、搜索引擎和语言选择行显式声明 `Role.RadioButton` 与 `selected`，颜色选择保持原单选语义，并补充颜色的本地化无障碍描述。


### 3.2 补充状态副标题与会话说明

| 位置 | 原中文 → 新中文 | 原英文 → 新英文 | 完整信息位置 |
| --- | --- | --- | --- |
| 首页知识库配置 | 分块、记忆、向量化、知识图谱 → 分块与记忆配置 | Chunking, memory, vectorization → Chunking and memory settings | 目标检索页面承担详细说明；首页无脚注 |
| SSH 未信任主机 | 需要确认主机指纹 → 待确认主机指纹 | Fingerprint confirmation required → Fingerprint needs review | 主机脚注保留完整原状态及主机地址 |
| SSH 认证 | SSH 密钥 / 密码 → 移入脚注 | SSH key / Password → Moved to footer | 认证方式：%1$s / Authentication: %1$s，保留实际认证类型 |
| SSH 转发运行状态 | 正在 127.0.0.1:%1$d 转发 → 监听端口 %1$d | Forwarding active on 127.0.0.1:%1$d → Listening on port %1$d | 原实际绑定端口及 ssh_forward_route 完整路由保留在可复制脚注 |
| 本地模型向量加载标记 | Active Emb（旧硬编码） → 无行内徽标 | Active Emb → No inline badge | 脚注为 嵌入模型就绪 / Embeddings ready，模型元数据保持原值 |
| 本地模型重排加载标记 | Active Rerank（旧硬编码） → 无行内徽标 | Active Rerank → No inline badge | 脚注为 重排模型就绪 / Reranker ready，模型元数据保持原值 |
| MCP 正常连接 | MCP 2026-07-28 · HTTPS → 已连接/未连接 | MCP 2026-07-28 · HTTPS → Connected/Disconnected | 原 skills_mcp_modern_transport、URL 与完整错误在对应 MCP 分组脚注 |
| MCP 旧配置 | 旧版配置：仅可查看、迁移或删除，不能同步和执行。 → 已连接/未连接 | Legacy configuration: visible for migration or deletion, but cannot sync or execute. → Connected/Disconnected | 原 skills_mcp_legacy_unsupported、URL 与完整错误在对应 MCP 分组脚注 |
| MCP 同步失败 | 同步失败：%1$s → 已连接/未连接 | Sync failed: %1$s → Connected/Disconnected | 原 skills_mcp_sync_error、URL 与完整错误在对应 MCP 分组脚注 |
| MCP 本机连接不可用 | 本机 HTTP MCP 未启用或缺少访问令牌；请打开“添加 MCP”的高级选项，或删除服务器后重新配置令牌。 → 已连接/未连接 | Local HTTP MCP is disabled or missing an access token. Open the advanced option in Add MCP, or delete and add the server to configure its token. → Connected/Disconnected | 原 mcp_loopback_unavailable、URL 与完整错误在对应 MCP 分组脚注 |
| 会话压缩不可用（进行中） | 正在压缩上下文，请稍候 → 正在压缩上下文 | Context is being compacted. Please wait. → Compaction in progress | 原完整不可用原因在上下文管理脚注 |
| 会话压缩不可用（生成中） | 正在生成回复，完成后可立即压缩 → 回复完成后可用 | A response is being generated. Compact context after it finishes. → Available after response | 原完整不可用原因在上下文管理脚注 |
| 会话上下文原理 | 达到阈值后先折叠较早的工具结果，仍不足时将最近 %1$d 轮之前的对话总结为摘要；完整记录仍保留在会话中。 → 无副标题 | At the threshold, earlier tool results are folded first. If more space is needed, conversations before the most recent %1$d turns are summarized. The complete record stays in the session. → No subtitle | 自动压缩阈值选择弹层头部保留完整原理 |
| 会话压缩保留说明 | 折叠较早的工具结果并总结历史，保留最近 %1$d 轮。 → 无副标题 | Fold earlier tool results and summarize history while retaining the most recent %1$d turns. → No subtitle | 与原理重复的句子删除，分组脚注保留单句压缩摘要 |
| 会话重排不可用 | 未配置默认重排模型，重排序已禁用。 → 无副标题 | No default rerank model is configured. Reranking has been disabled. → No subtitle | 检索脚注保留完整原文与原参数 |
| 会话检索关闭状态 | 所有记忆功能已关闭 → 无副标题 | All memory features disabled → No subtitle | 检索脚注保留完整原文与原参数 |
| 会话检索关闭说明 | AI 将仅基于当前对话上下文回答 → 无副标题 | AI will only use current conversation context → No subtitle | 检索脚注保留完整原文与原参数 |
| 会话长期记忆 | 既有资源“跨会话语义记忆上下文”（旧弹层未展示） → 新只读来源摘要：已关闭/会话 RAG/本会话及跨会话 | Existing resource “Semantic memory for context across sessions” (not shown in the old sheet) → New read-only source: Off/Session RAG/Session and global | 新分组 footer 使用该既有资源，未新增独立开关键 |
| 执行模式自动 | 自动：所有工具自动运行 → 所选模式名（同行值） | Auto: All tools run automatically → Selected mode name (inline value) | 完整解释随所选模式显示于执行模式弹层头部 |
| 执行模式半自动 | 半自动：写入文件和高风险工具需要批准 → 所选模式名（同行值） | Semi: File writes and high-risk tools need approval → Selected mode name (inline value) | 完整解释随所选模式显示于执行模式弹层头部 |
| 执行模式手动 | 手动：所有工具都需要批准 → 所选模式名（同行值） | Manual: All tools need approval → Selected mode name (inline value) | 完整解释随所选模式显示于执行模式弹层头部 |
| 会话临时授权（动态内容） | 原 grant.toolName 与 ToolExecutionTargets.display(grant.target) → 撤销 %1$s + %1$d. %2$s | Same original tool/target expressions → Revoke %1$s + %1$d. %2$s | 按同编号完整目标列入授权脚注；原授权回调与完整点击描述保留 |

### 3.3 检索页副标题、动态状态与标题


下表资源在原文件中保留，长说明通过 footer 或编辑器可访问；没有把用户模板正文写入新资源。

| 位置 | 原中文 | 新中文短副标题 | 原英文 | 新英文短副标题 | 完整说明位置 |
| --- | --- | --- | --- | --- | --- |
| 全局检索·知识图谱入口 | 知识图谱与抽取设置 | 图谱抽取与参数 | Knowledge graph and extraction settings | Extraction and graph options | 入口副标题已表达，不重复分组脚注 |
| 高级检索·混合检索 | 结合密集向量与稀疏 BM25 匹配 | 融合向量与关键词 | Combine dense vector with sparse BM25 matching | Vector and keyword matching | 混合检索分组 footer：原 retrieval_hybrid_desc |
| 高级检索·查询改写 | 改写用户查询以提升检索准确性 | 改写查询提升召回 | Rewrite user queries for better retrieval accuracy | Rewrite queries for retrieval | 查询改写分组 footer：原 retrieval_rewrite_desc |
| 高级检索·重排不可用 | 未检测到已配置的重排模型。重排序可提升多数据源融合后的相关性，请先前往“提供商管理”添加 Rerank 服务并设为默认重排模型。 | 未配置重排模型 | No rerank model is configured. Reranking improves relevance across multiple retrieval sources. Add a Rerank service in Provider Management and set it as the default rerank model. | No default rerank model | 重排分组 footer：原完整不可用说明 |
| 助手高级检索·重排不可用 | 未检测到已配置的重排模型，此开关已禁用。请先前往“提供商管理”添加 Rerank 模型并设为默认重排模型。 | 未配置重排模型 | No rerank model is configured, so this option is disabled. Add a Rerank model in Provider Management and set it as the default rerank model. | No default rerank model | 重排分组 footer：原完整不可用说明 |
| 知识图谱检索·JIT | 仅在持久图覆盖不足时，对可见命中片段即时抽取 | 图谱覆盖不足时补充 | Extract visible retrieved chunks only when graph coverage is insufficient | Fill gaps in graph coverage | 知识图谱检索分组 footer：原 kg_jit_fallback_desc |
| 助手编辑·RAG入口 | 分块、记忆、向量化、知识图谱 | 分块与记忆配置 | Chunking, memory, vectorization | Chunking and memory settings | 知识分组 footer：原 agent_edit_rag_desc |
| 助手编辑·高级检索入口 | 重排序、查询改写、混合搜索 | 重排、改写与混合检索 | Reranking, query rewriting, hybrid search | Rerank, rewrite and hybrid search | 知识分组 footer：原 agent_edit_retrieval_desc |

动态内容及独立说明的处理：

| 位置 | 原内容 → 当前行内容（中 / 英） | 完整信息位置 |
| --- | --- | --- |
| 全局检索·摘要模板 | 模板正文截取100字符 → 默认模板/自定义模板；Default template / Custom template | 原编辑器完整正文；删除与可点击行重复的提示脚注 |
| 检索高级·提取提示词 | 正文最多4行 → 默认模板/自定义模板；Default template / Custom template | 原编辑器；默认提取说明保留在 footer，并明确加“默认提示词：/Default prompt:”前缀 |
| 检索高级·提取模型 | 模型名副标题 → 同行模型值 | 模型选择器；超长模型值按值行规则截断，不算短副标题 |
| 检索高级·超时说明 | 容器内独立长说明 → 无副标题 | 原 rag_advanced_kg_timeout_desc 仅放在对应数值弹层说明 |
| 检索高级·页面说明 | rag_advanced_desc 独立文字 → 无副标题 | 知识图谱分组 footer |
| 高级检索·页面说明 | retrieval_desc 独立文字 → 无副标题 | 记忆检索分组 footer |
| 高级检索·重排接管状态 | 原行尾 Rerank 标签 → 无行尾徽标 | 记忆/文档分组 footer 说明启用重排时数量与阈值不可编辑；原禁用条件保留 |
| 知识图谱预算 | 容器内 kg_budget_partial_hint → 无副标题 | 部分预算风险留在分组脚注；每文档版本和超阈值确认说明进入对应值项弹层 |
| 助手检索·摘要模板 | 模板正文最多2行 → 沿用“使用默认/已配置”；Using Default / Configured | 原编辑器完整正文；删除重复的点击编辑脚注，非空继承模板不会误报为自定义 |
| 助手编辑·系统提示词 | 提示词正文最多2行 → 沿用“已配置/未设置”；Configured / Not Set | 原编辑器完整正文；原 agent_edit_prompt_hint 放在 footer |
| 助手编辑·模型 | 动态模型名副标题 → 已选专属模型/未设置；Agent model selected / Not Set | 完整模型名或ID放在模型分组 footer，选择器保持原实现 |
| 两助手配置页·继承状态 | 状态徽标/高容器 → 只读配置来源行，继承全局配置/自定义；Inherited from Global / Custom | 复用 internal NexaraSettingsRowLayout，不给只读状态增加点击语义 |
| 助手编辑·图标 | 页面独立图标网格 → 选择图标行，副标题为当前图标名称或自定义；Choose icon with localized icon name / Custom | 点击打开原10个图标构成的底部网格；原emoji ID和选择回调保留 |

长标题收敛（中英文原资源保留）：

| 原资源 | 新中文标题 | 新英文标题 | 语义保留 |
| --- | --- | --- | --- |
| rag_config_rerank_max_per_call | 单次重排文档上限 | Documents per rerank call | 原单次重排批量说明保留在 footer/弹层 |
| kg_context_budget | 上下文 Token 上限 | Context token limit | 保守估算说明在对应选择弹层 |
| kg_budget_calls | 文档调用上限 | Document call limit | 每文档版本说明在 footer |
| kg_budget_output | 文档输出 Token | Document output tokens | 每文档版本说明在 footer |
| kg_budget_request_output | 单次输出 Token | Tokens per request | 单次请求维度保留 |
| kg_budget_duration | 抽取时长（秒） | Time limit (sec) | 秒单位保留 |
| kg_budget_confirm_threshold | 大文档确认阈值 | Large document threshold | 预计调用超过阈值需确认说明在 footer |

助手高级检索两处“记忆/文档相似度阈值”标题统一为原 `retrieval_similarity_threshold`；记忆与文档维度由分组标题表达，不改变配置字段。


### 3.4 通用及会话标题资源

| 资源 | 中文旧 → 新 | 英文旧 → 新 | 语义说明 |
| --- | --- | --- | --- |
| sheet_tab_settings | 设置 → 上下文 | Settings → Context | 键名不变，其余三个标签不变 |
| sheet_settings_ui_title | 界面设置 → 显示 | UI Settings → Display | 只调整第四分组标题 |
| sheet_settings_keep_recent_turns | 压缩后保留最近轮数 → 保留最近轮次 | Recent turns to retain after compaction → Retained turns | 完整压缩后保留说明在上下文管理脚注 |
| sheet_settings_summary_threshold | 自动压缩阈值 → 自动压缩阈值 | Auto Compression Threshold → Compression threshold | 完整原理在阈值弹层，脚注保留单句摘要 |
| script_approve_writes | 单独审批脚本文件写入 → 脚本写入审批 | Approve script file writes separately → Approve script writes | 默认、继承与删除审批完整解释在脚本分组脚注 |
| p0_repeat_limit | 连续重复调用暂停次数 → 重复上限 | Pause after repeated calls → Repeat limit | 相同工具和参数连续重复达到阈值暂停的原说明在预算脚注 |
| p0_reset_consent | 重置不限轮次全自动执行确认 → 重置授权 | Require unlimited automatic execution confirmation again → Reset authorization | 下次开启不限轮次全自动执行须再次确认的原说明在预算脚注 |

新增 common/pages/retrieval/session 四组双语资源；没有删除原长说明资源。静态资源审查通过新增短副标题的中文 ≤16 汉字、英文 ≤36 字符限制。动态主机、模型、ID、工具目标不伪造短文案，完整内容可由脚注或原编辑器访问；截断例外列于第 8 节。

## 4. 强调按钮与危险操作


- Termux 全页仅“运行诊断测试”为实心主按钮；安装来源、系统权限、SSH、密钥复制与目录选择为设置行，刷新为 TextButton。
- 提供商表单保留“保存”为唯一主按钮，连接测试为 TextButton；本地提供商分支保持唯一主操作。
- 模型列表保留新增为主按钮，同步为 TextButton。模型编辑保留测试/取消测试为唯一色调按钮。
- SSH 主机打开终端为设置行；编辑弹层保存为唯一主按钮，导入及生成密钥为 TextButton。
- 备份页面的上传、刷新、恢复和配置为设置行；WebDAV 弹层保存为唯一主按钮，保存并测试为 TextButton。
- 技能包新建/保存、MCP 新增/保存、搜索密钥保存各自所在视口保留一个主按钮；导入及错误重试为 TextButton。
- 重置授权、模型删除、WebDAV 安全信息重置、用量清空、SSH 主机删除为 error 色设置行，确认语义与回调保留。
- Local ModelCard 的原 ListItem 整行不触发加载，原尾部按钮受 `engineEnabled` 控制。设置行增加点击加载入口后，行回调明确在 `engineEnabled` 为 true 时才执行；尾部加载仍受原门禁约束，删除按钮保持可用。主控安排对应回归验证。
- `NexaraSettingsSection` 独立提供分组间 24dp 留白；ProviderForm、LocalModels、ModelEditor、搜索配置及模型列表的外层 `spacedBy` 已归零。输入表单内部、命令和诊断输出内部的局部间距保留。

- 全局清理向量、恢复提取默认提示词、助手恢复全局与删除助手均为错误色设置行；保留原确认对话框或原即时重置回调。
- 删除助手的红色填充/描边容器移除。正文没有新增实心或色调强调动作按钮；全局预设仍作为页面主维度的分段选择器，底部选择弹层的选中预设属于选择状态。
- 助手编辑系统提示词移除42dp彩色图标徽章；图标选择网格移到底部弹层；名称、描述、头像、颜色选择盘保持原能力。头像相机按钮扩展为48dp触控区。
- 全局检索的两个导航行均使用20dp图标列。其余有图标的分组内设置行都有图标；外观组图标选择行不放前导图标，和颜色编辑说明保持16dp文本起点。
- 两助手页配置来源为只读共享行，没有伪造点击动作。查询策略列表使用 Role.RadioButton 单选语义并在选中项显示Check。新图标选择弹层所有图标均通过中英文资源提供 contentDescription。
- ColorPickerPanel 的预设色块触控区由主控补足 48dp，未保留 40dp 触控例外。


会话设置的“立即压缩”是设置行，授权撤销为 error 色设置行；数值弹层只在自定义展开时提供唯一“完成”主按钮，预设勾选/开关/单选状态不计为主动作。底部枚举选中即写入并关闭，没有额外保存事务。411dp 默认中文脚注不超过三行，完整安全或风险说明例外见第 12 节。

## 5. 测试清单、契约改写与结果

### 5.1 NumericSettingSpec：8 项纯逻辑测试

文件：native-ui/app/src/test/java/com/promenar/nexara/ui/common/NumericSettingSpecTest.kt。

| 测试 | 有意义的约束 | 结果 |
| --- | --- | --- |
| parsesAndAlignsTokenBudget | 123456 对齐为 120000，125000 半入对齐为 130000 | 两渠道通过 |
| invalidOrOutOfRangeInputIsNeverSilentlySubmitted | 非数值、空值、越界先拒绝，不静默提交边界 | 两渠道通过 |
| clampHonoursBoundsAndStep | 范围夹取与步长对齐 | 两渠道通过 |
| unlimitedPresetDoesNotWidenCustomRange | 轮次 0 只允许不限预设，自定义只接受 1–100 | 两渠道通过 |
| decimalsHaveStablePrecisionAndLocaleIndependentParsing | 小数精度、稳定格式及地区无关解析 | 两渠道通过 |
| selectedPresetHandlesFloatStorageNoiseAndIncludesDefault | Float 往返噪声下识别预设；原默认值自动进入选项 | 两渠道通过 |
| customIntegerUsesIntegralGrid | 整数输入按整数网格对齐 | 两渠道通过 |
| continuousValuesRetainStoragePrecision | 连续参数显示精度不量化原存储值 | 两渠道通过 |

保留失败复现 numeric-red.log（初始 7 项失败）、numeric-custom-range-red.log（8 项中 1 项失败，复现不限值误入自定义）与 numeric-green.log（8 项通过）。最终双渠道测试结果来自当前工作树的 validation-unit-lint.log。

### 5.2 会话检索纯逻辑：3 项

文件：native-ui/app/src/test/java/com/promenar/nexara/ui/common/SessionRetrievalSettingsTest.kt。fieldUpdatesComposeWithoutLosingEarlierChanges 验证连续更新保留先前字段；eachToggleChangesOnlyItsOwnField 验证五个开关只修改自身字段；memoryGateTakesPrecedenceOverGlobalScope 验证 enableMemory 总门优先。三项先在 retrieval-red.log 失败，再在 retrieval-green.log 通过，最终两渠道均通过。

### 5.3 Compose 布局与交互：设备测试回退

按任务书 3.5 允许的回退写于 native-ui/app/src/androidTest/java/com/promenar/nexara/ui/common/SettingsLayoutTest.kt。12 个方法全部编译进 SideloadDeviceTestAndroidTest APK；以下均未在设备运行。

| 测试 | 断言 |
| --- | --- |
| singleAndTwoLineRowsHaveContractHeights | Item/Toggle/Value 单行 48dp、带副标题 56dp，容差 0.5dp，含真实 Switch 尾控件 |
| controlsShareTextBlockCentre | 各行尾部与标题块中心线偏差 ≤1dp，含步进器胶囊 |
| longSubtitleIsExactlyOneEllipsizedLine | 超长副标题只有一行并省略 |
| leadingSpacerAlignsToIconColumn | 图标与 leadingSpacer 的文字起点均为 52dp |
| stepperCapsuleIs36DpAndBoundaryButtonsAreDisabled | 胶囊 36dp，下边界减按钮禁用 |
| upperBoundaryDisablesIncreaseAndAccessibleActionsUpdateValue | 上边界加按钮禁用，无障碍点击触发正确更新 |
| selectingPresetSubmitsItsValueAndDismisses | 选择 25，写入 25 并关闭 |
| invalidCustomValueShowsErrorAndDoesNotSubmit | 自定义 101 显示范围错误且不提交 |
| validCustomValueSubmitsAlignedValue | 自定义合法 71 可提交 |
| repeatedRagChangesPreserveOtherFieldsBeforeParentRecomposition | 父重组之前连续切换不覆盖其它 RAG 字段 |
| executionModePickerUsesNamedOptionsAndKeepsStoredString | 模式列表仅命名枚举，无数值自定义，选择 manual 原字符串写回 |
| languagePickerSubmitsOriginalCodeAndCloses | 语言弹层选择英文，仍写入 en 并关闭弹层 |

Robolectric 回退原因：初始运行在 MavenDependencyResolver 的 SDK 缓存锁写入处失败；命令级使用已有 SDK 离线缓存后，Compose 资源初始化仍在 ShadowPackageParser/PackageParserException 失败。当前 AGP 9.2.1 的单测资源 APK 使用相对路径，项目现有 Test workingDir 为 native-ui 根；试用旧 android.testConfig.useRelativePath 选项时 AGP 明确报告该选项已在 8.0 移除。日志 gradle-layout-red.log、gradle-layout-red-offline.log、gradle-layout-red-path.log 保留。没有保留该废弃选项，也没有为这组布局测试全局改变其它 JVM 测试的资源行为；同等断言移到设备测试后只构建、不操作设备。

依赖只增加 testImplementation 的 Compose BOM、ui-test-junit4 与 ui-test-manifest；两个工件均由已有 BOM 管理，无显式版本，没有其它新增依赖。

另新增 LocalModelsSettingGateTest 的 modelNameCannotLoadWhileEngineIsDisabled 与 emptyModelEntryCannotOpenAnotherImporterWhileBusy，保护引擎关闭与 busy 导入门禁。两项仅编译，未运行。

### 5.4 既有设备测试适配

| 文件 / 方法 | 改写内容 | 保留的业务断言 |
| --- | --- | --- |
| ThemeSettingsInteractionTest | 先打开 theme_mode_picker，再选择原主题标签 | 原选择结果与回调 |
| ProviderModelsAccessibilityTest | 先打开 model_editor_type_picker，再选择原模型类型标签 | 原类型/能力结果 |
| ProviderFormInteractionTest.presetAndCustomProtocolSelectorsUseExplicitSelectionCallbacks | 打开 provider_protocol_picker，真实展开回调绑定状态后选 Responses | 原协议类型、回调和关闭行为 |
| RagSettingsAccessibilityTest：全局 chunk | SetProgress 改为值行→自定义 500→完成 | 仍断言 docChunkSize=500 |
| RagSettingsAccessibilityTest：高级 memory | SetProgress 改为值行→预设 20 | 仍断言 memoryLimit=20 及单字段更新 |
| RagSettingsAccessibilityTest.ragAdvancedDispatchesValuePickerAndGraphNavigationFromProductionContent | 超时值行选预设 30，原方法按新入口重命名 | 仍断言 timeout=30、图谱导航与确认入口 |
| RagSettingsAccessibilityTest：助手 memory | SetProgress 改为点击增加 3 次，5→8；本地 Resources 显式声明 | 仍断言 memoryLimit=8 及原确认/字段行为 |

这些改写全部通过设备测试 APK 构建；未用编译通过代替点击运行结果。

### 5.5 源码字符串契约：逐文件改写与删除说明

未删除任何整个契约测试方法。移除的都是仅验证已废弃表现的断言，同时保留或补入真实行为与结构约束。以下 13 份契约在两个 distribution 单测中均通过。

| 测试文件 | 删除的旧写法断言 → 当前契约 | 保留的重要保护 |
| --- | --- | --- |
| SettingsVisualPrimitivesContractTest | 页面局部 typography.copy 字面量 → 全局字体、共享 Row、bodySmall、单行副标题、footer、labelMedium、24dp 分组 | 容器材质、换行、密度令牌 |
| BettboxVisualContractTest | ListItem、24dp 图标及 Small 间距 → 共享 Row、无 ListItem、20dp 图标+16dp 间距 | 统一材质与分组 |
| ManagementMaterialContractTest | ListItem 与固定 Role 字面位置 → 共享 Row、默认 Role 参数及传递 | surface 与主题角色 |
| Md3ConvergencePrimitivesContractTest | 旧无 footer 分组结构 → footer、labelMedium、FooterTop | BettboxListGroup 材质 |
| BettboxGroupedSurfacesContractTest | 固定 BettboxListGroup 数量 → NexaraSettingsSection 委托及真实分组名 | 实际页面分组覆盖 |
| UserSettingsHomeScreenContractTest | 无图标/无箭头及 88dp 资料区+56dp 头像 → 图标/箭头、56dp 资料行+48dp 头像；Provider 公共行/短协议/脚注 | 导航入口、菜单、账户操作 |
| RagSettingsMaterialContractTest | ListItem/Slider/FilterChip/Divider、页面重复 Theme → Section/Numeric/Toggle/策略值行，正文无 Slider | 清理、恢复与确认回调 |
| BackupContentUiContractTest | 内联 ViewModel 回调源码 → state/actions binder 及无状态 Content | 核心数据、密钥可选、密码及云备份真实流程 |
| ProviderModelReleaseBlockersTest | 次级 OutlinedButton、旧未使用选择器 ListItem → TextButton、真实协议值行/弹层/selected | 原协议、模型与错误门禁 |
| ProviderModelsScreenContractTest | FilledTonal 同步、外层 gap8、原位置分组断言 → TextButton、gap0、ModelRow 实际分组 | 搜索、稳定 key、批量行为 |
| SecureSecretUiContractTest | OutlinedButton、内联 list/select → TextButton、binder/actions | 密码校验、安全清空与异步结果 |
| SettingsAiAppearanceVisualContractTest | ListItemDefaults、直接 Header → 公共 Item/Section 委托 | 无装饰性边框/阴影限制 |
| SettingsToolsDataVisualContractTest | 页面直接 Header 调用 → Section 委托的标题与脚注 | 原工具和数据入口 |

## 6. 截图验收与逐页 G1–G9

新增 82 张，更新 143 张，删除 0 张；另 17 张未变化，总基线 242 张。新增来源为 SettingsPages 42、Retrieval 18、Components 13、Session 9。双渠道均独立 update 与 validate，使用现有共享 reference 目录。

主控目视检查全部 225 张新增/变化图：visual-review/sheet-01.png 至 sheet-64.png。文案收短后，变化的 43 张由 recheck-01.png 至 recheck-15.png 再检；轮次文案收短影响的 9 张由 final-recheck-01.png 至 final-recheck-03.png 逐张确认；语言入口影响的 8 张由 language-recheck-01.png 至 language-recheck-02.png 逐张确认。visual-review/manifest.json 保存最终每张文件名、尺寸、SHA-256 与首次联系表定位，final-recheck-files.json 与 language-recheck-files.json 保存对应变化集。联系表缩放仅用于查看，未修改基线像素。

### 6.1 G1–G9 判定口径

V＝源布局及当前截图目视符合；D＝设备测量待运行；E＝规格允许的输入框/命令块/颜色盘/弹层滑块或任务书保留的思考卡片；—＝不适用；X＝已知偏离，未当作通过。G2 的 ±1dp、G5 的 ±0.5dp 精度只能由设备布局断言确认，因此表中 V/D 不等同设备验收。

G1 控件同行；G2 尾控件居中；G3 默认中文副标题单行未截断；G4 强调按钮≤1；G5 48/56dp 行高；G6 组内起点一致；G7 长说明在脚注/弹层；G8 无行首标点与末行孤字；G9 数值 tnum 及值右缘对齐。长页在各自截图范围内判断，不把静态图当作滚动手势证据。

### 6.2 批次一页面与组件逐页结论

默认页均有深色中文 411dp/360dp；首页和工具另有浅色英文与 130% 字号。下列 ID 对应 manifest.json，文件名在第 7 节逐项列出。

| 页面 / 状态 / 图 ID | G1 | G2 | G3 | G4 | G5 | G6 | G7 | G8 | G9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 设置首页 默认、浅色英文、130%，168–171 | V | V/D | V | V | V/D | V | V | V | — |
| 工具设置顶部 默认/浅色英文/130%，186–189 | V | V/D | V | V | V/D | V | V | V | V |
| 工具执行预算，184–185 | V | V/D | — | V | V/D | V | V | V | V |
| 最大轮次弹层 打开，121–122 | V | V/D | — | V | V/D | V | V | V | V |
| 最大轮次弹层 自定义与越界错误，119–120、123–124 | E | V/D | — | V | V/D | V | V | V | V |
| Token 上限弹层，128–129 | V | V/D | — | V | V/D | V | V | V | V |
| 搜索配置，166–167 | V/E | V/D | V | V | V/D | V | V | V | V |
| Termux 已就绪，178–179 | V/E | V/D | V | V（诊断唯一） | V/D | V | V | V | V |
| Termux 诊断失败，176–177 | V/E | V/D | V | V（诊断唯一） | V/D | V | V | V | V |
| SSH 空态，174–175 | V | V/D | V | V | V/D | V | V | V | — |
| SSH 已有主机，172–173 | V | V/D | V | V | V/D | V | V | V | V |
| 备份默认，148–149 | V/E | V/D | V | V | V/D | V | V | V | — |
| 用量默认，182–183 | V | V/D | — | V | V/D | V | V | V | V |
| 提供商列表空态，160–161 | V | V/D | — | V | V/D | V | V | V | — |
| 提供商列表已配置，156–157 | V | V/D | V | V | V/D | V | V | V | — |
| 提供商表单，158–159 | V/E | V/D | V | V | V/D | V | V | V | — |
| 模型列表空态，164–165 | V | V/D | — | V | V/D | V | V | V | — |
| 模型列表已配置，162–163 | V | V/D | V（样例 ID） | V | V/D | V | V | V | — |
| 默认模型，150–151 | V | V/D | V | V | V/D | V | V | V | — |
| 本地模型默认，152–153 | V | V/D | V | V | V/D | V | V | V | — |
| 主题文字缩放开启，180–181 | V/E | V/D | V | V | V/D | V | V | V | V |
| 模型编辑，154–155 | V/E | V/D | V | V | V/D | V | V | V | V |
| 全局检索，142–143 | V/E | V/D | V | V | V/D | V | V | V | V |
| 检索高级，144–145 | V | V/D | V | V | V/D | V | V | V | V |
| 高级检索默认及完整长页，130–133 | V | V/D | V | V | V/D | V | V | V | V |
| 助手检索配置，138–139 | V | V/D | V | V | V/D | V | V | V | V |
| 助手高级检索默认及完整长页，134–137 | V | V/D | V | V | V/D | V | V | V | V |
| 全局连续阈值弹层，146–147 | E | V/D | V | V | V/D | V | V | V | V |
| 助手 5% 阈值弹层，140–141 | E | V/D | V | V | V/D | V | V | V | V |
| 会话模型标签，111–112 | V | V/D | V（样例 ID） | V | V/D | V | V | V | — |
| 会话参数标签，113–114 | V/E（思考卡） | V/D | V | V | V/D | V | V | V | V |
| 会话工具标签，115–116 | V | V/D | V | V | V/D | V | V | V | — |
| 会话上下文标签（含英文130%），108–110 | V | V/D | V | V | V/D | V | V | V | V |
| 会话 Top P 连续弹层，117–118 | E | V/D | V | V | V/D | V | V | V | V |
| 组件单/双/禁用行、开关、值行、步进器中间/边界，125–127 | V | V/D | V | V | V/D | V | V | V | V |

### 6.3 既有预览回归与超大字号偏离

既有 SettingsVisualScreenshotTest 的 130% 深浅色工具顶部、步进器边界、Termux ready/未安装/失败和完整下半页均逐张检查，保留 48/56dp 目标，未观察到新增覆盖或字形裁切。最后中文“重复上限”在 360dp/130% 保持单行。

| 既有压力页面 | G1 | G2 | G3 | G4 | G5 | G6 | G7 | G8 | G9 | 偏离 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 设置首页 200% | V | V/D | X | V | X | V | V | V | — | 较长副标题省略，文字块自然撑高 |
| 默认模型 200% | V | V/D | V/动态例外 | V | X | V | V | V | — | 大字多行标题/副标题撑高 |
| 主题 中文/英文 200% | V/E | V/D | X | V | X | V | V | V | V | 长副标题省略或自然增高 |
| 助手检索英文大字 | V | V/D | X | V | X | V | V | V | V | 长副标题省略，保持可读字号 |
| 提供商/模型列表大字 | V | V/D | 动态例外 | V | X | V | V | V | — | 长名称/ID省略，标题块可超过56dp |

这些不是用户已批准的规格例外；当前实现使用最小高度以保留超大字号可读性，没有强行裁切字形。任务书要求列出偏离，故明确保留为未满足项。其它已有知识库、DocEditor、图谱、欢迎引导、导航与会话相关变化图仅作全局字体回归：未观察到新增文字互盖/字形裁切；原文件名和 ID 的省略保持原规则，不据此宣称实施批次二或全路径设备验收。

## 7. 截图基线文件清单

以下文件名均相对于 native-ui/app/src/screenshotTestDebug/reference/。这份清单由任务前 baseline-before.json、当前文件与 git diff 实际集合生成，未把 Preview 声明数当作生成数量。

### 新增（82）

```text
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionContextBatch1Preview_会话上下文 深中130_924a2290_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionContextBatch1Preview_会话上下文 深中360_939f9512_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionContextBatch1Preview_会话上下文 深中411_1e4eee2e_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionModelBatch1Preview_会话模型 深中360_803a4a53_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionModelBatch1Preview_会话模型 深中411_2a2bf6c0_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionParamsBatch1Preview_会话参数 深中360_e4fb7c4c_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionParamsBatch1Preview_会话参数 深中411_eb4c5ff6_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionToolsBatch1Preview_会话工具 深中360_f710e318_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionToolsBatch1Preview_会话工具 深中411_7ee16e8b_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/continuousPickerBatch1Preview_连续弹层 深中360_d585ee0b_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/continuousPickerBatch1Preview_连续弹层 深中411_e0d06cbe_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/customPickerBatch1Preview_离散自定义 深中360_688fba69_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/customPickerBatch1Preview_离散自定义 深中411_e3f13b91_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/discretePickerBatch1Preview_离散弹层 深中360_aac4360f_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/discretePickerBatch1Preview_离散弹层 深中411_a9fb0a96_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/invalidPickerBatch1Preview_离散错误 深中360_11b44339_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/invalidPickerBatch1Preview_离散错误 深中411_bcafabc9_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/settingsComponentsBatch1Preview_设置组件 深中130_483fa756_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/settingsComponentsBatch1Preview_设置组件 深中360_0e44cb29_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/settingsComponentsBatch1Preview_设置组件 深中411_43db0646_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/tokenPickerBatch1Preview_Token上限弹层 深中360_1a26f4d4_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/tokenPickerBatch1Preview_Token上限弹层 深中411_1560d524_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/advancedRetrievalBatch1DefaultPreview_高级检索 默认 深色 中文 360dp_21b471de_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/advancedRetrievalBatch1DefaultPreview_高级检索 默认 深色 中文 411dp_ba0a08bc_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/advancedRetrievalBatch1FullPagePreview_高级检索 全页 深色 中文 360dp_b42698de_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/advancedRetrievalBatch1FullPagePreview_高级检索 全页 深色 中文 411dp_1ad80634_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentAdvancedRetrievalBatch1DefaultPreview_助手高级检索 默认 深色 中文 360dp_9e2ec1a6_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentAdvancedRetrievalBatch1DefaultPreview_助手高级检索 默认 深色 中文 411dp_92bbb691_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentAdvancedRetrievalBatch1FullPagePreview_助手高级检索 全页 深色 中文 360dp_394038de_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentAdvancedRetrievalBatch1FullPagePreview_助手高级检索 全页 深色 中文 411dp_2c44a45f_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentRagBatch1DefaultPreview_助手检索 默认 深色 中文 360dp_5b32599d_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentRagBatch1DefaultPreview_助手检索 默认 深色 中文 411dp_3d52f414_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentRetrievalBatch1ContinuousValuePreview_助手检索 连续参数 深色 中文 360dp_34f12f71_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentRetrievalBatch1ContinuousValuePreview_助手检索 连续参数 深色 中文 411dp_ab14e08d_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/globalRagBatch1DefaultPreview_全局检索 默认 深色 中文 360dp_8e55b938_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/globalRagBatch1DefaultPreview_全局检索 默认 深色 中文 411dp_2f9e9854_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/ragAdvancedBatch1DefaultPreview_检索高级 默认 深色 中文 360dp_e7dad58a_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/ragAdvancedBatch1DefaultPreview_检索高级 默认 深色 中文 411dp_57c63ba2_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/retrievalBatch1ContinuousValuePreview_全局检索 连续参数 深色 中文 360dp_f0afb286_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/retrievalBatch1ContinuousValuePreview_全局检索 连续参数 深色 中文 411dp_3c92d76d_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/backupPageBatch1_备份默认 深色中文 360_96fe25e9_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/backupPageBatch1_备份默认 深色中文 411_256bcfdd_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/defaultModelsBatch1_默认模型 深色中文 360_a5cefeb4_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/defaultModelsBatch1_默认模型 深色中文 411_dc7195ab_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/localModelsBatch1_本地模型 深色中文 360_3a2422fa_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/localModelsBatch1_本地模型 深色中文 411_8dddc05d_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/modelEditorBatch1_模型编辑 深色中文 360_db386e30_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/modelEditorBatch1_模型编辑 深色中文 411_45fa0835_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerConfiguredBatch1_提供商已配置 深色中文 360_f0db6591_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerConfiguredBatch1_提供商已配置 深色中文 411_3d091b01_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerFormBatch1_提供商表单 深色中文 360_1da4c5b8_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerFormBatch1_提供商表单 深色中文 411_6dc63768_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerListBatch1_提供商列表默认 深色中文 360_dd244b42_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerListBatch1_提供商列表默认 深色中文 411_00977853_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerModelRowBatch1_模型列表已配置 深色中文 360_914297d2_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerModelRowBatch1_模型列表已配置 深色中文 411_8a31dd97_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerModelsBatch1_模型列表默认 深色中文 360_fe642760_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerModelsBatch1_模型列表默认 深色中文 411_58b3c708_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/searchConfigBatch1_搜索配置 深色中文 360_faa0ae0a_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/searchConfigBatch1_搜索配置 深色中文 411_db88d89b_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/settingsHomeBatch1LargeText_设置首页 深色中文 字号130_07ff7895_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/settingsHomeBatch1LightEnglish_设置首页 浅色英文_08fc9be1_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/settingsHomeBatch1_设置首页 深色中文 360_215f4ca3_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/settingsHomeBatch1_设置首页 深色中文 411_78157f6c_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/sshHostBatch1_SSH 主机 深色中文 360_f9d0639f_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/sshHostBatch1_SSH 主机 深色中文 411_f2159585_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/sshPageBatch1_SSH 默认 深色中文 360_8b0dc5a6_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/sshPageBatch1_SSH 默认 深色中文 411_f8dfe20b_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/termuxDiagnosticFailureBatch1_Termux 诊断失败 深色中文 360_bf6882a0_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/termuxDiagnosticFailureBatch1_Termux 诊断失败 深色中文 411_6bfd648c_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/termuxReadyBatch1_Termux 已就绪 深色中文 360_8bda7944_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/termuxReadyBatch1_Termux 已就绪 深色中文 411_dc8d6cd0_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/themeTextScaleBatch1_主题文字缩放开启 深色中文 360_890f60fd_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/themeTextScaleBatch1_主题文字缩放开启 深色中文 411_388e2016_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/tokenUsageBatch1_用量默认 深色中文 360_36f3da0c_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/tokenUsageBatch1_用量默认 深色中文 411_41067c31_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsBudgetBatch1_工具执行预算 深色中文 360_da67b490_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsBudgetBatch1_工具执行预算 深色中文 411_4af71068_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsPageBatch1LargeText_工具设置顶部 深色中文 字号130_6d11a979_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsPageBatch1LightEnglish_工具设置顶部 浅色英文_7f8611db_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsPageBatch1_工具设置顶部 深色中文 360_e7a2d992_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsPageBatch1_工具设置顶部 深色中文 411_723dce31_0.png
```

### 更新（143）

```text
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/advancedRetrievalChineseLightReleasePreview_Advanced retrieval Chinese light_24f961e4_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/agentHubEmptyLargeFontReleasePreview_Agent hub empty Chinese large font_87cc7c82_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/agentHubPopulatedLargeFontReleasePreview_Agent hub populated Chinese large font_cda0168e_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/agentHubReleasePreview_Agent hub English_83d3189d_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/agentRetrievalEnglishLightLargeFontReleasePreview_Agent retrieval English light large font_ca6eb705_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/agentSessionsEmptyReleasePreview_Agent sessions empty Chinese_2200667e_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/agentSessionsSearchActiveReleasePreview_Agent sessions search active_a4588dae_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/agentSkillPacksReleasePreview_Agent skill packs Chinese_8fae2a85_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/attachmentActionsAnchoredReleasePreview_Attachment actions anchored_06db5765_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/attachmentActionsChineseLargeFontReleasePreview_Attachment actions Chinese font scale 2_2ce0607c_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/attachmentActionsCompactViewportReleasePreview_Attachment actions compact viewport_1c28da71_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/attachmentActionsLargeFontReleasePreview_Attachment actions font scale 2_dea1d01b_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/attachmentActionsRtlReleasePreview_Attachment actions RTL_32298043_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/backupSettingsReleasePreview_Backup settings English_f5f977a0_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatApprovalTabletReleasePreview_Chat approval tablet English_893d1f95_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatApprovalWritePreviewReleasePreview_Chat approval write preview Chinese_dbbe7da6_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatErrorLargeFontReleasePreview_Chat error font scale 2_1dd83379_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatFullContextDocumentsChineseReleasePreview_Chat full context documents Chinese_85b07ee7_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatFullContextDocumentsEnglishLargeFontReleasePreview_Chat full context documents English large font_35ac1c07_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatLargeFontReleasePreview_Chat large font scale 2_510f2739_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatLongModelMetadataReleasePreview_Chat long model metadata_5a74c56c_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatReadyReleasePreview_Chat ready with metadata_a90c576d_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatReasoningModelReleasePreview_Chat reasoning metadata_13d8936e_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatTaskChecklistExpandedReleasePreview_Chat task checklist expanded Chinese_72104472_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatTaskGeneratingReleasePreview_Chat task generating Chinese_88fa6d34_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatTaskPendingLargeFontReleasePreview_Chat task pending Chinese font scale 2_99b9eaf5_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/chatTaskPendingReleasePreview_Chat task pending Chinese_3db1c0ae_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/defaultModelsChineseLargeFontReleasePreview_Default models Chinese large font_8ee62bc8_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/defaultModelsChinesePhoneReleasePreview_Default models Chinese phone_c1f87db3_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/defaultModelsChineseTabletReleasePreview_Default models Chinese tablet_354be6c0_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/defaultModelsEnglishLandscapeReleasePreview_Default models English landscape_804166e4_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorExact800x360EditDirtyEnglishReleasePreview_DocEditor exact 800x360 edit dirty English_225b0a06_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorIndexPendingChinesePhoneReleasePreview_DocEditor index pending Chinese phone_126a6548_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorLargeFileChineseTabletReleasePreview_DocEditor large file Chinese tablet_caea6a50_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorLoadErrorChineseLargeFontReleasePreview_DocEditor load error Chinese large font_b4f65c95_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorLoadingEnglishPhoneReleasePreview_DocEditor loading English phone_5913c99b_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorReadyEditDirtyEnglishLandscapeReleasePreview_DocEditor ready edit dirty English landscape_363ab0b7_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorReadyEditDirtyEnglishPhoneReleasePreview_DocEditor ready edit dirty English phone_144bb6c8_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorRichMarkdownPreviewEnglishLightReleasePreview_DocEditor rich markdown preview English light_f71a33a1_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorRichMarkdownPreviewEnglishReleasePreview_DocEditor rich markdown preview English_56bfef50_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorSaveConflictChineseLargeFontReleasePreview_DocEditor save conflict Chinese large font_cce2c6e0_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorSplitEnglishTabletReleasePreview_DocEditor split English tablet_e8860e5e_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/docEditorUnsavedDiscardEnglishPhoneReleasePreview_DocEditor unsaved discard English phone_6138c8ff_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/emptyChatEnglishLandscapeReleasePreview_Empty chat English landscape_a72a9f74_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/emptyChatReleasePreview_Empty chat English_085f32cb_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/mainNavigationTabletDarkReleasePreview_Main navigation tablet dark_44817a23_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/mainNavigationTabletLightReleasePreview_Main navigation tablet light_976d1e91_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/modelPickerLargeCatalogDarkReleasePreview_Model picker 120 models dark_e51a8c0f_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/modelPickerLongNamesLightLargeFontReleasePreview_Model picker long names light large font_2dfe5cab_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/onboardingLanguageLargeFontReleasePreview_Onboarding language font scale 2_facd7ba7_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/onboardingLanguageReleasePreview_Onboarding language English_9d29d973_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/onboardingLanguageTabletReleasePreview_Onboarding language Chinese tablet_6e764911_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/onboardingModelListReleasePreview_Onboarding model list Chinese_dce7ea1f_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerFormAddReleasePreview_Provider form add phone_ffed90e8_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerFormEditActionsLargeFontReleasePreview_Provider form edit actions 2x_71df0535_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerFormLocalFailureReleasePreview_Provider form local onboarding failure_d4ce83e1_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerListChineseNarrowReleasePreview_Provider list Chinese narrow_342492e5_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerMaskedKeyReleasePreview_Provider masked API key_4f4dc12d_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerModelEditorReleasePreviewChineseLargeFont_Provider model editor Chinese large font_fded84d2_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerModelsChineseLandscapeReleasePreview_Provider models Chinese landscape_e17f67a3_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerModelsReleasePreviewChineseLargeFont_Provider models chinese large font_1ef09f04_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerModelsReleasePreviewEnglish_Provider models populated English_f241a120_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragAdvancedEnglishDarkReleasePreview_RAG advanced English dark_38e36222_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragDetailsKgLongLandscapeReleasePreview_RAG details KG long landscape_a7916274_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragDetailsRetrievedPhoneReleasePreview_RAG details retrieved phone_33c2f58a_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragDetailsWebLargeFontReleasePreview_RAG details web large font_eb9fa9d0_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragFilesPanelDeepTreeLargeFontReleasePreview_RAG FilesPanel deep tree Chinese large font_39b95084_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragFolderContentLandscapeReleasePreview_RAG folder content landscape_b873f31c_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragFolderContentPhoneReleasePreview_RAG folder content phone_fc7c7b6c_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragFolderSelectedLargeFontReleasePreview_RAG folder selected large font_a57e18dd_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragGlobalChineseDarkLargeFontReleasePreview_RAG global Chinese dark large font_6e60c62b_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragHomeDocumentsLandscapeReleasePreview_RAG home documents landscape_be41e2c0_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragHomeDocumentsReleasePreviewEnglish_RAG home documents English_658d0edb_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragHomeMemoryChineseLargeFontReleasePreview_RAG home memory Chinese large font_bdb92b52_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragHomeMemoryContentLargeFontReleasePreview_RAG home memory content Chinese large font_b9260e6c_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragHomeMemoryContentPhoneReleasePreview_RAG home memory content phone_afced64d_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragHomeMemoryDeleteConfirmationReleasePreview_RAG home memory delete confirmation phone_a54e322a_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragHomeMemoryExpandedPhoneReleasePreview_RAG home memory expanded phone_36b4e86b_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragHomeSelectedDocumentsPhoneReleasePreview_RAG home selected documents phone_1df78d96_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/resourceExplorerFilesImportEnglishReleasePreview_Resource Explorer files import English_b89c30f3_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/resourceExplorerRecycleDeleteConfirmChineseLargeFontReleasePreview_Resource Explorer recycle delete confirm Chinese large font_5f06a69d_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/resourceExplorerRecycleFailureLandscapeReleasePreview_Resource Explorer recycle failure landscape_edaa7b0e_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/resourceExplorerRecycleRunningChineseLargeFontReleasePreview_Resource Explorer recycle running Chinese large font_5f1f37ed_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/streamingChatChineseLandscapeReleasePreview_Streaming chat Chinese landscape_50d024bd_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/streamingChatReleasePreview_Streaming chat Chinese_f65d4631_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/themeSettingsChineseDarkLargeTextReleasePreview_Theme settings Chinese dark 2x_a9a5161a_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/themeSettingsChineseLightReleasePreview_Theme settings Chinese light_046ba5d0_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/themeSettingsEnglishDarkReleasePreview_Theme settings English dark_48c5130d_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/themeSettingsEnglishLightLargeTextReleasePreview_Theme settings English light 2x_0884a93f_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsAppChineseCompactLargeFontReleasePreview_User settings app Chinese compact large font_015f71c8_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsAppChinesePhoneReleasePreview_User settings app Chinese phone_921f91d8_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsAppChineseTabletReleasePreview_User settings app Chinese tablet_60d69b4f_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsIntegratedNavigationChinesePhoneReleasePreview_User settings integrated navigation Chinese phone_8b19e30b_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsProviderEmptyChineseLargeFontReleasePreview_User settings provider empty Chinese large font_2eb6fd68_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsProviderLargeFontReleasePreview_User settings provider English large font_e804a4ab_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/contextUsageDarkPreview_上下文用量 深色 大字号130_91da7506_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/contextUsageDarkPreview_上下文用量 深色_dcf0bd35_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/contextUsageLightPreview_上下文用量 浅色 大字号130_b3989ee5_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/contextUsageLightPreview_上下文用量 浅色_3431ccd2_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/quickModelDarkPreview_快速模型 深色 大字号130_a528c8d4_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/quickModelDarkPreview_快速模型 深色_6541a0b4_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/quickModelLightPreview_快速模型 浅色 大字号130_570fafb5_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/quickModelLightPreview_快速模型 浅色_11d65ae0_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/sessionContextManagementDarkPreview_上下文管理 深色 大字号130_1feef59d_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/sessionContextManagementDarkPreview_上下文管理 深色_65e5ab4e_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/sessionContextManagementLightPreview_上下文管理 浅色 大字号130_94d724f6_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/sessionContextManagementLightPreview_上下文管理 浅色_606cfa83_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/commandBlockDarkPreview_命令块 深色 大字号130_43eb13ff_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/commandBlockDarkPreview_命令块 深色_bd5814e3_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/commandBlockLightPreview_命令块 浅色 大字号130_37df0321_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/commandBlockLightPreview_命令块 浅色_99e150dc_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshEmptyDarkPreview_SSH 空态 深色 大字号130_dea50a1b_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshEmptyDarkPreview_SSH 空态 深色_a2952e4f_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshEmptyLightPreview_SSH 空态 浅色 大字号130_5ad6fe68_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshEmptyLightPreview_SSH 空态 浅色_7b77b0a8_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshHostsDarkPreview_SSH 主机列表 深色 大字号130_02a7dc2f_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshHostsDarkPreview_SSH 主机列表 深色_c2891582_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshHostsLightPreview_SSH 主机列表 浅色 大字号130_f9afc421_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshHostsLightPreview_SSH 主机列表 浅色_46dd8bea_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/stepperSettingDarkPreview_步进设置行 深色 大字号130_91755e5e_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/stepperSettingDarkPreview_步进设置行 深色_dfb5df34_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/stepperSettingLightPreview_步进设置行 浅色 大字号130_ed2b26b9_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/stepperSettingLightPreview_步进设置行 浅色_54cd73da_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledDarkPreview_Termux 未安装 深色 大字号130_6889bcc1_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledDarkPreview_Termux 未安装 深色_31a71686_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLightPreview_Termux 未安装 浅色 大字号130_b86d69ef_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLightPreview_Termux 未安装 浅色_f80ec007_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLowerDarkPreview_Termux 未安装下半页 深色 大字号130_8fa0b9a8_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLowerDarkPreview_Termux 未安装下半页 深色_47daebe2_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLowerLightPreview_Termux 未安装下半页 浅色 大字号130_55886a25_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLowerLightPreview_Termux 未安装下半页 浅色_95ecd34f_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyDarkPreview_Termux 已就绪 深色 大字号130_da2d0314_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyDarkPreview_Termux 已就绪 深色_03d873f7_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLightPreview_Termux 已就绪 浅色 大字号130_ef253423_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLightPreview_Termux 已就绪 浅色_88a18b69_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLowerDarkPreview_Termux 已就绪下半页 深色 大字号130_8fb8ecac_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLowerDarkPreview_Termux 已就绪下半页 深色_d2b0fde4_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLowerLightPreview_Termux 已就绪下半页 浅色 大字号130_a31ab7fd_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLowerLightPreview_Termux 已就绪下半页 浅色_64b239d4_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/toolsHeaderDarkPreview_工具设置顶部 深色 大字号130_e622b07b_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/toolsHeaderDarkPreview_工具设置顶部 深色_ea9aba16_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/toolsHeaderLightPreview_工具设置顶部 浅色 大字号130_da68fc1a_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/toolsHeaderLightPreview_工具设置顶部 浅色_e5018eab_0.png
```

### 删除（0）

无。

## 8. 无障碍例外清单与验证边界

| 项目 | 当前实现 / 证据 | 例外或未验证项 |
| --- | --- | --- |
| 200% 字号 | 不缩小用户文字，窄屏和放大字号允许标题完整换行、共享行自然增高 | 设置首页、主题、默认模型、助手检索和提供商/模型的部分压力状态超过 56dp 或副标题省略；G3/G5 偏离，没有获批放宽 |
| 48dp 触控 | 行本身至少 48dp；步进器 36dp 可见胶囊内，按钮 48dp 触控区；颜色盘及头像相机补足 48dp | 实际命中区域、邻近触点竞争及 TalkBack 焦点顺序未运行设备验证 |
| 精确对齐 | 四类行共享 Row；设备测试写入 ±1dp 中心、±0.5dp 高度断言 | 断言仅编译，静态截图目视不证明测量精度 |
| 值行与步进器 | Role.Button、stateDescription、增加/减少标签、边界 disabled；值文本用 tnum | TalkBack 实际朗读、开关合并语义、长按 400/80ms 节奏未设备实测 |
| 单选 | 主题、语言、模型类型、搜索、协议、策略、执行模式使用 Role.RadioButton 与 selected | 原标签迁移已审计，实际读屏切换与焦点恢复未设备实测 |
| 长 ID/动态值 | 值区限制 40%，单行 ID 保留原标识符换行/截断规则；完整模型、主机、URL、目标可在脚注或编辑器访问 | 任意长用户输入仍可能省略；不将动态 ID 当作默认短副标题的 G3 通过依据 |
| 脚注 | 使用阅读换行，值项解释进入弹层，SSH 可 SelectionContainer 选择复制 | 411dp 默认中文三行为硬性要求；完整安全/风险说明例外单列于第 12 节 |
| 对比度 | 当前 Nexara 固定深色 onSurfaceVariant 对背景/low/container 为 10.87/10.05/9.61；浅色为 8.86/8.40/7.96 | 根据当前 Color.kt 数值计算；动态壁纸、禁用态与设备实际色彩管理未逐组合验收 |
| 弹层 | 生产 wrapper 70% 最大高度与内部滚动，截图直接渲染相同 Content | 截图未渲染系统遮罩/拖拽/IME；隐藏内容可滚动的实际手势未操作设备 |

没有将步进器可见 32dp 按钮当作触控尺寸例外。200% 偏离单列为未满足项；任务书禁止设备操作，所以没有通过设备操作消除其余待验证项。

## 9. 验收命令与最后输出

所有 Gradle 命令在 native-ui/ 运行，Python 与 grep 在仓库根运行。命令串行执行，无 --stop、无杀守护进程。命令级 JAVA_TOOL_OPTIONS 使用本机已有 Robolectric SDK 离线缓存（-Drobolectric.dependency.dir=/Users/promenar/.gradle/caches/nexara-robolectric-sdk -Drobolectric.offline=true），没有修改仓库默认路径或持久环境。

日志与汇总均在本目录：validation-results.json、validation-junit-counts.json、validation-*.log。记录的是当前生产源、资源、测试与截图版本的最终一轮结果。

### unit-lint

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:lintSideloadDebug
```

退出码 0，耗时 241.91 秒；日志 validation-unit-lint.log。最后 8 行：

```text

> Task :app:lintSideloadDebug

[Incubating] Problems report is available at: file:///Users/promenar/Codex/Nexara-wt-redesign1/native-ui/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 4m 1s
73 actionable tasks: 24 executed, 49 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

### screenshot-update

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:updateSideloadDebugScreenshotTest :app:updatePlayDebugScreenshotTest
```

退出码 0，耗时 120.94 秒；日志 validation-screenshot-update.log。最后 8 行：

```text
> Task :app:updatePlayDebugScreenshotTest
Picked up JAVA_TOOL_OPTIONS: -Drobolectric.dependency.dir=/Users/promenar/.gradle/caches/nexara-robolectric-sdk -Drobolectric.offline=true

[Incubating] Problems report is available at: file:///Users/promenar/Codex/Nexara-wt-redesign1/native-ui/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 2m
63 actionable tasks: 8 executed, 55 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

### screenshot

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:validateSideloadDebugScreenshotTest :app:validatePlayDebugScreenshotTest
```

退出码 0，耗时 117.78 秒；日志 validation-screenshot.log。最后 8 行：

```text
> Task :app:packagePlayDebugScreenshotTestForScreenshotTest UP-TO-DATE

> Task :app:validatePlayDebugScreenshotTest
Picked up JAVA_TOOL_OPTIONS: -Drobolectric.dependency.dir=/Users/promenar/.gradle/caches/nexara-robolectric-sdk -Drobolectric.offline=true

BUILD SUCCESSFUL in 1m 57s
63 actionable tasks: 4 executed, 59 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

### assemble

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:assembleSideloadDebug :app:assemblePlayDebug
```

退出码 0，耗时 10.75 秒；日志 validation-assemble.log。最后 8 行：

```text
> Task :app:mergeProjectDexPlayDebug
> Task :app:packagePlayDebug
> Task :app:createPlayDebugApkListingFileRedirect UP-TO-DATE
> Task :app:assemblePlayDebug

BUILD SUCCESSFUL in 10s
87 actionable tasks: 10 executed, 77 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

### device-assemble

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" -Pnexara.deviceE2e=true -Pnexara.deviceE2eAbi=arm64-v8a :app:assembleSideloadDeviceTest :app:assembleSideloadDeviceTestAndroidTest
```

退出码 0，耗时 20.02 秒；日志 validation-device-assemble.log。最后 8 行：

```text
> Task :app:createSideloadDeviceTestAndroidTestApkListingFileRedirect UP-TO-DATE
> Task :app:assembleSideloadDeviceTestAndroidTest

[Incubating] Problems report is available at: file:///Users/promenar/Codex/Nexara-wt-redesign1/native-ui/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 19s
80 actionable tasks: 14 executed, 66 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

### text-wrapping

```bash
python3 scripts/ci/check-text-wrapping.py
```

退出码 0，耗时 0.21 秒；日志 validation-text-wrapping.log。最后 8 行：

```text
文本换行门禁通过
```

### distribution

```bash
python3 scripts/ci/check-distribution-manifests.py
```

退出码 0，耗时 0.03 秒；日志 validation-distribution.log。最后 8 行：

```text
发行清单检查通过（debug）：play 无所有文件访问权限，sideload 包含；包身份一致
```

### 静态滑块核查

```bash
grep -rn "NexaraSlider(\|NexaraSliderInt(" native-ui/app/src/main/java
```

退出码 0，全部命中仅限选择弹层与原定义：

```text
native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraValuePickerSheet.kt:109:            NexaraSlider(value = draft.toFloat(), onValueChange = { draft = spec.clamp(it.toDouble()) },
native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraSlider.kt:33:fun NexaraSlider(
native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraSlider.kt:109:fun NexaraSliderInt(
native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraSlider.kt:118:    NexaraSlider(
```

### 测试计数与补充静态复核

| 渠道 | JUnit XML | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- | --- |
| sideloadDebug | 525 | 3791 | 0 | 0 | 25 |
| playDebug | 523 | 3767 | 0 | 0 | 25 |

上表 tests 包含 skipped，未把跳过项称为执行通过。新增 8+3 项纯逻辑测试无跳过。设备构建没有执行 instrumentation tests。git diff --check 退出 0、无输出。旧/新 UiTags 与固定 testTag 的文件级集合对照没有丢失；default_model_quick_description 由 footerModifier 落在说明文本，模型类型/主题/协议等选项标签保留在等价底部单选项。范围审计没有发现禁止路径变更，生产 ui/chat/ 只有 SessionSettingsSheet.kt。依赖 diff 仅 Compose BOM 与两个允许的测试工件。

## 10. 未完成、偏离与已知限制

1. 任务书指定的所有主机验收命令均成功，源码与截图候选已交付；不宣称已完成第 10 节要求的模拟器实拍或真机验收。任务书明确禁止设备操作，布局/点击/读屏断言因此仅编译。
2. 200% 字号的 G3/G5 仍存在已知偏离，未获用户批准的例外，见第 6.3、8 节。当前共享布局优先保留大字号可读性，以最小高度而非强制裁切表达行高；没有把此判断冒充硬规格通过。
3. 新截图覆盖任务书 9.2 明列页面与所需组件；AgentEdit、SSH 编辑、MCP/WebDAV 的每个内部状态未另建全套截图，仅源差异与既有相关预览回归。没有对所有用户数据组合与滚动位置逐一穷举。
4. 数值选择弹层、搜索与模型编辑的 Content 预览不能验证真实模态遮罩、拖拽、输入法、长按、旋转或进程重建。设备测试 APK 编译验证其代码可构建，不证明上述运行行为。
5. 未新增独立长期记忆偏好。长期记忆分组显示既有 enableMemory/isGlobal 的来源摘要与原说明，以保持默认值和持久化语义；不是独立的新功能开关。
6. 全局字体会影响批次二以外页面，只做无新增裁切/重叠的截图回归；没有修改会话正文/步骤/输入区或宣称实现批次二。
7. 对生产状态、认证网络、真实本地模型载入、备份恢复、Termux/SSH 命令执行与用户资料持久化未做在线或设备验证。页面仍接原 actions/callbacks；不以预览样例当作这些能力验收。

## 11. DIA、HLG 与独立复核

DIA 已同步：CHANGELOG.md 的 Unreleased 新增“设置页视觉重设计”；README.md 更新设置界面与脚本写入审批名称；design-qa.md 写入当前设置合同与主机/设备证据边界；docs/context-management.md 更新四组、值行、步进器入口；规格同步阅读脚注、图标一致、弹层起点和任务书明确的长标题增高规则，普通默认行尺寸及设备验收要求保留。

HLG 已只读恢复 registry、索引及匹配原记录。遵任务书不追加 .agent/handover.md，交付与限制保存在本报告。未改 .pdec/，既有 PDEC 核验 approved/execution_ready=true、drift 为空；未初始化或迁移治理目录。

Agent 路由：主控负责共同组件、会话设置、集成、测试、逐图目视和报告；settings_pages 负责设置页及首页；retrieval_pages 负责五页检索、KG 与助手编辑；review 在施工之外串行做独立只读审阅。执行者采用最小上下文原生 Agent，文件所有权是逻辑边界，没有声称硬隔离。

主控复核两个页面报告、实际源文件与 HEAD diff、组件布局、偏好字段/原默认、测试改写、资源、依赖及全部命令输出。独立审阅提出的连续 RAG 更新、授权目标混同、记忆总门、默认标记、重复间距、Switch 撑高、单选语义、本地模型 engine/busy 门禁、只读假点击、标签落点、SSH 复制与认证/转发信息、协议/搜索深度/语言入口及设备测试旧路径问题均已逐项修复；细目见 independent-review.md。审阅发现的测试局部 Resources 声明已补齐；语言选择补充为底部单选，并增加原 en 代码写入与关闭的设备断言。上述内容均纳入最终设备 APK 编译。

保留全部任务边界：未执行 commit/push、tag/PR/release、设备/模拟器操作或 handover 追加；未读取或修改任务书排除目录、签名材料或 local.properties；未改 Room、偏好键/默认值、VM、存储层、.pdec 与批次二生产文件。没有停止或结束其它 Gradle/Kotlin 进程。

## 12. 复核问题修正

日期：2026-10-18。依据 `.agent/tmp-agent-reports/brief-redesign-batch1-r2.md`，在任务开始时的未提交批次一实现上修正。下文描述当前候选；前文只同步直接矛盾的说明落点，其余验收数字和历史记录保留。源码对照快照位于 `/tmp/nexara-redesign-r2-before/`，日志、审阅和截图哈希记录位于 `.agent/tmp-agent-reports/redesign-batch1-r2/`。

五个设备失败及基线通过结果来自任务书提供的 API 31 实测。本轮未操作设备，已验证范围为生产修正、设备测试编译、双渠道 JVM/lint/截图与 APK 构建；不把编译或截图称为五个设备用例运行通过。

### 12.1 对应任务书第 2 节：五个设备回归

| 任务书用例 / 根因 | 生产修正与测试保留 | 对应截图文件名 |
| --- | --- | --- |
| 360×800、2 倍字号云端表单：组名与行名均为 Presets | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ProviderFormScreen.kt:621` 使用“连接模板 / Connection template”，行名 Presets 保留。`native-ui/app/src/androidTest/java/com/promenar/nexara/ui/settings/ProviderFormInteractionTest.kt:325` 按原唯一语义节点滚动；未允许多节点。 | `providerFormAddReleasePreview_Provider form add phone_ffed90e8_0.png`；`providerFormBatch1_提供商表单 深色中文 411_6dc63768_0.png` |
| 横屏 2 倍字号云端表单：废弃列表序号越界 | `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/settings/ProviderFormInteractionTest.kt:346` 将 scrollToIndex(2) 换成名称、密钥字段的 performScrollTo；竖屏用例的序号滚动也换成原字段语义滚动。所有字段 displayed、连接/保存按钮 displayed 与 ≥48dp 断言保留。操作区位于 LazyColumn 外固定底部（`native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ProviderFormScreen.kt:675`），滚动字段后仍验证两按钮同时可见；不恢复无意义列表占位。 | `providerFormEditActionsLargeFontReleasePreview_Provider form edit actions 2x_71df0535_0.png`（2 倍字号静态视图；横屏滚动仍待设备运行） |
| 本地引导失败：分组和恢复操作同名 | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ProviderFormScreen.kt:923` 使用“本地推理 / On-device inference”，local_models_title 只承担恢复操作。原 localOnboardingFailureKeepsRecoveryActionReachable 未改，仍要求滚到、≥48dp、点击及回调。 | `providerFormLocalFailureReleasePreview_Provider form local onboarding failure_d4ce83e1_0.png` |
| 模型启用开关：带点击语义的 Switch 仅 32dp | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ProviderModelsScreen.kt:671` 直接扩充原 testTag 所在可点击节点至最小 48dp。ProviderModelsAccessibilityTest 原 ≥48dp 断言未改。 | `providerModelsReleasePreviewChineseLargeFont_Provider models chinese large font_1ef09f04_0.png`（触控边界不能仅由像素证明） |
| 共享设置长标题：220dp 下限制行数导致 48dp 高度 | `native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraSettingsItem.kt:90` 在可用宽度 <280dp 或放大字体时允许标题完整换行；普通默认行仍按 48/56dp 最小高度。原 UserSettingsAccessibilityTest 未改；`native-ui/app/src/androidTest/java/com/promenar/nexara/ui/common/SettingsLayoutTest.kt:140` 新增 >2 行、无 visualOverflow、≥72dp 断言。 | `narrowSettingsTitleBatch1Preview_窄屏长标题 English_058bf920_0.png`；`narrowSettingsTitleBatch1Preview_窄屏长标题 English 2x_7dc97dd8_0.png` |

仅上述两个 ProviderForm 压力用例改写结构定位；没有删除用例、放宽唯一节点要求或降低触控尺寸。设备不可操作，因此没有本轮设备红绿运行证据；任务书提供的复现、源码根因和回归断言分别登记，不混同。

### 12.2 对应任务书 3.1：脚注与信息落点

公共脚注在 `native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraSettingsSection.kt:61` 使用 NexaraReadingLineBreak；SelectionContainer、左右 16dp、上 8dp 与分组后 24dp 保留。同文件 NexaraSettingsGroup 让动态项目的地址、ID、描述及错误紧邻该项目，不把整组说明拼成文字墙。

| 页面 / 说明 | 当前处理与位置 | 对应截图文件名 |
| --- | --- | --- |
| 设置首页所有分组 | 删除通用、知识检索、工具数据、关于和资料说明的全部首页 footer；入口副标题与目标页承接解释。 `native-ui/app/src/main/java/com/promenar/nexara/ui/hub/UserSettingsHomeScreen.kt:207` | `settingsHomeBatch1_设置首页 深色中文 411_78157f6c_0.png` |
| 执行轮次与 Token 预算 | 轮次说明进入轮次弹层，Token 完整原理进入预算弹层；脚注只保留重复暂停、重置授权各一句。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/AgentRuntimeSettingsCard.kt:89` | `toolsBudgetBatch1_工具执行预算 深色中文 411_4af71068_0.png` |
| 会话上下文 | 完整阈值原理进入其选择弹层；分组只显示一条压缩摘要，追加当前不可用原因；删除两句重复说明。 `native-ui/app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt:727` | `sessionContextBatch1Preview_会话上下文 深中411_1e4eee2e_0.png` |
| 主题 | 文字缩放原理进入文字大小弹层；纯黑背景和不支持动态色的重复脚注删除；保留壁纸动态配色解释。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ThemeScreen.kt:171` | `themeTextScaleBatch1_主题文字缩放开启 深色中文 411_388e2016_0.png` |
| 知识图谱上下文与预算 | 估算原理、每文档版本预算范围和超阈值确认解释进入对应值项弹层；组脚注仅留 JIT 与部分预算风险。 `native-ui/app/src/main/java/com/promenar/nexara/ui/rag/KgRetrievalSettings.kt:98` | `advancedRetrievalBatch1FullPagePreview_高级检索 全页 深色 中文 411dp_1ad80634_0.png` |
| 检索高级与模板 | 抽取超时解释仅在弹层；全局和助手模板删除重复“点击编辑”脚注，完整模板由原编辑器查看；重排批量解释不在组脚注重复。 `native-ui/app/src/main/java/com/promenar/nexara/ui/rag/RagAdvancedScreen.kt:116` | `ragAdvancedBatch1DefaultPreview_检索高级 默认 深色 中文 411dp_57c63ba2_0.png` |
| Termux | 集成、权限、SSH 命令、本机主机、公钥、挂载按步骤分别说明；顶端已由步骤覆盖的五类错误不再重复。安装说明保留可识别版本号、≥0.109、启动初始化条件；未知错误保留原 guidance。后台风险全文保留。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/TermuxSettingsScreen.kt:421` | `termuxReadyBatch1_Termux 已就绪 深色中文 411_dc8d6cd0_0.png` |
| SSH | 地址与认证合并为每主机说明，保留终端策略与转发路由；空态删除与行内容重复的一句，完整连接和凭据风险保留。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/SshSettingsScreen.kt:467` | `sshHostBatch1_SSH 主机 深色中文 411_f2159585_0.png` |
| 备份 | 固定四类数据合并为一条说明，密钥加密与头像不迁移保留；WebDAV 只显示当前选中远端的大小和时间，不聚合全部文件说明。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/BackupSettingsScreen.kt:460` | `backupPageBatch1_备份默认 深色中文 411_256bcfdd_0.png` |
| 本地模型 | 引擎解释压缩为一句；模型元数据用逗号连接，完整错误、模型名、后端、大小与量化信息保留。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/LocalModelsScreen.kt:149` | `localModelsBatch1_本地模型 深色中文 411_8dddc05d_0.png` |
| 技能、工具与 MCP | 技能包仅保留加载与执行边界说明；预设工具、旧自定义工具和 MCP 按项展示 ID/描述或 URL/完整错误，旧工具安全警告保留一次。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/SkillsScreen.kt:273` | `toolsPageBatch1_工具设置顶部 深色中文 411_723dce31_0.png` |
| 提供商列表 | 每个提供商独立紧邻 URL、启用/凭据状态与未知 rawProtocolId；删除整组聚合段落。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ProviderListScreen.kt:186` | `providerConfiguredBatch1_提供商已配置 深色中文 411_3d091b01_0.png` |

默认 411dp 中文截图逐项检查：预算、上下文、主题、提供商、备份、本地模型、Termux 的普通脚注均不超过 3 行。动态项目按项分组避免聚合文字墙。没有用截断、maxLines 或省略号隐藏脚注。安全或风险原文例外如下：

| 例外 | 保留内容及位置 | 证据边界 |
| --- | --- | --- |
| Termux 后台运行与子进程限制 | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/TermuxSettingsScreen.kt:638` 保留 Android 子进程限制、唤醒锁耗电及不能绕过系统限制、厂商后台与通知要求的原文。 | Termux 完整长页与下半页预览中超过 3 行；按任务书安全/风险例外保留。 |
| SSH 连接、认证与私钥 | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/SshSettingsScreen.kt:417` 保留凭据仅在本机、备份排除、私钥导入/生成与终端连接边界。 | 连接说明组合与编辑器原风险说明可超过 3 行；空态有截图，编辑器每种状态未新增全套截图。 |

动态故障原始错误、任意长用户 ID/URL 和工具描述保持完整；不能保证任意输入都满足三行。360dp、英文、130%/200% 字号属于补充压力范围，不将默认 411dp 中文三行结论外推到这些条件。

### 12.3 对应任务书 3.2：图标列

| 分组 | 当前方案 / 位置 | 对应截图文件名 |
| --- | --- | --- |
| 执行预算 | 轮次、Token、重复上限、重置授权全部无图标，删除少数重置图标。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/AgentRuntimeSettingsCard.kt:88` | `toolsBudgetBatch1_工具执行预算 深色中文 411_4af71068_0.png` |
| 上下文管理 | 阈值、保留轮次、立即压缩全部无图标。 `native-ui/app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt:725` | `sessionContextBatch1Preview_会话上下文 深中411_1e4eee2e_0.png` |
| Termux 集成与状态 | 开关、版本、权限全部无图标；其它步骤在各自组内保持全有或全无。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/TermuxSettingsScreen.kt:419` | `termuxReadyBatch1_Termux 已就绪 深色中文 411_dc8d6cd0_0.png` |
| 协议与主题等枚举 | 单选项全部无前导图标，无空图标占位。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ProviderFormScreen.kt:706` | `providerFormBatch1_提供商表单 深色中文 411_6dc63768_0.png` |
| SSH 主机 | 网络转发行补 SwapHoriz，与其它主机行统一有图标。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/SshSettingsScreen.kt:467` | `sshHostBatch1_SSH 主机 深色中文 411_f2159585_0.png` |
| WebDAV | 空远端状态补 CloudOff，其余操作保留有图标。 `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/BackupSettingsScreen.kt:548` | `backupPageBatch1_备份默认 深色中文 411_256bcfdd_0.png` |

生产源码 `grep -rn "leadingSpacer = true" native-ui/app/src/main/java` 无匹配（退出 1 代表未发现），组件兼容参数及其对齐测试保留。共享行普通无图标起点 16dp，图标行起点 52dp；不再以空占位制造同组缩进。

### 12.4 对应任务书 3.3：选择弹层

`native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraValuePickerSheet.kt:67` 将说明设为 bodySmall 阅读换行。预设项取消额外水平内边距，自定义行 horizontalPadding=0；标题、说明、预设、自定义与输入框共用 wrapper 的 24dp 起点。数值弹层接受与触发行同一个 title，轮次预览也从硬编码“最大轮次”换成真实 skills_loop_limit。

证据：`discretePickerBatch1Preview_离散弹层 深中411_4a945f43_0.png`、`customPickerBatch1Preview_离散自定义 深中411_7d6ff36f_0.png`、`invalidPickerBatch1Preview_离散错误 深中411_6774feb4_0.png`、`tokenPickerBatch1Preview_Token上限弹层 深中411_161a49db_0.png`。自定义和错误预览增加展示高度，输入、范围提示与完成按钮完整呈现；生产弹层高度仍受原 70% 上限约束并内部滚动。

| 迁入说明 | 对应源码位置 |
| --- | --- |
| 执行循环限制：skills_loop_limit_desc | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/SkillsScreen.kt:635` |
| Token 预算：p0_runtime_limits_desc | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/AgentRuntimeSettingsCard.kt:96` |
| 文字大小：theme_text_scale_desc | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ThemeScreen.kt:171` |
| 压缩阈值：sheet_settings_context_description，保留最近轮次参数 | `native-ui/app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt:732` |
| 执行模式：当前 auto/semi/manual 的完整说明 | `native-ui/app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt:604` |
| KG 上下文估算、每文档版本预算、超限确认 | `native-ui/app/src/main/java/com/promenar/nexara/ui/rag/KgRetrievalSettings.kt:98` |
| 抽取超时：rag_advanced_kg_timeout_desc | `native-ui/app/src/main/java/com/promenar/nexara/ui/rag/RagAdvancedScreen.kt:116` |

独立审阅补充发现固定高度枚举列表的横屏可达风险，已在 `native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraBottomSheet.kt:41` 增加默认关闭的滚动参数；仅主题、语言、模型类型、协议、搜索深度、改写策略、执行模式七类枚举启用，已有滚动表单与数值弹层不叠加同向滚动。`native-ui/app/src/androidTest/java/com/promenar/nexara/ui/common/SettingsLayoutTest.kt:155` 覆盖 252dp 高度与 2 倍字号的最后一项滚动、≥48dp 及点击回调。此测试仅编译。

### 12.5 对应任务书 3.4：标题与 Token

| 页面 | 分组标题 / 保留的行标题 | 位置及截图 |
| --- | --- | --- |
| 提供商表单 | 连接模板 / 预设；本地推理 / 本地模型恢复操作 | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ProviderFormScreen.kt:621`；`providerFormBatch1_提供商表单 深色中文 411_6dc63768_0.png` |
| 会话工具 | 工具审批 / 执行模式 | `native-ui/app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsSheet.kt:594`；`sessionToolsBatch1Preview_会话工具 深中411_7ee16e8b_0.png` |
| 搜索配置 | 搜索结果 / 结果数量；搜索选项 / 搜索深度 | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/SkillsScreen.kt:913`；`searchConfigBatch1_搜索配置 深色中文 411_db88d89b_0.png` |
| 主题 | 显示风格 / 主题模式 | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ThemeScreen.kt:105`；`themeTextScaleBatch1_主题文字缩放开启 深色中文 411_388e2016_0.png` |
| 模型编辑 | 请求配置 / 模型 ID；模型用途 / 类型 | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ModelEditorSheet.kt:233`；`modelEditorBatch1_模型编辑 深色中文 411_45fa0835_0.png` |
| 全局检索 | 生成模板 / 摘要提示词 | `native-ui/app/src/main/java/com/promenar/nexara/ui/rag/GlobalRagConfigScreen.kt:167`；`globalRagBatch1DefaultPreview_全局检索 默认 深色 中文 411dp_2f9e9854_0.png` |
| 助手检索 | 记忆生成 / 摘要模板 | `native-ui/app/src/main/java/com/promenar/nexara/ui/hub/AgentRagConfigScreen.kt:156`；`agentRagBatch1DefaultPreview_助手检索 默认 深色 中文 411dp_3d52f414_0.png` |
| SSH 编辑 | 网络转发 / 端口转发 | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/SshSettingsScreen.kt:734`；`sshHostBatch1_SSH 主机 深色中文 411_f2159585_0.png`（主机列表截图；编辑器分组为源码复核） |
| 备份 | 归档选项 / 备份内容 | `native-ui/app/src/main/java/com/promenar/nexara/ui/settings/BackupSettingsScreen.kt:459`；`backupPageBatch1_备份默认 深色中文 411_256bcfdd_0.png` |

中文可见资源中的 token/tokens 统一为 Token，资源键和机器标识符如 max_tokens 不改。涉及 values-zh-rCN/strings.xml、p0_runtime_strings.xml、strings_kg_budget.xml、strings_kg_retrieval.xml；没有改变数值单位语义。上下文重复句的处理见 12.2。

新增双语资源均位于 values/settings_batch1_review.xml 与 values-zh-rCN/settings_batch1_review.xml，共 25 个同名键。当前文案如下，原文对照及具体迁移见前述各表：

| 资源键 | 当前中文 | 当前英文 |
| --- | --- | --- |
| `settings_batch1_connection_template` | 连接模板 | Connection template |
| `settings_batch1_local_inference` | 本地推理 | On-device inference |
| `settings_batch1_search_results` | 搜索结果 | Search results |
| `settings_batch1_search_options` | 搜索选项 | Search options |
| `settings_batch1_tool_approval` | 工具审批 | Tool approval |
| `settings_batch1_display_style` | 显示风格 | Display style |
| `settings_batch1_model_purpose` | 模型用途 | Model purpose |
| `settings_batch1_generation_template` | 生成模板 | Generation template |
| `settings_batch1_memory_generation` | 记忆生成 | Memory generation |
| `settings_batch1_network_forwarding` | 网络转发 | Network forwarding |
| `settings_batch1_backup_options` | 归档选项 | Archive options |
| `settings_batch1_runtime_footer` | 相同工具和参数连续重复达到上限时暂停。下次开启不限轮次全自动执行时需要确认。 | Repeated calls with identical arguments pause at the limit. Unlimited automatic execution asks for confirmation after a reset. |
| `settings_batch1_context_footer` | 压缩时折叠较早的工具结果并总结历史，保留最近 %1$d 轮；完整记录保留在会话中。 | Compaction folds earlier tool results and summarizes history, retaining the latest %1$d turns. The full record stays in the session. |
| `settings_batch1_termux_intro_footer` | SSH 与目录挂载可独立配置；命令需在 Termux 中粘贴执行，并遵循会话审批。诊断验证权限与结果回传。 | Configure SSH and mounts independently. Paste commands into Termux; Agent commands follow session approval. Diagnostics check permission and results. |
| `settings_batch1_termux_permission_footer` | 允许“在 Termux 环境中运行命令”权限；未弹出申请时，到应用详情的“权限 → 其它权限”查找。返回此页会刷新状态。 | Allow the permission to run commands in Termux. If no prompt appears, find it under app details → Permissions → Other permissions. Status refreshes on return. |
| `settings_batch1_termux_ssh_setup_footer` | 在 Termux 执行命令；whoami 输出用户名，密码登录需运行 passwd。sshd 仅监听本机，首次连接前须核对主机指纹。 | Run commands in Termux. whoami prints the username; run passwd for password login. sshd listens locally. Verify its fingerprint before the first connection. |
| `settings_batch1_termux_ssh_host_footer` | 添加 127.0.0.1:8022，填写 whoami 的用户名并选择密码或密钥认证。 | Add 127.0.0.1:8022 with the username from whoami and password or key authentication. |
| `settings_batch1_termux_key_section` | 公钥登录 | Public key login |
| `settings_batch1_termux_mount_footer` | 系统选择器授权目录（侧栏可选 Termux），以 /mnt/别名 访问，无需所有文件权限。关闭只读可写，别名冲突时更名。 | Grant a folder in the picker (Termux is in the sidebar), without all-files access; use /mnt/alias. Disable read-only to allow writes. Rename conflicting aliases. |
| `settings_batch1_backup_core_footer` | 固定包含%1$s；密钥可选，头像不迁移。包含密钥时须加密；加密适用于本机与 WebDAV。 | Always includes %1$s; keys are optional and avatars stay here. Encryption works for local and WebDAV backups and is required with keys. |
| `settings_batch1_backup_local_footer` | 数据默认保存在本机；WebDAV 仅供手动备份与恢复。可设置密码加密，包含密钥时必须加密。 | Data stays on this device; WebDAV provides manual backup and restore. Password encryption is optional unless keys are included. |
| `settings_batch1_local_models_footer` | 启用引擎后可在设备上运行已导入的 GGUF 模型，保护隐私并降低延迟。 | Enable the engine to run imported GGUF models on this device for privacy and low latency. |
| `settings_batch1_agent_skills_footer` | 任务匹配时加载 SKILL.md 指令与参考资料；技能包本身不执行代码。 | Matching tasks load SKILL.md instructions and references. Skill packs themselves do not execute code. |
| `settings_batch1_model_request` | 请求配置 | Request configuration |
| `settings_batch1_termux_enable_diagnostic_summary` | 开启后可运行诊断 | Enable to run diagnostics |

Termux 禁用态副标题由“通过 Termux 执行命令 / Run commands through Termux”调整为“开启后可运行诊断 / Enable to run diagnostics”；启用态仍使用前者。原启用提醒由状态副标题承接，不重复放在脚注。安装步骤原中文“结果回传需要 0.109 及以上版本”补齐为“结果回传需要可识别且不低于 0.109 的版本号”，英文同步加入 a recognizable version number of 0.109 or newer；安装后启动并等待初始化的条件保留。对应 strings_settings_termux.xml:29（中英文同位置）。SSH 配置说明压缩为“为终端和 SSH 工具配置主机，工具仅接收别名。/ Configure hosts for terminal and SSH tools. Tools receive only host aliases.”，仍保留工具只接收主机别名的边界。其它脚注只是删重、压缩与迁移，不改变权限、默认值或动作。

### 12.6 测试、独立复核及范围

SettingsLayoutTest 保留原 12 个用例，新增 3 个，共 15 个。新增断言位于 `native-ui/app/src/androidTest/java/com/promenar/nexara/ui/common/SettingsLayoutTest.kt:122`（五类左缘同为 24dp，容差 0.5px）、`native-ui/app/src/androidTest/java/com/promenar/nexara/ui/common/SettingsLayoutTest.kt:140`（完整长标题）、`native-ui/app/src/androidTest/java/com/promenar/nexara/ui/common/SettingsLayoutTest.kt:155`（受限枚举滚动）。没有将布局测量编译通过称为运行通过；设备测试 APK 已包含这些断言。

合法结构变化触发的四个 JVM 源码契约失败保存在 source-contract-red.json，修正后双渠道全量通过：

| 契约 | 改写及保留的用户意图 |
| --- | --- |
| `native-ui/app/src/test/java/com/promenar/nexara/ui/hub/UserSettingsHomeScreenContractTest.kt:120` | 删除首页必须有语言/关于脚注的旧要求，改为首页无 footer；图标、入口与导航约束保留。 |
| `native-ui/app/src/test/java/com/promenar/nexara/ui/hub/UserSettingsHomeScreenContractTest.kt:197` | 提供商按项 Group 与相邻 URL/状态/rawProtocolId；不再要求废弃的整组聚合脚注或装饰分割线，完整状态来源保留。 |
| `native-ui/app/src/test/java/com/promenar/nexara/ui/common/BettboxGroupedSurfacesContractTest.kt:37` | 认可委托同一 BettboxListGroup 材质的 NexaraSettingsGroup，公共分组面要求保留。 |
| `native-ui/app/src/test/java/com/promenar/nexara/ui/settings/BackupContentUiContractTest.kt:21` | 固定四类标签动态插入紧凑脚注，替换旧硬编码文案要求；核心类别不可切换、密钥可选及原安全回调断言保留。 |

NumericSettingSpec 的 8 项纯逻辑测试及会话检索 3 项测试未修改且通过；R2 没有新增业务逻辑或存储字段。纯布局和文案使用设备回归断言编译、双风味截图、lint 与构建作为主机替代证据。

独立审阅者为原生子 Agent review_redesign_r2，最小上下文、未参与施工，采用逻辑只读边界，无硬隔离声明。共发现并串行复核关闭：枚举弹层缺少受限高度滚动、旧自定义工具 ID 展示遗漏、Termux 不可解析版本的恢复说明遗漏。完整意见见 independent-review.md；其审阅没有运行设备或代替主控截图验收。

主控复核当前源码、任务开始快照差异、所有旧 UiTags/testTag 的保留、测试断言与双语资源。生产 ui/chat 增量仅 SessionSettingsSheet.kt；NumericSettingSpec、SettingsNumericSpecs、依赖、偏好键、默认值、存储/VM/Room、PDEC 与批次二生产源码没有 R2 修改。

### 12.7 截图基线与逐页 G1–G9

双渠道更新后有效基线 243 张；相对 R2 开始快照新增 10、更新 112、删除 9、哈希未变 121。新增/更新的 122 张原图均逐张目视：首次 visual-review 的 49 张联系表覆盖全部变化，后续 visual-recheck 及 visual-tightening 单独保存收尾变化图，逐张复查。visual-final-manifest.json 将每张当前原图 SHA-256 关联到实际目视记录；长图分段查看，未修改原图来规避差异。

旧尺寸的 8 张基线已确认哈希与开始快照一致，移至 /tmp/nexara-redesign-r2-before/retired-references/，由新尺寸截图替代。另 1 张历史搜索横屏基线在任务开始时已无对应预览函数，哈希未变，核对 JUnit 发现清单后同样备份移出；其余 243 张基线与两渠道 243 个发现用例逐项一致，未改动已验证用例的基线。两张 220dp 英文长标题为新增覆盖；其余新增是完整展示自定义/错误/预算弹层的尺寸替代。

以下判断仅针对可见截图，D 表示精确测量/真实操作仍待设备；N 为该页无适用数值或副标题。普通 411dp 中文默认行遵守尺寸合同，窄屏长标题或放大字号按任务书完整换行。

| 页面或组件 | G1/G4/G6/G7 | G2/G5 | G3 | G8 | G9 |
| --- | --- | --- | --- | --- | --- |
| 首页（含浅色英文） | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 默认中文通过 | 默认中文未见新增问题；压力例外见下文 | N |
| 工具顶部、执行预算、搜索配置 | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 默认中文通过 | 默认中文未见新增问题；压力例外见下文 | tnum/右缘目视通过 |
| Termux 未安装、就绪、诊断失败及完整下半页 | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 默认中文通过；完整风险脚注例外 | 默认中文未见新增问题；压力例外见下文 | 命令/状态 N |
| SSH 空态、已有主机 | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 默认中文通过；动态主机名例外 | 默认中文未见新增问题；压力例外见下文 | 数字/地址目视通过 |
| 备份、用量、提供商列表 | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 默认中文通过；动态 URL 例外 | 默认中文未见新增问题；压力例外见下文 | 用量数字目视通过 |
| 提供商表单、模型列表、模型编辑、默认/本地模型 | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 默认样例通过；任意长 ID 不外推 | 默认中文未见新增问题；压力例外见下文 | N |
| 主题文字缩放 | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 默认中文通过 | 默认中文未见新增问题；压力例外见下文 | 百分比目视通过 |
| 全局检索、检索高级、高级检索、助手检索、助手高级检索 | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 默认中文通过 | 默认中文未见新增问题；压力例外见下文 | tnum/右缘目视通过 |
| 会话模型、参数、工具、上下文四标签 | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 默认样例通过；动态 ID 例外 | 默认中文未见新增问题；压力例外见下文 | tnum/右缘目视通过 |
| 数值弹层（轮次、Token、自定义/错误、连续阈值/Top P）与共享行/步进器 | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 默认可见内容通过 | 默认中文未见新增问题；压力例外见下文 | tnum/右缘目视通过 |
| 220dp 长标题普通/2 倍字号 | 同行/按钮数/组内起点/说明位置目视通过，输入和命令按规格例外 | 居中/普通行高目视通过，精确值 D | 无副标题；标题完整 | 默认中文未见新增问题；压力例外见下文 | N |

130%/200% 或窄屏压力状态不承诺全部短副标题可完整显示；SSH 130% 的脚注可末行仅剩“名。”，Termux 窄屏放大字的步骤说明可末行仅剩“骤。”，这些压力条件不宣称 G3/G8 全部通过。第 6.3 的 G5 固定高度偏离是 R1 按原规则的历史记录；R2 任务书明确要求窄屏长标题完整换行并增高，当前 G5 按 411dp 普通默认行的 48/56dp 与长标题增高分别验收，精确值仍待设备。220dp 长标题已完整展示。弹层系统遮罩、IME、真实拖拽滚动、TalkBack、网络认证、本地模型加载及备份恢复没有运行验证。SSH 编辑、MCP/WebDAV 等内部状态仅源代码复核和已有相关预览，没有宣称穷举截图覆盖。

#### 新增（10）

相对路径基于 native-ui/app/src/screenshotTestDebug/reference/：

```text
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/customPickerBatch1Preview_离散自定义 深中360_b9a215c7_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/customPickerBatch1Preview_离散自定义 深中411_7d6ff36f_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/discretePickerBatch1Preview_离散弹层 深中360_dfbef3e1_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/discretePickerBatch1Preview_离散弹层 深中411_4a945f43_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/invalidPickerBatch1Preview_离散错误 深中360_79edacca_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/invalidPickerBatch1Preview_离散错误 深中411_6774feb4_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/narrowSettingsTitleBatch1Preview_窄屏长标题 English 2x_7dc97dd8_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/narrowSettingsTitleBatch1Preview_窄屏长标题 English_058bf920_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/tokenPickerBatch1Preview_Token上限弹层 深中360_bfdc647d_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/tokenPickerBatch1Preview_Token上限弹层 深中411_161a49db_0.png
```

#### 更新（112）

相对路径基于 native-ui/app/src/screenshotTestDebug/reference/：

```text
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/advancedRetrievalChineseLightReleasePreview_Advanced retrieval Chinese light_24f961e4_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/agentSkillPacksReleasePreview_Agent skill packs Chinese_8fae2a85_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/backupSettingsReleasePreview_Backup settings English_f5f977a0_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/defaultModelsChineseLargeFontReleasePreview_Default models Chinese large font_8ee62bc8_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/defaultModelsChinesePhoneReleasePreview_Default models Chinese phone_c1f87db3_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/defaultModelsEnglishLandscapeReleasePreview_Default models English landscape_804166e4_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerFormAddReleasePreview_Provider form add phone_ffed90e8_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerFormEditActionsLargeFontReleasePreview_Provider form edit actions 2x_71df0535_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerFormLocalFailureReleasePreview_Provider form local onboarding failure_d4ce83e1_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerListChineseNarrowReleasePreview_Provider list Chinese narrow_342492e5_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/providerModelEditorReleasePreviewChineseLargeFont_Provider model editor Chinese large font_fded84d2_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragAdvancedEnglishDarkReleasePreview_RAG advanced English dark_38e36222_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/ragGlobalChineseDarkLargeFontReleasePreview_RAG global Chinese dark large font_6e60c62b_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/themeSettingsChineseDarkLargeTextReleasePreview_Theme settings Chinese dark 2x_a9a5161a_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/themeSettingsChineseLightReleasePreview_Theme settings Chinese light_046ba5d0_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/themeSettingsEnglishDarkReleasePreview_Theme settings English dark_48c5130d_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/themeSettingsEnglishLightLargeTextReleasePreview_Theme settings English light 2x_0884a93f_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsAppChineseCompactLargeFontReleasePreview_User settings app Chinese compact large font_015f71c8_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsAppChinesePhoneReleasePreview_User settings app Chinese phone_921f91d8_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsAppChineseTabletReleasePreview_User settings app Chinese tablet_60d69b4f_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsIntegratedNavigationChinesePhoneReleasePreview_User settings integrated navigation Chinese phone_8b19e30b_0.png
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/userSettingsProviderLargeFontReleasePreview_User settings provider English large font_e804a4ab_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/contextUsageDarkPreview_上下文用量 深色 大字号130_91da7506_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/contextUsageDarkPreview_上下文用量 深色_dcf0bd35_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/sessionContextManagementDarkPreview_上下文管理 深色 大字号130_1feef59d_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/sessionContextManagementDarkPreview_上下文管理 深色_65e5ab4e_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/sessionContextManagementLightPreview_上下文管理 浅色 大字号130_94d724f6_0.png
com/promenar/nexara/ui/chat/ContextCompactionScreenshotTestKt/sessionContextManagementLightPreview_上下文管理 浅色_606cfa83_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionContextBatch1Preview_会话上下文 深中130_924a2290_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionContextBatch1Preview_会话上下文 深中360_939f9512_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionContextBatch1Preview_会话上下文 深中411_1e4eee2e_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionToolsBatch1Preview_会话工具 深中360_f710e318_0.png
com/promenar/nexara/ui/chat/SessionSettingsBatch1ScreenshotTestKt/sessionToolsBatch1Preview_会话工具 深中411_7ee16e8b_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/settingsComponentsBatch1Preview_设置组件 深中130_483fa756_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/settingsComponentsBatch1Preview_设置组件 深中360_0e44cb29_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/settingsComponentsBatch1Preview_设置组件 深中411_43db0646_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/advancedRetrievalBatch1DefaultPreview_高级检索 默认 深色 中文 360dp_21b471de_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/advancedRetrievalBatch1DefaultPreview_高级检索 默认 深色 中文 411dp_ba0a08bc_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/advancedRetrievalBatch1FullPagePreview_高级检索 全页 深色 中文 360dp_b42698de_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/advancedRetrievalBatch1FullPagePreview_高级检索 全页 深色 中文 411dp_1ad80634_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentAdvancedRetrievalBatch1DefaultPreview_助手高级检索 默认 深色 中文 360dp_9e2ec1a6_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentAdvancedRetrievalBatch1DefaultPreview_助手高级检索 默认 深色 中文 411dp_92bbb691_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentRagBatch1DefaultPreview_助手检索 默认 深色 中文 360dp_5b32599d_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/agentRagBatch1DefaultPreview_助手检索 默认 深色 中文 411dp_3d52f414_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/globalRagBatch1DefaultPreview_全局检索 默认 深色 中文 360dp_8e55b938_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/globalRagBatch1DefaultPreview_全局检索 默认 深色 中文 411dp_2f9e9854_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/ragAdvancedBatch1DefaultPreview_检索高级 默认 深色 中文 360dp_e7dad58a_0.png
com/promenar/nexara/ui/rag/RetrievalBatch1ScreenshotTestKt/ragAdvancedBatch1DefaultPreview_检索高级 默认 深色 中文 411dp_57c63ba2_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/backupPageBatch1_备份默认 深色中文 360_96fe25e9_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/backupPageBatch1_备份默认 深色中文 411_256bcfdd_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/defaultModelsBatch1_默认模型 深色中文 360_a5cefeb4_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/defaultModelsBatch1_默认模型 深色中文 411_dc7195ab_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/localModelsBatch1_本地模型 深色中文 360_3a2422fa_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/localModelsBatch1_本地模型 深色中文 411_8dddc05d_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/modelEditorBatch1_模型编辑 深色中文 360_db386e30_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/modelEditorBatch1_模型编辑 深色中文 411_45fa0835_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerConfiguredBatch1_提供商已配置 深色中文 360_f0db6591_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerConfiguredBatch1_提供商已配置 深色中文 411_3d091b01_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerFormBatch1_提供商表单 深色中文 360_1da4c5b8_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/providerFormBatch1_提供商表单 深色中文 411_6dc63768_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/searchConfigBatch1_搜索配置 深色中文 360_faa0ae0a_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/searchConfigBatch1_搜索配置 深色中文 411_db88d89b_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/settingsHomeBatch1LargeText_设置首页 深色中文 字号130_07ff7895_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/settingsHomeBatch1LightEnglish_设置首页 浅色英文_08fc9be1_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/settingsHomeBatch1_设置首页 深色中文 360_215f4ca3_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/settingsHomeBatch1_设置首页 深色中文 411_78157f6c_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/sshHostBatch1_SSH 主机 深色中文 360_f9d0639f_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/sshHostBatch1_SSH 主机 深色中文 411_f2159585_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/sshPageBatch1_SSH 默认 深色中文 360_8b0dc5a6_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/sshPageBatch1_SSH 默认 深色中文 411_f8dfe20b_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/termuxDiagnosticFailureBatch1_Termux 诊断失败 深色中文 360_bf6882a0_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/termuxDiagnosticFailureBatch1_Termux 诊断失败 深色中文 411_6bfd648c_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/termuxReadyBatch1_Termux 已就绪 深色中文 360_8bda7944_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/termuxReadyBatch1_Termux 已就绪 深色中文 411_dc8d6cd0_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/themeTextScaleBatch1_主题文字缩放开启 深色中文 360_890f60fd_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/themeTextScaleBatch1_主题文字缩放开启 深色中文 411_388e2016_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/tokenUsageBatch1_用量默认 深色中文 360_36f3da0c_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/tokenUsageBatch1_用量默认 深色中文 411_41067c31_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsBudgetBatch1_工具执行预算 深色中文 360_da67b490_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsBudgetBatch1_工具执行预算 深色中文 411_4af71068_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsPageBatch1LargeText_工具设置顶部 深色中文 字号130_6d11a979_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsPageBatch1LightEnglish_工具设置顶部 浅色英文_7f8611db_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsPageBatch1_工具设置顶部 深色中文 360_e7a2d992_0.png
com/promenar/nexara/ui/settings/SettingsPagesBatch1ScreenshotTestKt/toolsPageBatch1_工具设置顶部 深色中文 411_723dce31_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshEmptyDarkPreview_SSH 空态 深色 大字号130_dea50a1b_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshEmptyDarkPreview_SSH 空态 深色_a2952e4f_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshEmptyLightPreview_SSH 空态 浅色 大字号130_5ad6fe68_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshEmptyLightPreview_SSH 空态 浅色_7b77b0a8_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshHostsDarkPreview_SSH 主机列表 深色 大字号130_02a7dc2f_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshHostsDarkPreview_SSH 主机列表 深色_c2891582_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshHostsLightPreview_SSH 主机列表 浅色 大字号130_f9afc421_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/sshHostsLightPreview_SSH 主机列表 浅色_46dd8bea_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledDarkPreview_Termux 未安装 深色 大字号130_6889bcc1_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledDarkPreview_Termux 未安装 深色_31a71686_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLightPreview_Termux 未安装 浅色 大字号130_b86d69ef_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLightPreview_Termux 未安装 浅色_f80ec007_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLowerDarkPreview_Termux 未安装下半页 深色 大字号130_8fa0b9a8_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLowerDarkPreview_Termux 未安装下半页 深色_47daebe2_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLowerLightPreview_Termux 未安装下半页 浅色 大字号130_55886a25_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxNotInstalledLowerLightPreview_Termux 未安装下半页 浅色_95ecd34f_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyDarkPreview_Termux 已就绪 深色 大字号130_da2d0314_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyDarkPreview_Termux 已就绪 深色_03d873f7_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLightPreview_Termux 已就绪 浅色 大字号130_ef253423_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLightPreview_Termux 已就绪 浅色_88a18b69_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLowerDarkPreview_Termux 已就绪下半页 深色 大字号130_8fb8ecac_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLowerDarkPreview_Termux 已就绪下半页 深色_d2b0fde4_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLowerLightPreview_Termux 已就绪下半页 浅色 大字号130_a31ab7fd_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/termuxReadyLowerLightPreview_Termux 已就绪下半页 浅色_64b239d4_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/toolsHeaderDarkPreview_工具设置顶部 深色 大字号130_e622b07b_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/toolsHeaderDarkPreview_工具设置顶部 深色_ea9aba16_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/toolsHeaderLightPreview_工具设置顶部 浅色 大字号130_da68fc1a_0.png
com/promenar/nexara/ui/settings/SettingsVisualScreenshotTestKt/toolsHeaderLightPreview_工具设置顶部 浅色_e5018eab_0.png
```

#### 删除（9）

相对路径基于 native-ui/app/src/screenshotTestDebug/reference/：

```text
com/promenar/nexara/ui/ReleasePreviewScreenshotTestKt/searchSettingsEnglishLightLandscapeReleasePreview_Search settings English light landscape_30447b87_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/customPickerBatch1Preview_离散自定义 深中360_688fba69_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/customPickerBatch1Preview_离散自定义 深中411_e3f13b91_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/discretePickerBatch1Preview_离散弹层 深中360_aac4360f_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/discretePickerBatch1Preview_离散弹层 深中411_a9fb0a96_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/invalidPickerBatch1Preview_离散错误 深中360_11b44339_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/invalidPickerBatch1Preview_离散错误 深中411_bcafabc9_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/tokenPickerBatch1Preview_Token上限弹层 深中360_1a26f4d4_0.png
com/promenar/nexara/ui/common/SettingsComponentsBatch1ScreenshotTestKt/tokenPickerBatch1Preview_Token上限弹层 深中411_1560d524_0.png
```

### 12.8 最终验收命令与实际输出

所有 Gradle 命令在 native-ui/ 串行运行，Python/grep 在仓库根运行。命令级 JAVA_TOOL_OPTIONS 仅使用本机既有 Robolectric 离线 SDK 缓存，没有安装依赖或持久修改环境。Kotlin daemon 的用户 Library 写入被沙箱拒绝时，Gradle 使用自身非 daemon 编译回退；没有执行日志中建议的 --stop，也没有结束其它进程。

截图基线更新命令（同一命令级离线缓存环境）：

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:updateSideloadDebugScreenshotTest :app:updatePlayDebugScreenshotTest
```

退出码 0；日志 redesign-batch1-r2/validation-screenshot-update-tightening.log。最后 8 行：

```text
> Task :app:packagePlayDebugScreenshotTestForScreenshotTest

> Task :app:updatePlayDebugScreenshotTest
Picked up JAVA_TOOL_OPTIONS: -Drobolectric.dependency.dir=/Users/promenar/.gradle/caches/nexara-robolectric-sdk -Drobolectric.offline=true

BUILD SUCCESSFUL in 1m 56s
63 actionable tasks: 17 executed, 3 from cache, 43 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

| JVM 渠道 | XML | tests（含 skipped） | failures | errors | skipped |
| --- | --- | --- | --- | --- | --- |
| SideloadDebug | 525 | 3791 | 0 | 0 | 25 |
| PlayDebug | 523 | 3767 | 0 | 0 | 25 |

Lint 严重性计数：{"Warning": 646, "Hint": 11}；没有 Error/Fatal，保留的 Warning/Hint 不称为零告警，其中包含停止展示的原说明资源产生的 UnusedResources（例如主题纯黑背景、动态色不可用、检索模板及 Termux 诊断前提说明）。两渠道 JUnit 各 243/243、零失败，与当前 243 张基线逐项对应。

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:lintSideloadDebug
```

退出码 0；日志 redesign-batch1-r2/validation-unit-lint.log。最后 8 行：

```text
> Task :app:lintReportSideloadDebug
Wrote HTML report to file:///Users/promenar/Codex/Nexara-wt-redesign1/native-ui/app/build/reports/lint-results-sideloadDebug.html

> Task :app:lintSideloadDebug

BUILD SUCCESSFUL in 1m 28s
73 actionable tasks: 6 executed, 67 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:validateSideloadDebugScreenshotTest :app:validatePlayDebugScreenshotTest
```

退出码 0；日志 redesign-batch1-r2/validation-screenshot.log。最后 8 行：

```text
> Task :app:packagePlayDebugScreenshotTestForScreenshotTest UP-TO-DATE

> Task :app:validatePlayDebugScreenshotTest
Picked up JAVA_TOOL_OPTIONS: -Drobolectric.dependency.dir=/Users/promenar/.gradle/caches/nexara-robolectric-sdk -Drobolectric.offline=true

BUILD SUCCESSFUL in 1m 59s
63 actionable tasks: 4 executed, 59 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:assembleSideloadDebug :app:assemblePlayDebug
```

退出码 0；日志 redesign-batch1-r2/validation-assemble.log。最后 8 行：

```text
> Task :app:assembleSideloadDebug
> Task :app:packagePlayDebug
> Task :app:createPlayDebugApkListingFileRedirect UP-TO-DATE
> Task :app:assemblePlayDebug

BUILD SUCCESSFUL in 5s
87 actionable tasks: 6 executed, 81 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" -Pnexara.deviceE2e=true -Pnexara.deviceE2eAbi=arm64-v8a :app:assembleSideloadDeviceTest :app:assembleSideloadDeviceTestAndroidTest
```

退出码 0；日志 redesign-batch1-r2/validation-device-assemble.log。最后 8 行：

```text
> Task :app:assembleSideloadDeviceTestAndroidTest UP-TO-DATE
> Task :app:packageSideloadDeviceTest
> Task :app:createSideloadDeviceTestApkListingFileRedirect UP-TO-DATE
> Task :app:assembleSideloadDeviceTest

BUILD SUCCESSFUL in 6s
80 actionable tasks: 10 executed, 1 from cache, 69 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

```bash
/Applications/Xcode.app/Contents/Developer/usr/bin/python3 scripts/ci/check-text-wrapping.py
```

退出码 0；日志 redesign-batch1-r2/validation-text-wrapping.log。最后 8 行：

```text
文本换行门禁通过
```

```bash
/Applications/Xcode.app/Contents/Developer/usr/bin/python3 scripts/ci/check-distribution-manifests.py
```

退出码 0；日志 redesign-batch1-r2/validation-distribution.log。最后 8 行：

```text
发行清单检查通过（debug）：play 无所有文件访问权限，sideload 包含；包身份一致
```

```bash
git diff --check
```

退出码 0；日志 redesign-batch1-r2/validation-diff.log。最后 8 行：

```text

```

静态核查的实际输出保存在 validation-static-final.log：leadingSpacer = true 无匹配；NexaraSlider/NexaraSliderInt 只命中通用定义和 NexaraValuePickerSheet 内部；git diff --check 无输出。新增双语资源键一致，中文可见文本无小写 token/tokens。

设备构建产物仅供主控后续安装运行：

| APK | SHA-256 |
| --- | --- |
| `native-ui/app/build/outputs/apk/androidTest/sideload/deviceTest/app-sideload-deviceTest-androidTest.apk` | `97d90076dc47680555e071025f51dbc42ac4ca81326723b2644e66d9e5d1b1d1` |
| `native-ui/app/build/outputs/apk/play/debug/app-play-debug.apk` | `0915c3242bddd7d83be4f104e9a11085e7ea27d7d1f9d08e5b70e83b3e43604c` |
| `native-ui/app/build/outputs/apk/sideload/debug/app-sideload-debug.apk` | `0db0b92fc17b1b375b4addc49d4c4b2b0aa605ce369b55ecdc76b7f606a3c122` |
| `native-ui/app/build/outputs/apk/sideload/deviceTest/app-sideload-deviceTest.apk` | `30779356ea405992618c345a9de6ae29c07af2e91708f3749868b083d380215a` |

### 12.9 DIA、HLG、交付边界

DIA 已同步 CHANGELOG.md、README.md、design-qa.md、docs/context-management.md 与 docs/superpowers/specs/2026-10-17-nexara-settings-chat-redesign.md：包括阅读脚注、首页无脚注、组内图标一致、24dp 弹层起点、语义组名与窄屏长标题例外。没有把普通 48/56dp 尺寸或设备验收要求取消。

HLG 已读取 registry、索引与匹配的原始交接事实，按任务书不追加 handover。PDEC 当前核验 approved、valid=true、execution_ready=true、drift=[]，未改契约。未执行 commit/push/tag/PR/release、设备/模拟器操作；未读取或修改禁止目录、签名材料或 local.properties；未改批次二生产代码、存储键与默认值；未执行 --stop 或结束其它 Gradle/Kotlin 进程。

Agent 路由：主控施工、集成、逐图目视及验收；review_redesign_r2 独立只读审阅并串行复核修正。主控复核：源码/快照差异、资源、测试改写、全部命令输出、当前截图哈希与 APK。五个设备用例及新增布局用例的运行结果仍需主控在其设备上确认，报告没有代写通过结论。
