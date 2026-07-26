# Plan: 6_reranker

## Context
`/api/documents/query` ranks solely by pgvector cosine similarity, and the score threshold is applied *at fetch time* (`PgVectorSearchAdapter` → `SearchRequest.similarityThreshold`). Cosine similarity is a weak relevance signal. Add a **local cross-encoder reranker** (fully local, no external API — consistent w/ existing `TransformersEmbeddingModel` stack) to the query flow: vector search over-fetches a candidate pool w/o threshold, cross-encoder re-scores query↔chunk pairs, and the response score threshold is applied to **reranker** scores (not vector fetch). "Ingest" in the brief = typo; reranking only meaningful in query path (confirmed).

## New query flow (`QueryDocumentService`)
1. threshold = `score != null ? score : queryProperties.defaultScoreThreshold()` (0 ⇒ no filter, unchanged semantics)
2. candidates = `vectorSearchPort.search(question, candidatePoolSize)` — over-fetch, **no** similarity threshold
3. reranked = `rerankerPort.rerank(question, candidates)` — re-scored, sorted desc
4. `reranked.stream().filter(r -> r.score() >= threshold).limit(topK).toList()`

## Reranker backend
Model: cross-encoder `ms-marco-MiniLM-L-6-v2` ONNX export (e.g. `Xenova/ms-marco-MiniLM-L-6-v2`: `model.onnx` + `tokenizer.json`). Reuse deps already transitively pulled by `spring-ai-transformers`: `ai.djl.huggingface:tokenizers` (tokenize query+passage pair → ids/attention/type-ids) + `com.microsoft.onnxruntime:onnxruntime` (inference). Single logit output → `sigmoid` ⇒ score in [0,1] (keeps threshold semantics compatible w/ existing `[0,1]` validation). If compile can't resolve the two libs transitively → add explicit `implementation` entries in build.gradle.

## New files
- `application/port/out/RerankerPort.java` — `List<QueryResult> rerank(String query, List<QueryResult> candidates)` (returns re-scored, sorted desc; empty→empty).
- `adapter/out/reranking/OnnxCrossEncoderRerankerAdapter.java` — `@Component implements RerankerPort, DisposableBean`. On construct: resolve model + tokenizer `Resource`s from `RerankerProperties`, cache to local files if non-filesystem, load `HuggingFaceTokenizer` + `OrtEnvironment`/`OrtSession` (maxLength 512). Per candidate: encode (query, chunk.content), feed int64 `input_ids`/`attention_mask`/`token_type_ids`, read logit, `score = sigmoid(logit)`, build new `QueryResult(name, content, score)`. Sort desc. `destroy()` closes session/env.
- `config/RerankerProperties.java` — `@ConfigurationProperties(prefix="rag.reranker")` record(`Resource modelUri`, `Resource tokenizerUri`).

## Modified files
- `application/port/out/VectorSearchPort.java` — `search(String query, int topK)` (drop `scoreThreshold`).
- `adapter/out/persistence/PgVectorSearchAdapter.java` — remove `.similarityThreshold(...)`; `topK` = candidate pool size.
- `application/service/QueryDocumentService.java` — inject `RerankerPort`; implement new flow above; `search` uses `queryProperties.candidatePoolSize()`.
- `config/QueryProperties.java` — add `int candidatePoolSize` (validate `>0`; keep `defaultScoreThreshold` [0,1]).
- `LocalRagApplication.java` — add `RerankerProperties.class` to `@EnableConfigurationProperties`.
- `src/main/resources/application.yml` — add `rag.query.candidate-pool-size: 20`; `rag.reranker.model-uri` / `rag.reranker.tokenizer-uri` (default to `${RERANKER_MODEL_URI:...}` / classpath). Note: `default-score-threshold` now means reranker prob — may need retuning (0.75 kept).
- `build.gradle` — only if needed: explicit `ai.djl.huggingface:tokenizers` + `com.microsoft.onnxruntime:onnxruntime`.
- `docker-compose.yml` — pass `RERANKER_MODEL_URI` / `RERANKER_TOKENIZER_URI` env if remote-download used.
- `claude/plans/app_description.md` — add reranker note.

## Test
- `QueryDocumentServiceTest` — mock `VectorSearchPort` + `RerankerPort`; assert threshold applied to reranker scores, sorted desc, limited to topK, `score==0` ⇒ no filter. (No model needed → runs offline.)

## Verification
1. `./gradlew build` compiles + unit test green.
2. Provision model files (download `model.onnx` + `tokenizer.json`, set URIs). App boots, reranker bean loads.
3. Ingest a doc, `POST /api/documents/query` w/ `topK:3` → results reordered by reranker score (differs from raw cosine order); low-relevance chunks dropped by threshold.
4. `"score":0` → all topK returned (unfiltered); `"score":0.9` → only high-relevance.

## No questions

---

## Implementation status
All changes applied as planned:
- New: `RerankerPort`, `OnnxCrossEncoderRerankerAdapter` (adapter/out/reranking), `RerankerProperties`, `QueryDocumentServiceTest`, `resources/models/reranker/README.md`.
- Modified: `VectorSearchPort` (dropped threshold), `PgVectorSearchAdapter` (no similarityThreshold), `QueryDocumentService` (over-fetch → rerank → threshold+topK), `QueryProperties` (+candidatePoolSize), `LocalRagApplication`, `application.yml`, `build.gradle` (djl tokenizers + onnxruntime), `docker-compose.yml`, `app_description.md`.

Build not verified locally — no JDK/Gradle toolchain in sandbox (JAVA_HOME unset). Run `./gradlew build` in a JDK 21 env. Reranker bean requires `model.onnx` + `tokenizer.json` (see resources/models/reranker/README.md) to boot.

---

## Branch
`feature/6-reranker` ✓ created
