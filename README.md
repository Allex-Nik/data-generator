# Data generator

This project allows its user to create files of different formats and automatically fill them with randomly generated tabular data. 

Supported file formats:
* CSV
* JSON
* XLSX
* Arrow
* Parquet

Additionally, it creates tables in the existing test PostgreSQL database and fills them with random tabular data.

## Setup
1. Clone the project: `git clone https://github.com/Allex-Nik/data-generator.git`.
2. Build the project.
3. Create a test PostgreSQL database with the following credetials:
   * **url**: `jdbc:postgresql://localhost:5432/test_db`;
   * **username**: `test_user`;
   * **password**: `test`.

## How to run the project
### Run configuration
1. [Pass](#how-to-pass-program-arguments) program arguments via the run configuration.
2. Click `Run`.

### Command line 
1. [Make](#how-to-build-a-fat-jar-with-gradle) a fat jar.
2. Run the project with a command in the format: `java -jar path/to/jar/jar_name.jar args`.
Example run: `java -jar build/libs/data-generator-1.0-SNAPSHOT-standalone.jar customer csv customers 50 data`

### How to pass program arguments
There are 3 required arguments:
* type of the data (`customer`, `book`, or `order`),
* type of the file (`csv`, `json`, `xlsx`, `arrow`, or `parquet`),
* name of the file (any string);

and 2 optional arguments:
* number of entries of data (any positive number). Default is 50 000 entries.
* path where the file will be created. Default is the directory from which the program is run. If the specified directory does not exist, the program will create it. Specifying an absolute path is also possible.

Order of arguments matters. The arguments are case-sensitive.

Example: 
You want to create a JSON-file of type `book` named `books` with 100 entries and save it in a folder `data` in your current directory. Then the arguments you need to pass are: `book json books 100 data`.

### How to build a fat jar (with Gradle)
1. Run `./gradlew fatJar`.
2. Run  `zip -d path/to/jar/jar_name.jar 'META-INF/.SF' 'META-INF/.RSA' 'META-INF/*SF'` - this command deletes security signed files from the compiled jar.

Example of the command: `zip -d build/libs/data-generator-1.0-SNAPSHOT-standalone.jar 'META-INF/.SF' 'META-INF/.RSA' 'META-INF/*SF'`.
