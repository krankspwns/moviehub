package com.moviehub.catalog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
@ConfigurationPropertiesScan
class CatalogApplication

fun main(args: Array<String>) {
    // Pin the JVM zone: JDBC DATE conversion for historic dates (e.g. 1815-12-10) is shifted by
    // local-mean-time offsets in some zones, which would silently change stored birth dates.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    runApplication<CatalogApplication>(*args)
}


