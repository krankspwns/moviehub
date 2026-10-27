package com.moviehub.catalog.service

import com.moviehub.catalog.domain.CreditEntity
import com.moviehub.catalog.domain.MovieEntity
import com.moviehub.catalog.domain.MovieRepository
import com.moviehub.catalog.domain.NotFoundException
import com.moviehub.catalog.domain.PersonRepository
import com.moviehub.catalog.domain.Role
import com.moviehub.catalog.domain.ValidationException
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class MovieService(
    private val movies: MovieRepository,
    private val people: PersonRepository,
    private val artworkStorage: ArtworkStorage,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun list(query: String, page: Int, size: Int): PageView<MovieView> {
        val pageable = pageRequest(page, size, Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id")))
        val result = movies.search(likePattern(query), pageable)
        return PageView(result.content.map { it.toView() }, result.totalElements)
    }

    @Transactional(readOnly = true)
    fun get(id: String): MovieView = find(id).toView()

    @Transactional
    fun create(command: MovieCommand): MovieView {
        val clean = validate(command)
        val now = Instant.now(clock)
        val movie = MovieEntity(
            title = clean.title,
            synopsis = clean.synopsis,
            releaseYear = clean.releaseYear,
            runtimeMinutes = clean.runtimeMinutes,
            genres = clean.genres.toMutableList(),
            createdAt = now,
            updatedAt = now,
        )
        applyCredits(movie, clean.credits)
        return movies.save(movie).toView()
    }

    @Transactional
    fun update(id: String, command: MovieCommand): MovieView {
        val movie = find(id)
        val clean = validate(command)
        movie.title = clean.title
        movie.synopsis = clean.synopsis
        movie.releaseYear = clean.releaseYear
        movie.runtimeMinutes = clean.runtimeMinutes
        movie.genres.clear()
        movie.genres.addAll(clean.genres)
        movie.updatedAt = Instant.now(clock)
        applyCredits(movie, clean.credits)
        return movies.saveAndFlush(movie).toView()
    }

    @Transactional
    fun delete(id: String): Boolean {
        val movie = movies.findById(id).orElse(null) ?: return false
        movies.delete(movie)
        movies.flush()
        artworkStorage.delete(id)
        return true
    }

    private fun find(id: String): MovieEntity =
        movies.findById(id).orElseThrow { NotFoundException("Movie $id not found") }

    /**
     * Diff-based credit synchronisation. Replacing the collection wholesale would make
     * Hibernate insert before it deletes and trip the (movie, person, role) unique key.
     */
    private fun applyCredits(movie: MovieEntity, commands: List<CreditCommand>) {
        val wanted = commands.associateBy { it.personId to it.role }
        movie.credits.removeIf { (it.person!!.id to it.role) !in wanted }
        val existing = movie.credits.associateBy { it.person!!.id to it.role }
        for ((key, cmd) in wanted) {
            val current = existing[key]
            if (current != null) {
                current.characterName = cmd.characterName
            } else {
                val person = people.findById(cmd.personId)
                    .orElseThrow { ValidationException("Person ${cmd.personId} does not exist") }
                movie.credits.add(
                    CreditEntity(movie = movie, person = person, role = cmd.role, characterName = cmd.characterName),
                )
            }
        }
    }

    private fun validate(command: MovieCommand): MovieCommand {
        val title = command.title.trim()
        if (title.isEmpty()) throw ValidationException("title must not be blank")
        if (title.length > 200) throw ValidationException("title must be at most 200 characters")

        val synopsis = command.synopsis.trim()
        if (synopsis.length > 4000) throw ValidationException("synopsis must be at most 4000 characters")

        val maxYear = LocalDate.now(clock).year + 10
        if (command.releaseYear !in 1888..maxYear) {
            throw ValidationException("releaseYear must be between 1888 and $maxYear")
        }
        if (command.runtimeMinutes !in 0..1000) {
            throw ValidationException("runtimeMinutes must be between 0 and 1000 (0 = unknown)")
        }

        val genres = command.genres.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (genres.size > 10) throw ValidationException("a movie can have at most 10 genres")
        if (genres.any { it.length > 50 }) throw ValidationException("a genre must be at most 50 characters")

        val credits = command.credits.map { credit ->
            if (credit.personId.isBlank()) throw ValidationException("credit personId must not be blank")
            val character = credit.characterName.trim()
            if (character.length > 200) throw ValidationException("characterName must be at most 200 characters")
            if (credit.role != Role.ACTOR && character.isNotEmpty()) {
                throw ValidationException("characterName is only allowed for the ACTOR role")
            }
            credit.copy(characterName = character)
        }
        if (credits.distinctBy { it.personId to it.role }.size != credits.size) {
            throw ValidationException("duplicate credit (same person and role)")
        }

        return command.copy(title = title, synopsis = synopsis, genres = genres, credits = credits)
    }
}

