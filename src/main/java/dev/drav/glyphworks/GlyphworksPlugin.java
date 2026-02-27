package dev.drav.glyphworks;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.transfer.component.TransferConfigComponent;
import dev.drav.glyphworks.transfer.component.TransferLimitComponent;
import dev.drav.glyphworks.transfer.component.TransferNodeComponent;
import dev.drav.glyphworks.transfer.graph.GraphCache;
import dev.drav.glyphworks.transfer.system.GraphCacheUpdateSystem;
import dev.drav.glyphworks.transfer.system.TransferPullSystem;
import dev.drav.glyphworks.transfer.system.TransferPushSystem;

import javax.annotation.Nonnull;

public class GlyphworksPlugin extends JavaPlugin {

    private static GlyphworksPlugin instance;

    public GlyphworksPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static GlyphworksPlugin get() {
        return instance;
    }
    
    @Override
    protected void setup() {
        instance = this;
    }
}
