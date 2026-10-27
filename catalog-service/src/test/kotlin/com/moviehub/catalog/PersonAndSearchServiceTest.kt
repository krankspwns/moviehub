package com.moviehub.catalog

import com.moviehub.catalog.domain.NotFoundException
import com.moviehub.catalog.domain.Role
import com.moviehub.catalog.domain.ValidationException
import com.moviehub.catalog.service.CreditCommand
import com.moviehub.catalog.service.MovieCommand
import com.moviehub.catalog.service.MovieService
import com.moviehub.catalog.service.PersonCommand
import com.moviehub.catalog.service.PersonService
import com.moviehub.catalog.service.SearchService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonServiceTest : IntegrationTestBase() {

    @Autowired lateinit var people: PersonService

    @Autowired lateinit var movies: MovieService

    private fun movie(title: String, vararg credits: CreditCommand) =
        movies.create(MovieCommand(title, "", 2000, 100, emptyList(), credits.toList()))

    @Test
    fun `create stores trimmed fields and birth date`() {
        val p = people.create(PersonCommand("  Ada Lovelace ", " pioneer ", "1815-12-10"))
        assertEquals("Ada Lovelace", p.name)
        assertEquals("pioneer", p.bio)
        assertEquals(LocalDate.of(1815, 12, 10), p.birthDate)
        assertEquals(p, people.get(p.id))
    }

    @Test
    fun `blank birth date means unknown`() {
        assertNull(people.create(PersonCommand("Anon", birthDate = "  ")).birthDate)
    }

    @Test
    fun `invalid input is rejected`() {
        assertThrows<ValidationException> { people.create(PersonCommand("  ")) }
        assertThrows<ValidationException> { people.create(PersonCommand("n".repeat(201))) }
        assertThrows<ValidationException> { people.create(PersonCommand("x", bio = "b".repeat(4001))) }
        assertThrows<ValidationException> { people.create(PersonCommand("x", birthDate = "10/12/1815")) }
        assertThrows<ValidationException> { people.create(PersonCommand("x", birthDate = "2023-02-30")) }
        assertThrows<ValidationException> {
            people.create(PersonCommand("x", birthDate = LocalDate.now().plusDays(1).toString()))
        }
    }

    @Test
    fun `get unknown person throws NotFound`() {
        assertThrows<NotFoundException> { people.get("missing") }
    }

    @Test
    fun `update changes fields and can clear the birth date`() {
        val p = people.create(PersonCommand("Old", "bio", "1990-01-01"))
        val updated = people.update(p.id, PersonCommand("New", "", ""))
        assertEquals("New", updated.name)
        assertEquals("", updated.bio)
        assertNull(updated.birthDate)
    }

    @Test
    fun `update unknown person throws NotFound and invalid update keeps data`() {
        assertThrows<NotFoundException> { people.update("missing", PersonCommand("x")) }
        val p = people.create(PersonCommand("Keep"))
        assertThrows<ValidationException> { people.update(p.id, PersonCommand("")) }
        assertEquals("Keep", people.get(p.id).name)
    }

    @Test
    fun `delete cascades credits but keeps the movie`() {
        val p = people.create(PersonCommand("Gone"))
        val other = people.create(PersonCommand("Stays"))
        val m = movie("Film", CreditCommand(p.id, Role.ACTOR), CreditCommand(other.id, Role.DIRECTOR))

        assertTrue(people.delete(p.id))
        assertFalse(people.delete(p.id))

        val reloaded = movies.get(m.id)
        assertEquals(listOf("Stays"), reloaded.credits.map { it.personName })
    }

    @Test
    fun `filmography lists movies sorted by title with roles`() {
        val p = people.create(PersonCommand("Multi"))
        movie("Zulu", CreditCommand(p.id, Role.ACTOR))
        movie("alpha", CreditCommand(p.id, Role.DIRECTOR), CreditCommand(p.id, Role.WRITER))

        val film = people.get(p.id).filmography
        assertEquals(listOf("alpha", "alpha", "Zulu"), film.map { it.title })
        assertEquals(listOf(Role.DIRECTOR, Role.WRITER), film.take(2).map { it.role })
    }

    @Test
    fun `list sorts by name, paginates, searches and filters by role`() {
        val actor = people.create(PersonCommand("Bob Actor"))
        val director = people.create(PersonCommand("Alice Director"))
        people.create(PersonCommand("Carol Idle"))
        movie("M", CreditCommand(actor.id, Role.ACTOR), CreditCommand(director.id, Role.DIRECTOR))

        val all = people.list("", null, 0, 10)
        assertEquals(listOf("Alice Director", "Bob Actor", "Carol Idle"), all.items.map { it.name })
        assertEquals(3, all.total)

        assertEquals(listOf("Bob Actor"), people.list("", Role.ACTOR, 0, 10).items.map { it.name })
        assertEquals(listOf("Alice Director"), people.list("ALICE", null, 0, 10).items.map { it.name })
        assertEquals(0, people.list("alice", Role.ACTOR, 0, 10).total)

        val page2 = people.list("", null, 1, 2)
        assertEquals(listOf("Carol Idle"), page2.items.map { it.name })
        assertEquals(3, page2.total)
    }

    @Test
    fun `invalid paging is rejected`() {
        assertThrows<ValidationException> { people.list("", null, -1, 10) }
        assertThrows<ValidationException> { people.list("", null, 0, 1000) }
    }
}

class SearchServiceTest : IntegrationTestBase() {

    @Autowired lateinit var search: SearchService

    @Autowired lateinit var people: PersonService

    @Autowired lateinit var movies: MovieService

    @Test
    fun `search returns matching movies and people`() {
        val nolan = people.create(PersonCommand("Christopher Nolan"))
        movies.create(MovieCommand("Nolan's Dream", "", 2010, 0, emptyList(), emptyList()))
        movies.create(MovieCommand("Interstellar", "", 2014, 0, emptyList(), listOf(CreditCommand(nolan.id, Role.DIRECTOR))))
        movies.create(MovieCommand("Unrelated", "", 1999, 0, emptyList(), emptyList()))

        val result = search.search("nolan", 0)

        assertEquals(listOf("Interstellar", "Nolan's Dream"), result.movies.map { it.title })
        assertEquals(listOf("Christopher Nolan"), result.people.map { it.name })
    }

    @Test
    fun `no match gives empty lists`() {
        val result = search.search("nothing", 5)
        assertTrue(result.movies.isEmpty())
        assertTrue(result.people.isEmpty())
    }

    @Test
    fun `blank query and bad limits are rejected`() {
        assertThrows<ValidationException> { search.search("   ", 5) }
        assertThrows<ValidationException> { search.search("x", -1) }
        assertThrows<ValidationException> { search.search("x", SearchService.MAX_SEARCH_LIMIT + 1) }
    }

    @Test
    fun `limit caps each result list`() {
        repeat(5) { movies.create(MovieCommand("Star $it", "", 2000, 0, emptyList(), emptyList())) }
        assertEquals(3, search.search("star", 3).movies.size)
    }
}
