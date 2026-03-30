package dev.drav.glyphworks.test.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestRunnerComponent;
import dev.drav.glyphworks.test.TestSuite;

/**
 * The most-specific argument wins: if all three are provided only that test
 * runs. Resolves the target from the {@link TestRegistry}, builds the test
 * queue, and attaches a {@link TestRunnerComponent} to the executing player so
 * that {@link dev.drav.glyphworks.test.TestRunnerSystem} picks it up on the
 * next tick.
 */
public final class TestCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> testArg;
    private final OptionalArg<String> suiteArg;
    private final OptionalArg<String> moduleArg;
    private final FlagArg noCleanupArg;

    public TestCommand() {
        super("glyphworks:test", "Run a Glyphworks test module, suite, or single test. (no args) - run every test across all modules");
        addAliases("gw:test");
        this.testArg = withOptionalArg("test", "Test name within the suite", ArgTypes.STRING);
        this.suiteArg = withOptionalArg("suite", "Suite ID within the module", ArgTypes.STRING);
        this.moduleArg = withOptionalArg("module", "Module ID (omit to run all modules)", ArgTypes.STRING);
        this.noCleanupArg = withFlagArg("no-cleanup", "Keep test area blocks after the run (default: clean up)");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        String moduleName = moduleArg.get(context); // null when not provided
        String suiteName = suiteArg.get(context); // null when not provided
        String testName = testArg.get(context); // null when not provided
        boolean cleanupAfterRun = !noCleanupArg.get(context);

        List<TestCase> queue = new ArrayList<>();
        if (moduleName == null) {
            // No args — run everything.
            for (TestSuite suite : TestRegistry.all()) {
                queue.addAll(suite.getTests());
            }
        } else {
            Map<String, TestSuite> moduleSuites = TestRegistry.getModule(moduleName);
            if (moduleSuites == null) {
                context.sendMessage(Message.raw("[GlyphTest] Unknown module: \"" + moduleName
                        + "\". Available: " + String.join(", ", TestRegistry.moduleIds())));
                return;
            }
            if (suiteName == null) {
                // Module only — run everything in it.
                for (TestSuite suite : moduleSuites.values()) {
                    queue.addAll(suite.getTests());
                }
            } else {
                TestSuite suite = moduleSuites.get(suiteName);
                if (suite == null) {
                    context.sendMessage(Message.raw("[GlyphTest] Unknown suite: \"" + suiteName
                            + "\" in module \"" + moduleName + "\". Available: "
                            + String.join(", ", moduleSuites.keySet())));
                    return;
                }
                if (testName == null) {
                    queue.addAll(suite.getTests());
                } else {
                    Optional<TestCase> found = suite.findTest(testName);
                    if (found.isEmpty()) {
                        context.sendMessage(Message.raw("[GlyphTest] Unknown test \"" + testName
                                + "\" in suite \"" + moduleName + "." + suiteName + "\". Available: "
                                + suite.getTests().stream().map(TestCase::getName)
                                        .reduce((a, b) -> a + ", " + b).orElse("<none>")));
                        return;
                    }
                    queue.add(found.get());
                }
            }
        }

        if (queue.isEmpty()) {
            context.sendMessage(Message.raw("[GlyphTest] No tests found for the given target."));
            return;
        }

        // Guard against double-starting.
        if (store.getComponent(ref, TestRunnerComponent.getComponentType()) != null) {
            context.sendMessage(Message.raw("[GlyphTest] A test run is already in progress."));
            return;
        }

        // ── Grid layout ──
        // Origin: player feet position, 2 blocks south (+Z).
        TransformComponent transform = (TransformComponent) store.getComponent(ref,
                TransformComponent.getComponentType());
        int baseX = 0, baseY = 64, baseZ = 2;
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            baseX = (int) Math.floor(pos.x);
            baseY = (int) Math.floor(pos.y);
            baseZ = (int) Math.floor(pos.z) + 2;
        }

        int n = queue.size();
        int cols = (int) Math.ceil(Math.sqrt(n));

        // Use the largest declared area as the uniform cell size so no test overflows
        // into another.
        int maxWidth = queue.stream().mapToInt(TestCase::getAreaWidth).max().orElse(1);
        int maxDepth = queue.stream().mapToInt(TestCase::getAreaDepth).max().orElse(1);
        int gap = 2;

        int[] originXs = new int[n];
        int[] originYs = new int[n];
        int[] originZs = new int[n];
        for (int i = 0; i < n; i++) {
            int col = i % cols;
            int row = i / cols;
            originXs[i] = baseX + col * (maxWidth + gap);
            originYs[i] = baseY;
            originZs[i] = baseZ + row * (maxDepth + gap);
        }

        TestRunnerComponent runner = store.addComponent(ref, TestRunnerComponent.getComponentType());
        runner.init(queue, originXs, originYs, originZs, cleanupAfterRun);

        String target;
        if (moduleName == null) {
            target = "all modules";
        } else if (suiteName == null) {
            target = "module \"" + moduleName + "\"";
        } else if (testName == null) {
            target = "suite \"" + moduleName + "." + suiteName + "\"";
        } else {
            target = "test \"" + moduleName + "." + suiteName + "." + testName + "\"";
        }
        String cleanupNote = cleanupAfterRun ? "" : " [no-cleanup]";
        context.sendMessage(Message.raw("[GlyphTest] Started " + n + " test(s) — " + target + cleanupNote + "."));
    }
}
