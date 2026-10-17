# 任务书：设置页视觉重设计第一批——复核问题修正

## 1. 背景

Nexara 是 Android 原生 AI 客户端（Kotlin + Jetpack Compose + Material 3，模块 `native-ui/app`）。当前目录是独立 git worktree。工作树中已有设置页视觉重设计的未提交实现，实现依据为：

- 规格：`docs/superpowers/specs/2026-10-17-nexara-settings-chat-redesign.md`（下称“规格”）；
- 原任务书：`.agent/tmp-agent-reports/brief-redesign-batch1.md`；
- 实施报告：`.agent/tmp-agent-reports/report-redesign-batch1.md`。

主控在模拟器上运行了设备测试，并逐张检查了截图基线，发现下列问题。本任务在现有工作树上修正这些问题；原任务书的边界、验收命令与报告要求仍全部有效。

## 2. 设备测试失败（API 31 模拟器实测）

以下用例在当前工作树构建的 `SideloadDeviceTestAndroidTest` APK 上失败。主控已在实现前的基线版本上重跑过同一批用例，基线结果见第 2.2 节。

### 2.1 失败清单

| 用例 | 失败信息 |
| --- | --- |
| `ProviderFormInteractionTest#allCloudFieldsAndActionsRemainReachableAt360By800WithTwoXFontScale` | 第 326 行：`Expected at most 1 node but found 2 nodes that satisfy (Text contains 'Presets')` |
| `ProviderFormInteractionTest#cloudActionsRemainReachableInLandscapeWithTwoXFontScale` | 第 363 行：`Can't scroll to index 2, it is out of bounds [0, 2)` |
| `ProviderFormInteractionTest#localOnboardingFailureKeepsRecoveryActionReachable` | 第 298 行：`performScrollTo()` 失败（目标为 `local_models_title` 文本，并断言高度至少 48dp 且可点击） |
| `ProviderModelsAccessibilityTest#modelSummaryRowShowsCompactInfoAndNoExpandedInlineControlsAt2xFontScale` | 第 165 行：`providerModelsToggleAction(modelId)` 节点高度 32dp，期望至少 48dp |
| `UserSettingsAccessibilityTest#sharedSettingsItemLongTitleReflowsInsteadOfClipping` | 共享设置行的长标题测试：实际高度 48dp，期望至少 72dp（长标题应换行增高而不是被裁切或截断） |

`SettingsLayoutTest` 的 12 个用例、`ThemeSettingsInteractionTest`、`RagSettingsAccessibilityTest` 以及其余改写过的设备测试均已在设备上通过。

### 2.2 基线对照

在基线（19060788）构建上，上表 5 个用例全部通过，因此都是本批引入的回归。

完整设备测试套件（448 个用例）在这台模拟器上另有 36 个失败，它们在基线上同样失败，与本批无关，不在本任务范围内；`GenerationForegroundServiceColdStartDeviceTest` 的 2 个用例只在完整套件中偶发失败，单独运行均通过，同样不在范围内。

### 2.3 修正要求

- 基线上通过、当前失败的用例都视为本批引入的回归。按根因修复生产代码，不得通过放宽断言、删除用例或降低触控尺寸来让测试通过。
- 只有在原断言针对的是规格明确废弃的结构（例如某个被删除的列表项序号）时，才可改写测试；改写后的测试必须继续验证原测试的用户意图（可达性、可点击、48dp 触控区、单一节点等），并在报告中逐条说明。
- 例如：模型列表启用开关的可点击节点必须至少 48dp 高；提供商表单在 360×800、2 倍字号与横屏 2 倍字号下所有字段和操作仍可滚动到达；“预设”文本不得同时出现在两个可见节点中（分组标题与行标题、脚注重复时，改为语义不同的文字）。

## 3. 视觉问题

### 3.1 脚注排版

- 脚注目前继承界面换行策略 `NexaraUiLineBreak`（Balanced），多句文字被平衡成约半屏宽的窄栏。脚注改用阅读换行（`NexaraReadingLineBreak`），文字铺满分组宽度。
- 每个分组脚注在 411dp 宽、默认字号下不超过 3 行。不得把多个设置项的说明拼成一段文字墙。
- 超出时按以下顺序处理：
  1. 删除与副标题、标题或同组其它句子重复的句子；
  2. 值行的解释移到其选择弹层头部的说明行；
  3. 步进器、开关、操作行的解释保留在脚注，但每项压缩为一句；
  4. 仍超出的安全或风险说明保留原文，并在报告中列出该例外。
- 设置首页是导航页，不使用脚注。删除首页各分组的全部脚注；入口的详细说明由目标页面承担。

### 3.2 图标列与缩进

- 不允许出现“没有图标却缩进到 52dp”的文字行。现在“执行轮次与预算”“上下文管理”“Termux 集成与状态”等分组中，无图标行因 `leadingSpacer` 留下空白缩进，看起来像错位。
- 同一分组内的规则改为：要么所有行都有图标，要么所有行都没有图标。
  - 多数行有图标时，为其余行补上语义合适的 Material Rounded 图标（例如轮次 `Repeat`、Token `DataUsage`、重复上限 `Block`、压缩阈值 `Speed`、保留轮次 `History`、版本 `Info`、权限 `VerifiedUser`）；
  - 多数行没有图标时，删除少数行的图标。
- 删除 `leadingSpacer` 在生产页面中的全部用法；组件参数可保留以兼容，但不再使用。

### 3.3 选择弹层

- 弹层标题必须与触发它的设置行标题完全一致（例如行标题“执行循环限制”，弹层标题也是“执行循环限制”，不能是“最大轮次”）。
- 弹层头部的说明行（`bodySmall`）承接该项原有的解释（见 3.1 节第 2 步）。
- 弹层标题、说明、预设项文字、“自定义…”和输入框左缘使用同一起点（距弹层边缘 24dp）；当前预设项文字比标题多缩进约 18dp。

### 3.4 标题重复与用词

- 分组标题与组内唯一一行标题相同时（如“执行模式”分组内只有“执行模式”一行、“结果数量”分组内只有“结果数量”一行），分组标题改为上位概念（如“工具审批”“搜索结果”），或在同一页内与相邻分组合并。逐页排查此类重复。
- 会话上下文脚注中“达到阈值后先折叠……”与“折叠较早的工具结果并总结历史……”两句语义重复，只保留一句。
- 中文界面中的 “token” 统一为 “Token”。

## 4. 截图

修正后重新生成并逐张目视检查受影响的截图基线。报告中附加一节“复核问题修正”，逐条对应本任务书第 2、3 节说明修改位置（`文件:行号`）和对应截图文件名。

## 5. 边界与验收

- 边界同原任务书第 5 节：不提交、不推送、不追加 handover、不操作设备、不改批次二文件、不改存储键与默认值、不执行 `./gradlew --stop`。
- 验收命令同原任务书第 6 节，全部通过；另外运行：

```bash
grep -rn "leadingSpacer = true" native-ui/app/src/main/java
```

结果应为空。

- 报告追加写入 `.agent/tmp-agent-reports/report-redesign-batch1.md` 末尾的新章节“复核问题修正”，不改写已有章节以外的历史内容，只更新与修正直接矛盾的条目（如脚注落点表）。
