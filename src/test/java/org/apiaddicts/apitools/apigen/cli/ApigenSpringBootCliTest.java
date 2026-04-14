package org.apiaddicts.apitools.apigen.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the CLI entry point and subcommand wiring.
 */
class ApigenSpringBootCliTest {

    @Test
    void mainCommandShowsHelp() {
        ApigenSpringBootCli cli = new ApigenSpringBootCli();
        CommandLine cmd = new CommandLine(cli);

        int exitCode = cmd.execute("--help");
        assertEquals(0, exitCode);
    }

    @Test
    void versionFlagWorks() {
        ApigenSpringBootCli cli = new ApigenSpringBootCli();
        CommandLine cmd = new CommandLine(cli);

        int exitCode = cmd.execute("--version");
        assertEquals(0, exitCode);
    }

    @Test
    void subcommandsAreRegistered() {
        CommandLine cmd = new CommandLine(new ApigenSpringBootCli());

        assertNotNull(cmd.getSubcommands().get("init"));
        assertNotNull(cmd.getSubcommands().get("generate"));
        assertNotNull(cmd.getSubcommands().get("validate"));
        assertNotNull(cmd.getSubcommands().get("preview"));
        assertNotNull(cmd.getSubcommands().get("build"));
        assertNotNull(cmd.getSubcommands().get("run"));
        assertNotNull(cmd.getSubcommands().get("test"));
        assertNotNull(cmd.getSubcommands().get("clean"));
        assertNotNull(cmd.getSubcommands().get("config"));
        assertNotNull(cmd.getSubcommands().get("docker"));
        assertNotNull(cmd.getSubcommands().get("version"));
        assertNotNull(cmd.getSubcommands().get("help"));
    }

    @Test
    void noArgsShowsUsage() {
        ApigenSpringBootCli cli = new ApigenSpringBootCli();
        CommandLine cmd = new CommandLine(cli);

        int exitCode = cmd.execute();
        assertEquals(0, exitCode);
    }

    @Test
    void buildCommandFailsWithoutPom() {
        CommandLine cmd = new CommandLine(new ApigenSpringBootCli());

        int exitCode = cmd.execute("build", "-d", "/tmp/nonexistent-dir-apigen");
        assertEquals(1, exitCode);
    }
}
