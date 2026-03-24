package dev.drav.glyphworks.test.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
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
 * {@code /gtest <suite>} or {@code /gtest <suite>.<test>}
 *
 * <p>Resolves the target from the {@link TestRegistry}, builds the test queue,
 * and attaches a {@link TestRunnerComponent} to the executing player so that
 * {@code TestRunnerSystem} picks it up on the next tick.
 */
public final class GlyphTestCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> suiteArg;
    private final OptionalArg<String> testArg;

    public GlyphTestCommand() {
        super("gtest", "Run a Glyphworks test suite or single test");
        this.suiteArg = withRequiredArg("suite", "Suite ID", ArgTypes.STRING);
        this.testArg  = withOptionalArg("test",  "Test name within the suite", ArgTypes.STRING);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        String suiteName = suiteArg.get(context);
        String testName  = testArg.get(context); // null when not provided

        TestSuite suite = TestRegistry.get(suiteName);
        if (suite == null) {
            context.sendMessage(Message.raw("[GlyphTest] Unknown suite: \"" + suiteName
                    + "\". Available: " + TestRegistry.all().stream()
                            .map(TestSuite::getId)
                            .reduce((a, b) -> a + ", " + b).orElse("<none>")));
            return;
        }

        List<TestCase> queue = new ArrayList<>();
        if (testName != null) {
            Optional<TestCase> found = suite.findTest(testName);
            if (found.isEmpty()) {
                context.sendMessage(Message.raw("[GlyphTest] Unknown test \"" + testName
                        + "\" in suite \"" + suiteName + "\"."));
                return;
            }
            queue.add(found.get());
        } else {
            queue.addAll(suite.getTests());
        }

        if (queue.isEmpty()) {
            context.sendMessage(Message.raw("[GlyphTest] Suite \"" + suiteName + "\" has no tests."));
            return;
        }

        // Guard against double-starting.
        if (store.getComponent(ref, TestRunnerComponent.getComponentType()) != null) {
            context.sendMessage(Message.raw("[GlyphTest] A test run is already in progress."));
            return;
        }

        // ── Grid layout ──
        // Origin: player feet position, 2 blocks south (+Z).
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        int baseX = 0, baseY = 64, baseZ = 2;
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            baseX = (int) Math.floor(pos.x);
            baseY = (int) Math.floor(pos.y);
            baseZ = (int) Math.floor(pos.z) + 2;
        }

        int n    = queue.size();
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil((double) n / cols);

        // Use the largest declared area as the uniform cell size so no test overflows into another.
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
        runner.init(queue, originXs, originYs, originZs);
        context.sendMessage(Message.raw("[GlyphTest] Started " + n + " test(s) from suite \""
                + suiteName + "\" in a " + cols + "x" + rows + " grid."));
    }
}
