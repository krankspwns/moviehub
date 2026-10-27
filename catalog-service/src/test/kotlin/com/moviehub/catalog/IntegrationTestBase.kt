package com.moviehub.catalog

import com.moviehub.catalog.domain.CreditRepository
import com.moviehub.catalog.domain.MovieRepository
import com.moviehub.catalog.domain.PersonRepository
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path

/**
 * Boots the full catalog context against an in-memory H2 (with the real Flyway
 * migrations) and a throw-away artwork directory. Each test starts from empty tables.
 */
@SpringBootTest(
    properties = [
        "grpc.server.port=-1",
        "grpc.server.in-process-name=catalog-test",
        "spring.datasource.url=jdbc:h2:mem:catalog-test;DB_CLOSE_DELAY=-1",
    ],
)
abstract class IntegrationTestBase {

    @Autowired lateinit var movieRepository: MovieRepository

    @Autowired lateinit var personRepository: PersonRepository

    @Autowired lateinit var creditRepository: CreditRepository

    @BeforeEach
    fun cleanDatabase() {
        creditRepository.deleteAll()
        movieRepository.deleteAll()
        personRepository.deleteAll()
    }

    companion object {
        private val artworkDir: Path = Files.createTempDirectory("moviehub-artwork-test")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("moviehub.artwork.dir") { artworkDir.toString() }
            registry.add("moviehub.artwork.max-size-bytes") { 1024 }
        }
    }
}

/** Minimal valid image headers used by the artwork tests. */
object Images {
    val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
    val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    val WEBP = "RIFF".toByteArray() + byteArrayOf(1, 2, 3, 4) + "WEBP".toByteArray() + byteArrayOf(9)
}
