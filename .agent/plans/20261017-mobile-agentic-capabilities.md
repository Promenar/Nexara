# 移动端 Agent 能力增强与知识图谱修复（2026-10-17）

## 目标

1. 让手机端 Agent 具备：原生 SSH、SAF 授权的本地文件读写、无固定上限的工具循环、隔离沙箱内可联网的原生脚本执行、Termux 本地终端接入。
2. 修复知识图谱（KG）的正确性问题，并让持久图谱进入聊天检索。

## 非目标

- 不做 Android 端完整 IDE；完整 Linux 环境交给 Termux。
- 不在主进程内运行任何用户 / 模型提供的脚本。
- 不把凭据、私钥、密码交给模型；模型只见主机别名与脱敏状态。

## 已确定的产品决定

| 编号 | 决定 |
|---|---|
| D1 | 持久知识图谱进入聊天检索（KG-C）。 |
| D2 | 以 Gradle 产品风味区分 `play` 与 `sideload` 两个发行版本；“所有文件访问”只存在于 `sideload`，代码共享，`play` 的清单不声明该权限。 |
| D3 | Python 不预先承诺；先完成 Termux（SSH 路线）与 QuickJS，再以技术验证判定 Chaquopy 的增量价值，安装包增加数十 MB 可接受。 |
| D4 | 无限制轮次模式允许与全自动审批组合，由用户选择；界面明确提示风险，预算与循环检测仍默认开启且可调。 |

## 现状事实（2026-10-17，HEAD 06c3c5b7）

- 工具注册见 `NexaraApplication.kt` 的 `presetSkillRegistry`；工作区工具只作用于应用私有目录；`exec_js` 为一次性 WebView，网络与文件全关、5 秒超时。
- `infra/fs/SafImportHandler.kt` 存在但无任何调用方。
- 工具预算：轮次 `coerceIn(1, 100)`（`DefaultChatGenerationRuntime.kt` 的 `toolBudget`），单次生成调用上限 `MAX_TOOL_CALLS_PER_GENERATION = 200`。
- 跨轮次已有自动摘要：消息超出活动窗口时，`DefaultChatGenerationRuntime` 的 `summarize` 经 `SummaryManager` 更新会话 `summary`；单轮之内没有压缩，工具结果持续累积。
- `ToolRisk.PROCESS` 已定义但无工具使用；MCP 工具默认风险 `UNKNOWN`。
- MCP 仅支持 Streamable HTTP 且强制 HTTPS；全局 `cleartextTrafficPermitted="false"`。
- HTML 预览的 `RichContentWebViewPool` 开启 JS 且未限制网络。
- 当前无产品风味，构建类型为 debug / deviceTest / minifiedTest / release；CI 与脚本引用 `testDebugUnitTest`、`assembleDebug`、`lintDebug`、`assembleRelease`、`assembleDeviceTest`、`assembleMinifiedTest`。
- Termux 清单注册 `TermuxDocumentsProvider`，其主目录可经 SAF 授权访问。

## 工作包

### P0 执行底座

1. 启用 `ToolRisk.PROCESS`；审批卡展示执行目标（主机 / 路径 / 命令）；新增“本会话内对此目标始终允许”授权。
2. 无限制模式：轮次可设“不限”，并配套：
   - 单轮上下文压缩（早期工具结果折叠为摘要，摘要可持久化到会话）；
   - 大输出落工作区文件，模型只收头尾与路径；
   - 循环检测（同工具同参数重复 N 次暂停）；
   - 单任务 token / 费用预算与实时计数；
   - 前台通知进度与随时停止。
   与全自动审批组合时给出一次性明确风险确认（D4）。
3. 核实并收紧 HTML 预览 WebView 的网络与文件访问。
4. 产品风味 `distribution { play, sideload }`（D2）：同步 CI、发行脚本、验证账本与文档中的任务名。

验收：预算与循环检测单测；上下文压缩在长工具链下的回归测试；两个风味均可构建、`play` 产物清单不含 `MANAGE_EXTERNAL_STORAGE`。

### P1 SAF 工作区

- `WorkspaceBackend` 抽象：`Internal`（现有）、`SafTree`、`RealPath`（仅 `sideload`，所有文件访问）。
- SAF 写入使用乐观并发（大小 + 修改时间 + 哈希前置条件）；目录用 `DocumentsContract` 批量查询并在 Room 缓存索引；RAG 增量扫描。
- 删除：应用侧回收副本或强制 DELETE 级审批。
- 界面说明系统限制：Android 11+ 不可选内部存储根、可靠 SD 卡根、`Download`，不可选 `Android/data`、`Android/obb` 内文件；持久授权上限 512。
- 支持挂载 Termux 主目录。

验收：后端契约测试（三种实现共用）；冲突写入被拒；设备上授权、重启后恢复、撤销授权后的降级。

### P2 原生 SSH

- 技术验证在 sshj 与 mwiede/jsch 间选型：Ed25519 / rsa-sha2 / ECDSA、R8、known_hosts、PTY、SFTP、保活。
- 主机配置：密钥（应用内生成，Android Keystore 包裹）或密码（现有密钥存储）；首次连接指纹确认，变化即拦截。
- 工具：`ssh_exec`（非交互、超时、输出上限）、`sftp_list` / `sftp_read` / `sftp_write`；第二阶段 `ssh_session`（常驻会话）、用户终端界面、端口转发。
- Termux 路线 C：Termux 运行 sshd，Nexara 连接 `localhost:8022`。

验收：对测试 sshd 的集成测试（认证方式、指纹变更拦截、超时、截断）；凭据不出现在提示词、日志与工具结果中。

### P3 原生脚本运行时

- `isolatedProcess` 服务承载引擎；文件经工作区后端、网络经主进程代理（复用 `web_fetch` 公网策略，可加用户局域网白名单）；超时、内存、输出上限，超时结束进程。
- JS：QuickJS 替换 WebView 方案，提供 `fetch` / `fs` / `console`；`run_script(path)` 执行工作区脚本。
- Python（D3）：Chaquopy 技术验证——隔离进程能否初始化、实测包体增量、可用库范围；不满足隔离要求则不引入，交给 Termux。

验收：沙箱逃逸用例（读应用私有文件、访问内网地址、超时、超内存）全部被拒。

### P4 Termux 接入

- 路线 C（SSH）为首选，依赖 P2。
- 路线 A：`termux_exec` 通过 RUN_COMMAND 后台执行；需 Termux 0.109+、`allow-external-apps=true`、`com.termux.permission.RUN_COMMAND` 授权与 `<queries>` 包可见性；输出合计截断 100KB。
- 路线 B（本机 MCP，环回 HTTP + 令牌）列为高级选项。
- 引导页说明 Android 12+ 子进程限制可能结束 Termux 后台进程，以及唤醒锁设置。

### KG 修复

- KG-A 正确性（无表结构变更）：KG 模型按稳定 ID 路由到所属 Provider 并只发送远端模型 ID；成功空图合法化；手动抽取进入持久队列并事务提交；断点身份含内容版本 / 模型 / 提示词摘要 / 切块参数；JIT 超时与取消透传；入库、提示词、展示共用同一份已校验图；手动抽取复用文档解析器；配置变更使抽取器失效。
- KG-B 数据模型（Room 迁移）：实体与来源证据分表，按作用域唯一约束与事务 upsert；JIT 记录来源文档；删除只清证据，无存活引用才删实体；修正双库合并的文档 ID 映射；持久化建图意图与完成状态。
- KG-C 图检索（D1）：从查询与 RAG 命中选种子实体，1–2 跳扩展，节点 / 边 / token 预算，与向量结果融合；作用域与会话 / Agent 开关一致。
- KG-D：文档级调用 / token / 时长预算；大图按需加载、布局移出主线程、预建端点索引、线段视口相交；清理无消费者接口；按实现更正 PRD / ADR-018 / ARCHITECTURE_DESIGN。

## 顺序与并行

1. P0。
2. P1、P2、KG-A 并行（写入范围不重叠，各自 worktree）。
3. P4（C、A）、KG-B。
4. P3 JS、KG-C。
5. P3 Python 技术验证与取舍、KG-D。

SSH、脚本沙箱、KG-B 迁移完成后由未参与施工者独立审阅。

## 通用验收命令

```bash
cd native-ui && ./gradlew :app:testDebugUnitTest
cd native-ui && ./gradlew :app:lintDebug
python3 scripts/ci/check-text-wrapping.py
```

引入产品风味后，任务名随风味更新，以届时 CI 为准。

## P0 运行时执行结果

- 实施范围以 `.agent/tmp-agent-reports/brief-p0-runtime.md` 为准：目标授权、不限工具循环、长任务控制；HTML WebView、产品风味、SSH、Termux 与 KG 由其它工作包负责。
- 目标集中提取为完整 JSON 元组；工作区 UUID 按当前会话解析路径，移动绑定源与目标，MCP 绑定服务器与远端工具名，PROCESS 绑定主机与完整命令。显示清除控制字符并截断，授权键保持完整。会话授权保存在应用私有目录；显式审批在恢复时复核目标，使用持久授权的免审批调用在实际执行前复核目标及授权；可在会话工具设置撤销，删除会话时清理。
- `loop_limit` 保持 INT；0 为“不限”，旧 1–100 值保持有效。不限模式取消轮数及调用数固定上限；token 预算默认关闭，连续重复检测默认 3 次。工具设置提供预算、压缩比例、最近完整轮数和检测阈值；首次不限全自动组合显式确认，运行中切入自动模式同样检查。
- 请求压缩默认在已知模型上下文的 70% 触发，保留最近 3 轮调用与完整结果；仅替换早期结果正文，保持全部调用 ID、结果、签名和顺序。摘要按配置模型的所属提供商精确路由，失败或超时保留头尾；正文与 UI 历史保持完整。主请求、摘要及无 Usage 估算均纳入任务预算，JSON 用量元数据可跨审批续算，无 Room 表结构迁移。
- 超过 16,000 字符的结果完整保存到应用私有、按会话隔离的存储，不进入用户工作区。`read_tool_output` 为 SAFE_READ，按 UTF-16 字符偏移读取，默认 4,000、最多 8,000 字符；总容量 64 MiB，按首次写入顺序淘汰；超容量或 IO 失败明确降级，不提供虚假输出 ID。
- 聊天页与前台通知显示轮次、调用数、token 及估算标记；通知停止复用现有 coordinator 取消。重复调用暂停可继续或停止；取消保留已有结果的调用配对。私有授权与输出分别清理，一类失败不会跳过另一类。
- 验证命令、实际结果、截图基线与设备待验证场景见 `.agent/tmp-agent-reports/report-p0-runtime.md`。不执行提交、推送、设备或模拟器操作。未知模型上下文需在模型配置中补齐窗口；无法压缩的系统提示、用户消息、调用参数及保留轮次可能仍超出窗口。收尾请求也消耗 token，预算为进入收尾的门槛。

## P0 发行风味与 WebView 执行结果

执行日期：2026-10-17。范围为 `brief-p0-flavors.md` 的发行风味和富内容 WebView 工作包；工具执行底座、审批、上下文、前台服务及其它 P1/P2/KG 工作包未在此执行。成果位于 `feat/p0-distribution-flavors` 的未提交工作树，基准为 `57084b8c8d2051feaa07cf717eecd0de1c35eaa6`。

- 已建立共享 applicationId 的 `play`、`sideload`；仅侧载专属清单声明 `MANAGE_EXTERNAL_STORAGE`，集中能力入口为 `DistributionCapabilities`，设置页导出诊断首行显示渠道、能力和版本。未实现所有文件访问业务。
- 已适配日常 CI、目录刷新、两份发行 APK、独立校验和、按渠道归档的 mapping、双渠道 API35/36 冷安装矩阵和侧载 v0.1 升级入口；签名注入方式和发布授权门禁保持。`mainactivity-e2e` 固定匹配侧载，独立黑盒夹具不引入发行风味。当前任务和产物路径以 `docs/distribution-builds.md` 为准。
- 富内容使用受控 HTTPS 打包资源、内存文档和公网 HTTPS 子资源代理。关闭文件/内容访问、默认 WebView 联网及原生桥接；检查实际 DNS 结果和每次重定向，拒绝非公网与非预期协议。链接直接阻止。复用现有 OkHttp，无新增 AndroidX WebKit 依赖；内嵌图片/字体上限 4 MiB，公网单资源 16 MiB，最多六次请求。保留本地图表、公式、知识图谱模板资产、Kroki PlantUML 和原生 PNG 导出路径。
- 双风味 Debug JVM 各 3011 项：2986 通过、25 条件跳过、0 失败/错误；侧载 Lint 为 0 Error/Fatal、545 Warning、22 Hint。双 Debug、双 MinifiedTest、侧载 DeviceTest/AndroidTest 和 mainactivity-e2e APK 编译通过；Debug/MinifiedTest 的合并清单及四 APK 的权限差异和包身份均已核验，两个 MinifiedTest mapping 非空。
- 双风味截图各 111 项全部通过；共用原 `src/screenshotTestDebug/reference`，112 张参考 PNG 的 SHA-256 全部未变。一个既有未被当前预览引用的参考图保留。截图插件在变体注册时覆盖早期目录配置，现于项目配置完成后绑定共用基线。
- 发行工作流契约 40 项、合并清单脚本测试 3 项、模型目录测试 64 项、换行检查测试 5 项，以及设备/黑盒 Bash 契约、文本换行门禁、Shell 语法、三份 YAML 和 PDEC 校验全部通过。沙箱禁止 Robolectric 写用户目录下载锁，使用已有 SDK 缓存的离线测试配置解决；未跳过受影响用例。
- 独立只读审阅的初始文档、内嵌资源、并发快照、ECharts 安装时序、严格百分号解码与截图路径问题均已关闭；主 Agent 复核实际命令、XML、APK、mapping、截图哈希和 diff。

验收边界：遵守任务书，未执行签名 Release 构建、GitHub Actions、设备/模拟器运行或 git commit/push。WebView 在真实 Android WebView 中的功能、安全和 PNG 导出尚待主控设备验收，不能由 JVM/编译或 Compose 截图替代。计划中的其它 P0 能力不因此标记完成。

DIA 已同步 README、AGENTS、CHANGELOG、registry、发行说明、构建说明、图标任务说明和 PDEC。完整文件清单、逐项需求位置、命令/结果、任务路径对照和主控签名/设备步骤见 `.agent/tmp-agent-reports/report-p0-flavors.md`；HLG 通过标准 append 追加交接记录。
