#!/usr/bin/env bash
#
# Build the app image into minikube's docker daemon and (re)deploy the whole stack.
# Idempotent: safe to re-run after any code, manifest, realm or secret change.
#
# Afterwards run `minikube tunnel` in a second terminal to publish the LoadBalancer
# Services on 127.0.0.1:8080 (app/MCP) and 127.0.0.1:8081 (Keycloak).

set -euo pipefail

cd "$(dirname "$0")/.."

NAMESPACE="local-rag"
IMAGE="local-rag:dev"

log() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die() { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# --- 1. tooling ---------------------------------------------------------------
for cmd in minikube kubectl docker; do
  command -v "$cmd" >/dev/null || die "'$cmd' not found on PATH."
done

# --- 2. cluster identity ------------------------------------------------------
# Which minikube cluster this run targets, following the same precedence minikube
# itself uses: $MINIKUBE_PROFILE, then the persisted default (`minikube profile
# <name>`), then the built-in `minikube`. Reading the persisted value is what stops
# the script from silently overriding a profile the user deliberately selected.
resolve_profile() {
  local p="${MINIKUBE_PROFILE:-}"
  [[ -n "$p" ]] || p="$(minikube config get profile 2>/dev/null || true)"
  [[ -n "$p" ]] || p="minikube"
  printf '%s' "$p"
}
PROFILE="$(resolve_profile)"

# Pin kubectl to that same cluster. `minikube -p` and kubectl's current-context are
# two independent pointers; left unpinned, kubectl follows whatever context happens
# to be selected — so the image lands in one cluster while the manifests, Secrets
# included, are applied to another (a remote work cluster, say). minikube names the
# context after the profile, so this also fails loudly on a wrong name instead of
# deploying somewhere unintended.
KUBECTL=(kubectl --context "$PROFILE")

# --- 3. preflight -------------------------------------------------------------
# Fail before the (slow) image build rather than on a CrashLoopBackOff.
SECRET_FILES=(
  secrets/postgres_user.txt
  secrets/postgres_password.txt
  secrets/keycloak_admin_user.txt
  secrets/keycloak_admin_password.txt
  secrets/rag_admin_password.txt
  secrets/rag_user_password.txt
  secrets/rag_api_client_secret.txt
)
for f in "${SECRET_FILES[@]}"; do
  [[ -s "$f" ]] || die "missing or empty $f — see secrets/README.md."
done

# The reranker model is packaged into the jar (classpath: default in application.yml);
# unlike compose there is no bind mount to fall back on, so it must exist at build time.
for f in src/main/resources/models/reranker/model.onnx \
         src/main/resources/models/reranker/tokenizer.json; do
  [[ -s "$f" ]] || die "missing $f — see src/main/resources/models/reranker/README.md."
done

# --- 4. cluster ---------------------------------------------------------------
if ! minikube -p "$PROFILE" status >/dev/null 2>&1; then
  log "Starting minikube profile '$PROFILE'"
  # Two Java stacks plus Postgres; the 2 CPU / 2 GB default cannot hold them.
  minikube -p "$PROFILE" start --cpus=4 --memory=8g
fi

# minikube writes this context on start, so by now it must exist. If it does not, the
# profile and the kubeconfig disagree and every kubectl below would target the wrong
# cluster — stop rather than guess.
kubectl config get-contexts "$PROFILE" >/dev/null 2>&1 \
  || die "no kubeconfig context named '$PROFILE'; run: minikube -p $PROFILE update-context"

log "Enabling metrics-server (required by the HorizontalPodAutoscaler)"
minikube -p "$PROFILE" addons enable metrics-server

# --- 5. image -----------------------------------------------------------------
# Build straight into the cluster's daemon: no registry, no `minikube image load` copy.
log "Building $IMAGE inside minikube's docker daemon"
eval "$(minikube -p "$PROFILE" docker-env)"
docker build -t "$IMAGE" .

# --- 6. cluster-side inputs ---------------------------------------------------
# Secrets and the realm ConfigMap are created here, not by kustomize: their sources live
# outside k8s/ (which kustomize will not read) and the secret values must stay untracked.
log "Applying namespace, secrets and realm ConfigMap"
"${KUBECTL[@]}" apply -f k8s/namespace.yaml

apply_secret() { # apply_secret <name> <--from-file args...>
  local name="$1"; shift
  # `create --dry-run=client` only renders the manifest; the piped apply is the write,
  # so both halves must be pinned to the same context.
  "${KUBECTL[@]}" -n "$NAMESPACE" create secret generic "$name" "$@" \
    --dry-run=client -o yaml | "${KUBECTL[@]}" apply -f -
}

apply_secret rag-postgres \
  --from-file=username=secrets/postgres_user.txt \
  --from-file=password=secrets/postgres_password.txt

# Keys are Spring property names on purpose: mounted at /run/secrets they are picked up
# by the existing `optional:configtree:/run/secrets/` import, exactly as under compose.
apply_secret rag-datasource \
  --from-file=spring.datasource.username=secrets/postgres_user.txt \
  --from-file=spring.datasource.password=secrets/postgres_password.txt

# Key names match the compose secret names so the realm-import script is unchanged.
apply_secret rag-keycloak \
  --from-file=keycloak_admin_user=secrets/keycloak_admin_user.txt \
  --from-file=keycloak_admin_password=secrets/keycloak_admin_password.txt \
  --from-file=rag_admin_password=secrets/rag_admin_password.txt \
  --from-file=rag_user_password=secrets/rag_user_password.txt \
  --from-file=rag_api_client_secret=secrets/rag_api_client_secret.txt

# Keycloak only reads the realm at startup, so a changed template needs a restart. Detect
# that before overwriting the ConfigMap; an unchanged realm must not disturb a running pod.
realm_changed=0
if ! "${KUBECTL[@]}" -n "$NAMESPACE" get configmap keycloak-realm \
      -o "jsonpath={.data['realm-local-rag\.json']}" 2>/dev/null \
      | diff -q - keycloak/realm-local-rag.json >/dev/null 2>&1; then
  realm_changed=1
fi
"${KUBECTL[@]}" -n "$NAMESPACE" create configmap keycloak-realm \
  --from-file=keycloak/realm-local-rag.json \
  --dry-run=client -o yaml | "${KUBECTL[@]}" apply -f -

# Sampled before the apply below creates it: a Keycloak that does not exist yet is about
# to start with the current realm anyway, so a first deploy must not count as "needs a
# restart" — that would tear down a pod seconds after creating it.
keycloak_existed=0
if "${KUBECTL[@]}" -n "$NAMESPACE" get deploy keycloak >/dev/null 2>&1; then
  keycloak_existed=1
fi

# --- 7. workloads -------------------------------------------------------------
log "Applying manifests"
"${KUBECTL[@]}" apply -k k8s/

# Restarting is a hard reset here: start-dev keeps the realm in memory, so this also drops
# sessions and regenerates the signing keys. Only worth it when the realm actually changed.
if [[ "$realm_changed" == 1 && "$keycloak_existed" == 1 ]]; then
  log "Realm changed — restarting Keycloak to re-import it"
  "${KUBECTL[@]}" -n "$NAMESPACE" rollout restart deployment/keycloak
fi

# The app image tag never changes, so an unchanged pod spec would leave the old pods
# running with the newly built image ignored. Force them to pick it up.
log "Rolling the app onto the freshly built image"
"${KUBECTL[@]}" -n "$NAMESPACE" rollout restart deployment/rag-app

log "Waiting for rollouts (first start downloads the embedding model — be patient)"
"${KUBECTL[@]}" -n "$NAMESPACE" rollout status statefulset/rag-db --timeout=300s
"${KUBECTL[@]}" -n "$NAMESPACE" rollout status deployment/keycloak --timeout=600s
"${KUBECTL[@]}" -n "$NAMESPACE" rollout status deployment/rag-app --timeout=600s

"${KUBECTL[@]}" -n "$NAMESPACE" get pods,svc,hpa

cat <<EOF

$(printf '\033[1m')Deployed to profile '$PROFILE'.$(printf '\033[0m') Publish the Services from a second terminal:

    minikube -p $PROFILE tunnel

Then, with the same URLs docker-compose uses:

    app / MCP   http://localhost:8080
    Keycloak    http://localhost:8081

    curl -s http://localhost:8080/actuator/health/readiness

EOF
