package dev.drav.glyphworks.grid.type;

/**
 * Identifies a type of grid network (e.g. {@code "Item"}, {@code "Fluid"}).
 *
 * <p>
 * Multiple independent grids of different types can exist in the same world,
 * each transferring different kinds of data between their nodes. A block
 * declares
 * which network it participates in via
 * {@link dev.drav.glyphworks.grid.component.GridComponent#getGridType()}.
 *
 * <p>
 * Types are registered at plugin init via
 * {@link GridTypeRegistry#register(GridType)}.
 * Use {@link #of(String)} to create a simple named instance without a dedicated
 * class:
 * 
 * <pre>
 * GridTypeRegistry.register(GridType.of("Item"));
 * </pre>
 */
@FunctionalInterface
public interface GridType {

    /** Stable string identifier used for serialization and registry lookup. */
    String id();

    static GridType of(String id) {
        return () -> id;
    }
}
