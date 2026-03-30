package dev.drav.glyphworks.test.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Root command collection for the Glyphworks test framework.
 *
 * <pre>
 *   /glyphworks:test run   [--module] [--suite] [--test] [--no-cleanup]
 *   /glyphworks:test purge
 * </pre>
 *
 * Aliases: {@code gw:test}. Restricted to operators ({@code setPermissionGroup(null)}).
 */
public final class TestCommands extends AbstractCommandCollection {

    public TestCommands() {
        super("glyphworks:test", "Glyphworks test framework commands");
        addAliases("gw:test");
        setPermissionGroup(null); // operator-only
        addSubCommand(new TestRunCommand());
        addSubCommand(new TestPurgeCommand());
    }
}
