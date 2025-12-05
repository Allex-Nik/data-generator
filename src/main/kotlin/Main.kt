package org.datagenerator

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.system.exitProcess

fun main(arguments: Array<String>) {
    try {
        val args = validateArgs(arguments)
        val path = constructPath(args)
        if (!path.exists()) {
            createFileWithData(args.dataType, args.fileType, args.numEntries, path)
            println("File created.")
        } else {
            println("File already exists.") // TODO: Add possibility to replace the file if the user wants to
        }
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(1)
    } catch (e: Exception) {
        System.err.println(e.message)
        e.printStackTrace()
        exitProcess(1)
    }

    println(
        """Do you also want to create and populate tables in your PostgreSQL database? 
        |Type "yes" to do so, anything else otherwise.""".trimMargin()
    )
    val input = readln()
    if (input == "yes") {
        // credentials are hardcoded to simplify the usage for testing. No real data is stored in such a database
        val url = "jdbc:postgresql://localhost:5432/test_db"
        val userName = "test_user"
        val password = "test"
        // TODO: check: javax.sql.DataSource is preferred?
        DriverManager.getConnection(url, userName, password).use { conn ->
            createTables(conn)
            println("Tables created.")

            val customers = generateCustomers()
            val books = generateBooks()
            val orders = generateOrders()
            println("Data generated.")

            insertCustomers(conn, customers)
            insertBooks(conn, books)
            insertOrders(conn, orders)
            println("Data inserted.")
        }
    } else {
        println("The user has chosen not to create and populate tables")
    }
}

fun validateArgs(args: Array<String>): Args {
    require(args.size >= 3) {
        """Not all required arguments were passed. There are 3 required arguments:
            |- type of the data ("customer", "book", or "order"),
            |- type of the file ("csv", "json", "xlsx", "arrow", or "parquet"),
            |- name of the file (any string),
            |
            |and 2 optional arguments:
            |- number of entries of data (any positive number),
            |- path where the file will be created.
        """.trimMargin()
    }
    val dataType = args[0]
    require(dataType == "customer" || dataType == "book" || dataType == "order") {
        """Type of generated data (the first argument) must be either "customer", or "book", or "order".
        |Try again with one of these values.""".trimMargin()
    }

    val fileType = args[1]
    require(
        fileType == "csv" || fileType == "json"
                || fileType == "xlsx" || fileType == "arrow" || fileType == "parquet"
    ) {
        """Type of the file (the second argument) must be either "csv", or "json", or "xlsx", or "arrow", or "parquet".
        |Try again with one of these values.""".trimMargin()
    }

    val fileName = args[2]

    val numEntriesArg = if (args.size > 3) args[3] else null
    val numEntries = if (numEntriesArg != null) {
        numEntriesArg.toIntOrNull() ?: throw IllegalArgumentException("The fourth argument must be an integer")
    } else null
    if (numEntries != null) require(numEntries > 0) { "The number of entries must be positive" }

    val outputDir = if (args.size == 5) args[4] else null // TODO: Add checks and message?
    return Args(dataType, fileType, fileName, numEntries, outputDir)
}

fun constructPath(args: Args): Path {
    val outputDir = args.outputDir ?: ""
    val outputDirPath = Path(outputDir)
    Files.createDirectories(outputDirPath)
    return outputDirPath.resolve("${args.fileName}.${args.fileType}")
}

data class Args(val dataType: String, val fileType: String, val fileName: String, val numEntries: Int?, val outputDir: String?)
