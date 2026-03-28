package dev.drav.glyphworks.grid.component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3iUtil;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;

/**
 * Turns any block into a node in the Glyphworks grid.
 */
public class GridComponent implements Component<ChunkStore> {
    public static final BuilderCodec<GridComponent> CODEC = BuilderCodec
            .builder(GridComponent.class, GridComponent::new)
            .append(
                    new KeyedCodec<>("GridComponent_Type", Codec.STRING),
                    (c, v) -> c.gridType = GridTypeRegistry.get(v),
                    c -> c.gridType != null ? c.gridType.id() : null)
            .add()
            .append(
                    new KeyedCodec<>("GridComponent_Faces", new SetCodec<>(FacePlane.CODEC, HashSet::new, false)),
                    (c, v) -> c.faces = v,
                    c -> c.faces)
            .add()
            .append(
                    new KeyedCodec<>("GridComponent_Neighbors",
                            new SetCodec<>(Vector3iUtil.CODEC, HashSet::new, false)),
                    (c, v) -> c.neighbors = v,
                    c -> c.neighbors)
            .add()
            .append(
                    new KeyedCodec<>("GridComponent_TransferRate", Codec.FLOAT),
                    (c, v) -> c.transferRate = v,
                    c -> c.transferRate)
            .add()
            .build();

    public static ComponentType<ChunkStore, GridComponent> getComponentType() {
        return GlyphworksPlugin.get().getGridComponentType();
    }

    /**
     * The type of grid network this block belongs to.
     * Determines which grid this node is added to and what kind of data flows
     * through it.
     */
    private GridType gridType;

    /**
     * Configured face regions for this block.
     */
    private Set<FacePlane> faces;

    /**
     * World-relative positions of all neighboring blocks linked to this block via a
     * face connection.
     */
    private Set<Vector3i> neighbors;

    /**
     * Maximum throughput of this node per tick, expressed in the native unit of the
     * grid type (items, energy units, millibuckets, etc.).
     * Values ≥ 1 transfer that many items per tick; values < 1 transfer one item
     * every {@code 1/rate} ticks (e.g. {@code 0.2} = 1 item every 5 ticks).
     * On a grid path, the effective rate is min(rate of all nodes on the path).
     */
    private float transferRate = 1.0f;

    /**
     * Runtime-only fractional carry-over for sub-tick transfer rates.
     * Accumulates {@code effectiveRate} each tick; an integer batch is transferred
     * and consumed whenever the accumulator reaches 1.0.
     */
    private transient float transferAccumulator = 0.0f;

    /**
     * Runtime-only world origin position. Set at block load/place time by event
     * handlers; not serialised, not present in the codec.
     * Used by {@link dev.drav.glyphworks.transfer.item.ItemGridTypeHandler} to
     * locate the external inventory adjacent to extractor / inserter faces.
     */
    @Nullable
    private transient Vector3i originPosition;

    /**
     * No-arg constructor required by {@link #CODEC}.
     */
    public GridComponent() {
        this.gridType = null;
        this.faces = new HashSet<>();
        this.neighbors = new HashSet<>();
        this.transferRate = 1.0f;
    }

    /**
     * Copy constructor used by {@link #clone()}.
     * Each {@link FacePlane} is deep-copied so blocks don't share mutable face
     * state.
     */
    public GridComponent(GridComponent other) {
        this.gridType = other.gridType;
        this.faces = new HashSet<>(other.faces.size());
        for (FacePlane f : other.faces) {
            this.faces.add(f.clone());
        }
        this.neighbors = new HashSet<>(other.neighbors);
        this.transferRate = other.transferRate;
        // transferAccumulator intentionally not copied — fresh placement starts at
        // zero.
    }

    public GridType getGridType() {
        return gridType;
    }

    public void setGridType(GridType gridType) {
        this.gridType = gridType;
    }

    public Set<FacePlane> getFaces() {
        return Collections.unmodifiableSet(faces);
    }

    public void setFaces(Set<FacePlane> faces) {
        this.faces = new HashSet<>(faces);
    }

    public void addFace(FacePlane face) {
        faces.add(face);
    }

    public void removeFace(FacePlane face) {
        faces.remove(face);
    }

    public Set<Vector3i> getNeighbors() {
        return Collections.unmodifiableSet(neighbors);
    }

    public void setNeighbors(Set<Vector3i> neighbors) {
        this.neighbors = new HashSet<>(neighbors);
    }

    public void addNeighbor(Vector3i neighbor) {
        neighbors.add(neighbor);
    }

    public void removeNeighbor(Vector3i neighbor) {
        neighbors.remove(neighbor);
    }

    public float getTransferRate() {
        return transferRate;
    }

    public void setTransferRate(float transferRate) {
        this.transferRate = transferRate;
    }

    @Nullable
    public Vector3i getOriginPosition() {
        return originPosition;
    }

    public void setOriginPosition(@Nullable Vector3i originPosition) {
        this.originPosition = originPosition;
    }

    /**
     * Adds {@code amount} to the accumulator and returns the number of whole units
     * that have accumulated (floored). The fractional remainder is kept for the
     * next tick.
     *
     * @param amount the per-tick contribution (typically
     *               {@code effectiveRate * dt * TPS})
     * @return how many whole items (or other units) to transfer this tick; 0 if
     *         the threshold has not been reached yet
     */
    public int drainAccumulator(float amount) {
        transferAccumulator += amount;
        int whole = (int) transferAccumulator;
        transferAccumulator -= whole;
        return whole;
    }

    /**
     * Placement copy: generates a fresh node UUID and strips all neighbour linkage.
     * Used by the engine when a player places a block from a prototype.
     */
    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new GridComponent(this);
    }
}
