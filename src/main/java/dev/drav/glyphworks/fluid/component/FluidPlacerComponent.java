package dev.drav.glyphworks.fluid.component;

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
 * Component that designates a block as a <em>fluid placer</em>.
 *
 * <p>
 * Defines where in block-local space the placer writes world fluid to.
 * The system rotates {@code targetPosition} and {@code targetNormal} by the
 * block's placed rotation to find the actual world cell to fill.
 *
 * <p>
 * The block's INPUT grid face receives fluid from the network — this is
 * separate from the target used for world interaction.
 */
public final class FluidPlacerComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidPlacerComponent> CODEC = BuilderCodec
            .builder(FluidPlacerComponent.class, () -> new FluidPlacerComponent())
            .append(
                    new KeyedCodec<>("Glyphworks_FluidPlacerComponent_TargetPosition", Vector3iUtil.CODEC),
                    (c, v) -> c.targetPosition = v,
                    c -> c.targetPosition)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidPlacerComponent_TargetNormal", new EnumCodec<>(BlockFace.class)),
                    (c, v) -> c.targetNormal = v,
                    c -> c.targetNormal)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidPlacerComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidModule().getFluidPlacerComponentType();
    }

    /**
     * Block-local offset from the block origin to the face cell. Default: (0,0,0).
     */
    private Vector3i targetPosition = new Vector3i(0, 0, 0);

    /** Block-local normal pointing toward the world cell to fill. Default: Down. */
    private BlockFace targetNormal = BlockFace.Down;

    public FluidPlacerComponent() {
    }

    public FluidPlacerComponent(@Nonnull FluidPlacerComponent other) {
        this.targetPosition = new Vector3i(other.targetPosition);
        this.targetNormal = other.targetNormal;
    }

    public Vector3i getTargetPosition() {
        return targetPosition;
    }

    public BlockFace getTargetNormal() {
        return targetNormal;
    }

    @Override
    public FluidPlacerComponent clone() {
        return new FluidPlacerComponent(this);
    }
}
