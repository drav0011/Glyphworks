package dev.drav.glyphworks.item.tests.component;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.item.component.BlockMinerComponent;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Two-suite persistence test for {@link BlockMinerComponent}.
 *
 * <p>
 * Verifies that {@code miningProgress} (a runtime-mutated, persisted field)
 * survives a server restart. The test sets it to a specific non-zero value
 * before stopping so the assert phase can distinguish fresh init from restored
 * state.
 *
 * <pre>
 *   # Phase 1
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=setup -Dglyphworks.test.persistence.suite=block_miner_persistence_setup"
 *   ./gradlew runServer
 *
 *   # Phase 2
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=assert -Dglyphworks.test.persistence.suite=block_miner_persistence_assert"
 *   ./gradlew runServer
 * </pre>
 */
public final class BlockMinerComponentTests {

    private static final String MINER_ID       = "Glyphworks_Block_Miner";
    private static final int    SAVED_PROGRESS = 150;

    private BlockMinerComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    // ── Setup suite ───────────────────────────────────────────────────────────

    private static TestSuite buildSetupSuite() {
        return new TestSuite("block_miner_persistence_setup")
                .persistence()
                .test(setupMinerState());
    }

    private static TestCase setupMinerState() {
        return new TestCase("block_miner_persists_progress", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), MINER_ID);
                }))
                .step(Steps.waitUntil(ctx -> getMiner(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "miner block entity initialised"))
                .step(Steps.run(ctx -> {
                    BlockMinerComponent bmc = getMiner(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (bmc != null)
                        bmc.setMiningProgress(SAVED_PROGRESS);
                }))
                .step(Steps.assertThat(ctx -> {
                    BlockMinerComponent bmc = getMiner(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return bmc != null && bmc.getMiningProgress() == SAVED_PROGRESS;
                }, "baseline: miner miningProgress is " + SAVED_PROGRESS + " before server stop"));
    }

    // ── Assert suite ──────────────────────────────────────────────────────────

    private static TestSuite buildAssertSuite() {
        return new TestSuite("block_miner_persistence_assert")
                .persistence()
                .test(assertMinerState());
    }

    private static TestCase assertMinerState() {
        return new TestCase("block_miner_persists_progress", 3, 3, 3)
                .step(Steps.waitUntil(ctx -> getMiner(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "miner reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    BlockMinerComponent bmc = getMiner(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return bmc != null && bmc.getMiningProgress() == SAVED_PROGRESS;
                }, "BlockMinerComponent persists miningProgress across server restart"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static BlockMinerComponent getMiner(World world, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), BlockMinerComponent.getComponentType());
    }
}
