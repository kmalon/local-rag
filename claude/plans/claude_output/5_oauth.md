# Plan: 5_oauth

## Context
App = stateless REST API (ingest + query), no auth. Add OAuth2 so ingestion is admin-only, reads user-level. Keycloak as IdP (realm `local-rag`, roles `rag_admin`/`rag_user`, users Admin/User). Spring Boot app = **OAuth2 Resource Server** validating Keycloak JWT bearer tokens (correct model for token-based REST API; no login/session). Keycloak added to docker-compose, auto-imports realm on container start; user passwords injected via docker-compose env vars → realm JSON placeholders (Keycloak `${ENV}` substitution in realm import).

## Endpoint security (DocumentController)
- `POST /api/documents/ingest`, `POST /api/documents/ingest/file` → `hasRole('rag_admin')`
- `POST /api/documents/query` (the read endpoint) → `hasRole('rag_user')`
- Admin user (both roles) can do everything.

## build.gradle
Add:
- `implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'`
- `implementation 'org.springframework.boot:spring-boot-starter-security'`

## New files
- `config/SecurityConfig.java` — `@Configuration @EnableWebSecurity @EnableMethodSecurity`. `SecurityFilterChain` bean: `authorizeHttpRequests` mapping paths→roles above (ingest→rag_admin, query→rag_user), `.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(conv)))`, `csrf.disable()`, `sessionCreationPolicy(STATELESS)`. Custom `JwtAuthenticationConverter` extracting Keycloak realm roles from `realm_access.roles` claim, prefixing `ROLE_`.
- `keycloak/realm-local-rag.json` (resources or repo root `keycloak/`) — realm `local-rag`; realm roles `rag_admin`,`rag_user`; public client `rag-client` (directAccessGrantsEnabled=true, for token retrieval/testing); users:
  - `Admin` → roles [rag_admin, rag_user], password `${RAG_ADMIN_PASSWORD}`
  - `User` → role [rag_user], password `${RAG_USER_PASSWORD}`
  - passwords as non-temporary credentials referencing env placeholders.

## Modified files
- `application.yml` — add `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8080/realms/local-rag}`.
- `docker-compose.yml`:
  - new `keycloak` service: `quay.io/keycloak/keycloak:26.x`, cmd `start-dev --import-realm`, mount realm JSON → `/opt/keycloak/data/import`, env: `KC_BOOTSTRAP_ADMIN_USERNAME`/`KC_BOOTSTRAP_ADMIN_PASSWORD` (admin console), `RAG_ADMIN_PASSWORD`, `RAG_USER_PASSWORD` (realm import substitution), port `8081:8080` (avoid clash with app 8080), healthcheck.
  - `app` service: add `KEYCLOAK_ISSUER_URI: http://keycloak:8080/realms/local-rag`, `depends_on` keycloak (condition service_healthy). Note issuer host: app validates via in-network `keycloak:8080`; token `iss` must match → app uses same issuer host tokens are minted from (keep in-network host consistent).
  - passwords sourced from `.env`/shell (`${RAG_ADMIN_PASSWORD}` etc.), not hardcoded.

## Verification
1. `./gradlew build` compiles (JDK 21).
2. `docker compose up` → Keycloak imports realm, both users created, app starts as resource server.
3. Get token (password grant, client `rag-client`) for User → `POST /query` 200; `POST /ingest` 403.
4. Token for Admin → `/ingest` + `/ingest/file` 200; `/query` 200.
5. No token → 401 on all.

## Questions
No questions.

---

## Implementation status
All changes applied as planned:
- `build.gradle` — added `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`.
- `config/SecurityConfig.java` — new resource-server config; stateless, csrf off; ingest→`rag_admin`, query→`rag_user`; custom converter maps Keycloak `realm_access.roles`→`ROLE_*`.
- `application.yml` — `spring.security.oauth2.resourceserver.jwt.issuer-uri` (`KEYCLOAK_ISSUER_URI`, default localhost:8080).
- `keycloak/realm-local-rag.json` — realm `local-rag`; roles `rag_admin`/`rag_user`; public client `rag-client` (direct access grants); users Admin (both roles) / User (rag_user); passwords via `${RAG_ADMIN_PASSWORD}`/`${RAG_USER_PASSWORD}` placeholders.
- `docker-compose.yml` — Keycloak 26 service (`start-dev --import-realm`, health-gated). App gets `KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/local-rag` + `depends_on: keycloak (service_healthy)`.

### Credentials via Docker secrets
All usernames/passwords moved from env vars to Docker secrets (`secrets/*.txt`, file-based):
- **Postgres** — native `_FILE` convention (`POSTGRES_USER_FILE`/`POSTGRES_PASSWORD_FILE`).
- **App (Spring Boot)** — `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`; the `postgres_user`/`postgres_password` secrets are mounted at targets `spring.datasource.username`/`spring.datasource.password` so configtree binds them.
- **Keycloak realm passwords** — realm JSON holds `__RAG_ADMIN_PASSWORD__`/`__RAG_USER_PASSWORD__` tokens; the `entrypoint` renders the read-only template into the writable import dir with `sed`, substituting straight from the secret files (no env-var export, no KC placeholder feature/flag needed).
- **Keycloak bootstrap admin** — no realm-file equivalent, so `KC_BOOTSTRAP_ADMIN_USERNAME/PASSWORD` are still `export`ed from their secret files in the entrypoint. (`$$` escapes `$` from compose interpolation.)
- Real `secrets/*.txt` are **gitignored**; only `secrets/*.txt.example` templates + `secrets/README.md` (copy instructions) are tracked. Files carry dev defaults with **no trailing newline** (avoids leaking `\n` into passwords). Note: `sed` substitution assumes passwords contain no `sed`-special chars (`|`, `&`, `\`); escape or switch method for arbitrary secrets. Harden for prod: external secret store (Swarm/K8s secrets, Vault, SOPS).

Build not verified locally — no JDK 21 / Docker in this sandbox; run `./gradlew build` + `docker compose up` in an equipped environment. For token-based testing, mint tokens via in-network host `http://keycloak:8080` (client `rag-client`, password grant) so the JWT `iss` matches the app's issuer-uri.

---

## Branch
`feature/5-oauth-keycloak`
