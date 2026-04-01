package dev.drav.glyphworks.grid.graph;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import dev.drav.glyphworks.grid.type.GridType;

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
 *
 * <h3>Connected-component tracking</h3>
 * In addition to the adjacency map, the graph maintains two companion maps that
 * identify which independent network (connected component) each node belongs to:
 * <ul>
 * <li>{@code nodeToRoot} — maps every node to its component's <em>root</em>,
 * an arbitrary but stable representative position.</li>
 * <li>{@code components} — maps each root to the full set of positions in that
 * network.</li>
 * </ul>
 * Both maps are kept in sync by every mutation ({@link #addNode},
 * {@link #addEdge}, {@link #removeNode}, {@link #removeEdge}). Callers can
 * therefore query component membership in O(1) via
 * {@link #getComponentRoot(Vector3i)} and
 * {@link #getComponentMembers(Vector3i)} without triggering a BFS.
 */
public final class GridGraph {

    /**
     * The grid type this graph tracks. Used by systems that need to look up the
     * matching {@link dev.drav.glyphworks.grid.component.GridTypeEntry} on a
     * {@link dev.drav.glyphworks.grid.component.GridComponent}.
     */
    @Nullable
    private final GridType gridType;

    /**
     * pos → set of directly connected positions (undirected, both sides stored).
     */
    private final Map<Vector3i, Set<Vector3i>> adjacency  = new HashMap<>();

    /** pos → the root of the connected component that contains pos. */
    private final Map<Vector3i, Vector3i>       nodeToRoot = new HashMap<>();

    /** root → all member positions of that connected component. */
    private final Map<Vector3i, Set<Vector3i>>  components = new HashMap<>();

    /**
     * Creates a graph for the given grid type.
     */
    public GridGraph(@Nullable GridType gridType) {
        this.gridType = gridType;
    }

    /**
     * Returns the {@link GridType} this graph belongs to, or {@code null} if
     * unspecified.
     */
    @Nullable
    public GridType getGridType() {
        return gridType;
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Register a node. Does nothing if already present.
     * The new node starts as its own isolated component.
     */
    public void addNode(Vector3i pos) {
        if (adjacency.containsKey(pos))
            return;
        adjacency.put(pos, new HashSet<>());
        nodeToRoot.put(pos, pos);
        Set<Vector3i> comp = new HashSet<>();
        comp.add(pos);
        components.put(pos, comp);
    }

    /**
     * Remove a node and every edge that connects to it.
     * Any surviving neighbors whose component is split are re-indexed into their
     * new independent networks.
     */
    public void removeNode(Vector3i pos) {
        Set<Vector3i> neighbors = adjacency.remove(pos);
        if (neighbors == null)
            return;
        for (Vector3i neighbor : neighbors) {
            Set<Vector3i> neighborEdges = adjacency.get(neighbor);
            if (neighborEdges != null)
                neighborEdges.remove(pos);
        }

        Vector3i oldRoot = nodeToRoot.remove(pos);
        if (oldRoot == null)
            return;
        Set<Vector3i> oldComp = components.get(oldRoot);
        if (oldComp == null)
            return;
        oldComp.remove(pos);

        if (oldComp.isEmpty()) {
            components.remove(oldRoot);
            return;
        }

        // Removal may have split the component — re-discover sub-components among
        // the surviving members using the (already updated) adjacency.
        components.remove(oldRoot);
        for (Vector3i member : oldComp)
            nodeToRoot.remove(member);
        rediscoverComponents(oldComp);
    }

    /**
     * Record a link between two nodes. Idempotent.
     * If the two nodes belong to different components they are merged into one.
     */
    public void addEdge(Vector3i a, Vector3i b) {
        Set<Vector3i> setA = adjacency.get(a);
        Set<Vector3i> setB = adjacency.get(b);
        if (setA != null)
            setA.add(b);
        if (setB != null)
            setB.add(a);

        Vector3i rootA = nodeToRoot.get(a);
        Vector3i rootB = nodeToRoot.get(b);
        if (rootA == null || rootB == null || rootA.equals(rootB))
            return;

        Set<Vector3i> compA = components.get(rootA);
        Set<Vector3i> compB = components.get(rootB);
        if (compA == null || compB == null)
            return;

        // Merge smaller component into larger (keeps larger component's root).
        if (compA.size() >= compB.size()) {
            for (Vector3i node : compB) {
                nodeToRoot.put(node, rootA);
                compA.add(node);
            }
            components.remove(rootB);
        } else {
            for (Vector3i node : compA) {
                nodeToRoot.put(node, rootB);
                compB.add(node);
            }
            components.remove(rootA);
        }
    }

    /**
     * Remove the link between two nodes (both directions).
     * If this disconnects the two nodes, their component is split into two.
     */
    public void removeEdge(Vector3i a, Vector3i b) {
        Set<Vector3i> setA = adjacency.get(a);
        Set<Vector3i> setB = adjacency.get(b);
        if (setA != null)
            setA.remove(b);
        if (setB != null)
            setB.remove(a);

        Vector3i rootA = nodeToRoot.get(a);
        Vector3i rootB = nodeToRoot.get(b);
        if (rootA == null || !rootA.equals(rootB))
            return; // already different components or nodes not tracked

        Set<Vector3i> oldComp = components.get(rootA);
        if (oldComp == null)
            return;

        // BFS from a within the old component to check reachability.
        Set<Vector3i> reachableFromA = bfsWithin(a, oldComp);
        if (reachableFromA.contains(b))
            return; // still connected — no split

        Set<Vector3i> otherPart = new HashSet<>(oldComp);
        otherPart.removeAll(reachableFromA);

        components.remove(rootA);
        assignComponent(reachableFromA);
        assignComponent(otherPart);
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

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
     * Each edge is stored in both directions, so the total neighbor count is
     * halved.
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
     * Returns the canonical root of the connected component containing
     * {@code pos}, or {@code null} if {@code pos} is not in this graph.
     *
     * <p>
     * The root is an arbitrary but stable representative — it changes only when
     * topology changes (block placed or broken). All members of the same network
     * share the same root value, so equality of roots identifies membership.
     */
    @Nullable
    public Vector3i getComponentRoot(Vector3i pos) {
        return nodeToRoot.get(pos);
    }

    /**
     * Returns an unmodifiable view of every node in the component whose root is
     * {@code root}. Returns an empty set if {@code root} is not a known root.
     *
     * <p>
     * Combine with {@link #getComponentRoot(Vector3i)} to iterate an entire
     * independent network in O(N) without a BFS:
     * <pre>
     *   Vector3i root = graph.getComponentRoot(myPos);
     *   for (Vector3i member : graph.getComponentMembers(root)) { … }
     * </pre>
     */
    public Set<Vector3i> getComponentMembers(Vector3i root) {
        Set<Vector3i> comp = components.get(root);
        return comp != null ? Collections.unmodifiableSet(comp) : Collections.emptySet();
    }

    /**
     * Returns an unmodifiable view of every current component root. Each root
     * uniquely identifies one independent network in this graph.
     */
    public Set<Vector3i> getAllComponentRoots() {
        return Collections.unmodifiableSet(components.keySet());
    }

    /**
     * BFS from {@code start} — returns all positions reachable in the same
     * connected component, including {@code start} itself. Returns an empty set
     * if {@code start} is not in this graph.
     *
     * <p>
     * This performs a live BFS and is O(N) in the component size. Prefer
     * {@link #getComponentMembers(Vector3i)} when the component root is already
     * known, as that is O(1).
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * BFS from {@code start} visiting only nodes present in {@code allowed}.
     * Returns the set of reachable nodes (including {@code start}).
     */
    private Set<Vector3i> bfsWithin(Vector3i start, Set<Vector3i> allowed) {
        Set<Vector3i> visited = new HashSet<>();
        if (!allowed.contains(start))
            return visited;
        Queue<Vector3i> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            Vector3i cur = queue.poll();
            Set<Vector3i> adj = adjacency.get(cur);
            if (adj == null)
                continue;
            for (Vector3i nb : adj) {
                if (allowed.contains(nb) && visited.add(nb))
                    queue.add(nb);
            }
        }
        return visited;
    }

    /**
     * Re-discovers connected sub-components among {@code seeds} (using current
     * adjacency) and registers each as a new component entry in both
     * {@code components} and {@code nodeToRoot}.
     */
    private void rediscoverComponents(Set<Vector3i> seeds) {
        Set<Vector3i> unvisited = new HashSet<>(seeds);
        while (!unvisited.isEmpty()) {
            Vector3i seed = unvisited.iterator().next();
            Set<Vector3i> newComp = bfsWithin(seed, unvisited);
            unvisited.removeAll(newComp);
            assignComponent(newComp);
        }
    }

    /**
     * Registers {@code members} as a new component, using the first iterated
     * member as the root. Updates both {@code components} and {@code nodeToRoot}.
     */
    private void assignComponent(Set<Vector3i> members) {
        if (members.isEmpty())
            return;
        Vector3i root = members.iterator().next();
        components.put(root, members);
        for (Vector3i m : members)
            nodeToRoot.put(m, root);
    }
}
