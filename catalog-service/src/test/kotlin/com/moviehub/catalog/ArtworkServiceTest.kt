package com.moviehub.catalog

import com.moviehub.catalog.domain.NotFoundException
import com.moviehub.catalog.domain.ValidationException
import com.moviehub.catalog.service.ArtworkService
import com.moviehub.catalog.service.ArtworkStorage
import com.moviehub.catalog.service.MovieCommand
import com.moviehub.catalog.service.MovieService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtworkServiceTest : IntegrationTestBase() {

    @Autowired lateinit var artwork: ArtworkService
    @Autowired lateinit var storage: ArtworkStorage
    @Autowired lateinit var movies: MovieService

    private fun newMovie() = movies.create(MovieCommand("M", "", 2000, 0, emptyList(), emptyList())).id

    private fun upload(movieId: String, type: String, vararg chunks: ByteArray) =
        artwork.begin(movieId, type).let { up ->
            try {
                chunks.forEach(up::write)
                artwork.complete(up)
            } catch (e: Exception) {
                artwork.abort(up)
                throw e
            }
        }

    @Test
    fun `upload stores file, flags the movie and bumps the version`() {
        val id = newMovie()

        val first = upload(id, "image/png", Images.PNG)
        assertEquals(Images.PNG.size.toLong(), first.sizeBytes)
        assertEquals(1, first.version)

        val movie = movies.get(id)
        assertTrue(movie.hasArtwork)
        assertEquals(1, movie.artworkVersion)

        val stored = artwork.open(id)
        assertEquals("image/png", stored.contentType)
        assertContentEquals(Images.PNG, stored.stream.use { it.readAllBytes() })

        val second = upload(id, "image/jpeg", Images.JPEG)
        assertEquals(2, second.version)
        assertEquals("image/jpeg", artwork.open(id).also { it.stream.close() }.contentType)
    }

    @Test
    fun `chunked upload is reassembled in order`() {
        val id = newMovie()
        upload(id, "image/jpeg", Images.JPEG.copyOfRange(0, 3), Images.JPEG.copyOfRange(3, Images.JPEG.size))
        assertContentEquals(Images.JPEG, artwork.open(id).stream.use { it.readAllBytes() })
    }

    @Test
    fun `content type parameters and case are normalised`() {
        val id = newMovie()
        assertEquals("image/webp", upload(id, "IMAGE/WEBP; charset=binary", Images.WEBP).contentType)
    }

    @Test
    fun `unsupported content type is rejected before any bytes are accepted`() {
        val id = newMovie()
        assertThrows<ValidationException> { artwork.begin(id, "image/gif") }
        assertThrows<ValidationException> { artwork.begin(id, "application/pdf") }
    }

    @Test
    fun `unknown movie is rejected`() {
        assertThrows<NotFoundException> { artwork.begin("missing", "image/png") }
    }

    @Test
    fun `oversized upload is rejected and leaves no artwork`() {
        val id = newMovie()
        // test profile caps uploads at 1024 bytes
        assertThrows<ValidationException> { upload(id, "image/png", Images.PNG, ByteArray(2000)) }
        assertFalse(movies.get(id).hasArtwork)
        assertFalse(storage.exists(id))
    }

    @Test
    fun `upload exactly at the limit is accepted`() {
        val id = newMovie()
        val bytes = Images.JPEG + ByteArray(1024 - Images.JPEG.size)
        assertEquals(1024, upload(id, "image/jpeg", bytes).sizeBytes)
    }

    @Test
    fun `empty file is rejected`() {
        val id = newMovie()
        assertThrows<ValidationException> { upload(id, "image/png") }
    }

    @Test
    fun `file whose bytes do not match the declared type is rejected`() {
        val id = newMovie()
        assertThrows<ValidationException> { upload(id, "image/png", Images.JPEG) }
        assertThrows<ValidationException> { upload(id, "image/jpeg", "<html>not an image</html>".toByteArray()) }
        assertThrows<ValidationException> { upload(id, "image/webp", "RIFF".toByteArray() + ByteArray(8)) }
        assertFalse(movies.get(id).hasArtwork)
    }

    @Test
    fun `failed replacement keeps the previous artwork`() {
        val id = newMovie()
        upload(id, "image/png", Images.PNG)
        assertThrows<ValidationException> { upload(id, "image/png", ByteArray(2000)) }
        assertContentEquals(Images.PNG, artwork.open(id).stream.use { it.readAllBytes() })
    }

    @Test
    fun `open without artwork throws NotFound`() {
        val id = newMovie()
        assertThrows<NotFoundException> { artwork.open(id) }
        assertThrows<NotFoundException> { artwork.open("missing") }
    }

    @Test
    fun `deleting the movie removes its artwork file`() {
        val id = newMovie()
        upload(id, "image/png", Images.PNG)
        assertTrue(storage.exists(id))
        movies.delete(id)
        assertFalse(storage.exists(id))
    }

    @Test
    fun `storage refuses path traversal ids`() {
        assertThrows<IllegalArgumentException> { storage.exists("../../etc/passwd") }
    }
}

