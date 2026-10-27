package com.moviehub.gateway.graphql

import com.moviehub.gateway.catalog.CatalogClient
import com.moviehub.gateway.catalog.CatalogException
import com.moviehub.gateway.catalog.FailureKind
import com.moviehub.gateway.catalog.MovieDto
import com.moviehub.gateway.catalog.MovieInputDto
import com.moviehub.gateway.catalog.PageDto
import com.moviehub.gateway.catalog.PersonDto
import com.moviehub.gateway.catalog.PersonInputDto
import com.moviehub.gateway.catalog.Role
import com.moviehub.gateway.catalog.SearchResultDto
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller

/** GraphQL entry points. Pure delegation: validation and business rules live in the catalog service. */
@Controller
class CatalogGraphQlController(private val catalog: CatalogClient) {

    @QueryMapping
    fun movies(@Argument query: String?, @Argument page: Int, @Argument pageSize: Int): PageDto<MovieDto> =
        catalog.listMovies(query.orEmpty(), page, pageSize)

    @QueryMapping
    fun movie(@Argument id: String): MovieDto? = nullIfMissing { catalog.getMovie(id) }

    @QueryMapping
    fun people(
        @Argument query: String?,
        @Argument role: Role?,
        @Argument page: Int,
        @Argument pageSize: Int,
    ): PageDto<PersonDto> = catalog.listPeople(query.orEmpty(), role, page, pageSize)

    @QueryMapping
    fun person(@Argument id: String): PersonDto? = nullIfMissing { catalog.getPerson(id) }

    @QueryMapping
    fun search(@Argument query: String, @Argument limit: Int): SearchResultDto = catalog.search(query, limit)

    @MutationMapping
    fun createMovie(@Argument input: MovieInputDto): MovieDto = catalog.createMovie(input)

    @MutationMapping
    fun updateMovie(@Argument id: String, @Argument input: MovieInputDto): MovieDto = catalog.updateMovie(id, input)

    @MutationMapping
    fun deleteMovie(@Argument id: String): Boolean = catalog.deleteMovie(id)

    @MutationMapping
    fun createPerson(@Argument input: PersonInputDto): PersonDto = catalog.createPerson(input)

    @MutationMapping
    fun updatePerson(@Argument id: String, @Argument input: PersonInputDto): PersonDto = catalog.updatePerson(id, input)

    @MutationMapping
    fun deletePerson(@Argument id: String): Boolean = catalog.deletePerson(id)

    private inline fun <T> nullIfMissing(block: () -> T): T? = try {
        block()
    } catch (e: CatalogException) {
        if (e.kind == FailureKind.NOT_FOUND) null else throw e
    }
}

/**
 * Turns catalog failures into well-typed GraphQL errors. The `code` extension lets the UI
 * distinguish validation problems (show the message) from outages (show a retry hint).
 */
@Component
class CatalogExceptionResolver : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        if (ex !is CatalogException) return null
        val type = when (ex.kind) {
            FailureKind.NOT_FOUND -> ErrorType.NOT_FOUND
            FailureKind.INVALID -> ErrorType.BAD_REQUEST
            FailureKind.UNAVAILABLE, FailureKind.INTERNAL -> ErrorType.INTERNAL_ERROR
        }
        return GraphqlErrorBuilder.newError(env)
            .errorType(type)
            .message(ex.message)
            .extensions(mapOf("code" to ex.kind.name))
            .build()
    }
}

