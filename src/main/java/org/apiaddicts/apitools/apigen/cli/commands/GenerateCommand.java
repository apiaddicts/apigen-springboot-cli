package org.apiaddicts.apitools.apigen.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import org.apiaddicts.apitools.apigen.cli.ApigenSpringBootCli;
import org.apiaddicts.apitools.apigen.cli.utils.ConsoleOutput;
import org.apiaddicts.apitools.apigen.cli.utils.OpenApiParser;
import org.apiaddicts.apitools.apigen.cli.utils.ProcessRunner;
import org.apiaddicts.apitools.apigen.generatorcore.generator.Project;
import org.apiaddicts.apitools.apigen.generatorcore.generator.ProjectGenerator;
import org.apiaddicts.apitools.apigen.generatorcore.generator.implementations.java.apigen.ApigenGenerationStrategy;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import jakarta.validation.ConstraintViolation;
import org.apiaddicts.apitools.apigen.generatorcore.exceptions.InvalidValuesException;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Generate command that validates an OpenAPI spec, shows a summary,
 * then invokes the apigen.springboot generator (in-process or Docker).
 */
@Command(
    name = "generate",
    description = "Generate a Spring Boot project from an OpenAPI specification.",
    mixinStandardHelpOptions = true
)
public class GenerateCommand implements Callable<Integer> {

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Option(names = {"-f", "--file"}, description = "Path to the OpenAPI specification file.", required = true)
    private File specFile;

    @Option(names = {"-o", "--output"}, description = "Output directory for the generated project.", required = true)
    private File outputDir;

    @Option(names = {"--skip-validation"}, description = "Skip OpenAPI spec validation before generation.")
    private boolean skipValidation;

    @Option(names = {"--dry-run"}, description = "Show what would be generated without writing files.")
    private boolean dryRun;

    @Option(names = {"--parent-group"}, description = "Maven parent groupId for the generated project.",
            defaultValue = "org.apiaddicts.apitools.apigen")
    private String parentGroup;

    @Option(names = {"--parent-artifact"}, description = "Maven parent artifactId for the generated project.",
            defaultValue = "archetype-parent-spring-boot")
    private String parentArtifact;

    @Option(names = {"--parent-version"}, description = "Maven parent version for the generated project.",
            defaultValue = "2.0.3")
    private String parentVersion;

    @Option(names = {"--docker"}, description = "Use Docker-based generator instead of in-process generation.")
    private boolean useDocker;

    @Option(names = {"--docker-image"}, description = "Docker image for the generator.",
            defaultValue = "apiaddicts/apitools-apigen:2.0.3")
    private String dockerImage;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        ConsoleOutput.header("APIGen Code Generator");

        if (!specFile.exists()) {
            ConsoleOutput.error("Specification file not found: " + specFile.getAbsolutePath());
            return 1;
        }

        try {
            JsonNode root = OpenApiParser.parse(specFile);

            // Show summary
            ConsoleOutput.detail("Specification", specFile.getName());
            ConsoleOutput.detail("OpenAPI Version", OpenApiParser.getOpenApiVersion(root));
            ConsoleOutput.detail("API Title", OpenApiParser.getTitle(root));

            if (OpenApiParser.hasApigenExtensions(root)) {
                String groupId = OpenApiParser.getGroupId(root);
                String artifactId = OpenApiParser.getArtifactId(root);
                String basePackage = OpenApiParser.getBasePackage(root);
                if (!groupId.isBlank()) ConsoleOutput.detail("Group ID", groupId);
                if (!artifactId.isBlank()) ConsoleOutput.detail("Artifact ID", artifactId);
                if (!basePackage.isBlank()) ConsoleOutput.detail("Base Package", basePackage);
            }

            List<String> models = OpenApiParser.getModelNames(root);
            List<String> paths = OpenApiParser.getPaths(root);
            Map<String, Integer> ops = OpenApiParser.countOperationsByMethod(root);

            ConsoleOutput.detail("Models", String.valueOf(models.size()));
            ConsoleOutput.detail("Paths", String.valueOf(paths.size()));
            ConsoleOutput.detail("Operations", ops.toString());
            ConsoleOutput.detail("Output", outputDir.getAbsolutePath());
            ConsoleOutput.blank();

            // Validation (unless skipped)
            if (!skipValidation) {
                ConsoleOutput.step(1, 3, "Validating specification...");
                if (!OpenApiParser.hasApigenExtensions(root)) {
                    ConsoleOutput.error("Specification is missing x-apigen extensions. Use 'apigen-springboot-cli validate' for details.");
                    return 1;
                }
                ConsoleOutput.success("Validation passed.");
            }

            // Dry run
            if (dryRun) {
                ConsoleOutput.step(2, 3, "Dry run - files that would be generated:");
                ConsoleOutput.blank();
                showProjectionSummary(root);
                ConsoleOutput.blank();
                ConsoleOutput.info("Dry run complete. No files were written.");
                return 0;
            }

            // Ensure output directory
            Files.createDirectories(outputDir.toPath());

            // Execute generation
            ConsoleOutput.step(2, 3, "Generating Spring Boot project...");

            if (useDocker) {
                int result = runDockerGeneration();
                if (result != 0) {
                    ConsoleOutput.error("Generation failed with exit code: " + result);
                    return result;
                }
            } else {
                runInProcessGeneration();
            }

            ConsoleOutput.step(3, 3, "Generation complete.");
            ConsoleOutput.blank();
            ConsoleOutput.success("Project generated successfully at: " + outputDir.getAbsolutePath());
            ConsoleOutput.blank();
            ConsoleOutput.info("Next steps:");
            ConsoleOutput.command("apigen-springboot-cli build -d " + outputDir.getPath());
            ConsoleOutput.command("apigen-springboot-cli run -d " + outputDir.getPath());
            ConsoleOutput.command("apigen-springboot-cli test -d " + outputDir.getPath());
            ConsoleOutput.blank();
            return 0;

        } catch (InvalidValuesException e) {
            ConsoleOutput.error("Generation failed: configuration validation errors.");
            for (ConstraintViolation<?> v : e.getViolations()) {
                ConsoleOutput.error("  - " + v.getPropertyPath() + ": " + v.getMessage()
                        + " (value: " + v.getInvalidValue() + ")");
            }
            return 1;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            ConsoleOutput.error("Generation failed: " + msg);
            if (e.getCause() != null) {
                ConsoleOutput.error("Caused by: " + e.getCause());
            }
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * Invokes the apigen generator-core API directly in-process.
     * No external JAR or subprocess needed.
     */
    private void runInProcessGeneration() throws IOException {
        Map<String, Object> globalConfig = new HashMap<>();
        globalConfig.put("parentGroup", parentGroup);
        globalConfig.put("parentArtifact", parentArtifact);
        globalConfig.put("parentVersion", parentVersion);

        ProjectGenerator<?> generator = new ProjectGenerator<>(
                new ApigenGenerationStrategy(), globalConfig);

        // Use the file path so the Swagger parser can resolve $ref references
        Project project = generator.generate(specFile.toPath());

        unzipToOutput(project);
    }

    /**
     * Extracts the generated project ZIP into the output directory.
     */
    private void unzipToOutput(Project project) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(project.getContent().getByteArray()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(outputDir, entry.getName());
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private int runDockerGeneration() throws IOException, InterruptedException {
        ConsoleOutput.info("Using Docker image: " + dockerImage);

        if (!ProcessRunner.isAvailable("docker")) {
            ConsoleOutput.error("Docker is not available. Please install Docker and ensure it is running.");
            return 1;
        }

        String workDir = specFile.getAbsoluteFile().getParent();
        String specName = specFile.getName();

        return ProcessRunner.exec(null,
                "docker", "run", "--rm",
                "-v", workDir + ":/workspace",
                "-v", outputDir.getAbsolutePath() + ":/output",
                dockerImage,
                "generate",
                "-f", "/workspace/" + specName,
                "-o", "/output");
    }

    private void showProjectionSummary(JsonNode root) {
        List<String> models = OpenApiParser.getModelNames(root);
        String basePackage = OpenApiParser.getBasePackage(root);
        String pkg = basePackage.isBlank() ? "com.example" : basePackage;
        String pkgPath = pkg.replace('.', '/');

        ConsoleOutput.print("  src/main/java/" + pkgPath + "/");
        for (String model : models) {
            String lower = model.toLowerCase();
            ConsoleOutput.print("    " + lower + "/");
            ConsoleOutput.print("      " + model + ".java              (Entity)");
            ConsoleOutput.print("      " + model + "Repository.java    (Repository)");
            ConsoleOutput.print("      " + model + "Service.java       (Service)");
            ConsoleOutput.print("      " + model + "Controller.java    (Controller)");
        }
        ConsoleOutput.print("  src/main/resources/");
        ConsoleOutput.print("    application.properties");
        ConsoleOutput.print("  pom.xml");
    }
}
