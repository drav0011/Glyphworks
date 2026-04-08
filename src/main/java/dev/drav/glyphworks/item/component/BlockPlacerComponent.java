package dev.drav.glyphworks.item.component;

import javax.annotation.Nonnull;

import org.joml.Vector3i;

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
 * Marks a block as a <em>block placer</em>.
 *
 * <p>
 * Mirror of {@link dev.drav.glyphworks.fluid.component.FluidPlacerComponent}
 * for blocks. The system reads block-items from the paired
 * {@link com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock},
 * places the block into the world at the target cell, and removes the item
 * from the container. Non-block items are skipped.
 *
 * <p>
 * The block's INPUT grid face receives block-items from the network.
 */
public final class BlockPlacerComponent implements Component<ChunkStore> {

    public static final BuilderCodec<BlockPlacerComponent> CODEC = BuilderCodec
            .builder(BlockPlacerComponent.class, BlockPlacerComponent::new)
            .append(
                    new KeyedCodec<>("Glyphworks_BlockPlacerComponent_TargetPosition", Vector3iUtil.CODEC),
                    (c, v) -> c.targetPosition = v,
                    c -> c.targetPosition)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_BlockPlacerComponent_TargetNormal", new EnumCodec<>(BlockFace.class)),
                    (c, v) -> c.targetNormal = v,
                    c -> c.targetNormal)
            .add()
            .build();

    public static ComponentType<ChunkStore, BlockPlacerComponent> getComponentType() {
        return GlyphworksPlugin.get().getBlockPlacerComponentType();
    }

    /** Block-local offset from the block origin to the face cell. Default: (0,0,0). */
    @Nonnull
    private Vector3i targetPosition = new Vector3i(0, 0, 0);

    /** Block-local normal pointing toward the world cell to fill. Default: Down. */
    @Nonnull
    private BlockFace targetNormal = BlockFace.Down;

    public BlockPlacerComponent() {
    }

    @Nonnull
    public Vector3i getTargetPosition() {
        return targetPosition;
    }

    @Nonnull
    public BlockFace getTargetNormal() {
        return targetNormal;
    }

    @Override
    public BlockPlacerComponent clone() {
        BlockPlacerComponent c = new BlockPlacerComponent();
        c.targetPosition = new Vector3i(targetPosition.x, targetPosition.y, targetPosition.z);
        c.targetNormal = targetNormal;
        return c;
    }
}
