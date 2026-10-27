package com.moviehub.gateway.catalog

/** Transport-agnostic view of the catalog service, so web/GraphQL code never touches gRPC types. */

enum class Role { ACTOR, DIRECTOR, WRITER, PRODUCER, COMPOSER }

data class CreditDto(
    val personId: String,
    val personName: String,
    val role: Role,
    val characterName: String,
)

data class MovieDto(
    val id: String,
    val title: String,
    val synopsis: String,
    val releaseYear: Int,
    val runtimeMinutes: Int,
    val genres: List<String>,
    val artworkUrl: String?,
    val credits: List<CreditDto>,
    val createdAt: String,
    val updatedAt: String,
)

data class FilmographyEntryDto(val movieId: String, val title: String, val role: Role)

data class PersonDto(
    val id: String,
    val name: String,
    val bio: String,
    val birthDate: String?,
    val filmography: List<FilmographyEntryDto>,
    val avatarUrl: String? = null,
)

data class PageDto<T>(val items: List<T>, val total: Int, val page: Int, val pageSize: Int) {
    val totalPages: Int get() = if (pageSize <= 0) 0 else Math.ceilDiv(total, pageSize)
}

data class SearchResultDto(val movies: List<MovieDto>, val people: List<PersonDto>)

data class CreditInputDto(val personId: String, val role: Role, val characterName: String? = null)

data class MovieInputDto(
    val title: String,
    val synopsis: String? = null,
    val releaseYear: Int,
    val runtimeMinutes: Int? = null,
    val genres: List<String>? = null,
    val credits: List<CreditInputDto>? = null,
)

data class PersonInputDto(val name: String, val bio: String? = null, val birthDate: String? = null)

data class ArtworkInfoDto(val movieId: String, val contentType: String, val sizeBytes: Long, val artworkUrl: String)

class ArtworkDownload(val contentType: String, val chunks: Iterator<ByteArray>)

enum class FailureKind { NOT_FOUND, INVALID, UNAVAILABLE, INTERNAL }

/** Failure reported by the catalog service, already classified for the edge layers. */
class CatalogException(val kind: FailureKind, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

fun artworkUrl(movieId: String, version: Long) = "/api/movies/$movieId/artwork?v=$version"

fun avatarUrl(personId: String, version: Long) = "/api/people/$personId/avatar?v=$version"
