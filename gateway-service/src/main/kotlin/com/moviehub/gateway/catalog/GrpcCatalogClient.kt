package com.moviehub.gateway.catalog

import com.google.protobuf.ByteString
import com.moviehub.catalog.grpc.ArtworkChunk
import com.moviehub.catalog.grpc.ArtworkInfo
import com.moviehub.catalog.grpc.ArtworkKind
import com.moviehub.catalog.grpc.ArtworkMetadata
import com.moviehub.catalog.grpc.CatalogServiceGrpc.CatalogServiceBlockingStub
import com.moviehub.catalog.grpc.CatalogServiceGrpc.CatalogServiceStub
import com.moviehub.catalog.grpc.CreditInput
import com.moviehub.catalog.grpc.GetByIdRequest
import com.moviehub.catalog.grpc.ListMoviesRequest
import com.moviehub.catalog.grpc.ListPeopleRequest
import com.moviehub.catalog.grpc.MovieInput
import com.moviehub.catalog.grpc.PersonInput
import com.moviehub.catalog.grpc.SearchRequest
import com.moviehub.catalog.grpc.UpdateMovieRequest
import com.moviehub.catalog.grpc.UpdatePersonRequest
import com.moviehub.catalog.grpc.UploadArtworkRequest
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import com.moviehub.catalog.grpc.Credit as ProtoCredit
import com.moviehub.catalog.grpc.Movie as ProtoMovie
import com.moviehub.catalog.grpc.Person as ProtoPerson
import com.moviehub.catalog.grpc.Role as ProtoRole

private const val UPLOAD_CHUNK_SIZE = 64 * 1024

/** gRPC implementation of [CatalogClient]. All gRPC specifics (stubs, deadlines, status codes) stay in here. */
@Component
class GrpcCatalogClient(
    private val blocking: CatalogServiceBlockingStub,
    private val async: CatalogServiceStub,
    private val properties: CatalogProperties,
) : CatalogClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun stub() = blocking.withDeadlineAfter(properties.callTimeout.toMillis(), TimeUnit.MILLISECONDS)

    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: StatusRuntimeException) {
        throw e.toCatalogException()
    }

    // ---- movies ----

    override fun listMovies(query: String, page: Int, pageSize: Int): PageDto<MovieDto> = call {
        val response = stub().listMovies(
            ListMoviesRequest.newBuilder().setQuery(query).setPage(page).setPageSize(pageSize).build(),
        )
        PageDto(response.moviesList.map { it.toDto() }, response.total.toInt(), page, effectivePageSize(pageSize))
    }

    override fun getMovie(id: String): MovieDto = call { stub().getMovie(idOf(id)).toDto() }

    override fun createMovie(input: MovieInputDto): MovieDto = call { stub().createMovie(input.toProto()).toDto() }

    override fun updateMovie(id: String, input: MovieInputDto): MovieDto = call {
        stub().updateMovie(UpdateMovieRequest.newBuilder().setId(id).setMovie(input.toProto()).build()).toDto()
    }

    override fun deleteMovie(id: String): Boolean = call { stub().deleteMovie(idOf(id)).deleted }

    // ---- people ----

    override fun listPeople(query: String, role: Role?, page: Int, pageSize: Int): PageDto<PersonDto> = call {
        val response = stub().listPeople(
            ListPeopleRequest.newBuilder().setQuery(query).setPage(page).setPageSize(pageSize)
                .setRole(role?.toProto() ?: ProtoRole.ROLE_UNSPECIFIED).build(),
        )
        PageDto(response.peopleList.map { it.toDto() }, response.total.toInt(), page, effectivePageSize(pageSize))
    }

    override fun getPerson(id: String): PersonDto = call { stub().getPerson(idOf(id)).toDto() }

    override fun createPerson(input: PersonInputDto): PersonDto = call { stub().createPerson(input.toProto()).toDto() }

    override fun updatePerson(id: String, input: PersonInputDto): PersonDto = call {
        stub().updatePerson(UpdatePersonRequest.newBuilder().setId(id).setPerson(input.toProto()).build()).toDto()
    }

    override fun deletePerson(id: String): Boolean = call { stub().deletePerson(idOf(id)).deleted }

    // ---- search ----

    override fun search(query: String, limit: Int): SearchResultDto = call {
        val response = stub().search(SearchRequest.newBuilder().setQuery(query).setLimit(limit).build())
        SearchResultDto(response.moviesList.map { it.toDto() }, response.peopleList.map { it.toDto() })
    }

    // ---- artwork ----

    override fun uploadArtwork(movieId: String, contentType: String, content: InputStream): ArtworkInfoDto =
        upload(ArtworkKind.ARTWORK_KIND_MOVIE, movieId, contentType, content)

    override fun uploadAvatar(personId: String, contentType: String, content: InputStream): ArtworkInfoDto =
        upload(ArtworkKind.ARTWORK_KIND_PERSON, personId, contentType, content)

    private fun upload(kind: ArtworkKind, ownerId: String, contentType: String, content: InputStream): ArtworkInfoDto {
        val result = CompletableFuture<ArtworkInfo>()
        val requests = async.uploadArtwork(object : StreamObserver<ArtworkInfo> {
            private var last: ArtworkInfo? = null
            override fun onNext(value: ArtworkInfo) {
                last = value
            }
            override fun onError(t: Throwable) {
                result.completeExceptionally(t)
            }
            override fun onCompleted() {
                last?.let(result::complete) ?: result.completeExceptionally(IllegalStateException("no response"))
            }
        })
        try {
            requests.onNext(
                UploadArtworkRequest.newBuilder()
                    .setMetadata(ArtworkMetadata.newBuilder().setMovieId(ownerId).setContentType(contentType).setKind(kind))
                    .build(),
            )
            val buffer = ByteArray(UPLOAD_CHUNK_SIZE)
            while (!result.isDone) { // the server may reject early (e.g. too large); stop sending then
                val read = content.read(buffer)
                if (read < 0) break
                requests.onNext(UploadArtworkRequest.newBuilder().setChunk(ByteString.copyFrom(buffer, 0, read)).build())
            }
            if (!result.isDone) requests.onCompleted()
        } catch (e: Exception) {
            requests.onError(e)
            throw CatalogException(FailureKind.INTERNAL, "Failed to stream artwork", e)
        }
        val info = try {
            result.get(properties.uploadTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw (e.cause ?: e).let {
                if (it is StatusRuntimeException) it.toCatalogException() else CatalogException(FailureKind.INTERNAL, "Upload failed", it)
            }
        } catch (e: TimeoutException) {
            throw CatalogException(FailureKind.UNAVAILABLE, "Artwork upload timed out", e)
        }
        val url = if (kind ==
            ArtworkKind.ARTWORK_KIND_PERSON
        ) {
            avatarUrl(info.movieId, info.artworkVersion)
        } else {
            artworkUrl(info.movieId, info.artworkVersion)
        }
        return ArtworkInfoDto(info.movieId, info.contentType, info.sizeBytes, url)
    }

    override fun downloadArtwork(movieId: String): ArtworkDownload = call {
        download(movieId) {
            blocking.withDeadlineAfter(properties.uploadTimeout.toMillis(), TimeUnit.MILLISECONDS).downloadArtwork(idOf(movieId))
        }
    }

    override fun downloadAvatar(personId: String): ArtworkDownload = call {
        download(personId) {
            blocking.withDeadlineAfter(properties.uploadTimeout.toMillis(), TimeUnit.MILLISECONDS).downloadAvatar(idOf(personId))
        }
    }

    private fun download(id: String, open: () -> Iterator<ArtworkChunk>): ArtworkDownload {
        val stream = open()
        val first = stream.next() // throws NOT_FOUND here, before the caller commits any HTTP response
        val contentType = first.contentType
        val chunks = object : Iterator<ByteArray> {
            private var pending: ByteArray? = first.data.toByteArray()
            override fun hasNext(): Boolean = pending != null ||
                try {
                    stream.hasNext()
                } catch (e: StatusRuntimeException) {
                    log.warn("Image stream for {} failed mid-way: {}", id, e.status)
                    throw e.toCatalogException()
                }

            override fun next(): ByteArray {
                pending?.let {
                    pending = null
                    return it
                }
                return stream.next().data.toByteArray()
            }
        }
        return ArtworkDownload(contentType, chunks)
    }

    // ---- mapping ----

    private fun idOf(id: String) = GetByIdRequest.newBuilder().setId(id).build()

    private fun effectivePageSize(requested: Int) = if (requested == 0) DEFAULT_PAGE_SIZE else requested

    private fun StatusRuntimeException.toCatalogException(): CatalogException = when (status.code) {
        Status.Code.NOT_FOUND -> CatalogException(FailureKind.NOT_FOUND, status.description ?: "Not found", this)
        Status.Code.INVALID_ARGUMENT -> CatalogException(FailureKind.INVALID, status.description ?: "Invalid request", this)
        Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED ->
            CatalogException(FailureKind.UNAVAILABLE, "Catalog service is unavailable", this)
        else -> {
            log.error("Unexpected gRPC failure: {}", status, this)
            CatalogException(FailureKind.INTERNAL, "Unexpected error", this)
        }
    }

    private fun ProtoMovie.toDto() = MovieDto(
        id = id,
        title = title,
        synopsis = synopsis,
        releaseYear = releaseYear,
        runtimeMinutes = runtimeMinutes,
        genres = genresList.toList(),
        artworkUrl = if (hasArtwork) artworkUrl(id, artworkVersion) else null,
        credits = creditsList.map { it.toDto() },
        createdAt = Instant.ofEpochMilli(createdAtEpochMs).toString(),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs).toString(),
    )

    private fun ProtoCredit.toDto() = CreditDto(personId, personName, role.toDomain(), characterName)

    private fun ProtoPerson.toDto() = PersonDto(
        id = id,
        name = name,
        bio = bio,
        birthDate = birthDate.ifEmpty { null },
        filmography = filmographyList.map { FilmographyEntryDto(it.movieId, it.title, it.role.toDomain()) },
        avatarUrl = if (hasAvatar) avatarUrl(id, avatarVersion) else null,
    )

    private fun MovieInputDto.toProto(): MovieInput = MovieInput.newBuilder()
        .setTitle(title)
        .setSynopsis(synopsis.orEmpty())
        .setReleaseYear(releaseYear)
        .setRuntimeMinutes(runtimeMinutes ?: 0)
        .addAllGenres(genres.orEmpty())
        .addAllCredits(
            credits.orEmpty().map {
                CreditInput.newBuilder().setPersonId(it.personId).setRole(it.role.toProto())
                    .setCharacterName(it.characterName.orEmpty()).build()
            },
        )
        .build()

    private fun PersonInputDto.toProto(): PersonInput =
        PersonInput.newBuilder().setName(name).setBio(bio.orEmpty()).setBirthDate(birthDate.orEmpty()).build()

    private fun Role.toProto(): ProtoRole = when (this) {
        Role.ACTOR -> ProtoRole.ROLE_ACTOR
        Role.DIRECTOR -> ProtoRole.ROLE_DIRECTOR
        Role.WRITER -> ProtoRole.ROLE_WRITER
        Role.PRODUCER -> ProtoRole.ROLE_PRODUCER
        Role.COMPOSER -> ProtoRole.ROLE_COMPOSER
    }

    private fun ProtoRole.toDomain(): Role = when (this) {
        ProtoRole.ROLE_ACTOR -> Role.ACTOR
        ProtoRole.ROLE_DIRECTOR -> Role.DIRECTOR
        ProtoRole.ROLE_WRITER -> Role.WRITER
        ProtoRole.ROLE_PRODUCER -> Role.PRODUCER
        ProtoRole.ROLE_COMPOSER -> Role.COMPOSER
        ProtoRole.ROLE_UNSPECIFIED, ProtoRole.UNRECOGNIZED ->
            throw CatalogException(FailureKind.INTERNAL, "Catalog returned an unknown role")
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
