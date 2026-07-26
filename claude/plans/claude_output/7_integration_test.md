# Plan: 7_integration_test

## Context
All tests live under `src/test/java`, mixing fast unit tests w/ context-booting integration tests run by the single `test` task. Goal: isolate integration tests into a dedicated `integration-test` source set so unit tests stay fast and integration tests run as a separate Gradle task.

## Classification (current 2 test files)
- `pl.km.application.service.QueryDocumentServiceTest` — pure Mockito unit test, no Spring context → **stays** in `src/test/java`.
- `pl.km.config.SecurityConfigTest` — `@WebMvcTest` boots Spring MVC+Security slice context → **integration test → move**.

## Changes

### 1. New source set dir
- Create `src/integration-test/java/pl/km/config/SecurityConfigTest.java` (git-move file, package unchanged `pl.km.config`).
- Delete `src/test/java/pl/km/config/SecurityConfigTest.java`.

### 2. `build.gradle` — add integrationTest source set + task
```groovy
sourceSets {
    integrationTest {
        java.srcDir 'src/integration-test/java'
        resources.srcDir 'src/integration-test/resources'
        compileClasspath += sourceSets.main.output + sourceSets.test.output
        runtimeClasspath += sourceSets.main.output + sourceSets.test.output
    }
}
configurations {
    integrationTestImplementation.extendsFrom testImplementation
    integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
}
tasks.register('integrationTest', Test) {
    description = 'Runs integration tests.'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter test
}
tasks.named('check') { dependsOn 'integrationTest' }
```
(keeps existing `test { useJUnitPlatform() }`; integration tests still part of `build`/`check` lifecycle but as a distinct task.)

### 3. `app_description.md`
Append terse note: tests split — unit in `src/test`, integration (`@WebMvcTest`+) in `src/integration-test`, own `integrationTest` Gradle task.

## Verification
1. `./gradlew test` → runs only `QueryDocumentServiceTest` (5 tests), green; does NOT run SecurityConfigTest.
2. `./gradlew integrationTest` → runs `SecurityConfigTest` (5 tests), green.
3. `./gradlew build` (or `check`) → runs both tasks, all green.

## No questions

---

## Implementation status
All changes applied as planned:
- Moved `SecurityConfigTest.java` (git mv) `src/test/java/pl/km/config/` → `src/integration-test/java/pl/km/config/` (package unchanged).
- `build.gradle` — added `integrationTest` source set, `integrationTest{Implementation,RuntimeOnly}` configs extending test configs, `integrationTest` Test task (`shouldRunAfter test`), `check` depends on it.
- `app_description.md` — added test-split note.

Build not verified locally — no JDK 21 / Gradle toolchain in sandbox. Run `./gradlew test`, `./gradlew integrationTest`, `./gradlew build` in a JDK 21 env.

---

## Branch
`feature/7-integration-test-sourceset`
