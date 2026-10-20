package com.voxelport.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.ToLongFunction;
import org.junit.jupiter.api.Test;

class RegionsTest {
    private static ToLongFunction<Regions.Region> times(long india, long europe) {
        return r -> r.id().equals("eu") ? europe : india;
    }

    @Test
    void explicitPreferenceWins() {
        assertEquals(Regions.EUROPE, Regions.pick("eu", times(5, 500)));
        assertEquals(Regions.INDIA, Regions.pick("IN", times(500, 5)));
    }

    @Test
    void autoTakesTheFastestRegion() {
        assertEquals(Regions.EUROPE, Regions.pick("auto", times(150, 20)));
        assertEquals(Regions.INDIA, Regions.pick("auto", times(20, 150)));
        assertEquals(Regions.INDIA, Regions.pick(null, times(20, 150)));
    }

    @Test
    void unknownPreferenceBehavesLikeAuto() {
        assertEquals(Regions.EUROPE, Regions.pick("mars", times(150, 20)));
    }

    @Test
    void fallsBackToIndiaWhenNothingAnswers() {
        assertEquals(Regions.INDIA, Regions.pick("auto", times(Regions.UNREACHABLE, Regions.UNREACHABLE)));
    }

    @Test
    void usesEuropeWhenOnlyEuropeAnswers() {
        assertEquals(Regions.EUROPE, Regions.pick("auto", times(Regions.UNREACHABLE, 80)));
    }

    @Test
    void regionsCarryTheirPublicHostAndRelay() {
        assertEquals("play.voxelport.in", Regions.INDIA.publicHost());
        assertEquals("play-eu.voxelport.in", Regions.EUROPE.publicHost());
        assertEquals(Map.of("in", "India", "eu", "Europe"),
                Map.of(Regions.INDIA.id(), Regions.INDIA.label(), Regions.EUROPE.id(), Regions.EUROPE.label()));
        assertTrue(Regions.EUROPE.relayUrls().get(0).endsWith("/ws"));
    }
}
