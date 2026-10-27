package com.moviehub.catalog.service

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@ConfigurationProperties("moviehub.artwork")
data class ArtworkProperties(
    val dir: String = "./data/artwork",
    val maxSizeBytes: Long = 5L * 1024 * 1024,
    val allowedContentTypes: List<String> = listOf("image/jpeg", "image/png", "image/webp"),
)

/**
 * File-system storage for artwork. Files are keyed solely by the movie id (a UUID that
 * has already been resolved against the database), which rules out path traversal.
 */
@Component
class ArtworkStorage(properties: ArtworkProperties) {

    private val root: Path = Path.of(properties.dir).toAbsolutePath().normalize()
    private val tmp: Path = root.resolve(".tmp")

    init {
        Files.createDirectories(tmp)
    }

    fun newTempFile(): Path = Files.createTempFile(tmp, "upload-", ".part")

    fun commit(temp: Path, movieId: String) {
        Files.move(temp, resolve(movieId), StandardCopyOption.REPLACE_EXISTING)
    }

    fun open(movieId: String): InputStream = Files.newInputStream(resolve(movieId))

    fun exists(movieId: String): Boolean = Files.exists(resolve(movieId))

    fun delete(movieId: String) {
        Files.deleteIfExists(resolve(movieId))
    }

    fun deleteTemp(temp: Path) {
        Files.deleteIfExists(temp)
    }

    private fun resolve(movieId: String): Path {
        val path = root.resolve(movieId).normalize()
        require(path.parent == root) { "Invalid artwork id" }
        return path
    }
}
