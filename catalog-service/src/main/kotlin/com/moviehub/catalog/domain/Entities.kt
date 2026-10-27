package com.moviehub.catalog.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class Role { ACTOR, DIRECTOR, WRITER, PRODUCER, COMPOSER }

@Entity
@Table(name = "movies")
class MovieEntity(
    @Id
    var id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 200)
    var title: String = "",

    @Column(nullable = false, length = 4000)
    var synopsis: String = "",

    @Column(name = "release_year", nullable = false)
    var releaseYear: Int = 0,

    @Column(name = "runtime_minutes", nullable = false)
    var runtimeMinutes: Int = 0,

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "movie_genres", joinColumns = [JoinColumn(name = "movie_id")])
    @Column(name = "genre", nullable = false, length = 50)
    var genres: MutableList<String> = mutableListOf(),

    @OneToMany(mappedBy = "movie", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("role, id")
    var credits: MutableList<CreditEntity> = mutableListOf(),

    @Column(name = "artwork_content_type", length = 100)
    var artworkContentType: String? = null,

    @Column(name = "artwork_version", nullable = false)
    var artworkVersion: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "people")
class PersonEntity(
    @Id
    var id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 200)
    var name: String = "",

    @Column(nullable = false, length = 4000)
    var bio: String = "",

    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,

    @OneToMany(mappedBy = "person", cascade = [CascadeType.REMOVE], orphanRemoval = true)
    var credits: MutableList<CreditEntity> = mutableListOf(),

    @Column(name = "avatar_content_type", length = 100)
    var avatarContentType: String? = null,

    @Column(name = "avatar_version", nullable = false)
    var avatarVersion: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "credits")
class CreditEntity(
    @Id
    var id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    var movie: MovieEntity? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    var person: PersonEntity? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: Role = Role.ACTOR,

    @Column(name = "character_name", nullable = false, length = 200)
    var characterName: String = "",
)

