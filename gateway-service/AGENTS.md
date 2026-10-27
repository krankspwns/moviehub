# gateway-service/ (Spring Boot, Kotlin, HTTP :8080)

Package root: `src/main/kotlin/com/moviehub/gateway/`

| Path | Responsibility |
|---|---|
| `src/main/resources/graphql/schema.graphqls` | GraphQL contract the frontend depends on |
| `graphql/CatalogGraphQlController.kt` | Pure delegation to `CatalogClient`; `CatalogExceptionResolver` maps failures to GraphQL errors with `extensions.code` (`INVALID`, `NOT_FOUND`, `UNAVAILABLE`, `INTERNAL`) |
| `web/ArtworkController.kt` | REST upload/download of artwork (streams to catalog via gRPC) |
| `web/WebConfig.kt` | RFC 7807 errors for REST, CORS (`moviehub.cors.allowed-origins`) |
| `catalog/` | `CatalogClient` interface, `GrpcCatalogClient`, DTOs in `Models.kt`, channel config |

Rules
- No business logic here. Add behaviour in `catalog-service`, then expose it: schema → controller method → `CatalogClient` method → `GrpcCatalogClient`.
- Schema changes must be mirrored in `frontend/src/lib/api/catalog.ts`.
- Tests: `src/test/kotlin/com/moviehub/gateway/` (GraphQL via `GraphQlTester`, gRPC client via in-process server). Run `./gradlew :gateway-service:test`.
- Catalog address: env `CATALOG_GRPC_ADDRESS` (default `static://localhost:9090`).

