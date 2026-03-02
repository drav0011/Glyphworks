package dev.drav.glyphworks.transfer.component;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Defines a rectangular face region in absolute world coordinates.
 *
 * <p>A face is a 3D rectangle (planeMin to planeMax) with:
 * <ul>
 *   <li>A connection mode (INPUT, OUTPUT, BIDIRECTIONAL, CLOSED)</li>
 *   <li>Its own input inventory (where items arrive)</li>
 *   <li>Its own output inventory (where items are sourced from)</li>
 * </ul>
 *
 * <p>Coordinates are in absolute world space (from world origin 0,0,0).
 * When a block is placed, faces are calculated from block position + bounding box.
 *
 * <p>Example: A 2x2x1 block placed at world position (10, 5, 20):
 * <pre>
 * North face (entire 2x2 area):
 *   planeMin = (10, 5, 22)  // World coords of one corner
 *   planeMax = (11, 6, 22)  // World coords of opposite corner
 *   mode = OUTPUT
 *   inputInventory = null (OUTPUT face doesn't receive)
 *   outputInventory = blockInventory (items sourced from here)
 * </pre>
 *
 * <p><b>Collision Detection:</b> Two faces collide if they occupy the same 3D space:
 * <pre>
 * faceA.planeMin.equals(faceB.planeMin) && faceA.planeMax.equals(faceB.planeMax)
 * </pre>
 */
public class FacePlane {

    public static final BuilderCodec<FacePlane> CODEC = BuilderCodec
            .builder(FacePlane.class, FacePlane::new)
            .append(
                    new KeyedCodec<>("FacePlane_Min", Vector3i.CODEC),
                    (c, v) -> c.planeMin = v,
                    c -> c.planeMin)
            .add()
            .append(
                    new KeyedCodec<>("FacePlane_Max", Vector3i.CODEC),
                    (c, v) -> c.planeMax = v,
                    c -> c.planeMax)
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

    /**
     * Minimum corner of the face region in absolute world coordinates.
     */
    private Vector3i planeMin;

    /**
     * Maximum corner of the face region in absolute world coordinates.
     */
    private Vector3i planeMax;

    /**
     * Connection mode for this face region.
     */
    private FaceMode mode;

    /**
     * UUID of the neighboring TransferComponent node connected through this face.
     * Null means no connection. Persisted so the edge graph survives restarts.
     */
    @Nullable
    private UUID neighborNodeId;

    /**
     * Inventory where incoming items are deposited for this face.
     * Null if this face doesn't accept inputs (e.g., OUTPUT or CLOSED faces).
     * Not serialized - must be wired at runtime.
     */
    private transient ItemContainer inputInventory;

    /**
     * Inventory from which outgoing items are sourced for this face.
     * Null if this face doesn't send outputs (e.g., INPUT or CLOSED faces).
     * Not serialized - must be wired at runtime.
     */
    private transient ItemContainer outputInventory;

    /**
     * No-arg constructor for serialization.
     */
    public FacePlane() {
        this(new Vector3i(0, 0, 0), new Vector3i(0, 0, 0), FaceMode.BIDIRECTIONAL);
    }

    /**
     * Full constructor.
     *
     * @param planeMin Minimum corner in absolute world coordinates
     * @param planeMax Maximum corner in absolute world coordinates
     * @param mode     Connection mode (INPUT, OUTPUT, BIDIRECTIONAL, CLOSED)
     */
    public FacePlane(Vector3i planeMin, Vector3i planeMax, FaceMode mode) {
        this.planeMin = planeMin;
        this.planeMax = planeMax;
        this.mode = mode;
        this.neighborNodeId = null;
        this.inputInventory = null;
        this.outputInventory = null;
    }

    // Getters
    public Vector3i getPlaneMin() {
        return planeMin;
    }

    public Vector3i getPlaneMax() {
        return planeMax;
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

    // Setters
    public void setPlaneMin(Vector3i planeMin) {
        this.planeMin = planeMin;
    }

    public void setPlaneMax(Vector3i planeMax) {
        this.planeMax = planeMax;
    }

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
     * Useful for BIDIRECTIONAL faces that use a single inventory.
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

    @Override
    public String toString() {
        return String.format("FacePlane{min=%s, max=%s, mode=%s, canSend=%s, canReceive=%s}",
                planeMin, planeMax, mode, canSend(), canReceive());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FacePlane other = (FacePlane) o;
        return Objects.equals(planeMin, other.planeMin) &&
                Objects.equals(planeMax, other.planeMax);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planeMin, planeMax);
    }

    /**
     * Returns the FaceKey for this face (for HashMap lookups).
     */
    public FaceKey getFaceKey() {
        return new FaceKey(planeMin, planeMax);
    }

    /**
     * Checks if two faces collide (occupy the same 3D space).
     * Since faces store absolute world coordinates, collision is simply
     * checking if both min and max are identical.
     *
     * @param faceA First face
     * @param faceB Second face
     * @return true if faces occupy exactly the same 3D rectangle
     */
    public static boolean facesCollide(FacePlane faceA, FacePlane faceB) {
        return faceA.planeMin.equals(faceB.planeMin) &&
                faceA.planeMax.equals(faceB.planeMax);
    }
    
    /**
     * Checks if two face modes are compatible for creating an edge.
     * Returns the type of edge to create, or null if incompatible.
     */
    public static EdgeType checkModeCompatibility(FaceMode modeA, FaceMode modeB) {
        if (modeA == FaceMode.CLOSED || modeB == FaceMode.CLOSED) {
            return null; // No connection with CLOSED
        }

        if (modeA == FaceMode.OUTPUT && modeB == FaceMode.INPUT) {
            return EdgeType.A_TO_B; // A → B
        }
        if (modeA == FaceMode.INPUT && modeB == FaceMode.OUTPUT) {
            return EdgeType.B_TO_A; // B → A
        }
        if (modeA == FaceMode.BIDIRECTIONAL && modeB == FaceMode.BIDIRECTIONAL) {
            return EdgeType.BIDIRECTIONAL; // A ↔ B
        }
        if (modeA == FaceMode.OUTPUT && modeB == FaceMode.BIDIRECTIONAL) {
            return EdgeType.A_TO_B; // A → B
        }
        if (modeA == FaceMode.BIDIRECTIONAL && modeB == FaceMode.INPUT) {
            return EdgeType.A_TO_B; // A → B
        }
        if (modeA == FaceMode.BIDIRECTIONAL && modeB == FaceMode.OUTPUT) {
            return EdgeType.B_TO_A; // B → A
        }
        if (modeA == FaceMode.INPUT && modeB == FaceMode.BIDIRECTIONAL) {
            return EdgeType.B_TO_A; // B → A
        }

        // INPUT + INPUT or OUTPUT + OUTPUT = incompatible
        return null;
    }

    /**
     * Result type for edge creation based on face mode compatibility.
     */
    public enum EdgeType {
        A_TO_B,        // Create unidirectional edge from A to B
        B_TO_A,        // Create unidirectional edge from B to A
        BIDIRECTIONAL  // Create bidirectional edge
    }
}

