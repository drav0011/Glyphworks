package dev.drav.glyphworks.crafting.interaction;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.window.AutoCraftingBenchMonitorWindow;
import dev.drav.glyphworks.crafting.window.AutoCraftingBenchSelectWindow;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;

/**
 * Opens the automated crafting bench UI.
 *
 * <ul>
 * <li>If no recipe is locked → opens {@link AutoCraftingBenchSelectWindow}
 * (basic crafting browser) so the player can pick a recipe.</li>
 * <li>If a recipe is locked → opens {@link AutoCraftingBenchMonitorWindow}
 * (processing-style view) showing live progress and the item containers.
 * The "Change Recipe" button (SetActive=false) clears the lock.</li>
 * </ul>
 */
public class OpenAutoCraftingBenchInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenAutoCraftingBenchInteraction> CODEC = BuilderCodec.builder(
            OpenAutoCraftingBenchInteraction.class,
            OpenAutoCraftingBenchInteraction::new,
            SimpleBlockInteraction.CODEC)
            .documentation("Opens the automated crafting bench.")
            .build();

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i pos,
            @Nonnull CooldownHandler cooldownHandler) {

        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();

        Player playerComponent = (Player) commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null)
            return;

        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkRef == null || !chunkRef.isValid())
            return;

        Store<ChunkStore> chunkStoreStore = chunkStore.getStore();
        BlockComponentChunk blockComponentChunk = (BlockComponentChunk) chunkStoreStore.getComponent(
                chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null)
            return;

        Ref<ChunkStore> blockEntityRef = blockComponentChunk.getEntityReference(
                ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
        if (blockEntityRef == null || !blockEntityRef.isValid())
            return;

        AutoCraftingBenchBlock acbb = (AutoCraftingBenchBlock) chunkStoreStore.getComponent(
                blockEntityRef, AutoCraftingBenchBlock.getComponentType());
        BenchBlock benchBlock = (BenchBlock) chunkStoreStore.getComponent(
                blockEntityRef, BenchBlock.getComponentType());
        BlockModule.BlockStateInfo blockStateInfo = (BlockModule.BlockStateInfo) chunkStoreStore.getComponent(
                blockEntityRef, BlockModule.BlockStateInfo.getComponentType());

        if (acbb == null || benchBlock == null || blockStateInfo == null)
            return;

        PlayerRef playerRef = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null)
            sendFluidInfo(playerRef, chunkStoreStore, blockEntityRef);

        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null)
            return;

        UUIDComponent uuidComponent = (UUIDComponent) commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null)
            return;
        UUID uuid = uuidComponent.getUuid();

        WorldChunk worldChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (worldChunk == null)
            return;
        int rotationIndex = worldChunk.getRotationIndex(pos.x, pos.y, pos.z);

        int openSoundIndex = blockType.getBench().getLocalOpenSoundEventIndex();
        int closeSoundIndex = blockType.getBench().getLocalCloseSoundEventIndex();

        if (acbb.getLockedRecipeId() == null) {
            // ── No recipe locked: open recipe selector ────────────────────────
            AutoCraftingBenchSelectWindow selectWindow = new AutoCraftingBenchSelectWindow(
                    acbb, benchBlock, blockStateInfo,
                    pos.x, pos.y, pos.z, rotationIndex, blockType);
            openBenchPage(playerComponent, ref, store, selectWindow,
                    openSoundIndex, closeSoundIndex, commandBuffer, null);
        } else {
            // ── Recipe locked: open monitor window ────────────────────────────
            Map<UUID, AutoCraftingBenchMonitorWindow> windows = acbb.getWindows();
            AutoCraftingBenchMonitorWindow monitorWindow = new AutoCraftingBenchMonitorWindow(
                    acbb, benchBlock, blockStateInfo,
                    pos.x, pos.y, pos.z, rotationIndex, blockType);

            if (windows.putIfAbsent(uuid, monitorWindow) == null) {
                if (!openBenchPage(playerComponent, ref, store, monitorWindow,
                        openSoundIndex, closeSoundIndex, commandBuffer,
                        () -> windows.remove(uuid, monitorWindow))) {
                    windows.remove(uuid, monitorWindow);
                }
            }
        }
    }

    /**
     * Opens {@code window} on {@link Page#Bench}, registers the close sound, and
     * optionally runs {@code onClose} when the window is dismissed.
     *
     * @return {@code true} if the page was successfully opened
     */
    private static boolean openBenchPage(
            @Nonnull Player playerComponent,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull BenchWindow window,
            int openSoundIndex,
            int closeSoundIndex,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nullable Runnable onClose) {
        if (!playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)) {
            return false;
        }

        window.registerCloseEvent(event -> {
            if (onClose != null)
                onClose.run();
            if (closeSoundIndex != 0) {
                SoundUtil.playSoundEvent2d(ref, closeSoundIndex, SoundCategory.UI, commandBuffer);
            }
        });

        if (openSoundIndex != 0) {
            SoundUtil.playSoundEvent2d(ref, openSoundIndex, SoundCategory.UI, commandBuffer);
        }

        return true;
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
        // No client-side simulation needed.
    }

    private static void sendFluidInfo(
            @Nonnull PlayerRef player,
            @Nonnull Store<ChunkStore> store,
            @Nonnull Ref<ChunkStore> blockEntityRef) {
        FluidContainerComponent fcc = (FluidContainerComponent) store.getComponent(
                blockEntityRef, FluidContainerComponent.getComponentType());
        if (fcc == null)
            return;

        String fluid = fcc.getFluidId() != null ? fcc.getFluidId() : "Empty";
        player.sendMessage(Message.raw(
                "[Glyphworks] Mana: " + fluid + " " + fcc.getAmount() + "/" + fcc.getCapacity() + "L"));
    }
}
