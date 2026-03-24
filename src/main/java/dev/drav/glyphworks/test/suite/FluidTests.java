package dev.drav.glyphworks.test.suite;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Basic sanity tests for fluid placement in the world.
 *
 * <p>Each test places 3 Water_Source cells along one of the three world axes,
 * waits one second, then asserts all cells are still present.
 */
public final class FluidTests {

    /** Fluid to place and query in all tests. */
    private static final String FLUID_ID = "Water_Source";

    /** Number of ticks between placement and assertion (1 second at native TPS). */
    private static final int WAIT_TICKS = TickingThread.TPS;

    private FluidTests() {}

    /**
     * Builds and registers the {@code "fluids"} test suite.
     * Call once from {@code GlyphworksPlugin.setup()}.
     */
    public static void register() {
        TestRegistry.register(buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluids")
                .test(rowOfWaterX())
                .test(colOfWaterY())
                .test(rowOfWaterZ());
    }

    // -------------------------------------------------------------------------
    // Test: 3 water cells along X
    // -------------------------------------------------------------------------

    private static TestCase rowOfWaterX() {
        return new TestCase("row_of_water_x", 5, 3, 2)
                .step(Steps.run(ctx -> {
                    for (int i = 0; i < 3; i++) {
                        placeWater(ctx.getWorld(), ctx.getOriginX() + i, ctx.getOriginY(), ctx.getOriginZ());
                    }
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    for (int i = 0; i < 3; i++) {
                        if (!isWater(ctx.getWorld(), ctx.getOriginX() + i, ctx.getOriginY(), ctx.getOriginZ())) {
                            return false;
                        }
                    }
                    return true;
                }, "3 Water_Source cells persist along X after " + WAIT_TICKS + " ticks"));
    }

    // -------------------------------------------------------------------------
    // Test: 3 water cells along Y (vertical)
    // -------------------------------------------------------------------------

    private static TestCase colOfWaterY() {
        return new TestCase("col_of_water_y", 3, 3, 4)
                .step(Steps.run(ctx -> {
                    for (int i = 0; i < 3; i++) {
                        placeWater(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY() + i, ctx.getOriginZ());
                    }
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    for (int i = 0; i < 3; i++) {
                        if (!isWater(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY() + i, ctx.getOriginZ())) {
                            return false;
                        }
                    }
                    return true;
                }, "3 Water_Source cells persist along Y after " + WAIT_TICKS + " ticks"));
    }

    // -------------------------------------------------------------------------
    // Test: 3 water cells along Z
    // -------------------------------------------------------------------------

    private static TestCase rowOfWaterZ() {
        return new TestCase("row_of_water_z", 3, 5, 2)
                .step(Steps.run(ctx -> {
                    for (int i = 0; i < 3; i++) {
                        placeWater(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + i);
                    }
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    for (int i = 0; i < 3; i++) {
                        if (!isWater(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + i)) {
                            return false;
                        }
                    }
                    return true;
                }, "3 Water_Source cells persist along Z after " + WAIT_TICKS + " ticks"));
    }

    // -------------------------------------------------------------------------
    // Helpers — fluid placement / query via ChunkColumn → FluidSection
    // -------------------------------------------------------------------------

    /**
     * Places a {@link #FLUID_ID} source cell at the given world coordinates.
     *
     * <p>Mirrors the pattern used in {@code FluidPlacerSystem}: navigate from the
     * world's {@link ChunkStore} → {@link ChunkColumn} → section {@link Ref} →
     * {@link FluidSection}, creating the FluidSection component if absent.
     */
    private static void placeWater(World world, int x, int y, int z) {
        FluidSection fs = ensureFluidSection(world, x, y, z);
        if (fs == null) return;

        int fluidId = Fluid.getAssetMap().getIndex(FLUID_ID);
        Fluid fluid = Fluid.getAssetMap().getAsset(fluidId);
        if (fluid == null) return;

        fs.setFluid(x, y, z, fluidId, (byte) fluid.getMaxFluidLevel());
    }

    /**
     * Returns {@code true} when the block at {@code (x, y, z)} holds a
     * {@link #FLUID_ID} fluid.
     */
    private static boolean isWater(World world, int x, int y, int z) {
        long chunkIdx = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIdx);
        if (chunk == null) return false;

        int foundId = chunk.getFluidId(x, y, z);
        return foundId == Fluid.getAssetMap().getIndex(FLUID_ID);
    }

    /**
     * Retrieves (or creates) the {@link FluidSection} that contains
     * the world block at {@code (x, y, z)}.
     *
     * @return the FluidSection, or {@code null} if the chunk is not loaded
     */
    @Nullable
    private static FluidSection ensureFluidSection(World world, int x, int y, int z) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> store = chunkStore.getStore();

        long chunkIdx = ChunkUtil.indexChunkFromBlock(x, z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIdx);
        if (chunkRef == null || !chunkRef.isValid()) return null;

        ChunkColumn column = store.getComponent(chunkRef, ChunkColumn.getComponentType());
        if (column == null) return null;

        Ref<ChunkStore> sectionRef = column.getSection(ChunkUtil.chunkCoordinate(y));
        if (sectionRef == null) return null;

        FluidSection fs = store.getComponent(sectionRef, FluidSection.getComponentType());
        if (fs == null) {
            fs = store.addComponent(sectionRef, FluidSection.getComponentType());
        }
        return fs;
    }
}
