# 任务书：设置页视觉重设计（令牌、通用设置组件与全部设置页）

## 1. 背景与目标

Nexara 是 Android 原生 AI 客户端，使用 Kotlin、Jetpack Compose 和 Material 3，模块为 `native-ui/app`。当前目录是一个独立的 git worktree。

本任务把全部设置页（含二级、三级页面、设置类底部弹层和会话设置弹层）改为商业应用级别的信息密度与版式，具体要求是：

- 每个设置项只占一行，控件与标题同行，并且垂直居中；
- 数值设置统一为“值 + 箭头”行，点击后打开底部选择弹层；小范围整数改用同行的紧凑步进器；
- 说明文字不再撑大分组，详细解释移到分组脚注。

**权威规格**：`docs/superpowers/specs/2026-10-17-nexara-settings-chat-redesign.md`（下称“规格”）。

- 本任务实现规格的第 1–4 节、第 6 节、第 7 节“批次一”一行、第 8 节中与设置相关的测试，以及第 9.1、9.2 节的验收条件。
- 规格第 5 节（会话界面）不在本任务范围，见第 5 节“边界”。
- 规格中的尺寸、字号和规则都是硬性要求。规格与本任务书冲突时以本任务书为准。

## 2. 已验证的现状

所有路径相对于 `native-ui/app/src/main/java/com/promenar/nexara/`。

### 2.1 字体与间距

- **`ui/theme/Type.kt`**：`NexaraTypography` 的取值如下，与规格 2.1 节不一致，行高偏大是分组臃肿的原因之一。
  - `bodyLarge` 17/28
  - `bodyMedium` 15/26
  - `bodySmall` 14/24
  - `labelMedium` 13/18
- **`ui/common/NexaraSettingsPageLayout.kt`**：在页面内局部覆盖了 typography，取值为 `titleLarge` 22sp、`bodyLarge` 16sp、`bodyMedium` 14sp、`labelSmall` 等。
  - 覆盖必须与规格 2.1 节对齐。
  - 全局令牌对齐后，删除这些冗余覆盖或使其与全局一致；`SettingsVisualPrimitivesContractTest` 断言了这些源码片段，需要同步更新。
- **`ui/theme/Spacing.kt`**：`NexaraSpacing` 现有 XSmall 4、Small 8、Medium 12、Large 16、XLarge 24、XXLarge 32、ScreenHorizontal 16、MinimumTouchTarget 48。

### 2.2 设置组件（`ui/common/`）

- **`NexaraSettingsItem.kt`**：基于 M3 `ListItem`。
  - 两行时默认高 72dp。
  - 副标题使用 `bodyMedium`，且不限行数。
- **`SettingsToggle.kt`**：自定义 Row。
  - 有说明时最小高 72dp。
  - 说明不限行数。
- **`NexaraStepperSetting.kt`**：数值放在加减按钮上方的独立 Column 中（`verticalAlignment = Top`），因此看起来像按钮单独占了一行。这正是用户指出的错位问题。
- **`NexaraSettingsSection.kt`**：由标题加 `BettboxListGroup`（圆角 20dp，`surfaceContainerLow`）组成，没有脚注参数。
- **`NexaraSlider.kt`**：提供 `NexaraSlider` / `NexaraSliderInt`。

### 2.3 数值控件调用点

文件内行号可能已轻微漂移，以 grep 结果为准。

**NexaraStepperSetting**

| 调用点 | 当前用途 |
| --- | --- |
| `ui/settings/AgentRuntimeSettingsCard.kt:88` | `LoopLimitCard` |
| `ui/settings/AgentRuntimeSettingsCard.kt:97` | Token 上限，步长 10,000，范围 0–10,000,000 |
| `ui/settings/AgentRuntimeSettingsCard.kt` 约 :106 | 重复上限 2–20 |
| `ui/settings/SkillsScreen.kt:636` | `LoopLimitCard`：轮次 1–100 |
| `ui/chat/SessionSettingsSheet.kt:1016` | 自动压缩阈值 50–95%，步长 5 |
| `ui/chat/SessionSettingsSheet.kt:1024` | 保留最近轮次 1–20 |

`LoopLimitCard` 下方另有一个独立的“不限”开关：开启时写入 0，关闭时写入 50；偏好键为 `loop_limit`，默认值 50。

**NexaraSlider / NexaraSliderInt**

| 文件 | 调用点 |
| --- | --- |
| `ui/settings/ThemeScreen.kt` | :156，文字缩放 0.8–1.4，步数 5 |
| `ui/settings/SkillsScreen.kt` | :1046，搜索结果数 1–20 |
| `ui/chat/SessionSettingsSheet.kt` | 流式超时 30–300 秒（:464）、Top P、最大输出 Token 0–8192（:548 附近）、Top K、三种惩罚系数、字号 10–18（:965）；温度等参数位于 ParamsPanel 起始部分 |
| `ui/rag/AdvancedRetrievalScreen.kt` | :396 |
| `ui/rag/GlobalRagConfigScreen.kt` | :198、:225 |
| `ui/rag/RagAdvancedScreen.kt` | :288 |
| `ui/hub/AgentAdvancedRetrievalScreen.kt` | :482 |
| `ui/hub/AgentRagConfigScreen.kt` | :295 |

### 2.4 会话设置弹层（`ui/chat/SessionSettingsSheet.kt`）

- **标签**：`sheet_tab_model` / `sheet_tab_thinking` / `sheet_tab_tools` / `sheet_tab_settings`，中文依次为“模型 / 参数 / 工具 / 设置”。
  - 对应面板依次为 `ModelPanel`、`ParamsPanel`、`ToolsPanel`、`SettingsPanel`。
- **ParamsPanel 与 SettingsPanel 的写法**：
  - 分组使用“`HorizontalDivider` + 粗体 `labelMedium` 小标题”；
  - 每个参数使用“`titleSmall` 标题独占一行 + 下一行滑块与数值”。
- **SettingsPanel 的“检索”分组**：使用私有的 `ToolToggleRow`，样式与其它分组不一致。
- **`SessionContextSettingsSection`**：
  - 内部有一段长说明 `Text`，挂在分组容器内；
  - 已使用 testTag `session_context_management`、`session_context_compact_now`。

### 2.5 直接使用 `ListItem(` 的设置类文件

| 文件 | 调用次数 |
| --- | --- |
| `ui/settings/BackupSettingsScreen.kt` | 5 |
| `ui/settings/LocalModelsScreen.kt` | 4 |
| `ui/hub/AgentAdvancedRetrievalScreen.kt` | 4 |
| `ui/settings/TokenUsageScreen.kt` | 3 |
| `ui/rag/RagAdvancedScreen.kt` | 3 |
| `ui/rag/GlobalRagConfigScreen.kt` | 3 |
| `ui/settings/ProviderListScreen.kt` | 2 |
| `ui/hub/UserSettingsHomeScreen.kt` | 2 |

这些调用中凡属设置行的，都改用重写后的 `NexaraSettingsItem` 或新组件。

### 2.6 既有测试

`app/src/test/.../ui/common/` 和 `ui/settings/` 下有多份“读取源码字符串”式的视觉契约测试，会断言 `ListItem(`、`MaterialTheme.typography.bodyMedium` 等源码片段：

- `SettingsVisualPrimitivesContractTest`
- `BettboxVisualContractTest`
- `ManagementMaterialContractTest`
- `Md3ConvergencePrimitivesContractTest`
- `SettingsAiAppearanceVisualContractTest`
- `UserSettingsHomeScreenContractTest`

截图测试位于 `app/src/screenshotTest/kotlin/`，包括 `SettingsVisualScreenshotTest`、`ContextCompactionScreenshotTest` 和 `ReleasePreviewScreenshotTest`。

### 2.7 其它

- 仓库根有静态门禁 `scripts/ci/check-text-wrapping.py`，用于检查文本换行策略。
- JVM 测试已使用 Robolectric 4.11.1。`androidx.compose.ui:ui-test-junit4` 目前只用于 `androidTestImplementation`，由 compose BOM 2026.06.00 管理版本。

## 3. 需求

### 3.1 令牌

- 按规格 2.1 节修改 `NexaraTypography`。
- 按规格 2.2 节在 `NexaraSpacing` 中新增令牌。
- `NexaraSettingsPageLayout` 中的局部 typography 覆盖与全局令牌对齐。
- 字体变化会波及知识库、DocEditor 等非本任务页面。这些页面只需保证不出现裁切或重叠，不做重设计。

### 3.2 通用组件

按规格 3.2 节实现：

- **`NexaraSettingsItem`（重写）**：
  - 改为自定义 Row，不再使用 `ListItem`；
  - 保持现有参数签名，以便调用方无需修改；
  - 副标题最多 1 行，超出时截断。
- **`SettingsToggle`**：与 `NexaraSettingsItem` 共用同一个私有行布局（例如 `NexaraSettingsRowLayout`），保证高度、内边距和对齐完全一致。
- **`NexaraSettingsSection`**：新增 `footer: String? = null` 参数。
- **新增组件**：`NexaraValueRow`、`NexaraValuePickerSheet`（含离散与连续两种模式）、`NumericSettingSpec`（纯 Kotlin，与 Compose 解耦，放在 `ui/common/` 或其子包）、`NexaraInlineStepper`。
- **`NexaraStepperSetting`**：改为“设置行 + 行尾 `NexaraInlineStepper`”，删除数值叠在按钮上方的布局。
- **行布局规则**：
  - 前导图标列：图标 20dp，与文字间距 16dp，文字起点为 52dp；
  - 无图标时，文字起点为 16dp；
  - 同一分组内只要有一行带图标，其余无图标的行都提供 `leadingSpacer` 参数，留出 36dp 空位。

### 3.3 控件迁移

**迁移规则**：按规格 3.3 节的表格，逐一迁移第 2.3 节列出的全部调用点，以及迁移过程中发现的其它数值控件。

**迁移完成后的检查**：

- 设置页正文（不含 `NexaraValuePickerSheet` 内部）不再出现 `NexaraSlider(` 和 `NexaraSliderInt(`；
- 报告需附一张映射表，列出每个调用点的迁移结果：原控件 → 新控件、范围、预设、自定义范围、存储键及值语义。

**具体取值**：以规格 4.2–4.5 节为准。其中：

- **工具设置**：
  - 最大轮次和 Token 上限改用值行；
  - 重复上限改用行内步进器；
  - 搜索结果数改用行内步进器；
  - 删除“不限”独立开关，改为预设中的“不限”，写入值为 0，与原语义一致。
- **主题**：文字缩放改用值行，预设 80%–140%，每 10% 一档。写入值与原滑块的 Float 语义一致；原滑块步数 5 对应的档位为 0.8、0.9、…、1.4，取值必须落在这些档位上。
- **检索设置五页**（`GlobalRagConfigScreen`、`RagAdvancedScreen`、`AdvancedRetrievalScreen`、`AgentRagConfigScreen`、`AgentAdvancedRetrievalScreen`）：
  - 预设值以原滑块的范围、步数和默认值为依据制定；
  - 每个控件的预设清单写进报告。
- **会话设置弹层**：
  - 第四个标签改为“上下文”/“Context”，键名不变，其余标签名不变；
  - 参数面板中的思考强度保持现有卡片式单选，其余推理参数改为值行；
  - 上下文面板依次为“上下文管理 / 检索 / 长期记忆 / 显示”四组，统一使用 `NexaraSettingsSection`，用 `SettingsToggle` 替换 `ToolToggleRow`；
  - `SessionContextSettingsSection` 中的长说明移到脚注。

**值语义**：任何设置的存储键、范围、默认值和取值语义都保持不变。现有预设如果无法表达原默认值，必须把默认值加入预设，并标注“（默认）”。

### 3.4 逐页整理

对以下文件逐页应用规格 3.1 节的七条版式规则和 3.4 节的文案规则：

- **`ui/settings/` 下全部 Screen 与 Card**：`SkillsScreen`、`AgentRuntimeSettingsCard`、`ScriptRuntimeSettingsCard`、`TermuxSettingsScreen`、`SshSettingsScreen`、`BackupSettingsScreen`、`ThemeScreen`、`ProviderListScreen`、`ProviderFormScreen`、`ProviderModelsScreen`、`DefaultModelsScreen`、`LocalModelsScreen`、`TokenUsageScreen`、`AgentSkillsSection`、`ModelEditorSheet`、`TavilySecretEditor` 等；
- **`ui/hub/` 下的设置类页面**：`UserSettingsHomeScreen`、`AgentEditScreen`、`AgentRagConfigScreen`、`AgentAdvancedRetrievalScreen`；
- **`ui/rag/` 下的配置页**：`GlobalRagConfigScreen`、`RagAdvancedScreen`、`AdvancedRetrievalScreen`；
- **`ui/chat/SessionSettingsSheet.kt`**。

整理要求：

- **副标题文案**：
  - 中文不超过 16 个汉字，英文不超过 36 个字符；
  - 超出部分改写；
  - 被删掉的解释移入该分组的 `footer` 或选择弹层的说明，信息不得丢失；
  - 中英文资源同步修改。
- **强调按钮**：每屏最多 1 个实心或色调强调按钮，其余改为 `TextButton` 或设置行。Termux 页唯一的主按钮是“运行诊断测试”。
- **危险操作**（如“重置授权”）：做成设置行，标题使用 `error` 色。
- **说明文字**：分组容器内不再放置独立的长说明 `Text`。

### 3.5 布局测试

把 BOM 管理的 `androidx.compose.ui:ui-test-junit4` 与 `ui-test-manifest` 加入 `testImplementation`，不指定显式版本。这是项目中已有的工件，不算新依赖。

在 Robolectric 下编写 Compose 布局测试，必要时使用 `@Config(sdk = [34])`。断言内容如下：

1. 单行 `NexaraSettingsItem`、`SettingsToggle`、`NexaraValueRow` 高 48dp；带副标题的同类行高 56dp。容差 0.5dp。
2. 以上各行的尾部控件、`NexaraInlineStepper` 胶囊与标题文字块垂直居中，中心线偏差不超过 1dp。
3. 副标题超长时只显示 1 行。
4. 同一分组内，有图标的行与带 `leadingSpacer` 的无图标行文字起点相同。
5. `NexaraInlineStepper` 高 36dp，到达边界时对应按钮禁用。
6. `NexaraValuePickerSheet`：
   - 离散模式下，点击预设后回调写入对应的值；
   - 自定义输入超出范围时显示错误提示，并且不提交。

如果 Robolectric 无法运行 Compose 测试，把同样的断言写成 `androidTest` 下的设备测试，并在报告中说明原因。

### 3.6 其它测试

- **`NumericSettingSpec` 单元测试**：覆盖解析、边界与夹取、不限值、步长对齐（Token 上限对齐 10,000）、小数格式化与精度、`selectedPreset`。
- **既有源码字符串契约测试**：按新契约改写为断言当前实现的有意义片段。只验证已废弃写法的断言直接删除，并在报告中逐条列出。
- **截图测试**：新增或更新规格第 9.2 节所列页面与组件的截图预览。每个页面的默认状态使用深色、中文、宽 411dp；工具设置页和设置首页另加浅色与英文。组件级截图覆盖：设置行（单行/两行/禁用）、值行、行内步进器（中间值/边界）、离散弹层（含自定义展开与错误）、连续弹层。
- **截图基线**：用 `updateSideloadDebugScreenshotTest` / `updatePlayDebugScreenshotTest` 更新后，逐张目视检查新基线，确认 G1–G9 成立。报告中列出新增、更新和删除的基线文件名。

## 4. 设计判断原则

规格未覆盖的情况按以下原则处理，并在报告中写明判断：

1. 控件与标题同行、垂直居中优先，其次才考虑信息完整。
2. 解释性文字放脚注，状态性文字放副标题。
3. 能少一个强调色就少一个。
4. 不确定的取值语义一律保持原样，不得为了视觉效果改变行为。

## 5. 边界

- **文件范围**：
  - 只修改本 worktree 内的文件；
  - 不得读取或修改 `secure_env/`、`artifacts/`、`.v2c/`、`.mimosa/`、`*.jks`、`*.keystore`、`native-ui/local.properties`。
- **会话界面**：
  - 不改动会话界面，即规格第 5 节的内容；
  - `ui/chat/` 下只允许修改 `SessionSettingsSheet.kt`；
  - 不修改 `ui/chat/PipelineBubble.kt`、`CompactStepRow.kt`、`ChatScreen.kt`、`ChatInlineComponents.kt`、`ToolInvocationPresentation.kt`；
  - 不修改 `ui/common/SmoothStreamContent.kt`、`MarkdownText.kt`、`ui/renderer/`。
- **依赖与数据**：
  - 除第 3.5 节允许的测试依赖外，不新增依赖；
  - 不修改 Room schema、偏好键、默认值或 `.pdec/`。
- **测试标签**：保留所有 `UiTags` 和 testTag；元素被替换时，把标签迁移到语义等价的新元素上。
- **git 与设备**：
  - 不运行 git commit / push；
  - 不追加 `.agent/handover.md`；
  - 不启动或操作模拟器与设备。
- **Gradle**：不得执行 `./gradlew --stop`，不得结束其它 Gradle 或 Kotlin 守护进程。
- **注释**：新增注释使用简体中文，数量与周边代码相称；不保留描述修改过程的注释。

## 6. 验收

在 `native-ui/` 下运行，全部通过：

```bash
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:lintSideloadDebug
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:validateSideloadDebugScreenshotTest :app:validatePlayDebugScreenshotTest
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" :app:assembleSideloadDebug :app:assemblePlayDebug
./gradlew --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=1g" -Pnexara.deviceE2e=true -Pnexara.deviceE2eAbi=arm64-v8a :app:assembleSideloadDeviceTest :app:assembleSideloadDeviceTestAndroidTest
```

在仓库根运行，全部通过：

```bash
python3 scripts/ci/check-text-wrapping.py
python3 scripts/ci/check-distribution-manifests.py
```

全部完成后，再执行一次静态核查：

```bash
grep -rn "NexaraSlider(\|NexaraSliderInt(" native-ui/app/src/main/java
```

结果中只允许出现 `ui/common/NexaraSlider.kt` 本身和选择弹层内部的调用。

DIA：在 `CHANGELOG.md` 的 `[Unreleased]` 下新增“设置页视觉重设计”条目。检查 `README.md`、`design-qa.md` 和 `docs/` 中描述设置页视觉或控件形式的段落，与当前实现不一致的同步修改，并在报告中列出改动的文档。

## 7. 报告

完成后写入 `.agent/tmp-agent-reports/report-redesign-batch1.md`，包含以下内容：

- **组件落点**：新增和重写的组件及其职责，引用 `文件:行号`。
- **控件迁移映射表**：见第 3.3 节。
- **文案改写清单**：每条列出原副标题 → 新副标题，以及移入的脚注；中英文都要列出。
- **强调按钮与危险操作调整清单**。
- **测试清单与结果**：布局测试、`NumericSettingSpec` 单元测试，以及被改写或删除的契约测试逐条说明。
- **截图基线清单**：新增、更新、删除的基线文件名，并逐页写明 G1–G9 的自检结论。
- **无障碍例外清单**。
- **第 6 节各条命令的结果**：给出最后几行输出。
- **未完成项、偏离规格的地方及原因、已知限制**。
