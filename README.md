# APIGen Spring Boot CLI

Command-line interface for the [apigen.springboot](https://github.com/apiaddicts/apigen.springboot) code generator. Wraps project lifecycle commands and manages the generator via local JAR or Docker.

## Quick Start

```bash
# Build the CLI (fat JAR)
mvn clean package

# Run via JAR
java -jar target/apigen-springboot-cli-2.1.0-SNAPSHOT.jar --help

# Or build a native executable (requires GraalVM)
mvn clean package -Pnative
./target/apigen-springboot-cli --help
```

## Commands

### Project Lifecycle

| Command      | Description                                              |
|--------------|----------------------------------------------------------|
| `init`       | Scaffold a new project with a sample OpenAPI spec        |
| `generate`   | Generate a Spring Boot project from an OpenAPI spec      |
| `build`      | Build the generated project (`mvn clean install`)        |
| `run`        | Run the generated Spring Boot application                |
| `test`       | Run tests (`mvn test` or `mvn verify`)                   |
| `clean`      | Clean build artifacts and optionally IDE/generated files |

### Inspection

| Command      | Description                                           |
|--------------|-------------------------------------------------------|
| `validate`   | Validate an OpenAPI spec for APIGen compatibility     |
| `preview`    | Preview what would be generated without writing files |

### Tooling

| Command      | Description                                       |
|--------------|---------------------------------------------------|
| `config`     | View/manage `apigen.yaml` project configuration   |
| `docker`     | Manage the APIGen generator Docker container       |
| `version`    | Show version and environment details               |

## Example Workflow

```bash
# 1. Create a new project
apigen-springboot-cli init -n my-api -g com.example

# 2. Validate the generated OpenAPI spec
apigen-springboot-cli validate -f my-api/openapi.yaml

# 3. Generate the Spring Boot project
apigen-springboot-cli generate -f my-api/openapi.yaml -o my-api/generated

# 4. Build the generated project
apigen-springboot-cli build -d my-api/generated

# 5. Run the application
apigen-springboot-cli run -d my-api/generated --port 8080

# 6. Run tests
apigen-springboot-cli test -d my-api/generated

# 7. Clean up
apigen-springboot-cli clean -d my-api/generated --deep
```

## Build Options

### Fat JAR (default)

```bash
mvn clean package
# Produces: target/apigen-springboot-cli-2.1.0-SNAPSHOT.jar
```

### GraalVM Native Image

Requires GraalVM with `native-image` installed.

```bash
mvn clean package -Pnative
# Produces: target/apigen-springboot-cli (native binary)
```

## License

LGPL v3.0 — see [LICENSE](https://www.gnu.org/licenses/lgpl-3.0.html).
