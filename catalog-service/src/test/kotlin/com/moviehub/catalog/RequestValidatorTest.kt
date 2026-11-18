package com.moviehub.catalog

import com.moviehub.catalog.domain.Role
import com.moviehub.catalog.domain.ValidationException
import com.moviehub.catalog.grpc.MovieCommandValidator
import com.moviehub.catalog.grpc.PersonCommandValidator
import com.moviehub.catalog.grpc.RequestValidator
import com.moviehub.catalog.service.CreditCommand
import com.moviehub.catalog.service.MovieCommand
import com.moviehub.catalog.service.PersonCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

/** Plain unit tests: the validator needs no Spring context or database. */
class RequestValidatorTest {

    private val clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC)
    private val validator = RequestValidator(MovieCommandValidator(clock), PersonCommandValidator(clock))
    private val maxYear = 2036

    private fun movie(
        title: String = "Inception",
        synopsis: String = "A thief enters dreams.",
        year: Int = 2010,
        runtime: Int = 148,
        genres: List<String> = listOf("Sci-Fi"),
        credits: List<CreditCommand> = emptyList(),
    ) = MovieCommand(title, synopsis, year, runtime, genres, credits)

    private fun rejected(command: MovieCommand) {
        assertThrows<ValidationException> { validator.movie(command) }
    }

    private fun rejected(command: PersonCommand) {
        assertThrows<ValidationException> { validator.person(command) }
    }

    // ---- movie ----

    @Test
    fun `movie input is trimmed and genres are cleaned and de-duplicated`() {
        val clean = validator.movie(movie(title = "  Inception  ", synopsis = " s ", genres = listOf(" Sci-Fi ", "sci-fi", "", "Thriller")))
        assertEquals("Inception", clean.title)
        assertEquals("s", clean.synopsis)
        assertEquals(listOf("Sci-Fi", "Thriller"), clean.genres)
    }

    @Test
    fun `blank title is rejected`() = rejected(movie(title = "   "))

    @Test
    fun `title of 200 chars is accepted but 201 is rejected`() {
        validator.movie(movie(title = "a".repeat(200)))
        rejected(movie(title = "a".repeat(201)))
    }

    @Test
    fun `synopsis of 4000 chars is accepted but 4001 is rejected`() {
        validator.movie(movie(synopsis = "x".repeat(4000)))
        rejected(movie(synopsis = "x".repeat(4001)))
    }

    @Test
    fun `release year boundaries`() {
        validator.movie(movie(year = 1888))
        validator.movie(movie(year = maxYear))
        rejected(movie(year = 1887))
        rejected(movie(year = maxYear + 1))
    }

    @Test
    fun `runtime boundaries`() {
        validator.movie(movie(runtime = 0))
        validator.movie(movie(runtime = 1000))
        rejected(movie(runtime = -1))
        rejected(movie(runtime = 1001))
    }

    @Test
    fun `more than ten genres or an overly long genre is rejected`() {
        validator.movie(movie(genres = (1..10).map { "g$it" }))
        rejected(movie(genres = (1..11).map { "g$it" }))
        rejected(movie(genres = listOf("g".repeat(51))))
    }

    @Test
    fun `credit needs a person id`() = rejected(movie(credits = listOf(CreditCommand(" ", Role.ACTOR))))

    @Test
    fun `duplicate credit for same person and role is rejected but different roles are allowed`() {
        rejected(movie(credits = listOf(CreditCommand("p", Role.ACTOR), CreditCommand("p", Role.ACTOR))))
        validator.movie(movie(credits = listOf(CreditCommand("p", Role.ACTOR), CreditCommand("p", Role.DIRECTOR))))
    }

    @Test
    fun `character name is only allowed for actors and is trimmed and capped`() {
        rejected(movie(credits = listOf(CreditCommand("p", Role.DIRECTOR, "Hero"))))
        rejected(movie(credits = listOf(CreditCommand("p", Role.ACTOR, "c".repeat(201)))))
        val clean = validator.movie(movie(credits = listOf(CreditCommand("p", Role.ACTOR, " Cobb "))))
        assertEquals("Cobb", clean.credits.single().characterName)
    }

    // ---- person ----

    @Test
    fun `person input is trimmed and blank birth date means unknown`() {
        val clean = validator.person(PersonCommand("  Ada Lovelace ", " pioneer ", "  "))
        assertEquals(PersonCommand("Ada Lovelace", "pioneer", ""), clean)
    }

    @Test
    fun `invalid person input is rejected`() {
        rejected(PersonCommand("  "))
        rejected(PersonCommand("n".repeat(201)))
        rejected(PersonCommand("x", bio = "b".repeat(4001)))
        rejected(PersonCommand("x", birthDate = "10/12/1815"))
        rejected(PersonCommand("x", birthDate = "2023-02-30"))
    }

    @Test
    fun `birth date cannot be in the future but today is fine`() {
        validator.person(PersonCommand("x", birthDate = "2026-06-15"))
        rejected(PersonCommand("x", birthDate = "2026-06-16"))
    }
}
