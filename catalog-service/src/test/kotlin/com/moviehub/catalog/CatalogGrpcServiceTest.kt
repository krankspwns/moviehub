package com.moviehub.catalog

import com.google.protobuf.ByteString
import com.moviehub.catalog.grpc.ArtworkInfo
import com.moviehub.catalog.grpc.ArtworkMetadata
import com.moviehub.catalog.grpc.CatalogServiceGrpc
import com.moviehub.catalog.grpc.CreditInput
import com.moviehub.catalog.grpc.GetByIdRequest
import com.moviehub.catalog.grpc.ListMoviesRequest
import com.moviehub.catalog.grpc.ListPeopleRequest
import com.moviehub.catalog.grpc.MovieInput
import com.moviehub.catalog.grpc.PersonInput
import com.moviehub.catalog.grpc.Role
import com.moviehub.catalog.grpc.SearchRequest
import com.moviehub.catalog.grpc.UpdateMovieRequest
import com.moviehub.catalog.grpc.UpdatePersonRequest
import com.moviehub.catalog.grpc.UploadArtworkRequest
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the real gRPC server (in-process transport): mapping, status codes and streaming. */
class CatalogGrpcServiceTest : IntegrationTestBase() {

    private lateinit var channel: ManagedChannel
    private lateinit var stub: CatalogServiceGrpc.CatalogServiceBlockingStub
    private lateinit var asyncStub: CatalogServiceGrpc.CatalogServiceStub

    @BeforeEach
    fun connect() {
        channel = InProcessChannelBuilder.forName("catalog-test").directExecutor().build()
        stub = CatalogServiceGrpc.newBlockingStub(channel)
        asyncStub = CatalogServiceGrpc.newStub(channel)
    }

    @AfterEach
    fun disconnect() {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun movieInput(title: String = "Heat", vararg credits: CreditInput) = MovieInput.newBuilder()
        .setTitle(title).setSynopsis("Cops and robbers").setReleaseYear(1995).setRuntimeMinutes(170)
        .addGenres("Crime").addAllCredits(credits.toList()).build()

    private fun personInput(name: String) = PersonInput.newBuilder().setName(name).setBirthDate("1943-04-25").build()

    private fun id(value: String) = GetByIdRequest.newBuilder().setId(value).build()

    private fun status(block: () -> Unit): Status.Code = assertThrows<StatusRuntimeException> { block() }.status.code

    @Test
    fun `movie crud round trip`() {
        val pacino = stub.createPerson(personInput("Al Pacino"))
        val created = stub.createMovie(
            movieInput(
                "Heat",
                CreditInput.newBuilder().setPersonId(pacino.id).setRole(Role.ROLE_ACTOR).setCharacterName("Hanna").build(),
            ),
        )
        assertEquals("Heat", created.title)
        assertEquals("Al Pacino", created.creditsList.single().personName)
        assertEquals(Role.ROLE_ACTOR, created.creditsList.single().role)

        val updated = stub.updateMovie(
            UpdateMovieRequest.newBuilder().setId(created.id).setMovie(movieInput("Heat (1995)")).build(),
        )
        assertEquals("Heat (1995)", updated.title)
        assertEquals(0, updated.creditsCount)

        val list = stub.listMovies(ListMoviesRequest.newBuilder().setQuery("heat").setPageSize(5).build())
        assertEquals(1, list.total)

        assertTrue(stub.deleteMovie(id(created.id)).deleted)
        assertEquals(Status.Code.NOT_FOUND, status { stub.getMovie(id(created.id)) })
    }

    @Test
    fun `person crud and role filter`() {
        val person = stub.createPerson(personInput("Michael Mann"))
        stub.createMovie(
            movieInput("Heat", CreditInput.newBuilder().setPersonId(person.id).setRole(Role.ROLE_DIRECTOR).build()),
        )

        val updated = stub.updatePerson(
            UpdatePersonRequest.newBuilder().setId(person.id).setPerson(personInput("Michael K. Mann")).build(),
        )
        assertEquals("Michael K. Mann", updated.name)
        assertEquals("1943-04-25", updated.birthDate)

        val directors = stub.listPeople(ListPeopleRequest.newBuilder().setRole(Role.ROLE_DIRECTOR).build())
        assertEquals(1, directors.total)
        assertEquals("Heat", directors.getPeople(0).getFilmography(0).title)
        assertEquals(0, stub.listPeople(ListPeopleRequest.newBuilder().setRole(Role.ROLE_ACTOR).build()).total)

        assertTrue(stub.deletePerson(id(person.id)).deleted)
        assertEquals(Status.Code.NOT_FOUND, status { stub.getPerson(id(person.id)) })
    }

    @Test
    fun `domain errors become proper gRPC status codes`() {
        assertEquals(Status.Code.NOT_FOUND, status { stub.getMovie(id("missing")) })
        assertEquals(Status.Code.NOT_FOUND, status { stub.getPerson(id("missing")) })
        assertEquals(Status.Code.INVALID_ARGUMENT, status { stub.createMovie(movieInput("")) })
        assertEquals(Status.Code.INVALID_ARGUMENT, status { stub.createPerson(personInput("")) })
        assertEquals(
            Status.Code.INVALID_ARGUMENT,
            status { stub.listMovies(ListMoviesRequest.newBuilder().setPage(-1).build()) },
        )
        assertEquals(Status.Code.INVALID_ARGUMENT, status { stub.search(SearchRequest.newBuilder().setQuery(" ").build()) })
    }

    @Test
    fun `credit with unspecified role is rejected`() {
        val p = stub.createPerson(personInput("X"))
        val code = status { stub.createMovie(movieInput("T", CreditInput.newBuilder().setPersonId(p.id).build())) }
        assertEquals(Status.Code.INVALID_ARGUMENT, code)
    }

    @Test
    fun `deleting something that does not exist reports deleted=false`() {
        assertEquals(false, stub.deleteMovie(id("nope")).deleted)
        assertEquals(false, stub.deletePerson(id("nope")).deleted)
    }

    @Test
    fun `search spans movies and people`() {
        stub.createPerson(personInput("Heat Person"))
        stub.createMovie(movieInput("Heat"))
        val result = stub.search(SearchRequest.newBuilder().setQuery("heat").build())
        assertEquals(1, result.moviesCount)
        assertEquals(1, result.peopleCount)
    }

    // ---- artwork streaming ----

    private class Collector<T> : StreamObserver<T> {
        val result = CompletableFuture<T?>()
        private var last: T? = null
        override fun onNext(value: T) {
            last = value
        }
        override fun onError(t: Throwable) {
            result.completeExceptionally(t)
        }
        override fun onCompleted() {
            result.complete(last)
        }
    }

    private fun upload(movieId: String, contentType: String, chunks: List<ByteArray>): ArtworkInfo? {
        val collector = Collector<ArtworkInfo>()
        val request = asyncStub.uploadArtwork(collector)
        request.onNext(
            UploadArtworkRequest.newBuilder()
                .setMetadata(ArtworkMetadata.newBuilder().setMovieId(movieId).setContentType(contentType)).build(),
        )
        chunks.forEach { request.onNext(UploadArtworkRequest.newBuilder().setChunk(ByteString.copyFrom(it)).build()) }
        request.onCompleted()
        return collector.result.get(5, TimeUnit.SECONDS)
    }

    private fun uploadStatus(block: () -> Unit): Status.Code {
        val e = assertThrows<java.util.concurrent.ExecutionException> { block() }
        return Status.fromThrowable(e.cause).code
    }

    @Test
    fun `artwork upload and download round trip`() {
        val movie = stub.createMovie(movieInput())
        val info = upload(movie.id, "image/png", listOf(Images.PNG.copyOfRange(0, 4), Images.PNG.copyOfRange(4, Images.PNG.size)))

        assertEquals(Images.PNG.size.toLong(), info!!.sizeBytes)
        assertEquals(1, info.artworkVersion)
        assertTrue(stub.getMovie(id(movie.id)).hasArtwork)

        val chunks = stub.downloadArtwork(id(movie.id)).asSequence().toList()
        assertEquals("image/png", chunks.first().contentType)
        assertContentEquals(Images.PNG, chunks.fold(ByteArray(0)) { acc, c -> acc + c.data.toByteArray() })
    }

    @Test
    fun `artwork upload errors map to status codes`() {
        val movie = stub.createMovie(movieInput())
        assertEquals(Status.Code.NOT_FOUND, uploadStatus { upload("missing", "image/png", listOf(Images.PNG)) })
        assertEquals(Status.Code.INVALID_ARGUMENT, uploadStatus { upload(movie.id, "text/plain", listOf(Images.PNG)) })
        assertEquals(Status.Code.INVALID_ARGUMENT, uploadStatus { upload(movie.id, "image/png", listOf(ByteArray(5000))) })
        assertEquals(Status.Code.INVALID_ARGUMENT, uploadStatus { upload(movie.id, "image/png", emptyList()) })
    }

    @Test
    fun `upload without metadata first is rejected`() {
        val collector = Collector<ArtworkInfo>()
        val request = asyncStub.uploadArtwork(collector)
        request.onNext(UploadArtworkRequest.newBuilder().setChunk(ByteString.copyFrom(Images.PNG)).build())
        request.onCompleted()
        assertEquals(Status.Code.INVALID_ARGUMENT, uploadStatus { collector.result.get(5, TimeUnit.SECONDS) })
    }

    @Test
    fun `download for movie without artwork is NOT_FOUND`() {
        val movie = stub.createMovie(movieInput())
        assertEquals(Status.Code.NOT_FOUND, status { stub.downloadArtwork(id(movie.id)).asSequence().toList() })
        assertEquals(Status.Code.NOT_FOUND, status { stub.downloadArtwork(id("missing")).asSequence().toList() })
    }
}
