package com.moviehub.catalog.grpc

import com.moviehub.catalog.domain.Role
import com.moviehub.catalog.service.MovieCommand
import org.springframework.stereotype.Component
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import java.time.Clock
import java.time.LocalDate

/** Rules for a (already trimmed) [MovieCommand]. Errors are reported in rule order. */
@Component
class MovieCommandValidator(private val clock: Clock) : Validator {

    override fun supports(clazz: Class<*>): Boolean = MovieCommand::class.java.isAssignableFrom(clazz)

    override fun validate(target: Any, errors: Errors) {
        val movie = target as MovieCommand

        if (movie.title.isEmpty()) errors.rejectValue("title", "blank", "title must not be blank")
        if (movie.title.length > 200) errors.rejectValue("title", "tooLong", "title must be at most 200 characters")
        if (movie.synopsis.length > 4000) errors.rejectValue("synopsis", "tooLong", "synopsis must be at most 4000 characters")

        val maxYear = LocalDate.now(clock).year + 10
        if (movie.releaseYear !in 1888..maxYear) {
            errors.rejectValue("releaseYear", "range", "releaseYear must be between 1888 and $maxYear")
        }
        if (movie.runtimeMinutes !in 0..1000) {
            errors.rejectValue("runtimeMinutes", "range", "runtimeMinutes must be between 0 and 1000 (0 = unknown)")
        }

        if (movie.genres.size > 10) errors.rejectValue("genres", "tooMany", "a movie can have at most 10 genres")
        if (movie.genres.any { it.length > 50 }) errors.rejectValue("genres", "tooLong", "a genre must be at most 50 characters")

        for (credit in movie.credits) {
            if (credit.personId.isBlank()) errors.rejectValue("credits", "blankPerson", "credit personId must not be blank")
            if (credit.characterName.length > 200) {
                errors.rejectValue("credits", "tooLong", "characterName must be at most 200 characters")
            }
            if (credit.role != Role.ACTOR && credit.characterName.isNotEmpty()) {
                errors.rejectValue("credits", "actorOnly", "characterName is only allowed for the ACTOR role")
            }
        }
        if (movie.credits.distinctBy { it.personId to it.role }.size != movie.credits.size) {
            errors.rejectValue("credits", "duplicate", "duplicate credit (same person and role)")
        }
    }
}
