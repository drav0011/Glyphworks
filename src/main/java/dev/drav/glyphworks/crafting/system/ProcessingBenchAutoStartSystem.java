package dev.drav.glyphworks.crafting.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.grid.component.GridComponent;

/**
 * Automatically activates a {@link ProcessingBenchBlock} each tick when it has
 * a<
 * {@link GridComponent} and the necessary items to begin processing:
 *
 * <ul>
 * <li>At least one item in the input container.</li>
 * <li>At least one item in the fuel container, <em>when</em> the bench
 * defines fuel slots.</li>
 * </ul>
 *
 * <p>
 * This allows grid-connected furnaces/processors to start themselves as soon
 * as an item pipe fills their input (and fuel) slots, without requiring a
 * player
 * to open the UI and press the start button.
 *
 * <p>
 * Deactivation is still handled by the vanilla
 * {@code ProcessingBenchBlock.ProcessingBenchTick} — we never call
 * {@code setActive(false)} here.
 */
public final class ProcessingBenchAutoStartSystem extends EntityTickingSystem<ChunkStore> {

    public ProcessingBenchAutoStartSystem() {
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(ProcessingBenchBlock.getComponentType(), GridComponent.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ProcessingBenchBlock pbb = archetypeChunk.getComponent(index, ProcessingBenchBlock.getComponentType());
        // Skip if already running or bench config not yet initialised.
        if (pbb == null || pbb.isActive() || pbb.getProcessingBench() == null)
            return;

        BenchBlock benchBlock = archetypeChunk.getComponent(index, BenchBlock.getComponentType());
        if (benchBlock == null)
            return;

        // Require at least one item in the input container.
        if (pbb.getInputContainer().isEmpty())
            return;

        // When the bench has fuel slots, require at least one fuel item too.
        if (pbb.getProcessingBench().getFuel() != null) {
            if (pbb.getFuelContainer().isEmpty())
                return;
        }

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index,
                BlockModule.BlockStateInfo.getComponentType());
        pbb.setActive(true, benchBlock, blockStateInfo);
    }
}
