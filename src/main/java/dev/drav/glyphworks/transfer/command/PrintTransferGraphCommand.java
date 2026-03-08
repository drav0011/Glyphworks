package dev.drav.glyphworks.transfer.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Command: {@code gwtransfer}
 *
 * <p>Prints the current state of the Glyphworks transfer graph — all loaded
 * nodes, their face modes, and which neighbours each face is linked to.
 *
 * <p>Example output:
 * <pre>
 * [Net] 3 node(s) loaded
 *   a3f2c1d8  OUTPUT   autoPush  faces=6  linked=1  → [9b04fe22]
 *   9b04fe22  BIDIR             faces=6  linked=2  → [a3f2c1d8, deadbeef]
 *   deadbeef  INPUT    autoPull  faces=6  linked=1  → [9b04fe22]
 * </pre>
 */
public final class PrintTransferGraphCommand extends AbstractCommand {

    public PrintTransferGraphCommand() {
        super("gwtransfer", "Print the current Glyphworks transfer graph");
    }

    @Override
    @Nullable
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        Map<UUID, TransferComponent> nodes = GlyphworksPlugin.get().getLoadedNodes();
        context.sendMessage(Message.raw("[Net] " + nodes.size() + " node(s) loaded"));

        for (TransferComponent node : nodes.values()) {
            Collection<FacePlane> faces = node.getFaces().values();
            long linked = faces.stream().filter(f -> f.getNeighborNodeId() != null).count();

            String neighborList = faces.stream()
                    .map(FacePlane::getNeighborNodeId)
                    .filter(id -> id != null)
                    .distinct()
                    .map(PrintTransferGraphCommand::shortId)
                    .collect(Collectors.joining(", "));

            StringBuilder line = new StringBuilder("  ");
            line.append(shortId(node.getNodeId()));
            // line.append("  ").append(node.getDefaultFaceMode());
            if (node.isAutoPush()) line.append("  autoPush");
            if (node.isAutoPull()) line.append("  autoPull");
            line.append("  faces=").append(faces.size());
            line.append("  linked=").append(linked);
            if (!neighborList.isEmpty()) {
                line.append("  → [").append(neighborList).append("]");
            }
            context.sendMessage(Message.raw(line.toString()));
        }
        return null;
    }

    private static String shortId(UUID id) {
        String s = id.toString();
        return s.substring(s.length() - 8);
    }
}
