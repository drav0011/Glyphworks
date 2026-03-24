package dev.drav.glyphworks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.assetstore.codec.AssetCodecMapCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.content.connectedblocks.PipeConnectedBlockRuleSet;
import dev.drav.glyphworks.grid.command.GridGraphCommand;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.event.BreakGridBlockEvent;
import dev.drav.glyphworks.grid.event.ChunkLoadGridGraphEvent;
import dev.drav.glyphworks.grid.event.ChunkUnloadGridGraphEvent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.content.system.ProcessingBenchAutoStartSystem;
import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.interaction.OpenAutoCraftingBenchInteraction;
import dev.drav.glyphworks.crafting.system.AutoCraftingBenchSetupSystem;
import dev.drav.glyphworks.crafting.system.AutoCraftingBenchSystem;
import dev.drav.glyphworks.grid.system.GridSystem;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeHandlerRegistry;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.fluid.component.FluidPlacerComponent;
import dev.drav.glyphworks.fluid.component.FluidRemoverComponent;
import dev.drav.glyphworks.fluid.system.FluidPlacerSystem;
import dev.drav.glyphworks.fluid.system.FluidRemoverSystem;
import dev.drav.glyphworks.transfer.fluid.FluidGridTypeHandler;
import dev.drav.glyphworks.transfer.item.ItemGridTypeHandler;
import dev.drav.glyphworks.test.TestRunnerComponent;
import dev.drav.glyphworks.test.TestRunnerSystem;
import dev.drav.glyphworks.test.command.GlyphTestCommand;
import dev.drav.glyphworks.test.suite.BasicBlockTests;
import dev.drav.glyphworks.test.suite.FluidTests;
import dev.drav.glyphworks.test.suite.PipeConnectionTests;

public class GlyphworksPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger(GlyphworksPlugin.class.getName());
    private static GlyphworksPlugin instance;

    private ComponentType<ChunkStore, GridComponent> gridComponentType;
    private ComponentType<ChunkStore, AutoCraftingBenchBlock> autoCraftingBenchBlockComponentType;
    private ComponentType<ChunkStore, FluidContainerComponent> fluidContainerComponentType;
    private ComponentType<ChunkStore, FluidPipeComponent> fluidPipeComponentType;
    private ComponentType<ChunkStore, FluidRemoverComponent> fluidRemoverComponentType;
    private ComponentType<ChunkStore, FluidPlacerComponent> fluidPlacerComponentType;
    private ComponentType<EntityStore, TestRunnerComponent> testRunnerComponentType;

    /** Grid graphs per world, then per grid type ID. */
    private final Map<UUID, Map<String, GridGraph>> gridGraphs = new ConcurrentHashMap<>();

    @Nullable
    public GridGraph getGridGraph(World world, GridType type) {
        Map<String, GridGraph> worldGraphs = gridGraphs.get(world.getWorldConfig().getUuid());
        return worldGraphs != null ? worldGraphs.get(type.id()) : null;
    }

    public GridGraph getOrCreateGridGraph(World world, GridType type) {
        return gridGraphs
                .computeIfAbsent(world.getWorldConfig().getUuid(), id -> new ConcurrentHashMap<>())
                .computeIfAbsent(type.id(), id -> new GridGraph());
    }

    public GlyphworksPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static GlyphworksPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.info("[Glyphworks] setup()...");
        instance = this;

        // Register the pipe visual-state ruleset so the engine can resolve pipe shapes.
        ConnectedBlockRuleSet.CODEC.register("Pipe", PipeConnectedBlockRuleSet.class, PipeConnectedBlockRuleSet.CODEC);

        // Register built-in grid network types and their tick handlers.
        GridTypeRegistry.register(GridType.of("Item"));
        GridTypeHandlerRegistry.register(new ItemGridTypeHandler());
        GridTypeRegistry.register(GridType.of("Fluid"));
        GridTypeHandlerRegistry.register(new FluidGridTypeHandler());

        getEventRegistry().registerGlobal(RemoveWorldEvent.class, event -> {
            UUID worldId = event.getWorld().getWorldConfig().getUuid();

            gridGraphs.remove(worldId);
        });

        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, ChunkLoadGridGraphEvent::handle);

        this.gridComponentType = this.getChunkStoreRegistry().registerComponent(
                GridComponent.class,
                "GridComponent",
                GridComponent.CODEC);

        this.autoCraftingBenchBlockComponentType = this.getChunkStoreRegistry().registerComponent(
                AutoCraftingBenchBlock.class,
                "AutoCraftingBenchBlock",
                AutoCraftingBenchBlock.CODEC);

        this.fluidContainerComponentType = this.getChunkStoreRegistry().registerComponent(
                FluidContainerComponent.class,
                "FluidContainerComponent",
                FluidContainerComponent.CODEC);

        this.fluidPipeComponentType = this.getChunkStoreRegistry().registerComponent(
                FluidPipeComponent.class,
                "FluidPipeComponent",
                FluidPipeComponent.CODEC);

        this.fluidRemoverComponentType = this.getChunkStoreRegistry().registerComponent(
                FluidRemoverComponent.class,
                "FluidRemoverComponent",
                FluidRemoverComponent.CODEC);

        this.fluidPlacerComponentType = this.getChunkStoreRegistry().registerComponent(
                FluidPlacerComponent.class,
                "FluidPlacerComponent",
                FluidPlacerComponent.CODEC);

        getCodecRegistry((AssetCodecMapCodec) Interaction.CODEC).register(
                "OpenAutoCraftingBench", OpenAutoCraftingBenchInteraction.class,
                (BuilderCodec) OpenAutoCraftingBenchInteraction.CODEC);

        this.testRunnerComponentType = this.getEntityStoreRegistry().registerComponent(
                TestRunnerComponent.class,
                TestRunnerComponent::new);

        BasicBlockTests.register();
        FluidTests.register();
        PipeConnectionTests.register();

        this.getEntityStoreRegistry().registerSystem(new PlaceGridBlockEvent());
        this.getEntityStoreRegistry().registerSystem(new BreakGridBlockEvent());

        this.getCommandRegistry().registerCommand(new GridGraphCommand());
        this.getCommandRegistry().registerCommand(new GlyphTestCommand());

        LOGGER.info("[Glyphworks] setup() complete.");
    }

    @Override
    protected void start() {
        LOGGER.info("[Glyphworks] start() — plugin is live.");
        this.getChunkStoreRegistry().registerSystem(new GridSystem());
        // this.getChunkStoreRegistry().registerSystem(new ChunkUnloadGridGraphEvent());
        this.getChunkStoreRegistry().registerSystem(new ProcessingBenchAutoStartSystem());
        this.getChunkStoreRegistry().registerSystem(new AutoCraftingBenchSetupSystem());
        this.getChunkStoreRegistry().registerSystem(new AutoCraftingBenchSystem());
        this.getChunkStoreRegistry().registerSystem(new FluidRemoverSystem());
        this.getChunkStoreRegistry().registerSystem(new FluidPlacerSystem());
        this.getEntityStoreRegistry().registerSystem(new TestRunnerSystem());
    }

    public ComponentType<ChunkStore, GridComponent> getGridComponentType() {
        return gridComponentType;
    }

    public ComponentType<ChunkStore, AutoCraftingBenchBlock> getAutoCraftingBenchBlockComponentType() {
        return autoCraftingBenchBlockComponentType;
    }

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

    public ComponentType<EntityStore, TestRunnerComponent> getTestRunnerComponentType() {
        return testRunnerComponentType;
    }
}
