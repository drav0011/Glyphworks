package dev.drav.glyphworks.grid.graph;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.hypixel.hytale.math.vector.Vector3i;

/**
 * Runtime position-keyed graph of
 * {@link dev.drav.glyphworks.grid.component.GridComponent}
 * nodes for a single world and a single grid type.
 *
 * <p>
 * Not serialised — edges are reconstructed from {@code GridComponent.neighbors}
 * as blocks are placed, broken, and loaded.
 *
 * <p>
 * Not thread-safe. Only ever accessed on the owning world's thread.
 */
public final class GridGraph {

    /**
     * pos → set of directly connected positions (undirected, both sides stored).
     */
    private final Map<Vector3i, Set<Vector3i>> adjacency = new HashMap<>();

    /** Register a node. Does nothing if already present. */
    public void addNode(Vector3i pos) {
        adjacency.putIfAbsent(pos, new HashSet<>());
    }

    /** Remove a node and every edge that connects to it. */
    public void removeNode(Vector3i pos) {
        Set<Vector3i> neighbors = adjacency.remove(pos);
        if (neighbors == null)
            return;
        for (Vector3i neighbor : neighbors) {
            Set<Vector3i> neighborEdges = adjacency.get(neighbor);
            if (neighborEdges != null)
                neighborEdges.remove(pos);
        }
    }

    /** Record a link between two nodes. Idempotent. */
    public void addEdge(Vector3i a, Vector3i b) {
        Set<Vector3i> setA = adjacency.get(a);
        Set<Vector3i> setB = adjacency.get(b);
        if (setA != null)
            setA.add(b);
        if (setB != null)
            setB.add(a);
    }

    /** Remove the link between two nodes (both directions). */
    public void removeEdge(Vector3i a, Vector3i b) {
        Set<Vector3i> setA = adjacency.get(a);
        Set<Vector3i> setB = adjacency.get(b);
        if (setA != null)
            setA.remove(b);
        if (setB != null)
            setB.remove(a);
    }

    /** Returns an unmodifiable view of the neighbors, or an empty set if absent. */
    public Set<Vector3i> getNeighbors(Vector3i pos) {
        Set<Vector3i> neighbors = adjacency.get(pos);
        return neighbors != null ? Collections.unmodifiableSet(neighbors) : Collections.emptySet();
    }

    public boolean contains(Vector3i pos) {
        return adjacency.containsKey(pos);
    }

    /**
     * Returns the number of unique undirected edges in this graph.
     * Each edge is stored in both directions, so the total neighbor count is halved.
     */
    public int getEdgeCount() {
        int total = 0;
        for (Set<Vector3i> neighbors : adjacency.values()) {
            total += neighbors.size();
        }
        return total / 2;
    }

    /**
     * Returns an unmodifiable view of all node positions currently in the graph.
     */
    public Set<Vector3i> getNodePositions() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }

    /**
     * BFS from {@code start} — returns all positions reachable in the same
     * connected
     * component, including {@code start} itself. Returns an empty set if
     * {@code start}
     * is not in this graph.
     */
    public Set<Vector3i> getComponent(Vector3i start) {
        Set<Vector3i> visited = new HashSet<>();
        if (!adjacency.containsKey(start))
            return visited;
        Queue<Vector3i> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            Vector3i current = queue.poll();
            Set<Vector3i> neighbors = adjacency.get(current);
            if (neighbors == null)
                continue;
            for (Vector3i neighbor : neighbors) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }
}
