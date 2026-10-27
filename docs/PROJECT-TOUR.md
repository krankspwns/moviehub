# MovieHub project tour

Read this top to bottom (about 10 minutes). Each section says **what it is**, **where it lives** and **what to open first**.

## 1. The big picture

```
Browser (Svelte)  ──GraphQL /graphql──▶  gateway-service (:8080)  ──gRPC──▶  catalog-service (:9090)
                  ──HTTP upload/download─▶  (images only)                      H2 database + image files
```

| Part | Folder | Job in one line |
|------|--------|-----------------|
| Contract | `proto/` | The gRPC API between the two services (`catalog.proto`). Generates Java/Kotlin classes for both. |
| catalog-service | `catalog-service/` | The "brain": owns the data, validates every rule, stores movies, people, credits and image files. |
| gateway-service | `gateway-service/` | The "front door": exposes GraphQL + image endpoints to the browser, forwards everything to catalog over gRPC. No business rules. |
| frontend | `frontend/` | The Svelte + TypeScript UI. |

Why two services? The assessment asks for it, and it gives a clean split: the browser never talks to the data service directly.

## 2. The five technology requirements: what each is for and how we use it

| Requirement | Where it lives | One-line purpose |
|-------------|----------------|------------------|
| Svelte frontend in TypeScript | `frontend/` | The screens people click on. |
| Spring Boot services in Kotlin | `catalog-service/`, `gateway-service/` | The server-side logic, split in two. |
| At least 2 Spring Boot services | the same two folders | Separation of "owns the data" from "talks to the browser". |
| Service-to-service via gRPC | `proto/` + both services | A fast, typed contract between the two services. |
| Frontend-to-backend via GraphQL | `gateway-service/.../schema.graphqls` + `frontend/src/lib/api/catalog.ts` | The browser asks for exactly the data it needs. |

```
 Svelte + TypeScript          Spring Boot + Kotlin #1          Spring Boot + Kotlin #2
 (browser)                    gateway-service :8080            catalog-service :9090
 ───────────────  GraphQL  ─▶ ───────────────────── gRPC ───▶ ─────────────────────
 screens, forms,  (+ image     translates requests,            business rules, database,
 validation hints  HTTP)       no business rules               image files
```

### Svelte frontend in TypeScript (`frontend/`)
- **Purpose:** everything the user sees: lists, search, forms, popups, saved list, light/dark theme.
- **How:** Svelte 5 (with "runes": `$state`, `$derived`, `$props`) built by Vite. All code is TypeScript in strict mode (`tsconfig.json`), checked by `svelte-check`; the server's data shapes are declared once as TypeScript types in `src/lib/api/catalog.ts` (`Movie`, `Person`, `Credit`, ...), so a wrong field name fails at build time.
- **Shape:** `App.svelte` orchestrates; `lib/components/` holds the pieces; `lib/api/` is the only place that talks to the server; `lib/state/` and `lib/validate.ts` are plain TypeScript with unit tests (Vitest).
- **Dev wiring:** Vite proxies `/graphql` and `/api` to the gateway (`vite.config.ts`), so the browser only ever talks to one origin.

### Spring Boot services in Kotlin
- **Purpose:** all server-side behaviour. Kotlin gives short, null-safe code; Spring Boot 3.5 supplies the web server, GraphQL, JPA/Hibernate, Flyway and dependency injection.
- **How:** a Gradle multi-module build (root `build.gradle.kts`) with JDK 21 and Kotlin 2.1. Each service is its own runnable Spring Boot application with its own `application.yml`, port and tests. Style is enforced with ktlint.

### At least 2 Spring Boot services (we have 2)
| Service | Port | Responsibility | Spring pieces used |
|---------|------|----------------|--------------------|
| **catalog-service** | gRPC 9090 | System of record. Validates every rule (title length, year range, genres, credits, image type/size), stores movies, people and credits, stores image files, runs search. | Spring Data JPA, H2 file database, Flyway migrations, gRPC server (`net.devh` starter) |
| **gateway-service** | HTTP 8080 | The only thing the browser talks to. Exposes GraphQL and the image endpoints, turns each call into a gRPC call, converts failures into friendly errors. Holds **no** business rules and **no** database. | Spring for GraphQL, Spring Web MVC (uploads/downloads), gRPC client stubs |

Why split it? The browser never touches the data service directly, the contract between them (`proto/`) is explicit, and either side can change internally without breaking the other. The gateway can be tested with a fake catalog (`FakeCatalogClient` in the tests), and the catalog can be tested without any HTTP.

### Service-to-service communication via gRPC
- **Purpose:** the gateway-to-catalog link. gRPC is a binary, strongly typed call protocol: the contract is written once and both sides get generated code, so they cannot drift apart silently.
- **The contract:** `proto/src/main/proto/moviehub/catalog/v1/catalog.proto`. Gradle generates Java classes from it into both services (`proto/build.gradle.kts`).
- **How we use the three gRPC call styles:**
  | Style | Our RPCs | Why |
  |-------|----------|-----|
  | Unary (one request, one reply) | `ListMovies`, `GetMovie`, `CreateMovie`, `UpdateMovie`, `DeleteMovie`, the same for people, and `Search` | Ordinary CRUD and search. |
  | Client streaming | `UploadArtwork` (movie posters and person avatars; first message is metadata, the rest are 64 KiB chunks) | Large files never have to be held fully in memory. |
  | Server streaming | `DownloadArtwork`, `DownloadAvatar` | Images are streamed back to the browser in chunks. |
- **Client side:** `gateway-service/.../catalog/GrpcCatalogClient.kt` is the only class that knows gRPC. It sets deadlines (timeouts), and converts gRPC status codes (`NOT_FOUND`, `INVALID_ARGUMENT`, `UNAVAILABLE`, ...) into our own `CatalogException` kinds.
- **Server side:** `catalog-service/.../grpc/CatalogGrpcService.kt` only maps messages to domain objects (`Mapping.kt`) and delegates to the services; `GrpcErrors.kt` turns our exceptions into gRPC statuses.
- **Version note:** both services pin the same gRPC version via the BOM (`grpc-bom:1.68.3`); a mismatch crashes at startup.

### Frontend-to-backend communication via GraphQL
- **Purpose:** the browser describes exactly which fields it wants and gets exactly that, in a single request (for example a movie list with each movie's credits), without a pile of REST endpoints.
- **The contract:** `gateway-service/src/main/resources/graphql/schema.graphqls`.
  - Queries: `movies`, `movie`, `people`, `person`, `search`.
  - Mutations: `createMovie`, `updateMovie`, `deleteMovie`, `createPerson`, `updatePerson`, `deletePerson`.
- **Server side:** `CatalogGraphQlController.kt` has one method per query/mutation and simply calls `CatalogClient` (gRPC). Failures become GraphQL errors with an `extensions.code` of `INVALID`, `NOT_FOUND`, `UNAVAILABLE` or `INTERNAL`.
- **Client side:** `frontend/src/lib/api/catalog.ts` has one `graphql()` function that POSTs to `/graphql`, and small wrappers (`listMovies`, `saveMovie`, `getMovies`, ...). User input is always sent as GraphQL **variables**, never pasted into the query text, which prevents injection. Error codes are turned into readable messages.
- **The one exception: images.** GraphQL does not carry binary files natively and the browser needs upload progress, so uploads and downloads use plain HTTP (`/api/movies/{id}/artwork`, `/api/people/{id}/avatar`) on the same gateway. The gateway still relays them to the catalog over gRPC streaming, so the "frontend over HTTP/GraphQL, services over gRPC" rule holds.

### The same movie at each layer
| Layer | What "Heat" looks like |
|-------|-----------------------|
| Browser (TypeScript) | `Movie` object: `{ id, title, releaseYear, artworkUrl, credits: [...] }` |
| GraphQL | `type Movie { id title releaseYear artworkUrl credits { personName role } ... }` |
| gRPC (protobuf) | `message Movie { string id = 1; string title = 2; ... bool has_artwork = 7; ... }` |
| Catalog domain | `MovieEntity` rows in the `movies`, `movie_genres` and `credits` tables |

Each layer has its own type, and a mapper between neighbours (`catalog.ts` types, `GrpcCatalogClient` mapping, `Mapping.kt`, `Support.kt`). It is a little repetitive, but each layer can change without leaking into the others.

## 3. Follow one action end to end: "add a movie"

1. **UI** – `frontend/src/lib/components/MovieEditor.svelte` `submit()` checks the form with `lib/validate.ts`, then calls `saveMovie()` in `lib/api/catalog.ts`.
2. **GraphQL** – the call hits `gateway-service/.../graphql/CatalogGraphQlController.kt`, shaped by `resources/graphql/schema.graphqls`.
3. **Gateway client** – the controller calls `catalog/GrpcCatalogClient.kt`, which turns it into a gRPC request (`CreateMovie`).
4. **gRPC server** – `catalog-service/.../grpc/CatalogGrpcService.kt` receives it and maps proto ⇄ domain types (`Mapping.kt`).
5. **Business rules** – `service/MovieService.kt` `validate()` enforces title/year/runtime/genre/credit limits, then saves via `domain/Repositories.kt` (Spring Data) to `domain/Entities.kt` tables (created by `resources/db/migration/*.sql`).
6. The result travels back up the same path; the UI reloads the list.

Errors: catalog throws `ValidationException` / `NotFoundException` (`domain/Errors.kt`) → turned into gRPC status (`grpc/GrpcErrors.kt`) → gateway maps to GraphQL error codes (`INVALID`, `NOT_FOUND`, …) → UI shows the message.

## 4. Images (movie artwork and cast avatars)

GraphQL can't carry files, so images use plain HTTP:
`POST /api/movies/{id}/artwork` and `POST /api/people/{id}/avatar` → `gateway-service/.../web/ArtworkController.kt` → gRPC client-streaming `UploadArtwork` (64 KiB chunks) → `catalog-service/.../service/ArtworkService.kt` (type, size and file-signature checks) → files on disk via `ArtworkStorage.kt` (`catalog-service/data/artwork/`).
The browser uses `XMLHttpRequest` so it can show upload progress (`uploadImage()` in `catalog.ts`).

## 5. Frontend map

| File | What it does |
|------|--------------|
| `src/App.svelte` | The main screen's orchestration: which tab is shown, loading/search/paging, delete, saved list and theme, and which dialog is open. Start here. |
| `src/lib/api/catalog.ts` | Every server call (GraphQL queries/mutations, image upload) + TypeScript types. |
| `src/lib/validate.ts` | Form rules that mirror the backend, for instant messages. |
| `src/lib/format.ts` | Small pure helpers (initials, colour hue, role labels). |
| `src/lib/state/saved.ts` | "Saved" movies, kept in the browser's localStorage. |
| `src/lib/state/theme.ts` | Light/dark theme choice. |
| `src/lib/components/` | `Header` (tabs, theme toggle), `MovieCard` / `PersonCard`, `MovieDetails` / `PersonDetails` (read-only popups), `MovieEditor` / `PersonEditor` (forms with their own validation and uploads), `SearchResults`, `Icon`, `IconButton`, `Logo`. |
| `src/style.css` | All styling. Colours are CSS variables at the top; the light theme overrides them. Mobile-first with breakpoints at 640 / 1024 / 1440 px. |
| `src/lib/__tests__/` | Vitest tests (API client, components, editors, validation, saved list, theme). |

## 6. Backend map

**catalog-service** (`com.moviehub.catalog`)
- `domain/` – database entities, repositories, exceptions.
- `service/` – the rules: `MovieService`, `PersonService`, `SearchService`, `ArtworkService`, `ArtworkStorage`.
- `grpc/` – thin gRPC adapter (no rules here).
- `resources/db/migration/` – Flyway SQL (V1 tables, V2 avatars). Add new `V3__…sql` files for schema changes; never edit old ones.

**gateway-service** (`com.moviehub.gateway`)
- `graphql/` – GraphQL controller; `resources/graphql/schema.graphqls` is the public API.
- `catalog/` – `CatalogClient` (interface), `GrpcCatalogClient` (the only code that knows gRPC), `Models.kt` (DTOs).
- `web/` – image endpoints, CORS/static config.

## 7. Run it, poke it, test it

```bash
./setup.sh            # checks/installs tools, then starts everything
```
- App: http://localhost:5173 · GraphiQL (try queries by hand): http://localhost:8080/graphiql
- Backend: `./gradlew ktlintCheck test` (use JDK 21) · Frontend: `cd frontend && npm run verify` (format check, lint, type check, tests, build)
- Logs: `.run/*.log` · Reset data: stop, then delete `catalog-service/data/`

## 8. Good ways to learn it

1. Open GraphiQL, run `{ movies { items { title credits { personName role } } } }`, then find that field in `schema.graphqls` and follow it down to `MovieService`.
2. Read the tests – they describe behaviour in plain sentences: `catalog-service/src/test/.../MovieServiceTest.kt`, `ArtworkServiceTest.kt`, and `frontend/src/lib/__tests__/`.
3. Change a rule (e.g. max genres in `MovieService.validate()` and `lib/validate.ts`), run the tests, see what breaks.
4. Use the `AGENTS.md` files (root and per folder) as short cheat sheets; they list gotchas such as needing JDK 21 and the gRPC version alignment.

## 9. Vocabulary

- **gRPC / protobuf** – typed service-to-service calls defined in `.proto`; faster and stricter than JSON over HTTP.
- **GraphQL** – the browser asks for exactly the fields it needs through one `/graphql` endpoint.
- **Flyway** – applies the numbered SQL migrations on startup.
- **Svelte 5 runes** – `$props`, `$state`, `$derived`: how every component declares props and reactive state (enforced in `vite.config.ts`).
