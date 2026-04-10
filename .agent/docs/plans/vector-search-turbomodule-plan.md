# Vector Search TurboModule 实施方案

> **Status**: Draft
> **Created**: 2026-02-16
> **Prerequisite**: React Native New Architecture (已启用)

---

## 1. 架构概述

### 1.1 技术选型

| 技术 | 选择 | 理由 |
|------|------|------|
| **模块类型** | TurboModule | 新架构标准，支持 JSI 零拷贝 |
| **数据传递** | Float32Array (JSI) | 避免 JSON 序列化开销 |
| **并行计算** | OpenMP (可选) | 大规模向量时启用多线程 |
| **平台支持** | Android + iOS | 双平台原生实现 |

### 1.2 架构图

```mermaid
graph TB
    subgraph "JavaScript Layer"
        A[VectorStore.search] --> B[NativeVectorSearch.search]
    end
    
    subgraph "JSI Bridge"
        B -->|Zero-copy| C[Float32Array Access]
    end
    
    subgraph "Native Layer (C++)"
        C --> D[VectorSearchModule.cpp]
        D --> E{Vector Count}
        E -->|< 1000| F[Single Thread]
        E -->|>= 1000| G[OpenMP Parallel]
        F --> H[Sort & Return]
        G --> H
    end
    
    subgraph "JavaScript Layer"
        H -->|JSI| I[SearchResult[]]
    end
```

---

## 2. 文件结构

```
src/
├── native/
│   └── VectorSearch/
│       ├── index.ts                    # TypeScript 接口
│       └── NativeVectorSearch.ts       # Native Module Spec
│
android/
└── app/
    └── src/
        └── main/
            ├── java/com/promenar/nexara/
            │   └── VectorSearchPackage.java
            └── jni/
                ├── VectorSearchModule.cpp
                ├── VectorSearchModule.h
                └── CMakeLists.txt

ios/                                    # Expo 需要生成
└── Nexara/
    └── VectorSearch/
        ├── VectorSearchModule.mm
        └── VectorSearchSpec.h
```

---

## 3. 实施步骤

### Phase 1: TypeScript Spec 定义 (0.5h)

#### 3.1.1 创建 Native Module Spec

**文件**: `src/native/VectorSearch/NativeVectorSearch.ts`

```typescript
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  search(
    queryEmbedding: Float32Array,
    candidateEmbeddings: Float32Array[],
    candidateIds: string[],
    threshold: number,
    limit: number
  ): Promise<ReadonlyArray<{
    id: string;
    similarity: number;
  }>>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('VectorSearch');
```

#### 3.1.2 创建公开接口

**文件**: `src/native/VectorSearch/index.ts`

```typescript
import NativeVectorSearch from './NativeVectorSearch';

export interface VectorSearchResult {
  id: string;
  similarity: number;
}

export async function searchVectors(
  queryEmbedding: Float32Array,
  candidates: Array<{
    id: string;
    embedding: Float32Array;
  }>,
  threshold: number = 0.7,
  limit: number = 5
): Promise<VectorSearchResult[]> {
  if (!NativeVectorSearch) {
    throw new Error('VectorSearch native module not available');
  }

  const candidateEmbeddings = candidates.map(c => c.embedding);
  const candidateIds = candidates.map(c => c.id);

  const results = await NativeVectorSearch.search(
    queryEmbedding,
    candidateEmbeddings,
    candidateIds,
    threshold,
    limit
  );

  return results.map(r => ({
    id: r.id,
    similarity: r.similarity,
  }));
}
```

---

### Phase 2: Android 原生实现 (3h)

#### 3.2.1 CMakeLists.txt

**文件**: `android/app/src/main/jni/CMakeLists.txt`

```cmake
cmake_minimum_required(VERSION 3.13)
project(vectorsearch)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# OpenMP 支持 (可选，用于并行计算)
find_package(OpenMP)
if(OpenMP_CXX_FOUND)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
endif()

add_library(vectorsearch SHARED
    VectorSearchModule.cpp
)

target_include_directories(vectorsearch PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(vectorsearch
    fbjni::fbjni
    reactnative::reactnativejni
)
```

#### 3.2.2 C++ Header

**文件**: `android/app/src/main/jni/VectorSearchModule.h`

```cpp
#pragma once

#include <fbjni/fbjni.h>
#include <react/jni/CxxModuleWrapper.h>
#include <jsi/jsi.h>
#include <vector>
#include <cmath>
#include <algorithm>

namespace facebook {
namespace react {

class VectorSearchModule : public jni::HybridClass<VectorSearchModule> {
public:
  static constexpr auto kJavaDescriptor = 
    "Lcom/promenar/nexara/VectorSearchModule;";

  static void registerNatives() {
    javaClassStatic()->registerNatives({
      makeNativeMethod("nativeSearch", VectorSearchModule::nativeSearch),
    });
  }

private:
  friend HybridBase;

  static jni::local_ref<jni::JArrayFloat> nativeSearch(
    jni::alias_ref<jni::JArrayFloat> queryEmbedding,
    jni::alias_ref<jni::JArrayClass<jni::JArrayFloat>> candidateEmbeddings,
    jni::alias_ref<jni::JArrayString> candidateIds,
    jni::alias_ref<jni::JDouble> threshold,
    jni::alias_ref<jni::JInt> limit
  );
};

} // namespace react
} // namespace facebook
```

#### 3.2.3 C++ 实现

**文件**: `android/app/src/main/jni/VectorSearchModule.cpp`

```cpp
#include "VectorSearchModule.h"
#include <omp.h>

using namespace facebook::jni;
using namespace facebook::react;

namespace {

struct SearchResult {
  float similarity;
  int index;
  
  bool operator<(const SearchResult& other) const {
    return similarity > other.similarity; // 降序
  }
};

float cosineSimilarity(
  const float* a, 
  const float* b, 
  size_t len,
  float queryMag
) {
  float dot = 0.0f;
  float magB = 0.0f;
  
  for (size_t i = 0; i < len; i++) {
    dot += a[i] * b[i];
    magB += b[i] * b[i];
  }
  
  magB = std::sqrt(magB);
  
  if (queryMag == 0.0f || magB == 0.0f) {
    return 0.0f;
  }
  
  return dot / (queryMag * magB);
}

} // anonymous namespace

jni::local_ref<jni::JArrayFloat> VectorSearchModule::nativeSearch(
  alias_ref<jni::JArrayFloat> queryEmbedding,
  alias_ref<jni::JArrayClass<jni::JArrayFloat>> candidateEmbeddings,
  alias_ref<jni::JArrayString> candidateIds,
  alias_ref<jni::JDouble> threshold,
  alias_ref<jni::JInt> limit
) {
  // 获取查询向量
  size_t queryLen = queryEmbedding->size();
  std::vector<float> query(queryLen);
  queryEmbedding->getRegion(0, queryLen, query.data());
  
  // 预计算查询向量模长
  float queryMag = 0.0f;
  for (size_t i = 0; i < queryLen; i++) {
    queryMag += query[i] * query[i];
  }
  queryMag = std::sqrt(queryMag);
  
  // 获取候选向量数量
  size_t numCandidates = candidateEmbeddings->size();
  float thresh = threshold->doubleValue();
  int lim = limit->intValue();
  
  // 并行计算相似度
  std::vector<SearchResult> results;
  
  #pragma omp parallel for schedule(dynamic) if(numCandidates > 500)
  for (size_t i = 0; i < numCandidates; i++) {
    auto embArray = candidateEmbeddings->getElement(i);
    size_t embLen = embArray->size();
    
    if (embLen != queryLen) {
      continue; // 维度不匹配
    }
    
    std::vector<float> emb(embLen);
    embArray->getRegion(0, embLen, emb.data());
    
    float similarity = cosineSimilarity(
      query.data(), 
      emb.data(), 
      queryLen, 
      queryMag
    );
    
    if (similarity >= thresh) {
      #pragma omp critical
      {
        results.push_back({similarity, static_cast<int>(i)});
      }
    }
  }
  
  // 排序
  std::sort(results.begin(), results.end());
  
  // 构建返回结果
  int resultCount = std::min(static_cast<int>(results.size()), lim);
  auto resultArray = jni::JArrayFloat::newArray(resultCount * 2); // [id_index, similarity, ...]
  
  for (int i = 0; i < resultCount; i++) {
    resultArray->setElement(i * 2, static_cast<float>(results[i].index));
    resultArray->setElement(i * 2 + 1, results[i].similarity);
  }
  
  return resultArray;
}

void registerVectorSearchModule() {
  VectorSearchModule::registerNatives();
}
```

#### 3.2.4 Java Package

**文件**: `android/app/src/main/java/com/promenar/nexara/VectorSearchModule.java`

```java
package com.promenar.nexara;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

public class VectorSearchModule extends ReactContextBaseJavaModule {
    static {
        System.loadLibrary("vectorsearch");
    }

    public VectorSearchModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "VectorSearch";
    }

    private native float[] nativeSearch(
        float[] queryEmbedding,
        float[][] candidateEmbeddings,
        String[] candidateIds,
        double threshold,
        int limit
    );

    @ReactMethod
    public void search(
        float[] queryEmbedding,
        float[][] candidateEmbeddings,
        String[] candidateIds,
        double threshold,
        int limit,
        Promise promise
    ) {
        try {
            float[] results = nativeSearch(
                queryEmbedding,
                candidateEmbeddings,
                candidateIds,
                threshold,
                limit
            );

            WritableArray resultArray = Arguments.createArray();
            for (int i = 0; i < results.length; i += 2) {
                int index = (int) results[i];
                float similarity = results[i + 1];
                
                WritableMap map = Arguments.createMap();
                map.putString("id", candidateIds[index]);
                map.putDouble("similarity", similarity);
                resultArray.pushMap(map);
            }

            promise.resolve(resultArray);
        } catch (Exception e) {
            promise.reject("SEARCH_ERROR", e.getMessage());
        }
    }
}
```

#### 3.2.5 Package 注册

**文件**: `android/app/src/main/java/com/promenar/nexara/VectorSearchPackage.java`

```java
package com.promenar.nexara;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VectorSearchPackage implements ReactPackage {
    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();
        modules.add(new VectorSearchModule(reactContext));
        return modules;
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }
}
```

#### 3.2.6 MainApplication 注册

**文件**: `android/app/src/main/java/com/promenar/nexara/MainApplication.kt` (修改)

```kotlin
// 在 packages 列表中添加
packages.add(VectorSearchPackage())
```

---

### Phase 3: iOS 原生实现 (2h)

> **注意**: iOS 需要先运行 `npx expo prebuild` 生成 ios 目录

#### 3.3.1 Objective-C++ Spec

**文件**: `ios/Nexara/VectorSearch/VectorSearchSpec.h`

```objc
#import <React/RCTBridgeModule.h>
#import <jsi/jsi.h>

@interface VectorSearchSpec : NSObject <RCTBridgeModule>
@end
```

#### 3.3.2 Objective-C++ 实现

**文件**: `ios/Nexara/VectorSearch/VectorSearchModule.mm`

```objc
#import "VectorSearchSpec.h"
#import <React/RCTBridge.h>
#import <Accelerate/Accelerate.h>  // iOS 高性能数学库

@implementation VectorSearchSpec

RCT_EXPORT_MODULE(VectorSearch);

RCT_EXPORT_METHOD(search:(NSArray *)queryEmbedding
                  candidateEmbeddings:(NSArray *)candidateEmbeddings
                  candidateIds:(NSArray *)candidateIds
                  threshold:(double)threshold
                  limit:(double)limit
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
  
  dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
    @try {
      // 转换查询向量
      NSUInteger queryLen = [queryEmbedding count];
      float *query = (float *)malloc(queryLen * sizeof(float));
      for (NSUInteger i = 0; i < queryLen; i++) {
        query[i] = [[queryEmbedding objectAtIndex:i] floatValue];
      }
      
      // 计算查询向量模长
      float queryMag = 0.0f;
      vDSP_svesq(query, 1, &queryMag, queryLen);
      queryMag = sqrtf(queryMag);
      
      // 计算相似度
      NSUInteger numCandidates = [candidateEmbeddings count];
      NSMutableArray *results = [NSMutableArray array];
      
      for (NSUInteger i = 0; i < numCandidates; i++) {
        NSArray *emb = [candidateEmbeddings objectAtIndex:i];
        
        if ([emb count] != queryLen) continue;
        
        float *candidate = (float *)malloc(queryLen * sizeof(float));
        for (NSUInteger j = 0; j < queryLen; j++) {
          candidate[j] = [[emb objectAtIndex:j] floatValue];
        }
        
        // 使用 Accelerate 框架计算点积
        float dot = 0.0f, magB = 0.0f;
        vDSP_dotpr(query, 1, candidate, 1, &dot, queryLen);
        vDSP_svesq(candidate, 1, &magB, queryLen);
        magB = sqrtf(magB);
        
        free(candidate);
        
        float similarity = (queryMag > 0 && magB > 0) ? dot / (queryMag * magB) : 0.0f;
        
        if (similarity >= threshold) {
          [results addObject:@{
            @"index": @(i),
            @"similarity": @(similarity)
          }];
        }
      }
      
      // 排序
      [results sortUsingComparator:^NSComparisonResult(id a, id b) {
        float simA = [[a objectForKey:@"similarity"] floatValue];
        float simB = [[b objectForKey:@"similarity"] floatValue];
        return (simB < simA) ? NSOrderedDescending : ((simB > simA) ? NSOrderedAscending : NSOrderedSame);
      }];
      
      // 构建返回结果
      NSMutableArray *finalResults = [NSMutableArray array];
      NSUInteger resultCount = MIN([results count], (NSUInteger)limit);
      
      for (NSUInteger i = 0; i < resultCount; i++) {
        NSDictionary *result = [results objectAtIndex:i];
        NSUInteger index = [[result objectForKey:@"index"] unsignedIntegerValue];
        
        [finalResults addObject:@{
          @"id": [candidateIds objectAtIndex:index],
          @"similarity": [result objectForKey:@"similarity"]
        }];
      }
      
      free(query);
      resolve(finalResults);
      
    } @catch (NSException *e) {
      reject(@"SEARCH_ERROR", e.reason, nil);
    }
  });
}

@end
```

---

### Phase 4: VectorStore 集成 (1h)

**文件**: `src/lib/rag/vector-store.ts` (修改)

```typescript
import { db } from '../db';
import { generateId } from '../utils/id-generator';
import { searchVectors } from '../native/VectorSearch';

export class VectorStore {
  // ... 其他方法保持不变 ...

  async search(
    queryEmbedding: number[],
    options: {
      limit?: number;
      threshold?: number;
      filter?: { docId?: string; docIds?: string[]; sessionId?: string; type?: string };
    } = {},
  ): Promise<SearchResult[]> {
    const Limit = options.limit || 5;
    const Threshold = options.threshold || 0.7;

    // Step 1: SQL 查询
    let sql = 'SELECT * FROM vectors';
    const params: any[] = [];
    // ... (现有过滤逻辑)

    const results = await db.execute(sql, params);
    if (!results.rows) return [];

    // Step 2: 准备原生模块输入
    const queryTyped = new Float32Array(queryEmbedding);
    const candidates: Array<{ id: string; embedding: Float32Array }> = [];

    for (let i = 0; i < results.rows.length; i++) {
      const row = results.rows[i];
      const vec = new Float32Array(this.fromBlob(row.embedding));
      candidates.push({
        id: row.id as string,
        embedding: vec,
      });
    }

    // Step 3: 调用原生模块
    try {
      const nativeResults = await searchVectors(
        queryTyped,
        candidates,
        Threshold,
        Limit
      );

      // Step 4: 映射回完整结果
      return nativeResults.map(r => {
        const row = results.rows!.find((row: any) => row.id === r.id);
        return {
          id: r.id,
          docId: row?.doc_id,
          sessionId: row?.session_id,
          content: row?.content,
          embedding: this.fromBlob(row?.embedding),
          metadata: row?.metadata ? JSON.parse(row.metadata) : undefined,
          createdAt: row?.created_at,
          similarity: r.similarity,
        };
      });
    } catch (e) {
      // 降级到 JS 实现
      console.warn('[VectorStore] Native search failed, falling back to JS:', e);
      return this.searchJS(queryEmbedding, options);
    }
  }

  private async searchJS(
    queryEmbedding: number[],
    options: SearchOptions = {},
  ): Promise<SearchResult[]> {
    // 原有的 JS 实现作为降级方案
    // ...
  }
}
```

---

## 4. 构建配置

### 4.1 Android build.gradle 修改

**文件**: `android/app/build.gradle` (添加)

```groovy
android {
    // ... 现有配置 ...
    
    externalNativeBuild {
        cmake {
            path "src/main/jni/CMakeLists.txt"
        }
    }
    
    defaultConfig {
        // ... 现有配置 ...
        
        externalNativeBuild {
            cmake {
                arguments "-DANDROID_STL=c++_shared"
            }
        }
    }
}
```

### 4.2 iOS Podfile 修改

**文件**: `ios/Podfile` (添加)

```ruby
target 'Nexara' do
  # ... 现有配置 ...
  
  # VectorSearch 模块
  pod 'VectorSearch', :path => '../node_modules/@nexara/vector-search'
end
```

---

## 5. 验证计划

### 5.1 单元测试

**文件**: `__tests__/vector-search-native.test.ts`

```typescript
import { searchVectors } from '../src/native/VectorSearch';

describe('VectorSearch Native Module', () => {
  it('should return correct similarity results', async () => {
    const query = new Float32Array([1, 0, 0]);
    const candidates = [
      { id: 'a', embedding: new Float32Array([1, 0, 0]) }, // similarity = 1.0
      { id: 'b', embedding: new Float32Array([0, 1, 0]) }, // similarity = 0.0
      { id: 'c', embedding: new Float32Array([0.707, 0.707, 0]) }, // similarity ≈ 0.707
    ];

    const results = await searchVectors(query, candidates, 0.5, 2);

    expect(results.length).toBe(2);
    expect(results[0].id).toBe('a');
    expect(results[0].similarity).toBeCloseTo(1.0, 3);
    expect(results[1].id).toBe('c');
    expect(results[1].similarity).toBeCloseTo(0.707, 2);
  });

  it('should handle empty candidates', async () => {
    const query = new Float32Array([1, 0, 0]);
    const results = await searchVectors(query, [], 0.5, 5);
    expect(results.length).toBe(0);
  });
});
```

### 5.2 性能基准测试

| 向量数量 | JS 耗时 | Native 耗时 | 提升 |
|----------|---------|-------------|------|
| 100 | ~10ms | ~2ms | 5x |
| 500 | ~50ms | ~8ms | 6x |
| 1000 | ~100ms | ~15ms | 7x |
| 5000 | ~500ms | ~60ms | 8x |

---

## 6. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| iOS 目录不存在 | 高 | 先运行 `npx expo prebuild` |
| C++ 编译错误 | 中 | 使用 C++17 标准，避免新特性 |
| OpenMP 兼容性 | 低 | 添加编译时检测，降级到单线程 |
| 内存泄漏 | 高 | 使用 RAII，确保 malloc/free 配对 |

---

## 7. 实施时间表

| 阶段 | 任务 | 预计时间 |
|------|------|----------|
| Phase 1 | TypeScript Spec | 0.5h |
| Phase 2 | Android 实现 | 3h |
| Phase 3 | iOS 实现 | 2h |
| Phase 4 | VectorStore 集成 | 1h |
| Phase 5 | 测试验证 | 1h |
| **总计** | | **7.5h** |

---

## 8. 后续优化方向

1. **SIMD 加速**: 使用 NEON (ARM) / AVX (x86) 进一步优化
2. **GPU 计算**: 使用 Metal (iOS) / Vulkan (Android) 进行大规模并行
3. **增量更新**: 支持向量增量添加，避免全量重算
4. **量化压缩**: 使用 int8 量化减少内存占用
