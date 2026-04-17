package dev.drav.glyphworks.item.component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

public final class ItemSourceComponent implements Component<ChunkStore> {

    public static final BuilderCodec<ItemSourceComponent> CODEC = BuilderCodec
            .builder(ItemSourceComponent.class, () -> new ItemSourceComponent())
            .append(
                    new KeyedCodec<>("Glyphworks_ItemSourceComponent_SelectorContainer", SimpleItemContainer.CODEC),
                    (c, v) -> c.selectorContainer = v,
                    c -> c.selectorContainer)
            .add()
            .build();

    public static ComponentType<ChunkStore, ItemSourceComponent> getComponentType() {
        return GlyphworksPlugin.get().getItemModule().getItemSourceComponentType();
    }

    private SimpleItemContainer selectorContainer;

    public ItemSourceComponent() {
        this.selectorContainer = new SimpleItemContainer((short) 1);
    }

    public ItemSourceComponent(@Nonnull ItemSourceComponent other) {
        this.selectorContainer = new SimpleItemContainer((short) 1);
        ItemStack existingStack = other.selectorContainer.getItemStack((short) 0);
        if (existingStack != null) {
            this.selectorContainer.setItemStackForSlot((short) 0, existingStack, false);
        }
    }

    @Nullable
    public String getSelectedItemId() {
        ItemStack stack = selectorContainer.getItemStack((short) 0);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getItemId();
    }

    public SimpleItemContainer getSelectorContainer() {
        return selectorContainer;
    }

    @Override
    public ItemSourceComponent clone() {
        return new ItemSourceComponent(this);
    }
}
