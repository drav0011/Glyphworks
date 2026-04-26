package dev.drav.glyphworks.crafting.component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;

public final class FluidSlot {

    @Nonnull
    public static final BuilderCodec<FluidSlot> CODEC = BuilderCodec
            .builder(FluidSlot.class, FluidSlot::new)
            .append(
                    new KeyedCodec<>("Glyphworks_FluidSlot_ResourceTypeId", Codec.STRING),
                    (s, v) -> s.resourceTypeId = v,
                    s -> s.resourceTypeId)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidSlot_FilterValidIngredients", Codec.BOOLEAN),
                    (s, v) -> s.filterValidIngredients = v,
                    s -> s.filterValidIngredients)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidSlot_Icon", Codec.STRING),
                    (s, v) -> s.icon = v,
                    s -> s.icon)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidSlot_CapacityMbPerSlot", Codec.INTEGER),
                    (s, v) -> s.capacityMbPerSlot = v,
                    s -> s.capacityMbPerSlot)
            .addValidator(Validators.greaterThan(0))
            .add()
            .build();

    @Nullable
    private String resourceTypeId;

    private boolean filterValidIngredients;

    @Nullable
    private String icon;

    private int capacityMbPerSlot = 4000;

    public FluidSlot() {
    }

    @Nullable
    public String getResourceTypeId() {
        return resourceTypeId;
    }

    public int getCapacityMbPerSlot() {
        return capacityMbPerSlot;
    }
}
