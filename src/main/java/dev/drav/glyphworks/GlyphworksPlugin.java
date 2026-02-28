package dev.drav.glyphworks;

import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.event.BreakTransferableBlockEvent;
import dev.drav.glyphworks.transfer.event.PlaceTransferableBlockEvent;
import dev.drav.glyphworks.transfer.event.UseTransferableBlockEvent;
import dev.drav.glyphworks.transfer.graph.GraphManager;

public class GlyphworksPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger(GlyphworksPlugin.class.getName());
    private static GlyphworksPlugin instance;

    private ComponentType<ChunkStore, TransferComponent> transferComponentType;

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

        GraphManager.get().initialize();

        this.transferComponentType = this.getChunkStoreRegistry().registerComponent(
                TransferComponent.class,
                "TransferComponent",
                TransferComponent.CODEC);

        this.getEntityStoreRegistry().registerSystem(new PlaceTransferableBlockEvent());
        this.getEntityStoreRegistry().registerSystem(new BreakTransferableBlockEvent());
        this.getEntityStoreRegistry().registerSystem(new UseTransferableBlockEvent());

        LOGGER.info("[Glyphworks] setup() complete.");
    }

    @Override
    protected void start() {
        LOGGER.info("[Glyphworks] start() — plugin is live.");
        // this.getChunkStoreRegistry().registerSystem(new TransferSystem());
    }

    @Override
    protected void shutdown() {
        LOGGER.info("[Glyphworks] Shutting down plugin...");

        // Shutdown graph manager (saves all dirty graphs, stops auto-save)
        GraphManager.get().shutdown();

        LOGGER.info("[Glyphworks] Plugin shutdown complete.");
        super.shutdown();
    }

    public ComponentType<ChunkStore, TransferComponent> getTransferComponentType() {
        return transferComponentType;
    }
}
