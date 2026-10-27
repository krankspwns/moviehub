package com.moviehub.gateway.catalog

import java.io.InputStream

interface CatalogClient {
    fun listMovies(query: String, page: Int, pageSize: Int): PageDto<MovieDto>
    fun getMovie(id: String): MovieDto
    fun createMovie(input: MovieInputDto): MovieDto
    fun updateMovie(id: String, input: MovieInputDto): MovieDto
    fun deleteMovie(id: String): Boolean

    fun listPeople(query: String, role: Role?, page: Int, pageSize: Int): PageDto<PersonDto>
    fun getPerson(id: String): PersonDto
    fun createPerson(input: PersonInputDto): PersonDto
    fun updatePerson(id: String, input: PersonInputDto): PersonDto
    fun deletePerson(id: String): Boolean

    fun search(query: String, limit: Int): SearchResultDto

    fun uploadArtwork(movieId: String, contentType: String, content: InputStream): ArtworkInfoDto

    /** Eagerly fetches the first chunk, so a missing artwork fails before any HTTP bytes are sent. */
    fun downloadArtwork(movieId: String): ArtworkDownload

    fun uploadAvatar(personId: String, contentType: String, content: InputStream): ArtworkInfoDto

    fun downloadAvatar(personId: String): ArtworkDownload
}
