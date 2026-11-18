package com.moviehub.catalog.grpc

import com.moviehub.catalog.service.PersonCommand
import org.springframework.stereotype.Component
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Rules for a (already trimmed) [PersonCommand]. Errors are reported in rule order. */
@Component
class PersonCommandValidator(private val clock: Clock) : Validator {

    override fun supports(clazz: Class<*>): Boolean = PersonCommand::class.java.isAssignableFrom(clazz)

    override fun validate(target: Any, errors: Errors) {
        val person = target as PersonCommand

        if (person.name.isEmpty()) errors.rejectValue("name", "blank", "name must not be blank")
        if (person.name.length > 200) errors.rejectValue("name", "tooLong", "name must be at most 200 characters")
        if (person.bio.length > 4000) errors.rejectValue("bio", "tooLong", "bio must be at most 4000 characters")

        if (person.birthDate.isNotEmpty()) {
            val date = try {
                LocalDate.parse(person.birthDate)
            } catch (e: DateTimeParseException) {
                errors.rejectValue("birthDate", "format", "birthDate must be an ISO-8601 date (yyyy-MM-dd)")
                return
            }
            if (date.isAfter(LocalDate.now(clock))) {
                errors.rejectValue("birthDate", "future", "birthDate must not be in the future")
            }
        }
    }
}
