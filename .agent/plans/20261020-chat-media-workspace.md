# 会话媒体：工作区落地、前端渲染与模型查看实施计划

> 状态（2026-10-20）：M1–M6 已完成：M1–M5 已合入 `B-native-refactor`；M6 已在 OPPO PKH110（ColorOS 16）真机验收会话内卡片、播放、全屏、保存、分享、资源管理器预览，以及 DeepSeek（非视觉）、Gemini 3.8 Flash、GLM-5.3-Flash 的 `view_media`；生图、ComfyUI over tailnet、MCP 图片因缺少服务或主机尚未真机验证。`media_fetch` 对 tailnet 明文 http 返回错误并指引改用脚本下载，tailnet 明文直连为后续项。

## 目标

1. 图片、视频、音频统一先落在会话工作区，工作区文件是媒体的唯一事实源。来源包括：内置生图工具、MCP 工具返回的媒体、网络下载、脚本或命令产出（含经公网或 tailnet 调用其它主机上的 ComfyUI 等服务）、用户附件、工作区里已有的文件。
2. 模型可以通过引用把工作区里的媒体交给前端，前端在会话里渲染为图片、视频或音频卡片，供用户查看、播放、保存到系统相册或下载目录、分享。
3. 支持对应模态输入的模型，可以通过工具查看工作区里的媒体；模型能力不支持某种模态时，不向模型发送该模态的数据，并给出明确说明。
4. 资源管理器里的媒体文件可以直接预览和播放。

## 非目标

- 不做媒体编辑（裁剪、转码、剪辑）。
- 不做模型的音频或视频输出（语音合成、视频生成模型的原生输出协议）；这类服务的产物通过工具或脚本落到工作区后，按本计划渲染。
- 用户附件图片目前以 data URL 存在消息里，本计划不改它的存储方式。
- 不放宽明文 http 策略，前端不直接加载 tailnet 或局域网地址。

## 已验证事实（2026-10-20，基于当前工作树源码）

- 会话界面能显示的图片只有三类：用户附件缩略图（`ChatComposerAttachments`、`UserMessageBubble`）、工具结果 `ToolResult.data` 中的图片（`PipelineBubble.extractToolImageModels`，最多 4 张；目前只有 `ImageGenerationSkill` 写入 `data`）、Markdown `![](地址)` 图片（`MarkdownText` 用 `Coil3ImageTransformerImpl`）。三者都可以点开 `ImageLightbox` 放大。
- Markdown 图片地址直接交给 Coil：https 公网图片和本机绝对路径可加载；工作区虚拟路径（`/mnt/...`、私有工作区路径）无法解析；任意绝对路径都会被尝试读取。
- 生图工具把结果下载或解码到 `filesDir/generated_images/`，不在工作区内，模型和其它工具都访问不到。
- 项目没有任何音视频播放组件；`ImageLoader` 注册了 `VideoFrameDecoder`（coil-video）与 `SvgDecoder`，但会话中没有用到视频帧。
- MCP 调用结果整体 `toString()` 后作为文字 `content` 交给模型（`McpSkill`），图片、音频的 base64 原样进入模型上下文，界面不展示；`ResultSizeOptimizer` 中有提取 MCP 图片与多模态摘要的函数，但没有调用方。
- 网络安全配置只允许 https，明文 http 仅放行 `127.0.0.1`；Coil 使用默认网络栈，不经内置 Tailscale。`gitRemoteHttpClient` 演示了经 `tailnetManager.proxySelector`/`proxyAuthenticator` 访问 tailnet 的 OkHttp 写法。
- 脚本与执行工具（`python_exec`、`shell_exec`）以工作区真实目录为 cwd，可直接把下载结果写入工作区；tailnet 访问经内置 Tailscale 本机代理（环境变量注入）。
- 模型能力：`ModelCapabilities` 有 `vision`、`audioInput`、`videoUnderstanding`（编辑模型页对应“视觉 / 音频输入 / 视频理解”）。
- 协议层：`ProtocolMessage` 支持 `imageUrls`、`audioData`、`documentData`，没有视频字段；只有用户消息会填入图片。各协议的工具结果都只编码为文字：OpenAI Chat 的 `role=tool` 消息为字符串，Anthropic `tool_result.content` 为字符串，OpenAI Responses 的 `function_call_output.output` 为字符串，Gemini 的 `functionResponse` 为结构化文本。
- 工作区读写：私有工作区由 Room 跟踪（`IWorkspaceRepository`，按 UUID 定位），外部挂载与自选目录工作区经 `ExternalWorkspaceAccess`/`ExternalFolderBackend`；`ScriptWorkspaceGateway` 已把两者统一为按虚拟路径读写，`PrivateWorkspaceTransferBackend` 把私有工作区包装成 `ExternalFolderBackend`。

## 设计决策

### 媒体落地

- **统一服务**：新增 `WorkspaceMediaStore`，负责把字节流写入会话工作区、计算 sha256、识别 MIME（扩展名 + 文件头魔数）、读取尺寸与时长元数据，复用工作区现有写入、索引与审批逻辑。
- **默认目录**：未指定路径时写入工作区 `media/<yyyy-MM-dd>/`，文件名为“来源前缀 + 时间 + 短哈希”，如 `gen-1432-a1b2c3.png`、`mcp-1433-d4e5f6.mp3`。
- **生图工具**：结果改写入工作区默认目录，工具结果返回虚拟路径；`filesDir/generated_images/` 的旧文件保留读取兼容，不迁移。
- **MCP 媒体**：`image`、`audio` 内容项与带 `blob` 的 `resource` 解码后落到工作区，交给模型的文字结果只保留“已保存到 <虚拟路径>（类型、尺寸）”，不再包含 base64。
- **网络媒体**：新增 `media_fetch` 工具，参数为 URL 与可选保存路径，用 https 下载到工作区；tailnet 主机经内置 Tailscale 代理（复用 `gitRemoteHttpClient` 的代理配置写法），明文 http 只允许 tailnet 与本机回环地址。下载有大小上限与类型白名单（图片、音频、视频），写入沿用文件写入的审批规则。
- **脚本与命令**：不新增机制，脚本自己把产物写进工作区；工具指引写明“媒体产物请保存到工作区，再按引用规则交给用户”。

### 交给前端：引用规则

- 模型在回复正文中用 Markdown 图片语法引用工作区媒体：`![说明](工作区虚拟路径)`。前端按文件类型渲染：图片显示图片卡片，视频、音频显示播放卡片，其它文件显示文件卡片（打开、保存、分享）。
- 普通链接 `[名称](工作区虚拟路径)` 渲染为文件卡片。
- 解析范围只限当前会话可见的工作区虚拟路径与 https 公网图片；本机真实绝对路径不再直接加载。路径不存在、越界或已被删除时显示“文件不可用”占位，并提供在资源管理器中定位的入口。
- 工具步骤中的媒体：生图、`media_fetch`、MCP 媒体等工具的结果在工具步骤内显示同样的卡片；`ToolResult` 新增结构化字段 `media: List<MediaRef>`（虚拟路径、MIME、大小、sha256、宽高或时长），取代以 `data` 字段承载图片地址的做法，`data` 保留读取兼容。
- 会话持久化只存引用，不存媒体字节；重新打开会话时按引用重新解析。

### 前端渲染组件

- **加载**：自定义 Coil `Fetcher` 识别 `nexara-workspace://<会话>/<虚拟路径>` 形式的内部模型，经统一读取接口取字节；Markdown 与工具卡片都转换成该模型后交给 Coil，缩略图走 Coil 缓存。
- **图片**：沿用 `ChatImageThumbnail` 与 `ImageLightbox`，补充保存、分享、打开方式。
- **视频与音频**：引入 AndroidX Media3（ExoPlayer + UI），会话内为内联播放卡片（视频显示首帧封面，点按后播放，支持全屏），同一时刻只播放一个。播放源为工作区文件：真实路径可读时直接给播放器，SAF 挂载经 `ContentResolver`，私有工作区经自定义 `DataSource`。
- **保存与分享**：保存到系统相册（图片、视频）或“下载”（音频及其它）经 MediaStore 写入，无需存储权限；分享经 `FileProvider` 临时授权。
- **资源管理器**：媒体文件点按后打开同一套查看器。

### 模型查看：`view_media` 工具

- 参数：工作区虚拟路径（可多个）、可选问题说明。
- 可用性按当前路由模型能力决定：`vision` 支持图片，`audioInput` 支持音频，`videoUnderstanding` 支持视频；三者都不支持时不提供该工具。
- 不支持的模态：返回明确的文字说明（“当前模型不支持视频输入”），并附带文件元数据。
- 视频降级为关键帧：模型支持图片但不支持视频时，`view_media` 对视频抽取关键帧作为图片交给模型，并注明“以下为关键帧，不含声音”。抽帧密度由 Agent 通过参数自行调整：帧数 `frames` 或间隔 `interval_seconds` 二选一，可选时间范围 `start_seconds`/`end_seconds`；未指定时按时长均匀抽 8 帧。单次调用的帧数上限为 32，超出时截断并在结果中说明；每帧带时间戳，便于 Agent 针对某一段再次加密抽帧。模型支持视频输入时也可以显式要求按关键帧查看，以节省上下文。
- 预处理：图片长边缩放到上限并压缩为 JPEG 或 PNG；音频只接受协议支持的格式（OpenAI Chat 为 wav/mp3），不转码；视频与音频有大小上限，超出时返回说明。
- 协议编码：`ProtocolMessage` 新增 `videoData`，并允许 `role=tool` 消息携带媒体。各协议优先使用原生的“工具结果含媒体”格式（Anthropic `tool_result` 内容块），不支持的协议改为在工具结果之后追加一条合成用户消息承载媒体，并在文字中注明“以下为 view_media 加载的 <路径>”。各协议的原生支持情况在实施前以官方文档逐一核对。
- 媒体只在发起 `view_media` 的那一轮进入上下文；后续轮次在历史中替换为“已查看 <路径>”的文字，避免重复占用上下文。

## 分期与关键路径

1. **M1 媒体引用模型与读取接口**：`MediaRef`、按虚拟路径统一读取（私有工作区、外部挂载、自选目录）、MIME 与元数据识别、`WorkspaceMediaStore` 写入。
2. **M2 前端渲染**：Coil 工作区 Fetcher、Markdown 媒体引用与文件卡片、工具步骤媒体卡片、不可用占位、绝对路径加载收紧。
3. **M3 播放与保存**：Media3 视频与音频卡片、全屏播放、MediaStore 保存、FileProvider 分享、资源管理器预览。
4. **M4 媒体落地来源**：生图改写入工作区、MCP 媒体落地与 base64 剥离、`media_fetch`（含 tailnet 代理与类型/大小限制）、工具指引更新。
5. **M5 模型查看**：`view_media`、能力判定、预处理、各协议媒体编码与合成消息回退、历史中的媒体降级。
6. **M6 验收**：真机端到端验收（见下）。

M1 先于其它各期；M2 与 M4 可在 M1 后并行；M3 依赖 M2；M5 依赖 M1，与 M2–M4 无依赖。每期独立提交推送。

## 验收

- 单元测试：抽帧参数（帧数、间隔、时间范围、上限截断、时间戳）、虚拟路径解析与越界拒绝、MIME 识别、默认目录与命名、MCP 媒体解码落地与 base64 剥离、`media_fetch` 的 URL 策略（https、tailnet、回环、拒绝其它明文 http）与大小限制、Markdown 引用分类、`ToolResult.media` 序列化与旧 `data` 兼容、能力判定、图片预处理尺寸、各协议媒体编码与合成消息回退、历史媒体降级。
- 设备测试：生图结果出现在工作区并在会话中显示；Python 脚本经 tailnet 调用 ComfyUI 生成图片和视频并保存到工作区，模型回复中引用后显示图片卡片与可播放的视频卡片；MCP 返回的图片显示且模型上下文中不含 base64；视觉模型用 `view_media` 描述工作区图片，非视觉模型得到明确说明；保存到相册与分享可用；资源管理器预览可用。
- 截图：图片、视频、音频、文件卡片与不可用占位的浅色、深色与 1.3 倍字号。

## 已确认决策（2026-10-20 用户确认）

1. 交给前端只用 Markdown 引用，不另设展示工具；工具产出的媒体同时在工具步骤内显示。
2. 视频与音频引入 Media3，在会话内播放。
3. 只支持图片输入的模型查看视频时抽取关键帧，抽帧密度由 Agent 通过参数调整。
4. 默认落地目录为工作区 `media/<yyyy-MM-dd>/`。
