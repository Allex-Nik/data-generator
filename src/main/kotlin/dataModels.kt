package org.datagenerator

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonUnwrapped
import java.time.LocalDate

@JsonPropertyOrder("id", "firstName", "lastName", "address", "email")
data class Customer(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val address: String,
    val email: String,
) : TableRow()

@JsonPropertyOrder("id", "title", "author", "genre", "publisher")
data class Book(
    val id: Int,
    val title: String,
    @field:JsonUnwrapped(prefix = "author_")
    val author: Author,
    val genre: String,
    val publisher: String,
) : TableRow()

@JsonPropertyOrder("id", "firstName", "lastName")
data class Author(
    val id: Int,
    val firstName: String,
    val lastName: String,
)

@JsonPropertyOrder("id", "customerId", "bookId", "date", "quantity", "price")
data class Order(
    val id: Int,
    val customerId: Int,
    val bookId: Int,
    val date: LocalDate,
    val quantity: Int,
    val price: Double
) : TableRow()

open class TableRow