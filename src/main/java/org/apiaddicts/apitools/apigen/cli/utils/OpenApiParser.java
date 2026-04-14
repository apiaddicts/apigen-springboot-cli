package org.apiaddicts.apitools.apigen.cli.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Utility class for parsing and inspecting OpenAPI specs with x-apigen extensions.
 */
public final class OpenApiParser {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private OpenApiParser() {}

    /**
     * Parse an OpenAPI file (YAML or JSON) into a JsonNode tree.
     */
    public static JsonNode parse(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return YAML_MAPPER.readTree(file);
        } else {
            return JSON_MAPPER.readTree(file);
        }
    }

    public static JsonNode getApigenProject(JsonNode root) {
        return root.path("x-apigen-project");
    }

    public static JsonNode getApigenModels(JsonNode root) {
        JsonNode models = root.path("x-apigen-models");
        if (models.isMissingNode()) {
            models = root.path("components").path("x-apigen-models");
        }
        return models;
    }

    public static String getTitle(JsonNode root) {
        return root.path("info").path("title").asText("Untitled API");
    }

    public static String getVersion(JsonNode root) {
        return root.path("info").path("version").asText("0.0.0");
    }

    public static String getOpenApiVersion(JsonNode root) {
        if (root.has("openapi")) {
            return root.get("openapi").asText();
        } else if (root.has("swagger")) {
            return "2.0 (Swagger)";
        }
        return "unknown";
    }

    public static List<String> getPaths(JsonNode root) {
        List<String> paths = new ArrayList<>();
        JsonNode pathsNode = root.path("paths");
        if (!pathsNode.isMissingNode()) {
            pathsNode.fieldNames().forEachRemaining(paths::add);
        }
        Collections.sort(paths);
        return paths;
    }

    public static List<String> getModelNames(JsonNode root) {
        List<String> models = new ArrayList<>();
        JsonNode modelsNode = getApigenModels(root);
        if (!modelsNode.isMissingNode()) {
            modelsNode.fieldNames().forEachRemaining(models::add);
        }
        Collections.sort(models);
        return models;
    }

    public static List<String> getSchemaNames(JsonNode root) {
        List<String> schemas = new ArrayList<>();
        JsonNode schemasNode = root.path("components").path("schemas");
        if (!schemasNode.isMissingNode()) {
            schemasNode.fieldNames().forEachRemaining(schemas::add);
        }
        Collections.sort(schemas);
        return schemas;
    }

    public static Map<String, Integer> countOperationsByMethod(JsonNode root) {
        Map<String, Integer> counts = new TreeMap<>();
        JsonNode pathsNode = root.path("paths");
        if (!pathsNode.isMissingNode()) {
            pathsNode.fields().forEachRemaining(pathEntry -> {
                pathEntry.getValue().fieldNames().forEachRemaining(method -> {
                    String m = method.toUpperCase();
                    if (Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(m)) {
                        counts.merge(m, 1, Integer::sum);
                    }
                });
            });
        }
        return counts;
    }

    public static boolean hasApigenExtensions(JsonNode root) {
        return !root.path("x-apigen-project").isMissingNode()
                || !root.path("x-apigen-models").isMissingNode()
                || !root.path("components").path("x-apigen-models").isMissingNode();
    }

    public static String getBasePackage(JsonNode root) {
        JsonNode project = root.path("x-apigen-project");
        JsonNode javaProps = project.path("java-properties");
        // Check java-properties first (generator-core format), then top-level (legacy)
        String value = javaProps.path("base-package").asText(
                javaProps.path("basePackage").asText(""));
        if (value.isEmpty()) {
            value = project.path("base-package").asText(
                    project.path("basePackage").asText(""));
        }
        return value;
    }

    public static String getArtifactId(JsonNode root) {
        JsonNode project = root.path("x-apigen-project");
        JsonNode javaProps = project.path("java-properties");
        String value = javaProps.path("artifact-id").asText(
                javaProps.path("artifactId").asText(""));
        if (value.isEmpty()) {
            value = project.path("artifact-id").asText(
                    project.path("artifactId").asText(""));
        }
        return value;
    }

    public static String getGroupId(JsonNode root) {
        JsonNode project = root.path("x-apigen-project");
        JsonNode javaProps = project.path("java-properties");
        String value = javaProps.path("group-id").asText(
                javaProps.path("groupId").asText(""));
        if (value.isEmpty()) {
            value = project.path("group-id").asText(
                    project.path("groupId").asText(""));
        }
        return value;
    }
}
