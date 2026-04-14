package org.apiaddicts.apitools.apigen.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apiaddicts.apitools.apigen.cli.ApigenSpringBootCli;
import org.apiaddicts.apitools.apigen.cli.utils.ConsoleOutput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Manages apigen.yaml configuration files.
 */
@Command(
    name = "config",
    description = "View and manage APIGen project configuration (apigen.yaml).",
    mixinStandardHelpOptions = true,
    subcommands = {
        ConfigCommand.ShowConfig.class,
        ConfigCommand.SetConfig.class,
        ConfigCommand.InitConfig.class
    }
)
public class ConfigCommand implements Callable<Integer> {

    private static final String CONFIG_FILE = "apigen.yaml";

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        return new ShowConfig(this).call();
    }

    static ObjectMapper yamlMapper() {
        YAMLFactory factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        return new ObjectMapper(factory)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    static Path resolveConfigPath(File dir) {
        return (dir != null ? dir.toPath() : Path.of(".")).resolve(CONFIG_FILE);
    }

    @Command(name = "show", description = "Display the current APIGen configuration.", mixinStandardHelpOptions = true)
    static class ShowConfig implements Callable<Integer> {

        @ParentCommand
        private ConfigCommand configParent;

        @Option(names = {"-d", "--directory"}, description = "Project directory.", defaultValue = ".")
        private File directory;

        ShowConfig() {}

        ShowConfig(ConfigCommand parent) {
            this.configParent = parent;
        }

        @Override
        public Integer call() {
            ApigenSpringBootCli root = configParent != null ? configParent.parent : null;
            ConsoleOutput.init(root != null && root.noColor);
            ConsoleOutput.header("APIGen Configuration");

            Path configPath = resolveConfigPath(directory);
            if (!Files.exists(configPath)) {
                ConsoleOutput.warn("No " + CONFIG_FILE + " found in " + directory.getAbsolutePath());
                ConsoleOutput.info("Run 'apigen-springboot-cli config init' to create a default configuration.");
                return 0;
            }

            try {
                JsonNode config = yamlMapper().readTree(configPath.toFile());
                ConsoleOutput.detail("Config file", configPath.toAbsolutePath().toString());
                ConsoleOutput.blank();

                printNode(config, "");
                ConsoleOutput.blank();
                return 0;

            } catch (IOException e) {
                ConsoleOutput.error("Failed to read configuration: " + e.getMessage());
                return 1;
            }
        }

        private void printNode(JsonNode node, String indent) {
            if (node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    if (entry.getValue().isObject()) {
                        ConsoleOutput.print(indent + entry.getKey() + ":");
                        printNode(entry.getValue(), indent + "  ");
                    } else {
                        ConsoleOutput.detail(indent + entry.getKey(), entry.getValue().asText());
                    }
                }
            }
        }
    }

    @Command(name = "set", description = "Set a configuration value (e.g., 'config set project.group-id com.myorg').", mixinStandardHelpOptions = true)
    static class SetConfig implements Callable<Integer> {

        @ParentCommand
        private ConfigCommand configParent;

        @Parameters(index = "0", description = "Configuration key (dot-notation, e.g., project.group-id).")
        private String key;

        @Parameters(index = "1", description = "Value to set.")
        private String value;

        @Option(names = {"-d", "--directory"}, description = "Project directory.", defaultValue = ".")
        private File directory;

        @Override
        public Integer call() {
            ApigenSpringBootCli root = configParent != null ? configParent.parent : null;
            ConsoleOutput.init(root != null && root.noColor);

            Path configPath = resolveConfigPath(directory);
            ObjectMapper mapper = yamlMapper();

            try {
                ObjectNode config;
                if (Files.exists(configPath)) {
                    JsonNode existing = mapper.readTree(configPath.toFile());
                    config = existing.isObject() ? (ObjectNode) existing : mapper.createObjectNode();
                } else {
                    config = mapper.createObjectNode();
                }

                String[] parts = key.split("\\.");
                ObjectNode current = config;
                for (int i = 0; i < parts.length - 1; i++) {
                    JsonNode child = current.get(parts[i]);
                    if (child == null || !child.isObject()) {
                        ObjectNode newNode = mapper.createObjectNode();
                        current.set(parts[i], newNode);
                        current = newNode;
                    } else {
                        current = (ObjectNode) child;
                    }
                }
                current.put(parts[parts.length - 1], value);

                mapper.writeValue(configPath.toFile(), config);
                ConsoleOutput.success("Set " + key + " = " + value);
                return 0;

            } catch (IOException e) {
                ConsoleOutput.error("Failed to update configuration: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "init", description = "Create a default apigen.yaml configuration file.", mixinStandardHelpOptions = true)
    static class InitConfig implements Callable<Integer> {

        @ParentCommand
        private ConfigCommand configParent;

        @Option(names = {"-d", "--directory"}, description = "Target directory.", defaultValue = ".")
        private File directory;

        @Option(names = {"--force"}, description = "Overwrite existing configuration file.")
        private boolean force;

        @Override
        public Integer call() {
            ApigenSpringBootCli root = configParent != null ? configParent.parent : null;
            ConsoleOutput.init(root != null && root.noColor);

            Path configPath = resolveConfigPath(directory);

            if (Files.exists(configPath) && !force) {
                ConsoleOutput.error(CONFIG_FILE + " already exists. Use --force to overwrite.");
                return 1;
            }

            String defaultConfig = """
                    # APIGen Spring Boot CLI Configuration
                    # See: https://github.com/apiaddicts/apigen.springboot

                    spec: openapi.yaml
                    output: ./generated

                    project:
                      name: my-api
                      group-id: com.example
                      artifact-id: my-api
                      base-package: com.example.myapi

                    generator:
                      mode: local
                      parent-group: org.apiaddicts.apitools.apigen
                      parent-artifact: archetype-parent-spring-boot
                      parent-version: 2.0.3
                      docker-image: apiaddicts/apitools-apigen:2.0.3

                    options:
                      skip-validation: false
                    """;

            try {
                Files.createDirectories(configPath.getParent());
                Files.writeString(configPath, defaultConfig);
                ConsoleOutput.success("Created " + configPath.toAbsolutePath());
                ConsoleOutput.info("Edit the file to match your project settings.");
                return 0;

            } catch (IOException e) {
                ConsoleOutput.error("Failed to create configuration: " + e.getMessage());
                return 1;
            }
        }
    }
}
