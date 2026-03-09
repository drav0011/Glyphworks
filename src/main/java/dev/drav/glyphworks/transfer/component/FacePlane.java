package dev.drav.glyphworks.transfer.component;

import java.util.UUID;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Defines a face region of a block in block-relative coordinates.
 *
 * <p>Geometry is stored as offsets from the block origin (0,0,0). World-absolute
 * coordinates are computed on demand via {@link #getWorldMin(Vector3i)} /
 * {@link #getWorldMax(Vector3i)} — never persisted.
 *
 * <p>A face is bound to a specific hitbox detail-box via {@link #hitboxIndex}.
 * When a player clicks that box, the face's mode cycles. {@code -1} = non-interactable.
 *
 * <h3>Block-local coordinate convention (block origin = 0,0,0)</h3>
 * <pre>
 * North (+Z):  relMin=(0,0,1)  relMax=(1,1,1)
 * South (-Z):  relMin=(0,0,0)  relMax=(1,1,0)
 * East  (+X):  relMin=(1,0,0)  relMax=(1,1,1)
 * West  (-X):  relMin=(0,0,0)  relMax=(0,1,1)
 * Up    (+Y):  relMin=(0,1,0)  relMax=(1,1,1)
 * Down  (-Y):  relMin=(0,0,0)  relMax=(1,0,1)
 * </pre>
 */
public class FacePlane {

    public static final BuilderCodec<FacePlane> CODEC = BuilderCodec
            .builder(FacePlane.class, FacePlane::new)
            .append(
                    new KeyedCodec<>("FacePlane_RelMin", Vector3i.CODEC),
                    (c, v) -> c.relMin = v,
                    c -> c.relMin)
            .add()
            .append(
                    new KeyedCodec<>("FacePlane_RelMax", Vector3i.CODEC),
                    (c, v) -> c.relMax = v,
                    c -> c.relMax)
            .add()
            .append(
                    new KeyedCodec<>("FacePlane_HitboxIndex", Codec.INTEGER),
                    (c, v) -> c.hitboxIndex = v,
                    c -> c.hitboxIndex)
            .add()
            .append(
                    new KeyedCodec<>("FacePlane_Mode", new EnumCodec<>(FaceMode.class)),
                    (c, v) -> c.mode = v,
                    c -> c.mode)
            .add()
            .append(
                    new KeyedCodec<>("FacePlane_NeighborNodeId", Codec.STRING),
                    (c, v) -> c.neighborNodeId = (v == null ? null : UUID.fromString(v)),
                    c -> (c.neighborNodeId == null ? null : c.neighborNodeId.toString()))
            .add()
            .build();

    /** Block-origin-relative minimum corner. Persisted. */
    private Vector3i relMin;

    /** Block-origin-relative maximum corner. Persisted. */
    private Vector3i relMax;

    /**
     * Index into the block's hitbox detail-box array that triggers this face.
     * {@code -1} = non-interactable. Persisted.
     */
    private int hitboxIndex;

    /** Connection mode for this face. Persisted. */
    private FaceMode mode;

    /** UUID of the neighboring node. {@code null} = unlinked. Persisted. */
    @Nullable
    private UUID neighborNodeId;

    /** Inventory for incoming items. Transient — wired at runtime. */
    private transient ItemContainer inputInventory;

    /** Inventory for outgoing items. Transient — wired at runtime. */
    private transient ItemContainer outputInventory;

    /** No-arg constructor for CODEC. */
    public FacePlane() {
        this(new Vector3i(0, 0, 0), new Vector3i(0, 0, 0), -1, FaceMode.BIDIRECTIONAL);
    }

    /**
     * @param relMin      Block-relative minimum corner
     * @param relMax      Block-relative maximum corner
     * @param hitboxIndex Detail-box index that triggers this face ({@code -1} = non-interactable)
     * @param mode        Initial connection mode
     */
    public FacePlane(Vector3i relMin, Vector3i relMax, int hitboxIndex, FaceMode mode) {
        int dx = relMax.x - relMin.x;
        int dy = relMax.y - relMin.y;
        int dz = relMax.z - relMin.z;
        if (dx < 0 || dy < 0 || dz < 0 || dx > 1 || dy > 1 || dz > 1) {
            throw new IllegalArgumentException(
                    "FacePlane span must be 0 or 1 on each axis, got relMin=" + relMin + " relMax=" + relMax);
        }
        this.relMin = relMin;
        this.relMax = relMax;
        this.hitboxIndex = hitboxIndex;
        this.mode = mode;
        this.neighborNodeId = null;
    }

    /**
     * Returns the world-space minimum corner of this face, accounting for the block's
     * yaw rotation. The stored relMin/relMax are always in north-facing (authored)
     * coordinates; this method rotates them around the block unit-cell centre (0.5,y,0.5)
     * before adding {@code blockPos}.
     */
    public Vector3i getWorldMin(Vector3i blockPos, Rotation yaw) {
        Vector3i ra = rotateYaw(relMin, yaw);
        Vector3i rb = rotateYaw(relMax, yaw);
        return new Vector3i(
                blockPos.x + Math.min(ra.x, rb.x),
                blockPos.y + Math.min(ra.y, rb.y),
                blockPos.z + Math.min(ra.z, rb.z));
    }

    public Vector3i getWorldMax(Vector3i blockPos, Rotation yaw) {
        Vector3i ra = rotateYaw(relMin, yaw);
        Vector3i rb = rotateYaw(relMax, yaw);
        return new Vector3i(
                blockPos.x + Math.max(ra.x, rb.x),
                blockPos.y + Math.max(ra.y, rb.y),
                blockPos.z + Math.max(ra.z, rb.z));
    }

    private static Vector3i rotateYaw(Vector3i v, Rotation yaw) {
        switch (yaw) {
            case Ninety:     return new Vector3i(v.z,     v.y, 1 - v.x);
            case OneEighty:  return new Vector3i(1 - v.x, v.y, 1 - v.z);
            case TwoSeventy: return new Vector3i(1 - v.z, v.y, v.x);
            default:         return new Vector3i(v.x,     v.y, v.z);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Vector3i getRelMin() {
        return relMin;
    }

    public Vector3i getRelMax() {
        return relMax;
    }

    public int getHitboxIndex() {
        return hitboxIndex;
    }

    public FaceMode getMode() {
        return mode;
    }

    @Nullable
    public UUID getNeighborNodeId() {
        return neighborNodeId;
    }

    @Nullable
    public ItemContainer getInputInventory() {
        return inputInventory;
    }

    @Nullable
    public ItemContainer getOutputInventory() {
        return outputInventory;
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setMode(FaceMode mode) {
        this.mode = mode;
    }

    public void setNeighborNodeId(@Nullable UUID neighborNodeId) {
        this.neighborNodeId = neighborNodeId;
    }

    public void setInputInventory(ItemContainer inputInventory) {
        this.inputInventory = inputInventory;
    }

    public void setOutputInventory(ItemContainer outputInventory) {
        this.outputInventory = outputInventory;
    }

    /**
     * Convenience method to set both inventories to the same container.
     */
    public void setInventory(ItemContainer inventory) {
        this.inputInventory = inventory;
        this.outputInventory = inventory;
    }

    /**
     * Returns true if this face can currently send items.
     * Requires OUTPUT or BIDIRECTIONAL mode and an output inventory.
     */
    public boolean canSend() {
        return (mode == FaceMode.OUTPUT || mode == FaceMode.BIDIRECTIONAL) &&
                outputInventory != null;
    }

    /**
     * Returns true if this face can currently receive items.
     * Requires INPUT or BIDIRECTIONAL mode and an input inventory.
     */
    public boolean canReceive() {
        return (mode == FaceMode.INPUT || mode == FaceMode.BIDIRECTIONAL) &&
                inputInventory != null;
    }

    // ── Object overrides ──────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format("FacePlane{hitboxIndex=%d, relMin=%s, relMax=%s, mode=%s}",
                hitboxIndex, relMin, relMax, mode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FacePlane other = (FacePlane) o;
        return hitboxIndex == other.hitboxIndex;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(hitboxIndex);
    }

    /**
     * Returns a fresh {@code FacePlane} with the same geometry and mode but no
     * neighbour link ({@code neighborNodeId = null}).
     *
     * Shallow copy for <em>block placement</em>: no neighbor linkage is carried over,
     * because a freshly placed block starts unconnected.
     */
    public FacePlane copy() {
        return new FacePlane(relMin, relMax, hitboxIndex, mode);
    }

    /**
     * Full copy for <em>serialization</em>: preserves {@link #neighborNodeId} so that
     * established edges survive a chunk save/load cycle.
     */
    public FacePlane copyFull() {
        FacePlane f = new FacePlane(relMin, relMax, hitboxIndex, mode);
        f.neighborNodeId = this.neighborNodeId;
        return f;
    }

    // ── Mode compatibility ────────────────────────────────────────────────────

    /**
     * Checks if two face modes are compatible for creating an edge.
     * Returns the type of edge to create, or null if incompatible.
     */
    public static EdgeType checkModeCompatibility(FaceMode modeA, FaceMode modeB) {
        if (modeA == FaceMode.CLOSED || modeB == FaceMode.CLOSED) {
            return null;
        }
        if (modeA == FaceMode.OUTPUT && modeB == FaceMode.INPUT) return EdgeType.A_TO_B;
        if (modeA == FaceMode.INPUT && modeB == FaceMode.OUTPUT) return EdgeType.B_TO_A;
        if (modeA == FaceMode.BIDIRECTIONAL && modeB == FaceMode.BIDIRECTIONAL) return EdgeType.BIDIRECTIONAL;
        if (modeA == FaceMode.OUTPUT && modeB == FaceMode.BIDIRECTIONAL) return EdgeType.A_TO_B;
        if (modeA == FaceMode.BIDIRECTIONAL && modeB == FaceMode.INPUT) return EdgeType.A_TO_B;
        if (modeA == FaceMode.BIDIRECTIONAL && modeB == FaceMode.OUTPUT) return EdgeType.B_TO_A;
        if (modeA == FaceMode.INPUT && modeB == FaceMode.BIDIRECTIONAL) return EdgeType.B_TO_A;
        return null;
    }

    /**
     * Result type for edge creation based on face mode compatibility.
     */
    public enum EdgeType {
        A_TO_B, B_TO_A, BIDIRECTIONAL
    }
}

