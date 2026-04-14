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
 * Preview command that shows what a generate operation would produce
 * without writing any files to disk.
 */
@Command(
    name = "preview",
    description = "Preview the project structure that would be generated from an OpenAPI spec.",
    mixinStandardHelpOptions = true
)
public class PreviewCommand implements Callable<Integer> {

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Option(names = {"-f", "--file"}, description = "Path to the OpenAPI specification file.", required = true)
    private File specFile;

    @Option(names = {"--tree"}, description = "Show output as a directory tree (default).", defaultValue = "true")
    private boolean tree;

    @Option(names = {"--endpoints"}, description = "Show endpoint summary table.")
    private boolean endpoints;

    @Option(names = {"--models"}, description = "Show detailed model/attribute breakdown.")
    private boolean models;

    @Option(names = {"--all"}, description = "Show tree, endpoints, and models.")
    private boolean all;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        ConsoleOutput.header("APIGen Project Preview");

        if (!specFile.exists()) {
            ConsoleOutput.error("Specification file not found: " + specFile.getAbsolutePath());
            return 1;
        }

        try {
            JsonNode root = OpenApiParser.parse(specFile);

            ConsoleOutput.detail("Specification", specFile.getName());
            ConsoleOutput.detail("OpenAPI Version", OpenApiParser.getOpenApiVersion(root));
            ConsoleOutput.detail("API Title", OpenApiParser.getTitle(root));
            ConsoleOutput.detail("API Version", OpenApiParser.getVersion(root));
            ConsoleOutput.blank();

            if (!OpenApiParser.hasApigenExtensions(root)) {
                ConsoleOutput.warn("No x-apigen extensions found. Preview shows a generic project structure.");
                ConsoleOutput.blank();
            }

            boolean showAll = all || (!endpoints && !models);

            if (showAll || tree) {
                showProjectTree(root);
            }
            if (showAll || endpoints) {
                showEndpointSummary(root);
            }
            if (showAll || models) {
                showModelDetail(root);
            }

            ConsoleOutput.info("This is a preview only. No files were written.");
            ConsoleOutput.info("Run 'apigen-springboot-cli generate -f " + specFile.getName() + " -o ./output' to generate the project.");
            ConsoleOutput.blank();

            return 0;

        } catch (Exception e) {
            ConsoleOutput.error("Failed to parse specification: " + e.getMessage());
            if (parent != null && parent.verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private void showProjectTree(JsonNode root) {
        ConsoleOutput.header("Project Tree");

        List<String> modelNames = OpenApiParser.getModelNames(root);
        String basePackage = OpenApiParser.getBasePackage(root);
        String artifactId = OpenApiParser.getArtifactId(root);
        String pkg = basePackage.isBlank() ? "com.example" : basePackage;
        String pkgPath = pkg.replace('.', '/');
        String projectName = artifactId.isBlank() ? "generated-project" : artifactId;

        ConsoleOutput.print("  " + projectName + "/");
        ConsoleOutput.print("  ├── pom.xml");
        ConsoleOutput.print("  ├── src/");
        ConsoleOutput.print("  │   ├── main/");
        ConsoleOutput.print("  │   │   ├── java/" + pkgPath + "/");
        ConsoleOutput.print("  │   │   │   ├── Application.java");

        for (int i = 0; i < modelNames.size(); i++) {
            String model = modelNames.get(i);
            String lower = model.toLowerCase();
            boolean last = (i == modelNames.size() - 1);
            String prefix = last ? "└──" : "├──";
            String indent = last ? "    " : "│   ";

            ConsoleOutput.print("  │   │   │   " + prefix + " " + lower + "/");
            ConsoleOutput.print("  │   │   │   " + indent + " ├── " + model + ".java");
            ConsoleOutput.print("  │   │   │   " + indent + " ├── " + model + "Repository.java");
            ConsoleOutput.print("  │   │   │   " + indent + " ├── " + model + "Service.java");
            ConsoleOutput.print("  │   │   │   " + indent + " └── " + model + "Controller.java");
        }

        ConsoleOutput.print("  │   │   └── resources/");
        ConsoleOutput.print("  │   │       ├── application.properties");
        ConsoleOutput.print("  │   │       └── application.yml");
        ConsoleOutput.print("  │   └── test/");
        ConsoleOutput.print("  │       └── java/" + pkgPath + "/");
        ConsoleOutput.print("  │           └── ApplicationTests.java");
        ConsoleOutput.print("  └── README.md");
        ConsoleOutput.blank();

        int entityFiles = modelNames.size();
        int total = entityFiles * 4 + 5;

        ConsoleOutput.detail("Entities", String.valueOf(entityFiles));
        ConsoleOutput.detail("Repositories", String.valueOf(entityFiles));
        ConsoleOutput.detail("Services", String.valueOf(entityFiles));
        ConsoleOutput.detail("Controllers", String.valueOf(entityFiles));
        ConsoleOutput.detail("Total files", String.valueOf(total));
        ConsoleOutput.blank();
    }

    private void showEndpointSummary(JsonNode root) {
        ConsoleOutput.header("Endpoints");

        List<String> paths = OpenApiParser.getPaths(root);
        JsonNode pathsNode = root.path("paths");

        if (paths.isEmpty()) {
            ConsoleOutput.warn("No paths defined in the specification.");
            ConsoleOutput.blank();
            return;
        }

        ConsoleOutput.print(String.format("  %-8s %-30s %-20s %s", "METHOD", "PATH", "OPERATION ID", "BINDING"));
        ConsoleOutput.print("  " + "─".repeat(80));

        for (String path : paths) {
            JsonNode pathItem = pathsNode.get(path);
            Iterator<String> methods = pathItem.fieldNames();
            while (methods.hasNext()) {
                String method = methods.next();
                if (Set.of("get", "post", "put", "patch", "delete").contains(method)) {
                    JsonNode operation = pathItem.get(method);
                    String operationId = operation.path("operationId").asText("-");
                    String binding = operation.path("x-apigen-binding").path("model").asText("-");
                    ConsoleOutput.print(String.format("  %-8s %-30s %-20s %s",
                            method.toUpperCase(), path, operationId, binding));
                }
            }
        }

        ConsoleOutput.blank();
        Map<String, Integer> ops = OpenApiParser.countOperationsByMethod(root);
        ConsoleOutput.detail("Total operations", ops.values().stream().mapToInt(Integer::intValue).sum() + " " + ops);
        ConsoleOutput.blank();
    }

    private void showModelDetail(JsonNode root) {
        ConsoleOutput.header("Models");

        JsonNode modelsNode = OpenApiParser.getApigenModels(root);
        if (modelsNode.isMissingNode()) {
            ConsoleOutput.warn("No x-apigen-models defined.");
            ConsoleOutput.blank();
            return;
        }

        List<String> modelNames = OpenApiParser.getModelNames(root);
        for (String modelName : modelNames) {
            JsonNode model = modelsNode.get(modelName);

            String table = model.path("relational-persistence").path("table").asText("-");
            ConsoleOutput.print("  " + modelName + " (table: " + table + ")");

            JsonNode attributes = model.path("attributes");
            if (attributes.isArray()) {
                for (JsonNode attr : attributes) {
                    String name = attr.path("name").asText("?");
                    String type = attr.path("type").asText("?");
                    String column = attr.path("relational-persistence").path("column").asText("-");
                    boolean pk = attr.path("relational-persistence").path("primary-key").asBoolean(false);
                    boolean autoGen = attr.path("relational-persistence").path("auto-generated").asBoolean(false);

                    StringBuilder flags = new StringBuilder();
                    if (pk) flags.append(" [PK]");
                    if (autoGen) flags.append(" [AUTO]");

                    JsonNode validations = attr.path("validations");
                    if (validations.isArray() && validations.size() > 0) {
                        List<String> valList = new ArrayList<>();
                        for (JsonNode v : validations) {
                            valList.add(v.path("type").asText());
                        }
                        flags.append(" {").append(String.join(", ", valList)).append("}");
                    }

                    ConsoleOutput.print(String.format("    %-20s %-10s → %-15s%s", name, type, column, flags));
                }
            }
            ConsoleOutput.blank();
        }
    }
}
