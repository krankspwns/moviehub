package com.moviehub.gateway.web

import com.moviehub.gateway.catalog.ArtworkDownload
import com.moviehub.gateway.catalog.ArtworkInfoDto
import com.moviehub.gateway.catalog.CatalogClient
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.time.Duration

/**
 * Artwork is binary, so it cannot ride on GraphQL. A small REST surface handles it instead:
 * the browser uploads with multipart (which gives real upload progress events) and the gateway
 * relays the bytes to the catalog service over a client-streaming gRPC call.
 */
@RestController
class ArtworkController(private val catalog: CatalogClient) {

    @PostMapping("/api/movies/{id}/artwork", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@PathVariable id: String, @RequestPart("file") file: MultipartFile): ArtworkInfoDto =
        file.inputStream.use { catalog.uploadArtwork(id, file.contentType.orEmpty(), it) }

    /**
     * `v` is the artwork version from `Movie.artworkUrl`. When present the URL is immutable
     * (a new upload yields a new version), so browsers may cache it for a year.
     */
    @GetMapping("/api/movies/{id}/artwork")
    fun download(@PathVariable id: String, @RequestParam(required = false) v: Long?): ResponseEntity<StreamingResponseBody> =
        stream(catalog.downloadArtwork(id), v)

    @PostMapping("/api/people/{id}/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadAvatar(@PathVariable id: String, @RequestPart("file") file: MultipartFile): ArtworkInfoDto =
        file.inputStream.use { catalog.uploadAvatar(id, file.contentType.orEmpty(), it) }

    @GetMapping("/api/people/{id}/avatar")
    fun downloadAvatar(@PathVariable id: String, @RequestParam(required = false) v: Long?): ResponseEntity<StreamingResponseBody> =
        stream(catalog.downloadAvatar(id), v)

    private fun stream(artwork: ArtworkDownload, v: Long?): ResponseEntity<StreamingResponseBody> {
        val cache = if (v != null) CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable() else CacheControl.noCache()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(artwork.contentType))
            .cacheControl(cache)
            .header("X-Content-Type-Options", "nosniff")
            .body(StreamingResponseBody { out -> artwork.chunks.forEach(out::write) })
    }
}

