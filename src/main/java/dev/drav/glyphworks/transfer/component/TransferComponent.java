package dev.drav.glyphworks.transfer.component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
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
    private static final Logger LOGGER = Logger.getLogger(TransferComponent.class.getName());

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
                            c.faces.put(face.getFaceKey(), face);
                        }
                    },
                    c -> new HashSet<>(c.faces.values()))
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
     * Map of configured face regions for this block, keyed by face coordinates.
     * Key = FaceKey(planeMin, planeMax) for fast O(1) collision lookups.
     * Empty map means no faces configured (uses defaultFaceMode for all connections).
     */
    private Map<FaceKey, FacePlane> faces;

    /**
     * Default connection mode for faces not explicitly configured.
     * Used when faces map is empty or a touching area isn't covered by any face.
     */
    private FaceMode defaultFaceMode;


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
        this.defaultFaceMode = FaceMode.CLOSED;
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
        this.defaultFaceMode = FaceMode.CLOSED;
    }

    /**
     * Copy constructor used by {@link #clone()}.
     * Generates a NEW nodeId for each cloned instance (each placed block gets unique ID).
     */
    public TransferComponent(TransferComponent other) {
        this.nodeId = UUID.randomUUID();  // Generate NEW UUID for each block!
        this.maxOutputRate = other.maxOutputRate;
        this.maxInputRate = other.maxInputRate;
        this.autoPush = other.autoPush;
        this.autoPull = other.autoPull;
        this.faces = new HashMap<>(other.faces);
        this.defaultFaceMode = other.defaultFaceMode;
        // inventory references are not copied — must be re-injected at runtime
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new TransferComponent(this);
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
    public void setFaceInventory(FaceKey faceKey, ItemContainer inventory) {
        FacePlane face = faces.get(faceKey);
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

    public Map<FaceKey, FacePlane> getFaces() {
        return faces;
    }

    public FaceMode getDefaultFaceMode() {
        return defaultFaceMode;
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

    public void setDefaultFaceMode(FaceMode defaultFaceMode) {
        this.defaultFaceMode = defaultFaceMode;
    }

    /**
     * Adds or replaces a face in the faces map.
     */
    public void setFace(FaceKey key, FacePlane face) {
        faces.put(key, face);
    }

    /**
     * Removes a face from the faces map.
     */
    public void removeFace(FaceKey key) {
        faces.remove(key);
    }
}
