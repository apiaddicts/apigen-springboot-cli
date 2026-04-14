package org.apiaddicts.apitools.apigen.cli.commands;

import org.apiaddicts.apitools.apigen.cli.ApigenSpringBootCli;
import org.apiaddicts.apitools.apigen.cli.utils.ConsoleOutput;
import org.apiaddicts.apitools.apigen.cli.utils.ProcessRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.*;
import java.util.concurrent.Callable;

/**
 * Docker integration commands for managing the APIGen generator container.
 */
@Command(
    name = "docker",
    description = "Manage the APIGen generator Docker container.",
    mixinStandardHelpOptions = true,
    subcommands = {
        DockerCommand.PullImage.class,
        DockerCommand.StartContainer.class,
        DockerCommand.StopContainer.class,
        DockerCommand.StatusContainer.class
    }
)
public class DockerCommand implements Callable<Integer> {

    private static final String DEFAULT_IMAGE = "apiaddicts/apitools-apigen:2.0.3";
    private static final String CONTAINER_NAME = "apigen-generator";

    @ParentCommand
    private ApigenSpringBootCli parent;

    @Override
    public Integer call() {
        ConsoleOutput.init(parent != null && parent.noColor);
        ConsoleOutput.header("APIGen Docker Manager");
        ConsoleOutput.info("Use a subcommand: pull, start, stop, status");
        ConsoleOutput.blank();
        return 0;
    }

    static boolean isDockerAvailable() {
        return ProcessRunner.isAvailable("docker");
    }

    static ProcessRunner.Result captureDocker(String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "docker";
        System.arraycopy(args, 0, cmd, 1, args.length);
        return ProcessRunner.capture(null, 30, cmd);
    }

    static int runDocker(String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "docker";
        System.arraycopy(args, 0, cmd, 1, args.length);
        return ProcessRunner.exec(null, cmd);
    }

    // ── Subcommands ──────────────────────────────────────────────────────

    @Command(name = "pull", description = "Pull the latest APIGen generator Docker image.", mixinStandardHelpOptions = true)
    static class PullImage implements Callable<Integer> {

        @ParentCommand
        private DockerCommand dockerParent;

        @Option(names = {"--image"}, description = "Docker image to pull.", defaultValue = DEFAULT_IMAGE)
        private String image;

        @Override
        public Integer call() {
            ApigenSpringBootCli root = dockerParent != null ? dockerParent.parent : null;
            ConsoleOutput.init(root != null && root.noColor);
            ConsoleOutput.header("Pulling APIGen Docker Image");

            if (!isDockerAvailable()) {
                ConsoleOutput.error("Docker is not available. Please install Docker and ensure it is running.");
                return 1;
            }

            try {
                ConsoleOutput.info("Pulling image: " + image);
                ConsoleOutput.blank();
                int result = runDocker("pull", image);

                if (result == 0) {
                    ConsoleOutput.blank();
                    ConsoleOutput.success("Image pulled successfully: " + image);
                } else {
                    ConsoleOutput.error("Failed to pull image. Exit code: " + result);
                }
                return result;

            } catch (Exception e) {
                ConsoleOutput.error("Docker pull failed: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "start", description = "Start the APIGen generator container.", mixinStandardHelpOptions = true)
    static class StartContainer implements Callable<Integer> {

        @ParentCommand
        private DockerCommand dockerParent;

        @Option(names = {"--image"}, description = "Docker image to use.", defaultValue = DEFAULT_IMAGE)
        private String image;

        @Option(names = {"-p", "--port"}, description = "Host port to map.", defaultValue = "8080")
        private int port;

        @Option(names = {"-w", "--workspace"}, description = "Workspace directory to mount.", defaultValue = ".")
        private File workspace;

        @Option(names = {"--detach"}, description = "Run container in the background.", defaultValue = "true")
        private boolean detach;

        @Override
        public Integer call() {
            ApigenSpringBootCli root = dockerParent != null ? dockerParent.parent : null;
            ConsoleOutput.init(root != null && root.noColor);
            ConsoleOutput.header("Starting APIGen Container");

            if (!isDockerAvailable()) {
                ConsoleOutput.error("Docker is not available. Please install Docker and ensure it is running.");
                return 1;
            }

            try {
                ProcessRunner.Result existing = captureDocker("ps", "-q", "-f", "name=" + CONTAINER_NAME);
                if (!existing.stdout().isBlank()) {
                    ConsoleOutput.warn("Container '" + CONTAINER_NAME + "' is already running.");
                    ConsoleOutput.info("Use 'apigen-springboot-cli docker stop' to stop it first.");
                    return 0;
                }

                ProcessRunner.Result stopped = captureDocker("ps", "-aq", "-f", "name=" + CONTAINER_NAME);
                if (!stopped.stdout().isBlank()) {
                    runDocker("rm", CONTAINER_NAME);
                }

                ConsoleOutput.detail("Image", image);
                ConsoleOutput.detail("Port", port + ":8080");
                ConsoleOutput.detail("Workspace", workspace.getAbsolutePath());
                ConsoleOutput.blank();

                int result;
                if (detach) {
                    result = runDocker("run", "-d",
                            "--name", CONTAINER_NAME,
                            "-p", port + ":8080",
                            "-v", workspace.getAbsolutePath() + ":/workspace",
                            "-w", "/workspace",
                            image);
                } else {
                    result = runDocker("run", "--rm",
                            "--name", CONTAINER_NAME,
                            "-p", port + ":8080",
                            "-v", workspace.getAbsolutePath() + ":/workspace",
                            "-w", "/workspace",
                            image);
                }

                if (result == 0) {
                    ConsoleOutput.blank();
                    ConsoleOutput.success("Container started successfully.");
                    ConsoleOutput.info("Generator available at http://localhost:" + port);
                } else {
                    ConsoleOutput.error("Failed to start container. Exit code: " + result);
                }
                return result;

            } catch (Exception e) {
                ConsoleOutput.error("Failed to start container: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "stop", description = "Stop the running APIGen generator container.", mixinStandardHelpOptions = true)
    static class StopContainer implements Callable<Integer> {

        @ParentCommand
        private DockerCommand dockerParent;

        @Override
        public Integer call() {
            ApigenSpringBootCli root = dockerParent != null ? dockerParent.parent : null;
            ConsoleOutput.init(root != null && root.noColor);
            ConsoleOutput.header("Stopping APIGen Container");

            if (!isDockerAvailable()) {
                ConsoleOutput.error("Docker is not available.");
                return 1;
            }

            try {
                ProcessRunner.Result running = captureDocker("ps", "-q", "-f", "name=" + CONTAINER_NAME);
                if (running.stdout().isBlank()) {
                    ConsoleOutput.info("No running container found with name '" + CONTAINER_NAME + "'.");
                    return 0;
                }

                ConsoleOutput.info("Stopping container '" + CONTAINER_NAME + "'...");
                int result = runDocker("stop", CONTAINER_NAME);

                if (result == 0) {
                    runDocker("rm", CONTAINER_NAME);
                    ConsoleOutput.success("Container stopped and removed.");
                } else {
                    ConsoleOutput.error("Failed to stop container. Exit code: " + result);
                }
                return result;

            } catch (Exception e) {
                ConsoleOutput.error("Failed to stop container: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "status", description = "Show the status of the APIGen generator container.", mixinStandardHelpOptions = true)
    static class StatusContainer implements Callable<Integer> {

        @ParentCommand
        private DockerCommand dockerParent;

        @Override
        public Integer call() {
            ApigenSpringBootCli root = dockerParent != null ? dockerParent.parent : null;
            ConsoleOutput.init(root != null && root.noColor);
            ConsoleOutput.header("APIGen Container Status");

            if (!isDockerAvailable()) {
                ConsoleOutput.error("Docker is not available.");
                return 1;
            }

            try {
                ProcessRunner.Result running = captureDocker("ps", "-q", "-f", "name=" + CONTAINER_NAME);
                if (!running.stdout().isBlank()) {
                    ConsoleOutput.success("Container '" + CONTAINER_NAME + "' is RUNNING.");
                    ConsoleOutput.blank();

                    ProcessRunner.Result info = captureDocker("inspect", "--format",
                            "Image: {{.Config.Image}}\nCreated: {{.Created}}\nStatus: {{.State.Status}}\nPorts: {{.NetworkSettings.Ports}}",
                            CONTAINER_NAME);
                    ConsoleOutput.print(info.stdout());
                } else {
                    ProcessRunner.Result stopped = captureDocker("ps", "-aq", "-f", "name=" + CONTAINER_NAME);
                    if (!stopped.stdout().isBlank()) {
                        ConsoleOutput.warn("Container '" + CONTAINER_NAME + "' exists but is STOPPED.");
                        ConsoleOutput.info("Run 'apigen-springboot-cli docker start' to start it.");
                    } else {
                        ConsoleOutput.info("No container found with name '" + CONTAINER_NAME + "'.");
                        ConsoleOutput.info("Run 'apigen-springboot-cli docker start' to create and start one.");
                    }
                }
                ConsoleOutput.blank();
                return 0;

            } catch (Exception e) {
                ConsoleOutput.error("Failed to check container status: " + e.getMessage());
                return 1;
            }
        }
    }
}
