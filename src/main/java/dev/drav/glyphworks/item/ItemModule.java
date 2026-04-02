package dev.drav.glyphworks.item;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeHandlerRegistry;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;
import dev.drav.glyphworks.item.component.ItemSinkComponent;
import dev.drav.glyphworks.item.component.ItemSourceComponent;
import dev.drav.glyphworks.item.handlers.ItemGridTypeHandler;
import dev.drav.glyphworks.item.system.ItemSinkSystem;
import dev.drav.glyphworks.item.system.ItemSourceSystem;
import dev.drav.glyphworks.item.tests.ItemGridTransferTests;
import dev.drav.glyphworks.item.tests.ItemSinkSystemTests;
import dev.drav.glyphworks.item.tests.ItemSourceSystemTests;

/**
 * Sub-plugin that owns the Item grid type, the item source/sink component
 * types, and their ticking systems.
 */
public final class ItemModule extends GlyphworksModule {

    private ComponentType<ChunkStore, ItemSourceComponent> itemSourceComponentType;
    private ComponentType<ChunkStore, ItemSinkComponent> itemSinkComponentType;

    public ComponentType<ChunkStore, ItemSourceComponent> getItemSourceComponentType() {
        return itemSourceComponentType;
    }

    public ComponentType<ChunkStore, ItemSinkComponent> getItemSinkComponentType() {
        return itemSinkComponentType;
    }

    @Override
    public void setup(@Nonnull GlyphworksPlugin plugin) {
        GridTypeRegistry.register(GridType.of("Item"));
        GridTypeHandlerRegistry.register(new ItemGridTypeHandler());

        this.itemSourceComponentType = plugin.getChunkStoreRegistry().registerComponent(
                ItemSourceComponent.class, "Glyphworks_ItemSourceComponent", ItemSourceComponent.CODEC);

        this.itemSinkComponentType = plugin.getChunkStoreRegistry().registerComponent(
                ItemSinkComponent.class, "Glyphworks_ItemSinkComponent", ItemSinkComponent.CODEC);
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new ItemSourceSystem());
        plugin.getChunkStoreRegistry().registerSystem(new ItemSinkSystem());
    }

    @Override
    public void setupTests() {
        ItemGridTransferTests.register("item");
        ItemSourceSystemTests.register("item");
        ItemSinkSystemTests.register("item");
    }
}
