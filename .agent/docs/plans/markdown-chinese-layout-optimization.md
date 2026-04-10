# Nexara Markdown 中文排版优化 — 三阶段计划

> **版本**: v2.0 | **创建时间**: 2026-04-05 | **状态**: 待执行
> **目标**: 彻底解决会话界面中文 Markdown 排版混乱问题

---

## 一、问题诊断

### 1.1 当前渲染管线

```
LLM 原始输出
    │
    ▼
preprocessMarkdown()                    [markdown-utils.ts:14-63]
    ├── 1. LaTeX 分隔符转换 (\[ \] → $$ $$)
    ├── 2. 代码块保护（占位符替换）
    ├── 3. 结构化间距修复（7条幂等正则）
    ├── 4. addChineseLineBreaks()        [markdown-utils.ts:74-159]
    └── 5. 恢复代码块
    │
    ▼
ChatBubble.processedContent (useMemo)   [ChatBubble.tsx:937-957]
    ├── $$...$$ → ```latex ... ```
    └── extractImagesFromMarkdown()
    │
    ▼
StreamingCardList → splitContentIntoCards()
    │  (LLM_STRUCTURED_BLOCK_REGEX 分割)
    ▼
StreamCard → <Markdown>
    │  (react-native-markdown-display 7.0.2 → markdown-it AST → 自定义 rules)
    ▼
React Native View/Text 树
```

### 1.2 五层根因

| 层级 | 根因 | 严重度 |
|------|------|--------|
| **1** | `softbreak` 规则将所有单 `\n` 强制渲染为 `{"\n"}` 实际换行 | 🔴 主因 |
| **2** | `paragraph` 的 `flexDirection:'row'` 碎片化中文文本 | 🔴 主因 |
| **3** | `addChineseLineBreaks()` 在句末标点后插入 `\n` 与 softbreak 叠加 | 🔴 主因 |
| **4** | React Native `<Text>` 缺乏 CJK 排版原语 | 🟡 架构限制 |
| **5** | 无中文字体配置，依赖系统默认 | 🟡 次要 |

### 1.3 影响矩阵

| 场景 | 严重度 | 根因 | 典型表现 |
|------|--------|------|---------|
| LLM 长段落中文 | 🔴 高 | softbreak + row布局 | 段落中间多处不自然硬换行 |
| 句末标点后断行 | 🔴 高 | addChineseLineBreaks | 破坏阅读节奏 |
| 中英文混排 | 🟡 中 | row碎片化 | 间距异常 |
| 行内公式混排 | 🟡 中 | row设计 | 换行位置不自然 |
| 列表/标题 | 🟢 低 | 不受影响 | 基本正常 |

---

## 二、三方案对比评估

### 2.1 方案 A：Phase 1 快速修复（优化现有架构）

| 维度 | 评估 |
|------|------|
| 排版质量 | ★★☆（解决 60-70% 可感知问题） |
| 工程量 | **2-3 小时** |
| 并发 WebView | 0-2（仅 Math/Mermaid） |
| 峰值内存 | ~20 MB |
| 滚动性能 | ★★★（原生 FlatList） |
| 交互保真度 | ★★★（全原生） |
| 回归风险 | **低** |
| 长对话支持 | ★★★（FlatList 回收） |

**局限**：无法解决 React Native Text 的 CJK 排版本质缺陷（无 `word-break`、`line-break`、标点悬挂等 CSS 原语）。

### 2.2 方案 B：全页面单 WebView（已否决）

| 维度 | 评估 |
|------|------|
| 排版质量 | ★★★ |
| 工程量 | 16-26 天 |
| 并发 WebView | 1 |
| 峰值内存 | ~28-108 MB |
| 滚动性能 | ★★☆（需自实现虚拟滚动） |
| 交互保真度 | ★★☆（大量 bridge） |
| 回归风险 | **极高** |

**否决原因**：工程量过大；需要自实现虚拟滚动、滚动追踪、流式更新等 FlatList 已有能力；大量原生交互需 bridge 重写。

### 2.3 方案 C：每气泡 WebView + FlatList 回收（推荐）

| 维度 | 评估 |
|------|------|
| 排版质量 | ★★★（CSS 原生 CJK 排版） |
| 工程量 | **5-8 天**（含实例池） |
| 并发 WebView | **2-5** |
| 峰值内存 | ~30-50 MB |
| 滚动性能 | ★★★（FlatList 天然回收） |
| 交互保真度 | ★★★（大部分原生保留） |
| 回归风险 | **中** |
| 长对话支持 | ★★★（FlatList 回收） |

**核心设计**：
- 保留现有 FlatList 虚拟列表（含 inverted、滚动追踪、分页加载等）
- 用户消息（短文本纯文本）继续使用原生 RN Text 渲染
- AI 消息使用独立 WebView 渲染 Markdown 内容
- FlatList 回收机制自动管理 WebView 生命周期

### 2.4 单屏并发 WebView 数量计算

基于 393×851dp 标准手机（可用消息区域 ~651dp）：

| 场景 | 可见气泡 | FlatList 挂载(含buffer) | AI 气泡数 | 并发 WebView |
|------|---------|----------------------|---------|------------|
| 短文本密集 | 6个 | 8-10个 | 4-5个 | **≤5个** |
| 一问一答混合 | 4个 | 6-8个 | 3-4个 | **≤4个** |
| 长文本(AI回复) | 2个 | 4-5个 | 2-3个 | **≤3个** |
| 极端(代码块) | 1个 | 3-4个 | 2个 | **≤2个** |

> **结论**：典型场景并发 WebView 数量为 **2-5 个**，内存开销约 **30-50 MB**，在可接受范围内。

### 2.5 WebView 实例池（关键优化）

| 指标 | 无实例池 | 有实例池 |
|------|---------|---------|
| 滚动时 WebView 创建延迟 | ~150ms/个 | **~30ms**（仅 injectJavaScript） |
| 白屏闪烁 | 明显 | **消除** |
| 内存峰值 | 波动大 | 稳定 |
| 预创建开销 | 无 | 首次启动 ~300ms（创建3个空 WebView） |

---

## 三、Phase 1：快速修复（立即执行）

### 3.1 改动清单

| # | 文件 | 行号 | 改动内容 | 目的 |
|---|------|------|---------|------|
| 1 | `ChatBubble.tsx` | 1189-1191 | `softbreak` 规则返回 `null` 而非 `{"\n"}` | 消除段落内所有强制断行 |
| 2 | `ChatBubble.tsx` | 1131-1143 | `paragraph` 规则检测是否含行内数学 `$`，含时 `row/wrap`，否则使用默认 column 布局 | 纯文本段落恢复自然排版 |
| 3 | `markdown-utils.ts` | 74-159 | `addChineseLineBreaks()` 将插入的 `\n` 改为 `\n\n`（段落分割） | 消除与 softbreak 的叠加效应 |
| 4 | `markdown-utils.ts` | 12 | `LINE_BREAK_THRESHOLD` 从 60 提升至 80 | 减少不必要的换行插入 |
| 5 | `deepseek-formatter.ts` | 全文件 | 删除 `formatDeepSeekOutput()` 死代码 | 清理冗余 |

### 3.2 具体代码改动

#### 改动 1：softbreak 规则

```typescript
// Before (ChatBubble.tsx:1189-1191)
softbreak: (node: any, children: any, parent: any, styles: any) => (
    <Text key={node.key}>{"\n"}</Text>
),

// After
softbreak: () => null,
```

#### 改动 2：paragraph 规则

```typescript
// Before (ChatBubble.tsx:1131-1143)
paragraph: (node: any, children: any, parent: any, styles: any) => (
    <View key={node.key} style={{
        flexDirection: 'row', flexWrap: 'wrap',
        alignItems: 'baseline', marginBottom: 8,
    }}>
        {children}
    </View>
),

// After — 检测是否包含行内数学公式
paragraph: (node: any, children: any, parent: any, styles: any) => {
    // 检测节点内容是否包含行内数学公式
    const hasInlineMath = node.content?.includes('$') || 
        (children && Array.isArray(children) && 
         children.some((child: any) => 
           child.props?.source?.content?.includes('$')
         ));
    
    if (hasInlineMath) {
        // 含行内数学：保持 row/wrap 混排
        return (
            <View key={node.key} style={{
                flexDirection: 'row', flexWrap: 'wrap',
                alignItems: 'baseline', marginBottom: 8,
            }}>
                {children}
            </View>
        );
    }
    
    // 纯文本：使用默认列布局，恢复自然排版
    return (
        <View key={node.key} style={{ marginBottom: 8 }}>
            {children}
        </View>
    );
},
```

#### 改动 3：addChineseLineBreaks 插入 \n\n

```typescript
// Before (markdown-utils.ts:144-145)
if (distanceToNext >= LINE_BREAK_THRESHOLD / 2) {
    processedLine += line.slice(lastInsertPos, afterPunctPos) + '\n';
    lastInsertPos = afterPunctPos;
}

// After — 使用双换行实现段落分割
if (distanceToNext >= LINE_BREAK_THRESHOLD / 2) {
    processedLine += line.slice(lastInsertPos, afterPunctPos) + '\n\n';
    lastInsertPos = afterPunctPos;
}
```

#### 改动 4：阈值提升

```typescript
// Before (markdown-utils.ts:12)
const LINE_BREAK_THRESHOLD = 60;

// After
const LINE_BREAK_THRESHOLD = 80;
```

#### 改动 5：删除死代码

删除 `src/features/chat/utils/deepseek-formatter.ts` 全文件，并移除所有 import 引用。

### 3.3 风险评估

- **改动 1 (softbreak)**：可能影响列表项内换行。需要验证有序/无序列表中单换行的渲染行为
- **改动 2 (paragraph)**：`hasInlineMath` 检测依赖 AST 节点，需确认 markdown-it AST 中 content 字段的可靠性
- **改动 3 (\n\n)**：将软折行改为段落分割，视觉上间距会变大。需确认是否需要同步调整 `paragraph` 的 `marginBottom`
- **改动 4 (阈值 80)**：增加后，短段落（60-80字符）不再被插入换行，可能遗漏部分需要换行的场景

### 3.4 验收标准

- [ ] LLM 输出长段落中文无中间硬换行
- [ ] 句末标点后不出现不自然断行
- [ ] 行内数学公式（`$...$`）仍能正确混排显示
- [ ] 代码块、列表、标题、分隔线等结构化 Markdown 正常渲染
- [ ] 有序列表/无序列表间距合理
- [ ] 深色模式下排版无异常

---

## 四、Phase 2：每气泡 WebView 混合架构（Phase 1 验证后执行）

### 4.1 架构设计

```
┌─────────────────────────────────┐
│ GlassHeader (BlurView, 原生)      │  ← 保持不变
├─────────────────────────────────┤
│ AnimatedFlatList (inverted)      │  ← 保持不变
│                                  │
│  ┌────────────────────────────┐  │
│  │ UserBubble (原生 RN Text)   │  │  ← 短文本，无需 WebView
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ AIBubble                     │  │
│  │  ├─ ContextMenu (原生包裹层) │  │  ← 仅保留外部 ContextMenu
│  │  └─ MarkdownWebView (内部)   │  │  ← 新组件，替代 <Markdown>
│  │       ├─ HTML: Markdown内容  │  │
│  │       ├─ CSS: CJK排版        │  │
│  │       ├─ KaTeX (内嵌)        │  │
│  │       ├─ Highlight.js (内嵌) │  │
│  │       └─ JS Bridge           │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ UserBubble (原生 RN Text)   │  │
│  └────────────────────────────┘  │
│  ... (FlatList 虚拟回收)          │
├─────────────────────────────────┤
│ ChatInput (BlurView, 原生)        │  ← 保持不变
└─────────────────────────────────┘
```

### 4.2 核心组件设计

#### 4.2.1 `MarkdownWebView` 组件

```typescript
interface MarkdownWebViewProps {
    content: string;           // 预处理后的 Markdown 文本
    isDark: boolean;           // 暗色模式
    messageId: string;         // 消息 ID（用于缓存）
    onHeightChange?: (h: number) => void;  // 高度回传
    onLinkPress?: (url: string) => void;    // 链接点击
    onImagePress?: (url: string) => void;   // 图片点击
    isStreaming?: boolean;      // 是否流式输出中
}
```

**渲染策略**：
1. Markdown → HTML 转换在 JS 端完成（`marked.js` 或 `markdown-it` 内嵌于 HTML）
2. KaTeX / Highlight.js / Mermaid 通过 CDN 加载（首屏后缓存）
3. CSS 包含完整 CJK 排版规则
4. 高度通过 `postMessage` bridge 回传 RN 侧

#### 4.2.2 WebView 实例池

```typescript
interface WebViewPool {
    pool: WebViewInstance[];
    maxSize: number;       // 最大预创建数（建议 5）
    acquire(): WebViewInstance;  // 获取空闲实例
    release(instance: WebViewInstance): void;  // 归还
}
```

**关键机制**：
- 首次加载时预创建 3 个空 WebView 实例
- FlatList 回收气泡时，WebView 不销毁，归还至池中
- 新气泡进入视口时，从池中获取实例，通过 `injectJavaScript` 注入新内容
- 池满时按 LRU 策略淘汰最久未使用实例

### 4.3 文字选择与菜单交互方案

#### 4.3.1 问题分析

当前架构的交互冲突：

```
ContextMenu (RN 原生)
  └─ Pressable.onLongPress (200ms 延迟)
       └─ <Markdown> (react-native-markdown-display)
            └─ <Text selectable={true}> (原生文本可选择)
```

**问题**：ContextMenu 的 `onLongPress` 会**拦截** Text 的原生长按选择事件。用户无法选中气泡内的文字。

迁移到 WebView 后，冲突变为：

```
ContextMenu (RN 原生)
  └─ Pressable.onLongPress
       └─ <MarkdownWebView> (WebView)
            └─ HTML <p/selectable> (WebView 内文字)
```

WebView 有自己独立的文本选择机制。如果 RN 的 Pressable.onLongPress 先触发，WebView 内的文本选择将被阻止。

#### 4.3.2 方案对比

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| **A: 放弃 ContextMenu** | 移除 RN ContextMenu，完全依赖 WebView 内原生文字选择 + 系统菜单 | 零穿透问题；文字选择体验最优；复制/分享通过系统菜单完成 | 丢失自定义菜单项（知识图谱、手动向量、触发摘要等）；视觉风格不可定制 |
| **B: 仅保留气泡级 ContextMenu** | ContextMenu 仅包裹气泡外层，不在 WebView 区域上 | 保留消息级操作（删除、重新生成等）；WebView 内文字选择独立 | 需要精确定位触发区域；长按 WebView 区域可能同时触发两个事件 |
| **C: WebView 内自定义菜单** | 在 HTML 中实现自定义右键/长按菜单，通过 bridge 回传操作 | 完全控制菜单样式和内容；无穿透问题 | 实现复杂度高；需要 bridge 双向通信；触觉反馈需额外处理 |
| **D: 延迟判定（推荐）** | ContextMenu 增加延迟（如 500ms），WebView 长按先触发选择；短按触发 ContextMenu | 保留两套交互；无需大幅重构 | 用户需要学习不同长按时长含义；体验不够直觉 |

#### 4.3.3 推荐方案：B + 分区触发

```
气泡布局:
┌──────────────────────────┐
│ [头像] AI 名称 · 时间     │  ← ContextMenu 触发区（onLongPress 200ms）
├──────────────────────────┤
│                          │
│  WebView 渲染区域         │  ← 独立文字选择区（不拦截长按）
│  （Markdown 内容）        │     WebView 内长按 → 文字选择
│                          │     WebView 内选中文字 → 系统复制/全选菜单
│                          │
├──────────────────────────┤
│ [操作按钮: 重新生成 · ...] │  ← 原生 TouchableOpacity
└──────────────────────────┘

气泡外层: ContextMenu 仅包裹头部区域
```

**实现要点**：
1. **ContextMenu 缩小范围**：从包裹整个气泡缩小为仅包裹头部（头像+名称+时间）
2. **WebView 文字选择**：HTML 中所有文本元素设置 `-webkit-user-select: text` + `user-select: text`
3. **消息级操作迁移**：将"删除消息"、"重新生成"等消息级操作移至气泡底部操作栏（原生 TouchableOpacity），不再依赖 ContextMenu
4. **系统原生菜单**：WebView 内文字选中后，Android 系统自动弹出"复制/全选/分享"浮动菜单，无需自定义实现
5. **保留功能**：知识图谱、手动向量、触发摘要等 AI 特有操作保留在气泡底部操作栏

**交互分区映射**：

| 区域 | 交互方式 | 实现层 |
|------|---------|--------|
| 气泡头部（头像+名称） | 长按 → ContextMenu | RN 原生 |
| WebView 内容区域 | 长按 → 文字选择 → 系统菜单 | WebView 内 |
| 气泡底部操作栏 | 点击 → 执行操作 | RN 原生 TouchableOpacity |

#### 4.3.4 "选择文本" 功能处理

当前"选择文本"菜单项打开一个 Modal 显示纯文本内容（`ChatBubble.tsx:528-541`）：

```typescript
// 当前实现
{ label: '选择文本', icon: <Type />, onPress: () => setModalVisible(true) }
// Modal 内: <Typography selectable={true}>{content}</Typography>
```

**Phase 2 处理**：
- **删除该菜单项**：WebView 内直接支持文字选择，不再需要 Modal
- 删除 `setModalVisible` 状态及对应 Modal 组件（约减少 40 行代码）

### 4.4 改动文件清单

| # | 文件 | 操作 | 说明 |
|---|------|------|------|
| 1 | `src/components/chat/MarkdownWebView.tsx` | **新建** | WebView Markdown 渲染组件 |
| 2 | `src/components/chat/WebViewPool.ts` | **新建** | WebView 实例池 |
| 3 | `src/components/chat/markdown-html.ts` | **新建** | HTML 模板 + CSS 样式 + JS bridge |
| 4 | `src/features/chat/components/ChatBubble.tsx` | **修改** | AI 消息区域替换为 MarkdownWebView；ContextMenu 范围缩小 |
| 5 | `src/components/ui/ContextMenu.tsx` | **保留** | 缩小使用范围至气泡头部 |
| 6 | `src/components/chat/MathRenderer.tsx` | **保留** | 行内/块级数学仍由 MarkdownWebView 内 KaTeX 处理，MathRenderer 逐步废弃 |
| 7 | `src/lib/markdown/markdown-utils.ts` | **修改** | 移除 addChineseLineBreaks（CSS 处理） |

### 4.5 WebView HTML 模板核心 CSS

```css
/* CJK 排版规则 */
body {
    font-family: -apple-system, BlinkMacSystemFont, "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
    line-break: strict;
    word-break: break-all;
    overflow-wrap: break-word;
    -webkit-text-size-adjust: 100%;
    text-autospace: ideograph-alpha ideograph-numeric;
}

p {
    margin: 0 0 0.75em 0;
    text-indent: 0;
    hanging-punctuation: first allow-end last;
}

/* 中英文间距 */
span + span,
.english + .cjk,
.cjk + .english {
    /* 通过 JS 后处理添加半角空格 */
}

/* 代码块 */
pre code {
    font-family: "JetBrains Mono", "Fira Code", monospace;
    font-size: 13px;
}

/* KaTeX 公式 */
.katex { font-size: 1em !important; }
.katex-display { margin: 0.5em 0 !important; }
```

### 4.6 流式更新策略

```
流式输出时:
1. MarkdownWebView 接收新内容片段
2. 在 JS 端增量更新 DOM（而非全量替换）
3. 策略: 维护一个 "lastRenderedLength" 游标
   - 仅更新 lastRenderedLength 之后的 HTML
   - 保留已渲染部分的 DOM 状态（包括展开的代码块等）
4. 高度变更通过 MutationObserver + debounce 回传
```

### 4.7 验收标准

- [ ] WebView 内中文排版质量与 Web 端一致
- [ ] 长按 WebView 内容区域能正常选中文字
- [ ] 选中文字后系统菜单正常弹出（复制/全选/分享）
- [ ] 气泡头部 ContextMenu 正常工作
- [ ] 气泡底部操作栏功能正常
- [ ] 滚动流畅，无明显白屏闪烁
- [ ] 流式输出时内容实时更新
- [ ] 内存占用稳定在 30-50 MB
- [ ] 暗色模式正确切换
- [ ] 代码块语法高亮正常
- [ ] 行内数学公式正确渲染

---

## 五、执行路线图

```
Phase 1 (立即, 2-3h)
    │  修复 softbreak / paragraph / addChineseLineBreaks
    │  清理死代码
    ▼
验证 Phase 1 效果
    │  测试 5+ 种中文排版场景
    │  确认改善程度
    ├── 已足够 → 终止，不进入 Phase 2
    │
    └── 仍有问题 → Phase 2 原型 (3-5天)
         │  创建 MarkdownWebView 最小原型
         │  在 1-2 个 AI 气泡中替换测试
         │  验证: 排版质量 + 文字选择 + 滚动流畅度 + 内存
         │
         ├── 原型通过 → 全量迁移 (追加 3-5天)
         │
         └── 原型不通过 → 评估方案 B (全页面 WebView) 或终止
```

---

## 附录 A：技术选型参考

### A.1 Markdown → HTML 转换库（内嵌于 WebView）

| 库 | 大小 | 速度 | 特性 |
|----|------|------|------|
| **marked** | ~40KB | 快 | 简单 API，GFM 支持 |
| **markdown-it** | ~70KB | 中 | 插件生态丰富（已在项目中使用） |
| **snarkdown** | ~3KB | 最快 | 极简，仅基础语法 |

**推荐**：`marked`（体积适中，API 简洁，GFM 内置）。

### A.2 代码高亮

| 库 | 大小 | 特性 |
|----|------|------|
| **Highlight.js** | ~30KB (核心+常用语言) | 语言自动检测，主题丰富 |
| **Prism** | ~20KB (核心+常用) | 轻量，按需加载 |

**推荐**：`Highlight.js`（功能更完整）。

### A.3 CDN 资源（首屏后缓存）

```
https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css
https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js
https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/lib/highlight.min.js
https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/styles/github-dark.min.css
```

### A.4 行业先例

| 应用 | 方案 | 备注 |
|------|------|------|
| ChatGPT (iOS/Android) | 全页面 WebView | 排版完美，首次加载略慢 |
| Claude App | 全页面 WebView | 同上 |
| Cursor | 每气泡 WebView | 接近本方案 C |

---

## 附录 B：风险登记

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| Phase 1 softbreak 修改影响列表渲染 | 中 | 中 | 先在测试环境验证所有列表场景 |
| WebView 实例池首次启动延迟 | 高 | 低 | 异步预创建，不阻塞首屏 |
| Android 低端设备 WebView 内存 OOM | 低 | 高 | 池大小限制为 5；监测内存压力主动回收 |
| 流式更新频率过高导致 WebView 卡顿 | 中 | 中 | 增量 DOM 更新 + 200ms debounce |
| CDN 资源加载失败（无网络时） | 低 | 中 | 将关键资源内嵌为 base64 或本地文件 |
