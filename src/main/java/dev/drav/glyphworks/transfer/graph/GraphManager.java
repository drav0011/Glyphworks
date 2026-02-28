package dev.drav.glyphworks.transfer.graph;

import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages WorldGraph instances (one per world).
 * Singleton that coordinates all graph operations.
 *
 * <p><b>Persistence Strategy:</b> Graph edges are stored in each block's
 * {@code TransferComponent.connections} field and persisted via Hytale's
 * native CODEC system. On world load, the graph is rebuilt from these
 * stored connections. On world unload, the current graph state is saved
 * back to components.
 */
public class GraphManager {

    private static final Logger LOGGER = Logger.getLogger(GraphManager.class.getName());
    private static GraphManager instance;

    private final Map<String, WorldGraph> graphs = new ConcurrentHashMap<>();

    private GraphManager() {
        // Private constructor for singleton
    }

    /**
     * Gets the singleton GraphManager instance.
     */
    public static GraphManager get() {
        if (instance == null) {
            instance = new GraphManager();
        }
        return instance;
    }

    /**
     * Gets or creates a WorldGraph for the specified world.
     */
    @Nonnull
    public WorldGraph getOrCreateGraph(String worldName) {
        return graphs.computeIfAbsent(worldName, WorldGraph::new);
    }

    /**
     * Gets an existing WorldGraph for the specified world.
     */
    @Nullable
    public WorldGraph getGraph(String worldName) {
        return graphs.get(worldName);
    }

    /**
     * Removes a WorldGraph (on world unload).
     */
    public void removeGraph(String worldName) {
        WorldGraph graph = graphs.remove(worldName);
        if (graph != null) {
            graph.clear();
            LOGGER.info("[GraphManager] Removed graph for world: " + worldName);
        }
    }

    /**
     * Gets all world names that have graphs.
     */
    public Iterable<String> getWorldNames() {
        return graphs.keySet();
    }

    /**
     * Gets all WorldGraph instances.
     */
    public Iterable<WorldGraph> getAllGraphs() {
        return graphs.values();
    }

    /**
     * Clears all graphs (on server shutdown).
     */
    public void clearAll() {
        graphs.values().forEach(WorldGraph::clear);
        graphs.clear();
        LOGGER.info("[GraphManager] Cleared all graphs");
    }

    /**
     * Initializes the manager: rebuilds graphs from components for all loaded worlds.
     * Call this on server/plugin startup.
     */
    public void initialize() {
        LOGGER.info("[GraphManager] Initializing...");

        // Rebuild graphs for all loaded worlds from their components
        Universe universe = Universe.get();

        if (universe != null) {
            universe.getWorlds().values().forEach(world -> {
                try {
                    world.execute(() -> {
                        String worldName = world.getName();
                        WorldGraph graph = getOrCreateGraph(worldName);

                        graph.rebuildFromComponents();
                    });
                } catch (Exception e) {
                    LOGGER.warning("[GraphManager] Failed to rebuild graph for world " + world.getName() + ": " + e.getMessage());
                }
            });
        }

        LOGGER.info("[GraphManager] Initialized");
    }

    /**
     * Shuts down the manager: saves all graphs back to components.
     * Call this on server/plugin shutdown.
     */
    public void shutdown() {
        LOGGER.info("[GraphManager] Shutting down...");

        // Save all graphs to components
        Universe universe = Universe.get();

        if (universe != null) {
            universe.getWorlds().values().forEach(world -> {
                try {
                    world.execute(() -> {
                        String worldName = world.getName();
                        WorldGraph graph = getGraph(worldName);

                        if (graph != null) {
                            graph.saveToComponents();
                        }
                    });
                } catch (Exception e) {
                    LOGGER.warning("[GraphManager] Failed to save graph for world " + world.getName() + ": " + e.getMessage());
                }
            });
        }

        LOGGER.info("[GraphManager] Shutdown complete");
    }
}

