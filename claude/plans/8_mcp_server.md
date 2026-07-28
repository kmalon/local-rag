As Java Developer and AI Specialist:
- add MCP server exposing RAG documents for AI Agents/LLMs purposes,
- MCP part should be wrapped into separate feature package under root package,
- also, RAG part should be extracted to the separate package,
- common parts like configs, exception handlers, general Spring beans/configs etc. relevant for both feature should be at the root package,
- RAG feature should expose data via a facade,
- the facade should use shared data located in the shared kernel package (at the root). It could be current used object, just moved to the shared package,
- MCP server have to be protected with oAuth2 and Keycloak already added, role 'rag_mcp_user',
- add the 'rag_mcp_user' role to already created in the Keycloak import file users.