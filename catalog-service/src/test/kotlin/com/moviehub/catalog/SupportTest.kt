package com.moviehub.catalog

import com.moviehub.catalog.domain.ValidationException
import com.moviehub.catalog.service.MAX_PAGE_SIZE
import com.moviehub.catalog.service.likePattern
import com.moviehub.catalog.service.pageRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Sort
import kotlin.test.assertEquals

class SupportTest {

    @Test
    fun `likePattern lower-cases trims and wraps`() {
        assertEquals("%hello world%", likePattern("  Hello World "))
    }

    @Test
    fun `likePattern escapes wildcard characters and the escape character`() {
        assertEquals("%100!% !_ !!%", likePattern("100% _ !"))
    }

    @Test
    fun `likePattern of blank matches everything`() {
        assertEquals("%%", likePattern("   "))
    }

    @Test
    fun `pageRequest applies defaults and bounds`() {
        assertEquals(20, pageRequest(0, 0, Sort.unsorted()).pageSize)
        assertEquals(MAX_PAGE_SIZE, pageRequest(0, MAX_PAGE_SIZE, Sort.unsorted()).pageSize)
        assertThrows<ValidationException> { pageRequest(-1, 10, Sort.unsorted()) }
        assertThrows<ValidationException> { pageRequest(0, MAX_PAGE_SIZE + 1, Sort.unsorted()) }
        assertThrows<ValidationException> { pageRequest(0, -1, Sort.unsorted()) }
    }
}

