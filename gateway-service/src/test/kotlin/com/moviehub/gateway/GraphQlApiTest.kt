package com.moviehub.gateway

import com.moviehub.gateway.catalog.CatalogClient
import com.moviehub.gateway.catalog.MovieInputDto
import com.moviehub.gateway.catalog.PageDto
import com.moviehub.gateway.catalog.Role
import com.moviehub.gateway.catalog.CatalogException
import com.moviehub.gateway.catalog.FailureKind
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
class GraphQlApiTest {

    @Autowired lateinit var tester: HttpGraphQlTester
    @MockitoBean lateinit var catalog: CatalogClient

    @Test
    fun `movies query uses schema defaults and maps the page`() {
        whenever(catalog.listMovies(any(), any(), any())).thenReturn(PageDto(listOf(Fixtures.movie(artwork = "/api/movies/m1/artwork?v=2")), 25, 0, 12))

        tester.document("{ movies { total totalPages page pageSize items { id title artworkUrl genres credits { personName role characterName } } } }")
            .execute()
            .path("movies.total").entity(Int::class.java).isEqualTo(25)
            .path("movies.totalPages").entity(Int::class.java).isEqualTo(3)
            .path("movies.items[0].artworkUrl").entity(String::class.java).isEqualTo("/api/movies/m1/artwork?v=2")
            .path("movies.items[0].credits[0].role").entity(String::class.java).isEqualTo("ACTOR")

        verify(catalog).listMovies(eq(""), eq(0), eq(12))
    }

    @Test
    fun `movies query forwards search text and paging`() {
        whenever(catalog.listMovies(any(), any(), any())).thenReturn(PageDto(emptyList(), 0, 2, 5))
        tester.document("""{ movies(query: "heat", page: 2, pageSize: 5) { items { id } totalPages } }""")
            .execute().path("movies.totalPages").entity(Int::class.java).isEqualTo(0)
        verify(catalog).listMovies("heat", 2, 5)
    }

    @Test
    fun `missing movie resolves to null instead of an error`() {
        whenever(catalog.getMovie("x")).thenThrow(CatalogException(FailureKind.NOT_FOUND, "gone"))
        tester.document("""{ movie(id: "x") { id } }""").execute().errors().verify().path("movie").valueIsNull()
    }

    @Test
    fun `createMovie maps the input including optional fields`() {
        whenever(catalog.createMovie(any())).thenReturn(Fixtures.movie(title = "New"))
        tester.document(
            """mutation { createMovie(input: {title: "New", releaseYear: 2020, genres: ["Drama"],
                credits: [{personId: "p1", role: DIRECTOR}]}) { title } }""",
        ).execute().path("createMovie.title").entity(String::class.java).isEqualTo("New")

        val captor = argumentCaptor<MovieInputDto>()
        verify(catalog).createMovie(captor.capture())
        val input = captor.firstValue
        assertEquals("New", input.title)
        assertEquals(2020, input.releaseYear)
        assertEquals(null, input.synopsis)
        assertEquals(listOf("Drama"), input.genres)
        assertEquals(Role.DIRECTOR, input.credits!!.single().role)
    }

    @Test
    fun `schema rejects missing required input fields`() {
        tester.document("""mutation { createMovie(input: {title: "x"}) { id } }""")
            .execute().errors().expect { true }.verify()
    }

    @Test
    fun `validation failures surface as BAD_REQUEST with a code extension`() {
        whenever(catalog.createMovie(any())).thenThrow(CatalogException(FailureKind.INVALID, "title must not be blank"))
        tester.document("""mutation { createMovie(input: {title: "", releaseYear: 2000}) { id } }""")
            .execute().errors().expect { it.message == "title must not be blank" && it.extensions["code"] == "INVALID" && it.errorType.toString() == "BAD_REQUEST" }
            .verify()
    }

    @Test
    fun `outage surfaces as INTERNAL_ERROR with UNAVAILABLE code`() {
        whenever(catalog.search(any(), any())).thenThrow(CatalogException(FailureKind.UNAVAILABLE, "Catalog service is unavailable"))
        tester.document("""{ search(query: "a") { movies { id } } }""")
            .execute().errors().expect { it.extensions["code"] == "UNAVAILABLE" }.verify()
    }

    @Test
    fun `people query supports role filter and person details`() {
        whenever(catalog.listPeople(any(), any(), any(), any())).thenReturn(PageDto(listOf(Fixtures.person()), 1, 0, 12))
        tester.document("{ people(role: ACTOR) { items { name birthDate filmography { title role } } } }")
            .execute().path("people.items[0].filmography[0].title").entity(String::class.java).isEqualTo("Heat")
        verify(catalog).listPeople("", Role.ACTOR, 0, 12)
    }

    @Test
    fun `delete mutations return the catalog result`() {
        whenever(catalog.deleteMovie("m1")).thenReturn(false)
        whenever(catalog.deletePerson("p1")).thenReturn(true)
        tester.document("""mutation { a: deleteMovie(id: "m1") b: deletePerson(id: "p1") }""").execute()
            .path("a").entity(Boolean::class.java).isEqualTo(false)
            .path("b").entity(Boolean::class.java).isEqualTo(true)
    }
}

