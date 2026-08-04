# 9_minikube — run local-rag on minikube, scaled by k8s

Branch: `feature/9-minikube-k8s-scaling`

## Context

Today the whole stack is docker-compose: one `app` container, one pgvector, one Keycloak.
Ask (`claude/plans/9_minikube.md`): add minikube so the App scales under k8s. Keep the
current setup as is — compose stays untouched and working; add only what k8s needs.

Outcome: `k8s/deploy.sh` + `minikube tunnel` gives the **same URLs as compose**
(`localhost:8080` app/MCP, `localhost:8081` Keycloak), with the app running 2+ replicas
behind a load-balanced Service and an HPA scaling 2→5 on CPU.

## Decisions (agreed)

| Topic | Choice |
|---|---|
| Manifests | plain YAML + kustomize in `k8s/` (`kubectl apply -k`) |
| Exposure | Service `type: LoadBalancer` + `minikube tunnel` → binds `127.0.0.1:8080` / `:8081` |
| Reranker model | baked in image (already is — no `.dockerignore`, `COPY . .` → jar classpath) |
| Scaling | `replicas: 2` + HPA 2→5 @70% CPU, metrics-server addon |

Because URLs are identical to compose, **no change** to `keycloak.issuer-uri`,
`mcp.resource`, realm `redirectUris`, or CORS origins.

## App changes (minimum for k8s)

Only reason to touch app code: kubelet needs unauthenticated health probes.

1. `build.gradle` — add `implementation 'org.springframework.boot:spring-boot-starter-actuator'`.
2. `src/main/resources/application.yml` — add:
   ```yaml
   management:
     endpoints.web.exposure.include: health
     endpoint.health:
       probes.enabled: true
       show-details: never
   ```
   Default health groups (`liveness`=livenessState, `readiness`=readinessState) are right:
   a DB blip must not evict every replica from the Service.
3. `src/main/java/pl/km/config/SecurityConfig.java` — in `apiFilterChain`, add
   `/actuator/health/**` to the existing `permitAll()` matcher list (next to the
   `/.well-known/...` entry, same javadoc-comment style). Probes carry no bearer token.
   MCP chain untouched (`securityMatcher("/mcp", ...)`).
4. `src/integration-test/java/pl/km/config/SecurityConfigTest.java` — add
   `healthProbesArePublic()`: `GET /actuator/health/readiness` **not** 401
   (expect 404 — `@WebMvcTest` has no actuator handler, so 404 proves the chain let it through).

Nothing else in `src/` changes.

## New: `k8s/` (kustomize)

`kustomization.yaml` — `namespace: local-rag`, resource list, `configMapGenerator`
`keycloak-realm` from `keycloak/realm-local-rag.json` (single source of truth with compose).

`namespace.yaml` — `local-rag`.

`postgres.yaml` — StatefulSet `rag-db` (`pgvector/pgvector:pg16`, `volumeClaimTemplates`
2Gi on minikube `standard` SC) + ClusterIP Service `rag-db:5432`.
Secret `rag-postgres` mounted at `/run/secrets`; `POSTGRES_USER_FILE`/`POSTGRES_PASSWORD_FILE`
point there, `POSTGRES_DB: ragdb` — same wiring as compose.

`keycloak.yaml` — Deployment (1 replica, `quay.io/keycloak/keycloak:26.0`) reusing the
**same bash entrypoint as compose** (realm-template substitution + `start-dev --import-realm`),
with compose's `$$` un-escaped to `$`. Secret `rag-keycloak` mounted at `/run/secrets` with the
**identical key names** (`keycloak_admin_user`, `keycloak_admin_password`, `rag_admin_password`,
`rag_user_password`, `rag_api_client_secret`) so the script needs no edit; realm ConfigMap at
`/opt/keycloak/data/import-template`. Env `KC_HOSTNAME: http://localhost:8081`,
`KC_HOSTNAME_STRICT: "false"`, `KC_HEALTH_ENABLED: "true"`.
Probes hit container port 9000 `/health/ready` + `/health/live` directly (no `/dev/tcp` hack).
Service `keycloak` **type LoadBalancer**, `port: 8081 → targetPort: 8080`.

`app.yaml` — Deployment `rag-app`, `replicas: 2`, image `local-rag:dev`,
`imagePullPolicy: IfNotPresent` (built into minikube's docker daemon).
Env, mirroring compose minus the reranker overrides:
```
SPRING_DATASOURCE_URL: jdbc:postgresql://rag-db:5432/ragdb
SPRING_CONFIG_IMPORT: optional:configtree:/run/secrets/
KEYCLOAK_ISSUER_URI:  http://localhost:8081/realms/local-rag        # public → matches iss
KEYCLOAK_JWK_SET_URI: http://keycloak:8081/realms/local-rag/protocol/openid-connect/certs
API_AUDIENCE: rag-platform   MCP_AUDIENCE: rag-mcp
MCP_RESOURCE: http://localhost:8080/mcp
JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=75
```
Secret `rag-datasource` (keys `spring.datasource.username` / `spring.datasource.password`)
mounted at `/run/secrets` — reuses the existing configtree import unchanged.
`resources`: requests `cpu 250m / memory 768Mi`, limits `cpu 2 / memory 1536Mi` (HPA needs
the CPU request). Probes:
`startupProbe` `/actuator/health/readiness` period 5s failureThreshold 60 (≈5 min — first
boot downloads the embedding model), then `readinessProbe` same path, `livenessProbe`
`/actuator/health/liveness`. Service `rag-app` **type LoadBalancer** `8080 → 8080`.

`hpa.yaml` — `autoscaling/v2`, target `rag-app`, min 2 max 5, CPU `averageUtilization: 70`.

`deploy.sh` — idempotent:
1. require `minikube`/`kubectl`/`docker`; start cluster if down (`--cpus=4 --memory=8g`).
2. `minikube addons enable metrics-server`.
3. preflight: all `secrets/*.txt` present **and** `src/main/resources/models/reranker/{model.onnx,tokenizer.json}` present (else the baked-in classpath model is missing → fail early, don't ship a broken image).
4. `eval $(minikube docker-env)` → `docker build -t local-rag:dev .`.
5. create/refresh the 3 Secrets from `secrets/*.txt`
   (`kubectl create secret generic … --from-file=key=path --dry-run=client -o yaml | kubectl apply -f -`) — never committed.
6. `kubectl apply -k k8s/` + `kubectl -n local-rag rollout status`.
7. print the reminder to run `minikube tunnel` in a second terminal.

`k8s/README.md` — concise: prerequisites, the two commands, URLs, scale/HPA demo, teardown
(`kubectl delete -k k8s/`), and the note that compose is unaffected.

## Verification

```bash
./gradlew check                              # unit + integration, incl. new probe test
./k8s/deploy.sh                              # terminal 1
minikube tunnel                              # terminal 2 (sudo)
kubectl -n local-rag get pods,svc,hpa
```
- probes: `curl -s localhost:8080/actuator/health/readiness` → `{"status":"UP"}`, no token.
- token: client-credentials/password grant at `localhost:8081/realms/local-rag/...token`
  with `rag-api-client` + `secrets/rag_api_client_secret.txt`, `scope=openid rag-api`.
- REST: ingest (`rag_admin`) then `POST localhost:8080/api/documents/query` (`rag_user`) → 200.
- MCP: `POST localhost:8080/mcp` `tools/list` with a `rag-mcp-client` token
  (`scope=openid rag-mcp-api`) → `search_rag_documents`; no token → 401 carrying
  `resource_metadata`.
- load balancing: `kubectl -n local-rag logs -l app=rag-app --prefix -f` while looping
  queries → both pods serve.
- scaling: `kubectl -n local-rag scale deploy/rag-app --replicas=4` → 4 Ready, traffic spreads;
  `kubectl -n local-rag get hpa -w` shows targets/replicas (HPA needs ~1 min for metrics).
- compose regression: `docker compose up` still works, unchanged.

## Follow-ups

- `claude/plans/app_description.md` — append k8s/minikube line.
- Store accepted plan to `claude/plans/claude_output/9_minikube.md`.
- `plan-feature-branch` skill at the end.

## Unresolved questions

No questions.
