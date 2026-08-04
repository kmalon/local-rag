# Running local-rag on minikube

Same stack as `docker-compose.yml` (pgvector + Keycloak + app), but the app runs as a
scaled Deployment behind a load-balanced Service, with an HPA on top. Compose is
untouched and still works — this is an addition, not a replacement.

The published URLs are **identical to compose**, so nothing in `application.yml`, the
realm's `redirectUris`, or the CORS origins had to change:

| | URL |
| --- | --- |
| REST API | `http://localhost:8080/api/documents/...` |
| MCP | `http://localhost:8080/mcp` |
| Keycloak | `http://localhost:8081/realms/local-rag` |

## Prerequisites

- `minikube`, `kubectl`, `docker`
- `secrets/*.txt` filled in (see `secrets/README.md`)
- `src/main/resources/models/reranker/{model.onnx,tokenizer.json}` present — they are
  baked into the image (`classpath:` default), so a replica needs nothing from the host

`deploy.sh` checks all three and fails before building if something is missing.

## Deploy

```bash
./k8s/deploy.sh          # terminal 1: build image + apply manifests
minikube tunnel          # terminal 2: publish the Services on 127.0.0.1 (asks for sudo)
```

`minikube tunnel` must keep running, much like `docker compose up`. It binds each
`type: LoadBalancer` Service on its own port, and traffic goes through kube-proxy —
so requests are spread over every ready replica (`kubectl port-forward` would pin
them all to one pod).

Re-run `./k8s/deploy.sh` after any change; it rebuilds the image, refreshes the
Secrets/ConfigMap and rolls the app. Keycloak is only restarted when the realm file
actually changed.

### Choosing a cluster

`deploy.sh` targets one minikube profile — a profile being a wholly separate cluster
with its own node, IP and **image store**. It is resolved like minikube resolves it
itself: `$MINIKUBE_PROFILE`, else the persisted default from `minikube profile <name>`,
else `minikube`.

```bash
MINIKUBE_PROFILE=rag ./k8s/deploy.sh      # deploy into an isolated cluster
MINIKUBE_PROFILE=rag minikube tunnel      # the tunnel is per-profile too
kubectl --context rag -n local-rag get pods
```

Every `kubectl` call in the script is pinned with `--context "$PROFILE"`, because
kubectl's current-context is a *separate* pointer from `minikube -p`. Unpinned, the
image would be built into one cluster while the manifests — Secrets included — were
applied to whatever context happened to be selected, possibly a remote one.

Images live inside the profile's own daemon, so switching profiles (or running
`minikube delete`) means the new cluster has no `local-rag:dev`. Just re-run
`./k8s/deploy.sh`; the first build in a fresh profile is a cold one, so expect minutes.

## What runs

| Resource | Notes |
| --- | --- |
| `statefulset/rag-db` | pgvector, 2Gi PVC, ClusterIP only |
| `deployment/keycloak` | dev mode, realm re-imported from the ConfigMap on each start |
| `deployment/rag-app` | **2 replicas**, actuator probes, ONNX models in the image |
| `hpa/rag-app` | 2 → 5 replicas at 70% of the 250m CPU request |

Credentials come from the same `secrets/*.txt` files compose uses, turned into three
Secrets by `deploy.sh` (never committed). The app still reads its datasource credentials
through `optional:configtree:/run/secrets/`, unchanged.

## Scaling

```bash
kubectl -n local-rag scale deploy/rag-app --replicas=4
kubectl -n local-rag get hpa -w                      # needs ~1 min for metrics to appear
kubectl -n local-rag logs -l app=rag-app --prefix -f # watch requests hit several pods
```

## Troubleshooting

```bash
kubectl -n local-rag get pods
kubectl -n local-rag describe pod -l app=rag-app
kubectl -n local-rag logs -l app=rag-app --tail=100
```

- **Pods `Pending`** — not enough room; recreate the cluster with more headroom
  (`minikube delete && minikube start --cpus=4 --memory=8g`).
- **App stuck `0/1 Running` for minutes** — expected on a cold start: the embedding model
  is downloaded on first boot. The startup probe allows ~5 minutes.
- **401 with a fresh token** — the token's `iss` must be `http://localhost:8081/...`;
  mint it through the tunnel, not through a `port-forward` on a different port.
- **`localhost:8080` refused** — `minikube tunnel` is not running.
- **`ErrImagePull` on `local-rag:dev`** — the image lives in a different profile's image
  store (or the cluster was deleted and recreated). Re-run `./k8s/deploy.sh`.

## Teardown

```bash
kubectl delete -k k8s/     # keeps the cluster; the PVC goes with the namespace
minikube delete            # or drop the whole cluster
```
