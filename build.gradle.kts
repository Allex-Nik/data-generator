plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.dataframe") version "2.2.20"
    application
}

group = "org.datagenerator"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.datafaker:datafaker:2.5.2")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.20.1")

    implementation("org.apache.poi:poi:5.5.0")
    implementation("org.apache.poi:poi-ooxml:5.5.0")

    implementation("org.apache.parquet:parquet-avro:1.16.0")
    implementation("org.apache.parquet:parquet-hadoop:1.16.0")
    implementation("org.apache.hadoop:hadoop-common:3.4.0")

    implementation("org.jetbrains.kotlinx:dataframe:1.0.0-Beta3")
    implementation("org.jetbrains.kotlinx:dataframe-arrow:1.0.0-Beta3")

    implementation("org.postgresql:postgresql:42.7.8")

    implementation("org.apache.arrow:arrow-vector:18.3.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("org.datagenerator.MainKt")
    applicationDefaultJvmArgs = listOf(
        "--add-opens=java.base/java.nio=ALL-UNNAMED"
    )
}

tasks {
    val fatJar = register<Jar>("fatJar") {
        dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources"))
        archiveClassifier.set("standalone")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes(mapOf("Main-Class" to application.mainClass)) }
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) } +
                sourcesMain.output
        from(contents)
    }
    build {
        dependsOn(fatJar)
    }
}
