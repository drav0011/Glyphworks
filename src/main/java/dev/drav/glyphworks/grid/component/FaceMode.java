package dev.drav.glyphworks.grid.component;

/**
 * Defines the connection mode for a face of a grid component block.
 */
public enum FaceMode {
    /**
     * This face can only receive items (acts as input).
     * Creates incoming edges when adjacent to OUTPUT or BIDIRECTIONAL faces.
     */
    INPUT,

    /**
     * This face can only send items (acts as output).
     * Creates outgoing edges when adjacent to INPUT or BIDIRECTIONAL faces.
     */
    OUTPUT,

    /**
     * This face can both send and receive items.
     * Creates bidirectional edges with other BIDIRECTIONAL faces.
     * Creates appropriate directional edges with INPUT/OUTPUT faces.
     */
    BIDIRECTIONAL,

    /**
     * This face does not connect to anything.
     * No edges are created for this face.
     */
    CLOSED
}
