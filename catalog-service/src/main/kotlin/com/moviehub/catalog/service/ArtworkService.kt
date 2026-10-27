package com.moviehub.catalog.service

import com.moviehub.catalog.domain.MovieRepository
import com.moviehub.catalog.domain.NotFoundException
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

/** An in-flight upload. Bytes are spooled to a temp file so memory use stays constant. */
class ArtworkUpload internal constructor(
    val movieId: String,
    val contentType: String,
    internal val tempFile: Path,
    private val maxSizeBytes: Long,
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
    private val storage: ArtworkStorage,
    private val properties: ArtworkProperties,
    private val clock: Clock,
) {

    /** Validates the target and declared content type before any bytes are accepted. */
    fun begin(movieId: String, declaredContentType: String): ArtworkUpload {
        val contentType = declaredContentType.substringBefore(';').trim().lowercase()
        if (contentType !in properties.allowedContentTypes) {
            throw ValidationException("unsupported content type '$contentType'; allowed: ${properties.allowedContentTypes}")
        }
        if (!movies.existsById(movieId)) throw NotFoundException("Movie $movieId not found")
        return ArtworkUpload(movieId, contentType, storage.newTempFile(), properties.maxSizeBytes)
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
            val movie = movies.findById(upload.movieId)
                .orElseThrow { NotFoundException("Movie ${upload.movieId} not found") }
            storage.commit(upload.tempFile, movie.id)
            movie.artworkContentType = upload.contentType
            movie.artworkVersion += 1
            movie.updatedAt = Instant.now(clock)
            movies.save(movie)
            return ArtworkInfoView(movie.id, upload.contentType, upload.size, movie.artworkVersion)
        } catch (e: Exception) {
            storage.deleteTemp(upload.tempFile)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun open(movieId: String): StoredArtwork {
        val movie = movies.findById(movieId).orElseThrow { NotFoundException("Movie $movieId not found") }
        val type = movie.artworkContentType
        if (type == null || !storage.exists(movieId)) throw NotFoundException("Movie $movieId has no artwork")
        return StoredArtwork(type, storage.open(movieId))
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


