package dev.drav.glyphworks.test.command;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.runner.TestRunLauncher;
import dev.drav.glyphworks.test.world.TestWorldManager;

/**
 * {@code run} sub-command of {@code /glyphworks:test} — run tests in an
 * isolated void world.
 *
 * <p>
 * The most-specific argument wins: if all three are provided only that test
 * runs. A fresh world is created via {@link TestWorldManager}, a bare runner
 * entity is spawned inside it, and
 * {@link dev.drav.glyphworks.test.runner.TestRunnerSystem}
 * picks it up on the next tick. The player stays in their own world and
 * receives the final report as a chat message.
 */
public final class TestRunCommand extends AbstractAsyncCommand {

    private final OptionalArg<String> testArg;
    private final OptionalArg<String> suiteArg;
    private final OptionalArg<String> moduleArg;
    private final FlagArg noCleanupArg;

    public TestRunCommand() {
        super("run",
                "Run a Glyphworks test module, suite, or single test. (no args) - run every test across all modules");
        this.testArg = withOptionalArg("test", "Test name within the suite", ArgTypes.STRING);
        this.suiteArg = withOptionalArg("suite", "Suite ID within the module", ArgTypes.STRING);
        this.moduleArg = withOptionalArg("module", "Module ID (omit to run all modules)", ArgTypes.STRING);
        this.noCleanupArg = withFlagArg("no-cleanup", "Keep the test world after the run (default: delete it)");
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        if (context.isPlayer()) {
            Ref<EntityStore> playerEntityRef = context.senderAsPlayerRef();
            if (playerEntityRef != null) {
                Store<EntityStore> playerStore = playerEntityRef.getStore();
                World playerWorld = playerStore.getExternalData().getWorld();
                CompletableFuture<PlayerRef> playerRefFuture = new CompletableFuture<>();
                playerWorld.execute(() -> {
                    PlayerRef pr = (PlayerRef) playerStore.getComponent(
                            playerEntityRef, PlayerRef.getComponentType());
                    playerRefFuture.complete(pr);
                });
                return playerRefFuture.thenAccept(pr -> doRun(context, pr));
            }
        }

        doRun(context, null);

        return CompletableFuture.completedFuture(null);
    }

    private void doRun(@Nonnull CommandContext context, @Nullable PlayerRef playerRef) {
        String moduleName = moduleArg.get(context);
        String suiteName = suiteArg.get(context);
        String testName = testArg.get(context);
        boolean cleanupAfterRun = !noCleanupArg.get(context);

        List<TestCase> queue = TestRegistry.buildQueue(moduleName, suiteName, testName,
                msg -> context.sendMessage(Message.raw("[GlyphTest] " + msg)));
        if (queue == null)
            return;

        if (queue.isEmpty()) {
            context.sendMessage(Message.raw("[GlyphTest] No tests found."));
            return;
        }

        String target = buildTargetLabel(moduleName, suiteName, testName);
        String cleanupNote = cleanupAfterRun ? "" : " [no-cleanup]";
        String runId = TestWorldManager.newRunId();
        context.sendMessage(Message.raw("[GlyphTest] Creating test world glyphworks-test-" + runId
                + " and starting " + queue.size() + " test(s) \u2014 " + target + cleanupNote + "."));

        TestRunLauncher.launch(queue, cleanupAfterRun, playerRef, false, runId,
                msg -> context.sendMessage(Message.raw("[GlyphTest] ERROR: " + msg)));
    }

    private static String buildTargetLabel(String moduleName, String suiteName, String testName) {
        if (moduleName == null && suiteName == null && testName == null)
            return "all tests";
        StringBuilder sb = new StringBuilder();
        if (moduleName != null)
            sb.append(moduleName);
        if (suiteName != null) {
            if (sb.length() > 0)
                sb.append('.');
            sb.append(suiteName);
        }
        if (testName != null) {
            if (sb.length() > 0)
                sb.append('.');
            sb.append(testName);
        }
        return "\"" + sb + "\"";
    }
}
