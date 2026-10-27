plugins {
    kotlin("jvm") version "2.1.21" apply false
    kotlin("plugin.spring") version "2.1.21" apply false
    kotlin("plugin.jpa") version "2.1.21" apply false
    id("org.springframework.boot") version "3.5.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

allprojects {
    group = "com.moviehub"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
            compilerOptions {
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Same default zone as production (see CatalogApplication) so results don't depend on the dev machine.
        systemProperty("user.timezone", "UTC")
        testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    }
}


