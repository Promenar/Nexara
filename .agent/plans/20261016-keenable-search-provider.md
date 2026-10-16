# Keenable 搜索提供商接入计划

## 目标与边界

在设置 → 工具 → 联网搜索的提供商选择器中提供 Keenable；`web_search` 工具和会话联网检索使用同一配置，输出可展示的网页引用与摘要。支持免密钥访问和用户主动配置的可选密钥。保留既有默认引擎及 Tavily、SearXNG 独立工具。

不新增依赖、数据库迁移、应用权限、独立工具、部署或公开发行。本机与仓库中已有 APK、artifacts、.v2c 临时目录不属于交付范围。

## 已核验契约

- 官方来源：https://docs.keenable.ai/api-reference 与 https://docs.keenable.ai/api-reference/search 。
- 免密钥请求：POST `https://api.keenable.ai/v1/search/public`，包含 `X-Keenable-Title: Nexara`。
- 配置密钥后：POST `/v1/search`，使用 `X-API-Key`，密钥仅由 SecretStore 读取。
- 请求使用 `query`、`max_results`；结果上限 1–50。响应 `results` 包含 `title`、`url`、`snippet`、`description`，摘要优先 snippet。
- PDEC 已批准本机 macOS 执行 Android 构建与测试，校验 execution_ready=true、drift=[]。

## 实施顺序与所有权

1. 主控独占新增 HTTP 与路由回归测试，验证未接入时失败。
2. 主控实现 KeenableProvider，统一 WebSearchSkill 与 WebSearchContextProvider 路由，保留取消传播和 IO 调度。域名白名单、黑名单按 URL 主机及子域匹配，结果数有界；HTTP 错误、异常响应不回显服务端正文或密钥，不在认证失败时降级为匿名请求。
3. 主控扩展 SearchConfigViewModel、SecretCatalog 与既有密钥编辑器，提供商选择持久化、可选密钥安全保存/读取/清除及重试；密钥纳入现有加密备份目录，普通偏好不保存明文。
4. 主控在既有 Compose / Material 3 控件中加入选择项和免密钥说明，检查窄屏滚动及双语资源；不改变页面设计体系。
5. 实现稳定后安排未参与施工的只读独立审阅；主控逐项复核发现、实际 diff、测试与运行证据。
6. 更新 README、CHANGELOG、registry、计划执行结果；使用 HLG append dry-run/apply 记录交接；检查暂存清单后提交并推送当前分支，并核对远端 SHA。

## 验收

- 定向 JVM：`:app:testDebugUnitTest --tests '*WebSearchKeenableTest' --tests '*SearchConfigViewModelTest' --tests '*SearchProviderCancellationTest' --tests '*SecretStoreTest'`。
- 质量：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:validateDebugScreenshotTest`，同一工作树串行运行 Gradle。
- HTTP 用 MockEngine 覆盖匿名/密钥路径、参数、引证与摘要、域名过滤、无结果、异常格式、401/403/429/5xx、取消和工具错误状态。仅以公开合成查询验证真实免密钥端点，不调用付费认证端点。
- 本机专用 AVD 上检查新增选项可见、可选择与保存、密钥编辑器及长面板可滚动；未覆盖的设备/认证模式如实记录。

## 回滚

以单个功能提交回退接入与文档；保留既有配置及安全存储，不重置真实用户数据。现有引擎默认值不变，回退后保留的 Keenable 偏好不会引发反序列化或数据库迁移。

## 执行结果

- 已实现提供商、共享路由、安全凭据、加密备份目录与双语设置；默认引擎及独立工具保持兼容。
- 公开合成查询直连免密钥端点返回 HTTP 200，响应包含 query、mode、results，结果包含 title、url、description、snippet、acquired_at。
- 接入前新增 HTTP/路由测试 7 项中 5 项按预期失败，证明 keenable 选择落入 DuckDuckGo 默认路由；实现后定向 52 项测试通过。
- 初次完整质量检查通过：JVM 2998 项、0 失败、25 跳过；截图 111/111；Lint 0 错误、539 条警告；debug APK 与设备测试 APK 构建成功；文本换行门禁通过。
- API 35（本机 Nexara_API_35，emulator-5554，arm64-v8a）设备证据发现长面板停在半展开状态，底部说明和密钥不可见；使用完全展开状态后，原失败的两倍字号选择/持久化/滚动用例通过。原生独立审阅已复核该修正的范围。
- 同组既有 Agent 检索重置测试在隔离运行仍失败；截图与测试代码确认测试滚动到页面下方后点击屏幕外重置按钮。测试补充先滚动到按钮并验证可见，不改变检索功能。
- 最终源码质量检查退出码 0：JVM 2998 项、0 失败/错误、25 跳过；截图 111/111；Lint 0 错误、545 条警告；debug 与 AndroidTest APK 构建成功；文本换行与 diff 空白检查通过。日志：`artifacts/keenable-search-final-quality.log`。
- API 35 设备完整设置回归 9/9；恢复系统默认动画后，Keenable 两倍字号面板与既有重置弹窗两个用例 2/2。日志：`artifacts/keenable-search-final-device.log`、`artifacts/keenable-search-default-animation-device.log`。初次 2 个失败均已有复现、原因和通过证据。
- 成果与交接记录一并纳入当前 B-native-refactor 分支的功能提交，交付时核对远端与本机 HEAD 一致；未跟踪的既有 APK、artifacts、.v2c 目录不纳入提交。

## 独立审阅与验收边界

原生子 Agent `review_keenable` 在实现完成后只读审阅，并对面板完全展开修正单独复核，均无阻断发现。主控核对实际 diff、测试报告、失败截图与运行命令。原生子 Agent 的路径/命令限制是提示词逻辑边界，未声明系统级硬隔离。

认证接口由合成密钥与 MockEngine 验证，未执行真实带密钥请求；Keenable 密钥的备份目录由测试与既有通用备份链路核验，未执行真实账户密钥的备份往返。未执行物理设备、最低 API 31、签名发行包或公开发行验收。
