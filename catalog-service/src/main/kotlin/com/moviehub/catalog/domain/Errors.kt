package com.moviehub.catalog.domain

/** Requested entity does not exist. Mapped to gRPC `NOT_FOUND`. */
class NotFoundException(message: String) : RuntimeException(message)

/** Input failed business validation. Mapped to gRPC `INVALID_ARGUMENT`. */
class ValidationException(message: String) : RuntimeException(message)

