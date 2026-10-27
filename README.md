, stored in the browser's localStorage, so it is per browser and not shared); a violet/pink theme with a light/dark toggle (follows the OS by default, remembered per browser) and a custom favicon; loading, empty, success and error states.

New to the codebase? Start with the [project tour](docs/PROJECT-TOUR.md): a 10-minute walkthrough of what each folder and file does and how one request flows through the system.

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
Features: paginated movie and person lists; movie/person create, update and delete; credits with actor/creator roles and filmography; searches across movies and people; genre and role filtering; artwork upload with progress and cache-busted display; avatar photos for cast and creators; click-through detail popups for movies (creators, cast and characters) and people (bio, filmography); a **Saved** list (bookmark movies and cast/creators; stored in the browser's localStorage, so it is per browser and not shared); inline form validation with live character counters (the catalog service still enforces every rule); a type-to-search person picker for movie credits; a confirmation dialog before deleting and toast notifications for results; a violet/pink theme with a light/dark toggle (follows the OS by default, remembered per browser) and a custom favicon; loading, empty, success and error states.
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
./gradlew ktlintCheck test   # Kotlin style (auto-fix: ./gradlew ktlintFormat) + tests
cd frontend
npm ci
npm run verify   # prettier, eslint, svelte-check, tests, build (individual: format, lint, check, test)
npm test
npm run build
```

Backend tests exercise service validation, paging/search, artwork, gRPC and gateway GraphQL/HTTP behavior. Frontend tests exercise GraphQL requests and failures, upload progress, empty states, creation/search flows, inline validation, the delete confirmation and toast, the person picker, saved lists and the detail popups. No running services are required to run tests.

## AI tool use

This project was built with **GitHub Copilot (agent mode in the JetBrains IDE)** as a development aid.

**What the AI did:** drafted most of the Kotlin services, the Svelte/TypeScript UI, the unit tests, the setup script, the `AGENTS.md` files and the documentation, and ran builds, tests and live smoke tests from the terminal. It also helped diagnose problems (for example a gRPC version mismatch that only appeared when the services were actually started).

**What was decided from the assessment, not by the AI:** GraphQL for structured frontend/backend data, gRPC between the two services, a separate gateway and catalog service, and all business validation living in the catalog service.

**How the output was checked:** backend tests (`./gradlew ktlintCheck test`), frontend checks (`cd frontend && npm run verify`), and end-to-end smoke tests against the running stack (create, search, image upload and download). Tool-generated code was refactored where it fell short (for example splitting a large component and enforcing lint/format rules).

To understand the code quickly, start with [docs/PROJECT-TOUR.md](docs/PROJECT-TOUR.md). `AGENTS.md` files describe the same layout for AI assistants.

## Limitations

This is a local assessment application: there is no authentication or production deployment configuration. Artwork is stored on the local filesystem. For production use, add authentication/authorization, durable object storage, observability and deployment configuration.




**How the output was checked:** backend tests (`./gradlew ktlintCheck test`), frontend checks (`cd frontend && npm run verify`), and end-to-end smoke tests against the running stack (create, search, image upload and download). Tool-generated code was refactored where it fell short (for example splitting a large component and enforcing lint/format rules). The AI tooling cannot render the UI, so layout and visual details (cards, popups, light/dark theme, responsive behaviour) were reviewed and iterated on by the author in a real browser, and several bugs found that way (for example the editor opening blank from a popup, and Enter submitting a form from a search field) were fixed with regression tests.
