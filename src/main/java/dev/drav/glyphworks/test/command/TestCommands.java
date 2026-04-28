package dev.drav.glyphworks.test.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;

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
        setPermissionGroups(HytalePermissionsProvider.OP_GROUP);
        addSubCommand(new TestRunCommand());
        addSubCommand(new TestPurgeCommand());
    }

    @Override
    protected String generatePermissionNode() {
        return "glyphworks.test";
    }
}
