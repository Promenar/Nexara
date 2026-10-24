# 内置 Linux 执行环境（proot + Alpine）

日期：2026-10-24 · 状态：方案与原型已确认；P0 真机通过（2026-10-25），等待决策门

## 目标

在侧载版内置一套随 Nexara 进程运行的 Linux 用户空间：用 proot 加载 Alpine Linux（musl、apk 包管理器），作为 Agent 的统一执行基座。不安装 Termux，也能在工作区里使用 bash、apk 安装的软件包、musllinux wheel、上游预编译的 Linux arm64 程序，以及交互式终端。

非目标：

- 不做硬件虚拟机（AVF/pKVM 不向第三方应用开放），也不做 CPU 模拟（QEMU TCG）。
- 不提供路径级隔离。proot 只伪造路径和 root 身份，不是安全边界；命令仍按现有权限模型视为不受工作区约束的进程。
- 第一版不支持多个发行版或多套环境、不支持图形（X11/Wayland），也不让 Linux 环境跨设备备份或迁移。
- 不替代 Termux 和 SSH。两者保留：Play 版的完整 Linux 能力只能靠它们，平台一旦收紧下文的风险 R1，它们也是退路。

## 已验证的事实

| 事实 | 来源 |
| --- | --- |
| 真机 OPPO PKH110：Android 16，ColorOS V16.1.0，构建日期 2026-08-04，内核 6.6.118 GKI | 本机 adb getprop |
| ColorOS 15 早期固件会以 SIGKILL 杀死 proot 下的进程，2024-11 之后的 ColorOS/OxygenOS 15 固件已修复；Termux 维护者说明关闭幽灵进程限制无效 | [termux-app#4219](https://github.com/termux/termux-app/issues/4219) |
| targetSdk ≥ 29 时，execve 应用数据目录中的文件会被拒绝；nativeLibraryDir 中的打包二进制可以执行（需要 `useLegacyPackaging = true`） | `docs/superpowers/specs/2026-10-18-nexara-exec-backend.md` §2 |
| 用 linker64 运行或 dlopen 数据目录中的文件目前可行，但会产生 `avc: granted { execute }` 审计记录；现行规格规定不依赖这一行为 | 同上 |
| Alpine 3.24.3 aarch64 minirootfs：4,029,999 字节，sha256 `4d3f80af…7551f50`，发布于 2026-10-21 | dl-cdn.alpinelinux.org `latest-releases.yaml` |
| termux/proot 仍在维护，最近提交 2026-10-24；许可证为 GPL-2.0 | GitHub API |
| ReTerminal（MIT）的做法：targetSdk 37，`useLegacyPackaging = true`，用 NDK 和 CMake 从源码编译 proot（`PROOT_UNBUNDLE_LOADER`，loader 单独打包），assets 中内置 Alpine rootfs，并在 F-Droid 发布。这说明 proot 加 loader 放在 nativeLibraryDir、targetSdk 维持较高版本的方案已有公开实践 | GitHub 源码 |
| Aether（Zhou-Shilin/Aether，GPL-3.0，KMP Agent 客户端）在 Android 上同样用 proot 加 Alpine（assets 内置 rootfs、staging 解压后原子 rename），但它把 targetSdk 压到 28，用 linker64 启动的是直接取自 Termux 包、未经校验的二进制；Pi 与 Node 在 proot 内运行 | 源码与 issue 调研 |
| Alpine 3.23 起，apk-tools 3 用 `O_TMPFILE` 加 `linkat` 写包数据库，在应用私有目录下会失败；必须加 `--link2symlink`，配合 `-0` 与 `--no-chown` | Aether issue #81 |
| 写死 DNS 会导致 VPN（Tailscale 等）的私有域名无法解析；应从 ConnectivityManager 读取当前网络的 DNS（VPN 优先）生成 `resolv.conf`，原子写入，并且不覆盖用户的修改 | Aether issue #111、#67 |
| 发行 APK 的上限是 50 MiB，当前通用 APK 约 34.7 MB | `scripts/verify-release-apk.py` |
| 项目已经用 termux `terminal-emulator`/`terminal-view` 0.118.0 实现 SSH 终端，但构建时排除了 `libtermux.so`（本地 PTY 桥） | `native-ui/app/build.gradle.kts:464,599` |

## P0 真机结果（2026-10-25，PKH110，ColorOS V16.1.0，targetSdk 36）

探针提交 `55e72aa3`，在分支 `codex/linux-p0` 上，尚未合入主线。proot 来自 termux/proot 提交 `b27d81a6`，talloc 2.4.4，均从源码编译；APK 内为 `libnxproot.so` 约 257 KB、`libnxproot-loader.so` 约 3 KB，另带 Alpine 3.24.3 rootfs。原始数据存在 `.agent/tmp-agent-reports/linux-p0/probe-result-PKH110.json`。

| 项目 | 结果 |
| --- | --- |
| 从 nativeLibraryDir 直接 exec proot | 可行，不经过 linker64 |
| seccomp 加速 | 已启用（proot -v 输出 `ptrace acceleration (seccomp mode 2) enabled`） |
| 解包 rootfs | 345 ms |
| 启动开销（`true`，20 次） | 中位数 53 ms，p90 54 ms |
| `apk update` 与 `apk add bash python3 git` | 0.8 秒和 7.5 秒；包数据库写入正常（`--link2symlink`），共 29 个包、66.6 MiB |
| HTTPS 联网、DNS（ConnectivityManager） | 正常 |
| 纯计算（Python 循环 1000 万次） | Alpine python3 0.98 秒，打包 Python 1.18 秒，没有额外开销 |
| 生成 2 万个文件（shell 循环） | proot 4.3 秒；系统 sh 120 秒内只生成 1.6 万个（mksh 每次 printf 都要起进程，busybox ash 是内建命令） |
| find 遍历 2 万个文件 | proot 1.9 秒，系统 toybox 0.16 秒（只有 1.6 万个文件），慢约 10 倍 |
| tar 打包加解包 2 万个文件 | proot 52 秒，系统 2.2 秒（1.6 万个文件），慢约 15–20 倍 |
| rm -rf 2 万个文件 | proot 19.5 秒，系统 1.3 秒，慢约 12 倍 |
| git status（2 万个文件） | 1.09 秒 |
| SELinux 审计 | 加载 rootfs 中的 ELF 时记录 `avc: granted { execute }`，`tcontext=app_data_file`，证实了 R1 |
| 后台 | 探针没有前台服务，退到桌面后 Hans 立即冻结（`scene: LcdOn`），和现有内置命令不带前台服务时一样；带前台服务的表现在 B2 的真机验收中验证。前台运行期间没有出现 ColorOS 15 那种 SIGKILL 杀掉 proot 的情况 |

结论：技术上可行。计算和 shell 脚本的性能与原生相当或更好；文件元数据密集的操作比原生慢 10–20 倍，比讨论阶段估计的 2–5 倍更严重。大规模解包、`rm -rf node_modules` 这类操作会明显变慢。工作区对账、资源管理器等宿主侧文件操作不经过 proot，不受影响。

## 风险（按严重程度）

- **R1 平台收紧**：proot 的 loader 位于 nativeLibraryDir，它用 `mmap(PROT_EXEC)` 映射 rootfs 里的 ELF。这依赖的正是现行规格所说的“当前可行但有审计记录”的行为。Android 将来如果禁止对 `app_data_file` 的 execute 映射，整套环境都会失效，而且没有补救办法，只能回到 Termux/SSH。这是本方案的根本前提，因此必须在规格中改写原有非目标“不从网络下载可执行二进制”，并说明理由。
- **R2 厂商查杀**：当前 ColorOS 16.1 上 proot 能不能稳定运行，以及在后台被 Hans 冻结的表现，都只能真机实测，由 P0 判定。
- **R3 Play 政策**：`apk add` 会从网络下载可执行代码，Play 版不能提供。
- **R4 许可证**：proot 和 rootfs 里的 busybox 都是 GPL-2.0。它们以独立程序随 APK 分发，属于聚合分发，但需要在 THIRD_PARTY_NOTICES 中写明来源，并提供对应源码（固定的提交与源码包链接）。
- **R5 性能**：开销集中在路径类系统调用和进程启动。大仓库 git、装包这类场景会变慢，P0 用基准测试定量。

## 决策（2026-10-24 用户确认）

1. **工具形态**：不新增工具名。安装并启用 Linux 环境后，`shell_exec` 改在 Alpine 中运行；未安装或 Play 版仍用 `/system/bin/sh`。结果增加 `runtime: linux|system` 字段，系统提示词按当前状态说明可用命令。`python_exec` 和 `python_sandbox` 保持使用打包的 Python（两个渠道都可用）；Linux 中的 `python3` 由 apk 安装。
2. **渠道**：本期只做侧载版，Play 版不变。Play 版的补充路线见下一节，侧载版落地后再决定。
3. **rootfs 来源**：4 MB 的 minirootfs 作为 asset 随 APK 发布，首次安装可以离线完成；apk 仓库可选清华、中科大、阿里云和官方 CDN，并提供测速。
4. **工作区位置**：当前会话工作区绑定到容器内的 `/workspace`（`NEXARA_WORKSPACE=/workspace`），命令可见的挂载绑定到 `/mnt/<别名>`；Linux 主目录 `/root` 作为目录工作区出现在工作区选择器中。
5. **交互式终端**：纳入本期，重新启用 `libtermux.so` 的本地 PTY，复用 SSH 终端组件。
6. **原型**：视觉原型基本通过。会话页的命令记录和审批卡按原型精修；**输入浮岛保持现有实现，不按原型改**（原型里的输入框只是占位）。

## Play 版补充路线（待侧载版落地后决定）

Play 政策禁止应用从 Google Play 以外下载可执行代码（dex、JAR、.so 等），只豁免在虚拟机或解释器中运行、通过它们间接访问 Android API 的代码（[Play 设备和网络滥用政策](https://support.google.com/googleplay/android-developer/answer/16273414)）。proot 下的程序是原生执行，不属于这类豁免。以下三条路线都不在 Play 版应用内偷偷下载可执行代码：

- **A 配套扩展应用（推荐评估）**：把侧载版的 Linux 运行时单独打包为同签名的“Nexara Linux 扩展”，在 GitHub/F-Droid 发布。Play 版只显示引导和官方下载页链接，不在应用内下载或安装 APK，也不申请 `REQUEST_INSTALL_PACKAGES`。两者通过签名级权限保护的 bound service 通信，扩展以自己的 UID 运行命令；工作区沿用后台任务的“副本运行、结束合入”机制交换。现有 Termux 引导已是同类做法。是否会被审核视为引导侧载，需要提审前再核实政策。
- **B Play Feature Delivery 按需模块**：由 Play 分发一个预装精选工具（python、git、node）的 rootfs 模块，不开放 `apk add`，工具随应用更新。技术前提是按需模块中的原生库能进入可执行的 nativeLibraryDir，这一点现行规格列为“未测”。
- **C 现有 Termux / SSH 引导**：已实现，作为兜底。

为给 A 留接口，B2 中 `shell_exec` 到 Linux 运行时的调用经过 `LinuxRuntime` 接口，进程内实现与将来的扩展实现可以互换。

## 架构

```
shell_exec / 后台任务 / 终端页
  └─ LinuxRuntime（data/linux）
        ├─ PackagedProot        在 nativeLibraryDir 定位 libnxproot.so、libnxproot-loader.so
        ├─ LinuxRootfsInstaller 解包 asset、校验、版本标记、重置、卸载、占用统计
        ├─ LinuxExecEnvironment 拼 proot 参数、绑定、环境变量（代理、CA、语言、PATH）
        ├─ LinuxExecService     复用 BuiltinCommandRunner、WorkspaceCommandHost、ExecKeepAlive、RuntimeDataMonitor
        ├─ LinuxPackageService  apk 列表、搜索、安装、卸载、升级（结构化输出）
        └─ LinuxPtySession      本地 PTY 终端会话
```

- rootfs 位于 `filesDir/linux/alpine`，不放进 `no_backup`（那里属于运行时关键数据），也不在 `RuntimeDataMonitor` 的监控组中。系统提示词注明在其中安装软件包属于正常使用。
- proot 参数参照 ReTerminal：`--kill-on-exit -0 --link2symlink --sysvipc -L`，按需绑定 `/proc`、`/dev`、`/sys` 和系统目录；`/dev/shm` 指向 rootfs 内的 tmp；`PROOT_TMP_DIR` 放在 cacheDir 下。
- 进程管理沿用 setsid 进程组和超时、取消、停止的语义；工作区写锁、对账和 `workspace_changes` 保持不变。路径虚拟化同时处理宿主物理路径和 `/workspace`。
- 网络：tailnet 代理变量、系统 CA bundle（写入 rootfs 的 `/etc/ssl`）。`resolv.conf` 每次启动命令前按当前网络的 DNS 重新生成（VPN 优先），在 rootfs 之外写临时文件后原子 rename，避免被 rootfs 内的符号链接利用；带校验标记，用户改过就不覆盖。网络访问策略照常生效。
- 安装：从 asset 解压到 staging 目录，校验通过后原子 rename 为正式目录；中断后可以从头安全重试。可选的软件包预设（python、node、git 与搜索工具）装完后要实际运行一次验证。设置 `PIP_BREAK_SYSTEM_PACKAGES=1`，让 pip 可以直接装包。
- 系统文件入口：加一个 DocumentsProvider，把 `/root` 和 rootfs 暴露给系统“文件”应用与 SAF 选择器。Aether 在 vivo/Android 16 上遇到过文件不显示的问题，需要真机验证。

## 阶段与执行者

| 阶段 | 内容 | 执行者 | 依赖 |
| --- | --- | --- | --- |
| P0 可行性探针 | buildSrc 用 NDK 从固定提交编译 proot 和 loader；debug 探针页运行 `uname`、`apk add python3 git`、联网；做基准测试（git status、纯计算、tar、启动耗时），记录 avc 审计、后台与 Hans 表现 | Codex 编译，主控真机实测 | 无 |
| **决策门** | P0 不通过就停止，结论写入规格 | 用户 | P0 |
| B1 打包与安装 | `linux-runtime.properties`（proot 源码、talloc、rootfs 的 URL 与 sha256）；buildSrc 任务；`verify-release-apk.py` 新库校验；`LinuxRootfsInstaller`；渠道开关 `DistributionCapabilities.linuxRuntimeAvailable` | Codex | 决策门 |
| B2 执行接入 | `shell_exec` 路由；`CommandToolBackend.LINUX`；`ToolExecutionTarget` 新增 `linux`；后台任务 runner LINUX（`BackgroundJobStore` 兼容旧版本读取）；`ExecRunKind.LINUX`；系统提示词；`AgentReachModels` 数据 | Codex | B1 |
| B3 软件包与镜像 | `LinuxPackageService`、镜像配置与测速、操作进度流 | Codex | B1 |
| B4 终端与文件后端 | 本地 PTY（`libtermux.so`）；rootfs 浏览的目录操作后端（权限、属主、符号链接、修改时间）；DocumentsProvider | Codex | B1 |
| F1 原型确认 | 12 屏可视化原型：[Nexara Linux 环境原型](https://claude.ai/artifact/5YYvBzA13FyLcqwGXtWwhG)（工具页入口、安装引导、安装中、环境管理、软件包、镜像源、Linux 文件浏览、文件属性与编辑、本地终端、完整输出查看器、会话审批卡、可达范围） | 主控 | 无（已出，待确认） |
| F2 设置与引导 | 工具页入口、安装引导、环境管理页、软件包页、镜像源 | 主控或同模型子代理 | F1、B1/B3 状态模型 |
| F3 资源管理器加强 | 目录浏览器加属性、排序、隐藏文件、新建文件、文本编辑、十六进制预览；Linux 文件入口与“在终端打开” | 同模型子代理 | F1、B4 |
| F4 终端与输出 | 本地终端页（复用 SSH 终端组件）；完整输出查看器（stdout/stderr 分页签、ANSI 颜色） | 主控 | F1、B4 |
| F5 接入 | 审批卡类别条“Linux 命令”与命令记录按原型精修（输入浮岛不动）、可达范围面板行（可点击去修复）、活动面板 runner 名 | 主控 | B2 |
| 集成与验收 | 合并、全量验证、真机验收、文档 | 主控 | 全部 |

B2、B3、B4 在 B1 合入后可以分别在独立 worktree 并行，文件所有权见下表；F 阶段可以先基于假数据状态模型开工。

## 文件所有权

| 范围 | 所有者 |
| --- | --- |
| `native-ui/buildSrc/**`、`native-ui/app/linux/**`、`app/build.gradle.kts` 的 Linux 段、`scripts/verify-release-apk.py`、`data/linux/{PackagedProot,LinuxRootfsInstaller,LinuxRuntime}.kt`、`distribution/**` | B1 |
| `data/linux/LinuxExec*.kt`、`domain/tool/**`、`domain/jobs/**`、`data/jobs/**`、`data/exec/**`、`ui/chat/manager/skills/{BuiltinExecSkills,BackgroundJobSkills}.kt`、`ui/chat/manager/SystemPromptSections.kt`、`background/exec/**`、`NexaraApplication.kt` 的装配段 | B2 |
| `data/linux/LinuxPackage*.kt`、`data/linux/LinuxMirror*.kt` | B3 |
| `data/linux/LinuxPty*.kt`、`data/storage/` 中 rootfs 目录后端的新文件 | B4 |
| `ui/**` 的 Compose 界面、`res/**`、截图测试 | 主控与前端子代理（后端阶段只允许最小编译修正） |

## 验收

- 单元测试：proot 参数与绑定构造、环境变量白名单、rootfs 安装状态机（中断后重试、校验失败、空间不足）、`shell_exec` 路由判定、`CommandToolBackend` 与审批判定、后台任务 runner 的持久化兼容、apk 输出解析、镜像配置。
- 设备测试：仿照 `PackagedPythonDeviceTest` 新建 `LinuxRuntimeDeviceTest`，覆盖解包、执行、超时、取消、对账，并加入 `android-device-core-e2e.sh`（模拟器为 arm64，构建时加 `-Pnexara.deviceE2eAbi=arm64-v8a`）。
- 全量：`testSideloadDebugUnitTest`、`testPlayDebugUnitTest`、`lintSideloadDebug`、`assembleSideloadDebug`、`assemblePlayDebug`、`validateSideloadDebugScreenshotTest`、`check-distribution-manifests.py`、`verify-release-apk.py`。
- 真机（PKH110）：首次安装引导、`shell_exec` 在 Alpine 中运行并完成对账、`apk add`、后台任务、终端页、资源管理器、审批卡、可达范围；基准测试数据写入规格。

## 文档

更新 `docs/superpowers/specs/2026-10-18-nexara-exec-backend.md`（非目标、渠道表、风险 R1）或另起 Linux 运行时规格；同步 README、CHANGELOG、`docs/ARCHITECTURE.md`、`docs/distribution-builds.md`、`docs/legal/THIRD_PARTY_NOTICES.md`（proot、talloc、Alpine 与 busybox 的来源和源码链接）。

## Codex 任务书约定

每个后端阶段一份任务书，放在 `.agent/tmp-agent-reports/linux-<阶段>/brief.md`，与 `.agent/tmp-agent-reports/common-rules.md` 同时生效。在独立 worktree `../Nexara-wt-linux-<阶段>` 中执行，分支 `codex/linux-<阶段>`。命令为 `codex exec -m gpt-6.1-sol -c model_reasoning_effort=xhigh`，使用 workspace-write 沙箱；报告写入同一目录。主控复核 diff 与测试后再合并。
