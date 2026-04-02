package dev.drav.glyphworks.item.component;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Marks a block as a creative item source — a block that keeps its
 * {@link com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock}
 * filled to capacity with a fixed item type every tick.
 *
 * <p>
 * The {@code itemId} is the asset ID of the item (e.g. {@code "Rock_Stone"})
 * that this source produces infinitely. The paired
 * {@link dev.drav.glyphworks.item.system.ItemSourceSystem} refills empty slots
 * every tick so downstream grid consumers never run dry.
 */
public final class ItemSourceComponent implements Component<ChunkStore> {

    public static final BuilderCodec<ItemSourceComponent> CODEC = BuilderCodec
            .builder(ItemSourceComponent.class, ItemSourceComponent::new)
            .append(
                    new KeyedCodec<>("Glyphworks_ItemSourceComponent_ItemId", Codec.STRING),
                    (c, v) -> c.itemId = v,
                    c -> c.itemId)
            .add()
            .build();

    public static ComponentType<ChunkStore, ItemSourceComponent> getComponentType() {
        return GlyphworksPlugin.get().getItemSourceComponentType();
    }

    /** Asset ID of the item this block outputs infinitely. */
    @Nonnull
    private String itemId = "Rock_Stone";

    public ItemSourceComponent() {
    }

    @Nonnull
    public String getItemId() {
        return itemId;
    }

    @Override
    public ItemSourceComponent clone() {
        ItemSourceComponent c = new ItemSourceComponent();
        c.itemId = itemId;
        return c;
    }
}
