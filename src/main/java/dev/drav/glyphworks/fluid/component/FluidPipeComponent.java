package dev.drav.glyphworks.fluid.component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.FluidStack;

/**
 * Type-lock marker for a grid pipe block.
 *
 * <p>
 * When {@code fluidId} is {@code null} the pipe is unlocked and accepts any
 * fluid. Once fluid flows through it the ID is locked so that a second,
 * incompatible fluid cannot mix inside the same pipe segment.
 *
 * <p>
 * Actual fluid storage lives in the companion {@link FluidContainerComponent}
 * on the same block entity. This component is concerned only with type
 * identity, not the stored amount.
 */
public class FluidPipeComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidPipeComponent> CODEC = BuilderCodec
            .builder(FluidPipeComponent.class, FluidPipeComponent::new)
            .append(
                    new KeyedCodec<>("Glyphworks_FluidPipeComponent_FluidId", Codec.STRING),
                    (c, v) -> c.fluidId = v,
                    c -> c.fluidId)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidPipeComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidModule().getFluidPipeComponentType();
    }

    @Nullable
    private String fluidId;

    public FluidPipeComponent() {
    }

    public FluidPipeComponent(@Nonnull FluidPipeComponent other) {
        this.fluidId = other.fluidId;
    }

    @Nullable
    public String getFluidId() {
        return fluidId;
    }

    public void setFluidId(@Nullable String fluidId) {
        this.fluidId = fluidId;
    }

    /**
     * Returns {@code true} if this pipe can carry the given fluid.
     * An unlocked pipe ({@code fluidId == null}) accepts any fluid.
     */
    public boolean accepts(@Nullable FluidStack fluid) {
        if (fluidId == null) return true;
        if (fluid == null) return false;
        return fluidId.equals(fluid.getFluidId());
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new FluidPipeComponent(this);
    }
}
