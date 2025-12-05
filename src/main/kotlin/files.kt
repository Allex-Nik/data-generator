package org.datagenerator

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.apache.arrow.memory.BufferAllocator
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.*
import org.apache.arrow.vector.complex.StructVector
import org.apache.arrow.vector.ipc.ArrowFileWriter
import org.apache.arrow.vector.types.DateUnit
import org.apache.arrow.vector.types.FloatingPointPrecision
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.Field
import org.apache.arrow.vector.types.pojo.FieldType.notNullable
import org.apache.arrow.vector.types.pojo.Schema
import org.apache.avro.SchemaBuilder
import org.apache.avro.reflect.ReflectData
import org.apache.hadoop.conf.Configuration
import org.apache.parquet.avro.AvroParquetWriter
import org.apache.parquet.example.data.simple.SimpleGroupFactory
import org.apache.parquet.hadoop.example.ExampleParquetWriter
import org.apache.parquet.hadoop.util.HadoopOutputFile
import org.apache.parquet.io.LocalOutputFile
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.MessageTypeParser
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import org.apache.hadoop.fs.Path as HPath


fun createFileWithData(
    dataType: String,
    fileType: String,
    entries: Int?,
    path: Path
) {
    val numEntries = entries ?: 50_000

    val data = when (dataType) {
        "customer" -> generateCustomers(numEntries)
        "book" -> generateBooks(numEntries)
        "order" -> generateOrders(numEntries)
        else -> null
    } ?: return // TODO: Think of proper handling

    require(data.isNotEmpty()) { "Data must not be empty" }

    when (fileType) {
        "csv" -> generateCsv(data, path)
        "json" -> generateJson(data, path)
        "xlsx" -> generateExcel(data, path)
        "arrow" -> generateArrow(dataType, data, path)
        "parquet" -> generateParquet(dataType, data, path)
    }
}

fun <T : TableRow> generateCsv(data: List<T>, path: Path) {
    val mapper = CsvMapper().apply {
        registerModule(JavaTimeModule()) // register the module to support LocalDate
//        "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.0" also added to build.gradle.kts for that
//         findAndRegisterModules() is equivalent
    }
    // Adding a different separator: .withColumnSeparator(';')
    val schema = mapper.schemaFor(data.first()::class.java).withHeader()
    val csv = mapper.writer(schema).writeValueAsString(data)
    Files.writeString(path, csv)
}

fun <T : TableRow> generateJson(data: List<T>, path: Path) {
    val mapper = JsonMapper()
    val json = mapper
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(data)
    Files.writeString(path, json)
}

fun <T : TableRow> generateExcel(data: List<T>, path: Path) {
    val workbook: Workbook = XSSFWorkbook()
    val workSheet = workbook.createSheet()
    val headerRow = workSheet.createRow(0)
    val fields = data.first()::class.java.declaredFields
    fields.forEachIndexed { index, field ->
        headerRow.createCell(index).setCellValue(field.name)
    }
    data.forEachIndexed { index, dataRow ->
        val sheetRow = workSheet.createRow(index + 1)
        fields.forEachIndexed { colIndex, field ->
            field.isAccessible = true
            val cell = sheetRow.createCell(colIndex)
            when (val value = field.get(dataRow)) {
                is LocalDate -> cell.setCellValue(value.toString())
                is Number -> cell.setCellValue(value.toDouble())
                else -> cell.setCellValue(value.toString())
            }
        }
    }
    val output = FileOutputStream(path.toFile())
    workbook.write(output)
    workbook.close()
}

fun generateArrow(dataType: String, data: List<TableRow>, path: Path) = when (dataType) {
    "customer" -> generateArrowCustomers(data as List<Customer>, path)
    "book" -> generateArrowBooks(data as List<Book>, path)
    "order" -> generateArrowOrders(data as List<Order>, path)
    else -> null // TODO: Think of proper handling
}

fun generateArrowCustomers(data: List<Customer>, path: Path) {
    val id = Field("id", notNullable(ArrowType.Int(32, true)), null)
    val firstName = Field("firstName", notNullable(ArrowType.Utf8()), null)
    val lastName = Field("lastName", notNullable(ArrowType.Utf8()), null)
    val address = Field("address", notNullable(ArrowType.Utf8()), null)
    val email = Field("email", notNullable(ArrowType.Utf8()), null)

    val schema = Schema(listOf(id, firstName, lastName, address, email))

    val allocator: BufferAllocator = RootAllocator()
    val root = VectorSchemaRoot.create(schema, allocator)

    val idVector: IntVector = root.getVector(id) as IntVector
    val firstNameVector: VarCharVector = root.getVector(firstName) as VarCharVector
    val lastNameVector: VarCharVector = root.getVector(lastName) as VarCharVector
    val addressVector: VarCharVector = root.getVector(address) as VarCharVector
    val emailVector: VarCharVector = root.getVector(email) as VarCharVector

    idVector.allocateNew(data.size)
    firstNameVector.allocateNew(data.size)
    lastNameVector.allocateNew(data.size)
    addressVector.allocateNew(data.size)
    emailVector.allocateNew(data.size)

    data.forEachIndexed { index, customer ->
        idVector.set(index, customer.id)
        firstNameVector.set(index, customer.firstName.encodeToByteArray())
        lastNameVector.set(index, customer.lastName.encodeToByteArray())
        addressVector.set(index, customer.address.encodeToByteArray())
        emailVector.set(index, customer.email.encodeToByteArray())
    }

    root.rowCount = data.size

    val file = File(path.toString())
    val fileOutputStream = FileOutputStream(file)
    val writer = ArrowFileWriter(root, null, fileOutputStream.getChannel())
    writer.start()
    writer.writeBatch()
    writer.end()

    root.close()
    allocator.close()
}

/**
 * Two ways to deal with the complex `Author` field:
 *
 * 1. Via Struct (applied currently):
 * Define the `author` field and its children:
 *   - id
 *   - firstName
 *   - lastName
 * Add the parent `author` field to the schema.
 * Get all four vectors (parent and children), apply `allocate` to them.
 * Write to the children vectors, apply `setIndexDefined` to the parent vector in the loop.
 * Result: one column `author` with a dict in each cell
 *
 * 2. Unwrap the fields of `Author` manually (commented out):
 * Define only the three children fields.
 * Add all the three children fields to the schema.
 * Get them from the root as usual.
 * Write to them as usual.
 * Result: three separate columns (authorId, authorFirstName, authorLastName)
 */
fun generateArrowBooks(data: List<Book>, path: Path) {
    val id = Field("id", notNullable(ArrowType.Int(32, true)), null)
    val title = Field("title", notNullable(ArrowType.Utf8()), null)
    val author = Field(
        "author", notNullable(ArrowType.Struct()), listOf(
            Field("id", notNullable(ArrowType.Int(32, true)), null),
            Field("firstName", notNullable(ArrowType.Utf8()), null),
            Field("lastName", notNullable(ArrowType.Utf8()), null)
        )
    )
//    val authorId = Field("authorId", notNullable(ArrowType.Int(32, true)), null)
//    val authorFirstName = Field("authorFirstName", notNullable(ArrowType.Utf8()), null)
//    val authorLastName = Field("authorLastName", notNullable(ArrowType.Utf8()), null)
    val genre = Field("genre", notNullable(ArrowType.Utf8()), null)
    val publisher = Field("publisher", notNullable(ArrowType.Utf8()), null)

    val schema = Schema(listOf(id, title, author, genre, publisher))
//    val schema = Schema(listOf(id, title, authorId, authorFirstName, authorLastName, genre, publisher))

    val allocator: BufferAllocator = RootAllocator()
    val root = VectorSchemaRoot.create(schema, allocator)

    val idVector: IntVector = root.getVector(id) as IntVector
    val titleVector: VarCharVector = root.getVector(title) as VarCharVector
    val authorVector: StructVector = root.getVector(author) as StructVector
    val authorIdVector: IntVector = authorVector.getChild("id") as IntVector
    val authorFirstNameVector: VarCharVector = authorVector.getChild("firstName") as VarCharVector
    val authorLastNameVector: VarCharVector = authorVector.getChild("lastName") as VarCharVector
//    val authorIdVector: IntVector = root.getVector(authorId) as IntVector
//    val authorFirstNameVector: VarCharVector = root.getVector(authorFirstName) as VarCharVector
//    val authorLastNameVector: VarCharVector = root.getVector(authorLastName) as VarCharVector
    val genreVector: VarCharVector = root.getVector(genre) as VarCharVector
    val publisherVector: VarCharVector = root.getVector(publisher) as VarCharVector

    idVector.allocateNew(data.size)
    titleVector.allocateNew(data.size)
    authorVector.allocateNew()
    authorIdVector.allocateNew(data.size)
    authorFirstNameVector.allocateNew(data.size)
    authorLastNameVector.allocateNew(data.size)
    genreVector.allocateNew(data.size)
    publisherVector.allocateNew(data.size)

    data.forEachIndexed { index, book ->
        idVector.set(index, book.id)
        titleVector.set(index, book.title.encodeToByteArray())
        //authorVector.writer.//.setIndexDefined(index)//.set(index, book.author.encodeToByteArray()) ???
        // Added to handle the new Author field (object)
        authorVector.setIndexDefined(index) // just this with defining fields is not enough (`{}` in each `author` cell)
        authorIdVector.set(index, book.author.id)
        authorFirstNameVector.set(index, book.author.firstName.encodeToByteArray())
        authorLastNameVector.set(index, book.author.lastName.encodeToByteArray())
        genreVector.set(index, book.genre.encodeToByteArray())
        publisherVector.set(index, book.publisher.encodeToByteArray())
    }

    root.rowCount = data.size

    val file = File(path.toString())
    val fileOutputStream = FileOutputStream(file)
    val writer = ArrowFileWriter(root, null, fileOutputStream.getChannel())
    writer.start()
    writer.writeBatch()
    writer.end()

    root.close()
    allocator.close()
}

fun generateArrowOrders(data: List<Order>, path: Path) {
    val id = Field("id", notNullable(ArrowType.Int(32, true)), null)
    val customerId = Field("customerId", notNullable(ArrowType.Int(32, true)), null)
    val bookId = Field("bookId", notNullable(ArrowType.Int(32, true)), null)
    val date = Field("date", notNullable(ArrowType.Date(DateUnit.DAY)), null)
    val quantity = Field("quantity", notNullable(ArrowType.Int(32, true)), null)
    val price = Field(
        "price",
        notNullable(ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)),
        null
    )

    val schema = Schema(listOf(id, customerId, bookId, date, quantity, price))

    val allocator: BufferAllocator = RootAllocator()
    val root = VectorSchemaRoot.create(schema, allocator)

    val idVector: IntVector = root.getVector(id) as IntVector
    val customerIdVector: IntVector = root.getVector(customerId) as IntVector
    val bookIdVector: IntVector = root.getVector(bookId) as IntVector
    val dateVector = root.getVector(date) as DateDayVector
    val quantityVector: IntVector = root.getVector(quantity) as IntVector
    val priceVector = root.getVector(price) as Float8Vector

    idVector.allocateNew(data.size)
    customerIdVector.allocateNew(data.size)
    bookIdVector.allocateNew(data.size)
    dateVector.allocateNew(data.size)
    quantityVector.allocateNew(data.size)
    priceVector.allocateNew(data.size)

    data.forEachIndexed { index, order ->
        idVector.set(index, order.id)
        customerIdVector.set(index, order.customerId)
        bookIdVector.set(index, order.bookId)
        dateVector.set(index, order.date.toEpochDay().toInt())
        quantityVector.set(index, order.quantity)
        priceVector.set(index, order.price)
    }

    root.rowCount = data.size

    val file = File(path.toString())
    val fileOutputStream = FileOutputStream(file)
    val writer = ArrowFileWriter(root, null, fileOutputStream.getChannel())
    writer.start()
    writer.writeBatch()
    writer.end()

    root.close()
    allocator.close()
}

fun generateParquet(dataType: String, data: List<TableRow>, path: Path) = when (dataType) {
    "customer" -> generateParquetCustomers(data as List<Customer>, path)
    "book" -> generateParquetBooks(data as List<Book>, path)
    "order" -> generateParquetOrders(data as List<Order>, path)
    else -> null // TODO: Think of proper handling
}

/**
 * Example API.
 * TODO: Rewrite with AvroParquetWriter or ParquetWriter API
 */
fun generateParquetCustomers(customers: List<Customer>, path: Path) {
    // Available types are listed in org.apache.parquet.schema in PrimitiveType and LogicalTypeAnnotation
    val schemaString = """
        message customer {
          required int32 id;
          required binary firstName (UTF8);
          required binary lastName (UTF8);
          required binary address (UTF8);
          required binary email (UTF8);
        }
    """.trimIndent()

    // Parses and stores the schema written as a string.
    // Via this object we can access the name of the schema (customer in this case), fields, columns, types, some checks, etc.
    val schema: MessageType = MessageTypeParser.parseMessageType(schemaString)
    // Row container from datagenerator package. Add rows to it.
    val factory = SimpleGroupFactory(schema)
    val conf = Configuration()
    val hPath = HPath(path.toString())

    ExampleParquetWriter.builder(HadoopOutputFile.fromPath(hPath, conf)) // or LocalOutputFile(path)
        .withConf(conf)
        .withType(schema)
        .build().use { writer ->
            customers.forEach { customer ->
                writer.write(
                    factory.newGroup()
                        .append("id", customer.id)
                        .append("firstName", customer.firstName)
                        .append("lastName", customer.lastName)
                        .append("address", customer.address)
                        .append("email", customer.email)
                )
            }
        }
}

/**
 * Ways to deal with nested `author` field in parquet.
 *
 * 1. Manual unwrapping (just add the children columns of the author) is possible (like for Arrow format),
 * and this would preserve the order of columns.
 * Problem: schema is not preserved (children columns are presented as completely separate columns).
 *
 * 2. Use AvroParquetWriter and get the schema via reflection.
 * Problem: breaks the order of columns.
 *
 * 3. Use AvroParquetWriter and define the new schema manually.
 * This preserves the order of columns.
 * Problem: easy to make a typo, especially when the schema is large and/or complex.
 *
 * 4. Use AvroParquetWriter and define the new schema with SchemaBuilder (implemented here).
 * Preserves columns order.
 */
fun generateParquetBooks(books: List<Book>, path: Path) {
    val schema: org.apache.avro.Schema = SchemaBuilder.record("book")
        .fields()
        .name("id").type().intType().noDefault() // full field
        .requiredString("title") // shortcut
        .name("author").type(
            SchemaBuilder.record("author").fields()
                .requiredInt("id")
                .requiredString("firstName")
                .requiredString("lastName")
                .endRecord()
        ).noDefault()
        .requiredString("genre")
        .requiredString("publisher")
        .endRecord()

    AvroParquetWriter.builder<Book>(LocalOutputFile(path))
        .withSchema(schema) // ReflectData.AllowNull also possible
        .withDataModel(ReflectData.get())
        .withConf(Configuration()) // also possible to set compression, block size, page size
        .build().use { writer ->
            books.forEach { book ->
                writer.write(book)
            }
        }
}

/**
 * Example API.
 * TODO: Rewrite with AvroParquetWriter or ParquetWriter API
 */
fun generateParquetOrders(orders: List<Order>, path: Path) {
    val schemaString = """
        message customer {
          required int32 id;
          required int32 customerId;
          required int32 bookId;
          required int32 date (DATE);
          required int32 quantity;
        }
    """.trimIndent()

    val schema: MessageType = MessageTypeParser.parseMessageType(schemaString)
    val factory = SimpleGroupFactory(schema)
    val conf = Configuration()
    val hPath = HPath(path.toString())

    ExampleParquetWriter.builder(HadoopOutputFile.fromPath(hPath, conf))
        .withConf(conf)
        .withType(schema)
        .build().use { writer ->
            orders.forEach { order ->
                writer.write(
                    factory.newGroup()
                        .append("id", order.id)
                        .append("customerId", order.customerId)
                        .append("bookId", order.bookId)
                        .append("date", order.date.toEpochDay().toInt())
                        .append("quantity", order.quantity)
                )
            }
        }
}