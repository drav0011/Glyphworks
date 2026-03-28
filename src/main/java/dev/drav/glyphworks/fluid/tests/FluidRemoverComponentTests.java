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
import dev.drav.glyphworks.fluid.component.FluidRemoverComponent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "fluid_remover_component"} — pure unit tests for
 * {@link FluidRemoverComponent}.
 *
 * <p>
 * Verifies that {@link FluidRemoverComponent#clone()} deep-copies the position
 * vector and preserves the normal direction.
 */
public final class FluidRemoverComponentTests {

    private static final String FLUID_ID = "Water_Source";
    private static final String BLOCK_ID = "Fluid_Remover";
    private static final int WAIT_TICKS = 2 * TickingThread.TPS;

    private FluidRemoverComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_remover_component")
                .test(cloneDeepCopiesPosition())
                .test(clonePreservesNormal())
                .test(removerPicksUpWorldFluidToContainer())
                .test(removerSkipsWhenCellEmpty());
    }

    // -------------------------------------------------------------------------
    // Test 1: clone() deep-copies targetPosition (mutation isolation)
    // -------------------------------------------------------------------------

    private static TestCase cloneDeepCopiesPosition() {
        return new TestCase("remover_clone_deep_copies_position", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidRemoverComponent original = new FluidRemoverComponent();
                    FluidRemoverComponent clone = original.clone();
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
        return new TestCase("remover_clone_preserves_normal", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidRemoverComponent original = new FluidRemoverComponent();
                    // Default normal is Down; clone must carry it over.
                    FluidRemoverComponent clone = original.clone();
                    return clone.getTargetNormal() == BlockFace.Down;
                }, "clone() copies targetNormal from the original (default: Down)"));
    }

    // -------------------------------------------------------------------------
    // Test 3: remover picks up world fluid and fills its container
    //
    // Default targetNormal=Down → source cell is one block below the remover.
    // Steps: place block → wait for ECS → place world fluid below → wait for
    // system tick → assert container gained 1 000 L and world cell is empty.
    // -------------------------------------------------------------------------

    private static TestCase removerPicksUpWorldFluidToContainer() {
        return new TestCase("remover_picks_up_world_fluid_to_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    // Place the Fluid_Remover block one Y level above the origin
                    // so its default Down target points at (ox, oy, oz).
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox, oy + 1, oz, BLOCK_ID);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    // Place Water_Source at the target cell for the remover to consume.
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeFluid(w, ox, oy, oz, FLUID_ID);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // World cell must be empty.
                    if (getFluidId(w, ox, oy, oz) != 0)
                        return false;
                    // Container must hold exactly 1 000 L of Water_Source.
                    FluidContainerComponent fcc = getContainer(w, new Vector3i(ox, oy + 1, oz));
                    return fcc != null && fcc.getAmount() == 1_000
                            && FLUID_ID.equals(fcc.getLockedFluidId());
                }, "FluidRemoverSystem picks up Water_Source from world cell and adds 1 000 L to the container"));
    }

    // -------------------------------------------------------------------------
    // Test 4: remover skips when the target cell is already empty
    //
    // Container must stay at 0 L — the system guard returns early when
    // fluidId == 0 at the target cell.
    // -------------------------------------------------------------------------

    private static TestCase removerSkipsWhenCellEmpty() {
        return new TestCase("remover_skips_when_cell_empty", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox, oy + 1, oz, BLOCK_ID);
                    // Leave the target cell empty — nothing for the remover to consume.
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent fcc = getContainer(w, new Vector3i(ox, oy + 1, oz));
                    return fcc != null && fcc.getAmount() == 0 && fcc.isEmpty();
                }, "FluidRemoverSystem leaves container empty when the target cell contains no fluid"));
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

    /** Places {@code fluidId} as a source block at the given world position. */
    private static void placeFluid(World world, int x, int y, int z, String fluidId) {
        FluidSection fs = ensureFluidSection(world, x, y, z);
        if (fs == null)
            return;
        int indexedId = Fluid.getAssetMap().getIndex(fluidId);
        Fluid fluid = Fluid.getAssetMap().getAsset(indexedId);
        if (fluid == null)
            return;
        fs.setFluid(x, y, z, indexedId, (byte) fluid.getMaxFluidLevel());
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
