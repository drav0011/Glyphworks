package dev.drav.glyphworks.grid;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.command.GridGraphCommand;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.connectedblocks.PipeConnectedBlockRuleSet;
import dev.drav.glyphworks.grid.event.BreakGridBlockEvent;
import dev.drav.glyphworks.grid.interaction.ConfigureSourceInteraction;
import dev.drav.glyphworks.grid.interaction.InspectBlockInteraction;
import dev.drav.glyphworks.grid.event.ChunkLoadGridGraphEvent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.system.BlockChangeGridSystem;
import dev.drav.glyphworks.grid.system.GridSystem;
import dev.drav.glyphworks.grid.tests.GridBlockChangeTests;
import dev.drav.glyphworks.grid.tests.GridComponentTests;
import dev.drav.glyphworks.grid.tests.GridConnectionTests;
import dev.drav.glyphworks.grid.tests.GridFaceUtilTests;
import dev.drav.glyphworks.grid.tests.GridGraphTests;
import dev.drav.glyphworks.grid.tests.PipeConnectionTests;
import dev.drav.glyphworks.grid.type.GridType;

/**
 * Sub-plugin that owns all grid-network infrastructure:
 * the per-world graph state, the {@link GridComponent} component type,
 * block place/break event handlers, and the two grid systems.
 */
public final class GridModule extends GlyphworksModule {

    private ComponentType<ChunkStore, GridComponent> gridComponentType;

    /** Grid graphs per world UUID → per grid-type ID. */
    private final Map<UUID, Map<String, GridGraph>> gridGraphs = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Graph accessors (delegated from GlyphworksPlugin to preserve public API)
    // -------------------------------------------------------------------------

    @Nullable
    public GridGraph getGridGraph(World world, GridType type) {
        Map<String, GridGraph> worldGraphs = gridGraphs.get(world.getWorldConfig().getUuid());
        return worldGraphs != null ? worldGraphs.get(type.id()) : null;
    }

    public GridGraph getOrCreateGridGraph(World world, GridType type) {
        return gridGraphs
                .computeIfAbsent(world.getWorldConfig().getUuid(), id -> new ConcurrentHashMap<>())
                .computeIfAbsent(type.id(), id -> new GridGraph(type));
    }

    @Nonnull
    public Collection<GridGraph> getAllGridGraphs(World world) {
        Map<String, GridGraph> worldGraphs = gridGraphs.get(world.getWorldConfig().getUuid());
        return worldGraphs != null ? worldGraphs.values() : Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Component type accessor
    // -------------------------------------------------------------------------

    public ComponentType<ChunkStore, GridComponent> getGridComponentType() {
        return gridComponentType;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void setup(@Nonnull GlyphworksPlugin plugin) {
        // Pipe visual-state ruleset so the engine can resolve pipe shapes.
        ConnectedBlockRuleSet.CODEC.register(
                "Pipe", PipeConnectedBlockRuleSet.class, PipeConnectedBlockRuleSet.CODEC);

        // Drop world-local graph data when a world is removed.
        plugin.getEventRegistry().registerGlobal(RemoveWorldEvent.class,
                event -> gridGraphs.remove(event.getWorld().getWorldConfig().getUuid()));

        // Re-hydrate grid graphs when chunks are pre-loaded.
        plugin.getEventRegistry().registerGlobal(
                ChunkPreLoadProcessEvent.class, ChunkLoadGridGraphEvent::handle);

        this.gridComponentType = plugin.getChunkStoreRegistry().registerComponent(
                GridComponent.class, "Glyphworks_GridComponent", GridComponent.CODEC);

        plugin.getEntityStoreRegistry().registerSystem(new PlaceGridBlockEvent());
        plugin.getEntityStoreRegistry().registerSystem(new BreakGridBlockEvent());

        plugin.getCommandRegistry().registerCommand(new GridGraphCommand());

        plugin.getCodecRegistry(Interaction.CODEC).register(
                "InspectBlock",
                InspectBlockInteraction.class,
                InspectBlockInteraction.CODEC);

        plugin.getCodecRegistry(Interaction.CODEC).register(
                "ConfigureSource",
                ConfigureSourceInteraction.class,
                ConfigureSourceInteraction.CODEC);
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new GridSystem());
        plugin.getChunkStoreRegistry().registerSystem(new BlockChangeGridSystem());
    }

    @Override
    public void setupTests() {
        PipeConnectionTests.register("grid");
        GridGraphTests.register("grid");
        GridConnectionTests.register("grid");
        GridFaceUtilTests.register("grid");
        GridComponentTests.register("grid");
        GridBlockChangeTests.register("grid");
    }
}
