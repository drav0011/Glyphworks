package dev.drav.glyphworks.fluid.component;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Marker component that designates a block as a creative fluid sink — a block
 * that instantly destroys any fluid it receives from the grid.
 *
 * <p>
 * The paired {@link dev.drav.glyphworks.fluid.system.FluidSinkSystem}
 * resets the block's {@link FluidContainerComponent} to empty every tick,
 * effectively creating an infinite drain.
 */
public final class FluidSinkComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidSinkComponent> CODEC = BuilderCodec
            .builder(FluidSinkComponent.class, FluidSinkComponent::new)
            .build();

    public static ComponentType<ChunkStore, FluidSinkComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidSinkComponentType();
    }

    public FluidSinkComponent() {
    }

    @Override
    public FluidSinkComponent clone() {
        return new FluidSinkComponent();
    }
}
