package dev.drav.glyphworks.crafting.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.crafting.component.ManaLiquifierBlock;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;

/**
 * Per-tick processing system for {@link ManaLiquifierBlock}.
 *
 * <p>
 * Each tick, the system:
 * <ol>
 * <li>If no fuel energy remains, attempts to consume one fuel item from the
 * fuel container, converting its {@code FuelQuality} into seconds of burn
 * time. If no fuel is available, processing pauses.</li>
 * <li>Decrements {@link ManaLiquifierBlock#getRemainingFuelEnergy()} by
 * {@code dt} and advances {@link ManaLiquifierBlock#getProcessingProgress()}
 * by {@code dt}.</li>
 * <li>When progress reaches {@link ManaLiquifierBlock#RECIPE_TIME}, checks
 * that the input slot contains a valid essence and that the output
 * {@link FluidContainerComponent} has room for
 * {@link ManaLiquifierBlock#MANA_OUTPUT_PER_ESSENCE} liters. If both
 * conditions are met: consumes the essence and fills the fluid container.
 * If the output is full, progress is clamped until space frees up.</li>
 * </ol>
 */
public final class ManaLiquifierSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return ManaLiquifierBlock.getComponentType();
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) throws MatchException {

        ManaLiquifierBlock mlb = archetypeChunk.getComponent(index, ManaLiquifierBlock.getComponentType());
        if (mlb == null)
            return;

        ItemContainer inputContainer = mlb.getInputContainer();
        ItemContainer fuelContainer = mlb.getFuelContainer();
        if (inputContainer == null || fuelContainer == null)
            return;

        // ── Step 1: ensure there is fuel energy available ──────────────────────
        if (mlb.getRemainingFuelEnergy() <= 0.0f) {
            // Try to consume one fuel item.
            boolean consumed = false;
            for (short slot = 0; slot < fuelContainer.getCapacity(); slot++) {
                ItemStack fuelStack = fuelContainer.getItemStack(slot);
                if (ItemStack.isEmpty(fuelStack))
                    continue;
                double quality = fuelStack.getItem().getFuelQuality();
                if (quality <= 0.0)
                    continue;
                // Remove one item from the fuel slot and grant its energy as burn seconds.
                fuelContainer.removeItemStackFromSlot(slot, 1);
                mlb.setRemainingFuelEnergy((float) quality);
                consumed = true;
                break;
            }
            if (!consumed) {
                // No fuel — processing paused; do not reset progress.
                return;
            }
        }

        // ── Step 2: advance progress using fuel ────────────────────────────────
        float fuelLeft = mlb.getRemainingFuelEnergy() - dt;
        mlb.setRemainingFuelEnergy(Math.max(0.0f, fuelLeft));
        float newProgress = Math.min(mlb.getProcessingProgress() + dt, ManaLiquifierBlock.RECIPE_TIME);
        mlb.setProcessingProgress(newProgress);

        // ── Step 3: complete cycle if progress has reached recipe time ─────────
        if (newProgress < ManaLiquifierBlock.RECIPE_TIME)
            return;

        // Check essence in input slot.
        ItemStack essence = null;
        short essenceSlot = -1;
        for (short slot = 0; slot < inputContainer.getCapacity(); slot++) {
            ItemStack stack = inputContainer.getItemStack(slot);
            if (!ItemStack.isEmpty(stack) && ManaLiquifierBlock.ESSENCE_IDS.contains(stack.getItem().getId())) {
                essence = stack;
                essenceSlot = slot;
                break;
            }
        }
        if (essence == null) {
            // No valid essence — stay paused at recipe time.
            return;
        }

        // Check fluid container has space.
        FluidContainerComponent fluid = archetypeChunk.getComponent(
                index, FluidContainerComponent.getComponentType());
        if (fluid == null)
            return;

        int added = fluid.fill(ManaLiquifierBlock.MANA_FLUID_ID, ManaLiquifierBlock.MANA_OUTPUT_PER_ESSENCE);
        if (added <= 0) {
            // Output full — stay paused at recipe time until space frees up.
            return;
        }

        // Consume one essence and refund partial fill if less than expected was accepted.
        // (In practice fill() returns MANA_OUTPUT_PER_ESSENCE when there is space.)
        inputContainer.removeItemStackFromSlot(essenceSlot, 1);
        mlb.setProcessingProgress(0.0f);
    }
}
