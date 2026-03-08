package dev.drav.glyphworks.transfer.event;

import javax.annotation.Nonnull;

import java.util.UUID;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.transfer.TransferGraph;
import dev.drav.glyphworks.transfer.component.FaceMode;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.lookups.TransferLookup;
import dev.drav.glyphworks.transfer.state.TransferStateRegistry;
import dev.drav.glyphworks.util.BlockHitboxRaycast;
import dev.drav.glyphworks.util.FaceLinkUtil;

public final class UseTransferableBlockEvent extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    public static final String FACE_WRENCH_ITEM_ID = "Face_Wrench";

    private static final String PIPE_INTERACTION_ID = "Glyphworks_PipeUse";

    private static final double MAX_REACH = 10.0;

    public UseTransferableBlockEvent() {
        super(UseBlockEvent.Pre.class);
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull UseBlockEvent.Pre event) {

        String blockInteractionId = event.getBlockType().getInteractions().get(event.getInteractionType());
        if (!PIPE_INTERACTION_ID.equals(blockInteractionId)) return;

        ItemStack heldItem = event.getContext().getHeldItem();
        if (heldItem == null || heldItem.isEmpty() || !FACE_WRENCH_ITEM_ID.equals(heldItem.getItemId())) return;

        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        ModelComponent modelComponent = archetypeChunk.getComponent(index, ModelComponent.getComponentType());
        if (modelComponent == null) return;

        HeadRotation headRotation = archetypeChunk.getComponent(index, HeadRotation.getComponentType());
        if (headRotation == null) return;

        final double eyeX = transform.getPosition().getX();
        final double eyeY = transform.getPosition().getY() + modelComponent.getModel().getEyeHeight();
        final double eyeZ = transform.getPosition().getZ();

        final Vector3d dir = headRotation.getDirection();
        final double dirX = dir.x;
        final double dirY = dir.y;
        final double dirZ = dir.z;

        final Vector3i pos = event.getTargetBlock();

        commandBuffer.run(_ -> {
            World world = commandBuffer.getExternalData().getWorld();
            ChunkStore chunkStore = world.getChunkStore();

            TransferLookup lookup = TransferLookup.resolve(chunkStore, pos);
            if (lookup == null) return;

            BlockBoundingBoxes.RotatedVariantBoxes rotated =
                    BlockHitboxRaycast.resolveVariantBoxes(world, pos.x, pos.y, pos.z);

            BlockHitboxRaycast.BlockHitResult hit = rotated == null ? null
                    : BlockHitboxRaycast.raycastBlock(
                            eyeX, eyeY, eyeZ, dirX, dirY, dirZ,
                            pos.x, pos.y, pos.z, rotated, MAX_REACH);

            if (hit == null) return;

            FacePlane face = lookup.transfer().getFaces().get(hit.detailBoxIndex);
            if (face == null) return; // non-interactable hitbox (e.g. center)

            UUID oldNeighborId = face.getNeighborNodeId();
            face.setMode(nextMode(face.getMode()));
            FaceLinkUtil.relinkFace(lookup.transfer(), lookup.blockRef(), face, pos, chunkStore,
                    p -> TransferStateRegistry.applyState(world, p));
            UUID newNeighborId = face.getNeighborNodeId();

            TransferGraph graph = GlyphworksPlugin.get().getGraph(world);
            if (graph != null) {
                UUID myId = lookup.transfer().getNodeId();
                if (oldNeighborId != null) graph.removeEdge(myId, oldNeighborId);
                if (newNeighborId != null) graph.addEdge(myId, newNeighborId);
            }
        });
    }

    private static FaceMode nextMode(FaceMode current) {
        return switch (current) {
            case BIDIRECTIONAL -> FaceMode.INPUT;
            case INPUT         -> FaceMode.OUTPUT;
            case OUTPUT        -> FaceMode.CLOSED;
            case CLOSED        -> FaceMode.BIDIRECTIONAL;
        };
    }

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
