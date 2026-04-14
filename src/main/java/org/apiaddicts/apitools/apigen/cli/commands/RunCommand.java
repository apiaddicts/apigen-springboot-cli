package org.apiaddicts.apitools.apigen.cli.commands;

import org.apiaddicts.apitools.apigen.cli.ApigenSpringBootCli;
import org.apiaddicts.apitools.apigen.cli.utils.ConsoleOutput;
import org.apiaddicts.apitools.apigen.cli.utils.ProcessRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Wrapper command that starts a generated APIGen Spring Boot application
 * using {@code mvn spring-boot:run} or by directly running the packaged JAR.
 */
@Command(
    name = "run",
    description = "Run a generated Spring Boot application.",
    mixinStandardHelpOptions = true
)
public class RunCommand implements Callable<Integer> {

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Option(names = {"-d", "--directory"}, description = "Project directory.",
            defaultValue = ".")
    private File directory;

    @Option(names = {"--port"}, description = "Server port override.")
    private Integer port;

    @Option(names = {"--profile"}, description = "Spring profile to activate (e.g., dev, prod).")
    private String springProfile;

    @Option(names = {"--debug"}, description = "Enable remote debugging on port 5005.")
    private boolean debug;

    @Option(names = {"--jar"}, description = "Run a specific JAR file instead of using Maven.")
    private File jarFile;

    @Option(names = {"-P", "--maven-profiles"}, description = "Maven profiles to activate (e.g., h2).", split = ",")
    private List<String> mavenProfiles;

    @Option(names = {"--args"}, description = "Additional arguments to pass to the application.", split = " ")
    private List<String> appArgs;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        ConsoleOutput.header("Running APIGen Application");

        try {
            if (jarFile != null) {
                return runJar();
            } else {
                return runMaven();
            }
        } catch (Exception e) {
            ConsoleOutput.error("Failed to run application: " + e.getMessage());
            if (parent != null && parent.verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private int runMaven() throws Exception {
        Path projectDir = directory.toPath().toAbsolutePath();

        if (!projectDir.resolve("pom.xml").toFile().exists()) {
            ConsoleOutput.error("No pom.xml found in " + projectDir);
            ConsoleOutput.info("Use -d to specify the project directory, or --jar to run a specific JAR.");
            return 1;
        }

        String mvn = ProcessRunner.resolveMaven(projectDir);
        ConsoleOutput.detail("Maven", mvn);
        ConsoleOutput.detail("Directory", projectDir.toString());
        if (port != null) ConsoleOutput.detail("Port", String.valueOf(port));
        if (springProfile != null) ConsoleOutput.detail("Spring Profile", springProfile);
        if (mavenProfiles != null) ConsoleOutput.detail("Maven Profiles", String.join(",", mavenProfiles));
        if (debug) ConsoleOutput.detail("Debug", "enabled (port 5005)");
        ConsoleOutput.blank();

        List<String> cmd = new ArrayList<>();
        cmd.add(mvn);
        cmd.add("spring-boot:run");

        if (mavenProfiles != null && !mavenProfiles.isEmpty()) {
            cmd.add("-P" + String.join(",", mavenProfiles));
        }

        // Build the -Dspring-boot.run.arguments value
        List<String> jvmArgs = new ArrayList<>();
        List<String> runArgs = new ArrayList<>();

        if (port != null) {
            runArgs.add("--server.port=" + port);
        }
        if (springProfile != null) {
            runArgs.add("--spring.profiles.active=" + springProfile);
        }
        if (debug) {
            jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005");
        }
        if (appArgs != null) {
            runArgs.addAll(appArgs);
        }

        if (!runArgs.isEmpty()) {
            cmd.add("-Dspring-boot.run.arguments=" + String.join(",", runArgs));
        }
        if (!jvmArgs.isEmpty()) {
            cmd.add("-Dspring-boot.run.jvmArguments=" + String.join(" ", jvmArgs));
        }

        ConsoleOutput.info("Starting application...");
        ConsoleOutput.command(String.join(" ", cmd));
        ConsoleOutput.blank();

        return ProcessRunner.exec(projectDir, cmd.toArray(new String[0]));
    }

    private int runJar() throws Exception {
        if (!jarFile.exists()) {
            ConsoleOutput.error("JAR file not found: " + jarFile.getAbsolutePath());
            return 1;
        }

        ConsoleOutput.detail("JAR", jarFile.getAbsolutePath());
        if (port != null) ConsoleOutput.detail("Port", String.valueOf(port));
        if (springProfile != null) ConsoleOutput.detail("Spring Profile", springProfile);
        if (debug) ConsoleOutput.detail("Debug", "enabled (port 5005)");
        ConsoleOutput.blank();

        List<String> cmd = new ArrayList<>();
        cmd.add("java");

        if (debug) {
            cmd.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005");
        }

        cmd.add("-jar");
        cmd.add(jarFile.getAbsolutePath());

        if (port != null) {
            cmd.add("--server.port=" + port);
        }
        if (springProfile != null) {
            cmd.add("--spring.profiles.active=" + springProfile);
        }
        if (appArgs != null) {
            cmd.addAll(appArgs);
        }

        ConsoleOutput.info("Starting application...");
        ConsoleOutput.command(String.join(" ", cmd));
        ConsoleOutput.blank();

        return ProcessRunner.exec(jarFile.getAbsoluteFile().getParentFile().toPath(),
                cmd.toArray(new String[0]));
    }
}
