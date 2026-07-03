# Plan: 2_rag_ingest_file_and_inquery

## Context
Extend existing local-rag app (Spring Boot 3 + Spring AI, Ports & Adapters, `pl.km`) to:
1. Ingest binary/text files via multipart upload (not just raw JSON text)
2. Add query endpoint to perform similarity search against RAG DB

---

## Files created/modified

### build.gradle
Added: `implementation 'org.springframework.ai:spring-ai-tika-document-reader'`

### New ports
- `application/port/out/FileParserPort.java` — `parse(filename, InputStream) → String`
- `application/port/in/IngestFileUseCase.java` — `ingest(filename, InputStream)`
- `application/port/in/QueryDocumentUseCase.java` — `query(question, topK) → List<QueryResult>`
- `application/port/out/VectorSearchPort.java` — `search(query, topK) → List<QueryResult>`

### New domain model
- `domain/model/QueryResult.java` — record: name, content, score

### New adapters
- `adapter/out/parsing/TikaFileParserAdapter.java` — uses `TikaDocumentReader` (Spring AI)
- `adapter/out/persistence/PgVectorSearchAdapter.java` — uses `VectorStore.similaritySearch`

### Modified services
- `application/service/IngestDocumentService.java` — now also implements `IngestFileUseCase`

### New service
- `application/service/QueryDocumentService.java` — implements `QueryDocumentUseCase`

### New REST DTOs
- `adapter/in/rest/QueryRequest.java` — record: question, topK (defaults to 5)
- `adapter/in/rest/QueryResultDto.java` — record: name, content, score

### Modified controller
- `adapter/in/rest/DocumentController.java` — added:
  - `POST /api/documents/ingest/file` (multipart)
  - `POST /api/documents/query`

---

## Verification
1. `./gradlew build` — compiles cleanly
2. Upload file: `curl -X POST http://localhost:8080/api/documents/ingest/file -F "file=@test.pdf"`
3. Query: `curl -X POST http://localhost:8080/api/documents/query -H 'Content-Type: application/json' -d '{"question":"what is this about","topK":3}'`

---

## No questions

---

## Branch
`feature/2-rag-ingest-file-and-query` ✓ created

---

## Fix: wrap query response in object

**Issue:** `POST /api/documents/query` returned `List<QueryResultDto>` directly instead of wrapped in an object.

**Change:**
- Added `adapter/in/rest/QueryResponse.java` — record: `List<QueryResultDto> results`
- Updated `DocumentController.query()` return type from `ResponseEntity<List<QueryResultDto>>` to `ResponseEntity<QueryResponse>`

**Response shape after fix:**
```json
{ "results": [ { "name": "...", "content": "...", "score": 0.9 } ] }
```
