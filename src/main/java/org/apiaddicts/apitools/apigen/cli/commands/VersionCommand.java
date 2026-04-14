package org.apiaddicts.apitools.apigen.cli.commands;

import org.apiaddicts.apitools.apigen.cli.ApigenSpringBootCli;
import org.apiaddicts.apitools.apigen.cli.utils.ConsoleOutput;
import org.apiaddicts.apitools.apigen.cli.utils.ProcessRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Displays detailed version information including the CLI version,
 * Java runtime, OS, and availability of Maven and Docker.
 */
@Command(
    name = "version",
    description = "Show detailed version and environment information.",
    mixinStandardHelpOptions = true
)
public class VersionCommand implements Callable<Integer> {

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        ConsoleOutput.header("APIGen Spring Boot CLI - Environment");

        // CLI version
        String version = getClass().getPackage().getImplementationVersion();
        ConsoleOutput.detail("CLI Version", version != null ? version : "2.1.0-SNAPSHOT (dev)");

        // Java info
        ConsoleOutput.detail("Java Version", System.getProperty("java.version", "unknown"));
        ConsoleOutput.detail("Java Vendor", System.getProperty("java.vendor", "unknown"));
        ConsoleOutput.detail("Java Home", System.getProperty("java.home", "unknown"));

        // OS info
        ConsoleOutput.detail("OS", System.getProperty("os.name", "unknown") + " "
                + System.getProperty("os.version", "") + " "
                + System.getProperty("os.arch", ""));

        ConsoleOutput.blank();

        // Maven availability
        boolean mvnAvailable = ProcessRunner.isAvailable("mvn");
        if (mvnAvailable) {
            try {
                ProcessRunner.Result mvnResult = ProcessRunner.capture(null, 10, "mvn", "--version");
                String firstLine = mvnResult.stdout().lines().findFirst().orElse("unknown");
                ConsoleOutput.detail("Maven", firstLine);
            } catch (Exception e) {
                ConsoleOutput.detail("Maven", "available (version check failed)");
            }
        } else {
            ConsoleOutput.warn("Maven is not available on PATH.");
        }

        // Docker availability
        boolean dockerAvailable = ProcessRunner.isAvailable("docker");
        if (dockerAvailable) {
            try {
                ProcessRunner.Result dockerResult = ProcessRunner.capture(null, 10,
                        "docker", "version", "--format", "{{.Client.Version}}");
                ConsoleOutput.detail("Docker", dockerResult.stdout());
            } catch (Exception e) {
                ConsoleOutput.detail("Docker", "available (version check failed)");
            }
        } else {
            ConsoleOutput.warn("Docker is not available on PATH.");
        }

        // GraalVM check
        String vmName = System.getProperty("java.vm.name", "");
        if (vmName.toLowerCase().contains("graalvm") || vmName.toLowerCase().contains("substrate")) {
            ConsoleOutput.detail("GraalVM", "detected (" + vmName + ")");
        }

        ConsoleOutput.blank();
        return 0;
    }
}
