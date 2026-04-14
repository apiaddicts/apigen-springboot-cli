package org.apiaddicts.apitools.apigen.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import org.apiaddicts.apitools.apigen.cli.ApigenSpringBootCli;
import org.apiaddicts.apitools.apigen.cli.utils.ConsoleOutput;
import org.apiaddicts.apitools.apigen.cli.utils.OpenApiParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Validates an OpenAPI specification for APIGen compatibility.
 * Checks required x-apigen extensions, model definitions, and binding consistency.
 */
@Command(
    name = "validate",
    description = "Validate an OpenAPI specification for APIGen compatibility.",
    mixinStandardHelpOptions = true
)
public class ValidateCommand implements Callable<Integer> {

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Option(names = {"-f", "--file"}, description = "Path to the OpenAPI specification file.", required = true)
    private File specFile;

    @Option(names = {"--strict"}, description = "Enable strict validation (warnings become errors).")
    private boolean strict;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        ConsoleOutput.header("Validating OpenAPI Specification");

        if (!specFile.exists()) {
            ConsoleOutput.error("File not found: " + specFile.getAbsolutePath());
            return 1;
        }

        try {
            JsonNode root = OpenApiParser.parse(specFile);
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            ConsoleOutput.detail("File", specFile.getName());
            ConsoleOutput.detail("OpenAPI Version", OpenApiParser.getOpenApiVersion(root));
            ConsoleOutput.detail("API Title", OpenApiParser.getTitle(root));
            ConsoleOutput.blank();

            // 1. Check OpenAPI version
            String oaVersion = OpenApiParser.getOpenApiVersion(root);
            if (oaVersion.startsWith("2")) {
                warnings.add("Swagger 2.0 detected. OpenAPI 3.0+ is recommended for best results.");
            }

            // 2. Check x-apigen-project
            JsonNode project = OpenApiParser.getApigenProject(root);
            if (project.isMissingNode()) {
                errors.add("Missing required 'x-apigen-project' extension.");
            } else {
                if (project.path("name").isMissingNode() || project.path("name").asText().isBlank()) {
                    errors.add("x-apigen-project.name is required.");
                }
                if (OpenApiParser.getGroupId(root).isBlank()) {
                    errors.add("x-apigen-project.group-id is required.");
                }
                if (OpenApiParser.getArtifactId(root).isBlank()) {
                    errors.add("x-apigen-project.artifact-id is required.");
                }
                if (OpenApiParser.getBasePackage(root).isBlank()) {
                    warnings.add("x-apigen-project.base-package is not set. A default will be used.");
                }
            }

            // 3. Check x-apigen-models
            JsonNode models = OpenApiParser.getApigenModels(root);
            if (models.isMissingNode()) {
                errors.add("Missing required 'x-apigen-models' extension.");
            } else {
                List<String> modelNames = OpenApiParser.getModelNames(root);
                if (modelNames.isEmpty()) {
                    errors.add("x-apigen-models is empty. Define at least one model.");
                }

                for (String modelName : modelNames) {
                    JsonNode model = models.get(modelName);
                    if (model.path("relational-persistence").isMissingNode()) {
                        warnings.add("Model '" + modelName + "' has no relational-persistence definition.");
                    }
                    if (model.path("attributes").isMissingNode() || !model.path("attributes").isArray()) {
                        errors.add("Model '" + modelName + "' has no attributes defined.");
                    } else {
                        boolean hasPk = false;
                        for (JsonNode attr : model.path("attributes")) {
                            if (attr.path("relational-persistence").path("primary-key").asBoolean(false)) {
                                hasPk = true;
                                break;
                            }
                        }
                        if (!hasPk) {
                            warnings.add("Model '" + modelName + "' has no primary key attribute defined.");
                        }
                    }
                }
            }

            // 4. Check paths have x-apigen-binding
            List<String> paths = OpenApiParser.getPaths(root);
            if (paths.isEmpty()) {
                warnings.add("No paths defined in the specification.");
            } else {
                JsonNode pathsNode = root.path("paths");
                for (String path : paths) {
                    JsonNode pathItem = pathsNode.get(path);
                    Iterator<String> methods = pathItem.fieldNames();
                    while (methods.hasNext()) {
                        String method = methods.next();
                        if (Set.of("get", "post", "put", "patch", "delete").contains(method)) {
                            JsonNode operation = pathItem.get(method);
                            if (operation.path("x-apigen-binding").isMissingNode()) {
                                warnings.add("Operation " + method.toUpperCase() + " " + path + " has no x-apigen-binding.");
                            }
                        }
                    }
                }
            }

            // 5. Check components/schemas exist
            List<String> schemas = OpenApiParser.getSchemaNames(root);
            if (schemas.isEmpty()) {
                warnings.add("No schemas defined in components.schemas.");
            }

            // Print results
            ConsoleOutput.header("Validation Results");

            if (!errors.isEmpty()) {
                ConsoleOutput.print("  Errors (" + errors.size() + "):");
                for (String err : errors) {
                    ConsoleOutput.error(err);
                }
            }

            if (!warnings.isEmpty()) {
                ConsoleOutput.print("  Warnings (" + warnings.size() + "):");
                for (String warn : warnings) {
                    ConsoleOutput.warn(warn);
                }
            }

            ConsoleOutput.blank();

            if (errors.isEmpty() && warnings.isEmpty()) {
                ConsoleOutput.success("Specification is valid and ready for code generation!");
                return 0;
            } else if (errors.isEmpty()) {
                if (strict) {
                    ConsoleOutput.error("Strict mode: " + warnings.size() + " warnings treated as errors.");
                    return 1;
                }
                ConsoleOutput.success("Specification is valid with " + warnings.size() + " warning(s).");
                return 0;
            } else {
                ConsoleOutput.error("Validation failed with " + errors.size() + " error(s) and " + warnings.size() + " warning(s).");
                return 1;
            }

        } catch (Exception e) {
            ConsoleOutput.error("Failed to parse specification: " + e.getMessage());
            if (parent != null && parent.verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
