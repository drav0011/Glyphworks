package dev.drav.glyphworks.item.component;

import javax.annotation.Nonnull;

import org.joml.Vector3i;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3iUtil;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Marks a block as a <em>block miner</em>.
 *
 * <p>
 * Like {@link dev.drav.glyphworks.fluid.component.FluidRemoverComponent}, the
 * component stores a block-local target offset and normal that the system
 * rotates to world-space each tick. When the target cell contains a breakable
 * block the miner accumulates progress and eventually harvests it, placing the
 * resulting item into the paired {@link com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock}.
 *
 * <p>
 * The block's OUTPUT grid face pushes collected block-items into the network.
 */
public final class BlockMinerComponent implements Component<ChunkStore> {

    public static final BuilderCodec<BlockMinerComponent> CODEC = BuilderCodec
            .builder(BlockMinerComponent.class, () -> new BlockMinerComponent())
            .append(
                    new KeyedCodec<>("Glyphworks_BlockMinerComponent_TargetPosition", Vector3iUtil.CODEC),
                    (c, v) -> c.targetPosition = v,
                    c -> c.targetPosition)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_BlockMinerComponent_TargetNormal", new EnumCodec<>(BlockFace.class)),
                    (c, v) -> c.targetNormal = v,
                    c -> c.targetNormal)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_BlockMinerComponent_MiningProgress", Codec.INTEGER),
                    (c, v) -> c.miningProgress = v,
                    c -> c.miningProgress)
            .add()
            .build();

    public static ComponentType<ChunkStore, BlockMinerComponent> getComponentType() {
        return GlyphworksPlugin.get().getItemModule().getBlockMinerComponentType();
    }

    /** Block-local offset from the block origin to the face cell. Default: (0,0,0). */
    @Nonnull
    private Vector3i targetPosition = new Vector3i(0, 0, 0);

    /** Block-local normal pointing toward the world cell to mine. Default: Down. */
    @Nonnull
    private BlockFace targetNormal = BlockFace.Down;

    /**
     * Mining progress in ticks. Persisted so that mining survives server restarts.
     * Reset to 0 when the target cell becomes empty or the block type changes.
     */
    private int miningProgress = 0;

    /**
     * Numeric block-type ID of the block currently being mined.
     * Transient — re-populated every tick; not persisted.
     */
    private transient int lastSeenBlockId = -1;

    public BlockMinerComponent() {
    }

    public BlockMinerComponent(@Nonnull BlockMinerComponent other) {
        this.targetPosition = new Vector3i(other.targetPosition);
        this.targetNormal = other.targetNormal;
        this.miningProgress = other.miningProgress;
    }

    @Nonnull
    public Vector3i getTargetPosition() {
        return targetPosition;
    }

    @Nonnull
    public BlockFace getTargetNormal() {
        return targetNormal;
    }

    public int getMiningProgress() {
        return miningProgress;
    }

    public void setMiningProgress(int progress) {
        this.miningProgress = progress;
    }

    public int getLastSeenBlockId() {
        return lastSeenBlockId;
    }

    public void setLastSeenBlockId(int id) {
        this.lastSeenBlockId = id;
    }

    @Override
    public BlockMinerComponent clone() {
        return new BlockMinerComponent(this);
    }
}
