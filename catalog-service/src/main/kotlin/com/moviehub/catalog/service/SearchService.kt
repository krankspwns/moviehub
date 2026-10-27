package com.moviehub.catalog.service

import com.moviehub.catalog.domain.MovieRepository
import com.moviehub.catalog.domain.PersonRepository
import com.moviehub.catalog.domain.ValidationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchService(
    private val movies: MovieRepository,
    private val people: PersonRepository,
) {

    /**
     * Case-insensitive substring search across movies (title, synopsis, genre, credited
     * person) and people (name). A blank query is rejected: clients should use the list
     * operations to browse.
     */
    @Transactional(readOnly = true)
    fun search(query: String, limit: Int): SearchResult {
        if (query.isBlank()) throw ValidationException("query must not be blank")
        if (limit < 0 || limit > MAX_SEARCH_LIMIT) {
            throw ValidationException("limit must be between 1 and $MAX_SEARCH_LIMIT")
        }
        val effectiveLimit = if (limit == 0) DEFAULT_SEARCH_LIMIT else limit
        val pattern = likePattern(query)
        return SearchResult(
            movies = movies.search(pattern, PageRequest.of(0, effectiveLimit, Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id"))))
                .content.map { it.toView() },
            people = people.search(pattern, PageRequest.of(0, effectiveLimit, Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id"))))
                .content.map { it.toView() },
        )
    }

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 10
        const val MAX_SEARCH_LIMIT = 50
    }
}

