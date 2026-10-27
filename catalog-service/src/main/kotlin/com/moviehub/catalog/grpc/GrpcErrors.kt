package com.moviehub.catalog.grpc

import com.moviehub.catalog.domain.NotFoundException
import com.moviehub.catalog.domain.ValidationException
import io.grpc.Status
import net.devh.boot.grpc.server.advice.GrpcAdvice
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler
import org.slf4j.LoggerFactory

/** Central translation of domain exceptions to gRPC status codes. */
fun Throwable.toStatus(): Status = when (this) {
    is NotFoundException -> Status.NOT_FOUND.withDescription(message)
    is ValidationException -> Status.INVALID_ARGUMENT.withDescription(message)
    else -> {
        LoggerFactory.getLogger("grpc-errors").error("Unhandled error in gRPC call", this)
        Status.INTERNAL.withDescription("Internal error")
    }
}

@GrpcAdvice
class GrpcExceptionAdvice {
    @GrpcExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException): Status = e.toStatus()

    @GrpcExceptionHandler(ValidationException::class)
    fun invalid(e: ValidationException): Status = e.toStatus()

    @GrpcExceptionHandler(Exception::class)
    fun unexpected(e: Exception): Status = e.toStatus()
}
