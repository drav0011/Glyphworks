package dev.drav.glyphworks.item;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeHandlerRegistry;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;
import dev.drav.glyphworks.item.component.BlockMinerComponent;
import dev.drav.glyphworks.item.component.BlockPlacerComponent;
import dev.drav.glyphworks.item.component.ItemDropperComponent;
import dev.drav.glyphworks.item.component.ItemPickerComponent;
import dev.drav.glyphworks.item.component.ItemSinkComponent;
import dev.drav.glyphworks.item.component.ItemSourceComponent;
import dev.drav.glyphworks.item.handlers.ItemGridTypeHandler;
import dev.drav.glyphworks.item.interaction.OpenItemSourceInteraction;
import dev.drav.glyphworks.item.system.BlockMinerSystem;
import dev.drav.glyphworks.item.system.BlockPlacerSystem;
import dev.drav.glyphworks.item.system.ItemDropperSystem;
import dev.drav.glyphworks.item.system.ItemPickerSystem;
import dev.drav.glyphworks.item.system.ItemSingleSlotSetupSystem;
import dev.drav.glyphworks.item.system.ItemSinkSystem;
import dev.drav.glyphworks.item.system.ItemSourceSystem;
import dev.drav.glyphworks.item.tests.component.BlockMinerComponentTests;
import dev.drav.glyphworks.item.tests.component.BlockPlacerComponentTests;
import dev.drav.glyphworks.item.tests.component.ItemPickerComponentTests;
import dev.drav.glyphworks.item.tests.component.ItemSourceComponentTests;
import dev.drav.glyphworks.item.tests.system.BlockMinerSystemTests;
import dev.drav.glyphworks.item.tests.system.BlockPlacerSystemTests;
import dev.drav.glyphworks.item.tests.system.ItemDropperSystemTests;
import dev.drav.glyphworks.item.tests.system.ItemGridTransferTests;
import dev.drav.glyphworks.item.tests.system.ItemPickerSystemTests;
import dev.drav.glyphworks.item.tests.system.ItemSinkSystemTests;
import dev.drav.glyphworks.item.tests.system.ItemSourceSystemTests;

/**
 * Sub-plugin that owns the Item grid type, the item source/sink component
 * types, and their ticking systems.
 */
public final class ItemModule extends GlyphworksModule {

    private ComponentType<ChunkStore, ItemSourceComponent> itemSourceComponentType;
    private ComponentType<ChunkStore, ItemSinkComponent> itemSinkComponentType;
    private ComponentType<ChunkStore, BlockMinerComponent> blockMinerComponentType;
    private ComponentType<ChunkStore, BlockPlacerComponent> blockPlacerComponentType;
    private ComponentType<ChunkStore, ItemPickerComponent> itemPickerComponentType;
    private ComponentType<ChunkStore, ItemDropperComponent> itemDropperComponentType;

    public ComponentType<ChunkStore, ItemSourceComponent> getItemSourceComponentType() {
        return itemSourceComponentType;
    }

    public ComponentType<ChunkStore, ItemSinkComponent> getItemSinkComponentType() {
        return itemSinkComponentType;
    }

    public ComponentType<ChunkStore, BlockMinerComponent> getBlockMinerComponentType() {
        return blockMinerComponentType;
    }

    public ComponentType<ChunkStore, BlockPlacerComponent> getBlockPlacerComponentType() {
        return blockPlacerComponentType;
    }

    public ComponentType<ChunkStore, ItemPickerComponent> getItemPickerComponentType() {
        return itemPickerComponentType;
    }

    public ComponentType<ChunkStore, ItemDropperComponent> getItemDropperComponentType() {
        return itemDropperComponentType;
    }

    @Override
    public void setup(@Nonnull GlyphworksPlugin plugin) {
        GridTypeRegistry.register(GridType.of("Item"));
        GridTypeHandlerRegistry.register(new ItemGridTypeHandler());

        plugin.getCodecRegistry(Interaction.CODEC).register(
                "OpenItemSource",
                OpenItemSourceInteraction.class,
                OpenItemSourceInteraction.CODEC);

        this.itemSourceComponentType = plugin.getChunkStoreRegistry().registerComponent(
                ItemSourceComponent.class, "Glyphworks_ItemSourceComponent", ItemSourceComponent.CODEC);

        this.itemSinkComponentType = plugin.getChunkStoreRegistry().registerComponent(
                ItemSinkComponent.class, "Glyphworks_ItemSinkComponent", ItemSinkComponent.CODEC);

        this.blockMinerComponentType = plugin.getChunkStoreRegistry().registerComponent(
                BlockMinerComponent.class, "Glyphworks_BlockMinerComponent", BlockMinerComponent.CODEC);

        this.blockPlacerComponentType = plugin.getChunkStoreRegistry().registerComponent(
                BlockPlacerComponent.class, "Glyphworks_BlockPlacerComponent", BlockPlacerComponent.CODEC);

        this.itemPickerComponentType = plugin.getChunkStoreRegistry().registerComponent(
                ItemPickerComponent.class, "Glyphworks_ItemPickerComponent", ItemPickerComponent.CODEC);

        this.itemDropperComponentType = plugin.getChunkStoreRegistry().registerComponent(
                ItemDropperComponent.class, "Glyphworks_ItemDropperComponent", ItemDropperComponent.CODEC);
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new ItemSourceSystem());
        plugin.getChunkStoreRegistry().registerSystem(new ItemSinkSystem());
        plugin.getChunkStoreRegistry().registerSystem(new BlockMinerSystem());
        plugin.getChunkStoreRegistry().registerSystem(new BlockPlacerSystem());
        plugin.getChunkStoreRegistry().registerSystem(new ItemPickerSystem());
        plugin.getChunkStoreRegistry().registerSystem(new ItemDropperSystem());
        plugin.getChunkStoreRegistry().registerSystem(new ItemSingleSlotSetupSystem(BlockMinerComponent.getComponentType()) {});
        plugin.getChunkStoreRegistry().registerSystem(new ItemSingleSlotSetupSystem(BlockPlacerComponent.getComponentType()) {});
        plugin.getChunkStoreRegistry().registerSystem(new ItemSingleSlotSetupSystem(ItemPickerComponent.getComponentType()) {});
        plugin.getChunkStoreRegistry().registerSystem(new ItemSingleSlotSetupSystem(ItemDropperComponent.getComponentType()) {});
    }

    @Override
    public void setupTests() {
        ItemGridTransferTests.register("item");
        ItemSourceSystemTests.register("item");
        ItemSinkSystemTests.register("item");
        BlockMinerSystemTests.register("item");
        BlockPlacerSystemTests.register("item");
        ItemPickerSystemTests.register("item");
        ItemDropperSystemTests.register("item");
        ItemSourceComponentTests.register("item");
        ItemPickerComponentTests.register("item");
        BlockMinerComponentTests.register("item");
        BlockPlacerComponentTests.register("item");
    }
}
