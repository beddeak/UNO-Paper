package io.github.beddeak.uno.art;

public final class Canvas {

    private final int width;
    private final int height;
    private final int[] pixels;

    public Canvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int[] pixels() {
        return pixels;
    }

    public int get(int x, int y) {
        return pixels[y * width + x];
    }

    public void set(int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < width && y < height) {
            pixels[y * width + x] = argb;
        }
    }

    public void fill(Mask mask, int argb) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.get(x, y)) {
                    pixels[y * width + x] = argb;
                }
            }
        }
    }

    public void blit(Canvas source, int offsetX, int offsetY, int scale) {
        for (int y = 0; y < source.height; y++) {
            for (int x = 0; x < source.width; x++) {
                int pixel = source.get(x, y);
                if ((pixel >>> 24) == 0) {
                    continue;
                }
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        set(offsetX + x * scale + dx, offsetY + y * scale + dy, pixel);
                    }
                }
            }
        }
    }

    public byte[] toPng() {
        return Png.encode(width, height, pixels);
    }

    public static int rgb(int rgb) {
        return 0xFF000000 | rgb;
    }

    public static int mix(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 255) * (1 - t) + ((to >> 16) & 255) * t);
        int g = (int) Math.round(((from >> 8) & 255) * (1 - t) + ((to >> 8) & 255) * t);
        int b = (int) Math.round((from & 255) * (1 - t) + (to & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static int shade(int color, double factor) {
        return mix(color, 0xFF000000, 1 - factor);
    }
}
