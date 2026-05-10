# 本地模型功能补完 — 会话拆分与提示词

> **生成时间**: 2026-05-10
> **基线方案**: `.agent/plans/20260510-local-model-implementation.md`
> **工作区**: `/Users/promenar/Codex/Nexara/native-ui`
> **技术选型**: llama.cpp + JNI + GGUF 格式 + Vulkan GPU 加速
> **架构策略**: 从 S-2 起通过 `InferenceBackend` 抽象接口解耦后端，为远期 ggml-hexagon NPU 加速和 ExecuTorch QNN 预留插拔式扩展点

---

## 三阶段演进路线

```
Phase A (当前 7 会话, S-1→S-6):
  ┌──────────────────────────────────────────┐
  │ LlamaCppBackend (implements InferenceBackend) │
  │ ├── Vulkan GPU (8-14 tok/s) ← 旗舰设备默认   │
  │ └── CPU NEON  (4-8 tok/s)  ← 中低端降级     │
  │ 模型格式: GGUF (HuggingFace 直接下载)        │
  │ 设备覆盖: 100% (Android 5.0+)               │
  └──────────────────────────────────────────┘

Phase B (远期, ~2026 Q3-Q4, ggml-hexagon 合并主线后):
  ┌──────────────────────────────────────────┐
  │ LlamaHexagonBackend (implements InferenceBackend) │
  │ └── Hexagon NPU (预估 12-22 tok/s)        │
  │ 模型格式: GGUF (相同文件, 零切换成本!)      │
  │ 硬件要求: Snapdragon 8 Gen 2+ (v73+)      │
  │ 改动量: 仅新增一个 Backend 实现类           │
  └──────────────────────────────────────────┘

Phase C (远期可选, 高级用户可手动下载 .pte):
  ┌──────────────────────────────────────────┐
  │ ExecuTorchBackend (implements InferenceBackend)  │
  │ └── QNN NPU (18-25 tok/s, 当前最快)       │
  │ 模型格式: .pte (需从 HuggingFace 额外导出)  │
  │ 高级用户选项, 需单独下载模型文件             │
  └──────────────────────────────────────────┘
```

---

## 概述

本方案将本地模型补完实施拆分为 **7 个独立会话**，每个会话：
- 附带**即复制即用的提示词**，新会话直接粘贴即可启动
- 操作 **1~4 个文件**，改动范围可控
- 含**验收标准与验证指令**
- **标注与前后会话的依赖关系**

### 关键架构决策：InferenceBackend 抽象

从 S-2 起，`LocalInferenceEngine` 不直接持有 `LlamaContext`，而是持有 `InferenceBackend` 接口引用。这使得：
- 当前：`LlamaCppBackend` 封装 llama.cpp 调用
- 远期：只需新增 `LlamaHexagonBackend` 或 `ExecuTorchBackend` 实现类，上层代码零改动

### 会话依赖总览

```
S-1 (JNI构建 + LlamaContext + InferenceBackend接口) ── 2-3 天 ── 无依赖
    │
    ├── S-2 (引擎核心 + LlamaCppBackend + 三槽位) ── 3-4 天 ── 依赖 S-1
    │       │
    │       └── S-4 (协议集成 LocalProtocol) ── 2-3 天 ── 依赖 S-2
    │               │
    │               ├── S-5A (ModelsScreen+ViewModel) ── 2 天 ── 依赖 S-3+S-4
    │               │       │
    │               │       └── S-6 (Application+生命周期) ── 1-2 天 ── 依赖 S-5A+S-5B
    │               │
    │               └── S-5B (设置开关+Provider入口) ── 1 天 ── 依赖 S-4
    │                       │
    │                       └── S-6（同上）
    │
    └── S-3 (模型管理 Storage+下载+解析) ── 2-3 天 ── 依赖 S-1，与 S-2 并行！
            │
            └── S-5A（同上）
```

### 并行执行建议

| 并行组 | 会话 | 可同时启动时间 |
|--------|------|---------------|
| **组 A** | S-2 + S-3 | S-1 完成后立即并行 |
| **组 B** | S-5A + S-5B | S-3 + S-4 均完成后并行 |
| **串行链** | S-1 → 等组A → S-4 → 等组B → S-6 | 关键路径 |

> **预计总日历日**：约 10-12 天（充分利用并行度，关键路径为 S-1→S-2→S-4→S-5A→S-6）

---

## Session S-1：JNI 构建链路 + LlamaContext + InferenceBackend 接口

> **状态**: 无依赖 · 立即可启动
> **预估**: 2-3 天
> **关键产出**: `cpp/` 目录 + `LlamaContext.kt` + `InferenceBackend.kt` 接口 + 冒烟测试通过

---

**涉及文件**：
- `app/build.gradle.kts` — 新增 NDK + CMake 配置
- `app/CMakeLists.txt` — ← 新建：NDK 构建配置
- `cpp/native-lib.cpp` — ← 新建：JNI 桥接实现
- `data/local/inference/LlamaContext.kt` — ← 新建：Kotlin JNI 包装类
- `data/local/inference/InferenceBackend.kt` — ← 新建：推理后端抽象接口（~30行，为 NPU 可插拔铺路）
- （可选）`data/local/inference/GpuDetector.kt` — ← 新建：Vulkan 可用性检测

---

### 📋 复制以下提示词到新会话：

```
【任务】为 Nexara native-ui 项目建立 llama.cpp 的 Android JNI 构建链路，
并实现 Kotlin 侧的 LlamaContext 封装类。

【项目背景】
- 工作区: /Users/promenar/Codex/Nexara/native-ui
- 包名: com.promenar.nexara
- 当前项目是纯 Kotlin/Jetpack Compose Android 项目，使用 Gradle KTS
- build.gradle.kts 已有 NDK abiFilters 配置 (arm64-v8a, armeabi-v7a)
- 目标: 引入 llama.cpp 作为端侧 LLM 推理引擎，通过 JNI 桥接

【技术方案】
选择 llama.cpp 而非 MediaPipe/ExecuTorch，因为:
1. GGUF 格式无需模型转换，HuggingFace 直接下载
2. 运行时仅 ~4MB，不显著增加 APK 体积
3. 社区生态最丰富，支持 LLaMA/Mistral/Qwen/Gemma 等所有主流架构

【操作步骤】

═══════════════════════════════════════
【步骤 1】修改 build.gradle.kts — 添加 NDK + CMake 配置
═══════════════════════════════════════

在 android {} 块中添加 externalNativeBuild 和 sourceSets 配置:

```kotlin
android {
    // ... 已有配置 ...

    defaultConfig {
        // ... 已有配置 ...
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // ← 新增: CMake 构建配置
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // ← 新增: 指定 JNI libs 路径
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}
```

═══════════════════════════════════════
【步骤 2】创建 CMakeLists.txt
═══════════════════════════════════════

文件路径: app/src/main/cpp/CMakeLists.txt

关键内容:
- 从 llama.cpp 官方仓库 git clone 源码（或使用 FetchContent）
- 编译 llama 共享库 (libllama.so)
- 编译 JNI 桥接库 (libnexara_llama.so)
- 启用 Vulkan 支持 (GGML_VULKAN=ON)
- 启用 NEON 优化 (GGML_CPU_ARM_ARCH=armv8-a)

推荐使用 FetchContent 方式拉取 llama.cpp 源码（避免手动管理 submodule）:

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("nexara_llama")

# 方式A: FetchContent 从 GitHub 拉取
include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp
    GIT_TAG        master  # 或指定稳定 tag
)
FetchContent_MakeAvailable(llama_cpp)

# 方式B (备选): 直接使用预编译 .so 文件放到 jniLibs/ 目录
# 推荐先用方式B快速验证，后续再切换到方式A源码编译

# 编译 JNI 桥接库
add_library(nexara_llama SHARED native-lib.cpp)
target_link_libraries(nexara_llama llama)
```

如果 FetchContent 遇到网络问题，备选方案：从 https://github.com/ggml-org/llama.cpp/releases 下载 Android 预编译 .so，放到 `app/src/main/jniLibs/{abi}/libllama.so`。

═══════════════════════════════════════
【步骤 3】编写 JNI 桥接层 (native-lib.cpp)
═══════════════════════════════════════

文件路径: app/src/main/cpp/native-lib.cpp

需要实现的 JNI 函数（包名映射: com.promenar.nexara.data.local.inference.LlamaContext）:

```cpp
#include <jni.h>
#include <string>
#include "llama.h"

// 全局状态管理
struct LlamaSession {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    bool gpu_offloaded = false;
};

// 1. 模型加载
extern "C" JNIEXPORT jlong JNICALL
Java_com_promenar_nexara_data_local_inference_LlamaContext_nativeLoadModel(
    JNIEnv *env, jobject /* this */,
    jstring modelPath, jint nCtx, jint nThreads, jboolean useGpu) {
    
    auto *session = new LlamaSession();
    
    // 初始化 llama 后端
    llama_backend_init();
    
    // 模型参数
    llama_model_params model_params = llama_model_default_params();
    if (useGpu) {
        model_params.n_gpu_layers = 999; // offload all layers to GPU
    }
    
    // 加载模型
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    session->model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);
    
    if (!session->model) return 0; // 失败返回 0
    
    // 上下文参数
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx;
    ctx_params.n_threads = nThreads;
    
    session->ctx = llama_new_context_with_model(session->model, ctx_params);
    session->vocab = llama_model_get_vocab(session->model);
    session->gpu_offloaded = useGpu;
    
    return reinterpret_cast<jlong>(session);
}

// 2. Tokenize
extern "C" JNIEXPORT jintArray JNICALL
Java_com_promenar_nexara_data_local_inference_LlamaContext_nativeTokenize(
    JNIEnv *env, jobject, jlong ptr, jstring text, jboolean addBos) {
    
    auto *session = reinterpret_cast<LlamaSession*>(ptr);
    const char *str = env->GetStringUTFChars(text, nullptr);
    
    // 使用 llama_tokenize 获取 token 数组
    int n_tokens = llama_n_vocab(session->vocab); // 预估值
    std::vector<llama_token> tokens(n_tokens);
    
    n_tokens = llama_tokenize(session->vocab, str, strlen(str),
                               tokens.data(), tokens.size(),
                               addBos ? llama_token_get_bos(session->vocab) : LLAMA_TOKEN_NULL,
                               true);
    
    env->ReleaseStringUTFChars(text, str);
    
    jintArray result = env->NewIntArray(n_tokens);
    env->SetIntArrayRegion(result, 0, n_tokens, reinterpret_cast<jint*>(tokens.data()));
    return result;
}

// 3. 单步推理解码（自回归循环的核心）
extern "C" JNIEXPORT jint JNICALL
Java_com_promenar_nexara_data_local_inference_LlamaContext_nativeDecode(
    JNIEnv *env, jobject, jlong ptr, jint token) {
    
    auto *session = reinterpret_cast<LlamaSession*>(ptr);
    
    // 构造单个 token 的 batch
    llama_batch batch = llama_batch_init(1, 0, 1);
    batch.token[0] = token;
    batch.n_tokens = 1;
    
    if (llama_decode(session->ctx, batch) != 0) {
        return -1; // 解码失败
    }
    
    // Sample 下一个 token（greedy）
    int n_vocab = llama_n_vocab(session->vocab);
    const float *logits = llama_get_logits_ith(session->ctx, 0);
    
    // 简单 greedy sampling（生产环境可替换为 temperature/top-p sampling）
    llama_token next_token = 0;
    float max_logit = -INFINITY;
    for (int i = 0; i < n_vocab; i++) {
        if (logits[i] > max_logit) {
            max_logit = logits[i];
            next_token = i;
        }
    }
    
    return next_token;
}

// 4. 批量解码（一次性 ingest prompt tokens）
extern "C" JNIEXPORT void JNICALL
Java_com_promenar_nexara_data_local_inference_LlamaContext_nativeIngestPrompt(
    JNIEnv *env, jobject, jlong ptr, jintArray tokens) {
    
    auto *session = reinterpret_cast<LlamaSession*>(ptr);
    
    jsize len = env->GetArrayLength(tokens);
    jint *token_arr = env->GetIntArrayElements(tokens, nullptr);
    
    // 分批 eval prompt（处理长 prompt）
    int n_batch = llama_n_batch(session->ctx);
    for (int i = 0; i < len; i += n_batch) {
        int batch_size = std::min(n_batch, len - i);
        llama_batch batch = llama_batch_init(batch_size, 0, 1);
        for (int j = 0; j < batch_size; j++) {
            batch.token[j] = token_arr[i + j];
            batch.pos[j] = i + j;
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
        }
        batch.n_tokens = batch_size;
        llama_decode(session->ctx, batch);
    }
    
    env->ReleaseIntArrayElements(tokens, token_arr, 0);
}

// 5. Detokenize
extern "C" JNIEXPORT jstring JNICALL
Java_com_promenar_nexara_data_local_inference_LlamaContext_nativeDetokenize(
    JNIEnv *env, jobject, jlong ptr, jintArray tokens) {
    
    auto *session = reinterpret_cast<LlamaSession*>(ptr);
    jsize len = env->GetArrayLength(tokens);
    jint *token_arr = env->GetIntArrayElements(tokens, nullptr);
    
    std::string result;
    std::vector<llama_token> token_vec(token_arr, token_arr + len);
    
    // 逐 token detokenize
    std::vector<char> buf(256);
    for (auto tok : token_vec) {
        int n = llama_token_to_piece(session->vocab, tok, buf.data(), buf.size(), 0, true);
        if (n < 0) {
            buf.resize(-n);
            n = llama_token_to_piece(session->vocab, tok, buf.data(), buf.size(), 0, true);
        }
        if (n > 0) result.append(buf.data(), n);
    }
    
    env->ReleaseIntArrayElements(tokens, token_arr, 0);
    return env->NewStringUTF(result.c_str());
}

// 6. Embedding
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_promenar_nexara_data_local_inference_LlamaContext_nativeEmbed(
    JNIEnv *env, jobject, jlong ptr, jstring text) {
    
    auto *session = reinterpret_cast<LlamaSession*>(ptr);
    const char *str = env->GetStringUTFChars(text, nullptr);
    
    // Tokenize
    std::vector<llama_token> tokens(256);
    int n_tokens = llama_tokenize(session->vocab, str, strlen(str),
                                   tokens.data(), tokens.size(),
                                   LLAMA_TOKEN_NULL, true);
    env->ReleaseStringUTFChars(text, str);
    
    // Ingest
    int n_batch = llama_n_batch(session->ctx);
    for (int i = 0; i < n_tokens; i += n_batch) {
        int bs = std::min(n_batch, n_tokens - i);
        llama_batch batch = llama_batch_init(bs, 0, 1);
        for (int j = 0; j < bs; j++) {
            batch.token[j] = tokens[i + j];
            batch.pos[j] = i + j;
            batch.n_seq_id[j] = 1;
        }
        batch.n_tokens = bs;
        llama_decode(session->ctx, batch);
    }
    
    // 取最后一层 hidden state 平均值作为 embedding
    int n_embd = llama_n_embd(session->model);
    std::vector<float> embedding(n_embd, 0.0f);
    
    for (int t = 0; t < n_tokens; t++) {
        const float *embd = llama_get_embeddings_ith(session->ctx, t);
        for (int i = 0; i < n_embd; i++) {
            embedding[i] += embd[i] / n_tokens;
        }
    }
    
    jfloatArray result = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(result, 0, n_embd, embedding.data());
    return result;
}

// 7. 获取 EOS token
extern "C" JNIEXPORT jint JNICALL
Java_com_promenar_nexara_data_local_inference_LlamaContext_nativeGetEosToken(
    JNIEnv *env, jobject, jlong ptr) {
    auto *session = reinterpret_cast<LlamaSession*>(ptr);
    return llama_token_eos(session->vocab);    // 新版 API
    // 如果是旧版: return llama_token_eos(session->model);
}

// 8. 释放
extern "C" JNIEXPORT void JNICALL
Java_com_promenar_nexara_data_local_inference_LlamaContext_nativeFree(
    JNIEnv *, jobject, jlong ptr) {
    auto *session = reinterpret_cast<LlamaSession*>(ptr);
    if (session->ctx) llama_free(session->ctx);
    if (session->model) llama_free_model(session->model);
    llama_backend_free();
    delete session;
}
```

═══════════════════════════════════════
【步骤 4】创建 Kotlin 封装类 LlamaContext.kt
═══════════════════════════════════════

文件路径: app/src/main/java/com/promenar/nexara/data/local/inference/LlamaContext.kt

```kotlin
package com.promenar.nexara.data.local.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * llama.cpp 的 Kotlin JNI 包装。
 * 所有 native 方法在 IO 线程调用，通过 Dispatchers.IO 桥接。
 */
class LlamaContext private constructor(
    private val nativePtr: Long,
    val modelPath: String,
    val contextSize: Int,
    val gpuAccelerated: Boolean
) {
    companion object {
        init {
            System.loadLibrary("nexara_llama")
        }

        /**
         * 加载 GGUF 模型文件
         * @return LlamaContext 或 null（加载失败）
         */
        suspend fun load(
            modelPath: String,
            contextSize: Int = 2048,
            nThreads: Int = Runtime.getRuntime().availableProcessors(),
            useGpu: Boolean = false
        ): LlamaContext? = withContext(Dispatchers.IO) {
            val ptr = nativeLoadModel(modelPath, contextSize, nThreads, useGpu)
            if (ptr == 0L) null
            else LlamaContext(ptr, modelPath, contextSize, useGpu)
        }

        // ── Native 方法声明 ──
        private external fun nativeLoadModel(
            modelPath: String, nCtx: Int, nThreads: Int, useGpu: Boolean
        ): Long
    }

    // ── Tokenize / Detokenize ──
    suspend fun tokenize(text: String, addBos: Boolean = true): IntArray =
        withContext(Dispatchers.IO) {
            nativeTokenize(nativePtr, text, addBos)
        }

    suspend fun detokenize(tokens: IntArray): String =
        withContext(Dispatchers.IO) {
            nativeDetokenize(nativePtr, tokens)
        }

    // ── 核心推理 ──
    /**
     * 将 prompt tokens 一次性 ingest 到 KV cache
     */
    suspend fun ingestPrompt(tokens: IntArray) {
        withContext(Dispatchers.IO) {
            nativeIngestPrompt(nativePtr, tokens)
        }
    }

    /**
     * 单步解码：输入一个 token，返回下一个 token
     * @return 下一个 token ID，或 -1 表示解码失败
     */
    suspend fun decode(token: Int): Int = withContext(Dispatchers.IO) {
        nativeDecode(nativePtr, token)
    }

    // ── 便捷方法 ──
    /**
     * 流式生成：从 prompt 开始自回归生成，通过 Flow 逐 token 输出
     */
    fun generate(prompt: String, maxTokens: Int = 256): Flow<String> = flow {
        // 1. Tokenize prompt
        val promptTokens = tokenize(prompt, addBos = true)
        if (promptTokens.isEmpty()) {
            throw IllegalStateException("Failed to tokenize prompt")
        }

        // 2. Ingest prompt
        ingestPrompt(promptTokens)

        // 3. 自回归生成
        val eosToken = getEosToken()
        var currentToken = promptTokens.last()
        val generatedTokens = mutableListOf<Int>()

        for (i in 0 until maxTokens) {
            val nextToken = decode(currentToken)
            if (nextToken < 0) break // 解码失败
            if (nextToken == eosToken) break

            generatedTokens.add(nextToken)
            val text = detokenize(intArrayOf(nextToken))
            if (text.isNotEmpty()) emit(text)

            currentToken = nextToken
        }
    }

    // ── Embedding ──
    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        nativeEmbed(nativePtr, text)
    }

    // ── 元信息 ──
    suspend fun getEosToken(): Int = withContext(Dispatchers.IO) {
        nativeGetEosToken(nativePtr)
    }

    // ── 生命周期 ──
    fun release() {
        if (nativePtr != 0L) {
            nativeFree(nativePtr)
        }
    }

    protected fun finalize() {
        release()
    }

    // ── Native 声明 ──
    private external fun nativeTokenize(ptr: Long, text: String, addBos: Boolean): IntArray
    private external fun nativeDetokenize(ptr: Long, tokens: IntArray): String
    private external fun nativeIngestPrompt(ptr: Long, tokens: IntArray)
    private external fun nativeDecode(ptr: Long, token: Int): Int
    private external fun nativeEmbed(ptr: Long, text: String): FloatArray
    private external fun nativeGetEosToken(ptr: Long): Int
    private external fun nativeFree(ptr: Long)
}
```

═══════════════════════════════════════
【步骤 5】GPU 检测工具
═══════════════════════════════════════

文件路径: app/src/main/java/com/promenar/nexara/data/local/inference/GpuDetector.kt

```kotlin
package com.promenar.nexara.data.local.inference

import android.content.Context
import android.os.Build

object GpuDetector {
    fun supportsVulkan(): Boolean {
        // Android 7.0+ 内置 Vulkan 支持
        // 检查是否有 Vulkan 硬件
        return try {
            val pm = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
                .invoke(null, "ro.hardware.vulkan", "") as String
            pm.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        } catch (_: Exception) {
            // fallback: 根据 SOC 型号判断
            Build.HARDWARE.contains("qcom", ignoreCase = true) ||
            Build.HARDWARE.contains("exynos", ignoreCase = true) ||
            Build.HARDWARE.contains("mt6", ignoreCase = true) // Mediatek Dimensity
        }
    }

    fun recommendedThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            cores >= 8 -> 6      // 旗舰芯片，留 2 核给系统
            cores >= 6 -> 4
            else -> 2            // 低端设备保守
        }
    }
}
```

═══════════════════════════════════════
【步骤 6】创建 InferenceBackend 抽象接口（为 NPU 可插拔铺路）
═══════════════════════════════════════

文件路径: app/src/main/java/com/promenar/nexara/data/local/inference/InferenceBackend.kt

这是一个纯接口文件，体积极小（~30行）。提前创建以明确后续 S-2 的架构契约：

```kotlin
package com.promenar.nexara.data.local.inference

import kotlinx.coroutines.flow.Flow

/**
 * 推理后端抽象接口。
 * 
 * 当前实现: LlamaCppBackend (Vulkan GPU + CPU NEON)
 * 远期实现: LlamaHexagonBackend (Hexagon NPU, ggml-hexagon 合并后)
 * 可选实现: ExecuTorchBackend (QNN NPU, 高级用户可选)
 * 
 * 设计原则: 上层代码 (LocalInferenceEngine, LocalProtocol) 只依赖此接口，
 * 不感知具体后端。切换后端 = 替换一行工厂代码。
 */
interface InferenceBackend {
    
    /** 后端类型标识（用于 UI 展示和日志） */
    val backendType: BackendType
    
    /** 当前是否已加载模型 */
    val isLoaded: Boolean
    
    /**
     * 加载模型
     * @param path 模型文件路径 (GGUF 或 .pte)
     * @param config 加载配置
     * @return Result.success 或 Result.failure
     */
    suspend fun loadModel(path: String, config: LoadConfig): Result<Unit>
    
    /**
     * 流式生成文本
     * @param prompt 已格式化的 prompt 字符串
     * @param config 生成参数
     * @return 逐 token 的文本流
     */
    fun generate(prompt: String, config: GenerateConfig): Flow<String>
    
    /**
     * 文本 Embedding
     * @param text 输入文本
     * @return float32 embedding 向量
     */
    suspend fun embed(text: String): Result<FloatArray>
    
    /**
     * 卸载模型并释放资源
     */
    fun release()
}

/**
 * 后端类型枚举
 */
enum class BackendType(val displayName: String) {
    LLAMA_CPU("llama.cpp CPU"),
    LLAMA_VULKAN("llama.cpp Vulkan"),
    LLAMA_HEXAGON("llama.cpp Hexagon NPU"),
    EXECUTORCH_QNN("ExecuTorch QNN NPU")
}
```

═══════════════════════════════════════
【验收标准】
═══════════════════════════════════════

1. `./gradlew :app:compileDebugKotlin` 通过（包含 NDK 编译）
2. 用 TinyLlama-1.1B (Q4_K_M, ~650MB) 测试:
   - 调用 `LlamaContext.load(path)` 返回非 null
   - `tokenize("Hello")` 返回非空 IntArray
   - `generate("The capital of France is")` 流式输出包含 "Paris"
   - `release()` 后无内存泄漏
3. GPU 检测正确返回设备能力
4. `InferenceBackend` 接口编译通过

【关键风险提醒】
- NDK 编译首次会较慢（需下载 llama.cpp 源码并编译），预计 15-30 分钟
- 如果 FetchContent 因网络失败，改用预编译 .so 放置到 jniLibs/
- 新版 llama.cpp API 变化频繁，注意 GGML_API 宏和 token 类型 (llama_token = int32_t)
- Vulkan 支持需要设备有对应驱动，模拟器通常不支持，务必真机测试

【TS 原版参考】
- ../src/lib/local-inference/LocalModelServer.ts (llama.rn 调用方式)
- ../src/lib/local-inference/ModelStorageManager.ts (模型文件路径管理)
```

---

## Session S-2：InferenceBackend + LlamaCppBackend + LocalInferenceEngine

> **状态**: 依赖 S-1 完成
> **预估**: 3-4 天
> **可并行**: 与 S-3 同时启动
> **关键产出**: `LlamaCppBackend.kt` + 重构 `LocalInferenceEngine.kt`（通过 `InferenceBackend` 接口）+ 三槽位状态机

---

**涉及文件**：
- `data/local/inference/LlamaCppBackend.kt` — ← 新建：实现 `InferenceBackend`，封装 `LlamaContext`
- `data/local/inference/LocalInferenceEngine.kt` — ← 新建：三槽位引擎（通过 `InferenceBackend` 接口操作）
- `data/local/inference/InferenceBackend.kt` — 依赖（S-1 产出）
- `data/local/inference/LlamaContext.kt` — 依赖（S-1 产出）
- `data/local/inference/GpuDetector.kt` — 依赖（S-1 产出）

---

### 📋 复制以下提示词到新会话：

```
【任务】基于 S-1 产出的 InferenceBackend 接口和 LlamaContext，
实现 LlamaCppBackend（封装 llama.cpp 调用）和 LocalInferenceEngine（通过接口管理三槽位）。

【架构关键】引擎不直接持有 LlamaContext，而是通过 InferenceBackend 接口操作。
这意味着远期只需新增 LlamaHexagonBackend/ExecuTorchBackend 实现类即可获得 NPU 加速，
上层代码零改动。

【前置依赖】
- S-1 已完成：InferenceBackend.kt + LlamaContext.kt + JNI 桥接层 + GpuDetector.kt
- 可直接 import com.promenar.nexara.data.local.inference.*

【项目背景】
- 工作区: /Users/promenar/Codex/Nexara/native-ui
- 包名: com.promenar.nexara
- TS 原版参考: ../src/lib/local-inference/LocalModelServer.ts (三槽位 Zustand Store)

【操作步骤】

═══════════════════════════════════════
【步骤 1】创建 LlamaCppBackend + LocalInferenceEngine (含 Backend 抽象)
═══════════════════════════════════════

文件路径: app/src/main/java/com/promenar/nexara/data/local/inference/LocalInferenceEngine.kt

核心设计要点：

```kotlin
package com.promenar.nexara.data.local.inference

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class SlotType { MAIN, EMBEDDING, RERANK }

// LoadConfig / GenerateConfig 已在 InferenceBackend.kt 中定义（S-1 产出），直接 import 即可

data class SlotState(
    val modelPath: String? = null,
    val isLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val loadProgress: Float = 0f,
    val backendType: BackendType = BackendType.LLAMA_CPU,
    val modelName: String = "",
    val modelSize: String = "",
    val quantization: String = "",
    val error: String? = null
)

// ═══════════════════════════════════════
// 步骤 1: LlamaCppBackend — llama.cpp 的 InferenceBackend 实现
// ═══════════════════════════════════════

/**
 * 基于 llama.cpp 的推理后端实现。
 * 封装 LlamaContext 的所有 native 调用，通过 InferenceBackend 接口暴露。
 * 
 * 当前支持 Vulkan GPU + CPU NEON 两种后端，
 * 远期可通过 ggml-hexagon 实现 LlamaHexagonBackend，代码结构不变。
 */
class LlamaCppBackend : InferenceBackend {

    private var ctx: LlamaContext? = null
    override val backendType: BackendType
        get() = if (ctx?.gpuAccelerated == true) BackendType.LLAMA_VULKAN else BackendType.LLAMA_CPU

    override val isLoaded: Boolean get() = ctx != null

    override suspend fun loadModel(path: String, config: LoadConfig): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // 注意: S-1 产出的 LoadConfig 字段名为 threadCount（非 nThreads）
                val loadedCtx = LlamaContext.load(
                    modelPath = path,
                    contextSize = config.contextSize,
                    nThreads = config.threadCount,
                    useGpu = config.useGpu
                ) ?: throw IllegalStateException("Failed to load model: $path")
                
                ctx?.release()
                ctx = loadedCtx
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 流式生成。
     * S-1 的 LlamaContext.generate() 已修正生成流程:
     *   clear() → tokenize → ingestPrompt → sample(首个token) → 循环 decode
     * 消除了原方案 decode(promptTokens.last()) 重复处理最后一个 token 的 bug。
     *
     * 注意: 当前 native 层采样策略固定为 greedy。
     * GenerateConfig.temperature/topP/topK/repeatPenalty 字段保留供远期扩展。
     */
    override fun generate(prompt: String, config: GenerateConfig): Flow<String> {
        val context = ctx ?: throw IllegalStateException("No model loaded")
        return context.generate(prompt, config.maxTokens)
    }

    override suspend fun embed(text: String): Result<FloatArray> {
        val context = ctx ?: return Result.failure(IllegalStateException("No model loaded"))
        return withContext(Dispatchers.IO) {
            try { Result.success(context.embed(text)) }
            catch (e: Exception) { Result.failure(e) }
        }
    }

    override fun release() {
        ctx?.release()
        ctx = null
    }
}

// ═══════════════════════════════════════
// 步骤 2: LocalInferenceEngine — 通过 InferenceBackend 接口管理三槽位
// ═══════════════════════════════════════

/**
 * 推理引擎核心：通过 InferenceBackend 接口管理三个槽位的模型生命周期。
 * 
 * 架构: 引擎不直接依赖 LlamaContext，而是通过 InferenceBackend 接口操作。
 *       这确保了远期切换 NPU 后端时（LlamaHexagonBackend / ExecuTorchBackend），
 *       引擎代码零改动。
 */
class LocalInferenceEngine(private val appContext: Context) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // ── 三槽位状态 ──
    private val _mainSlot = MutableStateFlow(SlotState())
    val mainSlot: StateFlow<SlotState> = _mainSlot.asStateFlow()
    
    private val _embeddingSlot = MutableStateFlow(SlotState())
    val embeddingSlot: StateFlow<SlotState> = _embeddingSlot.asStateFlow()
    
    private val _rerankSlot = MutableStateFlow(SlotState())
    val rerankSlot: StateFlow<SlotState> = _rerankSlot.asStateFlow()
    
    // ── 后端实例（通过接口持有，不直接依赖 LlamaContext） ──
    private val mainBackend: InferenceBackend = LlamaCppBackend()
    private val embeddingBackend: InferenceBackend = LlamaCppBackend()
    private val rerankBackend: InferenceBackend = LlamaCppBackend()
    // 未来切换: 将 LlamaCppBackend() 替换为 LlamaHexagonBackend() 或 ExecuTorchBackend()
    
    var onStateChanged: (() -> Unit)? = null
    
    // ═══════════════════════════════════════
    // 模型加载/卸载（统一通过后端接口）
    // ═══════════════════════════════════════
    
    suspend fun loadModel(
        slot: SlotType,
        modelPath: String,
        config: LoadConfig = LoadConfig()
    ): Result<Unit> {
        val (stateField, backend) = when (slot) {
            SlotType.MAIN -> Pair(_mainSlot, mainBackend)
            SlotType.EMBEDDING -> Pair(_embeddingSlot, embeddingBackend)
            SlotType.RERANK -> Pair(_rerankSlot, rerankBackend)
        }
        
        stateField.value = stateField.value.copy(isLoading = true, loadProgress = 0f, error = null)
        
        val fileName = java.io.File(modelPath).name
        val sizeBytes = java.io.File(modelPath).length()
        stateField.value = stateField.value.copy(loadProgress = 0.3f)
        
        return backend.loadModel(modelPath, config).also { result ->
            result.onSuccess {
                stateField.value = SlotState(
                    modelPath = modelPath,
                    isLoaded = true,
                    isLoading = false,
                    loadProgress = 1f,
                    backendType = backend.backendType,
                    modelName = fileName,
                    modelSize = formatFileSize(sizeBytes),
                    quantization = detectQuantization(fileName)
                )
                onStateChanged?.invoke()
            }.onFailure { e ->
                stateField.value = stateField.value.copy(
                    isLoading = false, isLoaded = false, error = e.message
                )
            }
        }
    }
    
    suspend fun unloadModel(slot: SlotType) {
        when (slot) {
            SlotType.MAIN -> {
                mainBackend.release()
                _mainSlot.value = SlotState()
            }
            SlotType.EMBEDDING -> {
                embeddingBackend.release()
                _embeddingSlot.value = SlotState()
            }
            SlotType.RERANK -> {
                rerankBackend.release()
                _rerankSlot.value = SlotState()
            }
        }
        onStateChanged?.invoke()
    }
    
    // ═══════════════════════════════════════
    // 流式生成（通过后端接口）
    // ═══════════════════════════════════════
    
    fun generate(prompt: String, config: GenerateConfig = GenerateConfig()): Flow<String> {
        if (!mainBackend.isLoaded)
            throw IllegalStateException("No model loaded in main slot")
        return mainBackend.generate(prompt, config)
    }
    
    // ═══════════════════════════════════════
    // 本地 Embedding（通过后端接口）
    // ═══════════════════════════════════════
    
    suspend fun embed(text: String): Result<FloatArray> {
        if (!embeddingBackend.isLoaded)
            return Result.failure(IllegalStateException("No embedding model loaded"))
        return embeddingBackend.embed(text)
    }
    
    suspend fun embedBatch(texts: List<String>): Result<List<FloatArray>> {
        if (!embeddingBackend.isLoaded)
            return Result.failure(IllegalStateException("No embedding model loaded"))
        val results = mutableListOf<FloatArray>()
        for (text in texts) {
            embeddingBackend.embed(text).fold(
                onSuccess = { results.add(it) },
                onFailure = { return Result.failure(it) }
            )
        }
        return Result.success(results)
    }
    
    // ═══════════════════════════════════════
    // 本地 Reranker（简单 embedding 余弦相似度 fallback）
    // ═══════════════════════════════════════
    
    suspend fun rerank(
        query: String,
        documents: List<String>,
        topN: Int = 5
    ): Result<List<Pair<Int, Float>>> {
        if (!rerankBackend.isLoaded)
            return Result.failure(IllegalStateException("No reranker model loaded"))
        
        val queryEmb = rerankBackend.embed(query).getOrElse { return Result.failure(it) }
        
        val scored = documents.mapIndexed { idx, doc ->
            val docEmb = rerankBackend.embed(doc).getOrElse { return Result.failure(it) }
            val score = cosineSimilarity(queryEmb, docEmb)
            Pair(idx, score)
        }.sortedByDescending { it.second }.take(topN)
        
        return Result.success(scored)
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA == 0f || normB == 0f) 0f else dot / (sqrt(normA) * sqrt(normB))
    }
    
    // ═══════════════════════════════════════
    // 生命周期
    // ═══════════════════════════════════════
    
    fun release() {
        scope.cancel()
        mainBackend.release()
        embeddingBackend.release()
        rerankBackend.release()
    }
    
    // ═══════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            else -> "$bytes B"
        }
    }
    
    private fun detectQuantization(fileName: String): String {
        val patterns = listOf("Q8_0", "Q6_K", "Q5_K_M", "Q5_K_S", "Q4_K_M", "Q4_K_S",
                               "Q3_K_M", "Q2_K", "F16", "F32")
        return patterns.find { fileName.contains(it, ignoreCase = true) } ?: "Unknown"
    }
}
```

═══════════════════════════════════════
【验收标准】
═══════════════════════════════════════

1. 加载 Qwen2.5-1.5B Q4_K_M (~1GB) 模型到 main slot → `mainSlot.isLoaded = true`
2. `mainSlot.backendType` 正确显示 `LLAMA_VULKAN` 或 `LLAMA_CPU`
3. `engine.generate("1+1=")` 流式输出包含 "2"
4. 加载 nomic-embed-text-v1.5 到 embedding slot → `embed("hello")` 返回 FloatArray
5. 两个槽位独立加载/卸载，互不影响
6. `engine.release()` 后 `mainBackend.isLoaded = false`
7. **架构验证**: 代码中不存在 `LlamaContext` 的直接引用（全部通过 `InferenceBackend` 接口）

【关键设计决策】
- **生成流程修正**：S-1 新增了 `nativeSample`/`nativeClear`，generate 改为 `clear→tokenize→ingest→sample→decode` 循环。`LlamaCppBackend.generate()` 直接委托给 `ctx.generate()`，消除原方案 decode(promptTokens.last()) 重复处理末尾 token 的 bug
- **采样限制**：当前 native 层固定 greedy 采样。`GenerateConfig.temperature/topP/topK` 字段保留供远期扩展，当前不生效
- Reranker 简化实现：使用 embedding 余弦相似度 fallback（无需专用 reranker 模型即可工作）
- backendType 字段替代旧的 gpuAccelerated boolean，为 NPU 类型预留扩展空间
- 未来新增后端实现类时：只需新建 `XxxBackend : InferenceBackend`，引擎代码零改动
- GPU 加速默认启用（如果设备支持 Vulkan），通过 LoadConfig.useGpu 可关闭
- 所有模型加载在 IO 线程，状态通过 StateFlow 暴露给 UI 层
- **注意字段名**：S-1 产出的 `LoadConfig` 字段名为 `threadCount`，非原方案的 `nThreads`
```

---

## Session S-3：ModelStorageManager 模型文件管理 + GGUF 解析

> **状态**: 依赖 S-1 完成 · ✅ 与 S-2 并行！
> **预估**: 2-3 天
> **可并行**: 与 S-2 同时启动
> **关键产出**: `ModelStorageManager.kt` + `GgufParser.kt` + `ModelDownloader.kt`

---

**涉及文件**：
- `data/local/inference/ModelStorageManager.kt` — ← 新建：模型导入/删除/列表
- `data/local/inference/GgufParser.kt` — ← 新建：GGUF header 解析
- `data/local/inference/ModelDownloader.kt` — ← 新建：HTTP 断点下载
- `data/local/db/` — 可能需要新增 LocalModelEntity + DAO（可选，初期可用 SharedPreferences）

---

### 📋 复制以下提示词到新会话：

```
【任务】实现端侧模型的存储管理、GGUF 格式解析和下载功能。
本会话不涉及推理逻辑，仅处理模型文件的 I/O 和元数据管理。

【前置依赖】
- S-1 已完成：项目支持 NDK/JNI（目录结构就绪）
- 与 S-2（推理引擎）可完全并行开发

【项目背景】
- 工作区: /Users/promenar/Codex/Nexara/native-ui
- 包名: com.promenar.nexara
- 模型存储目录: /data/data/com.promenar.nexara.native/files/models/
- TS 原版参考: ../src/lib/local-inference/ModelStorageManager.ts

【操作步骤】

═══════════════════════════════════════
【步骤 1】创建 ModelStorageManager.kt
═══════════════════════════════════════

文件路径: app/src/main/java/com/promenar/nexara/data/local/inference/ModelStorageManager.kt

核心设计：

```kotlin
package com.promenar.nexara.data.local.inference

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class StoredModel(
    val id: String,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val format: String,        // "GGUF" 或 "Unknown"
    val addedAt: Long,
    // GGUF metadata（从 header 解析）
    val architecture: String = "",
    val quantization: String = "",
    val contextLength: Int = 0,
    val embeddingLength: Int = 0,
    val parameterCount: String = ""
)

class ModelStorageManager(private val context: Context) {

    val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    /**
     * 从系统文件选择器导入 .gguf 文件（通过 SAF Uri）
     */
    suspend fun importModel(uri: Uri): Result<StoredModel> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(uri) ?: "unknown.gguf"

            // 验证文件扩展名
            if (!fileName.endsWith(".gguf", ignoreCase = true)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Only .gguf files are supported")
                )
            }

            // 复制到 app 私有目录
            val destFile = File(modelsDir, fileName)
            var totalBytes = 0L

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                }
            } ?: throw IllegalStateException("Cannot open file: $uri")

            // 解析 GGUF metadata
            val metadata = GgufParser.parse(destFile.absolutePath)

            val model = StoredModel(
                id = "model_${System.currentTimeMillis()}",
                fileName = fileName,
                filePath = destFile.absolutePath,
                sizeBytes = totalBytes,
                format = "GGUF",
                addedAt = System.currentTimeMillis(),
                architecture = metadata.architecture,
                quantization = metadata.quantization,
                contextLength = metadata.contextLength,
                embeddingLength = metadata.embeddingLength,
                parameterCount = metadata.parameterCount
            )

            Result.success(model)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 列举所有已存储的模型
     */
    suspend fun listModels(): List<StoredModel> = withContext(Dispatchers.IO) {
        modelsDir.listFiles()
            ?.filter { it.extension.equals("gguf", ignoreCase = true) }
            ?.map { file ->
                try {
                    val metadata = GgufParser.parse(file.absolutePath)
                    StoredModel(
                        id = "model_${file.nameWithoutExtension}",
                        fileName = file.name,
                        filePath = file.absolutePath,
                        sizeBytes = file.length(),
                        format = "GGUF",
                        addedAt = file.lastModified(),
                        architecture = metadata.architecture,
                        quantization = metadata.quantization,
                        contextLength = metadata.contextLength,
                        embeddingLength = metadata.embeddingLength,
                        parameterCount = metadata.parameterCount
                    )
                } catch (_: Exception) {
                    StoredModel(
                        id = "model_${file.nameWithoutExtension}",
                        fileName = file.name,
                        filePath = file.absolutePath,
                        sizeBytes = file.length(),
                        format = "Unknown",
                        addedAt = file.lastModified()
                    )
                }
            }
            ?.sortedByDescending { it.addedAt }
            ?: emptyList()
    }

    /**
     * 删除模型文件
     */
    suspend fun deleteModel(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists() && file.delete()) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Failed to delete: $filePath"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取模型文件大小
     */
    fun getModelSize(filePath: String): Long = File(filePath).length()

    /**
     * 检测模型是否仍在导入中（文件扩展名临时）
     */
    fun isImporting(fileName: String): Boolean =
        fileName.endsWith(".tmp") || fileName.endsWith(".part")

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name ?: uri.lastPathSegment
    }
}
```

═══════════════════════════════════════
【步骤 2】创建 GgufParser.kt — GGUF 二进制 header 解析
═══════════════════════════════════════

文件路径: app/src/main/java/com/promenar/nexara/data/local/inference/GgufParser.kt

GGUF 格式规范: https://github.com/ggml-org/ggml/blob/master/docs/gguf.md

Header 结构（Little Endian）:
- 4 bytes: Magic "GGUF"
- 4 bytes: Version (uint32, 当前为 2 或 3)
- 8 bytes: Tensor 数量
- 8 bytes: Metadata K-V 数量

核心逻辑：

```kotlin
package com.promenar.nexara.data.local.inference

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class GgufMetadata(
    val version: Int = 0,
    val architecture: String = "",
    val quantization: String = "",
    val contextLength: Int = 2048,
    val embeddingLength: Int = 0,
    val parameterCount: String = "",
    val vocabSize: Int = 0,
    val modelName: String = ""
)

object GgufParser {

    fun parse(filePath: String): GgufMetadata {
        val file = File(filePath)
        if (!file.exists()) throw IllegalArgumentException("File not found: $filePath")

        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(4)
            raf.read(header)
            val magic = String(header, StandardCharsets.UTF_8)
            if (magic != "GGUF") {
                throw IllegalArgumentException("Not a valid GGUF file (magic: $magic)")
            }

            val version = readUInt32(raf)
            val tensorCount = readUInt64(raf)
            val kvCount = readUInt64(raf)

            val metadata = mutableMapOf<String, Any>()

            // 读取所有 key-value 对
            for (i in 0 until minOf(kvCount, 100)) { // 限制读取前 100 个以防异常
                val key = readString(raf)
                val (type, value) = readValue(raf)
                metadata[key] = value
            }

            return GgufMetadata(
                version = version.toInt(),
                architecture = (metadata["general.architecture"] as? String) ?: "",
                quantization = (metadata["general.file_type"] as? Int)?.let { quantType ->
                    mapQuantizationType(it)
                } ?: guessQuantizationFromFileName(filePath),
                contextLength = (metadata["llama.context_length"] as? Int)
                    ?: (metadata["{arch}.context_length"] as? Int)
                    ?: 2048,
                embeddingLength = (metadata["llama.embedding_length"] as? Int)
                    ?: (metadata["{arch}.embedding_length"] as? Int)
                    ?: 0,
                parameterCount = formatParameterCount(metadata),
                vocabSize = (metadata["llama.vocab_size"] as? Int)
                    ?: (metadata["{arch}.vocab_size"] as? Int)
                    ?: 0,
                modelName = (metadata["general.name"] as? String) ?: file.nameWithoutExtension
            )
        }
    }

    private fun readUInt32(raf: RandomAccessFile): Long {
        val buf = ByteArray(4)
        raf.read(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }

    private fun readUInt64(raf: RandomAccessFile): Long {
        val buf = ByteArray(8)
        raf.read(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readString(raf: RandomAccessFile): String {
        val length = readUInt64(raf)
        val buf = ByteArray(length.toInt())
        raf.read(buf)
        return String(buf, StandardCharsets.UTF_8)
    }

    private fun readValue(raf: RandomAccessFile): Pair<Int, Any> {
        val type = raf.read()
        return when (type) {
            0, 1 -> type to (raf.read().toInt()) // bool
            2, 3 -> type to raf.read()           // uint8/int8
            4, 5 -> type to readShort16(raf)      // uint16/int16
            6, 7 -> type to readUInt32(raf).toInt() // uint32/int32
            8, 9 -> type to readUInt64(raf)        // uint64/int64
            // float32/float64/string/array — 简化实现仅跳过
            else -> {
                // 跳过该类型，不阻塞解析
                type to "skipped"
            }
        }
    }

    private fun readShort16(raf: RandomAccessFile): Int {
        val buf = ByteArray(2)
        raf.read(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
    }

    private fun mapQuantizationType(type: Int): String = when (type) {
        1 -> "F16"; 2 -> "Q4_0"; 3 -> "Q4_1"; 7 -> "Q8_0"
        10 -> "Q2_K"; 12 -> "Q3_K"; 14 -> "Q4_K"; 15 -> "Q5_K"; 16 -> "Q6_K"
        else -> "QT$type"
    }

    private fun guessQuantizationFromFileName(path: String): String {
        val name = File(path).name.uppercase()
        return when {
            "Q8_0" in name -> "Q8_0"; "Q6_K" in name -> "Q6_K"
            "Q5_K_M" in name -> "Q5_K_M"; "Q5_K_S" in name -> "Q5_K_S"
            "Q4_K_M" in name -> "Q4_K_M"; "Q4_K_S" in name -> "Q4_K_S"
            "Q3_K" in name -> "Q3_K"; "Q2_K" in name -> "Q2_K"
            "F16" in name -> "F16"; "F32" in name -> "F32"
            else -> "Unknown"
        }
    }

    private fun formatParameterCount(metadata: Map<String, Any>): String {
        val count = metadata.filterKeys { it.contains("parameter_count") }
        return count.values.firstOrNull()?.toString() ?: ""
    }
}
```

═══════════════════════════════════════
【步骤 3】创建 ModelDownloader.kt — HTTP 模型下载
═══════════════════════════════════════

文件路径: app/src/main/java/com/promenar/nexara/data/local/inference/ModelDownloader.kt

```kotlin
package com.promenar.nexara.data.local.inference

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class ModelDownloader(private val context: Context) {

    data class DownloadState(
        val url: String,
        val fileName: String,
        val totalBytes: Long = -1,
        val downloadedBytes: Long = 0,
        val isDownloading: Boolean = false,
        val isComplete: Boolean = false,
        val error: String? = null
    )

    private val modelsDir = File(context.filesDir, "models").also { it.mkdirs() }

    /**
     * 下载模型文件（支持断点续传）
     * @param onProgress 进度回调 (0f-1f)
     */
    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): Result<StoredModel> = withContext(Dispatchers.IO) {
        try {
            val destFile = File(modelsDir, fileName)
            val tempFile = File(modelsDir, "$fileName.part")

            // 断点续传: 检查已有部分文件
            var startByte = if (tempFile.exists()) tempFile.length() else 0L
            if (destFile.exists()) {
                // 文件已完整下载
                return@withContext Result.success(
                    createModelInfo(destFile)
                )
            }

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                if (startByte > 0) {
                    setRequestProperty("Range", "bytes=$startByte-")
                }
            }

            val totalSize = if (startByte > 0) {
                connection.getHeaderField("Content-Range")
                    ?.substringAfter("/")?.toLongOrNull()
                    ?: (connection.contentLength + startByte)
            } else {
                connection.contentLengthLong
            }

            connection.inputStream.use { input ->
                FileOutputStream(tempFile, startByte > 0).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = startByte

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalSize > 0) {
                            onProgress(totalRead.toFloat() / totalSize)
                        }
                    }
                }
            }
            connection.disconnect()

            // 重命名临时文件
            tempFile.renameTo(destFile)

            Result.success(createModelInfo(destFile))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createModelInfo(file: File): StoredModel {
        return StoredModel(
            id = "model_${file.nameWithoutExtension}",
            fileName = file.name,
            filePath = file.absolutePath,
            sizeBytes = file.length(),
            format = "GGUF",
            addedAt = file.lastModified()
        )
    }
}
```

═══════════════════════════════════════
【验收标准】
═══════════════════════════════════════

1. 从文件管理器选择一个 .gguf 文件 → `importModel()` 成功复制到 modelsDir
2. `listModels()` 返回解析后的模型列表（含架构/量化/上下文长度）
3. `deleteModel()` 后文件确实被删除
4. 下载功能：输入 HuggingFace GGUF 链接，断点续传正常工作

【关键提醒】
- GGUF v3 格式的 metadata key 命名可能与 v2 略有不同，注意兼容
- SAF (Storage Access Framework) 导入大文件时可能较慢，建议显示进度
- 可选扩展：支持从 HuggingFace API 直接搜索/下载模型
```

---

## Session S-4：LocalProtocol 协议集成 + Prompt 模板

> **状态**: 依赖 S-2 完成
> **预估**: 2-3 天
> **关键产出**: `LocalProtocol.kt` + `LlmProvider.local()` 工厂

---

**涉及文件**：
- `data/remote/protocol/LocalProtocol.kt` — ← 新建：实现 LlmProtocol
- `data/remote/protocol/LlmProtocol.kt` — 修改：新增 `LOCAL` 枚举
- `data/remote/provider/LlmProvider.kt` — 修改：新增 `local()` 工厂
- `data/rag/EmbeddingClient.kt` — 修改：新增 `embedLocal()` 方法

---

### 📋 复制以下提示词到新会话：

```
【任务】实现本地 LLM 协议适配层，使本地模型无缝接入现有的 LlmProvider 架构。
ChatScreen/ChatViewModel 无需任何改动即可切换本地模型。

【前置依赖】
- S-2 已完成：LocalInferenceEngine.kt 可用
- 可直接 import com.promenar.nexara.data.local.inference.LocalInferenceEngine

【项目背景】
- 工作区: /Users/promenar/Codex/Nexara/native-ui
- 包名: com.promenar.nexara
- 现有架构: LlmProtocol 接口 → 三协议实现 (OpenAI/Anthropic/VertexAI) → LlmProvider 聚合
- 目标: 新增 LOCAL 协议作为第四实现，通过 LlmProtocol 接口打通
- TS 原版参考: ../src/lib/llm/providers/local-llm.ts

【操作步骤】

═══════════════════════════════════════
【步骤 1】扩展 ProtocolId 枚举
═══════════════════════════════════════

文件: app/src/main/java/com/promenar/nexara/data/remote/protocol/LlmProtocol.kt

在第 117 行的枚举中新增 LOCAL:

查找:
```kotlin
enum class ProtocolId {
    OPENAI,
    ANTHROPIC,
    VERTEX_AI
}
```

替换为:
```kotlin
enum class ProtocolId {
    OPENAI,
    ANTHROPIC,
    VERTEX_AI,
    LOCAL
}
```

═══════════════════════════════════════
【步骤 2】创建 LocalProtocol.kt
═══════════════════════════════════════

文件路径: app/src/main/java/com/promenar/nexara/data/remote/protocol/LocalProtocol.kt

核心设计：

```kotlin
package com.promenar.nexara.data.remote.protocol

import com.promenar.nexara.data.local.inference.GenerateConfig
import com.promenar.nexara.data.local.inference.LocalInferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 本地 LLM 协议实现，通过 LlmProtocol 接口桥接 LocalInferenceEngine。
 * 
 * 自动检测模型类型以选择合适的 prompt 模板 (ChatML / Llama3 / etc.)。
 */
class LocalProtocol(
    private val engine: LocalInferenceEngine,
    private val modelName: String = ""
) : LlmProtocol {

    override val id: ProtocolId = ProtocolId.LOCAL

    private val promptTemplate: PromptTemplate by lazy {
        detectTemplate(modelName)
    }

    override suspend fun sendPrompt(request: PromptRequest): Flow<StreamChunk> = flow {
        // 1. 构建 prompt
        val formattedPrompt = promptTemplate.format(
            messages = request.messages,
            tools = request.tools
        )

        // 2. 流式生成
        val genConfig = GenerateConfig(
            maxTokens = request.maxTokens ?: 512,
            temperature = request.temperature ?: 0.7f,
            topP = request.topP ?: 0.9f
        )

        engine.generate(formattedPrompt, genConfig).collect { tokenText ->
            emit(StreamChunk.TextDelta(content = tokenText))
        }

        // 3. 结束标记
        emit(StreamChunk.Done)
    }

    override suspend fun sendPromptSync(request: PromptRequest): PromptResponse {
        val formattedPrompt = promptTemplate.format(
            messages = request.messages,
            tools = request.tools
        )

        val genConfig = GenerateConfig(maxTokens = request.maxTokens ?: 512)
        val result = StringBuilder()

        engine.generate(formattedPrompt, genConfig).collect { tokenText ->
            result.append(tokenText)
        }

        return PromptResponse(content = result.toString())
    }

    override suspend fun listModels(): List<String> {
        return listOf(modelName.ifEmpty { "Local Model" })
    }

    override fun cancel() {
        // 本地模型通过卸载上下文实现取消效果
        // 简化实现：依赖 Kotlin Flow 的协程取消机制
    }

    /**
     * 检测模型类型选择最优 prompt 模板
     */
    private fun detectTemplate(name: String): PromptTemplate {
        val lower = name.lowercase()
        return when {
            "llama-3" in lower || "llama3" in lower -> Llama3Template()
            "qwen" in lower -> ChatMLTemplate()
            "mistral" in lower -> MistralTemplate()
            "gemma" in lower -> GemmaTemplate()
            else -> ChatMLTemplate() // default
        }
    }
}

// ═══════════════════════════════════════
// Prompt 模板体系
// ═══════════════════════════════════════

sealed class PromptTemplate {
    abstract fun format(messages: List<ProtocolMessage>, tools: List<ProtocolTool>?): String
}

/**
 * ChatML 格式（Qwen / DeepSeek / 通用）
 * <|im_start|>system
 * You are a helpful assistant.<|im_end|>
 * <|im_start|>user
 * Hello<|im_end|>
 * <|im_start|>assistant
 */
class ChatMLTemplate : PromptTemplate() {
    override fun format(messages: List<ProtocolMessage>, tools: List<ProtocolTool>?): String {
        val sb = StringBuilder()
        for (msg in messages) {
            sb.append("<|im_start|>${msg.role}\n${msg.content}<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }
}

/**
 * Llama 3 格式
 * <|begin_of_text|><|start_header_id|>system<|end_header_id|>
 * You are a helpful assistant.<|eot_id|>
 * <|start_header_id|>user<|end_header_id|>
 * Hello<|eot_id|>
 * <|start_header_id|>assistant<|end_header_id|>
 */
class Llama3Template : PromptTemplate() {
    override fun format(messages: List<ProtocolMessage>, tools: List<ProtocolTool>?): String {
        val sb = StringBuilder("<|begin_of_text|>")
        for (msg in messages) {
            sb.append("<|start_header_id|>${msg.role}<|end_header_id|>\n\n")
            sb.append("${msg.content}<|eot_id|>")
        }
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }
}

/**
 * Mistral 格式
 * <s>[INST] {system_prompt}\n\n{user_message} [/INST]
 */
class MistralTemplate : PromptTemplate() {
    override fun format(messages: List<ProtocolMessage>, tools: List<ProtocolTool>?): String {
        val sb = StringBuilder("<s>")
        val systemMsg = messages.find { it.role == "system" }
        val userMsgs = messages.filter { it.role == "user" }

        if (systemMsg != null) {
            sb.append("[INST] ${systemMsg.content}\n\n")
        } else {
            sb.append("[INST] ")
        }

        // 取最后一条用户消息作为 prompt
        val lastUser = userMsgs.lastOrNull()
        if (lastUser != null) {
            sb.append("${lastUser.content} [/INST]")
        }

        return sb.toString()
    }
}

/**
 * Gemma 格式
 * <bos><start_of_turn>user\nHello<end_of_turn>\n<start_of_turn>model\n
 */
class GemmaTemplate : PromptTemplate() {
    override fun format(messages: List<ProtocolMessage>, tools: List<ProtocolTool>?): String {
        val sb = StringBuilder("<bos>")
        for (msg in messages) {
            val role = if (msg.role == "assistant") "model" else msg.role
            sb.append("<start_of_turn>$role\n${msg.content}<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }
}
```

═══════════════════════════════════════
【步骤 3】扩展 LlmProvider — 新增 local() 工厂方法
═══════════════════════════════════════

文件: app/src/main/java/com/promenar/nexara/data/remote/provider/LlmProvider.kt

在 companion object 中新增：

```kotlin
// 在 createProtocol 的 when 分支中新增:
ProtocolId.LOCAL -> throw IllegalStateException(
    "Use LlmProvider.local(engine) factory for local models"
)

// 在 companion object 中新增:
fun local(engine: LocalInferenceEngine, modelName: String = ""): LlmProvider {
    return LlmProvider(LocalProtocol(engine, modelName))
}
```

同时添加 import:
```kotlin
import com.promenar.nexara.data.local.inference.LocalInferenceEngine
import com.promenar.nexara.data.remote.protocol.LocalProtocol
```

═══════════════════════════════════════
【步骤 4】EmbeddingClient 新增 embedLocal()
═══════════════════════════════════════

文件: app/src/main/java/com/promenar/nexara/data/rag/EmbeddingClient.kt

新增可选构造函数参数和本地 embedding 方法：

```kotlin
// 在 EmbeddingClient 类中添加:
private val localEngine: LocalInferenceEngine? = null,
// (修改主构造函数，添加到参数列表末尾)

// 新增方法:
suspend fun embedLocal(text: String): Result<FloatArray> {
    val engine = localEngine 
        ?: return Result.failure(IllegalStateException("Local engine not configured"))
    return engine.embed(text)
}

suspend fun embedLocalBatch(texts: List<String>): Result<List<FloatArray>> {
    val engine = localEngine
        ?: return Result.failure(IllegalStateException("Local engine not configured"))
    return engine.embedBatch(texts)
}
```

═══════════════════════════════════════
【验收标准】
═══════════════════════════════════════

1. `LlmProvider.local(engine, "Qwen2.5-7B")` 创建成功
2. 通过 `sendPrompt(request)` 发送对话，返回 Flow<StreamChunk> 流
3. ChatScreen 无改动即能使用本地模型（只需将 llmProvider 切换为 local 版本）
4. prompt 模板根据模型名自动选择正确格式（测试 Qwen 模型走 ChatML，Llama3 走 Llama3 模板）
5. `runBlocking { protocol.listModels() }` 返回非空列表

【关键设计决策】
- ChatML 作为默认模板（兼容性最广）
- 工具调用 (Tool Use) 暂不在本地模型实现（P2 优先级），基础对话先跑通
- 本地模型的 Thinking/Reasoning 暂不支持（需要模型原生支持思考模式）
```

---

## Session S-5A：LocalModelsViewModel + LocalModelsScreen 改造

> **状态**: 依赖 S-3 + S-4 完成
> **预估**: 2 天
> **可并行**: ✅ 与 S-5B 同时启动
> **关键产出**: `LocalModelsViewModel.kt` + 改造后 `LocalModelsScreen.kt`

---

**涉及文件**：
- `ui/settings/LocalModelsViewModel.kt` — ← 新建
- `ui/settings/LocalModelsScreen.kt` — 大幅改造（移除硬编码）

---

### 📋 复制以下提示词到新会话：

```
【任务】实现 LocalModelsViewModel 并改造 LocalModelsScreen，
将现有的硬编码占位数据替换为真实的模型管理和引擎状态。

【前置依赖】
- S-3 已完成：ModelStorageManager.kt + GgufParser.kt
- S-4 已完成：LocalProtocol.kt + LlmProvider.local()
- 可直接 import com.promenar.nexara.data.local.inference.*

【项目背景】
- 工作区: /Users/promenar/Codex/Nexara/native-ui
- 包名: com.promenar.nexara
- 当前 LocalModelsScreen: 硬编码 3 个假模型 PlaceholderModels，onLoad = {} 空回调，全部硬编码状态
- 目标: 接入真实的 ModelStorageManager 和 LocalInferenceEngine

【操作步骤】

═══════════════════════════════════════
【步骤 1】创建 LocalModelsViewModel.kt
═══════════════════════════════════════

文件路径: app/src/main/java/com/promenar/nexara/ui/settings/LocalModelsViewModel.kt

```kotlin
package com.promenar.nexara.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.promenar.nexara.NexaraApplication
import com.promenar.nexara.data.local.inference.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LocalModelsViewModel(application: Application) : ViewModel() {

    private val app = application as NexaraApplication
    
    // 从 Application 中获取（如果已集成），否则创建新实例
    // 注意: 实际集成时这些应从 Application 获取
    val engine: LocalInferenceEngine = // TODO: 从 app 获取
        LocalInferenceEngine(application)
    val storageManager: ModelStorageManager = 
        ModelStorageManager(application)
    val downloader: ModelDownloader = 
        ModelDownloader(application)

    // ── 状态 ──
    val mainSlot: StateFlow<SlotState> = engine.mainSlot
    val embeddingSlot: StateFlow<SlotState> = engine.embeddingSlot
    val rerankSlot: StateFlow<SlotState> = engine.rerankSlot

    val isEngineEnabled = MutableStateFlow(true)

    private val _availableModels = MutableStateFlow<List<StoredModel>>(emptyList())
    val availableModels: StateFlow<List<StoredModel>> = _availableModels.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _downloadState = MutableStateFlow<ModelDownloader.DownloadState?>(null)
    val downloadState: StateFlow<ModelDownloader.DownloadState?> = _downloadState.asStateFlow()

    init {
        refreshModelList()
    }

    // ── 模型管理 ──
    fun refreshModelList() {
        viewModelScope.launch {
            _availableModels.value = storageManager.listModels()
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            storageManager.importModel(uri)
                .onSuccess { 
                    refreshModelList() 
                }
                .onFailure { e ->
                    // TODO: 通过 error channel 通知 UI
                }
            _isImporting.value = false
        }
    }

    fun deleteModel(filePath: String) {
        viewModelScope.launch {
            storageManager.deleteModel(filePath)
                .onSuccess { refreshModelList() }
        }
    }

    // ── 模型加载/卸载 ──
    fun loadModel(slot: SlotType, modelPath: String) {
        viewModelScope.launch {
            engine.loadModel(slot, modelPath)
        }
    }

    fun unloadModel(slot: SlotType) {
        viewModelScope.launch {
            engine.unloadModel(slot)
        }
    }

    // ── 下载 ──
    fun downloadModel(url: String, fileName: String) {
        viewModelScope.launch {
            downloader.download(url, fileName) { progress ->
                _downloadState.value = _downloadState.value?.copy(
                    downloadedBytes = (_downloadState.value?.totalBytes ?: 0).let {
                        (it * progress).toLong()
                    }
                )
            }.onSuccess { refreshModelList() }
        }
    }

    fun setEngineEnabled(enabled: Boolean) {
        isEngineEnabled.value = enabled
        if (!enabled) {
            // 卸载所有已加载模型
            viewModelScope.launch {
                engine.unloadModel(SlotType.MAIN)
                engine.unloadModel(SlotType.EMBEDDING)
                engine.unloadModel(SlotType.RERANK)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 注意: 不要在 ViewModel 清理时释放 engine，
        // 因为 engine 生命周期应由 Application 管理
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LocalModelsViewModel(application) as T
                }
            }
    }
}
```

═══════════════════════════════════════
【步骤 2】改造 LocalModelsScreen.kt
═══════════════════════════════════════

当前问题:
1. 使用 `private val PlaceholderModels = listOf(...)` 硬编码
2. `var engineEnabled by remember { mutableStateOf(true) }` 无持久化
3. `onLoad = { }` 空回调
4. 引擎状态硬编码字符串

改造要点:
1. 注入 `LocalModelsViewModel` 并通过 `viewModel()` 获取
2. `PlaceholderModels` 替换为 `viewModel.availableModels.collectAsState()`
3. `engineEnabled` 替换为 `viewModel.isEngineEnabled.collectAsState()`
4. 引擎状态槽位替换为 `viewModel.mainSlot/embeddingSlot/rerankSlot.collectAsState()`
5. `onLoad = { }` 替换为 `viewModel.loadModel(slot, path)`
6. 删除按钮接入 `viewModel.deleteModel(path)`
7. 新增导入按钮（触发 SAF 文件选择器）→ `viewModel.importModel(uri)`

【关键修改指引】

在 LocalModelsScreen 函数签名中添加 ViewModel 参数或使用 viewModel():

```kotlin
@Composable
fun LocalModelsScreen(
    onNavigateBack: () -> Unit,
    viewModel: LocalModelsViewModel = viewModel(factory = LocalModelsViewModel.factory)
) {
    val availableModels by viewModel.availableModels.collectAsState()
    val engineEnabled by viewModel.isEngineEnabled.collectAsState()
    val mainSlot by viewModel.mainSlot.collectAsState()
    val embeddingSlot by viewModel.embeddingSlot.collectAsState()
    val rerankSlot by viewModel.rerankSlot.collectAsState()
    
    // engineEnabled toggle 接入 viewModel.setEngineEnabled()
    // 模型列表渲染 availableModels（替代 PlaceholderModels）
    // 引擎槽位状态渲染 mainSlot/embeddingSlot/rerankSlot
    // onLoad → viewModel.loadModel()
    // onDelete → viewModel.deleteModel()
}
```

【验收标准】
1. LocalModelsScreen 打开后展示真实导入的模型列表（非硬编码）
2. 加载按钮触发 loadModel，进度条正确显示
3. 引擎槽位状态正确反映真实加载情况（GPU 加速标识）
4. 删除模型后文件确实被移除，列表自动刷新
```

---

## Session S-5B：SettingsViewModel 开关 + ProviderSettingsScreen 入口

> **状态**: 依赖 S-4 完成
> **预估**: 1 天
> **可并行**: ✅ 与 S-5A 同时启动
> **关键产出**: `SettingsViewModel` 扩展 + `ProviderSettingsScreen` 入口

---

### 📋 复制以下提示词到新会话：

```
【任务】在设置系统中新增本地模型开关，并在 Provider 选择界面添加"本地模型"入口。

【前置依赖】
- S-4 已完成：LlmProvider.local() 工厂方法 + ProtocolId.LOCAL
- 与 S-5A 可并行执行

【项目背景】
- 工作区: /Users/promenar/Codex/Nexara/native-ui
- 包名: com.promenar.nexara
- SettingsViewModel 当前无 localModelsEnabled 状态
- NexaraApplication 的 buildProviderFromPrefs() 仅处理远程协议
- TS 原版参考: ../src/store/settings-store.ts (localModelsEnabled switch)

【操作步骤】

═══════════════════════════════════════
【步骤 1】SettingsViewModel 新增 localModelsEnabled
═══════════════════════════════════════

文件: app/src/main/java/com/promenar/nexara/ui/settings/SettingsViewModel.kt

新增:

```kotlin
// 在类属性区域新增：
private val _localModelsEnabled = MutableStateFlow(
    prefs.getBoolean("local_models_enabled", false)
)
val localModelsEnabled: StateFlow<Boolean> = _localModelsEnabled.asStateFlow()

// 新增方法：
fun setLocalModelsEnabled(enabled: Boolean) {
    _localModelsEnabled.value = enabled
    prefs.edit().putBoolean("local_models_enabled", enabled).apply()
}

// 在 loadProviders() 方法中，typeName when 分支新增:
ProtocolId.LOCAL -> "本地模型"
```

═══════════════════════════════════════
【步骤 2】NexaraApplication 支持本地 Provider 切换
═══════════════════════════════════════

文件: app/src/main/java/com/promenar/nexara/NexaraApplication.kt

新增:

```kotlin
// 新增本地推理引擎持有（延迟初始化）
val localInferenceEngine: LocalInferenceEngine by lazy {
    LocalInferenceEngine(this)
}

// 新增方法：切换到本地模型
fun switchToLocalProvider(modelName: String = "") {
    _llmProvider.value = LlmProvider.local(localInferenceEngine, modelName)
}

// 修改 buildProviderFromPrefs()，增加 LOCAL 分支:
private fun buildProviderFromPrefs(): LlmProvider {
    val config = getSavedProviderConfig()
    return if (config != null) {
        when (config.protocolId) {
            ProtocolId.LOCAL -> LlmProvider.local(localInferenceEngine, config.model)
            else -> LlmProvider.builder()
                .protocolId(config.protocolId)
                .baseUrl(config.baseUrl)
                .apiKey(config.apiKey)
                .model(config.model)
                .build()
        }
    } else {
        LlmProvider.builder()
            .protocolId(ProtocolId.OPENAI)
            .baseUrl("")
            .apiKey("")
            .model("")
            .build()
    }
}
```

═══════════════════════════════════════
【步骤 3】ProviderSettingsScreen 新增本地模型入口
═══════════════════════════════════════

在 Provider 选择界面的协议列表中添加 "本地模型" 选项，
点击后调用 `updateProvider(ProtocolId.LOCAL, "", "", "local")` 并导航到 LocalModelsScreen。

═══════════════════════════════════════
【验收标准】
═══════════════════════════════════════

1. 设置页出现 "启用本地模型" 开关
2. 开启后可在 Provider 选择中看到 "本地模型" 选项
3. 选择本地模型后，ChatScreen 对话走本地推理（调用 LocalProtocol）
4. 关闭本地模型开关后，回退到远程 Provider
5. 设置持久化：重启 App 后开关状态保留
```

---

## Session S-6：NexaraApplication 集成 + 生命周期管理

> **状态**: 依赖 S-5A + S-5B 完成
> **预估**: 1-2 天
> **关键产出**: Application 生命周期集成 + 内存管理

---

### 📋 复制以下提示词到新会话：

```
【任务】在 NexaraApplication 中完整集成本地推理引擎，并实现前后台切换模型保活和内存压力处理。

【前置依赖】
- S-5A 已完成：LocalModelsViewModel + LocalModelsScreen
- S-5B 已完成：SettingsViewModel.localModelsEnabled + ProviderSettingsScreen 入口

【项目背景】
- 工作区: /Users/promenar/Codex/Nexara/native-ui
- 包名: com.promenar.nexara
- NexaraApplication 当前仅为远程协议提供初始化

【操作步骤】

═══════════════════════════════════════
【步骤 1】完善 NexaraApplication 的本地引擎初始化
═══════════════════════════════════════

文件: app/src/main/java/com/promenar/nexara/NexaraApplication.kt

1. 确认 `localInferenceEngine` 的 lazy 初始化（S-5B 已添加）
2. 新增 `modelStorageManager` 和 `modelDownloader` 的 lazy 初始化
3. 在 `onCreate()` 中添加自动加载逻辑:

```kotlin
override fun onCreate() {
    super.onCreate()
    _llmProvider = MutableStateFlow(buildProviderFromPrefs())
    
    // 如果上次使用了本地模型且自动加载开关开启，则恢复加载
    if (prefs.getBoolean("local_models_enabled", false) &&
        prefs.getBoolean("local_auto_load", false)) {
        val lastModel = prefs.getString("last_local_model", null)
        if (lastModel != null) {
            CoroutineScope(Dispatchers.IO).launch {
                localInferenceEngine.loadModel(SlotType.MAIN, lastModel)
            }
        }
    }
}
```

═══════════════════════════════════════
【步骤 2】前后台切换模型保活
═══════════════════════════════════════

在 NexaraApplication 中注册 ProcessLifecycleOwner:

新增依赖: `implementation("androidx.lifecycle:lifecycle-process:2.7.0")`

```kotlin
override fun onCreate() {
    super.onCreate()
    // ... 已有初始化 ...
    
    // 注册前后台切换监听
    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStop() {
            // 切到后台：保存当前状态，但不释放模型
            // (Android 会保留进程，模型上下文不会丢失)
            val mainPath = localInferenceEngine.mainSlot.value.modelPath
            if (mainPath != null) {
                prefs.edit().putString("last_local_model", mainPath).apply()
            }
        }
        
        override fun onStart() {
            // 回到前台：状态自动恢复（引擎状态由 StateFlow 管理）
        }
    })
}
```

═══════════════════════════════════════
【步骤 3】内存压力处理
═══════════════════════════════════════

在 NexaraApplication 中重写 onTrimMemory:

```kotlin
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
            // 内存紧张: 卸载非活跃槽位（保留 MAIN）
            CoroutineScope(Dispatchers.IO).launch {
                localInferenceEngine.unloadModel(SlotType.RERANK)
                localInferenceEngine.unloadModel(SlotType.EMBEDDING)
            }
        }
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
            // 进入后台: 可选卸载大型模型（由用户设置控制）
        }
    }
}
```

═══════════════════════════════════════
【验收标准】
═══════════════════════════════════════

1. App 启动后引擎自动初始化（lazy 加载，首次使用时初始化）
2. 切到后台再切回，模型上下文不丢失，可继续对话
3. 收到系统低内存警告时，非活跃槽位自动卸载
4. `local_auto_load` 开启 + `last_local_model` 有值时，启动自动加载模型
```

---

## 执行总结

| 会话 | 内容 | 预估 | 依赖 | 并行 |
|------|------|------|------|------|
| **S-1** | JNI构建 + LlamaContext + InferenceBackend接口 | 2-3d | 无 | - |
| **S-2** | LlamaCppBackend + LocalInferenceEngine (三槽位) | 3-4d | S-1 | +S-3 |
| **S-3** | ModelStorageManager + GGUF解析 | 2-3d | S-1 | +S-2 |
| **S-4** | LocalProtocol 集成 + Prompt模板 | 2-3d | S-2 | - |
| **S-5A** | ModelsScreen + ViewModel 改造 | 2d | S-3+S-4 | +S-5B |
| **S-5B** | 设置开关 + Provider入口 | 1d | S-4 | +S-5A |
| **S-6** | Application 集成 + 生命周期 | 1-2d | S-5A+S-5B | - |
| **合计** | | **13-18d** | | |

**关键路径** (wall clock): S-1 → S-2 → S-4 → S-5A → S-6 ≈ **10-12 天**

### 远期扩展路径

| 阶段 | 触发条件 | 改动量 | 说明 |
|------|---------|--------|------|
| **Phase B** | ggml-hexagon 合并 llama.cpp 主线 | 新增 1 个 Backend 实现类 | `LlamaHexagonBackend : InferenceBackend`，GGUF 模型文件不变 |
| **Phase C** | 高级用户需求 | 新增 1 个 Backend + .pte 下载流 | `ExecuTorchBackend : InferenceBackend`，单独模型文件 |

> 架构保证：所有远期扩展仅需新增实现类，上层代码（引擎/协议/UI/ViewModel）零改动。

---

*文档结束*
