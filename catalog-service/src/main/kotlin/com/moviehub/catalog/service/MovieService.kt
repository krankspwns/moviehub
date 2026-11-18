package com.moviehub.catalog.service

import com.moviehub.catalog.domain.CreditEntity
import com.moviehub.catalog.domain.MovieEntity
import com.moviehub.catalog.domain.MovieRepository
import com.moviehub.catalog.domain.NotFoundException
import com.moviehub.catalog.domain.PersonRepository
import com.moviehub.catalog.domain.ValidationException
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * Persistence and database-backed rules only. Request validation happens before the call,
 * in [com.moviehub.catalog.grpc.RequestValidator].
 */
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
        val clean = command
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
        val clean = command
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

    private fun find(id: String): MovieEntity = movies.findById(id).orElseThrow { NotFoundException("Movie $id not found") }

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
}
