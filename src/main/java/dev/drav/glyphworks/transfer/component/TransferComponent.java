package dev.drav.glyphworks.transfer.component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Turns any block into a node in the Glyphworks item transfer network.
 *
 * <p>
 * The component holds two {@link ItemContainer} references — one for
 * incoming items ({@code inputInventory}) and one for outgoing items
 * ({@code outputInventory}). Both can point to the same container (e.g. a
 * chest) or to different ones (e.g. a machine with separate in/out slots).
 *
 * <p>
 * The inventory references are <strong>not serialised</strong> — they are
 * wired at runtime by the owning block. Only behaviour flags and identity are
 * persisted via {@link #CODEC}.
 *
 * <h3>Push vs Pull</h3>
 * <ul>
 * <li><b>canPush</b> — source node: items leave via
 * {@code outputInventory}.</li>
 * <li><b>canPull</b> — sink node: items arrive into
 * {@code inputInventory}.</li>
 * </ul>
 *
 * <h3>Auto flags</h3>
 * <ul>
 * <li><b>autoPush</b> — system initiates pushes automatically each tick.</li>
 * <li><b>autoPull</b> — system initiates pulls automatically each tick.</li>
 * </ul>
 *
 * <h3>Rates</h3>
 * {@code maxOutputRate} caps how many item stacks leave per tick;
 * {@code maxInputRate} caps how many arrive. The transfer system picks
 * {@code min(source.maxOutputRate, sink.maxInputRate)} as the effective limit.
 */
public class TransferComponent implements Component<ChunkStore> {
    public static final BuilderCodec<TransferComponent> CODEC = BuilderCodec
            .builder(TransferComponent.class, TransferComponent::new)
            .append(
                    new KeyedCodec<>("Transfer_NodeId", Codec.STRING),
                    (c, v) -> c.nodeId = UUID.fromString(v),
                    c -> c.nodeId.toString())
            .add()
            .append(
                    new KeyedCodec<>("Transfer_MaxOutputRate", Codec.INTEGER),
                    (c, v) -> c.maxOutputRate = v,
                    c -> c.maxOutputRate)
            .add()
            .append(
                    new KeyedCodec<>("Transfer_MaxInputRate", Codec.INTEGER),
                    (c, v) -> c.maxInputRate = v,
                    c -> c.maxInputRate)
            .add()
            .append(
                    new KeyedCodec<>("Transfer_AutoPush", Codec.BOOLEAN),
                    (c, v) -> c.autoPush = v,
                    c -> c.autoPush)
            .add()
            .append(
                    new KeyedCodec<>("Transfer_AutoPull", Codec.BOOLEAN),
                    (c, v) -> c.autoPull = v,
                    c -> c.autoPull)
            .add()
            // Persist face states - graph edges are reconstructed from faces on world load.
            .append(
                    new KeyedCodec<>("Transfer_Faces", new SetCodec<>(FacePlane.CODEC, HashSet::new, false)),
                    (c, v) -> {
                        c.faces = new HashMap<>();
                        for (FacePlane face : v) {
                            c.faces.put(face.getHitboxIndex(), face);
                        }
                    },
                    c -> new HashSet<>(c.faces.values()))
            .add()
            .append(
                    new KeyedCodec<>("Transfer_Yaw", new EnumCodec<>(Rotation.class)),
                    (c, v) -> c.yaw = (v == null ? Rotation.None : v),
                    c -> c.yaw)
            .add()
            .build();

    public static ComponentType<ChunkStore, TransferComponent> getComponentType() {
        return GlyphworksPlugin.get().getTransferComponentType();
    }

    // ------------------------------------------------------------------
    // Persisted fields
    // ------------------------------------------------------------------

    /**
     * Stable identity used as the graph node key. Generated on first placement.
     */
    private UUID nodeId;

    /**
     * Max item stacks this node can <em>send</em> per tick.
     */
    private int maxOutputRate;

    /**
     * Max item stacks this node can <em>receive</em> per tick.
     */
    private int maxInputRate;

    /**
     * When {@code true} the system automatically initiates pushes each tick.
     */
    private boolean autoPush;

    /**
     * When {@code true} the system automatically initiates pulls each tick.
     */
    private boolean autoPull;

    /**
     * Map of configured face regions for this block, keyed by hitboxIndex.
     * A hitboxIndex of -1 is non-interactable (faces not triggered by click).
     */
    private Map<Integer, FacePlane> faces;

    /**
     * Yaw rotation the block was placed with, in north-facing coords.
     * Always non-null; defaults to {@link Rotation#None}.
     */
    private Rotation yaw = Rotation.None;



    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    /**
     * No-arg constructor required by {@link #CODEC}.
     * Defaults to bidirectional container with no specific face config.
     */
    public TransferComponent() {
        this(Integer.MAX_VALUE, Integer.MAX_VALUE, true, true);
        this.faces = new HashMap<>();
    }

    /**
     * Full constructor.
     *
     * @param maxOutputRate Max item stacks sent per tick. Pass {@code 0} to disable pushing.
     * @param maxInputRate  Max item stacks received per tick. Pass {@code 0} to disable pulling.
     * @param autoPush      Whether the system pushes automatically each tick.
     * @param autoPull      Whether the system pulls automatically each tick.
     */
    public TransferComponent(
            int maxOutputRate,
            int maxInputRate,
            boolean autoPush,
            boolean autoPull) {
        this.nodeId = UUID.randomUUID();
        this.maxOutputRate = maxOutputRate;
        this.maxInputRate = maxInputRate;
        this.autoPush = autoPush;
        this.autoPull = autoPull;
        this.faces = new HashMap<>();
    }

    /**
     * Copy constructor used by {@link #clone()}.
     * Generates a NEW nodeId for each cloned instance (each placed block gets unique ID).
     * Each {@link FacePlane} is deep-copied so blocks don't share mutable face state.
     */
    public TransferComponent(TransferComponent other) {
        this.nodeId = UUID.randomUUID();  // Generate NEW UUID for each block!
        this.maxOutputRate = other.maxOutputRate;
        this.maxInputRate = other.maxInputRate;
        this.autoPush = other.autoPush;
        this.autoPull = other.autoPull;
        this.faces = new HashMap<>(other.faces.size());
        for (Map.Entry<Integer, FacePlane> e : other.faces.entrySet()) {
            this.faces.put(e.getKey(), e.getValue().copy());
        }
        // inventory references are not copied — must be re-injected at runtime
    }

    /**
     * Placement copy: generates a fresh node UUID and strips all neighbour linkage.
     * Used by the engine when a player places a block from a prototype.
     */
    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new TransferComponent(this);
    }

    /**
     * Serialization copy: preserves the existing node UUID and all face state
     * including {@link FacePlane#getNeighborNodeId()} so that edges survive a
     * chunk save/load cycle.
     *
     * <p>The default {@code Component.cloneSerializable()} falls back to
     * {@link #clone()}, which resets everything — overriding it here is critical.
     */
    @Nullable
    @Override
    public Component<ChunkStore> cloneSerializable() {
        TransferComponent copy = new TransferComponent(
                this.maxOutputRate, this.maxInputRate, this.autoPush, this.autoPull);
        copy.nodeId = this.nodeId; // preserve stable identity
        copy.yaw = this.yaw;
        copy.faces = new HashMap<>(this.faces.size());
        for (Map.Entry<Integer, FacePlane> e : this.faces.entrySet()) {
            copy.faces.put(e.getKey(), e.getValue().copyFull()); // preserve neighbourNodeId
        }
        return copy;
    }

    // ------------------------------------------------------------------
    // Inventory Wiring (per-face)
    // ------------------------------------------------------------------

    /**
     * Sets the inventory for a specific face.
     * Wires the inventory based on the face's mode.
     *
     * @param faceKey   The face coordinates key (planeMin, planeMax)
     * @param inventory The ItemContainer to wire to this face
     */
    public void setFaceInventory(int hitboxIndex, ItemContainer inventory) {
        FacePlane face = faces.get(hitboxIndex);
        if (face == null) {
            return;
        }

        // Wire based on face mode
        switch (face.getMode()) {
            case INPUT:
                face.setInputInventory(inventory);
                break;
            case OUTPUT:
                face.setOutputInventory(inventory);
                break;
            case BIDIRECTIONAL:
                face.setInventory(inventory); // Both input and output
                break;
            case CLOSED:
                // Don't wire anything
                break;
        }
    }

    /**
     * Convenience method to set the same inventory for all faces.
     * Useful for simple blocks with a single inventory.
     */
    public void setInventoryForAllFaces(ItemContainer inventory) {
        for (FacePlane face : faces.values()) {
            switch (face.getMode()) {
                case INPUT:
                    face.setInputInventory(inventory);
                    break;
                case OUTPUT:
                    face.setOutputInventory(inventory);
                    break;
                case BIDIRECTIONAL:
                    face.setInventory(inventory);
                    break;
                case CLOSED:
                    // Don't wire anything
                    break;
            }
        }
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public UUID getNodeId() {
        return nodeId;
    }

    public int getMaxOutputRate() {
        return maxOutputRate;
    }

    public int getMaxInputRate() {
        return maxInputRate;
    }

    public boolean isAutoPush() {
        return autoPush;
    }

    public boolean isAutoPull() {
        return autoPull;
    }

    public Map<Integer, FacePlane> getFaces() {
        return faces;
    }

    public Rotation getYaw() {
        return yaw;
    }

    // ------------------------------------------------------------------
    // Setters (runtime config — no graph rebuild needed unless noted)
    // ------------------------------------------------------------------

    public void setMaxOutputRate(int maxOutputRate) {
        this.maxOutputRate = maxOutputRate;
    }

    public void setMaxInputRate(int maxInputRate) {
        this.maxInputRate = maxInputRate;
    }

    public void setAutoPush(boolean autoPush) {
        this.autoPush = autoPush;
    }

    public void setAutoPull(boolean autoPull) {
        this.autoPull = autoPull;
    }

    /**
     * Adds or replaces a face in the faces map.
     */
    public void setFace(int hitboxIndex, FacePlane face) {
        faces.put(hitboxIndex, face);
    }

    /**
     * Removes a face from the faces map.
     */
    public void removeFace(int hitboxIndex) {
        faces.remove(hitboxIndex);
    }

    public void setYaw(Rotation yaw) {
        this.yaw = (yaw == null ? Rotation.None : yaw);
    }
}
