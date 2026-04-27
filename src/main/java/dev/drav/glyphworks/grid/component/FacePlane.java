package dev.drav.glyphworks.grid.component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3iUtil;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.protocol.BlockFace;

/**
 * Defines a face region of a block in block-relative coordinates.
 */
public class FacePlane {
    public static final BuilderCodec<FacePlane> CODEC = BuilderCodec
            .builder(FacePlane.class, FacePlane::new)
            .append(
                    new KeyedCodec<>("Glyphworks_FacePlane_Position", Vector3iUtil.CODEC),
                    (c, v) -> c.position = v,
                    c -> c.position)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FacePlane_Normal", new EnumCodec<>(BlockFace.class)),
                    (c, v) -> c.normal = v,
                    c -> c.normal)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FacePlane_Mode", new EnumCodec<>(FilterType.class)),
                    (c, v) -> c.mode = v,
                    c -> c.mode)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FacePlane_ContainerKey", Codec.STRING),
                    (c, v) -> c.containerKey = v,
                    c -> c.containerKey)
            .add()
            .build();

    /**
     * Block-origin relative position
     */
    private Vector3i position;

    /**
     * Normal vector for this face, relative to block
     */
    private BlockFace normal;

    /**
     * Connection mode for this face
     */
    private FilterType mode;

    /**
     * Optional key identifying which container on this block this face connects to.
     * Null only for blocks with no container (e.g. pipes). Both single-container
     * and multi-container blocks should set this (e.g. "inventory", "input",
     * "output", "fuel").
     */
    @Nullable
    private String containerKey;

    /**
     * Runtime-only fractional carry-over for sub-tick transfer rates.
     * Each face accumulates independently so that input and output faces on the
     * same block (e.g. a tank acting as a valve) cannot deplete each other's budget.
     */
    private transient float transferAccumulator = 0.0f;

    /**
     * No-arg constructor required by {@link #CODEC}.
     */
    public FacePlane() {
        this(new Vector3i(0, 0, 0), BlockFace.None, FilterType.ALLOW_ALL, null);
    }

    /**
     * @param position Block-relative position
     * @param normal   Normal vector for this face
     * @param mode     Initial connection mode
     */
    public FacePlane(Vector3i position, BlockFace normal, FilterType mode) {
        this(position, normal, mode, null);
    }

    /**
     * @param position     Block-relative position
     * @param normal       Normal vector for this face
     * @param mode         Initial connection mode
     * @param containerKey Optional container key for multi-container machines
     */
    public FacePlane(Vector3i position, BlockFace normal, FilterType mode, @Nullable String containerKey) {
        this.position = new Vector3i(position);
        this.normal = normal;
        this.mode = mode;
        this.containerKey = containerKey;
    }

    public FacePlane(@Nonnull FacePlane other) {
        this.position = new Vector3i(other.position);
        this.normal = other.normal;
        this.mode = other.mode;
        this.containerKey = other.containerKey;
    }

    public Vector3i getPosition() {
        return position;
    }

    public BlockFace getNormal() {
        return normal;
    }

    public FilterType getMode() {
        return mode;
    }

    public void setMode(FilterType mode) {
        this.mode = mode;
    }

    @Nullable
    public String getContainerKey() {
        return containerKey;
    }

    public void setContainerKey(@Nullable String containerKey) {
        this.containerKey = containerKey;
    }

    @Override
    public String toString() {
        return String.format("FacePlane{position=%s, normal=%s, mode=%s, containerKey=%s}", position, normal, mode,
                containerKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof FacePlane))
            return false;
        FacePlane other = (FacePlane) o;
        return position.equals(other.position) && normal == other.normal && mode == other.mode;
    }

    @Override
    public int hashCode() {
        int result = position.hashCode();
        result = 31 * result + normal.hashCode();
        result = 31 * result + mode.hashCode();
        return result;
    }

    /**
     * Adds {@code amount} to this face's accumulator and returns the number of
     * whole units that have accumulated. The fractional remainder is retained
     * for the next tick.
     */
    public int drainAccumulator(float amount) {
        transferAccumulator += amount;
        int whole = (int) transferAccumulator;
        transferAccumulator -= whole;
        return whole;
    }

    public FacePlane clone() {
        return new FacePlane(this);
    }
}
