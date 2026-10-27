# MovieHub

A responsive film catalog for managing movies, artwork, cast, and creators. Built for the included [technical assessment](docs/Technical-Assessment.pdf).

## Architecture

```text
Browser (Svelte 5 / TypeScript, Vite :5173)
    │ GraphQL /graphql (queries and mutations)
    │ HTTP multipart /api/movies/{id}/artwork and /api/people/{id}/avatar (binary upload with progress)
    ▼
Gateway (Spring Boot / Kotlin :8080)
    │ gRPC (including client-streamed artwork uploads)
    ▼
Catalog (Spring Boot / Kotlin, gRPC :9090)
    ├── H2 file database (Flyway migrations)
    └── Local artwork files
```

GraphQL is used for all structured frontend/backend data. Artwork uses a small HTTP endpoint because GraphQL does not natively transport binary files and `XMLHttpRequest` provides upload progress. The gateway communicates with the catalog **only by gRPC**. Images are served through the gateway at the relative URL returned by the movie query. No external API keys or services are required.

Features: paginated movie and person lists; movie/person create, update and delete; credits with actor/creator roles and filmography; searches across movies and people; genre and role filtering; artwork upload with progress and cache-busted display; avatar photos for cast and creators; a **Saved** list (bookmark movies, stored in the browser's localStorage, so it is per browser and not shared); a violet/pink theme with a custom favicon; loading, empty, success and error states.

## Prerequisites

- JDK 21 (Gradle 8.14.3 must itself run on a supported JDK; Java 26 as the default `java` is not supported). On macOS, if several JDKs are installed, run `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` in each backend terminal first.
- Node.js compatible with Vite 8 (Node.js 20.19+ or 22.12+) and npm.
- No separate database installation is needed. The Gradle wrapper is included.

## Quick start (one command)

```sh
./setup.sh
```

Checks for JDK 21, Node.js 22+ and curl, offers to install anything missing (Homebrew on macOS; apt or dnf on Linux), installs dependencies, then starts all three parts and prints <http://localhost:5173>. Press Ctrl-C to stop. Other commands: `./setup.sh check | install | start | stop | test`; add `--yes` to skip install prompts. On Windows, run it inside WSL. Logs are written to `.run/`.

## Run manually

From the repository root, use **three terminals**:

```sh
# Terminal 1: gRPC catalog (creates catalog-service/data automatically)
./gradlew :catalog-service:bootRun

# Terminal 2: GraphQL gateway and artwork HTTP API
./gradlew :gateway-service:bootRun

# Terminal 3: Svelte UI
cd frontend
npm ci
npm run dev
```

Open <http://localhost:5173>. Vite proxies `/graphql` and `/api` to the gateway on port 8080. GraphiQL is available at <http://localhost:8080/graphiql> and gateway health at <http://localhost:8080/actuator/health>. The catalog exposes gRPC on port 9090; it does not expose an HTTP health endpoint. Set `CATALOG_GRPC_ADDRESS` on the gateway if the catalog gRPC server does not run at `localhost:9090`.

If Gradle cannot download dependencies due to a `PKIX path building failed` error behind an HTTPS-inspecting company proxy, configure your JDK with the proxy's trusted CA. Do not disable TLS verification or commit private certificates. Both services use gRPC 1.68.3 via the gRPC BOM to keep the runtime compatible with the generated protobuf stubs.

To reset local data **after stopping the services**, remove the `catalog-service/data/` directory. It contains the H2 database and uploaded artwork and is excluded from Git. The app does not ship with sample data: add a person in **Cast & creators**, then add a movie and optionally assign credits or upload artwork. Image uploads accept JPEG, PNG and WebP up to 5 MB.

## Validation

```sh
./gradlew test
cd frontend
npm ci
npm run check
npm test
npm run build
```

Backend tests exercise service validation, paging/search, artwork, gRPC and gateway GraphQL/HTTP behavior. Frontend tests exercise GraphQL requests and failures, upload progress, empty states and creation/search flows. No running services are required to run tests.

## AI tool use

GitHub Copilot was used as a productivity aid to draft implementation code, tests, and this README, and to run checks during development. The decisions to keep structured data in GraphQL, use multipart HTTP for binary upload progress, proxy gateway calls over gRPC, and validate inputs in the catalog service reflect the assessment's requirements. Review the code and tests before submission; the submitter is responsible for explaining and maintaining the result.

## Limitations

This is a local assessment application: there is no authentication or production deployment configuration. Artwork is stored on the local filesystem. For production use, add authentication/authorization, durable object storage, observability and deployment configuration.





