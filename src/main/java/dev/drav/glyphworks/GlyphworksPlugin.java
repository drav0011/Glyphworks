package dev.drav.glyphworks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.transfer.TransferGraph;
import dev.drav.glyphworks.transfer.command.PrintTransferGraphCommand;
import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.event.BreakTransferableBlockEvent;
import dev.drav.glyphworks.transfer.event.ChunkLoadTransferGraphEvent;
import dev.drav.glyphworks.transfer.event.PlaceTransferableBlockEvent;
import dev.drav.glyphworks.transfer.event.UseTransferableBlockEvent;
import dev.drav.glyphworks.transfer.state.PipeStateComputer;
import dev.drav.glyphworks.transfer.state.TransferStateRegistry;

public class GlyphworksPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger(GlyphworksPlugin.class.getName());
    private static GlyphworksPlugin instance;

    private ComponentType<ChunkStore, TransferComponent> transferComponentType;

    /** One graph per world, keyed by stable world UUID. */
    private final Map<UUID, TransferGraph> graphs = new ConcurrentHashMap<>();

    @Nullable
    public TransferGraph getGraph(World world) {
        return graphs.get(world.getWorldConfig().getUuid());
    }

    public TransferGraph getOrCreateGraph(World world) {
        return graphs.computeIfAbsent(world.getWorldConfig().getUuid(), id -> new TransferGraph());
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

        getEventRegistry().registerGlobal(RemoveWorldEvent.class,
                event -> graphs.remove(event.getWorld().getWorldConfig().getUuid()));
        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, ChunkLoadTransferGraphEvent::handle);

        // Register visual state computers per block type.
        // The key must match BlockType.getId() for the root block type.
        // If states stop updating, log "rootId" in TransferStateRegistry.applyState to
        // verify.
        TransferStateRegistry.register("Transfer_PipeNode", PipeStateComputer::compute);

        getCommandRegistry().registerCommand(new PrintTransferGraphCommand());

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
