package com.moviehub.gateway

import com.moviehub.gateway.catalog.CatalogClient
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ArtworkControllerTest {

    @Autowired lateinit var mvc: MockMvc

    @MockitoBean lateinit var catalog: CatalogClient

    private val png = MockMultipartFile("file", "a.png", "image/png", byteArrayOf(1, 2, 3, 4))

    @Test
    fun `upload relays the file and returns artwork info`() {
        whenever(catalog.uploadArtwork(any(), any(), any())).thenAnswer {
            com.moviehub.gateway.catalog.ArtworkInfoDto(it.getArgument(0), it.getArgument(1), 4, "/api/movies/m1/artwork?v=1")
        }
        mvc.perform(multipart("/api/movies/m1/artwork").file(png))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.contentType").value("image/png"))
            .andExpect(jsonPath("$.sizeBytes").value(4))
            .andExpect(jsonPath("$.artworkUrl").value("/api/movies/m1/artwork?v=1"))
    }

    @Test
    fun `upload without file part is a 400`() {
        mvc.perform(multipart("/api/movies/m1/artwork").file(MockMultipartFile("other", byteArrayOf(1))))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `catalog failures map to HTTP statuses`() {
        whenever(catalog.uploadArtwork(any(), any(), any())).thenThrow(notFound("Movie m1 not found"))
        mvc.perform(multipart("/api/movies/m1/artwork").file(png)).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.detail").value("Movie m1 not found"))

        whenever(catalog.uploadArtwork(any(), any(), any())).thenThrow(invalid("unsupported content type"))
        mvc.perform(multipart("/api/movies/m1/artwork").file(png)).andExpect(status().isBadRequest)

        whenever(catalog.uploadArtwork(any(), any(), any()))
            .thenThrow(com.moviehub.gateway.catalog.CatalogException(com.moviehub.gateway.catalog.FailureKind.UNAVAILABLE, "down"))
        mvc.perform(multipart("/api/movies/m1/artwork").file(png)).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `download streams bytes with content type and immutable caching when versioned`() {
        whenever(catalog.downloadArtwork("m1")).thenReturn(
            com.moviehub.gateway.catalog.ArtworkDownload("image/png", listOf(byteArrayOf(1, 2), byteArrayOf(3)).iterator()),
        )
        val started = mvc.perform(get("/api/movies/m1/artwork").param("v", "3"))
            .andExpect(request().asyncStarted()).andReturn()
        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
            .andExpect(content().bytes(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `avatar upload and download use the people endpoints`() {
        whenever(catalog.uploadAvatar(any(), any(), any())).thenAnswer {
            com.moviehub.gateway.catalog.ArtworkInfoDto(it.getArgument(0), it.getArgument(1), 4, "/api/people/p1/avatar?v=1")
        }
        mvc.perform(multipart("/api/people/p1/avatar").file(png))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.artworkUrl").value("/api/people/p1/avatar?v=1"))

        whenever(catalog.downloadAvatar("p1")).thenReturn(
            com.moviehub.gateway.catalog.ArtworkDownload("image/png", listOf(byteArrayOf(9)).iterator()),
        )
        val started = mvc.perform(get("/api/people/p1/avatar").param("v", "1")).andReturn()
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk).andExpect(content().bytes(byteArrayOf(9)))

        whenever(catalog.downloadAvatar("none")).thenThrow(notFound())
        mvc.perform(get("/api/people/none/avatar")).andExpect(status().isNotFound)
    }

    @Test
    fun `unversioned download must revalidate and missing artwork is 404`() {
        whenever(catalog.downloadArtwork("m1")).thenReturn(
            com.moviehub.gateway.catalog.ArtworkDownload("image/png", listOf(byteArrayOf(1)).iterator()),
        )
        val started = mvc.perform(get("/api/movies/m1/artwork")).andReturn()
        mvc.perform(asyncDispatch(started)).andExpect(header().string("Cache-Control", "no-cache"))

        whenever(catalog.downloadArtwork("none")).thenThrow(notFound())
        mvc.perform(get("/api/movies/none/artwork")).andExpect(status().isNotFound)
    }
}
