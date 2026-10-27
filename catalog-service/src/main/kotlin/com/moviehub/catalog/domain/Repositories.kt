package com.moviehub.catalog.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * All `pattern` parameters are pre-built, lower-cased `%term%` LIKE patterns where the
 * special characters `%`, `_` and `!` in the user's term were escaped with `!`
 * (see [com.moviehub.catalog.service.likePattern]).
 */
interface MovieRepository : JpaRepository<MovieEntity, String> {

    @Query(
        """
        select m from MovieEntity m
        where lower(m.title) like :pattern escape '!'
           or lower(m.synopsis) like :pattern escape '!'
           or exists (select 1 from m.genres g where lower(g) like :pattern escape '!')
           or exists (select 1 from CreditEntity c where c.movie = m and lower(c.person.name) like :pattern escape '!')
        """,
    )
    fun search(@Param("pattern") pattern: String, pageable: Pageable): Page<MovieEntity>
}

interface PersonRepository : JpaRepository<PersonEntity, String> {

    @Query("select p from PersonEntity p where lower(p.name) like :pattern escape '!'")
    fun search(@Param("pattern") pattern: String, pageable: Pageable): Page<PersonEntity>

    @Query(
        """
        select p from PersonEntity p
        where lower(p.name) like :pattern escape '!'
          and exists (select 1 from CreditEntity c where c.person = p and c.role = :role)
        """,
    )
    fun searchByRole(
        @Param("pattern") pattern: String,
        @Param("role") role: Role,
        pageable: Pageable,
    ): Page<PersonEntity>
}

interface CreditRepository : JpaRepository<CreditEntity, String>

