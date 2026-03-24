package dev.drav.glyphworks.fluid.system;

import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidRemoverComponent;
import dev.drav.glyphworks.grid.util.GridFaceUtil;

/**
 * Ticking system for fluid remover blocks.
 *
 * <p>Each tick, for every block that has both a {@link FluidRemoverComponent}
 * and a {@link FluidContainerComponent}, the system:
 * <ol>
 *   <li>Skips if the container has no space for a full bucket (1 000 L).</li>
 *   <li>Rotates the component's {@code targetNormal} and {@code targetPosition}
 *       by the block's placed rotation to find the world cell to drain.</li>
 *   <li>Checks if that world cell contains a fluid.</li>
 *   <li>If so, removes the fluid block and fills the container with 1 000 L.</li>
 * </ol>
 * The fluid grid then drains the container and distributes it to connected tanks.
 */
public final class FluidRemoverSystem extends EntityTickingSystem<ChunkStore> {

    private static final Logger LOGGER = Logger.getLogger(FluidRemoverSystem.class.getName());

    /** Log at most once per this interval (ms) to avoid flooding the console. */
    private static final long LOG_INTERVAL_MS = 5_000;
    private long lastLogTime = 0;

    /** Liters per world source-fluid block (one bucket). */
    private static final int LITERS_PER_BLOCK = 1_000;

    private static final int EMPTY_FLUID_ID = 0;

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                FluidRemoverComponent.getComponentType(),
                FluidContainerComponent.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        boolean log = System.currentTimeMillis() - lastLogTime >= LOG_INTERVAL_MS;
        if (log) lastLogTime = System.currentTimeMillis();

        FluidContainerComponent fcc = chunk.getComponent(index, FluidContainerComponent.getComponentType());
        if (fcc == null) {
            if (log) LOGGER.info("[FluidRemover] fcc is null — entity skipped");
            return;
        }
        if (fcc.availableSpace() < LITERS_PER_BLOCK) {
            if (log) LOGGER.info("[FluidRemover] container full (space=" + fcc.availableSpace() + ") — skipping");
            return;
        }

        FluidRemoverComponent remover = chunk.getComponent(index, FluidRemoverComponent.getComponentType());
        if (remover == null) {
            if (log) LOGGER.info("[FluidRemover] remover component is null — entity skipped");
            return;
        }

        BlockModule.BlockStateInfo bsi = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (bsi == null) {
            if (log) LOGGER.info("[FluidRemover] BlockStateInfo is null — entity skipped");
            return;
        }
        if (!bsi.getChunkRef().isValid()) {
            if (log) LOGGER.info("[FluidRemover] chunkRef invalid — entity skipped");
            return;
        }

        ChunkStore chunkStore = commandBuffer.getExternalData();
        BlockChunk blockChunk = store.getComponent(bsi.getChunkRef(), BlockChunk.getComponentType());
        if (blockChunk == null) {
            if (log) LOGGER.info("[FluidRemover] BlockChunk is null — entity skipped");
            return;
        }

        int bi = bsi.getIndex();
        int localX = ChunkUtil.xFromBlockInColumn(bi);
        int localY = ChunkUtil.yFromBlockInColumn(bi);
        int localZ = ChunkUtil.zFromBlockInColumn(bi);
        Vector3i originPos = new Vector3i(
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX),
                localY,
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ));

        BlockSection blockSection = blockChunk.getSectionAtBlockY(localY);
        RotationTuple rotation = RotationTuple.get(blockSection.getRotationIndex(localX, localY, localZ));

        if (log) LOGGER.info("[FluidRemover] block at " + originPos
                + " | rotation=" + rotation
                + " | localNormal=" + remover.getTargetNormal()
                + " | localPos=" + remover.getTargetPosition()
                + " | container=" + fcc.getAmount() + "/" + fcc.getCapacity() + "L");

        Vector3i adjacent = resolveTargetCell(originPos, remover, rotation);
        if (adjacent == null) {
            if (log) LOGGER.info("[FluidRemover] resolveTargetCell returned null (worldNormal=None after rotation)");
            return;
        }

        if (log) LOGGER.info("[FluidRemover] target cell=" + adjacent);

        FluidSection fs = getFluidSection(chunkStore, store, adjacent);
        if (fs == null) {
            if (log) LOGGER.info("[FluidRemover] no FluidSection at " + adjacent);
            return;
        }

        int fluidId = fs.getFluidId(adjacent.x, adjacent.y, adjacent.z);
        if (log) LOGGER.info("[FluidRemover] fluidId at " + adjacent + "=" + fluidId);

        if (fluidId == EMPTY_FLUID_ID) {
            if (log) LOGGER.info("[FluidRemover] cell is empty — nothing to drain");
            return;
        }

        Fluid fluid = Fluid.getAssetMap().getAsset(fluidId);
        if (fluid == null) {
            if (log) LOGGER.info("[FluidRemover] Fluid asset not found for id=" + fluidId);
            return;
        }

        if (log) LOGGER.info("[FluidRemover] draining fluid='" + fluid.getId() + "' from " + adjacent);
        int filled = fcc.fill(fluid.getId(), LITERS_PER_BLOCK);
        if (filled > 0) {
            fs.setFluid(adjacent.x, adjacent.y, adjacent.z, EMPTY_FLUID_ID, (byte) 0);
            Ref<ChunkStore> adjChunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(adjacent.x, adjacent.z));
            if (adjChunkRef != null && adjChunkRef.isValid()) {
                BlockChunk adjBlockChunk = store.getComponent(adjChunkRef, BlockChunk.getComponentType());
                if (adjBlockChunk != null) adjBlockChunk.setTicking(adjacent.x, adjacent.y, adjacent.z, true);
            }
            if (log) LOGGER.info("[FluidRemover] filled " + filled + "L into container, cleared world cell");
        } else {
            if (log) LOGGER.info("[FluidRemover] fill() returned 0 — container may reject this fluid type");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the world cell the remover should drain.
     *
     * <p>The target is always the cell on the opposite side of the block from
     * its grid connection face.  Deriving the direction from the rotated grid
     * face (rather than from a fixed {@code targetNormal}) means the target
     * automatically tracks any {@link com.hypixel.hytale.protocol.VariantRotation}
     * — a normally-placed block targets the cell below, a sideways block targets
     * the correct horizontal neighbour, etc.
     */
    @Nullable
    private static Vector3i resolveTargetCell(
            @Nonnull Vector3i originPos,
            @Nonnull FluidRemoverComponent remover,
            @Nonnull RotationTuple rotation) {
        BlockFace worldNormal = GridFaceUtil.rotateBlockFace(remover.getTargetNormal(), rotation);
        if (worldNormal == BlockFace.None) return null;
        Vector3i worldFaceCell = GridFaceUtil.addOffset(
                originPos, GridFaceUtil.rotateFacePosition(remover.getTargetPosition(), rotation));
        return GridFaceUtil.addOffset(worldFaceCell, worldNormal);
    }

    @Nullable
    private static FluidSection getFluidSection(
            @Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> store,
            @Nonnull Vector3i pos) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) return null;
        ChunkColumn column = store.getComponent(chunkRef, ChunkColumn.getComponentType());
        if (column == null) return null;
        Ref<ChunkStore> section = column.getSection(ChunkUtil.chunkCoordinate(pos.y));
        if (section == null) return null;
        return store.getComponent(section, FluidSection.getComponentType());
    }
}

