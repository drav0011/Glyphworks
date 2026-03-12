package dev.drav.glyphworks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.command.GridGraphCommand;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.event.BreakGridBlockEvent;
import dev.drav.glyphworks.grid.event.ChunkLoadGridGraphEvent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.system.GridSystem;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;

public class GlyphworksPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger(GlyphworksPlugin.class.getName());
    private static GlyphworksPlugin instance;

    private ComponentType<ChunkStore, GridComponent> gridComponentType;

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

        // Register built-in grid network types.
        GridTypeRegistry.register(GridType.of("Item"));

        getEventRegistry().registerGlobal(RemoveWorldEvent.class, event -> {
            UUID worldId = event.getWorld().getWorldConfig().getUuid();

            gridGraphs.remove(worldId);
        });

        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, ChunkLoadGridGraphEvent::handle);

        this.gridComponentType = this.getChunkStoreRegistry().registerComponent(
                GridComponent.class,
                "GridComponent",
                GridComponent.CODEC);

        this.getEntityStoreRegistry().registerSystem(new PlaceGridBlockEvent());
        this.getEntityStoreRegistry().registerSystem(new BreakGridBlockEvent());

        this.getCommandRegistry().registerCommand(new GridGraphCommand());

        LOGGER.info("[Glyphworks] setup() complete.");
    }

    @Override
    protected void start() {
        LOGGER.info("[Glyphworks] start() — plugin is live.");
        this.getChunkStoreRegistry().registerSystem(new GridSystem());
        // ChunkUnloadGridGraphEvent is NOT registered here — see its class-level javadoc.
    }

    public ComponentType<ChunkStore, GridComponent> getGridComponentType() {
        return gridComponentType;
    }
}
