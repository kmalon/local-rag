As Java Developer and Security Engineer:
- add oAuth2 to the App,
- the Spring Boot app should use Keycloak, add it also to docker-compose.yml file.
- Keycloak during creating container should import realm file with the 'local-rag' name.
- to Keycloak add roles 'rag_admin' and 'rag_user'. Add users: Admin - with roles rag_admin, rag_user and User - with role rag_user. Passwords for both users should be defined in the docker-compose.yml file as env variable.
- secure endpoints with ingestion by role 'rag_admin' and GET endpoints with 'rag_user' role.
- master branch is your exit point for the task.