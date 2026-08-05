# Postman

`local-rag.postman_collection.json` — every HTTP endpoint the app exposes: the REST
document API, the MCP server, the public discovery/health endpoints, plus the Keycloak
token requests that make them callable.

Both files here are credential-free and safe to commit: the environment declares the
secret variables but leaves them empty, and you supply the values in Postman.

## Setup

Import `local-rag.postman_collection.json` and `local-rag.postman_environment.json`, then
select the environment (top right).

Open the environment editor and fill the **Current value** column for the three secret
variables — current values live only in your Postman installation and are stripped from
any export or share, so the credentials never reach a file:

| Variable | Value from |
| --- | --- |
| `apiClientSecret` | `secrets/rag_api_client_secret.txt` |
| `adminPassword` | `secrets/rag_admin_password.txt` |
| `userPassword` | `secrets/rag_user_password.txt` |

## Running

Start the stack first (`docker compose up`, or `k8s/deploy.sh` + `minikube tunnel` — both
publish app `:8080` and Keycloak `:8081`).

Run the three requests in **0. Auth**; their test scripts store `adminToken`, `userToken`
and `mcpToken` as collection variables, and every other request already points at the
right one. Re-run a token request when a call starts returning 401 — access tokens are
short-lived.

`1. Documents → Ingest file` needs a file picked by hand in the form-data `file` row;
Postman cannot carry a file path inside a collection.

The whole collection can be run with the Collection Runner: **0. Auth** and
**4. Negative checks** assert their outcomes, so a green run confirms the role/audience
split is intact.
