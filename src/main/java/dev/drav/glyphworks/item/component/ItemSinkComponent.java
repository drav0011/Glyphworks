package dev.drav.glyphworks.item.component;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Marker component that designates a block as a creative item sink — a block
 * that instantly destroys any items it receives from the grid.
 *
 * <p>
 * The paired {@link dev.drav.glyphworks.item.system.ItemSinkSystem} clears the
 * block's
 * {@link com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock}
 * every tick, effectively creating an infinite drain with no back-pressure.
 */
public final class ItemSinkComponent implements Component<ChunkStore> {

    public static final BuilderCodec<ItemSinkComponent> CODEC = BuilderCodec
            .builder(ItemSinkComponent.class, () -> new ItemSinkComponent())
            .build();

    public static ComponentType<ChunkStore, ItemSinkComponent> getComponentType() {
        return GlyphworksPlugin.get().getItemSinkComponentType();
    }

    public ItemSinkComponent() {
    }

    public ItemSinkComponent(@Nonnull ItemSinkComponent other) {
    }

    @Override
    public ItemSinkComponent clone() {
        return new ItemSinkComponent(this);
    }
}
