package dev.drav.glyphworks.fluid.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class FluidUtil {

    /**
     * 1 source block = 1 000 L = 1 bucket = 1 000 mB
     */
    public static final int LITERS_PER_BLOCK = 1_000;
    public static final int EMPTY_FLUID_ID = 0;
    public static final int EMPTY_BLOCK_ID = 0;

    private FluidUtil() {
    }

    @Nullable
    public static Ref<ChunkStore> getSectionRef(
            @Nonnull ChunkStore chunkStore,
            int blockX, int blockY, int blockZ) {
        Ref<ChunkStore> ref = chunkStore.getChunkSectionReferenceAtBlock(blockX, blockY, blockZ);
        return (ref != null && ref.isValid()) ? ref : null;
    }

    @Nullable
    public static BlockSection getBlockSection(
            @Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> store,
            int blockX, int blockY, int blockZ) {
        Ref<ChunkStore> ref = getSectionRef(chunkStore, blockX, blockY, blockZ);
        if (ref == null)
            return null;
        return store.getComponent(ref, BlockSection.getComponentType());
    }

    @Nullable
    public static FluidSection getFluidSection(
            @Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> store,
            @Nonnull Vector3i pos) {
        Ref<ChunkStore> ref = getSectionRef(chunkStore, pos.x, pos.y, pos.z);
        if (ref == null)
            return null;
        return store.getComponent(ref, FluidSection.getComponentType());
    }

    @Nullable
    public static FluidSection ensureFluidSection(
            @Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> store,
            int blockX, int blockY, int blockZ) {
        Ref<ChunkStore> ref = getSectionRef(chunkStore, blockX, blockY, blockZ);
        if (ref == null)
            return null;
        return store.ensureAndGetComponent(ref, FluidSection.getComponentType());
    }
}
