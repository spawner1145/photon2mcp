package dev.photonmcp.compat.terrain;

final class TerrainDepthDemand {
    private boolean requested;
    private boolean wanted;

    void request() {
        requested = true;
    }

    boolean wanted() {
        return wanted;
    }

    void endFrame() {
        wanted = requested;
        requested = false;
    }

    void reset() {
        wanted = requested = false;
    }
}
