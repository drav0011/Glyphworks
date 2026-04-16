package dev.drav.glyphworks.grid.type;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps {@link GridType} IDs to their {@link GridTypeHandler} implementations.
 *
 * <p>
 * Register handlers at plugin init alongside their matching
 * {@link GridType}:
 *
 * <pre>
 * GridTypeRegistry.register(GridType.of("Item"));
 * GridTypeHandlerRegistry.register(new ItemGridTypeHandler());
 * </pre>
 *
 * <p>
 * Unknown IDs are logged and return {@code null}.
 */
public final class GridTypeHandlerRegistry {

    private static final Logger LOGGER = Logger.getLogger(GridTypeHandlerRegistry.class.getName());

    private static final Map<String, GridTypeHandler> REGISTRY = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private GridTypeHandlerRegistry() {
    }

    /**
     * Registers a handler, keyed by {@link GridTypeHandler#typeId()}.
     * Re-registering the same ID overwrites the previous entry.
     */
    public static void register(@Nonnull GridTypeHandler handler) {
        REGISTRY.put(handler.typeId(), handler);
    }

    /**
     * Returns the {@link GridTypeHandler} registered under {@code id}, or
     * {@code null} if none is registered for that type.
     */
    @Nullable
    public static GridTypeHandler get(@Nonnull String id) {
        GridTypeHandler handler = REGISTRY.get(id);
        if (handler == null && WARNED.add(id)) {
            LOGGER.warning("[GridTypeHandlerRegistry] No handler registered for grid type: \"" + id + "\"");
        }
        return handler;
    }

    /** Returns an unmodifiable view of all registered handlers. */
    @Nonnull
    public static Collection<GridTypeHandler> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
