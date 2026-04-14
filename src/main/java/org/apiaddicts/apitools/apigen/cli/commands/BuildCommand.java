package org.apiaddicts.apitools.apigen.cli.commands;

import org.apiaddicts.apitools.apigen.cli.ApigenSpringBootCli;
import org.apiaddicts.apitools.apigen.cli.utils.ConsoleOutput;
import org.apiaddicts.apitools.apigen.cli.utils.ProcessRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Wrapper command that runs {@code mvn clean install} (or a custom Maven goal)
 * inside a generated APIGen Spring Boot project directory.
 *
 * <p>Automatically detects and prefers the Maven Wrapper ({@code mvnw}) when present.</p>
 */
@Command(
    name = "build",
    description = "Build a generated Spring Boot project using Maven.",
    mixinStandardHelpOptions = true
)
public class BuildCommand implements Callable<Integer> {

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Option(names = {"-d", "--directory"}, description = "Project directory to build.",
            defaultValue = ".")
    private File directory;

    @Option(names = {"--goals"}, description = "Maven goals to execute (comma-separated).",
            defaultValue = "clean,install", split = ",")
    private List<String> goals;

    @Option(names = {"--skip-tests"}, description = "Skip tests during build.")
    private boolean skipTests;

    @Option(names = {"-P", "--profiles"}, description = "Maven profiles to activate (comma-separated).",
            split = ",")
    private List<String> profiles;

    @Option(names = {"--offline"}, description = "Run Maven in offline mode.")
    private boolean offline;

    @Option(names = {"--quiet"}, description = "Run Maven in quiet mode.")
    private boolean quiet;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        ConsoleOutput.header("Building APIGen Project");

        Path projectDir = directory.toPath().toAbsolutePath();

        // Validate project directory
        if (!projectDir.resolve("pom.xml").toFile().exists()) {
            ConsoleOutput.error("No pom.xml found in " + projectDir);
            ConsoleOutput.info("Make sure you are in a generated project directory, or use -d to specify one.");
            return 1;
        }

        String mvn = ProcessRunner.resolveMaven(projectDir);
        ConsoleOutput.detail("Maven", mvn);
        ConsoleOutput.detail("Directory", projectDir.toString());
        ConsoleOutput.detail("Goals", String.join(" ", goals));
        if (skipTests) ConsoleOutput.detail("Skip Tests", "true");
        if (profiles != null && !profiles.isEmpty()) {
            ConsoleOutput.detail("Profiles", String.join(", ", profiles));
        }
        ConsoleOutput.blank();

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(mvn);
            cmd.addAll(goals);

            if (skipTests) {
                cmd.add("-DskipTests");
            }
            if (profiles != null && !profiles.isEmpty()) {
                cmd.add("-P" + String.join(",", profiles));
            }
            if (offline) {
                cmd.add("--offline");
            }
            if (quiet) {
                cmd.add("-q");
            }
            if (parent != null && parent.verbose) {
                cmd.add("-X");
            }

            ConsoleOutput.step(1, 2, "Running: " + String.join(" ", cmd));
            ConsoleOutput.blank();

            int exitCode = ProcessRunner.exec(projectDir, cmd.toArray(new String[0]));

            ConsoleOutput.blank();
            if (exitCode == 0) {
                ConsoleOutput.step(2, 2, "Build complete.");
                ConsoleOutput.success("Project built successfully.");
            } else {
                ConsoleOutput.error("Build failed with exit code: " + exitCode);
            }
            return exitCode;

        } catch (Exception e) {
            ConsoleOutput.error("Build failed: " + e.getMessage());
            if (parent != null && parent.verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
