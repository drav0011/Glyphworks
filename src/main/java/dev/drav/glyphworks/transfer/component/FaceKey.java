package dev.drav.glyphworks.transfer.component;

import com.hypixel.hytale.math.vector.Vector3i;

import java.util.Objects;

/**
 * Key class for identifying faces in a HashMap.
 * A face is uniquely identified by its min and max corner coordinates.
 * Two faces with the same min/max occupy the same 3D space (collision).
 */
public record FaceKey(Vector3i planeMin, Vector3i planeMax) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FaceKey faceKey = (FaceKey) o;
        return Objects.equals(planeMin, faceKey.planeMin) &&
               Objects.equals(planeMax, faceKey.planeMax);
    }

    @Override
    public String toString() {
        return String.format("FaceKey{min=%s, max=%s}", planeMin, planeMax);
    }

    /**
     * Creates a FaceKey from a FacePlane.
     */
    public static FaceKey fromFacePlane(FacePlane face) {
        return new FaceKey(face.getPlaneMin(), face.getPlaneMax());
    }
}

