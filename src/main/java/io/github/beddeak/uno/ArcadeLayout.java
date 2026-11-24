package io.github.beddeak.uno;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A block blueprint, kept independent of Bukkit so walking space can be checked offline. */
final class ArcadeLayout {
    static final int REVISION = 3;
    static final int FLOOR = 64;
    static final int RADIUS = 20;
    static final int MIN_Y = 63;
    static final int MAX_Y = 80;
    record Cell(int x, int y, int z) {}
    record Offset(double x, double z) {}
    private record Place(int rimX, int rimZ, int standX, int standZ, String color) {}
    private static final List<Place> PLACES = List.of(
            new Place(0, -2, 0, -3, "RED_CONCRETE"),
            new Place(3, -1, 4, -1, "YELLOW_CONCRETE"),
            new Place(2, 2, 2, 3, "LIME_CONCRETE"),
            new Place(-2, 2, -2, 3, "BLUE_CONCRETE"),
            new Place(-3, -1, -4, -1, "PURPLE_CONCRETE"));

    static Map<Cell, String> tabletop() {
        Map<Cell, String> top = new LinkedHashMap<>();
        int[] halfWidths = {2, 3, 3, 2, 2, 0};
        for (int row = 0; row < halfWidths.length; row++) {
            int z = row - 2;
            int width = halfWidths[row];
            for (int x = -width; x <= width; x++) {
                boolean felt = row > 0 && row + 1 < halfWidths.length && Math.abs(x) < width
                        && Math.abs(x) <= halfWidths[row - 1] && Math.abs(x) <= halfWidths[row + 1];
                top.put(new Cell(x, FLOOR + 1, z), felt ? "GREEN_WOOL" : "SMOOTH_QUARTZ");
            }
        }
        for (Place place : PLACES) {
            top.put(new Cell(place.rimX, FLOOR + 1, place.rimZ), place.color);
        }
        return top;
    }

    static Offset seatOffset(int index, int count, double radius, boolean hallTable) {
        if (hallTable && count <= PLACES.size()) {
            int station = (index * PLACES.size() + count / 2) / count;
            Place place = PLACES.get(station);
            double scale = Math.max(1, radius / 3);
            return new Offset(place.standX * scale, place.standZ * scale);
        }
        double angle = 2 * Math.PI * index / count;
        double distance = hallTable ? Math.max(4.5, radius) : radius;
        return new Offset(Math.sin(angle) * distance, -Math.cos(angle) * distance);
    }

    private final Map<Cell, String> blocks = new LinkedHashMap<>();

    Map<Cell, String> blocks() {
        return Collections.unmodifiableMap(blocks);
    }

    ArcadeLayout() {
        box(-20, 63, -20, 20, 63, 20, "SMOOTH_QUARTZ");
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                String floor = ring <= 7 ? "POLISHED_DIORITE" : "SMOOTH_QUARTZ";
                if (ring == 8) floor = "LIGHT_GRAY_CONCRETE";
                if (ring == 9 && (Math.abs(x) <= 5 || Math.abs(z) <= 5)) floor = "SEA_LANTERN";
                if (ring >= 18) floor = "LIGHT_GRAY_CONCRETE";
                block(x, FLOOR, z, floor);
                // Broad skylight, with flush white beams and recessed light strips.
                String roof = Math.abs(x) <= 10 && Math.abs(z) <= 10 ? "GLASS" : "SMOOTH_QUARTZ";
                if (x == 0 || z == 0) roof = "SMOOTH_QUARTZ";
                if (Math.abs(x) == 13 && Math.abs(z) <= 16) roof = "SEA_LANTERN";
                block(x, MAX_Y, z, roof);
            }
        }
        for (int i = -20; i <= 20; i++) {
            for (int y = 65; y < MAX_Y; y++) {
                boolean window = Math.abs(i) < 18 && i % 6 != 0 && y >= 68 && y <= 76;
                String wall = window ? "GLASS" : "SMOOTH_QUARTZ";
                if (y == 65) wall = "LIGHT_GRAY_CONCRETE";
                block(i, y, -20, wall);
                block(i, y, 20, wall);
                block(-20, y, i, wall);
                block(20, y, i, wall);
            }
        }
        // A restrained feature wall faces the entrance.
        box(-6, 68, -19, 6, 77, -19, "LIGHT_GRAY_CONCRETE");
        box(-6, 68, -18, 6, 68, -18, "SMOOTH_QUARTZ");
        box(-7, 68, -19, -7, 77, -19, "SEA_LANTERN");
        box(7, 68, -19, 7, 77, -19, "SEA_LANTERN");
        String[] colors = {"RED", "YELLOW", "LIME", "BLUE"};
        for (int i = 0; i < colors.length; i++) {
            box(-5 + i * 3, 69, -18, -4 + i * 3, 69, -18, colors[i] + "_CONCRETE");
        }
        blocks.putAll(tabletop());
        for (int x : new int[] {-16, 16}) {
            for (int z : new int[] {-7, 3}) lounge(x, z);
            for (int z : new int[] {-15, 14}) planter(x, z);
        }
        // Three low white tiers, with a three-block center stair aisle.
        for (int tier = 0; tier < 3; tier++) {
            int z = 13 + tier * 2;
            box(-11, 65, z, 11, 65 + tier, z + 1, "SMOOTH_QUARTZ");
            for (int x = -11; x <= 11; x++) {
                if (Math.abs(x) <= 1) continue;
                block(x, 65 + tier, z, "LIGHT_GRAY_CONCRETE");
                block(x, 66 + tier, z + 1, "SMOOTH_QUARTZ");
            }
        }
        // Flush arrival marker and an unobstructed route to the table.
        box(-2, FLOOR, 10, 2, FLOOR, 11, "LIGHT_BLUE_CONCRETE");
        for (int z = 6; z <= 11; z += 2) block(0, FLOOR, z, "SEA_LANTERN");
    }

    private void lounge(int x, int z) {
        box(x - 1, 65, z - 2, x + 1, 65, z + 2, "SMOOTH_QUARTZ");
        int back = x + (x < 0 ? -1 : 1);
        box(back, 66, z - 2, back, 66, z + 2, "SMOOTH_QUARTZ");
        box(x, 65, z - 1, x, 65, z + 1, "LIGHT_GRAY_CONCRETE");
    }

    private void planter(int x, int z) {
        box(x - 1, 65, z - 1, x + 1, 65, z + 1, "SMOOTH_QUARTZ");
        box(x - 1, 66, z - 1, x + 1, 66, z + 1, "OAK_LEAVES");
        block(x, 67, z, "OAK_LEAVES");
    }

    private void block(int x, int y, int z, String material) {
        blocks.put(new Cell(x, y, z), material);
    }

    private void box(int x1, int y1, int z1, int x2, int y2, int z2, String material) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) block(x, y, z, material);
            }
        }
    }
}
