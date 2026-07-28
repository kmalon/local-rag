Spring Boot 3 app (Ports & Adapters, feature-packaged). Ingests local files/docs via REST API, creates embeddings (Spring AI + OpenAI), stores to PostgreSQL pgvector. Package: pl.km. Build: Gradle.

Packages: `rag` (RAG feature: adapter/application/config, public `RagFacade`), `mcp` (MCP server feature: `RagMcpTools` @Tool + `McpServerConfig`), `shared` (shared kernel: `QueryResult`, exceptions), `config` (SecurityConfig) + `web` (GlobalExceptionHandler) = common at root. Cross-feature access only via `RagFacade` returning shared-kernel types.

Auth: OAuth2 Resource Server (JWT) via Keycloak realm `local-rag`, roles `rag_admin` (ingest) / `rag_user` (query) / `rag_mcp_user` (MCP). Two filter chains: `/mcp/**` also validates `aud` == `mcp.resource` (`http://localhost:8080/mcp`) and 401s with RFC 9728 `resource_metadata` hint; REST chain validates `iss` only, so foreign-client tokens still work. Audience comes from an mapper in the **optional** client scope `mcp-api` — MCP callers must request `scope=openid mcp-api`, REST-only tokens can't open MCP. Metadata at `/.well-known/oauth-protected-resource[/mcp]` (public).

Query: pgvector over-fetches candidate pool, local cross-encoder reranker (ms-marco-MiniLM ONNX via onnxruntime) re-scores; score threshold applied to reranker output.

MCP: Spring AI MCP server (SSE over WebMVC, `spring-ai-starter-mcp-server-webmvc`) at `/mcp/sse` + `/mcp/message`; read-only tool `search_rag_documents(question, topK, minScore)` delegating to `RagFacade`.

Infra: docker-compose (pgvector/pgvector:pg16 + Keycloak + app image). Tests split: unit in `src/test` (`test` task); integration (`@WebMvcTest`+) in `src/integration-test` (`integrationTest` task, wired into `check`).
