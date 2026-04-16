package dev.drav.glyphworks.fluid;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.fluid.component.FluidPlacerComponent;
import dev.drav.glyphworks.fluid.component.FluidRemoverComponent;
import dev.drav.glyphworks.fluid.component.FluidSinkComponent;
import dev.drav.glyphworks.fluid.component.FluidSourceComponent;
import dev.drav.glyphworks.fluid.handlers.FluidGridTypeHandler;
import dev.drav.glyphworks.fluid.interaction.OpenFluidContainerInteraction;
import dev.drav.glyphworks.fluid.system.FluidPlacerSystem;
import dev.drav.glyphworks.fluid.system.FluidRemoverSystem;
import dev.drav.glyphworks.fluid.system.FluidSinkSystem;
import dev.drav.glyphworks.fluid.system.FluidSourceSystem;
import dev.drav.glyphworks.fluid.tests.component.FluidContainerComponentTests;
import dev.drav.glyphworks.fluid.tests.component.FluidPipeComponentTests;
import dev.drav.glyphworks.fluid.tests.component.FluidPlacerComponentTests;
import dev.drav.glyphworks.fluid.tests.component.FluidRemoverComponentTests;
import dev.drav.glyphworks.fluid.tests.component.FluidSourceComponentTests;
import dev.drav.glyphworks.fluid.tests.system.FluidGridTransferTests;
import dev.drav.glyphworks.fluid.tests.system.FluidPlacerSystemTests;
import dev.drav.glyphworks.fluid.tests.system.FluidRemoverSystemTests;
import dev.drav.glyphworks.fluid.tests.system.FluidSinkSystemTests;
import dev.drav.glyphworks.fluid.tests.system.FluidSourceSystemTests;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeHandlerRegistry;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;

/**
 * Sub-plugin that owns the Fluid grid type, all four fluid component types,
 * and the fluid placer/remover systems.
 */
public final class FluidModule extends GlyphworksModule {

    private ComponentType<ChunkStore, FluidContainerComponent> fluidContainerComponentType;
    private ComponentType<ChunkStore, FluidPipeComponent> fluidPipeComponentType;
    private ComponentType<ChunkStore, FluidRemoverComponent> fluidRemoverComponentType;
    private ComponentType<ChunkStore, FluidPlacerComponent> fluidPlacerComponentType;
    private ComponentType<ChunkStore, FluidSourceComponent> fluidSourceComponentType;
    private ComponentType<ChunkStore, FluidSinkComponent> fluidSinkComponentType;

    public ComponentType<ChunkStore, FluidContainerComponent> getFluidContainerComponentType() {
        return fluidContainerComponentType;
    }

    public ComponentType<ChunkStore, FluidPipeComponent> getFluidPipeComponentType() {
        return fluidPipeComponentType;
    }

    public ComponentType<ChunkStore, FluidRemoverComponent> getFluidRemoverComponentType() {
        return fluidRemoverComponentType;
    }

    public ComponentType<ChunkStore, FluidPlacerComponent> getFluidPlacerComponentType() {
        return fluidPlacerComponentType;
    }

    public ComponentType<ChunkStore, FluidSourceComponent> getFluidSourceComponentType() {
        return fluidSourceComponentType;
    }

    public ComponentType<ChunkStore, FluidSinkComponent> getFluidSinkComponentType() {
        return fluidSinkComponentType;
    }

    @Override
    public void setup(@Nonnull GlyphworksPlugin plugin) {
        GridTypeRegistry.register(GridType.of("Fluid"));
        GridTypeHandlerRegistry.register(new FluidGridTypeHandler());

        plugin.getCodecRegistry(Interaction.CODEC).register(
                "OpenFluidContainer",
                OpenFluidContainerInteraction.class,
                OpenFluidContainerInteraction.CODEC);

        plugin.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, FluidItemRegistry::onItemsLoaded);

        this.fluidContainerComponentType = plugin.getChunkStoreRegistry().registerComponent(
                FluidContainerComponent.class, "Glyphworks_FluidContainerComponent", FluidContainerComponent.CODEC);

        this.fluidPipeComponentType = plugin.getChunkStoreRegistry().registerComponent(
                FluidPipeComponent.class, "Glyphworks_FluidPipeComponent", FluidPipeComponent.CODEC);

        this.fluidRemoverComponentType = plugin.getChunkStoreRegistry().registerComponent(
                FluidRemoverComponent.class, "Glyphworks_FluidRemoverComponent", FluidRemoverComponent.CODEC);

        this.fluidPlacerComponentType = plugin.getChunkStoreRegistry().registerComponent(
                FluidPlacerComponent.class, "Glyphworks_FluidPlacerComponent", FluidPlacerComponent.CODEC);

        this.fluidSourceComponentType = plugin.getChunkStoreRegistry().registerComponent(
                FluidSourceComponent.class, "Glyphworks_FluidSourceComponent", FluidSourceComponent.CODEC);

        this.fluidSinkComponentType = plugin.getChunkStoreRegistry().registerComponent(
                FluidSinkComponent.class, "Glyphworks_FluidSinkComponent", FluidSinkComponent.CODEC);
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new FluidRemoverSystem());
        plugin.getChunkStoreRegistry().registerSystem(new FluidPlacerSystem());
        plugin.getChunkStoreRegistry().registerSystem(new FluidSourceSystem());
        plugin.getChunkStoreRegistry().registerSystem(new FluidSinkSystem());
    }

    @Override
    public void setupTests() {
        FluidGridTransferTests.register("fluid");
        FluidSourceSystemTests.register("fluid");
        FluidSinkSystemTests.register("fluid");
        FluidPlacerSystemTests.register("fluid");
        FluidRemoverSystemTests.register("fluid");
        FluidContainerComponentTests.register("fluid");
        FluidPipeComponentTests.register("fluid");
        FluidSourceComponentTests.register("fluid");
        FluidPlacerComponentTests.register("fluid");
        FluidRemoverComponentTests.register("fluid");
    }
}
