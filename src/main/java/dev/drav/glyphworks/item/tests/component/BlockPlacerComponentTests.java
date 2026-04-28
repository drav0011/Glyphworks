package dev.drav.glyphworks.item.tests.component;

import com.hypixel.hytale.math.vector.Vector3i;

import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.item.component.BlockPlacerComponent;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Two-suite persistence test for {@link BlockPlacerComponent}.
 *
 * <p>
 * Verifies that the JSON-configured {@code targetNormal} (Down by default)
 * and {@code targetPosition} survive a server restart.
 *
 * <pre>
 *   # Phase 1
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=setup -Dglyphworks.test.persistence.suite=block_placer_persistence_setup"
 *   ./gradlew runServer
 *
 *   # Phase 2
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=assert -Dglyphworks.test.persistence.suite=block_placer_persistence_assert"
 *   ./gradlew runServer
 * </pre>
 */
public final class BlockPlacerComponentTests {

    private static final String PLACER_ID = "Glyphworks_Block_Placer";

    private BlockPlacerComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    // ── Setup suite ───────────────────────────────────────────────────────────

    private static TestSuite buildSetupSuite() {
        return new TestSuite("block_placer_persistence_setup")
                .persistence("setup")
                .test(setupPlacerState());
    }

    private static TestCase setupPlacerState() {
        return new TestCase("block_placer_persists_target", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), PLACER_ID);
                }))
                .step(Steps.waitUntil(ctx -> getPlacer(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "placer block entity initialised"))
                .step(Steps.assertThat(ctx -> {
                    BlockPlacerComponent bpc = getPlacer(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return bpc != null
                            && BlockFace.Down == bpc.getTargetNormal()
                            && new Vector3i(0, 0, 0).equals(bpc.getTargetPosition());
                }, "baseline: placer targetNormal=Down, targetPosition=(0,0,0) before server stop"));
    }

    // ── Assert suite ──────────────────────────────────────────────────────────

    private static TestSuite buildAssertSuite() {
        return new TestSuite("block_placer_persistence_assert")
                .persistence("assert")
                .test(assertPlacerState());
    }

    private static TestCase assertPlacerState() {
        return new TestCase("block_placer_persists_target", 3, 3, 3)
                .step(Steps.waitUntil(ctx -> getPlacer(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "placer reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    BlockPlacerComponent bpc = getPlacer(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return bpc != null
                            && BlockFace.Down == bpc.getTargetNormal()
                            && new Vector3i(0, 0, 0).equals(bpc.getTargetPosition());
                }, "BlockPlacerComponent persists targetNormal and targetPosition across server restart"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static BlockPlacerComponent getPlacer(World world, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), BlockPlacerComponent.getComponentType());
    }
}
