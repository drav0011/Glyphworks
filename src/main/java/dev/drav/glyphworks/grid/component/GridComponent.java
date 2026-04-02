package dev.drav.glyphworks.grid.component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Turns any block into a node in the Glyphworks grid.
 *
 * <p>
 * A block may participate in multiple independent grid types simultaneously.
 * Each {@link GridTypeEntry} within {@link #entries} holds the type identifier,
 * face configurations, neighbor set, and transfer rate for one grid type.
 */
public class GridComponent implements Component<ChunkStore> {

    public static final BuilderCodec<GridComponent> CODEC = BuilderCodec
            .builder(GridComponent.class, GridComponent::new)
            .append(
                    new KeyedCodec<>("Glyphworks_GridComponent_Entries",
                            new SetCodec<>(GridTypeEntry.CODEC, HashSet::new, false)),
                    (c, v) -> c.entries = v,
                    c -> c.entries)
            .add()
            .build();

    public static ComponentType<ChunkStore, GridComponent> getComponentType() {
        return GlyphworksPlugin.get().getGridComponentType();
    }

    /**
     * One entry per grid type this block participates in.
     */
    private Set<GridTypeEntry> entries;

    /**
     * Runtime-only world origin position. Set at block load/place time by event
     * handlers; not serialised.
     * Used by handlers to locate the external inventory adjacent to extractor /
     * inserter faces.
     */
    @Nullable
    private transient Vector3i originPosition;

    /**
     * No-arg constructor required by {@link #CODEC}.
     */
    public GridComponent() {
        this.entries = new HashSet<>();
    }

    /**
     * Copy constructor used by {@link #clone()}.
     * Each {@link GridTypeEntry} is deep-copied via its own copy constructor.
     */
    public GridComponent(@Nonnull GridComponent other) {
        this.entries = new HashSet<>(other.entries.size());
        for (GridTypeEntry e : other.entries) {
            this.entries.add(new GridTypeEntry(e));
        }
        // originPosition intentionally not copied — fresh placement starts without it.
    }

    /**
     * Returns an unmodifiable view of all grid type entries on this block.
     */
    @Nonnull
    public Set<GridTypeEntry> getEntries() {
        return Collections.unmodifiableSet(entries);
    }

    /**
     * Returns the entry for the given grid type ID, or {@code null} if this block
     * does not participate in that type.
     */
    @Nullable
    public GridTypeEntry getEntry(@Nonnull String typeId) {
        for (GridTypeEntry entry : entries) {
            if (entry.getGridType() != null && entry.getGridType().id().equals(typeId)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Adds a grid type entry. If an entry with the same type ID already exists it
     * is replaced.
     */
    public void addEntry(@Nonnull GridTypeEntry entry) {
        if (entry.getGridType() != null) {
            entries.removeIf(
                    e -> e.getGridType() != null && e.getGridType().id().equals(entry.getGridType().id()));
        }
        entries.add(entry);
    }

    /**
     * Removes the entry for the given grid type ID. No-op if not present.
     */
    public void removeEntry(@Nonnull String typeId) {
        entries.removeIf(e -> e.getGridType() != null && e.getGridType().id().equals(typeId));
    }

    @Nullable
    public Vector3i getOriginPosition() {
        return originPosition;
    }

    public void setOriginPosition(@Nullable Vector3i originPosition) {
        this.originPosition = originPosition;
    }

    /**
     * Placement copy: generates a fresh node and strips all neighbour linkage
     * (entries are deep-copied via {@link GridTypeEntry#GridTypeEntry(GridTypeEntry)}).
     */
    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new GridComponent(this);
    }
}

