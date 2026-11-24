package io.github.beddeak.uno.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchSizeTest {
    @Test
    void acceptsTwoToTenPlayersIncludingTheDefaultSoloTest() {
        for (int size = 2; size <= 10; size++) {
            for (int humans = 0; humans <= size; humans++) {
                MatchSize.require(humans, size - humans);
            }
        }
    }

    @Test
    void rejectsUnderfilledOverfilledAndOverflowedStartingRosters() {
        for (int[] counts : new int[][] {{0, 0}, {1, 0}, {0, 1}, {10, 1}, {0, 11}, {1, Integer.MAX_VALUE}}) {
            assertThrows(IllegalArgumentException.class, () -> MatchSize.require(counts[0], counts[1]));
        }
    }

    @Test
    void rejectsNegativeCountsEvenWhenTheirSumIsInRange() {
        assertThrows(IllegalArgumentException.class, () -> MatchSize.require(6, -1));
        assertThrows(IllegalArgumentException.class, () -> MatchSize.require(-1, 6));
    }
}
