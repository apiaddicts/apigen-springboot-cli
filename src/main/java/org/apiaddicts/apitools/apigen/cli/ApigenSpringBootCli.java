package org.apiaddicts.apitools.apigen.cli;

import org.apiaddicts.apitools.apigen.cli.commands.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * APIGen Spring Boot CLI — a command-line wrapper/runner for the
 * <a href="https://github.com/apiaddicts/apigen.springboot">apigen.springboot</a> code generator.
 *
 * <p>Provides a unified interface to initialise projects, validate and generate code
 * from OpenAPI specifications, and manage the full lifecycle of a generated Spring Boot
 * application (build, run, test, clean).</p>
 *
 * <h2>Distribution</h2>
 * <ul>
 *   <li>Fat JAR: {@code java -jar apigen-springboot-cli.jar <command>}</li>
 *   <li>Native executable (GraalVM): {@code ./apigen-springboot-cli <command>}</li>
 * </ul>
 */
@Command(
    name = "apigen-springboot-cli",
    description = "APIGen Spring Boot CLI — Generate and manage Spring Boot projects from OpenAPI specifications.",
    version = "apigen-springboot-cli 2.1.0",
    mixinStandardHelpOptions = true,
    subcommands = {
        // ── Project lifecycle ─────────────────────
        InitCommand.class,
        GenerateCommand.class,
        ValidateCommand.class,
        PreviewCommand.class,
        // ── Wrapper / runner ──────────────────────
        BuildCommand.class,
        RunCommand.class,
        TestCommand.class,
        CleanCommand.class,
        // ── Configuration & tooling ───────────────
        ConfigCommand.class,
        DockerCommand.class,
        VersionCommand.class,
        CommandLine.HelpCommand.class
    },
    header = {
        "",
        "    _    ____ ___                                 ____              _   ",
        "   / \\  |  _ \\_ _| __ _  ___ _ __               | __ )  ___   ___ | |_ ",
        "  / _ \\ | |_) | | / _` |/ _ \\ '_ \\    _____     |  _ \\ / _ \\ / _ \\| __|",
        " / ___ \\|  __/| || (_| |  __/ | | |  |_____|    | |_) | (_) | (_) | |_ ",
        "/_/   \\_\\_|  |___\\__, |\\___|_| |_|              |____/ \\___/ \\___/ \\__|",
        "                 |___/                                                  ",
        "  Spring Boot Code Generator CLI  v2.1.0",
        ""
    },
    footer = {
        "",
        "Lifecycle commands:",
        "  init       Create a new project scaffold with a sample OpenAPI spec",
        "  generate   Generate a Spring Boot project from an OpenAPI specification",
        "  build      Build the generated project (mvn clean install)",
        "  run        Run the generated Spring Boot application",
        "  test       Run tests in the generated project",
        "  clean      Clean build artifacts",
        "",
        "Inspection commands:",
        "  validate   Validate an OpenAPI spec for APIGen compatibility",
        "  preview    Preview what would be generated without writing files",
        "",
        "Tooling commands:",
        "  config     View and manage project configuration (apigen.yaml)",
        "  docker     Manage the APIGen generator Docker container",
        "  version    Show detailed version and environment information",
        "",
        "Examples:",
        "  apigen-springboot-cli init -n my-api",
        "  apigen-springboot-cli validate -f openapi.yaml",
        "  apigen-springboot-cli generate -f openapi.yaml -o ./out",
        "  apigen-springboot-cli build -d ./out",
        "  apigen-springboot-cli run -d ./out --port 9090",
        "  apigen-springboot-cli test -d ./out --integration",
        "",
        "Documentation: https://github.com/apiaddicts/apigen.springboot",
        "Community:     https://discord.gg/ZdbGqMBYy8",
        ""
    }
)
public class ApigenSpringBootCli implements Callable<Integer> {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output.",
            scope = CommandLine.ScopeType.INHERIT)
    public boolean verbose;

    @Option(names = {"--no-color"}, description = "Disable colored output.",
            scope = CommandLine.ScopeType.INHERIT)
    public boolean noColor;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ApigenSpringBootCli())
                .setExecutionStrategy(new CommandLine.RunLast())
                .setUsageHelpAutoWidth(true)
                .setUsageHelpLongOptionsMaxWidth(40)
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }
}
