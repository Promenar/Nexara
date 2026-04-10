# 多模态 RAG (Multimodal RAG)

**状态**: ⚪ Draft
**优先级**: Medium

## 目标
支持对图片内容进行向量化索引，允许用户通过自然语言检索图表或截图。

## 初步规划
1. **Embedding**: 调研移动端可用的多模态 Embedding 模型 (如 CLIP-Mobile)。
2. **Storage**: 扩展 `op-sqlite` 的向量表结构，支持 `image_path` 关联。
3. **Pipeline**: 在文件上传流水线中增加图片处理环节。

## 待调研
- `llama.rn` 对多模态模型的支持情况。
- 图片向量化的推理耗时与内存占用。
