# Secrets

Docker Compose reads each service credential from a file-based secret in this
directory. The real `*.txt` files are gitignored; only the `*.txt.example`
templates are tracked.

## First-time setup

Copy each template to its real filename and edit the value:

```bash
for f in secrets/*.txt.example; do cp "$f" "${f%.example}"; done
```

Then adjust the values as needed and run `docker compose up`.

## Files

| Secret file                     | Used by  | Purpose                                  |
| ------------------------------- | -------- | ---------------------------------------- |
| `postgres_user.txt`             | db, app  | PostgreSQL user (app datasource username)|
| `postgres_password.txt`         | db, app  | PostgreSQL password (datasource password)|
| `keycloak_admin_user.txt`       | keycloak | Keycloak bootstrap admin username        |
| `keycloak_admin_password.txt`   | keycloak | Keycloak bootstrap admin password        |
| `rag_admin_password.txt`        | keycloak | Password for realm user `Admin`          |
| `rag_user_password.txt`         | keycloak | Password for realm user `User`           |
| `rag_api_client_secret.txt`     | keycloak | Client secret for `rag-api-client`       |

## How the mapping works

- **db**: reads `POSTGRES_USER_FILE` / `POSTGRES_PASSWORD_FILE`.
- **app**: the `postgres_user` / `postgres_password` secrets are mounted at
  `/run/secrets/spring.datasource.username` and `.../spring.datasource.password`
  and imported via Spring's `configtree:` config import.
- **keycloak**: admin bootstrap secrets are exported as env vars; the realm
  user passwords and the `rag-api-client` secret are substituted into the realm
  import at container start.

## `rag_api_client_secret.txt`

`rag-api-client` is a confidential client: REST callers must present this secret
at the token endpoint, which is what stops a process that merely knows the
`client_id` from minting a `rag-platform` token. Give it to REST callers
(scripts, CI) only — never to an AI agent or anything driving the MCP server,
since handing it over collapses the whole client split. `rag-mcp-client` is
public by necessity and has no secret; its isolation comes from the scope split
alone.
