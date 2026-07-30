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
Layout above is as-planned. §10 revises `shared/`, deletes `web/`, and moves the facade.

## Steps

### 1. Move (pure refactor, no logic change)
- `application/model/QueryResult.java` → `pl.km.shared.rag.RagQueryResult`. Update refs: `QueryDocumentService`, `RerankerPort`, `VectorSearchPort`, `QueryDocumentPort`, `PgVectorSearchAdapter`, `OnnxCrossEncoderRerankerAdapter`, `DocumentController`.
- `application/exception/*` → `pl.km.shared.exception`. **Reverted in §10** (back into `rag`).
- `adapter/in/rest/{GlobalExceptionHandler,ErrorResponse}` → `pl.km.web`. **Reverted in §10.**
- Everything else under `adapter/`, `application/` → same paths under `pl.km.rag.`.
- `config/{ApplicationConfig,EmbeddingConfig,ChunkingProperties,QueryProperties,RerankerProperties}` → `pl.km.rag.config`. `SecurityConfig` stays `pl.km.config`.
- `LocalRagApplication`: fix `@EnableConfigurationProperties` imports (`pl.km.rag.config.*`). Component scan of `pl.km` still covers all.
- Tests: `src/test/.../QueryDocumentServiceTest` → `pl.km.rag.application`; `src/integration-test/.../SecurityConfigTest` imports updated (`pl.km.rag.adapter.in.rest.DocumentController`, `pl.km.rag.application.port.in.*`).

### 2. Facade (`pl.km.rag`) — repackaged + given real work in §10
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
- two **optional** client scopes, each holding an `oidc-audience-mapper` (`access.token.claim=true`, `id.token.claim=false`): `rag-api` → `rag-platform`, `rag-mcp-api` → `rag-mcp`. Audience is opt-in; a token with neither scope opens nothing.
- Each client lists `defaultClientScopes` explicitly (`acr basic email profile roles web-origins`) — Keycloak replaces, not merges, a client's scope set once `optionalClientScopes` is given, and dropping `roles` would kill the `realm_access` claim every role check depends on.
- Superseded by §9: originally a single client `rag-client` held both scopes, so a caller could request both and get a token valid on both surfaces.

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
- `SecurityConfig.audienceValidator(aud)` — `JwtClaimValidator` on `aud`; missing claim fails.
- **Both** chains validate audience, with different values (two-tier):
  - REST → `keycloak.audience.api` = `rag-platform` (`API_AUDIENCE`) — broad, shared across platform APIs.
  - `/mcp/**` → `keycloak.audience.mcp` = `rag-mcp` (`MCP_AUDIENCE`) — MCP-only, so an agent's token can't be replayed at other APIs.
- `mcp.resource` (`MCP_RESOURCE`, `http://localhost:8080/mcp`) is now **only** the RFC 9728 resource id / well-known path, no longer the `aud` value.
- Operational consequence: every client calling REST needs the `rag-platform` audience configured, else 401.
- `pl.km.mcp.ProtectedResourceMetadata(Controller)` — public RFC 9728 doc at `/.well-known/oauth-protected-resource` + `/…/mcp` (§3.1 path-scoped form); fields `resource`, `authorization_servers` (= `keycloak.issuer-uri`), `bearer_methods_supported`.
- `pl.km.config.ResourceMetadataEntryPoint` — wraps `BearerTokenAuthenticationEntryPoint`, appends `resource_metadata="…"` to the 401 challenge.

Not done: Keycloak 26 ignores RFC 8707 `resource` param (aud comes from mapper regardless); MCP SDK 0.10.0 = HTTP+SSE, predates the auth-spec revision.

### 9. Client split (added after §8)
`aud` alone was a convention, not a boundary: optional scopes are chosen by the caller, so one `rag-client` token could carry both audiences. Split so the audiences are mutually unreachable — a client cannot request an optional scope it isn't assigned:
- `rag-api-client` — optional scopes `… rag-api`; REST callers. **Confidential** (`publicClient=false`, `clientAuthenticatorType=client-secret`).
- `rag-mcp-client` — optional scopes `… rag-mcp-api`; AI agents → MCP. Public by necessity: an agent cannot keep a secret.
- Renamed with the split: scope `mcp-api` → `rag-mcp-api`, audience `rag_mcp` → `rag-mcp` (consistent `rag-` prefix, no underscore).
- Roles unchanged and still orthogonal; both users keep `rag_user` + `rag_mcp_user`, so the client/audience split — not the role — is what isolates the surfaces.

Why confidential on REST: `client_id` is an identifier, not a credential (RFC 6749 §2.1), so with both clients public any local process could run its own flow against `rag-api-client` and the scope split would only be a naming convention. The secret closes that. Viable here because there is no browser frontend — `DocumentController` is driven by scripts/CI, which can hold one. Together the two facts are exhaustive: `rag-platform` requires the `rag-api` scope, which only `rag-api-client` holds, which requires the secret.

Secret plumbing mirrors the existing user passwords: `secrets/rag_api_client_secret.txt` (gitignored by `secrets/*.txt`) → compose secret `rag_api_client_secret` → bash parameter expansion replaces `__RAG_API_CLIENT_SECRET__` in the realm template at container start. Never hand it to an agent; that collapses the split.

Still open (deliberately not done here): PKCE `S256` on `rag-mcp-client`, `directAccessGrantsEnabled=false` outside testing, optional `azp` validator on the MCP chain. Residual risk: the agent runs as the same OS user as the REST caller, so it could read the secret off disk — that is a host/sandboxing problem, outside OAuth's threat model, and the split stops *emergent* agent access, not local credential theft.

### 10. Module boundary + failure contract (added after §9, uncommitted)
Trigger: `RagFacade` had no obvious home — it is neither a hexagonal port nor an adapter, because hexagonal models module↔outside-world while this is module↔module. Model settled on: `rag` and `mcp` are **distinct modules**, one repo today, in-process call today, possibly REST between separate deployables later.

Packaging:
- Contract lives outside `rag`: `pl.km.shared.rag.{RagFacade, RagQueryResult, RagSearchUnavailableException}`. `mcp` imports only this — zero imports of `pl.km.rag`.
- `pl.km.rag.adapter.in.DefaultRagFacade` (public, bean in `ApplicationConfig`) — correctly an **inbound adapter** now, because it implements a contract `rag` does not own and translates model→contract. Was wrong while the interface still lived inside `rag` (that was a port in an adapter package).
- `QueryResult` split by lifecycle: `pl.km.rag.application.model.QueryResult` (internal, used by `QueryDocumentPort`/`VectorSearchPort`/`RerankerPort`) vs `pl.km.shared.rag.RagQueryResult` (published). Before the split one record was simultaneously rag's internal model *and* the MCP tool's wire schema — renaming a field in a vector-search adapter would have silently changed the published schema.
- Exceptions moved back into the owner: `pl.km.rag.application.exception.{RerankerException, UnsupportedFileTypeException}`. `pl.km.shared.exception` deleted. Rationale: `UnsupportedFileTypeException` is ingest-only, so `mcp` (search-only) can never throw it; and `shared` was never needed to share a handler — a global `@RestControllerAdvice` could always import from `rag`, so `shared` only pre-paid for a boundary rule not yet adopted.
- `GlobalExceptionHandler` → `pl.km.rag.adapter.in.rest.RagExceptionHandler`, scoped `@RestControllerAdvice(basePackageClasses = DocumentController.class)`; `ErrorResponse` moved with it; `pl.km.web` deleted. Error mapping is protocol-specific, so it belongs to the adapter owning the protocol. No behavioural change — both exceptions are rag-only.

Failure contract (fail loudly, no degradation):
- `RagSearchUnavailableException` is part of the published contract; a contract without declared failure modes is incomplete. Consumers catch it instead of any rag-internal exception, so an in-process and a future remote implementation report failure identically (REST split → the client adapter maps 503 onto it; `RagMcpTools` unchanged).
- `DefaultRagFacade` catches `RerankerException` (retryable wording) **and** bare `RuntimeException` (generic wording), logs both at ERROR with the cause, rethrows. The catch-all is an information-leak guard, not tidiness — see the MCP semantics below. Cost: an NPE reports as "search failed unexpectedly"; the ERROR log keeps the real cause. Question is deliberately not logged.
- REST is unaffected: `DocumentController` calls `QueryDocumentPort` directly, so it still sees raw `RerankerException` → 503. Each adapter sees what it needs.

MCP error semantics — **handled out of the box**, nothing to configure:
- `MethodToolCallback.call()` catches the reflective `InvocationTargetException` → `ToolExecutionException(toolDefinition, cause)`, whose `getMessage()` delegates to the cause's.
- `McpToolUtils.lambda$toSyncToolSpecification$0` wraps the call in `catch (Exception e)` → `new CallToolResult(List.of(new TextContent(e.getMessage())), true)`. Result: HTTP 200, JSON-RPC *success*, `isError: true`, SSE session survives — failure scoped to the one tool call. Matches the MCP spec (execution errors go in the result, not as protocol errors).
- **`getMessage()` is surfaced verbatim to the agent.** Hence the wording constants in `DefaultRagFacade`, and hence the catch-all: without it a pgvector failure would ship `"connection refused at 10.0.0.1:5432"` to an external agent. No stack trace crosses over.
- `ToolExecutionExceptionProcessor` is **not** on this path — it is consulted by `DefaultToolCallingManager` (ChatClient side) only. A processor bean here would be dead code.
- Nothing in Spring AI logs this path (`McpToolUtils` has no logger; the default processor logs at debug and is unused), so the facade's ERROR log is the only record of an MCP-side failure.

Rejected: skipping the reranker when `minScore == 0` as a client-side way to dodge reranker failures. The score filter *is* a no-op at 0 (sigmoid ⇒ scores in (0,1)), but the reranker's main job is reordering — `poolSize = max(20, topK)` then `.limit(topK)` takes the top-K *of the reranked order*, so skipping it silently returns top-K by raw vector similarity. It would also overload a quality knob as a failure switch (`0` and `0.01` running different pipelines), and a client can only discover the need after a failure, then unknowingly accept degraded ranking. If wanted later: explicit `rerank: false` param or a `degraded` response flag.

Still open: rename `pl.km.shared.rag` → `pl.km.ragapi` (it is a published API with its own compatibility rules, not a shared kernel; `shared` now holds nothing else); `minScore` unvalidated on both entry points (`QueryProperties` checks `[0,1]` for the configured default, `QueryRequest` clamps only `topK`, MCP passes through — `2.0` ⇒ empty list, `-1` ⇒ unfiltered); Spring Modulith / ArchUnit to enforce "no module imports another's `adapter..`".

## Verification
- `./gradlew clean check` — **done, 25 tests green** (13 unit / 12 integration). Note: container `pids.max=256`; kill stale Gradle JVMs first or test workers die with `pthread_create EAGAIN`.
- §10 is **unverified — never compiled**: no JDK on the machine where it was written (`JAVA_HOME` unset, no `javac`). Needs `./gradlew check`. New unit test `src/test/java/pl/km/rag/adapter/in/DefaultRagFacadeTest` (model→contract mapping, `RerankerException` translation, no internal detail in the message). Static check done: no references to `pl.km.shared.exception` or `pl.km.web` remain.
- Unit `src/test/java/pl/km/mcp/RagMcpToolErrorReportingTest` — drives `search_rag_documents` through the real `MethodToolCallback` → `ToolExecutionException` → `McpToolUtils` → `CallToolResult` chain with only `QueryDocumentPort` stubbed: reranker failure ⇒ `isError=true` carrying the facade's wording; unexpected failure ⇒ no `pgvector`/host/exception-class text; success ⇒ `isError=false`. This is what verifies the one step inferred from bytecode (`ToolExecutionException.getMessage()` delegating to its cause). Lives in `src/test` because it starts no Spring context — that, not "does it touch third-party code", is the split this project uses between the two source sets (every existing file follows it: `src/test` = plain JUnit/Mockito, `src/integration-test` = `@WebMvcTest` slices).
- Still not exercised end-to-end: `tools/call` over a real authenticated SSE session against the running stack (needs Keycloak + pgvector up).
- `docker compose down -v && docker compose up --build` — **realm re-import required**, old tokens carry the old client/aud. Token: `curl -d client_id=rag-mcp-client -d username=Admin -d password=<secret> -d grant_type=password -d scope="openid rag-mcp-api" http://localhost:8081/realms/local-rag/protocol/openid-connect/token`.
- Check claim: `… | jq -r .access_token | cut -d. -f2 | base64 -d | jq '.aud, .azp, .realm_access.roles'` → aud has `rag-mcp` (or `rag-platform` for `-d client_id=rag-api-client -d client_secret=$(cat secrets/rag_api_client_secret.txt) -d scope="openid rag-api"`), roles non-empty.
- **Confidential-client check**: same REST token request *without* `client_secret` → `401 invalid_client`. This is the step that makes the split a boundary rather than a convention.
- **First-boot check** (realm import with an explicit `clientScopes` array historically suppressed the built-in scopes, keycloak/keycloak#10021): if `realm_access.roles` is missing or Admin Console shows no `roles` scope on either client, the built-ins were not created → add them to the import or assign via kcadm.
- Cross-check the two tiers: `rag-api-client` + `scope=openid rag-api` → `/api/documents/query` 200, `/mcp/sse` 401; `rag-mcp-client` + `scope=openid rag-mcp-api` → the reverse; no scope → both 401.
- **Split check**: `rag-mcp-client` + `scope="openid rag-api"` → token issued but `aud` has **no** `rag-platform` (unassigned optional scope is silently dropped) → `/api/documents/query` 401. Same in reverse for `rag-api-client` + `rag-mcp-api`.
- `curl -s http://localhost:8080/.well-known/oauth-protected-resource | jq` → metadata, no token needed.
- `curl -N -H "Authorization: Bearer $T" http://localhost:8080/mcp/sse` → SSE `endpoint` event; no token → 401 + `WWW-Authenticate: … resource_metadata="…"`; user lacking `rag_mcp_user` → 403.
- Ingest a doc via `/api/documents/ingest` (Admin), then MCP `tools/list` + `tools/call search_rag_documents` over the SSE session → chunk returned. **Not yet run** (needs the stack up).

## Unresolved questions
1. `sse-endpoint` under `/mcp/sse` (single `/mcp/**` security rule) vs Spring AI default `/sse` — OK to deviate from default?
2. MCP server `version` hardcoded `1.0.0` vs app version `1.0-SNAPSHOT` — care?
3. Keycloak: give `rag_mcp_user` to both `Admin` and `User`, or `Admin` only?
4. If the platform grows to several APIs behind one Keycloak: move to per-API audience via client scopes and validate `aud` on the REST chain too?
