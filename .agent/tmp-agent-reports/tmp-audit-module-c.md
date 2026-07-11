# Nexara RAG 系统 + 知识图谱系统 深度审计报告（模块 C）

> 审计范围：RAG 检索链路、知识图谱系统、知识库文档系统、配置在业务链路中的生效性。
> 审计方式：只读代码审计，未修改任何文件。
> 报告日期：2026-07-05

---

## 一、总体结论

RAG 系统的**主干链路（文档导入 → 切片 → 向量化 → 检索 → 拼接 prompt → 展示引用）是真实存在且基本打通的**，向量/图谱均持久化到 Room 数据库（非内存），App 重启不丢失。但在**配置生效性、文档生命周期管理、检索质量**三个维度存在多处实质性缺陷，其中最严重的会让用户"调了参数但不生效""删了文档但向量残留""改了 embedding 模型后旧数据全废且无提示"。

**关键数据**：发现 6 个严重问题、7 个中等问题、5 个轻微问题。其中 **Query Rewriter 整套功能是 UI 摆设**（代码存在但从未被调用），**embedDimension / maxEmbedTokensPerCall 是死配置**（存了但 EmbeddingClient 从不读取），**文档删除不清理向量/图谱**，**重新索引不先删除旧向量导致重复**。

---

## 二、严重问题（Critical）

### C1. Query Rewriter 整套功能是"UI 摆设"——开关、策略、数量全都是死配置

**用户视角**：用户在「RAG 高级设置」里打开「查询改写」开关，选择 "multi-query" 策略，把数量设成 5，期望系统会先把他的问题改写成 5 个变体再检索，提升召回率。但实际上**这个开关从来没起过作用**，用户调与不调检索结果完全一样。

**证据**：
- `QueryRewriter` 类存在且实现完整（`app/src/main/java/com/promenar/nexara/data/rag/QueryRewriter.kt:7-12`，支持 HYDE / MULTI_QUERY / EXPANSION 三种策略）。
- 全代码库搜索 `QueryRewriter(`：**唯一一处匹配就是类定义本身**（`QueryRewriter.kt:7`）。没有任何地方实例化它。
- 全代码库搜索 `.rewrite(`（排除 URL/ContextBuilder 等无关项）：**0 处调用**。
- 检索入口 `MemoryManager.retrieveContext()`（`MemoryManager.kt:44-283`）从头到尾**没有调用任何 QueryRewriter**，直接用 `embeddingClient.embedQuery(query)` 对原始 query 向量化。
- UI 配置项完整存在（`AdvancedRetrievalScreen.kt:275-301` 的 enableQueryRewrite 开关、strategy 单选、count 滑块），配置持久化也完整（`RagConfigPersistence.kt:71-99` 的 enableQueryRewrite / queryRewriteStrategy / queryRewriteCount / queryRewriteModel），`MemoryManager.ragConfig` 里也有这些字段（`RagModels.kt:68/74-76`）—— **但 `MemoryManager` 检索时从不读取它们**。

**影响**：用户调整查询改写参数完全无效，相当于一个装饰性功能。这是最典型的"存了但没读取使用"的死配置。

---

### C2. 文档删除时向量与知识图谱数据不清理——残留垃圾数据持续污染检索

**用户视角**：用户在知识库里导入了一个过时的 PDF，发现内容有误，点了「删除」。UI 上文档消失了，用户以为干净了。但实际上**这个文档的向量切片和知识图谱边仍然留在数据库里**，之后每次提问，这些"幽灵文档"的内容依然会被检索出来并拼进 prompt，用户会困惑"我明明删了那个文件，为什么 AI 还在引用它"。

**证据**：
- `RagViewModel.deleteDocuments()`（`RagViewModel.kt:491-500`）只调用 `workspaceRepository.permanentDelete(id)`。
- `WorkspaceRepository.permanentDelete()`（`WorkspaceRepository.kt:157-178`）只做两件事：删物理文件 + 删 `file_entries` 表记录。**完全没有调用 `vectorDao.deleteByDocId()` 或 `kgEdgeDao.deleteByDocId()`**。
- `deleteCollection()`（`RagViewModel.kt:445-465`，删除文件夹）同理，只调 `permanentDelete`。
- 向量表 `vectors` 的 `doc_id` 字段没有任何外键约束指向 `file_entries`（`VectorEntity.kt:9-20` 的外键指向 `SessionEntity`，与文档无关），所以删除文档时数据库层也不会级联清理。
- `kg_edges` 表的 `doc_id` 同样无外键约束（`KgEdgeEntity.kt:9-26`）。
- 对比：重新抽取 KG 时 `GraphExtractor` 会先 `clearGraphForDoc(docId)`（`GraphExtractor.kt:162-164`），说明开发团队知道需要清理，但在删除文档路径上漏了。

**影响**：数据库持续膨胀，检索结果被已删除文档污染，且用户无法感知。

---

### C3. 重新索引不先删除旧向量——同一文档产生重复向量

**用户视角**：用户修改了 chunk size 配置（比如从 800 调到 500），点了「重新索引」想让文档按新切片重建。结果**旧向量没被删，新向量又加进去**，同一文档的内容现在在库里有两套甚至多套向量。检索时同一内容会被多次命中，挤占 topK 名额，用户看到引用列表里出现重复片段。

**证据**：
- `RagViewModel.reindexFile(uuid)`（`RagViewModel.kt:575-591`）、`reindexDocuments(uuids)`（`RagViewModel.kt:594-613`）、首次 `importDocuments`（`RagViewModel.kt:555-560`）全部直接调用 `vectorizationQueue.enqueueDocument()`。
- `VectorizationQueue.processDocumentTask()`（`VectorizationQueue.kt:227-303`）在切片+向量化后直接 `vectorStore.addVectorRecords(records)`（`:279`），**插入前没有调用 `vectorDao.deleteByDocId(docId)`**。
- 唯一会清理旧图谱的是 KG 抽取路径（`GraphExtractor.extractAndSave` 在 `:162` 调 `clearGraphForDoc`），但向量从来不清理。
- 向量主键是 `UUID.randomUUID()`（`VectorStore.kt:53`），所以不会触发 REPLACE 去重，必然产生重复行。

**影响**：重新索引后向量数翻倍，存储浪费，检索质量下降（重复内容挤占名额），且没有任何去重机制。

---

### C4. 切换 Embedding 模型后旧向量维度不匹配——整个知识库静默失效，用户无任何提示

**用户视角**：用户一开始用 OpenAI 的 `text-embedding-3-small`（1536 维）建好了知识库。后来换成另一个 768 维的 embedding 模型。之后每次提问，检索结果都是 0 条，AI 完全不引用任何文档。用户以为"RAG 坏了"但不知道为什么，也无法修复（除非清空全部向量重新索引，但 UI 上没有"重新向量全部"的入口能处理维度迁移）。

**证据**：
- `VectorStore.searchInMemory()`（`VectorStore.kt:109-176`）对每行做 `if (vec.size != queryDim) { dimensionMismatchCount++; continue }`（`:124-127`），维度不匹配的向量**直接跳过且不报错**。
- 诊断日志（`:160-161`）会打印 `⚠️ DIM_MISMATCH` 警告，但这个警告只在 Logcat 和 `onWarning` 回调里，**用户界面上看不到任何提示**。
- 检索结果为空时 `MemoryManager` 返回 `emptyResult`（`MemoryManager.kt:382-386`），上层正常走"无 RAG 上下文"的对话流程，用户只会觉得 AI 没引用文档。
- 没有"检测到维度不一致，是否重新向量化"的引导逻辑。
- `embedDimension` 配置项（`RagModels.kt:108`）虽然存在，但见 C5——它根本没生效。

**影响**：切换模型后知识库整体失效，用户体验上是"RAG 突然不工作了"，且无自愈路径。

---

### C5. `embedDimension` 与 `maxEmbedTokensPerCall` 是死配置——存了但 EmbeddingClient 从不使用

**用户视角**：用户在「RAG 配置」里看到「Embed 维度」和「单次最大 Token」两项，填了维度 768，期望系统在调用 embedding API 时带上 `dimensions=768` 参数做降维。但实际上**这两个值保存到 SharedPreferences 后就再也没被读取过**，API 请求体里根本没有 `dimensions` 字段。

**证据**：
- 配置 UI 存在（`GlobalRagConfigScreen.kt:216-230`），保存逻辑存在（`RagViewModel.kt:341-342` 存入 `embed_dimension` / `max_embed_tokens_per_call`）。
- `NexaraApplication.buildEmbeddingClient()`（`NexaraApplication.kt:310-352`）构造 `EmbeddingClient` 时**只传了 baseUrl/apiKey/model/localEngine，没有传 embedDimension 也没有传 maxEmbedTokensPerCall**。
- `EmbeddingClient.embedBatch()`（`EmbeddingClient.kt:119-155`）构造请求体只有 `model` 和 `input` 两个字段（`:129-132`），**没有 `dimensions` 字段**。
- `embedViaRemote()` 的 `batchSize = 50`（`EmbeddingClient.kt:79`）是硬编码，**完全没读取 `maxEmbedTokensPerCall`**。
- `RagConfiguration.embedDimension` / `maxEmbedTokensPerCall`（`RagModels.kt:108/111`）在整个 `data/rag` 目录里除了模型定义外**0 处使用**。

**影响**：用户调整这两个参数完全无效；且因为 `dimensions` 不下发，所谓"降维"能力是虚假的。

---

### C6. 大文件导入无大小/内存保护——100MB+ 文档会 OOM 崩溃

**用户视角**：用户从云盘选了一个 120MB 的 PDF（比如一本技术手册），点导入。App 直接卡死或闪退，因为代码用 `readBytes()` 把整个文件一次性读进内存，再连同 PDFBox 解析一起堆上，直接 OOM。

**证据**：
- `RagViewModel.importDocuments()`（`RagViewModel.kt:502-572`）对每个 URI 执行：
  - `val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }`（`:517`）—— **整个文件一次性读入内存为 ByteArray**，无大小检查。
  - PDF 路径调 `PdfExtractor.extract(app, uri)`（`:523`），其内部又 `PDDocument.load(inputStream)`（`PdfExtractor.kt:16`）—— 再次全量加载到 PDFBox 内存模型。
  - 失败时回退 `String(bytes, Charsets.UTF_8)`（`:524`）—— 把整个 ByteArray 转字符串，第三次内存峰值。
- 没有任何 `if (bytes.size > MAX) reject` 的保护。
- `PdfExtractor`（`PdfExtractor.kt:8-30`）没有任何页数上限、没有 try-on-OOMLoop 逐页解析，`PDFTextStripper.getText(document)`（`:21`）一次性提取全文。

**影响**：大文档导入必崩；且崩溃发生在协程里，用户只看到导入"没反应"或 App 闪退，没有友好错误提示。

---

## 三、中等问题（Medium）

### M1. 加密 PDF / 扫描件 PDF 处理：错误信息友好但无降级路径

**用户视角**：用户导入一个加密 PDF，导入"成功"了（文件出现在列表里），但向量化静默失败或提取出空文本。用户不知道这个文档其实没建上索引。

**证据**：
- `PdfExtractor.extract()`（`PdfExtractor.kt:12-29`）对加密 PDF 会抛异常被 `catch (e: Exception)` 吞掉返回 `Result.failure(e)`。
- 扫描件（无文本层）会返回 `Result.failure(Exception("PDF 可能为扫描件..."))`（`:23`）。
- 但 `RagViewModel.importDocuments()` 在 PDF 失败时走 `result.getOrNull()?.text ?: String(bytes, Charsets.UTF_8)`（`RagViewModel.kt:524`）—— **失败时回退成把 PDF 的二进制当 UTF-8 字符串解析**，这会产生大量乱码垃圾向量。这个回退逻辑本身就是 bug：PDF 解析失败不应该回退到二进制当文本。
- 没有对加密 PDF 提供"输入密码"的能力。

**影响**：扫描件/加密 PDF 会产生乱码向量污染检索；错误对用户不可见。

---

### M2. summary 类型的向量检索是死代码——永远返回 0 结果

**用户视角**：无（用户感知不到），但这是无效代码，说明 RAG 的"摘要检索"分支是摆设。

**证据**：
- `MemoryManager.retrieveContext()` 会执行一次 `type = "summary"` 的检索（`MemoryManager.kt:126-141`），limit 硬编码 `if (enableRerank) 10 else 5`（`:129`），阈值 `memoryThreshold - 0.05f`（`:130`）。
- 但全代码库搜索 `{"type":"summary"` 写入向量：**0 处**。向量入库只写 `"type":"memory"`（`MemoryManager.kt:321`、`VectorizationQueue.kt:218`）和 `"type":"document"`（`VectorizationQueue.kt:274`）。
- 因此这次 summary 检索每次都查空表，纯浪费一次 DB 查询。

**影响**：每次检索多一次无效 DB 查询；代码维护误导（看起来有摘要检索，实际没有）。

---

### M3. KeywordSearcher（BM25/FTS 分支）的 similarity 全是固定值——排序失真

**用户视角**：用户问的问题里有明确关键词，混合检索会把关键词命中和语义命中融合。但关键词命中的结果相似度全是同一个固定值，排序完全依赖 RRF 的 rank，BM25Boost 参数形同虚设。

**证据**：
- `KeywordSearcher.ftsSearch()`（`KeywordSearcher.kt:30-50`）把每条 FTS 命中都用 `rowToSearchResult(row, 1.0f)`（`:43`）—— **所有命中 similarity 都是 1.0**，没有用 FTS 的 bm25() 分数。
- `fallbackLikeSearch()`（`KeywordSearcher.kt:52-84`）按关键词命中数累加 `score += 1.0f`（`:65`），是简单的计数，也不是 BM25。
- `hybridBM25Boost` 配置（`RagModels.kt:87`）虽然在 RRF 融合里被乘进去（`MemoryManager.kt:353`），但因为没有真实 BM25 分数，boost 只是在缩放一个固定 rank 权重，效果有限。

**影响**：混合检索质量打折；用户调 `hybridBM25Boost` 参数效果不明显。

---

### M4. GraphExtractor 抽取失败的 chunk 会中断整个文档——已抽好的部分不落库

**用户视角**：用户导入一个长文档并开启 KG，抽取到第 5 个 chunk 时网络抖动超时。系统报"3/20 chunks failed"，然后**把已经成功抽取的前 4 个 chunk 的节点/边全部丢弃**（虽然 checkpoint 缓存了 JSON，但没有写入图谱数据库），用户以为重试能续上，但重试其实是从头再来。

**证据**：
- `GraphExtractor.extractAndSave()`（`GraphExtractor.kt:41-204`）循环处理每个 chunk，失败计入 `failCount`（`:101`）。
- 只要 `failCount > 0`，就 `return ExtractionResult(error = "...chunks failed...")`（`:108-114`）—— **直接返回，跳过了后面所有的合并去重、`graphStore.upsertNode`、`createEdge` 落库逻辑**（`:121-199`）。
- checkpoint 文件（`:65-90`）确实缓存了成功的 chunk JSON，重试时能跳过这些 chunk 不重新调 LLM，但**只有当全部 chunk 都成功后才会走到落库逻辑**。
- 这意味着：一个 100 chunk 的文档，99 个成功 1 个失败 = 0 个节点落库。

**影响**：长文档 KG 抽取成功率极低（任何一个 chunk 超时全盘皆输）；checkpoint 机制只省了 LLM 调用成本，没省用户等待时间和成功率。

---

### M5. MemoryManager 配置在 RAG 设置页保存时只重建 MemoryManager，不重建 MicroGraphExtractor / VectorizationQueue / GraphExtractor

**用户视角**：用户在 RAG 设置页改了 chunk size、KG 超时时间等参数。其中影响检索的（memoryLimit/threshold 等）会通过 `rebuildMemoryManager` 生效，但**影响向量化和 KG 抽取的参数（docChunkSize、chunkOverlap、kgExtractionTimeoutSeconds）在下次 App 重启前不会生效**。

**证据**：
- `RagViewModel.saveConfig()`（`RagViewModel.kt:297-348`）末尾只调 `app.rebuildMemoryManager()`（`:347`）。
- `NexaraApplication` 里：
  - `rebuildMemoryManager()`（`:479-481`）只置空 `_memoryManager`。
  - **没有** `rebuildVectorizationQueue()`，`_vectorizationQueue` 只在 provider 变更时置空（`:411/:415/:431/:573`），不在 RAG 配置变更时刷新。
  - `rebuildGraphExtractor()`（`:528-530`）存在，但 `saveConfig` **从不调用它**。
  - `MicroGraphExtractor` 也没有在 RAG 配置变更时重建。
- 后果：用户改了 `kgExtractionTimeoutSeconds`，但 `graphExtractor` 是懒加载且缓存的（`:504-526`），用的是旧超时；改了 `docChunkSize`，新导入的文档仍按旧值切片（因为 `_vectorizationQueue` 没重建，持有的还是构造时的默认 `RagConfiguration()`）。

**补充**：`VectorizationQueue` 构造时 `ragConfig` 参数有默认值 `RagConfiguration()`（`VectorizationQueue.kt:18`），而 `NexaraApplication` 构造它时**没传 ragConfig**（`NexaraApplication.kt:534-541`），所以 VectorizationQueue 永远用全默认值，即使用户改了 docChunkSize/chunkOverlap/memoryChunkSize 也不生效（除非重启 App）。

**影响**：切片参数和 KG 抽取参数对已运行进程不生效；只有重启 App 才会重新读取。

---

### M6. VectorizationQueue 串行处理 + 失败后停止后续——批量导入体验差

**用户视角**：用户一次性选了 20 个文档导入。队列串行处理，如果第 3 个文档因为 embedding API 限流失败，重试 3 次后这个任务标记 failed，**之后队列会继续**（这点 OK），但失败任务在 UI 上停留 2 秒就移除（`VectorizationQueue.kt:160`），用户可能来不及看清是哪个文件失败了。

**证据**：
- `processNext()`（`VectorizationQueue.kt:90-178`）一次只处理 `queue[0]`，串行。
- 失败后 `delay(2000)` 再移除（`:160`），错误信息写入 `task.error` 但只在那个 2 秒窗口可见。
- 失败的任务从 DB 删除（`:119` 在成功路径调 `removeTaskFromDb`；失败路径 `:155` 调 `saveTaskToDb` 保留，但 `finally` 块 `:163-165` 会从内存 queue 移除，下一次 `processNext` 不会再处理它）—— **失败任务不会自动重试到下一轮，必须用户手动 reindex**。
- 重试只针对网络/超时类错误（`:125-128`），且最多 3 次（`MAX_RETRIES=3`，`:453`）。

**影响**：批量导入时单个失败需要用户手动定位并重试；进度可见性不足。

---

### M7. 检索结果的"引用展示"只显示前 8 位 docId——用户无法识别引用来源

**用户视角**：用户问了一个问题，AI 引用了知识库内容，引用列表里显示来源是"文档: a3f2b1c9"（一串无意义 hash）。用户根本不知道这是哪个文档，因为显示的是 docId（UUID）的前 8 位而不是文档标题。

**证据**：
- `MemoryManager.retrieveContext()` 构造 `RagReference` 时（`MemoryManager.kt:246-267`），source 标签是 `"文档: ${fileUuid ?: r.docId.take(8)}"`（`:256`）—— 从 metadata 里取 `fileUuid` 的前 8 位，没有反查文档标题。
- `vectors` 表里只存 `doc_id`，不存文档标题；`RagViewModel.importDocuments` 入库时 metadata 是 `{"type":"document","fileUuid":"$docId","chunkIndex":$i}`（`VectorizationQueue.kt:274`），也没有标题。
- 要显示标题需要回查 `file_entries` 表，但检索链路里没有这一步。

**影响**：用户无法从引用列表识别文档来源，RAG 的"可溯源性"打折。

---

## 四、轻微问题（Minor）

### L1. `RecursiveCharacterTextSplitter` 与 `TrigramTextSplitter` 并存，前者几乎不用

**证据**：`RecursiveCharacterTextSplitter`（`TextSplitter.kt`）在 `NexaraApplication.textSplitter`（`:483-485`）和 `ImageService` 中使用，但实际向量化用的是 `TrigramTextSplitter`（`VectorizationQueue.kt:192/238`、`MemoryManager.kt:302`）。两套 splitter 逻辑不一致，维护成本高。

---

### L2. `GraphExtractor.resolveType` 的 knownTypes 与 prompt 里的 entity types 不一致

**证据**：`GraphStore.resolveType()`（`GraphStore.kt:219-229`）的 `knownTypes = setOf("concept", "person", "org", "location", "event", "product")`，但 `DEFAULT_KG_PROMPT`（`GraphExtractor.kt:358-364`）定义的类型是 `person / organization / location / event / item / concept`。`"organization"` vs `"org"`、`"item"` vs `"product"` 不一致，会导致 resolveType 判定异常。

---

### L3. `VectorizationTaskEntity` 的外键语义错误

**证据**：`VectorizationTaskEntity`（`VectorizationTaskEntity.kt:9-20`）对 `SessionEntity.session_id` 建了外键 + CASCADE，但文档类任务（type="document"）的 `sessionId` 永远是 null。这个外键对文档任务毫无意义，且语义上 `vectorization_tasks` 不应强绑定到 session。

---

### L4. `KeywordSearcher` query 截断为 60 字符但 FTS 未做分词预处理

**证据**：`KeywordSearcher.search()`（`KeywordSearcher.kt:21`）把 query 截断到 60 字符直接丢给 FTS `MATCH`。中文长 query 直接 MATCH 容易因 FTS 分词/操作符问题抛异常，然后回退到 `fallbackLikeSearch`（全表 `getAll()` 扫描，`:60`），性能差。

---

### L5. `GraphStore.createEdge` 的 weight 累加逻辑在重新抽取时已被 `clearGraphForDoc` 规避，但 JIT 路径仍会累加

**证据**：`GraphStore.createEdge()`（`GraphStore.kt:103-137`）发现已存在相同边时 `weight + weight`（`:116`）。全量抽取前会 `clearGraphForDoc`（正确），但 JIT 抽取（`MicroGraphExtractor.backgroundMerge`，`MicroGraphExtractor.kt:178-211`）不会清理，同一会话多次 JIT 抽取相同实体关系会导致 weight 不断累加，图谱权重失真。

---

## 五、跨维度专项分析

### 5.1 检索启用逻辑——开关基本可信，但 KG 开关有两套来源易混淆

- **RAG 总开关**：会话级 `RagOptions.enableMemory` / `enableDocs`（`ChatModels.kt:246-247`）控制是否检索，`ContextBuilder.performRagRetrieval()`（`ContextBuilder.kt:254-255`）在两者都为 false 时直接返回空——**开关真实生效**。
- **KG 开关**：`enableKnowledgeGraph` 在 `RagOptions` 里是 `Boolean?`（可空，`ChatModels.kt:251`），`ContextBuilder`（`ContextBuilder.kt:67`）用 `(params.session.ragOptions ?: tempRagOptions).enableKnowledgeGraph == true` 判断——null 视为关闭。但全局配置里 `enableKnowledgeGraph` 默认 false（`RagModels.kt:67`），且 `getDefaultRagOptions()`（`ChatViewModel.kt:1722-1733`）把它传进会话默认值。**逻辑可工作但可空类型容易引入误判**。
- **Rerank 开关**：需要 `options.enableRerank && effectiveConfig.enableRerank && rerankClient != null` 三者同时为真（`MemoryManager.kt:70`）。用户在会话级关了 rerank 会真实生效。**可信**。
- **未发现"开了不检索"或"关了还在检索"的 bug**（在开关本身的层面）。

### 5.2 检索质量

- **Embedding 维度**：跟随模型（text-embedding-3-small=1536），无降维（C5）。
- **相似度计算**：cosine，实现正确（`VectorStore.kt:178-189`），维度不匹配返回 0（静默丢弃，C4）。
- **Rerank**：真实生效，支持 API rerank 和 LLM 兜底打分（`Reranker.kt`），分批处理正确（`:54-66`）。但 `rerankFinalK` 默认 5（`RagModels.kt:85`），最终只取 5 条，对于大知识库偏少。
- **topK**：`rerankTopK=30` 召回候选，`rerankFinalK=5` 最终——可调且生效。
- **混合检索**：RRF fusion（`MemoryManager.kt:332-365`）实现合理，但 keyword 侧分数失真（M3）。

### 5.3 图谱真实性——参与检索，但只是"二次增强"，非核心

- **KG 在检索中的角色**：`ContextBuilder` 在 RAG 检索拿到 references 后，如果 KG 开启，调 `kgProvider.extractContext()`（`ContextBuilder.kt:68-78`）做 **JIT（即时）微图谱抽取**——把检索到的 topK 片段再喂给 LLM 抽实体关系，拼成 `## Knowledge Graph Relations` 塞进 system prompt（`ContextBuilder.kt:347-352`）。
- **结论**：知识图谱**确实参与检索增强**（不是纯可视化摆设），但它的参与方式是"对已检索片段做二次 LLM 抽取"，而不是"从预建图谱库里查询相关实体"。预建的文档级图谱（`GraphExtractor.extractAndSave` 落库的节点/边）**只用于可视化**（`KnowledgeGraphViewModel.loadGraph` → `graphStore.getGraphData`），**不参与检索 prompt 构建**。
- **影响**：用户花时间建好的文档级 KG，实际检索时没用上；真正参与检索的是 JIT 即时抽取的临时图谱。这是一个"认知错位"——用户以为预建图谱在增强检索，其实没有。

### 5.4 持久化——向量/图谱均持久化到 Room，可靠

- `vectors`、`kg_nodes`、`kg_edges`、`vectorization_tasks`、`kg_jit_cache` 均为 Room Entity（`data/local/db/entity/`），持久化到 SQLite。**App 重启不丢失**。
- 向量以 `ByteArray`（ByteBuffer）存储（`VectorStore.kt:16-20`），FTS 通过 `@Fts4(contentEntity=...)` 自动同步（`VectorFtsEntity.kt`）。
- 向量化任务有 `resumeInterruptedTasks()`（`VectorizationQueue.kt:379-416`）恢复中断任务。

### 5.5 可视化——检索过程有进度展示，但检索后无"诊断面板"

- **检索进度**：`ChatViewModel` 把 `onRagProgress` 映射成 `RagPhase`（embed/memory/docs/hybrid/rank/rerank/kg/ready，`ChatViewModel.kt:330-389`），用户能看到分阶段进度条。**这部分做得不错**。
- **检索结果展示**：`ragReferences` 会挂在 message 上展示（`MessageManager.kt:123`），但只显示 score 和 source（前 8 位 UUID，见 M7），**不显示 similarity 数值、不显示是否经过 rerank、不显示被阈值过滤了多少**。
- **诊断信息**：`RagDebugScreen.kt` 存在，但依赖 `trackRetrievalMetrics` 开关，默认关（`RagModels.kt:97`）。普通用户看不到 dim mismatch / below threshold 等关键诊断。

### 5.6 硬编码汇总

| 位置 | 硬编码值 | 说明 |
|---|---|---|
| `EmbeddingClient.kt:79` | `batchSize = 50` | embedding 每批文本数，忽略 `maxEmbedTokensPerCall` |
| `MemoryManager.kt:129` | `if (enableRerank) 10 else 5` | summary 检索 limit，且该检索是死代码（M2） |
| `MemoryManager.kt:130` | `memoryThreshold - 0.05f` | summary 阈值偏移，魔法数 |
| `MemoryManager.kt:337` | `rrfK = 60` | RRF 常数，行业标准，可接受 |
| `GraphExtractor.kt:14-18` | `chunkSize=1200, chunkOverlap=200, timeoutMs=120000` | 构造默认值，但实际被 `loadFullConfig` 覆盖（NexaraApplication.kt:522-524），非问题 |
| `KeywordSearcher.kt:21` | `60` 字符截断 | query 截断长度 |
| `KeywordSearcher.kt:43` | `1.0f` | FTS 命中固定分数（M3） |
| `ContextBuilder.kt:343` | `ref.content.take(400)` | 引用预览截断 400 字符 |

---

## 六、修复优先级建议

| 优先级 | 问题编号 | 一句话修复方向 |
|---|---|---|
| P0 | C2 | `deleteDocuments` / `permanentDelete` 增加 `vectorDao.deleteByDocId` + `kgEdgeDao.deleteByDocId` + `kgNodeDao.deleteOrphanNodes` |
| P0 | C3 | `processDocumentTask` 入口先 `vectorDao.deleteByDocId(docId)` 再切片 |
| P0 | C6 | 导入前校验文件大小，大文件分块读取或拒绝并提示 |
| P0 | C4 | 检测到 dim mismatch 时向用户弹出"模型已变更，需重新索引"引导 |
| P1 | C5 | `EmbeddingClient` 读取 `embedDimension`/`maxEmbedTokensPerCall`，请求体加 `dimensions` |
| P1 | C1 | `MemoryManager.retrieveContext` 接入 `QueryRewriter`，或在 UI 上明确标注"该功能暂未启用" |
| P1 | M5 | `saveConfig` 末尾增加 `rebuildVectorizationQueue` + `rebuildGraphExtractor` |
| P2 | M4 | GraphExtractor 允许部分成功落库（已成功的 chunk 先 upsert） |
| P2 | M1 | PDF 解析失败不应回退到二进制当文本；改为拒绝导入并提示 |
| P2 | M7 | 检索引用回查 `file_entries.name` 显示文档标题 |
| P3 | M2/M3/L1-L5 | 清理死代码、统一 splitter、修复类型枚举不一致等 |

---

## 七、审计方法说明

本报告基于对以下文件的逐行只读审查：
- `data/rag/` 全部 21 个文件
- `ui/rag/` 全部 17 个文件（含 ViewModel、Screen、canvas、components）
- `data/repository/VectorRepository.kt`、`KnowledgeGraphRepository.kt`
- `data/repository/WorkspaceRepository.kt`（验证删除链路）
- `domain/usecase/RagConfigPersistence.kt`
- `data/local/db/` 下 Vector / Kg / VectorizationTask 相关 entity + dao + FTS
- `ui/chat/ChatViewModel.kt`（RAG 触发与配置缓存逻辑）
- `ui/chat/manager/ContextBuilder.kt`（检索如何接入对话）
- `NexaraApplication.kt`（RAG 组件的依赖注入与重建逻辑）

所有"死配置""未调用"结论均通过全代码库 grep 交叉验证（非单文件推断）。文件:行号均可在报告中直接定位。
