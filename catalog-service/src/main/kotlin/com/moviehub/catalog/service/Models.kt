package com.moviehub.catalog.service

import com.moviehub.catalog.domain.Role
import java.time.Instant
import java.time.LocalDate

// ---- Read models (built inside a transaction so callers never touch lazy entities) ----

data class CreditView(
    val personId: String,
    val personName: String,
    val role: Role,
    val characterName: String,
)

data class MovieView(
    val id: String,
    val title: String,
    val synopsis: String,
    val releaseYear: Int,
    val runtimeMinutes: Int,
    val genres: List<String>,
    val hasArtwork: Boolean,
    val artworkVersion: Long,
    val credits: List<CreditView>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class FilmographyEntry(
    val movieId: String,
    val title: String,
    val role: Role,
)

data class PersonView(
    val id: String,
    val name: String,
    val bio: String,
    val birthDate: LocalDate?,
    val filmography: List<FilmographyEntry>,
    val hasAvatar: Boolean = false,
    val avatarVersion: Long = 0,
)

data class PageView<T>(val items: List<T>, val total: Long)

data class SearchResult(val movies: List<MovieView>, val people: List<PersonView>)

data class ArtworkInfoView(
    val movieId: String,
    val contentType: String,
    val sizeBytes: Long,
    val version: Long,
)

// ---- Commands ----

data class CreditCommand(
    val personId: String,
    val role: Role,
    val characterName: String = "",
)

data class MovieCommand(
    val title: String,
    val synopsis: String = "",
    val releaseYear: Int,
    val runtimeMinutes: Int = 0,
    val genres: List<String> = emptyList(),
    val credits: List<CreditCommand> = emptyList(),
)

data class PersonCommand(
    val name: String,
    val bio: String = "",
    /** ISO-8601 date (yyyy-MM-dd) or blank when unknown. */
    val birthDate: String = "",
)

