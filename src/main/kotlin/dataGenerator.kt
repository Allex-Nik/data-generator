package org.datagenerator

import net.datafaker.Faker
import java.time.LocalDate
import kotlin.random.Random

val faker = Faker()

fun generateCustomers(number: Int = 50_000): List<Customer> {
    require(number > 0) { "Number of customers must be greater than 0" }
    val customers = (1..number).map {
        Customer(
            id = it,
            firstName = faker.name().firstName(),
            lastName = faker.name().lastName(),
            address = faker.address().fullAddress(),
            email = faker.internet().emailAddress(),
        )
    }
    return customers
}

fun generateBooks(number: Int = 50_000): List<Book> {
    require(number > 0) { "Number of books must be greater than 0" }
    val books = (1..number).map {
        Book(
            id = it,
            title = faker.book().title(),
            author = Author(
                Random.nextInt(1, 100),
                faker.name().firstName(),
                faker.name().lastName()
            ),
            genre = faker.book().genre(),
            publisher = faker.book().publisher(),
        )
    }
    return books
}

fun generateOrders(number: Int = 50_000): List<Order> {
    require(number > 0) { "Number of orders must be greater than 0" }
    val orders = (1..number).map {
        Order(
            id = it,
            customerId = faker.number()
                .numberBetween(1, number), // TODO: need to coordinate this with the real number of customers
            bookId = faker.number().numberBetween(1, number),
            date = LocalDate.now(), // TODO: need to pick a random date within some period
            quantity = faker.number().numberBetween(1, 10),
            price = faker.number().randomDouble(2, 1, 10_000)
        )
    }
    return orders
}