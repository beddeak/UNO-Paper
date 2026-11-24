package io.github.beddeak.uno.art;

public final class Mask {

    private final int width;
    private final int height;
    private final boolean[] bits;

    public Mask(int width, int height) {
        this.width = width;
        this.height = height;
        this.bits = new boolean[width * height];
    }

    public boolean get(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && bits[y * width + x];
    }

    public void set(int x, int y) {
        if (x >= 0 && y >= 0 && x < width && y < height) {
            bits[y * width + x] = true;
        }
    }

    public Mask copy() {
        Mask copy = new Mask(width, height);
        System.arraycopy(bits, 0, copy.bits, 0, bits.length);
        return copy;
    }

    public Mask rect(int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                set(x, y);
            }
        }
        return this;
    }

    public Mask roundRect(int x0, int y0, int x1, int y1, int radius) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int dx = Math.max(Math.max(x0 + radius - x, x - (x1 - radius)), 0);
                int dy = Math.max(Math.max(y0 + radius - y, y - (y1 - radius)), 0);
                if (dx == 0 || dy == 0 || (dx - 0.5) * (dx - 0.5) + (dy - 0.5) * (dy - 0.5) <= radius * radius) {
                    set(x, y);
                }
            }
        }
        return this;
    }

    public Mask diamond(double cx, double cy, double halfWidth, double halfHeight) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Math.abs(x + 0.5 - cx) / halfWidth + Math.abs(y + 0.5 - cy) / halfHeight <= 1.0) {
                    set(x, y);
                }
            }
        }
        return this;
    }

    public Mask disk(double cx, double cy, double radius) {
        return ring(cx, cy, radius, -1);
    }

    public Mask ring(double cx, double cy, double outer, double inner) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                if (d <= outer && d >= inner) {
                    set(x, y);
                }
            }
        }
        return this;
    }

    public Mask band(double cx, double cy, double dirX, double dirY, double halfThickness, double radius) {
        double length = Math.hypot(dirX, dirY);
        double nx = -dirY / length;
        double ny = dirX / length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double px = x + 0.5 - cx;
                double py = y + 0.5 - cy;
                if (Math.abs(px * nx + py * ny) <= halfThickness && Math.hypot(px, py) <= radius) {
                    set(x, y);
                }
            }
        }
        return this;
    }

    public Mask triangle(double ax, double ay, double bx, double by, double cx, double cy) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double d1 = cross(px, py, ax, ay, bx, by);
                double d2 = cross(px, py, bx, by, cx, cy);
                double d3 = cross(px, py, cx, cy, ax, ay);
                boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
                boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
                if (!(negative && positive)) {
                    set(x, y);
                }
            }
        }
        return this;
    }

    private static double cross(double px, double py, double ax, double ay, double bx, double by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }

    public Mask glyph(String[] rows, int left, int top, int scale) {
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                if (rows[row].charAt(col) == '#') {
                    rect(left + col * scale, top + row * scale, left + col * scale + scale - 1, top + row * scale + scale - 1);
                }
            }
        }
        return this;
    }

    public Mask or(Mask other) {
        for (int i = 0; i < bits.length; i++) {
            bits[i] |= other.bits[i];
        }
        return this;
    }

    public Mask and(Mask other) {
        for (int i = 0; i < bits.length; i++) {
            bits[i] &= other.bits[i];
        }
        return this;
    }

    public Mask minus(Mask other) {
        for (int i = 0; i < bits.length; i++) {
            bits[i] &= !other.bits[i];
        }
        return this;
    }

    public Mask dilated(int radius) {
        Mask result = new Mask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!get(x, y)) {
                    continue;
                }
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        result.set(x + dx, y + dy);
                    }
                }
            }
        }
        return result;
    }

    public Mask boundary() {
        Mask result = new Mask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (get(x, y) && (!get(x - 1, y) || !get(x + 1, y) || !get(x, y - 1) || !get(x, y + 1))) {
                    result.set(x, y);
                }
            }
        }
        return result;
    }

    public Mask shifted(int dx, int dy) {
        Mask result = new Mask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (get(x, y)) {
                    result.set(x + dx, y + dy);
                }
            }
        }
        return result;
    }

    public Mask rotated180() {
        Mask result = new Mask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (get(x, y)) {
                    result.set(width - 1 - x, height - 1 - y);
                }
            }
        }
        return result;
    }

    public int count() {
        int count = 0;
        for (boolean bit : bits) {
            if (bit) {
                count++;
            }
        }
        return count;
    }
}
