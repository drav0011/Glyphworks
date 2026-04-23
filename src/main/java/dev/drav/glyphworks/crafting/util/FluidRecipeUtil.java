package dev.drav.glyphworks.crafting.util;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.BsonValue;

import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import dev.drav.glyphworks.fluid.FluidItemRegistry;

public final class FluidRecipeUtil {

    private FluidRecipeUtil() {}

    public static boolean isFluidIngredient(@Nonnull MaterialQuantity mat) {
        return mat.getItemId() != null && FluidItemRegistry.resolveFluidId(mat.getItemId()) != null;
    }

    @Nullable
    public static String fluidId(@Nonnull MaterialQuantity mat) {
        return mat.getItemId() != null ? FluidItemRegistry.resolveFluidId(mat.getItemId()) : null;
    }

    public static int fluidMb(@Nonnull MaterialQuantity mat) {
        BsonDocument meta = mat.getMetadata();
        if (meta != null) {
            BsonValue v = meta.get("FluidMb");
            if (v != null && v.isNumber()) {
                int override = v.asNumber().intValue();
                if (override > 0) return override;
            }
        }
        return Math.max(1, mat.getQuantity()) * 1000;
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