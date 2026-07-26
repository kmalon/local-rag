# Cross-encoder reranker model

The query endpoint reranks retrieved chunks with a local cross-encoder run via ONNX Runtime.
Model binaries are **not** committed. Provide two files here (classpath default), or override
`rag.reranker.model-uri` / `rag.reranker.tokenizer-uri` (env `RERANKER_MODEL_URI` /
`RERANKER_TOKENIZER_URI`) with `file:` or `https:` URIs.

Expected files (classpath default):
- `model.onnx`
- `tokenizer.json`

Recommended model: `cross-encoder/ms-marco-MiniLM-L-6-v2` (ONNX export, e.g. HuggingFace
`Xenova/ms-marco-MiniLM-L-6-v2`):

```
mkdir -p src/main/resources/models/reranker
curl -L -o src/main/resources/models/reranker/model.onnx \
  https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model.onnx
curl -L -o src/main/resources/models/reranker/tokenizer.json \
  https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer.json
```

The cross-encoder emits a single relevance logit per (query, chunk) pair; it is squashed to
`[0,1]` with a sigmoid so the configured `rag.query.default-score-threshold` stays comparable.
Note: this threshold now filters **reranker** scores, not raw vector similarity, and may need
retuning from the previous cosine-based `0.75`.
