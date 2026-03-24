package dev.drav.glyphworks.crafting;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.codec.AssetCodecMapCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.interaction.OpenAutoCraftingBenchInteraction;
import dev.drav.glyphworks.crafting.system.AutoCraftingBenchSetupSystem;
import dev.drav.glyphworks.crafting.system.AutoCraftingBenchSystem;
import dev.drav.glyphworks.crafting.system.ProcessingBenchAutoStartSystem;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeHandlerRegistry;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;
import dev.drav.glyphworks.transfer.item.ItemGridTypeHandler;

/**
 * Sub-plugin that owns the Item grid type, the auto-crafting bench component,
 * its interaction codec, and the three crafting/bench systems.
 */
public final class CraftingModule extends GlyphworksModule {

    private ComponentType<ChunkStore, AutoCraftingBenchBlock> autoCraftingBenchBlockComponentType;

    public ComponentType<ChunkStore, AutoCraftingBenchBlock> getAutoCraftingBenchBlockComponentType() {
        return autoCraftingBenchBlockComponentType;
    }

    @Override
    public void setup(@Nonnull GlyphworksPlugin plugin) {
        GridTypeRegistry.register(GridType.of("Item"));
        GridTypeHandlerRegistry.register(new ItemGridTypeHandler());

        this.autoCraftingBenchBlockComponentType = plugin.getChunkStoreRegistry().registerComponent(
                AutoCraftingBenchBlock.class, "AutoCraftingBenchBlock", AutoCraftingBenchBlock.CODEC);

        plugin.getCodecRegistry((AssetCodecMapCodec) Interaction.CODEC).register(
                "OpenAutoCraftingBench", OpenAutoCraftingBenchInteraction.class,
                (BuilderCodec) OpenAutoCraftingBenchInteraction.CODEC);
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new ProcessingBenchAutoStartSystem());
        plugin.getChunkStoreRegistry().registerSystem(new AutoCraftingBenchSetupSystem());
        plugin.getChunkStoreRegistry().registerSystem(new AutoCraftingBenchSystem());
    }
}
