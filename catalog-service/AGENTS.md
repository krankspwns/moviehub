# catalog-service/ (Spring Boot, Kotlin, gRPC :9090)

Package root: `src/main/kotlin/com/moviehub/catalog/`

| Path | Responsibility |
|---|---|
| `domain/` | JPA entities, repositories, `NotFoundException`/`ValidationException` |
| `service/` | Business rules + validation: `MovieService`, `PersonService`, `SearchService`, `ArtworkService`, `ArtworkStorage`, `Support.kt` (paging/LIKE helpers) |
| `grpc/` | `CatalogGrpcService` (thin), `Mapping.kt` (domain↔proto), `GrpcErrors.kt` (exception→status) |
| `config/` | `Clock` bean (inject `Clock`, never call `now()` directly) |
| `resources/db/migration/` | Flyway `V<n>__name.sql`; schema is `ddl-auto: validate`, so every entity change needs a new migration (never edit an applied one) |

Rules
- Validation lives in `service/` and throws `ValidationException`; `grpc/` must stay a thin adapter.
- Artwork: JPEG/PNG/WebP, max 5 MiB, 64 KiB chunks; deleting a movie deletes its file.
- Tests: `src/test/kotlin/com/moviehub/catalog/` (`IntegrationTestBase` provides the Spring context). One test class per service. Run `./gradlew :catalog-service:test`.
- Local run uses `./data`; for throwaway runs pass `--args='--moviehub.data-dir=/tmp/x'`.

