package dev.drav.glyphworks.fluid;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.fluid.component.FluidPlacerComponent;
import dev.drav.glyphworks.fluid.component.FluidRemoverComponent;
import dev.drav.glyphworks.fluid.system.FluidPlacerSystem;
import dev.drav.glyphworks.fluid.system.FluidRemoverSystem;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeHandlerRegistry;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;
import dev.drav.glyphworks.transfer.fluid.FluidGridTypeHandler;

/**
 * Sub-plugin that owns the Fluid grid type, all four fluid component types,
 * and the fluid placer/remover systems.
 */
public final class FluidModule extends GlyphworksModule {

    private ComponentType<ChunkStore, FluidContainerComponent> fluidContainerComponentType;
    private ComponentType<ChunkStore, FluidPipeComponent> fluidPipeComponentType;
    private ComponentType<ChunkStore, FluidRemoverComponent> fluidRemoverComponentType;
    private ComponentType<ChunkStore, FluidPlacerComponent> fluidPlacerComponentType;

    public ComponentType<ChunkStore, FluidContainerComponent> getFluidContainerComponentType() { return fluidContainerComponentType; }
    public ComponentType<ChunkStore, FluidPipeComponent> getFluidPipeComponentType() { return fluidPipeComponentType; }
    public ComponentType<ChunkStore, FluidRemoverComponent> getFluidRemoverComponentType() { return fluidRemoverComponentType; }
    public ComponentType<ChunkStore, FluidPlacerComponent> getFluidPlacerComponentType() { return fluidPlacerComponentType; }

    @Override
    public void setup(@Nonnull GlyphworksPlugin plugin) {
        GridTypeRegistry.register(GridType.of("Fluid"));
        GridTypeHandlerRegistry.register(new FluidGridTypeHandler());

        this.fluidContainerComponentType = plugin.getChunkStoreRegistry().registerComponent(
                FluidContainerComponent.class, "FluidContainerComponent", FluidContainerComponent.CODEC);

        this.fluidPipeComponentType = plugin.getChunkStoreRegistry().registerComponent(
                FluidPipeComponent.class, "FluidPipeComponent", FluidPipeComponent.CODEC);

        this.fluidRemoverComponentType = plugin.getChunkStoreRegistry().registerComponent(
                FluidRemoverComponent.class, "FluidRemoverComponent", FluidRemoverComponent.CODEC);

        this.fluidPlacerComponentType = plugin.getChunkStoreRegistry().registerComponent(
                FluidPlacerComponent.class, "FluidPlacerComponent", FluidPlacerComponent.CODEC);
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new FluidRemoverSystem());
        plugin.getChunkStoreRegistry().registerSystem(new FluidPlacerSystem());
    }
}
