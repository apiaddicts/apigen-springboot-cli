package org.apiaddicts.apitools.apigen.cli.commands;

import org.apiaddicts.apitools.apigen.cli.ApigenSpringBootCli;
import org.apiaddicts.apitools.apigen.cli.utils.ConsoleOutput;
import org.apiaddicts.apitools.apigen.cli.utils.ProcessRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Wrapper command that cleans a generated project's build artifacts.
 * Supports Maven clean and optional removal of IDE/generated files.
 */
@Command(
    name = "clean",
    description = "Clean build artifacts from a generated Spring Boot project.",
    mixinStandardHelpOptions = true
)
public class CleanCommand implements Callable<Integer> {

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Option(names = {"-d", "--directory"}, description = "Project directory.",
            defaultValue = ".")
    private File directory;

    @Option(names = {"--deep"}, description = "Also remove IDE files (.idea, .vscode, *.iml) and logs.")
    private boolean deep;

    @Option(names = {"--generated"}, description = "Remove the entire generated output directory.")
    private boolean removeGenerated;

    @Option(names = {"--output"}, description = "Path to the generated output directory to remove.",
            defaultValue = "./generated")
    private File generatedDir;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        ConsoleOutput.header("Cleaning Project");

        Path projectDir = directory.toPath().toAbsolutePath();
        int totalSteps = 1 + (deep ? 1 : 0) + (removeGenerated ? 1 : 0);
        int step = 0;

        // Maven clean
        if (projectDir.resolve("pom.xml").toFile().exists()) {
            step++;
            ConsoleOutput.step(step, totalSteps, "Running Maven clean...");
            try {
                String mvn = ProcessRunner.resolveMaven(projectDir);
                List<String> cmd = new ArrayList<>();
                cmd.add(mvn);
                cmd.add("clean");
                if (parent != null && parent.verbose) {
                    cmd.add("-X");
                }
                int exitCode = ProcessRunner.exec(projectDir, cmd.toArray(new String[0]));
                if (exitCode != 0) {
                    ConsoleOutput.warn("Maven clean returned exit code: " + exitCode);
                }
            } catch (Exception e) {
                ConsoleOutput.warn("Maven clean failed: " + e.getMessage());
            }
        } else {
            step++;
            ConsoleOutput.step(step, totalSteps, "No pom.xml found, skipping Maven clean.");
        }

        // Deep clean
        if (deep) {
            step++;
            ConsoleOutput.step(step, totalSteps, "Removing IDE and log files...");
            cleanPath(projectDir.resolve(".idea"));
            cleanPath(projectDir.resolve(".vscode"));
            cleanPath(projectDir.resolve(".settings"));
            cleanPath(projectDir.resolve(".classpath"));
            cleanPath(projectDir.resolve(".project"));
            // Remove *.iml files
            try {
                try (var stream = Files.walk(projectDir, 2)) {
                    stream.filter(p -> p.toString().endsWith(".iml"))
                          .forEach(this::cleanPath);
                }
            } catch (IOException e) {
                ConsoleOutput.warn("Could not scan for .iml files: " + e.getMessage());
            }
            // Remove log files
            cleanPath(projectDir.resolve("logs"));
            ConsoleOutput.success("IDE and log files removed.");
        }

        // Remove generated directory
        if (removeGenerated) {
            step++;
            Path genPath = generatedDir.toPath().toAbsolutePath();
            ConsoleOutput.step(step, totalSteps, "Removing generated directory: " + genPath);
            if (genPath.toFile().exists()) {
                cleanPath(genPath);
                ConsoleOutput.success("Generated directory removed.");
            } else {
                ConsoleOutput.info("Generated directory does not exist: " + genPath);
            }
        }

        ConsoleOutput.blank();
        ConsoleOutput.success("Clean complete.");
        return 0;
    }

    private void cleanPath(Path path) {
        try {
            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    Files.walkFileTree(path, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    Files.delete(path);
                }
            }
        } catch (IOException e) {
            ConsoleOutput.warn("Could not remove " + path + ": " + e.getMessage());
        }
    }
}
