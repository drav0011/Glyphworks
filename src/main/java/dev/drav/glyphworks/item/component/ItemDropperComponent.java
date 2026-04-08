package dev.drav.glyphworks.item.component;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Marker component that makes a block behave as an <em>item dropper</em>
 * (open crate).
 *
 * <p>
 * Every tick the system spawns item entities directly below the block for
 * every stack stored in the block's
 * {@link com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock},
 * then clears those slots. The block's INPUT grid face allows the network to
 * fill it continuously.
 *
 * <p>
 * No persistent fields; the codec is intentionally empty.
 */
public final class ItemDropperComponent implements Component<ChunkStore> {

    public static final BuilderCodec<ItemDropperComponent> CODEC = BuilderCodec
            .builder(ItemDropperComponent.class, ItemDropperComponent::new)
            .build();

    public static ComponentType<ChunkStore, ItemDropperComponent> getComponentType() {
        return GlyphworksPlugin.get().getItemDropperComponentType();
    }

    public ItemDropperComponent() {
    }

    @Override
    public ItemDropperComponent clone() {
        return new ItemDropperComponent();
    }
}
