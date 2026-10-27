package com.moviehub.catalog.service

import com.moviehub.catalog.domain.CreditEntity
import com.moviehub.catalog.domain.MovieEntity
import com.moviehub.catalog.domain.PersonEntity
import com.moviehub.catalog.domain.ValidationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

const val DEFAULT_PAGE_SIZE = 20
const val MAX_PAGE_SIZE = 100

/**
 * Builds a lower-cased `%term%` LIKE pattern. `!` is the escape character
 * (declared with `escape '!'` in the repositories) so users can search for
 * literal `%` and `_` without them acting as wildcards.
 */
fun likePattern(query: String): String {
    val escaped = query.trim().lowercase()
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_")
    return "%$escaped%"
}

/** Validates paging input. `size == 0` means "use the default". */
fun pageRequest(page: Int, size: Int, sort: Sort): Pageable {
    if (page < 0) throw ValidationException("page must be >= 0")
    if (size < 0 || size > MAX_PAGE_SIZE) throw ValidationException("page size must be between 1 and $MAX_PAGE_SIZE")
    return PageRequest.of(page, if (size == 0) DEFAULT_PAGE_SIZE else size, sort)
}

fun MovieEntity.toView() = MovieView(
    id = id,
    title = title,
    synopsis = synopsis,
    releaseYear = releaseYear,
    runtimeMinutes = runtimeMinutes,
    genres = genres.toList(),
    hasArtwork = artworkContentType != null,
    artworkVersion = artworkVersion,
    credits = credits.map { it.toView() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CreditEntity.toView() = CreditView(
    personId = person!!.id,
    personName = person!!.name,
    role = role,
    characterName = characterName,
)

fun PersonEntity.toView() = PersonView(
    id = id,
    name = name,
    bio = bio,
    birthDate = birthDate,
    filmography = credits
        .map { FilmographyEntry(it.movie!!.id, it.movie!!.title, it.role) }
        .sortedWith(compareBy({ it.title.lowercase() }, { it.role })),
    hasAvatar = avatarContentType != null,
    avatarVersion = avatarVersion,
)

