package dev.drav.glyphworks.fluid.tests;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.protocol.BlockFace;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.util.GridFaceUtil;

public final class FluidTestUtil {

    // -------------------------------------------------------------------------
    // Rotation indices: each transforms the default Down targetNormal to the
    // given world-space direction (verified against GridFaceUtil.rotateBlockFace).
    // -------------------------------------------------------------------------

    public static final int ROTATION_DOWN  = RotationTuple.index(Rotation.None, Rotation.None,        Rotation.None);       // 0
    public static final int ROTATION_UP    = RotationTuple.index(Rotation.None, Rotation.OneEighty,   Rotation.None);       // 8
    public static final int ROTATION_NORTH = RotationTuple.index(Rotation.None, Rotation.Ninety,      Rotation.None);       // 4
    public static final int ROTATION_SOUTH = RotationTuple.index(Rotation.None, Rotation.TwoSeventy,  Rotation.None);       // 12
    public static final int ROTATION_EAST  = RotationTuple.index(Rotation.None, Rotation.None,        Rotation.Ninety);     // 16
    public static final int ROTATION_WEST  = RotationTuple.index(Rotation.None, Rotation.None,        Rotation.TwoSeventy); // 48

    private FluidTestUtil() {
    }

    @Nullable
    public static FluidContainerComponent getContainer(World world, Vector3i pos) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), pos);
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), FluidContainerComponent.getComponentType());
    }

    public static int getFluidId(World world, int x, int y, int z) {
        FluidSection fs = FluidUtil.getFluidSection(
                world.getChunkStore(), world.getChunkStore().getStore(), new Vector3i(x, y, z));
        return fs != null ? fs.getFluidId(x, y, z) : 0;
    }

    public static void placeFluid(World world, int x, int y, int z, String fluidId) {
        FluidSection fs = FluidUtil.ensureFluidSection(world.getChunkStore(), world.getChunkStore().getStore(), x, y,
                z);
        if (fs == null)
            return;
        int indexedId = Fluid.getAssetMap().getIndex(fluidId);
        Fluid fluid = Fluid.getAssetMap().getAsset(indexedId);
        if (fluid == null)
            return;
        fs.setFluid(x, y, z, indexedId, (byte) fluid.getMaxFluidLevel());
    }

    /**
     * Places {@code blockId} at the given world position with a specific
     * {@link RotationTuple} index, triggering full block-entity initialisation.
     *
     * <p>This is used by direction tests to orient placer/remover blocks without
     * relying on player interaction or {@code world.setBlock} (which always uses
     * rotation 0).
     */
    public static void setBlockWithRotation(World world, int x, int y, int z, String blockId, int rotationIndex) {
        long chunkIdx = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIdx);
        if (chunk == null)
            return;
        int id = BlockType.getAssetMap().getIndex(blockId);
        BlockType bt = (BlockType) BlockType.getAssetMap().getAsset(id);
        if (bt == null)
            return;
        chunk.setBlock(x, y, z, id, bt, rotationIndex, 0, 0);
    }

    /**
     * Returns the world position of the cell adjacent to {@code (bx, by, bz)}
     * in the given {@code face} direction.
     */
    public static Vector3i targetPos(int bx, int by, int bz, BlockFace face) {
        return GridFaceUtil.addOffset(new Vector3i(bx, by, bz), face);
    }
}
