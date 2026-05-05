package dev.drav.glyphworks.grid.system;

import java.util.Set;

import org.joml.Vector3i;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;
import dev.drav.glyphworks.test.runner.TestRunnerContext;

/**
 * Suite {@code "grid_graph"} — tests the {@link GridGraph} data structure
 * directly.
 *
 * <p>
 * No blocks are placed. Tests manipulate the graph object via
 * {@link GlyphworksPlugin#getOrCreateGridGraph} using a test-only grid type
 * ({@code "__grid_test__"}) that never interferes with the live Item grid.
 *
 * <p>
 * Each test cleans up its positions at the start so re-running the suite
 * is idempotent regardless of previous state.
 */
public final class GridGraphTests {

    private static final String TEST_TYPE_PREFIX = "__grid_test__";

    private GridGraphTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("grid_graph")
                .test(addNodeContains())
                .test(addRemoveNode())
                .test(addEdgeBothDirections())
                .test(removeEdgeBothDirections())
                .test(removeNodeCleansEdges())
                .test(edgeCountChain())
                .test(getComponentBfs())
                .test(getComponentIsolated())
                .test(getComponentUnknownStart())
                .test(addEdgeNonExistentNodesNoOp())
                .test(addNodeIdempotentPreservesEdges());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static GridGraph graph(TestRunnerContext ctx) {
        GridType testType = GridType.of(TEST_TYPE_PREFIX
                + ":" + ctx.getOriginX()
                + ":" + ctx.getOriginY()
                + ":" + ctx.getOriginZ());

        return GlyphworksPlugin.get().getGridModule().getOrCreateGridGraph(ctx.getWorld(), testType);
    }

    private static Vector3i v(int x, int y, int z) {
        return new Vector3i(x, y, z);
    }

    private static Vector3i a(int ox, int oy, int oz) {
        return v(ox, oy, oz);
    }

    private static Vector3i b(int ox, int oy, int oz) {
        return v(ox + 1, oy, oz);
    }

    private static Vector3i c(int ox, int oy, int oz) {
        return v(ox + 2, oy, oz);
    }

    // -------------------------------------------------------------------------
    // Test 1: addNode → contains; before add → not contains
    // -------------------------------------------------------------------------

    private static TestCase addNodeContains() {
        return new TestCase("add_node_contains", 4, 3, 1)
                // Cleanup residual state from any previous run.
                .step(Steps.run(ctx -> graph(ctx).removeNode(
                        a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()))))
                .step(Steps.assertThat(ctx -> {
                    Vector3i pos = a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return !graph(ctx).contains(pos);
                }, "graph does not contain node before addNode"))
                .step(Steps.run(
                        ctx -> graph(ctx).addNode(a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()))))
                .step(Steps.assertThat(ctx -> {
                    Vector3i pos = a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return graph(ctx).contains(pos);
                }, "graph contains node after addNode"))
                // Cleanup.
                .step(Steps.run(ctx -> graph(ctx).removeNode(
                        a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()))));
    }

    // -------------------------------------------------------------------------
    // Test 2: addNode then removeNode → not contains
    // -------------------------------------------------------------------------

    private static TestCase addRemoveNode() {
        return new TestCase("add_remove_node", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    Vector3i pos = a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    g.removeNode(pos); // cleanup
                    g.addNode(pos);
                }))
                .step(Steps.assertThat(
                        ctx -> graph(ctx).contains(a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ())),
                        "addNode registers node in graph"))
                .step(Steps.run(ctx -> graph(ctx)
                        .removeNode(a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()))))
                .step(Steps.assertThat(
                        ctx -> !graph(ctx).contains(a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ())),
                        "removeNode unregisters node from graph"));
    }

    // -------------------------------------------------------------------------
    // Test 3: addEdge registers the edge on both endpoints
    // -------------------------------------------------------------------------

    private static TestCase addEdgeBothDirections() {
        return new TestCase("add_edge_both_directions", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    g.removeNode(pa);
                    g.removeNode(pb);
                    g.addNode(pa);
                    g.addNode(pb);
                    g.addEdge(pa, pb);
                }))
                .step(Steps.assertThat(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    return g.getNeighbors(pa).contains(pb) && g.getNeighbors(pb).contains(pa);
                }, "addEdge registers edge in both directions"))
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    g.removeNode(a(ox, oy, oz));
                    g.removeNode(b(ox, oy, oz));
                }));
    }

    // -------------------------------------------------------------------------
    // Test 4: removeEdge clears the edge on both endpoints
    // -------------------------------------------------------------------------

    private static TestCase removeEdgeBothDirections() {
        return new TestCase("remove_edge_both_directions", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    g.removeNode(pa);
                    g.removeNode(pb);
                    g.addNode(pa);
                    g.addNode(pb);
                    g.addEdge(pa, pb);
                    g.removeEdge(pa, pb);
                }))
                .step(Steps.assertThat(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    return !g.getNeighbors(pa).contains(pb) && !g.getNeighbors(pb).contains(pa);
                }, "removeEdge removes edge from both directions"))
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    g.removeNode(a(ox, oy, oz));
                    g.removeNode(b(ox, oy, oz));
                }));
    }

    // -------------------------------------------------------------------------
    // Test 5: removeNode also removes the position from its neighbors' edge sets
    // -------------------------------------------------------------------------

    private static TestCase removeNodeCleansEdges() {
        return new TestCase("remove_node_cleans_edges", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    g.removeNode(pa);
                    g.removeNode(pb);
                    g.addNode(pa);
                    g.addNode(pb);
                    g.addEdge(pa, pb);
                    g.removeNode(pa); // remove a — b's neighbor set should no longer contain a
                }))
                .step(Steps.assertThat(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    return !g.contains(pa) && !g.getNeighbors(pb).contains(pa);
                }, "removeNode removes node from its neighbors' edge sets"))
                .step(Steps.run(ctx -> graph(ctx)
                        .removeNode(b(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()))));
    }

    // -------------------------------------------------------------------------
    // Test 6: chain of A–B–C → getEdgeCount == 2
    // -------------------------------------------------------------------------

    private static TestCase edgeCountChain() {
        return new TestCase("edge_count_chain", 5, 3, 1)
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    Vector3i pc = c(ox, oy, oz);
                    g.removeNode(pa);
                    g.removeNode(pb);
                    g.removeNode(pc);
                    g.addNode(pa);
                    g.addNode(pb);
                    g.addNode(pc);
                    g.addEdge(pa, pb);
                    g.addEdge(pb, pc);
                }))
                .step(Steps.assertThat(ctx -> graph(ctx).getEdgeCount() == 2,
                        "chain of 3 nodes A-B-C has exactly 2 edges"))
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    g.removeNode(a(ox, oy, oz));
                    g.removeNode(b(ox, oy, oz));
                    g.removeNode(c(ox, oy, oz));
                }));
    }

    // -------------------------------------------------------------------------
    // Test 7: getComponent (BFS) from A returns {A, B, C} for chain A–B–C
    // -------------------------------------------------------------------------

    private static TestCase getComponentBfs() {
        return new TestCase("get_component_bfs", 5, 3, 1)
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    Vector3i pc = c(ox, oy, oz);
                    g.removeNode(pa);
                    g.removeNode(pb);
                    g.removeNode(pc);
                    g.addNode(pa);
                    g.addNode(pb);
                    g.addNode(pc);
                    g.addEdge(pa, pb);
                    g.addEdge(pb, pc);
                }))
                .step(Steps.assertThat(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    Vector3i pc = c(ox, oy, oz);
                    Set<Vector3i> comp = g.getComponent(pa);
                    return comp.size() == 3 && comp.contains(pa) && comp.contains(pb) && comp.contains(pc);
                }, "getComponent from A reaches B and C through chain A-B-C"))
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    g.removeNode(a(ox, oy, oz));
                    g.removeNode(b(ox, oy, oz));
                    g.removeNode(c(ox, oy, oz));
                }));
    }

    // -------------------------------------------------------------------------
    // Test 8: getComponent on A does not include isolated B
    // -------------------------------------------------------------------------

    private static TestCase getComponentIsolated() {
        return new TestCase("get_component_isolated", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    g.removeNode(pa);
                    g.removeNode(pb);
                    g.addNode(pa);
                    g.addNode(pb);
                    // No edge added — A and B are isolated from each other.
                }))
                .step(Steps.assertThat(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    Set<Vector3i> comp = g.getComponent(pa);
                    return comp.size() == 1 && comp.contains(pa) && !comp.contains(pb);
                }, "getComponent on isolated A does not reach B"))
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    g.removeNode(a(ox, oy, oz));
                    g.removeNode(b(ox, oy, oz));
                }));
    }

    // -------------------------------------------------------------------------
    // Test 9: getComponent on a position not in the graph returns empty set
    // -------------------------------------------------------------------------

    private static TestCase getComponentUnknownStart() {
        return new TestCase("get_component_unknown_start", 3, 3, 1)
                .step(Steps.run(ctx -> {
                    // Ensure the position is definitely not in the graph.
                    graph(ctx).removeNode(a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                }))
                .step(Steps.assertThat(ctx -> {
                    Vector3i pos = a(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    Set<Vector3i> comp = graph(ctx).getComponent(pos);
                    return comp.isEmpty();
                }, "getComponent on unknown start position returns empty set"));
    }

    // -------------------------------------------------------------------------
    // Test 10: addEdge on non-existent nodes is a silent no-op
    // -------------------------------------------------------------------------

    private static TestCase addEdgeNonExistentNodesNoOp() {
        return new TestCase("add_edge_non_existent_nodes_no_op", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Ensure neither node is present.
                    g.removeNode(a(ox, oy, oz));
                    g.removeNode(b(ox, oy, oz));
                    // addEdge on non-existent nodes — should do nothing.
                    g.addEdge(a(ox, oy, oz), b(ox, oy, oz));
                }))
                .step(Steps.assertThat(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Nodes must not have been implicitly created.
                    return !g.contains(a(ox, oy, oz)) && !g.contains(b(ox, oy, oz));
                }, "addEdge on non-existent nodes is a no-op and does not implicitly create phantom nodes"));
    }

    // -------------------------------------------------------------------------
    // Test 11: addNode called twice on the same position preserves existing edges
    // -------------------------------------------------------------------------

    private static TestCase addNodeIdempotentPreservesEdges() {
        return new TestCase("add_node_idempotent_preserves_edges", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    g.removeNode(pa);
                    g.removeNode(pb);
                    g.addNode(pa);
                    g.addNode(pb);
                    g.addEdge(pa, pb);
                    // Call addNode again on the already-registered node.
                    g.addNode(pa);
                }))
                .step(Steps.assertThat(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = a(ox, oy, oz);
                    Vector3i pb = b(ox, oy, oz);
                    // Edge must still be intact after the duplicate addNode call.
                    return g.getNeighbors(pa).contains(pb) && g.getNeighbors(pb).contains(pa);
                }, "addNode on an already-registered node is idempotent and preserves existing edges"))
                .step(Steps.run(ctx -> {
                    GridGraph g = graph(ctx);
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    g.removeNode(a(ox, oy, oz));
                    g.removeNode(b(ox, oy, oz));
                }));
    }
}
