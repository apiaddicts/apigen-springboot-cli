package org.apiaddicts.apitools.apigen.cli.commands;

import org.apiaddicts.apitools.apigen.cli.ApigenSpringBootCli;
import org.apiaddicts.apitools.apigen.cli.utils.ConsoleOutput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.Callable;

/**
 * Scaffolds a new APIGen project with a sample OpenAPI spec containing x-apigen extensions.
 */
@Command(
    name = "init",
    description = "Initialize a new APIGen project with a sample OpenAPI specification.",
    mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Option(names = {"-n", "--name"}, description = "Project name.", required = true)
    private String projectName;

    @Option(names = {"-g", "--group-id"}, description = "Maven group ID.", defaultValue = "com.example")
    private String groupId;

    @Option(names = {"-p", "--base-package"}, description = "Java base package.", defaultValue = "")
    private String basePackage;

    @Option(names = {"-d", "--directory"}, description = "Target directory.", defaultValue = ".")
    private File directory;

    @Option(names = {"--with-docker"}, description = "Include Docker configuration files.")
    private boolean withDocker;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        ConsoleOutput.header("Initializing APIGen Project");

        try {
            String artifactId = projectName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
            if (basePackage.isEmpty()) {
                basePackage = groupId + "." + artifactId.replace("-", "");
            }

            Path projectDir = directory.toPath().resolve(projectName);

            ConsoleOutput.step(1, withDocker ? 4 : 3, "Creating project directory: " + projectDir);
            Files.createDirectories(projectDir);

            ConsoleOutput.step(2, withDocker ? 4 : 3, "Generating OpenAPI specification with x-apigen extensions");
            writeOpenApiSpec(projectDir, artifactId);

            ConsoleOutput.step(3, withDocker ? 4 : 3, "Creating project configuration");
            writeApigenConfig(projectDir, artifactId);

            if (withDocker) {
                ConsoleOutput.step(4, 4, "Creating Docker configuration");
                writeDockerCompose(projectDir);
            }

            ConsoleOutput.blank();
            ConsoleOutput.success("Project '" + projectName + "' initialized successfully!");
            ConsoleOutput.blank();
            ConsoleOutput.info("Next steps:");
            ConsoleOutput.command("cd " + projectName);
            ConsoleOutput.command("apigen-springboot-cli validate -f openapi.yaml");
            ConsoleOutput.command("apigen-springboot-cli generate -f openapi.yaml -o ./generated");
            ConsoleOutput.command("apigen-springboot-cli build -d ./generated");
            ConsoleOutput.command("apigen-springboot-cli run -d ./generated");
            ConsoleOutput.blank();

            return 0;
        } catch (IOException e) {
            ConsoleOutput.error("Failed to initialize project: " + e.getMessage());
            return 1;
        }
    }

    private void writeOpenApiSpec(Path projectDir, String artifactId) throws IOException {
        String spec = String.format("""
                openapi: "3.0.0"
                info:
                  title: "%s API"
                  description: "Auto-generated API specification for %s."
                  version: "1.0.0"
                  contact:
                    name: API Support
                    email: support@example.com

                x-apigen-project:
                  name: %s
                  description: "%s Spring Boot Application."
                  group-id: %s
                  artifact-id: %s
                  version: "1.0.0-SNAPSHOT"
                  base-package: %s

                x-apigen-models:
                  Item:
                    relational-persistence:
                      table: items
                    attributes:
                      - name: id
                        type: Long
                        relational-persistence:
                          column: id
                          primary-key: true
                          auto-generated: true
                      - name: name
                        type: String
                        validations:
                          - type: NotNull
                          - type: Size
                            min: 1
                            max: 255
                        relational-persistence:
                          column: name
                      - name: description
                        type: String
                        relational-persistence:
                          column: description
                      - name: active
                        type: Boolean
                        relational-persistence:
                          column: active

                paths:
                  /items:
                    get:
                      operationId: getItems
                      summary: "Get all items."
                      tags:
                        - Items
                      x-apigen-binding:
                        model: Item
                      responses:
                        '200':
                          description: "Successful response."
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/ItemListResponse'
                    post:
                      operationId: createItem
                      summary: "Create a new item."
                      tags:
                        - Items
                      x-apigen-binding:
                        model: Item
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              $ref: '#/components/schemas/ItemRequest'
                      responses:
                        '201':
                          description: "Item created."
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/ItemResponse'

                  /items/{id}:
                    get:
                      operationId: getItemById
                      summary: "Get an item by ID."
                      tags:
                        - Items
                      x-apigen-binding:
                        model: Item
                      parameters:
                        - name: id
                          in: path
                          required: true
                          schema:
                            type: integer
                            format: int64
                      responses:
                        '200':
                          description: "Successful response."
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/ItemResponse'
                    put:
                      operationId: updateItem
                      summary: "Update an item."
                      tags:
                        - Items
                      x-apigen-binding:
                        model: Item
                      parameters:
                        - name: id
                          in: path
                          required: true
                          schema:
                            type: integer
                            format: int64
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              $ref: '#/components/schemas/ItemRequest'
                      responses:
                        '200':
                          description: "Item updated."
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/ItemResponse'
                    delete:
                      operationId: deleteItem
                      summary: "Delete an item."
                      tags:
                        - Items
                      x-apigen-binding:
                        model: Item
                      parameters:
                        - name: id
                          in: path
                          required: true
                          schema:
                            type: integer
                            format: int64
                      responses:
                        '204':
                          description: "Item deleted."

                components:
                  schemas:
                    ItemRequest:
                      type: object
                      required:
                        - name
                      properties:
                        name:
                          type: string
                          minLength: 1
                          maxLength: 255
                        description:
                          type: string
                        active:
                          type: boolean
                          default: true
                    ItemResponse:
                      type: object
                      properties:
                        id:
                          type: integer
                          format: int64
                        name:
                          type: string
                        description:
                          type: string
                        active:
                          type: boolean
                    ItemListResponse:
                      type: object
                      properties:
                        content:
                          type: array
                          items:
                            $ref: '#/components/schemas/ItemResponse'
                """,
                projectName, projectName, artifactId, projectName,
                groupId, artifactId, basePackage);

        Files.writeString(projectDir.resolve("openapi.yaml"), spec);
    }

    private void writeApigenConfig(Path projectDir, String artifactId) throws IOException {
        String config = String.format("""
                # APIGen CLI Configuration
                # See: https://github.com/apiaddicts/apigen.springboot

                spec: openapi.yaml
                output: ./generated

                project:
                  name: %s
                  group-id: %s
                  artifact-id: %s
                  base-package: %s
                """, projectName, groupId, artifactId, basePackage);

        Files.writeString(projectDir.resolve("apigen.yaml"), config);
    }

    private void writeDockerCompose(Path projectDir) throws IOException {
        String docker = """
                version: '3.8'
                services:
                  apigen:
                    image: apiaddicts/apitools-apigen:2.0.3
                    ports:
                      - "8080:8080"
                    volumes:
                      - ./:/workspace
                    working_dir: /workspace
                """;

        Files.writeString(projectDir.resolve("docker-compose.yml"), docker);
    }
}
