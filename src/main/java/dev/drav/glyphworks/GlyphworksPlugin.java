package dev.drav.glyphworks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.transfer.command.PrintTransferGraphCommand;
import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.event.BreakTransferableBlockEvent;
import dev.drav.glyphworks.transfer.event.PlaceTransferableBlockEvent;
import dev.drav.glyphworks.transfer.event.UseTransferableBlockEvent;

public class GlyphworksPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger(GlyphworksPlugin.class.getName());
    private static GlyphworksPlugin instance;

    private ComponentType<ChunkStore, TransferComponent> transferComponentType;

    /** All TransferComponent nodes currently loaded in any chunk. */
    private final Map<UUID, TransferComponent> loadedNodes = new ConcurrentHashMap<>();

    public void registerNode(TransferComponent transfer) {
        loadedNodes.put(transfer.getNodeId(), transfer);
    }

    public void unregisterNode(UUID nodeId) {
        loadedNodes.remove(nodeId);
    }

    public Map<UUID, TransferComponent> getLoadedNodes() {
        return loadedNodes;
    }

    public GlyphworksPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static GlyphworksPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.info("[Glyphworks] setup()...");
        instance = this;

        this.transferComponentType = this.getChunkStoreRegistry().registerComponent(
                TransferComponent.class,
                "TransferComponent",
                TransferComponent.CODEC);

        this.getEntityStoreRegistry().registerSystem(new PlaceTransferableBlockEvent());
        this.getEntityStoreRegistry().registerSystem(new BreakTransferableBlockEvent());
        this.getEntityStoreRegistry().registerSystem(new UseTransferableBlockEvent());

        this.getCommandRegistry().registerCommand(new PrintTransferGraphCommand());

        LOGGER.info("[Glyphworks] setup() complete.");
    }

    @Override
    protected void start() {
        LOGGER.info("[Glyphworks] start() — plugin is live.");
        // this.getChunkStoreRegistry().registerSystem(new TransferSystem());
    }

    public ComponentType<ChunkStore, TransferComponent> getTransferComponentType() {
        return transferComponentType;
    }
}
