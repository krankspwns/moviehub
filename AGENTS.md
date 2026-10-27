# AGENTS.md

MovieHub: movie catalog. Svelte UI → GraphQL gateway → gRPC catalog service. Read this file first; then read ONLY the nested `AGENTS.md` of the module you are touching.

## Routing (where work goes)

| Task | Module | Guide |
|---|---|---|
| gRPC contract (messages, RPCs) | `proto/` | `proto/AGENTS.md` |
| Business rules, validation, DB, artwork storage, gRPC server | `catalog-service/` | `catalog-service/AGENTS.md` |
| GraphQL schema/resolvers, artwork HTTP endpoint, gRPC client, CORS | `gateway-service/` | `gateway-service/AGENTS.md` |
| UI, GraphQL client, styling, frontend tests | `frontend/` | `frontend/AGENTS.md` |
| Assessment requirements | `docs/Technical-Assessment.pdf` | (binary; requirements are summarised below) |

Cross-module feature order: `proto` → `catalog-service` → `gateway-service` (schema + client) → `frontend`. Change one layer per commit/sub-task; do not edit modules a task does not need.

## Architecture (do not break)

- Browser → gateway: **GraphQL** `/graphql`; artwork binary via **REST** `/api/movies/{id}/artwork` (multipart, gives upload progress).
- Gateway → catalog: **gRPC only** (port 9090; client-streaming for uploads). The gateway holds no business logic.
- Validation and business rules live in `catalog-service` only. Gateway/UI may add UX checks but never replace them.
- Catalog storage: H2 file DB (Flyway migrations) + artwork files under `./data` (git-ignored).

## Assessment requirements (must remain true)

Svelte+TypeScript frontend; ≥2 Kotlin Spring Boot services; service↔service gRPC; frontend↔backend GraphQL; unit tests incl. edge cases; responsive UI with feedback for loading, upload, update and search; README with setup/run and AI usage. Features: movies with artwork, CRUD for movies and cast/creators, upload artwork, search.

## Commands

```sh
./setup.sh [check|install|start|stop|test] [--yes]   # bootstrap + run everything; logs in .run/
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # Gradle 8.14 does not run on JDK 26
./gradlew test                                      # all backend tests
./gradlew :catalog-service:test                     # one module (preferred; cheaper)
./gradlew :catalog-service:bootRun                  # :9090 gRPC
./gradlew :gateway-service:bootRun                  # :8080 HTTP
cd frontend && npm ci && npm run check && npm test && npm run build && npm run dev   # :5173
```

Run only the checks for the module you changed. Run everything before the final commit of a feature.

## Conventions

- **Commits**: Conventional Commits, one logical change each: `type(scope): imperative summary`. Types `feat fix test docs refactor chore`; scopes `proto catalog gateway frontend`.
- Tests accompany behaviour changes, including edge cases (blank/oversized input, missing IDs, empty results, failures).
- Kotlin: official style, no wildcard imports, constructor injection, small files per concern.
- Never commit `data/`, `build/`, `node_modules/`, `dist/`, `.idea/`, secrets or certificates.
- Keep docs short; update the README only when setup/run steps change. Prefer pointing to code over duplicating it.

## Known gotchas

- gRPC versions must stay aligned: both services import `io.grpc:grpc-bom:1.68.3`. Removing it causes `NoClassDefFoundError: io/grpc/InternalGlobalInterceptors` at startup (unit tests do not catch this).
- `typescript` is pinned to `~5.9` because `svelte-check` does not support TS 7 without extra setup.
- Behind an HTTPS-inspecting proxy, Gradle may fail with PKIX errors; trust the proxy CA in the JDK. Never disable TLS verification.

## Token-efficiency rules for agents

1. Start from the routing table; do not scan the whole repo. Use search for symbols instead of reading large files.
2. Never read generated/ignored folders (`build/`, `node_modules/`, `dist/`, `data/`) or the PDF.
3. Read the nested `AGENTS.md` of one module, then only the files named there.
4. Delegate independent module work in parallel; give each sub-task its module, the contract it relies on, and the test command.
5. Report results briefly: files changed, commands run, pass/fail.

Human-oriented walkthrough: `docs/PROJECT-TOUR.md` (keep it in sync when files move).
