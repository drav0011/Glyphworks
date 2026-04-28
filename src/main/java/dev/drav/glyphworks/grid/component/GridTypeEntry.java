package dev.drav.glyphworks.grid.component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;


import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;

/**
 * Holds all grid-network data for a single {@link GridType} within a
 * {@link GridComponent}.
 *
 * <p>
 * A block may participate in multiple independent grid types by carrying
 * several {@code GridTypeEntry} instances inside its {@link GridComponent}.
 * Each entry owns its own face configuration, neighbor set, and transfer rate.
 */
public class GridTypeEntry {

    public static final BuilderCodec<GridTypeEntry> CODEC = BuilderCodec
            .builder(GridTypeEntry.class, GridTypeEntry::new)
            .append(
                    new KeyedCodec<>("Glyphworks_GridTypeEntry_Type", Codec.STRING),
                    (c, v) -> c.gridType = GridTypeRegistry.get(v),
                    c -> c.gridType != null ? c.gridType.id() : null)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_GridTypeEntry_Faces", new SetCodec<>(FacePlane.CODEC, HashSet::new, false)),
                    (c, v) -> c.faces = v,
                    c -> c.faces)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_GridTypeEntry_Neighbors",
                            new SetCodec<>(Vector3i.CODEC, HashSet::new, false)),
                    (c, v) -> c.neighbors = v,
                    c -> c.neighbors)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_GridTypeEntry_TransferRate", Codec.FLOAT),
                    (c, v) -> c.transferRate = v,
                    c -> c.transferRate)
            .add()
            .build();

    /**
     * The type of grid network this entry belongs to.
     */
    @Nullable
    private GridType gridType;

    /**
     * Configured face regions for this grid type on this block.
     */
    private Set<FacePlane> faces;

    /**
     * World-relative positions of all neighboring blocks linked via a face
     * connection for this grid type.
     */
    private Set<Vector3i> neighbors;

    /**
     * Maximum throughput per tick for this grid type on this block.
     */
    private float transferRate = 1.0f;

    /**
     * No-arg constructor required by {@link #CODEC}.
     */
    public GridTypeEntry() {
        this.gridType = null;
        this.faces = new HashSet<>();
        this.neighbors = new HashSet<>();
        this.transferRate = 1.0f;
    }

    /**
     * Copy constructor — deep-copies the face set so entries don't share mutable
     * face state.
     */
    public GridTypeEntry(@Nonnull GridTypeEntry other) {
        this.gridType = other.gridType;
        this.faces = new HashSet<>(other.faces.size());
        for (FacePlane f : other.faces) {
            this.faces.add(f.clone());
        }
        this.neighbors = new HashSet<>(other.neighbors);
        this.transferRate = other.transferRate;
    }

    @Nullable
    public GridType getGridType() {
        return gridType;
    }

    public void setGridType(@Nullable GridType gridType) {
        this.gridType = gridType;
    }

    @Nonnull
    public Set<FacePlane> getFaces() {
        return Collections.unmodifiableSet(faces);
    }

    public void setFaces(@Nonnull Set<FacePlane> faces) {
        this.faces = new HashSet<>(faces);
    }

    public void addFace(@Nonnull FacePlane face) {
        faces.add(face);
    }

    public void removeFace(@Nonnull FacePlane face) {
        faces.remove(face);
    }

    @Nonnull
    public Set<Vector3i> getNeighbors() {
        return Collections.unmodifiableSet(neighbors);
    }

    public void setNeighbors(@Nonnull Set<Vector3i> neighbors) {
        this.neighbors = new HashSet<>(neighbors);
    }

    public void addNeighbor(@Nonnull Vector3i neighbor) {
        neighbors.add(neighbor);
    }

    public void removeNeighbor(@Nonnull Vector3i neighbor) {
        neighbors.remove(neighbor);
    }

    public float getTransferRate() {
        return transferRate;
    }

    public void setTransferRate(float transferRate) {
        this.transferRate = transferRate;
    }

    @Override
    public GridTypeEntry clone() {
        return new GridTypeEntry(this);
    }
}
