package dev.drav.glyphworks.fluid.component;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Marks a block as a creative fluid source — a block that always keeps its
 * {@link FluidContainerComponent} filled to capacity with a fixed fluid type.
 *
 * <p>
 * The {@code fluidId} is the asset ID of the fluid (e.g. {@code "Fluid_Water"})
 * that this source produces infinitely. The paired
 * {@link dev.drav.glyphworks.fluid.system.FluidSourceSystem} resets the
 * container to full every tick so downstream grid consumers never run dry.
 */
public final class FluidSourceComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidSourceComponent> CODEC = BuilderCodec
            .builder(FluidSourceComponent.class, () -> new FluidSourceComponent())
            .append(
                    new KeyedCodec<>("Glyphworks_FluidSourceComponent_FluidId", Codec.STRING),
                    (c, v) -> c.fluidId = v,
                    c -> c.fluidId)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidSourceComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidSourceComponentType();
    }

    /** Asset ID of the fluid this block outputs infinitely. */
    @Nonnull
    private String fluidId = "Fluid_Water";

    public FluidSourceComponent() {
    }

    public FluidSourceComponent(@Nonnull FluidSourceComponent other) {
        this.fluidId = other.fluidId;
    }

    @Nonnull
    public String getFluidId() {
        return fluidId;
    }

    public void setFluidId(@Nonnull String fluidId) {
        this.fluidId = fluidId;
    }

    @Override
    public FluidSourceComponent clone() {
        return new FluidSourceComponent(this);
    }
}
