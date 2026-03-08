package dev.drav.glyphworks.transfer.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.transfer.TransferGraph;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

/**
 * Command: {@code gwtransfer}
 *
 * <p>Prints the current state of the Glyphworks transfer graph for the world
 * the running player is in — all nodes, their positions, and adjacency.
 *
 * <p>Example output:
 * <pre>
 * [Net] world "Zone1" — 3 node(s)
 *   a3f2c1d8  (12, 64, -8)   → [9b04fe22]
 *   9b04fe22  (13, 64, -8)   → [a3f2c1d8, deadbeef]
 *   deadbeef  (14, 64, -8)   → [9b04fe22]
 * </pre>
 */
public final class PrintTransferGraphCommand extends AbstractPlayerCommand {

    public PrintTransferGraphCommand() {
        super("gwtransfer", "Print the Glyphworks transfer graph for the current world");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        TransferGraph graph = GlyphworksPlugin.get().getGraph(world);
        if (graph == null) {
            context.sendMessage(Message.raw("[Net] No graph for world \"" + world.getName() + "\" (no nodes ever placed)."));
            return;
        }

        Set<UUID> nodeIds = graph.getNodeIds();
        context.sendMessage(Message.raw("[Net] world \"" + world.getName() + "\" — " + nodeIds.size() + " node(s)"));

        for (UUID id : nodeIds) {
            var pos = graph.getPosition(id);
            Set<UUID> neighbors = graph.getNeighbors(id);

            StringBuilder line = new StringBuilder("  ").append(shortId(id));
            if (pos != null) {
                line.append("  (").append(pos.x).append(", ").append(pos.y).append(", ").append(pos.z).append(")");
            }
            if (!neighbors.isEmpty()) {
                StringBuilder nb = new StringBuilder();
                for (UUID n : neighbors) {
                    if (!nb.isEmpty()) nb.append(", ");
                    nb.append(shortId(n));
                }
                line.append("  → [").append(nb).append("]");
            }
            context.sendMessage(Message.raw(line.toString()));
        }
    }

    private static String shortId(UUID id) {
        String s = id.toString();
        return s.substring(s.length() - 8);
    }
}
