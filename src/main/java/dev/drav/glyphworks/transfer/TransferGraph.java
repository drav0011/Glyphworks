package dev.drav.glyphworks.transfer;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;

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

    // ── Mutation ──────────────────────────────────────────────────────────────

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
}
