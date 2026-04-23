package dev.drav.glyphworks.crafting.event;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;

public final class ResourceTypeRegistry {

    private static final Logger LOGGER = Logger.getLogger(ResourceTypeRegistry.class.getName());

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_ITEM_IDS = "ItemIds";
    private static final String TAG_RESOURCE_TYPE_ITEMS = "ResourceTypeItems";

    private static final List<String> REVERSE_ITEM_TAG_KEYS = List.of(
            TAG_ITEMS,
            TAG_ITEM_IDS,
            TAG_RESOURCE_TYPE_ITEMS);

    private static final Field ITEM_RESOURCE_TYPES_FIELD = resolveItemResourceTypesField();
    private static final Field RESOURCE_TYPE_DATA_FIELD = resolveResourceTypeDataField();

    private static final Map<String, Item> KNOWN_ITEMS = new HashMap<>();
    private static final Map<String, ResourceType> KNOWN_RESOURCE_TYPES = new HashMap<>();

    private ResourceTypeRegistry() {
    }

    private static Field resolveItemResourceTypesField() {
        try {
            Field field = Item.class.getDeclaredField("resourceTypes");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Item.resourceTypes field not found - update this class if the field was renamed", e);
        }
    }

    private static Field resolveResourceTypeDataField() {
        try {
            Field field = ResourceType.class.getDeclaredField("data");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("ResourceType.data field not found - update this class if the field was renamed", e);
        }
    }

    public static void onItemsLoaded(@Nonnull LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        KNOWN_ITEMS.putAll(event.getLoadedAssets());
        applyReverseResourceTypeMappings();
    }

    public static void onResourceTypesLoaded(
            @Nonnull LoadedAssetsEvent<String, ResourceType, DefaultAssetMap<String, ResourceType>> event) {
        KNOWN_RESOURCE_TYPES.putAll(event.getLoadedAssets());
        applyReverseResourceTypeMappings();
    }

    private static void applyReverseResourceTypeMappings() {
        if (KNOWN_ITEMS.isEmpty() || KNOWN_RESOURCE_TYPES.isEmpty()) {
            return;
        }

        Map<String, Set<ItemResourceType>> linksByItemId = collectReverseLinksByItemId();
        for (Map.Entry<String, Set<ItemResourceType>> entry : linksByItemId.entrySet()) {
            String itemId = entry.getKey();
            Item item = KNOWN_ITEMS.get(itemId);
            if (item == null) {
                LOGGER.warning("ResourceType reverse mapping references missing item: " + itemId);
                continue;
            }

            for (ItemResourceType resourceType : entry.getValue()) {
                injectResourceType(item, itemId, resourceType);
            }
        }
    }

    private static Map<String, Set<ItemResourceType>> collectReverseLinksByItemId() {
        Map<String, Set<ItemResourceType>> linksByItemId = new HashMap<>();

        for (Map.Entry<String, ResourceType> resourceTypeEntry : KNOWN_RESOURCE_TYPES.entrySet()) {
            String resourceTypeId = resourceTypeEntry.getKey();
            ResourceType resourceType = resourceTypeEntry.getValue();
            String[] itemRefs = extractItemReferences(resourceType);

            for (String itemRef : itemRefs) {
                ParsedItemReference parsed = parseItemReference(itemRef);
                if (parsed == null) {
                    LOGGER.warning("Invalid ResourceType reverse entry '" + itemRef + "' for " + resourceTypeId);
                    continue;
                }

                linksByItemId.computeIfAbsent(parsed.itemId, ignored -> new HashSet<>())
                        .add(new ItemResourceType(resourceTypeId, parsed.quantity));
            }
        }

        return linksByItemId;
    }

    private static String[] extractItemReferences(@Nonnull ResourceType resourceType) {
        Map<String, String[]> rawTags = getResourceTypeRawTags(resourceType);
        for (String tagKey : REVERSE_ITEM_TAG_KEYS) {
            String[] values = rawTags.get(tagKey);
            if (values != null && values.length > 0) {
                return values;
            }
        }
        return new String[0];
    }

    @Nonnull
    private static Map<String, String[]> getResourceTypeRawTags(@Nonnull ResourceType resourceType) {
        try {
            AssetExtraInfo.Data data = (AssetExtraInfo.Data) RESOURCE_TYPE_DATA_FIELD.get(resourceType);
            if (data == null) {
                return Map.of();
            }
            return data.getRawTags();
        } catch (IllegalAccessException e) {
            LOGGER.severe("Failed to read ResourceType tags for reverse mapping: " + e.getMessage());
            return Map.of();
        }
    }

    @Nullable
    private static ParsedItemReference parseItemReference(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        int separator = Math.max(trimmed.lastIndexOf(':'), trimmed.lastIndexOf('='));
        if (separator <= 0 || separator >= trimmed.length() - 1) {
            return new ParsedItemReference(trimmed, 1);
        }

        String itemId = trimmed.substring(0, separator).trim();
        String quantityPart = trimmed.substring(separator + 1).trim();
        if (itemId.isEmpty() || quantityPart.isEmpty()) {
            return null;
        }

        try {
            int quantity = Integer.parseInt(quantityPart);
            if (quantity <= 0) {
                return null;
            }
            return new ParsedItemReference(itemId, quantity);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void injectResourceType(
            @Nonnull Item item,
            @Nonnull String itemId,
            @Nonnull ItemResourceType entry) {
        try {
            ItemResourceType[] existing = (ItemResourceType[]) ITEM_RESOURCE_TYPES_FIELD.get(item);
            if (alreadyRegistered(existing, entry.id)) {
                return;
            }
            ItemResourceType[] updated = append(existing, entry);
            ITEM_RESOURCE_TYPES_FIELD.set(item, updated);
        } catch (IllegalAccessException e) {
            LOGGER.severe("Failed to inject resource type into " + itemId + ": " + e.getMessage());
        }
    }

    private static boolean alreadyRegistered(@Nullable ItemResourceType[] existing, @Nonnull String resourceTypeId) {
        if (existing == null || existing.length == 0) {
            return false;
        }
        for (ItemResourceType rt : existing) {
            if (resourceTypeId.equals(rt.id)) {
                return true;
            }
        }
        return false;
    }

    private static ItemResourceType[] append(@Nullable ItemResourceType[] existing, @Nonnull ItemResourceType entry) {
        if (existing == null || existing.length == 0) {
            return new ItemResourceType[] { entry };
        }
        ItemResourceType[] updated = Arrays.copyOf(existing, existing.length + 1);
        updated[existing.length] = entry;
        return updated;
    }

    private static final class ParsedItemReference {
        private final String itemId;
        private final int quantity;

        private ParsedItemReference(String itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }
}
