package dev.photonmcp.compat.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainDepthDemandTest {
    @Test
    void copiesOnlyAfterMaterialRequestsAndStopsAfterAnUnusedFrame() {
        var demand = new TerrainDepthDemand();
        assertFalse(demand.wanted());
        demand.request();
        assertFalse(demand.wanted());
        demand.endFrame();
        assertTrue(demand.wanted());
        demand.endFrame();
        assertFalse(demand.wanted());
    }

    @Test
    void repeatedRequestsCoalesceAndResetDropsStaleDemand() {
        var demand = new TerrainDepthDemand();
        demand.request();
        demand.request();
        demand.endFrame();
        assertTrue(demand.wanted());
        demand.request();
        demand.reset();
        assertFalse(demand.wanted());
        demand.endFrame();
        assertFalse(demand.wanted());
    }
}
