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

## 2. Follow one action end to end: "add a movie"

1. **UI** – `frontend/src/App.svelte` `submitMovie()` checks the form with `lib/validate.ts`, then calls `saveMovie()` in `lib/api/catalog.ts`.
2. **GraphQL** – the call hits `gateway-service/.../graphql/CatalogGraphQlController.kt`, shaped by `resources/graphql/schema.graphqls`.
3. **Gateway client** – the controller calls `catalog/GrpcCatalogClient.kt`, which turns it into a gRPC request (`CreateMovie`).
4. **gRPC server** – `catalog-service/.../grpc/CatalogGrpcService.kt` receives it and maps proto ⇄ domain types (`Mapping.kt`).
5. **Business rules** – `service/MovieService.kt` `validate()` enforces title/year/runtime/genre/credit limits, then saves via `domain/Repositories.kt` (Spring Data) to `domain/Entities.kt` tables (created by `resources/db/migration/*.sql`).
6. The result travels back up the same path; the UI reloads the list.

Errors: catalog throws `ValidationException` / `NotFoundException` (`domain/Errors.kt`) → turned into gRPC status (`grpc/GrpcErrors.kt`) → gateway maps to GraphQL error codes (`INVALID`, `NOT_FOUND`, …) → UI shows the message.

## 3. Images (movie artwork and cast avatars)

GraphQL can't carry files, so images use plain HTTP:
`POST /api/movies/{id}/artwork` and `POST /api/people/{id}/avatar` → `gateway-service/.../web/ArtworkController.kt` → gRPC client-streaming `UploadArtwork` (64 KiB chunks) → `catalog-service/.../service/ArtworkService.kt` (type, size and file-signature checks) → files on disk via `ArtworkStorage.kt` (`catalog-service/data/artwork/`).
The browser uses `XMLHttpRequest` so it can show upload progress (`uploadImage()` in `catalog.ts`).

## 4. Frontend map

| File | What it does |
|------|--------------|
| `src/App.svelte` | The main screen: tabs (Movies, Cast & creators, Saved, Explore), search, paging, edit dialogs, detail popups. Biggest file; start here. |
| `src/lib/api/catalog.ts` | Every server call (GraphQL queries/mutations, image upload) + TypeScript types. |
| `src/lib/validate.ts` | Form rules that mirror the backend, for instant messages. |
| `src/lib/format.ts` | Small pure helpers (initials, colour hue, role labels). |
| `src/lib/state/saved.ts` | "Saved" movies, kept in the browser's localStorage. |
| `src/lib/state/theme.ts` | Light/dark theme choice. |
| `src/lib/components/` | Reusable pieces: `MovieCard`, `PersonCard`, `MovieDetails`, `PersonDetails`, `Icon`, `IconButton`, `Logo`. |
| `src/style.css` | All styling. Colours are CSS variables at the top; the light theme overrides them. Mobile-first with breakpoints at 640 / 1024 / 1440 px. |
| `src/lib/__tests__/` | Vitest tests (API client, components, validation, saved list, theme). |

## 5. Backend map

**catalog-service** (`com.moviehub.catalog`)
- `domain/` – database entities, repositories, exceptions.
- `service/` – the rules: `MovieService`, `PersonService`, `SearchService`, `ArtworkService`, `ArtworkStorage`.
- `grpc/` – thin gRPC adapter (no rules here).
- `resources/db/migration/` – Flyway SQL (V1 tables, V2 avatars). Add new `V3__…sql` files for schema changes; never edit old ones.

**gateway-service** (`com.moviehub.gateway`)
- `graphql/` – GraphQL controller; `resources/graphql/schema.graphqls` is the public API.
- `catalog/` – `CatalogClient` (interface), `GrpcCatalogClient` (the only code that knows gRPC), `Models.kt` (DTOs).
- `web/` – image endpoints, CORS/static config.

## 6. Run it, poke it, test it

```bash
./setup.sh            # checks/installs tools, then starts everything
```
- App: http://localhost:5173 · GraphiQL (try queries by hand): http://localhost:8080/graphiql
- Backend tests: `./gradlew test` (use JDK 21) · Frontend: `cd frontend && npm run check && npm test`
- Logs: `.run/*.log` · Reset data: stop, then delete `catalog-service/data/`

## 7. Good ways to learn it

1. Open GraphiQL, run `{ movies { items { title credits { personName role } } } }`, then find that field in `schema.graphqls` and follow it down to `MovieService`.
2. Read the tests – they describe behaviour in plain sentences: `catalog-service/src/test/.../MovieServiceTest.kt`, `ArtworkServiceTest.kt`, and `frontend/src/lib/__tests__/`.
3. Change a rule (e.g. max genres in `MovieService.validate()` and `lib/validate.ts`), run the tests, see what breaks.
4. Use the `AGENTS.md` files (root and per folder) as short cheat sheets; they list gotchas such as needing JDK 21 and the gRPC version alignment.

## 8. Vocabulary

- **gRPC / protobuf** – typed service-to-service calls defined in `.proto`; faster and stricter than JSON over HTTP.
- **GraphQL** – the browser asks for exactly the fields it needs through one `/graphql` endpoint.
- **Flyway** – applies the numbered SQL migrations on startup.
- **Svelte 5 runes** – `$props`, `$state`, `$derived` in the newer components; `App.svelte` still uses the older `let` style.

