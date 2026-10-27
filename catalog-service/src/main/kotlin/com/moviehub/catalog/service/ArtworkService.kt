package com.moviehub.catalog.service

import com.moviehub.catalog.domain.MovieRepository
import com.moviehub.catalog.domain.NotFoundException
import com.moviehub.catalog.domain.PersonRepository
import com.moviehub.catalog.domain.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant

/** What an image belongs to: a movie poster or a person's avatar. */
enum class ImageOwner(val label: String) { MOVIE("Movie"), PERSON("Person") }

/** An in-flight upload. Bytes are spooled to a temp file so memory use stays constant. */
class ArtworkUpload internal constructor(
    val movieId: String,
    val contentType: String,
    internal val tempFile: Path,
    private val maxSizeBytes: Long,
    val owner: ImageOwner = ImageOwner.MOVIE,
) : AutoCloseable {

    private val out: OutputStream = Files.newOutputStream(tempFile, StandardOpenOption.WRITE)
    var size: Long = 0
        private set

    fun write(bytes: ByteArray) {
        size += bytes.size
        if (size > maxSizeBytes) {
            throw ValidationException("artwork exceeds the maximum size of $maxSizeBytes bytes")
        }
        out.write(bytes)
    }

    internal fun finish() = out.close()

    override fun close() {
        runCatching { out.close() }
    }
}

class StoredArtwork(val contentType: String, val stream: InputStream)

@Service
class ArtworkService(
    private val movies: MovieRepository,
    private val people: PersonRepository,
    private val storage: ArtworkStorage,
    private val properties: ArtworkProperties,
    private val clock: Clock,
) {

    /** Validates the target and declared content type before any bytes are accepted. */
    fun begin(movieId: String, declaredContentType: String): ArtworkUpload =
        begin(ImageOwner.MOVIE, movieId, declaredContentType)

    fun begin(owner: ImageOwner, id: String, declaredContentType: String): ArtworkUpload {
        val contentType = declaredContentType.substringBefore(';').trim().lowercase()
        if (contentType !in properties.allowedContentTypes) {
            throw ValidationException("unsupported content type '$contentType'; allowed: ${properties.allowedContentTypes}")
        }
        val exists = when (owner) {
            ImageOwner.MOVIE -> movies.existsById(id)
            ImageOwner.PERSON -> people.existsById(id)
        }
        if (!exists) throw NotFoundException("${owner.label} $id not found")
        return ArtworkUpload(id, contentType, storage.newTempFile(), properties.maxSizeBytes, owner)
    }

    /** Discards a failed/cancelled upload. Safe to call more than once. */
    fun abort(upload: ArtworkUpload) {
        upload.close()
        storage.deleteTemp(upload.tempFile)
    }

    @Transactional
    fun complete(upload: ArtworkUpload): ArtworkInfoView {
        upload.finish()
        try {
            if (upload.size == 0L) throw ValidationException("artwork file is empty")
            if (!matchesSignature(upload.tempFile, upload.contentType)) {
                throw ValidationException("file content does not match declared type ${upload.contentType}")
            }
            val now = Instant.now(clock)
            val version = when (upload.owner) {
                ImageOwner.MOVIE -> {
                    val movie = movies.findById(upload.movieId)
                        .orElseThrow { NotFoundException("Movie ${upload.movieId} not found") }
                    storage.commit(upload.tempFile, movie.id)
                    movie.artworkContentType = upload.contentType
                    movie.artworkVersion += 1
                    movie.updatedAt = now
                    movies.save(movie).artworkVersion
                }
                ImageOwner.PERSON -> {
                    val person = people.findById(upload.movieId)
                        .orElseThrow { NotFoundException("Person ${upload.movieId} not found") }
                    storage.commit(upload.tempFile, person.id)
                    person.avatarContentType = upload.contentType
                    person.avatarVersion += 1
                    person.updatedAt = now
                    people.save(person).avatarVersion
                }
            }
            return ArtworkInfoView(upload.movieId, upload.contentType, upload.size, version)
        } catch (e: Exception) {
            storage.deleteTemp(upload.tempFile)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun open(movieId: String): StoredArtwork = open(ImageOwner.MOVIE, movieId)

    @Transactional(readOnly = true)
    fun open(owner: ImageOwner, id: String): StoredArtwork {
        val type = when (owner) {
            ImageOwner.MOVIE -> movies.findById(id).orElseThrow { NotFoundException("Movie $id not found") }.artworkContentType
            ImageOwner.PERSON -> people.findById(id).orElseThrow { NotFoundException("Person $id not found") }.avatarContentType
        }
        if (type == null || !storage.exists(id)) throw NotFoundException("${owner.label} $id has no image")
        return StoredArtwork(type, storage.open(id))
    }

    private fun matchesSignature(file: Path, contentType: String): Boolean {
        val head = Files.newInputStream(file).use { it.readNBytes(12) }
        fun startsWith(vararg b: Int) = head.size >= b.size && b.indices.all { head[it] == b[it].toByte() }
        return when (contentType) {
            "image/jpeg" -> startsWith(0xFF, 0xD8, 0xFF)
            "image/png" -> startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            "image/webp" -> startsWith('R'.code, 'I'.code, 'F'.code, 'F'.code) &&
                head.size >= 12 && String(head, 8, 4, Charsets.US_ASCII) == "WEBP"
            else -> false
        }
    }
}

