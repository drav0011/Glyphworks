package dev.drav.glyphworks.grid.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;

/**
 * /glyphgraph [--type <id>] [--verbose]
 *
 * <p>Prints the in-memory grid graph for the current world.
 * <ul>
 *   <li>Without flags: one summary line per registered type.</li>
 *   <li>{@code --type <id>}: restrict output to that type.</li>
 *   <li>{@code --verbose}: also list every node and its neighbour positions.</li>
 * </ul>
 */
public final class GridGraphCommand extends AbstractWorldCommand {

    private final OptionalArg<String> typeArg;
    private final FlagArg verboseArg;

    public GridGraphCommand() {
        super("glyphgraph", "Print the Glyphworks grid graph for this world");
        this.typeArg = withOptionalArg("type", "Grid type ID to inspect (e.g. Item)", ArgTypes.STRING);
        this.verboseArg = withFlagArg("verbose", "Also list individual node positions and their neighbours");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {

        String filterType = typeArg.get(context);
        boolean verbose = verboseArg.get(context);

        Collection<GridType> types;
        if (filterType != null) {
            GridType t = GridTypeRegistry.get(filterType);
            if (t == null) {
                context.sendMessage(Message.raw("[Glyphworks] Unknown grid type: \"" + filterType + "\""));
                return;
            }
            types = List.of(t);
        } else {
            types = GridTypeRegistry.getAll();
        }

        if (types.isEmpty()) {
            context.sendMessage(Message.raw("[Glyphworks] No grid types registered."));
            return;
        }

        context.sendMessage(Message.raw("=== Glyphworks Grid Graph — world: " + world.getName() + " ==="));

        for (GridType type : types) {
            GridGraph graph = GlyphworksPlugin.get().getGridGraph(world, type);

            if (graph == null) {
                context.sendMessage(Message.raw("  [" + type.id() + "] <no graph>"));
                continue;
            }

            Set<Vector3i> nodes = graph.getNodePositions();
            int nodeCount = nodes.size();
            int edgeCount = graph.getEdgeCount();
            int componentCount = countComponents(graph, nodes);

            context.sendMessage(Message.raw(
                    "  [" + type.id() + "] nodes=" + nodeCount
                    + "  edges=" + edgeCount
                    + "  components=" + componentCount));

            if (verbose && nodeCount > 0) {
                // Sort for stable, readable output.
                List<Vector3i> sorted = new ArrayList<>(nodes);
                sorted.sort(Comparator
                        .comparingInt(Vector3i::getY)
                        .thenComparingInt(Vector3i::getX)
                        .thenComparingInt(Vector3i::getZ));

                for (Vector3i pos : sorted) {
                    Set<Vector3i> neighbours = graph.getNeighbors(pos);
                    if (neighbours.isEmpty()) {
                        context.sendMessage(Message.raw("    " + fmtPos(pos) + "  (isolated)"));
                    } else {
                        StringBuilder sb = new StringBuilder("    ").append(fmtPos(pos)).append("  → ");
                        boolean first = true;
                        for (Vector3i n : neighbours) {
                            if (!first) sb.append(", ");
                            sb.append(fmtPos(n));
                            first = false;
                        }
                        context.sendMessage(Message.raw(sb.toString()));
                    }
                }
            }
        }

        context.sendMessage(Message.raw("=== end ==="));
    }

    private static int countComponents(GridGraph graph, Set<Vector3i> allNodes) {
        Set<Vector3i> visited = new java.util.HashSet<>();
        int count = 0;
        for (Vector3i node : allNodes) {
            if (visited.add(node)) {
                visited.addAll(graph.getComponent(node));
                count++;
            }
        }
        return count;
    }

    private static String fmtPos(Vector3i pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}
