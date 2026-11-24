package io.github.beddeak.uno.card;

public enum CardType {
    NUMBER("", 0),
    SKIP("skip", 0),
    REVERSE("reverse", 0),
    DRAW_TWO("draw_two", 2),
    WILD("wild", 0),
    WILD_DRAW_FOUR("wild_draw_four", 4),
    WILD_DRAW_TEN("wild_draw_ten", 10),
    WILD_DRAW_NINETY_NINE("wild_draw_ninety_nine", 99);

    private final String id;
    private final int drawAmount;

    CardType(String id, int drawAmount) {
        this.id = id;
        this.drawAmount = drawAmount;
    }

    public String id() {
        return id;
    }

    public int drawAmount() {
        return drawAmount;
    }

    public boolean isWild() {
        return this == WILD || this == WILD_DRAW_FOUR || this == WILD_DRAW_TEN || this == WILD_DRAW_NINETY_NINE;
    }

    public boolean isDraw() {
        return drawAmount > 0;
    }

    /** +2, +4 and +10 stack on each other. +99 stays out: it cannot answer a stack and nothing answers it. */
    public boolean stacks() {
        return isDraw() && this != WILD_DRAW_NINETY_NINE;
    }
}
