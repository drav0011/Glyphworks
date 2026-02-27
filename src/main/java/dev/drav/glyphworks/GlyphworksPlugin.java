package dev.drav.glyphworks;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.event.BreakTransferableBlockEvent;
import dev.drav.glyphworks.transfer.event.PlaceTransferableBlockEvent;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

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
        LOGGER.info("[Glyphworks] setup() — registering components and systems...");
        instance = this;

        this.transferComponentType = this.getChunkStoreRegistry().registerComponent(
                TransferComponent.class,
                "TransferComponent",
                TransferComponent.CODEC);
        LOGGER.info("[Glyphworks] TransferComponent registered.");

        this.getEntityStoreRegistry().registerSystem(new PlaceTransferableBlockEvent());
        LOGGER.info("[Glyphworks] PlaceTransferableBlockEvent system registered.");

        this.getEntityStoreRegistry().registerSystem(new BreakTransferableBlockEvent());
        LOGGER.info("[Glyphworks] BreakTransferableBlockEvent system registered.");

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
