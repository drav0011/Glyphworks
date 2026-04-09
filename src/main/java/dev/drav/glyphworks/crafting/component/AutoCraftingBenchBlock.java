package dev.drav.glyphworks.crafting.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.window.AutoCraftingBenchMonitorWindow;

/**
 * Block state component for automated crafting benches.
 *
 * <p>
 * A single {@link CraftingRecipe} can be locked into this block by a player.
 * Once locked, the {@link #inputContainer} is dynamically sized to match the
 * recipe's ingredient list and each slot is filtered to accept only the
 * matching
 * item or resource-type. Pipes may therefore only insert the correct items.
 * When no recipe is locked the input container has 0 slots, blocking all
 * pipe insertion.
 *
 * <p>
 * Crafting progress is advanced each server tick by
 * {@link dev.drav.glyphworks.crafting.system.AutoCraftingBenchSystem}. When all
 * ingredients are present and the output has space, one craft cycle completes
 * automatically. If the output is full, progress is held at the recipe time
 * until space becomes available — inputs are never consumed into a full output.
 */
public class AutoCraftingBenchBlock implements Component<ChunkStore> {

    private static final short OUTPUT_SLOTS = 4;
    private static final float EJECT_VELOCITY = 2.0f;
    private static final float EJECT_SPREAD_VELOCITY = 1.0f;
    private static final float EJECT_VERTICAL_VELOCITY = 3.25f;

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── CODEC ──────────────────────────────────────────────────────────────────

    @Nonnull
    public static final BuilderCodec<AutoCraftingBenchBlock> CODEC = BuilderCodec
            .builder(AutoCraftingBenchBlock.class, () -> new AutoCraftingBenchBlock())
            .append(
                    new KeyedCodec<>("Glyphworks_AutoCraftingBenchBlock_LockedRecipeId", Codec.STRING),
                    (b, v) -> b.lockedRecipeId = v,
                    b -> b.lockedRecipeId)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoCraftingBenchBlock_InputContainer", ItemContainer.CODEC),
                    (b, v) -> b.inputContainer = v,
                    b -> b.inputContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoCraftingBenchBlock_OutputContainer", ItemContainer.CODEC),
                    (b, v) -> b.outputContainer = v,
                    b -> b.outputContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoCraftingBenchBlock_Progress", Codec.DOUBLE),
                    (b, v) -> b.craftingProgress = v.floatValue(),
                    b -> Double.valueOf(b.craftingProgress))
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoCraftingBenchBlock_ManaConsumptionRate", Codec.FLOAT),
                    (b, v) -> b.manaConsumptionRate = v,
                    b -> b.manaConsumptionRate)
            .add()
            .build();

    // ── Persisted fields (serialised by CODEC) ─────────────────────────────────

    /** The ID of the currently locked recipe, or {@code null} when unlocked. */
    @Nullable
    private String lockedRecipeId;

    /**
     * Dynamic input container. Capacity equals the number of distinct ingredients
     * in the locked recipe; 0 when no recipe is locked. Each slot is filtered to
     * accept only the matching ingredient.
     */
    private ItemContainer inputContainer;

    /**
     * Fixed 4-slot output-only container. Always present after
     * {@link #setupContainers}.
     */
    private ItemContainer outputContainer;

    /**
     * Progress toward the next completed craft, in seconds of recipe time elapsed.
     */
    private float craftingProgress = 0.0f;

    /**
     * Mana consumption rate in liters per tick while actively crafting.
     * Set per bench in JSON; defaults to 0 (no mana required).
     */
    private float manaConsumptionRate = 0.0f;

    // ── Transient / runtime ────────────────────────────────────────────────────

    /**
     * Resolved recipe object; derived from {@link #lockedRecipeId} at load time.
     */
    @Nullable
    private transient CraftingRecipe lockedRecipe;

    /**
     * Combined input+output view; rebuilt whenever the input container is rebuilt.
     */
    @Nullable
    private transient CombinedItemContainer itemContainer;

    /** {@code true} while the bench is actively counting down to the next craft. */
    private transient boolean isCrafting = false;

    /**
     * All monitor windows currently open for this bench, keyed by the viewer's
     * UUID.
     */
    @Nonnull
    private transient Map<UUID, AutoCraftingBenchMonitorWindow> windows = new ConcurrentHashMap<>();

    public AutoCraftingBenchBlock() {}

    public AutoCraftingBenchBlock(@Nonnull AutoCraftingBenchBlock other) {
        this.lockedRecipeId = other.lockedRecipeId;
        this.craftingProgress = other.craftingProgress;
        this.manaConsumptionRate = other.manaConsumptionRate;
    }

    // ── Component type ─────────────────────────────────────────────────────────

    public static ComponentType<ChunkStore, AutoCraftingBenchBlock> getComponentType() {
        return GlyphworksPlugin.get().getAutoCraftingBenchBlockComponentType();
    }

    // ── Container setup ────────────────────────────────────────────────────────

    /**
     * Initialises (or re-initialises) the input and output containers.
     *
     * <p>
     * Called once from
     * {@link dev.drav.glyphworks.crafting.system.AutoCraftingBenchSetupSystem}
     * when the block entity is first loaded or placed. Uses
     * {@link ItemContainer#ensureContainerCapacity} so that items already
     * restored by the CODEC are preserved when the capacity matches.
     */
    public void setupContainers(
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            @Nonnull World world,
            int blockX, int blockY, int blockZ,
            @Nonnull BlockType blockType,
            int rotationIndex) {

        // Resolve the persisted recipe reference.
        if (lockedRecipeId != null) {
            lockedRecipe = (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(lockedRecipeId);
        }

        List<ItemStack> ejected = new ArrayList<>();

        // Output container — fixed 4 slots, output-only.
        outputContainer = ItemContainer.ensureContainerCapacity(
                outputContainer, OUTPUT_SLOTS, SimpleItemContainer::getNewContainer, ejected);
        outputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
        outputContainer.setGlobalFilter(FilterType.ALLOW_OUTPUT_ONLY);

        // Input container — sized to the locked recipe, or 0 if no recipe.
        rebuildInputContainer(blockStateInfo, ejected);

        itemContainer = new CombinedItemContainer(inputContainer, outputContainer);

        // Eject any items that no longer fit (e.g. recipe changed offline).
        if (!ejected.isEmpty()) {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            Holder<EntityStore>[] holders = ejectItems(
                    entityStore, ejected, rotationIndex, blockType, blockX, blockY, blockZ);
            if (holders.length > 0) {
                world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
            }
        }
    }

    /**
     * Rebuilds {@link #inputContainer} to match the currently locked recipe.
     * Items that no longer fit after resizing are added to {@code ejected}.
     */
    private void rebuildInputContainer(
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            @Nonnull List<ItemStack> ejected) {

        List<MaterialQuantity> inputs = lockedRecipe != null
                ? CraftingManager.getInputMaterials(lockedRecipe)
                : List.of();
        short slotCount = (short) inputs.size();

        inputContainer = ItemContainer.ensureContainerCapacity(
                inputContainer, slotCount, SimpleItemContainer::getNewContainer, ejected);
        inputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());

        // Apply per-slot ingredient filters so only the correct item can enter each
        // slot.
        for (short i = 0; i < inputs.size(); i++) {
            MaterialQuantity mat = inputs.get(i);
            inputContainer.setSlotFilter(FilterActionType.ADD, i, (actionType, container, slotIndex, itemStack) -> {
                if (itemStack == null)
                    return true;
                return CraftingManager.matches(mat, itemStack);
            });
        }
    }

    // ── Recipe locking ─────────────────────────────────────────────────────────

    /**
     * Locks a new recipe into this bench, rebuilding the input container to match.
     *
     * <p>
     * All items currently in the input container are ejected as item entities
     * since they may not match the new recipe. Pass {@code null} to unlock,
     * which collapses the input container to 0 slots.
     */
    public void setLockedRecipe(
            @Nullable String recipeId,
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            @Nonnull World world,
            int blockX, int blockY, int blockZ,
            @Nonnull BlockType blockType,
            int rotationIndex) {

        lockedRecipeId = recipeId;
        lockedRecipe = recipeId != null
                ? (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(recipeId)
                : null;
        craftingProgress = 0.0f;
        isCrafting = false;

        List<ItemStack> ejected = new ArrayList<>();

        // Drop all existing input items — they may not match the new recipe.
        if (inputContainer != null) {
            ejected.addAll(inputContainer.dropAllItemStacks());
            inputContainer = null; // force fresh container below
        }

        rebuildInputContainer(blockStateInfo, ejected);
        itemContainer = new CombinedItemContainer(inputContainer, outputContainer);

        if (!ejected.isEmpty()) {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            Holder<EntityStore>[] holders = ejectItems(
                    entityStore, ejected, rotationIndex, blockType, blockX, blockY, blockZ);
            if (holders.length > 0) {
                world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
            }
        }

        blockStateInfo.markNeedsSaving();
    }

    // ── Crafting logic (called by AutoCraftingBenchSystem) ─────────────────────

    /**
     * Returns {@code true} when all recipe inputs are present in the required
     * quantities (i.e. one craft cycle can be consumed from the input container).
     */
    public boolean isReadyToCraft() {
        if (lockedRecipe == null || inputContainer == null || inputContainer.getCapacity() == 0) {
            return false;
        }
        List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(lockedRecipe);
        if (inputs.isEmpty())
            return false;
        return !inputContainer.getSlotMaterialsToRemove(inputs, true, true).isEmpty();
    }

    /**
     * Returns {@code true} if the output container has enough space to accept
     * the full output of one craft cycle.
     *
     * <p>
     * When {@code false}, the tick system holds {@link #craftingProgress} at
     * the recipe time without consuming any inputs.
     */
    public boolean canFitOutput() {
        if (lockedRecipe == null || outputContainer == null)
            return false;
        List<ItemStack> outputs = CraftingManager.getOutputItemStacks(lockedRecipe);
        return outputContainer.canAddItemStacks(outputs, false, false);
    }

    /**
     * Consumes one set of recipe inputs and adds the outputs to the output
     * container. Any output that cannot fit is ejected as item entities.
     *
     * <p>
     * Should only be called when both {@link #isReadyToCraft()} and
     * {@link #canFitOutput()} return {@code true}.
     *
     * @throws MatchException if the internal remove-materials transaction fails
     */
    public void completeCraft(
            @Nonnull Store<EntityStore> entityStore,
            int blockX, int blockY, int blockZ,
            @Nonnull BlockType blockType,
            int rotationIndex) throws MatchException {

        if (lockedRecipe == null)
            return;

        List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(lockedRecipe);
        List<ItemStack> outputs = CraftingManager.getOutputItemStacks(lockedRecipe);

        ListTransaction<MaterialTransaction> removeTx = inputContainer.removeMaterials(inputs, true, true, true);
        if (!removeTx.succeeded())
            return;

        craftingProgress = 0.0f;
        isCrafting = false;

        // Add outputs; eject any remainder that did not fit.
        ListTransaction<ItemStackTransaction> addTx = outputContainer.addItemStacks(outputs, false, false, false);
        List<ItemStack> remainder = new ArrayList<>();
        for (ItemStackTransaction tx : addTx.getList()) {
            ItemStack rem = tx.getRemainder();
            if (rem != null && !rem.isEmpty())
                remainder.add(rem);
        }
        if (!remainder.isEmpty()) {
            Holder<EntityStore>[] holders = ejectItems(
                    entityStore, remainder, rotationIndex, blockType, blockX, blockY, blockZ);
            entityStore.addEntities(holders, AddReason.SPAWN);
        }
    }

    // ── Getters / setters ──────────────────────────────────────────────────────

    @Nullable
    public String getLockedRecipeId() {
        return lockedRecipeId;
    }

    @Nullable
    public CraftingRecipe getLockedRecipe() {
        return lockedRecipe;
    }

    public float getCraftingProgress() {
        return craftingProgress;
    }

    public void setCraftingProgress(float progress) {
        craftingProgress = progress;
    }

    public boolean isCrafting() {
        return isCrafting;
    }

    public void setCrafting(boolean crafting) {
        isCrafting = crafting;
    }

    public float getManaConsumptionRate() {
        return manaConsumptionRate;
    }

    @Nullable
    public ItemContainer getInputContainer() {
        return inputContainer;
    }

    @Nullable
    public ItemContainer getOutputContainer() {
        return outputContainer;
    }

    @Nullable
    public CombinedItemContainer getItemContainer() {
        return itemContainer;
    }

    @Nonnull
    public Map<UUID, AutoCraftingBenchMonitorWindow> getWindows() {
        return Collections.unmodifiableMap(windows);
    }

    /** Pushes a normalised progress value (0–1) to all open monitor windows. */
    public void sendProgress(float progress) {
        if (!windows.isEmpty()) {
            windows.values().forEach(w -> w.setProgress(progress));
        }
    }

    // ── Item ejection ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Holder<EntityStore>[] ejectItems(
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull List<ItemStack> items,
            int rotationIndex,
            @Nullable BlockType blockType,
            int blockX, int blockY, int blockZ) {

        if (items.isEmpty())
            return Holder.emptyArray();

        RotationTuple rotation = RotationTuple.get(rotationIndex);
        Vector3d frontDir = new Vector3d(0.0d, 0.0d, 1.0d);
        rotation.yaw().rotateY(frontDir, frontDir);

        Vector3d dropPos;
        if (blockType == null) {
            dropPos = new Vector3d(blockX + 0.5d, blockY, blockZ + 0.5d);
        } else {
            BlockBoundingBoxes hitbox = (BlockBoundingBoxes) BlockBoundingBoxes.getAssetMap()
                    .getAsset(blockType.getHitboxTypeIndex());
            if (hitbox == null) {
                dropPos = new Vector3d(blockX + 0.5d, blockY, blockZ + 0.5d);
            } else {
                double depth = hitbox.get(0).getBoundingBox().depth();
                double frontOffset = (depth / 2.0d) + 0.1d;
                dropPos = getCenteredBlockPosition(blockType, rotationIndex, blockX, blockY, blockZ);
                dropPos.add(frontDir.x * frontOffset, 0.0d, frontDir.z * frontOffset);
            }
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<Holder<EntityStore>> result = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            float vx = (float) ((frontDir.x * EJECT_VELOCITY)
                    + (EJECT_SPREAD_VELOCITY * (rng.nextDouble() - 0.5d)));
            float vz = (float) ((frontDir.z * EJECT_VELOCITY)
                    + (EJECT_SPREAD_VELOCITY * (rng.nextDouble() - 0.5d)));
            Holder<EntityStore> h = ItemComponent.generateItemDrop(
                    accessor, item, dropPos, Rotation3f.ZERO, vx, EJECT_VERTICAL_VELOCITY, vz);
            if (h != null)
                result.add(h);
        }
        return result.toArray(Holder[]::new);
    }

    private static Vector3d getCenteredBlockPosition(
            @Nonnull BlockType blockType, int rotationIndex, int blockX, int blockY, int blockZ) {
        Vector3d center = new Vector3d();
        blockType.getBlockCenter(rotationIndex, center);
        return center.add(blockX, blockY, blockZ);
    }

    // ── clone ──────────────────────────────────────────────────────────────────

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new AutoCraftingBenchBlock(this);
    }
}
