# proto/

Single source of truth for the service contract: `src/main/proto/moviehub/catalog/v1/catalog.proto`. Java stubs are generated at build (protobuf 3.25.5, gRPC 1.68.3).

- Additive changes only (new fields/RPCs with new field numbers). Never reuse or renumber fields; reserve removed ones.
- After editing: update `catalog-service` (`grpc/` + `grpc/Mapping.kt`) and `gateway-service` (`catalog/GrpcCatalogClient.kt`, `catalog/Models.kt`), then `schema.graphqls` if exposed.
- Verify: `./gradlew :proto:build :catalog-service:test :gateway-service:test`.
- Do not hand-edit anything under `build/`.

