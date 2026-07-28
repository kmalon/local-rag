# 8_mcp_server — MCP server + feature packaging

Branch: `feature/8-mcp-server`

## Context
Plan `claude/plans/8_mcp_server.md`. App today = single RAG feature, flat `adapter/application/config` layout under `pl.km`. Need 2nd consumer surface: MCP server so AI agents/LLMs query the RAG knowledge base. Requires: split into feature packages (`rag`, `mcp`), keep common stuff at root, RAG exposes `RagFacade` returning shared-kernel types, MCP guarded by Keycloak JWT role `rag_mcp_user`.

Decisions: MCP surface = search tool only (read-only). REST controller keeps using ports directly (facade = cross-feature API for MCP).

## Target package layout
```
pl.km
├─ LocalRagApplication
├─ config/      SecurityConfig                      (common)
├─ web/         GlobalExceptionHandler, ErrorResponse (common)
├─ shared/      QueryResult                          (shared kernel)
│   └─ exception/ RerankerException, UnsupportedFileTypeException
├─ rag/
│   ├─ RagFacade (public iface), DefaultRagFacade (@Component, pkg-private)
│   ├─ adapter/in/rest/  DocumentController, IngestRequest, QueryRequest, QueryResponse, QueryResultDto
│   ├─ adapter/out/      chunking, parsing, persistence, reranking
│   ├─ application/      IngestDocumentService, QueryDocumentService, model/Document, port/in, port/out
│   └─ config/           ApplicationConfig, EmbeddingConfig, ChunkingProperties, QueryProperties, RerankerProperties
└─ mcp/
    ├─ RagMcpTools (@Tool)
    └─ config/McpServerConfig (ToolCallbackProvider bean)
```

## Steps

### 1. Move (pure refactor, no logic change)
- `application/model/QueryResult.java` → `pl.km.shared.QueryResult`. Update refs: `QueryDocumentService`, `RerankerPort`, `VectorSearchPort`, `QueryDocumentPort`, `PgVectorSearchAdapter`, `OnnxCrossEncoderRerankerAdapter`, `DocumentController`.
- `application/exception/*` → `pl.km.shared.exception`.
- `adapter/in/rest/{GlobalExceptionHandler,ErrorResponse}` → `pl.km.web`.
- Everything else under `adapter/`, `application/` → same paths under `pl.km.rag.`.
- `config/{ApplicationConfig,EmbeddingConfig,ChunkingProperties,QueryProperties,RerankerProperties}` → `pl.km.rag.config`. `SecurityConfig` stays `pl.km.config`.
- `LocalRagApplication`: fix `@EnableConfigurationProperties` imports (`pl.km.rag.config.*`). Component scan of `pl.km` still covers all.
- Tests: `src/test/.../QueryDocumentServiceTest` → `pl.km.rag.application`; `src/integration-test/.../SecurityConfigTest` imports updated (`pl.km.rag.adapter.in.rest.DocumentController`, `pl.km.rag.application.port.in.*`).

### 2. Facade (`pl.km.rag`)
```java
public interface RagFacade { List<QueryResult> search(String question, int topK, Double minScore); }
```
`DefaultRagFacade` (@Component) delegates to `QueryDocumentPort.query(...)`. No new logic — threshold/topK defaults stay in `QueryDocumentService`/`QueryProperties`.

### 3. MCP server
- `build.gradle`: `implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'` (version from existing spring-ai BOM 1.0.0).
- `pl.km.mcp.RagMcpTools`:
```java
@Tool(name="search_rag_documents", description="Search local RAG knowledge base; returns matching document chunks ranked by relevance")
public List<QueryResult> search(@ToolParam(description="natural-language question") String question,
                                @ToolParam(description="max results, default 5", required=false) Integer topK,
                                @ToolParam(description="min reranker score 0..1, default from server config", required=false) Double minScore)
```
  null `topK` → 5 (mirrors `QueryRequest`); null `minScore` → pass through (service applies config default). Calls `RagFacade` only.
- `pl.km.mcp.config.McpServerConfig`: `@Bean ToolCallbackProvider ragToolCallbacks(RagMcpTools t){ return MethodToolCallbackProvider.builder().toolObjects(t).build(); }`
- `application.yml`:
```yaml
spring.ai.mcp.server:
  name: local-rag-mcp
  version: 1.0.0
  type: SYNC
  sse-endpoint: /mcp/sse
  sse-message-endpoint: /mcp/message
```

### 4. Security
`SecurityConfig`: add before `anyRequest()`
```java
.requestMatchers("/mcp/**").hasRole("rag_mcp_user")
```
CSRF already off, stateless already set. JWT realm-role converter unchanged (`ROLE_rag_mcp_user` derived automatically).

### 5. Keycloak
`keycloak/realm-local-rag.json`: add realm role `rag_mcp_user` ("MCP access: query via MCP server"); add it to `realmRoles` of both users `Admin` and `User`.

### 6. Tests
- New unit `src/test/java/pl/km/mcp/RagMcpToolsTest`: mocked `RagFacade` — default topK=5 when null, args passed through.
- New IT `src/integration-test/java/pl/km/config/McpSecurityTest`: `@WebMvcTest` + `@Import(SecurityConfig)` (same shape as `SecurityConfigTest`) on `/mcp/sse` → 401 anonymous, 403 with `ROLE_rag_user`, non-401/403 with `ROLE_rag_mcp_user` (no handler in slice → 404; asserts filter chain, not MCP transport).

### 7. Docs
Update `claude/plans/app_description.md` (feature-package layout + MCP server + `rag_mcp_user`).

## Verification
- `./gradlew clean check` — unit + integration tests green, compile proves refactor complete.
- `docker compose up --build`; get token: `curl -d client_id=rag-client -d username=Admin -d password=<secret> -d grant_type=password http://localhost:8081/realms/local-rag/protocol/openid-connect/token`.
- `curl -N -H "Authorization: Bearer $T" http://localhost:8080/mcp/sse` → SSE stream w/ `endpoint` event; same without token → 401; token of a user lacking `rag_mcp_user` → 403.
- Ingest a doc via `/api/documents/ingest` (Admin), then MCP `tools/list` + `tools/call search_rag_documents` over the SSE session → chunk returned.

## Unresolved questions
1. `sse-endpoint` under `/mcp/sse` (single `/mcp/**` security rule) vs Spring AI default `/sse` — OK to deviate from default?
2. MCP server `version` hardcoded `1.0.0` vs app version `1.0-SNAPSHOT` — care?
3. Keycloak: give `rag_mcp_user` to both `Admin` and `User`, or `Admin` only?
