package dev.drav.glyphworks.transfer.graph;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.drav.glyphworks.transfer.component.FaceKey;
import dev.drav.glyphworks.transfer.component.FaceMode;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;

import java.util.*;
import java.util.logging.Logger;

/**
 * Per-world transfer network graph.
 *
 * <p>Each world has its own WorldGraph instance containing all nodes and edges
 * for that world. Keyed by stable {@code nodeId} ({@link UUID}) from each
 * block's {@code TransferComponent}.
 *
 * <h3>Graph Structure</h3>
 * The graph maintains:
 * <ul>
 *   <li><b>nodes</b>: Map of nodeId → block reference</li>
 *   <li><b>adjacency</b>: Map of nodeId → list of outgoing edges</li>
 * </ul>
 * <p>
 * Edges are created automatically when blocks with matching FacePlanes are placed adjacent.
 * The graph can contain multiple disconnected networks (isolated groups of connected nodes).
 *
 * <h3>Thread safety</h3>
 * All mutations happen on the Hytale server/world thread. No
 * additional synchronization is applied.
 */
public class WorldGraph {

    private static final Logger LOGGER = Logger.getLogger(WorldGraph.class.getName());

    private final String worldName;

    // nodeId → live entity ref
    private final Map<UUID, Ref<ChunkStore>> nodes = new HashMap<>();
    // nodeId → outgoing directed edges
    private final Map<UUID, List<GraphEdge>> adjacency = new HashMap<>();

    // Dirty flag for runtime changes
    private volatile boolean dirty = false;

    /**
     * Creates a new WorldGraph for the specified world.
     *
     * @param worldName The name of the world this graph belongs to
     */
    public WorldGraph(String worldName) {
        this.worldName = worldName;
        LOGGER.info("[WorldGraph] Created graph for world: " + worldName);
    }

    /**
     * Gets the world name this graph belongs to.
     */
    public String getWorldName() {
        return worldName;
    }

    // =================================================================
    //  Node management
    // =================================================================

    /**
     * Registers a new node. Safe to call multiple times with the same id (idempotent).
     *
     * @param nodeId   the unique node identifier
     * @param blockRef the block reference
     * @param position the block's world position (for logging only)
     */
    public void addNode(UUID nodeId, Ref<ChunkStore> blockRef, Vector3i position) {
        if (nodes.containsKey(nodeId)) {
            LOGGER.info("[WorldGraph] Node " + nodeId + " already registered, skipping");
            return;
        }

        nodes.put(nodeId, blockRef);
        adjacency.put(nodeId, new ArrayList<>());
        dirty = true; // Mark for async save
        LOGGER.info("[WorldGraph] Added node " + nodeId + " at position " + position);

        // Edge Creation: Check for collisions with neighbor planes
        createEdgesForNode(nodeId, blockRef, position);
    }

    /**
     * Creates edges for a newly added node by checking plane collisions with neighbors.
     * Planes store absolute world coordinates, so collision is a simple equality check.
     */
    private void createEdgesForNode(UUID nodeId, Ref<ChunkStore> blockRef, Vector3i blockPos) {
        // Get this block's TransferComponent
        TransferComponent thisComponent = blockRef.getStore().getComponent(blockRef, TransferComponent.getComponentType());
        if (thisComponent == null) {
            LOGGER.warning("[WorldGraph] Cannot create edges: TransferComponent not found for node " + nodeId);
            return;
        }

        LOGGER.info("[WorldGraph] Checking edges for node " + nodeId + " at " + blockPos +
                " with " + thisComponent.getFaces().size() + " face planes");

        // For each configured face plane in this block
        for (FacePlane thisPlane : thisComponent.getFaces().values()) {
            if (thisPlane.getMode() == FaceMode.CLOSED) {
                continue; // Skip CLOSED faces
            }

            LOGGER.info("[WorldGraph]   Checking plane " + thisPlane);

            // Get the FaceKey for direct HashMap lookup
            FaceKey thisFaceKey = thisPlane.getFaceKey();

            // Check all other registered nodes for collision using O(1) HashMap lookup
            for (UUID neighborNodeId : nodes.keySet()) {
                if (neighborNodeId.equals(nodeId)) {
                    continue; // Skip self
                }

                Ref<ChunkStore> neighborRef = nodes.get(neighborNodeId);
                if (neighborRef == null || !neighborRef.isValid()) {
                    continue;
                }

                TransferComponent neighborComponent = neighborRef.getStore().getComponent(neighborRef,
                        TransferComponent.getComponentType());
                if (neighborComponent == null) {
                    continue;
                }

                // Direct O(1) HashMap lookup for colliding face
                FacePlane neighborPlane = neighborComponent.getFaces().get(thisFaceKey);

                if (neighborPlane != null) {
                    // Faces collide (occupy same 3D space)!
                    LOGGER.info("[WorldGraph]     Planes collide! Checking mode compatibility...");

                    // Check mode compatibility
                    FacePlane.EdgeType edgeType =
                            FacePlane.checkModeCompatibility(
                                    thisPlane.getMode(), neighborPlane.getMode());

                    if (edgeType != null) {
                        // Create edge based on type
                        switch (edgeType) {
                            case A_TO_B:
                                addEdge(new GraphEdge(nodeId, neighborNodeId, false));
                                LOGGER.info("[WorldGraph]     Created edge: " + nodeId + " → " + neighborNodeId +
                                        " (" + thisPlane.getMode() + " → " + neighborPlane.getMode() + ")");
                                break;
                            case B_TO_A:
                                addEdge(new GraphEdge(neighborNodeId, nodeId, false));
                                LOGGER.info("[WorldGraph]     Created edge: " + neighborNodeId + " → " + nodeId +
                                        " (" + neighborPlane.getMode() + " → " + thisPlane.getMode() + ")");
                                break;
                            case BIDIRECTIONAL:
                                addEdge(new GraphEdge(nodeId, neighborNodeId, true));
                                LOGGER.info("[WorldGraph]     Created bidirectional edge: " + nodeId + " ↔ " + neighborNodeId +
                                        " (" + thisPlane.getMode() + " ↔ " + neighborPlane.getMode() + ")");
                                break;
                        }
                    } else {
                        LOGGER.info("[WorldGraph]     Modes incompatible: " + thisPlane.getMode() +
                                " and " + neighborPlane.getMode() + " - no edge created");
                    }
                }
            }
        }
    }

    /**
     * Removes a node and all edges touching it.
     */
    public void removeNode(UUID nodeId) {
        if (!nodes.containsKey(nodeId)) {
            LOGGER.warning("[WorldGraph] Attempted to remove non-existent node " + nodeId);
            return;
        }
        LOGGER.info("[WorldGraph] Removing node " + nodeId);
        nodes.remove(nodeId);
        adjacency.remove(nodeId);

        // Remove any edges pointing to this node
        for (List<GraphEdge> edges : adjacency.values()) {
            edges.removeIf(e -> e.getTo().equals(nodeId));
        }

        dirty = true; // Mark for async save
        LOGGER.info("[WorldGraph] Successfully removed node " + nodeId);
    }

    // =================================================================
    //  Edge management
    // =================================================================

    /**
     * Inserts an edge into the graph. If {@link GraphEdge#isBidirectional()}
     * is {@code true}, the matching reverse edge is also inserted automatically.
     */
    public void addEdge(GraphEdge edge) {
        adjacency.computeIfAbsent(edge.getFrom(), k -> new ArrayList<>()).add(edge);

        if (edge.isBidirectional()) {
            GraphEdge reverse = new GraphEdge(
                    edge.getTo(), edge.getFrom(), false
            );
            adjacency.computeIfAbsent(edge.getTo(), k -> new ArrayList<>()).add(reverse);
        }

        dirty = true; // Mark for async save
    }

    /**
     * Removes the directed edge from {@code from} to {@code to} (and the
     * reverse direction if present).
     */
    public void removeEdge(UUID from, UUID to) {
        List<GraphEdge> outFrom = adjacency.get(from);
        if (outFrom != null) outFrom.removeIf(e -> e.getTo().equals(to));
        List<GraphEdge> outTo = adjacency.get(to);
        if (outTo != null) outTo.removeIf(e -> e.getTo().equals(from));
    }

    // =================================================================
    //  Lookups
    // =================================================================

    /**
     * Returns all outgoing edges from {@code nodeId} regardless of resource
     * type. The returned list is live — do not mutate it.
     */
    public List<GraphEdge> getEdgesFrom(UUID nodeId) {
        return adjacency.getOrDefault(nodeId, Collections.emptyList());
    }

    /**
     * Returns the ids of all nodes reachable from {@code nodeId} via an enabled edge.
     */
    public List<UUID> getNeighbours(UUID nodeId) {
        List<GraphEdge> edges = adjacency.get(nodeId);
        if (edges == null) return Collections.emptyList();
        List<UUID> result = new ArrayList<>();
        for (GraphEdge e : edges) {
            if (e.isEnabled() && nodes.containsKey(e.getTo())) {
                result.add(e.getTo());
            }
        }
        return result;
    }

    /**
     * Returns the block reference for {@code nodeId}, or {@code null}.
     */
    public Ref<ChunkStore> getRef(UUID nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * Returns {@code true} if {@code nodeId} is registered in the cache.
     */
    public boolean hasNode(UUID nodeId) {
        return nodes.containsKey(nodeId);
    }

    /**
     * Returns an unmodifiable view of all registered node ids.
     */
    public Set<UUID> getAllNodeIds() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    // =================================================================
    //  Graph Rebuild (from Components)
    // =================================================================

    /**
     * Rebuilds the graph by scanning all nodes' face planes and recreating edges
     * wherever two nodes share a face position with compatible modes.
     * Called on world load to restore graph state from persisted face data.
     */
    public void rebuildFromComponents() {
        LOGGER.info("[WorldGraph] Rebuilding graph from " + nodes.size() + " components");

        // Clear all existing edges before rebuild
        for (List<GraphEdge> edgeList : adjacency.values()) {
            edgeList.clear();
        }

        // Build a map: FaceKey → (nodeId, FacePlane) for all non-CLOSED faces.
        // When two nodes share the same FaceKey the faces overlap — check compatibility.
        Map<FaceKey, UUID> faceOwner = new HashMap<>();
        Map<FaceKey, FacePlane> faceByKey = new HashMap<>();

        int edgesCreated = 0;

        for (Map.Entry<UUID, Ref<ChunkStore>> entry : nodes.entrySet()) {
            UUID nodeId = entry.getKey();
            Ref<ChunkStore> blockRef = entry.getValue();

            if (!blockRef.isValid()) {
                continue;
            }

            TransferComponent component = blockRef.getStore().getComponent(
                    blockRef, TransferComponent.getComponentType());
            if (component == null) {
                continue;
            }

            for (Map.Entry<FaceKey, FacePlane> faceEntry : component.getFaces().entrySet()) {
                FaceKey key = faceEntry.getKey();
                FacePlane plane = faceEntry.getValue();

                if (plane.getMode() == FaceMode.CLOSED) {
                    continue;
                }

                UUID existingOwner = faceOwner.get(key);
                if (existingOwner == null) {
                    // First node to register this face position
                    faceOwner.put(key, nodeId);
                    faceByKey.put(key, plane);
                } else {
                    // Second node shares this face position — check mode compatibility
                    FacePlane otherPlane = faceByKey.get(key);
                    FacePlane.EdgeType edgeType =
                            FacePlane.checkModeCompatibility(otherPlane.getMode(), plane.getMode());

                    if (edgeType != null) {
                        switch (edgeType) {
                            case A_TO_B:
                                addEdge(new GraphEdge(existingOwner, nodeId, false));
                                break;
                            case B_TO_A:
                                addEdge(new GraphEdge(nodeId, existingOwner, false));
                                break;
                            case BIDIRECTIONAL:
                                addEdge(new GraphEdge(existingOwner, nodeId, true));
                                break;
                        }
                        edgesCreated++;
                        LOGGER.info("[WorldGraph] Rebuilt edge " + edgeType +
                                " between " + existingOwner + " and " + nodeId +
                                " via face " + key);
                    }
                }
            }
        }

        dirty = false;
        LOGGER.info("[WorldGraph] Rebuilt graph: " + nodes.size() + " nodes, " + edgesCreated + " edges");
    }

    /**
     * Checks if an edge exists from -> to.
     */
    private boolean hasEdge(UUID from, UUID to) {
        List<GraphEdge> edges = adjacency.get(from);
        if (edges == null) return false;
        return edges.stream().anyMatch(e -> e.getTo().equals(to));
    }


    /**
     * Checks if graph has unsaved changes.
     */
    public boolean isDirty() {
        return dirty;
    }


    // =================================================================
    //  Reset
    // =================================================================

    /**
     * Clears all state. Called on world unload to prepare for a fresh rebuild
     * on the next load.
     */
    public void clear() {
        nodes.clear();
        adjacency.clear();
    }
}
