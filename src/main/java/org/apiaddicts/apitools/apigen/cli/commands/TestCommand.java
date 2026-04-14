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
 * Wrapper command that executes {@code mvn test} (or {@code verify})
 * inside a generated APIGen Spring Boot project.
 */
@Command(
    name = "test",
    description = "Run tests in a generated Spring Boot project.",
    mixinStandardHelpOptions = true
)
public class TestCommand implements Callable<Integer> {

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Option(names = {"-d", "--directory"}, description = "Project directory.",
            defaultValue = ".")
    private File directory;

    @Option(names = {"--integration"}, description = "Run integration tests (mvn verify) instead of unit tests.")
    private boolean integration;

    @Option(names = {"--class"}, description = "Run a specific test class (e.g., com.example.ItemTest).")
    private String testClass;

    @Option(names = {"--method"}, description = "Run a specific test method (e.g., testCreate). Requires --class.")
    private String testMethod;

    @Option(names = {"--fail-fast"}, description = "Stop at first test failure.")
    private boolean failFast;

    @Option(names = {"--report"}, description = "Open Surefire report after test run.")
    private boolean report;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        ConsoleOutput.header("Running Tests");

        Path projectDir = directory.toPath().toAbsolutePath();

        if (!projectDir.resolve("pom.xml").toFile().exists()) {
            ConsoleOutput.error("No pom.xml found in " + projectDir);
            return 1;
        }

        String mvn = ProcessRunner.resolveMaven(projectDir);
        ConsoleOutput.detail("Maven", mvn);
        ConsoleOutput.detail("Directory", projectDir.toString());
        ConsoleOutput.detail("Phase", integration ? "verify (integration)" : "test (unit)");
        if (testClass != null) ConsoleOutput.detail("Test Class", testClass);
        if (testMethod != null) ConsoleOutput.detail("Test Method", testMethod);
        ConsoleOutput.blank();

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(mvn);
            cmd.add(integration ? "verify" : "test");

            if (testClass != null) {
                String testSelector = testClass;
                if (testMethod != null) {
                    testSelector += "#" + testMethod;
                }
                cmd.add("-Dtest=" + testSelector);
            }
            if (failFast) {
                cmd.add("-Dsurefire.failIfNoSpecifiedTests=false");
                cmd.add("--fail-at-end");
            }
            if (parent != null && parent.verbose) {
                cmd.add("-X");
            }

            ConsoleOutput.step(1, 2, "Running: " + String.join(" ", cmd));
            ConsoleOutput.blank();

            int exitCode = ProcessRunner.exec(projectDir, cmd.toArray(new String[0]));

            ConsoleOutput.blank();
            if (exitCode == 0) {
                ConsoleOutput.step(2, 2, "Tests complete.");
                ConsoleOutput.success("All tests passed.");
            } else {
                ConsoleOutput.error("Tests failed with exit code: " + exitCode);
            }

            if (report) {
                Path reportPath = projectDir.resolve("target/surefire-reports");
                if (reportPath.toFile().exists()) {
                    ConsoleOutput.info("Test reports available at: " + reportPath);
                }
            }

            return exitCode;

        } catch (Exception e) {
            ConsoleOutput.error("Test execution failed: " + e.getMessage());
            if (parent != null && parent.verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
