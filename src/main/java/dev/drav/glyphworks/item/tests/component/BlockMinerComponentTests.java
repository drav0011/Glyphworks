package dev.drav.glyphworks.item.tests.component;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.item.component.BlockMinerComponent;
import dev.drav.glyphworks.item.tests.ItemTestUtil;
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

    private static final String MINER_ID          = "Glyphworks_Block_Miner";
    private static final String QUALITY1_BLOCK_ID  = "Rock_Basalt";
    private static final String QUALITY1_DROP_ID   = "Rock_Basalt_Cobble";
    private static final int    SAVED_PROGRESS      = 150;

    private BlockMinerComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    // ── Setup suite ───────────────────────────────────────────────────────────

    private static TestSuite buildSetupSuite() {
        return new TestSuite("block_miner_persistence_setup")
                .persistence("setup")
                .test(setupMinerState());
    }

    private static TestCase setupMinerState() {
        return new TestCase("block_miner_persists_progress", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, MINER_ID);
                    ctx.getWorld().setBlock(bx, by - 1, bz, QUALITY1_BLOCK_ID);
                }))
                .step(Steps.waitUntil(
                        ctx -> getMiner(ctx.getWorld(), ctx.getOriginX() + 1, ctx.getOriginY() + 1, ctx.getOriginZ() + 1) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "miner block entity initialised"))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (icb != null)
                        ItemTestUtil.fillContainer(icb.getItemContainer(), QUALITY1_DROP_ID);
                    BlockMinerComponent bmc = getMiner(ctx.getWorld(), bx, by, bz);
                    if (bmc != null)
                        bmc.setMiningProgress(SAVED_PROGRESS);
                }))
                .step(Steps.assertThat(ctx -> {
                    BlockMinerComponent bmc = getMiner(ctx.getWorld(), ctx.getOriginX() + 1, ctx.getOriginY() + 1, ctx.getOriginZ() + 1);
                    return bmc != null && bmc.getMiningProgress() == SAVED_PROGRESS;
                }, "baseline: miner miningProgress is " + SAVED_PROGRESS + " before server stop"));
    }

    // ── Assert suite ──────────────────────────────────────────────────────────

    private static TestSuite buildAssertSuite() {
        return new TestSuite("block_miner_persistence_assert")
                .persistence("assert")
                .test(assertMinerState());
    }

    private static TestCase assertMinerState() {
        return new TestCase("block_miner_persists_progress", 3, 3, 3)
                .step(Steps.waitUntil(
                        ctx -> getMiner(ctx.getWorld(), ctx.getOriginX() + 1, ctx.getOriginY() + 1, ctx.getOriginZ() + 1) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "miner reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    BlockMinerComponent bmc = getMiner(ctx.getWorld(), ctx.getOriginX() + 1, ctx.getOriginY() + 1, ctx.getOriginZ() + 1);
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
