package dev.drav.glyphworks.grid.type;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps string IDs to registered {@link GridType} instances.
 *
 * <p>
 * Register grid network types at plugin init:
 * 
 * <pre>
 * GridTypeRegistry.register(GridType.of("Item"));
 * </pre>
 *
 * <p>
 * Unknown IDs encountered during deserialization are logged and return
 * {@code null}.
 */
public final class GridTypeRegistry {

    private static final Logger LOGGER = Logger.getLogger(GridTypeRegistry.class.getName());

    private static final Map<String, GridType> REGISTRY = new HashMap<>();

    private GridTypeRegistry() {
    }

    /**
     * Registers a grid type, keyed by {@link GridType#id()}.
     * Re-registering the same ID overwrites the previous entry.
     */
    public static void register(@Nonnull GridType type) {
        REGISTRY.put(type.id(), type);
    }

    /**
     * Returns the {@link GridType} registered under {@code id}, or {@code null} if
     * unknown.
     */
    @Nullable
    public static GridType get(@Nonnull String id) {
        GridType type = REGISTRY.get(id);

        if (type == null) {
            LOGGER.warning("[GridTypeRegistry] Unknown grid type: \"" + id + "\"");
        }

        return type;
    }

    /** Returns an unmodifiable view of all registered grid types. */
    @Nonnull
    public static Collection<GridType> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
