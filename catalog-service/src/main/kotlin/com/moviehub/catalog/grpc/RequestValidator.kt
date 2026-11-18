package com.moviehub.catalog.grpc

import com.moviehub.catalog.domain.Role
import com.moviehub.catalog.domain.ValidationException
import com.moviehub.catalog.service.MovieCommand
import com.moviehub.catalog.service.PersonCommand
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Request-level validation, run by [CatalogGrpcService] before a command reaches the services.
 * Each method returns a normalised (trimmed, de-duplicated) copy or throws [ValidationException],
 * which [GrpcExceptionAdvice] maps to `INVALID_ARGUMENT`. Rules that need the database
 * (for example "the credited person exists") stay in the services.
 */
@Component
class RequestValidator(private val clock: Clock) {

    fun movie(command: MovieCommand): MovieCommand {
        val title = command.title.trim()
        if (title.isEmpty()) throw ValidationException("title must not be blank")
        if (title.length > 200) throw ValidationException("title must be at most 200 characters")

        val synopsis = command.synopsis.trim()
        if (synopsis.length > 4000) throw ValidationException("synopsis must be at most 4000 characters")

        val maxYear = LocalDate.now(clock).year + 10
        if (command.releaseYear !in 1888..maxYear) {
            throw ValidationException("releaseYear must be between 1888 and $maxYear")
        }
        if (command.runtimeMinutes !in 0..1000) {
            throw ValidationException("runtimeMinutes must be between 0 and 1000 (0 = unknown)")
        }

        val genres = command.genres.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (genres.size > 10) throw ValidationException("a movie can have at most 10 genres")
        if (genres.any { it.length > 50 }) throw ValidationException("a genre must be at most 50 characters")

        val credits = command.credits.map { credit ->
            if (credit.personId.isBlank()) throw ValidationException("credit personId must not be blank")
            val character = credit.characterName.trim()
            if (character.length > 200) throw ValidationException("characterName must be at most 200 characters")
            if (credit.role != Role.ACTOR && character.isNotEmpty()) {
                throw ValidationException("characterName is only allowed for the ACTOR role")
            }
            credit.copy(characterName = character)
        }
        if (credits.distinctBy { it.personId to it.role }.size != credits.size) {
            throw ValidationException("duplicate credit (same person and role)")
        }

        return command.copy(title = title, synopsis = synopsis, genres = genres, credits = credits)
    }

    fun person(command: PersonCommand): PersonCommand {
        val name = command.name.trim()
        if (name.isEmpty()) throw ValidationException("name must not be blank")
        if (name.length > 200) throw ValidationException("name must be at most 200 characters")

        val bio = command.bio.trim()
        if (bio.length > 4000) throw ValidationException("bio must be at most 4000 characters")

        val birthDate = command.birthDate.trim()
        if (birthDate.isNotEmpty()) {
            val date = try {
                LocalDate.parse(birthDate)
            } catch (e: DateTimeParseException) {
                throw ValidationException("birthDate must be an ISO-8601 date (yyyy-MM-dd)")
            }
            if (date.isAfter(LocalDate.now(clock))) throw ValidationException("birthDate must not be in the future")
        }
        return command.copy(name = name, bio = bio, birthDate = birthDate)
    }
}
