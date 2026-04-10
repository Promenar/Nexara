# Nexara Changelog

## v1.2.75 (2026-02-18)
- **Improve**: PDF Robustness 大文件支持（Phase 2 架构优化）
  - 新增 `extractTextFromUri` 方法，支持直接从文件 URI 提取
  - 大文件 (>5MB) 自动切换到临时文件 + URI 模式
  - 添加 60 秒超时保护
  - 自动清理临时文件
  - 支持 WebView 文件访问权限配置

## v1.2.74 (2026-02-18)
- **Feature**: Audit Logging 系统（Phase 1 架构优化）
  - 新增 `audit_logs` 数据库表，支持安全审计追踪
  - 创建 `audit-service.ts`，实现批量异步写入
  - 注入审计日志到文件系统技能（write/read/list）
  - 支持按会话、操作类型、时间范围查询
- **Note**: Vector Search TurboModule 已在前期实现完成

## v1.2.73 (2026-02-17)
- **Improve**: 内联样式优化（P2 审计修复）
  - 重构 RagReferencesList 组件，将内联样式提取为 useMemo + StyleSheet
  - 使用 Spacing 常量替代硬编码数值
  - 减少每次渲染时的样式对象创建开销

## v1.2.72 (2026-02-17)
- **Improve**: 间距 Token 统一（P2 审计修复）
  - 创建 `Spacing` 和 `SpacingPresets` 常量
  - 遵循 Tailwind CSS 规范，每单位 = 4px
  - 更新 SettingsItem、CollapsibleSection、GlassBottomSheet、ContextMenu、Switch 使用统一常量

## v1.2.71 (2026-02-17)
- **Improve**: 边框颜色统一（P2 审计修复）
  - 创建 `Borders` 常量，统一管理边框颜色
  - 支持 `primary`、`subtle`、`glass` 三种边框样式
  - 更新 CollapsibleSection、AnimatedSearchBar、SettingsItem、GlassBottomSheet、GlassAlert 使用统一常量

## v1.2.70 (2026-02-17)
- **Improve**: 阴影参数统一（P2 审计修复）
  - 创建 `Shadows` 常量，统一管理阴影样式
  - 支持 `sm`、`md`、`lg`、`glow` 四种阴影规格
  - 更新 GlassBottomSheet、GlassAlert、Switch、ContextMenu、FloatingTextEditorModal、FloatingCodeEditorModal 使用统一常量

## v1.2.69 (2026-02-17)
- **Fix**: ChatBubble LoadingDots 动画未显式清理（P2 审计修复）
  - 添加 `cancelAnimation` 清理逻辑，避免组件卸载后动画继续运行
- **Skip**: ToolExecutionTimeline 虚拟化 - 滚动逻辑复杂，迁移风险高
- **Skip**: RagReferences 虚拟化 - 引用列表通常较短，且有进入/退出动画

## v1.2.68 (2026-02-17)
- **Fix**: Marquee 使用旧版 Animated API（P3 审计修复）
  - 迁移到 Reanimated 的 useSharedValue + withTiming
  - 添加动画清理逻辑，避免内存泄漏
- **Fix**: CollapsibleSection 双重渲染（P3 审计修复）
  - 添加 `measured` 状态，测量完成后不再渲染测量层
  - 减少 50% 的 children 渲染开销

## v1.2.67 (2026-02-17)
- **Fix**: Haptics 调用延迟不一致（P2 审计修复）
  - 移除 ContextManagementPanel 中多余的 setTimeout 包装
  - lib/haptics 已内置 10ms 延迟，无需双重延迟
- **Fix**: ModelPicker 搜索无防抖（P2 审计修复）
  - 添加 150ms 防抖，避免输入时频繁过滤导致卡顿
  - 分离 searchQuery（显示值）和 debouncedQuery（过滤值）

## v1.2.66 (2026-02-17)
- **Fix**: 全局 Worklet 变量访问问题（P0 审计修复）
  - 修复 Switch 组件 worklet 中访问 JS 线程变量
  - 修复 AnimatedSearchBar 组件 worklet 变量访问
  - 修复 AnimatedInput 组件 worklet 变量访问
  - 修复 ProviderFormScreen 输入框焦点动画 worklet 变量访问
- **Improve**: ThemedSlider 添加 `useSlidingComplete` 选项
  - 支持滑动完成后再更新 Store，避免高频更新导致的性能问题
  - 默认保持原有行为（实时更新），可选启用优化模式

## v1.2.65 (2026-02-17)
- **Fix**: 输入栏重写模式性能问题
  - 修复 `focusAnimatedStyle` worklet 无法访问 JS 线程变量的问题
  - 预先计算颜色值再传入 worklet，避免每次渲染时的性能开销
- **Improve**: 输入框扩展高度从 120px 增加到 240px（约 10 行）
- **Improve**: 添加 `textAlignVertical: 'top'` 修复 Android 多行文本垂直居中问题
- **Improve**: 添加 `scrollEnabled` 支持超长文本滚动

## v1.2.64 (2026-02-17)
- **Fix**: 模型标签浅色模式高亮色不可见问题（根本原因修复）
  - 修复 `useAnimatedStyle` worklet 无法访问 JS 线程变量的问题
  - 将颜色值在组件渲染时预先计算，再传入 worklet
  - 影响 TypeButton（聊天/推理/图像生成/向量/重排序）和 CapabilityTag 组件

## v1.2.63 (2026-02-17)
- **Fix**: 模型标签浅色模式高亮色不可见问题
  - 修复 `interpolateColor` 混合 hex 和 rgb 格式时的兼容性问题
  - 将 `colors[500]` (hex) 转换为 `rgb()` 格式后再进行颜色插值
  - 影响 TypeButton 和 CapabilityTag 组件

## v1.2.62 (2026-02-17)
- **Fix**: 会话工具箱面板高度跳变问题
  - 将 `GlassBottomSheet` 高度从固定 `80%` 改为 `auto` 自适应
- **Fix**: 执行模式按钮浅色模式灰色阴影问题
  - 浅色模式下移除动画指示器的阴影效果
- **Feat**: 智能技能默认值根据模型能力自动设置
  - 新增 `isHighCapabilityModel()` 函数判断模型能力
  - 高参数模型（GPT-4、Claude 3.5、Gemini Pro 等）默认开启智能技能
  - 中低参数模型（GPT-3.5、Qwen-Turbo 等）默认关闭智能技能
  - 切换模型时自动更新智能技能状态

## v1.2.61 (2026-02-17)
- **Feat**: 中文智能换行增强
  - 新增 `addChineseLineBreaks` 函数，针对低参数模型输出的无换行长文本
  - 检测句末标点（。！？；）后智能插入换行
  - 幂等设计：对已正确排版的内容不产生影响
  - 保护代码块和行内代码不被误处理

## v1.2.60 (2026-02-17)
- **Fix**: CapabilityTag 激活态 opacity 计算错误修复
  - 修复 opacity 公式：`0.15 + progress * 0.15` → `0.05 + progress * 0.95`
  - 激活态 opacity 从 0.30 修正为 1.0，浅色模式标签可见

## v1.2.59 (2026-02-17)
- **Fix**: 会话标题自动命名触发条件修复
- **Fix**: ProviderModelsScreen 浅色模式标签高亮色修复
- **Feat**: 服务商管理界面动画优化

## v1.2.58 (2026-02-17)
- **Fix**: ProviderModelsScreen 浅色模式标签高亮色不可见问题
  - TypeButton: 使用 rgb() 格式替代 rgba()，通过 opacity 控制透明度
  - CapabilityTag: 同样使用 rgb() 格式 + opacity 组合
  - 修复 interpolateColor 不支持带透明度 hex 格式的问题

## v1.2.57 (2026-02-17)
- **Fix**: 会话标题自动命名触发条件修复
  - 修复 messages.length 判断：首次对话完成后消息数为 2，调整为 `<= 2`
  - 修复默认标题判断：新增中文"新会话"检测
  - 新增 startsWith 检测，覆盖"新会话 xxx"等变体

## v1.2.56 (2026-02-17)
- **Feat**: 服务商管理界面动画优化
  - ProviderFormScreen: 预设卡片入场动画 (FadeInDown + stagger)
  - ProviderFormScreen: 保存按钮点击缩放动画 + 加载状态
  - ProviderFormScreen: 输入框焦点边框颜色渐变动画
  - ProviderModelsScreen: 模型卡片入场动画 (FadeIn)
  - ProviderModelsScreen: TypeButton/CapabilityTag 颜色过渡动画
  - ProviderModelsScreen: TypeButton/CapabilityTag 添加 React.memo

## v1.2.55 (2026-02-17)
- **Fix**: ChatInput 焦点状态视觉效果修复
  - 添加 Keyboard.addListener('keyboardDidHide') 监听
  - 键盘消失时自动重置焦点状态和动画
- **Docs**: 新增服务商管理界面审计报告 (`docs/archive/provider-management-audit-2026-02-17.md`)
  - 审计范围：ProviderList、ProviderFormScreen、ProviderModelsScreen
  - 整体评分：A- (优秀，有微小改进空间)
  - 发现问题：输入框无焦点动画、保存按钮无点击动画、预设卡片无入场动画

## v1.2.54 (2026-02-17)
- **Fix**: 时间轴组件滚动穿透修复
  - 添加手势响应者拦截，防止滚动事件穿透到外层消息列表
  - 禁用 ScrollView 的 bounces 和 overScrollMode，防止边界回弹触发外层滚动
  - 添加滚动边界检测，在到达顶部/底部时正确处理滚动事件
- **Build**: 优化 APK 打包配置
  - 配置 ndk.abiFilters 只打包 arm64-v8a 架构
  - 移除 32 位 ARM (armeabi-v7a) 和 x86 模拟器库
  - 预计 APK 体积减少约 30-40%

## v1.2.32 (2026-02-17)
- **Fix**: 核心会话页面性能优化 (Phase 1 & 2)
  - **renderItem useCallback**: 会话详情页和会话列表页的 renderItem 提取为 useCallback
  - **FlatList 配置**: 会话列表页添加 getItemLayout、removeClippedSubviews、maxToRenderPerBatch 等优化配置
  - **内存泄漏修复**: ChatBubble 中 `(React as any)._aiImages` 全局变量替换为 useMemo
  - **计算优化**: reversedMessages 和 latestAssistantIndex 提取到组件顶层
  - **输入框焦点动画**: ChatInput 添加 onFocus/onBlur 焦点状态动画，边框颜色渐变 + 阴影效果
  - **列表项入场动画**: SwipeableSessionItem 添加 FadeIn.duration(200) 入场动画
  - **子组件 memo**: LoadingDots、MessageMeta、RagReferencesChip、RagReferencesList 添加 React.memo
- **Docs**: 新增核心会话页面审计报告 (`docs/archive/chat-page-audit-2026-02-17.md`)

## v1.2.31 (2026-02-17)
- **Fix**: 知识图谱系统全面修复
  - **边去重**: `createEdge` 新增去重逻辑，相同边累加权重而非重复创建
  - **并发安全**: `upsertNode` 使用 `INSERT OR IGNORE` 模式避免竞态条件
  - **节点合并**: `mergeNodes` 新增自环边和重复边清理逻辑
  - **文档更新**: `updateDocumentContent` 新增知识图谱清理和重新抽取逻辑
  - **累积器持久化**: `kgAccumulator` 添加到 Zustand persist 配置
- **Fix**: 文本编辑器大文件崩溃修复
  - **文件大小检测**: 加载时检测文件大小，显示文件大小信息
  - **大文件阈值**: 100KB 以上为大文件，500KB 以上为超大文件
  - **语法高亮限制**: 大文件自动禁用语法高亮预览，超大文件禁止切换预览模式
  - **警告横幅**: 大文件显示性能警告提示
- **Docs**: 新增知识图谱修复方案 (`docs/archive/kg-fix-plan-2026-02-17.md`)

## v1.2.30 (2026-02-17)
- **Fix**: RAG 与知识图谱数据级联删除修复
  - **会话删除**: 新增知识图谱数据清理逻辑，删除会话时同步清理 `kg_edges` 和孤立 `kg_nodes`
  - **批量删除文档**: 新增物理文件删除逻辑，批量删除文档时同步删除对应物理文件
- **Feature**: 孤立数据清理功能
  - **pruneOrphanSessions**: 扩展支持清理会话关联的孤立知识图谱数据
  - **pruneOrphanDocumentKG**: 新增方法清理已删除文档的孤立知识图谱数据
  - **设置页面**: RAG 配置面板新增「清理孤立数据」按钮，支持手动清理残留数据
- **Docs**: 
  - 新增 RAG 级联删除审计报告 (`docs/archive/rag-kg-cascade-delete-audit-2026-02-17.md`)
  - 新增知识图谱抽取机制审计报告 (`docs/archive/kg-extraction-audit-2026-02-17.md`)

## v1.2.29 (2026-02-17)
- **Performance**: 文库界面全面性能优化审计与实施
  - **PortalCards 组件**: 从内联定义提取为独立 `memo` 组件，避免每次渲染重新创建
  - **列表项动画**: `FadeIn/FadeOut` 时长从 200ms/150ms 优化为 120ms/80ms
  - **RagStatusIndicator**: 呼吸灯动画改为按需运行，空闲时自动停止降低 CPU 占用
  - **KnowledgeGraphView**: 新增 HTML 模板缓存机制，避免重复字符串生成
  - **批量操作工具栏**: 添加 `SlideInUp/SlideOutDown` 弹簧动画
- **Docs**: 新增文库界面审计报告 (`docs/archive/library-audit-2026-02-17.md`)

## v1.2.28 (2026-02-11)
- **Fix**: Markdown 预处理器全面重写 — 新增 7 条幂等正则修复"文字墙"问题 (`markdown-utils.ts`)。
- **Fix**: 标题无空格修复 (`###CJK` → `### CJK`)，适配 DeepSeek/Qwen 畸形输出。
- **Fix**: 粘连 bullet+bold 拆分 (`***text**` → `* **text**`)。
- **Fix**: 代码块保护 — 代码内 `#` 注释不再被误识为标题。
- **Fix**: `---` 分隔符不再被 `\s` 匹配换行符的 Bug 拆碎。
- **Cleanup**: 移除 `ChatBubble.tsx` 中废弃的 `formatDeepSeekOutput` 和 `parseMarkdownContent` 导入。
- **Docs**: 新增 Markdown 预处理器排障手册 (`docs/archive/markdown-preprocessing-guide.md`)。

## v1.2.27 (2026-02-07)
- **Robustness**: Enhanced Task Manager with strict cancellation protocol (`fail` action) and auto-skip logic for pending steps.
- **Protocol**: Updated System Prompt to explicitly handle user interruptions.
- **UI**: Cleaned up Task Monitor by removing confusing "Dismiss" button.
- **Build**: Successfully built Release APK v1.2.27 (Code 95).

## v1.2.25 (2026-02-06)
- **Feature**: Developed "Virtual Split Architecture" for multi-tool calling compatibility (DeepSeek/VertexAI).
- **UI**: Added visible loop counter ("De-blackboxing") on message bubbles.
- **UI**: Redesigned Context Management Panel for better information density.
- **Fix**: Resolved "Loop Limit" false positive by resetting counter on new turns.
- **Fix**: Standardized Super Assistant Settings UI with `SettingsSection` wrapper.
- **Audit**: Verified System Prompt construction logic effectiveness.
- **Build**: Successfully built release APK in isolated worktree.

## v1.2.24 (Previous)
- [Legacy updates inferred]
