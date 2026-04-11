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

/**
 * Tracks which fluid type is currently occupying a grid pipe block.
 *
 * <p>
 * When {@code fluidId} is {@code null} the pipe is uncontaminated and
 * accepts any fluid. Once fluid flows through it the ID is locked so that a
 * second, incompatible fluid cannot mix inside the same pipe segment. The lock
 * is cleared when the pipe is drained (handled by
 * {@link dev.drav.glyphworks.fluid.handlers.FluidGridTypeHandler}).
 */
public class FluidPipeComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidPipeComponent> CODEC = BuilderCodec
            .builder(FluidPipeComponent.class, () -> new FluidPipeComponent())
            .append(
                    new KeyedCodec<>("Glyphworks_FluidPipeComponent_FluidId", Codec.STRING),
                    (c, v) -> c.fluidId = v,
                    c -> c.fluidId)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidPipeComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidPipeComponentType();
    }

    /**
     * Asset ID of the fluid currently passing through this pipe, or {@code null}
     * when the pipe is clean and accepts any fluid.
     */
    @Nullable
    private String fluidId;

    /** No-arg constructor required by {@link #CODEC}. */
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
     * Returns {@code true} if this pipe can carry {@code fluidId}.
     * An uncontaminated pipe ({@code fluidId == null}) accepts anything.
     */
    public boolean accepts(String fluidId) {
        return this.fluidId == null || this.fluidId.equals(fluidId);
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new FluidPipeComponent(this);
    }
}
