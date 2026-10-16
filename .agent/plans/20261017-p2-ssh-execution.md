# P2 SSH 实施与验收计划

## 目标与边界

权威任务书：`.agent/tmp-agent-reports/brief-p2-ssh.md`。实现主机管理与加密凭据、指纹信任、Agent SSH/SFTP/PTY 工具、交互终端、回环端口转发。限定当前 worktree；不提交推送，不追加 handover，不操作模拟器/设备，不读取任务书禁止目录和文件内容。

## 关键路径与所有权

1. 主控核验 JSch/SSHJ、BC、Termux 仿真器的官方来源与 API，固定运行层/存储/UI 接口。
2. `ssh_storage` 独占主机模型、凭据封装、Room 表/DAO/迁移与存储测试；主控接线 Application。数据库恢复和备份的兼容影响必须先取证，不能静默破坏 v18 恢复契约。
3. `ssh_terminal_ui` 独占新增 SSH 设置页、终端桥接、双语独立字符串文件及桥接测试；复用现有 Material 3 与 Termux 仿真器/渲染器，不启动本地子进程。
4. 主控独占 SSH 传输、连接池/取消、Agent 工具、审批目标解析、前台服务、导航、依赖/R8、文档与集成测试。
5. `ssh_tech_review` 完成官方资料选型核验后，作为未参与施工的独立审阅者串行审阅最终实现；主控复核 diff、真实测试与修复结果。

## 不变量

- 工具严格只接受已配置的别名；凭据不进入参数、结果、日志、异常或备份导出。
- 首次指纹需要 UI 明确确认；变化拒绝，替换采用旧指纹比较后更新，连接在认证前校验。
- 执行/路径授权绑定完整别名及命令/路径；PTY session 只能由所属聊天会话访问，发送前解析真实别名。
- 输出、文件、列表、会话和连接均有上限；取消必须关闭通道，生成结束释放所属连接。
- 端口转发仅监听 127.0.0.1；前台服务不自动恢复连接，结束服务清理状态。
- 网络/IO 在 IO 调度器；常驻 PTY 空闲到期释放，终端退出默认关闭。

## 验收

按红绿闭环补充目标提取、存储/迁移、严格信任、参数、敏感信息、输出/超时/取消/释放测试。JVM 内嵌 Apache MINA 服务测试密码与公钥、退出码/stderr、SFTP/乐观并发与 PTY。每次仅运行一个 Gradle 进程。

```bash
cd native-ui
./gradlew :app:testDebugUnitTest :app:lintDebug :app:validateDebugScreenshotTest
./gradlew :app:assembleMinifiedTest
cd ..
python3 scripts/ci/check-text-wrapping.py
```

受沙箱限制时，只将临时编译缓存和测试进程 HOME 重定向到工作树，不改全局配置、不放宽测试门禁。设备、Termux 与 R8 运行时验证写入报告供主控执行；静态构建通过不等于设备通过。删除新增源文件/撤销本任务 diff 可回滚，保留旧 Room 结构与旧 schema 作为兼容证据。
