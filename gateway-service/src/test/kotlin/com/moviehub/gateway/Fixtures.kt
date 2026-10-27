package com.moviehub.gateway

import com.moviehub.gateway.catalog.ArtworkDownload
import com.moviehub.gateway.catalog.ArtworkInfoDto
import com.moviehub.gateway.catalog.CatalogClient
import com.moviehub.gateway.catalog.CatalogException
import com.moviehub.gateway.catalog.CreditDto
import com.moviehub.gateway.catalog.FailureKind
import com.moviehub.gateway.catalog.FilmographyEntryDto
import com.moviehub.gateway.catalog.MovieDto
import com.moviehub.gateway.catalog.MovieInputDto
import com.moviehub.gateway.catalog.PageDto
import com.moviehub.gateway.catalog.PersonDto
import com.moviehub.gateway.catalog.PersonInputDto
import com.moviehub.gateway.catalog.Role
import com.moviehub.gateway.catalog.SearchResultDto

object Fixtures {
    fun movie(id: String = "m1", title: String = "Heat", artwork: String? = null) = MovieDto(
        id = id,
        title = title,
        synopsis = "Cops and robbers",
        releaseYear = 1995,
        runtimeMinutes = 170,
        genres = listOf("Crime"),
        artworkUrl = artwork,
        credits = listOf(CreditDto("p1", "Al Pacino", Role.ACTOR, "Hanna")),
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-02T00:00:00Z",
    )

    fun person(id: String = "p1", name: String = "Al Pacino") = PersonDto(
        id = id,
        name = name,
        bio = "Actor",
        birthDate = "1940-04-25",
        filmography = listOf(FilmographyEntryDto("m1", "Heat", Role.ACTOR)),
    )
}

/** Hand-rolled fake so tests can also verify call arguments without Mockito's Kotlin pitfalls. */
open class FakeCatalogClient : CatalogClient {
    override fun listMovies(query: String, page: Int, pageSize: Int) = PageDto(listOf(Fixtures.movie()), 1, page, pageSize)
    override fun getMovie(id: String) = Fixtures.movie(id)
    override fun createMovie(input: MovieInputDto) = Fixtures.movie(title = input.title)
    override fun updateMovie(id: String, input: MovieInputDto) = Fixtures.movie(id, input.title)
    override fun deleteMovie(id: String) = true
    override fun listPeople(query: String, role: Role?, page: Int, pageSize: Int) =
        PageDto(listOf(Fixtures.person()), 1, page, pageSize)
    override fun getPerson(id: String) = Fixtures.person(id)
    override fun createPerson(input: PersonInputDto) = Fixtures.person(name = input.name)
    override fun updatePerson(id: String, input: PersonInputDto) = Fixtures.person(id, input.name)
    override fun deletePerson(id: String) = true
    override fun search(query: String, limit: Int) = SearchResultDto(listOf(Fixtures.movie()), listOf(Fixtures.person()))
    override fun uploadArtwork(movieId: String, contentType: String, content: java.io.InputStream) =
        ArtworkInfoDto(movieId, contentType, content.readAllBytes().size.toLong(), "/api/movies/$movieId/artwork?v=1")
    override fun downloadArtwork(movieId: String) = ArtworkDownload("image/png", listOf(byteArrayOf(1, 2, 3)).iterator())
}

fun notFound(message: String = "not found") = CatalogException(FailureKind.NOT_FOUND, message)
fun invalid(message: String = "bad input") = CatalogException(FailureKind.INVALID, message)

