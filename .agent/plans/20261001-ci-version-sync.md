# Android CI 与版本交付收口计划

## 目标与边界

恢复当前 UI 的可信截图回归、完整质量门禁和设备 E2E；统一 0.2.2-beta/code 4 的文档与发行配置，准备签名候选。保留历史验证范围，不发布 tag、PR 或 GitHub Release，不改变真实用户数据或凭据。

## 所有权与顺序

1. 主控核验 PDEC、本机工具链及最新 CI 证据，保存失败报告，审阅预期视觉变化和真实缺陷。
2. 主控处理截图测试、基线及 Android CI；文档 Agent 独占 README、CHANGELOG、新版发行文档、release workflow 与相关确定性测试。
3. 主控串行运行 Gradle 截图、JVM、Lint、编译与测试 APK 检查，修复有证据的失败。基线更新不降低差异阈值或跳过测试。
4. 独立审阅发行门禁和基线变更；主控验证签名候选、本机专用 AVD 的核心行为及可执行升级路径。无法执行项明确记录。
5. 主控同步 registry 与验证账本，使用 HLG append dry-run/apply 收口，只暂存本任务成果并提交推送，核对确切 SHA 的 GitHub CI。

## 验收与回滚

- PDEC validate 必须 execution_ready=true，无漂移。
- Gradle 使用项目 wrapper，依次验证 validateDebugScreenshotTest、testDebugUnitTest、lintDebug、assembleDebug 及 deviceTest APK 编译；同一工作树不并行 Gradle。
- Python/脚本测试覆盖发行版本匹配及拒绝未批准发布；文档不继承历史 PASS。
- 截图 diff 与当前 UI 变化对应，保留旧基线及新生成结果用于审阅；必要时恢复对应 Git 文件。
- APK 记录源码、版本、SHA-256、签名验真和设备证据；产物、秘密及现有未跟踪目录不纳管。
- 推送后当前 SHA 的 CI 必须实际完成；失败形成有界修复候选，不能以本机 PASS 替代远端结果。

## API31 编辑器布局循环修复

- 已复现原 Prompt 编辑器布局回调导致默认 Compose idle 持续活动；断开结果回写的单变量对照通过。
- 将布局结果读取延迟至行号绘制阶段，保留文本编辑、换行与滚动映射；不延长超时或关闭同步。
- 主控独占 UnifiedPromptEditor 与回归测试，独立审阅根因及修复。验收 API31 原方法和编辑回归、102截图、JVM/Lint/构建；生产变更后重建签名候选及冷装/覆盖升级，旧APK不作为新候选。

## 执行结果

- 已同步54张截图基线/新增预览，保留0.0001阈值；恢复置顶语义、更新当前导航与36dp规格断言，修复资源读取Lint及API31布局反馈循环。
- Linux实际发现的Android FD创建竞态场景迁入真实设备；Prompt7项回归纳入minimum/full。
- 源码7910b5f1的GitHub run 36875529644全部成功：截图102、JVM2798/0失败错误/25跳过、质量及API31/35/36设备矩阵通过。三份设备脚本退出0，报告98/138/138项含条件跳过。
- 最终签名候选20464008 bytes，SHA-256 ef68431b927e8121d2c0d71369e6300bf97334deb537760d827f0bcec3b96524；API31独立冷装与公开v0.1单库覆盖升级通过。
- 文档与发行配置统一0.2.2-beta/code4；公开发行保持PENDING。minified黑盒、双库三段及人工验收未完成，tag/Release未授权。
- 纯文档治理收口提交复用同一源码/测试/CI配置证据，使用[skip ci]；不将记录提交描述为另一次实际执行的CI。
