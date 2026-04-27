package dev.drav.glyphworks.fluid.component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.container.FluidContainer;

public class FluidContainerComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidContainerComponent> CODEC = BuilderCodec
            .builder(FluidContainerComponent.class, FluidContainerComponent::new)
            .append(
                    new KeyedCodec<>("Glyphworks_FluidContainerComponent_Container", FluidContainer.CODEC),
                    (c, v) -> c.fluidContainer = v,
                    c -> c.fluidContainer)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidContainerComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidModule().getFluidContainerComponentType();
    }

    @Nullable
    private FluidContainer fluidContainer;

    public FluidContainerComponent() {
        this.fluidContainer = new FluidContainer();
    }

    public FluidContainerComponent(@Nonnull FluidContainerComponent other) {
        this.fluidContainer = other.fluidContainer != null ? other.fluidContainer.clone() : null;
    }

    @Nonnull
    public FluidContainer getFluidContainer() {
        if (this.fluidContainer == null) {
            this.fluidContainer = new FluidContainer();
        }

        return fluidContainer;
    }

    public void setFluidContainer(@Nonnull FluidContainer container) {
        this.fluidContainer = container;
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new FluidContainerComponent(this);
    }
}
