package dev.drav.glyphworks.item;

import dev.drav.glyphworks.item.component.BlockMinerComponentTests;
import dev.drav.glyphworks.item.component.BlockPlacerComponentTests;
import dev.drav.glyphworks.item.component.ItemPickerComponentTests;
import dev.drav.glyphworks.item.component.ItemSourceComponentTests;
import dev.drav.glyphworks.item.system.BlockMinerSystemTests;
import dev.drav.glyphworks.item.system.BlockPlacerSystemTests;
import dev.drav.glyphworks.item.system.ItemDropperSystemTests;
import dev.drav.glyphworks.item.system.ItemGridTransferTests;
import dev.drav.glyphworks.item.system.ItemPickerSystemTests;
import dev.drav.glyphworks.item.system.ItemSinkSystemTests;
import dev.drav.glyphworks.item.system.ItemSourceSystemTests;

public final class ItemTestRegistrations {

    private ItemTestRegistrations() {
    }

    public static void registerAll() {
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