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
- Each client lists `defaultClientScopes` explicitly (`acr basic email profile roles web-origins`) — Keycloak replaces, not merges, a client's scope set once `optionalClientScopes` is given, and dropping `roles` would kill the `realm_access` claim every role check depends on. **Wrong, fixed in §12:** listing those names was not enough, because declaring `clientScopes` at all stops Keycloak creating the built-ins, so `roles` did not exist to be referenced.
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
- **First-boot check** (realm import with an explicit `clientScopes` array suppresses the built-in scopes, keycloak/keycloak#10021 — confirmed against the 26.0 source in §12, and it did bite): if `realm_access.roles` is missing or Admin Console shows no `roles` scope on either client, the import lost it → §12 declares `roles` in the realm so it cannot.
- Cross-check the two tiers: `rag-api-client` + `scope=openid rag-api` → `/api/documents/query` 200, `/mcp/sse` 401; `rag-mcp-client` + `scope=openid rag-mcp-api` → the reverse; no scope → both 401.
- **Split check**: `rag-mcp-client` + `scope="openid rag-api"` → token issued but `aud` has **no** `rag-platform` (unassigned optional scope is silently dropped) → `/api/documents/query` 401. Same in reverse for `rag-api-client` + `rag-mcp-api`.
- `curl -s http://localhost:8080/.well-known/oauth-protected-resource | jq` → metadata, no token needed.
- `curl -N -H "Authorization: Bearer $T" http://localhost:8080/mcp/sse` → SSE `endpoint` event; no token → 401 + `WWW-Authenticate: … resource_metadata="…"`; user lacking `rag_mcp_user` → 403.
- Ingest a doc via `/api/documents/ingest` (Admin), then MCP `tools/list` + `tools/call search_rag_documents` over the SSE session → chunk returned. **Not yet run** (needs the stack up).

### 11. Streamable HTTP, stateless (added after §10)

**Issue:** *"Add Streamable HTTP to make MCP tool stateless, so pure request/response model."*

§3 shipped HTTP+SSE: the client opens `GET /mcp/sse`, receives an `endpoint` event, POSTs to `/mcp/message`, and reads the reply back off the stream. Server keeps per-session state; a dropped stream loses the session. That contradicts the rest of the app — `SessionCreationPolicy.STATELESS`, a bearer token per request — and makes the server hostile to plain HTTP clients and load balancers. Streamable HTTP (MCP spec 2025-03-26) collapses it to one endpoint; its **stateless** flavour drops sessions entirely: one POST, one JSON response.

**Forced upgrade.** MCP SDK 0.10.0 (Spring AI 1.0.0) has no streamable transport at all — `WebMvcStatelessServerTransport` / `McpServerStatelessWebMvcAutoConfiguration` first appear in Spring AI 1.1.x (SDK 0.18.3), which is built against Boot 3.5.15. So:
- `build.gradle`: Boot `3.3.0` → `3.5.15`, dependency-management `1.1.5` → `1.1.7`, `springAiVersion` `1.0.0` → `1.1.8`; pinned reranker deps re-aligned to what `spring-ai-transformers:1.1.8` pulls (`tokenizers` `0.30.0` → `0.32.0`, `onnxruntime` `1.19.2` → `1.20.0`). Starter artifact name unchanged.
- `application.yml`: `protocol: STATELESS` + `streamable-http.mcp-endpoint: /mcp`; `sse-endpoint`/`sse-message-endpoint` deleted (dead once protocol ≠ SSE). Answers §Unresolved-1 by removing the question.
- `SecurityConfig.mcpFilterChain`: `securityMatcher("/mcp/**")` → `securityMatcher("/mcp", "/mcp/**")`. `/mcp/**` does match the bare `/mcp` under both Ant and PathPattern semantics; the endpoint is now *exactly* that path and this chain is the only thing between an agent and the tool, so it is spelled out rather than inferred.
- `McpServerConfig`: no code change. `StatelessToolCallbackConverterAutoConfiguration` consumes the same `ToolCallbackProvider` bean and converts via `McpToolUtils.toStatelessSyncToolSpecification` — the transport swap does not reach the tool.
- `mcp.resource` unchanged (`http://localhost:8080/mcp`): the RFC 9728 resource id and the endpoint are now literally the same URI. `docker-compose.yml` untouched.

**What stateless gives up** (deliberate, not overlooked): no session id, no `GET /mcp` SSE stream, no server→client traffic — sampling, roots, elicitation, progress notifications, `notifications/tools/list_changed`. This server publishes one read-only tool from a fixed list and uses none of them; `spring.ai.mcp.server.*-change-notification` become no-ops. Clients must not send `Mcp-Session-Id`.

**Error contract from §10 survives unchanged**, on the stateless path: `MethodToolCallback` → `ToolExecutionException` → `McpToolUtils.toStatelessSyncToolSpecification`'s handler → `CallToolResult(isError=true)`. `RagMcpToolErrorReportingTest` was retargeted to it (`spec.callHandler().apply(McpTransportContext.EMPTY, new CallToolRequest(name, args))` replaces the 0.10.0 `spec.call().apply(exchange, argsMap)`), so the wording constants in `DefaultRagFacade` remain covered.

**Tests:** `McpSecurityTest` now drives `POST /mcp` (same 401/challenge/403/allowed assertions) plus two new cases — every method on the bare `/mcp` path is guarded, and the retired `/mcp/sse`, `/mcp/message` sub-paths stay guarded. `@MockBean` → `@MockitoBean` in `McpSecurityTest` + `SecurityConfigTest` (deprecated since Boot 3.4, removed in Boot 4; the upgrade is what surfaced it).

**Verification — not run.** Still no JDK and no Docker on this machine (user opted against installing one), so §11 ships uncompiled like §10. What *was* verified, against the published artifacts rather than memory: `protocol` + `streamable-http.mcp-endpoint` and the `STATELESS` enum constant exist in `spring-ai-autoconfigure-mcp-server-common:1.1.8`'s configuration metadata; `McpServerStatelessWebMvcAutoConfiguration` binds the endpoint via `McpServerStreamableHttpProperties.getMcpEndpoint()`; `McpToolUtils.toStatelessSyncToolSpecification(ToolCallback, MimeType)`, `McpStatelessServerFeatures$SyncToolSpecification.callHandler()`, `McpTransportContext.EMPTY` and `McpSchema$CallToolRequest(String, Map)` all exist in `spring-ai-mcp:1.1.8` / `mcp-core:0.18.3`; the app-side Spring AI API surface (`VectorStore`, `SearchRequest.builder()`, `TransformersEmbeddingModel`, `TikaDocumentReader`, `MethodToolCallbackProvider`) is unchanged between 1.0.0 and 1.1.8.

To run: `./gradlew clean check` (likeliest fallout: Boot 3.5 test-slice or Mockito changes). Then, against the stack, replacing the §Verification SSE steps:
```
curl -si -X POST http://localhost:8080/mcp -H "Authorization: Bearer $T" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
```
→ 200 + JSON body and **no `Mcp-Session-Id` header**; then `tools/list` and `tools/call search_rag_documents` as independent POSTs with no prior handshake state. Negatives unchanged (no token → 401 + `resource_metadata` hint; `rag-api-client` token → 401; missing `rag_mcp_user` → 403), and `GET /mcp/sse` must now 401 rather than open a stream.

(`topK` was still unclamped upward at this point; §13 bounds it.)

### 12. Realm import: built-in `roles` client scope (added after §11)

**Issue** (found by a `/code-review` of PR #5): §5 assumed Keycloak merges its built-in client scopes into an imported realm and that listing `defaultClientScopes` explicitly was enough to keep `roles`. It does not merge. Confirmed in the Keycloak 26.0 source, not from memory:
- `RealmManager.importRealm` (26.0.0, line 580): `if (rep.getClientScopes() == null) createDefaultClientScopes(realm);` — declaring *any* `clientScopes` (as §5 did, for `rag-api` / `rag-mcp-api`) suppresses **all** built-ins.
- `RepresentationToModel.addClientScopeToClient` (line 406): a client referencing a scope that does not exist is skipped with `Referenced client scope 'roles' doesn't exist. Ignoring` — a WARN, not a failure.

So on a fresh `docker compose down -v && up` the clients ended up with no `roles` scope, hence no realm-roles mapper, hence no `realm_access.roles` claim. `SecurityConfig.extractRealmRoles` then grants zero authorities and **every** authenticated request is 403 — REST and MCP alike. Signature, `iss` and `aud` all still pass, so the failure looks like an authorisation bug rather than a realm-import one. This is exactly the "First-boot check" in §Verification; it had never been run.

**Fix:** declare the `roles` scope in the realm alongside the two custom ones, with the three mappers Keycloak's own `OIDCLoginProtocolFactory.addRolesClientScope` attaches (`oidc-usermodel-realm-role-mapper` → `realm_access.roles`, `oidc-usermodel-client-role-mapper` → `resource_access.${client_id}.roles`, `oidc-audience-resolve-mapper`), and trim both clients to `defaultClientScopes: ["roles"]` + their one optional `rag-*` scope. Every scope referenced is now declared in the same file, so the realm no longer depends on what Keycloak seeds.

Dropped from the client lists with that trim: `acr`, `basic`, `email`, `profile`, `web-origins`, `address`, `phone`, `offline_access`, `microprofile-jwt`. Nothing here reads their claims — the app validates `iss`, `aud` and `realm_access.roles`, CORS is configured in `SecurityConfig`, and there is no browser frontend or userinfo consumer. `sub` is a core access-token claim, not one of theirs. The alternative — transcribing all ten built-in scopes and their mappers into the import — was rejected: it is ~250 lines reconstructed from Keycloak internals that no test here covers, and it would drift on every Keycloak upgrade.

Not fixed by this and worth knowing: an *undeclared* scope reference fails silently. If a client later needs `email` or `profile`, declare it in `clientScopes` first — adding it to a client list alone leaves a WARN in the Keycloak log and no scope.

**Verification** (unchanged from §Verification, but now it is the point): after `docker compose down -v && docker compose up --build`, a token's `realm_access.roles` must be non-empty —
`… | jq -r .access_token | cut -d. -f2 | base64 -d | jq '.aud, .azp, .realm_access.roles'`. Empty roles ⇒ the import lost the scope again. Not run here (no Docker on this machine).

### 13. Bounding the MCP tool's `topK` (added after §12) — **superseded by §14**

**Issue:** `RagMcpTools` normalised only the lower bound (`topK == null || topK <= 0` ⇒ 5). Upward it passed anything through, and `QueryDocumentService` then over-fetches `max(candidatePoolSize, topK)` rows from pgvector and runs `OnnxCrossEncoderRerankerAdapter` once per candidate — sequential `session.run` calls on the request thread. `topK: 100000` therefore buys 100k inferences per call. The argument is chosen by an LLM, which makes an implausible value likelier here than on REST, and the tool is the surface newly exposed to callers outside the app.

**Fix:** `mcp.search.max-top-k` (`MCP_MAX_TOP_K`, default 20), injected into `RagMcpTools` by `@Value` — the same style as `mcp.resource`, and it keeps `mcp` from reading `rag.query.*` across the module boundary (§10). One resolution point bounds both ends:
```java
int topK = (requested == null || requested <= 0) ? DEFAULT_TOP_K : requested;
return Math.min(topK, maxTopK);
```
Clamping over rejecting: an over-large `topK` is an agent misjudging a knob, not an error worth a round trip, and it matches how the lower bound already behaves. The ceiling is stated in the `@ToolParam` description so the model can plan against it, and a clamp is logged at DEBUG. The constructor rejects `max-top-k < 1`, which would otherwise turn every search into an empty result.

Default of 20 = `rag.query.candidate-pool-size`: at that value the MCP surface never inflates the candidate pool beyond its configured size, so the reranker cost per call is what the pool was tuned for. The two settings are related but live in different modules — if one moves, move the other.

REST (`QueryRequest`) is deliberately untouched: same shape, but callers there are scripts and CI holding the confidential client's secret, not an LLM. Worth revisiting if that stops being true.

Tests in `RagMcpToolsTest`: 100k ⇒ maximum; exactly the maximum unchanged; the default is itself bounded when the maximum is configured below it; `max-top-k = 0` rejected at construction.

### 14. Input rules in value objects, enforced for both surfaces (added after §13)

**Issue:** §13 put the `topK` cap in `RagMcpTools`, which guards one of two doors — `QueryRequest` bounded `topK` only from below, so REST accepted `topK: 100000` and grew the candidate pool to match. A rule that belongs to the search was sitting in a transport, so every future inbound adapter had to remember it.

**Shape:** value objects in `pl.km.rag.application.model`, constructed **inside the port implementations** (`QueryDocumentService`, `IngestDocumentService`). Ports and `RagFacade` keep primitive parameters (`String`, `Integer`, `Double`), so adapters, the REST layer and the published `pl.km.shared.rag` contract stay free of rag internals, and no caller can reach the pipeline without passing validation. Trade accepted: a typed port signature would have made a transposed argument list a compile error; primitives do not.
- `Question`, `DocumentName`, `DocumentContent` — non-blank.
- `MinScore(double)` — `[0,1]`. Not configurable: reranker scores are sigmoid outputs, so a threshold outside that range cannot filter anything, it can only match everything or nothing. Takes a primitive because absence belongs to the *request*, not to a threshold — the service resolves the configured default first, so the type keeps one unconditional invariant and there is no null to unbox.
- `TopK` — `DEFAULT = 5`; `value >= 1` in the compact constructor, `value <= maxResults` in `TopK.boundedBy(requested, maxResults)`.

**Why the two bounds sit in different places.** `>= 1` holds for every `TopK` that could exist anywhere, so it is a constructor invariant and no instance can violate it. The ceiling is not a property of the number — it depends on how many candidates *this* server reranks — so it lives in a factory that is handed that context. Moving the floor into the factory too would leave `new TopK(-5)` constructible, degrading the type to a wrapper whose validity depends on everyone remembering a helper. The residual smell (two construction paths, one weaker) is contained by naming the factory `boundedBy` and constructing in exactly one place.

**Ceiling derived from the candidate pool, not a new constant** — a bound with a reason rather than a number someone picked. First cut tied it to a fixed `candidate-pool-size`; §15 replaces that with a derived pool, and the ceiling follows.

**The masking trap, and why the catch order is load-bearing.** §10 has `DefaultRagFacade` catching bare `RuntimeException` → "Document search failed unexpectedly. Retry in a few seconds", surfaced verbatim to the agent. A rejected argument landing there would tell a model to retry a call that can never succeed, and hide the rule it broke — strictly worse than the clamp being removed. So `InvalidInputException` is caught **first** and translated to a new contract exception, `pl.km.shared.rag.RagSearchArgumentException`, message unchanged, logged at DEBUG rather than ERROR because the caller erred, not the server. `RagMcpToolErrorReportingTest.rejectedArgumentTellsTheAgentTheRuleRatherThanToRetry` pins it.

**Reporting per adapter:** REST maps `InvalidInputException` → 400 + `ErrorResponse` in `RagExceptionHandler`; MCP surfaces the message as an `isError` tool result the agent can correct from. `RagMcpTools` loses `maxTopK`, `DEFAULT_TOP_K`, `effectiveTopK()` and the `mcp.search.max-top-k` property — it now passes arguments through untouched. Its `@ToolParam` text describes the ceiling without naming a number, since the limit is per-deployment and the error message carries it.

`DocumentController.ingestFile` keeps its own blank-filename check: it guards the multipart shape and answers before the domain is touched, while `DocumentName` holds the invariant on every path including JSON ingest. Layered, not duplicated — though they answer with different bodies (bare 400 vs `ErrorResponse`), left as is.

**Behaviour changes:** REST `topK: 0` 400 (was silently 5); REST `topK` above the pool 400 (was served with an inflated pool); blank question 400 (was plausible-looking nonsense); `score` outside [0,1] 400 (was empty or unfiltered); blank ingest name/content 400 (was 200 storing nothing — including a file that parses to blank text); MCP out-of-range 400-equivalent `isError` naming the range (was clamped to 20). An omitted `topK` still yields 5.

**Verification — not run** (no JDK, no Docker here). `./gradlew clean check` outstanding; new tests: `TopKTest`, `MinScoreTest`, `TextValueObjectsTest`, the facade translation case, the MCP masking regression, `QueryDocumentServiceTest` additions (pool no longer varies with `topK`), and `DocumentControllerValidationTest` — a `@WebMvcTest` wiring the **real** services against mocked out-ports, because mocking `QueryDocumentPort` the way the other slices do would mock away the validation under test. Against a running stack: `{"question":"hi","topK":100000}` → 400 naming the limit; `{"question":"hi"}` → 200 with at most 5 results; MCP `tools/call` with `topK: 100000` → `isError` carrying `topK must be between 1 and 20`, **not** "Retry in a few seconds".

### 15. Candidate pool derived from `topK` (added after §14)

**Trigger:** with §14's ceiling being the fixed pool, are `topK` and `candidate-pool-size` the same thing? No — the pool is the reranker's *input* (operator-chosen, per deployment), `topK` is the *output* limit after reranking and score filtering (caller-chosen, per request). But the question exposed a real flaw: what decides ranking quality is the **ratio** between them, and a fixed pool made that ratio an accident of which `topK` was asked for.
- `topK: 5`, pool 20 → 4×: the cross-encoder picks 5 winners out of 20 candidates. What the design is tuned for.
- `topK: 20`, pool 20 → 1×: nothing is selected, only reordered. The caller gets vector search's top 20 in a different order, and the second stage earns nothing.

Pre-existing, not introduced by §14 — the old `max(candidatePoolSize, topK)` hit 1× for any `topK` above the pool, unboundedly. §14 bounded it; §15 removes it.

**Change:** the pool is now computed from the request. `QueryProperties` replaces `candidatePoolSize` with three fields, each with one job:
- `over-fetch-factor: 4` — candidates per requested result; the ratio, now a guarantee.
- `min-candidates: 20` — floor, so `topK: 1` reranks a real field instead of 4 candidates. Beyond what "pool = topK × factor" strictly needs, but without it small requests would silently get a thinner candidate set than they do today.
- `max-candidates: 80` — cost ceiling, i.e. the most reranker inferences one query can trigger.

`poolSizeFor(topK) = min(maxCandidates, max(minCandidates, topK × factor))`, and `maxTopK() = maxCandidates / factor` (floor division, so the pool for the largest servable `topK` always fits under the ceiling). `TopK.boundedBy(requested, queryProperties.maxTopK())` — the VO split from §14 is untouched; only the number it is handed changes.

**Calibrated to preserve today's behaviour where it was already right:** `topK ≤ 5` still reranks 20 candidates, the largest servable `topK` is still 20, and the default path is bit-for-bit what it was. What changes is the middle and top of the range — `topK: 20` now reranks 80 candidates instead of 20, which is precisely the work that buys the ranking quality that was missing.

**Cost:** worst-case reranker work per query rises 20 → 80 sequential inferences. That is the deliberate trade — 4× the compute at the top of the range in exchange for the second stage actually selecting there. `max-candidates` is the dial if that proves too slow, and lowering it lowers the servable `topK` with it.

**Tests:** `QueryPropertiesTest` (derivation, floor/ceiling clamping, floor division with a ceiling that is not a multiple of the factor, and each configuration guard); `QueryDocumentServiceTest` gains `poolGrowsWithTopKUpToTheCostCeiling` and `poolNeverFallsBelowTheFloor` in place of the old "pool does not vary" assertion. Not compiled or run here.

### 16. Publishing the `topK` range to the agent (added after §15)

**Issue:** after §15 the ceiling is deployment-derived (`max-candidates / over-fetch-factor`), so the MCP tool could not state it. `@ToolParam(description = …)` is an annotation constant, and — checked against the 1.1.8 jars, not assumed — `ToolDefinitions.from(Method)` builds the definition through `ToolUtils.getToolDescription` + `JsonSchemaGenerator.generateForMethodInput` with no `Environment` on the path, so `${…}` in a description stays literal. `JsonSchemaGenerator` does honour Swagger `@Schema(maximum = …)`, but that is a constant too.

**Fix, in two parts.**

*Crossing the module boundary:* `RagFacade` gains `RagSearchLimits limits()` (`pl.km.shared.rag`, record of `defaultTopK` + `maxTopK`). `mcp` still imports nothing from `pl.km.rag`. The contract already publishes its failure modes; the bound a caller must respect belongs there for the same reason — it is what makes `RagSearchArgumentException` avoidable rather than discovered by trial, and it survives the facade becoming remote. Internally `QueryDocumentPort.limits()` returns `SearchLimits` (`rag.application.model`) and `DefaultRagFacade` maps it, mirroring `QueryResult` → `RagQueryResult`: retuning retrieval must not silently reshape a published type. `QueryDocumentService` derives it through `TopK.boundedBy(null, maxTopK)`, so the advertised default is by construction the applied one — a ceiling below 5 pulls both down together.

*Publishing it:* `McpServerConfig` assembles the tool definition instead of taking `MethodToolCallbackProvider`'s: `ToolDefinitions.from(method)` for name, description and generated schema, then `DefaultToolDefinition.builder()` with the schema patched and `MethodToolCallback.builder()` around the same method. The patch adds `"minimum": 1` / `"maximum": <maxTopK>` to `properties.topK` and appends the range to its description. Schema over prose because `tools/list` hands the schema to the model and a client can validate before calling; prose too because not every model reads schemas closely. Only the bounds are injected — the rest of the schema stays generated, so parameters are still described in one place, on the method.

Startup fails loudly if `properties.topK` is absent (renamed parameter, or a build without `-parameters`), rather than publishing an unbounded schema that only misbehaves once an agent calls.

**Tests:** `McpServerConfigTest` — bounds appear in the schema, follow the injected limits rather than a constant (20 vs 40), the prose carries the range, tool name still comes from the annotations, and the other parameters are left as generated. `DefaultRagFacadeTest` covers the limits mapping; `QueryDocumentServiceTest.publishesTheLimitsItEnforces` covers the derivation, including a configuration whose ceiling drags the default down. Not compiled or run here.

## Unresolved questions
1. ~~`sse-endpoint` under `/mcp/sse` (single `/mcp/**` security rule) vs Spring AI default `/sse` — OK to deviate from default?~~ Moot: SSE removed in §11; endpoint is `/mcp` (also the Spring AI default).
2. MCP server `version` hardcoded `1.0.0` vs app version `1.0-SNAPSHOT` — care?
3. Keycloak: give `rag_mcp_user` to both `Admin` and `User`, or `Admin` only?
4. If the platform grows to several APIs behind one Keycloak: move to per-API audience via client scopes and validate `aud` on the REST chain too?
5. (§11) `STATELESS` vs `STREAMABLE` — taken as stateless from the issue wording; `STREAMABLE` would keep sessions + an SSE upgrade path. Confirm nothing later needs server→client notifications.
6. (§11) RFC 9728 metadata + `resource_metadata` 401 hint kept as written for SSE — still correct for the single `/mcp` endpoint, but worth a re-read against the current MCP auth spec.
7. (§11) Boot 3.3 → 3.5 touches the whole app, not just MCP, and is in the same commit — split later if the history matters.
8. (§11) Nothing here is compiled or run; `./gradlew clean check` is outstanding.
