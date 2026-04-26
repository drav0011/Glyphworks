package dev.drav.glyphworks.crafting.util;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import dev.drav.glyphworks.fluid.event.FluidItemRegistry;

public final class FluidRecipeUtil {

    private FluidRecipeUtil() {}

    public static boolean isFluidIngredient(@Nonnull MaterialQuantity mat) {
        String resourceTypeId = mat.getResourceTypeId();
        return resourceTypeId != null && FluidItemRegistry.resolveItemId(resourceTypeId) != null;
    }

    @Nullable
    public static String fluidId(@Nonnull MaterialQuantity mat) {
        return isFluidIngredient(mat) ? mat.getResourceTypeId() : null;
    }

    public static int fluidMb(@Nonnull MaterialQuantity mat) {
        return Math.max(1, mat.getQuantity());
    }

    @Nonnull
    public static List<MaterialQuantity> itemParts(@Nonnull List<MaterialQuantity> materials) {
        List<MaterialQuantity> result = new ArrayList<>();
        for (MaterialQuantity mat : materials) {
            if (!isFluidIngredient(mat))
                result.add(mat);
        }
        return result;
    }

    @Nonnull
    public static List<MaterialQuantity> fluidParts(@Nonnull List<MaterialQuantity> materials) {
        List<MaterialQuantity> result = new ArrayList<>();
        for (MaterialQuantity mat : materials) {
            if (isFluidIngredient(mat))
                result.add(mat);
        }
        return result;
    }
}