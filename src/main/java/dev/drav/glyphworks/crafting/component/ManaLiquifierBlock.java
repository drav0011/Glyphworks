package dev.drav.glyphworks.crafting.component;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Block state component for the Mana Liquifier processing machine.
 *
 * <p>
 * Accepts any vanilla essence item in the input slot and heat-producing fuel
 * in the fuel slot. When fuel energy is available, processing advances each
 * tick. On cycle completion, one essence is consumed and
 * {@link #MANA_OUTPUT_PER_ESSENCE} liters of {@link #MANA_FLUID_ID} are added
 * to the block's {@link dev.drav.glyphworks.fluid.component.FluidContainerComponent}.
 */
public class ManaLiquifierBlock implements Component<ChunkStore> {

    /** Mana produced per essence consumed, in liters. */
    public static final int MANA_OUTPUT_PER_ESSENCE = 200;

    /** Time in seconds to process one essence. */
    public static final float RECIPE_TIME = 3.0f;

    /** Fluid ID output by this machine. */
    public static final String MANA_FLUID_ID = "Glyphworks_Fluid_Mana";

    /**
     * Vanilla essence item IDs accepted by this machine.
     * Any item in the input slot that is not in this set will not be processed.
     */
    public static final Set<String> ESSENCE_IDS = Set.of(
            "Ingredient_Fire_Essence",
            "Ingredient_Ice_Essence",
            "Ingredient_Life_Essence",
            "Ingredient_Lightning_Essence",
            "Ingredient_Void_Essence",
            "Ingredient_Water_Essence");

    // ── CODEC ─────────────────────────────────────────────────────────────────

    @Nonnull
    public static final BuilderCodec<ManaLiquifierBlock> CODEC = BuilderCodec
            .builder(ManaLiquifierBlock.class, () -> new ManaLiquifierBlock())
            .append(
                    new KeyedCodec<>("Glyphworks_ManaLiquifierBlock_InputContainer", ItemContainer.CODEC),
                    (b, v) -> b.inputContainer = v,
                    b -> b.inputContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_ManaLiquifierBlock_FuelContainer", ItemContainer.CODEC),
                    (b, v) -> b.fuelContainer = v,
                    b -> b.fuelContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_ManaLiquifierBlock_Progress", Codec.DOUBLE),
                    (b, v) -> b.processingProgress = v.floatValue(),
                    b -> Double.valueOf(b.processingProgress))
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_ManaLiquifierBlock_FuelEnergy", Codec.DOUBLE),
                    (b, v) -> b.remainingFuelEnergy = v.floatValue(),
                    b -> Double.valueOf(b.remainingFuelEnergy))
            .add()
            .build();

    // ── Persisted fields ───────────────────────────────────────────────────────

    /** Single-slot input container for essence items. */
    @Nullable
    private ItemContainer inputContainer;

    /** Single-slot fuel container for heat-producing items. */
    @Nullable
    private ItemContainer fuelContainer;

    /** Processing progress toward the current cycle, in seconds elapsed. */
    private float processingProgress = 0.0f;

    /**
     * Remaining fuel energy in seconds. Derived from the item's
     * {@code FuelQuality} on consumption; decremented each tick.
     */
    private float remainingFuelEnergy = 0.0f;

    public ManaLiquifierBlock() {
    }

    public ManaLiquifierBlock(@Nonnull ManaLiquifierBlock other) {
        this.processingProgress = other.processingProgress;
        this.remainingFuelEnergy = other.remainingFuelEnergy;
    }

    public static ComponentType<ChunkStore, ManaLiquifierBlock> getComponentType() {
        return GlyphworksPlugin.get().getManaLiquifierBlockComponentType();
    }

    // ── Container setup ────────────────────────────────────────────────────────

    /**
     * Creates or restores the input and output containers.
     * Called by {@link dev.drav.glyphworks.crafting.system.ManaLiquifierSetupSystem}
     * on entity added.
     *
     * @param blockStateInfo the block state info for change-event registration
     * @param ejected        receives any items that do not fit after resize
     */
    public void setupContainers(
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            @Nonnull List<ItemStack> ejected) {
        inputContainer = ItemContainer.ensureContainerCapacity(
                inputContainer, (short) 1, SimpleItemContainer::getNewContainer, ejected);
        inputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());

        fuelContainer = ItemContainer.ensureContainerCapacity(
                fuelContainer, (short) 1, SimpleItemContainer::getNewContainer, ejected);
        fuelContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    @Nullable
    public ItemContainer getInputContainer() {
        return inputContainer;
    }

    @Nullable
    public ItemContainer getFuelContainer() {
        return fuelContainer;
    }

    public float getProcessingProgress() {
        return processingProgress;
    }

    public void setProcessingProgress(float v) {
        processingProgress = v;
    }

    public float getRemainingFuelEnergy() {
        return remainingFuelEnergy;
    }

    public void setRemainingFuelEnergy(float v) {
        remainingFuelEnergy = v;
    }

    @Override
    public ManaLiquifierBlock clone() {
        return new ManaLiquifierBlock(this);
    }
}
