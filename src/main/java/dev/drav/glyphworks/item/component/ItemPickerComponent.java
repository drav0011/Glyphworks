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
 * Marks a block as an <em>item picker</em>.
 *
 * <p>
 * Each tick the system scans a configurable cube of world-space around the
 * block for dropped item entities and pulls them into the block's
 * {@link com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock}.
 * Entities that cannot fit (container full) are left on the ground.
 *
 * <p>
 * The block's OUTPUT grid face pushes collected items into the network.
 */
public final class ItemPickerComponent implements Component<ChunkStore> {

    public static final BuilderCodec<ItemPickerComponent> CODEC = BuilderCodec
            .builder(ItemPickerComponent.class, () -> new ItemPickerComponent())
            .append(
                    new KeyedCodec<>("Glyphworks_ItemPickerComponent_Radius", Codec.INTEGER),
                    (c, v) -> c.radius = v,
                    c -> c.radius)
            .add()
            .build();

    public static ComponentType<ChunkStore, ItemPickerComponent> getComponentType() {
        return GlyphworksPlugin.get().getItemModule().getItemPickerComponentType();
    }

    /** Half-size of the pick-up cube in blocks. Default: 3 (7×7×7 area). */
    private int radius = 3;

    public ItemPickerComponent() {
    }

    public ItemPickerComponent(@Nonnull ItemPickerComponent other) {
        this.radius = other.radius;
    }

    public int getRadius() {
        return radius;
    }

    @Override
    public ItemPickerComponent clone() {
        return new ItemPickerComponent(this);
    }
}
