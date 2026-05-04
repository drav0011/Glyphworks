package dev.drav.glyphworks.test.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.Universe;

import dev.drav.glyphworks.test.world.TestWorldManager;

/**
 * {@code purge} sub-command of {@code /glyphworks:test} — destroys all
 * leftover test worlds that were kept alive by {@code --no-cleanup}.
 */
public final class TestPurgeCommand extends CommandBase {

    private static final Logger LOGGER = Logger.getLogger(TestPurgeCommand.class.getName());

    public TestPurgeCommand() {
        super("purge", "Destroy all leftover test worlds kept by --no-cleanup");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<String> testWorldNames = new ArrayList<>();
        for (String name : Universe.get().getWorlds().keySet()) {
            if (name.startsWith(TestWorldManager.NAME_PREFIX)) {
                testWorldNames.add(name);
            }
        }

        if (testWorldNames.isEmpty()) {
            context.sendMessage(Message.raw("[GlyphTest] No leftover test worlds found."));
            return;
        }

        context.sendMessage(Message.raw("[GlyphTest] Purging " + testWorldNames.size() + " test world(s)..."));
        for (String worldName : testWorldNames) {
            LOGGER.info("[GlyphTest] Purging test world: " + worldName);
            CompletableFuture.runAsync(() -> TestWorldManager.destroyTestWorld(worldName));
        }
    }
}
