package org.datagenerator

import java.sql.Connection

fun createTables(conn: Connection) { // TODO: add `use`
    val stmt = conn.createStatement()

    stmt.execute("DROP TABLE IF EXISTS customers, books, orders;")

    val tableCustomersSql = """
        |CREATE TABLE IF NOT EXISTS customers 
        |(id SERIAL PRIMARY KEY, first_name VARCHAR(255), last_name VARCHAR(255), 
        |address VARCHAR(255), email VARCHAR(255));
        |""".trimMargin()

    val tableBooksSql = """
        |CREATE TABLE IF NOT EXISTS books 
        |(id SERIAL PRIMARY KEY, title VARCHAR(255), 
        |author_id INT, author_first_name VARCHAR(255), author_last_name VARCHAR(255),
        |genre VARCHAR(255), publisher VARCHAR(255));
        |""".trimMargin()

    val tableOrdersSql = """
        CREATE TABLE IF NOT EXISTS orders 
        (id SERIAL PRIMARY KEY, customer_id INT, book_id INT, date DATE, quantity INT);
        """.trimMargin()

    stmt.execute(tableCustomersSql)
    stmt.execute(tableBooksSql)
    stmt.execute(tableOrdersSql)
}

fun insertCustomers(conn: Connection, customers: List<Customer>) {
    val insertCustomersSql =
        """INSERT INTO customers (id, first_name, last_name, address, email) VALUES (?, ?, ?, ?, ?)"""
    conn.prepareStatement(insertCustomersSql).use { pstmt ->
        customers.forEach { customer ->
            pstmt.setInt(1, customer.id)
            pstmt.setString(2, customer.firstName)
            pstmt.setString(3, customer.lastName)
            pstmt.setString(4, customer.address)
            pstmt.setString(5, customer.email)
            pstmt.executeUpdate()
        }
    }
}

fun insertBooks(conn: Connection, books: List<Book>) {
    val insertBooksSql = """INSERT INTO books (id, title, author_id, author_first_name, author_last_name, genre, publisher) 
        |VALUES (?, ?, ?, ?, ?, ?, ?)""".trimMargin()
    conn.prepareStatement(insertBooksSql).use { pstmt ->
        books.forEach { book ->
            pstmt.setInt(1, book.id)
            pstmt.setString(2, book.title)
            pstmt.setInt(3, book.author.id)
            pstmt.setString(4, book.author.firstName)
            pstmt.setString(5, book.author.lastName)
            pstmt.setString(6, book.genre)
            pstmt.setString(7, book.publisher)
            pstmt.executeUpdate()
        }
    }
}

fun insertOrders(conn: Connection, orders: List<Order>) {
    val insertOrdersSql = """INSERT INTO orders (id, customer_id, book_id, date, quantity) VALUES (?, ?, ?, ?, ?)"""
    conn.prepareStatement(insertOrdersSql).use { pstmt ->
        orders.forEach { order ->
            pstmt.setInt(1, order.id)
            pstmt.setInt(2, order.customerId)
            pstmt.setInt(3, order.bookId)
            pstmt.setObject(4, order.date)
            pstmt.setInt(5, order.quantity)
            pstmt.executeUpdate()
        }
    }
}
