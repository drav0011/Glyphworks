package dev.drav.glyphworks.grid.component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;

/**
 * Turns any block into a node in the Glyphworks grid.
 */
public class GridComponent implements Component<ChunkStore> {
    public static final BuilderCodec<GridComponent> CODEC = BuilderCodec
            .builder(GridComponent.class, GridComponent::new)
            .append(
                    new KeyedCodec<>("GridComponent_Type", Codec.STRING),
                    (c, v) -> c.gridType = GridTypeRegistry.get(v),
                    c -> c.gridType != null ? c.gridType.id() : null)
            .add()
            .append(
                    new KeyedCodec<>("GridComponent_Faces", new SetCodec<>(FacePlane.CODEC, HashSet::new, false)),
                    (c, v) -> c.faces = v,
                    c -> c.faces)
            .add()
            .append(
                    new KeyedCodec<>("GridComponent_Neighbors", new SetCodec<>(Vector3i.CODEC, HashSet::new, false)),
                    (c, v) -> c.neighbors = v,
                    c -> c.neighbors)
            .add()
            .build();

    public static ComponentType<ChunkStore, GridComponent> getComponentType() {
        return GlyphworksPlugin.get().getGridComponentType();
    }

    /**
     * The type of grid network this block belongs to.
     * Determines which grid this node is added to and what kind of data flows
     * through it.
     */
    private GridType gridType;

    /**
     * Configured face regions for this block.
     */
    private Set<FacePlane> faces;

    /**
     * World-relative positions of all neighboring blocks linked to this block via a
     * face connection.
     */
    private Set<Vector3i> neighbors;

    /**
     * No-arg constructor required by {@link #CODEC}.
     */
    public GridComponent() {
        this.gridType = null;
        this.faces = new HashSet<>();
        this.neighbors = new HashSet<>();
    }

    /**
     * Copy constructor used by {@link #clone()}.
     * Each {@link FacePlane} is deep-copied so blocks don't share mutable face
     * state.
     */
    public GridComponent(GridComponent other) {
        this.gridType = other.gridType;
        this.faces = new HashSet<>(other.faces.size());
        for (FacePlane f : other.faces) {
            this.faces.add(f.clone());
        }
        this.neighbors = new HashSet<>(other.neighbors);
    }

    public GridType getGridType() {
        return gridType;
    }

    public void setGridType(GridType gridType) {
        this.gridType = gridType;
    }

    public Set<FacePlane> getFaces() {
        return Collections.unmodifiableSet(faces);
    }

    public void setFaces(Set<FacePlane> faces) {
        this.faces = new HashSet<>(faces);
    }

    public void addFace(FacePlane face) {
        faces.add(face);
    }

    public void removeFace(FacePlane face) {
        faces.remove(face);
    }

    public Set<Vector3i> getNeighbors() {
        return Collections.unmodifiableSet(neighbors);
    }

    public void setNeighbors(Set<Vector3i> neighbors) {
        this.neighbors = new HashSet<>(neighbors);
    }

    public void addNeighbor(Vector3i neighbor) {
        neighbors.add(neighbor);
    }

    public void removeNeighbor(Vector3i neighbor) {
        neighbors.remove(neighbor);
    }

    /**
     * Placement copy: generates a fresh node UUID and strips all neighbour linkage.
     * Used by the engine when a player places a block from a prototype.
     */
    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new GridComponent(this);
    }
}
