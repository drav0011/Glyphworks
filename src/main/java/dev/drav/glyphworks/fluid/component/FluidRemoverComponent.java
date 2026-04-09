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
 * Component that designates a block as a <em>fluid remover</em>.
 *
 * <p>
 * Defines where in block-local space the remover reads world fluid from.
 * The system rotates {@code targetPosition} and {@code targetNormal} by the
 * block's placed rotation to find the actual world cell to drain.
 *
 * <p>
 * The block's OUTPUT grid face connects to the fluid network — this is
 * separate from the target used for world interaction.
 */
public final class FluidRemoverComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidRemoverComponent> CODEC = BuilderCodec
            .builder(FluidRemoverComponent.class, () -> new FluidRemoverComponent())
            .append(
                    new KeyedCodec<>("Glyphworks_FluidRemoverComponent_TargetPosition", Vector3iUtil.CODEC),
                    (c, v) -> c.targetPosition = v,
                    c -> c.targetPosition)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidRemoverComponent_TargetNormal", new EnumCodec<>(BlockFace.class)),
                    (c, v) -> c.targetNormal = v,
                    c -> c.targetNormal)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidRemoverComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidRemoverComponentType();
    }

    /**
     * Block-local offset from the block origin to the face cell. Default: (0,0,0).
     */
    private Vector3i targetPosition = new Vector3i(0, 0, 0);

    /**
     * Block-local normal pointing toward the world cell to drain. Default: Down.
     */
    private BlockFace targetNormal = BlockFace.Down;

    public FluidRemoverComponent() {
    }

    public FluidRemoverComponent(@Nonnull FluidRemoverComponent other) {
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
    public FluidRemoverComponent clone() {
        return new FluidRemoverComponent(this);
    }
}
