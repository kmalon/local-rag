# Plan: 3_rag_chunks

## Context
Currently `IngestDocumentService.ingest(name, content)` embeds the whole document as one vector. Need semantic/structural chunking (paragraph/heading boundaries) w/ configurable ~20% overlap, fallback to configurable fixed-window split when text has no structural boundaries. Also restrict file ingestion to md/text/pdf, returning proper error for unsupported types.

Chunking applies in the shared `ingest(name, content)` path, so both raw-text ingest (`/api/documents/ingest`) and file ingest (`/api/documents/ingest/file`, which delegates to it after Tika parsing) get chunked.

## Config
New `rag.chunking.*` properties (application.yml), chars-based (no tokenizer dependency needed):
- `chunk-size` (int, default 1000) — target/max chunk size, also fixed-window fallback size
- `overlap-ratio` (double, default 0.2) — used for both merge-overlap and fixed-window step

## New files
- `application/port/out/TextSplitterPort.java` — `List<String> split(String text)`
- `adapter/out/chunking/StructuralTextSplitterAdapter.java`:
  1. Split text into blocks on markdown heading markers (`#`+) and blank-line paragraph boundaries
  2. Greedily merge consecutive blocks into chunks up to `chunk-size` chars; each new chunk is seeded with the trailing `overlap-ratio * chunk-size` chars of the previous chunk
  3. If splitting yields a single block bigger than `chunk-size` (no structural boundaries found) → fixed-window fallback: slide a `chunk-size`-char window with step `chunk-size * (1 - overlap-ratio)`
- `config/ChunkingProperties.java` — `@ConfigurationProperties(prefix="rag.chunking")` record(chunkSize, overlapRatio)
- `application/exception/UnsupportedFileTypeException.java` — RuntimeException
- `adapter/in/rest/GlobalExceptionHandler.java` — `@RestControllerAdvice`, maps `UnsupportedFileTypeException` → 400 `ErrorResponse`
- `adapter/in/rest/ErrorResponse.java` — record(error: String)

## Modified files
- `domain/model/Document.java` — add `chunkIndex` field + `Document.chunk(name, content, index)` factory (keep `Document.of` for index 0 / non-chunked callers)
- `application/service/IngestDocumentService.java` — inject `TextSplitterPort`; `ingest(name, content)` now: split → for each chunk, build `Document.chunk(name, chunkText, i)`, embed, save
- `adapter/out/parsing/TikaFileParserAdapter.java` — validate filename extension ∈ {md, markdown, txt, pdf} (case-insensitive) before parsing; else throw `UnsupportedFileTypeException` listing supported types
- `adapter/out/persistence/PgVectorDocumentRepository.java` — add `chunkIndex` to metadata map
- `LocalRagApplication.java` — `@EnableConfigurationProperties(ChunkingProperties.class)`
- `application.yml` — add `rag.chunking.chunk-size: 1000`, `rag.chunking.overlap-ratio: 0.2`

## Verification
1. `./gradlew build` — compiles cleanly
2. Ingest a multi-paragraph .md file → expect multiple rows in vector store (chunks), not one
3. Ingest unsupported file type (e.g. `.exe`) → 400 with `{"error": "..."}`
4. Ingest a large single-block .txt with no blank lines → fixed-window fallback produces multiple overlapping chunks
5. `/api/documents/query` still returns `{results:[...]}` (existing shape unaffected)

## No questions

---

## Branch
`feature/3-rag-chunking` ✓ created

---

## Fix: add doc/docx support

**Issue:** File ingestion only allowed `md/markdown/txt/pdf`, but Tika (already used as the parser) also supports `.doc`/`.docx`.

**Change:**
- `adapter/out/parsing/TikaFileParserAdapter.java` — `SUPPORTED_EXTENSIONS` extended with `"doc"`, `"docx"`
