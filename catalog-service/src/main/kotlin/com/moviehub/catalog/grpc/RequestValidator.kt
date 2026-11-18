package com.moviehub.catalog.grpc

import com.moviehub.catalog.domain.ValidationException
import com.moviehub.catalog.service.MovieCommand
import com.moviehub.catalog.service.PersonCommand
import org.springframework.stereotype.Component
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.validation.Validator

/**
 * Entry point used by [CatalogGrpcService]: normalises a command (trim, clean genres) and then
 * runs it through the matching Spring [Validator] ([MovieCommandValidator], [PersonCommandValidator]).
 * The first reported error becomes a [ValidationException], which [GrpcExceptionAdvice] maps to
 * `INVALID_ARGUMENT`. Rules that need the database stay in the services.
 */
@Component
class RequestValidator(private val movieValidator: MovieCommandValidator, private val personValidator: PersonCommandValidator) {

    fun movie(command: MovieCommand): MovieCommand {
        val clean = command.copy(
            title = command.title.trim(),
            synopsis = command.synopsis.trim(),
            genres = command.genres.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
            credits = command.credits.map { it.copy(characterName = it.characterName.trim()) },
        )
        return check(clean, movieValidator, "movie")
    }

    fun person(command: PersonCommand): PersonCommand {
        val clean = command.copy(name = command.name.trim(), bio = command.bio.trim(), birthDate = command.birthDate.trim())
        return check(clean, personValidator, "person")
    }

    private fun <T : Any> check(target: T, validator: Validator, name: String): T {
        val errors: Errors = BeanPropertyBindingResult(target, name)
        validator.validate(target, errors)
        errors.allErrors.firstOrNull()?.let { throw ValidationException(it.defaultMessage ?: "invalid $name") }
        return target
    }
}
