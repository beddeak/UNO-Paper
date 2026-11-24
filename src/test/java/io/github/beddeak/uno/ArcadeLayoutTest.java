package io.github.beddeak.uno;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArcadeLayoutTest {
    private final Map<ArcadeLayout.Cell, String> blocks = new ArcadeLayout().blocks();

    @Test
    void floorHasNoHolesAndRoomHasContinuousWallsAndRoof() {
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                assertNotNull(at(x, 64, z), "floor " + x + "," + z);
                assertNotNull(at(x, 80, z), "roof " + x + "," + z);
            }
            for (int y = 65; y < 80; y++) {
                assertNotNull(at(x, y, -20));
                assertNotNull(at(x, y, 20));
                assertNotNull(at(-20, y, x));
                assertNotNull(at(20, y, x));
            }
        }
    }

    @Test
    void arrivalHasHeadroomAndCanWalkToEveryDefaultSeatForTwoToTenPlayers() {
        Set<ArcadeLayout.Cell> reachable = new HashSet<>();
        ArrayDeque<ArcadeLayout.Cell> pending = new ArrayDeque<>();
        pending.add(new ArcadeLayout.Cell(0, 65, 10));
        while (!pending.isEmpty()) {
            ArcadeLayout.Cell cell = pending.removeFirst();
            if (Math.abs(cell.x()) >= 20 || Math.abs(cell.z()) >= 20 || !walkable(cell.x(), cell.z())
                    || !reachable.add(cell)) continue;
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                pending.add(new ArcadeLayout.Cell(cell.x() + step[0], 65, cell.z() + step[1]));
            }
        }
        assertTrue(reachable.contains(new ArcadeLayout.Cell(0, 65, 10)));
        for (int count = 2; count <= 10; count++) {
            Set<ArcadeLayout.Offset> seats = new HashSet<>();
            for (int i = 0; i < count; i++) {
                ArcadeLayout.Offset seat = ArcadeLayout.seatOffset(i, count, 3, true);
                assertTrue(seats.add(seat));
                for (double dx : new double[] {-0.3, 0.3}) {
                    for (double dz : new double[] {-0.3, 0.3}) {
                        int x = (int) Math.floor(0.5 + seat.x() + dx);
                        int z = (int) Math.floor(0.5 + seat.z() + dz);
                        assertTrue(reachable.contains(new ArcadeLayout.Cell(x, 65, z)), "seat " + i + "/" + count);
                    }
                }
            }
        }
    }

    @Test
    void tabletopSupportsWholeFeltAndCardPileWithoutBlockingSeats() {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                assertNotNull(at(x, 65, z));
                assertNull(at(x, 66, z));
                assertNull(at(x, 67, z));
            }
        }
        assertEquals("GREEN_WOOL", at(0, 65, 0));
    }

    @Test
    void fivePlayableStationsTouchFiveDistinctInlaysOnTheTabletop() {
        var top = ArcadeLayout.tabletop();
        assertEquals(30, top.size());
        assertTrue(top.entrySet().stream().allMatch(e -> e.getKey().y() == 65
                && e.getValue().equals(blocks.get(e.getKey()))));
        Set<String> inlays = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            var seat = ArcadeLayout.seatOffset(i, 5, 3, true);
            var adjoining = top.entrySet().stream().filter(e -> e.getValue().endsWith("_CONCRETE"))
                    .filter(e -> Math.abs(e.getKey().x() - seat.x()) + Math.abs(e.getKey().z() - seat.z()) == 1)
                    .toList();
            assertEquals(1, adjoining.size());
            assertTrue(inlays.add(adjoining.getFirst().getValue()));
        }
        assertEquals(Set.of("RED_CONCRETE", "YELLOW_CONCRETE", "LIME_CONCRETE", "BLUE_CONCRETE", "PURPLE_CONCRETE"), inlays);
        assertEquals(new ArcadeLayout.Offset(0, -3), ArcadeLayout.seatOffset(0, 5, 3, false));
    }

    @Test
    void spectatorStepsRiseAtMostOneBlockAndHaveHeadroom() {
        int previous = 64;
        for (int z = 11; z <= 18; z++) {
            int top = 64;
            while (at(0, top + 1, z) != null) top++;
            assertTrue(Math.abs(top - previous) <= 1, "step at " + z);
            assertNull(at(0, top + 1, z));
            assertNull(at(0, top + 2, z));
            previous = top;
        }
    }

    @Test
    void buildRemainsSmallAndInsideDedicatedRoomBounds() {
        assertTrue(blocks.size() < 12000);
        for (ArcadeLayout.Cell cell : blocks.keySet()) {
            assertTrue(Math.abs(cell.x()) <= 20 && Math.abs(cell.z()) <= 20);
            assertTrue(cell.y() >= 63 && cell.y() <= 80);
        }
    }

    private boolean walkable(int x, int z) {
        return at(x, 64, z) != null && at(x, 65, z) == null && at(x, 66, z) == null;
    }

    private String at(int x, int y, int z) {
        return blocks.get(new ArcadeLayout.Cell(x, y, z));
    }
}
