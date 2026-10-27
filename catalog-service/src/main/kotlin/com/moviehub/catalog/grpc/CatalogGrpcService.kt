package com.moviehub.catalog.grpc

import com.google.protobuf.ByteString
import com.moviehub.catalog.domain.ValidationException
import com.moviehub.catalog.service.ArtworkService
import com.moviehub.catalog.service.ArtworkUpload
import com.moviehub.catalog.service.ImageOwner
import com.moviehub.catalog.service.MovieService
import com.moviehub.catalog.service.PersonService
import com.moviehub.catalog.service.SearchService
import com.moviehub.catalog.service.StoredArtwork
import io.grpc.stub.StreamObserver
import net.devh.boot.grpc.server.service.GrpcService

private const val DOWNLOAD_CHUNK_SIZE = 64 * 1024

/**
 * Thin transport adapter: maps protobuf <-> domain types and delegates to the services.
 * Exceptions thrown from unary/server-streaming calls are translated by [GrpcExceptionAdvice].
 */
@GrpcService
class CatalogGrpcService(
    private val movies: MovieService,
    private val people: PersonService,
    private val search: SearchService,
    private val artwork: ArtworkService,
) : CatalogServiceGrpc.CatalogServiceImplBase() {

    // ---- Movies ----

    override fun listMovies(request: ListMoviesRequest, observer: StreamObserver<ListMoviesResponse>) = observer.reply {
        val page = movies.list(request.query, request.page, request.pageSize)
        ListMoviesResponse.newBuilder()
            .addAllMovies(page.items.map { it.toProto() })
            .setTotal(page.total)
            .build()
    }

    override fun getMovie(request: GetByIdRequest, observer: StreamObserver<Movie>) = observer.reply { movies.get(request.id).toProto() }

    override fun createMovie(request: MovieInput, observer: StreamObserver<Movie>) =
        observer.reply { movies.create(request.toCommand()).toProto() }

    override fun updateMovie(request: UpdateMovieRequest, observer: StreamObserver<Movie>) =
        observer.reply { movies.update(request.id, request.movie.toCommand()).toProto() }

    override fun deleteMovie(request: GetByIdRequest, observer: StreamObserver<DeleteResponse>) =
        observer.reply { DeleteResponse.newBuilder().setDeleted(movies.delete(request.id)).build() }

    // ---- People ----

    override fun listPeople(request: ListPeopleRequest, observer: StreamObserver<ListPeopleResponse>) = observer.reply {
        val page = people.list(request.query, request.role.toDomainOrNull(), request.page, request.pageSize)
        ListPeopleResponse.newBuilder()
            .addAllPeople(page.items.map { it.toProto() })
            .setTotal(page.total)
            .build()
    }

    override fun getPerson(request: GetByIdRequest, observer: StreamObserver<Person>) = observer.reply { people.get(request.id).toProto() }

    override fun createPerson(request: PersonInput, observer: StreamObserver<Person>) =
        observer.reply { people.create(request.toCommand()).toProto() }

    override fun updatePerson(request: UpdatePersonRequest, observer: StreamObserver<Person>) =
        observer.reply { people.update(request.id, request.person.toCommand()).toProto() }

    override fun deletePerson(request: GetByIdRequest, observer: StreamObserver<DeleteResponse>) =
        observer.reply { DeleteResponse.newBuilder().setDeleted(people.delete(request.id)).build() }

    // ---- Search ----

    override fun search(request: SearchRequest, observer: StreamObserver<SearchResponse>) = observer.reply {
        val result = search.search(request.query, request.limit)
        SearchResponse.newBuilder()
            .addAllMovies(result.movies.map { it.toProto() })
            .addAllPeople(result.people.map { it.toProto() })
            .build()
    }

    // ---- Artwork ----

    /**
     * Client-streaming upload: the first message carries metadata, the rest carry bytes.
     * Errors are reported through the observer directly because the advice only wraps
     * the initial call, not the returned request observer.
     */
    override fun uploadArtwork(responseObserver: StreamObserver<ArtworkInfo>): StreamObserver<UploadArtworkRequest> =
        object : StreamObserver<UploadArtworkRequest> {
            private var upload: ArtworkUpload? = null
            private var failed = false

            override fun onNext(message: UploadArtworkRequest) {
                if (failed) return
                try {
                    when (message.payloadCase) {
                        UploadArtworkRequest.PayloadCase.METADATA -> {
                            if (upload != null) throw ValidationException("metadata must be sent exactly once")
                            val owner = if (message.metadata.kind ==
                                ArtworkKind.ARTWORK_KIND_PERSON
                            ) {
                                ImageOwner.PERSON
                            } else {
                                ImageOwner.MOVIE
                            }
                            upload = artwork.begin(owner, message.metadata.movieId, message.metadata.contentType)
                        }
                        UploadArtworkRequest.PayloadCase.CHUNK -> {
                            val current = upload ?: throw ValidationException("metadata must be the first message")
                            current.write(message.chunk.toByteArray())
                        }
                        else -> throw ValidationException("empty upload message")
                    }
                } catch (e: Exception) {
                    fail(e)
                }
            }

            override fun onError(t: Throwable) {
                failed = true
                upload?.let(artwork::abort)
            }

            override fun onCompleted() {
                if (failed) return
                try {
                    val current = upload ?: throw ValidationException("no artwork metadata received")
                    val info = artwork.complete(current)
                    responseObserver.onNext(
                        ArtworkInfo.newBuilder()
                            .setMovieId(info.movieId)
                            .setContentType(info.contentType)
                            .setSizeBytes(info.sizeBytes)
                            .setArtworkVersion(info.version)
                            .build(),
                    )
                    responseObserver.onCompleted()
                } catch (e: Exception) {
                    fail(e)
                }
            }

            private fun fail(e: Exception) {
                failed = true
                upload?.let(artwork::abort)
                responseObserver.onError(e.toStatus().asRuntimeException())
            }
        }

    override fun downloadArtwork(request: GetByIdRequest, observer: StreamObserver<ArtworkChunk>) =
        stream(artwork.open(ImageOwner.MOVIE, request.id), observer)

    override fun downloadAvatar(request: GetByIdRequest, observer: StreamObserver<ArtworkChunk>) =
        stream(artwork.open(ImageOwner.PERSON, request.id), observer)

    private fun stream(stored: StoredArtwork, observer: StreamObserver<ArtworkChunk>) {
        stored.stream.use { input ->
            var first = true
            val buffer = ByteArray(DOWNLOAD_CHUNK_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                val chunk = ArtworkChunk.newBuilder().setData(ByteString.copyFrom(buffer, 0, read))
                if (first) {
                    chunk.setContentType(stored.contentType)
                    first = false
                }
                observer.onNext(chunk.build())
            }
        }
        observer.onCompleted()
    }

    /** Runs [block] and completes the observer; failures propagate to the exception advice. */
    private inline fun <T> StreamObserver<T>.reply(block: () -> T) {
        onNext(block())
        onCompleted()
    }
}
