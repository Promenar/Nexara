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

## KG-A 执行结果

- 完成九类正确性修复：全量、手动与 JIT 共用稳定模型路由和 KG 设置；成功空图合法；结果区分解析失败、超时、取消与提供商错误；统一校验名称身份、端点、字段、权重与图规模。
- 手动建图进入持久文件引用队列，复用 PDF、DOCX、大文本解析和 16 MiB 文本预算；候选提交检查 hash、epoch、活动任务标识与删除屏障，事务切换图谱及 `kgExtractedAt`，保留已有向量。失败回滚且向 UI 传播；能力升级与终态重试更换活动任务标识。
- 断点校验源文件 hash、提取文本 hash、模型、提示词摘要和切块参数，删除时清理。JIT 使用明确超时并透传取消；容量 8 的后台队列承担缓存与归并，配置重建取消旧作用域，长期聊天消费者动态取得有效抽取器。
- 验证：当前工作树 `:app:testDebugUnitTest` 与 `:app:lintDebug` 联合运行成功，3039 项单元测试中 3014 项通过、25 项跳过，0 失败、0 错误；lint 0 错误、544 条警告、22 条提示。文本换行检查及 `git diff --check` 通过。原始实现上的 16 个业务红测、审阅补充的 5 个业务红测与超长名称身份回归均已观察到失败，最终回归通过；真实 PDF/DOCX、Room 事务、删除迟到回片与队列并发均有集成覆盖。
- 由未参与施工的原生只读 Agent 独立审阅，主控修正其有效发现并复核最终 diff、测试与 lint 证据。DIA 已同步 `CHANGELOG.md` 与 `docs/ARCHITECTURE_DESIGN.md`；README 无需调整。详细文件、位置、用例和执行参数见 `.agent/tmp-agent-reports/report-kg-a.md`。
- 无新增第三方依赖、数据库结构或迁移；未修改 Agent 循环、审批、前台服务、产品风味、CI 或渲染器。遵照 KG-A 任务书保留未提交工作树，未提交、推送或操作设备。后续集成时复核共享文件；真实提供商请求、设备进程重启与菜单交互列为设备验收建议，不将本地测试视为这些场景已验收。

## P1 执行结果

数据库版本改为 v20，版本链为 v18→v19（知识图谱实体与证据）→v20（外部挂载与快照），v19 schema 保持主线内容。

- 外部文件夹使用独立后端契约和 `/mnt/<别名>/...` 命名空间；Room 19→20 持久化全局/会话挂载及知识库快照来源。十个工作区工具共用路由，写入按 `expected_sha256` 校验，审批与执行持续绑定挂载身份。现有私有工作区的事务、日志和恢复实现未改写。
- SAF 使用批量列投影、持久授权和临时文档写入；失效授权标记不可用并允许同来源重新授权。侧载源集提供所有文件访问设置和共享存储真实路径后端；Play 源集仅保留拒绝能力的桩实现。真实路径逐级绑定目录句柄并拒绝符号链接，文件冲突保留可核查副本。
- 跨后端移动采用有界快照、复制校验、删除源及失败补偿；挂载回收区的内容上限为 32 MiB、100 项、7 天。空间不足默认拒绝删除，显式不可恢复删除必须逐次审批。文件面板提供浏览、文本预览、只读副本外开、恢复及知识库快照导入/来源修改提示。
- `/mnt` 成为虚拟保留名。历史私有 `mnt` 目录与 UUID 保留，用户可在文件面板重命名解除路径歧义；其后代通过原 UUID 访问时保持私有工作区路由。不同来源必须使用新的挂载身份，既有审批不能沿用。
- 验证命令、实际结果、逐项实现位置、完整文件清单和独立审阅闭环见 `.agent/tmp-agent-reports/report-p1-saf.md`。仪器化测试仅编译，真实 Termux、系统授权、提供商行为及文件描述符生命周期由主控按报告执行设备验收。
- 按 P1 任务书不提交、不推送、不修改 PDEC、不追加 handover、不操作模拟器或设备；交接事实保存在执行报告。DIA 已同步 README、CHANGELOG 和本计划。
- 最终验收：侧载 3,267 项、Play 3,243 项单元测试均为 0 失败/错误，各 25 项既有联网条件用例跳过；111 项截图全部通过，lint 为 0 错误、556 警告、24 提示。双 Debug APK 与侧载 DeviceTest/AndroidTest APK 编译成功，SAF 和真实路径设备测试类均实际纳入测试产物；清单、文本换行及 diff 门禁通过。DeviceTest 主 APK 为项目默认 x86_64，两个 Debug APK 为 arm64-v8a。一次既有 P0 审批恢复集成用例间歇失败已保留 XML，相同源码单类隔离 5 项通过、限制 Gradle 并行度后的完整六任务通过；未宣称定位或修复该间歇触发根因。

## KG-B 执行结果

- Room v19 采用全局 `(规范化名称, 规范化类型)` 实体、唯一有向关系与独立来源证据。名称、类型和关系执行 NFKC、空白归一与大小写折叠；来源删除仅清证据并事务回收无证据对象，关系置信度按当前范围的存活证据聚合。
- 文档候选沿用 hash、epoch、活动任务与删除准入。JIT 传递文档 ID/内容版本，缓存绑定完整片段内容、模型稳定 ID、提示词及预算；跨会话命中幂等登记证据。联合推断只登记实际消费的来源，任一输入失效时撤销整次推断，不影响独立来源。
- v18→19 以 500 行主键游标批次及 SQLite 映射表合并重复实体、重指向关系，保留各来源权重与 legacy。多批迁移测试处理 2501 个节点、2500 条边，本机 Robolectric 实测 663 ms；真实手机和十万行规模尚待实测。v18 原始抽取版本不可追溯，迁移 hash 只作数据库快照锚点，可能保留升级前已陈旧的图，重建后才取得可校验版本。
- 文件持久化已确认的 `kgStrategy`，回收恢复经持久队列真正重建 KG，普通文件不扩大抽取；双库合并丢弃非工作区历史派生 KG 并记录诊断，源数据可恢复。常规备份排除并清理新增 KG 表；数据载荷 v4 兼容 v1–v3 文件行且保持严格必需表校验。
- 无消费者的字符串 SQL 条件与 `getGraphData` 助手参数已删除；结构化种子实体查询、端点和证据索引支持后续 1–2 跳读取。已适配图谱页面、路径来源展示、文档/会话删除、备份与双库恢复；未实现 KG-C 聊天图检索。
- 验证：`:app:testDebugUnitTest :app:lintDebug` 联合运行通过，3065 项单元测试中 3040 项通过、25 项跳过，0 失败、0 错误；Lint 0 Error/Fatal、544 Warning、22 Hint。文本换行门禁及 `git diff --check` 通过。来源删除、并发、恢复意图、非工作区双库恢复、JIT 来源/缓存及字符串 SQL 缺陷有失败复现；迁移与持久队列恢复有真实 Room 集成覆盖。
- 未参与施工的原生只读 Agent 完成独立审阅，主控用失败回归封闭备份兼容/严格校验、完整大小写折叠、联合 JIT 活性/预算来源及来源权重问题，并核验最终 diff、schema identity 和检查结果。DIA 已同步 `docs/ARCHITECTURE.md`、`CHANGELOG.md`；README 无需调整。
- 按 KG-B 任务书保留未提交工作树，未执行 commit/push、未追加 handover、未操作设备、未新增第三方依赖。交接、完整文件清单、逐需求位置、命令、复现边界及真实旧库设备验证步骤见 `.agent/tmp-agent-reports/report-kg-b.md`。

## P3 执行结果与 Python 技术验证结论

- JavaScript 采用 QuickJS 1.0.15，两个发行渠道共用非导出的隔离 UID 服务与 Messenger 能力代理。`exec_js` 移除 WebView 路径，支持 `result`、最后表达式和异步能力；`run_script` 支持 `.js/.mjs`、参数数组与同根相对模块，保留统一 `ScriptEngine` 接口。
- 文件代理复用现有路径规则、挂载身份、只读和版本校验及原工作区技能；默认写入二次审批，删除始终独立审批或对应会话目标授权。网络默认关闭，设置页维护精确局域网白名单；DNS 固定、逐跳检查、无应用凭据继承。超时默认 30 秒/最大 300 秒，QuickJS 堆 64 MiB、结果 64 KiB，日志/文件/网络/IPC 有界。取消和资源超限终止隔离进程；阻塞入口与能力调用使用全局 4 槽调度器。
- Kotlin 编译器、Compose 和序列化插件对齐 2.4.10 以读取依赖元数据，应用语言级别保持 2.2；协程运行/测试库为 1.11.0。未改 AGP、SDK、数据库、知识图谱、SSH、外部存储后端或 PDEC。第三方来源与许可已登记。
- 最终本机验收：侧载 3333 项（3308 通过/25 跳过）、Play 3309 项（3284 通过/25 跳过）单测均零失败/错误；111 项截图通过；lint 0 Error/Fatal、554 Warning、24 Hint。Play Debug、ARM64 侧载 DeviceTest/AndroidTest、双渠道 ARM64 MinifiedTest R8 构建均成功；QuickJS JNI/绑定类消费端保留规则在两个最终 R8 配置和 mapping 中确认。文本换行、Debug/MinifiedTest 渠道清单、diff 门禁通过，8 个设备用例均实际编入测试 APK，但未运行。
- Python 同构 ARM64 最小 Debug APK 实测：空运行时相对底座增加 18.237 MiB；requests 配置为 18.697 MiB、numpy 为 27.909 MiB、pandas 为 39.734 MiB。三个包的目标 Android wheel 经过构建期安装与打包；未验证 Android import/运算。原版 AndroidPlatform 使用私有 files/cache/偏好及标准库解包，推断与隔离 UID 不适配；隔离初始化与冷启动因禁止设备操作而未测，不能视为失败或成功。建议当前产品不引入 Chaquopy，临时工程、验证代码与下载解释器均已删除。
- 原生独立只读 Agent 审阅 Binder、能力、挂载、网络、限额和生命周期；主控复核并修复阻塞 I/O 拖住 watchdog、入口超时、审批来源及超限分类问题。已进入公开文件后端的操作不保证原子取消，沿用原后端提交/回滚语义；没有越界修改后端。
- 完整改动清单、逐项实现位置/测试、原始数据、偏差与主控设备命令见 `.agent/tmp-agent-reports/report-p3-script.md`。DIA 已同步 README、CHANGELOG、架构、发行构建、许可和 registry；依任务书不提交推送、不追加 handover、不操作设备。真实隔离权限、Android JNI、进程死亡、设置交互与 minified 功能仍由主控按报告设备验收。
