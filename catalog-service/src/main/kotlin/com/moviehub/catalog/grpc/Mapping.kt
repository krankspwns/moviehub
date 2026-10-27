package com.moviehub.catalog.grpc

import com.moviehub.catalog.domain.Role
import com.moviehub.catalog.domain.ValidationException
import com.moviehub.catalog.service.CreditCommand
import com.moviehub.catalog.service.CreditView
import com.moviehub.catalog.service.MovieCommand
import com.moviehub.catalog.service.MovieView
import com.moviehub.catalog.service.PersonCommand
import com.moviehub.catalog.service.PersonView
import com.moviehub.catalog.grpc.Role as ProtoRole

fun Role.toProto(): ProtoRole = when (this) {
    Role.ACTOR -> ProtoRole.ROLE_ACTOR
    Role.DIRECTOR -> ProtoRole.ROLE_DIRECTOR
    Role.WRITER -> ProtoRole.ROLE_WRITER
    Role.PRODUCER -> ProtoRole.ROLE_PRODUCER
    Role.COMPOSER -> ProtoRole.ROLE_COMPOSER
}

/** Returns null for UNSPECIFIED so it can double as "no filter". */
fun ProtoRole.toDomainOrNull(): Role? = when (this) {
    ProtoRole.ROLE_ACTOR -> Role.ACTOR
    ProtoRole.ROLE_DIRECTOR -> Role.DIRECTOR
    ProtoRole.ROLE_WRITER -> Role.WRITER
    ProtoRole.ROLE_PRODUCER -> Role.PRODUCER
    ProtoRole.ROLE_COMPOSER -> Role.COMPOSER
    ProtoRole.ROLE_UNSPECIFIED, ProtoRole.UNRECOGNIZED -> null
}

fun CreditView.toProto(): Credit = Credit.newBuilder()
    .setPersonId(personId)
    .setPersonName(personName)
    .setRole(role.toProto())
    .setCharacterName(characterName)
    .build()

fun MovieView.toProto(): Movie = Movie.newBuilder()
    .setId(id)
    .setTitle(title)
    .setSynopsis(synopsis)
    .setReleaseYear(releaseYear)
    .setRuntimeMinutes(runtimeMinutes)
    .addAllGenres(genres)
    .setHasArtwork(hasArtwork)
    .setArtworkVersion(artworkVersion)
    .addAllCredits(credits.map { it.toProto() })
    .setCreatedAtEpochMs(createdAt.toEpochMilli())
    .setUpdatedAtEpochMs(updatedAt.toEpochMilli())
    .build()

fun PersonView.toProto(): Person = Person.newBuilder()
    .setId(id)
    .setName(name)
    .setBio(bio)
    .setBirthDate(birthDate?.toString().orEmpty())
    .addAllFilmography(
        filmography.map {
            PersonMovie.newBuilder().setMovieId(it.movieId).setTitle(it.title).setRole(it.role.toProto()).build()
        },
    )
    .setHasAvatar(hasAvatar)
    .setAvatarVersion(avatarVersion)
    .build()

fun MovieInput.toCommand() = MovieCommand(
    title = title,
    synopsis = synopsis,
    releaseYear = releaseYear,
    runtimeMinutes = runtimeMinutes,
    genres = genresList,
    credits = creditsList.map {
        CreditCommand(
            personId = it.personId,
            role = it.role.toDomainOrNull() ?: throw ValidationException("credit role is required"),
            characterName = it.characterName,
        )
    },
)

fun PersonInput.toCommand() = PersonCommand(name = name, bio = bio, birthDate = birthDate)

