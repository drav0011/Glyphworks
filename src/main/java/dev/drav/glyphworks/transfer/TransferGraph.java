package dev.drav.glyphworks.transfer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.transfer.component.FaceMode;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.lookups.TransferLookup;

/**
 * Runtime graph of TransferComponent nodes for a single world.
 *
 * <p>Not serialised — edges are reconstructed from {@code FacePlane.neighborNodeId}
 * as blocks are placed, broken, and loaded.
 *
 * <p>Not thread-safe. Only ever accessed on the owning world's thread.
 */
public final class TransferGraph {

    /** nodeId → set of directly connected nodeIds (undirected, both sides stored). */
    private final Map<UUID, Set<UUID>> adjacency = new HashMap<>();

    /** nodeId → block position in this world. */
    private final Map<UUID, Vector3i> positions = new HashMap<>();

    /**
     * Directed route cache: autoPush source nodeId → list of reachable sinks.
     * Built by {@link #rebuildRoutesFrom} and invalidated on topology changes.
     */
    private final Map<UUID, List<TransferRoute>> routeCache = new HashMap<>();

    /**
     * A pre-computed route from an autoPush source to a reachable sink.
     *
     * @param sinkNodeId UUID of the sink node
     * @param sinkFace   The ingress {@link FacePlane} on the sink — holds the wired {@code inputInventory}
     * @param minRate    Pre-computed {@code min(all maxOutputRates along path, sink.maxInputRate)} — use directly in the tick
     */
    public record TransferRoute(UUID sinkNodeId, FacePlane sinkFace, int minRate) {}

    /** Internal BFS entry: node + accumulated min-rate so far on the path from source. */
    private record BfsEntry(UUID nodeId, int accMinRate) {}

    /** Register a node. Does nothing if already present. */
    public void addNode(UUID id, Vector3i pos) {
        positions.putIfAbsent(id, pos);
        adjacency.putIfAbsent(id, new HashSet<>());
    }

    /** Remove a node and every edge that connects to it. */
    public void removeNode(UUID id) {
        Set<UUID> neighbors = adjacency.remove(id);
        if (neighbors != null) {
            for (UUID neighbor : neighbors) {
                Set<UUID> neighborEdges = adjacency.get(neighbor);
                if (neighborEdges != null) neighborEdges.remove(id);
            }
        }
        positions.remove(id);
    }

    /** Record a link between two nodes. Idempotent. */
    public void addEdge(UUID a, UUID b) {
        Set<UUID> setA = adjacency.get(a);
        Set<UUID> setB = adjacency.get(b);
        if (setA != null) setA.add(b);
        if (setB != null) setB.add(a);
    }

    /** Remove the link between two nodes (both directions). */
    public void removeEdge(UUID a, UUID b) {
        Set<UUID> setA = adjacency.get(a);
        Set<UUID> setB = adjacency.get(b);
        if (setA != null) setA.remove(b);
        if (setB != null) setB.remove(a);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    @Nullable
    public Vector3i getPosition(UUID id) {
        return positions.get(id);
    }

    /** Returns an unmodifiable view of the neighbors, or an empty set if absent. */
    public Set<UUID> getNeighbors(UUID id) {
        Set<UUID> neighbors = adjacency.get(id);
        return neighbors != null ? Collections.unmodifiableSet(neighbors) : Collections.emptySet();
    }

    public boolean contains(UUID id) {
        return adjacency.containsKey(id);
    }

    /** Returns an unmodifiable view of all node IDs currently in the graph. */
    public Set<UUID> getNodeIds() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }

    /**
     * BFS from {@code start} — returns all nodes reachable in the same connected
     * component, including {@code start} itself. Returns an empty set if
     * {@code start} is not in this graph.
     */
    public Set<UUID> getComponent(UUID start) {
        Set<UUID> visited = new HashSet<>();
        if (!adjacency.containsKey(start)) return visited;
        Queue<UUID> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            Set<UUID> neighbors = adjacency.get(current);
            if (neighbors == null) continue;
            for (UUID neighbor : neighbors) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }

    // ── Route cache ───────────────────────────────────────────────────────────

    /**
     * Runs a directed BFS from {@code sourceId} and populates the route cache.
     * Call after any topology or wiring change that affects this source node.
     *
     * <p>Traversal rules:
     * <ul>
     *   <li>Egress: a face on the current node is traversable if its mode is OUTPUT or BIDIRECTIONAL.</li>
     *   <li>Ingress: the matching face on the neighbor must have mode INPUT or BIDIRECTIONAL.</li>
     *   <li>Sink: ingress face has a non-null {@code inputInventory} — route recorded, traversal stops here.</li>
     *   <li>Pass-through: ingress face has no {@code inputInventory} (pipe/connector) — BFS continues.</li>
     * </ul>
     */
    public void rebuildRoutesFrom(UUID sourceId, ChunkStore chunkStore) {
        List<TransferRoute> routes = new ArrayList<>();
        routeCache.put(sourceId, routes);

        Vector3i sourcePos = positions.get(sourceId);
        if (sourcePos == null) return;

        TransferLookup sourceLookup = TransferLookup.resolve(chunkStore, sourcePos);
        if (sourceLookup == null) return;

        Queue<BfsEntry> queue = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();

        queue.add(new BfsEntry(sourceId, sourceLookup.transfer().getMaxOutputRate()));
        visited.add(sourceId);

        while (!queue.isEmpty()) {
            BfsEntry entry = queue.poll();
            UUID currentId = entry.nodeId();
            int accRate = entry.accMinRate();

            Vector3i currentPos = positions.get(currentId);
            if (currentPos == null) continue;

            TransferLookup currentLookup = TransferLookup.resolve(chunkStore, currentPos);
            if (currentLookup == null) continue;

            TransferComponent current = currentLookup.transfer();

            for (FacePlane face : current.getFaces()) {
                UUID neighborId = face.getNeighborNodeId();
                if (neighborId == null) continue;

                // Egress check: can we leave through this face?
                FaceMode mode = face.getMode();
                if (mode != FaceMode.OUTPUT && mode != FaceMode.BIDIRECTIONAL) continue;

                Vector3i neighborPos = positions.get(neighborId);
                if (neighborPos == null) continue; // neighbor chunk not loaded

                TransferLookup neighborLookup = TransferLookup.resolve(chunkStore, neighborPos);
                if (neighborLookup == null) continue;

                TransferComponent neighbor = neighborLookup.transfer();

                // Find ingress face on neighbor: the face pointing back to currentId
                FacePlane ingressFace = null;
                for (FacePlane nf : neighbor.getFaces()) {
                    if (currentId.equals(nf.getNeighborNodeId())) {
                        ingressFace = nf;
                        break;
                    }
                }
                if (ingressFace == null) continue;

                // Ingress check: can we enter the neighbor through this face?
                FaceMode ingressMode = ingressFace.getMode();
                if (ingressMode != FaceMode.INPUT && ingressMode != FaceMode.BIDIRECTIONAL) continue;

                // Apply the current node's outgoing rate cap just before leaving it
                int newAccRate = Math.min(accRate, current.getMaxOutputRate());

                if (ingressFace.getInputInventory() != null) {
                    // Sink reached — record the route (also cap by sink's input rate).
                    // Guard with visited so a sink reachable via two pipe paths is only
                    // recorded once (first/shortest path wins).
                    if (visited.add(neighborId)) {
                        int finalRate = Math.min(newAccRate, neighbor.getMaxInputRate());
                        routes.add(new TransferRoute(neighborId, ingressFace, finalRate));
                    }
                } else if (!visited.contains(neighborId)) {
                    // Pass-through (pipe/connector) — continue BFS
                    visited.add(neighborId);
                    queue.add(new BfsEntry(neighborId, newAccRate));
                }
            }
        }
    }

    /**
     * Rebuilds routes for every autoPush node in the connected component containing
     * {@code anyNodeId}. Call after placement or chunk-load topology changes.
     */
    public void rebuildRoutesInComponent(UUID anyNodeId, ChunkStore chunkStore) {
        rebuildRoutesForNodes(getComponent(anyNodeId), chunkStore);
    }

    /**
     * Rebuilds routes for every autoPush node in {@code nodeIds}.
     * Use after a break event, passing the component snapshot captured before node removal.
     */
    public void rebuildRoutesForNodes(Collection<UUID> nodeIds, ChunkStore chunkStore) {
        for (UUID nodeId : nodeIds) {
            Vector3i pos = positions.get(nodeId);
            if (pos == null) continue;
            TransferLookup lookup = TransferLookup.resolve(chunkStore, pos);
            if (lookup == null || !lookup.transfer().isAutoPush()) continue;
            rebuildRoutesFrom(nodeId, chunkStore);
        }
    }

    /**
     * Returns the pre-computed routes from {@code sourceId}, or an empty list if
     * the cache does not contain an entry for this node.
     */
    public List<TransferRoute> getRoutes(UUID sourceId) {
        List<TransferRoute> routes = routeCache.get(sourceId);
        return routes != null ? Collections.unmodifiableList(routes) : Collections.emptyList();
    }
}
