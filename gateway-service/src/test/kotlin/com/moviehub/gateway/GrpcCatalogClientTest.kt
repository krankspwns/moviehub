package com.moviehub.gateway

import com.google.protobuf.ByteString
import com.moviehub.catalog.grpc.ArtworkChunk
import com.moviehub.catalog.grpc.ArtworkInfo
import com.moviehub.catalog.grpc.CatalogServiceGrpc
import com.moviehub.catalog.grpc.GetByIdRequest
import com.moviehub.catalog.grpc.ListMoviesRequest
import com.moviehub.catalog.grpc.ListMoviesResponse
import com.moviehub.catalog.grpc.Movie
import com.moviehub.catalog.grpc.UploadArtworkRequest
import com.moviehub.gateway.catalog.CatalogException
import com.moviehub.gateway.catalog.CatalogProperties
import com.moviehub.gateway.catalog.FailureKind
import com.moviehub.gateway.catalog.GrpcCatalogClient
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Runs the real [GrpcCatalogClient] against an in-process fake catalog server. */
class GrpcCatalogClientTest {

    private val received = java.io.ByteArrayOutputStream()
    private var uploadedType: String? = null
    private var maxBytes = Int.MAX_VALUE

    private val fake = object : CatalogServiceGrpc.CatalogServiceImplBase() {
        override fun listMovies(r: ListMoviesRequest, o: StreamObserver<ListMoviesResponse>) {
            if (r.query == "boom") return o.onError(Status.INVALID_ARGUMENT.withDescription("bad query").asRuntimeException())
            if (r.query == "crash") return o.onError(Status.INTERNAL.withDescription("secret").asRuntimeException())
            if (r.query == "down") return o.onError(Status.UNAVAILABLE.asRuntimeException())
            o.onNext(
                ListMoviesResponse.newBuilder().setTotal(41)
                    .addMovies(Movie.newBuilder().setId("m1").setTitle("Heat").setHasArtwork(true).setArtworkVersion(7).setCreatedAtEpochMs(0)).build(),
            )
            o.onCompleted()
        }

        override fun getMovie(r: GetByIdRequest, o: StreamObserver<Movie>) =
            o.onError(Status.NOT_FOUND.withDescription("Movie ${r.id} not found").asRuntimeException())

        override fun uploadArtwork(o: StreamObserver<ArtworkInfo>) = object : StreamObserver<UploadArtworkRequest> {
            override fun onNext(v: UploadArtworkRequest) {
                if (v.hasMetadata()) uploadedType = v.metadata.contentType else received.write(v.chunk.toByteArray())
                if (received.size() > maxBytes) o.onError(Status.INVALID_ARGUMENT.withDescription("too big").asRuntimeException())
            }
            override fun onError(t: Throwable) {}
            override fun onCompleted() {
                o.onNext(ArtworkInfo.newBuilder().setMovieId("m1").setContentType(uploadedType).setSizeBytes(received.size().toLong()).setArtworkVersion(2).build())
                o.onCompleted()
            }
        }

        override fun downloadArtwork(r: GetByIdRequest, o: StreamObserver<ArtworkChunk>) {
            if (r.id == "none") return o.onError(Status.NOT_FOUND.asRuntimeException())
            o.onNext(ArtworkChunk.newBuilder().setContentType("image/png").setData(ByteString.copyFrom(byteArrayOf(1, 2))).build())
            o.onNext(ArtworkChunk.newBuilder().setData(ByteString.copyFrom(byteArrayOf(3))).build())
            o.onCompleted()
        }
    }

    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var client: GrpcCatalogClient

    @BeforeEach
    fun start() {
        val name = UUID.randomUUID().toString()
        server = InProcessServerBuilder.forName(name).directExecutor().addService(fake).build().start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        client = GrpcCatalogClient(CatalogServiceGrpc.newBlockingStub(channel), CatalogServiceGrpc.newStub(channel), CatalogProperties())
    }

    @AfterEach
    fun stop() {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
        server.shutdownNow()
    }

    @Test
    fun `list maps proto to dto including artwork url and paging`() {
        val page = client.listMovies("", 1, 20)
        assertEquals(41, page.total)
        assertEquals(3, page.totalPages)
        assertEquals("/api/movies/m1/artwork?v=7", page.items.single().artworkUrl)
    }

    @Test
    fun `page size 0 reports the catalog default`() {
        assertEquals(20, client.listMovies("", 0, 0).pageSize)
    }

    @Test
    fun `grpc status codes are classified`() {
        assertEquals(FailureKind.INVALID, assertThrows<CatalogException> { client.listMovies("boom", 0, 1) }.kind)
        assertEquals("bad query", assertThrows<CatalogException> { client.listMovies("boom", 0, 1) }.message)
        assertEquals(FailureKind.UNAVAILABLE, assertThrows<CatalogException> { client.listMovies("down", 0, 1) }.kind)
        val internal = assertThrows<CatalogException> { client.listMovies("crash", 0, 1) }
        assertEquals(FailureKind.INTERNAL, internal.kind)
        assertEquals("Unexpected error", internal.message) // internal details are not leaked
        assertEquals(FailureKind.NOT_FOUND, assertThrows<CatalogException> { client.getMovie("zz") }.kind)
    }

    @Test
    fun `upload streams all bytes in chunks`() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val info = client.uploadArtwork("m1", "image/png", ByteArrayInputStream(data))
        assertContentEquals(data, received.toByteArray())
        assertEquals("image/png", uploadedType)
        assertEquals(200_000, info.sizeBytes)
        assertEquals("/api/movies/m1/artwork?v=2", info.artworkUrl)
    }

    @Test
    fun `upload rejected by the catalog surfaces as INVALID`() {
        maxBytes = 10
        val e = assertThrows<CatalogException> {
            client.uploadArtwork("m1", "image/png", ByteArrayInputStream(ByteArray(300_000)))
        }
        assertEquals(FailureKind.INVALID, e.kind)
    }

    @Test
    fun `download exposes content type and all chunks`() {
        val download = client.downloadArtwork("m1")
        assertEquals("image/png", download.contentType)
        assertContentEquals(byteArrayOf(1, 2, 3), download.chunks.asSequence().fold(ByteArray(0)) { a, c -> a + c })
    }

    @Test
    fun `download of missing artwork fails eagerly with NOT_FOUND`() {
        assertEquals(FailureKind.NOT_FOUND, assertThrows<CatalogException> { client.downloadArtwork("none") }.kind)
    }
}


