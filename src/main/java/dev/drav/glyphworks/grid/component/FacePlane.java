package dev.drav.glyphworks.grid.component;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;

/**
 * Defines a face region of a block in block-relative coordinates.
 */
public class FacePlane {
    public static final BuilderCodec<FacePlane> CODEC = BuilderCodec
            .builder(FacePlane.class, FacePlane::new)
            .append(
                    new KeyedCodec<>("FacePlane_Position", Vector3i.CODEC),
                    (c, v) -> c.position = v,
                    c -> c.position)
            .add()
            .append(
                    new KeyedCodec<>("FacePlane_Normal", new EnumCodec<>(BlockFace.class)),
                    (c, v) -> c.normal = v,
                    c -> c.normal)
            .add()
            .append(
                    new KeyedCodec<>("FacePlane_Mode", new EnumCodec<>(FaceMode.class)),
                    (c, v) -> c.mode = v,
                    c -> c.mode)
            .add()
            .build();

    /**
     * Block-origin relative position
     */
    private Vector3i position;

    /**
     * Normal vector for this face, relative to block
     */
    private BlockFace normal;

    /**
     * Connection mode for this face
     */
    private FaceMode mode;

    /**
     * No-arg constructor required by {@link #CODEC}.
     */
    public FacePlane() {
        this(new Vector3i(0, 0, 0), BlockFace.None, FaceMode.BIDIRECTIONAL);
    }

    /**
     * @param position Block-relative position
     * @param normal   Normal vector for this face
     * @param mode     Initial connection mode
     */
    public FacePlane(Vector3i position, BlockFace normal, FaceMode mode) {
        this.position = position;
        this.normal = normal;
        this.mode = mode;
    }

    public Vector3i getPosition() {
        return position;
    }

    public BlockFace getNormal() {
        return normal;
    }

    public FaceMode getMode() {
        return mode;
    }

    public void setMode(FaceMode mode) {
        this.mode = mode;
    }

    @Override
    public String toString() {
        return String.format("FacePlane{position=%s, normal=%s, mode=%s}", position, normal, mode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof FacePlane))
            return false;
        FacePlane other = (FacePlane) o;
        return position.equals(other.position) && normal == other.normal && mode == other.mode;
    }

    @Override
    public int hashCode() {
        int result = position.hashCode();
        result = 31 * result + normal.hashCode();
        result = 31 * result + mode.hashCode();
        return result;
    }

    public FacePlane clone() {
        return new FacePlane(position, normal, mode);
    }
}
