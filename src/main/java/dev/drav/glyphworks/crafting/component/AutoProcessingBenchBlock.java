package dev.drav.glyphworks.crafting.component;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Glyphworks configuration component for vanilla {@link com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock}
 * blocks that require liquid mana to operate.
 *
 * <p>
 * Carries the per-bench mana drain rate consumed by
 * {@link dev.drav.glyphworks.crafting.system.ProcessingBenchManaSystem} each tick
 * while the bench is actively processing.
 */
public final class AutoProcessingBenchBlock implements Component<ChunkStore> {

    @Nonnull
    public static final BuilderCodec<AutoProcessingBenchBlock> CODEC = BuilderCodec
            .builder(AutoProcessingBenchBlock.class, () -> new AutoProcessingBenchBlock())
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_ManaConsumptionRate", Codec.FLOAT),
                    (b, v) -> b.manaConsumptionRate = v,
                    b -> b.manaConsumptionRate)
            .add()
            .build();

    private float manaConsumptionRate = 1.0f;

    public AutoProcessingBenchBlock() {
    }

    public AutoProcessingBenchBlock(@Nonnull AutoProcessingBenchBlock other) {
        this.manaConsumptionRate = other.manaConsumptionRate;
    }

    public static ComponentType<ChunkStore, AutoProcessingBenchBlock> getComponentType() {
        return GlyphworksPlugin.get().getCraftingModule().getAutoProcessingBenchBlockComponentType();
    }

    public float getManaConsumptionRate() {
        return manaConsumptionRate;
    }

    public void setManaConsumptionRate(float manaConsumptionRate) {
        this.manaConsumptionRate = manaConsumptionRate;
    }

    @Override
    public Component<ChunkStore> clone() {
        return new AutoProcessingBenchBlock(this);
    }
}
