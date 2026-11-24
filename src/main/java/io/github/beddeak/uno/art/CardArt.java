package io.github.beddeak.uno.art;

import io.github.beddeak.uno.card.Card;
import io.github.beddeak.uno.card.CardColor;
import io.github.beddeak.uno.card.CardType;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CardArt {

    public static final int SIZE = 64;
    public static final String BACK = "back";
    public static final String BUTTON_DRAW = "button_draw";
    public static final String BUTTON_TAKE = "button_take";
    public static final String BUTTON_PASS = "button_pass";
    public static final String BUTTON_CALL = "button_call";
    public static final String BUTTON_HAND = "button_hand";
    public static final String BUTTON_CLAP = "button_clap";

    private static final int INK = 0xFF15151D;
    private static final int PAPER = 0xFFF7F5EE;
    private static final int GOLD = 0xFFF4C542;
    private static final int WILD_PANEL = 0xFF1E1E28;
    private static final int BACK_PANEL = 0xFF2A2463;
    private static final double CENTER = SIZE / 2.0;

    private CardArt() {
    }

    public static String chosenWildId(Card wild, CardColor color) {
        return wild.id() + "_" + color.id();
    }

    public static Map<String, Canvas> all() {
        Map<String, Canvas> textures = new LinkedHashMap<>();
        for (Card card : Card.distinct()) {
            textures.put(card.id(), card(card));
        }
        for (CardColor color : CardColor.values()) {
            for (Card wild : new Card[] {Card.wild(), Card.wildDrawFour(), Card.wildDrawTen(), Card.wildDrawNinetyNine()}) {
                textures.put(chosenWildId(wild, color), wild(wild.type(), color));
            }
        }
        textures.put(BACK, back());
        textures.put(BUTTON_DRAW, deckButton(Canvas.rgb(CardColor.GREEN.rgb())));
        textures.put(BUTTON_TAKE, deckButton(Canvas.rgb(CardColor.RED.rgb())));
        textures.put(BUTTON_PASS, passButton());
        textures.put(BUTTON_CALL, callButton());
        textures.put(BUTTON_HAND, handButton());
        textures.put(BUTTON_CLAP, clapButton());
        return textures;
    }

    public static Canvas card(Card card) {
        if (card.isWild()) {
            return wild(card.type(), null);
        }
        int base = Canvas.rgb(card.color().rgb());
        Canvas canvas = frame(base);
        Mask emblem = mask().diamond(CENTER, CENTER, 16, 23);
        canvas.fill(emblem, Canvas.shade(base, 0.68));
        canvas.fill(emblem.boundary(), Canvas.mix(base, PAPER, 0.7));
        drawSymbol(canvas, bigSymbol(card));
        drawCorner(canvas, cornerSymbol(card));
        return canvas;
    }

    private static Canvas wild(CardType type, CardColor chosen) {
        Canvas canvas = frame(chosen == null ? WILD_PANEL : Canvas.rgb(chosen.rgb()));
        Mask emblem = mask().diamond(CENTER, CENTER, 16, 23);
        paintQuadrants(canvas, emblem, chosen);
        canvas.fill(emblem.boundary(), PAPER);
        if (type == CardType.WILD_DRAW_FOUR) {
            drawSymbol(canvas, plusDigit(4));
            drawCorner(canvas, Glyphs.text(mask(), "+4", 17, 6));
        } else if (type == CardType.WILD_DRAW_TEN) {
            drawSymbol(canvas, plusTen());
            drawCorner(canvas, Glyphs.text(mask(), "+10", 17, 6));
        } else if (type == CardType.WILD_DRAW_NINETY_NINE) {
            canvas.fill(emblem.boundary(), GOLD);
            drawSymbol(canvas, plusNinetyNine(), GOLD);
            drawCorner(canvas, Glyphs.text(mask(), "+99", 17, 6), GOLD);
        } else {
            Mask corners = mask().rect(17, 6, 20, 9);
            corners.or(corners.rotated180());
            canvas.fill(corners.dilated(1), INK);
            paintQuadrants(canvas, mask().rect(17, 6, 20, 9), null, 19, 8);
            paintQuadrants(canvas, mask().rect(43, 54, 46, 57), null, 45, 56);
        }
        return canvas;
    }

    private static Canvas back() {
        Canvas canvas = frame(BACK_PANEL);
        Mask emblem = mask().diamond(CENTER, CENTER, 11, 16);
        paintQuadrants(canvas, emblem, null);
        canvas.fill(emblem.boundary(), PAPER);
        Mask text = Glyphs.text(mask(), "UNO", 26, 8);
        Glyphs.text(text, "UNO", 26, 51);
        canvas.fill(text.dilated(1), INK);
        canvas.fill(text, GOLD);
        return canvas;
    }

    private static Canvas deckButton(int badgeColor) {
        Canvas canvas = back();
        Mask badge = mask().disk(46, 46, 11.5);
        canvas.fill(badge.dilated(1), INK);
        canvas.fill(badge, badgeColor);
        drawSymbol(canvas, mask().glyph(Glyphs.BIG_PLUS, 40, 40, 2));
        return canvas;
    }

    private static Canvas passButton() {
        Canvas canvas = new Canvas(SIZE, SIZE);
        Mask circle = mask().disk(CENTER, CENTER, 24);
        canvas.fill(circle.dilated(1), INK);
        canvas.fill(circle, 0xFF5F6B7A);
        canvas.fill(circle.boundary(), 0xFF8D99A8);
        Mask arrow = mask().rect(17, 28, 35, 35).or(mask().triangle(35, 17, 48, 32, 35, 47));
        drawSymbol(canvas, arrow);
        return canvas;
    }

    private static Canvas callButton() {
        Canvas canvas = new Canvas(SIZE, SIZE);
        Mask bubble = mask().disk(CENTER, 28, 22).or(mask().triangle(14, 38, 8, 60, 32, 46));
        canvas.fill(bubble.dilated(1), INK);
        canvas.fill(bubble, 0xFFFF8F1F);
        canvas.fill(bubble.boundary(), 0xFFFFC069);
        drawSymbol(canvas, mask().glyph(Glyphs.BANG, 28, 18, 3));
        return canvas;
    }

    private static Canvas handButton() {
        Canvas canvas = new Canvas(SIZE, SIZE);
        CardColor[] colors = {CardColor.RED, CardColor.YELLOW, CardColor.BLUE};
        for (int i = 0; i < colors.length; i++) {
            int left = 6 + i * 14;
            int top = i == 1 ? 8 : 14;
            Mask card = mask().roundRect(left, top, left + 23, top + 37, 3);
            canvas.fill(card.dilated(1), INK);
            canvas.fill(card, PAPER);
            canvas.fill(mask().roundRect(left + 2, top + 2, left + 21, top + 35, 2), Canvas.rgb(colors[i].rgb()));
        }
        drawSymbol(canvas, mask().glyph(Glyphs.BIG_PLUS, 40, 40, 2));
        return canvas;
    }

    private static Canvas clapButton() {
        Canvas canvas = new Canvas(SIZE, SIZE);
        Mask burst = mask().disk(CENTER, CENTER, 21);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 8;
            burst.or(mask().band(CENTER, CENTER, Math.cos(angle), Math.sin(angle), 2.5, 30));
        }
        canvas.fill(burst.dilated(1), INK);
        canvas.fill(burst, 0xFFFFC21F);
        canvas.fill(mask().disk(CENTER, CENTER, 21).boundary(), 0xFFFFE89A);
        drawSymbol(canvas, bigSymbol(Card.number(CardColor.RED, 6)));
        return canvas;
    }

    private static Canvas frame(int panelColor) {
        Canvas canvas = new Canvas(SIZE, SIZE);
        canvas.fill(mask().roundRect(12, 1, 51, 62, 4), INK);
        canvas.fill(mask().roundRect(13, 2, 50, 61, 3), PAPER);
        Mask panel = mask().roundRect(15, 4, 48, 59, 2);
        int stripe = Canvas.mix(panelColor, PAPER, 0.10);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (panel.get(x, y)) {
                    canvas.set(x, y, Math.floorMod(x + y, 8) < 4 ? stripe : panelColor);
                }
            }
        }
        return canvas;
    }

    private static void paintQuadrants(Canvas canvas, Mask area, CardColor chosen) {
        paintQuadrants(canvas, area, chosen, (int) CENTER, (int) CENTER);
    }

    private static void paintQuadrants(Canvas canvas, Mask area, CardColor chosen, int splitX, int splitY) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!area.get(x, y)) {
                    continue;
                }
                CardColor color = x < splitX
                        ? (y < splitY ? CardColor.RED : CardColor.YELLOW)
                        : (y < splitY ? CardColor.BLUE : CardColor.GREEN);
                int rgb = Canvas.rgb(color.rgb());
                canvas.set(x, y, chosen == null || chosen == color ? rgb : Canvas.shade(rgb, 0.45));
            }
        }
    }

    private static Mask bigSymbol(Card card) {
        return switch (card.type()) {
            case NUMBER -> mask().glyph(Glyphs.BIG_DIGITS[card.number()], 24, 21, 3);
            case SKIP -> mask().ring(CENTER, CENTER, 10.5, 6.5).or(mask().band(CENTER, CENTER, 1, -1, 1.6, 10.5));
            case REVERSE -> {
                Mask top = mask().rect(23, 24, 35, 27).or(mask().triangle(35, 20, 43, 26, 35, 32));
                yield top.or(top.rotated180());
            }
            case DRAW_TWO -> plusDigit(2);
            case WILD_DRAW_FOUR -> plusDigit(4);
            case WILD_DRAW_TEN -> plusTen();
            case WILD_DRAW_NINETY_NINE -> plusNinetyNine();
            case WILD -> mask();
        };
    }

    private static Mask cornerSymbol(Card card) {
        return switch (card.type()) {
            case NUMBER -> Glyphs.text(mask(), Integer.toString(card.number()), 17, 6);
            case SKIP -> mask().glyph(Glyphs.SMALL_SKIP, 17, 6, 1);
            case REVERSE -> mask().glyph(Glyphs.SMALL_REVERSE, 17, 6, 1);
            case DRAW_TWO -> Glyphs.text(mask(), "+2", 17, 6);
            case WILD_DRAW_FOUR -> Glyphs.text(mask(), "+4", 17, 6);
            case WILD_DRAW_TEN -> Glyphs.text(mask(), "+10", 17, 6);
            case WILD_DRAW_NINETY_NINE -> Glyphs.text(mask(), "+99", 17, 6);
            case WILD -> mask();
        };
    }

    private static Mask plusDigit(int digit) {
        return mask().glyph(Glyphs.BIG_PLUS, 20, 26, 2).glyph(Glyphs.BIG_DIGITS[digit], 34, 25, 2);
    }

    private static Mask plusTen() {
        return mask().glyph(Glyphs.BIG_PLUS, 19, 29, 1)
                .glyph(Glyphs.BIG_DIGITS[1], 24, 25, 2)
                .glyph(Glyphs.BIG_DIGITS[0], 36, 25, 2);
    }

    private static Mask plusNinetyNine() {
        return mask().glyph(Glyphs.BIG_PLUS, 19, 29, 1)
                .glyph(Glyphs.BIG_DIGITS[9], 24, 25, 2)
                .glyph(Glyphs.BIG_DIGITS[9], 36, 25, 2);
    }

    private static void drawSymbol(Canvas canvas, Mask symbol) {
        drawSymbol(canvas, symbol, PAPER);
    }

    private static void drawSymbol(Canvas canvas, Mask symbol, int fill) {
        Mask outline = symbol.dilated(1);
        canvas.fill(outline.shifted(1, 1), INK);
        canvas.fill(outline, INK);
        canvas.fill(symbol, fill);
    }

    private static void drawCorner(Canvas canvas, Mask corner) {
        drawCorner(canvas, corner, PAPER);
    }

    private static void drawCorner(Canvas canvas, Mask corner, int fill) {
        Mask both = corner.copy().or(corner.rotated180());
        canvas.fill(both.dilated(1), INK);
        canvas.fill(both, fill);
    }

    private static Mask mask() {
        return new Mask(SIZE, SIZE);
    }
}
