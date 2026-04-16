package dev.drav.glyphworks.fluid;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.PlaceFluidInteraction;

public final class FluidItemRegistry {

    private static final Map<String, String> fluidIdToItemId = new HashMap<>();

    private FluidItemRegistry() {
    }

    static void onItemsLoaded(@Nonnull LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        for (Map.Entry<String, Item> entry : event.getLoadedAssets().entrySet()) {
            String fluidKey = extractFluidKey(entry.getValue());
            if (fluidKey != null) {
                fluidIdToItemId.put(fluidKey, entry.getKey());
            }
        }
    }

    @Nullable
    private static String extractFluidKey(@Nonnull Item item) {
        Map<InteractionType, String> interactions = item.getInteractions();
        if (interactions == null)
            return null;

        String rootId = interactions.get(InteractionType.Secondary);
        if (rootId == null)
            return null;

        RootInteraction root = (RootInteraction) RootInteraction.getAssetMap().getAsset(rootId);
        if (root == null)
            return null;

        for (String interactionId : root.getInteractionIds()) {
            Interaction interaction = (Interaction) Interaction.getAssetMap().getAsset(interactionId);
            if (!(interaction instanceof PlaceFluidInteraction))
                continue;
            return ((PlaceFluidInteraction) interaction).getFluidKey();
        }
        return null;
    }

    @Nullable
    public static String resolveItemId(@Nonnull String fluidId) {
        return fluidIdToItemId.get(fluidId);
    }
}
