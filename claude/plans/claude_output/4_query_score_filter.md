# Plan: 4_query_score_filter

## Context
`/api/documents/query` currently ignores similarity score entirely — `PgVectorSearchAdapter` builds `SearchRequest` with only `topK`, no threshold. Requirement: let caller optionally pass a score threshold in the request body to filter low-relevance chunks; `0` = no filtering (all topK); if omitted, fall back to a configured default. Follows the existing `ChunkingProperties` (`@ConfigurationProperties`) pattern already used for chunking config.

## Config
New `rag.query.default-score-threshold` (double, default `0.75`) in `application.yml`.

## New files
- `config/QueryProperties.java` — `@ConfigurationProperties(prefix="rag.query")` record(defaultScoreThreshold), validate `[0,1]` (mirrors `ChunkingProperties` validation style).

## Modified files
- `adapter/in/rest/QueryRequest.java` — add `Double score` (boxed/nullable = optional field). Keep existing `topK<=0 → 5` normalization in compact constructor.
- `application/port/in/QueryDocumentUseCase.java` — `query(String question, int topK, Double score)`.
- `application/service/QueryDocumentService.java` — inject `QueryProperties`; resolve `score == null ? queryProperties.defaultScoreThreshold() : score`, pass resolved double to `VectorSearchPort`.
- `application/port/out/VectorSearchPort.java` — `search(String query, int topK, double scoreThreshold)`.
- `adapter/out/persistence/PgVectorSearchAdapter.java` — add `.similarityThreshold(scoreThreshold)` to `SearchRequest.builder()` call.
- `adapter/in/rest/DocumentController.java` — pass `request.score()` through to `queryDocumentUseCase.query(...)`.
- `LocalRagApplication.java` — `@EnableConfigurationProperties({ChunkingProperties.class, QueryProperties.class})`.
- `application.yml` — add `rag.query.default-score-threshold: 0.75`.

## Verification
1. `./gradlew build` compiles cleanly
2. Query with `"score": 0` → results unfiltered (same as current no-threshold behavior)
3. Query with `score` omitted → default threshold (0.75) applied, low-similarity results excluded
4. Query with explicit `"score": 0.9` → only high-similarity results returned

## No questions

---

## Implementation status
All changes applied as planned (see diff). Build not verified locally — no Java/Docker toolchain available in this sandbox; recommend `./gradlew build` in an environment with JDK 21.

---

## Branch
`feature/4-query-score-filter` ✓ created
