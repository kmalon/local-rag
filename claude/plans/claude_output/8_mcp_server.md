# 8_mcp_server — MCP server + feature packaging

Branch: `feature/8-mcp-server`

## Context
Plan `claude/plans/8_mcp_server.md`. App today = single RAG feature, flat `adapter/application/config` layout under `pl.km`. Need 2nd consumer surface: MCP server so AI agents/LLMs query the RAG knowledge base. Requires: split into feature packages (`rag`, `mcp`), keep common stuff at root, RAG exposes `RagFacade` returning shared-kernel types, MCP guarded by Keycloak JWT role `rag_mcp_user`.

Decisions: MCP surface = search tool only (read-only). REST controller keeps using ports directly (facade = cross-feature API for MCP).

## Target package layout
```
pl.km
├─ LocalRagApplication
├─ config/      SecurityConfig, ResourceMetadataEntryPoint (common)
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
    ├─ ProtectedResourceMetadata(Controller)  (RFC 9728 discovery)
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

### 4. Security — two chains (revised during execution, see §8)
- `@Order(1)` `mcpFilterChain`: `securityMatcher("/mcp/**")`, `anyRequest().hasRole("rag_mcp_user")`, decoder = issuer + **audience** validator, entry point = `ResourceMetadataEntryPoint`.
- `@Order(2)` `apiFilterChain`: `/.well-known/oauth-protected-resource[/**]` permitAll; `/api/documents/ingest[/file]` `rag_admin`; `/api/documents/query` `rag_user`; decoder = issuer only (foreign-client tokens must keep working).
- Each chain builds its own decoder via private varargs helper `jwtDecoder(jwkSetUri, issuerUri, extraValidators...)`. No `JwtDecoder` bean (avoids ambiguity).
- CSRF off, stateless, realm-role converter unchanged in both.

### 5. Keycloak
`keycloak/realm-local-rag.json`:
- realm role `rag_mcp_user`; added to `realmRoles` of both users `Admin` and `User`.
- `oidc-audience-mapper` on `rag-client` with `included.custom.audience = http://localhost:8080/mcp`, `access.token.claim=true`, `id.token.claim=false`.

### 6. Tests
- Unit `src/test/java/pl/km/mcp/RagMcpToolsTest`: mocked `RagFacade` — default topK=5 when null/≤0, args passed through.
- Unit `src/test/java/pl/km/config/SecurityConfigAudienceTest`: accept exact/among-many, reject wrong aud, reject missing aud.
- Unit `src/test/java/pl/km/config/ResourceMetadataEntryPointTest`: well-known path derivation (with + without resource path).
- IT `src/integration-test/java/pl/km/config/McpSecurityTest`: `/mcp/sse` → 401 anonymous (+ `WWW-Authenticate` carries `resource_metadata`), 403 with `ROLE_rag_user`, non-401/403 with `ROLE_rag_mcp_user` (no handler in slice → 404; asserts filter chain, not MCP transport).
- IT `src/integration-test/java/pl/km/mcp/ProtectedResourceMetadataControllerTest`: metadata readable with no token, at both well-known paths.
- Slices importing `SecurityConfig` must set `mcp.resource` in `@TestPropertySource` (decoder bean resolves it).

### 7. Docs
Update `claude/plans/app_description.md` (feature-package layout + MCP server + `rag_mcp_user` + auth model).

### 8. MCP authorization (added after plan approval, commits `fa6c570` + `d441e07`)
Original plan had signature+`iss` only → any realm token opened MCP (confused deputy). Added:
- `mcp.resource` property (`MCP_RESOURCE`, default `http://localhost:8080/mcp`) = RFC 9728 resource id **and** required `aud` (RFC 8707). Wired in docker-compose.
- `SecurityConfig.audienceValidator(resource)` — `JwtClaimValidator` on `aud`; missing claim fails.
- Applied to `/mcp/**` only, **not** REST: REST callers arrive with tokens minted for their own clients. Cost: REST rests on realm roles alone.
- `pl.km.mcp.ProtectedResourceMetadata(Controller)` — public RFC 9728 doc at `/.well-known/oauth-protected-resource` + `/…/mcp` (§3.1 path-scoped form); fields `resource`, `authorization_servers` (= `keycloak.issuer-uri`), `bearer_methods_supported`.
- `pl.km.config.ResourceMetadataEntryPoint` — wraps `BearerTokenAuthenticationEntryPoint`, appends `resource_metadata="…"` to the 401 challenge.

Not done: Keycloak 26 ignores RFC 8707 `resource` param (aud comes from mapper regardless); MCP SDK 0.10.0 = HTTP+SSE, predates the auth-spec revision.

## Verification
- `./gradlew clean check` — **done, 25 tests green** (13 unit / 12 integration). Note: container `pids.max=256`; kill stale Gradle JVMs first or test workers die with `pthread_create EAGAIN`.
- `docker compose down -v && docker compose up --build` — **realm re-import required**, old tokens lack `aud`. Token: `curl -d client_id=rag-client -d username=Admin -d password=<secret> -d grant_type=password http://localhost:8081/realms/local-rag/protocol/openid-connect/token`.
- Check claim: `… | jq -r .access_token | cut -d. -f2 | base64 -d | jq .aud` → `http://localhost:8080/mcp`.
- `curl -s http://localhost:8080/.well-known/oauth-protected-resource | jq` → metadata, no token needed.
- `curl -N -H "Authorization: Bearer $T" http://localhost:8080/mcp/sse` → SSE `endpoint` event; no token → 401 + `WWW-Authenticate: … resource_metadata="…"`; user lacking `rag_mcp_user` → 403.
- Ingest a doc via `/api/documents/ingest` (Admin), then MCP `tools/list` + `tools/call search_rag_documents` over the SSE session → chunk returned. **Not yet run** (needs the stack up).

## Unresolved questions
1. `sse-endpoint` under `/mcp/sse` (single `/mcp/**` security rule) vs Spring AI default `/sse` — OK to deviate from default?
2. MCP server `version` hardcoded `1.0.0` vs app version `1.0-SNAPSHOT` — care?
3. Keycloak: give `rag_mcp_user` to both `Admin` and `User`, or `Admin` only?
4. If the platform grows to several APIs behind one Keycloak: move to per-API audience via client scopes and validate `aud` on the REST chain too?
