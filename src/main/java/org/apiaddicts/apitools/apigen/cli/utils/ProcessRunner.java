package org.apiaddicts.apitools.apigen.cli.utils;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Centralised process execution utility.
 * Used by wrapper commands (build, run, test, clean) to invoke Maven, Docker, and Java processes.
 */
public final class ProcessRunner {

    private ProcessRunner() {}

    /**
     * Result of a process execution.
     */
    public record Result(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    /**
     * Run a command inheriting the parent process IO (interactive mode).
     * Output goes directly to the terminal.
     */
    public static int exec(Path workDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null) {
            pb.directory(workDir.toFile());
        }
        pb.inheritIO();
        Process process = pb.start();
        return process.waitFor();
    }

    /**
     * Run a command inheriting IO with additional environment variables.
     */
    public static int exec(Path workDir, Map<String, String> env, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null) {
            pb.directory(workDir.toFile());
        }
        if (env != null) {
            pb.environment().putAll(env);
        }
        pb.inheritIO();
        Process process = pb.start();
        return process.waitFor();
    }

    /**
     * Run a command and capture its output (non-interactive mode).
     */
    public static Result capture(Path workDir, long timeoutSeconds, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null) {
            pb.directory(workDir.toFile());
        }
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String stdout = readStream(process.getInputStream());
        String stderr = readStream(process.getErrorStream());

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new Result(-1, stdout, "Process timed out after " + timeoutSeconds + " seconds");
        }

        return new Result(process.exitValue(), stdout.trim(), stderr.trim());
    }

    /**
     * Capture with default 60-second timeout.
     */
    public static Result capture(Path workDir, String... command) throws IOException, InterruptedException {
        return capture(workDir, 60, command);
    }

    /**
     * Check if a command is available on PATH.
     */
    public static boolean isAvailable(String command) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            String[] check;
            if (os.contains("win")) {
                check = new String[]{"where", command};
            } else {
                check = new String[]{"which", command};
            }
            Process p = new ProcessBuilder(check)
                    .redirectErrorStream(true)
                    .start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolve the Maven wrapper or fall back to system mvn.
     */
    public static String resolveMaven(Path projectDir) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String wrapper = os.contains("win") ? "mvnw.cmd" : "./mvnw";
        if (projectDir != null && projectDir.resolve(wrapper.replace("./", "")).toFile().exists()) {
            return wrapper;
        }
        return "mvn";
    }

    private static String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
