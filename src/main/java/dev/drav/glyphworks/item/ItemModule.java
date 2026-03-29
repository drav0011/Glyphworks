package dev.drav.glyphworks.item;

import javax.annotation.Nonnull;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeHandlerRegistry;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;
import dev.drav.glyphworks.item.handlers.ItemGridTypeHandler;
import dev.drav.glyphworks.item.tests.ItemGridTransferTests;

/**
 * Sub-plugin that owns the Item grid type and its handler.
 */
public final class ItemModule extends GlyphworksModule {

    @Override
    public void setup(@Nonnull GlyphworksPlugin plugin) {
        GridTypeRegistry.register(GridType.of("Item"));
        GridTypeHandlerRegistry.register(new ItemGridTypeHandler());
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
    }

    @Override
    public void setupTests() {
        ItemGridTransferTests.register("item");
    }
}
