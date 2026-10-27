package com.moviehub.gateway.web

import com.moviehub.gateway.catalog.CatalogException
import com.moviehub.gateway.catalog.FailureKind
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** RFC 7807 problem responses for the REST (artwork) endpoints. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(CatalogException::class)
    fun catalog(e: CatalogException): ProblemDetail {
        val status = when (e.kind) {
            FailureKind.NOT_FOUND -> HttpStatus.NOT_FOUND
            FailureKind.INVALID -> HttpStatus.BAD_REQUEST
            FailureKind.UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE
            FailureKind.INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        return ProblemDetail.forStatusAndDetail(status, e.message ?: status.reasonPhrase)
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(e: MaxUploadSizeExceededException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large (max 5 MB)")

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun missingPart(e: MissingServletRequestPartException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Multipart field '${e.requestPartName}' is required")
}

@ConfigurationProperties("moviehub.cors")
data class CorsProperties(val allowedOrigins: List<String> = emptyList())

@Configuration
class CorsConfig(private val properties: CorsProperties) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        if (properties.allowedOrigins.isEmpty()) return
        val origins = properties.allowedOrigins.toTypedArray()
        registry.addMapping("/graphql").allowedOrigins(*origins).allowedMethods("POST", "OPTIONS")
        registry.addMapping("/api/**").allowedOrigins(*origins).allowedMethods("GET", "POST", "OPTIONS")
    }
}
