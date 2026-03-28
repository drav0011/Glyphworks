package dev.drav.glyphworks.fluid.tests;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPlacerComponent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "fluid_placer_component"} — pure unit tests for
 * {@link FluidPlacerComponent}.
 *
 * <p>
 * Verifies that {@link FluidPlacerComponent#clone()} deep-copies the position
 * vector and preserves the normal direction.
 */
public final class FluidPlacerComponentTests {

    private static final String FLUID_ID = "Water_Source";
    private static final String BLOCK_ID = "Fluid_Placer";
    private static final int WAIT_TICKS = 2 * TickingThread.TPS;

    private FluidPlacerComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_placer_component")
                .test(cloneDeepCopiesPosition())
                .test(clonePreservesNormal())
                .test(placerDrainsContainerToWorldFluid())
                .test(placerSkipsBelowThreshold());
    }

    // -------------------------------------------------------------------------
    // Test 1: clone() deep-copies targetPosition (mutation isolation)
    // -------------------------------------------------------------------------

    private static TestCase cloneDeepCopiesPosition() {
        return new TestCase("placer_clone_deep_copies_position", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidPlacerComponent original = new FluidPlacerComponent();
                    FluidPlacerComponent clone = original.clone();
                    // Directly mutate the clone's vector fields.
                    clone.getTargetPosition().x = 9;
                    clone.getTargetPosition().y = 9;
                    clone.getTargetPosition().z = 9;
                    return original.getTargetPosition().x == 0
                            && original.getTargetPosition().y == 0
                            && original.getTargetPosition().z == 0;
                }, "mutating clone's targetPosition does not affect the original"));
    }

    // -------------------------------------------------------------------------
    // Test 2: clone() copies targetNormal from the original
    // -------------------------------------------------------------------------

    private static TestCase clonePreservesNormal() {
        return new TestCase("placer_clone_preserves_normal", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidPlacerComponent original = new FluidPlacerComponent();
                    // Default normal is Down; clone must carry it over.
                    FluidPlacerComponent clone = original.clone();
                    return clone.getTargetNormal() == BlockFace.Down;
                }, "clone() copies targetNormal from the original (default: Down)"));
    }

    // -------------------------------------------------------------------------
    // Test 3: placer drains 1 000 L from its container and writes world fluid
    //
    // Default targetNormal=Down → target cell is one block below the placer.
    // Steps: place block → wait for ECS → pre-fill container → wait for system
    // tick → assert world cell contains Water_Source.
    // -------------------------------------------------------------------------

    private static TestCase placerDrainsContainerToWorldFluid() {
        return new TestCase("placer_drains_container_to_world_fluid", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    // Place the Fluid_Placer block one Y level above the origin
                    // so its default Down target points at (ox, oy, oz).
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox, oy + 1, oz, BLOCK_ID);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    // Pre-fill the container so FluidPlacerSystem won't skip.
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent fcc = getContainer(w, new Vector3i(ox, oy + 1, oz));
                    if (fcc != null)
                        fcc.fill(FLUID_ID, 1_000);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Target cell must now contain Water_Source.
                    return getFluidId(w, ox, oy, oz) == Fluid.getAssetMap().getIndex(FLUID_ID);
                }, "FluidPlacerSystem drains 1 000 L from container and places Water_Source at the target cell"));
    }

    // -------------------------------------------------------------------------
    // Test 4: placer skips when container holds less than 1 000 L
    //
    // The system guard is: if (fcc.getAmount() < LITERS_PER_BLOCK) return.
    // An empty container means the target cell must stay fluid-free.
    // -------------------------------------------------------------------------

    private static TestCase placerSkipsBelowThreshold() {
        return new TestCase("placer_skips_below_threshold", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox, oy + 1, oz, BLOCK_ID);
                    // Container stays at 0 L — system must skip the tick.
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    return getFluidId(w, ox, oy, oz) == 0;
                }, "FluidPlacerSystem does not place fluid when container holds less than 1 000 L"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link FluidContainerComponent} for the block at {@code pos}, or
     * {@code null}.
     */
    @Nullable
    private static FluidContainerComponent getContainer(World world, Vector3i pos) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), pos);
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), FluidContainerComponent.getComponentType());
    }

    /**
     * Returns the fluid ID at the given world position, or {@code 0} if none / cell
     * unloaded.
     */
    private static int getFluidId(World world, int x, int y, int z) {
        long chunkIdx = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIdx);
        return chunk != null ? chunk.getFluidId(x, y, z) : 0;
    }

    /**
     * Retrieves the {@link FluidSection} that covers {@code (x, y, z)}, creating it
     * if absent.
     */
    @Nullable
    private static FluidSection ensureFluidSection(World world, int x, int y, int z) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> store = chunkStore.getStore();
        long chunkIdx = ChunkUtil.indexChunkFromBlock(x, z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIdx);
        if (chunkRef == null || !chunkRef.isValid())
            return null;
        ChunkColumn column = store.getComponent(chunkRef, ChunkColumn.getComponentType());
        if (column == null)
            return null;
        Ref<ChunkStore> sectionRef = column.getSection(ChunkUtil.chunkCoordinate(y));
        if (sectionRef == null)
            return null;
        FluidSection fs = store.getComponent(sectionRef, FluidSection.getComponentType());
        if (fs == null)
            fs = store.addComponent(sectionRef, FluidSection.getComponentType());
        return fs;
    }
}
