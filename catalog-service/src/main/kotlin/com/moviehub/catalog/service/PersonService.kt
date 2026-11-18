package com.moviehub.catalog.service

import com.moviehub.catalog.domain.NotFoundException
import com.moviehub.catalog.domain.PersonEntity
import com.moviehub.catalog.domain.PersonRepository
import com.moviehub.catalog.domain.Role
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class PersonService(private val people: PersonRepository, private val artworkStorage: ArtworkStorage, private val clock: Clock) {

    @Transactional(readOnly = true)
    fun list(query: String, role: Role?, page: Int, size: Int): PageView<PersonView> {
        val pageable = pageRequest(page, size, Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")))
        val pattern = likePattern(query)
        val result = if (role == null) people.search(pattern, pageable) else people.searchByRole(pattern, role, pageable)
        return PageView(result.content.map { it.toView() }, result.totalElements)
    }

    @Transactional(readOnly = true)
    fun get(id: String): PersonView = find(id).toView()

    @Transactional
    fun create(command: PersonCommand): PersonView {
        val clean = command
        val now = Instant.now(clock)
        val person = PersonEntity(
            name = clean.name,
            bio = clean.bio,
            birthDate = parseDate(clean.birthDate),
            createdAt = now,
            updatedAt = now,
        )
        return people.save(person).toView()
    }

    @Transactional
    fun update(id: String, command: PersonCommand): PersonView {
        val person = find(id)
        val clean = command
        person.name = clean.name
        person.bio = clean.bio
        person.birthDate = parseDate(clean.birthDate)
        person.updatedAt = Instant.now(clock)
        return people.saveAndFlush(person).toView()
    }

    /** Also removes the person's credits from every movie (cascade). */
    @Transactional
    fun delete(id: String): Boolean {
        val person = people.findById(id).orElse(null) ?: return false
        people.delete(person)
        artworkStorage.delete(id)
        return true
    }

    private fun find(id: String): PersonEntity = people.findById(id).orElseThrow { NotFoundException("Person $id not found") }

    /** Input is already validated by the request validator, so parsing cannot fail here. */
    private fun parseDate(raw: String): LocalDate? = raw.trim().takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
}
