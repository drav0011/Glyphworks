package dev.drav.glyphworks.fluid.component;

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
 * <p>When {@code lockedFluidId} is {@code null} the pipe is uncontaminated and
 * accepts any fluid.  Once fluid flows through it the ID is locked so that a
 * second, incompatible fluid cannot mix inside the same pipe segment.  The lock
 * is cleared when the pipe is drained (handled by
 * {@link dev.drav.glyphworks.transfer.fluid.FluidGridTypeHandler}).
 */
public class FluidPipeComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidPipeComponent> CODEC = BuilderCodec
            .builder(FluidPipeComponent.class, FluidPipeComponent::new)
            .append(
                    new KeyedCodec<>("FluidPipe_LockedFluidId", Codec.STRING),
                    (c, v) -> c.lockedFluidId = v,
                    c -> c.lockedFluidId)
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
    private String lockedFluidId;

    /** No-arg constructor required by {@link #CODEC}. */
    public FluidPipeComponent() {}

    @Nullable
    public String getLockedFluidId() {
        return lockedFluidId;
    }

    public void setLockedFluidId(@Nullable String lockedFluidId) {
        this.lockedFluidId = lockedFluidId;
    }

    /**
     * Returns {@code true} if this pipe can carry {@code fluidId}.
     * An uncontaminated pipe ({@code lockedFluidId == null}) accepts anything.
     */
    public boolean accepts(String fluidId) {
        return lockedFluidId == null || lockedFluidId.equals(fluidId);
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        // Pipe lock is not copied on placement; new pipes start uncontaminated.
        return new FluidPipeComponent();
    }
}
