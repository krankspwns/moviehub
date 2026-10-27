package com.moviehub.gateway.catalog

import com.moviehub.catalog.grpc.CatalogServiceGrpc
import net.devh.boot.grpc.client.inject.GrpcClient
import net.devh.boot.grpc.client.inject.GrpcClientBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@ConfigurationProperties("moviehub.catalog")
data class CatalogProperties(val callTimeout: Duration = Duration.ofSeconds(10), val uploadTimeout: Duration = Duration.ofSeconds(60))

/** Exposes the channel-backed stubs for the "catalog" client (see `grpc.client.catalog.*`) as beans. */
@Configuration
@GrpcClientBean(
    clazz = CatalogServiceGrpc.CatalogServiceBlockingStub::class,
    beanName = "catalogBlockingStub",
    client = GrpcClient("catalog"),
)
@GrpcClientBean(
    clazz = CatalogServiceGrpc.CatalogServiceStub::class,
    beanName = "catalogAsyncStub",
    client = GrpcClient("catalog"),
)
class CatalogClientConfig
