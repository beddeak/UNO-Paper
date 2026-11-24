package io.github.beddeak.uno;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

/** Technical cutaway of the actual blueprint, not an in-game screenshot. */
public final class ArcadePreview {
    private static final Map<String, Color> PALETTE = Map.ofEntries(
            Map.entry("SMOOTH_QUARTZ", new Color(239, 236, 228)),
            Map.entry("GREEN_WOOL", new Color(72, 105, 45)),
            Map.entry("POLISHED_DIORITE", new Color(210, 213, 212)),
            Map.entry("LIGHT_GRAY_CONCRETE", new Color(157, 163, 164)),
            Map.entry("LIGHT_BLUE_CONCRETE", new Color(111, 180, 197)),
            Map.entry("GLASS", new Color(194, 224, 230)),
            Map.entry("OAK_LEAVES", new Color(87, 133, 77)),
            Map.entry("BLACK_CONCRETE", new Color(30, 31, 46)),
            Map.entry("GRAY_CONCRETE", new Color(49, 51, 66)),
            Map.entry("POLISHED_DEEPSLATE", new Color(60, 63, 80)),
            Map.entry("POLISHED_BLACKSTONE", new Color(44, 40, 58)),
            Map.entry("SEA_LANTERN", new Color(191, 255, 244)),
            Map.entry("CYAN_CONCRETE", new Color(16, 175, 198)),
            Map.entry("CYAN_STAINED_GLASS", new Color(60, 229, 245)),
            Map.entry("MAGENTA_CONCRETE", new Color(211, 49, 202)),
            Map.entry("MAGENTA_STAINED_GLASS", new Color(245, 92, 226)),
            Map.entry("PURPLE_CONCRETE", new Color(114, 58, 176)),
            Map.entry("RED_CONCRETE", new Color(238, 66, 95)),
            Map.entry("YELLOW_CONCRETE", new Color(246, 205, 63)),
            Map.entry("LIME_CONCRETE", new Color(117, 213, 72)),
            Map.entry("BLUE_CONCRETE", new Color(72, 106, 242)),
            Map.entry("WHITE_CONCRETE", new Color(226, 234, 242)),
            Map.entry("STONE_PRESSURE_PLATE", new Color(166, 169, 184)));

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(1440, 1040, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(235, 240, 242));
        g.fillRect(0, 0, 1440, 1040);
        g.setColor(new Color(37, 85, 97));
        g.setFont(new Font("SansSerif", Font.BOLD, 38));
        g.drawString("UNO / MINIGAME HALL", 70, 72);
        g.setColor(new Color(91, 110, 121));
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.drawString("41 x 41 blocks  /  Quartz, glass & daylight  /  Five-seat UNO table", 72, 105);
        var blocks = new ArcadeLayout().blocks();
        blocks.entrySet().stream()
                .filter(e -> e.getKey().y() >= 64 && e.getKey().y() <= 77)
                .filter(e -> !(e.getKey().x() >= 19 || e.getKey().z() >= 19) || e.getKey().y() == 64)
                .sorted(Comparator.<Map.Entry<ArcadeLayout.Cell, String>>comparingInt(e -> e.getKey().x() + e.getKey().z())
                        .thenComparingInt(e -> e.getKey().y()))
                .forEach(e -> cube(g, e.getKey(), PALETTE.get(e.getValue())));
        g.setColor(new Color(37, 85, 97));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("FIVE-SEAT TABLE", 1145, 48);
        int tile = 20, cx = 1210, cz = 114;
        ArcadeLayout.tabletop().forEach((cell, material) -> {
            int x = cx + tile * cell.x(), z = cz + tile * cell.z();
            g.setColor(PALETTE.get(material));
            g.fillRect(x, z, tile, tile);
            g.setColor(new Color(45, 55, 52, 90));
            g.drawRect(x, z, tile, tile);
        });
        g.setColor(new Color(37, 85, 97));
        for (int i = 0; i < 5; i++) {
            var seat = ArcadeLayout.seatOffset(i, 5, 3, true);
            g.fillOval(cx + tile * (int) seat.x() + 6, cz + tile * (int) seat.z() + 6, 8, 8);
        }
        g.setColor(new Color(37, 85, 97));
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString("01  CENTRAL TABLE", 80, 905);
        g.drawString("02  LOUNGE & PLANTERS", 520, 905);
        g.drawString("03  SPECTATOR LOUNGE", 950, 905);
        g.setColor(new Color(91, 110, 121));
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.drawString("Blueprint cutaway: roof and front walls hidden. Actual Minecraft lighting and text are not shown.", 80, 967);
        g.dispose();
        Path output = Path.of(args.length == 0 ? "docs/arcade-preview.png" : args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        ImageIO.write(image, "png", output.toFile());
        System.out.println(output.toAbsolutePath());
    }

    private static void cube(Graphics2D g, ArcadeLayout.Cell cell, Color color) {
        int x = cell.x(), y = cell.y(), z = cell.z();
        g.setColor(color.darker());
        g.fillPolygon(face(x + 1, y, z, x + 1, y, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z));
        g.setColor(color.darker().darker());
        g.fillPolygon(face(x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1));
        g.setColor(color);
        Polygon top = face(x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1);
        g.fillPolygon(top);
        g.setColor(new Color(0, 0, 0, 32));
        g.drawPolygon(top);
    }

    private static Polygon face(int... coordinates) {
        Polygon p = new Polygon();
        for (int i = 0; i < coordinates.length; i += 3) {
            int x = coordinates[i], y = coordinates[i + 1], z = coordinates[i + 2];
            p.addPoint(720 + (x - z) * 14, 570 + (x + z) * 7 - (y - 64) * 14);
        }
        return p;
    }
}
