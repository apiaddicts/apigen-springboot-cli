package org.apiaddicts.apitools.apigen.cli.utils;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

/**
 * Utility class for styled console output with ANSI color support.
 * Provides consistent formatting across all CLI commands.
 */
public final class ConsoleOutput {

    private static boolean colorEnabled = true;

    private ConsoleOutput() {}

    public static void init(boolean noColor) {
        colorEnabled = !noColor;
        if (colorEnabled) {
            AnsiConsole.systemInstall();
        }
    }

    public static boolean isColorEnabled() {
        return colorEnabled;
    }

    public static void success(String message) {
        if (colorEnabled) {
            System.out.println(Ansi.ansi().fgGreen().a("✓ ").reset().a(message));
        } else {
            System.out.println("[OK] " + message);
        }
    }

    public static void error(String message) {
        if (colorEnabled) {
            System.err.println(Ansi.ansi().fgRed().a("✗ ").reset().a(message));
        } else {
            System.err.println("[ERROR] " + message);
        }
    }

    public static void warn(String message) {
        if (colorEnabled) {
            System.out.println(Ansi.ansi().fgYellow().a("⚠ ").reset().a(message));
        } else {
            System.out.println("[WARN] " + message);
        }
    }

    public static void info(String message) {
        if (colorEnabled) {
            System.out.println(Ansi.ansi().fgCyan().a("ℹ ").reset().a(message));
        } else {
            System.out.println("[INFO] " + message);
        }
    }

    public static void step(int current, int total, String message) {
        if (colorEnabled) {
            System.out.println(Ansi.ansi()
                    .fgBrightBlack().a("[" + current + "/" + total + "] ")
                    .reset().a(message));
        } else {
            System.out.println("[" + current + "/" + total + "] " + message);
        }
    }

    public static void header(String title) {
        String line = "─".repeat(Math.max(title.length() + 4, 40));
        if (colorEnabled) {
            System.out.println();
            System.out.println(Ansi.ansi().fgBrightCyan().bold().a(line).reset());
            System.out.println(Ansi.ansi().fgBrightCyan().bold().a("  " + title).reset());
            System.out.println(Ansi.ansi().fgBrightCyan().bold().a(line).reset());
        } else {
            System.out.println();
            System.out.println(line);
            System.out.println("  " + title);
            System.out.println(line);
        }
    }

    public static void detail(String key, String value) {
        if (colorEnabled) {
            System.out.println(Ansi.ansi()
                    .fgBrightBlack().a("  " + key + ": ")
                    .reset().fg(Ansi.Color.WHITE).a(value).reset());
        } else {
            System.out.println("  " + key + ": " + value);
        }
    }

    public static void blank() {
        System.out.println();
    }

    public static void print(String message) {
        System.out.println(message);
    }

    /**
     * Print a command that the user can copy/paste.
     */
    public static void command(String cmd) {
        if (colorEnabled) {
            System.out.println(Ansi.ansi().fgBrightYellow().a("  $ ").reset().a(cmd));
        } else {
            System.out.println("  $ " + cmd);
        }
    }
}
