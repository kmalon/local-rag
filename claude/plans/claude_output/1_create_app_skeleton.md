# Plan: 1_create_app_skeleton

## Context
Bootstrap local-rag as a Spring Boot app (Ports & Adapters) that ingests local files via REST, generates embeddings, and stores them to PostgreSQL with pgvector.

Current state: bare Gradle Java project, package `pl.km`, no Spring deps, no Docker.

---

## 1. build.gradle — replace with Spring Boot 3.x + Spring AI

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.0'
    id 'io.spring.dependency-management' version '1.1.5'
}
group = 'pl.km'
version = '1.0-SNAPSHOT'
java { sourceCompatibility = JavaVersion.VERSION_21 }
repositories { mavenCentral() }
ext { set('springAiVersion', '1.0.0') }
dependencyManagement {
    imports { mavenBom "org.springframework.ai:spring-ai-bom:${springAiVersion}" }
}
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.ai:spring-ai-pgvector-store-spring-boot-autoconfigure'
    implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter'
    runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
test { useJUnitPlatform() }
```

---

## 2. Package structure (src/main/java/pl/km/)

```
pl/km/
├── LocalRagApplication.java              ← replace Main.java
├── domain/model/
│   └── Document.java                     ← id, name, content, metadata
├── application/port/in/
│   └── IngestDocumentUseCase.java        ← interface: ingest(name, content)
├── application/port/out/
│   ├── EmbeddingPort.java               ← interface: embed(text) → float[]
│   └── DocumentVectorRepository.java     ← interface: save(Document, float[])
├── application/service/
│   └── IngestDocumentService.java        ← implements IngestDocumentUseCase
├── adapter/in/rest/
│   ├── DocumentController.java           ← POST /api/documents/ingest
│   └── IngestRequest.java               ← record: name, content
└── adapter/out/persistence/
    ├── PgVectorDocumentRepository.java   ← implements DocumentVectorRepository (Spring AI VectorStore)
    └── SpringAiEmbeddingAdapter.java     ← implements EmbeddingPort (Spring AI EmbeddingModel)
```

---

## 3. application.yml (src/main/resources/)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ragdb
    username: raguser
    password: ragpass
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:changeme}
    vectorstore:
      pgvector:
        initialize-schema: true
        dimensions: 1536
```

---

## 4. docker-compose.yml (project root)

```yaml
services:
  db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: ragdb
      POSTGRES_USER: raguser
      POSTGRES_PASSWORD: ragpass
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]

  app:
    build: .
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/ragdb
      SPRING_DATASOURCE_USERNAME: raguser
      SPRING_DATASOURCE_PASSWORD: ragpass
      OPENAI_API_KEY: ${OPENAI_API_KEY}
    depends_on: [db]

volumes:
  pgdata:
```

---

## 5. Dockerfile (project root)

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
```

---

## Files created/modified
- `build.gradle` — replaced with Spring Boot 3.3.0 + Spring AI 1.0.0
- `src/main/java/pl/km/Main.java` — deleted
- `src/main/java/pl/km/LocalRagApplication.java` — new
- `src/main/java/pl/km/domain/model/Document.java` — new
- `src/main/java/pl/km/application/port/in/IngestDocumentUseCase.java` — new
- `src/main/java/pl/km/application/port/out/EmbeddingPort.java` — new
- `src/main/java/pl/km/application/port/out/DocumentVectorRepository.java` — new
- `src/main/java/pl/km/application/service/IngestDocumentService.java` — new
- `src/main/java/pl/km/adapter/in/rest/IngestRequest.java` — new
- `src/main/java/pl/km/adapter/in/rest/DocumentController.java` — new
- `src/main/java/pl/km/adapter/out/persistence/SpringAiEmbeddingAdapter.java` — new
- `src/main/java/pl/km/adapter/out/persistence/PgVectorDocumentRepository.java` — new
- `src/main/resources/application.yml` — new
- `docker-compose.yml` — new
- `Dockerfile` — new

---

## Verification
1. `./gradlew build` — compiles cleanly
2. `docker compose up --build` — starts db + app
3. `curl -X POST http://localhost:8080/api/documents/ingest -H 'Content-Type: application/json' -d '{"name":"test.txt","content":"hello world"}'` — returns 200

---

## No questions

---

## Branch
`feature/1-app-skeleton`
