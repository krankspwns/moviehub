package com.moviehub.catalog

import com.moviehub.catalog.domain.NotFoundException
import com.moviehub.catalog.domain.Role
import com.moviehub.catalog.domain.ValidationException
import com.moviehub.catalog.service.CreditCommand
import com.moviehub.catalog.service.MovieCommand
import com.moviehub.catalog.service.MovieService
import com.moviehub.catalog.service.PersonCommand
import com.moviehub.catalog.service.PersonService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovieServiceTest : IntegrationTestBase() {

    @Autowired lateinit var movies: MovieService

    @Autowired lateinit var people: PersonService

    private fun command(
        title: String = "Inception",
        year: Int = 2010,
        runtime: Int = 148,
        genres: List<String> = listOf("Sci-Fi"),
        credits: List<CreditCommand> = emptyList(),
        synopsis: String = "A thief enters dreams.",
    ) = MovieCommand(title, synopsis, year, runtime, genres, credits)

    private fun person(name: String) = people.create(PersonCommand(name)).id

    // ---- create / read ----

    @Test
    fun `create persists the given fields`() {
        val created = movies.create(command(title = "Inception", genres = listOf("Sci-Fi", "Thriller")))

        assertEquals("Inception", created.title)
        assertEquals(listOf("Sci-Fi", "Thriller"), created.genres)
        assertFalse(created.hasArtwork)
        assertEquals(created, movies.get(created.id))
    }

    @Test
    fun `get unknown movie throws NotFound`() {
        assertThrows<NotFoundException> { movies.get("missing") }
    }

    @Test
    fun `create with credits exposes person names and character`() {
        val nolan = person("Christopher Nolan")
        val dicaprio = person("Leonardo DiCaprio")

        val created = movies.create(
            command(
                credits = listOf(
                    CreditCommand(nolan, Role.DIRECTOR),
                    CreditCommand(dicaprio, Role.ACTOR, "Cobb"),
                ),
            ),
        )

        assertEquals(2, created.credits.size)
        val actor = created.credits.single { it.role == Role.ACTOR }
        assertEquals("Leonardo DiCaprio", actor.personName)
        assertEquals("Cobb", actor.characterName)
    }

    // ---- validation / edge cases ----

    @Test
    fun `credit referencing unknown person is rejected`() {
        assertThrows<ValidationException> {
            movies.create(command(credits = listOf(CreditCommand("nope", Role.ACTOR))))
        }
        assertEquals(0, movies.list("", 0, 10).total)
    }

    @Test
    fun `same person can hold different roles on one movie`() {
        val p = person("Clint Eastwood")
        val ok = movies.create(command(credits = listOf(CreditCommand(p, Role.ACTOR), CreditCommand(p, Role.DIRECTOR))))
        assertEquals(2, ok.credits.size)
    }

    // ---- update ----

    @Test
    fun `update replaces fields and synchronises credits`() {
        val a = person("Actor A")
        val b = person("Actor B")
        val c = person("Director C")
        val created = movies.create(
            command(credits = listOf(CreditCommand(a, Role.ACTOR, "Old"), CreditCommand(b, Role.ACTOR))),
        )

        val updated = movies.update(
            created.id,
            command(
                title = "New title",
                genres = listOf("Drama"),
                credits = listOf(CreditCommand(a, Role.ACTOR, "New"), CreditCommand(c, Role.DIRECTOR)),
            ),
        )

        assertEquals("New title", updated.title)
        assertEquals(listOf("Drama"), updated.genres)
        assertEquals(setOf("Actor A", "Director C"), updated.credits.map { it.personName }.toSet())
        assertEquals("New", updated.credits.single { it.personId == a }.characterName)
        assertEquals(updated, movies.get(created.id))
    }

    @Test
    fun `update with identical credits does not violate unique constraint`() {
        val a = person("Actor A")
        val created = movies.create(command(credits = listOf(CreditCommand(a, Role.ACTOR))))
        val again = movies.update(created.id, command(credits = listOf(CreditCommand(a, Role.ACTOR))))
        assertEquals(1, again.credits.size)
    }

    @Test
    fun `update unknown movie throws NotFound and failed update leaves data untouched`() {
        assertThrows<NotFoundException> { movies.update("missing", command()) }
        val created = movies.create(command())
        val ghost = CreditCommand("nope", Role.ACTOR)
        assertThrows<ValidationException> { movies.update(created.id, command(title = "Changed", credits = listOf(ghost))) }
        assertEquals("Inception", movies.get(created.id).title)
    }

    // ---- delete ----

    @Test
    fun `delete removes movie and is idempotent`() {
        val created = movies.create(command())
        assertTrue(movies.delete(created.id))
        assertFalse(movies.delete(created.id))
        assertThrows<NotFoundException> { movies.get(created.id) }
    }

    @Test
    fun `deleting a movie keeps its people`() {
        val p = person("Keep Me")
        val created = movies.create(command(credits = listOf(CreditCommand(p, Role.DIRECTOR))))
        movies.delete(created.id)
        assertEquals("Keep Me", people.get(p).name)
        assertTrue(people.get(p).filmography.isEmpty())
    }

    // ---- list / search ----

    @Test
    fun `list is sorted by title and paginated with total`() {
        listOf("Charlie", "alpha", "Bravo", "Delta", "echo").forEach { movies.create(command(title = it)) }

        val first = movies.list("", 0, 2)
        val second = movies.list("", 1, 2)
        val last = movies.list("", 2, 2)

        assertEquals(5, first.total)
        assertEquals(listOf("alpha", "Bravo"), first.items.map { it.title })
        assertEquals(listOf("Charlie", "Delta"), second.items.map { it.title })
        assertEquals(listOf("echo"), last.items.map { it.title })
        assertTrue(movies.list("", 3, 2).items.isEmpty())
    }

    @Test
    fun `list on empty database returns empty page`() {
        val page = movies.list("", 0, 20)
        assertEquals(0, page.total)
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun `invalid paging is rejected and size zero uses default`() {
        assertThrows<ValidationException> { movies.list("", -1, 10) }
        assertThrows<ValidationException> { movies.list("", 0, 101) }
        assertThrows<ValidationException> { movies.list("", 0, -5) }
        movies.list("", 0, 0)
    }

    @Test
    fun `search matches title synopsis genre and credited person case-insensitively`() {
        val nolan = person("Christopher Nolan")
        movies.create(command(title = "Inception", genres = listOf("Sci-Fi"), credits = listOf(CreditCommand(nolan, Role.DIRECTOR))))
        movies.create(command(title = "Casablanca", synopsis = "Love in wartime", genres = listOf("Romance")))

        assertEquals(listOf("Inception"), movies.list("INCEP", 0, 10).items.map { it.title })
        assertEquals(listOf("Casablanca"), movies.list("wartime", 0, 10).items.map { it.title })
        assertEquals(listOf("Casablanca"), movies.list("romance", 0, 10).items.map { it.title })
        assertEquals(listOf("Inception"), movies.list("nolan", 0, 10).items.map { it.title })
        assertEquals(0, movies.list("zzz", 0, 10).total)
    }

    @Test
    fun `search does not return duplicates when several genres match`() {
        movies.create(command(title = "Dupes", genres = listOf("Action", "Action Comedy")))
        assertEquals(1, movies.list("action", 0, 10).total)
    }

    @Test
    fun `like wildcards in the query are treated literally`() {
        movies.create(command(title = "100% Wolf"))
        movies.create(command(title = "Snake_Eyes"))
        movies.create(command(title = "Something Else"))

        assertEquals(listOf("100% Wolf"), movies.list("%", 0, 10).items.map { it.title })
        assertEquals(listOf("Snake_Eyes"), movies.list("_", 0, 10).items.map { it.title })
        assertEquals(0, movies.list("!", 0, 10).total)
    }
}
